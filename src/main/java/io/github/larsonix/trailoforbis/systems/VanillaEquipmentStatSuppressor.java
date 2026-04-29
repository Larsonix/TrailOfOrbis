package io.github.larsonix.trailoforbis.systems;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatsSystems;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.Set;

/**
 * Suppresses vanilla equipment stat modifiers for players.
 *
 * <h2>Problem</h2>
 * <p>Hytale's {@code StatModifiersManager} applies stat modifiers from equipped items
 * every tick — health bonuses from armor, stat bonuses from weapons/utilities. These
 * stack ON TOP of our RPG stat system's modifiers, causing double stats (vanilla + RPG).
 *
 * <h2>Solution</h2>
 * <p>This system runs AFTER {@link EntityStatsSystems.Recalculate} (which applies
 * vanilla modifiers) and removes all equipment-sourced modifiers. Entity effect
 * modifiers ({@code "Effect_*"}) are intentionally preserved — those represent
 * gameplay mechanics (potions, buffs) that should stack with RPG stats.
 *
 * <h2>Modifier Key Patterns</h2>
 * <table>
 * <tr><th>Source</th><th>Key Pattern</th></tr>
 * <tr><td>Armor</td><td>{@code "Armor_ADDITIVE"}, {@code "Armor_MULTIPLICATIVE"}</td></tr>
 * <tr><td>Weapon</td><td>{@code "*Weapon_0"}, {@code "*Weapon_1"}, ...</td></tr>
 * <tr><td>Utility</td><td>{@code "*Utility_0"}, {@code "*Utility_1"}, ...</td></tr>
 * </table>
 *
 * <h2>Performance</h2>
 * <p>For each player, iterates 6 stat indices × ~4 key checks = ~24 hash lookups per tick.
 * {@code removeModifier} returns null immediately for non-existent keys. Negligible cost.
 *
 * @see EntityStatsSystems.Recalculate
 * @see StatsApplicationSystem
 */
public final class VanillaEquipmentStatSuppressor extends EntityTickingSystem<EntityStore> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /** Vanilla equipment modifier key prefixes to suppress. */
    private static final String PREFIX_ARMOR = "Armor_";
    private static final String PREFIX_WEAPON = "*Weapon_";
    private static final String PREFIX_UTILITY = "*Utility_";

    /** Fixed armor modifier keys (only two calculation types exist). */
    private static final String KEY_ARMOR_ADDITIVE = "Armor_ADDITIVE";
    private static final String KEY_ARMOR_MULTIPLICATIVE = "Armor_MULTIPLICATIVE";

    /** Maximum number of indexed modifiers to check per prefix (weapons/utilities). */
    private static final int MAX_INDEXED_MODIFIERS = 16;

    private final ComponentType<EntityStore, PlayerRef> playerRefType;
    private final ComponentType<EntityStore, EntityStatMap> statMapType;

    public VanillaEquipmentStatSuppressor() {
        this.playerRefType = PlayerRef.getComponentType();
        this.statMapType = EntityStatMap.getComponentType();
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(playerRefType, statMapType);
    }

    /**
     * Run AFTER Hytale's stat recalculation system.
     *
     * <p>This ensures vanilla equipment modifiers have been applied first,
     * so we can cleanly remove them without fighting the vanilla system.
     */
    @Nonnull
    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return Set.of(
            new SystemDependency<>(Order.AFTER, EntityStatsSystems.Recalculate.class)
        );
    }

    @Override
    public void tick(
            float dt,
            int index,
            @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer) {

        EntityStatMap statMap = archetypeChunk.getComponent(index, statMapType);
        if (statMap == null) {
            return;
        }

        suppressVanillaEquipmentModifiers(statMap);
    }

    /**
     * Removes all vanilla equipment stat modifiers from the entity's stat map.
     *
     * <p>For each vanilla stat index, directly removes modifiers using the known
     * key names. Uses the same incremental clearing pattern as Hytale's own
     * {@code StatModifiersManager.clearStatModifiers()} for indexed modifiers.
     *
     * <p>{@code removeModifier} returns null silently for non-existent keys,
     * so calling it with keys that don't exist is harmless — no enumeration needed.
     */
    private void suppressVanillaEquipmentModifiers(@Nonnull EntityStatMap statMap) {
        int[] statIndices = getStatIndices();

        for (int statIndex : statIndices) {
            // Armor modifier keys are fixed (only two calculation types)
            statMap.removeModifier(EntityStatMap.Predictable.SELF, statIndex, KEY_ARMOR_ADDITIVE);
            statMap.removeModifier(EntityStatMap.Predictable.SELF, statIndex, KEY_ARMOR_MULTIPLICATIVE);

            // Weapon/utility modifier keys are indexed — clear until null
            clearIndexedModifiers(statMap, statIndex, PREFIX_WEAPON);
            clearIndexedModifiers(statMap, statIndex, PREFIX_UTILITY);
        }
    }

    /**
     * Clears all indexed modifiers matching a prefix (e.g., "*Weapon_0", "*Weapon_1", ...).
     *
     * <p>Same pattern as Hytale's {@code StatModifiersManager.clearStatModifiers()}.
     */
    private static void clearIndexedModifiers(
            @Nonnull EntityStatMap statMap,
            int statIndex,
            @Nonnull String prefix) {
        int offset = 0;
        while (statMap.removeModifier(EntityStatMap.Predictable.SELF, statIndex, prefix + offset) != null) {
            offset++;
        }
    }

    /**
     * Returns all vanilla stat indices to check.
     *
     * <p>Covers all resource stats that equipment items can modify.
     * Called every tick but the array is tiny (6 elements) and the indices
     * are cached by DefaultEntityStatTypes.
     */
    private static int[] getStatIndices() {
        return new int[] {
            DefaultEntityStatTypes.getHealth(),
            DefaultEntityStatTypes.getOxygen(),
            DefaultEntityStatTypes.getStamina(),
            DefaultEntityStatTypes.getMana(),
            DefaultEntityStatTypes.getSignatureEnergy(),
            DefaultEntityStatTypes.getAmmo()
        };
    }
}
