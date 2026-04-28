package io.github.larsonix.trailoforbis.stones;

import io.github.larsonix.trailoforbis.gear.config.GearBalanceConfig;
import io.github.larsonix.trailoforbis.gear.config.ModifierConfig;
import io.github.larsonix.trailoforbis.gear.generation.GearModifierRoller;
import io.github.larsonix.trailoforbis.gear.generation.ImplicitDamageCalculator;
import io.github.larsonix.trailoforbis.gear.generation.ImplicitDefenseCalculator;
import io.github.larsonix.trailoforbis.gear.generation.ModifierPool;
import io.github.larsonix.trailoforbis.gear.model.GearData;
import io.github.larsonix.trailoforbis.gear.model.GearRarity;
import io.github.larsonix.trailoforbis.maps.config.RealmModifierConfig;
import io.github.larsonix.trailoforbis.maps.core.RealmMapData;
import io.github.larsonix.trailoforbis.maps.modifiers.RealmModifierRoller;
import io.github.larsonix.trailoforbis.stones.handler.GearStoneHandler;
import io.github.larsonix.trailoforbis.stones.handler.ItemTypeHandler;
import io.github.larsonix.trailoforbis.stones.handler.RealmMapStoneHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.function.BiFunction;

/**
 * Registry that maps stone types to their action implementations.
 *
 * <p>Uses polymorphic dispatch via {@link ItemTypeHandler} to eliminate
 * instanceof chains. Each item type (gear, realm map) has a registered
 * handler that encapsulates its roller-specific operations.
 *
 * <p>Usage:
 * <pre>{@code
 * StoneActionRegistry registry = new StoneActionRegistry(modifierConfig);
 * StoneAction action = registry.getAction(StoneType.GAIAS_CALIBRATION);
 * StoneActionResult result = action.execute(item, random);
 * }</pre>
 *
 * @see StoneAction
 * @see StoneType
 * @see ItemTypeHandler
 */
public class StoneActionRegistry {

    private final Map<StoneType, StoneAction> actions = new EnumMap<>(StoneType.class);
    private final Map<Class<? extends ModifiableItem>, ItemTypeHandler<?>> handlers = new HashMap<>();
    private final RealmModifierRoller realmModifierRoller;
    @Nullable
    private final GearModifierRoller gearModifierRoller;
    @Nullable
    private final ImplicitDamageCalculator implicitDamageCalculator;
    @Nullable
    private final ImplicitDefenseCalculator implicitDefenseCalculator;

    // ═══════════════════════════════════════════════════════════════════
    // CONSTRUCTOR
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Creates a new registry with default modifier configuration (maps only).
     */
    public StoneActionRegistry() {
        this(new RealmModifierConfig());
    }

    /**
     * Creates a new registry with realm modifier configuration (maps only).
     *
     * @param modifierConfig Configuration for realm modifier generation
     */
    public StoneActionRegistry(@Nonnull RealmModifierConfig modifierConfig) {
        this.realmModifierRoller = new RealmModifierRoller(modifierConfig);
        this.gearModifierRoller = null;
        this.implicitDamageCalculator = null;
        this.implicitDefenseCalculator = null;
        initHandlers();
        registerAllActions();
    }

    /**
     * Creates a new registry with a pre-built realm modifier roller (maps only).
     *
     * @param roller The realm modifier roller to use
     */
    public StoneActionRegistry(@Nonnull RealmModifierRoller roller) {
        this.realmModifierRoller = Objects.requireNonNull(roller, "Realm roller cannot be null");
        this.gearModifierRoller = null;
        this.implicitDamageCalculator = null;
        this.implicitDefenseCalculator = null;
        initHandlers();
        registerAllActions();
    }

    /**
     * Creates a new registry with both realm and gear configuration.
     *
     * @param realmModifierConfig Configuration for realm modifier generation
     * @param gearModifierConfig Configuration for gear modifiers
     * @param gearBalanceConfig Configuration for gear balance
     * @param equipmentStatConfig Configuration for equipment stat restrictions
     */
    public StoneActionRegistry(
            @Nonnull RealmModifierConfig realmModifierConfig,
            @Nonnull ModifierConfig gearModifierConfig,
            @Nonnull GearBalanceConfig gearBalanceConfig,
            @Nonnull io.github.larsonix.trailoforbis.gear.config.EquipmentStatConfig equipmentStatConfig) {
        this.realmModifierRoller = new RealmModifierRoller(realmModifierConfig);
        ModifierPool modifierPool = new ModifierPool(gearModifierConfig, gearBalanceConfig, equipmentStatConfig);
        this.gearModifierRoller = new GearModifierRoller(modifierPool, gearModifierConfig, gearBalanceConfig);
        this.implicitDamageCalculator = new ImplicitDamageCalculator(gearBalanceConfig);
        this.implicitDefenseCalculator = new ImplicitDefenseCalculator(gearBalanceConfig);
        initHandlers();
        registerAllActions();
    }

    /**
     * Creates a new registry with both realm and gear configuration.
     *
     * @param realmModifierConfig Configuration for realm modifier generation
     * @param gearModifierConfig Configuration for gear modifiers
     * @param gearBalanceConfig Configuration for gear balance
     * @deprecated Use constructor with EquipmentStatConfig instead
     */
    @Deprecated
    public StoneActionRegistry(
            @Nonnull RealmModifierConfig realmModifierConfig,
            @Nonnull ModifierConfig gearModifierConfig,
            @Nonnull GearBalanceConfig gearBalanceConfig) {
        this(realmModifierConfig, gearModifierConfig, gearBalanceConfig,
                io.github.larsonix.trailoforbis.gear.config.EquipmentStatConfig.unrestricted());
    }

    /**
     * Creates a new registry with pre-built rollers for both realms and gear.
     *
     * @param realmRoller The realm modifier roller
     * @param gearRoller The gear modifier roller
     */
    public StoneActionRegistry(
            @Nonnull RealmModifierRoller realmRoller,
            @Nonnull GearModifierRoller gearRoller) {
        this.realmModifierRoller = Objects.requireNonNull(realmRoller, "Realm roller cannot be null");
        this.gearModifierRoller = Objects.requireNonNull(gearRoller, "Gear roller cannot be null");
        this.implicitDamageCalculator = null;
        this.implicitDefenseCalculator = null;
        initHandlers();
        registerAllActions();
    }

    /**
     * Creates a new registry with pre-built rollers for realms, gear, and implicit damage.
     *
     * @param realmRoller The realm modifier roller
     * @param gearRoller The gear modifier roller
     * @param implicitCalculator The implicit damage calculator for weapon implicit rerolls
     */
    public StoneActionRegistry(
            @Nonnull RealmModifierRoller realmRoller,
            @Nonnull GearModifierRoller gearRoller,
            @Nonnull ImplicitDamageCalculator implicitCalculator) {
        this.realmModifierRoller = Objects.requireNonNull(realmRoller, "Realm roller cannot be null");
        this.gearModifierRoller = Objects.requireNonNull(gearRoller, "Gear roller cannot be null");
        this.implicitDamageCalculator = Objects.requireNonNull(implicitCalculator, "Implicit calculator cannot be null");
        this.implicitDefenseCalculator = null;
        initHandlers();
        registerAllActions();
    }

    // ═══════════════════════════════════════════════════════════════════
    // HANDLER INFRASTRUCTURE
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Registers item type handlers for polymorphic dispatch.
     */
    private void initHandlers() {
        handlers.put(RealmMapData.class, new RealmMapStoneHandler(realmModifierRoller));
        if (gearModifierRoller != null) {
            handlers.put(GearData.class, new GearStoneHandler(gearModifierRoller, implicitDamageCalculator, implicitDefenseCalculator));
        }
    }

    /**
     * Dispatches a stone operation to the appropriate handler for the item's type.
     *
     * @param item The item to operate on (runtime type determines handler)
     * @param fn The operation to perform on the handler
     * @return The result, or genericFailure if no handler is registered
     */
    @SuppressWarnings("unchecked")
    private <T extends ModifiableItem> StoneActionResult dispatch(
            @Nonnull T item,
            @Nonnull BiFunction<ItemTypeHandler<T>, T, StoneActionResult> fn) {
        var handler = (ItemTypeHandler<T>) handlers.get(item.getClass());
        if (handler == null) {
            return StoneActionResult.genericFailure();
        }
        return fn.apply(handler, item);
    }

    // ═══════════════════════════════════════════════════════════════════
    // ACTION REGISTRATION
    // ═══════════════════════════════════════════════════════════════════

    private void registerAllActions() {
        // Reroll stones
        actions.put(StoneType.GAIAS_CALIBRATION, createGaiasCalibrationAction());
        actions.put(StoneType.EMBER_OF_TUNING, createEmberOfTuningAction());
        actions.put(StoneType.ALTERVERSE_SHARD, createAlterverseShardAction());
        actions.put(StoneType.ORBISIAN_BLESSING, createOrbisianBlessingAction());
        actions.put(StoneType.ETHEREAL_CALIBRATION, createEtherealCalibrationAction());

        // Enhancement stones
        actions.put(StoneType.GAIAS_GIFT, createGaiasGiftAction());
        actions.put(StoneType.SPARK_OF_POTENTIAL, createSparkOfPotentialAction());
        actions.put(StoneType.CORE_OF_ASCENSION, createCoreOfAscensionAction());
        actions.put(StoneType.HEART_OF_LEGENDS, createHeartOfLegendsAction());
        actions.put(StoneType.CROWN_OF_TRANSCENDENCE, createCrownOfTranscendenceAction());
        actions.put(StoneType.CARTOGRAPHERS_POLISH, createCartographersPolishAction());

        // Removal stones
        actions.put(StoneType.PURGING_EMBER, createPurgingEmberAction());
        actions.put(StoneType.EROSION_SHARD, createErosionShardAction());
        actions.put(StoneType.TRANSMUTATION_CRYSTAL, createTransmutationCrystalAction());

        // Lock stones
        actions.put(StoneType.WARDENS_SEAL, createWardensSealAction());
        actions.put(StoneType.WARDENS_KEY, createWardensKeyAction());

        // Level stones
        actions.put(StoneType.FORTUNES_COMPASS, createFortunesCompassAction());
        actions.put(StoneType.ALTERVERSE_KEY, createAlterverseKeyAction());
        actions.put(StoneType.THRESHOLD_STONE, createThresholdStoneAction());

        // Special stones
        actions.put(StoneType.VARYNS_TOUCH, createVarynsTouchAction());
        actions.put(StoneType.GAIAS_PERFECTION, createGaiasPerfectionAction());
        actions.put(StoneType.LOREKEEPERS_SCROLL, createLorekeepersScrollAction());
        actions.put(StoneType.GENESIS_STONE, createGenesisStoneAction());
    }

    // ═══════════════════════════════════════════════════════════════════
    // ACCESS METHODS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Gets the action for a stone type.
     *
     * @param type The stone type
     * @return The action implementation
     */
    @Nonnull
    public StoneAction getAction(@Nonnull StoneType type) {
        StoneAction action = actions.get(type);
        if (action == null) {
            throw new IllegalStateException("No action registered for: " + type);
        }
        return action;
    }

    /**
     * Executes a stone action on an item.
     *
     * <p>Convenience method that handles validation and execution.
     *
     * @param random random source
     */
    @Nonnull
    public StoneActionResult execute(
            @Nonnull StoneType type,
            @Nonnull ModifiableItem item,
            @Nonnull Random random) {

        // Check if stone can be used on this item type
        if (!type.canUseOn(item)) {
            if (item.corrupted() && !type.worksOnCorrupted()) {
                return StoneActionResult.corruptedItem();
            }
            return StoneActionResult.invalidTarget(type.getTargetType());
        }

        return getAction(type).execute(item, random);
    }

    /**
     * Gets the realm modifier roller used by this registry.
     */
    @Nonnull
    public RealmModifierRoller getRealmModifierRoller() {
        return realmModifierRoller;
    }

    /**
     * @return null if gear not configured
     */
    @Nullable
    public GearModifierRoller getGearModifierRoller() {
        return gearModifierRoller;
    }

    /**
     * Gets the implicit damage calculator used by this registry.
     *
     * @return The implicit damage calculator, or null if not configured
     */
    @Nullable
    public ImplicitDamageCalculator getImplicitDamageCalculator() {
        return implicitDamageCalculator;
    }

    /**
     * @deprecated Use {@link #getRealmModifierRoller()} instead
     */
    @Deprecated
    @Nonnull
    public RealmModifierRoller getModifierRoller() {
        return realmModifierRoller;
    }

    // ═══════════════════════════════════════════════════════════════════
    // ACTION IMPLEMENTATIONS - REROLL STONES
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Gaia's Calibration - Rerolls modifier values.
     */
    private StoneAction createGaiasCalibrationAction() {
        return new StoneAction() {
            @Override
            public boolean canApply(@Nonnull ModifiableItem item) {
                return !item.modifiers().isEmpty() && item.hasUnlockedModifiers();
            }

            @Override
            @Nonnull
            public StoneActionResult execute(@Nonnull ModifiableItem item, @Nonnull Random random) {
                if (item.modifiers().isEmpty()) return StoneActionResult.noModifiers();
                if (!item.hasUnlockedModifiers()) return StoneActionResult.noUnlockedModifiers();
                return dispatch(item, (h, i) -> h.rerollValues(i, random));
            }
        };
    }

    /**
     * Ember of Tuning - Rerolls ONE random modifier's value.
     */
    private StoneAction createEmberOfTuningAction() {
        return new StoneAction() {
            @Override
            public boolean canApply(@Nonnull ModifiableItem item) {
                return !item.modifiers().isEmpty() && item.hasUnlockedModifiers();
            }

            @Override
            @Nonnull
            public StoneActionResult execute(@Nonnull ModifiableItem item, @Nonnull Random random) {
                if (item.modifiers().isEmpty()) return StoneActionResult.noModifiers();
                if (!item.hasUnlockedModifiers()) return StoneActionResult.noUnlockedModifiers();
                return dispatch(item, (h, i) -> h.rerollOneValue(i, random));
            }
        };
    }

    /**
     * Alterverse Shard - Rerolls all unlocked modifiers.
     */
    private StoneAction createAlterverseShardAction() {
        return new StoneAction() {
            @Override
            public boolean canApply(@Nonnull ModifiableItem item) {
                return !item.modifiers().isEmpty() && item.hasUnlockedModifiers();
            }

            @Override
            @Nonnull
            public StoneActionResult execute(@Nonnull ModifiableItem item, @Nonnull Random random) {
                if (item.modifiers().isEmpty()) return StoneActionResult.noModifiers();
                if (!item.hasUnlockedModifiers()) return StoneActionResult.noUnlockedModifiers();
                return dispatch(item, (h, i) -> h.rerollTypes(i, random));
            }
        };
    }

    /**
     * Orbisian Blessing - Rerolls quality.
     *
     * <p>Builder-sufficient: uses {@code ModifiableItem.withQuality()} directly.
     */
    private StoneAction createOrbisianBlessingAction() {
        return new StoneAction() {
            @Override
            public boolean canApply(@Nonnull ModifiableItem item) {
                return item.quality() < 101;
            }

            @Override
            @Nonnull
            public StoneActionResult execute(@Nonnull ModifiableItem item, @Nonnull Random random) {
                if (item.quality() >= 101) return StoneActionResult.maxQuality();
                int newQuality = 1 + random.nextInt(100);
                return StoneActionResult.success(item.withQuality(newQuality),
                    "Quality rerolled to " + newQuality + "%.");
            }
        };
    }

    /**
     * Ethereal Calibration - Rerolls weapon implicit damage value.
     *
     * <p>Only works on items that have an implicit damage stat.
     */
    private StoneAction createEtherealCalibrationAction() {
        return new StoneAction() {
            @Override
            public boolean canApply(@Nonnull ModifiableItem item) {
                return item.hasImplicit();
            }

            @Override
            @Nonnull
            public StoneActionResult execute(@Nonnull ModifiableItem item, @Nonnull Random random) {
                if (!item.hasImplicit()) {
                    return StoneActionResult.failure("This item has no implicit damage to reroll.");
                }
                return dispatch(item, (h, i) -> h.rerollImplicit(i, random));
            }
        };
    }

    // ═══════════════════════════════════════════════════════════════════
    // ACTION IMPLEMENTATIONS - ENHANCEMENT STONES
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Gaia's Gift - Adds a random modifier.
     */
    private StoneAction createGaiasGiftAction() {
        return new StoneAction() {
            @Override
            public boolean canApply(@Nonnull ModifiableItem item) {
                return item.canAddModifier();
            }

            @Override
            @Nonnull
            public StoneActionResult execute(@Nonnull ModifiableItem item, @Nonnull Random random) {
                if (!item.canAddModifier()) return StoneActionResult.maxModifiers();
                return dispatch(item, (h, i) -> h.addModifier(i, random));
            }
        };
    }

    /**
     * Spark of Potential - Upgrades Common to Uncommon + adds 1 modifier.
     */
    private StoneAction createSparkOfPotentialAction() {
        return createTierUpgradeAction(GearRarity.COMMON, GearRarity.UNCOMMON);
    }

    /**
     * Core of Ascension - Upgrades Uncommon to Rare + adds 1 modifier.
     */
    private StoneAction createCoreOfAscensionAction() {
        return createTierUpgradeAction(GearRarity.UNCOMMON, GearRarity.RARE);
    }

    /**
     * Heart of Legends - Upgrades Rare to Epic + adds 1 modifier.
     */
    private StoneAction createHeartOfLegendsAction() {
        return createTierUpgradeAction(GearRarity.RARE, GearRarity.EPIC);
    }

    /**
     * Crown of Transcendence - Upgrades Epic to Legendary + adds 1 modifier.
     */
    private StoneAction createCrownOfTranscendenceAction() {
        return createTierUpgradeAction(GearRarity.EPIC, GearRarity.LEGENDARY);
    }

    /**
     * Creates a tier upgrade action for the given rarity transition.
     *
     * <p>Uses {@code ModifiableItem.withRarity()} for the upgrade, then
     * dispatches {@code addModifier()} through the handler for the new modifier.
     *
     * @param fromRarity The required source rarity
     * @param toRarity The target rarity after upgrade
     * @return The upgrade action
     */
    private StoneAction createTierUpgradeAction(GearRarity fromRarity, GearRarity toRarity) {
        return new StoneAction() {
            @Override
            public boolean canApply(@Nonnull ModifiableItem item) {
                return item.rarity() == fromRarity &&
                       item.modifiers().size() < toRarity.getMaxModifiers();
            }

            @Override
            @Nonnull
            public StoneActionResult execute(@Nonnull ModifiableItem item, @Nonnull Random random) {
                if (item.rarity() != fromRarity) {
                    return StoneActionResult.failure(
                        "Item must be " + fromRarity.getHytaleQualityId() + " rarity.");
                }
                if (item.modifiers().size() >= toRarity.getMaxModifiers()) {
                    return StoneActionResult.failure(
                        "Item already has maximum modifiers for " + toRarity.getHytaleQualityId() + " rarity.");
                }

                // Upgrade rarity first, then add a modifier via handler dispatch
                ModifiableItem upgraded = item.withRarity(toRarity);
                StoneActionResult addResult = dispatch(upgraded, (h, i) -> h.addModifier(i, random));
                if (addResult.success()) {
                    return StoneActionResult.success(addResult.modifiedItem(),
                        "Upgraded to " + toRarity.getHytaleQualityId() + " with a new modifier !");
                }
                return addResult;
            }
        };
    }

    /**
     * Cartographer's Polish - Improves map quality.
     *
     * <p>Builder-sufficient: uses interface methods ({@code isIdentified()},
     * {@code withQuality()}) without dispatch.
     */
    private StoneAction createCartographersPolishAction() {
        return new StoneAction() {
            @Override
            public boolean canApply(@Nonnull ModifiableItem item) {
                return item.itemTargetType() == ItemTargetType.MAP_ONLY && item.quality() < 100;
            }

            @Override
            @Nonnull
            public StoneActionResult execute(@Nonnull ModifiableItem item, @Nonnull Random random) {
                if (item.quality() >= 100) return StoneActionResult.maxQuality();
                if (item.itemTargetType() != ItemTargetType.MAP_ONLY) {
                    return StoneActionResult.invalidTarget(ItemTargetType.MAP_ONLY);
                }

                // Unidentified maps get +5%, identified get +1-5%
                int increase = item.isIdentified() ? 1 + random.nextInt(5) : 5;
                int newQuality = Math.min(100, item.quality() + increase);
                return StoneActionResult.success(item.withQuality(newQuality),
                    "Quality improved to " + newQuality + "%.");
            }
        };
    }

    // ═══════════════════════════════════════════════════════════════════
    // ACTION IMPLEMENTATIONS - REMOVAL STONES
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Purging Ember - Removes all unlocked modifiers.
     */
    private StoneAction createPurgingEmberAction() {
        return new StoneAction() {
            @Override
            public boolean canApply(@Nonnull ModifiableItem item) {
                return item.hasUnlockedModifiers();
            }

            @Override
            @Nonnull
            public StoneActionResult execute(@Nonnull ModifiableItem item, @Nonnull Random random) {
                if (!item.hasUnlockedModifiers()) return StoneActionResult.noUnlockedModifiers();
                return dispatch(item, (h, i) -> h.clearUnlockedModifiers(i));
            }
        };
    }

    /**
     * Erosion Shard - Removes one random unlocked modifier.
     */
    private StoneAction createErosionShardAction() {
        return new StoneAction() {
            @Override
            public boolean canApply(@Nonnull ModifiableItem item) {
                return item.hasUnlockedModifiers();
            }

            @Override
            @Nonnull
            public StoneActionResult execute(@Nonnull ModifiableItem item, @Nonnull Random random) {
                if (!item.hasUnlockedModifiers()) return StoneActionResult.noUnlockedModifiers();
                return dispatch(item, (h, i) -> h.removeModifier(i, random));
            }
        };
    }

    /**
     * Transmutation Crystal - Removes one modifier and adds a new one (atomic swap).
     */
    private StoneAction createTransmutationCrystalAction() {
        return new StoneAction() {
            @Override
            public boolean canApply(@Nonnull ModifiableItem item) {
                return item.hasUnlockedModifiers();
            }

            @Override
            @Nonnull
            public StoneActionResult execute(@Nonnull ModifiableItem item, @Nonnull Random random) {
                if (!item.hasUnlockedModifiers()) return StoneActionResult.noUnlockedModifiers();
                return dispatch(item, (h, i) -> h.transmute(i, random));
            }
        };
    }

    // ═══════════════════════════════════════════════════════════════════
    // ACTION IMPLEMENTATIONS - LOCK STONES
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Warden's Seal - Locks a random unlocked modifier.
     */
    private StoneAction createWardensSealAction() {
        return new StoneAction() {
            @Override
            public boolean canApply(@Nonnull ModifiableItem item) {
                return item.hasUnlockedModifiers();
            }

            @Override
            @Nonnull
            public StoneActionResult execute(@Nonnull ModifiableItem item, @Nonnull Random random) {
                var modifiers = item.modifiers();
                List<Integer> unlocked = new ArrayList<>();
                for (int i = 0; i < modifiers.size(); i++) {
                    if (!modifiers.get(i).isLocked()) {
                        unlocked.add(i);
                    }
                }

                if (unlocked.isEmpty()) {
                    return StoneActionResult.failure("No unlocked modifiers to lock.");
                }

                int randomIndex = unlocked.get(random.nextInt(unlocked.size()));
                return dispatch(item, (h, i) -> h.lockModifier(i, randomIndex));
            }
        };
    }

    /**
     * Warden's Key - Unlocks a random locked modifier.
     */
    private StoneAction createWardensKeyAction() {
        return new StoneAction() {
            @Override
            public boolean canApply(@Nonnull ModifiableItem item) {
                return item.lockedModifierCount() > 0;
            }

            @Override
            @Nonnull
            public StoneActionResult execute(@Nonnull ModifiableItem item, @Nonnull Random random) {
                var modifiers = item.modifiers();
                List<Integer> locked = new ArrayList<>();
                for (int i = 0; i < modifiers.size(); i++) {
                    if (modifiers.get(i).isLocked()) {
                        locked.add(i);
                    }
                }

                if (locked.isEmpty()) {
                    return StoneActionResult.failure("No locked modifiers to unlock.");
                }

                int randomIndex = locked.get(random.nextInt(locked.size()));
                return dispatch(item, (h, i) -> h.unlockModifier(i, randomIndex));
            }
        };
    }

    // ═══════════════════════════════════════════════════════════════════
    // ACTION IMPLEMENTATIONS - LEVEL STONES
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Fortune's Compass - Adds +5% Item Quantity to maps as a dedicated bonus.
     *
     * <p>This bonus does not consume map modifier slots and is tracked separately
     * from normal prefix/suffix modifiers. Maximum bonus is +20%.
     *
     * <p>Uses {@code instanceof RealmMapData} because {@code fortunesCompassBonus()}
     * is a map-specific property not on the {@code ModifiableItem} interface.
     */
    private StoneAction createFortunesCompassAction() {
        return new StoneAction() {
            @Override
            public boolean canApply(@Nonnull ModifiableItem item) {
                if (!(item instanceof RealmMapData mapData)) {
                    return false;
                }
                return mapData.fortunesCompassBonus() < 20;
            }

            @Override
            @Nonnull
            public StoneActionResult execute(@Nonnull ModifiableItem item, @Nonnull Random random) {
                if (!(item instanceof RealmMapData mapData)) {
                    return StoneActionResult.invalidTarget(ItemTargetType.MAP_ONLY);
                }

                int currentBonus = mapData.fortunesCompassBonus();
                if (currentBonus >= 20) {
                    return StoneActionResult.failure("Map is at maximum Fortune's Compass bonus (20%).");
                }

                int newBonus = Math.min(currentBonus + 5, 20);
                int added = newBonus - currentBonus;
                RealmMapData result = mapData.withFortunesCompassBonus(newBonus);
                return StoneActionResult.success(result,
                    "Added +" + added + "% Fortune's Compass bonus (total: +" + newBonus + "%).");
            }
        };
    }

    /**
     * Alterverse Key - Changes map biome randomly.
     */
    private StoneAction createAlterverseKeyAction() {
        return new StoneAction() {
            @Override
            public boolean canApply(@Nonnull ModifiableItem item) {
                return item.itemTargetType() == ItemTargetType.MAP_ONLY;
            }

            @Override
            @Nonnull
            public StoneActionResult execute(@Nonnull ModifiableItem item, @Nonnull Random random) {
                return dispatch(item, (h, i) -> h.changeBiome(i, random));
            }
        };
    }

    /**
     * Threshold Stone - Rerolls level within ±3 and recalculates implicits.
     *
     * <p>Dispatches through handler so gear implicits are recalculated for
     * the new level. Maps just get a level number change (no implicits).
     */
    private StoneAction createThresholdStoneAction() {
        return new StoneAction() {
            @Override
            @Nonnull
            public StoneActionResult execute(@Nonnull ModifiableItem item, @Nonnull Random random) {
                int currentLevel = item.level();
                int minLevel = Math.max(1, currentLevel - 3);
                int maxLevel = currentLevel + 3;

                int newLevel;
                if (minLevel == maxLevel) {
                    newLevel = minLevel;
                } else {
                    int range = maxLevel - minLevel + 1;
                    if (range <= 1) {
                        newLevel = minLevel;
                    } else {
                        do {
                            newLevel = minLevel + random.nextInt(range);
                        } while (newLevel == currentLevel && range > 1);
                    }
                }

                // Dispatch to handler — gear handler recalculates implicits,
                // map handler just changes the level number
                final int level = newLevel;
                return dispatch(item, (h, i) -> h.changeLevel(i, level, random));
            }
        };
    }

    // ═══════════════════════════════════════════════════════════════════
    // ACTION IMPLEMENTATIONS - SPECIAL STONES
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Varyn's Touch - Corrupts with random effects.
     */
    private StoneAction createVarynsTouchAction() {
        return new StoneAction() {
            @Override
            public boolean canApply(@Nonnull ModifiableItem item) {
                return !item.corrupted();
            }

            @Override
            @Nonnull
            public StoneActionResult execute(@Nonnull ModifiableItem item, @Nonnull Random random) {
                if (item.corrupted()) {
                    return StoneActionResult.failure("Item is already corrupted.");
                }
                return dispatch(item, (h, i) -> h.corrupt(i, random));
            }
        };
    }

    /**
     * Gaia's Perfection - Sets perfect quality.
     *
     * <p>Builder-sufficient: uses {@code ModifiableItem.withQuality()} directly.
     */
    private StoneAction createGaiasPerfectionAction() {
        return new StoneAction() {
            @Override
            public boolean canApply(@Nonnull ModifiableItem item) {
                return item.quality() < 101;
            }

            @Override
            @Nonnull
            public StoneActionResult execute(@Nonnull ModifiableItem item, @Nonnull Random random) {
                if (item.quality() >= 101) return StoneActionResult.maxQuality();
                return StoneActionResult.success(item.withQuality(101), "Quality set to perfect !");
            }
        };
    }

    /**
     * Lorekeeper's Scroll - Identifies an item.
     *
     * <p>Gear is always identified ({@code isIdentified()} defaults to {@code true}),
     * so {@code canApply} filters gear out automatically.
     */
    private StoneAction createLorekeepersScrollAction() {
        return new StoneAction() {
            @Override
            public boolean canApply(@Nonnull ModifiableItem item) {
                return !item.isIdentified();
            }

            @Override
            @Nonnull
            public StoneActionResult execute(@Nonnull ModifiableItem item, @Nonnull Random random) {
                return dispatch(item, (h, i) -> h.identify(i));
            }
        };
    }

    /**
     * Genesis Stone - Fills all remaining modifier slots.
     */
    private StoneAction createGenesisStoneAction() {
        return new StoneAction() {
            @Override
            public boolean canApply(@Nonnull ModifiableItem item) {
                return item.canAddModifier();
            }

            @Override
            @Nonnull
            public StoneActionResult execute(@Nonnull ModifiableItem item, @Nonnull Random random) {
                if (!item.canAddModifier()) return StoneActionResult.maxModifiers();
                return dispatch(item, (h, i) -> h.fillModifiers(i, random));
            }
        };
    }
}
