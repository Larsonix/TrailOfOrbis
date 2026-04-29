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
import io.github.larsonix.trailoforbis.util.MessageColors;

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
            String.format("+%.1f%% Phys, +%.1f%% Charged Atk, +%.1f%% Crit Mult",
                data.getFire() * attrs.getFireGrants().getPhysicalDamagePercent(),
                data.getFire() * attrs.getFireGrants().getChargedAttackDamagePercent(),
                data.getFire() * attrs.getFireGrants().getCriticalMultiplier()));

        // Water (Blue theme) - Arcane mage
        sendAttributeLine(player, "WATER", data.getWater(), "#4488FF",
            String.format("+%.1f%% Spell, +%.1f Mana, +%.0f Barrier",
                data.getWater() * attrs.getWaterGrants().getSpellDamagePercent(),
                data.getWater() * attrs.getWaterGrants().getMaxMana(),
                data.getWater() * attrs.getWaterGrants().getEnergyShield()));

        // Lightning (Yellow theme) - Storm blitz
        sendAttributeLine(player, "LIGHTNING", data.getLightning(), "#FFFF44",
            String.format("+%.1f%% Atk Speed, +%.1f%% Move, +%.1f%% Crit",
                data.getLightning() * attrs.getLightningGrants().getAttackSpeedPercent(),
                data.getLightning() * attrs.getLightningGrants().getMoveSpeedPercent(),
                data.getLightning() * attrs.getLightningGrants().getCritChance()));

        // Earth (Brown theme) - Iron fortress
        sendAttributeLine(player, "EARTH", data.getEarth(), "#8B4513",
            String.format("+%.1f%% Max HP, +%.0f Armor, +%.1f%% Block",
                data.getEarth() * attrs.getEarthGrants().getMaxHealthPercent(),
                data.getEarth() * attrs.getEarthGrants().getArmor(),
                data.getEarth() * attrs.getEarthGrants().getBlockChance()));

        // Wind (Pale green theme) - Ghost ranger
        sendAttributeLine(player, "WIND", data.getWind(), "#AAFFAA",
            String.format("+%.0f Eva, +%.0f Acc, +%.1f%% Proj Dmg",
                data.getWind() * attrs.getWindGrants().getEvasion(),
                data.getWind() * attrs.getWindGrants().getAccuracy(),
                data.getWind() * attrs.getWindGrants().getProjectileDamagePercent()));

        // Void (Purple theme) - Life devourer
        sendAttributeLine(player, "VOID", data.getVoidAttr(), "#8844AA",
            String.format("+%.1f%% Life Steal, +%.2f%% True Dmg, +%.1f%% DoT",
                data.getVoidAttr() * attrs.getVoidGrants().getLifeSteal(),
                data.getVoidAttr() * attrs.getVoidGrants().getPercentHitAsTrueDamage(),
                data.getVoidAttr() * attrs.getVoidGrants().getDotDamagePercent()));

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
            MessageColors.ERROR, "/sec");
        sendResourceLine(player, "Mana", stats.getMaxMana(), stats.getManaRegen(),
            MessageColors.BLUE, "/sec");
        sendResourceLine(player, "Stamina", stats.getMaxStamina(), stats.getStaminaRegen(),
            MessageColors.ORANGE, "/sec");
        sendResourceLine(player, "Oxygen", stats.getMaxOxygen(), stats.getOxygenRegen(),
            MessageColors.LIGHT_BLUE, "/sec");
        sendResourceLine(player, "Sig.Energy", stats.getMaxSignatureEnergy(), stats.getSignatureEnergyRegen(),
            MessageColors.PURPLE, "/sec");

        // ==================== OFFENSE SECTION ====================
        sendSectionHeader(player, "OFFENSE", MessageColors.ERROR);

        player.sendMessage(Message.empty()
            .insert(Message.raw("  Physical : ").color(MessageColors.GRAY))
            .insert(Message.raw(String.format("+%.1f flat", stats.getPhysicalDamage())).color(MessageColors.WHITE))
            .insert(Message.raw(String.format(", +%.1f%%", stats.getPhysicalDamagePercent())).color(MessageColors.GRAY)));

        player.sendMessage(Message.empty()
            .insert(Message.raw("  Spell : ").color(MessageColors.GRAY))
            .insert(Message.raw(String.format("+%.1f flat", stats.getSpellDamage())).color(MessageColors.WHITE))
            .insert(Message.raw(String.format(", +%.1f%%", stats.getSpellDamagePercent())).color(MessageColors.GRAY)));

        player.sendMessage(Message.empty()
            .insert(Message.raw("  Critical : ").color(MessageColors.GRAY))
            .insert(Message.raw(String.format("%.1f%% chance", stats.getCriticalChance())).color(MessageColors.WARNING))
            .insert(Message.raw(String.format(", %.0f%% multiplier", stats.getCriticalMultiplier())).color(MessageColors.GRAY)));

        if (stats.getMeleeDamagePercent() > 0 || stats.getProjectileDamagePercent() > 0) {
            player.sendMessage(Message.empty()
                .insert(Message.raw("  Melee : ").color(MessageColors.GRAY))
                .insert(Message.raw(String.format("+%.1f%%", stats.getMeleeDamagePercent())).color(MessageColors.WHITE))
                .insert(Message.raw("  Projectile : ").color(MessageColors.GRAY))
                .insert(Message.raw(String.format("+%.1f%%", stats.getProjectileDamagePercent())).color(MessageColors.WHITE)));
        }

        // ==================== DEFENSE SECTION ====================
        sendSectionHeader(player, "DEFENSE", MessageColors.INFO);

        player.sendMessage(Message.empty()
            .insert(Message.raw("  Armor : ").color(MessageColors.GRAY))
            .insert(Message.raw(String.format("%.1f", stats.getArmor())).color(MessageColors.WHITE))
            .insert(Message.raw(String.format(" (%.1f%% reduction vs 100 dmg)",
                calculateArmorReduction(stats.getArmor(), 100f))).color(MessageColors.GRAY)));

        if (stats.getFallDamageReduction() > 0) {
            player.sendMessage(Message.empty()
                .insert(Message.raw("  Fall Resist : ").color(MessageColors.GRAY))
                .insert(Message.raw(String.format("%.1f%%", stats.getFallDamageReduction())).color(MessageColors.SUCCESS)));
        }

        if (stats.getFireResistance() > 0 || stats.getWaterResistance() > 0 ||
            stats.getLightningResistance() > 0 || stats.getEarthResistance() > 0 ||
            stats.getWindResistance() > 0 || stats.getVoidResistance() > 0) {
            player.sendMessage(Message.empty()
                .insert(Message.raw("  Resists : ").color(MessageColors.GRAY))
                .insert(Message.raw(String.format("Fire %.0f%%", stats.getFireResistance())).color("#FF6600"))
                .insert(Message.raw(", ").color(MessageColors.GRAY))
                .insert(Message.raw(String.format("Water %.0f%%", stats.getWaterResistance())).color("#66CCFF"))
                .insert(Message.raw(", ").color(MessageColors.GRAY))
                .insert(Message.raw(String.format("Light %.0f%%", stats.getLightningResistance())).color("#FFFF00"))
                .insert(Message.raw(", ").color(MessageColors.GRAY))
                .insert(Message.raw(String.format("Earth %.0f%%", stats.getEarthResistance())).color("#8B4513"))
                .insert(Message.raw(", ").color(MessageColors.GRAY))
                .insert(Message.raw(String.format("Wind %.0f%%", stats.getWindResistance())).color("#AAFFAA"))
                .insert(Message.raw(", ").color(MessageColors.GRAY))
                .insert(Message.raw(String.format("Void %.0f%%", stats.getVoidResistance())).color(MessageColors.DARK_PURPLE)));
        }

        if (stats.getEvasion() > 0) {
            player.sendMessage(Message.empty()
                .insert(Message.raw("  Evasion : ").color(MessageColors.GRAY))
                .insert(Message.raw(String.format("%.1f", stats.getEvasion())).color(MessageColors.WHITE)));
        }

        // ==================== MOVEMENT SECTION ====================
        sendSectionHeader(player, "MOVEMENT", MessageColors.ORANGE);

        player.sendMessage(Message.empty()
            .insert(Message.raw("  Move Speed : ").color(MessageColors.GRAY))
            .insert(Message.raw(String.format("+%.0f%%", stats.getMovementSpeedPercent())).color(MessageColors.WHITE))
            .insert(Message.raw("  Sprint : ").color(MessageColors.GRAY))
            .insert(Message.raw(String.format("+%.0f%%", stats.getSprintSpeedBonus())).color(MessageColors.WHITE)));

        player.sendMessage(Message.empty()
            .insert(Message.raw("  Jump Force : ").color(MessageColors.GRAY))
            .insert(Message.raw(String.format("+%.0f%%", stats.getJumpForceBonus())).color(MessageColors.WHITE))
            .insert(Message.raw("  Climb : ").color(MessageColors.GRAY))
            .insert(Message.raw(String.format("+%.0f%%", stats.getClimbSpeedBonus())).color(MessageColors.WHITE)));

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
        @Nonnull String color,
        @Nonnull String regenSuffix
    ) {
        Message msg = Message.empty()
            .insert(Message.raw("  " + name + " : ").color(MessageColors.GRAY))
            .insert(Message.raw(String.format("%.0f", maxValue)).color(color));

        if (regenRate > 0) {
            msg.insert(Message.raw(String.format("  (+%.1f%s)", regenRate, regenSuffix)).color(MessageColors.GRAY));
        }

        player.sendMessage(msg);
    }

    private float calculateArmorReduction(float armor, float damage) {
        if (armor <= 0 || damage <= 0) return 0f;
        return (armor / (armor + 10f * damage)) * 100f;
    }
}
