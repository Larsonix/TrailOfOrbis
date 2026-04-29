package io.github.larsonix.trailoforbis.leveling.systems;

import com.hypixel.hytale.logger.HytaleLogger;
import java.util.logging.Level;
import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathSystems;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.EntityStatType;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.Role;
import com.hypixel.hytale.server.core.asset.type.attitude.Attitude;
import io.github.larsonix.trailoforbis.TrailOfOrbis;
import io.github.larsonix.trailoforbis.api.ServiceRegistry;
import io.github.larsonix.trailoforbis.api.services.AttributeService;
import io.github.larsonix.trailoforbis.attributes.ComputedStats;
import io.github.larsonix.trailoforbis.database.models.PlayerData;
import io.github.larsonix.trailoforbis.util.MessageColors;
import io.github.larsonix.trailoforbis.leveling.api.LevelingService;
import io.github.larsonix.trailoforbis.leveling.config.LevelingConfig;
import io.github.larsonix.trailoforbis.leveling.xp.MobStatsXpCalculator;
import io.github.larsonix.trailoforbis.leveling.xp.XpSource;
import io.github.larsonix.trailoforbis.mobs.MobScalingManager;
import io.github.larsonix.trailoforbis.mobs.classification.MobClassificationService;
import io.github.larsonix.trailoforbis.mobs.classification.RPGMobClass;
import io.github.larsonix.trailoforbis.mobs.component.MobScalingComponent;
import io.github.larsonix.trailoforbis.maps.RealmsManager;
import io.github.larsonix.trailoforbis.maps.components.RealmMobComponent;
import io.github.larsonix.trailoforbis.maps.instance.RealmInstance;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * ECS system that grants XP to players when they kill mobs.
 *
 * <p>Extends {@link DeathSystems.OnDeathSystem} to react when any entity dies.
 * Filters to only process non-player deaths where the killer is a player.
 *
 * <p>XP calculation uses {@link MobStatsXpCalculator} which considers:
 * <ul>
 *   <li>Mob level</li>
 *   <li>Mob stat pool</li>
 *   <li>Mob tier (normal/elite/boss)</li>
 * </ul>
 *
 * <p>This system replaces LevelingCore's {@code GainXPEventSystem}.
 */
public class XpGainSystem extends DeathSystems.OnDeathSystem {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final TrailOfOrbis plugin;

    /** Creates a new XP gain system. */
    public XpGainSystem(@Nonnull TrailOfOrbis plugin) {
        this.plugin = plugin;
    }

    @Override
    @Nonnull
    public Query<EntityStore> getQuery() {
        // Match ALL entity deaths (like KillFeed does)
        // We filter manually in onComponentAdded to only process:
        // 1. Non-player deaths (players handled by XpLossSystem)
        // 2. Deaths caused by players (not mob-on-mob kills)
        // 3. Mobs with MobScalingComponent (scaled hostile mobs)
        return Archetype.empty();
    }

    @Override
    public void onComponentAdded(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull DeathComponent deathComponent,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        // ALWAYS log entry to trace system activation
        LOGGER.at(Level.INFO).log("onComponentAdded TRIGGERED for entity ref=%s", ref);

        // Get leveling config
        LevelingConfig config = getLevelingConfig();
        if (config == null) {
            LOGGER.at(Level.INFO).log("SKIP: LevelingConfig is null");
            return;
        }
        if (!config.isEnabled()) {
            LOGGER.at(Level.INFO).log("SKIP: Leveling is disabled");
            return;
        }
        if (!config.getXpGain().isEnabled()) {
            LOGGER.at(Level.INFO).log("SKIP: XP gain is disabled");
            return;
        }

        // Skip if this is a player death (handled by XpLossSystem)
        Player playerComponent = store.getComponent(ref, Player.getComponentType());
        if (playerComponent != null) {
            LOGGER.at(Level.INFO).log("SKIP: Entity is a player (handled by XpLossSystem)");
            return;
        }

        // Skip XP for realm mobs when the realm is no longer active (timeout/completed).
        // After timeout, mobs are despawned but may still die from in-flight damage in the
        // brief window before removal. Players should not receive XP for a failed realm.
        if (isRealmMobInEndedRealm(ref, store)) {
            LOGGER.at(Level.FINE).log("SKIP: Realm mob died in ended realm (timeout/completed)");
            return;
        }

        // Get the attacker (must be a player)
        // Hex spell damage is rewritten to EntitySource(caster) by HexDamageAttributionSystem
        // in FilterDamageGroup BEFORE death, so the standard EntitySource path handles it.
        PlayerRef attackerRef = getAttackerPlayerRef(ref, deathComponent, store);
        if (attackerRef == null) {
            Damage deathDmg = deathComponent.getDeathInfo();
            String srcInfo = "deathInfo=null";
            if (deathDmg != null) {
                Damage.Source src = deathDmg.getSource();
                srcInfo = src != null ? src.getClass().getName() : "source=null";
            }
            LOGGER.at(Level.INFO).log("SKIP: No player attacker found (src=%s)", srcInfo);
            return;
        }
        UUID attackerUuid = attackerRef.getUuid();
        LOGGER.at(Level.INFO).log("Attacker UUID: %s", attackerUuid);

        // Get mob scaling component (may be null for unscaled/chunk-loaded mobs)
        MobScalingComponent mobScaling = store.getComponent(ref, MobScalingComponent.getComponentType());

        // Calculate XP
        MobStatsXpCalculator calculator = getXpCalculator();
        if (calculator == null) {
            LOGGER.at(Level.INFO).log("SKIP: XpCalculator is null");
            return;
        }

        long xp;
        String mobInfo;
        int mobLevel = -1;
        if (mobScaling != null) {
            // Scaled mob - use full XP calculation from MobScalingComponent
            xp = calculator.calculateMobKillXp(mobScaling);
            mobLevel = mobScaling.getMobLevel();
            mobInfo = mobScaling.getClassification().name() + " Lv" + mobLevel;
            LOGGER.at(Level.FINE).log("MobScaling found: %s, XP=%d", mobInfo, xp);
        } else {
            // Unscaled mob (chunk-loaded, safe zone, or non-hostile)
            // Calculate XP from raw entity stats instead of flat 10 XP
            xp = calculateXpFromEntityStats(ref, store, calculator);
            mobInfo = "RawStats";
            LOGGER.at(Level.FINE).log("No MobScaling - calculated from raw stats, XP=%d", xp);
        }

        // Trigger guide milestone for first elite/boss kill (scaled mobs only)
        if (mobScaling != null && plugin.getGuideManager() != null) {
            RPGMobClass cls = mobScaling.getClassification();
            if (cls == RPGMobClass.ELITE || cls == RPGMobClass.BOSS) {
                plugin.getGuideManager().tryShow(attackerUuid, io.github.larsonix.trailoforbis.guide.GuideMilestone.FIRST_ELITE);
            }
        }

        // Apply level-gap penalty (only for scaled mobs with known level)
        if (mobLevel >= 0) {
            xp = applyLevelGapPenalty(attackerUuid, mobLevel, xp, config);
        }

        // Apply experienceGainPercent multiplier from player stats
        xp = applyExperienceGainBonus(attackerUuid, xp);

        // Grant XP to the player (party-aware: distributes to party if applicable)
        LevelingService levelingService = getLevelingService();
        if (levelingService == null) {
            LOGGER.at(Level.INFO).log("SKIP: LevelingService is null");
            return;
        }

        // Party XP distribution: if player is in a party, split XP among members.
        // If no party mod or solo, grants full XP to killer directly.
        Optional<io.github.larsonix.trailoforbis.compat.party.PartyIntegrationManager> partyOpt =
            ServiceRegistry.get(io.github.larsonix.trailoforbis.compat.party.PartyIntegrationManager.class);
        boolean distributed = false;
        if (partyOpt.isPresent()) {
            distributed = partyOpt.get().distributeXp(attackerUuid, xp);
        }
        if (!distributed) {
            levelingService.addXp(attackerUuid, xp, XpSource.MOB_KILL);
        }
        LOGGER.at(Level.INFO).log("SUCCESS: %s %d XP to %s from %s mob",
            distributed ? "Distributed" : "Granted", xp, attackerUuid, mobInfo);

        // Send XP gain chat message to player (killer always sees their XP)
        sendXpGainMessage(attackerRef, xp);
    }

    /** Sends an XP gain chat message to the player. */
    private void sendXpGainMessage(@Nonnull PlayerRef playerRef, long xp) {
        // Format: "+50 XP" in a nice color
        Message xpMessage = Message.empty()
            .insert(Message.raw("+").color(MessageColors.XP_GAIN))
            .insert(Message.raw(String.valueOf(xp)).color(MessageColors.XP_GAIN))
            .insert(Message.raw(" XP").color(MessageColors.XP_GAIN));

        playerRef.sendMessage(xpMessage);
    }

    /**
     * Applies level-gap XP penalty using an asymmetric rational curve.
     *
     * <p>Within the safe zone (±{@code base + percent × playerLevel}), full XP.
     * Beyond it, XP degrades smoothly:
     * <ul>
     *   <li>Downward (mob too low): steep falloff, 1% floor</li>
     *   <li>Upward (mob too high): gentle falloff, 5% floor</li>
     * </ul>
     *
     * @return The XP after applying the level-gap penalty
     */
    private long applyLevelGapPenalty(@Nonnull UUID playerUuid, int mobLevel, long rawXp,
                                       @Nonnull LevelingConfig config) {
        LevelingConfig.LevelGapConfig gapConfig = config.getXpGain().getLevelGap();
        if (!gapConfig.isEnabled()) {
            return rawXp;
        }

        LevelingService levelingService = getLevelingService();
        if (levelingService == null) {
            return rawXp;
        }

        int playerLevel = levelingService.getLevel(playerUuid);

        // Anti-boosting: use highest party member level for gap calculation
        Optional<io.github.larsonix.trailoforbis.compat.party.PartyIntegrationManager> partyOpt =
            ServiceRegistry.get(io.github.larsonix.trailoforbis.compat.party.PartyIntegrationManager.class);
        if (partyOpt.isPresent()) {
            int partyMaxLevel = partyOpt.get().getHighestPartyMemberLevel(playerUuid);
            if (partyMaxLevel > playerLevel) {
                LOGGER.at(Level.FINE).log("[LevelGap] Anti-boosting: party max %d > killer %d",
                    partyMaxLevel, playerLevel);
                playerLevel = partyMaxLevel;
            }
        }

        int safeZone = gapConfig.getSafeZoneBase() + (int) (gapConfig.getSafeZonePercent() * playerLevel);
        int levelDiff = Math.abs(playerLevel - mobLevel);
        int effectiveGap = Math.max(0, levelDiff - safeZone);

        if (effectiveGap == 0) {
            return rawXp; // Within safe zone — full XP
        }

        // Asymmetric: different curve for downward vs upward
        boolean isDownward = mobLevel < playerLevel;
        double falloffRange = safeZone * (isDownward
            ? gapConfig.getDownwardFalloffFactor()
            : gapConfig.getUpwardFalloffFactor());
        double exponent = isDownward
            ? gapConfig.getDownwardExponent()
            : gapConfig.getUpwardExponent();
        double floor = isDownward
            ? gapConfig.getDownwardFloor()
            : gapConfig.getUpwardFloor();

        // Prevent division by zero if falloffRange is tiny
        if (falloffRange < 1.0) {
            falloffRange = 1.0;
        }

        // Rational curve: f^e / (f^e + g^e) — smooth sigmoid-like falloff
        double fPow = Math.pow(falloffRange, exponent);
        double gPow = Math.pow(effectiveGap, exponent);
        double multiplier = fPow / (fPow + gPow);
        multiplier = Math.max(floor, multiplier);

        long result = Math.round(rawXp * multiplier);
        result = Math.max(1, result);

        LOGGER.at(Level.FINE).log("[LevelGap] player=%d mob=%d gap=%d safe=%d %s mult=%.2f xp=%d→%d",
            playerLevel, mobLevel, effectiveGap, safeZone,
            isDownward ? "DOWN" : "UP", multiplier, rawXp, result);

        return result;
    }

    /**
     * Applies the player's experienceGainPercent bonus to the raw XP amount.
     *
     * <p>Formula: finalXp = rawXp * (1 + experienceGainPercent / 100)
     * Result is always at least 1 XP (no negative/zero XP from rounding).
     *
     * @return The XP after applying the experience gain bonus
     */
    private long applyExperienceGainBonus(@Nonnull UUID playerUuid, long rawXp) {
        Optional<AttributeService> serviceOpt = ServiceRegistry.get(AttributeService.class);
        if (serviceOpt.isEmpty()) {
            return rawXp;
        }

        Optional<PlayerData> dataOpt = serviceOpt.get().getPlayerDataRepository().get(playerUuid);
        if (dataOpt.isEmpty()) {
            return rawXp;
        }

        ComputedStats stats = dataOpt.get().getComputedStats();
        if (stats == null) {
            return rawXp;
        }

        float bonus = stats.getExperienceGainPercent();
        if (Math.abs(bonus) < 0.001f) {
            return rawXp;
        }

        float multiplier = 1.0f + (bonus / 100.0f);
        long result = Math.round(rawXp * multiplier);
        return Math.max(1, result);
    }

    /**
     * Calculates XP from raw entity stats when MobScalingComponent is not available.
     *
     * <p>This handles chunk-loaded mobs or mobs that spawned before the scaling system.
     * It reads the entity's actual health, position, and role name to estimate XP.
     *
     * <p>Passive mobs (non-hostile) only give 1-5 XP.
     *
     * @return Calculated XP based on raw stats
     */
    private long calculateXpFromEntityStats(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull MobStatsXpCalculator calculator
    ) {
        // Check if this is a passive mob (non-combat)
        // HOSTILE = always attacks (skeletons, zombies) - combat mob
        // NEUTRAL = attacks when provoked (bears, wolves, spiders) - combat mob
        // FRIENDLY/REVERED/IGNORE = truly passive mobs - give only 1-5 XP
        NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
        if (npc != null) {
            Role role = npc.getRole();
            if (role != null) {
                Attitude attitude = role.getWorldSupport().getDefaultPlayerAttitude();
                if (attitude != Attitude.HOSTILE && attitude != Attitude.NEUTRAL) {
                    // Truly passive mob (FRIENDLY, REVERED, IGNORE) - give only 1-5 XP
                    long xp = ThreadLocalRandom.current().nextLong(1, 6); // 1-5 inclusive
                    LOGGER.at(Level.INFO).log("Passive mob (attitude=%s) -> XP=%d", attitude, xp);
                    return xp;
                }
            }
        }

        // Get max health from EntityStatMap
        double maxHealth = 50.0; // Default baseline
        EntityStatMap statMap = store.getComponent(ref, EntityStatMap.getComponentType());
        if (statMap != null) {
            int healthIndex = EntityStatType.getAssetMap().getIndex("Health");
            EntityStatValue healthStat = statMap.get(healthIndex);
            if (healthStat != null) {
                maxHealth = healthStat.getMax();
            }
        }

        // Get position for distance-based level estimation
        double distanceFromOrigin = 0.0;
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform != null) {
            Vector3d pos = transform.getPosition();
            distanceFromOrigin = Math.sqrt(pos.x * pos.x + pos.z * pos.z);
        }

        // Check classification
        RPGMobClass classification = RPGMobClass.HOSTILE;
        if (npc != null) {
            MobScalingManager manager = plugin.getMobScalingManager();
            if (manager != null && manager.getClassificationService() != null) {
                MobClassificationService service = manager.getClassificationService();
                classification = service.classify(manager.createContext(npc));
            }
        }

        // Calculate XP using the raw stats method
        long xp = calculator.calculateXpFromRawStats(maxHealth, distanceFromOrigin, classification);

        LOGGER.at(Level.INFO).log("Raw stats: maxHealth=%.0f, dist=%.0f, class=%s -> XP=%d",
            maxHealth, distanceFromOrigin, classification, xp);

        return xp;
    }

    /**
     * @return The attacker's PlayerRef, or null if not a player kill
     */
    @Nullable
    private PlayerRef getAttackerPlayerRef(
        @Nonnull Ref<EntityStore> deadRef,
        @Nonnull DeathComponent deathComponent,
        @Nonnull Store<EntityStore> store
    ) {
        Damage deathInfo = deathComponent.getDeathInfo();
        if (deathInfo == null) {
            return null;
        }

        Damage.Source source = deathInfo.getSource();

        LOGGER.atInfo().log("[XpGain-SRC] Death source class=%s", source.getClass().getName());

        // Standard path: EntitySource (melee, ranged, projectile, or hex rewritten by
        // HexDamageAttributionSystem in FilterDamageGroup before death)
        if (source instanceof Damage.EntitySource entitySource) {
            Ref<EntityStore> sourceRef = entitySource.getRef();
            if (!sourceRef.isValid()) {
                LOGGER.atInfo().log("[XpGain-SRC] EntitySource ref INVALID");
                return null;
            }
            Player attackerPlayer = store.getComponent(sourceRef, Player.getComponentType());
            if (attackerPlayer != null) {
                return store.getComponent(sourceRef, PlayerRef.getComponentType());
            }
            LOGGER.atInfo().log("[XpGain-SRC] EntitySource ref valid but NOT a player entity");
        }

        return null;
    }

    /**
     * Gets the leveling configuration.
     */
    @Nullable
    private LevelingConfig getLevelingConfig() {
        if (plugin.getConfigManager() == null) {
            return null;
        }
        return plugin.getConfigManager().getLevelingConfig();
    }

    /**
     * Gets the XP calculator.
     */
    @Nullable
    private MobStatsXpCalculator getXpCalculator() {
        return plugin.getXpCalculator();
    }

    /**
     * Gets the leveling service.
     */
    @Nullable
    private LevelingService getLevelingService() {
        return plugin.getLevelingManager();
    }

    /**
     * Checks if the dying entity is a realm mob whose realm is no longer active.
     *
     * <p>Returns true when the mob has a {@link RealmMobComponent} and the realm
     * has transitioned out of ACTIVE state (ENDING, CLOSING). This suppresses XP
     * for mobs killed during the timeout/completion cleanup window.
     *
     * <p>Non-realm mobs (no RealmMobComponent) always return false — their XP
     * is unaffected.
     */
    private boolean isRealmMobInEndedRealm(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull Store<EntityStore> store) {

        RealmMobComponent realmMob = store.getComponent(ref, RealmMobComponent.getComponentType());
        if (realmMob == null || realmMob.getRealmId() == null) {
            return false; // Not a realm mob — normal XP rules apply
        }

        RealmsManager realmsManager = plugin.getRealmsManager();
        if (realmsManager == null) {
            return false;
        }

        Optional<RealmInstance> realmOpt = realmsManager.getRealm(realmMob.getRealmId());
        if (realmOpt.isEmpty()) {
            return true; // Realm already removed — no XP
        }

        return !realmOpt.get().isActive();
    }
}
