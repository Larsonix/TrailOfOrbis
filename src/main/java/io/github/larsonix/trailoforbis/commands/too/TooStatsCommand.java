package io.github.larsonix.trailoforbis.commands.too;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import io.github.larsonix.trailoforbis.commands.base.OpenPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.TrailOfOrbis;
import io.github.larsonix.trailoforbis.api.ServiceRegistry;
import io.github.larsonix.trailoforbis.api.services.AttributeService;
import io.github.larsonix.trailoforbis.api.services.ConfigService;
import io.github.larsonix.trailoforbis.api.services.UIService;
import io.github.larsonix.trailoforbis.attributes.ComputedStats;
import io.github.larsonix.trailoforbis.config.RPGConfig;
import io.github.larsonix.trailoforbis.database.models.PlayerData;
import io.github.larsonix.trailoforbis.combat.CombatCalculator;
import io.github.larsonix.trailoforbis.leveling.api.LevelingService;
import io.github.larsonix.trailoforbis.util.MessageColors;
import io.github.larsonix.trailoforbis.util.NumberFormatter;

import javax.annotation.Nonnull;
import java.util.Optional;
import java.util.UUID;

/**
 * Command for displaying comprehensive player stats.
 *
 * <p>Usage: /too stats
 *
 * <p>Opens the Stats UI page showing organized sections for attributes,
 * resources, offense, defense, and movement stats.
 */
public class TooStatsCommand extends OpenPlayerCommand {
    @SuppressWarnings("unused")
    private final TrailOfOrbis plugin;

    public TooStatsCommand(TrailOfOrbis plugin) {
        super("stats", "View your complete RPG stats");
        this.plugin = plugin;
    }

    @Override
    protected void execute(
        @Nonnull CommandContext context,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull PlayerRef player,
        @Nonnull World world
    ) {
        // Open the Stats UI page instead of chat output
        Optional<UIService> uiServiceOpt = ServiceRegistry.get(UIService.class);
        if (uiServiceOpt.isPresent()) {
            uiServiceOpt.get().openStatsPage(player, store, ref);
            return;
        }

        // Fallback to chat output if UI service unavailable
        showChatStats(player);
    }

    /**
     * Shows stats in chat (fallback when UI is unavailable).
     */
    private void showChatStats(@Nonnull PlayerRef player) {
        UUID uuid = player.getUuid();

        AttributeService attributeService = ServiceRegistry.require(AttributeService.class);
        ConfigService configService = ServiceRegistry.require(ConfigService.class);

        Optional<PlayerData> dataOpt = attributeService.getPlayerDataRepository().get(uuid);

        if (dataOpt.isEmpty()) {
            player.sendMessage(Message.raw("No player data found !").color(MessageColors.ERROR));
            return;
        }

        PlayerData data = dataOpt.get();
        ComputedStats stats = attributeService.getStats(uuid);
        RPGConfig.AttributeConfig attrs = configService.getRPGConfig().getAttributes();

        // ==================== HEADER ====================
        player.sendMessage(Message.raw("").color(MessageColors.GRAY));
        player.sendMessage(Message.raw("============ CHARACTER STATS ============").color(MessageColors.GOLD));

        // ==================== ELEMENTS SECTION ====================
        sendSectionHeader(player, "ELEMENTS", MessageColors.WARNING);

        // Fire (Red theme) - Glass cannon
        sendAttributeLine(player, "FIRE", data.getFire(), "#FF4444",
            NumberFormatter.signedPercent(data.getFire() * attrs.getFireGrants().getPhysicalDamagePercent()) + " Phys, "
                + NumberFormatter.signedPercent(data.getFire() * attrs.getFireGrants().getChargedAttackDamagePercent()) + " Charged Atk, "
                + NumberFormatter.signedPercent(data.getFire() * attrs.getFireGrants().getCriticalMultiplier()) + " Crit Mult");

        // Water (Blue theme) - Arcane mage
        sendAttributeLine(player, "WATER", data.getWater(), "#4488FF",
            NumberFormatter.signedPercent(data.getWater() * attrs.getWaterGrants().getSpellDamagePercent()) + " Spell, "
                + NumberFormatter.signed(data.getWater() * attrs.getWaterGrants().getMaxMana()) + " Mana, "
                + NumberFormatter.signedPercent(data.getWater() * attrs.getWaterGrants().getEnergyShieldPercent()) + " ES, "
                + NumberFormatter.signed(data.getWater() * attrs.getWaterGrants().getEnergyShieldRegen()) + " ES/s");

        // Lightning (Yellow theme) - Storm blitz
        sendAttributeLine(player, "LIGHTNING", data.getLightning(), "#FFFF44",
            NumberFormatter.signedPercent(data.getLightning() * attrs.getLightningGrants().getAttackSpeedPercent()) + " Atk Speed, "
                + NumberFormatter.signedPercent(data.getLightning() * attrs.getLightningGrants().getMoveSpeedPercent()) + " Move, "
                + NumberFormatter.signedPercent(data.getLightning() * attrs.getLightningGrants().getCritChance()) + " Crit");

        // Earth (Brown theme) - Iron fortress
        sendAttributeLine(player, "EARTH", data.getEarth(), "#8B4513",
            NumberFormatter.signedPercent(data.getEarth() * attrs.getEarthGrants().getMaxHealthPercent()) + " Max HP, "
                + NumberFormatter.signed(data.getEarth() * attrs.getEarthGrants().getArmor()) + " Armor, "
                + NumberFormatter.signedPercent(data.getEarth() * attrs.getEarthGrants().getBlockChance()) + " Block");

        // Wind (Pale green theme) - Ghost ranger
        sendAttributeLine(player, "WIND", data.getWind(), "#AAFFAA",
            NumberFormatter.signed(data.getWind() * attrs.getWindGrants().getEvasion()) + " Eva, "
                + NumberFormatter.signed(data.getWind() * attrs.getWindGrants().getAccuracy()) + " Acc, "
                + NumberFormatter.signedPercent(data.getWind() * attrs.getWindGrants().getProjectileDamagePercent()) + " Proj Dmg");

        // Void (Purple theme) - Life devourer
        sendAttributeLine(player, "VOID", data.getVoidAttr(), "#8844AA",
            NumberFormatter.signedPercent(data.getVoidAttr() * attrs.getVoidGrants().getLifeSteal()) + " Life Steal, "
                + NumberFormatter.signedPercent(data.getVoidAttr() * attrs.getVoidGrants().getPercentHitAsTrueDamage()) + " True Dmg, "
                + NumberFormatter.signedPercent(data.getVoidAttr() * attrs.getVoidGrants().getDotDamagePercent()) + " DoT");

        // Unallocated points
        player.sendMessage(Message.empty()
            .insert(Message.raw("  Unallocated : ").color(MessageColors.GRAY))
            .insert(Message.raw(String.valueOf(data.getUnallocatedPoints())).color(
                data.getUnallocatedPoints() > 0 ? MessageColors.SUCCESS : MessageColors.WHITE)));

        if (stats == null) {
            player.sendMessage(Message.raw("Stats not calculated !").color(MessageColors.ERROR));
            return;
        }

        // ==================== RESOURCES SECTION ====================
        sendSectionHeader(player, "RESOURCES", MessageColors.SUCCESS);

        sendResourceLine(player, "Health", stats.getMaxHealth(), stats.getHealthRegen(),
            MessageColors.ERROR);
        sendResourceLine(player, "Mana", stats.getMaxMana(), stats.getManaRegen(),
            MessageColors.BLUE);
        sendResourceLine(player, "Stamina", stats.getMaxStamina(), stats.getStaminaRegen(),
            MessageColors.ORANGE);
        sendResourceLine(player, "Oxygen", stats.getMaxOxygen(), stats.getOxygenRegen(),
            MessageColors.LIGHT_BLUE);
        sendResourceLine(player, "Sig.Energy", stats.getMaxSignatureEnergy(), stats.getSignatureEnergyRegen(),
            MessageColors.PURPLE);

        // Energy Shield subsection
        if (stats.getEnergyShield() > 0) {
            player.sendMessage(Message.empty()
                .insert(Message.raw("  E. Shield : ").color(MessageColors.GRAY))
                .insert(Message.raw(NumberFormatter.flat(stats.getEnergyShield())).color(MessageColors.LIGHT_BLUE))
                .insert(Message.raw("  (" + NumberFormatter.regen(stats.getEnergyShieldRegen())
                    + ", " + NumberFormatter.time(stats.getEnergyShieldRegenDelay()) + " delay)").color(MessageColors.GRAY)));
        }

        // Stamina recovery speed (if modified from default)
        if (stats.getStaminaRegenStartDelay() != 0) {
            player.sendMessage(Message.empty()
                .insert(Message.raw("  Stamina Recovery : ").color(MessageColors.GRAY))
                .insert(Message.raw(NumberFormatter.signedPercent(stats.getStaminaRegenStartDelay())).color(
                    stats.getStaminaRegenStartDelay() > 0 ? MessageColors.SUCCESS : MessageColors.ERROR)));
        }

        // ==================== OFFENSE SECTION ====================
        sendSectionHeader(player, "OFFENSE", MessageColors.ERROR);

        player.sendMessage(Message.empty()
            .insert(Message.raw("  Physical : ").color(MessageColors.GRAY))
            .insert(Message.raw(NumberFormatter.signed(stats.getPhysicalDamage()) + " flat").color(MessageColors.WHITE))
            .insert(Message.raw(", " + NumberFormatter.signedPercent(stats.getPhysicalDamagePercent())).color(MessageColors.GRAY)));

        player.sendMessage(Message.empty()
            .insert(Message.raw("  Spell : ").color(MessageColors.GRAY))
            .insert(Message.raw(NumberFormatter.signed(stats.getSpellDamage()) + " flat").color(MessageColors.WHITE))
            .insert(Message.raw(", " + NumberFormatter.signedPercent(stats.getSpellDamagePercent())).color(MessageColors.GRAY)));

        player.sendMessage(Message.empty()
            .insert(Message.raw("  Critical : ").color(MessageColors.GRAY))
            .insert(Message.raw(NumberFormatter.percent(stats.getCriticalChance()) + " chance").color(MessageColors.WARNING))
            .insert(Message.raw(", " + NumberFormatter.percent(stats.getCriticalMultiplier()) + " multiplier").color(MessageColors.GRAY)));

        if (stats.getMeleeDamagePercent() > 0 || stats.getProjectileDamagePercent() > 0) {
            player.sendMessage(Message.empty()
                .insert(Message.raw("  Melee : ").color(MessageColors.GRAY))
                .insert(Message.raw(NumberFormatter.signedPercent(stats.getMeleeDamagePercent())).color(MessageColors.WHITE))
                .insert(Message.raw("  Projectile : ").color(MessageColors.GRAY))
                .insert(Message.raw(NumberFormatter.signedPercent(stats.getProjectileDamagePercent())).color(MessageColors.WHITE)));
        }

        // ==================== DEFENSE SECTION ====================
        sendSectionHeader(player, "DEFENSE", MessageColors.INFO);

        int playerLevel = ServiceRegistry.get(LevelingService.class)
            .map(ls -> ls.getLevel(uuid)).orElse(1);
        RPGConfig.ArmorConfig armorCfg = configService.getRPGConfig().getArmor();
        player.sendMessage(Message.empty()
            .insert(Message.raw("  Armor : ").color(MessageColors.GRAY))
            .insert(Message.raw(NumberFormatter.smallFlat(stats.getArmor())).color(MessageColors.WHITE))
            .insert(Message.raw(" (" + NumberFormatter.percent(
                CombatCalculator.estimateArmorReduction(stats.getArmor(), playerLevel,
                    armorCfg.getLevelScale(), armorCfg.getBaseConstant()))
                + " reduction vs Lv" + playerLevel + ")").color(MessageColors.GRAY)));

        if (stats.getFallDamageReduction() > 0) {
            player.sendMessage(Message.empty()
                .insert(Message.raw("  Fall Resist : ").color(MessageColors.GRAY))
                .insert(Message.raw(NumberFormatter.percent(stats.getFallDamageReduction())).color(MessageColors.SUCCESS)));
        }

        if (stats.getPhysicalResistance() > 0) {
            player.sendMessage(Message.empty()
                .insert(Message.raw("  Phys Resist : ").color(MessageColors.GRAY))
                .insert(Message.raw(NumberFormatter.percent(stats.getPhysicalResistance())).color(MessageColors.WHITE)));
        }

        if (stats.getFireResistance() > 0 || stats.getWaterResistance() > 0 ||
            stats.getLightningResistance() > 0 || stats.getEarthResistance() > 0 ||
            stats.getWindResistance() > 0 || stats.getVoidResistance() > 0) {
            player.sendMessage(Message.empty()
                .insert(Message.raw("  Resists : ").color(MessageColors.GRAY))
                .insert(Message.raw("Fire " + NumberFormatter.percent(stats.getFireResistance())).color("#FF6600"))
                .insert(Message.raw(", ").color(MessageColors.GRAY))
                .insert(Message.raw("Water " + NumberFormatter.percent(stats.getWaterResistance())).color("#66CCFF"))
                .insert(Message.raw(", ").color(MessageColors.GRAY))
                .insert(Message.raw("Light " + NumberFormatter.percent(stats.getLightningResistance())).color("#FFFF00"))
                .insert(Message.raw(", ").color(MessageColors.GRAY))
                .insert(Message.raw("Earth " + NumberFormatter.percent(stats.getEarthResistance())).color("#8B4513"))
                .insert(Message.raw(", ").color(MessageColors.GRAY))
                .insert(Message.raw("Wind " + NumberFormatter.percent(stats.getWindResistance())).color("#AAFFAA"))
                .insert(Message.raw(", ").color(MessageColors.GRAY))
                .insert(Message.raw("Void " + NumberFormatter.percent(stats.getVoidResistance())).color(MessageColors.DARK_PURPLE)));
        }

        if (stats.getEvasion() > 0) {
            player.sendMessage(Message.empty()
                .insert(Message.raw("  Evasion : ").color(MessageColors.GRAY))
                .insert(Message.raw(NumberFormatter.smallFlat(stats.getEvasion())).color(MessageColors.WHITE)));
        }

        // ==================== MOVEMENT SECTION ====================
        sendSectionHeader(player, "MOVEMENT", MessageColors.ORANGE);

        player.sendMessage(Message.empty()
            .insert(Message.raw("  Move Speed : ").color(MessageColors.GRAY))
            .insert(Message.raw(NumberFormatter.signedPercent(stats.getMovementSpeedPercent())).color(MessageColors.WHITE))
            .insert(Message.raw("  Sprint : ").color(MessageColors.GRAY))
            .insert(Message.raw(NumberFormatter.signedPercent(stats.getSprintSpeedBonus())).color(MessageColors.WHITE)));

        player.sendMessage(Message.empty()
            .insert(Message.raw("  Jump Force : ").color(MessageColors.GRAY))
            .insert(Message.raw(NumberFormatter.signedPercent(stats.getJumpForceBonus())).color(MessageColors.WHITE))
            .insert(Message.raw("  Climb : ").color(MessageColors.GRAY))
            .insert(Message.raw(NumberFormatter.signedPercent(stats.getClimbSpeedBonus())).color(MessageColors.WHITE)));

        // ==================== FOOTER ====================
        player.sendMessage(Message.raw("==========================================").color(MessageColors.GOLD));
        player.sendMessage(Message.raw("Tip : Use /too attr allocate <stat> to allocate points").color(MessageColors.GRAY));
    }

    private void sendSectionHeader(@Nonnull PlayerRef player, @Nonnull String title, @Nonnull String color) {
        player.sendMessage(Message.empty()
            .insert(Message.raw("--- ").color(MessageColors.GRAY))
            .insert(Message.raw(title).color(color))
            .insert(Message.raw(" ---").color(MessageColors.GRAY)));
    }

    private void sendAttributeLine(
        @Nonnull PlayerRef player,
        @Nonnull String name,
        int value,
        @Nonnull String color,
        @Nonnull String contributions
    ) {
        player.sendMessage(Message.empty()
            .insert(Message.raw("  " + name + " : ").color(color))
            .insert(Message.raw(String.valueOf(value)).color(MessageColors.WHITE))
            .insert(Message.raw("  (" + contributions + ")").color(MessageColors.GRAY)));
    }

    private void sendResourceLine(
        @Nonnull PlayerRef player,
        @Nonnull String name,
        float maxValue,
        float regenRate,
        @Nonnull String color
    ) {
        Message msg = Message.empty()
            .insert(Message.raw("  " + name + " : ").color(MessageColors.GRAY))
            .insert(Message.raw(NumberFormatter.flat(maxValue)).color(color));

        if (regenRate > 0) {
            msg.insert(Message.raw("  (" + NumberFormatter.regen(regenRate) + ")").color(MessageColors.GRAY));
        }

        player.sendMessage(msg);
    }

}
