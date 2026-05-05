package io.github.larsonix.trailoforbis.ailments.component;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * ECS component marking an entity as burning from an RPG ailment.
 *
 * <p>Attached by {@link io.github.larsonix.trailoforbis.combat.ailments.CombatAilmentApplicator}
 * when a Burn ailment is applied. Drives the {@link RpgBurnTickSystem} which queries
 * for entities with this component and fires DOT damage through the pipeline.
 *
 * <p>Non-stacking: reapplication refreshes duration and takes the stronger DPS.
 *
 * <p>The parallel {@link io.github.larsonix.trailoforbis.ailments.AilmentTracker} entry
 * remains the authoritative source for readers (shock amp, freeze slow, conditionals).
 * This component exists solely to drive ECS-efficient tick iteration.
 */
public class RpgBurnComponent implements Component<EntityStore> {

    public static final BuilderCodec<RpgBurnComponent> CODEC = BuilderCodec.builder(
            RpgBurnComponent.class, RpgBurnComponent::new
        )
        .append(new KeyedCodec<>("Dps", Codec.FLOAT),
                RpgBurnComponent::setDps, RpgBurnComponent::getDps).add()
        .append(new KeyedCodec<>("RemainingDuration", Codec.FLOAT),
                RpgBurnComponent::setRemainingDuration, RpgBurnComponent::getRemainingDuration).add()
        .append(new KeyedCodec<>("SourceUuid", Codec.UUID_STRING),
                RpgBurnComponent::setSourceUuid, RpgBurnComponent::getSourceUuid).add()
        .build();

    /** Registered component type — set during plugin init. */
    public static ComponentType<EntityStore, RpgBurnComponent> TYPE = null;

    private float dps;
    private float remainingDuration;
    private UUID sourceUuid;
    private float elapsedSinceTick;

    public RpgBurnComponent() {
        this.sourceUuid = new UUID(0L, 0L);
    }

    public RpgBurnComponent(float dps, float duration, @Nonnull UUID sourceUuid) {
        this.dps = dps;
        this.remainingDuration = duration;
        this.sourceUuid = sourceUuid;
    }

    /**
     * Refreshes the burn with new values (non-stacking: takes stronger DPS, longer duration).
     */
    public void refresh(float newDps, float newDuration, @Nonnull UUID newSourceUuid) {
        if (newDps > this.dps) {
            this.dps = newDps;
            this.sourceUuid = newSourceUuid;
        }
        this.remainingDuration = Math.max(this.remainingDuration, newDuration);
    }

    public boolean isExpired() {
        return remainingDuration <= 0;
    }

    // Getters/setters for codec and tick system
    public float getDps() { return dps; }
    public void setDps(float dps) { this.dps = dps; }
    public float getRemainingDuration() { return remainingDuration; }
    public void setRemainingDuration(float remainingDuration) { this.remainingDuration = remainingDuration; }
    @Nonnull public UUID getSourceUuid() { return sourceUuid; }
    public void setSourceUuid(@Nonnull UUID sourceUuid) { this.sourceUuid = sourceUuid; }
    public float getElapsedSinceTick() { return elapsedSinceTick; }
    public void setElapsedSinceTick(float elapsed) { this.elapsedSinceTick = elapsed; }

    @Override
    public Component<EntityStore> clone() {
        RpgBurnComponent copy = new RpgBurnComponent(dps, remainingDuration, sourceUuid);
        copy.elapsedSinceTick = this.elapsedSinceTick;
        return copy;
    }
}
