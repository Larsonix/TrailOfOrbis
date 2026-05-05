package io.github.larsonix.trailoforbis.mobs.profile;

import io.github.larsonix.trailoforbis.elemental.ElementType;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Immutable resistance profile for a mob.
 *
 * <p>Contains per-element resistances (can be negative for weaknesses),
 * optional physical resistance, per-ailment threshold bonuses, and
 * poison immunity flag.
 *
 * <p>Created once during mob spawn via {@link ResistanceProfileResolver}
 * and never mutated.
 *
 * @see ResistanceProfileResolver
 */
public final class ResistanceProfile {

    /** Neutral profile — 0% all resistances, no ailment bonuses, no immunities. */
    public static final ResistanceProfile NEUTRAL = new ResistanceProfile(
        new EnumMap<>(ElementType.class), 0.0, Collections.emptyMap(), false
    );

    private final Map<ElementType, Double> resistances;
    private final double physicalResistance;
    private final Map<String, Double> ailmentThresholdBonuses;
    private final boolean poisonImmune;

    /**
     * Creates a new resistance profile.
     *
     * @param resistances             Per-element resistance percentages (can be negative)
     * @param physicalResistance      Physical damage resistance percentage
     * @param ailmentThresholdBonuses Per-ailment threshold bonus percentages (e.g., "burn_threshold" → 50.0)
     * @param poisonImmune            Whether this mob is immune to the Poison ailment
     */
    public ResistanceProfile(
            @Nonnull Map<ElementType, Double> resistances,
            double physicalResistance,
            @Nonnull Map<String, Double> ailmentThresholdBonuses,
            boolean poisonImmune) {
        // Defensive copy — immutable
        this.resistances = new EnumMap<>(ElementType.class);
        this.resistances.putAll(resistances);
        this.physicalResistance = physicalResistance;
        this.ailmentThresholdBonuses = Map.copyOf(ailmentThresholdBonuses);
        this.poisonImmune = poisonImmune;
    }

    /**
     * Gets the resistance for a specific element.
     *
     * @param element The element type
     * @return Resistance percentage (positive = resist, negative = weakness), 0.0 if not set
     */
    public double getResistance(@Nonnull ElementType element) {
        return resistances.getOrDefault(element, 0.0);
    }

    /**
     * Gets all elemental resistances.
     *
     * @return Unmodifiable map of element → resistance percentage
     */
    @Nonnull
    public Map<ElementType, Double> getResistances() {
        return Collections.unmodifiableMap(resistances);
    }

    public double getPhysicalResistance() {
        return physicalResistance;
    }

    /**
     * Gets the ailment threshold bonus for a specific ailment type.
     *
     * @param ailmentKey The ailment key (e.g., "burn_threshold", "freeze_threshold")
     * @return Bonus percentage, or 0.0 if no bonus
     */
    public double getAilmentThresholdBonus(@Nonnull String ailmentKey) {
        return ailmentThresholdBonuses.getOrDefault(ailmentKey, 0.0);
    }

    /**
     * Gets all ailment threshold bonuses.
     *
     * @return Unmodifiable map of ailment key → bonus percentage
     */
    @Nonnull
    public Map<String, Double> getAilmentThresholdBonuses() {
        return ailmentThresholdBonuses;
    }

    public boolean isPoisonImmune() {
        return poisonImmune;
    }

    /**
     * Checks if this mob has any non-zero resistances.
     */
    public boolean hasResistances() {
        return physicalResistance != 0.0 || resistances.values().stream().anyMatch(v -> v != 0.0);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("ResistanceProfile{");
        if (physicalResistance != 0.0) {
            sb.append("phys=").append(physicalResistance).append("%, ");
        }
        for (ElementType element : ElementType.values()) {
            double r = getResistance(element);
            if (r != 0.0) {
                sb.append(element.getDisplayName().toLowerCase()).append("=").append(r).append("%, ");
            }
        }
        if (poisonImmune) {
            sb.append("poisonImmune, ");
        }
        if (!ailmentThresholdBonuses.isEmpty()) {
            sb.append("ailments=").append(ailmentThresholdBonuses);
        }
        sb.append("}");
        return sb.toString();
    }
}
