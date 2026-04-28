package io.github.larsonix.trailoforbis.combat.indicators.color;

import io.github.larsonix.trailoforbis.combat.DamageBreakdown;
import io.github.larsonix.trailoforbis.combat.DamageType;
import io.github.larsonix.trailoforbis.combat.indicators.CombatIndicatorService.CombatTextParams;
import io.github.larsonix.trailoforbis.elemental.ElementType;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;

/**
 * Resolves which {@link CombatTextProfile} to use for a given damage event.
 *
 * <p>Pure logic class — no Hytale API dependencies, fully unit-testable.
 *
 * <p>Resolution priority:
 * <ol>
 *   <li>Avoidance: missed → dodged → blocked → parried</li>
 *   <li>Primary element from {@link DamageBreakdown#getPrimaryElement()}</li>
 *   <li>DamageType fallback (magic, physical)</li>
 *   <li>Crit variant suffix ({@code _crit} vs {@code _normal})</li>
 *   <li>Fallback chain: element crit → element normal → physical variant → null</li>
 * </ol>
 */
public class CombatTextProfileResolver {

    private final Map<String, CombatTextProfile> profiles;

    /**
     * @param profiles Map of profile ID → profile (e.g., "fire_normal", "dodged")
     */
    public CombatTextProfileResolver(@Nonnull Map<String, CombatTextProfile> profiles) {
        this.profiles = profiles;
    }

    /**
     * Resolves which profile to use for a damage event.
     *
     * @param breakdown The damage breakdown (nullable for edge cases)
     * @param params The combat text flags (crit, avoidance)
     * @return The resolved profile, or null to use vanilla default (white)
     */
    @Nullable
    public CombatTextProfile resolve(
        @Nullable DamageBreakdown breakdown,
        @Nonnull CombatTextParams params
    ) {
        // 1. Avoidance has highest priority
        if (params.isMissed()) return profiles.get("missed");
        if (params.isDodged()) return profiles.get("dodged");
        if (params.isBlocked()) return profiles.get("blocked");
        if (params.isParried()) return profiles.get("parried");

        if (breakdown == null) return profiles.get("physical_normal");

        // 2. Determine element key
        String elementKey = resolveElementKey(breakdown);

        // 3. Crit variant
        String variant = params.isCrit() ? "_crit" : "_normal";

        // 4. Lookup with fallback chain
        String key = elementKey + variant;
        CombatTextProfile profile = profiles.get(key);

        // 5. Fallback: element normal (if crit variant not configured)
        if (profile == null && params.isCrit()) {
            profile = profiles.get(elementKey + "_normal");
        }

        // 6. Fallback: physical variant
        if (profile == null) {
            profile = profiles.get("physical" + variant);
        }

        // 7. Final fallback: physical_normal
        if (profile == null) {
            profile = profiles.get("physical_normal");
        }

        return profile;
    }

    /**
     * Determines the element key string from a damage breakdown by comparing
     * all damage sources and picking the most prominent one.
     *
     * <p>Resolution logic:
     * <ol>
     *   <li>If elemental damage exists, compare the highest elemental source
     *       against physical damage — the most prominent wins</li>
     *   <li>On ties, physical wins (stable default, avoids flickering)</li>
     *   <li>If no elemental damage exists, fall back to {@code damageType}
     *       (respects magic-typed spells, elemental DOTs, etc.)</li>
     * </ol>
     *
     * @return Element key: "fire", "water", "magic", "physical", etc.
     */
    @Nonnull
    private String resolveElementKey(@Nonnull DamageBreakdown breakdown) {
        // If there's elemental damage in the breakdown, compare it against physical
        if (breakdown.hasElementalDamage()) {
            // Find the highest elemental source
            float maxElemental = 0f;
            ElementType maxElement = null;
            for (var entry : breakdown.elementalDamage().entrySet()) {
                if (entry.getValue() > maxElemental) {
                    maxElemental = entry.getValue();
                    maxElement = entry.getKey();
                }
            }

            // Compare against physical — most prominent wins, ties go to physical
            float physical = breakdown.physicalDamage();
            if (maxElement != null && maxElemental > physical) {
                return maxElement.name().toLowerCase();
            }
            return "physical";
        }

        // No elemental damage — fall back to DamageType classification
        // (handles magic spells, elemental DOTs, etc.)
        DamageType damageType = breakdown.damageType();
        return switch (damageType) {
            case MAGIC -> "magic";
            case FIRE, WATER, LIGHTNING, EARTH, WIND, VOID ->
                damageType.name().toLowerCase();
            default -> "physical";
        };
    }
}
