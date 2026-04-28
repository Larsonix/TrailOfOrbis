package io.github.larsonix.trailoforbis.mobs.classification;

import com.hypixel.hytale.server.core.asset.type.attitude.Attitude;

import javax.annotation.Nullable;

/**
 * Context object containing all necessary information to classify a mob.
 *
 * <p>Decouples the classification logic from the Hytale Entity API,
 * allowing for easier unit testing and flexibility.
 */
public class MobClassificationContext {
    private final String roleName;
    private final int roleIndex;
    private final Attitude defaultAttitude;

    public MobClassificationContext(
            @Nullable String roleName,
            int roleIndex,
            @Nullable Attitude defaultAttitude) {
        this.roleName = roleName;
        this.roleIndex = roleIndex;
        this.defaultAttitude = defaultAttitude;
    }

    @Nullable
    public String getRoleName() {
        return roleName;
    }

    public int getRoleIndex() {
        return roleIndex;
    }

    @Nullable
    public Attitude getDefaultAttitude() {
        return defaultAttitude;
    }
}
