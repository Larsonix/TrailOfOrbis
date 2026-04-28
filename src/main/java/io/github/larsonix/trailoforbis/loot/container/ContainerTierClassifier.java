package io.github.larsonix.trailoforbis.loot.container;

import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Classifies containers into tiers based on block type ID patterns.
 *
 * <p>Uses configurable wildcard patterns to match block types to tiers.
 * Patterns support:
 * <ul>
 *   <li>{@code *} - matches any sequence of characters</li>
 *   <li>Exact matches (case-insensitive)</li>
 * </ul>
 *
 * <h2>Pattern Examples</h2>
 * <pre>
 * "Chest"        - matches "Chest" exactly
 * "Dungeon_*"    - matches "Dungeon_Chest", "Dungeon_Barrel", etc.
 * "*_Boss"       - matches "Fire_Boss", "Ice_Boss", etc.
 * "*Treasure*"   - matches anything containing "Treasure"
 * </pre>
 *
 * <h2>Thread Safety</h2>
 * <p>This class is thread-safe. Pattern caching uses ConcurrentHashMap.
 *
 * @see ContainerTier
 * @see ContainerLootConfig.TierConfig
 */
public final class ContainerTierClassifier {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    // Cached compiled patterns per tier
    private final Map<ContainerTier, List<Pattern>> tierPatterns = new ConcurrentHashMap<>();

    // Classification cache (block type ID -> tier)
    private final Map<String, ContainerTier> classificationCache = new ConcurrentHashMap<>();

    // Config for tier definitions
    private final ContainerLootConfig config;

    /**
     * Creates a new tier classifier with the given config.
     *
     * @param config The container loot configuration
     */
    public ContainerTierClassifier(@Nonnull ContainerLootConfig config) {
        this.config = Objects.requireNonNull(config, "config cannot be null");
        compilePatterns();
    }

    /**
     * Compiles all tier patterns from config into regex patterns.
     */
    private void compilePatterns() {
        tierPatterns.clear();
        classificationCache.clear();

        for (Map.Entry<String, ContainerLootConfig.TierConfig> entry : config.getContainerTiers().entrySet()) {
            ContainerTier tier = ContainerTier.fromConfigKey(entry.getKey());
            List<Pattern> patterns = new ArrayList<>();

            for (String pattern : entry.getValue().getPatterns()) {
                try {
                    Pattern compiled = compileWildcardPattern(pattern);
                    patterns.add(compiled);
                } catch (Exception e) {
                    LOGGER.atWarning().withCause(e).log(
                        "Invalid pattern '%s' for tier %s", pattern, tier);
                }
            }

            if (!patterns.isEmpty()) {
                tierPatterns.put(tier, patterns);
                LOGGER.atFine().log("Compiled %d patterns for tier %s", patterns.size(), tier);
            }
        }
    }

    /**
     * Converts a wildcard pattern to a regex pattern.
     *
     * <p>Supports:
     * <ul>
     *   <li>{@code *} - matches any sequence of characters</li>
     *   <li>All other characters are escaped for literal matching</li>
     * </ul>
     *
     * @param wildcardPattern The wildcard pattern (e.g., "Dungeon_*")
     * @return Compiled case-insensitive regex pattern
     */
    @Nonnull
    private Pattern compileWildcardPattern(@Nonnull String wildcardPattern) {
        StringBuilder regex = new StringBuilder();
        regex.append("^"); // Start anchor

        for (int i = 0; i < wildcardPattern.length(); i++) {
            char c = wildcardPattern.charAt(i);
            if (c == '*') {
                regex.append(".*");
            } else if (c == '?') {
                regex.append(".");
            } else {
                // Escape regex special characters
                if ("\\[]{}().+^$|".indexOf(c) >= 0) {
                    regex.append("\\");
                }
                regex.append(c);
            }
        }

        regex.append("$"); // End anchor
        return Pattern.compile(regex.toString(), Pattern.CASE_INSENSITIVE);
    }

    /**
     * Classifies a container by its block type ID.
     *
     * <p>Checks patterns in tier priority order (BOSS, SPECIAL, DUNGEON, BASIC).
     * Results are cached for performance.
     *
     * @param blockTypeId The block type ID (e.g., "Dungeon_Chest")
     * @return The classified tier, defaults to BASIC if no pattern matches
     */
    @Nonnull
    public ContainerTier classify(@Nullable String blockTypeId) {
        if (blockTypeId == null || blockTypeId.isEmpty()) {
            return ContainerTier.BASIC;
        }

        // Check cache first
        ContainerTier cached = classificationCache.get(blockTypeId);
        if (cached != null) {
            return cached;
        }

        // Check each tier in priority order
        ContainerTier result = classifyUncached(blockTypeId);
        classificationCache.put(blockTypeId, result);

        LOGGER.atFine().log("Classified '%s' as %s tier", blockTypeId, result);
        return result;
    }

    /**
     * Classifies without using cache.
     */
    @Nonnull
    private ContainerTier classifyUncached(@Nonnull String blockTypeId) {
        // Check in priority order: BOSS > SPECIAL > DUNGEON > BASIC
        ContainerTier[] priorityOrder = {
            ContainerTier.BOSS,
            ContainerTier.SPECIAL,
            ContainerTier.DUNGEON,
            ContainerTier.BASIC
        };

        for (ContainerTier tier : priorityOrder) {
            List<Pattern> patterns = tierPatterns.get(tier);
            if (patterns != null) {
                for (Pattern pattern : patterns) {
                    if (pattern.matcher(blockTypeId).matches()) {
                        return tier;
                    }
                }
            }
        }

        return ContainerTier.BASIC;
    }

    /**
     * @return null if not configured
     */
    @Nullable
    public ContainerLootConfig.TierConfig getTierConfig(@Nonnull ContainerTier tier) {
        return config.getContainerTiers().get(tier.getConfigKey());
    }

    /**
     * Gets the effective loot multiplier for a tier.
     *
     * <p>Uses config value if available, otherwise falls back to tier default.
     *
     * @return 1.0 = baseline
     */
    public double getLootMultiplier(@Nonnull ContainerTier tier) {
        ContainerLootConfig.TierConfig tierConfig = getTierConfig(tier);
        if (tierConfig != null) {
            return tierConfig.getLootMultiplier();
        }
        return tier.getDefaultLootMultiplier();
    }

    /**
     * Gets the effective rarity bonus for a tier.
     *
     * <p>Uses config value if available, otherwise falls back to tier default.
     *
     * @return 0.0-1.0 range
     */
    public double getRarityBonus(@Nonnull ContainerTier tier) {
        ContainerLootConfig.TierConfig tierConfig = getTierConfig(tier);
        if (tierConfig != null) {
            return tierConfig.getRarityBonus();
        }
        return tier.getDefaultRarityBonus();
    }

    /**
     * Gets the map chance multiplier for a tier.
     *
     * @return 1.0 = baseline
     */
    public double getMapChanceMultiplier(@Nonnull ContainerTier tier) {
        ContainerLootConfig.TierConfig tierConfig = getTierConfig(tier);
        if (tierConfig != null) {
            return tierConfig.getMapChanceMultiplier();
        }
        return 1.0;
    }

    /**
     * Gets the stone chance multiplier for a tier.
     *
     * @return 1.0 = baseline
     */
    public double getStoneChanceMultiplier(@Nonnull ContainerTier tier) {
        ContainerLootConfig.TierConfig tierConfig = getTierConfig(tier);
        if (tierConfig != null) {
            return tierConfig.getStoneChanceMultiplier();
        }
        return 1.0;
    }

    /**
     * Gets the gear drop range for a tier.
     *
     * @return [min, max] gear drops
     */
    public int[] getGearDropRange(@Nonnull ContainerTier tier) {
        ContainerLootConfig.TierConfig tierConfig = getTierConfig(tier);
        if (tierConfig != null) {
            return new int[]{tierConfig.getMinGearDrops(), tierConfig.getMaxGearDrops()};
        }
        return new int[]{0, 2}; // Default
    }

    /**
     * Gets the total item count range for a tier (used in total replacement mode).
     *
     * @return [min, max] total items
     */
    public int[] getItemRange(@Nonnull ContainerTier tier) {
        ContainerLootConfig.TierConfig tierConfig = getTierConfig(tier);
        if (tierConfig != null) {
            return new int[]{tierConfig.getMinItems(), tierConfig.getMaxItems()};
        }
        return new int[]{3, 5}; // Default
    }

    /**
     * Checks if this tier guarantees rare or better loot.
     *
     * @return true if rare or better is guaranteed
     */
    public boolean isGuaranteedRareOrBetter(@Nonnull ContainerTier tier) {
        ContainerLootConfig.TierConfig tierConfig = getTierConfig(tier);
        if (tierConfig != null) {
            return tierConfig.isGuaranteedRareOrBetter();
        }
        return false;
    }

    /**
     * Clears the classification cache.
     *
     * <p>Call this after config reload to ensure new patterns are used.
     */
    public void clearCache() {
        classificationCache.clear();
    }

    /**
     * Reloads patterns from config.
     *
     * <p>Call this after config reload.
     */
    public void reload() {
        compilePatterns();
    }
}
