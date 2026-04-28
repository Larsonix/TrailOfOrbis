package io.github.larsonix.trailoforbis.loot.container;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.map.MapCodec;
import com.hypixel.hytale.component.Resource;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Persistent ChunkStore resource tracking which containers have been processed
 * for loot replacement.
 *
 * <p>This replaces the in-memory-only {@link ContainerTracker} as the source of
 * truth for "has this container been opened and had its loot replaced?" The data
 * is serialized with the world's ChunkStore, so it survives server restarts.
 *
 * <p>One instance exists per world. Each entry maps a position key ({@code "x,y,z"})
 * to a {@link ProcessedEntry} containing the timestamp and first opener's UUID.
 *
 * <h2>Persistence lifecycle</h2>
 * <ol>
 *   <li>Registered via {@code getChunkStoreRegistry().registerResource()} in plugin setup</li>
 *   <li>Hytale creates a default (empty) instance for new worlds via {@code CODEC.getDefaultValue()}</li>
 *   <li>For existing worlds, the CODEC decodes saved BSON data into the resource</li>
 *   <li>Mutations mark the chunk store dirty automatically (Hytale detects resource changes)</li>
 *   <li>{@link #clone()} is called during save to produce a serialization snapshot</li>
 * </ol>
 *
 * <h2>Access pattern</h2>
 * <pre>
 * // From an ECS system with access to ChunkStore:
 * Store&lt;ChunkStore&gt; store = world.getChunkStore().getStore();
 * ProcessedContainerResource resource = store.getResource(resourceType);
 * if (resource.isProcessed(x, y, z)) { return; }
 * resource.markProcessed(x, y, z, playerId);
 * </pre>
 *
 * @see ContainerLootInterceptor
 * @see ContainerTracker
 */
public class ProcessedContainerResource implements Resource<ChunkStore> {

    // =========================================================================
    // INNER DATA CLASS
    // =========================================================================

    /**
     * Mutable data class for a single processed container entry.
     *
     * <p>Uses mutable fields (not a record) because {@link BuilderCodec} requires
     * field-setting lambdas during BSON deserialization.
     */
    public static final class ProcessedEntry {

        /** Epoch milliseconds when the container was first processed. */
        long timestamp;

        /** UUID string of the first player to open the container. */
        String firstOpenerId;

        /** Default constructor — required by BuilderCodec. */
        public ProcessedEntry() {
            this.timestamp = 0L;
            this.firstOpenerId = "";
        }

        public ProcessedEntry(long timestamp, @Nonnull String firstOpenerId) {
            this.timestamp = timestamp;
            this.firstOpenerId = firstOpenerId;
        }

        /** Copy constructor for deep-copy in {@link ProcessedContainerResource#clone()}. */
        public ProcessedEntry(@Nonnull ProcessedEntry other) {
            this.timestamp = other.timestamp;
            this.firstOpenerId = other.firstOpenerId;
        }

        public long getTimestamp() {
            return timestamp;
        }

        @Nonnull
        public String getFirstOpenerId() {
            return firstOpenerId;
        }

        static final BuilderCodec<ProcessedEntry> CODEC = BuilderCodec.builder(
                ProcessedEntry.class, ProcessedEntry::new)
            .addField(
                new KeyedCodec<>("Timestamp", Codec.LONG),
                (entry, value) -> entry.timestamp = value,
                entry -> entry.timestamp)
            .addField(
                new KeyedCodec<>("Uuid", Codec.STRING),
                (entry, value) -> entry.firstOpenerId = value,
                entry -> entry.firstOpenerId)
            .build();
    }

    // =========================================================================
    // CODEC
    // =========================================================================

    /**
     * CODEC for serialization/deserialization.
     *
     * <p>Serializes as: {@code {"Processed": {"x,y,z": {"Timestamp": 123456, "Uuid": "uuid"}, ...}}}
     *
     * <p>The {@code MapCodec} uses string keys (position) and {@code ProcessedEntry.CODEC}
     * for values. The default value (from {@code getDefaultValue()}) is an empty resource.
     */
    public static final BuilderCodec<ProcessedContainerResource> CODEC = BuilderCodec.builder(
            ProcessedContainerResource.class, ProcessedContainerResource::new)
        .addField(
            new KeyedCodec<>("Processed",
                new MapCodec<>(ProcessedEntry.CODEC, ConcurrentHashMap::new)),
            (resource, value) -> resource.processed = new ConcurrentHashMap<>(value),
            resource -> resource.processed)
        .build();

    // =========================================================================
    // STATE
    // =========================================================================

    /** Position key → entry. Keys are "x,y,z" strings. */
    private Map<String, ProcessedEntry> processed = new ConcurrentHashMap<>();

    // =========================================================================
    // CONSTRUCTORS
    // =========================================================================

    /** Default constructor — creates an empty resource. Used by CODEC for new worlds. */
    public ProcessedContainerResource() {
    }

    /** Copy constructor — deep-copies all entries. Used by {@link #clone()}. */
    public ProcessedContainerResource(@Nonnull ProcessedContainerResource other) {
        for (Map.Entry<String, ProcessedEntry> entry : other.processed.entrySet()) {
            this.processed.put(entry.getKey(), new ProcessedEntry(entry.getValue()));
        }
    }

    // =========================================================================
    // RESOURCE CONTRACT
    // =========================================================================

    /**
     * Produces a deep copy for serialization.
     *
     * <p>Called by Hytale during {@code Store.copySerializableEntity()} when saving
     * the world. The copy must be independent — mutations on the live instance must
     * not affect the copy being serialized.
     */
    @Nullable
    @Override
    public Resource<ChunkStore> clone() {
        return new ProcessedContainerResource(this);
    }

    // =========================================================================
    // POSITION KEY
    // =========================================================================

    /**
     * Builds a position key from block coordinates.
     * Matches the {@code "x,y,z"} format used by Loot4Everyone's LootChestTemplate.
     */
    @Nonnull
    private static String key(int x, int y, int z) {
        return x + "," + y + "," + z;
    }

    // =========================================================================
    // QUERIES
    // =========================================================================

    /**
     * Checks if a container at the given position has been processed.
     *
     * @param x Block X coordinate
     * @param y Block Y coordinate
     * @param z Block Z coordinate
     * @return true if the container was previously processed
     */
    public boolean isProcessed(int x, int y, int z) {
        return processed.containsKey(key(x, y, z));
    }

    /**
     * Gets the processed entry for a container.
     *
     * @param x Block X coordinate
     * @param y Block Y coordinate
     * @param z Block Z coordinate
     * @return The entry, or null if not processed
     */
    @Nullable
    public ProcessedEntry getEntry(int x, int y, int z) {
        return processed.get(key(x, y, z));
    }

    // =========================================================================
    // MUTATIONS
    // =========================================================================

    /**
     * Marks a container as processed.
     *
     * <p>Uses {@code putIfAbsent} for thread safety — if two players open the same
     * container simultaneously, only the first one succeeds.
     *
     * @param x Block X coordinate
     * @param y Block Y coordinate
     * @param z Block Z coordinate
     * @param firstOpenerId UUID of the player who triggered the loot replacement
     * @return true if this was the first time marking (the caller should run replacement)
     */
    public boolean markProcessed(int x, int y, int z, @Nonnull UUID firstOpenerId) {
        String k = key(x, y, z);
        ProcessedEntry entry = new ProcessedEntry(
            System.currentTimeMillis(),
            firstOpenerId.toString()
        );
        return processed.putIfAbsent(k, entry) == null;
    }

    /**
     * Removes a container from tracking (e.g., admin reset command).
     *
     * @param x Block X coordinate
     * @param y Block Y coordinate
     * @param z Block Z coordinate
     * @return true if an entry was removed
     */
    public boolean remove(int x, int y, int z) {
        return processed.remove(key(x, y, z)) != null;
    }

    /**
     * Clears all tracked containers (e.g., admin reset-all command).
     */
    public void clear() {
        processed.clear();
    }

    /**
     * Gets the number of tracked containers.
     *
     * @return Number of containers currently tracked
     */
    public int size() {
        return processed.size();
    }
}
