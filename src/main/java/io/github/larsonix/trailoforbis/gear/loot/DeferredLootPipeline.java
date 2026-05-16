package io.github.larsonix.trailoforbis.gear.loot;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import io.github.larsonix.trailoforbis.gear.generation.GearGenerator;
import io.github.larsonix.trailoforbis.gear.generation.GearGenerator.GeneratedGear;
import io.github.larsonix.trailoforbis.gear.item.ItemRegistryService;
import io.github.larsonix.trailoforbis.gear.item.ItemRegistryService.BatchRegistrationEntry;
import io.github.larsonix.trailoforbis.gear.item.ItemWorldSyncService;
import io.github.larsonix.trailoforbis.gear.loot.LootCalculator.LootRoll;
import io.github.larsonix.trailoforbis.gear.loot.LootCategory.ImplicitRoll;
import io.github.larsonix.trailoforbis.gear.loot.LootCategory.SuperCategory;
import io.github.larsonix.trailoforbis.gear.loot.LootSettings.MobType;
import io.github.larsonix.trailoforbis.gear.model.GearData;
import io.github.larsonix.trailoforbis.gear.model.GearRarity;
import io.github.larsonix.trailoforbis.gear.util.GearUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Pipelined loot generation that moves expensive work off the ECS death tick.
 *
 * <h2>Architecture (250ms budget at 30 TPS)</h2>
 * <pre>
 * Tick 0 (0ms) ─ CAPTURE
 *   LootListener.onComponentAdded() captures DeathContext → queue (~0.1ms)
 *
 * ~66ms ─ GENERATE (background thread)
 *   Drain queue, generate all items (pure computation, thread-safe)
 *
 * ~100-150ms ─ FINALIZE (world thread via world.execute())
 *   Batch-register items (single StampedLock), sync to nearby players, spawn first wave
 *
 * ~150-250ms ─ SPAWN REMAINING (world thread, staggered)
 *   Spawn remaining items in waves to avoid client entity burst
 * </pre>
 *
 * <h2>Key Benefits</h2>
 * <ul>
 *   <li>Death tick drops from ~10-20ms → ~0.1ms</li>
 *   <li>Multiple concurrent deaths coalesced into one batch</li>
 *   <li>Single StampedLock cycle for all items</li>
 *   <li>Single player lookup for all deaths</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 * <p>Queue operations are lock-free (ConcurrentLinkedQueue). Generation uses
 * ThreadLocalRandom. Finalization runs on the world thread.
 */
public final class DeferredLootPipeline {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /** Accumulation window before processing queued deaths (ms). ~2 ticks at 30 TPS. */
    private static final long ACCUMULATION_WINDOW_MS = 66;

    /** Max items to spawn per tick wave. */
    private static final int SPAWN_BATCH_SIZE = 4;

    /** Delay between spawn waves (ms). ~1 tick at 30 TPS. */
    private static final long SPAWN_STAGGER_MS = 33;

    // ── Dependencies ──

    private final LootCalculator calculator;
    private final LootGenerator generator;
    private final ItemRegistryService itemRegistry;
    private final ItemWorldSyncService worldSyncService;

    // ── State ──

    private final ConcurrentLinkedQueue<DeathContext> pendingDeaths = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean processingScheduled = new AtomicBoolean(false);
    private final ScheduledExecutorService executor;
    private volatile boolean shuttingDown = false;

    // ── Records ──

    /**
     * Lightweight death context captured during the ECS death tick.
     * All fields are immutable values — no entity refs or store refs retained.
     */
    public record DeathContext(
            @Nonnull Vector3d position,
            @Nonnull MobType mobType,
            int mobLevel,
            int playerLevel,
            @Nonnull java.util.UUID killerPlayerId,
            @Nonnull RealmLootContext realmContext,
            int qualityBonus,
            @Nonnull World world
    ) {}

    /**
     * A batch of generated items from a single mob death, pending finalization.
     */
    private record PendingLoot(
            @Nonnull DeathContext death,
            @Nonnull List<GeneratedGear> items
    ) {}

    /**
     * An item ready to spawn, with pre-computed velocity.
     */
    private record SpawnEntry(
            @Nonnull ItemStack item,
            @Nonnull Vector3d position,
            float vx, float vy, float vz
    ) {}

    // =========================================================================
    // CONSTRUCTOR
    // =========================================================================

    public DeferredLootPipeline(
            @Nonnull LootCalculator calculator,
            @Nonnull LootGenerator generator,
            @Nonnull ItemRegistryService itemRegistry,
            @Nonnull ItemWorldSyncService worldSyncService) {
        this.calculator = Objects.requireNonNull(calculator);
        this.generator = Objects.requireNonNull(generator);
        this.itemRegistry = Objects.requireNonNull(itemRegistry);
        this.worldSyncService = Objects.requireNonNull(worldSyncService);
        this.executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "RPG-LootPipeline");
            t.setDaemon(true);
            return t;
        });
    }

    // =========================================================================
    // PUBLIC API — called from ECS death tick
    // =========================================================================

    /**
     * Queues a mob death for deferred loot processing.
     *
     * <p>This is the ONLY method called from the ECS death tick.
     * It captures the death context and schedules background processing.
     * Total cost: ~0.1ms (queue add + CAS).
     *
     * @param ctx The death context with all parameters needed for loot generation
     */
    public void queueDeath(@Nonnull DeathContext ctx) {
        if (shuttingDown) {
            return;
        }
        pendingDeaths.add(ctx);

        // Schedule processing if not already scheduled.
        // compareAndSet ensures only ONE processing task runs per accumulation window.
        if (processingScheduled.compareAndSet(false, true)) {
            executor.schedule(this::processAccumulatedDeaths,
                    ACCUMULATION_WINDOW_MS, TimeUnit.MILLISECONDS);
        }
    }

    // =========================================================================
    // BACKGROUND THREAD — item generation (pure computation)
    // =========================================================================

    /**
     * Drains all accumulated deaths and generates items on the background thread.
     * Then schedules finalization on the world thread.
     */
    private void processAccumulatedDeaths() {
        // Allow new scheduling immediately
        processingScheduled.set(false);

        if (shuttingDown) {
            return;
        }

        // Drain all pending deaths
        List<DeathContext> batch = new ArrayList<>();
        DeathContext ctx;
        while ((ctx = pendingDeaths.poll()) != null) {
            batch.add(ctx);
        }

        if (batch.isEmpty()) {
            return;
        }

        LOGGER.atFine().log("[LootPipeline] Processing %d accumulated death(s)", batch.size());

        // Generate all items OFF the world thread
        List<PendingLoot> allLoot = new ArrayList<>();
        for (DeathContext death : batch) {
            List<GeneratedGear> items = generateItemsForDeath(death);
            if (!items.isEmpty()) {
                allLoot.add(new PendingLoot(death, items));
            }
        }

        if (allLoot.isEmpty()) {
            LOGGER.atFine().log("[LootPipeline] No items generated from %d death(s)", batch.size());
            return;
        }

        // Count total items for logging
        int totalItems = allLoot.stream().mapToInt(l -> l.items().size()).sum();
        LOGGER.atFine().log("[LootPipeline] Generated %d item(s) from %d death(s), scheduling finalization",
                totalItems, allLoot.size());

        // Schedule finalization on the world thread
        // Group by world in case deaths span multiple worlds (realm transitions)
        var byWorld = new java.util.LinkedHashMap<World, List<PendingLoot>>();
        for (PendingLoot loot : allLoot) {
            byWorld.computeIfAbsent(loot.death().world(), k -> new ArrayList<>()).add(loot);
        }

        for (var entry : byWorld.entrySet()) {
            World world = entry.getKey();
            List<PendingLoot> worldLoot = entry.getValue();

            if (!world.isAlive()) {
                LOGGER.atFine().log("[LootPipeline] Skipping %d loot batch(es) — world is dead",
                        worldLoot.size());
                continue;
            }

            world.execute(() -> finalizeBatch(world, worldLoot));
        }
    }

    /**
     * Generates items for a single death context. Runs on the background thread.
     *
     * <p>All computation here is thread-safe: ThreadLocalRandom, immutable configs,
     * no world/store access.
     */
    private List<GeneratedGear> generateItemsForDeath(@Nonnull DeathContext death) {
        // Calculate loot roll
        LootRoll lootRoll = calculator.calculateLoot(
                death.killerPlayerId(), death.mobType(),
                death.mobLevel(), death.playerLevel(),
                death.position(), death.realmContext()
        );

        if (!lootRoll.shouldDrop()) {
            return List.of();
        }

        // Generate items using the side-effect-free path
        List<GeneratedGear> items = new ArrayList<>();
        for (int i = 0; i < lootRoll.dropCount(); i++) {
            GeneratedGear gear = generateSingleDropDeferred(
                    lootRoll.itemLevel(), lootRoll.rarityBonus());
            if (gear != null) {
                items.add(gear);
            }
        }

        // Apply quality bonus if applicable
        if (death.qualityBonus() > 0 && !items.isEmpty()) {
            List<GeneratedGear> boosted = new ArrayList<>(items.size());
            for (GeneratedGear gear : items) {
                int boostedQuality = Math.min(101, gear.gearData().quality() + death.qualityBonus());
                if (boostedQuality != gear.gearData().quality()) {
                    GearData updated = gear.gearData().withQuality(boostedQuality);
                    ItemStack updatedItem = GearUtils.setGearData(gear.finalItem(), updated);
                    boosted.add(new GeneratedGear(updatedItem, updated, gear.baseItemAsset()));
                } else {
                    boosted.add(gear);
                }
            }
            items = boosted;
        }

        if (!items.isEmpty()) {
            LOGGER.atInfo().log("Generated %d loot drops at level %d with %.1f%% rarity bonus",
                    items.size(), lootRoll.itemLevel(), lootRoll.rarityBonus());
        }

        return items;
    }

    /**
     * Generates a single drop using the implicit-driven pipeline and the
     * side-effect-free {@code GearGenerator.generateOnly()}.
     *
     * <p>Mirrors {@code LootGenerator.generateSingleDropDynamic()} but uses
     * the registration-free generation path.
     */
    @Nullable
    private GeneratedGear generateSingleDropDeferred(int itemLevel, double rarityBonus) {
        DynamicLootRegistry dynamicRegistry = generator.getDynamicRegistry();
        if (!dynamicRegistry.isDiscovered()) {
            return null;
        }

        GearGenerator gearGen = generator.getGearGenerator();
        LootCategoryConfig categoryConfig = generator.getCategoryConfig();

        // 1. Roll rarity
        double decimalBonus = rarityBonus / 100.0;
        GearRarity rarity = gearGen.getRarityRoller().roll(decimalBonus);

        // 2. Roll super-category → category → implicit
        java.util.Random random = ThreadLocalRandom.current();
        SuperCategory superCat = categoryConfig.rollSuperCategory(random);
        LootCategory category = categoryConfig.rollCategory(superCat, random);
        if (category == null) return null;

        ImplicitRoll implicitRoll = categoryConfig.rollImplicit(category, random);
        if (implicitRoll == null) return null;

        // 3. Select skin
        String skinItemId = selectSkinFromImplicit(dynamicRegistry, implicitRoll, rarity);
        if (skinItemId == null) return null;

        // 4. Create base item
        ItemStack skinItem = generator.createBaseItem(skinItemId);
        if (skinItem == null) return null;

        // 5. Generate gear WITHOUT registration
        return gearGen.generateOnly(
                skinItem, itemLevel, implicitRoll.slotString(), rarity,
                implicitRoll.equipmentType(),
                implicitRoll.skinWeaponType(),
                implicitRoll.element()
        );
    }

    /**
     * Selects a skin item ID from the implicit roll.
     * Mirrors LootGenerator.selectSkinFromImplicit().
     */
    @Nullable
    private String selectSkinFromImplicit(
            @Nonnull DynamicLootRegistry registry,
            @Nonnull ImplicitRoll roll,
            @Nonnull GearRarity rarity) {
        if (roll.skinMaterial() != null) {
            LootGenerator.EquipmentSlot slot = mapStringToSlot(roll.slotString());
            return registry.selectSkinForMaterial(slot, roll.skinMaterial(), rarity);
        } else if (roll.skinWeaponType() != null) {
            return registry.selectSkinForWeaponType(roll.skinWeaponType(), rarity);
        }
        return null;
    }

    private static LootGenerator.EquipmentSlot mapStringToSlot(String slotString) {
        return switch (slotString.toLowerCase()) {
            case "weapon" -> LootGenerator.EquipmentSlot.WEAPON;
            case "head" -> LootGenerator.EquipmentSlot.HEAD;
            case "chest" -> LootGenerator.EquipmentSlot.CHEST;
            case "legs" -> LootGenerator.EquipmentSlot.LEGS;
            case "hands" -> LootGenerator.EquipmentSlot.HANDS;
            case "shield", "off_hand" -> LootGenerator.EquipmentSlot.OFF_HAND;
            default -> LootGenerator.EquipmentSlot.WEAPON;
        };
    }

    // =========================================================================
    // WORLD THREAD — finalization (registration + sync + spawn)
    // =========================================================================

    /**
     * Finalizes a batch of generated loot on the world thread.
     *
     * <p>Performs batch registration, player sync, and staggered entity spawning.
     * Total world-thread cost: ~3-8ms for typical batches (vs 40-80ms without pipeline).
     */
    private void finalizeBatch(@Nonnull World world, @Nonnull List<PendingLoot> allLoot) {
        if (!world.isAlive()) {
            return;
        }

        Store<EntityStore> store = world.getEntityStore().getStore();

        // ── 1. Batch-register ALL items (single StampedLock cycle) ──
        List<BatchRegistrationEntry> registrations = new ArrayList<>();
        for (PendingLoot loot : allLoot) {
            for (GeneratedGear gear : loot.items()) {
                String customId = gear.gearData().getItemId();
                if (customId != null) {
                    registrations.add(new BatchRegistrationEntry(
                            gear.baseItemAsset(), customId, gear.gearData().rarity()));
                }
            }
        }

        if (!registrations.isEmpty()) {
            itemRegistry.createAndRegisterBatch(registrations);
        }

        // ── 2. Collect all ItemStacks for sync and spawn ──
        List<ItemStack> allItems = new ArrayList<>();
        List<SpawnEntry> allSpawns = new ArrayList<>();

        for (PendingLoot loot : allLoot) {
            Vector3d pos = loot.death().position();
            for (GeneratedGear gear : loot.items()) {
                allItems.add(gear.finalItem());

                // Pre-compute random velocity for spawn
                float vx = (float) (ThreadLocalRandom.current().nextDouble() * 2 - 1) * 2f;
                float vy = 3.25f;
                float vz = (float) (ThreadLocalRandom.current().nextDouble() * 2 - 1) * 2f;
                allSpawns.add(new SpawnEntry(gear.finalItem(), pos, vx, vy, vz));
            }
        }

        // ── 3. Batch-sync ALL definitions to nearby players ──
        // Use the first death position for player lookup (all deaths in same realm area)
        if (!allItems.isEmpty()) {
            Vector3d syncPosition = allLoot.getFirst().death().position();
            int synced = worldSyncService.syncItemsToNearbyPlayers(store, syncPosition, allItems);
            LOGGER.atFine().log("[LootPipeline] Batch-synced %d items to nearby players", synced);
        }

        // ── 4. Spawn entities (staggered for large batches) ──
        spawnStaggered(world, store, allSpawns);

        int totalItems = allSpawns.size();
        int totalDeaths = allLoot.size();
        LOGGER.atInfo().log("[LootPipeline] Finalized %d item(s) from %d death(s) (batch registered + synced + spawned)",
                totalItems, totalDeaths);
    }

    /**
     * Spawns items, staggering across ticks if the batch is large.
     */
    private void spawnStaggered(
            @Nonnull World world,
            @Nonnull Store<EntityStore> store,
            @Nonnull List<SpawnEntry> spawns) {

        if (spawns.size() <= SPAWN_BATCH_SIZE) {
            // Small batch — spawn all immediately
            spawnBatch(store, spawns);
            return;
        }

        // Large batch — partition and stagger
        List<List<SpawnEntry>> waves = partition(spawns, SPAWN_BATCH_SIZE);

        // First wave: immediate
        spawnBatch(store, waves.getFirst());

        // Remaining waves: scheduled with stagger delay
        for (int i = 1; i < waves.size(); i++) {
            final List<SpawnEntry> wave = waves.get(i);
            final long delay = SPAWN_STAGGER_MS * i;
            executor.schedule(() -> {
                if (world.isAlive()) {
                    world.execute(() -> spawnBatch(store, wave));
                }
            }, delay, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Spawns a batch of items into the world.
     */
    private void spawnBatch(@Nonnull Store<EntityStore> store, @Nonnull List<SpawnEntry> entries) {
        Vector3f rotation = new Vector3f(0, 0, 0);
        for (SpawnEntry entry : entries) {
            try {
                Holder<EntityStore> holder = ItemComponent.generateItemDrop(
                        store, entry.item(), entry.position(), rotation,
                        entry.vx(), entry.vy(), entry.vz());
                if (holder != null) {
                    store.addEntity(holder, AddReason.SPAWN);
                }
            } catch (Exception e) {
                LOGGER.atWarning().withCause(e).log("[LootPipeline] Failed to spawn item");
            }
        }
    }

    // =========================================================================
    // UTILITY
    // =========================================================================

    /**
     * Partitions a list into sublists of the given size.
     */
    private static <T> List<List<T>> partition(@Nonnull List<T> list, int size) {
        List<List<T>> partitions = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            partitions.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return partitions;
    }

    // =========================================================================
    // LIFECYCLE
    // =========================================================================

    /**
     * Shuts down the pipeline. Pending items are lost (acceptable — same as
     * items lost on server shutdown with the synchronous approach).
     */
    public void shutdown() {
        shuttingDown = true;
        pendingDeaths.clear();
        executor.shutdown();
        try {
            if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        LOGGER.atInfo().log("[LootPipeline] Shut down");
    }
}
