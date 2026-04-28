package io.github.larsonix.trailoforbis.combat.deathrecap;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.attributes.ComputedStats;
import io.github.larsonix.trailoforbis.combat.DamageBreakdown;
import io.github.larsonix.trailoforbis.combat.detection.DamageTypeClassifier;
import io.github.larsonix.trailoforbis.combat.resolution.CombatEntityResolver;
import io.github.larsonix.trailoforbis.mobs.classification.RPGMobClass;
import io.github.larsonix.trailoforbis.elemental.ElementType;
import io.github.larsonix.trailoforbis.elemental.ElementalStats;
import io.github.larsonix.trailoforbis.mobs.component.MobScalingComponent;

import javax.annotation.Nonnull;
import java.util.EnumMap;
import java.util.Map;
import javax.annotation.Nullable;
import java.util.logging.Level;

/**
 * Records combat damage for death recap system.
 *
 * <p>This class extracts death recap recording logic from RPGDamageSystem,
 * handling:
 * <ul>
 *   <li>Creating combat snapshots from damage breakdowns</li>
 *   <li>Extracting attacker info for display</li>
 *   <li>Recording snapshots to the death recap tracker</li>
 * </ul>
 */
public class DeathRecapRecorder {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final CombatEntityResolver entityResolver;
    private final DamageTypeClassifier classifier;
    private final DeathRecapTracker tracker;
    private final ComponentType<EntityStore, MobScalingComponent> mobScalingComponentType;
    private final LevelingProvider levelingProvider;

    /**
     * Provider interface for getting player levels.
     */
    @FunctionalInterface
    public interface LevelingProvider {
        int getLevel(@Nonnull java.util.UUID playerId);
    }

    /**
     * Creates a new DeathRecapRecorder.
     *
     * @param entityResolver The entity resolver for attacker lookups
     * @param classifier The damage type classifier
     * @param tracker The death recap tracker (may be null)
     * @param mobScalingComponentType The mob scaling component type
     * @param levelingProvider Provider for getting player levels
     */
    public DeathRecapRecorder(
        @Nonnull CombatEntityResolver entityResolver,
        @Nonnull DamageTypeClassifier classifier,
        @Nullable DeathRecapTracker tracker,
        @Nullable ComponentType<EntityStore, MobScalingComponent> mobScalingComponentType,
        @Nullable LevelingProvider levelingProvider
    ) {
        this.entityResolver = entityResolver;
        this.classifier = classifier;
        this.tracker = tracker;
        this.mobScalingComponentType = mobScalingComponentType;
        this.levelingProvider = levelingProvider;
    }

    /**
     * Helper record for attacker information.
     *
     * @param name The attacker's display name
     * @param type The attacker type ("player", "mob", "environment")
     * @param level The attacker's level
     * @param mobClass The mob's RPG classification (null for players/environment)
     */
    public record AttackerInfo(
        String name,
        String type,
        int level,
        @Nullable RPGMobClass mobClass
    ) {}

    /**
     * Records a combat snapshot using DamageBreakdown for death recap.
     *
     * @param index The entity index
     * @param archetypeChunk The archetype chunk
     * @param store The entity store
     * @param damage The damage event
     * @param breakdown The damage breakdown from calculator
     * @param baseDamage Original base damage
     * @param defenderStats Defender's stats
     * @param attackerStats Attacker's stats (for armor penetration)
     * @param defenderMaxHealth Defender's max health
     * @param defenderHealthBefore Defender's health before damage
     */
    public void recordDamage(
        int index,
        @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull Damage damage,
        @Nonnull DamageBreakdown breakdown,
        float baseDamage,
        @Nullable ComputedStats defenderStats,
        @Nullable ComputedStats attackerStats,
        float defenderMaxHealth,
        float defenderHealthBefore
    ) {
        if (tracker == null || !tracker.getConfig().isEnabled()) {
            return;
        }

        Ref<EntityStore> defenderRef = archetypeChunk.getReferenceTo(index);
        if (defenderRef == null || !defenderRef.isValid()) {
            return;
        }

        PlayerRef defenderPlayer = store.getComponent(defenderRef, PlayerRef.getComponentType());
        if (defenderPlayer == null) {
            return;
        }

        AttackerInfo attackerInfo = getAttackerInfo(store, damage);
        float defenderEvasion = defenderStats != null ? defenderStats.getEvasion() : 0f;
        float defenderArmor = defenderStats != null ? defenderStats.getArmor() : 0f;
        float armorPenetration = attackerStats != null ? attackerStats.getArmorPenetration() : 0f;

        // Extract defender's raw elemental resistances for death recap display
        Map<ElementType, Float> defenderRawResistances = extractRawResistances(defenderStats);

        // Extract attacker's elemental penetration for death recap display
        Map<ElementType, Float> attackerElemPenetration = extractPenetration(attackerStats);

        CombatSnapshot snapshot = CombatSnapshot.fromBreakdown(
            breakdown,
            attackerInfo.name(),
            attackerInfo.type(),
            attackerInfo.level(),
            attackerInfo.mobClass(),
            baseDamage,
            defenderMaxHealth,
            defenderHealthBefore,
            defenderEvasion,
            defenderArmor,
            armorPenetration,
            defenderRawResistances,
            attackerElemPenetration
        );

        tracker.recordDamage(defenderPlayer.getUuid(), snapshot);
    }

    /**
     * Records a combat snapshot with explicit attacker info (bypasses damage source resolution).
     *
     * <p>Used for spell damage where the attacker identity comes from config (spell display name)
     * rather than from the {@link Damage} source. The spell name appears as the "attacker" in
     * death recap, e.g., "Lightning Bolt — 45 Magic Damage [Shocked +25%]".
     *
     * @param index The entity index
     * @param archetypeChunk The archetype chunk
     * @param store The entity store
     * @param attackerInfo Pre-built attacker info (spell name as source)
     * @param breakdown The damage breakdown from calculator
     * @param baseDamage Original base damage
     * @param defenderStats Defender's stats
     * @param attackerStats Attacker's stats (null for spells without caster identity)
     * @param defenderMaxHealth Defender's max health
     * @param defenderHealthBefore Defender's health before damage
     */
    public void recordDamageWithAttacker(
        int index,
        @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull AttackerInfo attackerInfo,
        @Nonnull DamageBreakdown breakdown,
        float baseDamage,
        @Nullable ComputedStats defenderStats,
        @Nullable ComputedStats attackerStats,
        float defenderMaxHealth,
        float defenderHealthBefore
    ) {
        if (tracker == null || !tracker.getConfig().isEnabled()) {
            return;
        }

        Ref<EntityStore> defenderRef = archetypeChunk.getReferenceTo(index);
        if (defenderRef == null || !defenderRef.isValid()) {
            return;
        }

        PlayerRef defenderPlayer = store.getComponent(defenderRef, PlayerRef.getComponentType());
        if (defenderPlayer == null) {
            return;
        }

        float defenderEvasion = defenderStats != null ? defenderStats.getEvasion() : 0f;
        float defenderArmor = defenderStats != null ? defenderStats.getArmor() : 0f;
        float armorPenetration = attackerStats != null ? attackerStats.getArmorPenetration() : 0f;

        Map<ElementType, Float> defenderRawResistances = extractRawResistances(defenderStats);
        Map<ElementType, Float> attackerElemPenetration = extractPenetration(attackerStats);

        CombatSnapshot snapshot = CombatSnapshot.fromBreakdown(
            breakdown,
            attackerInfo.name(),
            attackerInfo.type(),
            attackerInfo.level(),
            attackerInfo.mobClass(),
            baseDamage,
            defenderMaxHealth,
            defenderHealthBefore,
            defenderEvasion,
            defenderArmor,
            armorPenetration,
            defenderRawResistances,
            attackerElemPenetration
        );

        tracker.recordDamage(defenderPlayer.getUuid(), snapshot);
    }

    /**
     * Gets attacker information for death recap display.
     *
     * @param store The entity store
     * @param damage The damage event
     * @return Information about the attacker
     */
    @Nonnull
    public AttackerInfo getAttackerInfo(@Nonnull Store<EntityStore> store, @Nonnull Damage damage) {
        // Check for environment damage
        if (!(damage.getSource() instanceof Damage.EntitySource entitySource)) {
            String causeName = classifier.formatDamageCause(DamageTypeClassifier.getDamageCause(damage));
            return new AttackerInfo(causeName, "environment", 0, null);
        }

        Ref<EntityStore> immediateRef = entitySource.getRef();
        if (immediateRef == null || !immediateRef.isValid()) {
            return new AttackerInfo("Unknown", "environment", 0, null);
        }

        Ref<EntityStore> attackerRef = entityResolver.resolveTrueAttacker(store, immediateRef);
        if (attackerRef == null) {
            return new AttackerInfo("Unknown", "environment", 0, null);
        }

        // Check if attacker is a player
        PlayerRef playerRef = store.getComponent(attackerRef, PlayerRef.getComponentType());
        if (playerRef != null) {
            // Get player level from leveling service
            int playerLevel = 1;
            if (levelingProvider != null) {
                playerLevel = levelingProvider.getLevel(playerRef.getUuid());
            }
            return new AttackerInfo(playerRef.getUsername(), "player", playerLevel, null);
        }

        // Check if attacker is a scaled mob
        try {
            if (mobScalingComponentType != null) {
                MobScalingComponent scaling = store.getComponent(attackerRef, mobScalingComponentType);
                if (scaling != null) {
                    String mobName = DeathRecapFormatter.formatMobName(
                        scaling.getRoleName() != null ? scaling.getRoleName() : "Unknown Mob"
                    );
                    return new AttackerInfo(
                        mobName,
                        "mob",
                        scaling.getMobLevel(),
                        scaling.getClassification()
                    );
                }
            }
        } catch (Exception e) {
            LOGGER.at(Level.FINE).log("Could not get mob scaling info for attacker: %s", e.getMessage());
        }

        // Fallback for unscaled entities
        return new AttackerInfo("Unknown Entity", "mob", 1, null);
    }

    /**
     * @return true if tracker is initialized and enabled
     */
    public boolean isAvailable() {
        return tracker != null && tracker.getConfig().isEnabled();
    }

    /**
     * Gets target information for combat log display.
     *
     * <p>This is similar to {@link #getAttackerInfo} but resolves information about
     * the entity being attacked (the target/defender) rather than the attacker.
     * Used for displaying damage dealt logs from the attacker's perspective.
     *
     * @param store The entity store
     * @param targetRef The target entity reference
     * @return Information about the target
     */
    @Nonnull
    public AttackerInfo getTargetInfo(
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> targetRef
    ) {
        if (targetRef == null || !targetRef.isValid()) {
            return new AttackerInfo("Unknown", "environment", 0, null);
        }

        // Check if target is a player
        PlayerRef playerRef = store.getComponent(targetRef, PlayerRef.getComponentType());
        if (playerRef != null) {
            // Get player level from leveling service
            int playerLevel = 1;
            if (levelingProvider != null) {
                playerLevel = levelingProvider.getLevel(playerRef.getUuid());
            }
            return new AttackerInfo(playerRef.getUsername(), "player", playerLevel, null);
        }

        // Check if target is a scaled mob
        try {
            if (mobScalingComponentType != null) {
                MobScalingComponent scaling = store.getComponent(targetRef, mobScalingComponentType);
                if (scaling != null) {
                    String mobName = DeathRecapFormatter.formatMobName(
                        scaling.getRoleName() != null ? scaling.getRoleName() : "Unknown Mob"
                    );
                    return new AttackerInfo(
                        mobName,
                        "mob",
                        scaling.getMobLevel(),
                        scaling.getClassification()
                    );
                }
            }
        } catch (Exception e) {
            LOGGER.at(Level.FINE).log("Could not get mob scaling info for target: %s", e.getMessage());
        }

        // Fallback for unscaled entities
        return new AttackerInfo("Unknown Entity", "mob", 1, null);
    }

    /**
     * Extracts raw elemental resistances from defender's stats for death recap display.
     *
     * @param stats The defender's computed stats (may be null)
     * @return Map of raw resistance values per element, or null if no stats
     */
    @Nullable
    private Map<ElementType, Float> extractRawResistances(@Nullable ComputedStats stats) {
        if (stats == null) {
            return null;
        }

        ElementalStats elemental = stats.getElemental();
        if (elemental == null) {
            return null;
        }

        EnumMap<ElementType, Float> result = new EnumMap<>(ElementType.class);
        for (ElementType type : ElementType.values()) {
            result.put(type, (float) elemental.getResistance(type));
        }
        return result;
    }

    /**
     * Extracts elemental penetration values from attacker's stats for death recap display.
     *
     * @param stats The attacker's computed stats (may be null)
     * @return Map of penetration values per element, or null if no stats
     */
    @Nullable
    private Map<ElementType, Float> extractPenetration(@Nullable ComputedStats stats) {
        if (stats == null) {
            return null;
        }

        ElementalStats elemental = stats.getElemental();
        if (elemental == null) {
            return null;
        }

        EnumMap<ElementType, Float> result = new EnumMap<>(ElementType.class);
        boolean hasAnyPenetration = false;
        for (ElementType type : ElementType.values()) {
            float pen = (float) elemental.getPenetration(type);
            result.put(type, pen);
            if (pen > 0) {
                hasAnyPenetration = true;
            }
        }

        // Only return the map if there's any penetration to show
        return hasAnyPenetration ? result : null;
    }
}
