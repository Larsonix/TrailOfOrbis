package io.github.larsonix.trailoforbis.leveling.config;

import javax.annotation.Nonnull;

/**
 * Configuration for the leveling system.
 *
 * <p>Supports loading from standalone YAML file (leveling.yml) with snake_case keys.
 *
 * <p>Controls XP formulas, gain rates, loss penalties, and UI settings.
 */
public class LevelingConfig {

    private boolean enabled = true;
    private FormulaConfig formula = new FormulaConfig();
    private XpGainConfig xpGain = new XpGainConfig();
    private XpLossConfig xpLoss = new XpLossConfig();
    private UiConfig ui = new UiConfig();
    private CelebrationConfig celebration = new CelebrationConfig();

    // ==================== Getters and Setters ====================

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public FormulaConfig getFormula() {
        return formula;
    }

    public void setFormula(FormulaConfig formula) {
        this.formula = formula;
    }

    public XpGainConfig getXpGain() {
        return xpGain;
    }

    public void setXpGain(XpGainConfig xpGain) {
        this.xpGain = xpGain;
    }

    // YAML snake_case setter
    public void setXp_gain(XpGainConfig xpGain) {
        this.xpGain = xpGain;
    }

    public XpLossConfig getXpLoss() {
        return xpLoss;
    }

    public void setXpLoss(XpLossConfig xpLoss) {
        this.xpLoss = xpLoss;
    }

    // YAML snake_case setter
    public void setXp_loss(XpLossConfig xpLoss) {
        this.xpLoss = xpLoss;
    }

    public UiConfig getUi() {
        return ui;
    }

    public void setUi(UiConfig ui) {
        this.ui = ui;
    }

    public CelebrationConfig getCelebration() {
        return celebration;
    }

    public void setCelebration(CelebrationConfig celebration) {
        this.celebration = celebration;
    }

    // ==================== Validation ====================

    /**
     * @throws ConfigValidationException if validation fails
     */
    public void validate() throws ConfigValidationException {
        if (formula.maxLevel < 1) {
            throw new ConfigValidationException("formula.max_level must be >= 1");
        }

        String type = formula.type != null ? formula.type.toLowerCase() : "effort";
        if ("effort".equals(type)) {
            EffortConfig eff = formula.effort;
            if (eff.baseMobs <= 0) {
                throw new ConfigValidationException("formula.effort.base_mobs must be > 0");
            }
            if (eff.targetMobs <= eff.baseMobs) {
                throw new ConfigValidationException("formula.effort.target_mobs must be > base_mobs");
            }
            if (eff.targetLevel < 2) {
                throw new ConfigValidationException("formula.effort.target_level must be >= 2");
            }
        } else if ("exponential".equals(type)) {
            if (formula.baseXp <= 0) {
                throw new ConfigValidationException("formula.base_xp must be > 0");
            }
            if (formula.exponent <= 0) {
                throw new ConfigValidationException("formula.exponent must be > 0");
            }
        } else {
            throw new ConfigValidationException("formula.type must be 'effort' or 'exponential', got: " + formula.type);
        }
        if (xpGain.xpPerMobLevel < 0) {
            throw new ConfigValidationException("xp_gain.xp_per_mob_level must be >= 0");
        }
        if (xpGain.poolMultiplier < 0) {
            throw new ConfigValidationException("xp_gain.pool_multiplier must be >= 0");
        }
        // NOTE: Boss/elite XP multipliers moved to mob-classification.yml (xp_multipliers).
        // The old bossMultiplier/eliteMultiplier fields were dead code and have been removed.
        if (xpLoss.percentage < 0 || xpLoss.percentage > 1.0) {
            throw new ConfigValidationException("xp_loss.percentage must be between 0 and 1.0");
        }
        if (xpLoss.minLevel < 1) {
            throw new ConfigValidationException("xp_loss.min_level must be >= 1");
        }
    }

    /**
     * Exception thrown when config validation fails.
     */
    public static class ConfigValidationException extends Exception {
        public ConfigValidationException(String message) {
            super(message);
        }
    }

    // ==================== Diagnostics ====================

    /** Returns a diagnostic summary of the configuration. */
    @Nonnull
    public String getDiagnostics() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== LevelingConfig ===\n");
        sb.append("enabled: ").append(enabled).append("\n");
        sb.append("\n[Formula]\n");
        sb.append("  type: ").append(formula.type).append("\n");
        sb.append("  max_level: ").append(formula.maxLevel).append("\n");

        String type = formula.type != null ? formula.type.toLowerCase() : "effort";
        if ("effort".equals(type)) {
            EffortConfig eff = formula.effort;
            sb.append("  base_mobs: ").append(eff.baseMobs).append("\n");
            sb.append("  target_mobs: ").append(eff.targetMobs).append("\n");
            sb.append("  target_level: ").append(eff.targetLevel).append("\n");
            double derivedExponent = Math.log(eff.targetMobs / eff.baseMobs) / Math.log(eff.targetLevel);
            sb.append("  derived_exponent: ").append(String.format("%.4f", derivedExponent)).append("\n");
            sb.append("  mobs at Lv1: ").append(String.format("%.1f", eff.baseMobs)).append("\n");
            sb.append("  mobs at Lv10: ").append(String.format("%.1f", eff.baseMobs * Math.pow(10, derivedExponent))).append("\n");
            sb.append("  mobs at Lv50: ").append(String.format("%.1f", eff.baseMobs * Math.pow(50, derivedExponent))).append("\n");
            sb.append("  mobs at Lv100: ").append(String.format("%.1f", eff.baseMobs * Math.pow(100, derivedExponent))).append("\n");
        } else {
            sb.append("  base_xp: ").append(formula.baseXp).append("\n");
            sb.append("  exponent: ").append(formula.exponent).append("\n");
            sb.append("  XP for level 10: ").append(String.format("%.0f", formula.baseXp * Math.pow(9, formula.exponent))).append("\n");
            sb.append("  XP for level 50: ").append(String.format("%.0f", formula.baseXp * Math.pow(49, formula.exponent))).append("\n");
        }
        sb.append("\n[XP Gain]\n");
        sb.append("  enabled: ").append(xpGain.enabled).append("\n");
        sb.append("  xp_per_mob_level: ").append(xpGain.xpPerMobLevel).append("\n");
        sb.append("  pool_multiplier: ").append(xpGain.poolMultiplier).append("\n");
        // Boss/elite XP multipliers are in mob-classification.yml, not here
        sb.append("\n[XP Loss]\n");
        sb.append("  enabled: ").append(xpLoss.enabled).append("\n");
        sb.append("  percentage: ").append(String.format("%.0f%%", xpLoss.percentage * 100)).append("\n");
        sb.append("  min_level: ").append(xpLoss.minLevel).append("\n");
        sb.append("\n[UI]\n");
        sb.append("  show_xp_bar: ").append(ui.showXpBar).append("\n");
        sb.append("  show_level_up_notification: ").append(ui.showLevelUpNotification).append("\n");
        sb.append("  show_xp_gain_notification: ").append(ui.showXpGainNotification).append("\n");
        return sb.toString();
    }

    // ==================== Nested Config Classes ====================

    /**
     * XP-to-level formula configuration.
     *
     * <p>Supports two formula types:
     * <ul>
     *   <li>{@code "effort"} (default) — effort-based: defines mobs-per-level curve,
     *       derives XP from estimated mob rewards</li>
     *   <li>{@code "exponential"} — legacy: XP = baseXp × (level - 1)^exponent</li>
     * </ul>
     */
    public static class FormulaConfig {
        private String type = "effort";
        private int maxLevel = 1_000_000;

        // Effort-based formula config
        private EffortConfig effort = new EffortConfig();

        // Legacy exponential formula config
        private double baseXp = 50.0;
        private double exponent = 2.2;

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public int getMaxLevel() {
            return maxLevel;
        }

        public void setMaxLevel(int maxLevel) {
            this.maxLevel = maxLevel;
        }

        // YAML snake_case setter
        public void setMax_level(int maxLevel) {
            this.maxLevel = maxLevel;
        }

        public EffortConfig getEffort() {
            return effort;
        }

        public void setEffort(EffortConfig effort) {
            this.effort = effort;
        }

        public double getBaseXp() {
            return baseXp;
        }

        public void setBaseXp(double baseXp) {
            this.baseXp = baseXp;
        }

        // YAML snake_case setter
        public void setBase_xp(double baseXp) {
            this.baseXp = baseXp;
        }

        public double getExponent() {
            return exponent;
        }

        public void setExponent(double exponent) {
            this.exponent = exponent;
        }
    }

    /**
     * Configuration for the effort-based leveling formula.
     *
     * <p>Three numbers describe the entire gameplay feel:
     * <ul>
     *   <li>{@code baseMobs} — mobs per level at level 1</li>
     *   <li>{@code targetMobs} — mobs per level at targetLevel</li>
     *   <li>{@code targetLevel} — reference level for the curve</li>
     * </ul>
     */
    public static class EffortConfig {
        private double baseMobs = 3.0;
        private double targetMobs = 150.0;
        private int targetLevel = 100;

        public double getBaseMobs() {
            return baseMobs;
        }

        public void setBaseMobs(double baseMobs) {
            this.baseMobs = baseMobs;
        }

        // YAML snake_case setter
        public void setBase_mobs(double baseMobs) {
            this.baseMobs = baseMobs;
        }

        public double getTargetMobs() {
            return targetMobs;
        }

        public void setTargetMobs(double targetMobs) {
            this.targetMobs = targetMobs;
        }

        // YAML snake_case setter
        public void setTarget_mobs(double targetMobs) {
            this.targetMobs = targetMobs;
        }

        public int getTargetLevel() {
            return targetLevel;
        }

        public void setTargetLevel(int targetLevel) {
            this.targetLevel = targetLevel;
        }

        // YAML snake_case setter
        public void setTarget_level(int targetLevel) {
            this.targetLevel = targetLevel;
        }
    }

    /**
     * XP gain configuration for mob kills.
     */
    public static class XpGainConfig {
        private boolean enabled = true;
        private double xpPerMobLevel = 5.0;
        private double poolMultiplier = 0.1;
        private LevelGapConfig levelGap = new LevelGapConfig();
        // Boss/elite XP multipliers are in MobClassificationConfig (mob-classification.yml), not here.

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public double getXpPerMobLevel() {
            return xpPerMobLevel;
        }

        public void setXpPerMobLevel(double xpPerMobLevel) {
            this.xpPerMobLevel = xpPerMobLevel;
        }

        // YAML snake_case setter
        public void setXp_per_mob_level(double xpPerMobLevel) {
            this.xpPerMobLevel = xpPerMobLevel;
        }

        public double getPoolMultiplier() {
            return poolMultiplier;
        }

        public void setPoolMultiplier(double poolMultiplier) {
            this.poolMultiplier = poolMultiplier;
        }

        // YAML snake_case setter
        public void setPool_multiplier(double poolMultiplier) {
            this.poolMultiplier = poolMultiplier;
        }

        public LevelGapConfig getLevelGap() {
            return levelGap;
        }

        public void setLevelGap(LevelGapConfig levelGap) {
            this.levelGap = levelGap;
        }

        // YAML snake_case setter
        public void setLevel_gap(LevelGapConfig levelGap) {
            this.levelGap = levelGap;
        }

        // getBossMultiplier/getEliteMultiplier removed — these were dead code.
        // XP multipliers live in mob-classification.yml via MobClassificationConfig.
    }

    /**
     * Level-gap XP penalty configuration.
     *
     * <p>Reduces XP when mob level is far above or below player level.
     * Uses an asymmetric PoE-style rational curve — aggressive downward
     * (trivial mobs), gentle upward (challenging mobs).
     *
     * <p>Safe zone = {@code safeZoneBase + safeZonePercent * playerLevel}.
     * Within the safe zone, full XP is granted. Beyond it, XP degrades
     * smoothly to the configured floor.
     */
    public static class LevelGapConfig {
        private boolean enabled = true;
        private int safeZoneBase = 20;
        private double safeZonePercent = 0.10;
        // Downward (mob too low): aggressive penalty
        private double downwardFalloffFactor = 0.5;
        private double downwardExponent = 2.5;
        private double downwardFloor = 0.01;
        // Upward (mob too high): gentle penalty
        private double upwardFalloffFactor = 1.0;
        private double upwardExponent = 1.5;
        private double upwardFloor = 0.05;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public int getSafeZoneBase() { return safeZoneBase; }
        public void setSafeZoneBase(int v) { this.safeZoneBase = v; }
        public void setSafe_zone_base(int v) { this.safeZoneBase = v; }

        public double getSafeZonePercent() { return safeZonePercent; }
        public void setSafeZonePercent(double v) { this.safeZonePercent = v; }
        public void setSafe_zone_percent(double v) { this.safeZonePercent = v; }

        public double getDownwardFalloffFactor() { return downwardFalloffFactor; }
        public void setDownwardFalloffFactor(double v) { this.downwardFalloffFactor = v; }
        public void setDownward_falloff_factor(double v) { this.downwardFalloffFactor = v; }

        public double getDownwardExponent() { return downwardExponent; }
        public void setDownwardExponent(double v) { this.downwardExponent = v; }
        public void setDownward_exponent(double v) { this.downwardExponent = v; }

        public double getDownwardFloor() { return downwardFloor; }
        public void setDownwardFloor(double v) { this.downwardFloor = v; }
        public void setDownward_floor(double v) { this.downwardFloor = v; }

        public double getUpwardFalloffFactor() { return upwardFalloffFactor; }
        public void setUpwardFalloffFactor(double v) { this.upwardFalloffFactor = v; }
        public void setUpward_falloff_factor(double v) { this.upwardFalloffFactor = v; }

        public double getUpwardExponent() { return upwardExponent; }
        public void setUpwardExponent(double v) { this.upwardExponent = v; }
        public void setUpward_exponent(double v) { this.upwardExponent = v; }

        public double getUpwardFloor() { return upwardFloor; }
        public void setUpwardFloor(double v) { this.upwardFloor = v; }
        public void setUpward_floor(double v) { this.upwardFloor = v; }
    }

    /**
     * XP loss configuration on player death.
     */
    public static class XpLossConfig {
        private boolean enabled = false;
        private double percentage = 0.10;
        private int minLevel = 1;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public double getPercentage() {
            return percentage;
        }

        public void setPercentage(double percentage) {
            this.percentage = percentage;
        }

        public int getMinLevel() {
            return minLevel;
        }

        public void setMinLevel(int minLevel) {
            this.minLevel = minLevel;
        }

        // YAML snake_case setter
        public void setMin_level(int minLevel) {
            this.minLevel = minLevel;
        }
    }

    /**
     * UI configuration for leveling displays.
     */
    public static class UiConfig {
        private boolean showXpBar = true;
        private boolean showLevelUpNotification = true;
        private boolean showXpGainNotification = true;

        public boolean isShowXpBar() {
            return showXpBar;
        }

        public void setShowXpBar(boolean showXpBar) {
            this.showXpBar = showXpBar;
        }

        // YAML snake_case setter
        public void setShow_xp_bar(boolean showXpBar) {
            this.showXpBar = showXpBar;
        }

        public boolean isShowLevelUpNotification() {
            return showLevelUpNotification;
        }

        public void setShowLevelUpNotification(boolean showLevelUpNotification) {
            this.showLevelUpNotification = showLevelUpNotification;
        }

        // YAML snake_case setter
        public void setShow_level_up_notification(boolean showLevelUpNotification) {
            this.showLevelUpNotification = showLevelUpNotification;
        }

        public boolean isShowXpGainNotification() {
            return showXpGainNotification;
        }

        public void setShowXpGainNotification(boolean showXpGainNotification) {
            this.showXpGainNotification = showXpGainNotification;
        }

        // YAML snake_case setter
        public void setShow_xp_gain_notification(boolean showXpGainNotification) {
            this.showXpGainNotification = showXpGainNotification;
        }
    }

    /**
     * Configuration for level-up celebrations: banners, sounds, chat breakdowns, milestones.
     */
    public static class CelebrationConfig {
        private BannerConfig banner = new BannerConfig();
        private SoundConfig sound = new SoundConfig();
        private ChatConfig chat = new ChatConfig();
        private MilestoneConfig milestones = new MilestoneConfig();
        private EmoteConfig emote = new EmoteConfig();

        public BannerConfig getBanner() {
            return banner;
        }

        public void setBanner(BannerConfig banner) {
            this.banner = banner;
        }

        public SoundConfig getSound() {
            return sound;
        }

        public void setSound(SoundConfig sound) {
            this.sound = sound;
        }

        public ChatConfig getChat() {
            return chat;
        }

        public void setChat(ChatConfig chat) {
            this.chat = chat;
        }

        public MilestoneConfig getMilestones() {
            return milestones;
        }

        public void setMilestones(MilestoneConfig milestones) {
            this.milestones = milestones;
        }

        public EmoteConfig getEmote() {
            return emote;
        }

        public void setEmote(EmoteConfig emote) {
            this.emote = emote;
        }
    }

    /**
     * Fullscreen banner timing per milestone tier.
     */
    public static class BannerConfig {
        private boolean enabled = true;
        private BannerTiming normal = new BannerTiming(2.5f, 0.5f, 1.0f);
        private BannerTiming minor = new BannerTiming(3.0f, 0.5f, 1.0f);
        private BannerTiming major = new BannerTiming(4.0f, 1.0f, 1.5f);
        private BannerTiming huge = new BannerTiming(5.0f, 1.0f, 1.5f);

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public BannerTiming getNormal() {
            return normal;
        }

        public void setNormal(BannerTiming normal) {
            this.normal = normal;
        }

        public BannerTiming getMinor() {
            return minor;
        }

        public void setMinor(BannerTiming minor) {
            this.minor = minor;
        }

        public BannerTiming getMajor() {
            return major;
        }

        public void setMajor(BannerTiming major) {
            this.major = major;
        }

        public BannerTiming getHuge() {
            return huge;
        }

        public void setHuge(BannerTiming huge) {
            this.huge = huge;
        }
    }

    /**
     * Timing values for a single banner tier.
     */
    public static class BannerTiming {
        private float duration = 2.5f;
        private float fadeIn = 0.5f;
        private float fadeOut = 1.0f;

        public BannerTiming() {
        }

        public BannerTiming(float duration, float fadeIn, float fadeOut) {
            this.duration = duration;
            this.fadeIn = fadeIn;
            this.fadeOut = fadeOut;
        }

        public float getDuration() {
            return duration;
        }

        public void setDuration(float duration) {
            this.duration = duration;
        }

        public float getFadeIn() {
            return fadeIn;
        }

        public void setFadeIn(float fadeIn) {
            this.fadeIn = fadeIn;
        }

        // YAML snake_case setter
        public void setFade_in(float fadeIn) {
            this.fadeIn = fadeIn;
        }

        public float getFadeOut() {
            return fadeOut;
        }

        public void setFadeOut(float fadeOut) {
            this.fadeOut = fadeOut;
        }

        // YAML snake_case setter
        public void setFade_out(float fadeOut) {
            this.fadeOut = fadeOut;
        }
    }

    /**
     * Sound effect IDs per milestone tier.
     */
    public static class SoundConfig {
        private boolean enabled = true;
        private String normal = "SFX_Discovery_Z1_Short";
        private String minor = "SFX_Discovery_Z1_Medium";
        private String major = "SFX_Discovery_Z3_Medium";
        private String huge = "SFX_Chest_Legendary_FirstOpen_Player";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getNormal() {
            return normal;
        }

        public void setNormal(String normal) {
            this.normal = normal;
        }

        public String getMinor() {
            return minor;
        }

        public void setMinor(String minor) {
            this.minor = minor;
        }

        public String getMajor() {
            return major;
        }

        public void setMajor(String major) {
            this.major = major;
        }

        public String getHuge() {
            return huge;
        }

        public void setHuge(String huge) {
            this.huge = huge;
        }
    }

    /**
     * Chat breakdown settings.
     */
    public static class ChatConfig {
        private boolean enabled = true;
        private boolean showTotals = true;
        private boolean showBorders = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isShowTotals() {
            return showTotals;
        }

        public void setShowTotals(boolean showTotals) {
            this.showTotals = showTotals;
        }

        // YAML snake_case setter
        public void setShow_totals(boolean showTotals) {
            this.showTotals = showTotals;
        }

        public boolean isShowBorders() {
            return showBorders;
        }

        public void setShowBorders(boolean showBorders) {
            this.showBorders = showBorders;
        }

        // YAML snake_case setter
        public void setShow_borders(boolean showBorders) {
            this.showBorders = showBorders;
        }
    }

    /**
     * Milestone interval definitions.
     */
    public static class MilestoneConfig {
        private int minor = 10;
        private int major = 50;
        private int huge = 250;

        public int getMinor() {
            return minor;
        }

        public void setMinor(int minor) {
            this.minor = minor;
        }

        public int getMajor() {
            return major;
        }

        public void setMajor(int major) {
            this.major = major;
        }

        public int getHuge() {
            return huge;
        }

        public void setHuge(int huge) {
            this.huge = huge;
        }
    }

    /**
     * Emote celebration configuration.
     *
     * <p>When a valid emote ID is configured, the player character will perform
     * the emote animation on level-up. Set to empty string to disable.
     *
     * <p>Use the {@code /emote} command in-game to discover available emote IDs,
     * or check server logs at startup for the list.
     */
    public static class EmoteConfig {
        private String emoteId = "";

        public String getEmoteId() {
            return emoteId;
        }

        public void setEmoteId(String emoteId) {
            this.emoteId = emoteId;
        }

        // YAML snake_case setter
        public void setEmote_id(String emoteId) {
            this.emoteId = emoteId;
        }
    }
}
