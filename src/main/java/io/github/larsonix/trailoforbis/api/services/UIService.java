package io.github.larsonix.trailoforbis.api.services;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Service interface for UI management operations.
 *
 * <p>Provides decoupled access to UI page management without requiring
 * direct dependency on the main plugin class.
 */
public interface UIService {

    void openAttributePage(
        @Nonnull PlayerRef player,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref
    );

    void openStatsPage(
        @Nonnull PlayerRef player,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref
    );

    void closePage(@Nonnull UUID playerId);

    void onPlayerDisconnect(@Nonnull UUID playerId);

    void openLootFilterPage(
        @Nonnull PlayerRef player,
        @Nonnull Store<EntityStore> store
    );

    /** @return null if no page is open */
    @Nullable
    String getOpenPageType(@Nonnull UUID playerId);
}
