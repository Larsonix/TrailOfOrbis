package io.github.larsonix.trailoforbis.combat;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Set;

/**
 * Represents the type of attack for damage calculation modifiers.
 *
 * <p>Used to apply the appropriate damage bonuses:
 * <ul>
 *   <li>MELEE: Direct physical attacks (swords, fists, etc.) - uses meleeDamagePercent</li>
 *   <li>PROJECTILE: Ranged attacks (arrows, thrown items, etc.) - uses projectileDamagePercent</li>
 *   <li>AREA: Area of effect attacks (explosions, etc.) - uses areaDamagePercent</li>
 *   <li>UNKNOWN: Could not determine attack type - no type-specific bonus applied</li>
 * </ul>
 *
 * <p>Attack types can be detected from damage cause IDs using {@link #fromCauseId(String)}.
 * Each type defines keywords that, if present in the cause ID, identify that attack type.
 */
public enum AttackType {
    /**
     * Direct melee attack (sword, axe, fist, etc.)
     * Applies meleeDamagePercent bonus.
     *
     * <p>Detected by keywords: melee, slash, stab, punch, kick, bite, claw
     */
    MELEE("Melee", Set.of("melee", "slash", "stab", "punch", "kick", "bite", "claw")),

    /**
     * Ranged projectile attack (arrow, thrown weapon, etc.)
     * Applies projectileDamagePercent bonus.
     *
     * <p>Note: Projectile detection is primarily done via ProjectileComponent,
     * not cause ID matching. These keywords are fallback patterns.
     *
     * <p>Detected by keywords: projectile, arrow, bolt, thrown, shot
     */
    PROJECTILE("Projectile", Set.of("projectile", "arrow", "bolt", "thrown", "shot")),

    /**
     * Area of effect attack (explosion, sweep, etc.)
     * Applies areaDamagePercent bonus.
     *
     * <p>Detected by keywords: explosion, sweep, area, splash, shockwave, blast, nova
     */
    AREA("Area", Set.of("explosion", "sweep", "area", "splash", "shockwave", "blast", "nova")),

    /**
     * Unknown or unclassified attack type.
     * No type-specific bonus applied.
     */
    UNKNOWN("Unknown", Set.of());

    private final String displayName;
    private final Set<String> causeKeywords;

    AttackType(String displayName, Set<String> causeKeywords) {
        this.displayName = displayName;
        this.causeKeywords = causeKeywords;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * @return Immutable set of lowercase keywords
     */
    public Set<String> getCauseKeywords() {
        return causeKeywords;
    }

    /**
     * Checks if this attack type should apply a damage bonus.
     *
     * @return true if this type has a corresponding damage modifier
     */
    public boolean hasModifier() {
        return this != UNKNOWN;
    }

    /**
     * Checks if a damage cause ID matches this attack type.
     *
     * @param causeId The damage cause identifier (case-insensitive)
     * @return true if any of this type's keywords appear in the cause ID
     */
    public boolean matchesCause(@Nullable String causeId) {
        if (causeId == null || causeKeywords.isEmpty()) {
            return false;
        }
        String lowerCause = causeId.toLowerCase();
        for (String keyword : causeKeywords) {
            if (lowerCause.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Determines the attack type from a damage cause ID.
     *
     * <p>Checks each attack type's keywords against the cause ID.
     * Priority order: AREA, PROJECTILE, MELEE (first match wins).
     * Returns UNKNOWN if no keywords match.
     *
     * <p>Note: For projectile detection, prefer checking ProjectileComponent
     * directly rather than relying solely on cause ID matching.
     *
     * @param causeId The damage cause identifier (case-insensitive)
     * @return The matched attack type, or UNKNOWN if no match
     */
    @Nonnull
    public static AttackType fromCauseId(@Nullable String causeId) {
        if (causeId == null) {
            return UNKNOWN;
        }

        // Check in priority order: AREA first (most specific), then PROJECTILE, then MELEE
        if (AREA.matchesCause(causeId)) {
            return AREA;
        }
        if (PROJECTILE.matchesCause(causeId)) {
            return PROJECTILE;
        }
        if (MELEE.matchesCause(causeId)) {
            return MELEE;
        }

        return UNKNOWN;
    }
}
