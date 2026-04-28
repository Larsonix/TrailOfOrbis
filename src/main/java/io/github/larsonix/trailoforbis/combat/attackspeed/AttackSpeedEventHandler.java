package io.github.larsonix.trailoforbis.combat.attackspeed;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.event.EventPriority;
import com.hypixel.hytale.event.EventRegistry;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.MouseButtonEvent;
import com.hypixel.hytale.protocol.MouseButtonState;
import com.hypixel.hytale.protocol.MouseButtonType;
import com.hypixel.hytale.server.core.entity.InteractionManager;
import com.hypixel.hytale.server.core.event.events.player.PlayerMouseButtonEvent;
import com.hypixel.hytale.server.core.modules.interaction.InteractionModule;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.TrailOfOrbis;
import io.github.larsonix.trailoforbis.attributes.ComputedStats;
import io.github.larsonix.trailoforbis.combat.InteractionSpeedSystem;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;

/**
 * Event-driven attack speed system that modifies weapon cooldowns.
 *
 * <p><b>Architecture:</b>
 * <pre>
 * Player Left-Clicks
 *     ↓
 * PlayerMouseButtonEvent fires (NORMAL priority)
 *     ↓
 * Vanilla creates attack cooldown in CooldownHandler
 *     ↓
 * PlayerMouseButtonEvent fires (LATE priority) ← THIS HANDLER
 *     ↓
 * Modifies the cooldown that was just created
 * </pre>
 *
 * <p>This solves the lazy cooldown creation problem: the old approach tried to
 * modify cooldowns in {@code StatsApplicationSystem}, but cooldowns don't exist
 * until the player actually attacks. By hooking at LATE priority, we run after
 * vanilla creates the cooldown.
 *
 * <p><b>WARNING:</b> Uses reflection into Hytale internals. May break on updates.
 */
public class AttackSpeedEventHandler {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final TrailOfOrbis plugin;
    private final AttackSpeedConfig config;

    // Cached reflection fields — initialized once on first use
    private static Field cooldownHandlerField;
    private static Field cooldownsMapField;
    private static boolean reflectionInitialized = false;
    private static boolean reflectionAvailable = false;

    public AttackSpeedEventHandler(TrailOfOrbis plugin, AttackSpeedConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    /**
     * Registers this handler with the event registry.
     *
     * @param eventRegistry The plugin's event registry
     */
    public void register(EventRegistry eventRegistry) {
        if (!config.enabled()) {
            LOGGER.atInfo().log("Attack speed event handler disabled in config");
            return;
        }

        // Initialize reflection fields lazily
        initializeReflection();

        if (!reflectionAvailable) {
            LOGGER.atWarning().log(
                    "Attack speed event handler disabled - reflection not available. " +
                    "This may be caused by a Hytale update changing internal field names."
            );
            return;
        }

        // Register at LATE priority to run AFTER vanilla creates cooldowns
        eventRegistry.register(
                EventPriority.LATE,
                PlayerMouseButtonEvent.class,
                this::onPlayerMouseButton
        );

        LOGGER.atInfo().log(
                "Attack speed event handler registered (priority: LATE, maxBonus: %.0f%%, minCooldown: %.3fs)",
                config.maxBonus(), config.minCooldown()
        );
    }

    /**
     * Handles mouse button events to modify attack cooldowns.
     */
    private void onPlayerMouseButton(PlayerMouseButtonEvent event) {
        // Only process left-click press (attacks)
        MouseButtonEvent mouseButton = event.getMouseButton();
        if (mouseButton.mouseButtonType != MouseButtonType.Left) {
            return;
        }
        if (mouseButton.state != MouseButtonState.Pressed) {
            return;
        }

        PlayerRef playerRef = event.getPlayerRefComponent();
        UUID uuid = playerRef.getUuid();

        // Get player's attack speed stat
        ComputedStats stats = plugin.getAttributeManager().getStats(uuid);
        if (stats == null) {
            return;
        }

        float attackSpeed = stats.getAttackSpeedPercent();

        // No modification needed if attack speed is zero
        if (Math.abs(attackSpeed) < 0.001f) {
            return;
        }

        // Clamp attack speed to configured bounds
        float clampedSpeed = config.clampAttackSpeed(attackSpeed);

        // Apply to cooldowns via reflection
        applyCooldownModification(event, clampedSpeed);
    }

    /**
     * Applies the attack speed modification to active cooldowns.
     */
    private void applyCooldownModification(PlayerMouseButtonEvent event, float attackSpeed) {
        if (!reflectionAvailable) {
            return;
        }

        Ref<EntityStore> ref = event.getPlayerRef();
        if (ref == null || !ref.isValid()) {
            return;
        }

        Store<EntityStore> store = ref.getStore();

        try {
            InteractionManager interactionManager = store.getComponent(
                    ref, InteractionModule.get().getInteractionManagerComponent()
            );
            if (interactionManager == null) {
                return;
            }

            CooldownHandler handler = (CooldownHandler) cooldownHandlerField.get(interactionManager);
            if (handler == null) {
                return;
            }

            @SuppressWarnings("unchecked")
            Map<String, CooldownHandler.Cooldown> cooldowns =
                    (Map<String, CooldownHandler.Cooldown>) cooldownsMapField.get(handler);
            if (cooldowns == null || cooldowns.isEmpty()) {
                return;
            }

            // Modify all active cooldowns
            // The attack just triggered at NORMAL priority should have created/reset a cooldown
            for (CooldownHandler.Cooldown cooldown : cooldowns.values()) {
                float baseCooldown = cooldown.getCooldown();

                // Use InteractionSpeedSystem's formula with our config's max
                float scaledCooldown = InteractionSpeedSystem.calculateScaledCooldown(
                        baseCooldown, attackSpeed, (float) config.maxBonus()
                );

                // Enforce minimum cooldown from config
                scaledCooldown = Math.max(scaledCooldown, config.minCooldown());

                // Only update if cooldown actually changed
                if (Float.compare(scaledCooldown, baseCooldown) != 0) {
                    cooldown.setCooldownMax(scaledCooldown);

                    if (plugin.getConfigManager().getRPGConfig().isDebugMode()) {
                        LOGGER.atFine().log(
                                "Modified cooldown: %.3fs -> %.3fs (%.1f%% attack speed)",
                                baseCooldown, scaledCooldown, attackSpeed
                        );
                    }
                }
            }
        } catch (IllegalAccessException e) {
            LOGGER.atWarning().withCause(e).log("Failed to access cooldown fields");
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Unexpected error applying attack speed modification");
        }
    }

    /**
     * Initializes reflection fields for accessing Hytale's cooldown internals.
     */
    private static synchronized void initializeReflection() {
        if (reflectionInitialized) {
            return;
        }

        reflectionInitialized = true;

        try {
            cooldownHandlerField = InteractionManager.class.getDeclaredField("cooldownHandler");
            cooldownHandlerField.setAccessible(true);

            cooldownsMapField = CooldownHandler.class.getDeclaredField("cooldowns");
            cooldownsMapField.setAccessible(true);

            reflectionAvailable = true;
            LOGGER.atFine().log("Attack speed reflection initialized successfully");
        } catch (NoSuchFieldException e) {
            LOGGER.atWarning().log(
                    "Failed to find cooldown reflection fields — attack speed will have no effect. " +
                    "This may be caused by a Hytale update changing internal field names."
            );
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Reflection initialization failed");
        }
    }

    /**
     * @return true if cooldown modification will work
     */
    public static boolean isReflectionAvailable() {
        initializeReflection();
        return reflectionAvailable;
    }
}
