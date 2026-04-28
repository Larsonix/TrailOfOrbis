package io.github.larsonix.trailoforbis.mobs.classification;

import io.github.larsonix.trailoforbis.mobs.classification.provider.TagLookupProvider;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Service responsible for classifying NPCs into RPG classes.
 *
 * <p>Uses a simplified 4-class system (PASSIVE, HOSTILE, ELITE, BOSS) with
 * efficient LivingWorld group lookups instead of individual tag checks.
 *
 * <p><b>Classification Priority (with DynamicEntityRegistry):</b>
 * <ol>
 *   <li>Dynamic registry lookup (if enabled) - O(1) cached result</li>
 *   <li>Fallback to static classification if registry miss</li>
 * </ol>
 *
 * <p><b>Classification Priority (static fallback):</b>
 * <ol>
 *   <li>Role override (config: bosses/elites lists) → BOSS or ELITE</li>
 *   <li>Aggressive NPCGroup → HOSTILE</li>
 *   <li>Neutral NPCGroup → PASSIVE (livestock, non-aggressive wildlife)</li>
 *   <li>Passive NPCGroup → PASSIVE</li>
 *   <li>Attitude fallback (HOSTILE/NEUTRAL → HOSTILE, others → PASSIVE)</li>
 * </ol>
 */
public class MobClassificationService {

    // Hytale's LivingWorld meta-groups (filename-only keys, no path prefix)
    private static final String LIVING_WORLD_AGGRESSIVE = "Aggressive";
    private static final String LIVING_WORLD_NEUTRAL = "Neutral";
    private static final String LIVING_WORLD_PASSIVE = "Passive";

    private final MobClassificationConfig config;
    private final TagLookupProvider tagLookupProvider;

    // Optional dynamic registry for automatic mod compatibility
    private DynamicEntityRegistry registry;

    public MobClassificationService(@Nonnull MobClassificationConfig config, @Nonnull TagLookupProvider tagLookupProvider) {
        this.config = config;
        this.tagLookupProvider = tagLookupProvider;
    }

    /**
     * Sets the dynamic entity registry for O(1) classification lookups.
     *
     * @param registry The registry to use, or null to use static classification only
     */
    public void setRegistry(@Nullable DynamicEntityRegistry registry) {
        this.registry = registry;
    }

    /**
     * Gets the dynamic entity registry.
     *
     * @return The registry, or null if not set
     */
    @Nullable
    public DynamicEntityRegistry getRegistry() {
        return registry;
    }

    /**
     * Determines the RPG class based on the provided context.
     *
     * @param context The classification context containing mob details
     * @return The determined RPG class
     */
    @Nonnull
    public RPGMobClass classify(@Nonnull MobClassificationContext context) {
        String roleName = context.getRoleName();

        // 0. Check dynamic registry first (O(1) lookup with full pattern matching)
        if (registry != null && roleName != null) {
            DiscoveredRole discovered = registry.getDiscoveredRole(roleName);
            if (discovered != null) {
                return discovered.classification();
            }
        }

        // 1. Check explicit role overrides (Highest Priority for static fallback)
        if (roleName != null) {
            if (config.isBoss(roleName)) {
                return RPGMobClass.BOSS;
            }
            if (config.isElite(roleName)) {
                return RPGMobClass.ELITE;
            }
        }

        // 2. Check LivingWorld groups (3 fast bitset checks)
        int roleIndex = context.getRoleIndex();

        // Aggressive group: Trork, Goblin, Skeleton, Void, Zombie, Vermin, Predators, etc.
        if (tagLookupProvider.hasTag(LIVING_WORLD_AGGRESSIVE, roleIndex)) {
            return RPGMobClass.HOSTILE;
        }

        // Neutral group: Prey, PreyBig — livestock and non-aggressive wildlife.
        // These don't fight back (bears are in PredatorsBig → Aggressive, not here).
        if (tagLookupProvider.hasTag(LIVING_WORLD_NEUTRAL, roleIndex)) {
            return RPGMobClass.PASSIVE;
        }

        // Passive group: Critters, Birds, Aquatic - non-combat
        if (tagLookupProvider.hasTag(LIVING_WORLD_PASSIVE, roleIndex)) {
            return RPGMobClass.PASSIVE;
        }

        // 3. Attitude fallback for mobs not in LivingWorld groups
        if (context.getDefaultAttitude() != null) {
            switch (context.getDefaultAttitude()) {
                case HOSTILE:
                case NEUTRAL:
                    return RPGMobClass.HOSTILE;
                case FRIENDLY:
                case REVERED:
                case IGNORE:
                default:
                    return RPGMobClass.PASSIVE;
            }
        }

        return RPGMobClass.PASSIVE;
    }

    /**
     * Helper to classify by role name string alone.
     *
     * <p>With dynamic registry enabled, this returns full classification (including
     * HOSTILE and PASSIVE). Without registry, only checks config overrides.
     *
     * @param roleName The role name to check
     * @return The classification if found, null otherwise
     */
    @Nullable
    public RPGMobClass classifyByName(@Nullable String roleName) {
        if (roleName == null) return null;

        // Check dynamic registry first (full classification)
        if (registry != null) {
            RPGMobClass classification = registry.getClassification(roleName);
            if (classification != null) {
                return classification;
            }
        }

        // Fallback to static config overrides only
        if (config.isBoss(roleName)) return RPGMobClass.BOSS;
        if (config.isElite(roleName)) return RPGMobClass.ELITE;
        return null;
    }

    public MobClassificationConfig getConfig() {
        return config;
    }
}
