package io.github.larsonix.trailoforbis.combat.effects.impl;

import com.hypixel.hytale.logger.HytaleLogger;
import io.github.larsonix.trailoforbis.attributes.ComputedStats;
import io.github.larsonix.trailoforbis.combat.AttackType;
import io.github.larsonix.trailoforbis.combat.effects.CombatEffect;
import io.github.larsonix.trailoforbis.combat.effects.CombatEffectContext;

import javax.annotation.Nonnull;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Warlock KS2: Arcane Overload — Spell/magic damage has a chance to repeat
 * as 50% bonus Void damage.
 *
 * <p>Activated when {@code spellEchoChance > 0} in player stats.
 *
 * <p>Migrated from hardcoded RPGDamageSystem lines 1011-1022.
 */
public class SpellEchoEffect implements CombatEffect {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /** Echo deals this fraction of the original spell damage. */
    private static final float ECHO_DAMAGE_FRACTION = 0.5f;

    @Nonnull
    @Override
    public String getId() {
        return "spell_echo";
    }

    @Override
    public boolean isActive(@Nonnull UUID playerId, @Nonnull ComputedStats stats) {
        return stats.getSpellEchoChance() > 0;
    }

    @Override
    public float onPostModifications(@Nonnull CombatEffectContext ctx) {
        if (!ctx.hasAttackerStats() || ctx.attackType() != AttackType.SPELL) {
            return ctx.rpgDamage();
        }

        float echoPct = ctx.attackerStats().getSpellEchoChance();
        if (ThreadLocalRandom.current().nextFloat() * 100f >= echoPct) {
            return ctx.rpgDamage(); // Didn't proc
        }

        float echoDamage = ctx.rpgDamage() * ECHO_DAMAGE_FRACTION;
        LOGGER.atFine().log("Spell Echo: %.0f%% proc — +%.1f echo (50%% of %.1f)",
            echoPct, echoDamage, ctx.rpgDamage());

        return ctx.rpgDamage() + echoDamage;
    }
}
