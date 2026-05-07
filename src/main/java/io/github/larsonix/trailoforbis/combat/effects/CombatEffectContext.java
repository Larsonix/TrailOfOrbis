package io.github.larsonix.trailoforbis.combat.effects;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.attributes.ComputedStats;
import io.github.larsonix.trailoforbis.combat.AttackType;
import io.github.larsonix.trailoforbis.combat.DamageBreakdown;
import io.github.larsonix.trailoforbis.combat.DamageTrace;
import io.github.larsonix.trailoforbis.elemental.ElementType;
import io.github.larsonix.trailoforbis.elemental.ElementalStats;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Immutable context passed to {@link CombatEffect} hooks during combat processing.
 *
 * <p>Contains all data from the damage pipeline that effects might need to read.
 * The {@code rpgDamage} field is the current damage value that effects can modify
 * through their return values.
 *
 * <p>Created once per damage event by RPGDamageSystem, reused across all effect hooks.
 *
 * @param attackerUuid Attacker's UUID (null for environmental damage)
 * @param defenderUuid Defender's UUID
 * @param attackerStats Attacker's computed stats (null if no RPG stats)
 * @param defenderStats Defender's computed stats (null if no RPG stats)
 * @param attackerElemental Attacker's elemental stats (null if none)
 * @param defenderElemental Defender's elemental stats (null if none)
 * @param breakdown The damage breakdown with per-element and per-type values
 * @param trace The full 10-step calculation trace (null if tracing disabled)
 * @param attackType The attack type (MELEE, PROJECTILE, AREA, SPELL)
 * @param spellElement The element for spell attacks (null for non-spell)
 * @param rpgDamage Current RPG damage value (modifiable by effects)
 * @param rpgBaseDamage Base weapon/spell damage before all modifiers
 * @param healthBefore Defender's HP before this damage
 * @param maxHealth Defender's max HP
 * @param wasCrit Whether this hit was a critical strike
 * @param damage The raw Hytale Damage event
 * @param store The ECS entity store
 * @param defenderRef Reference to the defender entity
 * @param attackerRef Reference to the attacker entity (null for environmental)
 * @param commandBuffer ECS command buffer for deferred operations
 * @param attackerHealthPercent Attacker's current HP as fraction (0.0-1.0), -1 if unknown
 */
public record CombatEffectContext(
    @Nullable UUID attackerUuid,
    @Nonnull UUID defenderUuid,
    @Nullable ComputedStats attackerStats,
    @Nullable ComputedStats defenderStats,
    @Nullable ElementalStats attackerElemental,
    @Nullable ElementalStats defenderElemental,
    @Nullable DamageBreakdown breakdown,
    @Nullable DamageTrace trace,
    @Nonnull AttackType attackType,
    @Nullable ElementType spellElement,
    float rpgDamage,
    float rpgBaseDamage,
    float healthBefore,
    float maxHealth,
    boolean wasCrit,
    @Nonnull Damage damage,
    @Nonnull Store<EntityStore> store,
    @Nullable Ref<EntityStore> defenderRef,
    @Nullable Ref<EntityStore> attackerRef,
    @Nullable CommandBuffer<EntityStore> commandBuffer,
    float attackerHealthPercent
) {

    /**
     * Creates a new context with an updated rpgDamage value.
     * Used when an effect modifies the damage and subsequent effects need the new value.
     */
    @Nonnull
    public CombatEffectContext withDamage(float newDamage) {
        return new CombatEffectContext(
            attackerUuid, defenderUuid, attackerStats, defenderStats,
            attackerElemental, defenderElemental, breakdown, trace,
            attackType, spellElement, newDamage, rpgBaseDamage,
            healthBefore, maxHealth, wasCrit, damage, store, defenderRef, attackerRef,
            commandBuffer, attackerHealthPercent
        );
    }

    /**
     * Returns the attacker's missing HP percentage (0.0 = full HP, 1.0 = dead).
     * Returns 0 if attacker health is unknown.
     */
    public float attackerMissingHpPercent() {
        if (attackerHealthPercent < 0) return 0f;
        return Math.max(0f, 1.0f - attackerHealthPercent);
    }

    /**
     * Returns the defender's current HP percentage (0.0 - 1.0).
     */
    public float defenderHealthPercent() {
        return maxHealth > 0 ? healthBefore / maxHealth : 0f;
    }

    /**
     * Returns true if the attacker has RPG stats (is a player or RPG mob).
     */
    public boolean hasAttackerStats() {
        return attackerStats != null;
    }

    /**
     * Returns true if the defender has RPG stats.
     */
    public boolean hasDefenderStats() {
        return defenderStats != null;
    }
}
