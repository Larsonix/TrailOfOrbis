package io.github.larsonix.trailoforbis.compat.party;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Configuration for party mod integration.
 *
 * <p>Loaded from {@code config/party.yml}. All features gracefully
 * degrade when no party mod is detected.
 */
public class PartyConfig {

    private boolean enabled = true;
    private String apiClass = "me.tsumori.partypro.api.PartyProAPI";
    private DetectionConfig detection = new DetectionConfig();
    private XpSharingConfig xpSharing = new XpSharingConfig();
    private AntiBoostingConfig antiBoosting = new AntiBoostingConfig();
    private PvpProtectionConfig pvpProtection = new PvpProtectionConfig();
    private RealmCoopConfig realmCoop = new RealmCoopConfig();
    private HudConfig hud = new HudConfig();

    // ── Getters ──

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean v) { this.enabled = v; }

    public String getApiClass() { return apiClass; }
    public void setApiClass(String v) { this.apiClass = v; }
    public void setApi_class(String v) { this.apiClass = v; }

    public DetectionConfig getDetection() { return detection; }
    public void setDetection(DetectionConfig v) { this.detection = v; }

    public XpSharingConfig getXpSharing() { return xpSharing; }
    public void setXpSharing(XpSharingConfig v) { this.xpSharing = v; }
    public void setXp_sharing(XpSharingConfig v) { this.xpSharing = v; }

    public AntiBoostingConfig getAntiBoosting() { return antiBoosting; }
    public void setAntiBoosting(AntiBoostingConfig v) { this.antiBoosting = v; }
    public void setAnti_boosting(AntiBoostingConfig v) { this.antiBoosting = v; }

    public PvpProtectionConfig getPvpProtection() { return pvpProtection; }
    public void setPvpProtection(PvpProtectionConfig v) { this.pvpProtection = v; }
    public void setPvp_protection(PvpProtectionConfig v) { this.pvpProtection = v; }

    public RealmCoopConfig getRealmCoop() { return realmCoop; }
    public void setRealmCoop(RealmCoopConfig v) { this.realmCoop = v; }
    public void setRealm_coop(RealmCoopConfig v) { this.realmCoop = v; }

    public HudConfig getHud() { return hud; }
    public void setHud(HudConfig v) { this.hud = v; }

    // ── Nested configs ──

    public static class DetectionConfig {
        private int maxRetries = 5;
        private long retryDelayMs = 2000;

        public int getMaxRetries() { return maxRetries; }
        public void setMaxRetries(int v) { this.maxRetries = v; }
        public void setMax_retries(int v) { this.maxRetries = v; }

        public long getRetryDelayMs() { return retryDelayMs; }
        public void setRetryDelayMs(long v) { this.retryDelayMs = v; }
        public void setRetry_delay_ms(long v) { this.retryDelayMs = v; }
    }

    public static class XpSharingConfig {
        private boolean enabled = true;
        private String mode = "equal";
        private Map<String, Double> partySizeMultipliers = createDefaultMultipliers();
        private double maxDistance = -1.0;
        private String sourceName = "PARTY_SHARE";

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean v) { this.enabled = v; }

        public String getMode() { return mode; }
        public void setMode(String v) { this.mode = v; }

        public Map<String, Double> getPartySizeMultipliers() { return partySizeMultipliers; }
        public void setPartySizeMultipliers(Map<String, Double> v) { this.partySizeMultipliers = v; }
        public void setParty_size_multipliers(Map<String, Double> v) { this.partySizeMultipliers = v; }

        public double getMaxDistance() { return maxDistance; }
        public void setMaxDistance(double v) { this.maxDistance = v; }
        public void setMax_distance(double v) { this.maxDistance = v; }

        public String getSourceName() { return sourceName; }
        public void setSourceName(String v) { this.sourceName = v; }
        public void setSource_name(String v) { this.sourceName = v; }

        public double getMultiplierForSize(int partySize) {
            if (partySizeMultipliers != null) {
                Double specific = partySizeMultipliers.get(String.valueOf(partySize));
                if (specific != null) return specific;
            }
            return 1.0;
        }

        private static Map<String, Double> createDefaultMultipliers() {
            var map = new LinkedHashMap<String, Double>();
            map.put("2", 1.2);
            map.put("3", 1.35);
            map.put("4", 1.45);
            map.put("5", 1.5);
            return map;
        }
    }

    public static class AntiBoostingConfig {
        private boolean enabled = true;
        private boolean usePartyMaxLevel = true;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean v) { this.enabled = v; }

        public boolean isUsePartyMaxLevel() { return usePartyMaxLevel; }
        public void setUsePartyMaxLevel(boolean v) { this.usePartyMaxLevel = v; }
        public void setUse_party_max_level(boolean v) { this.usePartyMaxLevel = v; }
    }

    public static class PvpProtectionConfig {
        private boolean enabled = true;
        private boolean blockFriendlyFire = true;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean v) { this.enabled = v; }

        public boolean isBlockFriendlyFire() { return blockFriendlyFire; }
        public void setBlockFriendlyFire(boolean v) { this.blockFriendlyFire = v; }
        public void setBlock_friendly_fire(boolean v) { this.blockFriendlyFire = v; }
    }

    public static class RealmCoopConfig {
        private boolean enabled = true;
        private boolean autoEnterParty = true;
        private double entryRadius = -1.0;
        private String entryMessage = "Entering realm with your party...";

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean v) { this.enabled = v; }

        public boolean isAutoEnterParty() { return autoEnterParty; }
        public void setAutoEnterParty(boolean v) { this.autoEnterParty = v; }
        public void setAuto_enter_party(boolean v) { this.autoEnterParty = v; }

        public double getEntryRadius() { return entryRadius; }
        public void setEntryRadius(double v) { this.entryRadius = v; }
        public void setEntry_radius(double v) { this.entryRadius = v; }

        public String getEntryMessage() { return entryMessage; }
        public void setEntryMessage(String v) { this.entryMessage = v; }
        public void setEntry_message(String v) { this.entryMessage = v; }
    }

    public static class HudConfig {
        private boolean enabled = true;
        private String levelFormat = "Lv.{level}";
        private int levelSlot = 1;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean v) { this.enabled = v; }

        public String getLevelFormat() { return levelFormat; }
        public void setLevelFormat(String v) { this.levelFormat = v; }
        public void setLevel_format(String v) { this.levelFormat = v; }

        public int getLevelSlot() { return levelSlot; }
        public void setLevelSlot(int v) { this.levelSlot = v; }
        public void setLevel_slot(int v) { this.levelSlot = v; }
    }
}
