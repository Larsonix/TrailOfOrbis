package io.github.larsonix.trailoforbis.combat.indicators.color;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.UIComponentsUpdate;
import com.hypixel.hytale.protocol.UpdateType;
import com.hypixel.hytale.protocol.packets.assets.UpdateEntityUIComponents;
import com.hypixel.hytale.server.core.modules.entity.tracker.EntityTrackerSystems;
import com.hypixel.hytale.server.core.modules.entityui.UIComponentList;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.combat.DamageBreakdown;
import io.github.larsonix.trailoforbis.combat.indicators.CombatIndicatorService.CombatTextParams;
import io.github.larsonix.trailoforbis.config.ConfigManager;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

            // Register templates
            templateRegistry = new CombatTextTemplateRegistry();
            List<CombatTextProfile> profileList = new ArrayList<>(profileMap.values());

            if (!templateRegistry.initialize(profileList)) {
                LOGGER.atWarning().log("Template registration failed — colored combat text disabled");
                return false;
            }

            // Rebuild profile map with assigned template indices
            Map<String, CombatTextProfile> registeredProfiles = new java.util.LinkedHashMap<>();
            for (CombatTextProfile profile : profileList) {
                int index = templateRegistry.getTemplateIndex(profile.id());
                CombatTextProfile registered = profile.withTemplateIndex(index);
                registeredProfiles.put(registered.id(), registered);
            }

            // Create resolver with registered profiles
            profileResolver = new CombatTextProfileResolver(registeredProfiles);

            enabled = true;
            LOGGER.atInfo().log("Colored combat text initialized: %d profiles", registeredProfiles.size());
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
     * <p>Call from {@code PlayerReadyEvent} handler after the player is in-world.
     *
     * @param player The player to sync templates to
     */
    public void onPlayerReady(@Nonnull PlayerRef player) {
        if (!enabled || templateRegistry == null) return;
        templateRegistry.syncToPlayer(player);
    }

    /**
     * Resolves the appropriate color profile and applies the template swap.
     *
     * <p>This is the single integration point called by {@code CombatIndicatorService}
     * BEFORE queuing {@code CombatTextUpdate}. It:
     * <ol>
     *   <li>Resolves which profile matches the damage event</li>
     *   <li>Reads the defender entity's {@code UIComponentList}</li>
     *   <li>Clones the component IDs and swaps the CombatText index</li>
     *   <li>Queues a {@code UIComponentsUpdate} on the attacker's viewer</li>
     * </ol>
     *
     * @param store The entity store
     * @param defenderRef The entity the combat text appears on
     * @param attackerViewer The attacker's entity viewer (who sees the text)
     * @param attacker The attacker player ref (for fallback template overwrite)
     * @param breakdown The damage breakdown for profile resolution
     * @param params Combat text flags (crit, avoidance)
     * @return The resolved profile (for text formatting), or null if no color applied
     */
    @Nullable
    public CombatTextProfile applyAndResolve(
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> defenderRef,
        @Nonnull EntityTrackerSystems.EntityViewer attackerViewer,
        @Nonnull PlayerRef attacker,
        @Nullable DamageBreakdown breakdown,
        @Nonnull CombatTextParams params
    ) {
        if (!enabled || profileResolver == null || templateRegistry == null) return null;

        try {
            // 1. Resolve profile
            CombatTextProfile profile = profileResolver.resolve(breakdown, params);
            if (profile == null || !profile.isRegistered()) return null;

            // 2. Try per-entity template swap (preferred approach)
            boolean applied = tryPerEntitySwap(store, defenderRef, attackerViewer, profile);

            // 3. Fallback: global template definition overwrite (for entities without UIComponentList)
            if (!applied) {
                applyTemplateOverwriteFallback(attacker, profile);
            }

            return profile;
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to apply colored combat text — falling back to vanilla");
            return null;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void shutdown() {
        enabled = false;
    }

    // ── Per-entity template swap (Approach B) ────────────────────────────

    /**
     * Swaps the CombatText template index in the defender's UIComponentList
     * for the attacker's viewer only.
     *
     * @return true if the swap was applied, false if UIComponentList unavailable
     */
    private boolean tryPerEntitySwap(
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> defenderRef,
        @Nonnull EntityTrackerSystems.EntityViewer attackerViewer,
        @Nonnull CombatTextProfile profile
    ) {
        UIComponentList uiComponentList = store.getComponent(defenderRef, UIComponentList.getComponentType());
        if (uiComponentList == null) return false;

        int[] currentIds = uiComponentList.getComponentIds();
        if (currentIds == null || currentIds.length == 0) return false;

        // Find the CombatText position in the component IDs array
        int combatTextPos = findCombatTextPosition(currentIds);
        if (combatTextPos == -1) return false;

        // Clone and swap
        int[] modifiedIds = currentIds.clone();
        modifiedIds[combatTextPos] = profile.templateIndex();

        // Send UIComponentsUpdate to the attacker's viewer for the defender entity
        attackerViewer.queueUpdate(defenderRef, new UIComponentsUpdate(modifiedIds));

        LOGGER.at(Level.FINE).log("Applied combat text template '%s' (index %d) to entity via per-entity swap",
            profile.id(), profile.templateIndex());

        return true;
    }

    /**
     * Finds the position in the componentIds array that holds the CombatText template.
     *
     * <p>Looks for either the vanilla CombatText index or any of our custom indices
     * (in case a previous hit already swapped it).
     *
     * @return Position index, or -1 if not found
     */
    private int findCombatTextPosition(@Nonnull int[] componentIds) {
        int vanillaIndex = templateRegistry.getVanillaCombatTextIndex();
        Set<Integer> customIndices = templateRegistry.getAllRegisteredIndices();

        for (int i = 0; i < componentIds.length; i++) {
            if (componentIds[i] == vanillaIndex || customIndices.contains(componentIds[i])) {
                return i;
            }
        }
        return -1;
    }

    // ── Template overwrite fallback (Approach A) ─────────────────────────

    /**
     * Falls back to overwriting the global CombatText template definition
     * on the attacker's client. Less precise (affects all entities), but works
     * when the entity has no UIComponentList.
     */
    private void applyTemplateOverwriteFallback(
        @Nonnull PlayerRef attacker,
        @Nonnull CombatTextProfile profile
    ) {
        int vanillaIndex = templateRegistry.getVanillaCombatTextIndex();

        attacker.getPacketHandler().writeNoCache(
            new UpdateEntityUIComponents(
                UpdateType.AddOrUpdate,
                templateRegistry.getMaxId(),
                Map.of(vanillaIndex, buildTemplatePacket(profile))
            )
        );

        LOGGER.at(Level.FINE).log("Applied combat text template '%s' via global overwrite fallback", profile.id());
    }

    /**
     * Builds a protocol template packet for the overwrite fallback.
     * Re-creates from profile since the registry's stored packets are at custom indices.
     */
    @Nonnull
    private com.hypixel.hytale.protocol.EntityUIComponent buildTemplatePacket(@Nonnull CombatTextProfile profile) {
        // Clone the vanilla template and apply this profile's visual properties.
        // Used for the fallback path where we overwrite the global template definition.
        var serverAssetMap = com.hypixel.hytale.server.core.modules.entityui.asset.EntityUIComponent.getAssetMap();
        var vanillaAsset = serverAssetMap.getAsset(templateRegistry.getVanillaCombatTextIndex());
        if (vanillaAsset == null) return new com.hypixel.hytale.protocol.EntityUIComponent();

        com.hypixel.hytale.protocol.EntityUIComponent packet = vanillaAsset.toPacket();
        packet.combatTextColor = profile.color();
        packet.combatTextFontSize = profile.fontSize();
        packet.combatTextDuration = profile.duration();
        packet.combatTextHitAngleModifierStrength = profile.hitAngleModifierStrength();

        if (profile.animations().length > 0) {
            packet.combatTextAnimationEvents = CombatTextTemplateRegistry.convertAnimations(profile.animations());
        }

        return packet;
    }
}
