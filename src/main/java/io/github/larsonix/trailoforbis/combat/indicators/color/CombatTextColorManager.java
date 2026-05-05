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
import java.util.List;
import java.util.Map;
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
     */
    private void applyTemplateOverwrite(
        @Nonnull PlayerRef attacker,
        @Nonnull CombatTextProfile profile
    ) {
        int vanillaIndex = templateRegistry.getVanillaCombatTextIndex();
        com.hypixel.hytale.protocol.EntityUIComponent packet = templateRegistry.buildTemplate(profile);

        attacker.getPacketHandler().writeNoCache(
            new UpdateEntityUIComponents(
                UpdateType.AddOrUpdate,
                templateRegistry.getVanillaMaxId(),
                Map.of(vanillaIndex, packet)
            )
        );

        LOGGER.at(Level.FINE).log("Applied combat text template '%s' via global overwrite", profile.id());
    }
}
