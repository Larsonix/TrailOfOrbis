package io.github.larsonix.trailoforbis.lootfilter.bridge;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.TrailOfOrbis;
import io.github.larsonix.trailoforbis.lootfilter.LootFilterManager;
import li.kelp.vuetale.app.PlayerUi;
import li.kelp.vuetale.app.PlayerUiManager;
import li.kelp.vuetale.javascript.JSEngine;
import li.kelp.vuetale.javascript.ModuleRegistry;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Initializes Vuetale and manages the loot filter's Vue page lifecycle.
 *
 * <p>Call {@link #initialize(LootFilterManager)} once during plugin startup
 * (after loot filter manager is ready). Call {@link #shutdown()} during plugin disable.
 *
 * <p>Thread safety: all public methods are safe from the game thread.
 * Vuetale internally dispatches V8 operations to its dedicated thread.
 */
public final class VuetaleIntegration {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static volatile boolean initialized = false;
    private static LootFilterBridge bridge;

    private VuetaleIntegration() {}

    /**
     * Initialize the Vuetale runtime and register the loot filter bridge.
     * Safe to call multiple times — subsequent calls are no-ops.
     */
    public static void initialize(@Nonnull LootFilterManager filterManager, @Nonnull TrailOfOrbis plugin) {
        if (initialized) return;

        try {
            // Register module aliases for classpath resource loading.
            // "core" = Vuetale's runtime (renderer, loader, Common.js, etc.)
            // "loot" = our loot filter Vue components
            ModuleRegistry.INSTANCE.registerModule("core", VuetaleIntegration.class, null);
            ModuleRegistry.INSTANCE.registerModule("loot", VuetaleIntegration.class, null);

            // Access the singleton to trigger lazy V8 initialization
            JSEngine engine = JSEngine.Companion.getInstance();

            // Create and register the Java bridge as a global JS variable.
            // Since we use Kelpy's JAR (no registerJavaBridge method), access V8 runtime
            // via reflection to set the global.
            bridge = new LootFilterBridge(filterManager, plugin);
            engine.runOnV8Thread((kotlin.jvm.functions.Function0<Object>) () -> {
                try {
                    var field = li.kelp.vuetale.javascript.VueBridge.class.getDeclaredField("v8Runtime");
                    field.setAccessible(true);
                    var v8Runtime = (com.caoccao.javet.interop.V8Runtime) field.get(engine.getBridge());
                    v8Runtime.getGlobalObject().set("lootBridge", bridge);
                } catch (Exception e) {
                    LOGGER.atSevere().withCause(e).log("Failed to register lootBridge in V8 runtime");
                }
                return null;
            });

            initialized = true;
            LOGGER.atInfo().log("Vuetale initialized — lootBridge registered");
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to initialize Vuetale runtime");
        }
    }

    /**
     * Shut down the Vuetale runtime. Called during plugin disable.
     */
    public static void shutdown() {
        if (!initialized) return;

        try {
            JSEngine.Companion.getInstance().close();
            LOGGER.atInfo().log("Vuetale runtime shut down");
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Error shutting down Vuetale");
        }

        initialized = false;
        bridge = null;
    }

    /**
     * Open the loot filter page for a player using Vuetale.
     *
     * @param playerRef The player to open the page for
     * @param ref       Entity reference
     * @param store     Entity store
     */
    public static void openLootFilterPage(
            @Nonnull PlayerRef playerRef,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull Store<EntityStore> store) {

        if (!initialized || bridge == null) {
            LOGGER.atWarning().log("Cannot open loot filter page — Vuetale not initialized");
            return;
        }

        UUID playerId = playerRef.getUuid();

        // Get or create the player's Vuetale UI handle
        PlayerUi ui = PlayerUiManager.INSTANCE.getOrCreate(playerId, playerRef, ref, store);

        ui.openPage("@loot/pages/LootFilterPage", CustomPageLifetime.CanDismiss);

        // Push initial state (safe from game thread — dispatches to V8 internally)
        String stateJson = bridge.serializeStateJson(playerId);
        ui.setData("stateJson", stateJson);
        ui.setData("playerId", playerId.toString());
    }

    /**
     * Clean up Vuetale state when a player disconnects.
     */
    public static void onPlayerDisconnect(@Nonnull UUID playerId) {
        if (!initialized) return;
        PlayerUiManager.INSTANCE.remove(playerId);
    }

    /**
     * @return true if Vuetale runtime is initialized and ready
     */
    public static boolean isInitialized() {
        return initialized;
    }

    /**
     * @return the bridge instance, or null if not initialized
     */
    @Nullable
    public static LootFilterBridge getBridge() {
        return bridge;
    }
}
