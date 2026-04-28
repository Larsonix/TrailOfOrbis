package io.github.larsonix.trailoforbis.api.services;

import io.github.larsonix.trailoforbis.config.RPGConfig;
import io.github.larsonix.trailoforbis.mobs.stats.MobStatPoolConfig;

import javax.annotation.Nullable;

/**
 * Service interface for configuration access.
 *
 * <p>Provides decoupled access to RPG configuration without requiring
 * direct dependency on the main plugin class.
 */
public interface ConfigService {

    /** @return null if not loaded */
    @Nullable
    RPGConfig getRPGConfig();

    /** @return null if not loaded */
    @Nullable
    MobStatPoolConfig getMobStatPoolConfig();

    boolean loadConfigs();

    boolean reloadConfigs();
}
