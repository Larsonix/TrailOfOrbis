package io.github.larsonix.trailoforbis.mobs.systems;

import com.hypixel.hytale.logger.HytaleLogger;
import java.util.logging.Level;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.HolderSystem;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.EntityStatType;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.Modifier;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.StaticModifier;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.movement.controllers.MotionController;
import com.hypixel.hytale.server.npc.role.Role;
import com.hypixel.hytale.server.npc.systems.BalancingInitialisationSystem;
import com.hypixel.hytale.server.core.entity.nameplate.Nameplate;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.modules.entity.component.DisplayNameComponent;
import io.github.larsonix.trailoforbis.TrailOfOrbis;
import io.github.larsonix.trailoforbis.combat.format.CombatFormatConstants;
import io.github.larsonix.trailoforbis.mobs.infobar.MobInfoFormatter;
import io.github.larsonix.trailoforbis.mobs.MobScalingConfig;
import io.github.larsonix.trailoforbis.mobs.MobScalingManager;
import io.github.larsonix.trailoforbis.mobs.calculator.DistanceBonusCalculator;
import io.github.larsonix.trailoforbis.mobs.calculator.PlayerLevelCalculator;
import io.github.larsonix.trailoforbis.mobs.classification.MobClassificationConfig;
import io.github.larsonix.trailoforbis.mobs.classification.MobClassificationService;
import io.github.larsonix.trailoforbis.mobs.classification.RPGMobClass;
import io.github.larsonix.trailoforbis.mobs.component.MobScalingComponent;
import io.github.larsonix.trailoforbis.elemental.ElementalStats;
import io.github.larsonix.trailoforbis.elemental.ElementType;
import io.github.larsonix.trailoforbis.mobs.model.MobStats;
import io.github.larsonix.trailoforbis.mobs.modifiers.MobModifierApplier;
import io.github.larsonix.trailoforbis.mobs.modifiers.MobModifierComponent;
import io.github.larsonix.trailoforbis.mobs.modifiers.MobModifierManager;
import io.github.larsonix.trailoforbis.mobs.modifiers.ModifierType;
import io.github.larsonix.trailoforbis.mobs.spawn.component.RPGSpawnedMarker;
import io.github.larsonix.trailoforbis.mobs.spawn.manager.RPGSpawnManager;
import io.github.larsonix.trailoforbis.mobs.speed.MobSpeedEffectManager;
import io.github.larsonix.trailoforbis.mobs.stats.MobStatFactory;
import io.github.larsonix.trailoforbis.maps.components.RealmMobComponent;
import io.github.larsonix.trailoforbis.maps.instance.RealmInstance;
import io.github.larsonix.trailoforbis.maps.core.RealmMapData;
import io.github.larsonix.trailoforbis.maps.modifiers.RealmModifier;
import io.github.larsonix.trailoforbis.maps.RealmsManager;

import com.hypixel.hytale.builtin.mounts.NPCMountComponent;

import javax.annotation.Nonnull;
import java.util.List;
import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.util.Optional;
import java.util.Set;

/**
 * ECS HolderSystem for applying mob scaling at spawn time.
 *
 * <p>This system intercepts mob spawns via {@code onEntityAdd} and:
 * <ol>
 *   <li>Classifies the mob (Monster, Beast, Livestock, etc.)</li>
 *   <li>Calculates distance-based bonus pool</li>
 *   <li>Calculates player-based level</li>
 *   <li>Randomly distributes bonus stats</li>
 *   <li>Attaches {@link MobScalingComponent} with calculated stats</li>
 *   <li>Applies stat modifiers to {@link EntityStatMap}</li>
 * </ol>
 *
 * <p><b>CRITICAL</b>: This system must run AFTER {@link BalancingInitialisationSystem}
 * so that vanilla health is set first, then we can apply our multipliers.
 */
public class MobScalingSystem extends HolderSystem<EntityStore> {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();


    /** Unique modifier key to identify our scaling (avoids conflicts) */
    private static final String RPG_HEALTH_KEY = "RPG_HEALTH_SCALING";

    /**
     * Cached reflection field for clearing vanilla health regeneration on scaled mobs.
     *
     * <p>Vanilla Health.json defines a Percentage-type NPC regen (5% of max HP every 0.5s
     * after 15s out of combat). Because we apply a MULTIPLICATIVE modifier to max HP,
     * this percentage regen scales proportionally — a 2000 HP mob regens 200 HP/sec.
     *
     * <p>We clear {@code EntityStatValue.regeneratingValues} per-entity at spawn time
     * so our {@code MobRegenerationSystem} is the sole source of mob health regen.
     * This preserves vanilla regen for players (Creative mode instant heal) and
     * unscaled NPCs.
     */
    @Nullable
    private static volatile Field regeneratingValuesField;

    /** Set to true if reflection lookup fails — prevents repeated retry + log spam. */
    private static volatile boolean regenerationFieldFailed = false;
    
    // Plugin reference for dynamic access to MobScalingManager
    private final TrailOfOrbis plugin;

    // Component types (cached for performance)
    private final ComponentType<EntityStore, NPCEntity> npcType;
    private final ComponentType<EntityStore, EntityStatMap> statMapType;
    private final ComponentType<EntityStore, TransformComponent> transformType;

    // Cached stat indices
    private int healthStatIndex = -1;

    public MobScalingSystem(@Nonnull TrailOfOrbis plugin) {
        this.plugin = plugin;
        this.npcType = NPCEntity.getComponentType();
        this.statMapType = EntityStatMap.getComponentType();
        this.transformType = TransformComponent.getComponentType();
    }

    @Override
    @Nonnull
    public Query<EntityStore> getQuery() {
        return Archetype.of(npcType, statMapType, transformType);
    }

    @Override
    @Nonnull
    public Set<Dependency<EntityStore>> getDependencies() {
        return Set.of(
            new SystemDependency<>(Order.AFTER, BalancingInitialisationSystem.class)
        );
    }

    @Override
    public void onEntityRemoved(@Nonnull Holder<EntityStore> holder, @Nonnull RemoveReason reason,
                                @Nonnull Store<EntityStore> store) {
        // No cleanup needed
    }

    @Override
    public void onEntityAdd(@Nonnull Holder<EntityStore> holder, @Nonnull AddReason reason,
                            @Nonnull Store<EntityStore> store) {
        // Skip processing during world shutdown to prevent race conditions with entity removal
        com.hypixel.hytale.server.core.universe.world.World world = store.getExternalData().getWorld();
        if (world == null || !world.isAlive()) {
            return;
        }

        // Get manager dynamically
        MobScalingManager manager = plugin.getMobScalingManager();
        if (manager == null || !manager.isInitialized()) return;

        MobScalingConfig config = manager.getConfig();
        if (!config.isEnabled()) return;

        // Only process SPAWN and LOAD reasons
        if (reason != AddReason.SPAWN && reason != AddReason.LOAD) return;

        // Skip mobs that were dead when loaded (health <= 0)
        // This prevents scaling system from processing already-dead entities
        if (reason == AddReason.LOAD) {
            EntityStatMap statMap = holder.getComponent(statMapType);
            if (statMap != null) {
                if (healthStatIndex < 0) {
                    healthStatIndex = EntityStatType.getAssetMap().getIndex("Health");
                }
                EntityStatValue healthStat = statMap.get(healthStatIndex);
                if (healthStat != null && healthStat.get() <= 0) {
                    LOGGER.atFine().log("[MobScaling] Skipping dead mob on LOAD (health=%.1f)", healthStat.get());
                    return;
                }
            }
        }

        // Check for existing scaling component (persisted from save)
        ComponentType<EntityStore, MobScalingComponent> scalingType = plugin.getMobScalingComponentType();
        if (scalingType == null) return;

        MobScalingComponent existingScaling = holder.getComponent(scalingType);
        if (existingScaling != null) {
            if (reason == AddReason.SPAWN) {
                // SPAWN with existing component = double-fire guard — skip
                LOGGER.atFine().log("[MobScaling] Skipping mob - already scaled on SPAWN (Lv%d)",
                    existingScaling.getMobLevel());
                return;
            }

            // LOAD: Recalculate stats and re-apply health modifier.
            // MobStats is NOT serialized (codec comment line 40), and the health formula
            // may have changed since the mob was saved. Vanilla regen is also re-initialized
            // by EntityStatMap.CODEC.afterDecode → synchronizeAsset → initializeRegenerating,
            // so we must clear it again.
            recalculateLoadedMob(holder, existingScaling, store, manager);
            return;
        }

        // Get NPC component
        NPCEntity npc = holder.getComponent(npcType);
        if (npc == null || npc.getRole() == null) return;

        // Skip tamed/mounted entities — NPCMountComponent is present on horses that have
        // been tamed. Its ownerPlayerRef is NOT serialized, so on LOAD Hytale's
        // NPCMountSystems.OnAdd may remove it, but if our system runs first we catch it here.
        try {
            if (holder.getComponent(NPCMountComponent.getComponentType()) != null) {
                LOGGER.atFine().log("[MobScaling] Skipping mounted/tamed entity %s", npc.getRoleName());
                return;
            }
        } catch (Exception e) {
            // MountPlugin not initialized — fall through to normal processing
        }

        // Log that we're processing this mob (FINE level - per-mob diagnostic)
        LOGGER.atFine().log("[MobScaling] Processing %s (no existing MobScalingComponent)", npc.getRoleName());

        // Check for realm mob - use realm level instead of player-proximity-based level
        // Use plugin's component type getter for reliability (same as RealmEntitySpawner)
        ComponentType<EntityStore, RealmMobComponent> realmMobType = plugin.getRealmMobComponentType();
        RealmMobComponent realmMob = (realmMobType != null) ? holder.getComponent(realmMobType) : null;

        // Debug: Log realm mob detection status for all NPCs
        if (realmMobType == null) {
            LOGGER.atFine().log("[MobScaling] RealmMobComponent type is NULL for %s", npc.getRoleName());
        } else if (realmMob == null) {
            LOGGER.atFine().log("[MobScaling] No RealmMobComponent found for %s", npc.getRoleName());
        } else {
            LOGGER.atFine().log("[MobScaling] Found RealmMobComponent for %s: realmId=%s",
                npc.getRoleName(), realmMob.getRealmId());
        }

        if (realmMob != null && realmMob.getRealmId() != null) {
            // This is a realm mob - use realm-based scaling (FINE level - per-mob diagnostic)
            LOGGER.atFine().log("[MobScaling] Detected realm mob %s in realm %s, using realm-based scaling",
                npc.getRoleName(), realmMob.getRealmId().toString().substring(0, 8));
            processRealmMob(holder, npc, realmMob, scalingType, reason, store, manager);
            return;
        }

        // Check for AI-spawned minions in realm worlds (e.g., wolves summoned by Trork orcs)
        // These NPCs appear in a realm world without a RealmMobComponent — tag them as minions
        if (realmMob == null && realmMobType != null) {
            RealmsManager rm = plugin.getRealmsManager();
            if (rm != null) {
                Optional<RealmInstance> realmOpt = rm.getRealmByWorld(world);
                if (realmOpt.isPresent()) {
                    RealmInstance realm = realmOpt.get();
                    RealmMapData mapData = realm.getMapData();

                    // Calculate minion level as a fraction of realm level
                    int minionLevel = Math.max(1, Math.round(mapData.level() * rm.getMinionLevelFraction()));

                    RealmMobComponent minionComp = new RealmMobComponent();
                    minionComp.setRealmId(realm.getRealmId());
                    minionComp.setRealmLevel(minionLevel);
                    minionComp.setCountsForCompletion(false);
                    minionComp.setElite(false);
                    minionComp.setReinforcement(false);
                    minionComp.setWaveNumber(0);

                    // Apply realm modifiers (same logic as RealmEntitySpawner)
                    for (RealmModifier mod : mapData.modifiers()) {
                        switch (mod.type()) {
                            case MONSTER_HEALTH -> minionComp.setHealthMultiplier(minionComp.getHealthMultiplier() + mod.value() / 100f);
                            case MONSTER_DAMAGE -> minionComp.setDamageMultiplier(minionComp.getDamageMultiplier() + mod.value() / 100f);
                            case MONSTER_SPEED -> minionComp.setSpeedMultiplier(minionComp.getSpeedMultiplier() + mod.value() / 100f);
                            case MONSTER_ATTACK_SPEED -> minionComp.setAttackSpeedMultiplier(minionComp.getAttackSpeedMultiplier() + mod.value() / 100f);
                            default -> { }
                        }
                    }

                    holder.addComponent(realmMobType, minionComp);

                    LOGGER.atFine().log("[MobScaling] Tagged AI-spawned minion %s in realm %s (level %d → minion level %d)",
                        npc.getRoleName(), realm.getRealmId().toString().substring(0, 8), mapData.level(), minionLevel);

                    processRealmMob(holder, npc, minionComp, scalingType, reason, store, manager);
                    return;
                }
            }
        }

        // 1. CLASSIFY MOB
        MobClassificationService classificationService = manager.getClassificationService();
        if (classificationService == null) return;

        RPGMobClass classification = classificationService.classify(manager.createContext(npc));

        // Get mob position (needed for level calculation and elite roll)
        TransformComponent transform = holder.getComponent(transformType);
        if (transform == null) return;
        Vector3d position = transform.getPosition();

        // Get calculators for level calculation
        DistanceBonusCalculator distanceCalculator = manager.getDistanceCalculator();
        PlayerLevelCalculator playerLevelCalculator = manager.getPlayerLevelCalculator();
        if (distanceCalculator == null || playerLevelCalculator == null) return;

        // Check Safe Zone early (using distance from spawn, not origin)
        double distFromSpawn = DistanceBonusCalculator.calculateDistanceFromSpawn(position.x, position.z, world);
        if (distanceCalculator.isInSafeZone(distFromSpawn)) return;

        // Calculate mob level for elite roll
        int mobLevel = playerLevelCalculator.calculateEffectiveLevel(position, store);
        String roleName = npc.getRoleName();

        // 2. ROLL FOR ELITE
        // After removing deterministic elite detection, the only way to be ELITE is:
        // - Through this random roll (for HOSTILE/MINOR mobs)
        // - Or being classified as BOSS (which stays BOSS)
        MobScalingConfig.EliteChanceConfig eliteConfig = config.getEliteChance();
        if (eliteConfig.isEnabled() && (classification == RPGMobClass.HOSTILE || classification == RPGMobClass.MINOR)) {
            double eliteChance = eliteConfig.calculateChance(mobLevel);
            if (java.util.concurrent.ThreadLocalRandom.current().nextDouble() < eliteChance) {
                classification = RPGMobClass.ELITE;
                // Per-mob event - use FINE level to avoid log spam
                LOGGER.atFine().log("[MobScaling] %s rolled ELITE at spawn (Lv%d, chance=%.1f%%)",
                    roleName, mobLevel, eliteChance * 100);
            }
        }

        // 3. CALCULATE BONUS POOL (level-based, same formula as realm mobs)
        double bonusPool = mobLevel * config.getDistanceScaling().getPoolPerLevel();

        // 4. APPLY CLASS MULTIPLIERS
        MobClassificationConfig classConfig = classificationService.getConfig();
        double statMultiplier = classConfig.getStatMultiplier(classification);

        // DEBUG: Log classification and multiplier for special mobs (FINE level to avoid log spam)
        if (statMultiplier != 1.0) {
            LOGGER.at(Level.FINE).log("[MobScaling] %s spawned as %s - stat multiplier: %.1fx (Lv%d, pool=%.0f)",
                roleName, classification, statMultiplier, mobLevel, bonusPool);
        }

        // 5. GENERATE STATS via Template + Noise factory
        MobStatFactory statFactory = manager.getStatFactory();
        if (statFactory == null) return;

        long seed = System.nanoTime() ^ position.hashCode();

        // Resolve NPC groups and element for archetype/resistance resolution
        var npcGroups = getNpcGroups(roleName, manager);
        var elementResolver = manager.getElementResolver();
        var detectedElement = elementResolver != null ? elementResolver.resolve(roleName, npcGroups) : null;

        MobStats stats = statFactory.generate(mobLevel, bonusPool, roleName, npcGroups, detectedElement, seed);

        // Apply classification multiplier (ELITE/BOSS scale HP/damage/armor)
        if (statMultiplier != 1.0) {
            stats = stats.withMultiplier(statMultiplier);
        }

        // Apply late-game scaling (accelerating HP/damage/armor bonus above threshold level)
        MobScalingConfig.LateGameScalingConfig lateGame = config.getLateGameScaling();
        if (lateGame.isEnabled() && mobLevel > lateGame.getThreshold()) {
            stats = stats.withLateGameScaling(
                lateGame.calculateHpMultiplier(mobLevel),
                lateGame.calculateDamageMultiplier(mobLevel),
                lateGame.calculateArmorMultiplier(mobLevel));
        }

        // 5.5 ROLL AND APPLY MOB MODIFIERS
        List<ModifierType> modifiers = List.of();
        MobModifierManager modManager = plugin.getMobModifierManager();
        if (modManager != null && modManager.isEnabled()
                && (classification == RPGMobClass.ELITE || classification == RPGMobClass.BOSS)) {
            String tier = classification == RPGMobClass.BOSS ? "boss" : "elite";
            // Boss that rolled elite chance → Elite Boss (3 modifiers)
            // This is handled by checking if a BOSS also rolled through the elite chance above
            modifiers = modManager.getRoller().roll(mobLevel, tier, seed);

            if (!modifiers.isEmpty()) {
                stats = modManager.getApplier().applyStats(stats, modifiers);
                LOGGER.at(Level.FINE).log("[MobModifier] %s rolled %d modifiers: %s",
                    roleName, modifiers.size(), modifiers);
            }
        }

        // DEBUG: Log generated stats for special mobs (FINE level to avoid log spam)
        if (statMultiplier != 1.0) {
            LOGGER.at(Level.FINE).log("[MobScaling] %s stats: HP=%.0f, DMG=%.1f, Armor=%.0f, Crit=%.0f%%/%.0f%%",
                classification, stats.maxHealth(), stats.physicalDamage(), stats.armor(),
                stats.criticalChance(), stats.criticalMultiplier());
        }

        // 6. ATTACH COMPONENTS
        MobScalingComponent scaling = new MobScalingComponent();
        scaling.setMobLevel(mobLevel);
        scaling.setDistanceLevel(distanceCalculator.estimateLevelFromDistance(distFromSpawn));
        scaling.setDistanceBonus(bonusPool);
        scaling.setClassification(classification);
        scaling.setRoleName(roleName); // For death recap display
        scaling.setStats(stats);
        // Store vanilla HP for weighted formula calculation
        scaling.setVanillaHP(npc.getRole().getInitialMaxHealth());

        // Attach component to entity (scalingType already verified at start of method)
        holder.addComponent(scalingType, scaling);

        // Attach modifier component if modifiers were rolled
        if (!modifiers.isEmpty()) {
            MobModifierComponent modComp = new MobModifierComponent();
            modComp.setModifiers(modifiers);
            holder.addComponent(MobModifierComponent.getComponentType(), modComp);
        }

        // Store nameplate text for deferred activation. MobNameplateActivationSystem
        // populates the text when a player gets within range. We add an empty Nameplate
        // component immediately to suppress the client's default health bar rendering.
        if (classification != RPGMobClass.PASSIVE) {
            String nameplateText;
            if (!modifiers.isEmpty() && modManager != null) {
                nameplateText = MobModifierApplier.formatNameplate(
                    mobLevel, classification, modifiers, modManager.getConfig());
            } else {
                nameplateText = MobInfoFormatter.formatPlainText(
                    mobLevel, 0, classification, null);
            }
            scaling.setNameplateText(nameplateText);
            holder.addComponent(Nameplate.getComponentType(), new Nameplate(""));
        }

        // Set DisplayNameComponent for death screen / kill feed display
        // Without this, entity-killed death messages show "unknown" instead of the mob name
        // Use putComponent — some vanilla NPCs already have a DisplayNameComponent
        String formattedName = CombatFormatConstants.formatMobName(roleName);
        holder.putComponent(DisplayNameComponent.getComponentType(),
            new DisplayNameComponent(Message.raw(formattedName)));

        // Apply modifier visuals (tint, VFX, scale) BEFORE stat modifiers
        if (!modifiers.isEmpty() && modManager != null) {
            String tier = classification == RPGMobClass.BOSS ? "boss" : "elite";
            Ref<EntityStore> entityRef = npc.getReference();
            if (entityRef != null && entityRef.isValid()) {
                modManager.getApplier().applyVisuals(holder, modifiers, tier, entityRef, store);
            }
        }

        // Apply stat modifiers to EntityStatMap
        applyStatModifiers(holder, scaling, stats, reason, manager);

        // Apply speed effect if mob has speed modifier
        applySpeedEffect(holder, npc, stats, store, manager);

        // Apply knockback resistance
        applyKnockbackResistance(npc, stats);

        // 8. SPAWN MULTIPLIER (Class-based and level-based spawn rate modification)
        // Skip for PASSIVE mobs — no additional spawns for ambient creatures
        if (reason == AddReason.SPAWN && classification != RPGMobClass.PASSIVE) {
            applySpawnMultiplier(holder, npc, classification, position, store, manager);
        }
    }

    /**
     * Applies spawn multiplier using the new RPGSpawnManager system.
     *
     * <p>This replaces the old AdditionalMobSpawner/SpawnMultiplierCalculator approach
     * with a cleaner component-based system that:
     * <ul>
     *   <li>Uses RPGSpawnedMarker component for loop prevention</li>
     *   <li>Supports per-class spawn multipliers</li>
     *   <li>Supports level-based spawn scaling</li>
     * </ul>
     */
    private void applySpawnMultiplier(
            @Nonnull Holder<EntityStore> holder,
            @Nonnull NPCEntity originalNpc,
            @Nonnull RPGMobClass classification,
            @Nonnull Vector3d position,
            @Nonnull Store<EntityStore> store,
            @Nonnull MobScalingManager manager) {

        RPGSpawnManager spawnManager = manager.getRPGSpawnManager();
        if (spawnManager == null || !spawnManager.isEnabled()) {
            return;
        }

        // Check if this is an RPG-spawned mob (prevents infinite loops)
        ComponentType<EntityStore, RPGSpawnedMarker> markerType = plugin.getRPGSpawnedMarkerType();
        if (markerType != null && holder.getComponent(markerType) != null) {
            return;
        }

        // Calculate combined spawn multiplier (class-based * level-based)
        double multiplier = spawnManager.calculateSpawnMultiplier(originalNpc, classification, position, store);

        // Determine additional spawns using probabilistic rounding
        int additionalCount = spawnManager.getAdditionalSpawnCount(multiplier);

        if (additionalCount > 0) {
            spawnManager.spawnAdditional(originalNpc, position, additionalCount, store);
        }
    }

    /**
     * Processes a realm mob using realm-based level instead of player proximity.
     *
     * <p>Realm mobs have their level determined by the realm's map level, not by
     * nearby player levels. This ensures consistent difficulty within a realm.
     * The realm level is stored directly in {@link RealmMobComponent} at spawn time.
     *
     * @param holder The entity holder
     * @param npc The NPC entity
     * @param realmMob The realm mob component (contains realmLevel)
     * @param scalingType The MobScalingComponent type
     * @param reason The add reason (SPAWN or LOAD)
     * @param store The entity store
     * @param manager The mob scaling manager
     */
    private void processRealmMob(
            @Nonnull Holder<EntityStore> holder,
            @Nonnull NPCEntity npc,
            @Nonnull RealmMobComponent realmMob,
            @Nonnull ComponentType<EntityStore, MobScalingComponent> scalingType,
            @Nonnull AddReason reason,
            @Nonnull Store<EntityStore> store,
            @Nonnull MobScalingManager manager) {

        // Get realm level directly from the component (set during spawn by RealmEntitySpawner)
        int realmLevel = realmMob.getRealmLevel();
        if (realmLevel <= 0) {
            LOGGER.atWarning().log("RealmMobComponent has invalid level %d for %s, using fallback 1",
                realmLevel, npc.getRoleName());
            realmLevel = 1;
        }

        // Per-mob diagnostic - use FINE level
        LOGGER.atFine().log("[MobScaling] Processing realm mob %s: level=%d, realm=%s",
            npc.getRoleName(), realmLevel, realmMob.getRealmId().toString().substring(0, 8));

        // 1. CLASSIFY MOB — use realm spawner's pool classification, NOT entity discovery
        // Entity discovery patterns (e.g., "Golem_*" → BOSS) are for overworld only.
        // Realm mobs get their classification from the pool they were drawn from in realm-mobs.yml.
        RPGMobClass classification;
        if (realmMob.isBoss()) {
            classification = RPGMobClass.BOSS;
        } else if (realmMob.isElite()) {
            classification = RPGMobClass.ELITE;
        } else {
            classification = RPGMobClass.HOSTILE;
        }

        // 2. CALCULATE BONUS POOL (level-based, shared formula with overworld)
        double bonusPool = realmLevel * manager.getConfig().getDistanceScaling().getPoolPerLevel();

        // Apply class multipliers
        MobClassificationService classificationService = manager.getClassificationService();
        if (classificationService == null) return;
        MobClassificationConfig classConfig = classificationService.getConfig();
        double statMultiplier = classConfig.getStatMultiplier(classification);
        String roleName = npc.getRoleName();

        // Realm map modifiers (health/damage/speed) are applied AFTER by RealmModifierSystem
        // as the final multiplier layer. Only classification multiplier goes into the stat pool.
        double combinedStatMultiplier = statMultiplier;

        LOGGER.at(Level.FINE).log("[MobScaling] Realm mob %s: Lv%d, class=%s, statMult=%.2fx",
            roleName, realmLevel, classification, statMultiplier);

        // 3. GENERATE STATS via Template + Noise factory
        MobStatFactory statFactory = manager.getStatFactory();
        if (statFactory == null) return;

        long seed = System.nanoTime() ^ holder.hashCode();

        // Resolve NPC groups and element for archetype/resistance resolution
        var npcGroups = getNpcGroups(roleName, manager);
        var elementResolver = manager.getElementResolver();
        var detectedElement = elementResolver != null ? elementResolver.resolve(roleName, npcGroups) : null;

        MobStats stats = statFactory.generate(realmLevel, bonusPool, roleName, npcGroups, detectedElement, seed);

        // Apply classification multiplier (ELITE/BOSS)
        if (combinedStatMultiplier != 1.0) {
            stats = stats.withMultiplier(combinedStatMultiplier);
        }

        // Apply late-game scaling (accelerating HP/damage/armor bonus above threshold level)
        MobScalingConfig.LateGameScalingConfig lateGameRealm = manager.getConfig().getLateGameScaling();
        if (lateGameRealm.isEnabled() && realmLevel > lateGameRealm.getThreshold()) {
            stats = stats.withLateGameScaling(
                lateGameRealm.calculateHpMultiplier(realmLevel),
                lateGameRealm.calculateDamageMultiplier(realmLevel),
                lateGameRealm.calculateArmorMultiplier(realmLevel));
        }

        // 3.5 ROLL AND APPLY MOB MODIFIERS (same system for realm and overworld)
        List<ModifierType> modifiers = List.of();
        MobModifierManager modManager = plugin.getMobModifierManager();
        if (modManager != null && modManager.isEnabled()
                && (classification == RPGMobClass.ELITE || classification == RPGMobClass.BOSS)) {
            String modTier = classification == RPGMobClass.BOSS ? "boss" : "elite";
            modifiers = modManager.getRoller().roll(realmLevel, modTier, seed);
            if (!modifiers.isEmpty()) {
                stats = modManager.getApplier().applyStats(stats, modifiers);
                LOGGER.at(Level.FINE).log("[MobModifier] Realm mob %s rolled %d modifiers: %s",
                    roleName, modifiers.size(), modifiers);
            }
        }

        // 4. ATTACH COMPONENTS
        MobScalingComponent scaling = new MobScalingComponent();
        scaling.setMobLevel(realmLevel);
        scaling.setDistanceLevel(0); // Realm mobs get no distance XP bonus
        scaling.setDistanceBonus(bonusPool);
        scaling.setClassification(classification);
        scaling.setRoleName(roleName);
        scaling.setStats(stats);
        // Store vanilla HP for weighted formula calculation
        scaling.setVanillaHP(npc.getRole().getInitialMaxHealth());

        holder.addComponent(scalingType, scaling);

        // Attach modifier component if modifiers were rolled
        if (!modifiers.isEmpty()) {
            MobModifierComponent modComp = new MobModifierComponent();
            modComp.setModifiers(modifiers);
            holder.addComponent(MobModifierComponent.getComponentType(), modComp);
        }

        // Add empty Nameplate to suppress the client's default health bar rendering.
        // Only populate nameplate TEXT for elite/boss mobs — regular hostile realm mobs
        // don't need level text since the realm level is already known from the map.
        // This reduces screen clutter while preserving elite/boss visual identity.
        if (classification != RPGMobClass.PASSIVE) {
            if (classification == RPGMobClass.ELITE || classification == RPGMobClass.BOSS) {
                String nameplateText;
                if (!modifiers.isEmpty() && modManager != null) {
                    nameplateText = MobModifierApplier.formatNameplate(
                        realmLevel, classification, modifiers, modManager.getConfig());
                } else {
                    nameplateText = MobInfoFormatter.formatPlainText(
                        realmLevel, 0, classification, null);
                }
                scaling.setNameplateText(nameplateText);
            }
            holder.addComponent(Nameplate.getComponentType(), new Nameplate(""));
        }

        // Set DisplayNameComponent for death screen / kill feed display
        // Use putComponent — some vanilla NPCs already have a DisplayNameComponent
        String formattedName = CombatFormatConstants.formatMobName(roleName);
        holder.putComponent(DisplayNameComponent.getComponentType(),
            new DisplayNameComponent(Message.raw(formattedName)));

        // Apply modifier visuals (tint, VFX, scale)
        if (!modifiers.isEmpty() && modManager != null) {
            String modTier = classification == RPGMobClass.BOSS ? "boss" : "elite";
            Ref<EntityStore> entityRef = npc.getReference();
            if (entityRef != null && entityRef.isValid()) {
                modManager.getApplier().applyVisuals(holder, modifiers, modTier, entityRef, store);
            }
        }

        // 5. APPLY STAT MODIFIERS
        applyStatModifiers(holder, scaling, stats, reason, manager);
        applySpeedEffect(holder, npc, stats, store, manager);
        applyKnockbackResistance(npc, stats);

        LOGGER.at(Level.FINE).log("[MobScaling] Realm mob %s scaled: Lv%d, HP=%.0f, DMG=%.1f",
            roleName, realmLevel, stats.maxHealth(), stats.physicalDamage());
    }

    /**
     * Recalculates stats and re-applies health modifier for a mob loaded from save.
     *
     * <p>Called when {@code onEntityAdd} fires with {@code AddReason.LOAD} and the mob
     * already has a persisted {@link MobScalingComponent}. This is necessary because:
     * <ol>
     *   <li>{@code MobStats} is not serialized — defaults to {@link MobStats#UNSCALED} on load</li>
     *   <li>The health formula may have changed since the mob was saved, leaving the
     *       old {@code RPG_HEALTH_SCALING} modifier with a stale value</li>
     *   <li>Vanilla health regen is re-initialized by {@code EntityStatMap.CODEC.afterDecode}
     *       → {@code synchronizeAsset} → {@code initializeRegenerating}, so must be cleared again</li>
     * </ol>
     *
     * <p>Uses the persisted {@code mobLevel}, {@code distanceBonus}, and {@code classification}
     * to regenerate stats. Health percentage ratio is preserved across the recalculation.
     *
     * @param holder   The entity holder
     * @param scaling  The persisted MobScalingComponent
     * @param store    The entity store
     * @param manager  The MobScalingManager
     */
    private void recalculateLoadedMob(
            @Nonnull Holder<EntityStore> holder,
            @Nonnull MobScalingComponent scaling,
            @Nonnull Store<EntityStore> store,
            @Nonnull MobScalingManager manager) {

        // Regenerate MobStats from persisted inputs via Template + Noise factory
        MobStatFactory statFactory = manager.getStatFactory();
        if (statFactory == null) return;

        int mobLevel = scaling.getMobLevel();
        double distanceBonus = scaling.getDistanceBonus();
        RPGMobClass classification = scaling.getClassification();
        String roleName = scaling.getRoleName();

        // Refresh vanillaHP from current Role — handles taming/role reset.
        // NPCMountSystems.OnAdd may reset the Role on LOAD (ownerPlayerRef not serialized),
        // making the stored vanillaHP stale. Reading from the current Role ensures the
        // ADDITIVE health offset is calculated against the correct base.
        NPCEntity npcForHP = holder.getComponent(npcType);
        if (npcForHP != null && npcForHP.getRole() != null) {
            int currentVanillaHP = npcForHP.getRole().getInitialMaxHealth();
            if (currentVanillaHP != scaling.getVanillaHP() && currentVanillaHP > 0) {
                LOGGER.atFine().log("[MobScaling] VanillaHP updated: %d -> %d (role change) for %s",
                    scaling.getVanillaHP(), currentVanillaHP, roleName);
                scaling.setVanillaHP(currentVanillaHP);
            }
        }

        MobClassificationConfig classConfig = manager.getClassificationService().getConfig();
        double statMultiplier = classConfig.getStatMultiplier(classification);

        long seed = System.nanoTime() ^ holder.hashCode();

        var npcGroups = getNpcGroups(roleName, manager);
        var elementResolver = manager.getElementResolver();
        var detectedElement = elementResolver != null ? elementResolver.resolve(roleName, npcGroups) : null;

        MobStats stats = statFactory.generate(mobLevel, distanceBonus, roleName, npcGroups, detectedElement, seed);
        if (statMultiplier != 1.0) {
            stats = stats.withMultiplier(statMultiplier);
        }

        // Apply late-game scaling (accelerating HP/damage/armor bonus above threshold level)
        MobScalingConfig.LateGameScalingConfig lateGameLoad = manager.getConfig().getLateGameScaling();
        if (lateGameLoad.isEnabled() && mobLevel > lateGameLoad.getThreshold()) {
            stats = stats.withLateGameScaling(
                lateGameLoad.calculateHpMultiplier(mobLevel),
                lateGameLoad.calculateDamageMultiplier(mobLevel),
                lateGameLoad.calculateArmorMultiplier(mobLevel));
        }

        // Re-apply modifier stat bonuses from persisted MobModifierComponent
        MobModifierComponent modComp = holder.getComponent(MobModifierComponent.getComponentType());
        if (modComp != null) {
            modComp.resolveModifiers();
            List<ModifierType> modifiers = modComp.getModifiers();
            if (!modifiers.isEmpty()) {
                MobModifierManager modManager = plugin.getMobModifierManager();
                if (modManager != null && modManager.isEnabled()) {
                    stats = modManager.getApplier().applyStats(stats, modifiers);
                    // Re-apply visuals (effects don't persist across save/load)
                    NPCEntity npcForVisuals = holder.getComponent(npcType);
                    if (npcForVisuals != null) {
                        Ref<EntityStore> entityRef = npcForVisuals.getReference();
                        if (entityRef != null && entityRef.isValid()) {
                            String tier = classification == RPGMobClass.BOSS ? "boss" : "elite";
                            modManager.getApplier().applyVisuals(holder, modifiers, tier, entityRef, store);
                        }
                    }
                }
            }
        }

        scaling.setStats(stats);

        // Regenerate nameplate text from recalculated data
        if (classification != RPGMobClass.PASSIVE) {
            String nameplateText;
            List<ModifierType> modifiers = modComp != null ? modComp.getModifiers() : List.of();
            MobModifierManager modManager = plugin.getMobModifierManager();
            if (!modifiers.isEmpty() && modManager != null && modManager.isEnabled()) {
                nameplateText = MobModifierApplier.formatNameplate(
                    mobLevel, classification, modifiers, modManager.getConfig());
            } else {
                nameplateText = MobInfoFormatter.formatPlainText(
                    mobLevel, 0, classification, null);
            }
            scaling.setNameplateText(nameplateText);

            // Ensure Nameplate component exists (old saves may not have one persisted)
            if (holder.getComponent(Nameplate.getComponentType()) == null) {
                holder.addComponent(Nameplate.getComponentType(), new Nameplate(""));
            }
        }

        // Re-apply health modifier with current formula (putModifier is idempotent)
        applyStatModifiers(holder, scaling, stats, AddReason.LOAD, manager);

        // Re-apply speed and knockback from regenerated stats
        NPCEntity npc = holder.getComponent(npcType);
        if (npc != null) {
            applySpeedEffect(holder, npc, stats, store, manager);
            applyKnockbackResistance(npc, stats);
        }

        LOGGER.atFine().log("[MobScaling] Recalculated loaded mob %s: Lv%d, HP=%.0f, class=%s, mods=%s",
            scaling.getRoleName(), mobLevel, stats.maxHealth(), classification,
            modComp != null ? modComp.getModifiers() : "none");
    }

    /**
     * Applies calculated stats to the mob's EntityStatMap using the weighted RPG formula.
     *
     * <p><b>Weighted RPG Formula:</b> Compresses vanilla stat ranges to prevent stacking.
     * <pre>
     * weight = √(vanillaStat / 100)
     * effectiveRpg = max(rpgTargetStat × progressiveScale, 10 × bossMultiplier)
     * finalStat = effectiveRpg × weight
     * </pre>
     *
     * <p>The modifier is applied as <b>ADDITIVE</b> (not MULTIPLICATIVE) because
     * Hytale's {@code EntityStatValue.computeModifiers()} <b>sums</b> all MULTIPLICATIVE
     * amounts into one float before multiplying. If both this system and
     * {@code RealmModifierSystem} use MULTIPLICATIVE, the amounts sum instead of
     * multiplying sequentially, making our HP replacement ineffective.
     *
     * <p>With ADDITIVE, the math becomes:
     * <pre>
     * max = assetMax + NPC_Max + RPG_HEALTH_OFFSET = finalHP
     * </pre>
     * Any MULTIPLICATIVE modifiers (realm health bonus, etc.) then correctly scale
     * the RPG-calculated HP as a percentage bonus.
     *
     * @param holder    The entity holder
     * @param component The MobScalingComponent containing vanilla HP and classification
     * @param stats     The calculated mob stats (RPG target values)
     * @param reason    The reason the entity was added (SPAWN vs LOAD)
     * @param manager   The MobScalingManager for accessing config
     */
    private void applyStatModifiers(
            @Nonnull Holder<EntityStore> holder,
            @Nonnull MobScalingComponent component,
            @Nonnull MobStats stats,
            @Nonnull AddReason reason,
            @Nonnull MobScalingManager manager) {

        EntityStatMap statMap = holder.getComponent(statMapType);
        if (statMap == null) {
            return;
        }

        // Lazy init stat index
        if (healthStatIndex < 0) {
            healthStatIndex = EntityStatType.getAssetMap().getIndex("Health");
        }

        EntityStatValue healthStat = statMap.get(healthStatIndex);
        if (healthStat == null) {
            return;
        }

        // Calculate current ratio to preserve health percentage for LOAD reason
        // SPAWN reason always starts at 100% (1.0 ratio)
        float currentRatio = 1.0f;
        int vanillaHP = component.getVanillaHP();

        if (reason == AddReason.LOAD) {
            // Capture health ratio WITH old modifier (preserves health percentage)
            float max = healthStat.getMax();
            currentRatio = max > 0 ? healthStat.get() / max : 1.0f;

            // Remove old modifier to discover the ACTUAL vanilla base HP.
            // Critical when the NPC's Role changed (taming, NPCMountSystems role reset)
            // because the stored vanillaHP may be from a different Role.
            statMap.removeModifier(healthStatIndex, RPG_HEALTH_KEY);
            float actualVanillaMax = healthStat.getMax();
            if (actualVanillaMax > 0) {
                vanillaHP = (int) actualVanillaMax;
                component.setVanillaHP(vanillaHP); // Update for future saves
            }
        }

        if (vanillaHP <= 0) {
            vanillaHP = 100; // Fallback: matches Health.json asset max
        }

        // === WEIGHTED RPG FORMULA ===
        // This replaces vanilla HP with a formula-derived value that:
        // - Uses pure RPG stats (not vanilla × RPG multiplier)
        // - Compresses the range using √(vanilla/100) weight
        // - Applies exponential scaling to match gear progression
        // - Makes level 1 mobs easier than vanilla

        double rpgTargetHP = stats.maxHealth()
                * manager.getConfig().getBalanceMultipliers().getHealth();
        int mobLevel = component.getMobLevel();

        // Get progressive scaling factor (0.15 → 1.0 based on level)
        double progressiveScale = manager.getStatPoolConfig().calculateScalingFactor(mobLevel);

        // Exponential scaling via centralized LevelScaling (config.yml → scaling).
        // This is the third application of LevelScaling in the HP pipeline
        // (pool generation applies it twice). Together they produce the intended HP curve.
        double expMultiplier = manager.getConfig().getExponentialScaling().calculateMultiplier(mobLevel);

        // Boss multiplier for the floor value
        double bossMultiplier = (component.getClassification() == RPGMobClass.BOSS) ? 2.5 : 1.0;

        // Weight: compresses vanilla range via square root
        // vanillaHP 100 → weight 1.0
        // vanillaHP 400 → weight 2.0 (not 4.0)
        // vanillaHP 40 → weight 0.63
        double weight = Math.sqrt((double) vanillaHP / 100.0);

        // Effective RPG: scales with level and exponential multiplier, has a floor to prevent 0 stats
        // Floor of 10 HP ensures mobs aren't invisible-health while staying killable at level 1
        double effectiveRpg = Math.max(rpgTargetHP * progressiveScale * expMultiplier, 10.0 * bossMultiplier);

        // Final HP: weighted RPG value (replaces vanilla entirely)
        double finalHP = effectiveRpg * weight;

        // Calculate ADDITIVE offset to reach finalHP from vanillaHP.
        //
        // WHY ADDITIVE, NOT MULTIPLICATIVE:
        // Hytale's EntityStatValue.computeModifiers() SUMS all MULTIPLICATIVE amounts
        // into one float, then multiplies once: max = (base + additive_sum) * mult_sum.
        // If two systems both use MULTIPLICATIVE (e.g., our 0.094 + realm's 1.06),
        // they SUM to 1.154 instead of multiplying sequentially (0.094 × 1.06).
        // This makes our HP replacement invisible — vanilla HP barely changes.
        //
        // ADDITIVE avoids this: our offset adjusts the base (alongside NPC_Max),
        // then any MULTIPLICATIVE modifiers (realm health bonus, etc.) correctly
        // scale the RPG-calculated HP as a percentage bonus.
        //
        // Math: assetMax(100) + NPC_Max(vanillaHP-100) + RPG_HEALTH(finalHP-vanillaHP)
        //     = 100 + (vanillaHP - 100) + (finalHP - vanillaHP) = finalHP
        float healthOffset = (float) (finalHP - vanillaHP);

        // Log for debugging (FINE level to avoid spam)
        LOGGER.atFine().log("[MobScaling] HP formula: vanilla=%d, rpgTarget=%.0f, progScale=%.2f, expMult=%.2f, weight=%.2f, final=%.0f, offset=%+.1f",
            vanillaHP, rpgTargetHP, progressiveScale, expMultiplier, weight, finalHP, healthOffset);

        // Create and apply health modifier as ADDITIVE to replace vanilla HP
        StaticModifier healthMod = new StaticModifier(
            Modifier.ModifierTarget.MAX,
            StaticModifier.CalculationType.ADDITIVE,
            healthOffset
        );

        statMap.putModifier(healthStatIndex, RPG_HEALTH_KEY, healthMod);

        // Set health based on preserved ratio
        if (currentRatio >= 0.999f) {
            statMap.maximizeStatValue(healthStatIndex);
        } else {
            float newMax = healthStat.getMax();
            statMap.setStatValue(healthStatIndex, newMax * currentRatio);
        }

        // Safety net: ensure entity survived the health recalculation.
        // Catches edge cases where ratio × newMax rounds to 0 (e.g., stale vanillaHP
        // from a Role change produced a near-zero offset on a previous save).
        if (reason == AddReason.LOAD && healthStat.get() <= 0) {
            statMap.maximizeStatValue(healthStatIndex);
            LOGGER.atWarning().log("[MobScaling] Health safety net activated for %s (ratio=%.3f)",
                component.getRoleName(), currentRatio);
        }

        // Clear vanilla NPC health regeneration for this entity.
        // Vanilla Health.json has a Percentage-type regen (5% of max/0.5s) that scales
        // with our HP modifier. We null the regeneratingValues array so vanilla's
        // NPCEntityRegenerateStatsSystem finds nothing to apply. Our MobRegenerationSystem
        // provides RPG-controlled regen instead. Players and unscaled NPCs are unaffected.
        clearVanillaHealthRegen(healthStat);
    }

    /**
     * Clears vanilla health regeneration on a scaled mob's Health stat.
     *
     * <p>Uses reflection to null the private {@code regeneratingValues} field on
     * {@link EntityStatValue}. The field is cached after first successful access.
     * If reflection fails (e.g., Hytale renames the field in a future update),
     * a warning is logged and the mob keeps vanilla regen — degraded but functional.
     *
     * @param healthStat The mob's Health EntityStatValue
     */
    static void clearVanillaHealthRegen(@Nonnull EntityStatValue healthStat) {
        if (regenerationFieldFailed) return;
        try {
            Field field = regeneratingValuesField;
            if (field == null) {
                field = EntityStatValue.class.getDeclaredField("regeneratingValues");
                field.setAccessible(true);
                regeneratingValuesField = field;
            }
            field.set(healthStat, null);
        } catch (NoSuchFieldException e) {
            regenerationFieldFailed = true;
            LOGGER.atWarning().log("[MobScaling] Cannot clear vanilla health regen: " +
                "EntityStatValue.regeneratingValues field not found (Hytale API changed?). Will not retry.");
        } catch (Exception e) {
            LOGGER.atFine().withCause(e).log("[MobScaling] Failed to clear vanilla health regen");
        }
    }

    private void applySpeedEffect(
            @Nonnull Holder<EntityStore> holder,
            @Nonnull NPCEntity npc,
            @Nonnull MobStats stats,
            @Nonnull Store<EntityStore> store,
            @Nonnull MobScalingManager manager) {

        float speedMultiplier = (float) stats.moveSpeed();
        if (Math.abs(speedMultiplier - 1.0f) < 0.01f) return;

        MobSpeedEffectManager speedManager = manager.getSpeedEffectManager();
        if (speedManager == null || !speedManager.isInitialized()) return;

        Ref<EntityStore> entityRef = npc.getReference();
        if (entityRef == null || !entityRef.isValid()) return;

        speedManager.applySpeedEffect(entityRef, speedMultiplier, store);
    }

    /**
     * Applies knockback resistance to a mob by modifying its motion controller's knockback scale.
     *
     * <p>Knockback resistance is a percentage (0-100) that reduces how much knockback the mob receives.
     * 0% = normal knockback, 100% = immune to knockback.
     *
     * @param npc The NPC entity
     * @param stats The calculated mob stats containing knockback resistance
     */
    private void applyKnockbackResistance(@Nonnull NPCEntity npc, @Nonnull MobStats stats) {
        double knockbackResistance = stats.knockbackResistance();

        // Skip if no resistance (0% = normal knockback)
        if (knockbackResistance <= 0.0) {
            return;
        }

        Role role = npc.getRole();
        if (role == null) {
            return;
        }

        MotionController motionController = role.getActiveMotionController();
        if (motionController == null) {
            return;
        }

        // Get base knockback scale from role (default is 1.0)
        double baseKnockbackScale = role.getKnockbackScale();

        // Calculate effective knockback scale: base * (1 - resistance/100)
        // Resistance of 100% = 0 knockback scale (immune)
        // Resistance of 50% = 0.5 knockback scale (half knockback)
        double resistanceFactor = 1.0 - Math.min(knockbackResistance, 100.0) / 100.0;
        double effectiveKnockbackScale = baseKnockbackScale * resistanceFactor;

        // Apply the modified knockback scale
        motionController.setKnockbackScale(effectiveKnockbackScale);

        // Log for debugging
        if (plugin.getConfigManager().getRPGConfig().isDebugMode() && knockbackResistance > 0) {
            LOGGER.at(Level.FINE).log("[MobScaling] Applied knockback resistance: %.0f%% (scale: %.2f -> %.2f)",
                knockbackResistance, baseKnockbackScale, effectiveKnockbackScale);
        }
    }

    @Nullable
    public ComponentType<EntityStore, MobScalingComponent> getScalingComponentType() {
        return plugin.getMobScalingComponentType();
    }

    /**
     * Extracts NPC group memberships for a mob from the DynamicEntityRegistry.
     *
     * <p>Returns empty set if the registry isn't available (e.g., entity discovery disabled).
     * This enables faction-based resistance profiles (Trork→Earth) and group-based
     * archetype detection (animals→Beast).
     */
    @Nonnull
    private java.util.Set<String> getNpcGroups(@Nullable String roleName, @Nonnull MobScalingManager manager) {
        var registry = manager.getDynamicEntityRegistry();
        if (registry == null || roleName == null) {
            return java.util.Set.of();
        }
        var discovered = registry.getDiscoveredRole(roleName);
        return discovered != null ? discovered.memberGroups() : java.util.Set.of();
    }
}