package io.github.larsonix.trailoforbis.api.services;

import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * Service interface for skill tree map visualization.
 *
 * <p>Manages the map-based skill tree UI using packet spoofing
 * to display custom content without player teleportation.
 *
 * <p>This service complements the existing {@link SkillTreeService}
 * which handles node allocation logic.
 */
public interface SkillTreeMapService {

    /** Sends custom map packets to display the skill tree. */
    void openMap(@Nonnull PlayerRef player);

    /** Closes the skill tree map and restores normal world map. */
    void closeMap(@Nonnull PlayerRef player);

    /** Toggles between skill tree map and normal map. */
    void toggleMap(@Nonnull PlayerRef player);

    boolean isInMapMode(@Nonnull UUID playerId);

    /** Only updates markers, not full chunks (more efficient). */
    void refreshMap(@Nonnull PlayerRef player);

    void onPlayerDisconnect(@Nonnull UUID playerId);
}
