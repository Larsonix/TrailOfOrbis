package io.github.larsonix.trailoforbis.maps.portal;

import com.hypixel.hytale.math.vector.Vector3i;
import io.github.larsonix.trailoforbis.gear.model.GearRarity;
import io.github.larsonix.trailoforbis.maps.core.RealmBiomeType;
import io.github.larsonix.trailoforbis.maps.core.RealmLayoutSize;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Tracks visual state for a single active portal.
 *
 * <p>Created when a portal is activated with a realm map, destroyed when
 * the realm closes. Holds all data needed for runtime particle spawning
 * and cleanup of decorative blocks.
 */
public final class PortalVisualState {

    private final UUID worldUuid;
    private final Vector3i position;
    private final RealmBiomeType biome;
    private final GearRarity rarity;
    private final RealmLayoutSize size;
    private final int quality;
    private final boolean corrupted;

    /** Positions of decorative blocks placed around the portal (for cleanup). */
    private final List<Vector3i> decorativePositions = new ArrayList<>();

    /** Tick counter for particle timing. */
    private int tickCounter;

    public PortalVisualState(
            @Nonnull UUID worldUuid,
            @Nonnull Vector3i position,
            @Nonnull RealmBiomeType biome,
            @Nonnull GearRarity rarity,
            @Nonnull RealmLayoutSize size,
            int quality,
            boolean corrupted) {
        this.worldUuid = worldUuid;
        this.position = position;
        this.biome = biome;
        this.rarity = rarity;
        this.size = size;
        this.quality = quality;
        this.corrupted = corrupted;
    }

    @Nonnull public UUID getWorldUuid() { return worldUuid; }
    @Nonnull public Vector3i getPosition() { return position; }
    @Nonnull public RealmBiomeType getBiome() { return biome; }
    @Nonnull public GearRarity getRarity() { return rarity; }
    @Nonnull public RealmLayoutSize getSize() { return size; }
    public int getQuality() { return quality; }
    public boolean isCorrupted() { return corrupted; }

    @Nonnull public List<Vector3i> getDecorativePositions() { return decorativePositions; }

    public int getTickCounter() { return tickCounter; }
    public void incrementTick() { tickCounter++; }

    // ═══════════════════════════════════════════════════════════════════
    // SMOKE LAYER (primary visual — colored ring around portal)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Number of smoke particles per tick, scaled by rarity + quality.
     * Higher rarities produce a denser, more impressive smoke ring.
     */
    public int getSmokeParticleCount() {
        int base = switch (rarity) {
            case COMMON -> 2;
            case UNCOMMON -> 3;
            case RARE -> 4;
            case EPIC -> 5;
            case LEGENDARY -> 6;
            case MYTHIC, UNIQUE -> 8;
        };
        return base + (int) (base * quality / 200.0);
    }

    /**
     * Scale multiplier for smoke particles. The spawner's base scale (0.15-0.30)
     * is multiplied by this — higher rarities produce larger smoke plumes.
     */
    public float getSmokeParticleScale() {
        return switch (rarity) {
            case COMMON -> 0.7f;
            case UNCOMMON -> 0.8f;
            case RARE -> 0.9f;
            case EPIC -> 1.0f;
            case LEGENDARY -> 1.1f;
            case MYTHIC, UNIQUE -> 1.3f;
        };
    }

    /**
     * Ring radius for smoke particles. Wider spread for higher rarities.
     */
    public double getSmokeRingRadius() {
        return switch (rarity) {
            case COMMON -> 0.6;
            case UNCOMMON -> 0.7;
            case RARE -> 0.8;
            case EPIC -> 0.9;
            case LEGENDARY -> 1.0;
            case MYTHIC, UNIQUE -> 1.2;
        };
    }

    // ═══════════════════════════════════════════════════════════════════
    // CORE GLOW LAYER (bright center pulse)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Scale multiplier for the core glow at portal center.
     * The spawner's base scale (0.25-0.40) is multiplied by this.
     */
    public float getCoreParticleScale() {
        return switch (rarity) {
            case COMMON -> 0.6f;
            case UNCOMMON -> 0.7f;
            case RARE -> 0.8f;
            case EPIC -> 0.9f;
            case LEGENDARY -> 1.0f;
            case MYTHIC, UNIQUE -> 1.3f;
        };
    }

    // ═══════════════════════════════════════════════════════════════════
    // SPARKLE LAYER (Epic+ only — bright accent flashes)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Whether this rarity has the sparkle accent layer.
     * Only Epic and above get sparkles — adds visual intensity.
     */
    public boolean hasSparkleLayer() {
        return rarity.ordinal() >= io.github.larsonix.trailoforbis.gear.model.GearRarity.EPIC.ordinal();
    }

    /**
     * Number of sparkle particles per tick (Epic+ only).
     */
    public int getSparkleCount() {
        return switch (rarity) {
            case EPIC -> 2;
            case LEGENDARY -> 3;
            case MYTHIC, UNIQUE -> 4;
            default -> 0;
        };
    }
}
