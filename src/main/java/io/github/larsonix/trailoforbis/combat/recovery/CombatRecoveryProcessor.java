package io.github.larsonix.trailoforbis.combat.recovery;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.attributes.ComputedStats;
import io.github.larsonix.trailoforbis.combat.resolution.CombatEntityResolver;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.logging.Level;

/**
 * Processes combat recovery mechanics (leech, steal, block heal).
 *
 * <p>This class handles 4 distinct resource recovery mechanics:
 *
 * <h3>Leech (gain resources from YOUR damage output - always works)</h3>
 * <ul>
 *   <li>{@link #applyLifeLeech} - Heal % of damage dealt (always works)</li>
 *   <li>{@link #applyManaLeech} - Restore mana % of damage dealt (always works)</li>
 * </ul>
 *
 * <h3>Steal (take resources FROM enemy - enemy loses what you gain)</h3>
 * <ul>
 *   <li>{@link #applyLifeSteal} - Heal % of damage AND deal extra damage (always works, enemies have HP)</li>
 *   <li>{@link #applyManaSteal} - Gain mana FROM enemy, enemy loses mana (only if enemy has mana)</li>
 * </ul>
 *
 * <h3>Other Recovery</h3>
 * <ul>
 *   <li>{@link #applyReflectedDamage} - Reflect damage back to attacker (parry)</li>
 *   <li>{@link #applyBlockHeal} - Heal defender on successful block</li>
 * </ul>
 */
public class CombatRecoveryProcessor {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final CombatEntityResolver entityResolver;

    /**
     * Creates a new CombatRecoveryProcessor.
     *
     * @param entityResolver The entity resolver for attacker lookups
     */
    public CombatRecoveryProcessor(@Nonnull CombatEntityResolver entityResolver) {
        this.entityResolver = entityResolver;
    }

    /**
     * Applies life leech healing to the attacker.
     *
     * <p>Leech = gain resources from YOUR damage output (always works).
     * The attacker heals for a percentage of damage dealt.
     *
     * @param store The entity store
     * @param damage The damage event (contains attacker reference)
     * @param healAmount The amount to heal
     */
    public void applyLifeLeech(
        @Nonnull Store<EntityStore> store,
        @Nonnull Damage damage,
        float healAmount
    ) {
        if (healAmount <= 0) {
            return;
        }

        if (!(damage.getSource() instanceof Damage.EntitySource entitySource)) {
            return;
        }

        Ref<EntityStore> immediateRef = entitySource.getRef();
        if (immediateRef == null || !immediateRef.isValid()) {
            return;
        }

        Ref<EntityStore> attackerRef = entityResolver.resolveTrueAttacker(store, immediateRef);
        if (attackerRef == null) {
            return;
        }

        // Get attacker's EntityStatMap
        EntityStatMap attackerStatMap = store.getComponent(attackerRef, EntityStatMap.getComponentType());
        if (attackerStatMap == null) {
            return;
        }

        // Get current and max health
        int healthIndex = DefaultEntityStatTypes.getHealth();
        EntityStatValue healthStat = attackerStatMap.get(healthIndex);
        if (healthStat == null) {
            return;
        }

        float currentHealth = healthStat.get();
        float maxHealth = healthStat.getMax();

        // Apply healing (capped at max health)
        float newHealth = Math.min(currentHealth + healAmount, maxHealth);
        if (newHealth > currentHealth) {
            attackerStatMap.setStatValue(EntityStatMap.Predictable.SELF, healthIndex, newHealth);
            LOGGER.at(Level.FINE).log("Life leech: +%.1f HP (%.1f -> %.1f)",
                healAmount, currentHealth, newHealth);
        }
    }

    /**
     * Applies life steal healing to the attacker.
     *
     * <p>Steal = take resources FROM enemy (enemy loses what you gain).
     * The attacker heals AND the enemy takes extra damage (already included in RPG damage calc).
     *
     * <p>Note: The "extra damage" portion is handled in the damage calculation phase,
     * this method just handles the healing portion.
     *
     * @param store The entity store
     * @param damage The damage event (contains attacker reference)
     * @param healAmount The amount to heal
     */
    public void applyLifeSteal(
        @Nonnull Store<EntityStore> store,
        @Nonnull Damage damage,
        float healAmount
    ) {
        if (healAmount <= 0) {
            return;
        }

        if (!(damage.getSource() instanceof Damage.EntitySource entitySource)) {
            return;
        }

        Ref<EntityStore> immediateRef = entitySource.getRef();
        if (immediateRef == null || !immediateRef.isValid()) {
            return;
        }

        Ref<EntityStore> attackerRef = entityResolver.resolveTrueAttacker(store, immediateRef);
        if (attackerRef == null) {
            return;
        }

        // Get attacker's EntityStatMap
        EntityStatMap attackerStatMap = store.getComponent(attackerRef, EntityStatMap.getComponentType());
        if (attackerStatMap == null) {
            return;
        }

        // Get current and max health
        int healthIndex = DefaultEntityStatTypes.getHealth();
        EntityStatValue healthStat = attackerStatMap.get(healthIndex);
        if (healthStat == null) {
            return;
        }

        float currentHealth = healthStat.get();
        float maxHealth = healthStat.getMax();

        // Apply healing (capped at max health)
        float newHealth = Math.min(currentHealth + healAmount, maxHealth);
        if (newHealth > currentHealth) {
            attackerStatMap.setStatValue(EntityStatMap.Predictable.SELF, healthIndex, newHealth);
            LOGGER.at(Level.FINE).log("Life steal: +%.1f HP (%.1f -> %.1f)",
                healAmount, currentHealth, newHealth);
        }
    }

    /**
     * Applies mana leech to the attacker, restoring mana based on damage dealt.
     *
     * <p>Leech = gain resources from YOUR damage output (always works).
     * The attacker restores mana for a percentage of damage dealt.
     *
     * @param store The entity store
     * @param damage The damage event (contains attacker reference)
     * @param manaAmount The amount of mana to restore
     */
    public void applyManaLeech(
        @Nonnull Store<EntityStore> store,
        @Nonnull Damage damage,
        float manaAmount
    ) {
        if (manaAmount <= 0) {
            return;
        }

        if (!(damage.getSource() instanceof Damage.EntitySource entitySource)) {
            return;
        }

        Ref<EntityStore> immediateRef = entitySource.getRef();
        if (immediateRef == null || !immediateRef.isValid()) {
            return;
        }

        Ref<EntityStore> attackerRef = entityResolver.resolveTrueAttacker(store, immediateRef);
        if (attackerRef == null) {
            return;
        }

        // Get attacker's EntityStatMap
        EntityStatMap attackerStatMap = store.getComponent(attackerRef, EntityStatMap.getComponentType());
        if (attackerStatMap == null) {
            return;
        }

        // Get current and max mana
        int manaIndex = DefaultEntityStatTypes.getMana();
        EntityStatValue manaStat = attackerStatMap.get(manaIndex);
        if (manaStat == null) {
            return;
        }

        float currentMana = manaStat.get();
        float maxMana = manaStat.getMax();

        // Apply mana restoration (capped at max mana)
        float newMana = Math.min(currentMana + manaAmount, maxMana);
        if (newMana > currentMana) {
            attackerStatMap.setStatValue(EntityStatMap.Predictable.SELF, manaIndex, newMana);
            LOGGER.at(Level.FINE).log("Mana leech: +%.1f mana (%.1f -> %.1f)",
                manaAmount, currentMana, newMana);
        }
    }

    /**
     * Applies mana steal: attacker gains mana AND enemy loses mana.
     *
     * <p>Steal = take resources FROM enemy (only works if enemy has mana).
     * Unlike mana leech (which always works), mana steal requires the target
     * to have a mana pool. If the target has no mana stat, nothing happens.
     *
     * @param store The entity store
     * @param damage The damage event (contains attacker reference)
     * @param manaAmount The amount of mana to steal
     * @param defenderRef Reference to the defender (target of mana drain)
     */
    public void applyManaSteal(
        @Nonnull Store<EntityStore> store,
        @Nonnull Damage damage,
        float manaAmount,
        @Nonnull Ref<EntityStore> defenderRef
    ) {
        if (manaAmount <= 0) {
            return;
        }

        if (!(damage.getSource() instanceof Damage.EntitySource entitySource)) {
            return;
        }

        Ref<EntityStore> immediateRef = entitySource.getRef();
        if (immediateRef == null || !immediateRef.isValid()) {
            return;
        }

        Ref<EntityStore> attackerRef = entityResolver.resolveTrueAttacker(store, immediateRef);
        if (attackerRef == null) {
            return;
        }

        // Check if defender has mana (only steal if they do)
        if (!defenderRef.isValid()) {
            return;
        }
        EntityStatMap defenderStatMap = store.getComponent(defenderRef, EntityStatMap.getComponentType());
        if (defenderStatMap == null) {
            return;
        }

        int manaIndex = DefaultEntityStatTypes.getMana();
        EntityStatValue defenderManaStat = defenderStatMap.get(manaIndex);
        if (defenderManaStat == null) {
            // Target has no mana pool - mana steal doesn't work
            return;
        }

        float defenderCurrentMana = defenderManaStat.get();
        if (defenderCurrentMana <= 0) {
            // Target has no mana to steal
            return;
        }

        // Calculate actual stolen amount (can't steal more than they have)
        float actualStolen = Math.min(manaAmount, defenderCurrentMana);

        // Drain mana from defender
        float newDefenderMana = defenderCurrentMana - actualStolen;
        defenderStatMap.setStatValue(EntityStatMap.Predictable.SELF, manaIndex, newDefenderMana);

        // Give mana to attacker
        EntityStatMap attackerStatMap = store.getComponent(attackerRef, EntityStatMap.getComponentType());
        if (attackerStatMap == null) {
            return;
        }

        EntityStatValue attackerManaStat = attackerStatMap.get(manaIndex);
        if (attackerManaStat == null) {
            return;
        }

        float attackerCurrentMana = attackerManaStat.get();
        float attackerMaxMana = attackerManaStat.getMax();
        float newAttackerMana = Math.min(attackerCurrentMana + actualStolen, attackerMaxMana);

        if (newAttackerMana > attackerCurrentMana) {
            attackerStatMap.setStatValue(EntityStatMap.Predictable.SELF, manaIndex, newAttackerMana);
            LOGGER.at(Level.FINE).log("Mana steal: +%.1f mana (%.1f -> %.1f), drained %.1f from target",
                actualStolen, attackerCurrentMana, newAttackerMana, actualStolen);
        }
    }

    /**
     * Applies reflected damage to the attacker (from parry).
     *
     * @param store The entity store
     * @param entitySource The entity source containing attacker reference
     * @param reflectedDamage The amount of damage to reflect
     * @param minAttackerHp Minimum HP the attacker is left with (prevents parry kills)
     */
    public void applyReflectedDamage(
        @Nonnull Store<EntityStore> store,
        @Nonnull Damage.EntitySource entitySource,
        float reflectedDamage,
        float minAttackerHp
    ) {
        if (reflectedDamage <= 0) {
            return;
        }

        Ref<EntityStore> immediateRef = entitySource.getRef();
        if (immediateRef == null || !immediateRef.isValid()) {
            return;
        }

        Ref<EntityStore> attackerRef = entityResolver.resolveTrueAttacker(store, immediateRef);
        if (attackerRef == null) {
            return;
        }

        // Get attacker's EntityStatMap
        EntityStatMap attackerStatMap = store.getComponent(attackerRef, EntityStatMap.getComponentType());
        if (attackerStatMap == null) {
            return;
        }

        // Get current health
        int healthIndex = DefaultEntityStatTypes.getHealth();
        EntityStatValue healthStat = attackerStatMap.get(healthIndex);
        if (healthStat == null) {
            return;
        }

        float currentHealth = healthStat.get();

        // Apply reflected damage (don't go below minAttackerHp to prevent parry kills)
        float newHealth = Math.max(minAttackerHp, currentHealth - reflectedDamage);
        attackerStatMap.setStatValue(EntityStatMap.Predictable.SELF, healthIndex, newHealth);
        LOGGER.at(Level.FINE).log("Reflected damage: %.1f to attacker (%.1f -> %.1f HP)",
            reflectedDamage, currentHealth, newHealth);
    }

    /**
     * Applies healing to defender after a successful block with BLOCK_HEAL_PERCENT.
     *
     * @param index The entity index in the archetype chunk
     * @param archetypeChunk The archetype chunk containing the defender
     * @param store The entity store
     * @param healAmount The amount of health to restore
     */
    public void applyBlockHeal(
        int index,
        @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
        @Nonnull Store<EntityStore> store,
        float healAmount
    ) {
        if (healAmount <= 0) {
            return;
        }

        Ref<EntityStore> defenderRef = archetypeChunk.getReferenceTo(index);
        if (defenderRef == null || !defenderRef.isValid()) {
            return;
        }

        EntityStatMap statMap = store.getComponent(defenderRef, EntityStatMap.getComponentType());
        if (statMap != null) {
            int healthIndex = DefaultEntityStatTypes.getHealth();
            EntityStatValue healthStat = statMap.get(healthIndex);
            if (healthStat != null) {
                float currentHealth = healthStat.get();
                float maxHealth = healthStat.getMax();
                float newHealth = Math.min(currentHealth + healAmount, maxHealth);
                statMap.setStatValue(EntityStatMap.Predictable.SELF, healthIndex, newHealth);
                LOGGER.at(Level.FINE).log("Block heal: %.1f HP restored (%.1f -> %.1f)",
                    healAmount, currentHealth, newHealth);
            }
        }
    }

    /**
     * Applies thorns damage to the attacker when the defender takes damage.
     *
     * <p>Thorns is a defensive counter-attack mechanic. When the defender has thorns stats,
     * they deal damage back to the attacker based on two components:
     * <ul>
     *   <li><b>Flat thorns</b>: thornsDamage × (1 + thornsDamagePercent/100)</li>
     *   <li><b>Reflected damage</b>: damageTaken × (reflectDamagePercent/100)</li>
     * </ul>
     *
     * <p>Total return damage = flat thorns + reflected damage
     *
     * <p><b>Non-lethal:</b> Thorns will not kill the attacker. If the attacker would drop
     * below 1 HP, they are left at 1 HP instead. This prevents cheap cheese kills and
     * ensures the attacker has a chance to disengage.
     *
     * @param store The entity store
     * @param damage The damage event (contains attacker reference)
     * @param defenderStats The defender's computed stats (for thorns values)
     * @param damageTaken The amount of damage the defender took (for reflect calculation)
     */
    public void applyThornsDamage(
        @Nonnull Store<EntityStore> store,
        @Nonnull Damage damage,
        @Nullable ComputedStats defenderStats,
        float damageTaken
    ) {
        if (defenderStats == null || damageTaken <= 0) {
            return;
        }

        // Calculate flat thorns with percent bonus
        float thornsDamageFlat = defenderStats.getThornsDamage();
        float thornsDamagePercent = defenderStats.getThornsDamagePercent();
        float flatThorns = thornsDamageFlat * (1f + thornsDamagePercent / 100f);

        // Calculate reflected damage
        float reflectPercent = defenderStats.getReflectDamagePercent();
        float reflectedDamage = damageTaken * (reflectPercent / 100f);

        float totalThornsDamage = flatThorns + reflectedDamage;
        if (totalThornsDamage <= 0) {
            return;
        }

        // Get attacker from damage source
        if (!(damage.getSource() instanceof Damage.EntitySource entitySource)) {
            return;
        }

        Ref<EntityStore> immediateRef = entitySource.getRef();
        if (immediateRef == null || !immediateRef.isValid()) {
            return;
        }

        Ref<EntityStore> attackerRef = entityResolver.resolveTrueAttacker(store, immediateRef);
        if (attackerRef == null) {
            return;
        }

        // Get attacker's EntityStatMap
        EntityStatMap attackerStatMap = store.getComponent(attackerRef, EntityStatMap.getComponentType());
        if (attackerStatMap == null) {
            return;
        }

        // Get current health
        int healthIndex = DefaultEntityStatTypes.getHealth();
        EntityStatValue healthStat = attackerStatMap.get(healthIndex);
        if (healthStat == null) {
            return;
        }

        float currentHealth = healthStat.get();

        // Apply thorns damage, but cap at 1 HP (non-lethal)
        // Thorns should hurt but not kill — prevents cheap cheese kills
        float newHealth = Math.max(1f, currentHealth - totalThornsDamage);
        attackerStatMap.setStatValue(EntityStatMap.Predictable.SELF, healthIndex, newHealth);

        LOGGER.at(Level.FINE).log(
            "Thorns damage: %.1f (flat=%.1f × %.2f + reflect=%.1f%% of %.1f) to attacker (%.1f -> %.1f HP)",
            totalThornsDamage, thornsDamageFlat, 1f + thornsDamagePercent / 100f,
            reflectPercent, damageTaken, currentHealth, newHealth);
    }
}
