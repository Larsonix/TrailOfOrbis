package io.github.larsonix.trailoforbis.ailments.component;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * ECS component marking an entity as poisoned from an RPG ailment.
 *
 * <p>Attached by {@link io.github.larsonix.trailoforbis.combat.ailments.CombatAilmentApplicator}
 * when the first Poison stack is applied. Drives the {@link RpgPoisonTickSystem} which queries
 * for entities with this component and fires combined DOT damage through the pipeline.
 *
 * <p>Stacking: up to {@code maxStacks} independent stacks, each with its own DPS and duration.
 * Component is removed when the last stack expires.
 *
 * <p>The parallel {@link io.github.larsonix.trailoforbis.ailments.AilmentTracker} entry
 * remains the authoritative source for readers (stack count, total DPS, remaining damage).
 * This component exists solely to drive ECS-efficient tick iteration.
 */
public class RpgPoisonComponent implements Component<EntityStore> {

    // Codec serializes stack count + primary source for save/load.
    // Individual stack details are transient — stacks resume with current DPS on load.
    public static final BuilderCodec<RpgPoisonComponent> CODEC = BuilderCodec.builder(
            RpgPoisonComponent.class, RpgPoisonComponent::new
        )
        .append(new KeyedCodec<>("TotalDps", Codec.FLOAT),
                RpgPoisonComponent::setTotalDps, RpgPoisonComponent::getTotalDps).add()
        .append(new KeyedCodec<>("PrimarySourceUuid", Codec.UUID_STRING),
                RpgPoisonComponent::setPrimarySourceUuid, RpgPoisonComponent::getPrimarySourceUuid).add()
        .build();

    /** Registered component type — set during plugin init. */
    public static ComponentType<EntityStore, RpgPoisonComponent> TYPE = null;

    /** Independent poison stacks with their own DPS and duration. */
    private final List<PoisonStack> stacks = new ArrayList<>();

    private float elapsedSinceTick;

    public RpgPoisonComponent() {
    }

    /**
     * Adds a new poison stack.
     *
     * @param dps      Damage per second for this stack
     * @param duration Duration in seconds
     * @param source   UUID of the entity that applied this stack
     * @param maxStacks Maximum stacks allowed
     * @return true if added, false if at max stacks
     */
    public boolean addStack(float dps, float duration, @Nonnull UUID source, int maxStacks) {
        if (stacks.size() >= maxStacks) {
            return false;
        }
        stacks.add(new PoisonStack(dps, duration, source));
        return true;
    }

    /**
     * Ticks all stacks, decrementing duration and removing expired ones.
     *
     * @param dt Delta time in seconds
     */
    public void tickStacks(float dt) {
        Iterator<PoisonStack> it = stacks.iterator();
        while (it.hasNext()) {
            PoisonStack stack = it.next();
            stack.remainingDuration -= dt;
            if (stack.remainingDuration <= 0) {
                it.remove();
            }
        }
    }

    /**
     * Calculates total poison damage for this tick across all stacks.
     */
    public float calculateDamageThisTick(float dt) {
        float total = 0f;
        for (PoisonStack stack : stacks) {
            total += stack.dps * dt;
        }
        return total;
    }

    /**
     * @return UUID of the first stack's source (for kill attribution)
     */
    @Nonnull
    public UUID getPrimarySourceUuid() {
        return stacks.isEmpty() ? new UUID(0L, 0L) : stacks.get(0).sourceUuid;
    }

    public void setPrimarySourceUuid(@Nonnull UUID uuid) {
        // Used by codec on load — stacks may not exist yet
    }

    public boolean isEmpty() {
        return stacks.isEmpty();
    }

    public int getStackCount() {
        return stacks.size();
    }

    // For codec serialization
    public float getTotalDps() {
        float total = 0f;
        for (PoisonStack stack : stacks) {
            total += stack.dps;
        }
        return total;
    }

    public void setTotalDps(float dps) {
        // Used by codec on load — stacks are rebuilt from tracker
    }

    /**
     * @return The longest remaining duration among all active stacks, or 0 if empty
     */
    public float getLongestRemainingDuration() {
        float longest = 0f;
        for (PoisonStack stack : stacks) {
            if (stack.remainingDuration > longest) {
                longest = stack.remainingDuration;
            }
        }
        return longest;
    }

    public float getElapsedSinceTick() { return elapsedSinceTick; }
    public void setElapsedSinceTick(float elapsed) { this.elapsedSinceTick = elapsed; }

    @Override
    public Component<EntityStore> clone() {
        RpgPoisonComponent copy = new RpgPoisonComponent();
        copy.elapsedSinceTick = this.elapsedSinceTick;
        for (PoisonStack stack : stacks) {
            copy.stacks.add(new PoisonStack(stack.dps, stack.remainingDuration, stack.sourceUuid));
        }
        return copy;
    }

    /**
     * Single poison stack with independent DPS, duration, and source.
     */
    public static class PoisonStack {
        float dps;
        float remainingDuration;
        @Nonnull UUID sourceUuid;

        PoisonStack(float dps, float remainingDuration, @Nonnull UUID sourceUuid) {
            this.dps = dps;
            this.remainingDuration = remainingDuration;
            this.sourceUuid = sourceUuid;
        }
    }
}
