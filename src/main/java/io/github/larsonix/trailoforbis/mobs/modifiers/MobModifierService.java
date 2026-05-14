package io.github.larsonix.trailoforbis.mobs.modifiers;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

/**
 * Public API for the mob modifier system.
 * Registered in {@link io.github.larsonix.trailoforbis.api.services.ServiceRegistry}.
 */
public interface MobModifierService {

    /**
     * Returns the active modifiers on a mob, or empty list if none.
     */
    @Nonnull
    List<ModifierType> getModifiers(@Nonnull Ref<EntityStore> mobRef, @Nonnull Store<EntityStore> store);

    /**
     * Checks if a mob has a specific modifier.
     */
    boolean hasModifier(@Nonnull Ref<EntityStore> mobRef, @Nonnull Store<EntityStore> store, @Nonnull ModifierType type);

    /**
     * Returns the number of active modifiers on a mob.
     */
    int getModifierCount(@Nonnull Ref<EntityStore> mobRef, @Nonnull Store<EntityStore> store);

    /**
     * Returns the modifier component on a mob, or null if none.
     */
    @Nullable
    MobModifierComponent getComponent(@Nonnull Ref<EntityStore> mobRef, @Nonnull Store<EntityStore> store);

    /**
     * Whether the modifier system is enabled.
     */
    boolean isEnabled();
}
