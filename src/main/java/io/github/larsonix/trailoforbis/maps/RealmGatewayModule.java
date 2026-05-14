package io.github.larsonix.trailoforbis.maps;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.World;
import io.github.larsonix.trailoforbis.TrailOfOrbis;
import io.github.larsonix.trailoforbis.database.repository.SpawnGatewayRepository;
import io.github.larsonix.trailoforbis.maps.config.RealmsConfig;
import io.github.larsonix.trailoforbis.maps.gateway.GatewayTierRepository;
import io.github.larsonix.trailoforbis.maps.gateway.GatewayUpgradeConfig;
import io.github.larsonix.trailoforbis.maps.gateway.GatewayUpgradeManager;
import io.github.larsonix.trailoforbis.maps.spawn.SpawnGatewayManager;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Manages spawn gateway portals around world spawn + tier-based upgrades.
 *
 * <p>Independent subsystem — does not interact with realm instances.
 * Gateways are permanent portals in the overworld, not realm portals.
 */
final class RealmGatewayModule {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    @Nullable private SpawnGatewayManager spawnGatewayManager;
    @Nullable private GatewayUpgradeManager gatewayUpgradeManager;

    /**
     * Initializes spawn gateway and upgrade systems.
     *
     * @param config The realms config (for gateway settings)
     */
    void initialize(@Nonnull RealmsConfig config) {
        TrailOfOrbis plugin = TrailOfOrbis.getInstanceOrNull();
        if (plugin == null) {
            LOGGER.atWarning().log("TrailOfOrbis plugin not available - spawn gateways disabled");
            return;
        }
        if (!config.getSpawnGatewayConfig().isEnabled()) {
            LOGGER.atInfo().log("Spawn gateways disabled by config");
            return;
        }

        SpawnGatewayRepository gatewayRepository = new SpawnGatewayRepository(plugin.getDataManager());
        spawnGatewayManager = new SpawnGatewayManager(config.getSpawnGatewayConfig(), gatewayRepository);

        GatewayTierRepository tierRepository = new GatewayTierRepository(plugin.getDataManager());
        GatewayUpgradeConfig upgradeConfig = GatewayUpgradeConfig.createDefault();
        gatewayUpgradeManager = new GatewayUpgradeManager(upgradeConfig, tierRepository);

        // Wire tier repository into spawn gateway manager for auto-registration
        spawnGatewayManager.setGatewayTierRepository(tierRepository);

        LOGGER.atInfo().log("Gateway module initialized (SpawnGatewayManager + GatewayUpgradeManager)");
    }

    void shutdown() {
        if (gatewayUpgradeManager != null) {
            gatewayUpgradeManager.getRepository().clearCache();
            gatewayUpgradeManager = null;
        }
        if (spawnGatewayManager != null) {
            spawnGatewayManager.clearCache();
            spawnGatewayManager = null;
        }
    }

    /**
     * Ensures spawn gateways exist around a world's spawn point.
     *
     * @param world The world to check/place gateways in
     * @return CompletableFuture that completes with true if gateways were placed
     */
    @Nonnull
    CompletableFuture<Boolean> ensureSpawnGatewaysExist(@Nonnull World world) {
        if (spawnGatewayManager == null) {
            return CompletableFuture.completedFuture(false);
        }

        // Load existing gateway tiers into cache when world activates
        if (gatewayUpgradeManager != null) {
            UUID worldUuid = world.getWorldConfig().getUuid();
            gatewayUpgradeManager.getRepository().loadWorldGateways(worldUuid);
        }

        return spawnGatewayManager.ensureGatewaysExist(world);
    }

    @Nullable
    SpawnGatewayManager getSpawnGatewayManager() { return spawnGatewayManager; }

    @Nullable
    GatewayUpgradeManager getGatewayUpgradeManager() { return gatewayUpgradeManager; }
}
