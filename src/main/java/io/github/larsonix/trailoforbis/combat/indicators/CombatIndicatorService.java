package io.github.larsonix.trailoforbis.combat.indicators;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.CombatTextUpdate;
import com.hypixel.hytale.protocol.Vector3d;
import com.hypixel.hytale.protocol.packets.player.DamageInfo;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.server.core.modules.entity.tracker.EntityTrackerSystems;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.TrailOfOrbis;
import io.github.larsonix.trailoforbis.combat.DamageBreakdown;
import io.github.larsonix.trailoforbis.combat.DamageTrace;
import io.github.larsonix.trailoforbis.combat.indicators.color.CombatTextColorManager;
import io.github.larsonix.trailoforbis.combat.deathrecap.CombatSnapshot;
import io.github.larsonix.trailoforbis.combat.detection.DamageTypeClassifier;
import io.github.larsonix.trailoforbis.combat.deathrecap.DeathRecapRecorder;
import io.github.larsonix.trailoforbis.combat.avoidance.AvoidanceProcessor;
import io.github.larsonix.trailoforbis.combat.resolution.CombatEntityResolver;
import io.github.larsonix.trailoforbis.mobs.classification.RPGMobClass;
import io.github.larsonix.trailoforbis.systems.VanillaStatReader;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.logging.Level;

/**
 * Handles combat indicator display (damage numbers, avoidance text).
 *
 * <p>This class extracts indicator logic from RPGDamageSystem, providing:
 * <ul>
 *   <li>Defender screen flash (red vignette)</li>
 *   <li>Attacker floating combat text</li>
 *   <li>Combat text styling</li>
 *   <li>Detailed damage breakdown chat messages</li>
 * </ul>
 */
public class CombatIndicatorService {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final CombatEntityResolver entityResolver;
    private final TrailOfOrbis plugin;
    @Nullable
    private final CombatTextColorManager colorManager;

    /**
     * Creates a new CombatIndicatorService.
     *
     * @param entityResolver The entity resolver for attacker lookups
     * @param plugin The main plugin instance for combat detail toggle
     * @param colorManager The colored combat text manager (nullable if disabled)
     */
    public CombatIndicatorService(
        @Nonnull CombatEntityResolver entityResolver,
        @Nonnull TrailOfOrbis plugin,
        @Nullable CombatTextColorManager colorManager
    ) {
        this.entityResolver = entityResolver;
        this.plugin = plugin;
        this.colorManager = colorManager;
    }

    /**
     * Parameters for combat text display.
     */
    public record CombatTextParams(
        boolean isCrit,
        boolean isBlocked,
        boolean isDodged,
        boolean isParried,
        boolean isMissed
    ) {
        public static CombatTextParams forDamage(boolean isCrit) {
            return new CombatTextParams(isCrit, false, false, false, false);
        }

        public static CombatTextParams forAvoidance(@Nonnull DamageBreakdown.AvoidanceReason reason) {
            return new CombatTextParams(
                false,
                reason == DamageBreakdown.AvoidanceReason.BLOCKED,
                reason == DamageBreakdown.AvoidanceReason.DODGED,
                reason == DamageBreakdown.AvoidanceReason.PARRIED,
                reason == DamageBreakdown.AvoidanceReason.MISSED
            );
        }
    }

    /**
     * Sends damage indicators for RPG damage with full trace for detailed chat.
     *
     * <p>Handles defender screen flash, attacker floating combat text, and
     * detailed traced breakdown in chat (if the attacker has detail mode enabled).
     *
     * @param store The entity store
     * @param defenderRef The defender entity reference
     * @param damage The damage event
     * @param rpgDamage The calculated RPG damage amount
     * @param breakdown The damage breakdown for determining indicator style
     * @param wasParried Whether the attack was parried
     * @param targetInfo Information about the target for chat display
     * @param trace The full damage trace for formula breakdown
     */
    public void sendDamageIndicators(
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> defenderRef,
        @Nonnull Damage damage,
        float rpgDamage,
        @Nonnull DamageBreakdown breakdown,
        boolean wasParried,
        @Nonnull DeathRecapRecorder.AttackerInfo targetInfo,
        @Nonnull DamageTrace trace
    ) {
        // Get attacker reference
        Ref<EntityStore> attackerRef = entityResolver.getAttackerRef(store, damage);

        // Send defender screen flash
        sendDefenderIndicator(store, defenderRef, attackerRef, rpgDamage, DamageTypeClassifier.getDamageCause(damage));

        // Send attacker floating text
        Float hitAngle = damage.getIfPresentMetaObject(Damage.HIT_ANGLE);
        sendAttackerCombatText(
            store, defenderRef, attackerRef, rpgDamage,
            new CombatTextParams(
                breakdown.wasCritical(),
                breakdown.wasBlocked(),
                breakdown.wasDodged(),
                wasParried,
                breakdown.wasMissed()
            ),
            hitAngle,
            breakdown
        );

        // Send detailed breakdown to attacker chat if enabled
        if (attackerRef != null) {
            sendDetailedBreakdownChat(
                store, attackerRef, trace,
                targetInfo.name(), targetInfo.level(), targetInfo.mobClass());
        }
    }

    /**
     * Sends visual damage indicators only (screen flash + floating text).
     *
     * <p>Used for DOT and environmental damage that don't have a full DamageTrace.
     * No chat breakdown is sent.
     *
     * @param store The entity store
     * @param defenderRef The defender entity reference
     * @param damage The damage event
     * @param rpgDamage The calculated RPG damage amount
     * @param breakdown The damage breakdown for determining indicator style
     * @param wasParried Whether the attack was parried
     */
    public void sendDamageIndicatorsVisualOnly(
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> defenderRef,
        @Nonnull Damage damage,
        float rpgDamage,
        @Nonnull DamageBreakdown breakdown,
        boolean wasParried
    ) {
        Ref<EntityStore> attackerRef = entityResolver.getAttackerRef(store, damage);

        sendDefenderIndicator(store, defenderRef, attackerRef, rpgDamage, DamageTypeClassifier.getDamageCause(damage));

        Float hitAngle = damage.getIfPresentMetaObject(Damage.HIT_ANGLE);
        sendAttackerCombatText(
            store, defenderRef, attackerRef, rpgDamage,
            new CombatTextParams(
                breakdown.wasCritical(),
                breakdown.wasBlocked(),
                breakdown.wasDodged(),
                wasParried,
                breakdown.wasMissed()
            ),
            hitAngle,
            breakdown
        );
    }

    /**
     * Sends avoidance indicators (Blocked, Dodged, etc.) when damage is avoided.
     *
     * @param store The entity store
     * @param defenderRef The defender entity reference
     * @param damage The damage event
     * @param reason The avoidance reason
     */
    public void sendAvoidanceIndicators(
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> defenderRef,
        @Nonnull Damage damage,
        @Nonnull DamageBreakdown.AvoidanceReason reason
    ) {
        // Get attacker reference
        Ref<EntityStore> attackerRef = entityResolver.getAttackerRef(store, damage);

        // Send attacker floating text with avoidance message
        Float hitAngle = damage.getIfPresentMetaObject(Damage.HIT_ANGLE);
        sendAttackerCombatText(
            store, defenderRef, attackerRef, 0f,
            CombatTextParams.forAvoidance(reason),
            hitAngle,
            null
        );
    }

    /**
     * Sends red screen flash (DamageInfo packet) to the defender if they're a player.
     *
     * @param store The entity store
     * @param defenderRef The defender entity reference
     * @param attackerRef The attacker entity reference (for position)
     * @param damage The damage amount (for intensity)
     * @param cause The damage cause (for packet)
     */
    public void sendDefenderIndicator(
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> defenderRef,
        @Nullable Ref<EntityStore> attackerRef,
        float damage,
        @Nullable DamageCause cause
    ) {
        if (attackerRef == null || cause == null) {
            return;
        }

        PlayerRef defender = store.getComponent(defenderRef, PlayerRef.getComponentType());
        if (defender == null) {
            return;
        }

        // Check if damage is below the minimum threshold (% of max HP) to trigger alert
        float actualMaxHP = 100f;
        EntityStatMap statMap = store.getComponent(defenderRef, EntityStatMap.getComponentType());
        if (statMap != null) {
            EntityStatValue healthStat = statMap.get(DefaultEntityStatTypes.getHealth());
            if (healthStat != null) {
                actualMaxHP = healthStat.getMax();
            }
        }

        float damagePercent = (damage / actualMaxHP) * 100f;
        float threshold = plugin.getConfigManager().getRPGConfig().getCombat().getHealthAlertMinThreshold();
        if (damagePercent < threshold) {
            LOGGER.at(Level.FINE).log("INDICATOR SKIPPED for defender %s: %.1f damage (%.1f%% < %.1f%% threshold)",
                defender.getUsername(), damage, damagePercent, threshold);
            return;
        }

        // Scale RPG damage to vanilla-equivalent for the client's health alert system
        float vanillaBaseHP = VanillaStatReader.getBaseHealth(store, defenderRef);
        float scaledDamage = (damage / actualMaxHP) * vanillaBaseHP;

        TransformComponent transform = store.getComponent(attackerRef, TransformComponent.getComponentType());
        if (transform == null) {
            return;
        }

        com.hypixel.hytale.math.vector.Vector3d pos = transform.getPosition();
        Vector3d packetPos = new Vector3d(pos.x, pos.y, pos.z);
        DamageInfo packet = new DamageInfo(packetPos, scaledDamage, cause.toPacket());
        defender.getPacketHandler().writeNoCache(packet);

        LOGGER.at(Level.FINE).log("INDICATOR SENT to defender %s: %.1f RPG → %.1f scaled (%.1f%% of max HP)",
            defender.getUsername(), damage, scaledDamage, damagePercent);
    }

    /**
     * Queues floating combat text for the attacker to see above the defender.
     *
     * <p>If a {@link CombatTextColorManager} is active, applies a per-entity template
     * swap BEFORE queuing the text so the attacker sees colored damage numbers.
     *
     * @param store The entity store
     * @param defenderRef The defender entity reference (where text appears)
     * @param attackerRef The attacker entity reference (who sees it)
     * @param damage The damage amount
     * @param params The combat text parameters
     * @param hitAngle The hit angle for animation (may be null)
     * @param breakdown The damage breakdown for color resolution (nullable for simple cases)
     */
    public void sendAttackerCombatText(
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> defenderRef,
        @Nullable Ref<EntityStore> attackerRef,
        float damage,
        @Nonnull CombatTextParams params,
        @Nullable Float hitAngle,
        @Nullable DamageBreakdown breakdown
    ) {
        if (attackerRef == null) {
            return;
        }

        PlayerRef attacker = store.getComponent(attackerRef, PlayerRef.getComponentType());
        if (attacker == null || !attacker.isValid()) {
            return;
        }

        EntityTrackerSystems.EntityViewer entityViewer =
            store.getComponent(attackerRef, EntityTrackerSystems.EntityViewer.getComponentType());
        if (entityViewer == null || !entityViewer.visible.contains(defenderRef)) {
            return;
        }

        // Apply colored template swap before queuing combat text
        if (colorManager != null && colorManager.isEnabled()) {
            colorManager.applyAndResolve(store, defenderRef, entityViewer, attacker, breakdown, params);
        }

        // Format text based on damage context
        String damageText;
        if (params.isMissed()) {
            damageText = "Miss";
        } else if (params.isDodged()) {
            damageText = "Dodged";
        } else if (params.isBlocked()) {
            damageText = "Blocked";
        } else if (params.isParried()) {
            damageText = "Parried";
        } else {
            damageText = String.valueOf((int) Math.floor(damage));
            if (params.isCrit()) {
                damageText = damageText + " !";
            }
        }

        // Build and queue CombatText update
        CombatTextUpdate combatTextUpdate = new CombatTextUpdate(
            hitAngle != null ? hitAngle : 0f,
            damageText
        );

        entityViewer.queueUpdate(defenderRef, combatTextUpdate);

        String statusLabel = params.isMissed() ? " (MISSED)" : params.isDodged() ? " (DODGED)" :
            params.isBlocked() ? " (BLOCKED)" : params.isParried() ? " (PARRIED)" : "";
        String critLabel = params.isCrit() ? " (CRIT!)" : "";
        LOGGER.at(Level.FINE).log("COMBAT TEXT queued for attacker %s: %s%s%s",
            attacker.getUsername(), combatTextUpdate.text, critLabel, statusLabel);
    }

    /**
     * Sends traced damage breakdown to attacker chat with full formula detail.
     *
     * @param store The entity store
     * @param attackerRef The attacker entity reference
     * @param trace The full damage trace
     * @param targetName The target's display name
     * @param targetLevel The target's level
     * @param targetClass The target's mob class (null for players)
     */
    private void sendDetailedBreakdownChat(
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> attackerRef,
        @Nonnull DamageTrace trace,
        @Nonnull String targetName,
        int targetLevel,
        @Nullable RPGMobClass targetClass
    ) {
        PlayerRef attacker = store.getComponent(attackerRef, PlayerRef.getComponentType());
        if (attacker == null || !attacker.isValid()) {
            return;
        }

        // Check if player has detail mode enabled
        if (!plugin.isCombatDetailEnabled(attacker.getUuid())) {
            return;
        }

        Message message = CombatLogFormatter.formatDamageDealt(trace, targetName, targetLevel, targetClass);
        attacker.sendMessage(message);
    }

    /**
     * Sends spell damage indicators to the defender.
     *
     * <p>Spell damage via {@code EnvironmentSource} has no attacker entity, so:
     * <ul>
     *   <li>Screen flash uses the defender's own position as damage origin</li>
     *   <li>No floating combat text (no attacker player to show it to)</li>
     * </ul>
     *
     * <p>The damage cause index should be set on the {@link Damage} object before
     * calling this method (done in {@code handleSpellDamage}) to ensure Hytale's
     * built-in damage text renders with the correct element color.
     *
     * @param store The entity store
     * @param defenderRef The defender entity reference
     * @param rpgDamage The calculated RPG damage amount
     * @param cause The damage cause (for screen flash packet)
     */
    public void sendSpellDamageIndicator(
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> defenderRef,
        float rpgDamage,
        @Nullable DamageCause cause
    ) {
        if (cause == null || rpgDamage <= 0) {
            return;
        }

        // Get defender position for the damage info packet
        TransformComponent transform = store.getComponent(defenderRef, TransformComponent.getComponentType());
        if (transform == null) {
            return;
        }

        com.hypixel.hytale.math.vector.Vector3d pos = transform.getPosition();
        Vector3d packetPos = new Vector3d(pos.x, pos.y + 1.0, pos.z);

        // If defender is a player, send screen flash to them
        PlayerRef defender = store.getComponent(defenderRef, PlayerRef.getComponentType());
        if (defender != null) {
            float actualMaxHP = 100f;
            EntityStatMap statMap = store.getComponent(defenderRef, EntityStatMap.getComponentType());
            if (statMap != null) {
                EntityStatValue healthStat = statMap.get(DefaultEntityStatTypes.getHealth());
                if (healthStat != null) {
                    actualMaxHP = healthStat.getMax();
                }
            }
            float vanillaBaseHP = VanillaStatReader.getBaseHealth(store, defenderRef);
            float scaledDamage = (rpgDamage / actualMaxHP) * vanillaBaseHP;
            DamageInfo packet = new DamageInfo(packetPos, scaledDamage, cause.toPacket());
            defender.getPacketHandler().writeNoCache(packet);
        }

        LOGGER.at(Level.FINE).log("SPELL INDICATOR sent for defender: %.1f RPG damage", rpgDamage);
    }

    /**
     * Sends damage received log to the defender with full traced breakdown.
     *
     * @param store The entity store
     * @param defenderRef The defender entity reference
     * @param snapshot The combat snapshot containing all damage data
     * @param trace The full damage trace
     */
    public void sendDamageReceivedLog(
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> defenderRef,
        @Nonnull CombatSnapshot snapshot,
        @Nonnull DamageTrace trace
    ) {
        PlayerRef defender = store.getComponent(defenderRef, PlayerRef.getComponentType());
        if (defender == null || !defender.isValid()) {
            return;
        }

        // Check if player has detail mode enabled
        if (!plugin.isCombatDetailEnabled(defender.getUuid())) {
            return;
        }

        Message message = CombatLogFormatter.formatDamageReceived(trace, snapshot);
        defender.sendMessage(message);
    }

    /**
     * Sends avoidance log to the defender if they have detail mode enabled.
     *
     * <p>Shows the defender that they avoided an attack (dodged, blocked, parried, missed).
     *
     * @param store The entity store
     * @param defenderRef The defender entity reference
     * @param reason The avoidance reason
     * @param attackerInfo Information about the attacker
     * @param estimatedDamage Estimated damage that would have been dealt
     */
    public void sendAvoidanceLog(
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> defenderRef,
        @Nonnull DamageBreakdown.AvoidanceReason reason,
        @Nonnull DeathRecapRecorder.AttackerInfo attackerInfo,
        float estimatedDamage,
        @Nonnull AvoidanceProcessor.AvoidanceDetail avoidanceStats
    ) {
        PlayerRef defender = store.getComponent(defenderRef, PlayerRef.getComponentType());
        if (defender == null || !defender.isValid()) {
            return;
        }

        // Check if player has detail mode enabled
        if (!plugin.isCombatDetailEnabled(defender.getUuid())) {
            return;
        }

        // Use the formatter for avoidance message
        Message message = CombatLogFormatter.formatAvoidance(
            reason,
            attackerInfo.name(),
            attackerInfo.level(),
            attackerInfo.mobClass(),
            estimatedDamage,
            avoidanceStats);
        defender.sendMessage(message);
    }
}
