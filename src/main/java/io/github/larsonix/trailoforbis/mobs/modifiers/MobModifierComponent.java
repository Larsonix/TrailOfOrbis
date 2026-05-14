package io.github.larsonix.trailoforbis.mobs.modifiers;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.TrailOfOrbis;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * ECS component storing active modifiers on a mob.
 *
 * <p>Attached to Elite, Boss, and Elite Boss mobs at spawn time.
 * Stores modifier names as a comma-separated string for forward
 * compatibility — adding/removing/reordering ModifierType enum
 * values won't break saved entities.
 *
 * <p>Transient state (resolved modifiers, timestamps, summon refs)
 * is NOT serialized — it's reconstructed on load via {@link #resolveModifiers()}.
 */
public class MobModifierComponent implements Component<EntityStore> {

    // ==================== CODEC ====================

    public static final BuilderCodec<MobModifierComponent> CODEC = BuilderCodec.builder(
            MobModifierComponent.class, MobModifierComponent::new
        )
        .append(new KeyedCodec<>("Modifiers", Codec.STRING),
                MobModifierComponent::setModifierString,
                c -> Objects.requireNonNullElse(c.getModifierString(), "")).add()
        .append(new KeyedCodec<>("EnrageTriggered", Codec.BOOLEAN),
                MobModifierComponent::setEnrageTriggered,
                MobModifierComponent::isEnrageTriggered).add()
        .append(new KeyedCodec<>("SummonT60", Codec.BOOLEAN),
                MobModifierComponent::setSummonThreshold60Triggered,
                MobModifierComponent::isSummonThreshold60Triggered).add()
        .append(new KeyedCodec<>("SummonT30", Codec.BOOLEAN),
                MobModifierComponent::setSummonThreshold30Triggered,
                MobModifierComponent::isSummonThreshold30Triggered).add()
        .build();

    // ==================== Serialized Fields ====================

    /** Comma-separated modifier names (e.g., "BLAZING,ENRAGED") */
    private String modifierString = "";

    /** Enrage threshold already triggered (one-shot, persistent) */
    private boolean enrageTriggered = false;

    /** Summoner 60% HP threshold already triggered */
    private boolean summonThreshold60Triggered = false;

    /** Summoner 30% HP threshold already triggered */
    private boolean summonThreshold30Triggered = false;

    // ==================== Transient State ====================

    /** Resolved modifier types (reconstructed from modifierString) */
    private List<ModifierType> modifiers = List.of();

    /** Last time this mob took damage (for Regenerating idle check) */
    private long lastDamageTimestamp = 0;

    /** Entity refs of summoned minions (for cleanup on death) */
    private final List<Ref<EntityStore>> summonedMinions = new ArrayList<>();

    /** Last time Thunderous fired a lightning strike (cooldown tracking) */
    private long lastLightningTimestamp = 0;

    /** Last recorded position for Blazing trail (to detect movement) */
    @Nullable
    private com.hypixel.hytale.math.vector.Vector3d lastTrailPosition = null;

    // ==================== Constructors ====================

    public MobModifierComponent() {
    }

    private MobModifierComponent(@Nonnull MobModifierComponent other) {
        this.modifierString = other.modifierString;
        this.enrageTriggered = other.enrageTriggered;
        this.summonThreshold60Triggered = other.summonThreshold60Triggered;
        this.summonThreshold30Triggered = other.summonThreshold30Triggered;
        this.modifiers = other.modifiers;
        this.lastDamageTimestamp = other.lastDamageTimestamp;
        this.lastLightningTimestamp = other.lastLightningTimestamp;
        this.lastTrailPosition = other.lastTrailPosition;
        // summonedMinions are entity-specific, don't clone
    }

    // ==================== Static Accessor ====================

    @Nonnull
    public static ComponentType<EntityStore, MobModifierComponent> getComponentType() {
        return TrailOfOrbis.getInstance().getMobModifierComponentType();
    }

    // ==================== Modifier Resolution ====================

    /**
     * Sets modifiers from a list of types (used at spawn time).
     * Updates both the transient list and the serialized string.
     */
    public void setModifiers(@Nonnull List<ModifierType> types) {
        this.modifiers = List.copyOf(types);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < types.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(types.get(i).name());
        }
        this.modifierString = sb.toString();
    }

    /**
     * Reconstructs the transient modifier list from the serialized string.
     * Called on world load when the component is deserialized.
     */
    public void resolveModifiers() {
        if (modifierString == null || modifierString.isEmpty()) {
            this.modifiers = List.of();
            return;
        }
        List<ModifierType> resolved = new ArrayList<>();
        for (String name : modifierString.split(",")) {
            ModifierType type = ModifierType.fromName(name.trim());
            if (type != null) {
                resolved.add(type);
            }
        }
        this.modifiers = List.copyOf(resolved);
    }

    // ==================== Queries ====================

    @Nonnull
    public List<ModifierType> getModifiers() {
        return modifiers;
    }

    public boolean hasModifier(@Nonnull ModifierType type) {
        return modifiers.contains(type);
    }

    public int modifierCount() {
        return modifiers.size();
    }

    /** True if any modifier needs the tick system. */
    public boolean hasAnyTickable() {
        for (ModifierType mod : modifiers) {
            if (mod.requiresTick()) return true;
        }
        return false;
    }

    /** True if any modifier has a death trigger. */
    public boolean hasAnyDeathTrigger() {
        for (ModifierType mod : modifiers) {
            if (mod.hasDeathTrigger()) return true;
        }
        return false;
    }

    // ==================== Threshold Flags ====================

    public boolean isEnrageTriggered() { return enrageTriggered; }
    public void setEnrageTriggered(boolean triggered) { this.enrageTriggered = triggered; }

    public boolean isSummonThreshold60Triggered() { return summonThreshold60Triggered; }
    public void setSummonThreshold60Triggered(boolean triggered) { this.summonThreshold60Triggered = triggered; }

    public boolean isSummonThreshold30Triggered() { return summonThreshold30Triggered; }
    public void setSummonThreshold30Triggered(boolean triggered) { this.summonThreshold30Triggered = triggered; }

    // ==================== Damage Tracking ====================

    public long getLastDamageTimestamp() { return lastDamageTimestamp; }
    public void setLastDamageTimestamp(long timestamp) { this.lastDamageTimestamp = timestamp; }
    public void markDamaged() { this.lastDamageTimestamp = System.currentTimeMillis(); }

    // ==================== Thunderous Tracking ====================

    public long getLastLightningTimestamp() { return lastLightningTimestamp; }
    public void setLastLightningTimestamp(long timestamp) { this.lastLightningTimestamp = timestamp; }

    // ==================== Blazing Trail Tracking ====================

    @Nullable
    public com.hypixel.hytale.math.vector.Vector3d getLastTrailPosition() { return lastTrailPosition; }
    public void setLastTrailPosition(@Nullable com.hypixel.hytale.math.vector.Vector3d pos) { this.lastTrailPosition = pos; }

    // ==================== Summon Tracking ====================

    @Nonnull
    public List<Ref<EntityStore>> getSummonedMinions() { return summonedMinions; }

    public void addSummonedMinion(@Nonnull Ref<EntityStore> ref) { summonedMinions.add(ref); }

    public int activeSummonCount() {
        summonedMinions.removeIf(ref -> !ref.isValid());
        return summonedMinions.size();
    }

    // ==================== Codec Helpers ====================

    @Nonnull
    String getModifierString() { return modifierString; }
    void setModifierString(@Nonnull String s) { this.modifierString = s; }

    // ==================== Component Interface ====================

    @Override
    @Nonnull
    public Component<EntityStore> clone() {
        return new MobModifierComponent(this);
    }
}
