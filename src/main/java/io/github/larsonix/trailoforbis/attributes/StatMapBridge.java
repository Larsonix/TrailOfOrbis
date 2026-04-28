package io.github.larsonix.trailoforbis.attributes;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.Modifier;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.StaticModifier;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.systems.VanillaStatReader;

import javax.annotation.Nonnull;
import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Bridges ComputedStats to Hytale's native EntityStatMap.
 *
 * <p>This is the key integration layer between Trail of Orbis's stat system
 * and Hytale's ECS stat system. When both mods are loaded, Hexcode reads
 * stats from EntityStatMap — without this bridge, stats computed by ToO
 * (like maxMana from WATER attribute) are invisible to Hexcode.
 *
 * <p><b>Phase 1:</b> Bridges maxMana only.
 * <p><b>Phase 2 (future):</b> Will also bridge Hexcode-specific stats
 * (Volatility, Magic_Power, MagicCharges) once those are added to ComputedStats.
 *
 * <p><b>Thread safety:</b> All methods must be called from the world thread.
 * The caller (stats application callback) handles this via {@code world.execute()}.
 *
 * <p><b>Modifier stacking:</b> After this bridge applies:
 * <pre>
 * Total Mana = base (Hytale default)
 *            + armor mana (Hexcode's ArmorManaPatcher, per armor piece)
 *            + attribute mana (this bridge, from WATER attribute + gear + skills)
 *            + weapon mana (native Weapon.StatModifiers if holding hex staff)
 * </pre>
 * All sources use ADDITIVE modifiers with different keys — they stack naturally.
 */
public final class StatMapBridge {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    // Modifier IDs — prefixed with "rpg_" to avoid collision with Hexcode
    private static final String MANA_MODIFIER_ID = "rpg_max_mana";
    private static final String VOLATILITY_MODIFIER_ID = "rpg_volatility";
    private static final String MAGIC_POWER_MODIFIER_ID = "rpg_magic_power";
    private static final String MAGIC_CHARGES_MODIFIER_ID = "rpg_magic_charges";

    // Hexcode stat indices — resolved lazily via reflection, cached
    private static volatile int hexVolatilityIndex = Integer.MIN_VALUE;
    private static volatile int hexMagicPowerIndex = Integer.MIN_VALUE;
    private static volatile int hexMagicChargesIndex = Integer.MIN_VALUE;
    private static volatile boolean hexIndicesResolved = false;

    private StatMapBridge() {
        // Utility class
    }

    /**
     * Applies select ComputedStats fields to the player's EntityStatMap.
     *
     * <p><b>MUST be called on the world thread.</b>
     *
     * <p>Currently bridges:
     * <ul>
     *   <li>maxMana → Mana stat (MAX, ADDITIVE) with key "rpg_max_mana"</li>
     * </ul>
     *
     * <p>Mana regen is NOT bridged because Hytale's regen system uses interval-based
     * {@code Regenerating} configuration on the stat type, not a separate stat index.
     * Our manaRegen field is used internally by ToO's own regen systems.
     *
     * @param store The entity store (from world thread context)
     * @param ref The player's entity reference
     * @param stats The fully-computed stats to bridge
     * @param playerId The player's UUID (for vanilla base stat lookup)
     */
    public static void applyToEntity(
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull ComputedStats stats,
            @Nonnull UUID playerId
    ) {
        if (!ref.isValid()) {
            LOGGER.atFine().log("[StatMapBridge] Entity ref invalid for %s, skipping", playerId);
            return;
        }

        EntityStatMap statMap = store.getComponent(ref, EntityStatMap.getComponentType());
        if (statMap == null) {
            LOGGER.atFine().log("[StatMapBridge] No EntityStatMap for %s, skipping", playerId);
            return;
        }

        applyMana(statMap, stats, playerId, store, ref);

        // Bridge Hexcode-facing stats (only if Hexcode is loaded and indices resolved)
        if (isHexcodeAvailable()) {
            applyHexcodeStats(statMap, stats, playerId);
        }
    }

    /**
     * Applies maxMana to EntityStatMap with percentage preservation.
     *
     * <p>Computes the mana bonus as {@code computedMaxMana - vanillaBaseMana}
     * and applies it as an ADDITIVE MAX modifier. Preserves current mana as
     * a percentage of max across the modifier swap to avoid clamping.
     */
    private static void applyMana(
            @Nonnull EntityStatMap statMap,
            @Nonnull ComputedStats stats,
            @Nonnull UUID playerId,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref
    ) {
        int manaIndex = DefaultEntityStatTypes.getMana();
        if (manaIndex == Integer.MIN_VALUE) {
            LOGGER.atWarning().log("[StatMapBridge] Mana stat index not resolved, skipping");
            return;
        }

        // Clean up legacy modifier key (mana was previously handled by StatsApplicationSystem
        // under "rpg_attribute_bonus" — remove it so it doesn't double-count)
        statMap.removeModifier(manaIndex, "rpg_attribute_bonus");

        // Compute bonus: total computed mana minus vanilla base
        float baseMana = VanillaStatReader.getBaseMana(store, ref);
        float manaBonus = stats.getMaxMana() - baseMana;

        // Skip if existing modifier already has the correct value (prevent feedback loops)
        Modifier existing = statMap.getModifier(manaIndex, MANA_MODIFIER_ID);
        if (existing instanceof StaticModifier existingStatic) {
            if (Math.abs(existingStatic.getAmount() - manaBonus) < 0.001f) {
                return;
            }
        } else if (existing == null && Math.abs(manaBonus) < 0.001f) {
            return;
        }

        // Preserve current mana as percentage of max
        EntityStatValue statValue = statMap.get(manaIndex);
        float percentage = 1.0f;
        if (statValue != null && statValue.getMax() > 0) {
            percentage = statValue.get() / statValue.getMax();
        }

        // Remove old, apply new
        statMap.removeModifier(manaIndex, MANA_MODIFIER_ID);
        if (Math.abs(manaBonus) > 0.001f) {
            StaticModifier modifier = new StaticModifier(
                    Modifier.ModifierTarget.MAX,
                    StaticModifier.CalculationType.ADDITIVE,
                    manaBonus
            );
            statMap.putModifier(manaIndex, MANA_MODIFIER_ID, modifier);
        }

        // Restore current mana to same percentage of new max
        statValue = statMap.get(manaIndex);
        if (statValue != null) {
            float newMax = statValue.getMax();
            float restored = Math.max(0f, Math.min(newMax * percentage, newMax));
            statMap.setStatValue(EntityStatMap.Predictable.SELF, manaIndex, restored);
        }

        LOGGER.atFine().log("[StatMapBridge] Applied mana for %s: bonus=%.1f (computed=%.1f, base=%.1f)",
                playerId, manaBonus, stats.getMaxMana(), baseMana);
    }

    // ==================== Hexcode Stat Bridging ====================

    /**
     * Applies Hexcode-facing magic stats to EntityStatMap.
     *
     * <p>Bridges 3 stats from ComputedStats to Hexcode's custom stat types:
     * <ul>
     *   <li>volatilityMax → Volatility (max glyph budget per cast)</li>
     *   <li>magicPower → Magic_Power (effect magnitude multiplier)</li>
     *   <li>magicCharges → MagicCharges (max concurrent active spells)</li>
     * </ul>
     *
     * <p>drawAccuracy and castSpeed are ToO-internal stats — they stay in
     * ComputedStats only and are NOT bridged to EntityStatMap.
     */
    private static void applyHexcodeStats(
            @Nonnull EntityStatMap statMap,
            @Nonnull ComputedStats stats,
            @Nonnull UUID playerId
    ) {
        applyHexcodeStat(statMap, hexVolatilityIndex, VOLATILITY_MODIFIER_ID,
                stats.getVolatilityMax(), playerId, "Volatility");
        applyHexcodeStat(statMap, hexMagicPowerIndex, MAGIC_POWER_MODIFIER_ID,
                stats.getMagicPower(), playerId, "MagicPower");
        applyHexcodeStat(statMap, hexMagicChargesIndex, MAGIC_CHARGES_MODIFIER_ID,
                (float) stats.getMagicCharges(), playerId, "MagicCharges");
    }

    /**
     * Applies a single stat to EntityStatMap with duplicate-check optimization.
     */
    private static void applyHexcodeStat(
            @Nonnull EntityStatMap statMap,
            int statIndex,
            @Nonnull String modifierId,
            float value,
            @Nonnull UUID playerId,
            @Nonnull String statName
    ) {
        if (statIndex == Integer.MIN_VALUE) {
            return;
        }

        // Skip if existing modifier already matches
        Modifier existing = statMap.getModifier(statIndex, modifierId);
        if (existing instanceof StaticModifier existingStatic) {
            if (Math.abs(existingStatic.getAmount() - value) < 0.001f) {
                return;
            }
        } else if (existing == null && Math.abs(value) < 0.001f) {
            return;
        }

        // Remove old, apply new
        statMap.removeModifier(statIndex, modifierId);
        if (Math.abs(value) > 0.001f) {
            StaticModifier modifier = new StaticModifier(
                    Modifier.ModifierTarget.MAX,
                    StaticModifier.CalculationType.ADDITIVE,
                    value
            );
            statMap.putModifier(statIndex, modifierId, modifier);
        }

        LOGGER.atFine().log("[StatMapBridge] Applied %s for %s: %.2f", statName, playerId, value);
    }

    // ==================== Hexcode Stat Index Resolution ====================

    /**
     * Resolves Hexcode-specific stat indices via reflection.
     *
     * <p>Hexcode registers custom stat types (Volatility, Magic_Power, MagicCharges)
     * via {@code EntityStatType.getAssetMap().getIndex()}. We mirror that resolution
     * here using reflection to avoid any compile-time dependency on Hexcode.
     *
     * <p>Indices are cached after first resolution — they're stable after startup.
     * If Hexcode isn't loaded, all indices remain {@code Integer.MIN_VALUE} and
     * Hexcode-specific stats are silently skipped.
     *
     * <p>Call this once during initialization (after all plugins have started)
     * or lazily on first use.
     */
    public static void resolveHexcodeStatIndices() {
        if (hexIndicesResolved) {
            return;
        }

        try {
            // Try to load HexcodeEntityStatTypes via reflection
            Class<?> hexStatTypes = Class.forName(
                    "com.riprod.hexcode.core.common.stats.HexcodeEntityStatTypes"
            );

            // Resolve each stat index
            hexVolatilityIndex = invokeStatGetter(hexStatTypes, "getVolatility");
            hexMagicPowerIndex = invokeStatGetter(hexStatTypes, "getMagicPower");
            hexMagicChargesIndex = invokeStatGetter(hexStatTypes, "getMagicCharges");

            LOGGER.atInfo().log("[StatMapBridge] Hexcode stat indices resolved: Volatility=%d, MagicPower=%d, MagicCharges=%d",
                    hexVolatilityIndex, hexMagicPowerIndex, hexMagicChargesIndex);

        } catch (ClassNotFoundException e) {
            // Hexcode not loaded — expected when running without it
            LOGGER.atInfo().log("[StatMapBridge] Hexcode not detected, magic stat bridging disabled");
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("[StatMapBridge] Failed to resolve Hexcode stat indices");
        }

        hexIndicesResolved = true;
    }

    /**
     * Invokes a static getter on the Hexcode stat types class via reflection.
     *
     * @return The stat index, or {@code Integer.MIN_VALUE} if resolution failed
     */
    private static int invokeStatGetter(@Nonnull Class<?> clazz, @Nonnull String methodName) {
        try {
            Method method = clazz.getMethod(methodName);
            Object result = method.invoke(null);
            if (result instanceof Integer idx) {
                return idx;
            }
        } catch (Exception e) {
            LOGGER.atWarning().log("[StatMapBridge] Failed to invoke %s: %s", methodName, e.getMessage());
        }
        return Integer.MIN_VALUE;
    }

    // ==================== Hexcode Index Accessors ====================

    /** Returns the resolved Volatility stat index, or {@code Integer.MIN_VALUE} if unavailable. */
    public static int getHexVolatilityIndex() {
        return hexVolatilityIndex;
    }

    /** Returns the resolved Magic_Power stat index, or {@code Integer.MIN_VALUE} if unavailable. */
    public static int getHexMagicPowerIndex() {
        return hexMagicPowerIndex;
    }

    /** Returns the resolved MagicCharges stat index, or {@code Integer.MIN_VALUE} if unavailable. */
    public static int getHexMagicChargesIndex() {
        return hexMagicChargesIndex;
    }

    /** Returns true if Hexcode stat indices have been resolved (even if Hexcode isn't loaded). */
    public static boolean areHexIndicesResolved() {
        return hexIndicesResolved;
    }

    /** Returns true if Hexcode stats are available for bridging. */
    public static boolean isHexcodeAvailable() {
        return hexIndicesResolved
                && hexVolatilityIndex != Integer.MIN_VALUE
                && hexMagicPowerIndex != Integer.MIN_VALUE
                && hexMagicChargesIndex != Integer.MIN_VALUE;
    }
}
