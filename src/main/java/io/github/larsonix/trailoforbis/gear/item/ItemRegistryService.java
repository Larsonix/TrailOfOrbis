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
import io.github.larsonix.trailoforbis.gear.model.GearRarity;
import io.github.larsonix.trailoforbis.gear.model.WeaponType;
import io.github.larsonix.trailoforbis.gear.reskin.ReskinResourceTypeRegistry;
import io.github.larsonix.trailoforbis.compat.HexcodeCompat;

import com.hypixel.hytale.server.core.modules.entitystats.modifier.StaticModifier;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
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

    // =========================================================================
    // CACHED REFLECTION FIELDS — resolved once, reused for all item registrations.
    // Eliminates ~200-400μs of getDeclaredField() overhead PER ITEM.
    // =========================================================================

    // Item fields
    private static final Field ITEM_ID_FIELD;
    private static final Field ITEM_MAX_STACK_FIELD;
    private static final Field ITEM_TRANSLATION_PROPS_FIELD;
    private static final Field ITEM_CACHED_PACKET_FIELD;
    private static final Field ITEM_DURABILITY_LOSS_ON_DEATH_FIELD;
    private static final Field ITEM_ARMOR_FIELD;
    private static final Field ITEM_WEAPON_FIELD;
    private static final Field ITEM_UTILITY_FIELD;
    private static final Field ITEM_PLAYER_ANIMATIONS_ID_FIELD;
    private static final Field ITEM_RESOURCE_TYPES_FIELD;
    private static final Field ITEM_INTERACTIONS_FIELD;

    // ItemWeapon fields
    private static final Field WEAPON_RENDER_DUAL_WIELDED_FIELD;
    private static final Field WEAPON_ENTITY_STATS_TO_CLEAR_FIELD;
    private static final Field WEAPON_STAT_MODIFIERS_FIELD;
    private static final Field WEAPON_RAW_STAT_MODIFIERS_FIELD;

    // ItemUtility fields
    private static final Field UTILITY_ENTITY_STATS_TO_CLEAR_FIELD;
    private static final Field UTILITY_STAT_MODIFIERS_FIELD;
    private static final Field UTILITY_USABLE_FIELD;
    private static final Field UTILITY_COMPATIBLE_FIELD;

    static {
        try {
            // Item fields
            ITEM_ID_FIELD = Item.class.getDeclaredField("id");
            ITEM_ID_FIELD.setAccessible(true);

            ITEM_MAX_STACK_FIELD = Item.class.getDeclaredField("maxStack");
            ITEM_MAX_STACK_FIELD.setAccessible(true);

            ITEM_TRANSLATION_PROPS_FIELD = Item.class.getDeclaredField("translationProperties");
            ITEM_TRANSLATION_PROPS_FIELD.setAccessible(true);

            ITEM_CACHED_PACKET_FIELD = Item.class.getDeclaredField("cachedPacket");
            ITEM_CACHED_PACKET_FIELD.setAccessible(true);

            ITEM_DURABILITY_LOSS_ON_DEATH_FIELD = Item.class.getDeclaredField("durabilityLossOnDeath");
            ITEM_DURABILITY_LOSS_ON_DEATH_FIELD.setAccessible(true);

            ITEM_ARMOR_FIELD = Item.class.getDeclaredField("armor");
            ITEM_ARMOR_FIELD.setAccessible(true);

            ITEM_WEAPON_FIELD = Item.class.getDeclaredField("weapon");
            ITEM_WEAPON_FIELD.setAccessible(true);

            ITEM_UTILITY_FIELD = Item.class.getDeclaredField("utility");
            ITEM_UTILITY_FIELD.setAccessible(true);

            ITEM_PLAYER_ANIMATIONS_ID_FIELD = Item.class.getDeclaredField("playerAnimationsId");
            ITEM_PLAYER_ANIMATIONS_ID_FIELD.setAccessible(true);

            ITEM_RESOURCE_TYPES_FIELD = Item.class.getDeclaredField("resourceTypes");
            ITEM_RESOURCE_TYPES_FIELD.setAccessible(true);

            ITEM_INTERACTIONS_FIELD = Item.class.getDeclaredField("interactions");
            ITEM_INTERACTIONS_FIELD.setAccessible(true);

            // ItemWeapon fields
            Class<?> weaponClass = com.hypixel.hytale.server.core.asset.type.item.config.ItemWeapon.class;
            WEAPON_RENDER_DUAL_WIELDED_FIELD = weaponClass.getDeclaredField("renderDualWielded");
            WEAPON_RENDER_DUAL_WIELDED_FIELD.setAccessible(true);

            WEAPON_ENTITY_STATS_TO_CLEAR_FIELD = weaponClass.getDeclaredField("entityStatsToClear");
            WEAPON_ENTITY_STATS_TO_CLEAR_FIELD.setAccessible(true);

            WEAPON_STAT_MODIFIERS_FIELD = weaponClass.getDeclaredField("statModifiers");
            WEAPON_STAT_MODIFIERS_FIELD.setAccessible(true);

            WEAPON_RAW_STAT_MODIFIERS_FIELD = weaponClass.getDeclaredField("rawStatModifiers");
            WEAPON_RAW_STAT_MODIFIERS_FIELD.setAccessible(true);

            // ItemUtility fields
            UTILITY_ENTITY_STATS_TO_CLEAR_FIELD = ItemUtility.class.getDeclaredField("entityStatsToClear");
            UTILITY_ENTITY_STATS_TO_CLEAR_FIELD.setAccessible(true);

            UTILITY_STAT_MODIFIERS_FIELD = ItemUtility.class.getDeclaredField("statModifiers");
            UTILITY_STAT_MODIFIERS_FIELD.setAccessible(true);

            UTILITY_USABLE_FIELD = ItemUtility.class.getDeclaredField("usable");
            UTILITY_USABLE_FIELD.setAccessible(true);

            UTILITY_COMPATIBLE_FIELD = ItemUtility.class.getDeclaredField("compatible");
            UTILITY_COMPATIBLE_FIELD.setAccessible(true);

        } catch (NoSuchFieldException e) {
            throw new ExceptionInInitializerError(
                "Failed to cache reflection fields for ItemRegistryService. " +
                "Hytale version may have changed field names: " + e.getMessage());
        }
    }

    /** Default cleanup interval: 24 hours */
    private static final long CLEANUP_INTERVAL_HOURS = 24;

    /** Default retention period: 30 days */
    private static final int DEFAULT_RETENTION_DAYS = 30;

    /** Demotion threshold: items not observed for this long move from Hot → Cold */
    private static final long DEMOTION_THRESHOLD_MINUTES = 30;

    /** Eviction threshold: cold items not observed for this long are fully removed */
    private static final long EVICTION_THRESHOLD_HOURS = 72;

    /** Startup load window: only load items seen within this many days */
    private static final int STARTUP_LOAD_DAYS = 3;

    /** Maximum items to demote/evict per sweep (lock is held briefly per batch, not per item) */
    private static final int SWEEP_BATCH_SIZE = 5000;

    /**
     * HOT tier: items actively in use. Full Item objects exist in the asset map.
     * Map: customId → ItemRegistryEntry (baseItemId + optional secondaryInteractionId)
     */
    private final Map<String, ItemRegistryEntry> registeredItems = new ConcurrentHashMap<>();

    /**
     * COLD tier: items removed from asset map but retaining metadata for instant re-materialization.
     * Items here can be synchronously promoted back to Hot on demand.
     */
    private final Map<String, ColdEntry> coldItems = new ConcurrentHashMap<>();

    /**
     * Last observation timestamp for all items (hot + cold).
     * Updated by observeItem() whenever an item is seen in a player inventory, equipped, or picked up.
     */
    private final Map<String, Long> lastObservedAt = new ConcurrentHashMap<>();

    /**
     * Metadata retained for a demoted (cold) item. Enough to re-materialize the full Item clone.
     */
    record ColdEntry(@Nonnull String baseItemId, @Nullable String secondaryInteractionId, long demotedAt) {}

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
     * Supplier for currently-active item IDs across all online players.
     * Called by the demotion sweep to ensure equipped items are never demoted.
     * Set via {@link #setActiveItemsSupplier(java.util.function.Supplier)}.
     */
    @Nullable
    private volatile java.util.function.Supplier<Set<String>> activeItemsSupplier;

    /**
     * Tracks hex injections during bulk operations (startup, etc.) for summary logging.
     */
    private int hexInjectionCount = 0;

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
     * Retroactively injects reskin ResourceTypes into all already-registered items.
     *
     * <p>Items loaded from the database cache during {@link #initialize(DataManager)}
     * are created before the reskin system initializes, so they miss the ResourceType
     * injection in {@link #createCustomItem}. This method patches them after the fact.
     *
     * @return Number of items successfully patched
     */
    public int retroInjectReskinResourceTypes() {
        if (reskinRegistry == null) {
            return 0;
        }
        int patched = 0;
        for (Map.Entry<String, ItemRegistryEntry> entry : registeredItems.entrySet()) {
            String customId = entry.getKey();
            String baseItemId = entry.getValue().baseItemId();

            long stamp = assetMapLock.readLock();
            Item customItem;
            try {
                customItem = internalAssetMap.get(customId);
            } finally {
                assetMapLock.unlockRead(stamp);
            }
            if (customItem == null) {
                continue;
            }

            Item baseItem = Item.getAssetMap().getAsset(baseItemId);
            if (baseItem == null || baseItem == Item.UNKNOWN) {
                continue;
            }

            // Always re-inject — replaces any stale ResourceTypes from previous builds
            injectReskinResourceType(customItem, baseItem);
            patched++;
        }
        return patched;
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
     * Loads cached item registrations from the database into the COLD tier.
     *
     * <p>Items are stored as lightweight metadata only — NO Item objects are created
     * and NOTHING is added to Hytale's asset map. This keeps the heap small at boot.
     *
     * <p>Items are promoted to Hot on-demand when players actually need them, via
     * {@link #observeItem(String)} or {@link #markItemsSeen(Collection)}.
     */
    private void loadCachedRegistrations() {
        if (repository == null) {
            return;
        }

        Map<String, ItemRegistryEntry> cached = repository.loadRecent(STARTUP_LOAD_DAYS);
        int loaded = 0;
        int skipped = 0;
        long now = System.currentTimeMillis();

        for (Map.Entry<String, ItemRegistryEntry> entry : cached.entrySet()) {
            String customId = entry.getKey();
            ItemRegistryEntry data = entry.getValue();
            String baseItemId = data.baseItemId();

            // Verify base item exists (skip if removed in a game update)
            Item baseItem = Item.getAssetMap().getAsset(baseItemId);
            if (baseItem == null || baseItem == Item.UNKNOWN) {
                skipped++;
                continue;
            }

            // Load into COLD tier: lightweight metadata, no Item object, no asset map entry.
            coldItems.put(customId, new ColdEntry(baseItemId, data.secondaryInteractionId(), now));
            lastObservedAt.put(customId, now);
            loaded++;
        }

        LOGGER.atInfo().log(
            "Loaded %d item registrations into cold tier (%d skipped — base items missing). " +
            "Hot tier: 0. Items promote on demand when players need them.",
            loaded, skipped);
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

        // Initial demotion sweep: run 60s after boot as safety net
        cleanupScheduler.schedule(() -> {
            try {
                demotionSweep();
            } catch (Exception e) {
                LOGGER.atWarning().withCause(e).log("Item registry initial demotion sweep failed");
            }
        }, 60, TimeUnit.SECONDS);

        // Demotion sweep: run every 15 minutes, move unobserved hot items to cold
        cleanupScheduler.scheduleAtFixedRate(() -> {
            try {
                demotionSweep();
            } catch (Exception e) {
                LOGGER.atWarning().withCause(e).log("Item registry demotion sweep failed");
            }
        }, 15, 15, TimeUnit.MINUTES);

        // Eviction sweep: run every 6 hours, permanently remove old cold items
        cleanupScheduler.scheduleAtFixedRate(() -> {
            try {
                evictionSweep();
            } catch (Exception e) {
                LOGGER.atWarning().withCause(e).log("Item registry eviction sweep failed");
            }
        }, 6, 6, TimeUnit.HOURS);

        // DB cleanup: run once per day, remove very old entries as safety net
        cleanupScheduler.scheduleAtFixedRate(() -> {
            try {
                if (repository != null) {
                    int removed = repository.cleanupOldEntries(DEFAULT_RETENTION_DAYS);
                    if (removed > 0) {
                        LOGGER.atInfo().log("DB safety cleanup: %d stale entries removed (>%d days)", removed, DEFAULT_RETENTION_DAYS);
                    }
                }
            } catch (Exception e) {
                LOGGER.atWarning().withCause(e).log("Item registry DB cleanup failed");
            }
        }, CLEANUP_INTERVAL_HOURS, CLEANUP_INTERVAL_HOURS, TimeUnit.HOURS);

        LOGGER.atInfo().log("Item registry cleanup scheduled: demotion every 15m (threshold %dm), eviction every 6h (threshold %dh), DB cleanup every %dh",
            DEMOTION_THRESHOLD_MINUTES, EVICTION_THRESHOLD_HOURS, CLEANUP_INTERVAL_HOURS);
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

        // Force MaxStack=1 on all RPG gear. Stackable weapon-bases (pillows MaxStack=100,
        // spears MaxStack=30) would otherwise create stackable RPG items, breaking the
        // instanceId system which requires each RPG item to be unique and unstackable.
        if (baseItem.getMaxStack() > 1) {
            try {
                ITEM_MAX_STACK_FIELD.setInt(customItem, 1);
            } catch (Exception e) {
                LOGGER.atWarning().withCause(e).log("Failed to force maxStack=1 for %s", customId);
            }
        }

        // Neutralize durability on death for RPG gear — RPG items are permanent
        neutralizeDurabilityOnDeath(customItem);

        // De-couple weapon/armor/utility from shared base references.
        // The copy constructor shallow-copies these — they share the same object as the
        // base item. We create independent copies so our mutations don't corrupt the base.
        // ALL fields are preserved (full field copy) — we do NOT zero anything.
        decoupleComponents(customItem);

        // Copy playerAnimationsId from base item so the client doesn't log
        // "Missing playerAnimationsId" for every registered custom item during
        // ItemAnimations updates. For hex magic weapons, this is overridden later
        // by injectHexcodeIfApplicable() with "HexStaff" or "HexBook".
        copyPlayerAnimationsId(baseItem, customItem);

        // Inject reskin ResourceType so RPG items match workbench reskin recipes
        injectReskinResourceType(customItem, baseItem);

        // Inject Hexcode interactions + tags for magic weapons
        injectHexcodeIfApplicable(customItem, baseItem);

        // Change the ID to our custom ID (using cached reflection field)
        try {
            ITEM_ID_FIELD.set(customItem, customId);
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to set custom item ID");
            throw new RuntimeException("Cannot set item ID", e);
        }

        // Set translationProperties to use custom translation keys
        String compactInstanceId = extractInstanceIdFromCustomId(customId);
        String nameKey = "rpg.gear." + compactInstanceId + ".name";
        String descKey = "rpg.gear." + compactInstanceId + ".description";

        try {
            ITEM_TRANSLATION_PROPS_FIELD.set(customItem, new ItemTranslationProperties(nameKey, descKey));
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to set translationProperties for %s", customId);
        }

        // Clear cached packet since we modified the item
        try {
            ITEM_CACHED_PACKET_FIELD.set(customItem, null);
        } catch (Exception e) {
            LOGGER.atFine().log("Could not clear cachedPacket: %s", e.getMessage());
        }

        // Verify weapon is present after copy (diagnostic logging).
        // Exception: Hexcode spellbooks intentionally have weapon set to null
        // (they're off-hand items, not main-hand weapons).
        if (baseItem.getWeapon() != null && customItem.getWeapon() == null) {
            String baseId = baseItem.getId();
            WeaponType wt = baseId != null ? WeaponType.fromItemIdOrUnknown(baseId) : WeaponType.UNKNOWN;
            if (wt != WeaponType.SPELLBOOK) {
                LOGGER.atSevere().log("CRITICAL: Weapon lost during Item copy for %s (base: %s)", customId, baseId);
            } else {
                LOGGER.atFine().log("Weapon removed from spellbook %s (intentional - off-hand item)", customId);
            }
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

        // Hytale's Item copy constructor deep-copies the interactions map (new instance).
        // This is correct behavior — prevents cross-contamination between items.
        // Only log at FINE for diagnostic purposes.
        if (customInteractions != baseInteractions) {
            LOGGER.atFine().log("[INTERACTION] Interactions map deep-copied (expected — Hytale copy constructor behavior)");
        } else {
            LOGGER.atFine().log("[INTERACTION] Interactions map is shared (shallow copy)");
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
            ITEM_DURABILITY_LOSS_ON_DEATH_FIELD.setBoolean(item, false);
            LOGGER.atFine().log("Set durabilityLossOnDeath=false for RPG item");
        } catch (IllegalAccessException e) {
            LOGGER.atWarning().withCause(e).log(
                "Failed to set durabilityLossOnDeath - RPG items may lose durability on death");
        }
    }

    /**
     * De-couples weapon/armor/utility components from the shared base item references.
     *
     * <p>The {@code Item} copy constructor shallow-copies {@code armor}, {@code weapon},
     * and {@code utility} — they share the same object as the base item. We CANNOT mutate
     * those shared objects (that would corrupt the base item). Instead, we create fresh
     * instances with ALL fields preserved via full field-by-field reflection copy.
     *
     * <p>This is a de-coupling operation, NOT a stat-zeroing operation. Every field from
     * the original mod item is preserved exactly — knockback resistances, damage resistances,
     * stat modifiers, regenerating values, all of it. Our runtime systems handle stat
     * authority separately (VanillaEquipmentStatSuppressor + RPGDamageSystem).
     *
     * @param item The custom item to de-couple (must be a fresh copy, not the base item)
     */
    private void decoupleComponents(@Nonnull Item item) {
        decoupleArmor(item);
        decoupleWeapon(item);
        decoupleUtility(item);
    }

    /**
     * De-couples the item's {@code ItemArmor} from the shared base reference.
     *
     * <p>Creates an independent copy via full field-by-field copy, then zeroes ALL combat
     * stat fields. Our RPG system is the sole authority for all offensive and defensive
     * numbers — no vanilla or modded combat stats should leak through.
     *
     * <h3>Preserved (base gameplay feel)</h3>
     * <ul>
     *   <li>{@code armorSlot} — structural (Head/Chest/Legs/Hands)</li>
     *   <li>{@code cosmeticsToHide} — visual only</li>
     *   <li>{@code knockbackResistances/knockbackEnhancements} — gameplay feel, not damage numbers</li>
     * </ul>
     *
     * <h3>Zeroed (RPG system is sole authority)</h3>
     * <ul>
     *   <li>{@code baseDamageResistance} — prevents FilterDamageGroup double-dip</li>
     *   <li>{@code statModifiers/rawStatModifiers} — prevents vanilla HP/Stamina/Mana bonuses</li>
     *   <li>{@code damageResistanceValues} — per-element defense (our ElementalCalculator handles this)</li>
     *   <li>{@code damageEnhancementValues/damageEnhancementValuesRaw} — per-element offense</li>
     *   <li>{@code damageClassEnhancement} — per-class (Light/Charged/Signature) scaling</li>
     *   <li>{@code regeneratingValues/regenerating} — resource regen (our stat pipeline handles this)</li>
     *   <li>{@code interactionModifiers/interactionModifiersRaw} — per-interaction combat stat changes</li>
     * </ul>
     */
    private void decoupleArmor(@Nonnull Item item) {
        com.hypixel.hytale.server.core.asset.type.item.config.ItemArmor armor = item.getArmor();
        if (armor == null) {
            return;
        }

        try {
            // Full field-by-field copy first — gets knockback, cosmetics, slot, and any
            // future non-combat fields we don't know about yet.
            Class<?> armorClass = armor.getClass();
            com.hypixel.hytale.server.core.asset.type.item.config.ItemArmor decoupled =
                new com.hypixel.hytale.server.core.asset.type.item.config.ItemArmor(
                    armor.getArmorSlot(), 0.0, null, null);

            for (Field field : armorClass.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                field.setAccessible(true);
                field.set(decoupled, field.get(armor));
            }

            // Zero ALL combat stat fields — RPG system is sole authority for damage numbers.
            // Knockback resistances/enhancements and cosmetics are intentionally preserved.
            String[] combatFields = {
                "baseDamageResistance",       // flat armor → FilterDamageGroup double-dip
                "statModifiers",              // HP/Stamina/Mana bonuses → VanillaEquipmentStatSuppressor backup
                "rawStatModifiers",           // raw version of above (compiled on decode)
                "damageResistanceValues",     // per-element defense → our ElementalCalculator
                "damageResistanceValuesRaw",  // raw version of above (compiled on decode)
                "damageEnhancementValues",    // per-element offense → our damage pipeline
                "damageEnhancementValuesRaw", // raw version of above
                "damageClassEnhancement",     // per-class (Light/Charged/Sig) → our attack type system
                "regeneratingValues",         // resource regen → our stat pipeline
                "regenerating",               // raw regen config
                "interactionModifiers",       // per-interaction combat stats
                "interactionModifiersRaw",    // raw version of above
            };

            for (String fieldName : combatFields) {
                try {
                    Field f = armorClass.getDeclaredField(fieldName);
                    f.setAccessible(true);
                    Class<?> fieldType = f.getType();
                    if (fieldType == double.class) {
                        f.setDouble(decoupled, 0.0);
                    } else if (fieldType == float.class) {
                        f.setFloat(decoupled, 0.0f);
                    } else if (fieldType == int.class) {
                        f.setInt(decoupled, 0);
                    } else {
                        f.set(decoupled, null);
                    }
                } catch (NoSuchFieldException ignored) {
                    // Field doesn't exist in this Hytale version — skip safely
                }
            }

            ITEM_ARMOR_FIELD.set(item, decoupled);

            LOGGER.atFine().log("De-coupled armor for RPG item (combat stats zeroed, knockback/cosmetics preserved)");
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log(
                "Failed to de-couple armor — shared reference remains");
        }
    }

    /**
     * De-couples the item's {@code ItemWeapon} from the shared base reference.
     *
     * <p>Creates an independent copy with ALL fields preserved via full field-by-field
     * reflection copy. This includes statModifiers (SignatureEnergy, Ammo, SignatureCharges),
     * entityStatsToClear, renderDualWielded, and any mod-specific weapon fields.
     */
    private void decoupleWeapon(@Nonnull Item item) {
        com.hypixel.hytale.server.core.asset.type.item.config.ItemWeapon weapon = item.getWeapon();
        if (weapon == null) {
            return;
        }

        try {
            // Copy ALL instance fields from the original weapon to a fresh object.
            // The previous selective copy (only statModifiers, entityStatsToClear,
            // renderDualWielded) missed rawStatModifiers and rawEntityStatsToClear,
            // which Hytale's runtime needs to populate the Signature Ability HUD.
            // This full-field-copy pattern matches injectHexWeapon() which works.
            Class<?> weaponClass = weapon.getClass();
            com.hypixel.hytale.server.core.asset.type.item.config.ItemWeapon clean =
                new com.hypixel.hytale.server.core.asset.type.item.config.ItemWeapon();

            for (Field field : weaponClass.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                field.setAccessible(true);
                field.set(clean, field.get(weapon));
            }

            ITEM_WEAPON_FIELD.set(item, clean);

            @SuppressWarnings("unchecked")
            Int2ObjectMap<StaticModifier[]> statModifiers =
                (Int2ObjectMap<StaticModifier[]>) WEAPON_STAT_MODIFIERS_FIELD.get(clean);
            LOGGER.atFine().log("De-coupled weapon for RPG item (preserved all %d stat modifier(s))",
                statModifiers != null ? statModifiers.size() : 0);
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log(
                "Failed to neutralize weapon stats - vanilla weapon stats may leak");
        }
    }

    /**
     * De-couples the item's {@code ItemUtility} from the shared base reference.
     *
     * <p>Creates an independent copy with ALL fields preserved via full field-by-field
     * reflection copy. This includes usable, compatible, entityStatsToClear, statModifiers,
     * and any mod-specific utility fields.
     */
    private void decoupleUtility(@Nonnull Item item) {
        com.hypixel.hytale.server.core.asset.type.item.config.ItemUtility utility = item.getUtility();
        if (utility == null) {
            return;
        }

        try {
            // Copy ALL instance fields — same pattern as decoupleWeapon().
            Class<?> utilityClass = utility.getClass();
            com.hypixel.hytale.server.core.asset.type.item.config.ItemUtility clean =
                new com.hypixel.hytale.server.core.asset.type.item.config.ItemUtility();

            for (Field field : utilityClass.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                field.setAccessible(true);
                field.set(clean, field.get(utility));
            }

            ITEM_UTILITY_FIELD.set(item, clean);

            @SuppressWarnings("unchecked")
            Int2ObjectMap<StaticModifier[]> statModifiers =
                (Int2ObjectMap<StaticModifier[]>) UTILITY_STAT_MODIFIERS_FIELD.get(clean);
            LOGGER.atFine().log("De-coupled utility for RPG item (preserved all %d stat modifier(s))",
                statModifiers != null ? statModifiers.size() : 0);
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log(
                "Failed to neutralize utility stats - vanilla utility stats may leak");
        }
    }

    /**
     * Copies the {@code playerAnimationsId} field from a base item to a custom item.
     *
     * <p>The {@code Item(Item)} copy constructor may not preserve this field.
     * Without it, the client logs "Missing playerAnimationsId" for every custom
     * item during ItemAnimations updates — thousands of debug warnings per session.
     *
     * @param baseItem The source item
     * @param customItem The target item
     */
    private void copyPlayerAnimationsId(@Nonnull Item baseItem, @Nonnull Item customItem) {
        try {
            Object baseAnimId = ITEM_PLAYER_ANIMATIONS_ID_FIELD.get(baseItem);
            if (baseAnimId != null) {
                ITEM_PLAYER_ANIMATIONS_ID_FIELD.set(customItem, baseAnimId);
            }
        } catch (Exception e) {
            LOGGER.atFine().log("Could not copy playerAnimationsId from %s: %s",
                baseItem.getId(), e.getMessage());
        }
    }

    /**
     * Injects the reskin ResourceType onto a custom item so it matches
     * StructuralCrafting reskin recipes at the Builder's Workbench.
     *
     * <p>Uses the base item's vanilla quality to determine the recipe group.
     * For RPG rarity-aware injection, use {@link #injectReskinResourceType(Item, Item, GearRarity)}.
     *
     * @param customItem The cloned custom item to modify
     * @param baseItem   The original base item (for slot/quality lookup)
     */
    private void injectReskinResourceType(@Nonnull Item customItem, @Nonnull Item baseItem) {
        injectReskinResourceType(customItem, baseItem, null);
    }

    /**
     * Injects reskin ResourceType(s) onto a custom item based on RPG rarity.
     *
     * <p>When {@code rarity} is non-null, uses {@link GearRarity#getAllowedSkinQualities()}
     * to determine which recipe groups the item should match. For LEGENDARY/MYTHIC/UNIQUE,
     * this returns both "Epic" and "Legendary", so the item gets TWO ResourceTypes and
     * matches recipes in both groups at the Builder's Workbench.
     *
     * <p>When {@code rarity} is null, falls back to the base item's vanilla quality
     * (used during retro-inject at startup when GearData is not yet available).
     *
     * @param customItem The cloned custom item to modify
     * @param baseItem   The original base item (for slot/quality lookup)
     * @param rarity     The RPG rarity (null = use vanilla quality fallback)
     */
    private void injectReskinResourceType(@Nonnull Item customItem, @Nonnull Item baseItem,
                                           @Nullable GearRarity rarity) {
        ReskinResourceTypeRegistry registry = this.reskinRegistry;
        if (registry == null) {
            return;
        }

        try {
            EquipmentSlot slot;
            boolean isWeapon = false;
            String baseItemId = baseItem.getId();
            if (baseItem.getWeapon() != null) {
                isWeapon = true;
                if (baseItemId != null && baseItemId.toLowerCase().contains("shield")) {
                    slot = EquipmentSlot.OFF_HAND;
                } else if (baseItemId != null && WeaponType.fromItemIdOrUnknown(baseItemId) == WeaponType.SPELLBOOK) {
                    slot = EquipmentSlot.OFF_HAND;
                } else {
                    slot = EquipmentSlot.WEAPON;
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
            } else {
                return;
            }

            // Determine which vanilla quality IDs to use for ResourceType lookup
            Set<String> qualityIds;
            if (rarity != null) {
                qualityIds = rarity.getAllowedSkinQualities();
            } else {
                // Fallback: use the base item's vanilla quality
                int qualityIndex = baseItem.getQualityIndex();
                ItemQuality quality = (qualityIndex > 0)
                        ? ItemQuality.getAssetMap().getAsset(qualityIndex)
                        : null;
                String qualityId = (quality != null && quality.getId() != null)
                        ? quality.getId()
                        : "Common"; // Default for items with no explicit quality
                qualityIds = Set.of(qualityId);
            }

            // Collect ResourceType IDs for all applicable qualities.
            // For LEGENDARY/MYTHIC/UNIQUE this yields 2 entries (Epic + Legendary).
            String category = isWeapon ? WeaponType.fromItemIdOrUnknown(baseItemId).name() : null;
            List<ItemResourceType> reskinTypes = new ArrayList<>();

            for (String qualityId : qualityIds) {
                String reskinTypeId;
                if (isWeapon) {
                    reskinTypeId = registry.getResourceTypeId(slot, qualityId, category);
                } else {
                    reskinTypeId = registry.getResourceTypeId(slot, qualityId);
                }
                if (reskinTypeId != null) {
                    ItemResourceType rt = new ItemResourceType();
                    rt.id = reskinTypeId;
                    rt.quantity = 1;
                    reskinTypes.add(rt);
                }
            }

            if (reskinTypes.isEmpty()) {
                return;
            }

            // MERGE our reskin types with the BASE ITEM's original ResourceTypes.
            // Always read from baseItem (never customItem) to avoid accumulating reskin
            // types across multiple calls (createCustomItem, batchRegister, reinject).
            // Mod resource types are preserved; our reskin types are fresh each call.
            ItemResourceType[] baseTypes = (ItemResourceType[]) ITEM_RESOURCE_TYPES_FIELD.get(baseItem);
            List<ItemResourceType> merged = new ArrayList<>();
            if (baseTypes != null) {
                Collections.addAll(merged, baseTypes);
            }
            merged.addAll(reskinTypes);

            ITEM_RESOURCE_TYPES_FIELD.set(customItem, merged.toArray(new ItemResourceType[0]));

            // Clear the cached toPacket() result so the client gets the updated ResourceTypes.
            try {
                ITEM_CACHED_PACKET_FIELD.set(customItem, null);
            } catch (Exception ignored) {
                // Not critical
            }

            LOGGER.atFine().log("Set %d reskin ResourceType(s) for %s (rarity=%s, qualities=%s)",
                    reskinTypes.size(), baseItem.getId(),
                    rarity != null ? rarity.name() : "vanilla-fallback", qualityIds);
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log(
                    "Failed to inject reskin ResourceType for %s", baseItem.getId());
        }
    }

    /**
     * Re-injects reskin ResourceType(s) for an already-registered custom item
     * using the correct RPG rarity.
     *
     * <p>Called when GearData becomes available (e.g., during item sync after player
     * login) to correct the vanilla-quality-based fallback from retro-inject.
     *
     * @param customId The custom item ID (e.g., "rpg_gear_XXXX")
     * @param rarity   The RPG rarity from GearData
     */
    public void reinjectReskinResourceType(@Nonnull String customId, @Nonnull GearRarity rarity) {
        ReskinResourceTypeRegistry registry = this.reskinRegistry;
        if (registry == null) {
            return;
        }

        long stamp = assetMapLock.readLock();
        Item customItem;
        try {
            customItem = internalAssetMap.get(customId);
        } finally {
            assetMapLock.unlockRead(stamp);
        }
        if (customItem == null) {
            return;
        }

        ItemRegistryEntry entry = registeredItems.get(customId);
        if (entry == null) {
            return;
        }

        Item baseItem = Item.getAssetMap().getAsset(entry.baseItemId());
        if (baseItem == null || baseItem == Item.UNKNOWN) {
            return;
        }

        injectReskinResourceType(customItem, baseItem, rarity);
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

            // Set the new interactions map (using cached field)
            ITEM_INTERACTIONS_FIELD.set(item, java.util.Collections.unmodifiableMap(newInteractions));

            // Clear cached packet since we modified the item
            try {
                ITEM_CACHED_PACKET_FIELD.set(item, null);
            } catch (Exception e) {
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

            ITEM_INTERACTIONS_FIELD.set(customItem, java.util.Collections.unmodifiableMap(merged));

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
                    LOGGER.atFine().log("[Hexcode] Tag Family=%s injected onto %s (base: %s), verified=%s",
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
                ITEM_PLAYER_ANIMATIONS_ID_FIELD.set(customItem, isHexStaff ? "HexStaff" : "HexBook");
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
                    ITEM_WEAPON_FIELD.set(customItem, null);
                } catch (Exception e) {
                    LOGGER.atFine().log("[Hexcode] Could not strip weapon from book: %s", e.getMessage());
                }

                // Ensure Utility.Usable=true for off-hand/utility slot placement.
                // Create new ItemUtility instance — never modify shared reference from copy.
                ensureUtilityFlag(customItem, true, false);
            }

            // === 5. CLEAR CACHED PACKET ===
            try {
                ITEM_CACHED_PACKET_FIELD.set(customItem, null);
            } catch (Exception e) {
                // Not critical
            }

            hexInjectionCount++;
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
            WEAPON_RENDER_DUAL_WIELDED_FIELD.setBoolean(newWeapon, false);

            // Set the new weapon on the item
            ITEM_WEAPON_FIELD.set(item, newWeapon);

            // Verify: read back rawStatModifiers to confirm they're present
            Object rawMods = WEAPON_RAW_STAT_MODIFIERS_FIELD.get(newWeapon);
            int modCount = (rawMods instanceof java.util.Map<?,?> map) ? map.size() : 0;

            LOGGER.atFine().log("[Hexcode] Injected hex weapon onto %s: %d stat modifier groups, "
                    + "renderDualWielded=%s (from ref: %s)",
                    item.getId(), modCount, WEAPON_RENDER_DUAL_WIELDED_FIELD.getBoolean(newWeapon),
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
            UTILITY_USABLE_FIELD.setBoolean(newUtility, needUsable || hasUsable);
            UTILITY_COMPATIBLE_FIELD.setBoolean(newUtility, needCompatible || hasCompatible);

            // Set on the item
            ITEM_UTILITY_FIELD.set(item, newUtility);

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
        registerItemInternal(customId, customItem, baseItem.getId(), null, true, false, true);
        return customItem;
    }

    /**
     * Creates and registers a custom item with ASYNC persistence and RPG rarity.
     *
     * <p>The in-memory asset map registration is synchronous (instant), making the
     * item usable immediately. Only the database persistence is deferred to a
     * background thread via {@link CompletableFuture}.
     *
     * <p>Use this for hot-path registrations (loot generation) where blocking
     * the world thread for a DB write is unacceptable. If the server crashes
     * before the async write completes, items are re-registered from player
     * inventories on next login via {@code GearItemSyncManager.ensureItemsRegistered()}.
     *
     * <p>Post-registration verification is skipped on this path (hot path optimization).
     *
     * @param baseItem The base item to clone
     * @param customId The custom item ID
     * @param rarity   The RPG rarity for reskin ResourceType (null = use vanilla quality fallback)
     * @return The created custom Item
     */
    @Nonnull
    public Item createAndRegister(@Nonnull Item baseItem, @Nonnull String customId,
                                  @Nullable GearRarity rarity) {
        Item customItem = createCustomItem(baseItem, customId);
        if (rarity != null) {
            injectReskinResourceType(customItem, baseItem, rarity);
        }
        registerItemInternal(customId, customItem, baseItem.getId(), null, true, false, true);
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
     * Creates and registers a custom item with SYNCHRONOUS persistence and RPG rarity.
     *
     * <p>When {@code rarity} is non-null, the reskin ResourceType is assigned based
     * on the RPG rarity (e.g., MYTHIC → Epic+Legendary recipe groups) instead of
     * the base item's vanilla quality. This ensures the Builder's Workbench shows
     * skin options matching the item's RPG rarity tier.
     *
     * @param baseItem The base item to clone
     * @param customId The custom item ID
     * @param rarity   The RPG rarity for reskin ResourceType (null = use vanilla quality fallback)
     * @return The created custom Item
     */
    @Nonnull
    public Item createAndRegisterSync(@Nonnull Item baseItem, @Nonnull String customId,
                                      @Nullable GearRarity rarity) {
        Item customItem = createCustomItem(baseItem, customId);
        if (rarity != null) {
            injectReskinResourceType(customItem, baseItem, rarity);
        }
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
        registerItemInternal(customId, customItem, baseItemId, secondaryInteractionId, persist, false, false);
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
        registerItemInternal(customId, customItem, baseItemId, secondaryInteractionId, persist, sync, false);
    }

    /**
     * Internal registration method with full control.
     *
     * @param customId The custom item ID
     * @param customItem The custom Item object
     * @param baseItemId The base item ID (for persistence, may be null)
     * @param secondaryInteractionId The secondary interaction ID (for stones/maps), may be null
     * @param persist Whether to persist to database
     * @param sync Whether to persist synchronously (blocking)
     * @param skipVerification Whether to skip post-registration verification (for loot hot path)
     */
    private void registerItemInternal(
            @Nonnull String customId,
            @Nonnull Item customItem,
            @Nullable String baseItemId,
            @Nullable String secondaryInteractionId,
            boolean persist,
            boolean sync,
            boolean skipVerification) {

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
        lastObservedAt.put(customId, System.currentTimeMillis());
        // If this item was previously cold (re-registered from fallback), remove from cold tier
        coldItems.remove(customId);

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

        // ========== POST-REGISTRATION VERIFICATION (skipped on loot hot path) ==========
        if (!skipVerification) {
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

                // Verify weapon wasn't lost (spellbooks intentionally have weapon removed)
                if (baseItemId != null) {
                    Item baseItem = Item.getAssetMap().getAsset(baseItemId);
                    if (baseItem != null && baseItem.getWeapon() != null && !hasWeapon) {
                        WeaponType baseWt = WeaponType.fromItemIdOrUnknown(baseItemId);
                        if (baseWt != WeaponType.SPELLBOOK) {
                            LOGGER.atSevere().log("[VERIFY] CRITICAL: Item %s lost weapon after registration!", customId);
                        }
                    }
                }

                LOGGER.atFine().log("Registered custom item: %s (base: %s, secondary: %s, sync: %s)",
                    customId, baseItemId, secondaryInteractionId, sync);
            }
        } else {
            LOGGER.atFine().log("Registered custom item (no verify): %s (base: %s)", customId, baseItemId);
        }
    }

    // =========================================================================
    // BATCH REGISTRATION (for loot pipeline)
    // =========================================================================

    /**
     * Entry for batch item registration.
     *
     * @param baseItem The base item to clone
     * @param customId The custom item ID
     * @param rarity   RPG rarity for reskin ResourceType (nullable)
     */
    public record BatchRegistrationEntry(
            @Nonnull Item baseItem,
            @Nonnull String customId,
            @Nullable GearRarity rarity) {}

    /**
     * Registers multiple custom items in a single batch operation.
     *
     * <p>This is the high-performance path for loot generation. Instead of acquiring
     * Hytale's StampedLock once per item (N lock cycles), this method:
     * <ol>
     *   <li>Creates all custom Item clones <b>outside</b> the lock</li>
     *   <li>Acquires the write lock <b>once</b>, registers all items, releases</li>
     *   <li>Performs tracking, Hexcode compat, and async DB persistence outside the lock</li>
     * </ol>
     *
     * <p>No post-registration verification is performed (saves ~0.1ms × N items).
     * Items registered via this path are verified implicitly when players interact with them.
     *
     * @param entries The items to register
     * @return Number of items actually registered (excludes already-registered items)
     */
    public int createAndRegisterBatch(@Nonnull List<BatchRegistrationEntry> entries) {
        Objects.requireNonNull(entries, "entries cannot be null");

        if (entries.isEmpty() || !initialized) {
            return 0;
        }

        // Phase 1: Create all custom Items OUTSIDE the lock (most expensive part)
        record PreparedItem(String customId, Item customItem, String baseItemId, @Nullable GearRarity rarity) {}
        List<PreparedItem> prepared = new ArrayList<>(entries.size());

        for (BatchRegistrationEntry entry : entries) {
            // Skip if already registered (hot or cold — cold will be promoted on observation)
            if (isRegistered(entry.customId())) {
                continue;
            }

            Item customItem = createCustomItem(entry.baseItem(), entry.customId());
            if (entry.rarity() != null) {
                injectReskinResourceType(customItem, entry.baseItem(), entry.rarity());
            }
            prepared.add(new PreparedItem(
                    entry.customId(), customItem, entry.baseItem().getId(), entry.rarity()));
        }

        if (prepared.isEmpty()) {
            return 0;
        }

        // Phase 2: Single StampedLock write for ALL items
        long stamp = assetMapLock.writeLock();
        try {
            for (PreparedItem item : prepared) {
                internalAssetMap.put(item.customId(), item.customItem());
            }
        } finally {
            assetMapLock.unlockWrite(stamp);
        }

        // Phase 3: Tracking, Hexcode compat, async DB persistence — all outside lock
        long now = System.currentTimeMillis();
        Map<String, ItemRegistryEntry> toPersist = new HashMap<>();

        for (PreparedItem item : prepared) {
            String baseItemId = item.baseItemId();

            registeredItems.put(item.customId(),
                    new ItemRegistryEntry(baseItemId, null));
            lastObservedAt.put(item.customId(), now);
            coldItems.remove(item.customId());

            // Hexcode compat
            if (baseItemId != null && HexcodeCompat.isLoaded()) {
                WeaponType baseWeaponType = WeaponType.fromItemIdOrUnknown(baseItemId);
                if (baseWeaponType == WeaponType.STAFF || baseWeaponType == WeaponType.WAND) {
                    HexcodeCompat.registerHexAsset(item.customId(), baseItemId, true);
                } else if (baseWeaponType == WeaponType.SPELLBOOK) {
                    HexcodeCompat.registerHexAsset(item.customId(), baseItemId, false);
                }
            }

            // Collect for batch DB persist
            if (baseItemId != null) {
                toPersist.put(item.customId(), new ItemRegistryEntry(baseItemId, null));
            }
        }

        // Async batch DB persist
        if (persistenceEnabled && repository != null && !toPersist.isEmpty()) {
            repository.registerBatch(toPersist);
        }

        LOGGER.atInfo().log("Batch-registered %d items (single lock cycle)", prepared.size());
        return prepared.size();
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

        if (!customIds.isEmpty()) {
            // Update in-memory observation timestamps (always, even without persistence)
            long now = System.currentTimeMillis();
            int promoted = 0;
            for (String id : customIds) {
                lastObservedAt.put(id, now);
                // Promote cold items synchronously (player has this item — it must be Hot)
                ColdEntry cold = coldItems.get(id);
                if (cold != null) {
                    promoteToHot(id, cold);
                    promoted++;
                }
            }

            if (promoted > 0) {
                LOGGER.atInfo().log("Promoted %d items from cold to hot (batch markItemsSeen, hot: %d, cold: %d)",
                    promoted, registeredItems.size(), coldItems.size());
            }

            // Update DB timestamps
            if (persistenceEnabled && repository != null) {
                repository.updateLastSeen(customIds);
            }
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
            coldItems.remove(customId);
            lastObservedAt.remove(customId);
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
        coldItems.remove(customId);
        lastObservedAt.remove(customId);

        LOGGER.atFine().log("Unregistered custom item: %s", customId);
    }

    /**
     * Checks if a custom item ID is registered in memory.
     *
     * @param customId The item ID to check
     * @return true if registered by this service
     */
    public boolean isRegistered(@Nonnull String customId) {
        if (registeredItems.containsKey(customId)) return true;
        // Also check cold tier — item is still "ours" even if demoted
        ColdEntry cold = coldItems.get(customId);
        if (cold != null) {
            // Auto-promote: something is asking about this item, it's probably needed
            promoteToHot(customId, cold);
            // Verify promotion succeeded (fails if base item no longer exists)
            return registeredItems.containsKey(customId);
        }
        return false;
    }

    /**
     * @return null if not registered
     */
    @Nullable
    public String getBaseItemId(@Nonnull String customId) {
        ItemRegistryEntry entry = registeredItems.get(customId);
        if (entry != null) return entry.baseItemId();
        // Check cold tier
        ColdEntry cold = coldItems.get(customId);
        return cold != null ? cold.baseItemId() : null;
    }

    /**
     * @return null if not registered or no secondary interaction
     */
    @Nullable
    public String getSecondaryInteractionId(@Nonnull String customId) {
        ItemRegistryEntry entry = registeredItems.get(customId);
        if (entry != null) return entry.secondaryInteractionId();
        ColdEntry cold = coldItems.get(customId);
        return cold != null ? cold.secondaryInteractionId() : null;
    }

    /**
     * @return null if not registered
     */
    @Nullable
    public ItemRegistryEntry getRegistryEntry(@Nonnull String customId) {
        ItemRegistryEntry entry = registeredItems.get(customId);
        if (entry != null) return entry;
        // Check cold tier — promote if needed
        ColdEntry cold = coldItems.get(customId);
        if (cold != null) {
            promoteToHot(customId, cold);
            return registeredItems.get(customId);
        }
        return null;
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
    // HOT/COLD TIER MANAGEMENT
    // =========================================================================

    /**
     * Sets the supplier for active item IDs. Called during plugin init.
     * The supplier should return all RPG item IDs currently held by online players.
     */
    public void setActiveItemsSupplier(@Nullable java.util.function.Supplier<Set<String>> supplier) {
        this.activeItemsSupplier = supplier;
    }

    /**
     * Observes an item as currently in use. Keeps it in the Hot tier.
     * If the item is in the Cold tier, synchronously promotes it back to Hot.
     *
     * <p>Call this from any code path that interacts with an RPG item:
     * inventory sync, equipment change, item pickup, weapon switch.
     *
     * @param customId The custom item ID (rpg_gear_*, rpg_map_*, rpg_gem_*)
     */
    public void observeItem(@Nonnull String customId) {
        if (customId == null || customId.isEmpty()) return;
        lastObservedAt.put(customId, System.currentTimeMillis());

        // If cold, promote synchronously — the item is needed right now
        ColdEntry cold = coldItems.get(customId);
        if (cold != null) {
            promoteToHot(customId, cold);
        }
    }

    /**
     * Synchronously re-materializes a cold item into the asset map (Hot tier).
     * Called when any observation detects a cold item.
     */
    private void promoteToHot(@Nonnull String customId, @Nonnull ColdEntry cold) {
        if (!initialized || internalAssetMap == null) return;

        Item baseItem = Item.getAssetMap().getAsset(cold.baseItemId());
        if (baseItem == null || baseItem == Item.UNKNOWN) {
            LOGGER.atWarning().log("Cannot promote cold item %s — base item %s not found",
                customId, cold.baseItemId());
            coldItems.remove(customId);
            return;
        }

        try {
            Item customItem;
            if (cold.secondaryInteractionId() != null) {
                customItem = createCustomItemWithSecondaryInteraction(
                    baseItem, customId, cold.secondaryInteractionId());
            } else {
                customItem = createCustomItem(baseItem, customId);
            }

            long stamp = assetMapLock.writeLock();
            try {
                internalAssetMap.put(customId, customItem);
            } finally {
                assetMapLock.unlockWrite(stamp);
            }

            registeredItems.put(customId, new ItemRegistryEntry(cold.baseItemId(), cold.secondaryInteractionId()));
            coldItems.remove(customId);
            lastObservedAt.put(customId, System.currentTimeMillis());

            // Re-register in Hexcode's hex asset maps (if applicable)
            if (HexcodeCompat.isLoaded()) {
                WeaponType baseWeaponType = WeaponType.fromItemIdOrUnknown(cold.baseItemId());
                if (baseWeaponType == WeaponType.STAFF || baseWeaponType == WeaponType.WAND) {
                    HexcodeCompat.registerHexAsset(customId, cold.baseItemId(), true);
                } else if (baseWeaponType == WeaponType.SPELLBOOK) {
                    HexcodeCompat.registerHexAsset(customId, cold.baseItemId(), false);
                }
            }

            LOGGER.atFine().log("Promoted cold item %s back to Hot (base: %s)", customId, cold.baseItemId());
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to promote cold item %s", customId);
            coldItems.remove(customId);
        }
    }

    /**
     * Demotion sweep: moves hot items not observed within the threshold to Cold tier.
     * Removes their full Item clone from the asset map, retaining only lightweight metadata.
     * Batched to avoid holding the write lock for too long.
     */
    private void demotionSweep() {
        if (!initialized || internalAssetMap == null) return;

        // SAFETY: Before checking timestamps, refresh observations for all items
        // currently held by online players. This prevents demoting equipped weapons.
        java.util.function.Supplier<Set<String>> supplier = this.activeItemsSupplier;
        if (supplier != null) {
            try {
                Set<String> activeIds = supplier.get();
                if (activeIds != null && !activeIds.isEmpty()) {
                    long now = System.currentTimeMillis();
                    for (String id : activeIds) {
                        lastObservedAt.put(id, now);
                    }
                    LOGGER.atFine().log("Demotion safety scan: refreshed %d active player items", activeIds.size());
                }
            } catch (Exception e) {
                LOGGER.atWarning().withCause(e).log("Demotion safety scan failed — skipping sweep to be safe");
                return;
            }
        }

        long cutoff = System.currentTimeMillis() - (DEMOTION_THRESHOLD_MINUTES * 60 * 1000);
        List<String> candidates = new ArrayList<>();

        for (Map.Entry<String, ItemRegistryEntry> entry : registeredItems.entrySet()) {
            Long lastSeen = lastObservedAt.get(entry.getKey());
            if (lastSeen == null || lastSeen < cutoff) {
                candidates.add(entry.getKey());
            }
        }

        if (candidates.isEmpty()) return;

        // Phase 1: Move entries from hot to cold (ConcurrentHashMap ops, no lock needed)
        List<String> toDemote = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (int i = 0; i < candidates.size() && toDemote.size() < SWEEP_BATCH_SIZE; i++) {
            String customId = candidates.get(i);
            ItemRegistryEntry entry = registeredItems.remove(customId);
            if (entry == null) continue; // already removed by another thread
            coldItems.put(customId, new ColdEntry(entry.baseItemId(), entry.secondaryInteractionId(), now));
            toDemote.add(customId);
        }

        // Phase 2: Batch-remove from asset map under a single write lock
        // Guard: only remove items still in cold tier (a concurrent promotion may have moved them back)
        if (!toDemote.isEmpty()) {
            long stamp = assetMapLock.writeLock();
            try {
                for (String customId : toDemote) {
                    if (coldItems.containsKey(customId)) {
                        internalAssetMap.remove(customId);
                    }
                }
            } finally {
                assetMapLock.unlockWrite(stamp);
            }

            LOGGER.atInfo().log("Demotion sweep: %d items moved Hot -> Cold (threshold: %dm, hot: %d, cold: %d)",
                toDemote.size(), DEMOTION_THRESHOLD_MINUTES, registeredItems.size(), coldItems.size());
        }
    }

    /**
     * Eviction sweep: permanently removes cold items not observed within the eviction threshold.
     * Removes from cold tier, lastObservedAt, and database.
     */
    private void evictionSweep() {
        long cutoff = System.currentTimeMillis() - (EVICTION_THRESHOLD_HOURS * 60 * 60 * 1000);
        List<String> candidates = new ArrayList<>();

        for (Map.Entry<String, ColdEntry> entry : coldItems.entrySet()) {
            Long lastSeen = lastObservedAt.get(entry.getKey());
            if (lastSeen == null || lastSeen < cutoff) {
                candidates.add(entry.getKey());
            }
        }

        if (candidates.isEmpty()) return;

        int evicted = 0;
        List<String> toDeleteFromDb = new ArrayList<>();

        for (int i = 0; i < candidates.size() && evicted < SWEEP_BATCH_SIZE; i++) {
            String customId = candidates.get(i);
            if (coldItems.remove(customId) != null) {
                lastObservedAt.remove(customId);
                toDeleteFromDb.add(customId);
                evicted++;
            }
        }

        // Batch-delete from database
        if (!toDeleteFromDb.isEmpty() && persistenceEnabled && repository != null) {
            int dbRemoved = repository.deleteByIds(toDeleteFromDb);
            LOGGER.atInfo().log("Eviction sweep: %d items permanently removed (threshold: %dh, %d deleted from DB, hot: %d, cold: %d)",
                evicted, EVICTION_THRESHOLD_HOURS, dbRemoved, registeredItems.size(), coldItems.size());
        } else if (evicted > 0) {
            LOGGER.atInfo().log("Eviction sweep: %d items permanently removed (threshold: %dh, hot: %d, cold: %d)",
                evicted, EVICTION_THRESHOLD_HOURS, registeredItems.size(), coldItems.size());
        }
    }

    /** @return Number of items in the Hot tier (full asset map entries). */
    public int getHotItemCount() { return registeredItems.size(); }

    /** @return Number of items in the Cold tier (metadata only, no asset map entry). */
    public int getColdItemCount() { return coldItems.size(); }

    /** @return Total items tracked across both tiers. */
    public int getTotalItemCount() { return registeredItems.size() + coldItems.size(); }

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

        int hotCount = registeredItems.size();
        int coldCount = coldItems.size();

        // Remove all hot items from the asset map (with lock)
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
        coldItems.clear();
        lastObservedAt.clear();
        internalAssetMap = null;
        assetMapLock = null;
        repository = null;
        persistenceEnabled = false;
        initialized = false;

        LOGGER.atInfo().log("ItemRegistryService shutdown, cleaned up %d hot + %d cold items", hotCount, coldCount);
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
