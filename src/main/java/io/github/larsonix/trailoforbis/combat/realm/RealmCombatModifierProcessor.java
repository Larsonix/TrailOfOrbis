package io.github.larsonix.trailoforbis.combat.realm;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.TrailOfOrbis;
import io.github.larsonix.trailoforbis.combat.effects.CombatEffectRegistry;
import io.github.larsonix.trailoforbis.combat.resolution.CombatEntityResolver;
import io.github.larsonix.trailoforbis.elemental.ElementType;
import io.github.larsonix.trailoforbis.elemental.ElementalStats;
import io.github.larsonix.trailoforbis.maps.RealmsManager;
import io.github.larsonix.trailoforbis.maps.components.RealmMobComponent;
import io.github.larsonix.trailoforbis.maps.instance.RealmInstance;
import io.github.larsonix.trailoforbis.maps.modifiers.RealmModifierType;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Handles realm modifier lookups for the combat pipeline.
 *
 * <p>Realm modifiers affect combat in several ways: extra elemental damage on mobs,
 * armor bonuses, player vulnerability increases, and healing reduction. This processor
 * centralizes all realm modifier queries used across Phase 5 and Phase 7 of the pipeline.
 *
 * <p>Also includes the Berserker's Rage leech cap check, which queries the combat
 * effect registry for active keystone effects.
 *
 * <p>Extracted from RPGDamageSystem to isolate realm-specific combat logic.
 */
public class RealmCombatModifierProcessor {

    private final CombatEntityResolver entityResolver;

    @Nullable
    private volatile CombatEffectRegistry combatEffectRegistry;

    /** Mapping of elemental modifier types to their element. */
    private static final Map<RealmModifierType, ElementType> ELEMENTAL_MODIFIERS = Map.of(
        RealmModifierType.MONSTERS_EXTRA_FIRE, ElementType.FIRE,
        RealmModifierType.MONSTERS_EXTRA_WATER, ElementType.WATER,
        RealmModifierType.MONSTERS_EXTRA_LIGHTNING, ElementType.LIGHTNING,
        RealmModifierType.MONSTERS_EXTRA_EARTH, ElementType.EARTH,
        RealmModifierType.MONSTERS_EXTRA_WIND, ElementType.WIND,
        RealmModifierType.MONSTERS_EXTRA_VOID, ElementType.VOID
    );

    public RealmCombatModifierProcessor(@Nonnull CombatEntityResolver entityResolver) {
        this.entityResolver = entityResolver;
    }

    public void setCombatEffectRegistry(@Nullable CombatEffectRegistry registry) {
        this.combatEffectRegistry = registry;
    }

    /**
     * Returns the maximum HP that can be recovered via leech/steal for this attacker.
     * If Berserker's Rage is active, caps recovery to not exceed 50% max HP.
     * Returns Float.MAX_VALUE if no cap applies.
     */
    public float getBerserkersRageLeechCap(@Nullable UUID attackerUuid,
                                            @Nonnull Store<EntityStore> store,
                                            @Nonnull Damage damage) {
        if (attackerUuid == null || combatEffectRegistry == null) return Float.MAX_VALUE;

        // Check if berserkers_rage is active for this player
        boolean hasRage = combatEffectRegistry.getActiveEffects(attackerUuid).stream()
            .anyMatch(e -> "berserkers_rage".equals(e.getId()));
        if (!hasRage) return Float.MAX_VALUE;

        // Resolve attacker HP to enforce the 50% cap
        Ref<EntityStore> atkRef = (damage.getSource() instanceof Damage.EntitySource es) ? es.getRef() : null;
        if (atkRef == null || !atkRef.isValid()) return Float.MAX_VALUE;

        try {
            EntityStatMap statMap = store.getComponent(atkRef, EntityStatMap.getComponentType());
            if (statMap == null) return Float.MAX_VALUE;
            EntityStatValue hpStat = statMap.get(DefaultEntityStatTypes.getHealth());
            if (hpStat == null) return Float.MAX_VALUE;

            float currentHp = hpStat.get();
            float maxHp = hpStat.getMax();
            float capHp = maxHp * 0.50f;

            if (currentHp >= capHp) {
                return 0f; // Already at or above 50% — no leech allowed
            }
            return capHp - currentHp; // Only heal up to the 50% threshold
        } catch (Exception e) {
            return Float.MAX_VALUE;
        }
    }

    /**
     * Calculates bonus elemental damage from realm MONSTERS_EXTRA_[ELEMENT] modifiers.
     *
     * <p>When a realm mob attacks a player, each elemental modifier adds bonus damage
     * of that element, reduced by the player's resistance.
     *
     * @param store The entity store
     * @param damage The damage event (to resolve attacker's realm)
     * @param baseDamage The base damage dealt (before elemental bonus)
     * @param defenderElemental The defender's elemental stats (for resistance)
     * @return Total bonus elemental damage to add
     */
    public float calculateRealmElementalBonusDamage(
            @Nonnull Store<EntityStore> store,
            @Nonnull Damage damage,
            float baseDamage,
            @Nonnull ElementalStats defenderElemental) {

        // Resolve the attacker's realm (mob → realm)
        Ref<EntityStore> attackerRef = entityResolver.getAttackerRef(store, damage);
        if (attackerRef == null) {
            return 0f;
        }
        RealmMobComponent realmMob = store.getComponent(attackerRef, RealmMobComponent.getComponentType());
        if (realmMob == null || realmMob.getRealmId() == null) {
            return 0f;
        }
        TrailOfOrbis rpg = TrailOfOrbis.getInstanceOrNull();
        if (rpg == null) {
            return 0f;
        }
        RealmsManager rm = rpg.getRealmsManager();
        if (rm == null) {
            return 0f;
        }
        Optional<RealmInstance> realmOpt = rm.getRealm(realmMob.getRealmId());
        if (realmOpt.isEmpty()) {
            return 0f;
        }

        // Check each elemental modifier
        float totalBonus = 0f;
        var mapData = realmOpt.get().getMapData();
        for (var entry : ELEMENTAL_MODIFIERS.entrySet()) {
            int modValue = mapData.getModifierValue(entry.getKey());
            if (modValue > 0) {
                ElementType element = entry.getValue();
                float rawBonus = baseDamage * (modValue / 100.0f);
                // Reduce by player's resistance to this element
                double resistance = defenderElemental.getResistance(element);
                float afterResist = rawBonus * (1.0f - (float) (resistance / 100.0));
                if (afterResist > 0) {
                    totalBonus += afterResist;
                }
            }
        }
        return totalBonus;
    }

    /**
     * Returns the ARMORED_MONSTERS modifier value for a realm mob defender.
     *
     * @param store The entity store
     * @param defenderRef The defender entity reference (mob)
     * @return Armor bonus percentage (0 if not a realm mob or no modifier)
     */
    public int getRealmArmoredMonstersBonus(@Nonnull Store<EntityStore> store, @Nullable Ref<EntityStore> defenderRef) {
        if (defenderRef == null || !defenderRef.isValid()) {
            return 0;
        }
        RealmMobComponent realmMob = store.getComponent(defenderRef, RealmMobComponent.getComponentType());
        if (realmMob == null || realmMob.getRealmId() == null) {
            return 0;
        }
        TrailOfOrbis rpg = TrailOfOrbis.getInstanceOrNull();
        if (rpg == null) {
            return 0;
        }
        RealmsManager rm = rpg.getRealmsManager();
        if (rm == null) {
            return 0;
        }
        Optional<RealmInstance> realmOpt = rm.getRealm(realmMob.getRealmId());
        if (realmOpt.isEmpty()) {
            return 0;
        }
        return realmOpt.get().getMapData().getModifierValue(RealmModifierType.ARMORED_MONSTERS);
    }

    /**
     * Returns the PLAYER_VULNERABILITY modifier value for a player in a realm.
     *
     * @param defenderUuid The player UUID being damaged
     * @return Vulnerability percentage (0 if not in realm or no modifier)
     */
    public int getRealmPlayerVulnerability(@Nonnull UUID defenderUuid) {
        TrailOfOrbis rpg = TrailOfOrbis.getInstanceOrNull();
        if (rpg == null) {
            return 0;
        }
        RealmsManager rm = rpg.getRealmsManager();
        if (rm == null) {
            return 0;
        }
        Optional<RealmInstance> realmOpt = rm.getPlayerRealm(defenderUuid);
        if (realmOpt.isEmpty()) {
            return 0;
        }
        return realmOpt.get().getMapData().getModifierValue(RealmModifierType.PLAYER_VULNERABILITY);
    }

    /**
     * Returns the healing multiplier from the REDUCED_HEALING realm modifier.
     *
     * <p>If the healer is inside a realm with REDUCED_HEALING, returns a value
     * less than 1.0 (e.g., 0.6 for 40% reduced healing). Returns 1.0 if not
     * in a realm or no modifier is present.
     *
     * @param healerUuid The UUID of the player receiving healing (may be null)
     * @return Healing multiplier in range (0, 1.0]
     */
    public float getRealmHealingMultiplier(@Nullable UUID healerUuid) {
        if (healerUuid == null) {
            return 1.0f;
        }
        TrailOfOrbis rpg = TrailOfOrbis.getInstanceOrNull();
        if (rpg == null) {
            return 1.0f;
        }
        RealmsManager rm = rpg.getRealmsManager();
        if (rm == null) {
            return 1.0f;
        }
        Optional<RealmInstance> realmOpt = rm.getPlayerRealm(healerUuid);
        if (realmOpt.isEmpty()) {
            return 1.0f;
        }
        int reduction = realmOpt.get().getMapData().getModifierValue(RealmModifierType.REDUCED_HEALING);
        return reduction > 0 ? 1.0f - (reduction / 100.0f) : 1.0f;
    }
}
