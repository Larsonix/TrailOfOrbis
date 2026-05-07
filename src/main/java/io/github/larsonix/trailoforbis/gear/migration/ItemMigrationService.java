package io.github.larsonix.trailoforbis.gear.migration;

import io.github.larsonix.trailoforbis.elemental.ElementType;
import io.github.larsonix.trailoforbis.gear.config.EquipmentStatConfig;
import io.github.larsonix.trailoforbis.gear.config.GearBalanceConfig;
import io.github.larsonix.trailoforbis.gear.config.ModifierConfig;
import io.github.larsonix.trailoforbis.gear.conversion.EquipmentTypeResolver;
import io.github.larsonix.trailoforbis.gear.conversion.VanillaItemConverter;
import io.github.larsonix.trailoforbis.gear.generation.GearGenerator;
import io.github.larsonix.trailoforbis.gear.generation.ImplicitDamageCalculator;
import io.github.larsonix.trailoforbis.gear.generation.ImplicitDefenseCalculator;
import io.github.larsonix.trailoforbis.gear.generation.ModifierPool;
import io.github.larsonix.trailoforbis.gear.model.ArmorImplicit;
import io.github.larsonix.trailoforbis.gear.model.WeaponImplicit;
import io.github.larsonix.trailoforbis.gear.instance.GearInstanceId;
import io.github.larsonix.trailoforbis.gear.instance.GearInstanceIdGenerator;
import io.github.larsonix.trailoforbis.gear.migration.GearValidator.ValidationResult;
import io.github.larsonix.trailoforbis.gear.model.EquipmentType;
import io.github.larsonix.trailoforbis.gear.model.GearData;
import io.github.larsonix.trailoforbis.gear.model.WeaponType;
import io.github.larsonix.trailoforbis.gear.util.GearUtils;
import io.github.larsonix.trailoforbis.api.ServiceRegistry;
import io.github.larsonix.trailoforbis.leveling.api.LevelingService;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/**
 * Orchestrates item migration on player login.
 *
 * <p>For each gear item in the player's inventory with a stale version stamp:
 * validates against current config rules and applies fixes (rerolled implicits/modifiers).
 *
 * <p><b>Integration point</b>: Called from PlayerJoinListener's world.execute() block,
 * after player data loads but before stat recalculation. This ensures:
 * <ul>
 *   <li>Player level is available (for value scaling on rerolls)</li>
 *   <li>Fixed stats feed into the first stat calculation</li>
 *   <li>Runs on world thread (safe to modify inventory)</li>
 * </ul>
 *
 * <p><b>Performance</b>: Typical player has 10-50 gear items. Version check is O(1) per item.
 * After first login post-update, all items are stamped — subsequent logins are zero-cost.
 */
public final class ItemMigrationService {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final GearValidator validator;
    private final GearFixer fixer;
    private final EquipmentTypeResolver equipmentTypeResolver;
    private final List<ItemMigration> migrationChain;

    /** Set after GearManager finishes initializing VanillaItemConverter (later phase). */
    @Nullable
    private VanillaItemConverter vanillaItemConverter;

    /** Set after GearManager initialization for resolving legacy item base IDs from custom IDs. */
    @Nullable
    private io.github.larsonix.trailoforbis.gear.item.ItemRegistryService itemRegistryService;

    /** Set after GearManager initialization — used as the SOURCE OF TRUTH for what implicits an item should have. */
    @Nullable
    private GearGenerator gearGenerator;

    /**
     * Level sentinel: use material-based estimation for world containers without player context.
     */
    public static final int LEVEL_FROM_MATERIAL = -1;

    /**
     * Creates an ItemMigrationService with the given configs.
     *
     * <p>Called during GearManager initialization (before initialized=true),
     * so configs are passed directly rather than via GearManager getters.
     *
     * @param modifierConfig The modifier configuration
     * @param balanceConfig The gear balance configuration
     * @param equipmentStatConfig The equipment stat restriction config
     */
    public ItemMigrationService(
            @Nonnull ModifierConfig modifierConfig,
            @Nonnull GearBalanceConfig balanceConfig,
            @Nonnull EquipmentStatConfig equipmentStatConfig) {

        this.validator = new GearValidator(modifierConfig, balanceConfig, equipmentStatConfig);
        this.equipmentTypeResolver = new EquipmentTypeResolver();

        // Create a modifier pool for rerolling (thread-safe with ThreadLocalRandom)
        ModifierPool modifierPool = new ModifierPool(
                modifierConfig, balanceConfig,
                equipmentStatConfig,
                ThreadLocalRandom.current());

        ImplicitDamageCalculator implicitDamageCalc = new ImplicitDamageCalculator(balanceConfig);
        ImplicitDefenseCalculator implicitDefenseCalc = new ImplicitDefenseCalculator(balanceConfig);

        this.fixer = new GearFixer(modifierConfig, balanceConfig, modifierPool,
                implicitDamageCalc, implicitDefenseCalc, this.validator);

        // Build ordered migration chain (add new migrations here as versions increase)
        this.migrationChain = buildMigrationChain();
    }

    /**
     * Builds the ordered list of version-specific migrations.
     *
     * <p>Add new migrations here when bumping ItemVersionRegistry.CURRENT_VERSION.
     * Migrations are sorted by fromVersion to ensure correct ordering.
     */
    @Nonnull
    private static List<ItemMigration> buildMigrationChain() {
        List<ItemMigration> migrations = new ArrayList<>();
        // Add version-specific migrations here as needed:
        // migrations.add(new V1ToV2ItemMigration());
        // migrations.add(new V2ToV3ItemMigration());
        migrations.sort(Comparator.comparingInt(ItemMigration::fromVersion));
        return List.copyOf(migrations);
    }

    /**
     * Sets the VanillaItemConverter for converting non-RPG items during integrity checks.
     * Called by GearManager after VanillaItemConverter is initialized (later phase).
     */
    public void setVanillaItemConverter(@Nullable VanillaItemConverter converter) {
        this.vanillaItemConverter = converter;
    }

    /**
     * Sets the ItemRegistryService for resolving legacy item base IDs.
     * Legacy items have custom "rpg_gear_*" IDs but no RPG:BaseItemId metadata.
     * The registry maps custom IDs back to original item IDs (e.g., "Weapon_Staff_Wood").
     */
    public void setItemRegistryService(@Nullable io.github.larsonix.trailoforbis.gear.item.ItemRegistryService registryService) {
        this.itemRegistryService = registryService;
    }

    /**
     * Sets the GearGenerator — used as the source of truth for what implicits an item should have.
     * The migration system asks GearGenerator "what would you produce?" rather than
     * duplicating generation rules in the validator.
     */
    public void setGearGenerator(@Nullable GearGenerator generator) {
        this.gearGenerator = generator;
    }

    /**
     * Result of an integrity check pass on a player's inventory.
     *
     * @param converted Number of vanilla items converted to RPG gear
     * @param migrated Number of existing RPG items fixed (rerolled/clamped)
     */
    public record IntegrityResult(int converted, int migrated) {
        public int total() { return converted + migrated; }
    }

    /**
     * Runs full item integrity check on a player's inventory.
     *
     * <p>For each item:
     * <ul>
     *   <li>Vanilla weapon/armor → converted to RPG gear via VanillaItemConverter</li>
     *   <li>RPG gear with stale version → validated + fixed via GearValidator/GearFixer</li>
     *   <li>RPG gear at current version → skipped (zero-cost)</li>
     * </ul>
     *
     * <p>Must be called on the world thread (inventory modifications).
     *
     * @param player The player entity
     * @param playerId The player's UUID (for logging)
     * @return Result with counts of converted and migrated items
     */
    public IntegrityResult migratePlayerGear(@Nonnull Player player, @Nonnull UUID playerId) {
        Inventory inventory = player.getInventory();
        if (inventory == null) {
            return new IntegrityResult(0, 0);
        }

        // Resolve player level for vanilla item conversion
        int playerLevel = ServiceRegistry.get(LevelingService.class)
                .map(svc -> svc.getLevel(playerId))
                .orElse(1);

        int converted = 0;
        int migrated = 0;

        // Process ALL 6 containers that can hold gear
        String[] names = {"hotbar", "armor", "storage", "backpack", "utility", "tools"};
        ItemContainer[] containers = {
                inventory.getHotbar(), inventory.getArmor(), inventory.getStorage(),
                inventory.getBackpack(), inventory.getUtility(), inventory.getTools()
        };

        for (int i = 0; i < containers.length; i++) {
            int[] counts = processContainer(containers[i], playerId, names[i], playerLevel);
            converted += counts[0];
            migrated += counts[1];
        }

        if (converted + migrated > 0) {
            LOGGER.at(Level.INFO).log("Item integrity for %s: %d converted, %d migrated",
                    playerId, converted, migrated);
        }

        return new IntegrityResult(converted, migrated);
    }

    /**
     * Processes all items in a container — converts vanilla items AND migrates stale RPG items.
     *
     * <p>Used by both player-login and world-container-open paths.
     *
     * @param container The item container to process (may be null)
     * @param playerId The player UUID for logging (may be null for world containers)
     * @param containerName A label for logging
     * @param conversionLevel Level to assign to newly-converted vanilla items.
     *                        Use player level for inventories, LEVEL_FROM_MATERIAL for world containers.
     * @return int[2]: [0] = vanilla items converted, [1] = RPG items migrated
     */
    public int[] processContainer(@Nullable ItemContainer container, @Nullable UUID playerId,
                                   @Nonnull String containerName, int conversionLevel) {
        if (container == null) {
            return new int[]{0, 0};
        }

        int converted = 0;
        int migrated = 0;

        for (short slot = 0; slot < container.getCapacity(); slot++) {
            ItemStack itemStack = container.getItemStack(slot);
            if (itemStack == null || itemStack.isEmpty()) {
                continue;
            }

            try {
                if (!GearUtils.isRpgGear(itemStack)) {
                    // NOT RPG gear — try vanilla → RPG conversion
                    ItemStack result = tryConvertVanilla(itemStack, conversionLevel);
                    if (result != null) {
                        container.setItemStackForSlot(slot, result);
                        converted++;
                    }
                } else {
                    // IS RPG gear — check if it needs migration
                    int version = GearUtils.readVersion(itemStack);
                    if (version >= ItemVersionRegistry.CURRENT_VERSION) {
                        continue; // Already current, skip
                    }

                    ItemStack result = migrateItem(itemStack, playerId);
                    if (result != null) {
                        container.setItemStackForSlot(slot, result);
                        migrated++;
                    }
                }
            } catch (Exception e) {
                LOGGER.at(Level.WARNING).withCause(e).log(
                        "Failed to process item in %s slot %d for %s, skipping",
                        containerName, slot, playerId);
            }
        }

        return new int[]{converted, migrated};
    }

    /**
     * Legacy overload for callers that don't need vanilla conversion.
     * Delegates with conversionLevel=0 (disables conversion).
     */
    public int processContainer(@Nullable ItemContainer container, @Nullable UUID playerId,
                                 @Nonnull String containerName) {
        int[] counts = processContainer(container, playerId, containerName, 0);
        return counts[1]; // Return only migration count for backward compat
    }

    /**
     * Attempts to convert a vanilla item to RPG gear.
     *
     * @param itemStack The vanilla item
     * @param conversionLevel The level to assign (0 = skip, LEVEL_FROM_MATERIAL = estimate from material)
     * @return The converted RPG item, or null if not convertible
     */
    @Nullable
    private ItemStack tryConvertVanilla(@Nonnull ItemStack itemStack, int conversionLevel) {
        if (vanillaItemConverter == null || !vanillaItemConverter.isEnabled()) {
            return null;
        }
        if (conversionLevel == 0) {
            return null; // Conversion disabled for this call
        }

        int level = conversionLevel;
        if (level == LEVEL_FROM_MATERIAL) {
            // Estimate level from material tier
            level = estimateLevelFromMaterial(itemStack);
        }

        Optional<ItemStack> result = vanillaItemConverter.convert(
                itemStack, level, VanillaItemConverter.AcquisitionSource.INVENTORY_MIGRATION);
        return result.orElse(null);
    }

    /**
     * Estimates an appropriate item level from the item's material tier.
     * Used for world containers where no player level context is available.
     */
    private int estimateLevelFromMaterial(@Nonnull ItemStack itemStack) {
        if (vanillaItemConverter == null) return 1;

        // Use material mapper to get the tier, map to a reasonable level
        String itemId = itemStack.getItemId();
        if (itemId == null) return 1;

        // Material tiers map to level ranges:
        // Wood/Leather = 1-10, Stone/Copper = 5-15, Iron = 10-25, Gold = 20-35, Diamond = 30-50
        // Use the material mapper's max rarity as a proxy for tier level
        var maxRarity = vanillaItemConverter.getMaterialMapper().getMaxRarity(itemId);
        return switch (maxRarity) {
            case COMMON -> 3;
            case UNCOMMON -> 8;
            case RARE -> 15;
            case EPIC -> 25;
            case LEGENDARY -> 40;
            case MYTHIC, UNIQUE -> 50;
        };
    }

    /**
     * Migrates a single RPG item: resolves identity, validates, fixes, stamps.
     *
     * @param itemStack The item to migrate
     * @param playerId For logging
     * @return The migrated item, or null if migration should be skipped
     */
    @Nullable
    private ItemStack migrateItem(@Nonnull ItemStack itemStack, @Nonnull UUID playerId) {
        // Read gear data
        Optional<GearData> gearOpt = GearUtils.readGearData(itemStack);
        if (gearOpt.isEmpty()) {
            return GearUtils.stampVersion(itemStack);
        }

        GearData gear = gearOpt.get();

        // Run version-specific transforms (data shape changes between versions)
        int itemVersion = GearUtils.readVersion(itemStack);
        gear = applyMigrationChain(gear, itemVersion);

        // Resolve the REAL base item ID (e.g., "Weapon_Staff_Wood", "Utility_Spellbook_Copper")
        String baseItemId = resolveBaseItemId(gear, itemStack);

        // Ensure baseItemId is stored on the GearData for downstream (setGearData will persist it)
        if (baseItemId != null && !baseItemId.equals(gear.baseItemId())) {
            gear = gear.withBaseItemId(baseItemId);
        }

        // Resolve equipment/weapon type from the base item ID
        String slot = guessSlot(baseItemId);
        EquipmentType equipmentType = equipmentTypeResolver.resolve(baseItemId, slot);
        WeaponType weaponType = equipmentType != null ? equipmentType.getWeaponType() : null;
        String effectiveSlot = equipmentType != null ? equipmentType.getSlot() : slot;

        // === IMPLICIT CORRECTNESS ===
        // Ask GearGenerator what implicits this item SHOULD have (source of truth)
        // Then compare with what it actually has and fix discrepancies
        boolean implicitChanged = false;
        if (gearGenerator != null) {
            GearData afterImplicitFix = fixImplicitsFromGenerator(gear, baseItemId, equipmentType, effectiveSlot);
            if (afterImplicitFix != gear) {
                gear = afterImplicitFix;
                implicitChanged = true;
            }
        }

        // === MODIFIER VALIDATION ===
        // Validate modifiers against config (existence, slot, range)
        ValidationResult result = validator.validate(gear, equipmentType, weaponType, effectiveSlot);

        boolean modifiersNeedFix = result.needsMigration();

        if (!implicitChanged && !modifiersNeedFix) {
            // Item is fully correct — ensure instanceId + baseItemId, then stamp
            boolean needsInstanceId = !gear.hasInstanceId();
            boolean needsBaseIdUpdate = baseItemId != null && !baseItemId.equals(gearOpt.get().baseItemId());
            if (needsInstanceId || needsBaseIdUpdate) {
                if (needsInstanceId) {
                    gear = gear.withInstanceId(GearInstanceIdGenerator.generate());
                }
                if (needsBaseIdUpdate) {
                    gear = gear.withBaseItemId(baseItemId);
                }
                return GearUtils.setGearData(itemStack, gear);
            }
            return GearUtils.stampVersion(itemStack);
        }

        // Apply modifier fixes (clamping, replacement)
        GearData fixedGear = modifiersNeedFix
                ? fixer.fix(gear, result, equipmentType, weaponType, effectiveSlot)
                : gear;

        // Ensure instanceId exists
        if (!fixedGear.hasInstanceId()) {
            fixedGear = fixedGear.withInstanceId(GearInstanceIdGenerator.generate());
        }

        // Ensure correct baseItemId is persisted (prevents future poisoning)
        if (baseItemId != null && !baseItemId.equals(fixedGear.baseItemId())) {
            fixedGear = fixedGear.withBaseItemId(baseItemId);
        }

        // Write fixed data back (setGearData stamps version + clears stale metadata)
        return GearUtils.setGearData(itemStack, fixedGear);
    }

    /**
     * Compares item's current implicits to what GearGenerator would produce.
     * Fixes any discrepancies by generating the correct ones.
     *
     * <p>Rules (derived from GearGenerator.generateImplicits):
     * <ul>
     *   <li>Weapon slot → should have weapon implicit, should NOT have armor implicit</li>
     *   <li>Armor slot → should have armor implicit, should NOT have weapon implicit</li>
     *   <li>If type matches existing → keep player's rolled value (don't re-roll)</li>
     *   <li>If type is wrong or missing → generate correct one</li>
     * </ul>
     *
     * @return The gear with corrected implicits, or the SAME reference if no changes
     */
    @Nonnull
    private GearData fixImplicitsFromGenerator(
            @Nonnull GearData gear,
            @Nullable String baseItemId,
            @Nullable EquipmentType equipmentType,
            @Nonnull String slot) {

        // Ask GearGenerator what this item SHOULD have
        GearGenerator.ImplicitExpectation expected = gearGenerator.getExpectedImplicits(
                slot, baseItemId, equipmentType, gear.level());

        GearData fixed = gear;
        boolean changed = false;

        // Migrate legacy spell_damage to actual element (preserves rolled value and range)
        if (fixed.implicit() != null && "spell_damage".equals(fixed.implicit().damageType())) {
            ElementType[] elements = ElementType.values();
            ElementType randomElement = elements[ThreadLocalRandom.current().nextInt(elements.length)];
            WeaponImplicit migrated = fixed.implicit().withDamageType(randomElement.getDamageTypeId());
            fixed = fixed.withImplicit(migrated);
            changed = true;
            LOGGER.at(Level.INFO).log("Migration: spell_damage → %s for level %d %s",
                    randomElement.getDamageTypeId(), fixed.level(), baseItemId);
        }

        // Fix weapon implicit
        if (expected.shouldHaveWeapon()) {
            if (gear.implicit() == null || !expected.isWeaponTypeValid(gear.implicit().damageType())) {
                // Wrong type or missing → generate correct one
                WeaponImplicit correct = expected.generateWeapon(ThreadLocalRandom.current());
                fixed = fixed.withImplicit(correct);
                changed = true;
                LOGGER.at(Level.INFO).log("Migration: fixed weapon implicit %s → %s for level %d %s",
                        gear.implicit() != null ? gear.implicit().damageType() : "MISSING",
                        correct.damageType(), gear.level(), baseItemId);
            }
        } else if (gear.implicit() != null) {
            // Shouldn't have weapon implicit — remove it
            fixed = fixed.withImplicit(null);
            changed = true;
            LOGGER.at(Level.INFO).log("Migration: removed weapon implicit from non-weapon %s", baseItemId);
        }

        // Fix armor implicit
        if (expected.shouldHaveArmor()) {
            if (gear.armorImplicit() == null || !expected.isArmorTypeValid(gear.armorImplicit().defenseType())) {
                // Wrong type or missing → generate correct one
                ArmorImplicit correct = expected.generateArmor(ThreadLocalRandom.current());
                if (correct != null) {
                    fixed = fixed.withArmorImplicit(correct);
                    changed = true;
                    LOGGER.at(Level.INFO).log("Migration: fixed armor implicit %s → %s for level %d %s",
                            gear.armorImplicit() != null ? gear.armorImplicit().defenseType() : "MISSING",
                            correct.defenseType(), gear.level(), baseItemId);
                }
            }
        } else if (gear.armorImplicit() != null) {
            // Shouldn't have armor implicit — remove it
            fixed = fixed.withArmorImplicit(null);
            changed = true;
            LOGGER.at(Level.INFO).log("Migration: removed armor implicit from %s", baseItemId);
        }

        return changed ? fixed : gear;
    }

    /**
     * Resolves the original Hytale item ID for an RPG gear item.
     *
     * <p>An rpg_gear_* custom ID is NEVER a valid base item ID. This method
     * always resolves through to the real underlying item (e.g., "Weapon_Staff_Wood").
     *
     * <p>Resolution order:
     * <ol>
     *   <li>GearData.baseItemId() — stored on the item's RPG data</li>
     *   <li>RPG:BaseItemId metadata — stored on the ItemStack</li>
     *   <li>ItemStack.getItemId() — the current item ID</li>
     *   <li>ItemRegistryService lookup — maps rpg_gear_* → original ID from database</li>
     * </ol>
     *
     * <p>Any result starting with "rpg_gear_" is discarded and resolved via the registry.
     * This handles both missing baseItemId AND poisoned baseItemId from previous migrations.
     * If ALL resolution paths fail, returns null rather than storing a poisoned rpg_gear_* ID.
     */
    @Nullable
    private String resolveBaseItemId(@Nonnull GearData gear, @Nonnull ItemStack itemStack) {
        // Collect all candidate IDs
        String fromGearData = gear.baseItemId();
        String fromMetadata = GearUtils.getBaseItemId(itemStack);
        String fromItemStack = itemStack.getItemId();

        // Use the first non-null, non-empty, non-rpg_gear candidate
        String candidate = pickValidBaseId(fromGearData, fromMetadata, fromItemStack);

        if (candidate != null) {
            return candidate;
        }

        // All candidates are either null or rpg_gear_* — must look up from registry
        String customId = pickCustomId(fromGearData, fromMetadata, fromItemStack);
        if (customId != null && itemRegistryService != null) {
            String resolved = itemRegistryService.getBaseItemId(customId);
            if (resolved != null && !resolved.isEmpty() && !resolved.startsWith("rpg_gear_")) {
                return resolved;
            }
        }

        // All resolution failed — return null to prevent storing rpg_gear_* as baseItemId.
        // The item keeps its existing baseItemId (or null). On next login the registry
        // may be available and resolution will succeed then.
        LOGGER.at(Level.WARNING).log(
                "Could not resolve base item ID for RPG gear (itemId=%s, gearDataBase=%s). "
                + "Item will retain existing baseItemId until registry resolves it.",
                fromItemStack, fromGearData);
        return null;
    }

    /**
     * Returns the first non-null, non-empty value that is NOT an rpg_gear_* custom ID.
     */
    @Nullable
    private static String pickValidBaseId(String... candidates) {
        for (String c : candidates) {
            if (c != null && !c.isEmpty() && !c.startsWith("rpg_gear_")) {
                return c;
            }
        }
        return null;
    }

    /**
     * Returns the first rpg_gear_* custom ID found (for registry lookup).
     */
    @Nullable
    private static String pickCustomId(String... candidates) {
        for (String c : candidates) {
            if (c != null && c.startsWith("rpg_gear_")) {
                return c;
            }
        }
        return null;
    }

    /**
     * Applies version-specific migrations in order.
     *
     * <p>Runs all migrations whose {@code fromVersion} is >= the item's current version
     * and < {@link ItemVersionRegistry#CURRENT_VERSION}. This transforms the data shape
     * before validation/fixing.
     *
     * @param gear The gear data to transform
     * @param itemVersion The item's stored version (0 = legacy pre-migration)
     * @return Transformed gear data (or original if no transforms needed)
     */
    @Nonnull
    private GearData applyMigrationChain(@Nonnull GearData gear, int itemVersion) {
        if (migrationChain.isEmpty()) {
            return gear;
        }

        GearData current = gear;
        for (ItemMigration migration : migrationChain) {
            if (itemVersion <= migration.fromVersion()) {
                current = migration.migrate(current);
                LOGGER.at(Level.INFO).log("Migration: applied v%d→v%d transform",
                        migration.fromVersion(), migration.toVersion());
            }
        }
        return current;
    }

    /**
     * Guesses the slot string from an item ID for EquipmentTypeResolver.
     *
     * <p>The resolver needs a slot hint for armor (to determine head/chest/legs/hands).
     * For weapons, it resolves from the item ID alone.
     */
    @Nonnull
    private static String guessSlot(@Nullable String itemId) {
        if (itemId == null) return "weapon";

        String lower = itemId.toLowerCase();

        // Weapons are identified by type, slot doesn't matter for resolution
        if (lower.startsWith("weapon_") || lower.contains("sword") || lower.contains("staff")
                || lower.contains("bow") || lower.contains("wand") || lower.contains("dagger")
                || lower.contains("axe") || lower.contains("mace") || lower.contains("spear")
                || lower.contains("crossbow") || lower.contains("scythe") || lower.contains("halberd")) {
            return "weapon";
        }

        // Armor slot from ID suffix
        if (lower.contains("_head") || lower.contains("helmet") || lower.contains("hat")) {
            return "head";
        }
        if (lower.contains("_chest") || lower.contains("chestplate") || lower.contains("tunic")) {
            return "chest";
        }
        if (lower.contains("_legs") || lower.contains("leggings") || lower.contains("pants")) {
            return "legs";
        }
        if (lower.contains("_hands") || lower.contains("gloves") || lower.contains("gauntlet")) {
            return "hands";
        }

        // Shield
        if (lower.contains("shield")) {
            return "weapon";
        }

        // Default: treat as weapon (weapons don't need slot for resolution)
        return "weapon";
    }
}
