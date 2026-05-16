package io.github.larsonix.trailoforbis.leveling.xp;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.compat.party.PartyBridge;
import io.github.larsonix.trailoforbis.compat.party.PartyConfig;
import io.github.larsonix.trailoforbis.compat.party.PartyIntegrationManager;
import io.github.larsonix.trailoforbis.leveling.api.LevelingService;
import io.github.larsonix.trailoforbis.util.PlayerWorldCache;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Unified XP distribution service that handles both party-based and proximity-based
 * XP sharing with a single clean pipeline.
 *
 * <h3>Two modes:</h3>
 * <ul>
 *   <li><b>Party mode</b> (PartyPro installed): XP shared with party members in same world</li>
 *   <li><b>Proximity mode</b> (no party mod): XP shared with players within radius of mob death</li>
 * </ul>
 *
 * <h3>Pipeline:</h3>
 * <ol>
 *   <li>Resolve eligible recipients (party or proximity)</li>
 *   <li>Solo fast-path if only 1 recipient</li>
 *   <li>Pre-compute group max level (O(n), for anti-boosting if enabled)</li>
 *   <li>Calculate XP pool with group size multiplier</li>
 *   <li>Split pool by mode (equal / killer_bonus)</li>
 *   <li>Apply per-player modifiers (level-gap penalty, XP gain bonus)</li>
 *   <li>Grant XP via LevelingService</li>
 * </ol>
 */
public class XpDistributionService {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final LevelingService levelingService;
    private final PartyConfig partyConfig;
    @Nullable
    private final PartyIntegrationManager partyManager;

    public XpDistributionService(
            @Nonnull LevelingService levelingService,
            @Nonnull PartyConfig partyConfig,
            @Nullable PartyIntegrationManager partyManager) {
        this.levelingService = levelingService;
        this.partyConfig = partyConfig;
        this.partyManager = partyManager;
    }

    /**
     * Distributes XP to the killer and eligible nearby/party players.
     *
     * <p>Always handles the XP grant internally. The caller should NOT grant XP
     * separately — this method does it for all recipients.
     *
     * @param killerUuid The player who got the kill
     * @param rawXp Raw XP amount (after realm bonus, before per-player modifiers)
     * @param perPlayerModifier Per-player adjustment callback (gap penalty, XP gain%)
     * @param mobPosition World position of the dead mob (radius center for proximity mode)
     * @param store The world's entity store (for position lookups in proximity mode)
     */
    public void distribute(
            @Nonnull UUID killerUuid,
            long rawXp,
            @Nonnull XpRecipientModifier perPlayerModifier,
            @Nonnull Vector3d mobPosition,
            @Nonnull Store<EntityStore> store) {

        // 1. Resolve eligible recipients
        List<UUID> recipients = resolveEligibleRecipients(killerUuid, mobPosition, store);

        // Determine sharing mode for XpSource tagging (only matters for groups)
        boolean isPartyMode = recipients.size() > 1
            && partyConfig.getXpSharing().isEnabled()
            && isUsingPartyMode(killerUuid);

        // 2. Delegate to core distribution logic
        distribute(killerUuid, rawXp, perPlayerModifier, recipients, isPartyMode);
    }

    /**
     * Core distribution logic — distributes XP to a pre-resolved recipient list.
     *
     * <p>Package-private for testability: tests can call this directly with
     * a known recipient list, bypassing world/party resolution.
     *
     * @param killerUuid The player who got the kill
     * @param rawXp Raw XP amount (after realm bonus, before per-player modifiers)
     * @param perPlayerModifier Per-player adjustment callback
     * @param recipients Pre-resolved eligible recipients (always includes killer)
     * @param isPartyMode true if XP source should be PARTY_SHARE for non-killers
     */
    void distribute(
            @Nonnull UUID killerUuid,
            long rawXp,
            @Nonnull XpRecipientModifier perPlayerModifier,
            @Nonnull List<UUID> recipients,
            boolean isPartyMode) {

        PartyConfig.XpSharingConfig sharingConfig = partyConfig.getXpSharing();

        // Solo fast-path
        if (recipients.size() <= 1) {
            long finalXp = Math.max(1, perPlayerModifier.apply(killerUuid, rawXp, -1));
            levelingService.addXp(killerUuid, finalXp, XpSource.MOB_KILL);
            LOGGER.at(Level.FINE).log("[XpDist] Solo: %d XP to %s", finalXp,
                killerUuid.toString().substring(0, 8));
            return;
        }

        // Pre-compute group max level (O(n), only if anti-boosting is enabled)
        int groupMaxLevel = -1;
        if (partyConfig.getAntiBoosting().isEnabled()
                && partyConfig.getAntiBoosting().isUsePartyMaxLevel()) {
            groupMaxLevel = 0;
            for (UUID memberId : recipients) {
                int level = levelingService.getLevel(memberId);
                if (level > groupMaxLevel) {
                    groupMaxLevel = level;
                }
            }
        }

        // Calculate pool with group size multiplier
        double multiplier = sharingConfig.getMultiplierForSize(recipients.size());
        long totalPool = Math.round(rawXp * multiplier);

        // Split by mode and grant
        if ("killer_bonus".equalsIgnoreCase(sharingConfig.getMode())) {
            distributeKillerBonus(killerUuid, rawXp, totalPool, recipients,
                perPlayerModifier, groupMaxLevel, isPartyMode);
        } else {
            distributeEqual(killerUuid, totalPool, recipients,
                perPlayerModifier, groupMaxLevel, isPartyMode);
        }

        LOGGER.at(Level.FINE).log("[XpDist] %s: %d raw XP (x%.2f pool) to %d recipients from %s",
            isPartyMode ? "Party" : "Proximity", rawXp, multiplier, recipients.size(),
            killerUuid.toString().substring(0, 8));
    }

    // ═══════════════════════════════════════════════════════════════════
    // DISTRIBUTION MODES
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Equal split: each member gets pool / count, modified per-player.
     */
    private void distributeEqual(
            @Nonnull UUID killerUuid,
            long totalPool,
            @Nonnull List<UUID> recipients,
            @Nonnull XpRecipientModifier modifier,
            int groupMaxLevel,
            boolean isPartyMode) {

        long shareBase = Math.max(1, totalPool / recipients.size());

        for (UUID memberId : recipients) {
            long memberXp = Math.max(1, modifier.apply(memberId, shareBase, groupMaxLevel));
            XpSource source = resolveSource(memberId, killerUuid, isPartyMode);
            levelingService.addXp(memberId, memberXp, source);
        }
    }

    /**
     * Killer bonus: killer gets full rawXp (modified), others get pool / count (modified).
     * Note: total distributed may exceed pool since killer gets unscaled rawXp.
     */
    private void distributeKillerBonus(
            @Nonnull UUID killerUuid,
            long rawXp,
            long totalPool,
            @Nonnull List<UUID> recipients,
            @Nonnull XpRecipientModifier modifier,
            int groupMaxLevel,
            boolean isPartyMode) {

        // Killer gets full raw XP (modified by their own stats)
        long killerXp = Math.max(1, modifier.apply(killerUuid, rawXp, groupMaxLevel));
        levelingService.addXp(killerUuid, killerXp, XpSource.MOB_KILL);

        // Others get bonus share
        long bonusBase = Math.max(1, totalPool / recipients.size());
        for (UUID memberId : recipients) {
            if (!memberId.equals(killerUuid)) {
                long memberXp = Math.max(1, modifier.apply(memberId, bonusBase, groupMaxLevel));
                XpSource source = resolveSource(memberId, killerUuid, isPartyMode);
                levelingService.addXp(memberId, memberXp, source);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // RECIPIENT RESOLUTION
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Resolves the list of players eligible for XP sharing.
     *
     * <p>Party mode: online party members in the same world as the killer.
     * Proximity mode: all players within radius of mob death position.
     * Fallback: just the killer (solo).
     *
     * @return List of eligible player UUIDs (always includes the killer)
     */
    @Nonnull
    private List<UUID> resolveEligibleRecipients(
            @Nonnull UUID killerUuid,
            @Nonnull Vector3d mobPosition,
            @Nonnull Store<EntityStore> store) {

        PartyConfig.XpSharingConfig sharingConfig = partyConfig.getXpSharing();

        // Party mode: PartyPro available, XP sharing enabled, killer is in a party
        if (sharingConfig.isEnabled() && isUsingPartyMode(killerUuid)) {
            return resolvePartyRecipients(killerUuid, mobPosition);
        }

        // Proximity mode: no party mod, proximity sharing enabled
        if (partyConfig.getProximity().isEnabled()) {
            return resolveProximityRecipients(killerUuid, mobPosition, store);
        }

        // Solo fallback
        return List.of(killerUuid);
    }

    /**
     * Checks if party-based XP sharing should be used for this player.
     */
    private boolean isUsingPartyMode(@Nonnull UUID killerUuid) {
        return partyManager != null
            && partyManager.isPartyModAvailable()
            && partyManager.getBridge().isInParty(killerUuid);
    }

    /**
     * Party mode: get online party members filtered to the killer's world.
     * Optionally filters by max_distance from mob position.
     */
    @Nonnull
    private List<UUID> resolvePartyRecipients(
            @Nonnull UUID killerUuid,
            @Nonnull Vector3d mobPosition) {

        PartyBridge bridge = partyManager.getBridge();
        List<UUID> onlineMembers = bridge.getOnlinePartyMembers(killerUuid);

        // Filter to same world
        World killerWorld = PlayerWorldCache.getPlayerWorld(killerUuid).orElse(null);
        if (killerWorld == null) {
            return List.of(killerUuid);
        }

        List<UUID> sameWorld = new ArrayList<>();
        for (UUID memberId : onlineMembers) {
            World memberWorld = PlayerWorldCache.getPlayerWorld(memberId).orElse(null);
            if (killerWorld.equals(memberWorld)) {
                sameWorld.add(memberId);
            }
        }

        // Optional distance filter (max_distance in party config, -1 = unlimited)
        double maxDist = partyConfig.getXpSharing().getMaxDistance();
        if (maxDist > 0) {
            PlayerRef killerRef = PlayerWorldCache.findPlayerRef(killerUuid, killerWorld);
            if (killerRef != null) {
                Store<EntityStore> store = killerWorld.getEntityStore().getStore();
                sameWorld = filterByDistance(sameWorld, mobPosition, maxDist, store);
            }
        }

        // Safety net: always include killer
        if (!sameWorld.contains(killerUuid)) {
            sameWorld.add(0, killerUuid);
        }

        return sameWorld;
    }

    /**
     * Proximity mode: find all players within radius of mob death position.
     * Uses XZ distance only (vertical height doesn't affect combat grouping).
     */
    @Nonnull
    private List<UUID> resolveProximityRecipients(
            @Nonnull UUID killerUuid,
            @Nonnull Vector3d mobPosition,
            @Nonnull Store<EntityStore> store) {

        double radius = partyConfig.getProximity().getRadius();
        double radiusSq = radius * radius;

        World killerWorld = PlayerWorldCache.getPlayerWorld(killerUuid).orElse(null);
        if (killerWorld == null) {
            return List.of(killerUuid);
        }

        List<UUID> nearby = new ArrayList<>();
        for (PlayerRef playerRef : killerWorld.getPlayerRefs()) {
            UUID playerId = playerRef.getUuid();
            Ref<EntityStore> entityRef = playerRef.getReference();
            if (entityRef == null || !entityRef.isValid()) {
                // Player might be transitioning — skip but include if they're the killer
                if (playerId.equals(killerUuid)) {
                    nearby.add(playerId);
                }
                continue;
            }

            TransformComponent transform = store.getComponent(entityRef,
                TransformComponent.getComponentType());
            if (transform == null) {
                if (playerId.equals(killerUuid)) {
                    nearby.add(playerId);
                }
                continue;
            }

            Vector3d playerPos = transform.getPosition();
            double distSq = distanceSquaredXZ(mobPosition, playerPos);
            if (distSq <= radiusSq) {
                nearby.add(playerId);
            }
        }

        // Safety net: always include killer
        if (!nearby.contains(killerUuid)) {
            nearby.add(0, killerUuid);
        }

        return nearby;
    }

    // ═══════════════════════════════════════════════════════════════════
    // UTILITIES
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Filters a list of player UUIDs to only those within distance of a position.
     * Used for party mode's optional max_distance check.
     */
    @Nonnull
    private List<UUID> filterByDistance(
            @Nonnull List<UUID> players,
            @Nonnull Vector3d center,
            double maxDistance,
            @Nonnull Store<EntityStore> store) {

        double maxDistSq = maxDistance * maxDistance;
        List<UUID> filtered = new ArrayList<>();

        for (UUID playerId : players) {
            PlayerRef ref = PlayerWorldCache.findPlayerRef(playerId);
            if (ref == null) {
                continue;
            }
            Ref<EntityStore> entityRef = ref.getReference();
            if (entityRef == null || !entityRef.isValid()) {
                continue;
            }
            TransformComponent transform = store.getComponent(entityRef,
                TransformComponent.getComponentType());
            if (transform == null) {
                continue;
            }
            double distSq = distanceSquaredXZ(center, transform.getPosition());
            if (distSq <= maxDistSq) {
                filtered.add(playerId);
            }
        }

        return filtered;
    }

    /**
     * XZ-plane distance squared (ignores Y). Vertical distance doesn't affect
     * combat grouping — players on different floors of a tower share XP.
     */
    private static double distanceSquaredXZ(@Nonnull Vector3d a, @Nonnull Vector3d b) {
        double dx = a.x - b.x;
        double dz = a.z - b.z;
        return dx * dx + dz * dz;
    }

    /**
     * Determines the XpSource for a recipient based on their role and sharing mode.
     */
    @Nonnull
    private static XpSource resolveSource(@Nonnull UUID memberId, @Nonnull UUID killerUuid,
                                           boolean isPartyMode) {
        if (memberId.equals(killerUuid)) {
            return XpSource.MOB_KILL;
        }
        return isPartyMode ? XpSource.PARTY_SHARE : XpSource.NEARBY_SHARE;
    }
}
