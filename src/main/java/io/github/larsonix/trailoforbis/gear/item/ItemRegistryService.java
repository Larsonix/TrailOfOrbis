package io.github.larsonix.trailoforbis.gear.item;

import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.ItemResourceType;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemUtility;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemQuality;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemTranslationProperties;
import io.github.larsonix.trailoforbis.database.DataManager;
import io.github.larsonix.trailoforbis.database.repository.ItemRegistryRepository;
import io.github.larsonix.trailoforbis.database.repository.ItemRegistryRepository.ItemRegistryEntry;
import io.github.larsonix.trailoforbis.gear.loot.LootGenerator.EquipmentSlot;
import io.github.larsonix.trailoforbis.gear.model.ArmorMaterial;
import io.github.larsonix.trailoforbis.gear.model.WeaponType;
import io.github.larsonix.trailoforbis.gear.reskin.ReskinResourceTypeRegistry;
import io.github.larsonix.trailoforbis.compat.HexcodeCompat;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.StampedLock;

/**
 * Service for dynamically registering custom items in Hytale's server-side asset map.
 *
 * <p>This service solves the problem where custom item IDs (e.g., "rpg_gear_xxx") are not
 * recognized by the server's interaction validation system. By registering custom Item objects
 * in the asset map, the server can properly validate interactions for RPG gear items.
 *
 * <h2>Persistence</h2>
 * <p>Custom item registrations are persisted to the database via {@link ItemRegistryRepository}.
 * On server startup, all cached registrations are restored before players can connect.
 * This ensures items don't show as "?" after server restarts.
 *
 * <h2>Why This Is Needed</h2>
 * <p>When a player tries to use an item:
 * <ol>
 *   <li>Server calls {@code Item.getAssetMap().getAsset(itemId)}</li>
 *   <li>If the item ID isn't registered, it returns {@code Item.UNKNOWN}</li>
 *   <li>Interaction validation fails, and the server sends a cancel packet</li>
 *   <li>Client sees animation start then immediately stop</li>
 * </ol>
 *
 * <p>By registering our custom items in the asset map, the server recognizes them and allows
 * interactions to proceed normally.
 *
 * <h2>Thread Safety</h2>
 * <p>This service is thread-safe. The underlying asset map uses a StampedLock for concurrent
 * access, and we track registered items in a ConcurrentHashMap.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Initialize once during plugin startup (with DB persistence)
 * ItemRegistryService registry = new ItemRegistryService();
 * registry.initialize(dataManager);  // Loads cached items from DB
 *
 * // Register a custom item (auto-persisted to DB)
 * registry.createAndRegister(baseItem, "rpg_gear_abc123");
 *
 * // Shutdown during plugin disable
 * registry.shutdown();
 * }</pre>
 *
 * @see Item
 * @see DefaultAssetMap
 * @see ItemRegistryRepository
 */
public final class ItemRegistryService {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /** Default cleanup interval: 24 hours */
    private static final long CLEANUP_INTERVAL_HOURS = 24;

    /** Default retention period: 30 days */
    private static final int DEFAULT_RETENTION_DAYS = 30;

    /**
     * Tracks all items registered by this service.
     * Map: customId → ItemRegistryEntry (baseItemId + optional secondaryInteractionId)
     */
    private final Map<String, ItemRegistryEntry> registeredItems = new ConcurrentHashMap<>();

    /**
     * Cached reflection field for accessing the internal asset map.
     */
    private Field assetMapField;

    /**
     * Direct reference to the internal asset map.
     */
    private Map<String, Item> internalAssetMap;

    /**
     * Reference to Hytale's StampedLock for thread-safe asset map access.
     * CRITICAL: Must acquire write lock before modifying internalAssetMap.
     */
    private StampedLock assetMapLock;

    /**
     * Database repository for persistent storage.
     */
    private ItemRegistryRepository repository;

    /**
     * Scheduler for periodic cleanup of old entries.
     */
    private ScheduledExecutorService cleanupScheduler;

    /**
     * Whether the service has been initialized.
     */
    private volatile boolean initialized = false;

    /**
     * Reskin ResourceType registry — maps (slot, quality, category) to ResourceType IDs.
     * Set after reskin recipe generation. Null if reskin system is not initialized.
     */
    @Nullable
    private volatile ReskinResourceTypeRegistry reskinRegistry;

    /**
     * Whether persistence is enabled (requires DataManager).
     */
    private volatile boolean persistenceEnabled = false;

    /**
     * Sets the reskin ResourceType registry. Called after recipe generation
     * so that newly created custom items receive reskin ResourceTypes.
     *
     * @param registry The reskin registry (may be null to disable)
     */
    public void setReskinRegistry(@Nullable ReskinResourceTypeRegistry registry) {
        this.reskinRegistry = registry;
    }

    /**
     * Initializes the registry service without database persistence.
     *
     * <p>This method is provided for backward compatibility and testing.
     * For production use, prefer {@link #initialize(DataManager)}.
     *
     * @throws RuntimeException if reflection access fails
     */
    public void initialize() {
        initializeReflection();
    }

    /**
     * Initializes the registry service with database persistence.
     *
     * <p>This method:
     * <ol>
     *   <li>Sets up reflection access to Hytale's asset map</li>
     *   <li>Loads all cached item registrations from the database</li>
     *   <li>Re-registers all cached items in memory</li>
     *   <li>Starts the cleanup scheduler</li>
     * </ol>
     *
     * <p>This must be called during plugin startup, BEFORE any players can connect.
     *
     * @param dataManager The data manager for database connections
     * @throws RuntimeException if initialization fails
     */
    public void initialize(@Nonnull DataManager dataManager) {
        Objects.requireNonNull(dataManager, "dataManager cannot be null");

        // Initialize reflection first
        initializeReflection();

        // Set up persistence
        this.repository = new ItemRegistryRepository(dataManager);
        this.persistenceEnabled = true;

        // Run schema migration for existing databases (adds secondary_interaction_id column)
        repository.migrateSchema();

        // Load cached registrations from database
        loadCachedRegistrations();

        // Start cleanup scheduler
        scheduleCleanup();

        LOGGER.atInfo().log("ItemRegistryService initialized with persistence enabled");
    }

    /**
     * Initializes reflection access to Hytale's internal asset map.
     */
    private void initializeReflection() {
        if (initialized) {
            LOGGER.atWarning().log("ItemRegistryService already initialized");
            return;
        }

        try {
            // Access the protected assetMap field in DefaultAssetMap
            assetMapField = DefaultAssetMap.class.getDeclaredField("assetMap");
            assetMapField.setAccessible(true);

            // Get reference to the actual map
            DefaultAssetMap<String, Item> assetMap = Item.getAssetMap();
            @SuppressWarnings("unchecked")
            Map<String, Item> map = (Map<String, Item>) assetMapField.get(assetMap);
            internalAssetMap = map;

            // Get reference to the StampedLock for thread-safe modifications
            // CRITICAL: The internal map is NOT thread-safe (Object2ObjectOpenCustomHashMap)
            // We MUST use this lock when modifying the map to avoid race conditions
            Field lockField = DefaultAssetMap.class.getDeclaredField("assetMapLock");
            lockField.setAccessible(true);
            assetMapLock = (StampedLock) lockField.get(assetMap);

            initialized = true;
            LOGGER.atInfo().log("ItemRegistryService reflection access initialized (with lock)");

        } catch (NoSuchFieldException e) {
            LOGGER.atSevere().withCause(e).log(
                "Failed to find 'assetMap' field in DefaultAssetMap. " +
                "Hytale version may have changed field names.");
            throw new RuntimeException("Cannot access asset map - field not found", e);

        } catch (IllegalAccessException e) {
            LOGGER.atSevere().withCause(e).log(
                "Failed to access 'assetMap' field in DefaultAssetMap. " +
                "Security manager may be blocking reflection.");
            throw new RuntimeException("Cannot access asset map - access denied", e);

        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Unexpected error initializing ItemRegistryService");
            throw new RuntimeException("Cannot access asset map", e);
        }
    }

    /**
     * Loads cached item registrations from the database and re-registers them.
     *
     * <p>This restores both regular gear items and items with secondary interactions
     * (stones, realm maps) so they work correctly after server restart.
     */
    private void loadCachedRegistrations() {
        if (repository == null) {
            return;
        }

        Map<String, ItemRegistryEntry> cached = repository.loadAll();
        int registered = 0;
        int skipped = 0;
        int withSecondary = 0;

        for (Map.Entry<String, ItemRegistryEntry> entry : cached.entrySet()) {
            String customId = entry.getKey();
            ItemRegistryEntry data = entry.getValue();
            String baseItemId = data.baseItemId();
            String secondaryInteractionId = data.secondaryInteractionId();

            // Get base item from Hytale's asset map
            Item baseItem = Item.getAssetMap().getAsset(baseItemId);
            if (baseItem == null || baseItem == Item.UNKNOWN) {
                LOGGER.atWarning().log(
                    "Skipping cached item %s - base item %s not found",
                    customId, baseItemId);
                skipped++;
                continue;
            }

            // Re-register in memory (don't persist again - already in DB)
            try {
                Item customItem;
                if (secondaryInteractionId != null) {
                    // Restore with secondary interaction (stones, maps)
                    customItem = createCustomItemWithSecondaryInteraction(
                        baseItem, customId, secondaryInteractionId);
                    withSecondary++;
                } else {
                    // Regular gear item
                    customItem = createCustomItem(baseItem, customId);
                }
                registerItemInternal(customId, customItem, baseItemId, secondaryInteractionId, false);
                registered++;
            } catch (Exception e) {
                LOGGER.atWarning().withCause(e).log(
                    "Failed to re-register cached item %s", customId);
                skipped++;
            }
        }

        LOGGER.atInfo().log(
            "Loaded %d item registrations from cache (%d with secondary interactions, %d skipped)",
            registered, withSecondary, skipped);
    }

    /**
     * Schedules the periodic cleanup task.
     */
    private void scheduleCleanup() {
        cleanupScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "RPG-ItemRegistry-Cleanup");
            t.setDaemon(true);
            return t;
        });

        // Run cleanup once per day
        cleanupScheduler.scheduleAtFixedRate(() -> {
            try {
                if (repository != null) {
                    int removed = repository.cleanupOldEntries(DEFAULT_RETENTION_DAYS);
                    if (removed > 0) {
                        LOGGER.atInfo().log(
                            "Cleaned up %d stale item registry entries", removed);
                    }
                }
            } catch (Exception e) {
                LOGGER.atWarning().withCause(e).log("Item registry cleanup failed");
            }
        }, CLEANUP_INTERVAL_HOURS, CLEANUP_INTERVAL_HOURS, TimeUnit.HOURS);

        LOGGER.atFine().log("Item registry cleanup scheduled (every %d hours, retain %d days)",
            CLEANUP_INTERVAL_HOURS, DEFAULT_RETENTION_DAYS);
    }

    // =========================================================================
    // ITEM CREATION AND REGISTRATION
    // =========================================================================

    /**
     * Creates a custom Item by cloning a base item and changing its ID.
     *
     * <p>The custom item inherits all properties from the base item:
     * <ul>
     *   <li>Model, texture, icon</li>
     *   <li>Weapon/armor/tool configuration</li>
     *   <li>Interaction mappings (critical for usability)</li>
     *   <li>All other item properties</li>
     * </ul>
     *
     * <p>Additionally, this method sets the {@code translationProperties} to use
     * custom RPG translation keys (e.g., "rpg.gear.{instanceId}.name"), ensuring
     * that server-side code like the repair kit UI can display correct item names.
     *
     * @param baseItem The base item to clone
     * @param customId The custom item ID to assign
     * @return A new Item with the custom ID but all other properties from base
     */
    @Nonnull
    public Item createCustomItem(@Nonnull Item baseItem, @Nonnull String customId) {
        Objects.requireNonNull(baseItem, "baseItem cannot be null");
        Objects.requireNonNull(customId, "customId cannot be null");

        // Use Item's copy constructor to clone all properties
        Item customItem = new Item(baseItem);

        // Neutralize durability on death for RPG gear — RPG items are permanent
        neutralizeDurabilityOnDeath(customItem);

        // Inject reskin ResourceType so RPG items match workbench reskin recipes
        injectReskinResourceType(customItem, baseItem);

        // Inject Hexcode interactions + tags for magic weapons
        injectHexcodeIfApplicable(customItem, baseItem);

        // Change the ID to our custom ID
        // The 'id' field is protected, so we need reflection
        try {
            Field idField = Item.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(customItem, customId);
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to set custom item ID");
            throw new RuntimeException("Cannot set item ID", e);
        }

        // Set translationProperties to use custom translation keys
        // This ensures server-side getTranslationKey() returns our custom keys
        // which are registered via UpdateTranslations packet
        String compactInstanceId = extractInstanceIdFromCustomId(customId);
        String nameKey = "rpg.gear." + compactInstanceId + ".name";
        String descKey = "rpg.gear." + compactInstanceId + ".description";

        try {
            Field translationPropsField = Item.class.getDeclaredField("translationProperties");
            translationPropsField.setAccessible(true);
            translationPropsField.set(customItem, new ItemTranslationProperties(nameKey, descKey));
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to set translationProperties for %s", customId);
        }

        // Clear cached packet since we modified the item
        // The Item class caches toPacket() results via SoftReference
        try {
            Field cachedPacketField = Item.class.getDeclaredField("cachedPacket");
            cachedPacketField.setAccessible(true);
            cachedPacketField.set(customItem, null);
        } catch (Exception e) {
            // Field may not exist in all versions - not critical
            LOGGER.atFine().log("Could not clear cachedPacket: %s", e.getMessage());
        }

        // Verify weapon is present after copy (diagnostic logging)
        if (baseItem.getWeapon() != null && customItem.getWeapon() == null) {
            LOGGER.atSevere().log("CRITICAL: Weapon lost during Item copy for %s", customId);
        }

        LOGGER.atFine().log("Created custom item %s: weapon=%s, interactions=%d, translationKey=%s",
            customId,
            customItem.getWeapon() != null ? "present" : "absent",
            customItem.getInteractions() != null ? customItem.getInteractions().size() : 0,
            customItem.getTranslationKey());

        // ========== INTERACTION DIAGNOSTIC LOGGING (FINE level) ==========
        // This logging helps diagnose why custom weapons might not deal damage.
        // Attack interactions require item.getInteractions().get(InteractionType.Primary) != null
        Map<InteractionType, String> baseInteractions = baseItem.getInteractions();
        Map<InteractionType, String> customInteractions = customItem.getInteractions();

        // Log base item interactions (FINE level - per-item diagnostic)
        LOGGER.atFine().log("[INTERACTION] Base item %s: interactions=%d, hasPrimary=%s, hasSecondary=%s",
            baseItem.getId(),
            baseInteractions != null ? baseInteractions.size() : 0,
            baseInteractions != null && baseInteractions.containsKey(InteractionType.Primary),
            baseInteractions != null && baseInteractions.containsKey(InteractionType.Secondary));

        // Log custom item interactions (FINE level - per-item diagnostic)
        LOGGER.atFine().log("[INTERACTION] Custom item %s: interactions=%d, hasPrimary=%s, hasSecondary=%s",
            customId,
            customInteractions != null ? customInteractions.size() : 0,
            customInteractions != null && customInteractions.containsKey(InteractionType.Primary),
            customInteractions != null && customInteractions.containsKey(InteractionType.Secondary));

        // Verify same reference (shallow copy worked)
        if (customInteractions != baseInteractions) {
            LOGGER.atWarning().log("[INTERACTION] Interactions map NOT shared between base and custom item - copy may have failed!");
        } else {
            LOGGER.atFine().log("[INTERACTION] Interactions map is shared (shallow copy OK)");
        }

        // CRITICAL: Check for Primary interaction (keep at SEVERE - this is an actual error)
        if (customInteractions == null || !customInteractions.containsKey(InteractionType.Primary)) {
            LOGGER.atSevere().log("[INTERACTION] CRITICAL: Item %s has NO Primary interaction - ATTACKS WILL FAIL!", customId);
            if (baseInteractions != null) {
                LOGGER.atSevere().log("[INTERACTION] Base item interaction keys: %s", baseInteractions.keySet());
            }
        }

        return customItem;
    }

    /**
     * Extracts the compact instance ID from a custom item ID.
     *
     * <p>For gear items: Strips "rpg_gear_" prefix → "1706123456789_42"
     * (matches GearInstanceId.toCompactString())
     *
     * <p>For stones/maps: Strips "rpg_" prefix only → "stone_1706123456789_42"
     * (matches CustomItemInstanceId.toCompactString())
     *
     * @param customId Format: "rpg_{type}_{instanceId}"
     * @return The compact instance ID matching the respective class's toCompactString()
     */
    @Nonnull
    private String extractInstanceIdFromCustomId(@Nonnull String customId) {
        // Gear: strip full prefix (matches GearInstanceId.toCompactString())
        if (customId.startsWith("rpg_gear_")) {
            return customId.substring("rpg_gear_".length());
        }
        // Stones/Maps: strip only "rpg_" prefix (matches CustomItemInstanceId.toCompactString())
        if (customId.startsWith("rpg_")) {
            return customId.substring("rpg_".length());
        }
        // Unknown format - return as-is
        LOGGER.atWarning().log("Unknown custom item ID format: %s", customId);
        return customId;
    }

    /**
     * Neutralizes durability-on-death for RPG gear items.
     *
     * <p>RPG gear is permanent — it should never lose durability from death penalties.
     * The Item copy constructor inherits {@code durabilityLossOnDeath=true} from the
     * base item. We set it to {@code false} so vanilla death handling skips this item.
     *
     * <p>Note: We intentionally do NOT copy {@code durabilityLossOnHit} or
     * {@code maxDurability} from the base item. The copy constructor leaves these
     * at 0, which means no durability bar and no per-hit degradation — exactly what
     * we want for permanent RPG gear.
     *
     * @param item The custom item to neutralize
     */
    private void neutralizeDurabilityOnDeath(@Nonnull Item item) {
        try {
            Field field = Item.class.getDeclaredField("durabilityLossOnDeath");
            field.setAccessible(true);
            field.setBoolean(item, false);

            LOGGER.atFine().log("Set durabilityLossOnDeath=false for RPG item");
        } catch (NoSuchFieldException | IllegalAccessException e) {
            LOGGER.atWarning().withCause(e).log(
                "Failed to set durabilityLossOnDeath - RPG items may lose durability on death");
        }
    }

    /**
     * Injects the reskin ResourceType onto a custom item so it matches
     * StructuralCrafting reskin recipes at the Builder's Workbench.
     *
     * <p>Determines the item's equipment slot, vanilla quality, and category,
     * looks up the corresponding reskin ResourceType ID from the registry,
     * and appends it to the item's existing ResourceTypes array.
     *
     * <p>The original ResourceTypes are preserved for compatibility with
     * other mods' recipes (e.g., Armory color variants).
     *
     * @param customItem The cloned custom item to modify
     * @param baseItem   The original base item (for slot/quality/category lookup)
     */
    private void injectReskinResourceType(@Nonnull Item customItem, @Nonnull Item baseItem) {
        ReskinResourceTypeRegistry registry = this.reskinRegistry;
        if (registry == null) {
            return; // Reskin system not initialized yet
        }

        try {
            // Determine equipment slot (mirrors DynamicLootRegistry.determineSlot logic)
            EquipmentSlot slot;
            String category;
            String baseItemId = baseItem.getId();
            if (baseItem.getWeapon() != null) {
                // Shields are weapons in Hytale but go in OFF_HAND (detected by name)
                if (baseItemId != null && baseItemId.toLowerCase().contains("shield")) {
                    slot = EquipmentSlot.OFF_HAND;
                    category = WeaponType.SHIELD.name();
                } else {
                    slot = EquipmentSlot.WEAPON;
                    category = WeaponType.fromItemIdOrUnknown(baseItemId).name();
                }
            } else if (baseItem.getArmor() != null) {
                var armorSlot = baseItem.getArmor().getArmorSlot();
                if (armorSlot == null) {
                    return;
                }
                slot = switch (armorSlot) {
                    case Head -> EquipmentSlot.HEAD;
                    case Chest -> EquipmentSlot.CHEST;
                    case Legs -> EquipmentSlot.LEGS;
                    case Hands -> EquipmentSlot.HANDS;
                };
                category = ArmorMaterial.fromItemIdOrSpecial(baseItemId).name();
            } else {
                return; // Not a weapon or armor
            }

            // Determine vanilla quality
            int qualityIndex = baseItem.getQualityIndex();
            ItemQuality quality = ItemQuality.getAssetMap().getAsset(qualityIndex);
            if (quality == null) {
                return;
            }
            String qualityId = quality.getId();

            // Look up the reskin ResourceType for this group
            String reskinTypeId = registry.getResourceTypeId(slot, qualityId, category);
            if (reskinTypeId == null) {
                return; // No reskin group for this item (fewer than 2 items in group)
            }

            // Build new ResourceTypes array: original types + reskin type
            // CRITICAL: Must create NEW array — copy constructor shares reference
            ItemResourceType[] existingTypes = baseItem.getResourceTypes();
            int existingLength = existingTypes != null ? existingTypes.length : 0;

            ItemResourceType reskinType = new ItemResourceType();
            reskinType.id = reskinTypeId;
            reskinType.quantity = 1;

            ItemResourceType[] newTypes = new ItemResourceType[existingLength + 1];
            if (existingTypes != null) {
                System.arraycopy(existingTypes, 0, newTypes, 0, existingLength);
            }
            newTypes[existingLength] = reskinType;

            // Set via reflection
            Field resourceTypesField = Item.class.getDeclaredField("resourceTypes");
            resourceTypesField.setAccessible(true);
            resourceTypesField.set(customItem, newTypes);

            LOGGER.atFine().log("Injected reskin ResourceType %s for %s",
                    reskinTypeId, baseItem.getId());
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log(
                    "Failed to inject reskin ResourceType for %s", baseItem.getId());
        }
    }

    /**
     * Injects a Secondary interaction into an Item.
     *
     * <p>This is used for stones and realm maps to enable right-click functionality.
     * When an item has a Secondary interaction, right-clicking triggers Hytale's
     * interaction system which can open custom UI pages.
     *
     * <p>The RootInteraction ID should reference an asset registered in the
     * asset pack (e.g., "RPG_Stone_Secondary" in Server/Item/RootInteractions/RPG/).
     *
     * @param item The item to modify
     * @param rootInteractionId The RootInteraction asset ID for Secondary interaction
     */
    public void injectSecondaryInteraction(@Nonnull Item item, @Nonnull String rootInteractionId) {
        Objects.requireNonNull(item, "item cannot be null");
        Objects.requireNonNull(rootInteractionId, "rootInteractionId cannot be null");

        try {
            // Get the current interactions map
            Map<InteractionType, String> currentInteractions = item.getInteractions();

            // Create a new EnumMap with existing entries plus our Secondary interaction
            java.util.EnumMap<InteractionType, String> newInteractions =
                new java.util.EnumMap<>(InteractionType.class);

            if (currentInteractions != null && !currentInteractions.isEmpty()) {
                newInteractions.putAll(currentInteractions);
            }

            // Add Secondary interaction
            newInteractions.put(InteractionType.Secondary, rootInteractionId);

            // Use reflection to set the new interactions map
            Field interactionsField = Item.class.getDeclaredField("interactions");
            interactionsField.setAccessible(true);
            interactionsField.set(item, java.util.Collections.unmodifiableMap(newInteractions));

            // Clear cached packet since we modified the item
            try {
                Field cachedPacketField = Item.class.getDeclaredField("cachedPacket");
                cachedPacketField.setAccessible(true);
                cachedPacketField.set(item, null);
            } catch (NoSuchFieldException e) {
                // Field may not exist in all versions - not critical
                LOGGER.atFine().log("Could not clear cachedPacket after interaction injection: %s", e.getMessage());
            }

            LOGGER.atFine().log("[INTERACTION] Injected Secondary interaction '%s' into item %s",
                rootInteractionId, item.getId());

        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log(
                "Failed to inject Secondary interaction into item %s", item.getId());
        }
    }

    // =========================================================================
    // HEXCODE INTEGRATION (interactions + tags for magic weapons)
    // =========================================================================

    /** Cached hex staff interactions read from a real Hexcode item at startup */
    private static volatile Map<InteractionType, String> cachedHexStaffInteractions;
    /** Cached hex book interactions read from a real Hexcode item at startup */
    private static volatile Map<InteractionType, String> cachedHexBookInteractions;
    /** Cached reference to a real Hexcode staff Item (for weapon stat modifiers) */
    @Nullable private static volatile Item cachedHexStaffItem;
    /** Whether hex interaction cache has been initialized */
    private static volatile boolean hexInteractionsCacheInitialized = false;

    /**
     * Initializes the hex interaction cache by reading interactions from actual
     * Hexcode items in the asset map.
     *
     * <p>Must be called AFTER all mod assets are loaded (during or after start()).
     * Reads the resolved interaction strings from real Hexcode items — these strings
     * are guaranteed to resolve in RootInteraction.getAssetMap() because Hytale
     * loaded and validated them from Hexcode's JSON.
     */
    public static void initializeHexInteractionCache() {
        if (hexInteractionsCacheInitialized || !HexcodeCompat.isLoaded()) {
            return;
        }

        HytaleLogger logger = HytaleLogger.forEnclosingClass();

        // Find any Hexcode staff to read its resolved interactions
        String[] staffCandidates = {
            "Hexstaff_Basic_Crude", "Hexstaff_Basic_Copper", "Hexstaff_Basic_Iron",
            "Hexstaff_Basic_Bronze", "Hexstaff_Basic_Thorium"
        };
        for (String staffId : staffCandidates) {
            Item staffItem = Item.getAssetMap().getAsset(staffId);
            if (staffItem != null && staffItem.getInteractions() != null && !staffItem.getInteractions().isEmpty()) {
                cachedHexStaffInteractions = staffItem.getInteractions();
                cachedHexStaffItem = staffItem;
                logger.atInfo().log("[HexInteraction] Cached hex staff interactions + weapon from %s: %s",
                    staffId, cachedHexStaffInteractions.keySet());
                break;
            }
        }

        // Find any Hexcode book to read its resolved interactions
        String[] bookCandidates = {
            "Hex_Book", "Fire_Hexbook", "Ice_Hexbook", "Arcane_Hexbook"
        };
        for (String bookId : bookCandidates) {
            Item bookItem = Item.getAssetMap().getAsset(bookId);
            if (bookItem != null && bookItem.getInteractions() != null && !bookItem.getInteractions().isEmpty()) {
                cachedHexBookInteractions = bookItem.getInteractions();
                logger.atInfo().log("[HexInteraction] Cached hex book interactions from %s: %s",
                    bookId, cachedHexBookInteractions.keySet());
                break;
            }
        }

        if (cachedHexStaffInteractions == null) {
            logger.atWarning().log("[HexInteraction] Could not find any Hexcode staff item to cache interactions");
        }
        if (cachedHexBookInteractions == null) {
            logger.atWarning().log("[HexInteraction] Could not find any Hexcode book item to cache interactions");
        }

        hexInteractionsCacheInitialized = true;
    }

    /**
     * Injects Hexcode interactions and tags onto a magic weapon Item.
     *
     * <p>For staffs/wands: replaces combat interactions (Primary, Secondary, Ability1, Ability3)
     * with hex interactions read from a real Hexcode item. Preserves utility interactions
     * (Use, Pick, SwapFrom, Wielding). Injects Tags.Family: ["HexStaff"].
     *
     * <p>For spellbooks: replaces Secondary with hex book interaction.
     * Injects Tags.Family: ["HexBook"].
     *
     * @param customItem The copied Item to modify
     * @param baseItem The original base item (for weapon type detection)
     */
    private void injectHexcodeIfApplicable(@Nonnull Item customItem, @Nonnull Item baseItem) {
        if (!HexcodeCompat.isLoaded()) {
            return;
        }

        // Determine if this is a magic weapon that should receive hex interactions.
        // ALL magic weapons (staffs, wands, spellbooks) — vanilla, Hexcode, or other mods —
        // become Hexcode casting focuses when Hexcode is loaded.
        String baseId = baseItem.getId();
        if (baseId == null) {
            return;
        }

        WeaponType weaponType = WeaponType.fromItemIdOrUnknown(baseId);
        if (!weaponType.isMagic()) {
            return;
        }
        if (weaponType != WeaponType.STAFF && weaponType != WeaponType.WAND && weaponType != WeaponType.SPELLBOOK) {
            return;
        }

        boolean isHexStaff = (weaponType == WeaponType.STAFF || weaponType == WeaponType.WAND);
        boolean isHexBook = (weaponType == WeaponType.SPELLBOOK);

        Map<InteractionType, String> hexSource = isHexStaff ? cachedHexStaffInteractions : cachedHexBookInteractions;

        if (hexSource == null) {
            LOGGER.atWarning().log("[Hexcode] No cached hex interactions for %s (staff=%s, book=%s) — hex casting will not work",
                baseItem.getId(), isHexStaff, isHexBook);
            return;
        }

        try {
            // === 1. INJECT INTERACTIONS ===
            java.util.EnumMap<InteractionType, String> merged =
                new java.util.EnumMap<>(InteractionType.class);

            if (isHexBook) {
                // Spellbooks: REPLACE all interactions with hex book interactions only.
                // Vanilla spellbooks have Primary (attack) which causes main-hand rendering.
                // Base Hex_Book has ONLY Secondary — no Primary. We must match that exactly.
                merged.putAll(hexSource);
            } else {
                // Staffs/Wands: Start with existing interactions (preserves Use, Pick, Wielding)
                // then overlay hex interactions (Primary, Secondary, Ability1, Ability3)
                Map<InteractionType, String> existing = customItem.getInteractions();
                if (existing != null && !existing.isEmpty()) {
                    merged.putAll(existing);
                }
                merged.putAll(hexSource);
            }

            Field interactionsField = Item.class.getDeclaredField("interactions");
            interactionsField.setAccessible(true);
            interactionsField.set(customItem, java.util.Collections.unmodifiableMap(merged));

            // === 2. INJECT TAGS ===
            String familyTag = isHexStaff ? "HexStaff" : "HexBook";
            try {
                var data = customItem.getData();
                if (data != null) {
                    data.putTags(Map.of("Family", new String[]{familyTag}));
                    // Verify tag was injected by reading it back
                    Map<String, String[]> rawTags = data.getRawTags();
                    String[] familyTags = rawTags != null ? rawTags.get("Family") : null;
                    boolean verified = false;
                    if (familyTags != null) {
                        for (String t : familyTags) {
                            if (familyTag.equals(t)) {
                                verified = true;
                                break;
                            }
                        }
                    }
                    LOGGER.atInfo().log("[Hexcode] Tag Family=%s injected onto %s (base: %s), verified=%s",
                            familyTag, customItem.getId(), baseItem.getId(), verified);
                } else {
                    LOGGER.atWarning().log("[Hexcode] getData() returned null for %s — tags NOT injected",
                            customItem.getId());
                }
            } catch (Exception e) {
                LOGGER.atWarning().log("[Hexcode] Could not inject tag Family=%s onto %s: %s",
                        familyTag, customItem.getId(), e.getMessage());
            }

            // === 3. INJECT PLAYER ANIMATIONS ===
            // Hex staffs use "HexStaff" animations for casting poses, hex books use "HexBook"
            try {
                Field animField = Item.class.getDeclaredField("playerAnimationsId");
                animField.setAccessible(true);
                animField.set(customItem, isHexStaff ? "HexStaff" : "HexBook");
            } catch (Exception e) {
                LOGGER.atFine().log("[Hexcode] Could not inject playerAnimationsId: %s", e.getMessage());
            }

            // === 4. SERVER-SIDE UTILITY + WEAPON FLAGS ===
            //
            // Key Hytale mechanics:
            //   Utility.Compatible (StatModifiersManager:77, InteractionContext:442):
            //     When main-hand item has compatible=true, off-hand item's stats apply
            //     AND Secondary interactions route to off-hand.
            //   Utility.Usable (Inventory:159):
            //     Hard SlotFilter: only items with usable=true can enter the utility slot.
            //   Item copy constructor: SHALLOW copies utility (shared reference).
            //     Must create NEW ItemUtility instance — never modify the shared one.
            //
            // Staffs: Need Compatible=true for interaction routing to off-hand book.
            //   Hexcode staffs already have this from Template_HexStaff.
            //   Vanilla/other mod staffs likely don't — we must inject it.
            //
            // Books: Need Usable=true for utility/off-hand slot placement.
            //   Hexcode books already have this from Template_HexBook.
            //   Vanilla/other mod spellbooks likely don't — we must inject it.
            //   NULL weapon on server (books are off-hand, not main-hand weapons).
            if (isHexStaff) {
                // Replace weapon with a copy from a reference Hexcode staff.
                // The reference weapon has: RenderDualWielded=false, and stat modifiers
                // for Volatility, Magic_Power, MagicCharges — without these, the player
                // has 0 volatility budget and ALL glyphs fizzle with 0 damage.
                // Must create NEW weapon object — Item copy constructor shallow-copies weapon.
                injectHexWeapon(customItem);

                // Ensure Utility.Compatible=true for interaction routing to off-hand book.
                // Create new ItemUtility instance — never modify shared reference from copy.
                ensureUtilityFlag(customItem, false, true);
            } else {
                // Books: null weapon (off-hand items, not main-hand weapons)
                try {
                    Field weaponField = Item.class.getDeclaredField("weapon");
                    weaponField.setAccessible(true);
                    weaponField.set(customItem, null);
                } catch (Exception e) {
                    LOGGER.atFine().log("[Hexcode] Could not strip weapon from book: %s", e.getMessage());
                }

                // Ensure Utility.Usable=true for off-hand/utility slot placement.
                // Create new ItemUtility instance — never modify shared reference from copy.
                ensureUtilityFlag(customItem, true, false);
            }

            // === 5. CLEAR CACHED PACKET ===
            try {
                Field cachedPacketField = Item.class.getDeclaredField("cachedPacket");
                cachedPacketField.setAccessible(true);
                cachedPacketField.set(customItem, null);
            } catch (NoSuchFieldException e) {
                // Not critical
            }

            LOGGER.atFine().log("[Hexcode] Injected hex compat onto %s (base: %s, staff=%s, book=%s): "
                + "interactions=%d, tag=%s",
                customItem.getId(), baseItem.getId(), isHexStaff, isHexBook,
                merged.size(), familyTag);

        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log(
                "[Hexcode] Failed to inject hex interactions into %s", baseItem.getId());
        }
    }

    /**
     * Checks if an Item has a specific Tags.Family value.
     *
     * <p>Uses the same detection method as Hexcode's {@code HexStaffUtil.hasTagFamily()} —
     * reads {@code item.getData().getRawTags()} and checks the "Family" array.
     *
     * @param item The item to check
     * @param familyValue The family tag to look for (e.g., "HexStaff", "HexBook")
     * @return true if the item has this family tag
     */
    private static boolean hasHexFamilyTag(@Nullable Item item, @Nonnull String familyValue) {
        if (item == null) {
            return false;
        }
        try {
            var data = item.getData();
            if (data == null) {
                return false;
            }
            Map<String, String[]> rawTags = data.getRawTags();
            if (rawTags == null) {
                return false;
            }
            String[] familyTags = rawTags.get("Family");
            if (familyTags != null) {
                for (String tag : familyTags) {
                    if (familyValue.equals(tag)) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            // Tag API not available — fall through
        }
        return false;
    }

    /**
     * Ensures the utility field on a custom item has the required flags set.
     *
     * <p>The Item copy constructor shallow-copies utility (shared reference).
     * We must NEVER modify the shared instance — always create a new one.
     * If the existing utility already has the required flag, this is a no-op.
     *
     * @param item The custom item to modify
     * @param needUsable Whether Usable must be true (for off-hand slot placement)
     * @param needCompatible Whether Compatible must be true (for interaction routing)
     */
    /**
     * Replaces the weapon on a magic weapon Item with a copy of a reference Hexcode
     * staff's weapon. This gives the RPG weapon the correct stat modifiers:
     * Volatility, Magic_Power, MagicCharges, RenderDualWielded=false.
     *
     * <p>Without these modifiers, equipping the staff gives the player 0 Volatility,
     * causing all glyphs to fizzle and all spells to do 0 damage.
     *
     * <p>Creates a NEW weapon object via reflection to avoid modifying the shared
     * reference from the Item copy constructor.
     */
    private void injectHexWeapon(@Nonnull Item item) {
        Item hexRef = cachedHexStaffItem;
        if (hexRef == null || hexRef.getWeapon() == null) {
            LOGGER.atWarning().log("[Hexcode] No cached hex staff item — cannot inject weapon stat modifiers");
            return;
        }

        try {
            Object hexWeapon = hexRef.getWeapon();
            Class<?> weaponClass = hexWeapon.getClass();

            // Create new weapon instance
            Object newWeapon = weaponClass.getDeclaredConstructor().newInstance();

            // Copy ALL fields from the hex reference weapon
            for (Field field : weaponClass.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                    continue; // Skip static fields (CODEC, etc.)
                }
                field.setAccessible(true);
                field.set(newWeapon, field.get(hexWeapon));
            }

            // Ensure RenderDualWielded=false (should already be from hex reference)
            Field dualWieldField = weaponClass.getDeclaredField("renderDualWielded");
            dualWieldField.setAccessible(true);
            dualWieldField.setBoolean(newWeapon, false);

            // Set the new weapon on the item
            Field weaponField = Item.class.getDeclaredField("weapon");
            weaponField.setAccessible(true);
            weaponField.set(item, newWeapon);

            // Verify: read back rawStatModifiers to confirm they're present
            Field rawModsField = weaponClass.getDeclaredField("rawStatModifiers");
            rawModsField.setAccessible(true);
            Object rawMods = rawModsField.get(newWeapon);
            int modCount = (rawMods instanceof java.util.Map<?,?> map) ? map.size() : 0;

            LOGGER.atInfo().log("[Hexcode] Injected hex weapon onto %s: %d stat modifier groups, "
                    + "renderDualWielded=%s (from ref: %s)",
                    item.getId(), modCount, dualWieldField.getBoolean(newWeapon),
                    hexRef.getId());
        } catch (Exception e) {
            LOGGER.atWarning().log("[Hexcode] Could not inject hex weapon: %s", e.getMessage());
        }
    }

    private void ensureUtilityFlag(@Nonnull Item item, boolean needUsable, boolean needCompatible) {
        try {
            ItemUtility existing = item.getUtility();
            boolean hasUsable = existing.isUsable();
            boolean hasCompatible = existing.isCompatible();

            // Check if injection is needed
            if ((!needUsable || hasUsable) && (!needCompatible || hasCompatible)) {
                return; // Already has the required flags
            }

            // Create a new ItemUtility with the required flags
            ItemUtility newUtility = new ItemUtility();
            Field usableField = ItemUtility.class.getDeclaredField("usable");
            usableField.setAccessible(true);
            Field compatibleField = ItemUtility.class.getDeclaredField("compatible");
            compatibleField.setAccessible(true);

            usableField.setBoolean(newUtility, needUsable || hasUsable);
            compatibleField.setBoolean(newUtility, needCompatible || hasCompatible);

            // Set on the item via reflection
            Field utilityField = Item.class.getDeclaredField("utility");
            utilityField.setAccessible(true);
            utilityField.set(item, newUtility);

            LOGGER.atFine().log("[Hexcode] Injected utility flags (usable=%s, compatible=%s) onto %s",
                    newUtility.isUsable(), newUtility.isCompatible(), item.getId());
        } catch (Exception e) {
            LOGGER.atWarning().log("[Hexcode] Could not inject utility flags: %s", e.getMessage());
        }
    }

    /**
     * Creates a custom item with a Secondary interaction injected.
     *
     * <p>This is a convenience method that combines {@link #createCustomItem} and
     * {@link #injectSecondaryInteraction} for items that need right-click behavior.
     *
     * @param baseItem The base item to clone
     * @param customId The custom item ID
     * @param secondaryInteractionId The RootInteraction ID for Secondary interaction
     * @return The created custom Item with Secondary interaction
     */
    @Nonnull
    public Item createCustomItemWithSecondaryInteraction(
            @Nonnull Item baseItem,
            @Nonnull String customId,
            @Nonnull String secondaryInteractionId) {

        Item customItem = createCustomItem(baseItem, customId);
        injectSecondaryInteraction(customItem, secondaryInteractionId);
        return customItem;
    }

    /**
     * Registers a custom item in the server-side asset map.
     *
     * <p>After registration, the server will recognize this item ID for interaction validation.
     *
     * @param customId The custom item ID
     * @param customItem The custom Item object (should be created via {@link #createCustomItem})
     * @throws IllegalStateException if service not initialized
     */
    public void registerItem(@Nonnull String customId, @Nonnull Item customItem) {
        registerItemInternal(customId, customItem, null, null, false);
    }

    /**
     * Convenience method to create and register a custom item in one call.
     *
     * <p>If persistence is enabled, the registration is also saved to the database
     * asynchronously.
     *
     * @param baseItem The base item to clone
     * @param customId The custom item ID
     * @return The created custom Item
     */
    @Nonnull
    public Item createAndRegister(@Nonnull Item baseItem, @Nonnull String customId) {
        Item customItem = createCustomItem(baseItem, customId);
        registerItemInternal(customId, customItem, baseItem.getId(), null, true, false);
        return customItem;
    }

    /**
     * Creates and registers a custom item with SYNCHRONOUS database persistence.
     *
     * <p>Unlike {@link #createAndRegister}, this method blocks until the database
     * save completes. Use this for critical registrations where the item must be
     * persisted before continuing (e.g., item generation before spawning).
     *
     * @param baseItem The base item to clone
     * @param customId The custom item ID
     * @return The created custom Item
     */
    @Nonnull
    public Item createAndRegisterSync(@Nonnull Item baseItem, @Nonnull String customId) {
        Item customItem = createCustomItem(baseItem, customId);
        registerItemInternal(customId, customItem, baseItem.getId(), null, true, true);
        return customItem;
    }

    /**
     * Creates and registers a custom item with a Secondary interaction injected.
     *
     * <p>This is used for stones and realm maps that need right-click behavior.
     * The Secondary interaction enables Hytale's interaction system to open
     * custom UI pages when the player right-clicks the item.
     *
     * <p>Uses SYNCHRONOUS database persistence to ensure the item is saved
     * before it's spawned in the world.
     *
     * @param baseItem The base item to clone
     * @param customId The custom item ID
     * @param secondaryInteractionId The RootInteraction asset ID (e.g., "RPG_Stone_Secondary")
     * @return The created custom Item with Secondary interaction
     */
    @Nonnull
    public Item createAndRegisterWithSecondarySync(
            @Nonnull Item baseItem,
            @Nonnull String customId,
            @Nonnull String secondaryInteractionId) {

        Item customItem = createCustomItemWithSecondaryInteraction(baseItem, customId, secondaryInteractionId);
        registerItemInternal(customId, customItem, baseItem.getId(), secondaryInteractionId, true, true);
        return customItem;
    }

    /**
     * Internal registration method with persistence control.
     *
     * @param customId The custom item ID
     * @param customItem The custom Item object
     * @param baseItemId The base item ID (for persistence, may be null)
     * @param secondaryInteractionId The secondary interaction ID (for stones/maps), may be null
     * @param persist Whether to persist to database
     */
    private void registerItemInternal(
            @Nonnull String customId,
            @Nonnull Item customItem,
            @Nullable String baseItemId,
            @Nullable String secondaryInteractionId,
            boolean persist) {
        registerItemInternal(customId, customItem, baseItemId, secondaryInteractionId, persist, false);
    }

    /**
     * Internal registration method with persistence and sync control.
     *
     * <p>Thread-Safety: This method acquires Hytale's StampedLock write lock
     * before modifying the asset map to ensure visibility to all threads.
     *
     * @param customId The custom item ID
     * @param customItem The custom Item object
     * @param baseItemId The base item ID (for persistence, may be null)
     * @param secondaryInteractionId The secondary interaction ID (for stones/maps), may be null
     * @param persist Whether to persist to database
     * @param sync Whether to persist synchronously (blocking)
     */
    private void registerItemInternal(
            @Nonnull String customId,
            @Nonnull Item customItem,
            @Nullable String baseItemId,
            @Nullable String secondaryInteractionId,
            boolean persist,
            boolean sync) {

        Objects.requireNonNull(customId, "customId cannot be null");
        Objects.requireNonNull(customItem, "customItem cannot be null");

        if (!initialized) {
            throw new IllegalStateException(
                "ItemRegistryService not initialized. Call initialize() first.");
        }

        // CRITICAL: Acquire write lock before modifying the asset map
        // The internal map (Object2ObjectOpenCustomHashMap) is NOT thread-safe
        // Without the lock, readers using optimistic reads may see stale data
        long stamp = assetMapLock.writeLock();
        try {
            internalAssetMap.put(customId, customItem);
        } finally {
            assetMapLock.unlockWrite(stamp);
        }

        // Track for cleanup and re-registration on restart
        String effectiveBaseItemId = baseItemId != null ? baseItemId : customItem.getId();
        registeredItems.put(customId, new ItemRegistryEntry(effectiveBaseItemId, secondaryInteractionId));

        // Register in Hexcode's hex asset maps so pedestal detection, casting particles,
        // and glyph colors work with our custom item IDs.
        // All magic weapons get registered — vanilla, Hexcode, and other mods.
        if (baseItemId != null && HexcodeCompat.isLoaded()) {
            WeaponType baseWeaponType = WeaponType.fromItemIdOrUnknown(baseItemId);
            if (baseWeaponType == WeaponType.STAFF || baseWeaponType == WeaponType.WAND) {
                HexcodeCompat.registerHexAsset(customId, baseItemId, true);
            } else if (baseWeaponType == WeaponType.SPELLBOOK) {
                HexcodeCompat.registerHexAsset(customId, baseItemId, false);
            }
        }

        // Persist to database
        if (persist && persistenceEnabled && repository != null && baseItemId != null) {
            if (sync) {
                repository.registerSync(customId, baseItemId, secondaryInteractionId);
            } else {
                repository.register(customId, baseItemId, secondaryInteractionId);
            }
        }

        // ========== POST-REGISTRATION VERIFICATION (FINE level, errors at SEVERE) ==========
        // Verify item is visible in asset map after registration
        Item retrieved = Item.getAssetMap().getAsset(customId);
        if (retrieved == null) {
            LOGGER.atSevere().log("[VERIFY] CRITICAL: Item %s returned NULL from asset map after registration!", customId);
        } else if (retrieved == Item.UNKNOWN) {
            LOGGER.atSevere().log("[VERIFY] CRITICAL: Item %s returned Item.UNKNOWN from asset map after registration!", customId);
        } else {
            // Verify interactions are present (critical for attacks to work)
            Map<InteractionType, String> retrievedInteractions = retrieved.getInteractions();
            boolean hasPrimary = retrievedInteractions != null &&
                                 retrievedInteractions.containsKey(InteractionType.Primary);
            boolean hasSecondary = retrievedInteractions != null &&
                                   retrievedInteractions.containsKey(InteractionType.Secondary);
            boolean hasWeapon = retrieved.getWeapon() != null;

            // Per-item verification is verbose - use FINE level
            LOGGER.atFine().log("[VERIFY] Retrieved item %s: interactions=%d, hasPrimary=%s, hasSecondary=%s, weapon=%s",
                customId,
                retrievedInteractions != null ? retrievedInteractions.size() : 0,
                hasPrimary,
                hasSecondary,
                hasWeapon ? "present" : "absent");

            // Keep actual errors at SEVERE
            if (!hasPrimary && hasWeapon) {
                LOGGER.atSevere().log("[VERIFY] CRITICAL: Retrieved item %s has NO Primary interaction - ATTACKS WILL FAIL!", customId);
                if (retrievedInteractions != null) {
                    LOGGER.atSevere().log("[VERIFY] Available interaction keys: %s", retrievedInteractions.keySet());
                }
            }

            // Verify secondary interaction was persisted correctly for items that need it
            if (secondaryInteractionId != null && !hasSecondary) {
                LOGGER.atSevere().log("[VERIFY] CRITICAL: Item %s should have Secondary interaction '%s' but doesn't!",
                    customId, secondaryInteractionId);
            }

            // Verify weapon wasn't lost
            if (baseItemId != null) {
                Item baseItem = Item.getAssetMap().getAsset(baseItemId);
                if (baseItem != null && baseItem.getWeapon() != null && !hasWeapon) {
                    LOGGER.atSevere().log("[VERIFY] CRITICAL: Item %s lost weapon after registration!", customId);
                }
            }

            LOGGER.atFine().log("Registered custom item: %s (base: %s, secondary: %s, sync: %s)",
                customId, baseItemId, secondaryInteractionId, sync);
        }
    }

    // =========================================================================
    // BATCH OPERATIONS (for reconnect fallback)
    // =========================================================================

    /**
     * Persists any items that are registered in memory but missing from the database.
     *
     * <p>This is used as a fallback when items are found in a player's inventory
     * that weren't in the database cache (edge case recovery).
     *
     * @param customIds Collection of custom item IDs to check and persist
     * @return Number of items persisted
     */
    public int persistMissingItems(@Nonnull Collection<String> customIds) {
        Objects.requireNonNull(customIds, "customIds cannot be null");

        if (!persistenceEnabled || repository == null) {
            return 0;
        }

        Map<String, ItemRegistryEntry> toPersist = new HashMap<>();
        for (String customId : customIds) {
            ItemRegistryEntry entry = registeredItems.get(customId);
            if (entry != null) {
                toPersist.put(customId, entry);
            }
        }

        if (toPersist.isEmpty()) {
            return 0;
        }

        return repository.registerBatch(toPersist);
    }

    /**
     * Updates the last_seen timestamp for items seen in player inventories.
     *
     * <p>This prevents items from being cleaned up while they're still in use.
     *
     * @param customIds Collection of custom item IDs to mark as seen
     */
    public void markItemsSeen(@Nonnull Collection<String> customIds) {
        Objects.requireNonNull(customIds, "customIds cannot be null");

        if (persistenceEnabled && repository != null && !customIds.isEmpty()) {
            repository.updateLastSeen(customIds);
        }
    }

    // =========================================================================
    // QUERY OPERATIONS
    // =========================================================================

    /**
     * Unregisters a custom item from the asset map.
     *
     * <p>Note: This only removes from memory, not from the database.
     * Database entries are cleaned up by the periodic cleanup task.
     *
     * @param customId The custom item ID to unregister
     */
    public void unregisterItem(@Nonnull String customId) {
        Objects.requireNonNull(customId, "customId cannot be null");

        if (!initialized || internalAssetMap == null) {
            registeredItems.remove(customId);
            return;
        }

        // Acquire write lock for thread-safe removal
        long stamp = assetMapLock.writeLock();
        try {
            internalAssetMap.remove(customId);
        } finally {
            assetMapLock.unlockWrite(stamp);
        }
        registeredItems.remove(customId);

        LOGGER.atFine().log("Unregistered custom item: %s", customId);
    }

    /**
     * Checks if a custom item ID is registered in memory.
     *
     * @param customId The item ID to check
     * @return true if registered by this service
     */
    public boolean isRegistered(@Nonnull String customId) {
        return registeredItems.containsKey(customId);
    }

    /**
     * @return null if not registered
     */
    @Nullable
    public String getBaseItemId(@Nonnull String customId) {
        ItemRegistryEntry entry = registeredItems.get(customId);
        return entry != null ? entry.baseItemId() : null;
    }

    /**
     * @return null if not registered or no secondary interaction
     */
    @Nullable
    public String getSecondaryInteractionId(@Nonnull String customId) {
        ItemRegistryEntry entry = registeredItems.get(customId);
        return entry != null ? entry.secondaryInteractionId() : null;
    }

    /**
     * @return null if not registered
     */
    @Nullable
    public ItemRegistryEntry getRegistryEntry(@Nonnull String customId) {
        return registeredItems.get(customId);
    }

    public int getRegisteredCount() {
        return registeredItems.size();
    }

    /**
     * @return null if persistence is not enabled
     */
    @Nullable
    public ItemRegistryRepository getRepository() {
        return repository;
    }

    // =========================================================================
    // LIFECYCLE
    // =========================================================================

    /**
     * Shuts down the service, cleaning up all registered items.
     *
     * <p>Should be called during plugin disable.
     */
    public void shutdown() {
        if (!initialized) {
            return;
        }

        // Stop cleanup scheduler
        if (cleanupScheduler != null) {
            cleanupScheduler.shutdownNow();
            cleanupScheduler = null;
        }

        int count = registeredItems.size();

        // Remove all registered items from the asset map (with lock)
        if (internalAssetMap != null && assetMapLock != null) {
            long stamp = assetMapLock.writeLock();
            try {
                for (String id : registeredItems.keySet()) {
                    internalAssetMap.remove(id);
                }
            } finally {
                assetMapLock.unlockWrite(stamp);
            }
        }

        registeredItems.clear();
        internalAssetMap = null;
        assetMapLock = null;
        repository = null;
        persistenceEnabled = false;
        initialized = false;

        LOGGER.atInfo().log("ItemRegistryService shutdown, cleaned up %d items", count);
    }

    /**
     * Checks if the service is initialized and ready to use.
     *
     * @return true if initialized
     */
    public boolean isInitialized() {
        return initialized;
    }

    /**
     * Checks if database persistence is enabled.
     *
     * @return true if persistence is enabled
     */
    public boolean isPersistenceEnabled() {
        return persistenceEnabled;
    }
}
