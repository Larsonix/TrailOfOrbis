package io.github.larsonix.trailoforbis.maps.spawning;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.assetstore.AssetPack;
import com.hypixel.hytale.server.core.asset.AssetModule;
import com.hypixel.hytale.protocol.BlockMaterial;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.modules.prefabspawner.PrefabSpawnerBlock;
import com.hypixel.hytale.server.core.prefab.selection.buffer.PrefabBufferUtil;
import com.hypixel.hytale.server.core.prefab.selection.buffer.impl.IPrefabBuffer;
import com.hypixel.hytale.server.core.prefab.selection.buffer.impl.PrefabBuffer;
import com.hypixel.hytale.server.core.util.PrefabUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.section.FluidSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import io.github.larsonix.trailoforbis.maps.core.RealmBiomeType;
import io.github.larsonix.trailoforbis.maps.instance.RealmInstance;
import io.github.larsonix.trailoforbis.maps.templates.RealmTemplateRegistry;
import io.github.larsonix.trailoforbis.sanctum.SanctumBlockPlacer;
import io.github.larsonix.trailoforbis.util.TerrainUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Scatters individual vanilla prefab structures throughout realm arenas at runtime.
 * <p>
 * Many vanilla Hytale prefabs (Monuments, Fossils, Hotsprings) contain internal
 * PrefabSpawnerBlock references that crash WorldGen V2's PrefabLoader. This system
 * bypasses that limitation by using {@link PrefabUtil#paste} at runtime, the same
 * technique proven by {@link BossStructurePlacer}.
 * <p>
 * Separation of concerns:
 * <ul>
 *   <li><b>WorldGen Props</b> (biome JSON) — simple prefabs: rocks, trees, plants, arches</li>
 *   <li><b>RealmStructurePlacer</b> (this class) — complex prefabs: monuments, fossils, hotsprings</li>
 *   <li><b>BossStructurePlacer</b> — compound boss camps: center + satellites + dirt paths</li>
 * </ul>
 * <p>
 * Runs after chunks load but before mob spawning, so structures exist on terrain
 * before mobs attempt to spawn near them.
 */
public class RealmStructurePlacer {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    // ═══════════════════════════════════════════════════════════════════
    // STRUCTURE DEFINITION
    // ═══════════════════════════════════════════════════════════════════

    /**
     * A single structure type to scatter in a biome.
     *
     * @param dirPath           prefab directory path (e.g. "Monuments/Incidental/Sandstone/Tent")
     * @param countPer100Blocks density: structures per 100-block arena radius
     * @param minRadius         minimum arena radius to include this entry
     * @param removeWaterlog    if true, clear fluid from all blocks after paste (for water-designed prefabs on land)
     */
    private record StructureEntry(
        String dirPath,
        double countPer100Blocks,
        int minRadius,
        boolean removeWaterlog
    ) {
        /** Convenience constructor without waterlog flag (default: false). */
        StructureEntry(String dirPath, double countPer100Blocks, int minRadius) {
            this(dirPath, countPer100Blocks, minRadius, false);
        }
    }

    /**
     * Per-biome structure pools. Same pattern as {@link BossStructurePlacer}'s BOSS_CAMPS.
     * <p>
     * Paths here are ones that crash WorldGen V2 but work via PrefabUtil.paste():
     * Monuments, Fossils, Hotsprings — all contain PrefabSpawnerBlock child references.
     */
    private static final Map<RealmBiomeType, List<StructureEntry>> STRUCTURE_POOLS;

    static {
        var map = new EnumMap<RealmBiomeType, List<StructureEntry>>(RealmBiomeType.class);

        // ─── Forest: Trork beast-tribe territory ───
        // ALL faction structures placed at runtime for collision prevention.
        // Progressive: warnings → scout camps → watchtowers → operations → fortress.
        map.put(RealmBiomeType.FOREST, List.of(
            // ── TRORK BOUNDARY (R35+ — territorial warnings) ──
            new StructureEntry("Npc/Trork/Warning", 2.0, 35),

            // ── TRORK SCOUT CAMPS (R45+ — small outposts) ──
            new StructureEntry("Npc/Trork/Bonfire", 1.5, 45),
            new StructureEntry("Npc/Trork/Fireplace", 1.0, 45),
            new StructureEntry("Npc/Trork/Trap", 1.0, 45),
            new StructureEntry("Npc/Trork/Tent", 1.5, 45),
            new StructureEntry("Npc/Trork/Tier_1/Watchtower", 1.0, 45),

            // ── TRORK PERIMETER (R55+ — watchtowers) ──
            new StructureEntry("Npc/Trork/Tier_1/Watchtower", 1.0, 55),
            new StructureEntry("Npc/Trork/Tier_2/Watchtower", 0.5, 55),

            // ── TRORK OPERATIONS (R70+ — established camps) ──
            new StructureEntry("Npc/Trork/Warehouse", 1.0, 70),
            new StructureEntry("Npc/Trork/Tier_2/Store", 0.8, 70),
            new StructureEntry("Npc/Trork/Tier_2/Watchtower", 1.0, 70),
            new StructureEntry("Npc/Trork/Misc/Large", 0.8, 70),
            new StructureEntry("Npc/Trork/Resource/Lumber/Forest_Aspen", 1.0, 70),
            new StructureEntry("Npc/Trork/Resource/Lumber/Forest_Autumn", 0.8, 70),
            new StructureEntry("Npc/Trork/Resource/Lumber/Forest_Gully", 0.8, 70),
            new StructureEntry("Npc/Trork/Resource/Rock/Stone", 0.4, 70),
            new StructureEntry("Npc/Trork/Burrow", 0.5, 70),

            // ── TRORK FORTRESS (R100+ — stronghold) ──
            new StructureEntry("Npc/Trork/Tier_3/Watchtower", 0.8, 100),
            new StructureEntry("Npc/Trork/Tier_3/Store", 0.5, 100),
            new StructureEntry("Npc/Trork/Misc/Large", 1.0, 100)
        ));

        // ─── Desert: Scorched ruins, fossils, hotsprings, sandstone camps ───
        map.put(RealmBiomeType.DESERT, List.of(
            // Ancient remains — fossils emerging from sand
            new StructureEntry("Rock_Formations/Fossils/Small", 2.0, 35),
            new StructureEntry("Rock_Formations/Fossils/Large/Normal", 1.0, 55),
            new StructureEntry("Rock_Formations/Fossils/Large/Ruined", 1.0, 55),
            new StructureEntry("Rock_Formations/Fossils/Gigantic/Normal", 0.5, 100),
            new StructureEntry("Rock_Formations/Fossils/Gigantic/Ruined", 0.5, 100),

            // Thermal vents
            new StructureEntry("Rock_Formations/Hotsprings/Desert", 0.8, 35),

            // Scattered sandstone ruins — abandoned camps and tents
            new StructureEntry("Monuments/Incidental/Sandstone/Tent", 2.0, 35),
            new StructureEntry("Monuments/Incidental/Sandstone/Grass/Camp", 2.0, 45),
            new StructureEntry("Monuments/Incidental/Sandstone/Red/Camp", 1.5, 45),
            new StructureEntry("Monuments/Incidental/Sandstone/White/Camp", 1.0, 45),

            // Ancient water sources and dead wood
            new StructureEntry("Monuments/Incidental/Sandstone/Grass/Well", 1.0, 55),
            new StructureEntry("Monuments/Incidental/Sandstone/Red/Well", 0.8, 55),
            new StructureEntry("Monuments/Incidental/Sandstone/Grass/Drywood", 1.0, 55),

            // Desert outposts — Zone 2 encounter structures
            new StructureEntry("Monuments/Encounter/Zone2/Tier1/Outpost", 0.8, 70),
            new StructureEntry("Monuments/Incidental/Sandstone/Normal", 0.5, 70),
            new StructureEntry("Monuments/Incidental/Sandstone/Oasis/Rock_Camp", 0.5, 70),

            // Major ruins — treasure rooms on largest arenas
            new StructureEntry("Monuments/Incidental/Treasure_Rooms/Sandstone", 0.3, 100)
        ));

        // ─── Volcano: Thermal vents, ancient fossils, geological ruins ───
        // No NPC faction — elemental creatures (Golem_Firesteel, Skeleton_Burnt, Dragon_Fire).
        // Geological features intensify with arena size: vents → fossils → gigantic remains.
        map.put(RealmBiomeType.VOLCANO, List.of(
            // Thermal vents — active volcanic geology
            new StructureEntry("Rock_Formations/Hotsprings/Desert", 1.5, 35),

            // Ancient remains — creatures trapped in lava flows
            new StructureEntry("Rock_Formations/Fossils/Small", 2.0, 35),
            new StructureEntry("Rock_Formations/Fossils/Large/Normal", 1.0, 55),
            new StructureEntry("Rock_Formations/Fossils/Large/Ruined", 0.8, 55),

            // Massive fossils on largest arenas
            new StructureEntry("Rock_Formations/Fossils/Gigantic/Normal", 0.5, 90),
            new StructureEntry("Rock_Formations/Fossils/Gigantic/Ruined", 0.5, 100)
        ));

        // ─── Tundra: Outlander frost-tribe + Yeti + frozen ruins ───
        // ALL faction structures placed at runtime for collision prevention.
        map.put(RealmBiomeType.TUNDRA, List.of(
            // ── ANCIENT RUINS (frozen civilization) ──
            new StructureEntry("Monuments/Incidental/Shale/Ruins/Frozen", 1.5, 35),
            new StructureEntry("Monuments/Incidental/Shipwrecks/Cold", 0.5, 55, true),
            new StructureEntry("Monuments/Unique/Temple/Portal/Snowlands", 0.3, 70),
            new StructureEntry("Monuments/Unique/Outlander_Temple/Stage1/Monument/Ruined/Snow", 0.3, 100),
            new StructureEntry("Monuments/Unique/Outlander_Temple/Stage1/Monument/Hidden/Snow", 0.3, 100),

            // ── OUTLANDER BOUNDARY (R35+ — ice spikes and totems) ──
            new StructureEntry("Npc/Outlander/Spikes", 2.5, 35),
            new StructureEntry("Npc/Outlander/Totems", 0.8, 35),

            // ── OUTLANDER OUTPOSTS (R45+ — camps and shelters) ──
            new StructureEntry("Npc/Outlander/Braziers", 1.0, 45),
            new StructureEntry("Npc/Outlander/Houses/Tier0", 2.0, 45),
            new StructureEntry("Npc/Yeti/Camps", 1.5, 45),
            new StructureEntry("Npc/Outlander/Misc", 1.0, 45),

            // ── OUTLANDER PERIMETER (R55+ — watchtowers and forts) ──
            new StructureEntry("Npc/Outlander/Towers/Tier1", 1.0, 55),
            new StructureEntry("Npc/Outlander/Forts/Tier1", 0.8, 55),

            // ── OUTLANDER SETTLEMENT (R70+ — established civilization) ──
            new StructureEntry("Npc/Outlander/Houses/Tier1", 1.5, 70),
            new StructureEntry("Npc/Outlander/Houses/Tier2", 0.8, 70),
            new StructureEntry("Npc/Outlander/Forts/Tier2", 0.8, 70),
            new StructureEntry("Npc/Outlander/Towers/Tier2", 0.8, 70),
            new StructureEntry("Npc/Outlander/Boats/Large", 0.5, 70, true),

            // ── OUTLANDER STRONGHOLD (R100+ — fortified compound) ──
            new StructureEntry("Npc/Outlander/Houses/Tier3", 0.8, 100),
            new StructureEntry("Npc/Outlander/Forts/Tier3", 0.5, 100),
            new StructureEntry("Npc/Outlander/Towers/Tier3", 0.8, 100),
            new StructureEntry("Npc/Outlander/Gates/Tier3", 0.3, 100)
        ));

        // ─── Jungle: Feran beast-tribe territory + ancient Slothian ruins ───
        // ALL faction structures placed here (not in WorldGen) for proper collision
        // prevention via StructureBoundsRegistry. Same architecture as Desert.
        // Progressive discovery: boundary markers → scout camps → villages → fortress.
        map.put(RealmBiomeType.JUNGLE, List.of(
            // ── ANCIENT RUINS (pre-Feran Slothian civilization) ──
            new StructureEntry("Monuments/Incidental/Slothian/Land/Shrine", 1.5, 35),
            new StructureEntry("Monuments/Incidental/Slothian/Land/Well", 1.0, 35),
            new StructureEntry("Monuments/Incidental/Slothian/Land/Hunting", 1.5, 45),
            new StructureEntry("Monuments/Incidental/Slothian/Land/Merchant", 0.8, 45),
            new StructureEntry("Monuments/Incidental/Slothian/Land/Temple", 0.8, 55),
            new StructureEntry("Monuments/Incidental/Slothian/Biome/Trees/Banyan", 1.0, 55),
            new StructureEntry("Monuments/Incidental/Slothian/Biome/Trees/Jungle", 0.8, 55),
            new StructureEntry("Monuments/Incidental/Slothian/Biome/Rock_Pillar", 1.0, 70),
            new StructureEntry("Monuments/Incidental/Slothian/Biome/Trees/Crystal", 0.5, 70),
            new StructureEntry("Monuments/Incidental/Quartzite/Ruins", 0.5, 70),
            new StructureEntry("Monuments/Unique/Mage_Towers/Quartzite/Tier_2", 0.3, 90),
            new StructureEntry("Monuments/Unique/Mage_Towers/Quartzite/Tier_3", 0.2, 100),

            // ── FERAN BOUNDARY MARKERS (R35+ — first signs of the tribe) ──
            // Wall corners and entrances mark Feran territorial edges.
            new StructureEntry("Npc/Feran/Tier1/Corners", 2.5, 35),
            new StructureEntry("Npc/Feran/Tier1/Straight/Entrances", 0.8, 35),

            // ── FERAN SCOUT OUTPOSTS (R45+ — patrol camps) ──
            new StructureEntry("Npc/Feran/Tier1/Chieftain", 0.8, 45),
            new StructureEntry("Npc/Feran/Tier1/Straight/Normal", 1.5, 45),

            // ── FERAN PERIMETER (R55+ — defensive walls) ──
            new StructureEntry("Npc/Feran/Tier2/Wall", 1.5, 55),
            new StructureEntry("Npc/Feran/Tier2/Entrance", 0.8, 55),

            // ── FERAN VILLAGE (R70+ — established settlement) ──
            new StructureEntry("Npc/Feran/Tier2/Hut", 2.0, 70),
            new StructureEntry("Npc/Feran/Tier2/Chieftain", 0.3, 70),

            // ── FERAN FORTRESS (R100+ — Tier 3 elite structures) ──
            new StructureEntry("Npc/Feran/Tier3/Base", 0.5, 100),
            new StructureEntry("Npc/Feran/Tier3/Chieftain", 0.3, 100),
            new StructureEntry("Npc/Feran/Tier3/Huts", 0.8, 100),
            new StructureEntry("Npc/Feran/Tier3/Walls", 0.8, 100)
        ));

        // ─── CAVERNS: Scarak hive cave structures (pure stone, NO ice) ───
        // Spider/Boomshroom/Rat nodes = stone caves with webs/mushrooms/nests.
        // Crystal/Shale nodes removed: they contain ice blocks and icicles.
        map.put(RealmBiomeType.CAVERNS, List.of(
            // ── SPIDER CAVES (R35+ — stone with web decorations, fits Scarak theme) ──
            new StructureEntry("Cave/Nodes/Rock_Stone/Spider", 2.5, 35),
            new StructureEntry("Cave/Nodes/Rock_Stone/Rat", 1.5, 35),

            // ── DEEP SPIDER DENS (R55+ — larger spider web formations) ──
            new StructureEntry("Cave/Nodes/Rock_Stone/Spider_Deep", 2.0, 55),
            new StructureEntry("Cave/Nodes/Rock_Stone/Boomshroom", 1.0, 55),

            // ── DEEP HIVE (R70+ — mushroom + deep spider) ──
            new StructureEntry("Cave/Nodes/Rock_Stone/Boomshroom_Deep", 1.0, 70),
            new StructureEntry("Cave/Nodes/Rock_Stone/Spider_Deep", 1.5, 70),

            // ── DEEP CORE (R90+ — dense spider/mushroom colony) ──
            new StructureEntry("Cave/Nodes/Rock_Stone/Spider_Deep", 2.0, 90)
        ));

        // ─── FROZEN CRYPTS: Self-contained frozen ruins and encounter areas ───
        // Replaced Cave/Nodes/Rock_Shale — those are corridor-carving prefabs designed
        // for embedded-in-rock placement. These are freestanding structures that work
        // correctly in open arena environments with collision prevention.
        map.put(RealmBiomeType.FROZEN_CRYPTS, List.of(
            // ── OUTER PERIMETER (R35+ — ancient frozen ruins scattered through ice) ──
            new StructureEntry("Monuments/Incidental/Shale/Ruins/Frozen", 2.0, 35),
            new StructureEntry("Rock_Formations/Fossils/Small", 1.5, 35),

            // ── MID ZONE (R55+ — frozen encounter areas and pathways) ──
            new StructureEntry("Monuments/Encounter/Zone3/Tier2/Frozen/Outpost", 0.8, 55),
            new StructureEntry("Monuments/Encounter/Zone3/Tier2/Frozen/Pathways", 0.8, 55),
            new StructureEntry("Rock_Formations/Fossils/Large/Normal", 0.5, 55),

            // ── DEEP CRYPT (R70+ — frozen encounter chambers) ──
            new StructureEntry("Monuments/Encounter/Shale/Frozen", 1.0, 70),

            // ── INNER SANCTUM (R90+ — Outlander ice cave, frozen temple) ──
            new StructureEntry("Npc/Outlander/Ice_Caves", 0.3, 90),
            new StructureEntry("Monuments/Unique/Temple/Portal/Snowlands", 0.2, 100)
        ));

        // ─── SAND TOMBS: Self-contained sandstone monuments and treasure rooms ───
        // Replaced Cave/Nodes/Rock_Sandstone — same corridor-carving issue.
        // Uses Monuments/Incidental (self-contained buildings), Fossils (geological),
        // and Treasure_Rooms (tomb chambers) that look correct freestanding.
        map.put(RealmBiomeType.SAND_TOMBS, List.of(
            // ── OUTER CHAMBERS (R35+ — sandstone decorations and ancient fossils) ──
            new StructureEntry("Monuments/Incidental/Sandstone/!Normal", 2.0, 35),
            new StructureEntry("Rock_Formations/Fossils/Small", 1.5, 35),

            // ── MID TOMB (R45+ — ancient wells and camp remnants) ──
            new StructureEntry("Monuments/Incidental/Sandstone/!Wells/White", 1.0, 45),
            new StructureEntry("Monuments/Incidental/Sandstone/!Wells/Yellow", 0.8, 45),

            // ── INNER TOMB (R55+ — deeper monument ruins) ──
            new StructureEntry("Monuments/Incidental/Sandstone/Normal", 1.0, 55),
            new StructureEntry("Rock_Formations/Fossils/Large/Normal", 0.5, 55),

            // ── TREASURE VAULT (R70+ — burial treasures and oasis remnants) ──
            new StructureEntry("Monuments/Incidental/Treasure_Rooms/Sandstone", 0.5, 70),
            new StructureEntry("Monuments/Incidental/Sandstone/Oasis/Rock_Camp", 0.5, 70),

            // ── DEEP BURIAL CHAMBER (R100+ — largest ruined structures) ──
            new StructureEntry("Rock_Formations/Fossils/Large/Ruined", 0.3, 100)
        ));

        // ─── SWAMP: Abandoned Kweebec settlements + ancient ruins ───
        // Kweebec structures are all LEAF directories with no PrefabSpawnerBlocks.
        // Same Npc/ path architecture as Trork (Forest) and Outlander (Tundra).
        // Theme: a civilization that lost to the swamp — decaying farms, sunken houses.
        map.put(RealmBiomeType.SWAMP, List.of(
            // ── OUTER MARSH (R35+ — first signs of abandoned settlement) ──
            new StructureEntry("Npc/Kweebec/Swamp/Old_Farms", 2.0, 35),
            new StructureEntry("Monuments/Incidental/Grasslands/Houses/Swamp", 1.5, 35),

            // ── KWEEBEC OUTSKIRTS (R45+ — camps and guard posts) ──
            new StructureEntry("Npc/Kweebec/Swamp/Camps", 1.5, 45),
            new StructureEntry("Npc/Kweebec/Swamp/Guards", 1.5, 45),

            // ── KWEEBEC VILLAGE (R55+ — sunken dwellings and overgrown farms) ──
            new StructureEntry("Npc/Kweebec/Swamp/Houses", 2.5, 55),
            new StructureEntry("Npc/Kweebec/Swamp/Farms", 2.0, 55),

            // ── DEEP SWAMP (R70+ — larger architectural remnants) ──
            new StructureEntry("Rock_Formations/Fossils/Small", 1.0, 70),

            // ── SWAMP HEART (R90+ — complete village encounter) ──
            new StructureEntry("Monuments/Encounter/Zone4/Tier4/Village/Swamp/House", 0.8, 90),
            new StructureEntry("Monuments/Encounter/Zone4/Tier4/Village/Swamp/Watchtower", 0.3, 90)
        ));

        // ─── Beach: Sunken Corsair — coral city ruins, shipwrecks, pirate camps ───
        // 62 City_Oceans coral buildings (EXCLUSIVE — zero cross-biome usage) + 6 warm
        // shipwrecks + beach camps. Progressive discovery: scattered ruins → coral
        // settlements → massive capital building. All ocean-designed prefabs need
        // removeWaterlog=true to clear embedded fluid from their water-origin blocks.
        map.put(RealmBiomeType.BEACH, List.of(
            // ── OUTER SHORE (R35+ — scattered coral ruins and wrecked ships) ──
            new StructureEntry("Monuments/Encounter/City_Oceans/Buildings_Small/Coral", 2.5, 35, true),
            new StructureEntry("Monuments/Incidental/Shipwrecks/Tropical", 1.5, 35, true),
            new StructureEntry("Monuments/Incidental/Grasslands/Camps/Beach", 2.0, 35),
            new StructureEntry("Rock_Formations/Fossils/Small", 1.5, 35),

            // ── PIRATE OUTSKIRTS (R45+ — camps, beach houses, rafts) ──
            new StructureEntry("Monuments/Encounter/City_Oceans/Buildings_Small/Normal", 1.5, 45, true),
            new StructureEntry("Monuments/Incidental/Grasslands/Houses/Beach", 1.0, 45),
            new StructureEntry("Monuments/Incidental/Slothian/Water/Raft", 0.8, 45, true),

            // ── CORAL SETTLEMENT (R55+ — medium coral buildings, temperate wrecks) ──
            new StructureEntry("Monuments/Encounter/City_Oceans/Buildings_Medium/Coral", 1.5, 55, true),
            new StructureEntry("Monuments/Incidental/Shipwrecks/Temperate", 1.0, 55, true),

            // ── SUNKEN DISTRICT (R70+ — larger ruins, deep ocean wreck) ──
            new StructureEntry("Monuments/Encounter/City_Oceans/Buildings_Medium/Normal", 1.0, 70, true),
            new StructureEntry("Monuments/Incidental/Ocean/Shipwrecks", 0.5, 70, true),
            new StructureEntry("Rock_Formations/Fossils/Large/Normal", 0.5, 70),

            // ── CORSAIR CAPITAL (R100+ — massive coral capital and tower) ──
            new StructureEntry("Monuments/Encounter/City_Oceans/Buildings_Capital", 0.3, 100, true),
            new StructureEntry("Monuments/Encounter/City_Oceans/Buildings_Tower", 0.3, 100, true)
        ));

        STRUCTURE_POOLS = Collections.unmodifiableMap(map);
    }

    // ═══════════════════════════════════════════════════════════════════
    // PLACEMENT PARAMETERS
    // ═══════════════════════════════════════════════════════════════════

    /** Keep structures this far from arena walls (fraction of radius). */
    private static final double WALL_MARGIN = 0.85;

    /** Keep structures this far from player spawn (blocks).
     *  Buffer beyond SpawnZoneClearer.DEFAULT_CLEAR_RADIUS (10) so structures
     *  don't visually intrude into the cleared spawn zone. */
    private static final int SPAWN_EXCLUSION_RADIUS = 16;

    /** Minimum separation between placed structures (blocks). */
    private static final int MIN_SEPARATION = 15;

    /** Maximum placement attempts per structure before skipping. */
    private static final int MAX_ATTEMPTS = 12;

    /** Maximum recursion depth for nested spawner expansion. */
    private static final int MAX_EXPANSION_DEPTH = 3;

    private static final Rotation[] ROTATIONS = {
        Rotation.None, Rotation.Ninety, Rotation.OneEighty, Rotation.TwoSeventy
    };

    // ═══════════════════════════════════════════════════════════════════
    // STATE
    // ═══════════════════════════════════════════════════════════════════

    private final Map<String, PrefabBuffer> prefabCache = new ConcurrentHashMap<>();
    private final StructureBoundsRegistry boundsRegistry;

    public RealmStructurePlacer(@Nonnull StructureBoundsRegistry boundsRegistry) {
        this.boundsRegistry = boundsRegistry;
    }

    // ═══════════════════════════════════════════════════════════════════
    // PUBLIC API
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Scatter structures throughout the realm arena based on biome pools.
     * <p>
     * Must be called on the world thread after chunks are loaded.
     *
     * @param world the realm world (chunks must be loaded)
     * @param realm the realm instance (for biome, radius, spawn position)
     * @return number of structures successfully placed
     */
    public int placeStructures(@Nonnull World world, @Nonnull RealmInstance realm) {
        RealmBiomeType biome = realm.getBiome();
        List<StructureEntry> entries = STRUCTURE_POOLS.get(biome);
        if (entries == null || entries.isEmpty()) {
            return 0;
        }

        double arenaRadius = realm.getMapData().computeArenaRadius();
        var terrainMaterials = biome.getTerrainMaterials();
        int maxScanY = (int) RealmTemplateRegistry.getBaseYForBiome(biome) + 30;
        double maxPlacement = arenaRadius * WALL_MARGIN;

        Random random = ThreadLocalRandom.current();
        List<double[]> placedPositions = new ArrayList<>();
        int totalPlaced = 0;

        for (StructureEntry entry : entries) {
            if (arenaRadius < entry.minRadius()) {
                continue;
            }

            int count = (int) Math.round(entry.countPer100Blocks() * arenaRadius / 100.0);
            if (count <= 0) {
                continue;
            }

            for (int i = 0; i < count; i++) {
                boolean placed = false;

                for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
                    // Uniform circular disk sampling
                    double angle = random.nextDouble() * 2.0 * Math.PI;
                    double dist = Math.sqrt(random.nextDouble()) * maxPlacement;
                    double x = Math.cos(angle) * dist;
                    double z = Math.sin(angle) * dist;

                    // Check spawn exclusion
                    if (x * x + z * z < SPAWN_EXCLUSION_RADIUS * SPAWN_EXCLUSION_RADIUS) {
                        continue;
                    }

                    // Check minimum separation from already-placed structures
                    boolean tooClose = false;
                    for (double[] pos : placedPositions) {
                        double dx = x - pos[0];
                        double dz = z - pos[1];
                        if (dx * dx + dz * dz < MIN_SEPARATION * MIN_SEPARATION) {
                            tooClose = true;
                            break;
                        }
                    }
                    if (tooClose) {
                        continue;
                    }

                    // Find ground level using multi-point sampling to prevent floating
                    // on uneven terrain. Samples 3×3 grid and uses the lowest Y.
                    int blockX = (int) Math.floor(x);
                    int blockZ = (int) Math.floor(z);
                    int groundY = TerrainUtils.findStructureGroundLevel(
                        world, blockX, blockZ, maxScanY, terrainMaterials, 4);

                    // Place prefab (with overlap check + optional waterlog removal)
                    if (pastePrefabFromDirectory(world, entry.dirPath(), blockX, groundY, blockZ,
                            random, entry.removeWaterlog(), realm.getRealmId(), terrainMaterials)) {
                        placedPositions.add(new double[]{x, z});
                        totalPlaced++;
                        placed = true;
                        break;
                    }
                }

                if (!placed) {
                    LOGGER.atFine().log("Failed to place structure %s after %d attempts",
                        entry.dirPath(), MAX_ATTEMPTS);
                }
            }
        }

        if (totalPlaced > 0) {
            LOGGER.atInfo().log("Scattered %d structures for realm %s [%s R%.0f]",
                totalPlaced, realm.getRealmId().toString().substring(0, 8),
                biome.name(), arenaRadius);
        }

        return totalPlaced;
    }

    public void shutdown() {
        int cached = prefabCache.size();
        prefabCache.clear();
        LOGGER.atInfo().log("RealmStructurePlacer shut down — %d cached prefabs released", cached);
    }

    // ═══════════════════════════════════════════════════════════════════
    // PREFAB PLACEMENT (same technique as BossStructurePlacer)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Paste a random prefab from a directory path.
     * Scans all asset packs for .prefab.json files, picks one randomly,
     * pastes with random rotation, expands PrefabSpawnerBlocks, and optionally
     * removes waterlogged fluid from the pasted volume.
     *
     * @param removeWaterlog if true, scan pasted volume and clear fluid from all blocks
     */
    private boolean pastePrefabFromDirectory(@Nonnull World world, @Nonnull String dirPath,
                                              int x, int y, int z, @Nonnull Random random,
                                              boolean removeWaterlog, @Nonnull UUID realmId,
                                              @Nonnull java.util.Set<String> terrainMaterials) {
        try {
            List<Path> prefabFiles = findPrefabFilesInDirectory(dirPath);
            if (prefabFiles.isEmpty()) {
                LOGGER.atFine().log("No prefab files found for: %s", dirPath);
                return false;
            }

            Path chosen = prefabFiles.get(random.nextInt(prefabFiles.size()));
            PrefabBuffer buffer = loadPrefabFile(chosen);
            if (buffer == null) return false;

            Rotation rotation = ROTATIONS[random.nextInt(ROTATIONS.length)];

            IPrefabBuffer accessor = buffer.newAccess();
            Vector3i pastePos = new Vector3i(x, y, z);

            // Pre-paste overlap checks
            int halfW = Math.max(Math.abs(accessor.getMinX()), Math.abs(accessor.getMaxX()));
            int halfD = Math.max(Math.abs(accessor.getMinZ()), Math.abs(accessor.getMaxZ()));

            // Check 1: Registry overlap (other runtime structures)
            if (boundsRegistry.overlaps(realmId, x - halfW, z - halfD, x + halfW, z + halfD)) {
                LOGGER.atFine().log("Skipping %s at (%d,%d) — overlaps existing structure", dirPath, x, z);
                return false;
            }

            // Check 2: WorldGen prop occupancy (trees, rocks already placed)
            if (TerrainUtils.isAreaOccupied(world, x, z, halfW, halfD, y, terrainMaterials)) {
                LOGGER.atFine().log("Skipping %s at (%d,%d) — area occupied by props", dirPath, x, z);
                return false;
            }

            PrefabUtil.paste(
                accessor, world,
                pastePos,
                rotation,
                false,               // force=false: use placeBlock (respects existing blocks, skips air)
                random,
                0, false, false,
                false,               // loadEntities: NO vanilla NPCs
                world.getEntityStore().getStore()
            );

            // Register this structure's footprint for future overlap checks
            boundsRegistry.register(realmId, x - halfW, z - halfD, x + halfW, z + halfD);

            expandSpawnerBlocks(world, pastePos, accessor, random, 0);

            // Un-waterlog ALL solid blocks in the volume. Runs on EVERY paste, every
            // biome, every structure — no block should ever be waterlogged after placement.
            clearWaterloggedBlocks(world, pastePos, accessor);

            return true;

        } catch (Throwable t) {
            LOGGER.atFine().log("Failed to paste prefab from %s: %s", dirPath, t.getMessage());
            return false;
        }
    }

    /**
     * Find all .prefab.json files in a directory path across all loaded asset packs.
     */
    /**
     * Paste without overlap checks — used for spawner expansion children (internal sub-prefabs).
     */
    private boolean pastePrefabFromDirectoryUnchecked(@Nonnull World world, @Nonnull String dirPath,
                                                       int x, int y, int z, @Nonnull Random random) {
        try {
            List<Path> prefabFiles = findPrefabFilesInDirectory(dirPath);
            if (prefabFiles.isEmpty()) return false;

            Path chosen = prefabFiles.get(random.nextInt(prefabFiles.size()));
            PrefabBuffer buffer = loadPrefabFile(chosen);
            if (buffer == null) return false;

            Rotation rotation = ROTATIONS[random.nextInt(ROTATIONS.length)];
            IPrefabBuffer accessor = buffer.newAccess();
            Vector3i pastePos = new Vector3i(x, y, z);

            PrefabUtil.paste(accessor, world, pastePos, rotation, false, random,
                0, false, false, false, world.getEntityStore().getStore());
            clearWaterloggedBlocks(world, pastePos, accessor);
            return true;
        } catch (Throwable e) {
            LOGGER.atFine().log("Failed to paste unchecked prefab %s at (%d,%d,%d): %s", dirPath, x, y, z, e.getMessage());
            return false;
        }
    }

    @Nonnull
    private List<Path> findPrefabFilesInDirectory(@Nonnull String dirPath) {
        List<Path> found = new ArrayList<>();
        try {
            List<AssetPack> packs = AssetModule.get().getAssetPacks();
            for (int i = packs.size() - 1; i >= 0; --i) {
                Path prefabsDir = packs.get(i).getRoot()
                    .resolve("Server").resolve("Prefabs").resolve(dirPath);
                if (!Files.isDirectory(prefabsDir)) continue;

                try (var stream = Files.list(prefabsDir)) {
                    stream.filter(p -> {
                        String name = p.getFileName().toString();
                        return name.endsWith(".prefab.json") || name.endsWith(".prefab.json.lpf");
                    }).forEach(found::add);
                }

                if (!found.isEmpty()) break; // First pack with files wins (newest-first)
            }
        } catch (Throwable t) {
            LOGGER.atFine().log("Error scanning directory %s: %s", dirPath, t.getMessage());
        }
        return found;
    }

    @Nullable
    private PrefabBuffer loadPrefabFile(@Nonnull Path path) {
        String key = path.toString();
        try {
            return prefabCache.computeIfAbsent(key, k -> {
                try {
                    Path loadPath = path;
                    if (k.endsWith(".lpf")) {
                        String jsonName = k.substring(0, k.length() - 4);
                        loadPath = Path.of(jsonName);
                    }
                    return PrefabBufferUtil.loadBuffer(loadPath);
                } catch (Throwable e) {
                    LOGGER.atFine().log("Failed to load prefab: %s", k);
                    return null;
                }
            });
        } catch (Throwable e) {
            LOGGER.atFine().log("Failed to load prefab %s: %s", key, e.getMessage());
            return null;
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // PREFAB SPAWNER EXPANSION
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Scan a pasted prefab volume for PrefabSpawnerBlock components and expand
     * them into child prefabs. Replicates vanilla's RecursivePrefabLoader at runtime.
     */
    private int expandSpawnerBlocks(@Nonnull World world, @Nonnull Vector3i pastePos,
                                     @Nonnull IPrefabBuffer buffer, @Nonnull Random random,
                                     int depth) {
        if (depth >= MAX_EXPANSION_DEPTH) {
            return 0;
        }

        int minX = buffer.getMinX() + pastePos.x;
        int maxX = buffer.getMaxX() + pastePos.x;
        int minY = buffer.getMinY() + pastePos.y;
        int maxY = buffer.getMaxY() + pastePos.y;
        int minZ = buffer.getMinZ() + pastePos.z;
        int maxZ = buffer.getMaxZ() + pastePos.z;

        SanctumBlockPlacer blockPlacer = new SanctumBlockPlacer();
        BlockType airBlock = BlockType.getAssetMap().getAsset("Empty");
        int expanded = 0;

        for (int wx = minX; wx <= maxX; wx++) {
            for (int wz = minZ; wz <= maxZ; wz++) {
                WorldChunk chunk = world.getChunkIfInMemory(
                    ChunkUtil.indexChunkFromBlock(wx, wz));
                if (chunk == null) continue;

                for (int wy = minY; wy <= maxY; wy++) {
                    try {
                        Ref<ChunkStore> blockRef = chunk.getBlockComponentEntity(wx, wy, wz);
                        if (blockRef == null) continue;

                        PrefabSpawnerBlock spawner = blockRef.getStore()
                            .getComponent(blockRef, PrefabSpawnerBlock.getComponentType());
                        if (spawner == null) continue;

                        String dotPath = spawner.getPrefabPath();
                        if (dotPath == null || dotPath.isEmpty()) {
                            blockPlacer.placeBlock(world, wx, wy, wz, airBlock);
                            continue;
                        }

                        String slashPath = dotPath.replace('.', '/');

                        // Child prefabs from spawner expansion: skip overlap checks
                        // (they're internal parts of an already-validated parent structure)
                        boolean childPlaced = pastePrefabFromDirectoryUnchecked(
                            world, slashPath, wx, wy, wz, random);

                        if (childPlaced) {
                            expanded++;
                            List<Path> childFiles = findPrefabFilesInDirectory(slashPath);
                            if (!childFiles.isEmpty()) {
                                PrefabBuffer childBuffer = loadPrefabFile(
                                    childFiles.get(random.nextInt(childFiles.size())));
                                if (childBuffer != null) {
                                    expanded += expandSpawnerBlocks(world,
                                        new Vector3i(wx, wy, wz),
                                        childBuffer.newAccess(), random, depth + 1);
                                }
                            }
                        }

                        blockPlacer.placeBlock(world, wx, wy, wz, airBlock);

                    } catch (Throwable t) {
                        LOGGER.atFine().log("Error expanding spawner at (%d,%d,%d): %s",
                            wx, wy, wz, t.getMessage());
                    }
                }
            }
        }

        if (expanded > 0) {
            LOGGER.atFine().log("Expanded %d PrefabSpawner blocks (depth=%d)", expanded, depth);
        }
        return expanded;
    }

    // ═══════════════════════════════════════════════════════════════════
    // WATERLOG REMOVAL
    // ═══════════════════════════════════════════════════════════════════

    private static final BlockType EMPTY_BLOCK = BlockType.getAssetMap().getAsset("Empty");

    /**
     * Clear fluid from every position that has a non-Empty block AND fluid.
     * <p>
     * Waterlogged = any named block (not "Empty") + fluid at the same position.
     * Natural water = the literal "Empty" air block + fluid.
     * <p>
     * Does NOT use BlockMaterial.Solid — model/transparent blocks like barnacles
     * and seaweed report as non-Solid despite being visible placed blocks.
     * Instead checks: is this block literally "Empty" (air)? If not, it's a
     * real block that shouldn't have fluid.
     */
    private int clearWaterloggedBlocks(@Nonnull World world, @Nonnull Vector3i pastePos,
                                        @Nonnull IPrefabBuffer buffer) {
        int half = Math.max(
            Math.max(Math.abs(buffer.getMinX()), Math.abs(buffer.getMaxX())),
            Math.max(Math.abs(buffer.getMinZ()), Math.abs(buffer.getMaxZ()))
        ) + 20;
        int halfY = Math.max(Math.abs(buffer.getMinY()), Math.abs(buffer.getMaxY())) + 20;

        var chunkStore = world.getChunkStore();
        int cleared = 0;

        for (int wx = pastePos.x - half; wx <= pastePos.x + half; wx++) {
            for (int wz = pastePos.z - half; wz <= pastePos.z + half; wz++) {
                WorldChunk chunk = world.getChunkIfInMemory(
                    ChunkUtil.indexChunkFromBlock(wx, wz));
                if (chunk == null) continue;

                for (int wy = pastePos.y - halfY; wy <= pastePos.y + halfY; wy++) {
                    try {
                        int fluidId = chunk.getFluidId(wx, wy, wz);
                        if (fluidId == 0) continue;

                        // Only keep fluid at positions where the block is literally Empty (air).
                        // Any other block (solid, model, transparent — barnacles, seaweed,
                        // stairs, ropes, fences, ANY placed block) should not have fluid.
                        BlockType block = world.getBlockType(wx, wy, wz);
                        if (block == null || block == EMPTY_BLOCK) {
                            continue; // Air + fluid = natural water → keep
                        }

                        Ref<ChunkStore> section = chunkStore.getChunkSectionReference(
                            ChunkUtil.chunkCoordinate(wx),
                            ChunkUtil.chunkCoordinate(wy),
                            ChunkUtil.chunkCoordinate(wz));
                        if (section == null || !section.isValid()) continue;

                        FluidSection fluidSection = section.getStore()
                            .getComponent(section, FluidSection.getComponentType());
                        if (fluidSection == null) continue;

                        fluidSection.setFluid(wx, wy, wz, 0, (byte) 0);
                        cleared++;
                    } catch (Throwable e) {
                        LOGGER.atFine().log("Failed to clear waterlogged block at (%d,%d,%d): %s", wx, wy, wz, e.getMessage());
                    }
                }
            }
        }

        if (cleared > 0) {
            LOGGER.atInfo().log("Cleared %d waterlogged blocks at (%d,%d,%d)",
                cleared, pastePos.x, pastePos.y, pastePos.z);
        }
        return cleared;
    }
}
