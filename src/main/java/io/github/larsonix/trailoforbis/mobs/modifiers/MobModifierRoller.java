package io.github.larsonix.trailoforbis.mobs.modifiers;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Rolls modifiers for a mob at spawn time.
 *
 * <p>Uses level-gated pool selection, random draw with no duplicates,
 * and exclusion rules to prevent degenerate combinations.
 */
public class MobModifierRoller {

    private static final int MAX_REROLL_ATTEMPTS = 3;

    private final MobModifierConfig config;

    public MobModifierRoller(@Nonnull MobModifierConfig config) {
        this.config = config;
    }

    /**
     * Rolls modifiers for a mob based on its level and tier.
     *
     * @param mobLevel    Effective mob level (determines available pool via level gating)
     * @param tier        Rarity tier name: "elite", "boss", or "elite_boss"
     * @param seed        Random seed for deterministic rolling (from mob spawn)
     * @return Unmodifiable list of rolled modifiers (may be empty if tier has 0 count)
     */
    @Nonnull
    public List<ModifierType> roll(int mobLevel, @Nonnull String tier, long seed) {
        int count = config.getModifierCount(tier);
        if (count <= 0) {
            return List.of();
        }

        List<ModifierType> pool = ModifierType.availableAtLevel(mobLevel);
        if (pool.isEmpty()) {
            return List.of();
        }

        Random random = new Random(seed ^ 0x4D4F44494649L); // "MODIFI" as salt
        List<ModifierType> selected = new ArrayList<>(count);

        for (int slot = 0; slot < count; slot++) {
            ModifierType rolled = rollOne(pool, selected, random);
            if (rolled != null) {
                selected.add(rolled);
            }
        }

        return Collections.unmodifiableList(selected);
    }

    /**
     * Rolls a single modifier from the pool, respecting exclusions and no-duplicate rules.
     */
    @Nullable
    private ModifierType rollOne(
        @Nonnull List<ModifierType> pool,
        @Nonnull List<ModifierType> alreadySelected,
        @Nonnull Random random
    ) {
        // Build candidate list (exclude already-selected)
        List<ModifierType> candidates = new ArrayList<>(pool);
        candidates.removeAll(alreadySelected);

        if (candidates.isEmpty()) {
            return null;
        }

        for (int attempt = 0; attempt < MAX_REROLL_ATTEMPTS; attempt++) {
            ModifierType candidate = candidates.get(random.nextInt(candidates.size()));

            if (!isExcludedWith(candidate, alreadySelected)) {
                return candidate;
            }

            // Remove the excluded candidate and try again
            candidates.remove(candidate);
            if (candidates.isEmpty()) {
                return null;
            }
        }

        // Fallback: pick any remaining candidate (ignore exclusion)
        return candidates.get(random.nextInt(candidates.size()));
    }

    /**
     * Checks if a candidate modifier is excluded by any already-selected modifier.
     */
    private boolean isExcludedWith(@Nonnull ModifierType candidate, @Nonnull List<ModifierType> selected) {
        for (ModifierType existing : selected) {
            if (config.isExcluded(candidate, existing)) {
                return true;
            }
        }
        return false;
    }
}
