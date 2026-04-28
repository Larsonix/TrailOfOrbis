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
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.ailments.AilmentTracker;
import io.github.larsonix.trailoforbis.attributes.ComputedStats;
import io.github.larsonix.trailoforbis.combat.CombatCalculator;
import io.github.larsonix.trailoforbis.combat.DamageBreakdown;
import io.github.larsonix.trailoforbis.combat.EnergyShieldTracker;
import io.github.larsonix.trailoforbis.combat.detection.DamageTypeClassifier;
import io.github.larsonix.trailoforbis.combat.resolution.CombatEntityResolver;
import io.github.larsonix.trailoforbis.config.ConfigManager;
import io.github.larsonix.trailoforbis.config.RPGConfig;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/**
 * Processes post-calculation damage modifications.
 *
 * <p>This class extracts damage modification logic from RPGDamageSystem,
 * handling:
 * <ul>
 *   <li>Parry mechanics with damage reflection</li>
 *   <li>Energy shield absorption</li>
 *   <li>Mind over Matter (mana buffer)</li>
 *   <li>Fall damage reduction</li>
 *   <li>Shock amplification</li>
 *   <li>Vulnerability</li>
 * </ul>
 */
public class DamageModifierProcessor {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /** Effect ID for shock ailment (single native effect) */
    private static final String SHOCK_EFFECT_ID = "rpg_ailment_shock";

    private final CombatEntityResolver entityResolver;
    private final ConfigManager configManager;
    private final DamageTypeClassifier classifier;
    private final EnergyShieldTracker energyShieldTracker;
    private final AilmentTracker ailmentTracker;

    /** Cached native effect index for shock. Resolved lazily on first use. */
    private volatile int shockEffectIndex = Integer.MIN_VALUE;
    private volatile boolean shockIndexResolved = false;

    /**
     * Result of damage modifications.
     *
     * @param finalDamage The final damage after all modifications
     * @param breakdown The updated damage breakdown
     * @param wasParried Whether the attack was parried
     * @param shieldAbsorbed Amount of damage absorbed by energy shield
     * @param parryReductionMult The parry damage reduction multiplier (0-1, e.g. 0.5 = take 50% damage)
     * @param energyShieldBefore Shield HP before this hit (0 if no shield)
     * @param manaBufferPercent Mind over Matter buffer % (0 if not active)
     * @param manaAbsorbed Mana consumed by Mind over Matter
     * @param shockBonusPercent Shock damage amplification % (0 if not shocked)
     * @param damageBeforeShock Damage before shock amplification
     * @param parryChance Defender's parry chance % before roll
     * @param damageTakenModifier Defender's damage taken modifier %
     */
    public record ModificationResult(
        float finalDamage,
        @Nonnull DamageBreakdown breakdown,
        boolean wasParried,
        float shieldAbsorbed,
        float parryReductionMult,
        float energyShieldBefore,
        float manaBufferPercent,
        float manaAbsorbed,
        float shockBonusPercent,
        float damageBeforeShock,
        float parryChance,
        float damageTakenModifier
    ) {}

    /**
     * Creates a new DamageModifierProcessor.
     *
     * @param entityResolver The entity resolver for reflected damage
     * @param configManager The config manager
     * @param energyShieldTracker The energy shield tracker (may be null)
     * @param ailmentTracker The ailment tracker for shock checks (may be null)
     */
    public DamageModifierProcessor(
        @Nonnull CombatEntityResolver entityResolver,
        @Nonnull ConfigManager configManager,
        @Nullable EnergyShieldTracker energyShieldTracker,
        @Nullable AilmentTracker ailmentTracker
    ) {
        this.entityResolver = entityResolver;
        this.configManager = configManager;
        this.classifier = new DamageTypeClassifier();
        this.energyShieldTracker = energyShieldTracker;
        this.ailmentTracker = ailmentTracker;
    }

    private RPGConfig.CombatConfig.ParryConfig getParryConfig() {
        return configManager.getRPGConfig().getCombat().getParry();
    }

    /**
     * Applies all post-calculation damage modifications.
     *
     * @param breakdown The initial damage breakdown
     * @param defenderStats The defender's stats (may be null)
     * @param store The entity store
     * @param defenderRef The defender entity reference
     * @param damage The damage event
     * @param defenderUuid The defender's UUID (may be null)
     * @return The modification result with final damage and flags
     */
    @Nonnull
    public ModificationResult applyModifications(
        @Nonnull DamageBreakdown breakdown,
        @Nullable ComputedStats defenderStats,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> defenderRef,
        @Nonnull Damage damage,
        @Nullable UUID defenderUuid
    ) {
        float rpgDamage = breakdown.totalDamage();
        boolean wasParried = false;
        float shieldAbsorbed = 0f;
        float parryReductionMult = 0f;
        float trackedParryChance = 0f;
        float trackedDamageTakenMod = 0f;

        // Capture parry chance and damage taken modifier for trace
        if (defenderStats != null) {
            trackedParryChance = defenderStats.getParryChance();
            trackedDamageTakenMod = defenderStats.getDamageTakenPercent();
        }

        // Parry - reduces damage (reflection is handled separately)
        if (defenderStats != null && damage.getSource() instanceof Damage.EntitySource) {
            if (trackedParryChance > 0 && ThreadLocalRandom.current().nextFloat() * 100f < trackedParryChance) {
                wasParried = true;
                float damageReduction = getParryConfig().getDamageReduction();
                parryReductionMult = damageReduction;
                breakdown = breakdown.withParried(damageReduction);
                rpgDamage = breakdown.totalDamage();
                LOGGER.at(Level.FINE).log("Parried: taking %.0f%% damage (%.1f)",
                    damageReduction * 100f, rpgDamage);
            }
        }

        // Energy shield absorption (AFTER armor)
        // Fall damage bypasses energy shield — it always hits true HP
        float energyShieldBefore = 0f;
        boolean isFall = classifier.isFallDamage(damage);
        if (defenderStats != null && rpgDamage > 0 && !isFall) {
            // Capture shield state before absorption
            if (energyShieldTracker != null && defenderStats.getEnergyShield() > 0) {
                PlayerRef defenderPlayer = store.getComponent(defenderRef, PlayerRef.getComponentType());
                if (defenderPlayer != null) {
                    EnergyShieldTracker.ShieldState state = energyShieldTracker.getState(defenderPlayer.getUuid());
                    if (state != null) {
                        energyShieldBefore = state.currentShield();
                    }
                }
            }
            float beforeShield = rpgDamage;
            rpgDamage = applyEnergyShield(rpgDamage, defenderStats, store, defenderRef);
            shieldAbsorbed = beforeShield - rpgDamage;
            if (shieldAbsorbed > 0) {
                breakdown = breakdown.withShieldAbsorbed(shieldAbsorbed, rpgDamage);
            }
        }

        // Mind over Matter - absorb from mana
        float manaBufferPercent = 0f;
        float manaAbsorbed = 0f;
        if (defenderStats != null && rpgDamage > 0 && damage.getSource() instanceof Damage.EntitySource) {
            manaBufferPercent = defenderStats.getManaAsDamageBuffer();
            float beforeMana = rpgDamage;
            rpgDamage = applyManaBuffer(rpgDamage, defenderStats, store, defenderRef);
            manaAbsorbed = beforeMana - rpgDamage;
        }

        // Fall damage reduction (VIT-based)
        if (defenderStats != null && classifier.isFallDamage(damage) && rpgDamage > 0) {
            rpgDamage = applyFallDamageReduction(rpgDamage, defenderStats);
        }

        // Shock amplification — gate with native hasEffect() before querying tracker
        float shockBonusPercent = 0f;
        float damageBeforeShock = rpgDamage;
        if (rpgDamage > 0) {
            if (ailmentTracker != null && defenderUuid != null) {
                if (hasNativeShockEffect(store, defenderRef)) {
                    shockBonusPercent = ailmentTracker.getShockDamageIncreasePercent(defenderUuid);
                }
            }
            rpgDamage = applyShockAmplification(rpgDamage, defenderUuid, store, defenderRef);
        }

        return new ModificationResult(rpgDamage, breakdown, wasParried, shieldAbsorbed, parryReductionMult,
            energyShieldBefore, manaBufferPercent, manaAbsorbed, shockBonusPercent, damageBeforeShock,
            trackedParryChance, trackedDamageTakenMod);
    }

    /**
     * Applies parry damage reduction and returns reflected damage amount.
     *
     * @param breakdown The damage breakdown
     * @param defenderStats The defender's stats
     * @param damage The damage event
     * @return Parry result containing reduced breakdown and reflected damage
     */
    @Nonnull
    public ParryResult applyParry(
        @Nonnull DamageBreakdown breakdown,
        @Nonnull ComputedStats defenderStats,
        @Nonnull Damage damage
    ) {
        if (!(damage.getSource() instanceof Damage.EntitySource)) {
            return new ParryResult(breakdown, false, 0f);
        }

        float parryChance = defenderStats.getParryChance();
        if (parryChance <= 0 || ThreadLocalRandom.current().nextFloat() * 100f >= parryChance) {
            return new ParryResult(breakdown, false, 0f);
        }

        float damageReduction = getParryConfig().getDamageReduction();

        // Guard against division by zero from invalid config
        if (damageReduction <= 0) {
            LOGGER.at(Level.WARNING).log("Invalid parry damageReduction config: %.2f, skipping parry", damageReduction);
            return new ParryResult(breakdown, false, 0f);
        }

        DamageBreakdown reduced = breakdown.withParried(damageReduction);

        // Calculate reflected damage: reflect a percentage of the BLOCKED portion
        // blockedDamage = totalDamage × (1 - damageReduction) = the damage we avoided
        // reflectedDamage = blockedDamage × reflectAmount
        float blockedDamage = breakdown.totalDamage() * (1f - damageReduction);
        float reflectedDamage = blockedDamage * getParryConfig().getReflectAmount();

        LOGGER.at(Level.FINE).log("Parried: taking %.0f%% damage (%.1f), blocked %.1f, reflected %.1f",
            damageReduction * 100f, reduced.totalDamage(), blockedDamage, reflectedDamage);

        return new ParryResult(reduced, true, reflectedDamage);
    }

    /**
     * Result of parry calculation.
     */
    public record ParryResult(
        @Nonnull DamageBreakdown breakdown,
        boolean wasParried,
        float reflectedDamage
    ) {}

    /**
     * Applies energy shield absorption.
     *
     * @param damage The damage amount
     * @param defenderStats The defender's stats
     * @param store The entity store
     * @param defenderRef The defender reference
     * @return The remaining damage after shield absorption
     */
    public float applyEnergyShield(
        float damage,
        @Nonnull ComputedStats defenderStats,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> defenderRef
    ) {
        if (energyShieldTracker == null) {
            return damage;
        }

        float maxShield = defenderStats.getEnergyShield();
        if (maxShield <= 0) {
            return damage;
        }

        PlayerRef defenderPlayer = store.getComponent(defenderRef, PlayerRef.getComponentType());
        if (defenderPlayer == null) {
            return damage;
        }

        float beforeShield = damage;
        damage = energyShieldTracker.absorbDamage(defenderPlayer.getUuid(), damage);
        float absorbed = beforeShield - damage;

        if (absorbed > 0) {
            LOGGER.at(Level.FINE).log("Shield absorbed: %.1f (%.1f -> %.1f damage)",
                absorbed, beforeShield, damage);
        }

        return damage;
    }

    /**
     * Applies mana buffer (Mind over Matter) absorption.
     *
     * @param damage The damage amount
     * @param defenderStats The defender's stats
     * @param store The entity store
     * @param defenderRef The defender reference
     * @return The remaining damage after mana absorption
     */
    public float applyManaBuffer(
        float damage,
        @Nonnull ComputedStats defenderStats,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> defenderRef
    ) {
        float manaBuffer = defenderStats.getManaAsDamageBuffer();
        if (manaBuffer <= 0) {
            return damage;
        }

        EntityStatMap defenderStatMap = store.getComponent(defenderRef, EntityStatMap.getComponentType());
        if (defenderStatMap == null) {
            return damage;
        }

        int manaIndex = DefaultEntityStatTypes.getMana();
        EntityStatValue manaStat = defenderStatMap.get(manaIndex);
        if (manaStat == null) {
            return damage;
        }

        float currentMana = manaStat.get();
        float manaToAbsorb = Math.min(damage * manaBuffer / 100f, currentMana);
        if (manaToAbsorb > 0) {
            damage -= manaToAbsorb;
            defenderStatMap.setStatValue(manaIndex, currentMana - manaToAbsorb);
            LOGGER.at(Level.FINE).log("Mind over Matter: %.1f damage absorbed by mana (%.0f%% buffer)",
                manaToAbsorb, manaBuffer);
        }

        return damage;
    }

    /**
     * Applies fall damage reduction based on VIT.
     *
     * @param damage The damage amount
     * @param defenderStats The defender's stats
     * @return The reduced damage
     */
    public float applyFallDamageReduction(float damage, @Nonnull ComputedStats defenderStats) {
        var fallResult = CombatCalculator.applyFallDamageReduction(damage, defenderStats);
        if (fallResult.wasReduced()) {
            LOGGER.at(Level.FINE).log("Fall damage reduction: %.1f -> %.1f (%.1f%% from VIT)",
                fallResult.originalDamage(), fallResult.finalDamage(), fallResult.reductionPercent());
            return fallResult.finalDamage();
        }
        return damage;
    }

    /**
     * Applies shock amplification to damage, gated by native {@code hasEffect()} check.
     *
     * <p>Uses Hytale's native {@link EffectControllerComponent#hasEffect(int)} to verify
     * the shock effect is active before querying the ailment tracker for the bonus percentage.
     * This ensures damage amplification only applies when the native effect system confirms
     * the shock is active, preventing timing gaps between tracker state and effect expiry.
     *
     * @param damage The damage amount
     * @param defenderUuid The defender's UUID (may be null)
     * @param store The entity store for native effect lookup
     * @param defenderRef The defender reference for native effect lookup
     * @return The amplified damage
     */
    public float applyShockAmplification(
        float damage,
        @Nullable UUID defenderUuid,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> defenderRef
    ) {
        if (ailmentTracker == null || defenderUuid == null) {
            return damage;
        }

        // Gate with native hasEffect() — only amplify if native shock effect is active
        if (!hasNativeShockEffect(store, defenderRef)) {
            return damage;
        }

        float shockBonus = ailmentTracker.getShockDamageIncreasePercent(defenderUuid);
        if (shockBonus > 0) {
            // Cap shock bonus to prevent overflow (max 500% = 6x damage)
            float cappedBonus = Math.min(shockBonus, 500f);
            float beforeShock = damage;
            damage *= (1.0f + cappedBonus / 100.0f);
            LOGGER.at(Level.FINE).log("Shock amplification: %.1f -> %.1f (+%.1f%%, native hasEffect)",
                beforeShock, damage, cappedBonus);
        }

        return damage;
    }

    /**
     * Legacy overload without native effect check. Falls back to ailment tracker only.
     *
     * @param damage The damage amount
     * @param defenderUuid The defender's UUID (may be null)
     * @return The amplified damage
     */
    public float applyShockAmplification(float damage, @Nullable UUID defenderUuid) {
        if (ailmentTracker == null || defenderUuid == null) {
            return damage;
        }

        float shockBonus = ailmentTracker.getShockDamageIncreasePercent(defenderUuid);
        if (shockBonus > 0) {
            // Cap shock bonus to prevent overflow (max 500% = 6x damage)
            float cappedBonus = Math.min(shockBonus, 500f);
            float beforeShock = damage;
            damage *= (1.0f + cappedBonus / 100.0f);
            LOGGER.at(Level.FINE).log("Shock amplification: %.1f -> %.1f (+%.1f%%)",
                beforeShock, damage, cappedBonus);
        }

        return damage;
    }

    /**
     * @return Minimum attacker HP after parry reflection (prevents parry kills)
     */
    public float getReflectMinAttackerHp() {
        return getParryConfig().getReflectMinAttackerHp();
    }

    // ==================== Native Effect Helpers ====================

    /**
     * Checks whether the defender has the native shock effect active.
     *
     * <p>Uses {@link EffectControllerComponent#hasEffect(int)} with a lazily-resolved
     * effect index for {@code rpg_ailment_shock}.
     *
     * @param store The entity store
     * @param defenderRef The defender reference
     * @return true if the defender has the native shock effect active
     */
    private boolean hasNativeShockEffect(
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> defenderRef
    ) {
        if (!defenderRef.isValid()) {
            return false;
        }
        EffectControllerComponent effectController = store.getComponent(
            defenderRef, EffectControllerComponent.getComponentType());
        if (effectController == null) {
            return false;
        }
        int idx = resolveShockEffectIndex();
        if (idx == Integer.MIN_VALUE) {
            return false;
        }
        return effectController.hasEffect(idx);
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
}
