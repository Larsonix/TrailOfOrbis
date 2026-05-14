package io.github.larsonix.trailoforbis.combat.indicators.color;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.UpdateType;
import com.hypixel.hytale.protocol.packets.assets.UpdateEntityUIComponents;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import io.github.larsonix.trailoforbis.combat.DamageBreakdown;
import io.github.larsonix.trailoforbis.combat.indicators.CombatIndicatorService.CombatTextParams;
import io.github.larsonix.trailoforbis.config.ConfigManager;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Orchestrates the colored combat text system.
 *
 * <p>Composes:
 * <ul>
 *   <li>{@link CombatTextColorConfig} — YAML configuration</li>
 *   <li>{@link CombatTextTemplateRegistry} — Template registration + player sync</li>
 *   <li>{@link CombatTextProfileResolver} — DamageBreakdown → profile resolution</li>
 * </ul>
 *
 * <p>Follows the Manager pattern: initialized in {@code TrailOfOrbis.start()},
 * provides a single integration point ({@link #applyAndResolve}) called by
 * {@link io.github.larsonix.trailoforbis.combat.indicators.CombatIndicatorService}.
 */
public class CombatTextColorManager {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final ConfigManager configManager;
    private CombatTextColorConfig config;
    private CombatTextTemplateRegistry templateRegistry;
    private CombatTextProfileResolver profileResolver;
    private boolean enabled;

    /**
     * Per-player cache of the last applied profile ID.
     * Prevents sending redundant template swap packets when the same element
     * is used for AoE damage (40 identical packets → 1).
     */
    private final Map<UUID, String> lastAppliedProfile = new HashMap<>();

    /**
     * Per-player timestamp of last template swap packet send (nanos).
     * Used for time-based coalescing: when fighting mixed-element mobs, template
     * swaps alternate rapidly (fire → lightning → fire). Without coalescing, each
     * hit sends a packet. With this cooldown, at most one swap per window is sent.
     */
    private final Map<UUID, Long> lastSwapTimeNanos = new HashMap<>();

    /**
     * Minimum interval between template swap packets for the same player (nanos).
     * 50ms ≈ 1.5 ticks at 30 TPS — imperceptible to the player but prevents
     * 20-40 template swaps per second during mixed-element combat.
     */
    private static final long SWAP_COOLDOWN_NANOS = 50_000_000L;

    /**
     * Cached built templates by profile ID.
     * Since the same profile always produces the same EntityUIComponent,
     * we build once and reuse instead of cloning+overriding per hit.
     */
    private final Map<String, com.hypixel.hytale.protocol.EntityUIComponent> templateCache = new HashMap<>();

    public CombatTextColorManager(@Nonnull ConfigManager configManager) {
        this.configManager = configManager;
    }

    /**
     * Initializes the colored combat text system.
     *
     * <p>Loads config, builds profiles, and registers templates in the EntityUI system.
     * Must be called during plugin start, after ConfigManager has loaded configs.
     *
     * @return true if initialization succeeded and the system is enabled
     */
    public boolean initialize() {
        try {
            // Load config
            config = configManager.getCombatTextColorConfig();
            if (config == null || !config.isEnabled()) {
                LOGGER.atInfo().log("Colored combat text is disabled in config");
                return false;
            }

            Map<String, CombatTextProfile> profileMap = config.getProfiles();
            if (profileMap.isEmpty()) {
                LOGGER.atWarning().log("No combat text profiles configured — system disabled");
                return false;
            }

            // Inject built-in profiles that must exist regardless of config file version.
            // This ensures JAR-only upgrades work for existing players without config changes.
            injectBuiltInProfiles(profileMap);

            // Register templates
            templateRegistry = new CombatTextTemplateRegistry();
            List<CombatTextProfile> profileList = new ArrayList<>(profileMap.values());

            if (!templateRegistry.initialize(profileList)) {
                LOGGER.atWarning().log("Template registration failed — colored combat text disabled");
                return false;
            }

            // Create resolver directly — no template index assignment needed.
            // Templates are built on demand and applied at the vanilla index.
            Map<String, CombatTextProfile> resolvedProfiles = new java.util.LinkedHashMap<>();
            for (CombatTextProfile profile : profileList) {
                resolvedProfiles.put(profile.id(), profile);
            }
            profileResolver = new CombatTextProfileResolver(resolvedProfiles);

            enabled = true;
            LOGGER.atInfo().log("Colored combat text initialized: %d profiles", resolvedProfiles.size());
            return true;

        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to initialize colored combat text");
            enabled = false;
            return false;
        }
    }

    /**
     * Syncs all custom templates to a newly connected player.
     *
     * Resolves the appropriate color profile and applies the template overwrite.
     *
     * <p>This is the single integration point called by {@code CombatIndicatorService}
     * BEFORE queuing {@code CombatTextUpdate}. It overwrites the vanilla CombatText
     * template definition at its original index on the attacker's client.
     *
     * @param attacker The attacker player ref (receives the template overwrite)
     * @param breakdown The damage breakdown for profile resolution
     * @param params Combat text flags (crit, avoidance)
     * @return The resolved profile (for text formatting), or null if no color applied
     */
    @Nullable
    public CombatTextProfile applyAndResolve(
        @Nonnull PlayerRef attacker,
        @Nullable DamageBreakdown breakdown,
        @Nonnull CombatTextParams params
    ) {
        if (!enabled || profileResolver == null || templateRegistry == null) return null;

        try {
            CombatTextProfile profile = profileResolver.resolve(breakdown, params);
            if (profile == null) return null;

            applyTemplateOverwrite(attacker, profile);
            return profile;
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to apply colored combat text — falling back to vanilla");
            return null;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Applies a specific color profile by key, bypassing the damage/avoidance resolver.
     *
     * <p>Used for non-damage combat text like recovery healing ("+15" in green).
     * Falls back gracefully if the profile doesn't exist or the system is disabled.
     *
     * @param store The entity store
     * @param entityRef The entity to apply the template on
     * @param viewer The viewer who sees the colored text
     * @param player The player ref for fallback path
     * @param profileKey The profile key (e.g., "healing")
     */
    public void applyByKey(
        @Nonnull PlayerRef player,
        @Nonnull String profileKey
    ) {
        if (!enabled || profileResolver == null || templateRegistry == null) return;

        CombatTextProfile profile = profileResolver.getByKey(profileKey);
        if (profile == null) return;

        applyTemplateOverwrite(player, profile);
    }

    public void shutdown() {
        enabled = false;
        lastAppliedProfile.clear();
        lastSwapTimeNanos.clear();
        templateCache.clear();
    }

    // ── Built-in profiles ──────────────────────────────────────────────

    /**
     * Injects profiles that must exist for code-driven features, regardless of
     * the player's config file version. Config values take precedence if present.
     */
    private void injectBuiltInProfiles(@Nonnull Map<String, CombatTextProfile> profiles) {
        if (!profiles.containsKey("healing")) {
            profiles.put("healing", CombatTextProfile.of(
                "healing",
                CombatTextColorConfig.parseColor("#55FF55"), // green
                54.0f,  // smaller than damage, same as avoidance text
                0.6f,   // slightly longer visibility
                0f,     // no hit angle influence for self-text
                CombatTextColorConfig.HARDCODED_DEFAULTS.animations()
            ));
        }
    }

    // ── Global template overwrite ──────────────────────────────────────────

    /**
     * Overwrites the vanilla CombatText template at its original index
     * on the attacker's client. Uses the vanilla maxId to avoid client-side
     * array resize races with Hytale's asset pipeline.
     *
     * <p>Two-level dedup:
     * <ol>
     *   <li><b>Same-profile dedup</b>: skips if the same profile is already active
     *       (handles AoE with identical element)</li>
     *   <li><b>Time-based coalescing</b>: during mixed-element combat, template swaps
     *       alternate rapidly. A 50ms cooldown coalesces multiple swaps into one packet.
     *       The latest profile is always tracked in {@code lastAppliedProfile} so the
     *       NEXT hit outside the window sends the correct swap.</li>
     * </ol>
     */
    private void applyTemplateOverwrite(
        @Nonnull PlayerRef attacker,
        @Nonnull CombatTextProfile profile
    ) {
        UUID playerUuid = attacker.getUuid();

        // Level 1: Same-profile dedup — skip if already active
        String lastProfile = lastAppliedProfile.get(playerUuid);
        if (profile.id().equals(lastProfile)) {
            return;
        }

        // Level 2: Time-based coalescing — suppress rapid alternating swaps.
        // During mixed-element combat (fire mob → lightning mob → fire mob),
        // every hit would swap the template. Instead, only send one swap per
        // cooldown window. The lastAppliedProfile is still updated so the
        // next hit outside the window knows which profile to send.
        long now = System.nanoTime();
        Long lastSwap = lastSwapTimeNanos.get(playerUuid);
        if (lastSwap != null && (now - lastSwap) < SWAP_COOLDOWN_NANOS) {
            // Within cooldown — don't send, but track the desired profile.
            // The combat text will use the previous template (slightly wrong color)
            // for at most 50ms — imperceptible during rapid combat.
            lastAppliedProfile.put(playerUuid, profile.id());
            return;
        }

        // Send the template swap
        com.hypixel.hytale.protocol.EntityUIComponent packet = templateCache.computeIfAbsent(
            profile.id(), id -> templateRegistry.buildTemplate(profile));

        int vanillaIndex = templateRegistry.getVanillaCombatTextIndex();
        attacker.getPacketHandler().writeNoCache(
            new UpdateEntityUIComponents(
                UpdateType.AddOrUpdate,
                templateRegistry.getVanillaMaxId(),
                Map.of(vanillaIndex, packet)
            )
        );

        lastAppliedProfile.put(playerUuid, profile.id());
        lastSwapTimeNanos.put(playerUuid, now);
        LOGGER.at(Level.FINE).log("Applied combat text template '%s' via global overwrite", profile.id());
    }

    /**
     * Removes cached state for a disconnecting player.
     * Called from player disconnect lifecycle.
     */
    public void removePlayer(@Nonnull UUID playerUuid) {
        lastAppliedProfile.remove(playerUuid);
        lastSwapTimeNanos.remove(playerUuid);
    }
}
