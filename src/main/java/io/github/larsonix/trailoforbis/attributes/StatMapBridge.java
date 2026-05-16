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
import io.github.larsonix.trailoforbis.config.ConfigManager;
import io.github.larsonix.trailoforbis.gear.model.WeaponType;
import io.github.larsonix.trailoforbis.systems.VanillaStatReader;

import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemWeapon;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Bridges ComputedStats to Hytale's native EntityStatMap.
 *
 * <p>This is the key integration layer between Trail of Orbis's stat system
 * and Hytale's ECS stat system. When both mods are loaded, Hexcode reads
 * stats from EntityStatMap — without this bridge, stats computed by ToO
 * (like maxMana from WATER attribute) are invisible to Hexcode.
 *
 * <p>Bridges maxMana (from WATER attribute) plus Hexcode-specific stats:
 * Volatility, Magic_Power, and MagicCharges (via reflection-resolved indices).
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

    /** ConfigManager for lazy config access (survives /rpg reload). */
    private static volatile ConfigManager configManager;

    private StatMapBridge() {
        // Utility class
    }

    /**
     * Sets the config manager for lazy config lookups (stat caps, etc.).
     * Uses ConfigManager instead of direct config reference so /rpg reload
     * picks up changes without re-wiring dependencies.
     */
    public static void setConfigManager(@Nullable ConfigManager cfgMgr) {
        configManager = cfgMgr;
    }

    /**
     * Removes the RPG mana modifier from a player's EntityStatMap.
     *
     * <p>Called when entering Creative mode to cleanly revert mana to vanilla base.
     *
     * @param statMap The entity stat map
     */
    public static void removeManaModifier(@Nonnull EntityStatMap statMap) {
        statMap.removeModifier(DefaultEntityStatTypes.getMana(), MANA_MODIFIER_ID);
    }

    /**
     * Applies select ComputedStats fields to the player's EntityStatMap.
     *
     * <p><b>MUST be called on the world thread.</b>
     *
     * <p>Bridges:
     * <ul>
     *   <li>maxMana → Mana stat (MAX, ADDITIVE) with key "rpg_max_mana"</li>
     *   <li>volatilityMax → Volatility (MAX, ADDITIVE) with key "rpg_volatility"</li>
     *   <li>magicPower → Magic_Power (MAX, ADDITIVE) with key "rpg_magic_power"</li>
     *   <li>magicCharges → MagicCharges (MAX, ADDITIVE) with key "rpg_magic_charges"</li>
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

    // ==================== Weapon Mechanic Stat Correction ====================

    /**
     * Forces the client to re-receive current weapon-mechanic stat values.
     *
     * <p>Called by {@code ItemSyncCoordinator} after every equipment sync flush.
     * The client re-applies {@code weapon.statModifiers} on each UpdateItems packet,
     * resetting SignatureEnergy/Ammo/SignatureCharges to their modifier values.
     * This method immediately force-sends the server's actual current values using
     * {@code Predictable.SELF}, which unconditionally queues an {@code EntityStatUpdate}
     * (see decompiled {@code EntityStatMap.setStatValue()} line 195).
     *
     * @param statMap The player's entity stat map
     * @param player The player (for inventory access)
     */
    public static void correctWeaponMechanicStats(
            @Nonnull EntityStatMap statMap,
            @Nonnull Player player) {

        @SuppressWarnings("deprecation")
        Inventory inventory = player.getInventory();
        if (inventory == null) return;

        // Correct stats declared by the held weapon (SignatureEnergy, SignatureCharges, Ammo)
        ItemStack itemInHand = inventory.getItemInHand();
        correctStatsForWeapon(statMap, itemInHand);

        // Correct stats declared by the utility item (if weapon allows it)
        correctStatsForUtility(statMap, inventory, itemInHand);
    }

    /**
     * Force-sends current values for all stats declared in the weapon's statModifiers.
     */
    private static void correctStatsForWeapon(
            @Nonnull EntityStatMap statMap,
            @Nullable ItemStack itemStack) {

        if (ItemStack.isEmpty(itemStack)) return;

        Item item = itemStack.getItem();
        if (item == null) return;

        ItemWeapon weapon = item.getWeapon();
        if (weapon == null) return;

        Int2ObjectMap<StaticModifier[]> mods = weapon.getStatModifiers();
        if (mods == null || mods.isEmpty()) return;

        for (int statIndex : mods.keySet()) {
            EntityStatValue esv = statMap.get(statIndex);
            if (esv == null) continue;
            statMap.setStatValue(EntityStatMap.Predictable.SELF, statIndex, esv.get());
        }
    }

    /**
     * Force-sends current values for stats declared in the utility item's statModifiers.
     * Only applies if the weapon allows a utility slot (one-handed / compatible).
     */
    private static void correctStatsForUtility(
            @Nonnull EntityStatMap statMap,
            @Nonnull Inventory inventory,
            @Nullable ItemStack weaponStack) {

        // Only process utility if weapon allows it (per WeaponType rules, not vanilla flag)
        if (!ItemStack.isEmpty(weaponStack)) {
            Item weaponItem = weaponStack.getItem();
            if (weaponItem != null) {
                WeaponType weaponType = WeaponType.fromItemIdOrUnknown(weaponItem.getId());
                if (!weaponType.allowsOffhand()) {
                    return; // Weapon blocks offhand stats
                }
            }
        }

        ItemStack utilityStack = inventory.getUtilityItem();
        if (ItemStack.isEmpty(utilityStack)) return;

        Item utilityItem = utilityStack.getItem();
        if (utilityItem == null) return;

        com.hypixel.hytale.server.core.asset.type.item.config.ItemUtility utility = utilityItem.getUtility();
        if (utility == null) return;

        Int2ObjectMap<StaticModifier[]> mods = utility.getStatModifiers();
        if (mods == null || mods.isEmpty()) return;

        for (int statIndex : mods.keySet()) {
            EntityStatValue esv = statMap.get(statIndex);
            if (esv == null) continue;
            statMap.setStatValue(EntityStatMap.Predictable.SELF, statIndex, esv.get());
        }
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
        // Stats are pre-capped in AttributeCalculator — apply directly
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
    /**
     * Resolves Hexcode stat indices via the HexcodeBridge (direct API, no reflection).
     * Falls back gracefully if Hexcode is not loaded.
     */
    public static void resolveHexcodeStatIndices() {
        if (hexIndicesResolved) {
            return;
        }

        try {
            var manager = io.github.larsonix.trailoforbis.compat.HexcodeCompatManager.get();
            if (manager.isLoaded()) {
                var bridge = manager.bridge();
                bridge.resolveStatIndices();
                hexVolatilityIndex = bridge.getVolatilityStatIndex();
                hexMagicPowerIndex = bridge.getMagicPowerStatIndex();
                hexMagicChargesIndex = bridge.getMagicChargesStatIndex();

                LOGGER.atInfo().log("[StatMapBridge] Hexcode stat indices resolved via bridge: Volatility=%d, MagicPower=%d, MagicCharges=%d",
                        hexVolatilityIndex, hexMagicPowerIndex, hexMagicChargesIndex);
            } else {
                LOGGER.atInfo().log("[StatMapBridge] Hexcode not loaded, magic stat bridging disabled");
            }
        } catch (IllegalStateException e) {
            LOGGER.atInfo().log("[StatMapBridge] HexcodeCompatManager not initialized, magic stat bridging disabled");
        }

        hexIndicesResolved = true;
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
