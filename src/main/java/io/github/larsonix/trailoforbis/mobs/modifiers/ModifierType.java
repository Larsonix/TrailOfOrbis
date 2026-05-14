package io.github.larsonix.trailoforbis.mobs.modifiers;

import io.github.larsonix.trailoforbis.elemental.ElementType;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * All mob modifier types in Trail of Orbis.
 *
 * <p>Each modifier carries its full definition: category, complexity tier,
 * display name, visual colors, ModelVFX asset, and stat bonus. This enum
 * is the single source of truth for what modifiers exist.
 *
 * <p>Modifiers are fully random (any mod on any mob) and level-gated
 * by tier. Adding a new modifier requires only a new enum value here
 * plus a config entry in {@code mob-modifiers.yml}.
 *
 * @see ModifierCategory
 * @see ModifierTier
 * @see StatBonus
 */
public enum ModifierType {

    // ==================== Category A: Stat-Based (Tier 1) ====================

    HARDENED(ModifierCategory.STAT, ModifierTier.TIER_1,
        "Hardened", "#8B7355", "#A0926B", "Erosion_Test",
        StatBonus.armor(0.50)),

    VIGOROUS(ModifierCategory.STAT, ModifierTier.TIER_1,
        "Vigorous", "#2D5A27", "#4A8B3F", null,
        StatBonus.maxHp(0.50)),

    FIERCE(ModifierCategory.STAT, ModifierTier.TIER_1,
        "Fierce", "#8B2500", "#CC3300", null,
        StatBonus.damage(0.35)),

    SWIFT(ModifierCategory.STAT, ModifierTier.TIER_1,
        "Swift", "#88CCFF", "#CCECFF", null,
        StatBonus.speed(0.40)),

    RESOLUTE(ModifierCategory.STAT, ModifierTier.TIER_1,
        "Resolute", "#3A3A3A", "#5A5A5A", null,
        StatBonus.knockback(1.0)),

    // ==================== Category B: Elemental (Tier 2) ====================

    BLAZING(ModifierCategory.ELEMENTAL, ModifierTier.TIER_2,
        "Blazing", "#FF4400", "#FFAA00", "Burn",
        StatBonus.elementDamage(ElementType.FIRE, 0.30, 0.20)),

    FROZEN(ModifierCategory.ELEMENTAL, ModifierTier.TIER_2,
        "Frozen", "#44AADD", "#CCECFF", "Freeze",
        StatBonus.elementDamage(ElementType.WATER, 0.30, 0.20)),

    THUNDEROUS(ModifierCategory.ELEMENTAL, ModifierTier.TIER_2,
        "Thunderous", "#DDAA00", "#FFFF44", null,
        StatBonus.elementDamage(ElementType.LIGHTNING, 0.30, 0.20)),

    VENOMOUS(ModifierCategory.ELEMENTAL, ModifierTier.TIER_2,
        "Venomous", "#225522", "#66AA44", "Poison",
        StatBonus.elementDamage(ElementType.VOID, 0.30, 0.20)),

    // ==================== Category C: Tactical Counter (Tier 3) ====================

    REFLECTIVE(ModifierCategory.TACTICAL, ModifierTier.TIER_3,
        "Reflective", "#888888", "#CCCCCC", null,
        StatBonus.reflect(0.15)),

    WARDING(ModifierCategory.TACTICAL, ModifierTier.TIER_3,
        "Warding", "#442266", "#7744AA", null,
        StatBonus.elementalResist(0.50)),

    EVASIVE(ModifierCategory.TACTICAL, ModifierTier.TIER_3,
        "Evasive", "#445566", "#778899", null,
        StatBonus.evasion(0.25)),

    // ==================== Category D: Behavioral (Tier 2 / Tier 4) ====================

    ENRAGED(ModifierCategory.BEHAVIORAL, ModifierTier.TIER_2,
        "Enraged", "#CC0000", "#FF2200", null,
        StatBonus.EMPTY),

    REGENERATING(ModifierCategory.BEHAVIORAL, ModifierTier.TIER_3,
        "Regenerating", "#22CC22", "#88FF88", null,
        StatBonus.EMPTY),

    SHADOW_STEP(ModifierCategory.BEHAVIORAL, ModifierTier.TIER_4,
        "Shadow Step", "#1A0033", "#330066", "Portal_Teleport",
        StatBonus.EMPTY),

    SUMMONER(ModifierCategory.BEHAVIORAL, ModifierTier.TIER_4,
        "Summoner", "#888822", "#CCCC44", null,
        StatBonus.EMPTY),

    // ==================== Category E: Aura/Area (Tier 3 / Tier 4) ====================

    PACK_LEADER(ModifierCategory.AURA, ModifierTier.TIER_3,
        "Pack Leader", "#AA8800", "#FFCC44", "Drop_Rare",
        StatBonus.EMPTY),

    FROST_AURA(ModifierCategory.AURA, ModifierTier.TIER_3,
        "Frost Aura", "#3388BB", "#AADDEE", null,
        StatBonus.EMPTY),

    RALLYING(ModifierCategory.AURA, ModifierTier.TIER_4,
        "Rallying", "#660000", "#AA2222", null,
        StatBonus.EMPTY),

    // ==================== Category F: Death Trigger (Tier 4) ====================

    VOLATILE(ModifierCategory.DEATH, ModifierTier.TIER_4,
        "Volatile", "#FF6600", "#FFCC00", null,
        StatBonus.EMPTY);

    // ==================== Fields ====================

    private final ModifierCategory category;
    private final ModifierTier tier;
    private final String displayName;
    private final String tintBottom;
    private final String tintTop;
    @Nullable private final String modelVfxId;
    private final StatBonus statBonus;

    ModifierType(
        @Nonnull ModifierCategory category,
        @Nonnull ModifierTier tier,
        @Nonnull String displayName,
        @Nonnull String tintBottom,
        @Nonnull String tintTop,
        @Nullable String modelVfxId,
        @Nonnull StatBonus statBonus
    ) {
        this.category = category;
        this.tier = tier;
        this.displayName = displayName;
        this.tintBottom = tintBottom;
        this.tintTop = tintTop;
        this.modelVfxId = modelVfxId;
        this.statBonus = statBonus;
    }

    // ==================== Accessors ====================

    @Nonnull public ModifierCategory getCategory() { return category; }
    @Nonnull public ModifierTier getTier() { return tier; }
    @Nonnull public String getDisplayName() { return displayName; }
    @Nonnull public String getTintBottom() { return tintBottom; }
    @Nonnull public String getTintTop() { return tintTop; }
    @Nullable public String getModelVfxId() { return modelVfxId; }
    @Nonnull public StatBonus getStatBonus() { return statBonus; }

    // ==================== Query Methods ====================

    /** True if this modifier needs the tick system for runtime behavior. */
    public boolean requiresTick() {
        return category == ModifierCategory.BEHAVIORAL
            || category == ModifierCategory.AURA
            || this == BLAZING       // fire trail
            || this == THUNDEROUS    // lightning strike cooldown
            || this == FROZEN;       // proximity slow aura
    }

    /** True if this modifier triggers effects on mob death. */
    public boolean hasDeathTrigger() {
        return this == VOLATILE || this == RALLYING || this == VENOMOUS;
    }

    /** True if this modifier has an aura that affects nearby entities. */
    public boolean hasAura() {
        return this == PACK_LEADER || this == FROST_AURA || this == FROZEN;
    }

    /** True if this modifier modifies stats at generation time. */
    public boolean hasStatBonus() {
        return statBonus.hasEffect();
    }

    // ==================== Pool Queries ====================

    /**
     * Returns all modifiers available at the given mob level.
     * Respects level gating — only returns modifiers whose tier's
     * levelGate is at or below the mob level.
     */
    @Nonnull
    public static List<ModifierType> availableAtLevel(int level) {
        ModifierTier highest = ModifierTier.highestForLevel(level);
        List<ModifierType> available = new ArrayList<>();
        for (ModifierType type : values()) {
            if (type.tier.ordinal() <= highest.ordinal()) {
                available.add(type);
            }
        }
        return available;
    }

    /**
     * Looks up a modifier by name (case-insensitive, matches enum name or displayName).
     */
    @Nullable
    public static ModifierType fromName(@Nullable String name) {
        if (name == null || name.isEmpty()) return null;
        for (ModifierType type : values()) {
            if (type.name().equalsIgnoreCase(name)
                || type.displayName.equalsIgnoreCase(name)) {
                return type;
            }
        }
        return null;
    }

    /**
     * Returns the config key for this modifier (lowercase enum name).
     */
    @Nonnull
    public String configKey() {
        return name().toLowerCase();
    }
}
