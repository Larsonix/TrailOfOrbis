package io.github.larsonix.trailoforbis.commands.shortcuts;

import com.hypixel.hytale.builtin.instances.InstancesPlugin;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import io.github.larsonix.trailoforbis.commands.base.OpenPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.TrailOfOrbis;
import io.github.larsonix.trailoforbis.maps.RealmsManager;
import io.github.larsonix.trailoforbis.sanctum.SkillSanctumInstance;
import io.github.larsonix.trailoforbis.sanctum.SkillSanctumManager;
import io.github.larsonix.trailoforbis.util.MessageColors;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * Shortcut command that toggles the Skill Sanctum.
 *
 * <p>If the player is outside the sanctum, enters it.
 * If the player is inside the sanctum, exits it.
 *
 * <p>For subcommands (allocate, deallocate, respec, info, list), use /too skilltree.
 *
 * <p>Usage: /skilltree, /st, /skills, /tree
 */
public final class SkillTreeShortcutCommand extends OpenPlayerCommand {

    private final TrailOfOrbis plugin;

    public SkillTreeShortcutCommand(TrailOfOrbis plugin) {
        super("skilltree", "Toggle the Skill Sanctum (use /too skilltree for subcommands)");
        this.addAliases("st", "skills", "tree");
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
        SkillSanctumManager sanctumManager = plugin.getSkillSanctumManager();
        if (sanctumManager == null || !sanctumManager.isEnabled()) {
            player.sendMessage(Message.raw("Skill Sanctum is not available.").color(MessageColors.ERROR));
            return;
        }

        // Block access while in a combat realm
        RealmsManager realmsManager = plugin.getRealmsManager();
        if (realmsManager != null && realmsManager.isPlayerInCombatRealm(player)) {
            player.sendMessage(Message.raw("Cannot access Skill Sanctum while in a combat realm !").color(MessageColors.ERROR));
            return;
        }

        UUID playerId = player.getUuid();

        // Use world-based detection (handles server restart/relog correctly)
        boolean inSanctumWorld = sanctumManager.isPlayerInSanctumWorld(player);
        // In-memory tracking (only valid if we created the sanctum this session)
        boolean hasActiveInstance = sanctumManager.hasActiveSanctum(playerId);

        if (inSanctumWorld) {
            // Player is physically in sanctum world - exit them
            if (hasActiveInstance) {
                // Normal case: we have tracking, use closeSanctum
                sanctumManager.closeSanctum(playerId);
                player.sendMessage(Message.raw("Exited Skill Sanctum.").color(MessageColors.SUCCESS));
            } else {
                // Orphaned case: in sanctum world but no tracked instance
                // This happens after server restart or if tracking got out of sync
                handleOrphanedSanctumExit(player, ref, store);
            }
        } else {
            // Player is NOT in sanctum - enter it
            if (hasActiveInstance) {
                // Has an active instance but not physically in it - teleport back
                SkillSanctumInstance instance = sanctumManager.getSanctumInstance(playerId);
                if (instance != null && instance.teleportPlayerToSpawn(player)) {
                    player.sendMessage(Message.raw("Returning to your Skill Sanctum...").color(MessageColors.INFO));
                } else {
                    // Instance exists but can't teleport - clean up and reopen
                    sanctumManager.closeSanctum(playerId);
                    openNewSanctum(sanctumManager, player);
                }
            } else {
                // No instance, not in sanctum - open a new one
                openNewSanctum(sanctumManager, player);
            }
        }
    }

    /**
     * Opens a new skill sanctum for the player.
     */
    private void openNewSanctum(SkillSanctumManager sanctumManager, PlayerRef player) {
        player.sendMessage(Message.raw("Opening Skill Sanctum...").color(MessageColors.INFO));
        sanctumManager.openSanctum(player).thenAccept(success -> {
            if (success) {
                player.sendMessage(Message.raw("Welcome to your Skill Sanctum !").color(MessageColors.GOLD));
            } else {
                player.sendMessage(Message.raw("Failed to open Skill Sanctum.").color(MessageColors.ERROR));
            }
        });
    }

    /**
     * Handles the case where a player is in a sanctum world but we don't have
     * an active instance tracked for them (orphaned sanctum).
     *
     * <p>This can happen after a server restart or if tracking got out of sync.
     * We use InstancesPlugin.exitInstance() to properly exit the player.
     */
    private void handleOrphanedSanctumExit(PlayerRef player, Ref<EntityStore> ref, Store<EntityStore> store) {
        player.sendMessage(Message.raw("Exiting Skill Sanctum...").color(MessageColors.INFO));
        InstancesPlugin.exitInstance(ref, store);
        player.sendMessage(Message.raw("Exited Skill Sanctum.").color(MessageColors.SUCCESS));
    }
}
