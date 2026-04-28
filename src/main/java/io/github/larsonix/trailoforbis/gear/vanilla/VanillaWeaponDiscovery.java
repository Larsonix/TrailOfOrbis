package io.github.larsonix.trailoforbis.gear.vanilla;

import com.hypixel.hytale.assetstore.AssetExtraInfo;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.assetstore.map.IndexedLookupTableAssetMap;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.DamageEntityInteraction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.combat.DamageCalculator;
import it.unimi.dsi.fastutil.objects.Object2FloatMap;

import io.github.larsonix.trailoforbis.gear.config.GearBalanceConfig.FamilyAttackProfile;
import io.github.larsonix.trailoforbis.gear.config.GearBalanceConfig.VanillaWeaponProfilesConfig;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Discovers vanilla weapon attack profiles at server startup.
 *
 * <p>This service enumerates all weapons in Hytale's asset map and extracts
 * their attack damage values by traversing the interaction chain:
 *
 * <pre>
 * Item.getInteractionVars() → Map&lt;String, String&gt;
 *   Keys ending in "_Damage" → RootInteraction ID
 *     → RootInteraction.getInteractionIds()
 *       → Interaction (find DamageEntityInteraction in chain)
 *         → DamageCalculator.baseDamageRaw (default damage)
 *         → AngledDamage[] (backstab variants)
 * </pre>
 *
 * <p>For each weapon, a {@link VanillaWeaponProfile} is created with:
 * <ul>
 *   <li>All attack names and damage values</li>
 *   <li>Geometric mean reference point</li>
 *   <li>Pre-computed effectiveness multipliers</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>
 * VanillaWeaponDiscovery discovery = new VanillaWeaponDiscovery();
 * discovery.discoverAll();
 *
 * VanillaWeaponProfile profile = discovery.getProfile("Weapon_Daggers_Iron");
 * float attackMultiplier = profile.getAttackTypeMultiplier(vanillaDamage);
 * </pre>
 */
public class VanillaWeaponDiscovery {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /** Cached weapon profiles by item ID */
    private final Map<String, VanillaWeaponProfile> profiles = new ConcurrentHashMap<>();

    /** Config for family-based attack profiles */
    private final VanillaWeaponProfilesConfig config;

    /** Whether discovery has been run */
    private volatile boolean discovered = false;

    // Reflection fields (cached for performance)
    private Field damageCalculatorField;
    private Field angledDamageField;
    private Field baseDamageRawField;
    private Field angledDamageCalculatorField;
    private Field angleRadField;
    private Field angleDistanceRadField;

    /**
     * Creates a new discovery service with family-based attack profiles.
     *
     * @param config The vanilla weapon profiles config containing family profiles
     */
    public VanillaWeaponDiscovery(VanillaWeaponProfilesConfig config) {
        this.config = config;
        initReflection();
    }

    /**
     * Initializes reflection fields for accessing protected members.
     */
    private void initReflection() {
        try {
            // DamageEntityInteraction fields
            damageCalculatorField = DamageEntityInteraction.class.getDeclaredField("damageCalculator");
            damageCalculatorField.setAccessible(true);

            angledDamageField = DamageEntityInteraction.class.getDeclaredField("angledDamage");
            angledDamageField.setAccessible(true);

            // DamageCalculator fields
            baseDamageRawField = DamageCalculator.class.getDeclaredField("baseDamageRaw");
            baseDamageRawField.setAccessible(true);

            // AngledDamage fields (inner class of DamageEntityInteraction)
            Class<?> angledDamageClass = DamageEntityInteraction.AngledDamage.class;
            angledDamageCalculatorField = angledDamageClass.getSuperclass().getDeclaredField("damageCalculator");
            angledDamageCalculatorField.setAccessible(true);

            angleRadField = angledDamageClass.getDeclaredField("angleRad");
            angleRadField.setAccessible(true);

            angleDistanceRadField = angledDamageClass.getDeclaredField("angleDistanceRad");
            angleDistanceRadField.setAccessible(true);

            LOGGER.at(Level.FINE).log("Reflection fields initialized for VanillaWeaponDiscovery");
        } catch (NoSuchFieldException e) {
            LOGGER.at(Level.SEVERE).withCause(e).log("Failed to initialize reflection for weapon discovery");
        }
    }

    /**
     * Discovers all weapon profiles from the asset map.
     *
     * <p>This should be called once during plugin startup, after Hytale has
     * loaded all assets but before combat systems are used.
     */
    public void discoverAll() {
        if (discovered) {
            LOGGER.at(Level.WARNING).log("VanillaWeaponDiscovery.discoverAll() called multiple times");
            return;
        }

        long startTime = System.currentTimeMillis();
        DefaultAssetMap<String, Item> items = Item.getAssetMap();
        int totalItems = 0;
        int weaponCount = 0;
        int profileCount = 0;

        // Per-family tracking: familyName → [weaponCount, minNormals, maxNormals, minBackstabs, maxBackstabs]
        Map<String, int[]> familyStats = new HashMap<>();

        for (Item item : items.getAssetMap().values()) {
            totalItems++;
            if (item.getWeapon() == null) {
                continue;
            }
            weaponCount++;

            try {
                VanillaWeaponProfile profile = discoverWeapon(item);
                if (profile != null && profile.hasAttacks()) {
                    profiles.put(item.getId(), profile);
                    profileCount++;

                    // Count normals and backstabs for this weapon
                    int normals = 0;
                    int backstabs = 0;
                    float minNormalDmg = Float.MAX_VALUE;
                    float maxNormalDmg = Float.MIN_VALUE;
                    float maxBackstabDmg = 0f;
                    for (VanillaAttackInfo attack : profile.attacks()) {
                        if (attack.isBackstab()) {
                            backstabs++;
                            if (attack.damage() > maxBackstabDmg) maxBackstabDmg = attack.damage();
                        } else {
                            normals++;
                            if (attack.damage() < minNormalDmg) minNormalDmg = attack.damage();
                            if (attack.damage() > maxNormalDmg) maxNormalDmg = attack.damage();
                        }
                    }

                    // Enriched per-weapon FINE log
                    String normalRange = normals > 0
                            ? String.format("%dn [%.1f-%.1f]", normals, minNormalDmg, maxNormalDmg)
                            : "0n";
                    String backstabRange = backstabs > 0
                            ? String.format("%db [%.1f]", backstabs, maxBackstabDmg)
                            : "0b";
                    LOGGER.at(Level.FINE).log(
                            "Discovered %s (%s): %s + %s, eff=%s",
                            item.getId(),
                            profile.weaponFamily(),
                            normalRange,
                            backstabRange,
                            profile.getEffectivenessRangeString()
                    );

                    // Accumulate per-family stats
                    int[] stats = familyStats.computeIfAbsent(
                            profile.weaponFamily(), _ -> new int[]{0, Integer.MAX_VALUE, 0, Integer.MAX_VALUE, 0});
                    stats[0]++; // weapon count
                    stats[1] = Math.min(stats[1], normals);
                    stats[2] = Math.max(stats[2], normals);
                    stats[3] = Math.min(stats[3], backstabs);
                    stats[4] = Math.max(stats[4], backstabs);
                }
            } catch (Exception e) {
                LOGGER.at(Level.WARNING).withCause(e).log("Failed to discover weapon: %s", item.getId());
            }
        }

        discovered = true;
        long elapsed = System.currentTimeMillis() - startTime;

        LOGGER.at(Level.INFO).log(
                "Discovered %d vanilla weapon profiles from %d weapons (%d total items) in %dms",
                profileCount, weaponCount, totalItems, elapsed
        );

        // Per-family summary at INFO level
        if (!familyStats.isEmpty()) {
            StringBuilder sb = new StringBuilder("Family profiles: ");
            boolean first = true;
            for (Map.Entry<String, int[]> entry : familyStats.entrySet()) {
                if (!first) sb.append(", ");
                first = false;
                int[] s = entry.getValue();
                String nRange = s[1] == s[2] ? String.valueOf(s[1]) : s[1] + "-" + s[2];
                String bRange = s[3] == s[4] ? String.valueOf(s[3]) : s[3] + "-" + s[4];
                sb.append(String.format("%s(%dw, %sn, %sb)", entry.getKey(), s[0], nRange, bRange));
            }
            LOGGER.at(Level.INFO).log(sb.toString());
        }
    }

    /**
     * Gets the profile for a weapon item ID.
     *
     * @param itemId The Hytale item ID (e.g., "Weapon_Daggers_Iron")
     * @return The weapon profile, or null if not found
     */
    @Nullable
    public VanillaWeaponProfile getProfile(String itemId) {
        return profiles.get(itemId);
    }

    /**
     * Checks if a weapon profile exists.
     *
     * @param itemId The Hytale item ID
     * @return True if a profile was discovered for this weapon
     */
    public boolean hasProfile(String itemId) {
        return profiles.containsKey(itemId);
    }

    /**
     * Gets the number of discovered profiles.
     *
     * @return The profile count
     */
    public int getProfileCount() {
        return profiles.size();
    }

    /**
     * Checks if discovery has been run.
     *
     * @return True if discoverAll() has been called
     */
    public boolean isDiscovered() {
        return discovered;
    }

    /**
     * Discovers attack profiles for a single weapon.
     */
    @Nullable
    private VanillaWeaponProfile discoverWeapon(Item item) {
        List<VanillaAttackInfo> attacks = new ArrayList<>();
        Map<String, String> interactionVars = item.getInteractionVars();

        if (interactionVars == null || interactionVars.isEmpty()) {
            return null;
        }

        for (Map.Entry<String, String> entry : interactionVars.entrySet()) {
            String attackName = entry.getKey();
            if (!attackName.endsWith("_Damage")) {
                continue;
            }

            String rootInteractionId = entry.getValue();
            extractAttacksFromInteraction(attackName, rootInteractionId, attacks);
        }

        if (attacks.isEmpty()) {
            return null;
        }

        // Extract weapon family from tags and ID
        String tagFamily = extractTagFamily(item);
        String idFamily = extractFamilyFromId(item.getId());

        // Two-tier config lookup: ID-based name first (more specific), then tag-based
        FamilyAttackProfile familyProfile;
        String resolvedVia;
        if (idFamily != null && config.familyProfiles().containsKey(idFamily)) {
            familyProfile = config.familyProfiles().get(idFamily);
            resolvedVia = idFamily + " via id";
        } else if (tagFamily != null && config.familyProfiles().containsKey(tagFamily)) {
            familyProfile = config.familyProfiles().get(tagFamily);
            resolvedVia = tagFamily + " via tag";
        } else {
            familyProfile = config.defaultProfile();
            resolvedVia = "default";
        }

        // Canonical family = tag if available, else ID, else Unknown
        String canonicalFamily = tagFamily != null ? tagFamily
                : (idFamily != null ? idFamily : "Unknown");

        // Count attack types for logging
        long normalCount = attacks.stream().filter(a -> !a.isBackstab()).count();
        long backstabCount = attacks.stream().filter(VanillaAttackInfo::isBackstab).count();

        LOGGER.at(Level.FINE).log(
                "%s (%s, resolved=%s): %dn + %db attacks",
                item.getId(), canonicalFamily, resolvedVia, normalCount, backstabCount);

        return VanillaWeaponProfile.create(item.getId(), canonicalFamily, attacks, familyProfile);
    }

    /**
     * Extracts attacks from a root interaction chain.
     */
    private void extractAttacksFromInteraction(
            String attackName,
            String rootInteractionId,
            List<VanillaAttackInfo> attacks
    ) {
        IndexedLookupTableAssetMap<String, RootInteraction> rootMap = RootInteraction.getAssetMap();
        RootInteraction root = rootMap.getAsset(rootInteractionId);
        if (root == null) {
            return;
        }

        String[] interactionIds = root.getInteractionIds();
        if (interactionIds == null) {
            return;
        }

        IndexedLookupTableAssetMap<String, Interaction> interactionMap = Interaction.getAssetMap();

        for (String interactionId : interactionIds) {
            Interaction interaction = interactionMap.getAsset(interactionId);
            if (interaction instanceof DamageEntityInteraction dmgInteraction) {
                extractFromDamageInteraction(attackName, dmgInteraction, attacks);
            }
        }
    }

    /**
     * Extracts base damage and backstab variants from a DamageEntityInteraction.
     */
    private void extractFromDamageInteraction(
            String attackName,
            DamageEntityInteraction dmgInteraction,
            List<VanillaAttackInfo> attacks
    ) {
        try {
            // Extract base damage
            DamageCalculator calc = (DamageCalculator) damageCalculatorField.get(dmgInteraction);
            float baseDamage = extractTotalDamage(calc);
            if (baseDamage > 0) {
                attacks.add(VanillaAttackInfo.normal(attackName, baseDamage));
            }

            // Extract backstab variants (AngledDamage[])
            Object angledDamageArray = angledDamageField.get(dmgInteraction);
            if (angledDamageArray != null) {
                DamageEntityInteraction.AngledDamage[] angledDamages =
                        (DamageEntityInteraction.AngledDamage[]) angledDamageArray;

                for (DamageEntityInteraction.AngledDamage angled : angledDamages) {
                    extractFromAngledDamage(attackName, angled, attacks);
                }
            }
        } catch (IllegalAccessException e) {
            LOGGER.at(Level.WARNING).withCause(e).log("Failed to extract damage from interaction");
        }
    }

    /**
     * Extracts damage from an AngledDamage (backstab) variant.
     */
    private void extractFromAngledDamage(
            String attackName,
            DamageEntityInteraction.AngledDamage angled,
            List<VanillaAttackInfo> attacks
    ) {
        try {
            // Get the DamageCalculator from AngledDamage (inherited from TargetedDamage)
            DamageCalculator calc = (DamageCalculator) angledDamageCalculatorField.get(angled);
            float damage = extractTotalDamage(calc);

            if (damage > 0) {
                // Convert radians to degrees for readability
                float angleRad = angleRadField.getFloat(angled);
                float distanceRad = angleDistanceRadField.getFloat(angled);
                float angleDeg = (float) Math.toDegrees(angleRad);
                float distanceDeg = (float) Math.toDegrees(distanceRad);

                attacks.add(VanillaAttackInfo.backstab(attackName, damage, angleDeg, distanceDeg));
            }
        } catch (IllegalAccessException e) {
            LOGGER.at(Level.WARNING).withCause(e).log("Failed to extract angled damage");
        }
    }

    /**
     * Extracts total damage from a DamageCalculator.
     *
     * <p>Sums all damage types (Physical, Fire, etc.) from baseDamageRaw.
     *
     * @param calc The damage calculator (may be null)
     * @return Total damage, or 0 if no damage found
     */
    @SuppressWarnings("unchecked")
    private float extractTotalDamage(@Nullable DamageCalculator calc) {
        if (calc == null) {
            return 0f;
        }

        try {
            Object2FloatMap<String> baseDamageRaw =
                    (Object2FloatMap<String>) baseDamageRawField.get(calc);

            if (baseDamageRaw == null || baseDamageRaw.isEmpty()) {
                return 0f;
            }

            float total = 0f;
            for (Object2FloatMap.Entry<String> entry : baseDamageRaw.object2FloatEntrySet()) {
                total += entry.getFloatValue();
            }
            return total;
        } catch (IllegalAccessException e) {
            LOGGER.at(Level.WARNING).withCause(e).log("Failed to extract base damage");
            return 0f;
        }
    }

    /**
     * Extracts the canonical weapon family from Hytale's raw tag system.
     *
     * <p>Uses {@code getRawTags()} which returns {@code Map<String, String[]>}
     * with proper tag names including inherited tags. Looks for the "Family" key.
     *
     * @param item The item to check
     * @return The tag-based family name (e.g., "Dagger", "Bow"), or null if not found
     */
    @Nullable
    private String extractTagFamily(Item item) {
        AssetExtraInfo.Data data = item.getData();
        if (data == null) {
            return null;
        }

        Map<String, String[]> rawTags = data.getRawTags();
        if (rawTags == null) {
            return null;
        }

        String[] familyTags = rawTags.get("Family");
        if (familyTags != null && familyTags.length > 0) {
            return familyTags[0];
        }

        return null;
    }

    /**
     * Extracts weapon family from the item ID structure.
     *
     * <p>Parses "Weapon_Daggers_Iron" → "Daggers", "Weapon_Battleaxe_Iron" → "Battleaxe".
     *
     * @param itemId The Hytale item ID
     * @return The ID-parsed family name, or null if not parseable
     */
    @Nullable
    private String extractFamilyFromId(String itemId) {
        if (itemId != null && itemId.startsWith("Weapon_")) {
            String[] parts = itemId.split("_");
            if (parts.length >= 2) {
                return parts[1];
            }
        }
        return null;
    }
}
