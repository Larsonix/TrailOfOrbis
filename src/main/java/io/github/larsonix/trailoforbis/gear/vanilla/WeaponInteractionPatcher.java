package io.github.larsonix.trailoforbis.gear.vanilla;

import com.hypixel.hytale.assetstore.AssetExtraInfo;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Auto-patches weapons that ship without InteractionVars (damage definitions).
 *
 * <h2>Problem</h2>
 * <p>Some vanilla and modded weapons define a parent template (e.g., {@code Template_Weapon_Sword})
 * which provides the interaction chain (swing left, swing right, thrust, guard, signature).
 * But they never define {@code InteractionVars} — the per-weapon overrides that supply damage
 * values to the chain's {@code ReplaceInteraction} nodes. When the chain executes, it looks
 * up the variable, finds nothing, and the interaction fails silently. No damage event fires.
 *
 * <h2>Solution</h2>
 * <p>At startup, scan all weapon Items in the asset map. Group by parent template.
 * For each template group, find a "donor" — a working sibling weapon with valid
 * InteractionVars. Copy the donor's InteractionVars to each broken sibling via
 * reflection. The actual damage values are irrelevant (our RPG system overrides them),
 * but valid RootInteraction references are required for the engine to fire the damage
 * events that our system intercepts.
 *
 * <h2>Safety</h2>
 * <ul>
 *   <li>Additive only — never overwrites existing InteractionVars</li>
 *   <li>Runs once at startup (Phase 3.5) — no runtime mutation</li>
 *   <li>Creates new maps, never mutates returned unmodifiable maps</li>
 *   <li>Clears cachedPacket after patching to force toPacket() rebuild</li>
 *   <li>Validates all RootInteraction IDs resolve before applying</li>
 *   <li>Configurable exclusions for mod weapons, NPC items, cosmetics</li>
 * </ul>
 */
public final class WeaponInteractionPatcher {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    // Reflection fields (cached)
    private final Field interactionVarsField;
    private final Field cachedPacketField;

    // Config
    private final boolean enabled;
    private final Set<String> patchedFamilies;
    private final List<String> excludedPrefixes;
    private final List<String> excludedPatterns;
    private final Set<String> excludedItems;

    /**
     * Creates the patcher with configuration loaded from the given path.
     *
     * @param configDir The config directory (mods/trailoforbis_TrailOfOrbis/config/)
     * @param classLoader The plugin class loader (for default config resource)
     */
    public WeaponInteractionPatcher(@Nonnull Path configDir, @Nonnull ClassLoader classLoader) {
        // Load config
        Map<String, Object> config = loadConfig(configDir, classLoader);
        this.enabled = getBoolean(config, "enabled", true);
        this.patchedFamilies = new HashSet<>(getStringList(config, "patched_families"));
        this.excludedPrefixes = getStringList(config, "excluded_prefixes");
        this.excludedPatterns = getStringList(config, "excluded_patterns");
        this.excludedItems = new HashSet<>(getStringList(config, "excluded_items"));

        // Cache reflection fields
        Field ivField = null;
        Field cpField = null;
        try {
            ivField = Item.class.getDeclaredField("interactionVars");
            ivField.setAccessible(true);
            cpField = Item.class.getDeclaredField("cachedPacket");
            cpField.setAccessible(true);
        } catch (NoSuchFieldException e) {
            LOGGER.atSevere().withCause(e).log(
                    "Failed to initialize reflection for WeaponInteractionPatcher — patching disabled");
        }
        this.interactionVarsField = ivField;
        this.cachedPacketField = cpField;
    }

    /**
     * Scans all weapons and patches those with missing InteractionVars.
     *
     * <p>Call once during plugin startup (Phase 3.5), after all assets are loaded.
     *
     * @return The number of weapons patched
     */
    public int patchAll() {
        if (!enabled) {
            LOGGER.atInfo().log("Weapon interaction patching is disabled");
            return 0;
        }

        if (interactionVarsField == null || cachedPacketField == null) {
            LOGGER.atWarning().log("Reflection not initialized — skipping weapon patching");
            return 0;
        }

        long startTime = System.currentTimeMillis();
        DefaultAssetMap<String, Item> assetMap = Item.getAssetMap();

        // Phase 1: Scan and group weapons by parent template
        Map<String, List<Item>> templateGroups = new LinkedHashMap<>();
        int scannedWeapons = 0;
        int skippedExcluded = 0;

        for (Item item : assetMap.getAssetMap().values()) {
            if (!isWeapon(item)) {
                continue;
            }
            scannedWeapons++;

            String itemId = item.getId();

            // Skip templates themselves
            if (itemId.startsWith("Template_")) {
                continue;
            }

            // Check exclusions
            if (isExcluded(itemId)) {
                skippedExcluded++;
                continue;
            }

            // Check weapon family
            String family = resolveFamily(item);
            if (family == null || !patchedFamilies.contains(family)) {
                continue;
            }

            // Group by parent template
            String parentKey = resolveParentKey(item);
            if (parentKey == null) {
                continue;
            }

            templateGroups.computeIfAbsent(parentKey, _ -> new ArrayList<>()).add(item);
        }

        // Phase 2: For each template group, find donor and patch broken siblings
        int totalPatched = 0;
        int totalBroken = 0;
        int noDonor = 0;
        Map<String, Integer> patchedPerFamily = new LinkedHashMap<>();

        for (Map.Entry<String, List<Item>> entry : templateGroups.entrySet()) {
            String templateId = entry.getKey();
            List<Item> siblings = entry.getValue();

            // Separate into donors (have InteractionVars) and broken (don't)
            Item donor = null;
            List<Item> broken = new ArrayList<>();

            for (Item sibling : siblings) {
                Map<String, String> vars = getInteractionVars(sibling);
                if (vars != null && !vars.isEmpty()) {
                    // First working sibling becomes donor
                    if (donor == null) {
                        donor = sibling;
                    }
                } else {
                    broken.add(sibling);
                }
            }

            if (broken.isEmpty()) {
                continue;
            }

            totalBroken += broken.size();

            if (donor == null) {
                noDonor += broken.size();
                LOGGER.atWarning().log(
                        "Template %s has %d broken weapons but NO working donor — cannot patch: %s",
                        templateId, broken.size(),
                        broken.stream().map(Item::getId).toList());
                continue;
            }

            Map<String, String> donorVars = getInteractionVars(donor);

            // Validate donor's RootInteraction references
            if (!validateInteractionVars(donorVars, donor.getId())) {
                LOGGER.atWarning().log(
                        "Donor %s has invalid RootInteraction references — skipping template %s",
                        donor.getId(), templateId);
                continue;
            }

            // Patch each broken sibling
            String family = resolveFamily(donor);
            for (Item brokenItem : broken) {
                if (applyPatch(brokenItem, donorVars)) {
                    totalPatched++;
                    patchedPerFamily.merge(family != null ? family : "Unknown", 1, Integer::sum);
                    LOGGER.atFine().log("Patched %s using donor %s (family: %s)",
                            brokenItem.getId(), donor.getId(), templateId);
                }
            }
        }

        long elapsed = System.currentTimeMillis() - startTime;

        // Summary log
        if (totalPatched > 0) {
            StringBuilder families = new StringBuilder();
            patchedPerFamily.forEach((f, count) -> {
                if (!families.isEmpty()) families.append(", ");
                families.append(f).append("=").append(count);
            });
            LOGGER.atInfo().log(
                    "Weapon interaction patcher: patched %d/%d broken weapons across %d families in %dms [%s]%s",
                    totalPatched, totalBroken, templateGroups.size(), elapsed, families,
                    noDonor > 0 ? String.format(" (%d skipped: no donor)", noDonor) : "");
        } else if (totalBroken > 0) {
            LOGGER.atWarning().log(
                    "Weapon interaction patcher: found %d broken weapons but patched 0 (no valid donors) in %dms",
                    totalBroken, elapsed);
        } else {
            LOGGER.atInfo().log(
                    "Weapon interaction patcher: scanned %d weapons, all have InteractionVars (%dms, %d excluded)",
                    scannedWeapons, elapsed, skippedExcluded);
        }

        return totalPatched;
    }

    // =========================================================================
    // PATCHING
    // =========================================================================

    /**
     * Applies InteractionVars from a donor to a broken weapon via reflection.
     *
     * <p>Creates a new unmodifiable map (never mutates shared references).
     * Clears cachedPacket to force toPacket() rebuild.
     *
     * @param target The broken weapon Item to patch
     * @param donorVars The donor's InteractionVars map to copy
     * @return true if patching succeeded
     */
    private boolean applyPatch(@Nonnull Item target, @Nonnull Map<String, String> donorVars) {
        try {
            // Double-check: don't overwrite existing vars (additive-only)
            Map<String, String> currentVars = getInteractionVars(target);
            if (currentVars != null && !currentVars.isEmpty()) {
                return false;
            }

            // Create a new unmodifiable map with the donor's entries
            Map<String, String> patchedVars = Collections.unmodifiableMap(new HashMap<>(donorVars));

            // Reflect-set the interactionVars field
            interactionVarsField.set(target, patchedVars);

            // Clear cachedPacket to force toPacket() rebuild
            cachedPacketField.set(target, null);

            return true;
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to patch InteractionVars on %s", target.getId());
            return false;
        }
    }

    // =========================================================================
    // VALIDATION
    // =========================================================================

    /**
     * Validates that all RootInteraction IDs in the map resolve to actual assets.
     *
     * @param vars The InteractionVars map to validate
     * @param ownerId The weapon ID (for logging)
     * @return true if all IDs resolve
     */
    private boolean validateInteractionVars(
            @Nullable Map<String, String> vars,
            @Nonnull String ownerId) {
        if (vars == null || vars.isEmpty()) {
            return false;
        }

        var rootMap = RootInteraction.getAssetMap();
        for (Map.Entry<String, String> entry : vars.entrySet()) {
            String varName = entry.getKey();
            String rootId = entry.getValue();
            if (rootMap.getAsset(rootId) == null) {
                LOGGER.atWarning().log(
                        "InteractionVar '%s' on %s references unknown RootInteraction '%s'",
                        varName, ownerId, rootId);
                return false;
            }
        }
        return true;
    }

    // =========================================================================
    // WEAPON DETECTION & CLASSIFICATION
    // =========================================================================

    /**
     * Checks if an Item is a weapon (has Weapon field or weapon Tags.Type).
     */
    private boolean isWeapon(@Nonnull Item item) {
        // Primary check: has Weapon component
        if (item.getWeapon() != null) {
            return true;
        }

        // Secondary check: Tags.Type contains "Weapon"
        AssetExtraInfo.Data data = item.getData();
        if (data != null) {
            Map<String, String[]> rawTags = data.getRawTags();
            if (rawTags != null) {
                String[] typeTags = rawTags.get("Type");
                if (typeTags != null) {
                    for (String tag : typeTags) {
                        if ("Weapon".equals(tag)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    /**
     * Checks if a weapon ID should be excluded from patching.
     */
    private boolean isExcluded(@Nonnull String itemId) {
        if (excludedItems.contains(itemId)) {
            return true;
        }
        for (String prefix : excludedPrefixes) {
            if (itemId.startsWith(prefix)) {
                return true;
            }
        }
        for (String pattern : excludedPatterns) {
            if (itemId.contains(pattern)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Resolves the weapon family from Tags.Family or item ID parsing.
     *
     * @return Family name (e.g., "Sword", "Axe"), or null if unknown
     */
    @Nullable
    private String resolveFamily(@Nonnull Item item) {
        // Try Tags.Family first (most reliable)
        AssetExtraInfo.Data data = item.getData();
        if (data != null) {
            Map<String, String[]> rawTags = data.getRawTags();
            if (rawTags != null) {
                String[] familyTags = rawTags.get("Family");
                if (familyTags != null && familyTags.length > 0) {
                    return familyTags[0];
                }
            }
        }

        // Fallback: parse from item ID ("Weapon_Sword_Iron" → "Sword")
        String id = item.getId();
        if (id.startsWith("Weapon_")) {
            String[] parts = id.split("_");
            if (parts.length >= 2) {
                return parts[1];
            }
        }

        return null;
    }

    /**
     * Resolves the grouping key for this weapon (by family).
     *
     * <p>Hytale's Item class doesn't expose the parent template key at runtime
     * (it's resolved during codec decode). We group by weapon family instead,
     * which achieves the same result since all weapons of the same family
     * share the same interaction chain structure.
     *
     * @return Group key (e.g., "Sword", "Axe"), or null if family unknown
     */
    @Nullable
    private String resolveParentKey(@Nonnull Item item) {
        return resolveFamily(item);
    }

    /**
     * Gets InteractionVars from an Item, handling null/empty gracefully.
     */
    @Nullable
    @SuppressWarnings("unchecked")
    private Map<String, String> getInteractionVars(@Nonnull Item item) {
        try {
            // Use reflection to get the field directly (same as the public getter,
            // but we already have the field cached)
            Object vars = interactionVarsField.get(item);
            if (vars instanceof Map<?, ?> map) {
                return map.isEmpty() ? null : (Map<String, String>) map;
            }
        } catch (Exception e) {
            // Fallback to public API
        }

        Map<String, String> vars = item.getInteractionVars();
        return (vars == null || vars.isEmpty()) ? null : vars;
    }

    // =========================================================================
    // CONFIG LOADING
    // =========================================================================

    /**
     * Loads weapon-patching.yml config from disk or bundled default.
     */
    @SuppressWarnings("unchecked")
    @Nonnull
    private static Map<String, Object> loadConfig(@Nonnull Path configDir, @Nonnull ClassLoader classLoader) {
        Path configPath = configDir.resolve("weapon-patching.yml");

        // Copy default if missing
        if (!Files.exists(configPath)) {
            try (InputStream defaultStream = classLoader.getResourceAsStream("config/weapon-patching.yml")) {
                if (defaultStream != null) {
                    Files.createDirectories(configDir);
                    Files.copy(defaultStream, configPath);
                    LOGGER.atInfo().log("Created default weapon-patching.yml");
                }
            } catch (IOException e) {
                LOGGER.atWarning().withCause(e).log("Failed to create default weapon-patching.yml");
            }
        }

        // Load config
        try (InputStream input = Files.newInputStream(configPath)) {
            org.yaml.snakeyaml.Yaml yaml = new org.yaml.snakeyaml.Yaml();
            Object loaded = yaml.load(input);
            if (loaded instanceof Map<?, ?> map) {
                return (Map<String, Object>) map;
            }
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to load weapon-patching.yml — using defaults");
        }

        return Collections.emptyMap();
    }

    private static boolean getBoolean(@Nonnull Map<String, Object> config, @Nonnull String key, boolean defaultValue) {
        Object value = config.get(key);
        return value instanceof Boolean b ? b : defaultValue;
    }

    @SuppressWarnings("unchecked")
    @Nonnull
    private static List<String> getStringList(@Nonnull Map<String, Object> config, @Nonnull String key) {
        Object value = config.get(key);
        if (value instanceof List<?> list) {
            List<String> result = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof String s) {
                    result.add(s);
                }
            }
            return result;
        }
        return Collections.emptyList();
    }
}
