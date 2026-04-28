package io.github.larsonix.trailoforbis.mobs.classification;

import com.hypixel.hytale.codec.codecs.EnumCodec;

/**
 * Defines the RPG class of a mob for XP rewards and stat scaling.
 *
 * <p>This is a 5-tier system that classifies mobs for RPG mechanics:
 * <ul>
 *   <li><b>XP Rewards:</b> Multiplier applied to base XP calculation</li>
 *   <li><b>Stat Scaling:</b> Multiplier applied to HP, damage, etc.</li>
 *   <li><b>Combat Relevance:</b> Whether the mob participates in RPG systems</li>
 * </ul>
 *
 * <p><b>Tier Overview:</b>
 * <pre>
 * | Class    | XP Mult | Stat Mult | Description                    |
 * |----------|---------|-----------|--------------------------------|
 * | PASSIVE  | 0.1     | 0.0       | Non-combat (chickens, birds)   |
 * | MINOR    | 0.5     | 1.0       | Small hostiles (larvae, foxes) |
 * | HOSTILE  | 1.0     | 1.0       | Standard enemies               |
 * | ELITE    | 1.5     | 1.5       | Mini-bosses                    |
 * | BOSS     | 5.0     | 3.0       | Major bosses                   |
 * </pre>
 *
 * <p><b>Classification Priority:</b>
 * <ol>
 *   <li>Role override (config: bosses/elites/minors lists)</li>
 *   <li>Group patterns (NPCGroup names matching patterns)</li>
 *   <li>Name patterns (role names matching patterns)</li>
 *   <li>Direct group membership (hostile/minor/passive groups)</li>
 *   <li>Attitude fallback (HOSTILE/NEUTRAL → HOSTILE, others → PASSIVE)</li>
 * </ol>
 */
public enum RPGMobClass {
    /**
     * Non-combat creatures (critters, birds, fish, ambient wildlife).
     * <p>Effect: Minimal XP (0.1x), no stat scaling. Mostly ignored by RPG systems.
     */
    PASSIVE,

    /**
     * Small or weak hostile creatures (void larvae, foxes, small vermin).
     * <p>Effect: Reduced XP (0.5x) but normal stat scaling (1.0x).
     * <p>Use for mobs that are easy to kill in large numbers and shouldn't
     * be farmed for full XP.
     */
    MINOR,

    /**
     * Standard combat enemies (zombies, trorks, predators, defensive animals).
     * <p>Effect: Base XP (1.0x) and base stat scaling (1.0x).
     */
    HOSTILE,

    /**
     * Mini-bosses and veteran enemies (captains, chieftains, ogres).
     * <p>Effect: +50% XP (1.5x) and +50% stats (1.5x).
     */
    ELITE,

    /**
     * Major bosses (dragons, yeti, dungeon bosses).
     * <p>Effect: +400% XP (5.0x) and +200% stats (3.0x).
     */
    BOSS;

    public static final EnumCodec<RPGMobClass> CODEC = new EnumCodec<>(RPGMobClass.class);

    /**
     * Checks if this class represents a combat-relevant mob.
     * Combat-relevant mobs receive stat scaling and can grant XP.
     *
     * @return true if MINOR, HOSTILE, ELITE, or BOSS
     */
    public boolean isCombatRelevant() {
        return this != PASSIVE;
    }

    /**
     * Checks if this class is hostile (will attack or defend).
     * Used for spawn multipliers and threat detection.
     * <p>Note: MINOR mobs are hostile in behavior but use reduced XP.
     *
     * @return true if MINOR, HOSTILE, ELITE, or BOSS
     */
    public boolean isHostile() {
        return this == MINOR || this == HOSTILE || this == ELITE || this == BOSS;
    }
}
