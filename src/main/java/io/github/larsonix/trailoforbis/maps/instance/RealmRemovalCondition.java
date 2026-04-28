package io.github.larsonix.trailoforbis.maps.instance;

import com.hypixel.hytale.builtin.instances.removal.RemovalCondition;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import javax.annotation.Nonnull;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

/**
 * Custom removal condition for realm instances.
 *
 * <p>This condition integrates with the realm system to determine when
 * a realm world should be removed. It considers:
 * <ul>
 *   <li>Realm completion state</li>
 *   <li>Timeout expiration</li>
 *   <li>Empty world conditions</li>
 *   <li>Force close requests</li>
 * </ul>
 *
 * <p>The condition delegates to the RealmsManager to check if the
 * associated RealmInstance should be closed.
 *
 * @see RemovalCondition
 * @see RealmInstance
 */
public class RealmRemovalCondition implements RemovalCondition {

    /**
     * Codec for serialization.
     */
    public static final BuilderCodec<RealmRemovalCondition> CODEC = BuilderCodec.builder(
            RealmRemovalCondition.class,
            RealmRemovalCondition::new
        )
        .append(
            new KeyedCodec<>("RealmId", Codec.UUID_STRING),
            (o, v) -> o.realmId = v,
            o -> o.realmId
        ).add()
        .append(
            new KeyedCodec<>("GracePeriodSeconds", Codec.DOUBLE),
            (o, v) -> o.gracePeriodSeconds = v,
            o -> o.gracePeriodSeconds
        ).add()
        .build();

    /**
     * The realm ID this condition is for.
     */
    private UUID realmId;

    /**
     * Grace period after completion before removal.
     */
    private double gracePeriodSeconds = 60.0;

    /**
     * Function to check if realm should be removed.
     * Set by RealmsManager.
     */
    private static volatile Function<UUID, Boolean> removalChecker;

    /**
     * Default constructor for codec.
     */
    public RealmRemovalCondition() {
    }

    /**
     * Creates a removal condition for a specific realm.
     *
     * @param realmId The realm ID
     * @param gracePeriodSeconds Grace period in seconds
     */
    public RealmRemovalCondition(@Nonnull UUID realmId, double gracePeriodSeconds) {
        this.realmId = Objects.requireNonNull(realmId);
        this.gracePeriodSeconds = gracePeriodSeconds;
    }

    /**
     * Creates a removal condition with default grace period.
     *
     * @param realmId The realm ID
     */
    public RealmRemovalCondition(@Nonnull UUID realmId) {
        this(realmId, 60.0);
    }

    /**
     * Sets the global removal checker function.
     *
     * <p>Called by RealmsManager during initialization.
     *
     * @param checker Function that returns true if realm should be removed
     */
    public static void setRemovalChecker(@Nonnull Function<UUID, Boolean> checker) {
        removalChecker = Objects.requireNonNull(checker);
    }

    @Override
    public boolean shouldRemoveWorld(@Nonnull Store<ChunkStore> store) {
        if (realmId == null) {
            return true; // Invalid condition, remove
        }

        // Check with the realm manager
        Function<UUID, Boolean> checker = removalChecker;
        if (checker != null) {
            return checker.apply(realmId);
        }

        // Fallback: check if world is empty
        World world = store.getExternalData().getWorld();
        return world.getPlayerCount() == 0;
    }

    /**
     * @return The realm ID this condition tracks
     */
    public UUID getRealmId() {
        return realmId;
    }

    /**
     * @return The grace period in seconds
     */
    public double getGracePeriodSeconds() {
        return gracePeriodSeconds;
    }
}
