package io.github.larsonix.trailoforbis.util;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.server.core.cosmetics.CosmeticsModule;
import com.hypixel.hytale.server.core.cosmetics.Emote;
import com.hypixel.hytale.server.core.cosmetics.EmoteAsset;
import com.hypixel.hytale.server.core.entity.AnimationUtils;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Shared utility for triggering celebration emotes on players.
 *
 * <p>Used by both the level-up celebration and realm victory sequence.
 * Validates emote IDs at construction time and logs available emotes
 * for configuration purposes.
 *
 * <p>Thread-safe: stateless after construction (emote ID is immutable).
 */
public final class EmoteCelebrationHelper {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /** The validated emote ID, or null if disabled/invalid. */
    @Nullable
    private final String emoteId;

    /**
     * Creates a new emote celebration helper.
     *
     * <p>Validates the configured emote ID against the known emote registries.
     * If the ID is empty or null, emotes are disabled (no warning).
     * If the ID is non-empty but not found, a warning is logged and emotes are disabled.
     *
     * @param configuredEmoteId The emote ID from configuration (empty string = disabled)
     * @param context           A human-readable label for log messages (e.g., "level-up", "realm-victory")
     */
    public EmoteCelebrationHelper(@Nullable String configuredEmoteId, @Nonnull String context) {
        if (configuredEmoteId == null || configuredEmoteId.isBlank()) {
            this.emoteId = null;
            LOGGER.atFine().log("Emote celebrations disabled for %s (no emote_id configured)", context);
            return;
        }

        // Validate the emote ID exists in either registry
        if (isValidEmoteId(configuredEmoteId)) {
            this.emoteId = configuredEmoteId;
            LOGGER.atInfo().log("Emote celebrations enabled for %s: emote_id='%s'", context, configuredEmoteId);
        } else {
            this.emoteId = null;
            LOGGER.atWarning().log(
                "Emote ID '%s' not found for %s — emote celebrations disabled. "
                    + "Use the /emote command in-game to see available emotes, "
                    + "or check server logs at startup for the emote list.",
                configuredEmoteId, context);
        }
    }

    /**
     * Whether emote celebrations are enabled (valid emote ID configured).
     *
     * @return true if {@link #playEmote} will attempt to trigger an emote
     */
    public boolean isEnabled() {
        return emoteId != null;
    }

    /**
     * Triggers the configured celebration emote on a player.
     *
     * <p>Resolves the player's entity reference and store from the PlayerRef,
     * then sends the animation packet. The emote is visible to the player
     * themselves and all nearby players.
     *
     * <p>Fails gracefully: logs a warning on error, never throws.
     *
     * @param playerRef The player to trigger the emote on
     */
    public void playEmote(@Nonnull PlayerRef playerRef) {
        if (emoteId == null) {
            return;
        }

        try {
            Ref<EntityStore> ref = playerRef.getReference();
            if (ref == null) {
                LOGGER.atFine().log("Cannot play emote — player entity reference is null");
                return;
            }

            Store<EntityStore> store = ref.getStore();
            if (store == null) {
                LOGGER.atFine().log("Cannot play emote — entity store is null");
                return;
            }

            AnimationUtils.playAnimation(ref, AnimationSlot.Emote, null, emoteId, true, store);
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to play celebration emote '%s'", emoteId);
        }
    }

    /**
     * Logs all available emote IDs to the server log.
     *
     * <p>Call this once at plugin startup to help server operators discover
     * valid emote IDs for configuration.
     */
    public static void logAvailableEmotes() {
        try {
            Set<String> allIds = new TreeSet<>();

            // Built-in cosmetics emotes
            Map<String, Emote> builtinEmotes = CosmeticsModule.get().getRegistry().getEmotesInGame();
            if (builtinEmotes != null && !builtinEmotes.isEmpty()) {
                allIds.addAll(builtinEmotes.keySet());
            }

            // Server-defined emote assets
            var serverEmotes = EmoteAsset.getAssetMap();
            if (serverEmotes != null) {
                var assetMap = serverEmotes.getAssetMap();
                if (assetMap != null && !assetMap.isEmpty()) {
                    allIds.addAll(assetMap.keySet());
                }
            }

            if (allIds.isEmpty()) {
                LOGGER.atInfo().log("[EmoteCelebration] No emotes found in registries");
            } else {
                LOGGER.atInfo().log("[EmoteCelebration] Available emotes (%d): %s", allIds.size(), allIds);
            }
        } catch (Exception e) {
            LOGGER.atFine().log("Could not enumerate available emotes: %s", e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // INTERNAL
    // ═══════════════════════════════════════════════════════════════════

    private static boolean isValidEmoteId(@Nonnull String emoteId) {
        try {
            // Check built-in cosmetics emotes
            Map<String, Emote> builtinEmotes = CosmeticsModule.get().getRegistry().getEmotesInGame();
            if (builtinEmotes != null && builtinEmotes.containsKey(emoteId)) {
                return true;
            }

            // Check server-defined emote assets
            var serverEmotes = EmoteAsset.getAssetMap();
            if (serverEmotes != null && serverEmotes.getAsset(emoteId) != null) {
                return true;
            }
        } catch (Exception e) {
            LOGGER.atFine().log("Could not validate emote ID '%s': %s", emoteId, e.getMessage());
        }
        return false;
    }
}
