package io.github.larsonix.trailoforbis.sanctum.config;

/**
 * Configuration for the Skill Sanctum system.
 *
 * <p>Loaded from {@code skill-sanctum.yml} via ConfigManager.
 * All defaults match the actual hardcoded values in the sanctum codebase,
 * so loading the default config produces identical behavior to pre-config code.
 *
 * <h3>YAML binding:</h3>
 * <p>SnakeYAML uses setter methods to populate fields. Each field has both
 * camelCase and snake_case setters to support YAML's snake_case convention.
 */
public class SkillSanctumConfig {

    // ═══════════════════════════════════════════════════════════════════
    // TOP-LEVEL FIELDS
    // ═══════════════════════════════════════════════════════════════════

    private boolean enabled = true;
    private LayoutConfig layout = new LayoutConfig();
    private SpawnConfig spawn = new SpawnConfig();
    private NodesConfig nodes = new NodesConfig();
    private StatesConfig states = new StatesConfig();
    private ConnectionsConfig connections = new ConnectionsConfig();
    private BeamsConfig beams = new BeamsConfig();
    private PerformanceConfig performance = new PerformanceConfig();
    private ExitConfig exit = new ExitConfig();

    // ═══════════════════════════════════════════════════════════════════
    // GETTERS
    // ═══════════════════════════════════════════════════════════════════

    public boolean isEnabled() { return enabled; }
    public LayoutConfig getLayout() { return layout; }
    public SpawnConfig getSpawn() { return spawn; }
    public NodesConfig getNodes() { return nodes; }
    public StatesConfig getStates() { return states; }
    public ConnectionsConfig getConnections() { return connections; }
    public BeamsConfig getBeams() { return beams; }
    public PerformanceConfig getPerformance() { return performance; }
    public ExitConfig getExit() { return exit; }

    // ═══════════════════════════════════════════════════════════════════
    // SETTERS (camelCase + snake_case for YAML binding)
    // ═══════════════════════════════════════════════════════════════════

    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public void setLayout(LayoutConfig layout) { this.layout = layout != null ? layout : new LayoutConfig(); }
    public void setSpawn(SpawnConfig spawn) { this.spawn = spawn != null ? spawn : new SpawnConfig(); }
    public void setNodes(NodesConfig nodes) { this.nodes = nodes != null ? nodes : new NodesConfig(); }
    public void setStates(StatesConfig states) { this.states = states != null ? states : new StatesConfig(); }
    public void setConnections(ConnectionsConfig c) { this.connections = c != null ? c : new ConnectionsConfig(); }
    public void setBeams(BeamsConfig beams) { this.beams = beams != null ? beams : new BeamsConfig(); }
    public void setPerformance(PerformanceConfig p) { this.performance = p != null ? p : new PerformanceConfig(); }
    public void setExit(ExitConfig exit) { this.exit = exit != null ? exit : new ExitConfig(); }

    // ═══════════════════════════════════════════════════════════════════
    // VALIDATION
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Validates all config values and nested configs.
     *
     * @throws SkillSanctumConfig.ConfigValidationException if any value is out of range
     */
    public void validate() throws SkillSanctumConfig.ConfigValidationException {
        layout.validate();
        spawn.validate();
        nodes.validate();
        states.validate();
        connections.validate();
        beams.validate();
        performance.validate();
        exit.validate();
    }

    // ═══════════════════════════════════════════════════════════════════
    // NESTED CONFIGS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Layout mapping: how the skill tree maps from logical space to world space.
     */
    public static class LayoutConfig {
        private double scale = 0.07;
        private double planeHeight = 65.0;

        public double getScale() { return scale; }
        public double getPlaneHeight() { return planeHeight; }

        public void setScale(double scale) { this.scale = scale; }
        public void setPlaneHeight(double h) { this.planeHeight = h; }
        public void setPlane_height(double h) { this.planeHeight = h; }

        void validate() throws SkillSanctumConfig.ConfigValidationException {
            if (scale <= 0 || scale > 10.0)
                throw new SkillSanctumConfig.ConfigValidationException("skill-sanctum.layout.scale must be in (0, 10.0], got: " + scale);
            if (planeHeight < 0 || planeHeight > 500)
                throw new SkillSanctumConfig.ConfigValidationException("skill-sanctum.layout.plane_height must be in [0, 500], got: " + planeHeight);
        }
    }

    /**
     * Player spawn: where the player appears when entering the sanctum.
     */
    public static class SpawnConfig {
        private double offsetZ = -20.0;
        private double yOffset = 1.0;
        private double pitch = -15.0;

        public double getOffsetZ() { return offsetZ; }
        public double getYOffset() { return yOffset; }
        public double getPitch() { return pitch; }

        public void setOffsetZ(double v) { this.offsetZ = v; }
        public void setOffset_z(double v) { this.offsetZ = v; }
        public void setYOffset(double v) { this.yOffset = v; }
        public void setY_offset(double v) { this.yOffset = v; }
        public void setPitch(double v) { this.pitch = v; }

        void validate() throws SkillSanctumConfig.ConfigValidationException {
            if (pitch < -90 || pitch > 90)
                throw new SkillSanctumConfig.ConfigValidationException("skill-sanctum.spawn.pitch must be in [-90, 90], got: " + pitch);
        }
    }

    /**
     * Node orb visual settings: sizes and light radii by node type.
     */
    public static class NodesConfig {
        private double basicSize = 0.5;
        private double notableSize = 0.7;
        private double keystoneSize = 1.0;
        private double originSize = 1.2;
        private double basicLightRadius = 3.0;
        private double notableLightRadius = 5.0;
        private double keystoneLightRadius = 8.0;
        private double originLightRadius = 10.0;

        public double getBasicSize() { return basicSize; }
        public double getNotableSize() { return notableSize; }
        public double getKeystoneSize() { return keystoneSize; }
        public double getOriginSize() { return originSize; }
        public double getBasicLightRadius() { return basicLightRadius; }
        public double getNotableLightRadius() { return notableLightRadius; }
        public double getKeystoneLightRadius() { return keystoneLightRadius; }
        public double getOriginLightRadius() { return originLightRadius; }

        public void setBasicSize(double v) { this.basicSize = v; }
        public void setBasic_size(double v) { this.basicSize = v; }
        public void setNotableSize(double v) { this.notableSize = v; }
        public void setNotable_size(double v) { this.notableSize = v; }
        public void setKeystoneSize(double v) { this.keystoneSize = v; }
        public void setKeystone_size(double v) { this.keystoneSize = v; }
        public void setOriginSize(double v) { this.originSize = v; }
        public void setOrigin_size(double v) { this.originSize = v; }
        public void setBasicLightRadius(double v) { this.basicLightRadius = v; }
        public void setBasic_light_radius(double v) { this.basicLightRadius = v; }
        public void setNotableLightRadius(double v) { this.notableLightRadius = v; }
        public void setNotable_light_radius(double v) { this.notableLightRadius = v; }
        public void setKeystoneLightRadius(double v) { this.keystoneLightRadius = v; }
        public void setKeystone_light_radius(double v) { this.keystoneLightRadius = v; }
        public void setOriginLightRadius(double v) { this.originLightRadius = v; }
        public void setOrigin_light_radius(double v) { this.originLightRadius = v; }

        void validate() throws SkillSanctumConfig.ConfigValidationException {
            if (basicSize <= 0) throw new SkillSanctumConfig.ConfigValidationException("skill-sanctum.nodes.basic_size must be > 0");
            if (notableSize <= 0) throw new SkillSanctumConfig.ConfigValidationException("skill-sanctum.nodes.notable_size must be > 0");
            if (keystoneSize <= 0) throw new SkillSanctumConfig.ConfigValidationException("skill-sanctum.nodes.keystone_size must be > 0");
            if (originSize <= 0) throw new SkillSanctumConfig.ConfigValidationException("skill-sanctum.nodes.origin_size must be > 0");
            if (basicLightRadius < 0) throw new SkillSanctumConfig.ConfigValidationException("skill-sanctum.nodes.basic_light_radius must be >= 0");
            if (notableLightRadius < 0) throw new SkillSanctumConfig.ConfigValidationException("skill-sanctum.nodes.notable_light_radius must be >= 0");
            if (keystoneLightRadius < 0) throw new SkillSanctumConfig.ConfigValidationException("skill-sanctum.nodes.keystone_light_radius must be >= 0");
            if (originLightRadius < 0) throw new SkillSanctumConfig.ConfigValidationException("skill-sanctum.nodes.origin_light_radius must be >= 0");
        }
    }

    /**
     * Visual state settings: how allocated/available/locked nodes look.
     */
    public static class StatesConfig {
        private double lockedIntensity = 0.2;
        private double availableIntensity = 0.6;
        private double allocatedIntensity = 1.0;
        private double availablePulseRate = 2.0;

        public double getLockedIntensity() { return lockedIntensity; }
        public double getAvailableIntensity() { return availableIntensity; }
        public double getAllocatedIntensity() { return allocatedIntensity; }
        public double getAvailablePulseRate() { return availablePulseRate; }

        public void setLockedIntensity(double v) { this.lockedIntensity = v; }
        public void setLocked_intensity(double v) { this.lockedIntensity = v; }
        public void setAvailableIntensity(double v) { this.availableIntensity = v; }
        public void setAvailable_intensity(double v) { this.availableIntensity = v; }
        public void setAllocatedIntensity(double v) { this.allocatedIntensity = v; }
        public void setAllocated_intensity(double v) { this.allocatedIntensity = v; }
        public void setAvailablePulseRate(double v) { this.availablePulseRate = v; }
        public void setAvailable_pulse_rate(double v) { this.availablePulseRate = v; }

        void validate() throws SkillSanctumConfig.ConfigValidationException {
            if (lockedIntensity < 0 || lockedIntensity > 1.0)
                throw new SkillSanctumConfig.ConfigValidationException("skill-sanctum.states.locked_intensity must be in [0, 1.0]");
            if (availableIntensity < 0 || availableIntensity > 1.0)
                throw new SkillSanctumConfig.ConfigValidationException("skill-sanctum.states.available_intensity must be in [0, 1.0]");
            if (allocatedIntensity < 0 || allocatedIntensity > 1.0)
                throw new SkillSanctumConfig.ConfigValidationException("skill-sanctum.states.allocated_intensity must be in [0, 1.0]");
            if (availablePulseRate <= 0)
                throw new SkillSanctumConfig.ConfigValidationException("skill-sanctum.states.available_pulse_rate must be > 0");
        }
    }

    /**
     * Connection rendering: laser beam settings for node connections.
     */
    public static class ConnectionsConfig {
        private int beamDurationMs = 5000;
        private int initialFillRate = 30;
        private int refreshMarginMs = 500;

        public int getBeamDurationMs() { return beamDurationMs; }
        public int getInitialFillRate() { return initialFillRate; }
        public int getRefreshMarginMs() { return refreshMarginMs; }

        public void setBeamDurationMs(int v) { this.beamDurationMs = v; }
        public void setBeam_duration_ms(int v) { this.beamDurationMs = v; }
        public void setInitialFillRate(int v) { this.initialFillRate = v; }
        public void setInitial_fill_rate(int v) { this.initialFillRate = v; }
        public void setRefreshMarginMs(int v) { this.refreshMarginMs = v; }
        public void setRefresh_margin_ms(int v) { this.refreshMarginMs = v; }

        void validate() throws SkillSanctumConfig.ConfigValidationException {
            if (beamDurationMs < 500)
                throw new SkillSanctumConfig.ConfigValidationException("skill-sanctum.connections.beam_duration_ms must be >= 500");
            if (initialFillRate < 1)
                throw new SkillSanctumConfig.ConfigValidationException("skill-sanctum.connections.initial_fill_rate must be >= 1");
            if (refreshMarginMs < 0 || refreshMarginMs >= beamDurationMs)
                throw new SkillSanctumConfig.ConfigValidationException("skill-sanctum.connections.refresh_margin_ms must be in [0, beam_duration_ms)");
        }
    }

    /**
     * Beam particles: allocation burst visual effect.
     */
    public static class BeamsConfig {
        private String particleType = "Explosion_Medium";
        private int burstIntervalMs = 500;
        private int particlesPerBurst = 3;

        public String getParticleType() { return particleType; }
        public int getBurstIntervalMs() { return burstIntervalMs; }
        public int getParticlesPerBurst() { return particlesPerBurst; }

        public void setParticleType(String v) { this.particleType = v; }
        public void setParticle_type(String v) { this.particleType = v; }
        public void setBurstIntervalMs(int v) { this.burstIntervalMs = v; }
        public void setBurst_interval_ms(int v) { this.burstIntervalMs = v; }
        public void setParticlesPerBurst(int v) { this.particlesPerBurst = v; }
        public void setParticles_per_burst(int v) { this.particlesPerBurst = v; }

        void validate() throws SkillSanctumConfig.ConfigValidationException {
            if (particleType == null || particleType.isBlank())
                throw new SkillSanctumConfig.ConfigValidationException("skill-sanctum.beams.particle_type must not be empty");
            if (burstIntervalMs < 50)
                throw new SkillSanctumConfig.ConfigValidationException("skill-sanctum.beams.burst_interval_ms must be >= 50");
            if (particlesPerBurst < 1)
                throw new SkillSanctumConfig.ConfigValidationException("skill-sanctum.beams.particles_per_burst must be >= 1");
        }
    }

    /**
     * Performance tuning: spawn batching and lazy loading.
     */
    public static class PerformanceConfig {
        private boolean lazyLoading = false;
        private double lazyLoadRadius = 50.0;
        private int maxSpawnsPerTick = 15;
        private int spawnBatchDelayTicks = 1;

        public boolean isLazyLoading() { return lazyLoading; }
        public double getLazyLoadRadius() { return lazyLoadRadius; }
        public int getMaxSpawnsPerTick() { return maxSpawnsPerTick; }
        public int getSpawnBatchDelayTicks() { return spawnBatchDelayTicks; }

        public void setLazyLoading(boolean v) { this.lazyLoading = v; }
        public void setLazy_loading(boolean v) { this.lazyLoading = v; }
        public void setLazyLoadRadius(double v) { this.lazyLoadRadius = v; }
        public void setLazy_load_radius(double v) { this.lazyLoadRadius = v; }
        public void setMaxSpawnsPerTick(int v) { this.maxSpawnsPerTick = v; }
        public void setMax_spawns_per_tick(int v) { this.maxSpawnsPerTick = v; }
        public void setSpawnBatchDelayTicks(int v) { this.spawnBatchDelayTicks = v; }
        public void setSpawn_batch_delay_ticks(int v) { this.spawnBatchDelayTicks = v; }

        void validate() throws SkillSanctumConfig.ConfigValidationException {
            if (lazyLoadRadius <= 0)
                throw new SkillSanctumConfig.ConfigValidationException("skill-sanctum.performance.lazy_load_radius must be > 0");
            if (maxSpawnsPerTick < 1)
                throw new SkillSanctumConfig.ConfigValidationException("skill-sanctum.performance.max_spawns_per_tick must be >= 1");
            if (spawnBatchDelayTicks < 0)
                throw new SkillSanctumConfig.ConfigValidationException("skill-sanctum.performance.spawn_batch_delay_ticks must be >= 0");
        }
    }

    /**
     * Exit settings: auto-close and exit command.
     */
    public static class ExitConfig {
        private boolean autoCloseOnInactivity = true;
        private int inactivityTimeoutSeconds = 300;
        private boolean allowExitCommand = true;

        public boolean isAutoCloseOnInactivity() { return autoCloseOnInactivity; }
        public int getInactivityTimeoutSeconds() { return inactivityTimeoutSeconds; }
        public boolean isAllowExitCommand() { return allowExitCommand; }

        public void setAutoCloseOnInactivity(boolean v) { this.autoCloseOnInactivity = v; }
        public void setAuto_close_on_inactivity(boolean v) { this.autoCloseOnInactivity = v; }
        public void setInactivityTimeoutSeconds(int v) { this.inactivityTimeoutSeconds = v; }
        public void setInactivity_timeout_seconds(int v) { this.inactivityTimeoutSeconds = v; }
        public void setAllowExitCommand(boolean v) { this.allowExitCommand = v; }
        public void setAllow_exit_command(boolean v) { this.allowExitCommand = v; }

        void validate() throws SkillSanctumConfig.ConfigValidationException {
            if (inactivityTimeoutSeconds < 10)
                throw new SkillSanctumConfig.ConfigValidationException("skill-sanctum.exit.inactivity_timeout_seconds must be >= 10");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // EXCEPTION
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Thrown when a config value fails validation.
     */
    public static class ConfigValidationException extends Exception {
        public ConfigValidationException(String message) {
            super(message);
        }
    }
}
