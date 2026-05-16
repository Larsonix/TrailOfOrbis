package io.github.larsonix.trailoforbis.ailments.component;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * Resolves a damage source UUID to a {@link Damage.Source} that is safe to use
 * within the current world's tick context.
 *
 * <p>Critical safety invariant: the returned {@link Damage.EntitySource} ref must
 * belong to the <em>same</em> world/store as the entity being damaged. A cross-world
 * ref causes {@code Store.assertThread()} crashes when other mods' event handlers
 * access the attacker's components on the wrong thread.
 */
public final class DotSourceResolver {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private DotSourceResolver() {}

    /**
     * Resolves a source entity UUID to a {@link Damage.Source}, constrained to the
     * current world's store.
     *
     * <p>Two-tier lookup:
     * <ol>
     *   <li>PlayerRef (global) with same-world validation</li>
     *   <li>Local store UUID registry (always same-world by definition)</li>
     * </ol>
     *
     * <p>Falls back to {@link Damage#NULL_SOURCE} if the source entity is not in
     * the current world (e.g., player teleported to a different world).
     */
    @Nonnull
    public static Damage.Source resolveSource(@Nonnull UUID sourceUuid, @Nonnull Store<EntityStore> store) {
        // Tier 1: PlayerRef lookup with same-world guard
        try {
            PlayerRef playerRef = Universe.get().getPlayer(sourceUuid);
            if (playerRef != null) {
                World tickWorld = store.getExternalData().getWorld();
                World playerWorld = Universe.get().getWorld(playerRef.getWorldUuid());
                if (tickWorld != null && tickWorld == playerWorld) {
                    Ref<EntityStore> ref = playerRef.getReference();
                    if (ref != null && ref.isValid()) {
                        return new Damage.EntitySource(ref);
                    }
                } else {
                    LOGGER.atFine().log(
                            "DOT source %s is in a different world, using NULL_SOURCE",
                            sourceUuid);
                }
            }
        } catch (Exception ignored) {}

        // Tier 2: Local store UUID registry — validate store membership to prevent cross-store crashes
        try {
            Ref<EntityStore> ref = store.getExternalData().getRefFromUUID(sourceUuid);
            if (ref != null && ref.isValid() && ref.getStore() == store) {
                return new Damage.EntitySource(ref);
            }
        } catch (Exception ignored) {}

        return Damage.NULL_SOURCE;
    }
}
