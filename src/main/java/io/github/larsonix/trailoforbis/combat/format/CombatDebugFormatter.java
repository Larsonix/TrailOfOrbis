package io.github.larsonix.trailoforbis.combat.format;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import io.github.larsonix.trailoforbis.api.ServiceRegistry;
import io.github.larsonix.trailoforbis.api.services.AttributeService;
import io.github.larsonix.trailoforbis.api.services.SkillTreeService;
import io.github.larsonix.trailoforbis.attributes.ComputedStats;
import io.github.larsonix.trailoforbis.combat.AttackType;
import io.github.larsonix.trailoforbis.combat.DamageBreakdown;
import io.github.larsonix.trailoforbis.database.models.PlayerData;
import io.github.larsonix.trailoforbis.elemental.ElementType;
import io.github.larsonix.trailoforbis.gear.model.GearData;
import io.github.larsonix.trailoforbis.gear.model.WeaponImplicit;
import io.github.larsonix.trailoforbis.gear.util.GearUtils;
import io.github.larsonix.trailoforbis.leveling.api.LevelingService;
import io.github.larsonix.trailoforbis.skilltree.model.SkillTreeData;
import io.github.larsonix.trailoforbis.util.MessageColors;
import io.github.larsonix.trailoforbis.util.PlayerWorldCache;

import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Formats the full combat debug dump shown on every hit when /too combat debug is enabled.
 *
 * <p>Five sections: Attributes, Skill Tree, Equipment, Key Stats, Hit Result.
 * All data is queried live from ServiceRegistry at the moment of the hit.
 */
public final class CombatDebugFormatter {

    private CombatDebugFormatter() {}

    /**
     * Builds the full debug dump message for a combat hit.
     *
     * @param attackerUuid The attacker's UUID
     * @param attackerStats The attacker's ComputedStats used in this calculation (may be null)
     * @param attackerElemental The attacker's ElementalStats used in this calculation (may be null)
     * @param breakdown The damage breakdown from the calculator
     * @param attackType The attack type
     * @param spellElement The weapon/spell element (null for physical)
     * @param baseDamage The RPG base damage fed into the calculator
     * @param attackTypeMultiplier The attack type multiplier applied
     * @param conditionalMultiplier The conditional multiplier applied
     * @return Formatted chat message
     */
    @Nonnull
    public static Message format(
        @Nonnull UUID attackerUuid,
        @Nullable ComputedStats attackerStats,
        @Nullable io.github.larsonix.trailoforbis.elemental.ElementalStats attackerElemental,
        @Nonnull DamageBreakdown breakdown,
        @Nonnull AttackType attackType,
        @Nullable ElementType spellElement,
        float baseDamage,
        float attackTypeMultiplier,
        float conditionalMultiplier
    ) {
        MessageBuilder mb = new MessageBuilder();
        mb.header("COMBAT DEBUG", MessageColors.GOLD);

        renderAttributes(mb, attackerUuid);
        renderSkillTree(mb, attackerUuid);
        renderEquipment(mb, attackerUuid);
        renderKeyStats(mb, attackerStats, attackerElemental);
        renderHitResult(mb, breakdown, attackType, spellElement, baseDamage, attackTypeMultiplier, conditionalMultiplier);

        mb.footer(MessageColors.GOLD);
        return mb.build();
    }

    /**
     * Generates a plain-text version of the debug dump for the server log.
     * Same data as the chat version, but as a simple string without color formatting.
     */
    @Nonnull
    public static String formatForLog(
        @Nonnull UUID attackerUuid,
        @Nullable ComputedStats attackerStats,
        @Nullable io.github.larsonix.trailoforbis.elemental.ElementalStats attackerElemental,
        @Nonnull DamageBreakdown breakdown,
        @Nonnull AttackType attackType,
        @Nullable ElementType spellElement,
        float baseDamage,
        float attackTypeMultiplier,
        float conditionalMultiplier
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("═══ COMBAT DEBUG ═══\n");

        // Attributes
        AttributeService attrService = ServiceRegistry.get(AttributeService.class).orElse(null);
        if (attrService != null) {
            Optional<PlayerData> dataOpt = attrService.getPlayerDataRepository().get(attackerUuid);
            if (dataOpt.isPresent()) {
                PlayerData d = dataOpt.get();
                int level = ServiceRegistry.get(LevelingService.class).map(s -> s.getLevel(attackerUuid)).orElse(1);
                sb.append(String.format("Attrs: F:%d W:%d L:%d E:%d A:%d V:%d (Lv%d)\n",
                    d.getFire(), d.getWater(), d.getLightning(), d.getEarth(), d.getWind(), d.getVoidAttr(), level));
            }
        }

        // Skill tree
        SkillTreeService treeService = ServiceRegistry.get(SkillTreeService.class).orElse(null);
        if (treeService != null) {
            SkillTreeData treeData = treeService.getSkillTreeData(attackerUuid);
            Set<String> nodes = treeData.getAllocatedNodes();
            sb.append(String.format("Tree: %d nodes", nodes.size()));
            if (!nodes.isEmpty()) {
                sb.append(" [");
                int count = 0;
                for (String n : nodes) {
                    if (count > 0) sb.append(", ");
                    if (count >= 10) { sb.append("...+").append(nodes.size() - 10); break; }
                    sb.append(n);
                    count++;
                }
                sb.append("]");
            }
            sb.append("\n");
        }

        // Equipment
        Inventory inv = getInventory(attackerUuid);
        if (inv != null) {
            ItemStack weapon = inv.getActiveHotbarItem();
            if (weapon != null && !weapon.isEmpty()) {
                String wId = weapon.getItem() != null ? weapon.getItem().getId() : "?";
                Optional<GearData> gd = GearUtils.readGearData(weapon);
                if (gd.isPresent()) {
                    var implicit = gd.get().implicit();
                    sb.append(String.format("Weapon: %s [%s Lv%d]",
                        gd.get().getBaseItemId() != null ? gd.get().getBaseItemId() : wId,
                        gd.get().rarity(), gd.get().level()));
                    if (implicit != null) {
                        sb.append(String.format(" -> %.0f %s (%s)", implicit.rolledValue(),
                            implicit.damageTypeDisplayName(), implicit.damageType()));
                    }
                    sb.append("\n");
                } else {
                    sb.append("Weapon: ").append(wId).append(" (vanilla)\n");
                }
            } else {
                sb.append("Weapon: (empty)\n");
            }
        }

        // Key stats
        if (attackerStats != null) {
            sb.append(String.format("Stats: physDmg=%.1f(+%.1f%%) melee=%.1f(+%.1f%%) spell=%.1f(+%.1f%%)\n",
                attackerStats.getPhysicalDamage(), attackerStats.getPhysicalDamagePercent(),
                attackerStats.getMeleeDamage(), attackerStats.getMeleeDamagePercent(),
                attackerStats.getSpellDamage(), attackerStats.getSpellDamagePercent()));
            sb.append(String.format("Stats: crit=%.1f%%/%.0f%% atkSpd=+%.1f%% fireConv=%.0f%% voidConv=%.0f%%\n",
                attackerStats.getCriticalChance(), attackerStats.getCriticalMultiplier(),
                attackerStats.getAttackSpeedPercent(),
                attackerStats.getFireConversion(), attackerStats.getVoidConversion()));
            sb.append(String.format("Stats: weaponElement=%s weaponBase=%.1f isRpg=%s\n",
                attackerStats.getWeaponSpellElement() != null ? attackerStats.getWeaponSpellElement().name() : "NULL",
                attackerStats.getWeaponBaseDamage(), attackerStats.isHoldingRpgGear()));
        }

        // Hit result
        sb.append(String.format("Hit: element=%s type=%s base=%.1f atkMult=%.2fx cond=%.2fx\n",
            spellElement != null ? spellElement.name() : "PHYS", attackType, baseDamage,
            attackTypeMultiplier, conditionalMultiplier));

        if (breakdown.wasAvoided()) {
            String avoidType = breakdown.wasDodged() ? "DODGED" : breakdown.wasEvaded() ? "EVADED" : breakdown.wasBlocked() ? "BLOCKED" : breakdown.wasParried() ? "PARRIED" : "MISSED";
            sb.append("Result: ").append(avoidType).append(" (0 damage)\n");
        } else {
            sb.append(String.format("PreDef=%.1f PostDef=%.1f armor=-%.0f%%",
                breakdown.preDefenseDamage(), breakdown.totalDamage(), breakdown.armorReduction()));
            if (breakdown.shieldAbsorbed() > 0) sb.append(String.format(" shield=%.1f", breakdown.shieldAbsorbed()));
            sb.append("\n");
            sb.append(String.format("Result: damageType=%s crit=%s phys=%.1f true=%.1f",
                breakdown.damageType(), breakdown.wasCritical(), breakdown.physicalDamage(), breakdown.trueDamage()));
            for (ElementType elem : ElementType.values()) {
                float dmg = breakdown.getElementalDamage(elem);
                if (dmg > 0) sb.append(String.format(" %s=%.1f", elem.name(), dmg));
            }
            float total = breakdown.physicalDamage() + breakdown.trueDamage();
            for (ElementType elem : ElementType.values()) total += breakdown.getElementalDamage(elem);
            sb.append(String.format(" total=%.1f", total));
        }
        sb.append("\n═══════════════════");

        return sb.toString();
    }

    // ==================== Section 1: Attributes ====================

    private static void renderAttributes(@Nonnull MessageBuilder mb, @Nonnull UUID uuid) {
        mb.section("Attributes");

        AttributeService attrService = ServiceRegistry.get(AttributeService.class).orElse(null);
        if (attrService == null) {
            mb.line("(AttributeService unavailable)", MessageColors.GRAY);
            return;
        }

        Optional<PlayerData> dataOpt = attrService.getPlayerDataRepository().get(uuid);
        if (dataOpt.isEmpty()) {
            mb.line("(PlayerData not found)", MessageColors.GRAY);
            return;
        }

        PlayerData data = dataOpt.get();
        int level = ServiceRegistry.get(LevelingService.class)
            .map(svc -> svc.getLevel(uuid))
            .orElse(1);

        mb.line(String.format(
            "Fire:%d  Water:%d  Light:%d  Earth:%d  Wind:%d  Void:%d  (Lv%d)",
            data.getFire(), data.getWater(), data.getLightning(),
            data.getEarth(), data.getWind(), data.getVoidAttr(), level
        ), MessageColors.WHITE);
    }

    // ==================== Section 2: Skill Tree ====================

    private static void renderSkillTree(@Nonnull MessageBuilder mb, @Nonnull UUID uuid) {
        mb.section("Skill Tree");

        SkillTreeService treeService = ServiceRegistry.get(SkillTreeService.class).orElse(null);
        if (treeService == null) {
            mb.line("(SkillTreeService unavailable)", MessageColors.GRAY);
            return;
        }

        SkillTreeData treeData = treeService.getSkillTreeData(uuid);
        Set<String> nodes = treeData.getAllocatedNodes();

        if (nodes.isEmpty()) {
            mb.line("No nodes allocated", MessageColors.GRAY);
            return;
        }

        mb.line(nodes.size() + " nodes allocated", MessageColors.WHITE);

        // Show node IDs (truncate if too many)
        StringBuilder nodeList = new StringBuilder();
        int count = 0;
        for (String nodeId : nodes) {
            if (count > 0) nodeList.append(", ");
            if (count >= 15) {
                nodeList.append("... +").append(nodes.size() - 15).append(" more");
                break;
            }
            nodeList.append(nodeId);
            count++;
        }
        mb.line(nodeList.toString(), MessageColors.GRAY);
    }

    // ==================== Section 3: Equipment ====================

    private static void renderEquipment(@Nonnull MessageBuilder mb, @Nonnull UUID uuid) {
        mb.section("Equipment");

        Inventory inventory = getInventory(uuid);
        if (inventory == null) {
            mb.line("(Inventory unavailable)", MessageColors.GRAY);
            return;
        }

        // Weapon
        ItemStack weapon = inventory.getActiveHotbarItem();
        renderWeaponSlot(mb, "Weapon", weapon);

        // Offhand
        ItemStack offhand = inventory.getUtilityItem();
        renderGearSlot(mb, "Offhand", offhand);

        // Armor (compact: all on one line)
        renderArmorCompact(mb, inventory);
    }

    private static void renderWeaponSlot(@Nonnull MessageBuilder mb, @Nonnull String label, @Nullable ItemStack item) {
        if (item == null || item.isEmpty()) {
            mb.line(label + ": (empty)", MessageColors.GRAY);
            return;
        }

        String itemId = item.getItem() != null ? item.getItem().getId() : "???";
        Optional<GearData> gearOpt = GearUtils.readGearData(item);
        if (gearOpt.isEmpty()) {
            mb.line(label + ": " + itemId + " (vanilla)", MessageColors.WHITE);
            return;
        }

        GearData gear = gearOpt.get();
        WeaponImplicit implicit = gear.implicit();
        if (implicit != null) {
            ElementType elem = implicit.getSpellElement();
            mb.line(String.format("%s: %s [%s Lv%d] -> %.0f %s (%s)",
                label,
                gear.getBaseItemId() != null ? gear.getBaseItemId() : itemId,
                gear.rarity(),
                gear.level(),
                implicit.rolledValue(),
                implicit.damageTypeDisplayName(),
                implicit.damageType()
            ), elem != null ? getElementColor(elem) : MessageColors.WHITE);
        } else {
            mb.line(String.format("%s: %s [%s Lv%d] (no implicit)",
                label,
                gear.getBaseItemId() != null ? gear.getBaseItemId() : itemId,
                gear.rarity(),
                gear.level()
            ), MessageColors.WHITE);
        }
    }

    private static void renderGearSlot(@Nonnull MessageBuilder mb, @Nonnull String label, @Nullable ItemStack item) {
        if (item == null || item.isEmpty()) {
            mb.line(label + ": (empty)", MessageColors.GRAY);
            return;
        }

        String itemId = item.getItem() != null ? item.getItem().getId() : "???";
        Optional<GearData> gearOpt = GearUtils.readGearData(item);
        if (gearOpt.isEmpty()) {
            mb.line(label + ": " + itemId + " (vanilla)", MessageColors.WHITE);
            return;
        }

        GearData gear = gearOpt.get();
        mb.line(String.format("%s: %s [%s Lv%d]",
            label,
            gear.getBaseItemId() != null ? gear.getBaseItemId() : itemId,
            gear.rarity(),
            gear.level()
        ), MessageColors.WHITE);
    }

    private static void renderArmorCompact(@Nonnull MessageBuilder mb, @Nonnull Inventory inventory) {
        var armorContainer = inventory.getArmor();
        if (armorContainer == null || armorContainer.getCapacity() == 0) {
            mb.line("Armor: (none)", MessageColors.GRAY);
            return;
        }

        StringBuilder sb = new StringBuilder("Armor: ");
        boolean any = false;
        String[] slotNames = {"Head", "Chest", "Legs", "Hands"};

        for (short slot = 0; slot < Math.min(armorContainer.getCapacity(), 4); slot++) {
            ItemStack item = armorContainer.getItemStack(slot);
            if (any) sb.append(" | ");

            String slotName = slot < slotNames.length ? slotNames[slot] : "Slot" + slot;
            if (item == null || item.isEmpty()) {
                sb.append(slotName).append(":--");
            } else {
                Optional<GearData> gearOpt = GearUtils.readGearData(item);
                if (gearOpt.isPresent()) {
                    GearData gear = gearOpt.get();
                    sb.append(slotName).append(":").append(gear.rarity().name().charAt(0)).append("L").append(gear.level());
                } else {
                    String id = item.getItem() != null ? item.getItem().getId() : "?";
                    sb.append(slotName).append(":").append(id);
                }
            }
            any = true;
        }

        mb.line(sb.toString(), MessageColors.WHITE);
    }

    // ==================== Section 4: Key Stats ====================

    private static void renderKeyStats(
        @Nonnull MessageBuilder mb,
        @Nullable ComputedStats stats,
        @Nullable io.github.larsonix.trailoforbis.elemental.ElementalStats elemental
    ) {
        mb.section("Key Stats");

        if (stats == null) {
            mb.line("(ComputedStats unavailable)", MessageColors.GRAY);
            return;
        }

        // Damage stats
        mb.line(String.format(
            "PhysDmg:%.1f (+%.1f%%)  MeleeDmg:%.1f (+%.1f%%)  SpellDmg:%.1f (+%.1f%%)",
            stats.getPhysicalDamage(), stats.getPhysicalDamagePercent(),
            stats.getMeleeDamage(), stats.getMeleeDamagePercent(),
            stats.getSpellDamage(), stats.getSpellDamagePercent()
        ), MessageColors.INFO);

        // Crit and speed
        mb.line(String.format(
            "Crit:%.1f%%/%.0f%%  AtkSpd:+%.1f%%  DmgPct:%.1f%%  AllDmg:%.1f%%",
            stats.getCriticalChance(), stats.getCriticalMultiplier(),
            stats.getAttackSpeedPercent(),
            stats.getDamagePercent(), stats.getAllDamagePercent()
        ), MessageColors.INFO);

        // Conversion
        float totalConv = stats.getFireConversion() + stats.getWaterConversion()
            + stats.getLightningConversion() + stats.getEarthConversion()
            + stats.getWindConversion() + stats.getVoidConversion();
        if (totalConv > 0) {
            mb.line(String.format(
                "Conv: Fire:%.0f%% Water:%.0f%% Light:%.0f%% Earth:%.0f%% Wind:%.0f%% Void:%.0f%%",
                stats.getFireConversion(), stats.getWaterConversion(),
                stats.getLightningConversion(), stats.getEarthConversion(),
                stats.getWindConversion(), stats.getVoidConversion()
            ), MessageColors.INFO);
        }

        // Defense summary
        mb.line(String.format(
            "Armor:%.1f (+%.1f%%)  Evasion:%.1f  ES:%.1f  Dodge:%.1f%%",
            stats.getArmor(), stats.getArmorPercent(),
            stats.getEvasion(), stats.getEnergyShield(), stats.getDodgeChance()
        ), MessageColors.INFO);

        // Elemental flat damage from gear/tree (NOT weapon base)
        if (elemental != null) {
            StringBuilder elemFlat = new StringBuilder("ElemFlat:");
            boolean any = false;
            for (ElementType type : ElementType.values()) {
                double flat = elemental.getFlatDamage(type);
                if (flat > 0) {
                    if (any) elemFlat.append("  ");
                    elemFlat.append(type.name().substring(0, 1)).append(":").append(String.format("%.1f", flat));
                    any = true;
                }
            }
            if (any) {
                elemFlat.append("  (from gear/tree)");
                mb.line(elemFlat.toString(), MessageColors.INFO);
            }
        }
    }

    // ==================== Section 5: Hit Result ====================

    private static void renderHitResult(
        @Nonnull MessageBuilder mb,
        @Nonnull DamageBreakdown bd,
        @Nonnull AttackType attackType,
        @Nullable ElementType spellElement,
        float baseDamage,
        float attackTypeMultiplier,
        float conditionalMultiplier
    ) {
        mb.section("Hit Result");

        // Attack context
        String elemLabel = spellElement != null ? spellElement.name() : "PHYS";
        String elemColor = spellElement != null ? CombatFormatConstants.getElementColor(spellElement) : MessageColors.WHITE;
        mb.text("Element:", MessageColors.GRAY);
        mb.text(elemLabel, elemColor);
        mb.line(String.format("  Type:%s  Base:%.1f  AtkMult:%.2fx",
            attackType.name(), baseDamage, attackTypeMultiplier), MessageColors.WHITE);

        // Avoidance flags
        if (bd.wasAvoided()) {
            String avoidType = bd.wasDodged() ? "DODGED" : bd.wasEvaded() ? "EVADED" : bd.wasBlocked() ? "BLOCKED" : bd.wasParried() ? "PARRIED" : "MISSED";
            mb.line("Result: " + avoidType + " (0 damage)", MessageColors.SUCCESS);
            return;
        }

        // Pre-defense vs post-defense
        float preDef = bd.preDefenseDamage();
        float total = bd.totalDamage();
        if (preDef > 0 && preDef != total) {
            mb.line(String.format("Pre-Defense:%.1f  Post-Defense:%.1f  Mitigated:%.1f (%.0f%%)",
                preDef, total, preDef - total, (preDef - total) / preDef * 100f), MessageColors.INFO);
        }

        // Defense breakdown
        if (bd.armorReduction() > 0) {
            mb.text(String.format("Armor: -%.0f%%", bd.armorReduction()), MessageColors.INFO);
        }
        StringBuilder resistLine = new StringBuilder();
        for (ElementType elem : ElementType.values()) {
            float resist = bd.getResistanceReduction(elem);
            if (resist != 0) {
                if (!resistLine.isEmpty()) resistLine.append("  ");
                resistLine.append(String.format("%s:-%.0f%%", elem.name().substring(0, 1), resist));
            }
        }
        if (!resistLine.isEmpty()) {
            mb.text("  Resist: " + resistLine, MessageColors.INFO);
        }
        if (bd.armorReduction() > 0 || !resistLine.isEmpty()) {
            mb.line("", MessageColors.GRAY); // newline after defense line
        }

        // Per-type final damage (element-colored)
        StringBuilder dmgLine = new StringBuilder();
        float phys = bd.physicalDamage();
        if (phys > 0) dmgLine.append(String.format("Physical:%.1f  ", phys));
        for (ElementType elem : ElementType.values()) {
            float elemDmg = bd.getElementalDamage(elem);
            if (elemDmg > 0) dmgLine.append(String.format("%s:%.1f  ", elem.getDisplayName(), elemDmg));
        }
        float trueDmg = bd.trueDamage();
        if (trueDmg > 0) dmgLine.append(String.format("True:%.1f  ", trueDmg));
        if (dmgLine.length() > 0) {
            mb.line(dmgLine.toString().trim(), MessageColors.WHITE);
        }

        // Post-calc effects
        if (bd.shieldAbsorbed() > 0) {
            mb.line(String.format("Shield Absorbed: %.1f", bd.shieldAbsorbed()), MessageColors.LIGHT_BLUE);
        }
        if (bd.wasBlocked()) {
            mb.line("Active Block: damage reduced", MessageColors.INFO);
        }
        if (bd.wasParried()) {
            mb.line("Parried: damage reduced", MessageColors.INFO);
        }

        // Summary line
        mb.line(String.format("Crit:%s  Conditional:%.2fx  DamageType:%s  Total:%.1f",
            bd.wasCritical() ? "YES" + (bd.critTier() > 1 ? " T" + bd.critTier() : "") + " (" + String.format("%.2f", bd.critMultiplier()) + "x)" : "NO",
            conditionalMultiplier,
            bd.damageType().name(),
            total
        ), bd.wasCritical() ? MessageColors.GOLD : MessageColors.SUCCESS);
    }

    // ==================== Helpers ====================

    @Nullable
    private static Inventory getInventory(@Nonnull UUID uuid) {
        try {
            PlayerRef ref = PlayerWorldCache.findPlayerRef(uuid);
            if (ref == null) return null;
            Ref<EntityStore> entityRef = ref.getReference();
            if (entityRef == null || !entityRef.isValid()) return null;
            Player player = entityRef.getStore().getComponent(entityRef, Player.getComponentType());
            if (player == null) return null;
            return player.getInventory();
        } catch (Exception e) {
            return null;
        }
    }

    @Nonnull
    private static String getElementColor(@Nonnull ElementType elem) {
        return CombatFormatConstants.getElementColor(elem);
    }
}
