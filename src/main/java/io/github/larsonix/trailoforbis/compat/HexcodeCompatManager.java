package io.github.larsonix.trailoforbis.compat;

import com.hypixel.hytale.common.plugin.PluginIdentifier;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.plugin.PluginManager;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Singleton manager for the Hexcode compatibility layer.
 *
 * <p>Detects Hexcode at runtime via {@code PluginManager}, then creates either
 * {@link HexcodeBridgeImpl} (direct imports, zero reflection) or
 * {@link HexcodeBridgeNoop} (safe no-ops). Java's lazy class loading ensures
 * {@code HexcodeBridgeImpl} is never loaded when Hexcode is absent.
 *
 * <p><b>Thread safety:</b> The singleton is created once during plugin
 * initialization on the main thread. The bridge reference is volatile for
 * safe publication to ECS system threads.
 *
 * @see HexcodeBridge
 */
public final class HexcodeCompatManager {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final PluginIdentifier HEXCODE_ID = new PluginIdentifier("Riprod", "Hexcode");

    // Singleton
    private static volatile HexcodeCompatManager instance;

    // The bridge implementation (real or noop)
    private final HexcodeBridge bridge;
    private final String hexcodeVersion;

    // Attribution statistics (for /too hexcode diagnostics)
    private final AtomicLong tier1Hits = new AtomicLong();
    private final AtomicLong tier2Hits = new AtomicLong();
    private final AtomicLong tier3Hits = new AtomicLong();
    private final AtomicLong tier4Hits = new AtomicLong();
    private final AtomicLong unattributed = new AtomicLong();

    private HexcodeCompatManager(@Nonnull HexcodeBridge bridge, @Nullable String version) {
        this.bridge = bridge;
        this.hexcodeVersion = version;
    }

    /**
     * Initializes the Hexcode compatibility layer.
     *
     * <p>Call once during {@code TrailOfOrbis.start()}, after all plugins have loaded.
     * Detects Hexcode and creates the appropriate bridge implementation.
     */
    public static void initialize() {
        if (instance != null) return;

        var plugin = PluginManager.get().getPlugin(HEXCODE_ID);
        if (plugin != null) {
            String version = null;
            try {
                version = plugin.getManifest().getVersion().toString();
            } catch (Exception e) {
                LOGGER.atFine().log("[HexcodeCompat] Could not read Hexcode version: %s", e.getMessage());
            }

            HexcodeBridge impl = new HexcodeBridgeImpl();
            instance = new HexcodeCompatManager(impl, version);
            LOGGER.atInfo().log("[HexcodeCompat] Hexcode v%s DETECTED — direct integration active (zero reflection)",
                    version != null ? version : "unknown");
        } else {
            instance = new HexcodeCompatManager(new HexcodeBridgeNoop(), null);
            LOGGER.atInfo().log("[HexcodeCompat] Hexcode not detected — compatibility features disabled");
        }
    }

    /**
     * Returns the singleton instance. Must be called after {@link #initialize()}.
     *
     * @throws IllegalStateException if called before initialize()
     */
    @Nonnull
    public static HexcodeCompatManager get() {
        HexcodeCompatManager mgr = instance;
        if (mgr == null) {
            throw new IllegalStateException("HexcodeCompatManager.initialize() not called yet");
        }
        return mgr;
    }

    /**
     * Returns the bridge implementation (real or noop).
     * This is the primary access point for all Hexcode compatibility operations.
     */
    @Nonnull
    public HexcodeBridge bridge() {
        return bridge;
    }

    // ── Convenience Delegations ──
    // These exist so callsites can use HexcodeCompatManager.get().isLoaded() directly

    /** Whether Hexcode is loaded and fully initialized. */
    public boolean isLoaded() {
        return bridge.isLoaded();
    }

    /** Hexcode version string, or null if not loaded. */
    @Nullable
    public String getVersion() {
        return hexcodeVersion;
    }

    // ── Attribution Statistics ──

    public void recordTier1Hit() { tier1Hits.incrementAndGet(); }
    public void recordTier2Hit() { tier2Hits.incrementAndGet(); }
    public void recordTier3Hit() { tier3Hits.incrementAndGet(); }
    public void recordTier4Hit() { tier4Hits.incrementAndGet(); }
    public void recordUnattributed() { unattributed.incrementAndGet(); }

    public long getTier1Hits() { return tier1Hits.get(); }
    public long getTier2Hits() { return tier2Hits.get(); }
    public long getTier3Hits() { return tier3Hits.get(); }
    public long getTier4Hits() { return tier4Hits.get(); }
    public long getUnattributed() { return unattributed.get(); }

    // ── Health Check ──

    /**
     * Logs a structured health check summary. Called during initialization.
     */
    public void runHealthCheck() {
        if (!bridge.isLoaded()) {
            LOGGER.atInfo().log("[HexcodeCompat] === Health Check: DISABLED (Hexcode not present) ===");
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("\n[HexcodeCompat] ═══ Hexcode Integration Health Check ═══");
        sb.append("\n  Plugin:     Hexcode v").append(hexcodeVersion != null ? hexcodeVersion : "unknown");

        // Component status
        sb.append("\n  Components: HexEffects=").append(status(bridge.getHexEffectsComponentType() != null));
        sb.append(" | ProjectileState=").append(status(bridge.getProjectileStateComponentType() != null));
        sb.append(" | ShatterState=").append(status(bridge.getShatterStateComponentType() != null));

        // Feature status
        sb.append("\n  Tracking:   ").append(bridge.isTrackingInitialized() ? "ACTIVE" : "PENDING");

        sb.append("\n═══════════════════════════════════════════════════════");
        LOGGER.atInfo().log(sb.toString());
    }

    /**
     * Returns a diagnostic summary for the /too hexcode admin command.
     */
    @Nonnull
    public String getDiagnostics() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Hexcode Integration ===\n");

        if (!bridge.isLoaded()) {
            sb.append("Status: NOT LOADED\n");
            return sb.toString();
        }

        sb.append("Status: ACTIVE\n");
        sb.append("Version: ").append(hexcodeVersion != null ? hexcodeVersion : "unknown").append("\n");
        sb.append("Mode: Direct imports (compileOnly)\n");
        sb.append("\nComponents:\n");
        sb.append("  HexEffects:      ").append(status(bridge.getHexEffectsComponentType() != null)).append("\n");
        sb.append("  ProjectileState: ").append(status(bridge.getProjectileStateComponentType() != null)).append("\n");
        sb.append("  ShatterState:    ").append(status(bridge.getShatterStateComponentType() != null)).append("\n");
        sb.append("  Tracking:        ").append(bridge.isTrackingInitialized() ? "ACTIVE" : "PENDING").append("\n");

        sb.append("\nRegistry:\n");
        sb.append("  Constructs:  ").append(HexEntityRegistry.getConstructCount()).append("\n");
        sb.append("  Projectiles: ").append(HexEntityRegistry.getProjectileCount()).append("\n");

        sb.append("\nAttribution Stats:\n");
        sb.append("  Tier 1 (ThreadLocal):  ").append(tier1Hits.get()).append("\n");
        sb.append("  Tier 2 (Construct):    ").append(tier2Hits.get()).append("\n");
        sb.append("  Tier 3 (Projectile):   ").append(tier3Hits.get()).append("\n");
        sb.append("  Tier 4 (Recent):       ").append(tier4Hits.get()).append("\n");
        sb.append("  Unattributed:          ").append(unattributed.get()).append("\n");

        return sb.toString();
    }

    /** Shutdown — clean up resources. */
    public void shutdown() {
        HexCastStateStore.clear();
        HexSpellEchoService.clear();
        HexEntityRegistry.clear();
    }

    private static String status(boolean ok) {
        return ok ? "OK" : "MISSING";
    }
}
