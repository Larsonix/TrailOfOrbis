package io.github.larsonix.trailoforbis.systems;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.attributes.BaseStats;
import io.github.larsonix.trailoforbis.util.PlayerWorldCache;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * Production implementation of {@link StatProvider} that reads base stats from the live Hytale server.
 *
 * <p>Resolves the player's entity reference via {@link PlayerWorldCache} and delegates to
 * {@link VanillaStatReader} to extract vanilla stat values. Falls back to defaults if the
 * player entity is unavailable.
 */
public class HytaleStatProvider implements StatProvider {
    @Override
    @Nonnull
    public BaseStats getBaseStats(@Nonnull UUID playerId) {
        try {
            PlayerRef ref = PlayerWorldCache.findPlayerRef(playerId);
            if (ref != null) {
                Ref<EntityStore> entityRef = ref.getReference();
                if (entityRef != null && entityRef.isValid()) {
                    return VanillaStatReader.readBaseStats(entityRef.getStore(), entityRef);
                }
            }
        } catch (Exception ignored) {
            // Cache or entity might not be initialized
        }
        return BaseStats.defaults();
    }
}
