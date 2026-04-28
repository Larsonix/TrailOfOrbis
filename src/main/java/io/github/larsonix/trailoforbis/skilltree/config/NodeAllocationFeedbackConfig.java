package io.github.larsonix.trailoforbis.skilltree.config;

/**
 * Configuration for node allocation/deallocation feedback.
 *
 * <p>Controls banners, sounds, chat breakdowns, and toast notifications
 * when players allocate or deallocate skill tree nodes.
 *
 * <p>Feedback is tiered by node type: basic, notable, keystone.
 * Basic nodes skip the banner to avoid spam when allocating many in sequence.
 */
public class NodeAllocationFeedbackConfig {

    private BannerConfig banner = new BannerConfig();
    private SoundConfig sound = new SoundConfig();
    private ChatConfig chat = new ChatConfig();
    private ToastConfig toast = new ToastConfig();

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

    public ToastConfig getToast() {
        return toast;
    }

    public void setToast(ToastConfig toast) {
        this.toast = toast;
    }

    // ═══════════════════════════════════════════════════════════════════
    // BANNER CONFIG
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Fullscreen banner timing per node tier.
     *
     * <p>Basic nodes skip the banner (too spammy). Notable and keystone get banners.
     */
    public static class BannerConfig {
        private boolean enabled = true;
        private BannerTiming basic = new BannerTiming(1.5f, 0.3f, 0.5f);
        private BannerTiming notable = new BannerTiming(2.5f, 0.5f, 1.0f);
        private BannerTiming keystone = new BannerTiming(4.0f, 1.0f, 1.5f);

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public BannerTiming getBasic() {
            return basic;
        }

        public void setBasic(BannerTiming basic) {
            this.basic = basic;
        }

        public BannerTiming getNotable() {
            return notable;
        }

        public void setNotable(BannerTiming notable) {
            this.notable = notable;
        }

        public BannerTiming getKeystone() {
            return keystone;
        }

        public void setKeystone(BannerTiming keystone) {
            this.keystone = keystone;
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

    // ═══════════════════════════════════════════════════════════════════
    // SOUND CONFIG
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Sound effect IDs per node tier.
     */
    public static class SoundConfig {
        private boolean enabled = true;
        private String basic = "SFX_Discovery_Z1_Short";
        private String notable = "SFX_Discovery_Z1_Medium";
        private String keystone = "SFX_Discovery_Z3_Medium";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getBasic() {
            return basic;
        }

        public void setBasic(String basic) {
            this.basic = basic;
        }

        public String getNotable() {
            return notable;
        }

        public void setNotable(String notable) {
            this.notable = notable;
        }

        public String getKeystone() {
            return keystone;
        }

        public void setKeystone(String keystone) {
            this.keystone = keystone;
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // CHAT CONFIG
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Chat breakdown settings.
     */
    public static class ChatConfig {
        private boolean enabled = true;
        private boolean showBorders = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
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

    // ═══════════════════════════════════════════════════════════════════
    // TOAST CONFIG
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Toast notification settings.
     */
    public static class ToastConfig {
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
