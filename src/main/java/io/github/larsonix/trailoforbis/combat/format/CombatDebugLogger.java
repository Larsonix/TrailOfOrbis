package io.github.larsonix.trailoforbis.combat.format;

import com.hypixel.hytale.logger.HytaleLogger;
import java.util.logging.Level;

import io.github.larsonix.trailoforbis.attributes.ComputedStats;
import io.github.larsonix.trailoforbis.combat.DamageBreakdown;
import io.github.larsonix.trailoforbis.elemental.ElementType;
import io.github.larsonix.trailoforbis.elemental.ElementalStats;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.EnumMap;
import java.util.Map;

/**
 * Static debug logging helpers for the damage pipeline.
 *
 * <p>All methods log at FINE level (invisible in production). Also provides utility
 * methods for extracting elemental stat maps used by combat log displays.
 *
 * <p>Extracted from RPGDamageSystem to reduce orchestrator size. Zero pipeline side effects.
 */
public final class CombatDebugLogger {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private CombatDebugLogger() {} // static utility

    public static void logAttackerStats(
        @Nullable ComputedStats stats,
        @Nullable ElementalStats elemental,
        boolean hasRpgWeapon,
        float rpgWeaponDamage
    ) {
        LOGGER.at(Level.FINE).log("[DmgPipeline] ──── ATTACKER STATS ────");
        if (stats == null) {
            LOGGER.at(Level.FINE).log("[DmgPipeline] (no stats - environment/mob damage)");
            return;
        }

        String weaponId = stats.getWeaponItemId() != null ? stats.getWeaponItemId() : "(none)";
        LOGGER.at(Level.FINE).log("[DmgPipeline] Weapon: %s | RPG: %s | BaseDmg: %.1f",
            weaponId, hasRpgWeapon, rpgWeaponDamage);
        LOGGER.at(Level.FINE).log("[DmgPipeline] Physical: flat=%.1f, %%=%.1f",
            stats.getPhysicalDamage(), stats.getPhysicalDamagePercent());
        LOGGER.at(Level.FINE).log("[DmgPipeline] Melee: flat=%.1f, %%=%.1f",
            stats.getMeleeDamage(), stats.getMeleeDamagePercent());
        LOGGER.at(Level.FINE).log("[DmgPipeline] Crit: %.1f%% / x%.2f",
            stats.getCriticalChance(), stats.getCriticalMultiplier() / 100f);
        LOGGER.at(Level.FINE).log("[DmgPipeline] Armor Pen: %.1f%% | True Dmg: %.1f",
            stats.getArmorPenetration(), stats.getTrueDamage());
        LOGGER.at(Level.FINE).log("[DmgPipeline] All Dmg%%: %.1f | Multiplier: %.1f",
            stats.getAllDamagePercent(), stats.getDamageMultiplier());
        LOGGER.at(Level.FINE).log("[DmgPipeline] Life Steal: %.1f%% | Mana Leech: %.1f%%",
            stats.getLifeSteal(), stats.getManaLeech());

        // Elemental stats from attacker's elemental
        if (elemental != null) {
            LOGGER.at(Level.FINE).log("[DmgPipeline] Elemental - Fire: %.1f+%.1f%% | Cold: %.1f+%.1f%% | Lightning: %.1f+%.1f%% | Chaos: %.1f+%.1f%%",
                elemental.getFlatDamage(ElementType.FIRE), elemental.getPercentDamage(ElementType.FIRE),
                elemental.getFlatDamage(ElementType.WATER), elemental.getPercentDamage(ElementType.WATER),
                elemental.getFlatDamage(ElementType.LIGHTNING), elemental.getPercentDamage(ElementType.LIGHTNING),
                elemental.getFlatDamage(ElementType.VOID), elemental.getPercentDamage(ElementType.VOID));
        }

        // Conversions
        float fireConv = stats.getFireConversion();
        float coldConv = stats.getWaterConversion();
        float lightConv = stats.getLightningConversion();
        float chaosConv = stats.getVoidConversion();
        if (fireConv > 0 || coldConv > 0 || lightConv > 0 || chaosConv > 0) {
            LOGGER.at(Level.FINE).log("[DmgPipeline] Conversions - Fire: %.1f%% | Cold: %.1f%% | Lightning: %.1f%% | Chaos: %.1f%%",
                fireConv, coldConv, lightConv, chaosConv);
        }
    }

    public static void logDefenderStats(
        @Nullable ComputedStats stats,
        @Nullable ElementalStats elemental
    ) {
        LOGGER.at(Level.FINE).log("[DmgPipeline] ──── DEFENDER STATS ────");
        if (stats == null) {
            LOGGER.at(Level.FINE).log("[DmgPipeline] (no stats)");
            return;
        }

        LOGGER.at(Level.FINE).log("[DmgPipeline] Armor: %.1f | Phys Resist: %.1f%%",
            stats.getArmor(), stats.getPhysicalResistance());
        LOGGER.at(Level.FINE).log("[DmgPipeline] Evasion: %.1f | Dodge: %.1f%% | Passive Block: %.1f%%",
            stats.getEvasion(), stats.getDodgeChance(), stats.getPassiveBlockChance());
        LOGGER.at(Level.FINE).log("[DmgPipeline] Crit Nullify: %.1f%%", stats.getCritNullifyChance());
        LOGGER.at(Level.FINE).log("[DmgPipeline] Energy Shield: %.1f", stats.getEnergyShield());

        // Elemental resistances
        if (elemental != null) {
            LOGGER.at(Level.FINE).log("[DmgPipeline] Elemental Resist - Fire: %.1f%% | Cold: %.1f%% | Lightning: %.1f%% | Chaos: %.1f%%",
                elemental.getResistance(ElementType.FIRE),
                elemental.getResistance(ElementType.WATER),
                elemental.getResistance(ElementType.LIGHTNING),
                elemental.getResistance(ElementType.VOID));
        }
    }

    public static void logDamageBreakdown(
        @Nonnull DamageBreakdown breakdown,
        float rpgBaseDamage,
        float attackTypeMultiplier,
        float conditionalMultiplier,
        @Nullable ComputedStats attackerStats,
        @Nullable ComputedStats defenderStats,
        @Nullable ElementalStats attackerElemental,
        @Nullable ElementalStats defenderElemental,
        float finalDamage
    ) {
        LOGGER.at(Level.FINE).log("[DmgPipeline] ──── DAMAGE BREAKDOWN ────");

        // Base calculation
        float rawCondMult = conditionalMultiplier / attackTypeMultiplier;
        LOGGER.at(Level.FINE).log("[DmgPipeline] Base Input: %.1f × attackMult(%.2f) × condMult(%.2f)",
            rpgBaseDamage, attackTypeMultiplier, rawCondMult);

        // Physical damage with armor
        float armorReduct = breakdown.armorReduction();
        LOGGER.at(Level.FINE).log("[DmgPipeline] Physical: %.1f (after armor: -%.1f%%)",
            breakdown.physicalDamage(), armorReduct);

        // Elemental damage with resistances
        for (ElementType elem : ElementType.values()) {
            float elemDmg = breakdown.getElementalDamage(elem);
            float elemResist = breakdown.getResistanceReduction(elem);
            if (elemDmg > 0 || elemResist > 0) {
                LOGGER.at(Level.FINE).log("[DmgPipeline] %s: %.1f (resist: -%.1f%%)",
                    elem.name(), elemDmg, elemResist);
            }
        }

        // True damage
        if (breakdown.trueDamage() > 0) {
            LOGGER.at(Level.FINE).log("[DmgPipeline] True: %.1f", breakdown.trueDamage());
        }

        // Critical
        String critStr;
        if (breakdown.wasCritical()) {
            String tierSuffix = breakdown.critTier() > 1 ? " T" + breakdown.critTier() : "";
            critStr = String.format("YES%s x%.2f", tierSuffix, breakdown.critMultiplier());
        } else {
            critStr = "NO";
        }
        LOGGER.at(Level.FINE).log("[DmgPipeline] Critical: %s", critStr);

        // Final summary
        LOGGER.at(Level.FINE).log("[DmgPipeline] ═══════════════════════════════════════════");
        LOGGER.at(Level.FINE).log("[DmgPipeline] FINAL DAMAGE: %.1f (%s)", finalDamage, breakdown.damageType());
        LOGGER.at(Level.FINE).log("[DmgPipeline] ════════════ DAMAGE EVENT END ════════════");
    }

    // ==================== Combat Log Helper Methods ====================

    /**
     * Extracts raw elemental resistances from defender's elemental stats for combat log display.
     *
     * @param elemental The defender's elemental stats (may be null)
     * @return Map of raw resistance values per element, or null if no stats
     */
    @Nullable
    public static Map<ElementType, Float> extractRawResistances(@Nullable ElementalStats elemental) {
        if (elemental == null) {
            return null;
        }

        EnumMap<ElementType, Float> result = new EnumMap<>(ElementType.class);
        boolean hasAny = false;
        for (ElementType type : ElementType.values()) {
            float resistance = (float) elemental.getResistance(type);
            result.put(type, resistance);
            if (resistance != 0f) {
                hasAny = true;
            }
        }
        return hasAny ? result : null;
    }

    /**
     * Extracts elemental penetration values from attacker's elemental stats for combat log display.
     *
     * @param elemental The attacker's elemental stats (may be null)
     * @return Map of penetration values per element, or null if no penetration
     */
    @Nullable
    public static Map<ElementType, Float> extractPenetration(@Nullable ElementalStats elemental) {
        if (elemental == null) {
            return null;
        }

        EnumMap<ElementType, Float> result = new EnumMap<>(ElementType.class);
        boolean hasAny = false;
        for (ElementType type : ElementType.values()) {
            float penetration = (float) elemental.getPenetration(type);
            result.put(type, penetration);
            if (penetration > 0f) {
                hasAny = true;
            }
        }
        return hasAny ? result : null;
    }
}
