package io.github.larsonix.trailoforbis.maps.templates;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.codecs.EnumCodec;
import com.hypixel.hytale.math.vector.Vector3d;

import javax.annotation.Nonnull;
import java.util.Objects;

/**
 * Represents a spawn point for monsters within a realm template.
 *
 * <p>Spawn points define locations and parameters for spawning monsters
 * during realm initialization. Each spawn point has:
 * <ul>
 *   <li>A position in the world</li>
 *   <li>A radius within which monsters can spawn</li>
 *   <li>A spawn type (normal, elite, boss, pack)</li>
 *   <li>A maximum count of monsters that can spawn at this point</li>
 * </ul>
 *
 * @param position The center position for spawning
 * @param radius The radius around the position where monsters can spawn
 * @param type The classification of monsters to spawn here
 * @param maxCount Maximum number of monsters this point can spawn
 */
public record MonsterSpawnPoint(
    @Nonnull Vector3d position,
    float radius,
    @Nonnull SpawnType type,
    int maxCount
) {

    /**
     * Classification of spawn points that determines what type of monsters spawn.
     */
    public enum SpawnType {
        /** Regular monsters with standard difficulty */
        NORMAL("Normal", 1.0f),
        /** Stronger monsters with increased rewards */
        ELITE("Elite", 2.0f),
        /** Boss monsters - powerful enemies with significant rewards */
        BOSS("Boss", 5.0f),
        /** Pack spawns - groups of weaker monsters */
        PACK("Pack", 0.5f);

        private final String displayName;
        private final float rewardMultiplier;

        SpawnType(String displayName, float rewardMultiplier) {
            this.displayName = displayName;
            this.rewardMultiplier = rewardMultiplier;
        }

        public String getDisplayName() {
            return displayName;
        }

        /**
         * @return Reward multiplier for monsters spawned at this type of point
         */
        public float getRewardMultiplier() {
            return rewardMultiplier;
        }

        public static final EnumCodec<SpawnType> CODEC = new EnumCodec<>(SpawnType.class);
    }

    // === VALIDATION ===

    /**
     * Compact constructor with validation.
     */
    public MonsterSpawnPoint {
        Objects.requireNonNull(position, "Position cannot be null");
        Objects.requireNonNull(type, "Spawn type cannot be null");

        if (radius < 0) {
            radius = 0;
        }
        if (maxCount < 1) {
            maxCount = 1;
        }
    }

    // === FACTORY METHODS ===

    /**
     * Creates a normal spawn point with default radius.
     *
     * @param position The spawn position
     * @param maxCount Maximum monster count
     * @return A new MonsterSpawnPoint
     */
    public static MonsterSpawnPoint normal(Vector3d position, int maxCount) {
        return new MonsterSpawnPoint(position, 5.0f, SpawnType.NORMAL, maxCount);
    }

    /**
     * Creates an elite spawn point.
     *
     * @param position The spawn position
     * @return A new MonsterSpawnPoint for a single elite
     */
    public static MonsterSpawnPoint elite(Vector3d position) {
        return new MonsterSpawnPoint(position, 3.0f, SpawnType.ELITE, 1);
    }

    /**
     * Creates a boss spawn point.
     *
     * @param position The spawn position
     * @return A new MonsterSpawnPoint for a boss
     */
    public static MonsterSpawnPoint boss(Vector3d position) {
        return new MonsterSpawnPoint(position, 2.0f, SpawnType.BOSS, 1);
    }

    /**
     * Creates a pack spawn point for groups of monsters.
     *
     * @param position The spawn position
     * @param packSize The size of the pack
     * @return A new MonsterSpawnPoint for a pack
     */
    public static MonsterSpawnPoint pack(Vector3d position, int packSize) {
        return new MonsterSpawnPoint(position, 8.0f, SpawnType.PACK, packSize);
    }

    // === UTILITY METHODS ===

    /**
     * @return Whether this spawn point is for a boss
     */
    public boolean isBossSpawn() {
        return type == SpawnType.BOSS;
    }

    /**
     * @return Whether this spawn point is for an elite
     */
    public boolean isEliteSpawn() {
        return type == SpawnType.ELITE;
    }

    /**
     * @return Whether this is a standard spawn point
     */
    public boolean isNormalSpawn() {
        return type == SpawnType.NORMAL;
    }

    /**
     * @return Whether this is a pack spawn point
     */
    public boolean isPackSpawn() {
        return type == SpawnType.PACK;
    }

    /**
     * @return The reward multiplier for monsters spawned here
     */
    public float getRewardMultiplier() {
        return type.getRewardMultiplier();
    }

    /**
     * Creates a copy of this spawn point with a different position.
     *
     * @param newPosition The new position
     * @return A new MonsterSpawnPoint with the updated position
     */
    public MonsterSpawnPoint withPosition(Vector3d newPosition) {
        return new MonsterSpawnPoint(newPosition, radius, type, maxCount);
    }

    /**
     * Creates a copy with an adjusted max count.
     *
     * @param newMaxCount The new maximum count
     * @return A new MonsterSpawnPoint with the updated count
     */
    public MonsterSpawnPoint withMaxCount(int newMaxCount) {
        return new MonsterSpawnPoint(position, radius, type, newMaxCount);
    }

    // === CODEC ===

    /**
     * Codec for serialization of MonsterSpawnPoint records.
     */
    public static final Codec<MonsterSpawnPoint> CODEC = new Codec<>() {
        @Override
        public MonsterSpawnPoint decode(@Nonnull org.bson.BsonValue encoded, @Nonnull com.hypixel.hytale.codec.ExtraInfo extraInfo) {
            if (!encoded.isDocument()) {
                throw new IllegalArgumentException("Expected BsonDocument for MonsterSpawnPoint");
            }
            org.bson.BsonDocument doc = encoded.asDocument();

            Vector3d position = Vector3d.CODEC.decode(doc.get("Position"), extraInfo);
            float radius = doc.containsKey("Radius") ? (float) doc.getDouble("Radius").getValue() : 5.0f;
            SpawnType type = doc.containsKey("Type") ?
                SpawnType.CODEC.decode(doc.get("Type"), extraInfo) : SpawnType.NORMAL;
            int maxCount = doc.containsKey("MaxCount") ? doc.getInt32("MaxCount").getValue() : 1;

            return new MonsterSpawnPoint(position, radius, type, maxCount);
        }

        @Override
        public org.bson.BsonValue encode(@Nonnull MonsterSpawnPoint value, @Nonnull com.hypixel.hytale.codec.ExtraInfo extraInfo) {
            org.bson.BsonDocument doc = new org.bson.BsonDocument();
            doc.put("Position", Vector3d.CODEC.encode(value.position(), extraInfo));
            doc.put("Radius", new org.bson.BsonDouble(value.radius()));
            doc.put("Type", SpawnType.CODEC.encode(value.type(), extraInfo));
            doc.put("MaxCount", new org.bson.BsonInt32(value.maxCount()));
            return doc;
        }

        @Override
        public com.hypixel.hytale.codec.schema.config.Schema toSchema(@Nonnull com.hypixel.hytale.codec.schema.SchemaContext context) {
            return new com.hypixel.hytale.codec.schema.config.ObjectSchema();
        }
    };
}
