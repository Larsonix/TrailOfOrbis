package io.github.larsonix.trailoforbis.guide;

import com.hypixel.hytale.logger.HytaleLogger;
import io.github.larsonix.trailoforbis.database.DataManager;

import javax.annotation.Nonnull;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Database repository for player guide milestone completion tracking.
 *
 * <p>Each milestone is shown once per player, ever. This repository
 * tracks which milestones a player has already seen.
 */
public class GuideRepository {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static final String SELECT_ALL =
        "SELECT milestone_id FROM rpg_guide_milestones WHERE player_uuid = ?";

    private static final String SELECT_ONE =
        "SELECT 1 FROM rpg_guide_milestones WHERE player_uuid = ? AND milestone_id = ?";

    private static final String INSERT =
        "INSERT INTO rpg_guide_milestones (player_uuid, milestone_id) VALUES (?, ?)";

    private static final String DELETE_ONE =
        "DELETE FROM rpg_guide_milestones WHERE player_uuid = ? AND milestone_id = ?";

    private final DataManager dataManager;

    public GuideRepository(@Nonnull DataManager dataManager) {
        this.dataManager = Objects.requireNonNull(dataManager, "dataManager");
    }

    /**
     * Loads all completed milestone IDs for a player.
     * Called once on player join, cached in memory by GuideManager.
     */
    @Nonnull
    public Set<String> getCompletedMilestones(@Nonnull UUID playerId) {
        Set<String> completed = new HashSet<>();

        try (Connection conn = dataManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SELECT_ALL)) {

            stmt.setString(1, playerId.toString());

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    completed.add(rs.getString("milestone_id"));
                }
            }

        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to load guide milestones for %s",
                playerId.toString().substring(0, 8));
        }

        return completed;
    }

    /**
     * Checks if a specific milestone is completed for a player.
     */
    public boolean isCompleted(@Nonnull UUID playerId, @Nonnull String milestoneId) {
        try (Connection conn = dataManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SELECT_ONE)) {

            stmt.setString(1, playerId.toString());
            stmt.setString(2, milestoneId);

            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }

        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to check milestone %s for %s",
                milestoneId, playerId.toString().substring(0, 8));
            return false;
        }
    }

    /**
     * Marks a milestone as completed for a player.
     * Idempotent: duplicate inserts are silently ignored.
     */
    public void markCompleted(@Nonnull UUID playerId, @Nonnull String milestoneId) {
        try (Connection conn = dataManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(INSERT)) {

            stmt.setString(1, playerId.toString());
            stmt.setString(2, milestoneId);
            stmt.executeUpdate();

            LOGGER.atFine().log("Marked milestone %s completed for %s",
                milestoneId, playerId.toString().substring(0, 8));

        } catch (SQLException e) {
            // Duplicate key is expected if milestone was already marked
            String msg = e.getMessage();
            if (msg != null && (msg.contains("Duplicate") || msg.contains("UNIQUE") || msg.contains("PRIMARY"))) {
                LOGGER.atFine().log("Milestone %s already completed for %s (duplicate insert)",
                    milestoneId, playerId.toString().substring(0, 8));
            } else {
                LOGGER.atWarning().withCause(e).log("Failed to mark milestone %s for %s",
                    milestoneId, playerId.toString().substring(0, 8));
            }
        }
    }

    /**
     * Removes a milestone completion record. Used by admin commands for testing.
     */
    public void deleteMilestone(@Nonnull UUID playerId, @Nonnull String milestoneId) {
        try (Connection conn = dataManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(DELETE_ONE)) {

            stmt.setString(1, playerId.toString());
            stmt.setString(2, milestoneId);
            stmt.executeUpdate();

        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to delete milestone %s for %s",
                milestoneId, playerId.toString().substring(0, 8));
        }
    }
}
