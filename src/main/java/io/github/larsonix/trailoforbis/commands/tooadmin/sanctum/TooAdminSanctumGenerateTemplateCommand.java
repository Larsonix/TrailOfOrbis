package io.github.larsonix.trailoforbis.commands.tooadmin.sanctum;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.TrailOfOrbis;
import io.github.larsonix.trailoforbis.sanctum.GalaxySpiralLayoutMapper;
import io.github.larsonix.trailoforbis.skilltree.SkillTreeManager;
import io.github.larsonix.trailoforbis.skilltree.config.SkillNode;
import io.github.larsonix.trailoforbis.util.MessageColors;

import javax.annotation.Nonnull;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Generates a template YAML file with all node IDs and their current positions.
 *
 * <p>Usage: /tooadmin sanctum generatetemplate
 *
 * <p>Creates skill-tree-positions.yml with all nodes listed by region.
 * User can then edit coordinates directly and the system will use them.
 */
public final class TooAdminSanctumGenerateTemplateCommand extends AbstractPlayerCommand {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final String TEMPLATE_FILENAME = "skill-tree-positions.yml";

    private final TrailOfOrbis plugin;

    public TooAdminSanctumGenerateTemplateCommand(TrailOfOrbis plugin) {
        super("generatetemplate", "Generate a template with all node positions");
        this.addAliases("template");
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
        SkillTreeManager skillTreeManager = plugin.getSkillTreeManager();
        if (skillTreeManager == null) {
            sender.sendMessage(Message.raw("Skill tree system not initialized !").color(MessageColors.ERROR));
            return;
        }

        sender.sendMessage(Message.empty()
            .insert(Message.raw("[TooAdmin] ").color(MessageColors.GOLD))
            .insert(Message.raw("Generating position template...").color(MessageColors.WHITE)));

        try {
            Path outputPath = generateTemplate(skillTreeManager);

            sender.sendMessage(Message.empty()
                .insert(Message.raw("[TooAdmin] ").color(MessageColors.GOLD))
                .insert(Message.raw("Template generated !").color(MessageColors.SUCCESS)));
            sender.sendMessage(Message.empty()
                .insert(Message.raw("File : ").color(MessageColors.GRAY))
                .insert(Message.raw(outputPath.getFileName().toString()).color(MessageColors.WHITE)));
            sender.sendMessage(Message.raw("Edit the X/Y/Z values, then restart server to apply.").color(MessageColors.GRAY));

        } catch (IOException e) {
            LOGGER.atSevere().withCause(e).log("Failed to generate template");
            sender.sendMessage(Message.raw("Failed to generate - check server logs.").color(MessageColors.ERROR));
        }
    }

    private Path generateTemplate(SkillTreeManager skillTreeManager) throws IOException {
        Path configDir = plugin.getConfigManager().getConfigDir().resolve("config");
        Files.createDirectories(configDir);
        Path outputPath = configDir.resolve(TEMPLATE_FILENAME);

        // Get all nodes grouped by region
        Map<String, List<SkillNode>> nodesByRegion = new LinkedHashMap<>();

        for (SkillNode node : skillTreeManager.getAllNodes()) {
            String region = node.getSkillTreeRegion().name().toLowerCase();
            nodesByRegion.computeIfAbsent(region, k -> new ArrayList<>()).add(node);
        }

        // Sort nodes within each region by ID for consistency
        for (List<SkillNode> nodes : nodesByRegion.values()) {
            nodes.sort(Comparator.comparing(SkillNode::getId));
        }

        // Write YAML manually for nice formatting
        try (FileWriter writer = new FileWriter(outputPath.toFile())) {
            writer.write("# ══════════════════════════════════════════════════════════════════════════════\n");
            writer.write("# SKILL TREE NODE POSITIONS\n");
            writer.write("# ══════════════════════════════════════════════════════════════════════════════\n");
            writer.write("#\n");
            writer.write("# Edit the x, y, z coordinates for each node.\n");
            writer.write("# Coordinates are in world blocks relative to sanctum center (0, 65, 0).\n");
            writer.write("#\n");
            writer.write("# Tips:\n");
            writer.write("#   - Y = 65 is the base height (origin level)\n");
            writer.write("#   - X+ = East (Fire direction)\n");
            writer.write("#   - X- = West (Ice direction)\n");
            writer.write("#   - Z+ = South (Lightning direction)\n");
            writer.write("#   - Z- = North (Nature direction)\n");
            writer.write("#   - Y+ = Up (Void direction)\n");
            writer.write("#   - Y- = Down (Water direction)\n");
            writer.write("#\n");
            writer.write("# After editing, restart server or reload config to apply.\n");
            writer.write("#\n");
            writer.write("# ══════════════════════════════════════════════════════════════════════════════\n\n");

            writer.write("nodes:\n");

            // Define region order
            String[] regionOrder = {"core", "fire", "ice", "lightning", "nature", "void", "water"};

            int totalNodes = 0;

            for (String region : regionOrder) {
                List<SkillNode> nodes = nodesByRegion.get(region);
                if (nodes == null || nodes.isEmpty()) continue;

                writer.write("\n  # ─────────────────────────────────────────────────────────────────────────────\n");
                writer.write("  # " + region.toUpperCase() + " (" + nodes.size() + " nodes)\n");
                writer.write("  # ─────────────────────────────────────────────────────────────────────────────\n");

                for (SkillNode node : nodes) {
                    String nodeId = node.getId();

                    // Get current procedural position
                    Vector3d pos = GalaxySpiralLayoutMapper.toWorldPosition(node);

                    writer.write("\n  " + nodeId + ":\n");
                    writer.write(String.format("    x: %.1f\n", pos.x));
                    writer.write(String.format("    y: %.1f\n", pos.y));
                    writer.write(String.format("    z: %.1f\n", pos.z));

                    totalNodes++;
                }
            }

            LOGGER.atInfo().log("Generated template with %d nodes to %s", totalNodes, outputPath);
        }

        return outputPath;
    }
}
