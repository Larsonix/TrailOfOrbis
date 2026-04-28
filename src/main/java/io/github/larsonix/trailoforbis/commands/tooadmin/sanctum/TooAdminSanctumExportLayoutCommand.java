package io.github.larsonix.trailoforbis.commands.tooadmin.sanctum;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.TrailOfOrbis;
import io.github.larsonix.trailoforbis.sanctum.SkillSanctumInstance;
import io.github.larsonix.trailoforbis.sanctum.SkillSanctumManager;
import io.github.larsonix.trailoforbis.sanctum.components.SkillNodeComponent;
import io.github.larsonix.trailoforbis.util.MessageColors;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import javax.annotation.Nonnull;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Exports the current positions of all nodes in the active sanctum to a YAML file.
 *
 * <p>Usage: /tooadmin sanctum exportlayout
 *
 * <p>This reads the world positions of all node entities and saves them to
 * exported-node-positions.yml in the config directory. The SkillTreeLayout
 * will then use these positions instead of procedural generation.
 */
public final class TooAdminSanctumExportLayoutCommand extends AbstractPlayerCommand {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final String EXPORT_FILENAME = "exported-node-positions.yml";

    private final TrailOfOrbis plugin;

    public TooAdminSanctumExportLayoutCommand(TrailOfOrbis plugin) {
        super("exportlayout", "Export current node positions to YAML");
        this.addAliases("export");
        this.plugin = plugin;
    }

    @Override
    protected void execute(
        @Nonnull CommandContext context,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull PlayerRef sender,
        @Nonnull World world
    ) {
        SkillSanctumManager sanctumManager = plugin.getSkillSanctumManager();
        if (sanctumManager == null || !sanctumManager.isEnabled()) {
            sender.sendMessage(Message.raw("Skill Sanctum system is not enabled !").color(MessageColors.ERROR));
            return;
        }

        UUID playerId = sender.getUuid();

        // Check if in a sanctum
        if (!sanctumManager.hasActiveSanctum(playerId)) {
            sender.sendMessage(Message.raw("You must be in a sanctum to export layout !").color(MessageColors.ERROR));
            sender.sendMessage(Message.raw("Use /tooadmin sanctum editlayout to open the editor.").color(MessageColors.GRAY));
            return;
        }

        SkillSanctumInstance instance = sanctumManager.getSanctumInstance(playerId);
        if (instance == null) {
            sender.sendMessage(Message.raw("Could not find your sanctum instance.").color(MessageColors.ERROR));
            return;
        }

        World sanctumWorld = instance.getSanctumWorld();
        if (sanctumWorld == null || !sanctumWorld.isAlive()) {
            sender.sendMessage(Message.raw("Sanctum world is not available.").color(MessageColors.ERROR));
            return;
        }

        sender.sendMessage(Message.empty()
            .insert(Message.raw("[TooAdmin] ").color(MessageColors.GOLD))
            .insert(Message.raw("Exporting node positions...").color(MessageColors.WHITE)));

        // Execute on sanctum world thread to safely read entity positions
        sanctumWorld.execute(() -> {
            try {
                Map<String, NodeExportData> exportedNodes = collectNodePositions(instance, sanctumWorld);

                if (exportedNodes.isEmpty()) {
                    sender.sendMessage(Message.raw("No nodes found to export !").color(MessageColors.ERROR));
                    return;
                }

                // Save to YAML (on separate thread to avoid blocking world thread)
                CompletableFuture.runAsync(() -> {
                    try {
                        Path exportPath = saveToYaml(exportedNodes);
                        sender.sendMessage(Message.empty()
                            .insert(Message.raw("[TooAdmin] ").color(MessageColors.GOLD))
                            .insert(Message.raw("Exported ").color(MessageColors.SUCCESS))
                            .insert(Message.raw(String.valueOf(exportedNodes.size())).color(MessageColors.WHITE))
                            .insert(Message.raw(" node positions !").color(MessageColors.SUCCESS)));
                        sender.sendMessage(Message.empty()
                            .insert(Message.raw("Saved to : ").color(MessageColors.GRAY))
                            .insert(Message.raw(exportPath.getFileName().toString()).color(MessageColors.WHITE)));
                        sender.sendMessage(Message.raw("Restart server or reload config to apply new layout.").color(MessageColors.GRAY));

                        LOGGER.atInfo().log("Exported %d node positions to %s", exportedNodes.size(), exportPath);
                    } catch (IOException e) {
                        LOGGER.atSevere().withCause(e).log("Failed to save exported positions");
                        sender.sendMessage(Message.raw("Failed to save — check server logs.").color(MessageColors.ERROR));
                    }
                });

            } catch (Exception e) {
                LOGGER.atSevere().withCause(e).log("Error collecting node positions");
                sender.sendMessage(Message.raw("Export failed — check server logs.").color(MessageColors.ERROR));
            }
        });
    }

    /**
     * Collects positions of all node entities in the sanctum.
     */
    private Map<String, NodeExportData> collectNodePositions(
            @Nonnull SkillSanctumInstance instance,
            @Nonnull World sanctumWorld) {

        Map<String, NodeExportData> result = new LinkedHashMap<>();
        Store<EntityStore> store = sanctumWorld.getEntityStore().getStore();

        ComponentType<EntityStore, SkillNodeComponent> nodeComponentType = plugin.getSkillNodeComponentType();
        if (nodeComponentType == null) {
            LOGGER.atWarning().log("SkillNodeComponent type not registered");
            return result;
        }

        // Iterate through all tracked node entities
        Set<String> nodeIds = instance.getSpawnedNodeIds();
        for (String nodeId : nodeIds) {
            Ref<EntityStore> entityRef = instance.getNodeEntity(nodeId);
            if (entityRef == null || !entityRef.isValid()) {
                continue;
            }

            // Get transform component for position
            TransformComponent transform = store.getComponent(entityRef, TransformComponent.getComponentType());
            if (transform == null) {
                continue;
            }

            Vector3d pos = transform.getPosition();

            // Get node component for metadata
            SkillNodeComponent nodeComponent = store.getComponent(entityRef, nodeComponentType);
            String region = nodeComponent != null ? nodeComponent.getRegion().name() : "UNKNOWN";

            result.put(nodeId, new NodeExportData(
                pos.x,
                pos.y,
                pos.z,
                region
            ));
        }

        return result;
    }

    /**
     * Saves exported positions to YAML file.
     */
    private Path saveToYaml(Map<String, NodeExportData> nodes) throws IOException {
        Path configDir = plugin.getConfigManager().getConfigDir().resolve("config");
        Files.createDirectories(configDir);
        Path exportPath = configDir.resolve(EXPORT_FILENAME);

        // Build YAML structure
        Map<String, Object> yamlRoot = new LinkedHashMap<>();

        // Add header comment info
        yamlRoot.put("_comment", "Exported node positions - edit and reload to apply");
        yamlRoot.put("_exportedAt", System.currentTimeMillis());
        yamlRoot.put("_nodeCount", nodes.size());

        // Group by region for organization
        Map<String, Map<String, Map<String, Object>>> byRegion = new LinkedHashMap<>();
        for (Map.Entry<String, NodeExportData> entry : nodes.entrySet()) {
            String nodeId = entry.getKey();
            NodeExportData data = entry.getValue();

            String region = data.region.toLowerCase();
            byRegion.computeIfAbsent(region, k -> new LinkedHashMap<>())
                .put(nodeId, createNodeMap(data));
        }

        yamlRoot.put("nodes", byRegion);

        // Write with nice formatting
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        options.setIndent(2);
        Yaml yaml = new Yaml(options);

        try (FileWriter writer = new FileWriter(exportPath.toFile())) {
            writer.write("# ══════════════════════════════════════════════════════════════════════════════\n");
            writer.write("# EXPORTED SKILL TREE NODE POSITIONS\n");
            writer.write("# ══════════════════════════════════════════════════════════════════════════════\n");
            writer.write("#\n");
            writer.write("# This file contains manually positioned node coordinates.\n");
            writer.write("# If this file exists, SkillTreeLayout will use these positions instead of\n");
            writer.write("# procedural generation.\n");
            writer.write("#\n");
            writer.write("# To edit: Open sanctum with /tooadmin sanctum editlayout, move nodes, then export.\n");
            writer.write("# To reset: Delete this file and reload config.\n");
            writer.write("#\n");
            writer.write("# ══════════════════════════════════════════════════════════════════════════════\n\n");
            yaml.dump(yamlRoot, writer);
        }

        return exportPath;
    }

    private Map<String, Object> createNodeMap(NodeExportData data) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("x", Math.round(data.x * 100.0) / 100.0);  // 2 decimal places
        map.put("y", Math.round(data.y * 100.0) / 100.0);
        map.put("z", Math.round(data.z * 100.0) / 100.0);
        return map;
    }

    /**
     * Internal data class for exported node positions.
     */
    private record NodeExportData(double x, double y, double z, String region) {}
}
