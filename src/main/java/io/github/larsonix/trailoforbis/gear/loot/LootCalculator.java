package io.github.larsonix.trailoforbis.gear.loot;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;

import io.github.larsonix.trailoforbis.gear.loot.LootSettings.MobType;

import java.util.Objects;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Calculates loot drop chances and rarity bonuses.
 *
 * <p>Factors considered:
 * <ul>
 *   <li>Base drop chance from config</li>
 *   <li>Mob type (normal/elite/boss)</li>
 *   <li>Player WIND attribute (Ghost archetype = fortune/loot bonus)</li>
 *   <li>Distance from world spawn</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 * <p>This class is thread-safe when constructed with the default constructor
 * (uses ThreadLocalRandom). For seeded testing, provide a synchronized Random
 * or accept that tests run single-threaded.
 */
public final class LootCalculator {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final LootSettings settings;
    private final RarityBonusCalculator rarityBonusCalculator;
    private final DropLevelBlender dropLevelBlender;
    private final Random random;

    // World spawn position (configurable if needed)
    private volatile Vector3d worldSpawn = new Vector3d(0, 64, 0);

    /**
     * Creates a LootCalculator with ThreadLocalRandom for thread safety.
     *
     * @param settings The loot settings
     * @param rarityBonusCalculator For calculating player WIND rarity bonus
     * @param dropLevelBlender The drop level blender
     */
    public LootCalculator(LootSettings settings, RarityBonusCalculator rarityBonusCalculator, DropLevelBlender dropLevelBlender) {
        this(settings, rarityBonusCalculator, dropLevelBlender, ThreadLocalRandom.current());
    }

    /**
     * Creates a LootCalculator with custom random (for testing).
     *
     * @param settings The loot settings
     * @param rarityBonusCalculator For calculating player WIND rarity bonus
     * @param dropLevelBlender The drop level blender
     * @param random The random number generator
     */
    public LootCalculator(LootSettings settings, RarityBonusCalculator rarityBonusCalculator, DropLevelBlender dropLevelBlender, Random random) {
        this.settings = Objects.requireNonNull(settings, "settings cannot be null");
        this.rarityBonusCalculator = Objects.requireNonNull(rarityBonusCalculator, "rarityBonusCalculator cannot be null");
        this.dropLevelBlender = Objects.requireNonNull(dropLevelBlender, "dropLevelBlender cannot be null");
        this.random = Objects.requireNonNull(random, "random cannot be null");
    }

    /**
     * Sets the world spawn position for distance calculations.
     *
     * @param spawn The world spawn position
     */
    public void setWorldSpawn(Vector3d spawn) {
        this.worldSpawn = Objects.requireNonNull(spawn, "spawn cannot be null");
    }

    /**
     * Gets the world spawn position.
     *
     * @return The current world spawn position
     */
    public Vector3d getWorldSpawn() {
        return worldSpawn;
    }

    // =========================================================================
    // DROP CHANCE
    // =========================================================================

    /**
     * Calculates if gear should drop from a mob death.
     *
     * @param mobType The type of mob killed
     * @return true if gear should drop
     */
    public boolean shouldDropGear(MobType mobType) {
        double baseChance = settings.getBaseDropChance();
        double quantityMultiplier = settings.getQuantityMultiplier(mobType);

        // Final drop chance = base * quantity multiplier
        double finalChance = baseChance * quantityMultiplier;

        // Roll
        double roll = random.nextDouble();
        boolean drops = roll < finalChance;

        LOGGER.atFine().log("Drop roll: %.3f vs %.3f (base=%.2f, mult=%.2f) -> %s",
                roll, finalChance, baseChance, quantityMultiplier, drops ? "DROP" : "NO DROP");

        return drops;
    }

    /**
     * Calculates how many gear pieces should drop.
     * For bosses, this might be > 1.
     *
     * @param mobType The type of mob killed
     * @return Number of gear pieces to drop (at least 1 if called after shouldDropGear)
     */
    public int calculateDropCount(MobType mobType) {
        double quantityMultiplier = settings.getQuantityMultiplier(mobType);

        // Base 1 drop
        int baseDrops = 1;

        // Extra drops based on quantity bonus
        // Each 100% bonus = guaranteed extra drop
        // Fractional bonus = chance for extra drop
        double extraDrops = quantityMultiplier - 1.0;

        int guaranteed = (int) extraDrops;
        double fractional = extraDrops - guaranteed;

        int total = baseDrops + guaranteed;
        if (random.nextDouble() < fractional) {
            total++;
        }

        LOGGER.atFine().log("Drop count: base=%d, mult=%.2f -> %d drops",
                baseDrops, quantityMultiplier, total);

        return total;
    }

    // =========================================================================
    // RARITY BONUS
    // =========================================================================

    /**
     * Calculates the total rarity bonus for a drop.
     *
     * @param playerId The player who killed the mob
     * @param mobType The type of mob killed
     * @param deathPosition Where the mob died
     * @return Total rarity bonus (percentage, used by GearGenerator)
     */
    public double calculateRarityBonus(UUID playerId, MobType mobType, Vector3d deathPosition) {
        double totalBonus = 0;

        // 1. LUCK bonus
        double luckBonus = calculateLuckBonus(playerId);
        totalBonus += luckBonus;

        // 2. Distance bonus
        double distanceBonus = calculateDistanceBonus(deathPosition);
        totalBonus += distanceBonus;

        // 3. Mob type bonus
        double mobBonus = settings.getRarityBonus(mobType);
        totalBonus += mobBonus;

        LOGGER.atFine().log("Rarity bonus: LUCK=%.1f%%, Distance=%.1f%%, Mob=%.1f%% -> Total=%.1f%%",
                luckBonus, distanceBonus, mobBonus, totalBonus);

        return totalBonus;
    }

    /**
     * Calculates rarity bonus from player's WIND attribute.
     * Delegates to shared {@link RarityBonusCalculator}.
     */
    private double calculateLuckBonus(UUID playerId) {
        return rarityBonusCalculator.calculatePlayerBonus(playerId);
    }

    /**
     * Calculates rarity bonus from distance to world spawn.
     */
    private double calculateDistanceBonus(Vector3d position) {
        if (!settings.isDistanceScalingEnabled()) {
            return 0;
        }

        Vector3d spawn = worldSpawn;

        // Calculate horizontal distance (ignore Y)
        double dx = position.x - spawn.x;
        double dz = position.z - spawn.z;
        double distance = Math.sqrt(dx * dx + dz * dz);

        // Convert to rarity percent
        double bonus = distance / settings.getBlocksPerPercent();

        // Cap at maximum
        return Math.min(bonus, settings.getMaxDistanceBonus());
    }

    // =========================================================================
    // ITEM LEVEL CALCULATION
    // =========================================================================

    /**
     * Calculates the item level for dropped gear.
     *
     * <p>When level blending is enabled, pulls the drop level toward the
     * player's level (capped at ±maxOffset from source). Otherwise falls
     * back to source level ± variance.
     *
     * @param mobLevel The level of the mob killed
     * @param playerLevel The level of the player who killed the mob
     * @return The item level for the dropped gear (at least 1)
     */
    public int calculateItemLevel(int mobLevel, int playerLevel) {
        return dropLevelBlender.calculate(mobLevel, playerLevel, random);
    }

    // =========================================================================
    // LOOT ROLL RESULT
    // =========================================================================

    /**
     * Result of loot calculation.
     *
     * @param shouldDrop Whether gear should drop
     * @param dropCount Number of items to drop
     * @param rarityBonus Rarity bonus percentage
     * @param itemLevel The item level for drops
     */
    public record LootRoll(
            boolean shouldDrop,
            int dropCount,
            double rarityBonus,
            int itemLevel
    ) {
        public static final LootRoll NO_DROP = new LootRoll(false, 0, 0, 0);
    }

    /**
     * Performs a complete loot calculation.
     *
     * @param playerId The player who killed the mob
     * @param mobType The type of mob
     * @param mobLevel The level of the mob
     * @param playerLevel The level of the player
     * @param deathPosition Where the mob died
     * @return Complete loot roll result
     */
    public LootRoll calculateLoot(UUID playerId, MobType mobType, int mobLevel, int playerLevel, Vector3d deathPosition) {
        return calculateLoot(playerId, mobType, mobLevel, playerLevel, deathPosition, RealmLootContext.NONE);
    }

    /**
     * Performs a complete loot calculation with realm context.
     *
     * <p>This overload applies realm modifier bonuses:
     * <ul>
     *   <li>IIQ (Item Quantity) increases drop chance</li>
     *   <li>IIR (Item Rarity) increases rarity bonus</li>
     * </ul>
     *
     * @param playerId The player who killed the mob
     * @param mobType The type of mob
     * @param mobLevel The level of the mob
     * @param playerLevel The level of the player
     * @param deathPosition Where the mob died
     * @param realmContext Realm loot bonuses (IIQ/IIR)
     * @return Complete loot roll result
     */
    public LootRoll calculateLoot(
            UUID playerId,
            MobType mobType,
            int mobLevel,
            int playerLevel,
            Vector3d deathPosition,
            RealmLootContext realmContext) {

        // Apply IIQ bonus to drop chance
        if (!shouldDropGearWithBonus(mobType, realmContext.itemQuantityBonus())) {
            return LootRoll.NO_DROP;
        }

        int dropCount = calculateDropCountWithBonus(mobType, realmContext.itemQuantityBonus());
        double rarityBonus = calculateRarityBonus(playerId, mobType, deathPosition);

        // Add realm IIR bonus
        rarityBonus += realmContext.itemRarityBonus();

        int itemLevel = calculateItemLevel(mobLevel, playerLevel);

        if (realmContext.hasBonus()) {
            LOGGER.atFine().log("Loot roll with realm bonus: IIQ=%.1f%%, IIR=%.1f%%, drops=%d, rarity=%.1f%%",
                realmContext.itemQuantityBonus(), realmContext.itemRarityBonus(), dropCount, rarityBonus);
        }

        return new LootRoll(true, dropCount, rarityBonus, itemLevel);
    }

    /**
     * Calculates if gear should drop with IIQ bonus applied.
     *
     * @param mobType The type of mob killed
     * @param iiqBonus Item quantity bonus percentage
     * @return true if gear should drop
     */
    private boolean shouldDropGearWithBonus(MobType mobType, double iiqBonus) {
        double baseChance = settings.getBaseDropChance();
        double quantityMultiplier = settings.getQuantityMultiplier(mobType);

        // Apply IIQ bonus as a multiplier
        double iiqMultiplier = 1.0 + (iiqBonus / 100.0);
        double finalChance = baseChance * quantityMultiplier * iiqMultiplier;

        double roll = random.nextDouble();
        boolean drops = roll < finalChance;

        if (iiqBonus > 0) {
            LOGGER.atFine().log("Drop roll with IIQ: %.3f vs %.3f (base=%.2f, mobMult=%.2f, iiqMult=%.2f) -> %s",
                roll, finalChance, baseChance, quantityMultiplier, iiqMultiplier, drops ? "DROP" : "NO DROP");
        }

        return drops;
    }

    /**
     * Calculates drop count with IIQ bonus applied.
     *
     * @param mobType The type of mob killed
     * @param iiqBonus Item quantity bonus percentage
     * @return Number of gear pieces to drop
     */
    private int calculateDropCountWithBonus(MobType mobType, double iiqBonus) {
        double quantityMultiplier = settings.getQuantityMultiplier(mobType);

        // Apply IIQ bonus to quantity multiplier
        double iiqMultiplier = 1.0 + (iiqBonus / 100.0);
        double totalMultiplier = quantityMultiplier * iiqMultiplier;

        // Base 1 drop + bonus from multiplier
        int baseDrops = 1;
        double extraDrops = totalMultiplier - 1.0;

        int guaranteed = (int) extraDrops;
        double fractional = extraDrops - guaranteed;

        int total = baseDrops + guaranteed;
        if (random.nextDouble() < fractional) {
            total++;
        }

        return total;
    }

    // =========================================================================
    // ACCESSORS (for testing)
    // =========================================================================

    /**
     * Gets the loot settings.
     *
     * @return The loot settings
     */
    public LootSettings getSettings() {
        return settings;
    }
}
