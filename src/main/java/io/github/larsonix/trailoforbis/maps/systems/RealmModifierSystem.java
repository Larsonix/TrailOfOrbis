package io.github.larsonix.trailoforbis.maps.systems;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.HolderSystem;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.EntityStatType;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.Modifier;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.StaticModifier;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import io.github.larsonix.trailoforbis.TrailOfOrbis;
import io.github.larsonix.trailoforbis.maps.components.RealmMobComponent;
import io.github.larsonix.trailoforbis.mobs.systems.MobScalingSystem;

import javax.annotation.Nonnull;
import java.util.Set;

/**
 * ECS holder system that applies realm modifiers to mob stats.
 *
 * <p>When a mob with {@link RealmMobComponent} is added to the entity store,
 * this system reads the realm's modifiers and applies appropriate stat changes.
 *
 * <p>Modifiers applied:
 * <ul>
 *   <li><b>MONSTER_DAMAGE</b> - Increases mob damage output</li>
 *   <li><b>MONSTER_HEALTH</b> - Increases mob max health</li>
 *   <li><b>MONSTER_SPEED</b> - Increases mob movement speed</li>
 * </ul>
 *
 * <p>This system runs AFTER {@link MobScalingSystem} to ensure map modifiers
 * are the final multiplier layer, applied on top of all base stat calculations.
 *
 * @see RealmMobComponent
 * @see MobScalingSystem
 */
public class RealmModifierSystem extends HolderSystem<EntityStore> {

    /**
     * Modifier key for realm stat modifications.
     * Used to identify and potentially remove these modifiers later.
     */
    private static final String REALM_HEALTH_KEY = "REALM_HEALTH_MOD";

    private final TrailOfOrbis plugin;

    // Component types for query
    private final ComponentType<EntityStore, RealmMobComponent> realmMobType;
    private final ComponentType<EntityStore, NPCEntity> npcType;
    private final ComponentType<EntityStore, EntityStatMap> statMapType;

    // Cached stat indices (lazily initialized)
    private int healthStatIndex = -1;

    /**
     * Creates a new realm modifier system.
     *
     * @param plugin The TrailOfOrbis plugin instance
     */
    public RealmModifierSystem(@Nonnull TrailOfOrbis plugin) {
        this.plugin = plugin;
        this.realmMobType = RealmMobComponent.getComponentType();
        this.npcType = NPCEntity.getComponentType();
        this.statMapType = EntityStatMap.getComponentType();
    }

    @Override
    @Nonnull
    public Set<Dependency<EntityStore>> getDependencies() {
        // Map modifiers are the FINAL multiplier layer — must run after all base stat calculations
        return Set.of(
            new SystemDependency<>(Order.AFTER, MobScalingSystem.class)
        );
    }

    @Override
    @Nonnull
    public Query<EntityStore> getQuery() {
        return Archetype.of(realmMobType, npcType, statMapType);
    }

    @Override
    public void onEntityAdd(
            @Nonnull Holder<EntityStore> holder,
            @Nonnull AddReason reason,
            @Nonnull Store<EntityStore> store) {

        // Only process SPAWN reason (when mobs are first added)
        if (reason != AddReason.SPAWN) {
            return;
        }

        // Get RealmMobComponent
        RealmMobComponent realmMob = holder.getComponent(realmMobType);
        if (realmMob == null || realmMob.getRealmId() == null) {
            return;
        }

        // Get entity stats
        EntityStatMap statMap = holder.getComponent(statMapType);
        if (statMap == null) {
            return;
        }

        // Apply map modifiers as the final multiplier layer.
        // Multiplier values are already set on the component by RealmEntitySpawner.
        applyModifiers(statMap, realmMob);
    }

    @Override
    public void onEntityRemoved(
            @Nonnull Holder<EntityStore> holder,
            @Nonnull RemoveReason reason,
            @Nonnull Store<EntityStore> store) {
        // Modifiers are automatically cleaned up when entity is removed
        // No explicit cleanup needed
    }

    /**
     * Applies realm modifiers to a mob's stats as the final multiplier layer.
     *
     * <p>Reads multiplier values from the RealmMobComponent (set by RealmEntitySpawner,
     * which already includes map modifier % and player count scaling). This system
     * only applies them as ECS stat modifiers — it does not recalculate or overwrite
     * the component values.
     *
     * @param statMap The mob's stat map (base stats already set by MobScalingSystem)
     * @param realmMob The mob's realm component (multipliers set by spawner)
     */
    private void applyModifiers(
            @Nonnull EntityStatMap statMap,
            @Nonnull RealmMobComponent realmMob) {

        // Read multipliers from component — authoritative values set by RealmEntitySpawner
        // (includes map modifier % + player count scaling, no level scaling)
        float healthMultiplier = realmMob.getHealthMultiplier();

        // Apply health multiplier as final layer on top of all base stat calculations
        if (healthMultiplier != 1.0f) {
            applyHealthMultiplier(statMap, healthMultiplier);
        }

        // Damage and speed multipliers are stored in component
        // and applied during combat/movement via RealmMobComponent.getDamageMultiplier() etc.
    }

    /**
     * Applies a health multiplier to a mob's stats.
     *
     * @param statMap The mob's stat map
     * @param multiplier The health multiplier (1.0 = no change, 2.0 = double)
     */
    private void applyHealthMultiplier(@Nonnull EntityStatMap statMap, float multiplier) {
        // Cache stat index on first use
        if (healthStatIndex < 0) {
            healthStatIndex = EntityStatType.getAssetMap().getIndex("Health");
        }

        if (healthStatIndex < 0) {
            return; // Health stat not found
        }

        // Get current health value to preserve ratio
        EntityStatValue healthStat = statMap.get(healthStatIndex);
        if (healthStat == null) {
            return;
        }

        float currentMax = healthStat.getMax();
        float currentRatio = currentMax > 0 ? healthStat.get() / currentMax : 1.0f;

        // Create and apply health modifier
        StaticModifier healthMod = new StaticModifier(
            Modifier.ModifierTarget.MAX,
            StaticModifier.CalculationType.MULTIPLICATIVE,
            multiplier
        );

        statMap.putModifier(healthStatIndex, REALM_HEALTH_KEY, healthMod);

        // Restore health to same percentage of new max
        float newMax = healthStat.getMax();
        if (currentRatio >= 0.999f) {
            statMap.maximizeStatValue(healthStatIndex);
        } else {
            statMap.setStatValue(healthStatIndex, newMax * currentRatio);
        }
    }
}
