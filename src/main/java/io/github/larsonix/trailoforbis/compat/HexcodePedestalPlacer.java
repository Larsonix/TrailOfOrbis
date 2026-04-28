package io.github.larsonix.trailoforbis.compat;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import io.github.larsonix.trailoforbis.maps.core.RealmBiomeType;
import io.github.larsonix.trailoforbis.maps.instance.RealmInstance;
import io.github.larsonix.trailoforbis.maps.templates.RealmTemplateRegistry;
import io.github.larsonix.trailoforbis.util.TerrainUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Places Hexcode pedestal blocks in realm arenas when the Hexcode mod is detected.
 *
 * <p>Pedestals allow players to modify spells mid-realm-run at fixed locations.
 * The pedestal tier scales with realm difficulty. Placement uses Hytale's block API
 * (not prefab paste) since pedestals are single blocks with embedded
 * {@code PedestalBlockComponent} in their BlockType definition.
 *
 * <p>No compile-time dependency on Hexcode — block IDs are config-driven strings.
 */
public class HexcodePedestalPlacer {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /** Maximum placement attempts before giving up. */
    private static final int MAX_ATTEMPTS = 20;

    private final HexcodePedestalConfig config;

    public HexcodePedestalPlacer(@Nonnull HexcodePedestalConfig config) {
        this.config = config;
    }

    /**
     * Attempts to place a pedestal in the realm arena.
     *
     * <p>Must be called on the world thread after chunks are loaded.
     *
     * @param world The realm world
     * @param realm The realm instance
     * @return true if a pedestal was placed
     */
    public boolean tryPlacePedestal(@Nonnull World world, @Nonnull RealmInstance realm) {
        if (!config.isEnabled() || !HexcodeCompat.isLoaded()) {
            return false;
        }

        // Roll spawn chance
        float roll = ThreadLocalRandom.current().nextFloat() * 100f;
        if (roll >= config.getSpawn_chance()) {
            LOGGER.atFine().log("[PedestalPlacer] Spawn chance failed: %.1f >= %.1f",
                roll, config.getSpawn_chance());
            return false;
        }

        // Derive tier from realm level (1-20: T1, 21-40: T2, 41-60: T3, 61-80: T4, 81+: T5)
        int realmLevel = realm.getMapData().level();
        int realmTier = Math.min(5, Math.max(1, (realmLevel - 1) / 20 + 1));
        String blockId = config.getBlockIdForTier(realmTier);
        if (blockId == null) {
            LOGGER.atWarning().log("[PedestalPlacer] No pedestal block configured for realm tier %d", realmTier);
            return false;
        }

        // Resolve BlockType from asset map
        BlockType blockType = BlockType.getAssetMap().getAsset(blockId);
        if (blockType == null) {
            LOGGER.atFine().log("[PedestalPlacer] Block type '%s' not found in asset map — Hexcode asset pack may be missing",
                blockId);
            return false;
        }

        // Find a valid placement position
        double arenaRadius = realm.getMapData().computeArenaRadius();
        RealmBiomeType biome = realm.getBiome();
        int maxScanY = (int) RealmTemplateRegistry.getBaseYForBiome(biome) + 30;
        var terrainMaterials = biome.getTerrainMaterials();

        float minDist = config.getMin_distance_from_spawn();
        float maxDist = Math.min(config.getMax_distance_from_spawn(), (float) arenaRadius * 0.85f);

        if (maxDist <= minDist) {
            LOGGER.atFine().log("[PedestalPlacer] Arena too small for pedestal (radius=%.1f, minDist=%.0f)",
                arenaRadius, minDist);
            return false;
        }

        var random = ThreadLocalRandom.current();
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            // Random position within distance ring
            double angle = random.nextDouble() * 2.0 * Math.PI;
            double dist = minDist + random.nextDouble() * (maxDist - minDist);
            int blockX = (int) Math.floor(Math.cos(angle) * dist);
            int blockZ = (int) Math.floor(Math.sin(angle) * dist);

            // Find ground level (returns Y of first clear block above terrain)
            int groundY = TerrainUtils.findGroundLevel(world, blockX, blockZ, maxScanY, terrainMaterials);
            if (groundY < 0) {
                continue;
            }

            // findGroundLevel returns the clear block above terrain — place pedestal there
            int placeY = groundY;

            // Ensure chunk is loaded
            WorldChunk chunk = world.getChunkIfInMemory(
                ChunkUtil.indexChunkFromBlock(blockX, blockZ));
            if (chunk == null) {
                continue;
            }

            // Place the pedestal block
            try {
                chunk.setBlock(blockX, placeY, blockZ, blockType.getId());
                LOGGER.atInfo().log("[PedestalPlacer] Placed %s at (%d, %d, %d) in realm %s [tier %d]",
                    blockId, blockX, placeY, blockZ,
                    realm.getRealmId().toString().substring(0, 8), realmTier);
                return true;
            } catch (Exception e) {
                LOGGER.atFine().log("[PedestalPlacer] Failed to place block at (%d, %d, %d): %s",
                    blockX, placeY, blockZ, e.getMessage());
            }
        }

        LOGGER.atFine().log("[PedestalPlacer] Failed to place pedestal after %d attempts in realm %s",
            MAX_ATTEMPTS, realm.getRealmId().toString().substring(0, 8));
        return false;
    }

    /**
     * Config for pedestal placement in realms.
     * Designed for SnakeYAML deserialization.
     */
    public static class HexcodePedestalConfig {

        private boolean enabled = true;
        private float spawn_chance = 15f;
        private Map<Integer, String> tier_by_realm_tier = new LinkedHashMap<>();
        private float min_distance_from_spawn = 20f;
        private float max_distance_from_spawn = 40f;

        public HexcodePedestalConfig() {
            initializeDefaults();
        }

        private void initializeDefaults() {
            if (tier_by_realm_tier.isEmpty()) {
                tier_by_realm_tier.put(1, "Hexcode_Pedestal_Thorium");
                tier_by_realm_tier.put(2, "Hexcode_Pedestal_Fire");
                tier_by_realm_tier.put(3, "Hexcode_Pedestal_Arcane");
                tier_by_realm_tier.put(4, "Hexcode_Pedestal_Void");
                tier_by_realm_tier.put(5, "Hexcode_Pedestal_Void");
            }
        }

        /**
         * Gets the pedestal block ID for the given realm tier.
         * Falls back to the highest configured tier if the exact tier is not mapped.
         */
        @Nullable
        public String getBlockIdForTier(int tier) {
            String blockId = tier_by_realm_tier.get(tier);
            if (blockId != null) {
                return blockId;
            }
            // Fall back to nearest lower tier
            for (int t = tier - 1; t >= 1; t--) {
                blockId = tier_by_realm_tier.get(t);
                if (blockId != null) {
                    return blockId;
                }
            }
            return null;
        }

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public float getSpawn_chance() { return spawn_chance; }
        public void setSpawn_chance(float spawn_chance) { this.spawn_chance = spawn_chance; }

        public Map<Integer, String> getTier_by_realm_tier() { return tier_by_realm_tier; }
        public void setTier_by_realm_tier(Map<Integer, String> m) { this.tier_by_realm_tier = m; }

        public float getMin_distance_from_spawn() { return min_distance_from_spawn; }
        public void setMin_distance_from_spawn(float v) { this.min_distance_from_spawn = v; }

        public float getMax_distance_from_spawn() { return max_distance_from_spawn; }
        public void setMax_distance_from_spawn(float v) { this.max_distance_from_spawn = v; }
    }
}
