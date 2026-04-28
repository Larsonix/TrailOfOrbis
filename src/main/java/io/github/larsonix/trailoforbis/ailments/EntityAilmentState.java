package io.github.larsonix.trailoforbis.ailments;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

/**
 * Tracks all active ailments on a single entity.
 *
 * <p>Designed for efficient iteration and modification during tick processing.
 * Handles different stacking behaviors:
 * <ul>
 *   <li><b>Burn, Freeze, Shock:</b> Non-stacking (at most one instance, refreshes)</li>
 *   <li><b>Poison:</b> Stacking (multiple independent instances, up to max stacks)</li>
 * </ul>
 *
 * <p><b>Thread Safety:</b> This class is NOT thread-safe. It's designed for
 * single-threaded ECS tick access. The parent {@link AilmentTracker} handles
 * thread-safe access at the entity level.
 */
public class EntityAilmentState {

    /** Non-stacking ailments: at most one instance per type (Burn, Freeze, Shock) */
    private final EnumMap<AilmentType, AilmentState> singleAilments = new EnumMap<>(AilmentType.class);

    /** Stacking ailments (Poison): list of independent instances */
    private final List<AilmentState> poisonStacks = new ArrayList<>();

    /** Maximum poison stacks allowed */
    private static final int DEFAULT_MAX_POISON_STACKS = 10;

    private int maxPoisonStacks = DEFAULT_MAX_POISON_STACKS;

    public EntityAilmentState() {
    }

    /**
     * Sets the maximum poison stacks allowed.
     */
    public void setMaxPoisonStacks(int maxStacks) {
        this.maxPoisonStacks = Math.max(1, maxStacks);
    }

    /** @return Burn state or null if not burning */
    @Nullable
    public AilmentState getBurn() {
        return singleAilments.get(AilmentType.BURN);
    }

    /** @return Freeze state or null if not frozen */
    @Nullable
    public AilmentState getFreeze() {
        return singleAilments.get(AilmentType.FREEZE);
    }

    /** @return Shock state or null if not shocked */
    @Nullable
    public AilmentState getShock() {
        return singleAilments.get(AilmentType.SHOCK);
    }

    /** @return Unmodifiable list of poison stacks */
    @Nonnull
    public List<AilmentState> getPoisonStacks() {
        return Collections.unmodifiableList(poisonStacks);
    }

    /** @return Number of active poison stacks */
    public int getPoisonStackCount() {
        return poisonStacks.size();
    }

    /** @return Combined DPS from all poison stacks */
    public float getTotalPoisonDps() {
        float total = 0f;
        for (AilmentState stack : poisonStacks) {
            total += stack.magnitude();
        }
        return total;
    }

    /** @return true if the ailment is active */
    public boolean hasAilment(@Nonnull AilmentType type) {
        if (type == AilmentType.POISON) {
            return !poisonStacks.isEmpty();
        }
        return singleAilments.containsKey(type);
    }

    /** @return true if any ailment is active */
    public boolean hasAnyAilment() {
        return !singleAilments.isEmpty() || !poisonStacks.isEmpty();
    }

    /** @return Slow percentage (0-30), or 0 if not frozen */
    public float getFreezeSlowPercent() {
        AilmentState freeze = singleAilments.get(AilmentType.FREEZE);
        return freeze != null ? Math.min(freeze.magnitude(), 30f) : 0f;
    }

    /** @return Increased damage taken percentage (0-50), or 0 if not shocked */
    public float getShockDamageIncreasePercent() {
        AilmentState shock = singleAilments.get(AilmentType.SHOCK);
        return shock != null ? Math.min(shock.magnitude(), 50f) : 0f;
    }

    /**
     * Applies an ailment, handling stacking behavior appropriately.
     *
     * <p>Stacking behavior:
     * <ul>
     *   <li>Burn: Refreshes duration, takes stronger magnitude</li>
     *   <li>Freeze: Refreshes if new duration is longer</li>
     *   <li>Shock: Always overwrites</li>
     *   <li>Poison: Adds new stack if under limit</li>
     * </ul>
     *
     * @return true if applied, false if blocked (e.g., at max poison stacks)
     */
    public boolean applyAilment(@Nonnull AilmentState state) {
        AilmentType type = state.type();

        if (type == AilmentType.POISON) {
            // Stacking: add new stack if under limit
            if (poisonStacks.size() < maxPoisonStacks) {
                poisonStacks.add(state);
                return true;
            }
            return false; // At max stacks
        } else {
            // Non-stacking: refresh or apply new
            AilmentState existing = singleAilments.get(type);
            if (existing != null) {
                // Merge based on ailment type
                AilmentState merged = mergeAilment(existing, state);
                singleAilments.put(type, merged);
            } else {
                singleAilments.put(type, state);
            }
            return true;
        }
    }

    /** Merges an existing ailment with a new application. */
    private AilmentState mergeAilment(@Nonnull AilmentState existing, @Nonnull AilmentState incoming) {
        return switch (existing.type()) {
            case BURN -> {
                // Take stronger DPS, refresh duration
                yield existing.refresh(incoming.remainingDuration(), incoming.magnitude());
            }
            case FREEZE -> {
                // Take longer duration and stronger effect
                yield existing.refresh(incoming.remainingDuration(), incoming.magnitude());
            }
            case SHOCK -> {
                // Always take incoming (latest shock)
                yield incoming;
            }
            case POISON -> {
                // Poison stacks, shouldn't reach here
                throw new IllegalStateException("Poison should use stacking path");
            }
        };
    }

    /**
     * Ticks all ailments and calculates total DoT damage.
     *
     * <p>This method:
     * <ul>
     *   <li>Updates all ailment durations</li>
     *   <li>Removes expired ailments</li>
     *   <li>Returns total DoT damage to apply</li>
     * </ul>
     *
     * @return Total DoT damage from Burn + Poison this tick
     */
    public float tickAndGetDamage(float dt) {
        float totalDamage = 0f;

        // Tick single ailments (Burn, Freeze, Shock)
        for (var entry : new ArrayList<>(singleAilments.entrySet())) {
            AilmentState current = entry.getValue();
            AilmentState updated = current.afterTick(dt);

            if (updated.isExpired()) {
                singleAilments.remove(entry.getKey());
            } else {
                singleAilments.put(entry.getKey(), updated);

                // Accumulate Burn DoT
                if (entry.getKey() == AilmentType.BURN) {
                    totalDamage += updated.calculateDamageThisTick(dt);
                }
            }
        }

        // Tick poison stacks
        Iterator<AilmentState> it = poisonStacks.iterator();
        List<AilmentState> updatedStacks = new ArrayList<>();

        while (it.hasNext()) {
            AilmentState stack = it.next();
            AilmentState updated = stack.afterTick(dt);

            if (!updated.isExpired()) {
                updatedStacks.add(updated);
                totalDamage += updated.calculateDamageThisTick(dt);
            }
            // Expired stacks are not added to updatedStacks
        }

        // Replace poison list with updated stacks
        poisonStacks.clear();
        poisonStacks.addAll(updatedStacks);

        return totalDamage;
    }

    /**
     * Returns per-source DPS for all active DoT ailments.
     *
     * <p>Call this BEFORE {@link #tickAndGetDamage(float)} to snapshot source
     * attribution (tick may expire ailments, losing their source info).
     *
     * @return Map of source UUID → total DPS from that source's DoTs
     */
    @Nonnull
    public Map<UUID, Float> getDotDpsPerSource() {
        Map<UUID, Float> result = new HashMap<>();

        AilmentState burn = singleAilments.get(AilmentType.BURN);
        if (burn != null) {
            result.merge(burn.sourceUuid(), burn.magnitude(), Float::sum);
        }

        for (AilmentState poison : poisonStacks) {
            result.merge(poison.sourceUuid(), poison.magnitude(), Float::sum);
        }

        return result;
    }

    /**
     * Calculates the total remaining DoT damage from all active damage-over-time ailments.
     *
     * <p>For each DoT ailment (Burn, Poison stacks), remaining damage = magnitude × remainingDuration.
     *
     * @return Total remaining DoT damage, or 0 if no DoTs active
     */
    public float getRemainingDotDamage() {
        float total = 0f;

        AilmentState burn = singleAilments.get(AilmentType.BURN);
        if (burn != null) {
            total += burn.magnitude() * burn.remainingDuration();
        }

        for (AilmentState poison : poisonStacks) {
            total += poison.magnitude() * poison.remainingDuration();
        }

        return total;
    }

    /**
     * Removes all damage-over-time ailments (Burn and Poison), keeping non-DoT
     * ailments (Freeze, Shock) intact. Used by the DETONATE_DOT_ON_CRIT mechanic.
     */
    public void detonateAllDots() {
        singleAilments.remove(AilmentType.BURN);
        poisonStacks.clear();
    }

    /** Removes a specific ailment type. */
    public void removeAilment(@Nonnull AilmentType type) {
        if (type == AilmentType.POISON) {
            poisonStacks.clear();
        } else {
            singleAilments.remove(type);
        }
    }

    /**
     * Clears all ailments.
     */
    public void clearAll() {
        singleAilments.clear();
        poisonStacks.clear();
    }

    /** Gets all active ailments (for UI/debugging). */
    @Nonnull
    public List<AilmentState> getAllAilments() {
        List<AilmentState> all = new ArrayList<>();
        all.addAll(singleAilments.values());
        all.addAll(poisonStacks);
        return all;
    }

    /**
     * @return Number of distinct ailment types active (poison counts as one regardless of stacks)
     */
    public int getActiveAilmentCount() {
        int count = singleAilments.size();
        if (!poisonStacks.isEmpty()) {
            count++;
        }
        return count;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("EntityAilmentState{");
        boolean first = true;

        for (var entry : singleAilments.entrySet()) {
            if (!first) sb.append(", ");
            sb.append(entry.getValue());
            first = false;
        }

        if (!poisonStacks.isEmpty()) {
            if (!first) sb.append(", ");
            sb.append("Poison[").append(poisonStacks.size())
              .append(" stacks, ").append(String.format("%.1f", getTotalPoisonDps()))
              .append(" DPS]");
        }

        sb.append("}");
        return sb.toString();
    }
}
