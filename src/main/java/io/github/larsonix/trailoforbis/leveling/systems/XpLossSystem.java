package io.github.larsonix.trailoforbis.leveling.systems;

import com.hypixel.hytale.logger.HytaleLogger;
import java.util.logging.Level;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathSystems;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.TrailOfOrbis;
import io.github.larsonix.trailoforbis.leveling.api.LevelingService;
import io.github.larsonix.trailoforbis.leveling.config.LevelingConfig;
import io.github.larsonix.trailoforbis.leveling.xp.XpSource;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

/**
 * ECS system that removes XP from players when they die.
 *
 * <p>Extends {@link DeathSystems.OnDeathSystem} to react when a player dies.
 * XP loss is configurable via {@link LevelingConfig.XpLossConfig}.
 *
 * <p>Features:
 * <ul>
 *   <li>Configurable loss percentage (e.g., 10% of current XP)</li>
 *   <li>Minimum level protection (won't drop below configured level)</li>
 *   <li>Can be disabled entirely</li>
 * </ul>
 *
 * <p>This system replaces LevelingCore's death-related XP loss.
 */
public class XpLossSystem extends DeathSystems.OnDeathSystem {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final TrailOfOrbis plugin;

    /** Creates a new XP loss system. */
    public XpLossSystem(@Nonnull TrailOfOrbis plugin) {
        this.plugin = plugin;
    }

    @Override
    @Nullable
    public Query<EntityStore> getQuery() {
        // Only process player deaths
        return Player.getComponentType();
    }

    @Override
    public void onComponentAdded(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull DeathComponent deathComponent,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        // Get leveling config
        LevelingConfig config = getLevelingConfig();
        if (config == null || !config.isEnabled() || !config.getXpLoss().isEnabled()) {
            return;
        }

        LevelingConfig.XpLossConfig lossConfig = config.getXpLoss();

        // Get player UUID
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) {
            return;
        }
        UUID playerId = playerRef.getUuid();

        // Trigger guide milestone for first death
        if (plugin.getGuideManager() != null) {
            plugin.getGuideManager().tryShow(playerId, io.github.larsonix.trailoforbis.guide.GuideMilestone.FIRST_DEATH);
        }

        // Get leveling service
        LevelingService levelingService = getLevelingService();
        if (levelingService == null) {
            return;
        }

        // Check minimum level protection
        int currentLevel = levelingService.getLevel(playerId);
        if (currentLevel <= lossConfig.getMinLevel()) {
            return; // Don't lose XP at or below minimum level
        }

        // Calculate XP loss based on PROGRESS within current level, not total XP
        // This prevents halving a player's total level on death
        long currentXp = levelingService.getXp(playerId);
        long xpForCurrentLevel = levelingService.getXpForLevel(currentLevel);
        long progressXp = currentXp - xpForCurrentLevel;

        // Only apply penalty to progress XP (XP earned since reaching current level)
        double lossPercentage = lossConfig.getPercentage();
        long xpToLose = (long) Math.ceil(progressXp * lossPercentage);

        if (xpToLose <= 0) {
            return; // No progress to lose (just leveled up or already at level threshold)
        }

        // Progress-based loss can never drop below current level's threshold,
        // but we still check minimum level protection for safety
        long minLevelXp = levelingService.getXpForLevel(lossConfig.getMinLevel());
        long xpAfterLoss = currentXp - xpToLose;
        if (xpAfterLoss < minLevelXp) {
            // Clamp loss to not drop below minimum level
            xpToLose = currentXp - minLevelXp;
            if (xpToLose <= 0) {
                return;
            }
        }

        // Apply XP loss
        levelingService.removeXp(playerId, xpToLose, XpSource.DEATH_PENALTY);

        // Debug logging
        if (plugin.getConfigManager().getRPGConfig().isDebugMode()) {
            LOGGER.at(Level.FINE).log(
                "XP loss on death: %d from %s (level %d, progress %d/%d, %.0f%% penalty, was %d total, now %d)",
                xpToLose, playerId, currentLevel, progressXp,
                levelingService.getXpForLevel(currentLevel + 1) - xpForCurrentLevel,
                lossPercentage * 100, currentXp, currentXp - xpToLose);
        }
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
     * Gets the leveling service.
     */
    @Nullable
    private LevelingService getLevelingService() {
        return plugin.getLevelingManager();
    }
}
