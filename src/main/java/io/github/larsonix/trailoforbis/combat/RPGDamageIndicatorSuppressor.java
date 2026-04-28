package io.github.larsonix.trailoforbis.combat;

import java.util.Set;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

/**
 * Minimal suppressor that prevents vanilla EntityUIEvents from showing duplicate damage.
 *
 * <p>This system runs BEFORE vanilla EntityUIEvents and sets damage.setAmount(0)
 * for any damage that RPGDamageSystem has already displayed indicators for.
 *
 * <p>CRITICAL: This system overrides shouldProcessEvent() to process cancelled events
 * and all other events where indicators have been sent.
 */
public class RPGDamageIndicatorSuppressor extends DamageEventSystem {

    /**
     * CRITICAL: Override to allow processing cancelled events.
     * Without this, the ECS framework would skip this system entirely
     * when damage.isCancelled() is true.
     */
    @Override
    protected boolean shouldProcessEvent(@Nonnull Damage damage) {
        return true;  // Process ALL events, including cancelled ones
    }

    /**
     * Returns dependencies to ensure this system runs BEFORE vanilla EntityUIEvents.
     *
     * <p>This is critical for preventing duplicate damage indicators: our system
     * sets damage.setAmount(0) which prevents EntityUIEvents from showing damage.
     */
    @Nonnull
    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return Set.of(
            // Run BEFORE vanilla EntityUIEvents to set amount to 0 first
            new SystemDependency<>(Order.BEFORE, DamageSystems.EntityUIEvents.class),
            // Run BEFORE vanilla PlayerHitIndicators to suppress health alert
            new SystemDependency<>(Order.BEFORE, DamageSystems.PlayerHitIndicators.class)
        );
    }

    /**
     * Query.any() matches all entities that receive damage.
     */
    @Override
    public Query<EntityStore> getQuery() {
        return Query.any();
    }

    /**
     * Runs in InspectDamage group (same as vanilla PlayerHitIndicators).
     */
    @Override
    public SystemGroup<EntityStore> getGroup() {
        return DamageModule.get().getInspectDamageGroup();
    }

    /**
     * Suppresses vanilla damage indicators for RPG-processed damage.
     *
     * <p>If RPGDamageSystem has already sent damage indicators (INDICATORS_SENT=true),
     * this system zeros the damage amount to prevent vanilla EntityUIEvents from
     * showing a duplicate.
     */
    @Override
    public void handle(
        int index,
        @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull Damage damage
    ) {
        // Check if RPGDamageSystem has already sent indicators
        Boolean indicatorsSent = damage.getIfPresentMetaObject(RPGDamageSystem.INDICATORS_SENT);
        if (indicatorsSent != null && indicatorsSent) {
            // Zero the amount to prevent vanilla EntityUIEvents from showing duplicate
            damage.setAmount(0);
        }
    }
}
