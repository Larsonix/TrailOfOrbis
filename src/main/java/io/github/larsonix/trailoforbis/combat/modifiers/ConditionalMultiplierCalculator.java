package io.github.larsonix.trailoforbis.combat.modifiers;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.ailments.AilmentTracker;
import io.github.larsonix.trailoforbis.ailments.AilmentType;
import io.github.larsonix.trailoforbis.attributes.ComputedStats;
import io.github.larsonix.trailoforbis.combat.resolution.CombatEntityResolver;
import io.github.larsonix.trailoforbis.combat.tracking.ConsecutiveHitTracker;
import io.github.larsonix.trailoforbis.maps.components.RealmMobComponent;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Calculates conditional damage multipliers.
 *
 * <p>This class extracts conditional multiplier logic from RPGDamageSystem,
 * combining multiple situational bonuses:
 * <ul>
 *   <li>Realm damage multiplier (MONSTER_DAMAGE modifier)</li>
 *   <li>Execute bonus (vs low HP targets, &lt; 35%)</li>
 *   <li>Damage vs Frozen (defender has freeze ailment)</li>
 *   <li>Damage vs Shocked (defender has shock ailment)</li>
 *   <li>Damage at Low Life (attacker HP ≤ 35%)</li>
 * </ul>
 */
public class ConditionalMultiplierCalculator {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /** Effect ID for burn ailment (single native effect) */
    private static final String BURN_EFFECT_ID = "rpg_ailment_burn";

    /** Effect ID for shock ailment (single native effect) */
    private static final String SHOCK_EFFECT_ID = "rpg_ailment_shock";

    private final CombatEntityResolver entityResolver;
    private final AilmentTracker ailmentTracker;
    private final ConsecutiveHitTracker consecutiveHitTracker;

    /**
     * Maximum damage amplification multiplier from Shock (our pipeline).
     * <p>
     * When Hexcode is present, Erode (their pipeline) stacks multiplicatively with
     * Shock (our pipeline). This cap limits the Shock multiplier to prevent the
     * combined effect from becoming excessive. Default: {@link Float#MAX_VALUE} (no cap).
     */
    private volatile float maxShockAmplification = Float.MAX_VALUE;

    /** Cached native effect index for shock. Resolved lazily on first use. */
    private volatile int shockEffectIndex = Integer.MIN_VALUE;
    private volatile boolean shockIndexResolved = false;

    /** Cached native effect index for burn. Resolved lazily on first use. */
    private volatile int burnEffectIndex = Integer.MIN_VALUE;
    private volatile boolean burnIndexResolved = false;

    /**
     * Creates a new ConditionalMultiplierCalculator.
     *
     * @param entityResolver The entity resolver for attacker lookups
     * @param ailmentTracker The ailment tracker for status checks (may be null)
     */
    public ConditionalMultiplierCalculator(
        @Nonnull CombatEntityResolver entityResolver,
        @Nullable AilmentTracker ailmentTracker
    ) {
        this(entityResolver, ailmentTracker, null);
    }

    /**
     * Creates a new ConditionalMultiplierCalculator with consecutive hit tracking.
     *
     * @param entityResolver         The entity resolver for attacker lookups
     * @param ailmentTracker         The ailment tracker for status checks (may be null)
     * @param consecutiveHitTracker  The consecutive hit tracker (may be null)
     */
    public ConditionalMultiplierCalculator(
        @Nonnull CombatEntityResolver entityResolver,
        @Nullable AilmentTracker ailmentTracker,
        @Nullable ConsecutiveHitTracker consecutiveHitTracker
    ) {
        this.entityResolver = entityResolver;
        this.ailmentTracker = ailmentTracker;
        this.consecutiveHitTracker = consecutiveHitTracker;
    }

    /**
     * Sets the maximum Shock amplification multiplier.
     *
     * <p>When Hexcode's Erode stacks with our Shock, the combined amplification
     * can become excessive. This cap limits the Shock multiplier from our side.
     * Example: 2.0 means Shock can at most double the damage.
     *
     * @param maxAmplification The maximum multiplier (e.g., 2.0 for 200%)
     */
    public void setMaxShockAmplification(float maxAmplification) {
        this.maxShockAmplification = Math.max(1.0f, maxAmplification);
    }

    /**
     * Calculates the combined conditional damage multiplier.
     *
     * @param store The entity store
     * @param damage The damage event
     * @param attackerStats The attacker's computed stats (may be null)
     * @param defenderHealthPercent The defender's current health percentage (0-1)
     * @param defenderUuid The defender's UUID for ailment lookup
     * @param defenderRef The defender's entity reference for native effect checks (may be null)
     * @return Combined multiplier (1.0 = no bonus)
     */
    public float calculate(
        @Nonnull Store<EntityStore> store,
        @Nonnull Damage damage,
        @Nullable ComputedStats attackerStats,
        float defenderHealthPercent,
        @Nullable UUID defenderUuid,
        @Nullable Ref<EntityStore> defenderRef
    ) {
        return calculateDetailed(store, damage, attackerStats, defenderHealthPercent, defenderUuid, defenderRef).combined();
    }

    /**
     * Calculates conditional damage multipliers with individual breakdowns.
     *
     * <p>Returns a {@link ConditionalResult} exposing each conditional multiplier
     * individually (realm, execute, vsFrozen, vsShocked, lowLife) along with the
     * combined product. Used by the combat detail trace system.
     *
     * <p>Ailment checks use Hytale's native {@link EffectControllerComponent#hasEffect(int)}
     * for shock (single effect ID), falling back to {@link AilmentTracker#hasAilment}
     * for freeze (multiple effect IDs by slow percentage).
     *
     * @param store The entity store
     * @param damage The damage event
     * @param attackerStats The attacker's computed stats (may be null)
     * @param defenderHealthPercent The defender's current health percentage (0-1)
     * @param defenderUuid The defender's UUID for ailment lookup
     * @param defenderRef The defender's entity reference for native effect checks (may be null)
     * @return Detailed result with individual and combined multipliers
     */
    @Nonnull
    public ConditionalResult calculateDetailed(
        @Nonnull Store<EntityStore> store,
        @Nonnull Damage damage,
        @Nullable ComputedStats attackerStats,
        float defenderHealthPercent,
        @Nullable UUID defenderUuid,
        @Nullable Ref<EntityStore> defenderRef
    ) {
        // Realm damage multiplier (from MONSTER_DAMAGE modifier)
        float realmMult = getRealmDamageMultiplier(store, damage);
        if (realmMult != 1.0f) {
            LOGGER.at(Level.FINE).log("Conditional: realm damage x%.2f", realmMult);
        }

        if (attackerStats == null) {
            if (realmMult != 1.0f) {
                return new ConditionalResult(realmMult, realmMult, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f);
            }
            return ConditionalResult.NONE;
        }

        // Execute bonus (vs low HP targets)
        float executeMult = getExecuteMultiplier(attackerStats, defenderHealthPercent);

        // Ailment-based bonuses — compute individually using native hasEffect()
        float vsFrozenMult = 1.0f;
        float vsShockedMult = 1.0f;
        if (defenderUuid != null) {
            // Get native effect controller for hasEffect() checks
            EffectControllerComponent effectController = getNativeEffectController(store, defenderRef);

            // Freeze: multiple native effect IDs (rpg_ailment_freeze_5 through _30),
            // so fall back to ailmentTracker which tracks freeze state centrally
            if (ailmentTracker != null && ailmentTracker.hasAilment(defenderUuid, AilmentType.FREEZE)) {
                float bonusPct = attackerStats.getDamageVsFrozenPercent();
                if (bonusPct > 0) {
                    vsFrozenMult = 1.0f + bonusPct / 100f;
                    LOGGER.at(Level.FINE).log("Conditional: vs Frozen +%.0f%%", bonusPct);
                }
            }

            // Shock: single native effect ID — use hasEffect() for authoritative check
            if (hasNativeShockEffect(effectController, defenderUuid)) {
                float bonusPct = attackerStats.getDamageVsShockedPercent();
                if (bonusPct > 0) {
                    vsShockedMult = 1.0f + bonusPct / 100f;
                    // Cap Shock amplification to prevent excessive stacking with Hexcode's Erode
                    if (vsShockedMult > maxShockAmplification) {
                        LOGGER.at(Level.FINE).log("Conditional: vs Shocked capped from %.2f to %.2f",
                            vsShockedMult, maxShockAmplification);
                        vsShockedMult = maxShockAmplification;
                    }
                    LOGGER.at(Level.FINE).log("Conditional: vs Shocked +%.0f%% (native hasEffect)", bonusPct);
                }
            }
        }

        // Damage at low life (attacker HP ≤ 35%)
        float lowLifeMult = getLowLifeMultiplier(attackerStats, store, damage);

        // Consecutive hit bonus (stacking per hit within 2s window)
        float consecutiveMult = 1.0f;
        if (consecutiveHitTracker != null) {
            float bonusPerHit = attackerStats.getConsecutiveHitBonus();
            if (bonusPerHit > 0) {
                UUID attackerUuid = entityResolver.getAttackerUuid(store, damage);
                if (attackerUuid != null) {
                    consecutiveHitTracker.recordHit(attackerUuid);
                    consecutiveMult = consecutiveHitTracker.getMultiplier(attackerUuid, bonusPerHit);
                    if (consecutiveMult != 1.0f) {
                        LOGGER.at(Level.FINE).log("Conditional: consecutive hits x%.2f (bonus=%.0f%%/hit)",
                            consecutiveMult, bonusPerHit);
                    }
                }
            }
        }

        float combined = realmMult * executeMult * vsFrozenMult * vsShockedMult * lowLifeMult * consecutiveMult;

        return new ConditionalResult(combined, realmMult, executeMult, vsFrozenMult, vsShockedMult, lowLifeMult, consecutiveMult);
    }

    /**
     * Gets the realm damage multiplier for the attacker, if they are a realm mob.
     *
     * <p>This multiplier comes from the {@code MONSTER_DAMAGE} realm modifier
     * and is stored in {@link RealmMobComponent#getDamageMultiplier()}.
     *
     * @param store The entity store
     * @param damage The damage event
     * @return The damage multiplier (1.0 if attacker is not a realm mob or no modifier)
     */
    public float getRealmDamageMultiplier(
        @Nonnull Store<EntityStore> store,
        @Nonnull Damage damage
    ) {
        // Check if damage came from an entity
        if (!(damage.getSource() instanceof Damage.EntitySource entitySource)) {
            return 1.0f;
        }

        // Get immediate attacker entity reference
        Ref<EntityStore> immediateRef = entitySource.getRef();
        if (immediateRef == null || !immediateRef.isValid()) {
            return 1.0f;
        }

        // Resolve true attacker (handle projectiles/proxies)
        Ref<EntityStore> attackerRef = entityResolver.resolveTrueAttacker(store, immediateRef);
        if (attackerRef == null) {
            return 1.0f;
        }

        // Check if attacker has RealmMobComponent
        RealmMobComponent realmMob = store.getComponent(attackerRef, RealmMobComponent.getComponentType());
        if (realmMob == null) {
            return 1.0f;
        }

        return realmMob.getDamageMultiplier();
    }

    /**
     * Calculates execute bonus multiplier for low-health targets.
     *
     * @param attackerStats The attacker's stats
     * @param defenderHealthPercent The defender's health percentage (0-1)
     * @return Multiplier (1.0 = no bonus)
     */
    public float getExecuteMultiplier(
        @Nonnull ComputedStats attackerStats,
        float defenderHealthPercent
    ) {
        if (defenderHealthPercent >= 0 && defenderHealthPercent < 0.35f) {
            float executePct = attackerStats.getExecuteDamagePercent();
            if (executePct > 0) {
                float bonus = 1.0f + executePct / 100f;
                LOGGER.at(Level.FINE).log("Conditional: execute +%.0f%% (target at %.0f%% HP)",
                    executePct, defenderHealthPercent * 100f);
                return bonus;
            }
        }
        return 1.0f;
    }

    /**
     * Calculates ailment-based bonus multipliers (vs frozen, vs shocked).
     *
     * <p>Uses native {@link EffectControllerComponent#hasEffect(int)} for shock
     * checks when a defender reference is available. Falls back to
     * {@link AilmentTracker#hasAilment} for freeze (multiple native effect IDs).
     *
     * @param attackerStats The attacker's stats
     * @param defenderUuid The defender's UUID for ailment lookup
     * @param store The entity store (may be null for legacy callers)
     * @param defenderRef The defender's entity reference (may be null)
     * @return Multiplier (1.0 = no bonus)
     */
    public float getAilmentBonusMultiplier(
        @Nonnull ComputedStats attackerStats,
        @Nullable UUID defenderUuid,
        @Nullable Store<EntityStore> store,
        @Nullable Ref<EntityStore> defenderRef
    ) {
        if (defenderUuid == null) {
            return 1.0f;
        }

        float multiplier = 1.0f;

        // Bonus vs Frozen — uses ailmentTracker (freeze has multiple native effect IDs)
        if (ailmentTracker != null && ailmentTracker.hasAilment(defenderUuid, AilmentType.FREEZE)) {
            float bonusPct = attackerStats.getDamageVsFrozenPercent();
            if (bonusPct > 0) {
                multiplier *= (1.0f + bonusPct / 100f);
                LOGGER.at(Level.FINE).log("Conditional: vs Frozen +%.0f%%", bonusPct);
            }
        }

        // Bonus vs Shocked — uses native hasEffect() for single effect ID
        EffectControllerComponent effectController = getNativeEffectController(store, defenderRef);
        if (hasNativeShockEffect(effectController, defenderUuid)) {
            float bonusPct = attackerStats.getDamageVsShockedPercent();
            if (bonusPct > 0) {
                multiplier *= (1.0f + bonusPct / 100f);
                LOGGER.at(Level.FINE).log("Conditional: vs Shocked +%.0f%% (native hasEffect)", bonusPct);
            }
        }

        return multiplier;
    }

    /**
     * Calculates ailment-based bonus multipliers (vs frozen, vs shocked).
     *
     * <p>Legacy overload without native effect check — falls back to
     * {@link AilmentTracker#hasAilment} for both ailment types.
     *
     * @param attackerStats The attacker's stats
     * @param defenderUuid The defender's UUID for ailment lookup
     * @return Multiplier (1.0 = no bonus)
     */
    public float getAilmentBonusMultiplier(
        @Nonnull ComputedStats attackerStats,
        @Nullable UUID defenderUuid
    ) {
        if (ailmentTracker == null || defenderUuid == null) {
            return 1.0f;
        }

        float multiplier = 1.0f;

        // Bonus vs Frozen
        if (ailmentTracker.hasAilment(defenderUuid, AilmentType.FREEZE)) {
            float bonusPct = attackerStats.getDamageVsFrozenPercent();
            if (bonusPct > 0) {
                multiplier *= (1.0f + bonusPct / 100f);
                LOGGER.at(Level.FINE).log("Conditional: vs Frozen +%.0f%%", bonusPct);
            }
        }

        // Bonus vs Shocked
        if (ailmentTracker.hasAilment(defenderUuid, AilmentType.SHOCK)) {
            float bonusPct = attackerStats.getDamageVsShockedPercent();
            if (bonusPct > 0) {
                multiplier *= (1.0f + bonusPct / 100f);
                LOGGER.at(Level.FINE).log("Conditional: vs Shocked +%.0f%%", bonusPct);
            }
        }

        return multiplier;
    }

    /**
     * Calculates low-life bonus multiplier for attackers at low health.
     *
     * @param attackerStats The attacker's stats
     * @param store The entity store
     * @param damage The damage event
     * @return Multiplier (1.0 = no bonus)
     */
    public float getLowLifeMultiplier(
        @Nonnull ComputedStats attackerStats,
        @Nonnull Store<EntityStore> store,
        @Nonnull Damage damage
    ) {
        float damageAtLowLife = attackerStats.getDamageAtLowLife();
        if (damageAtLowLife <= 0) {
            return 1.0f;
        }

        if (!(damage.getSource() instanceof Damage.EntitySource entitySource)) {
            return 1.0f;
        }

        Ref<EntityStore> attackerRef = entitySource.getRef();
        if (attackerRef == null || !attackerRef.isValid()) {
            return 1.0f;
        }

        Ref<EntityStore> trueAttackerRef = entityResolver.resolveTrueAttacker(store, attackerRef);
        if (trueAttackerRef == null) {
            return 1.0f;
        }

        EntityStatMap attackerStatMap = store.getComponent(trueAttackerRef, EntityStatMap.getComponentType());
        if (attackerStatMap == null) {
            return 1.0f;
        }

        EntityStatValue attackerHealth = attackerStatMap.get(DefaultEntityStatTypes.getHealth());
        if (attackerHealth == null || attackerHealth.getMax() <= 0) {
            return 1.0f;
        }

        float hpPercent = attackerHealth.get() / attackerHealth.getMax();
        if (hpPercent <= 0.35f) {
            float bonus = 1.0f + damageAtLowLife / 100.0f;
            LOGGER.at(Level.FINE).log("Conditional: at low life +%.0f%% (attacker at %.0f%% HP)",
                damageAtLowLife, hpPercent * 100f);
            return bonus;
        }

        return 1.0f;
    }

    // ==================== Native Effect Helpers ====================

    /**
     * Gets the {@link EffectControllerComponent} for a defender entity.
     *
     * @param store The entity store (may be null)
     * @param defenderRef The defender reference (may be null or invalid)
     * @return The effect controller, or null if unavailable
     */
    @Nullable
    private EffectControllerComponent getNativeEffectController(
        @Nullable Store<EntityStore> store,
        @Nullable Ref<EntityStore> defenderRef
    ) {
        if (store == null || defenderRef == null || !defenderRef.isValid()) {
            return null;
        }
        return store.getComponent(defenderRef, EffectControllerComponent.getComponentType());
    }

    /**
     * Checks whether the defender has the shock ailment active.
     *
     * <p>Prefers Hytale's native {@link EffectControllerComponent#hasEffect(int)} with
     * a lazily-resolved effect index for {@code rpg_ailment_shock}. Falls back to
     * {@link AilmentTracker#hasAilment} if the native component is unavailable or
     * the effect index cannot be resolved (e.g., effects not yet registered, or
     * defenderRef is null in unit tests).
     *
     * @param effectController The defender's effect controller (may be null)
     * @param defenderUuid The defender's UUID for tracker fallback (may be null)
     * @return true if the defender is shocked
     */
    private boolean hasNativeShockEffect(
        @Nullable EffectControllerComponent effectController,
        @Nullable UUID defenderUuid
    ) {
        if (effectController != null) {
            int idx = resolveShockEffectIndex();
            if (idx != Integer.MIN_VALUE) {
                return effectController.hasEffect(idx);
            }
        }
        // Fallback: no native component or index not resolved — use ailment tracker
        if (ailmentTracker != null && defenderUuid != null) {
            return ailmentTracker.hasAilment(defenderUuid, AilmentType.SHOCK);
        }
        return false;
    }

    /**
     * Lazily resolves the native effect index for shock.
     *
     * @return The effect index, or {@link Integer#MIN_VALUE} if not yet registered
     */
    private int resolveShockEffectIndex() {
        if (!shockIndexResolved) {
            shockEffectIndex = EntityEffect.getAssetMap().getIndex(SHOCK_EFFECT_ID);
            shockIndexResolved = (shockEffectIndex != Integer.MIN_VALUE);
        }
        return shockEffectIndex;
    }

    /**
     * Lazily resolves the native effect index for burn.
     * Reserved for future use (e.g., vs-burning conditional bonuses).
     *
     * @return The effect index, or {@link Integer#MIN_VALUE} if not yet registered
     */
    @SuppressWarnings("unused")
    private int resolveBurnEffectIndex() {
        if (!burnIndexResolved) {
            burnEffectIndex = EntityEffect.getAssetMap().getIndex(BURN_EFFECT_ID);
            burnIndexResolved = (burnEffectIndex != Integer.MIN_VALUE);
        }
        return burnEffectIndex;
    }
}
