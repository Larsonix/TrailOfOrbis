package io.github.larsonix.trailoforbis.util;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.universe.world.World;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Utility for awaiting chunk loading before spawning entities in new instances.
 *
 * <p>Uses Hytale's {@code World.getChunkAsync()} which returns a future that
 * completes when the chunk is loaded AND set to TICKING state — meaning
 * entities can safely spawn in it.
 */
public final class ChunkLoadHelper {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /** Maximum time to wait for chunks before proceeding anyway. */
    private static final int TIMEOUT_SECONDS = 10;

    private ChunkLoadHelper() {}

    /**
     * Future that completes when all chunks in the given radius are loaded and TICKING.
     * Chunk size is 32 blocks. Radius 1 = 9 chunks (3x3), radius 3 = 49 chunks (7x7).
     *
     * @param radiusInChunks number of chunks outward from center (0 = center only)
     */
    @Nonnull
    public static CompletableFuture<Void> awaitAreaLoaded(
            @Nonnull World world, int centerBlockX, int centerBlockZ, int radiusInChunks) {

        int centerCX = ChunkUtil.chunkCoordinate(centerBlockX);
        int centerCZ = ChunkUtil.chunkCoordinate(centerBlockZ);

        List<CompletableFuture<?>> futures = new ArrayList<>();
        for (int dx = -radiusInChunks; dx <= radiusInChunks; dx++) {
            for (int dz = -radiusInChunks; dz <= radiusInChunks; dz++) {
                long index = ChunkUtil.indexChunk(centerCX + dx, centerCZ + dz);
                futures.add(world.getChunkAsync(index));
            }
        }

        int totalChunks = futures.size();
        LOGGER.atFine().log("Awaiting %d chunks around (%d, %d)", totalChunks, centerBlockX, centerBlockZ);

        // NOTE: No .exceptionally() handler here — timeout propagates as TimeoutException.
        // Callers use thenRun() for the success path (runs inline on world thread, zero gap)
        // and .exceptionally() for the timeout path (runs on scheduler thread, needs world.execute).
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture<?>[0]))
            .orTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }
}
