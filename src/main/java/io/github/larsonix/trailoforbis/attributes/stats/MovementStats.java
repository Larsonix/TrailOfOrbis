package io.github.larsonix.trailoforbis.attributes.stats;

import javax.annotation.Nonnull;
import java.util.Objects;

/**
 * Container for movement-related stats (speed bonuses, jump force, etc.).
 *
 * <p>This class holds all stats that affect player movement.
 * Used as part of the composed {@code ComputedStats} structure.
 *
 * <p>Thread-safe: This class is mutable but designed for single-threaded access
 * during entity processing. For concurrent access, external synchronization is needed.
 */
public final class MovementStats {

    // ==================== Base Movement ====================
    private float movementSpeedPercent;

    // ==================== Movement Abilities ====================
    private float jumpForceBonus;
    private float jumpForcePercent;
    private float sprintSpeedBonus;
    private float climbSpeedBonus;

    // ==================== Movement Mode Bonuses ====================
    private float walkSpeedPercent;
    private float runSpeedPercent;
    private float crouchSpeedPercent;

    /**
     * Creates a new MovementStats with all values initialized to 0.
     */
    public MovementStats() {
        // All fields default to 0
    }

    /**
     * Private constructor for builder.
     */
    private MovementStats(Builder builder) {
        this.movementSpeedPercent = builder.movementSpeedPercent;
        this.jumpForceBonus = builder.jumpForceBonus;
        this.jumpForcePercent = builder.jumpForcePercent;
        this.sprintSpeedBonus = builder.sprintSpeedBonus;
        this.climbSpeedBonus = builder.climbSpeedBonus;
        this.walkSpeedPercent = builder.walkSpeedPercent;
        this.runSpeedPercent = builder.runSpeedPercent;
        this.crouchSpeedPercent = builder.crouchSpeedPercent;
    }

    // ==================== Getters ====================

    public float getMovementSpeedPercent() {
        return movementSpeedPercent;
    }

    public float getJumpForceBonus() {
        return jumpForceBonus;
    }

    public float getJumpForcePercent() {
        return jumpForcePercent;
    }

    public float getSprintSpeedBonus() {
        return sprintSpeedBonus;
    }

    public float getClimbSpeedBonus() {
        return climbSpeedBonus;
    }

    public float getWalkSpeedPercent() {
        return walkSpeedPercent;
    }

    public float getRunSpeedPercent() {
        return runSpeedPercent;
    }

    public float getCrouchSpeedPercent() {
        return crouchSpeedPercent;
    }

    // ==================== Setters ====================

    public void setMovementSpeedPercent(float movementSpeedPercent) {
        this.movementSpeedPercent = movementSpeedPercent;
    }

    public void setJumpForceBonus(float jumpForceBonus) {
        this.jumpForceBonus = jumpForceBonus;
    }

    public void setJumpForcePercent(float jumpForcePercent) {
        this.jumpForcePercent = jumpForcePercent;
    }

    public void setSprintSpeedBonus(float sprintSpeedBonus) {
        this.sprintSpeedBonus = sprintSpeedBonus;
    }

    public void setClimbSpeedBonus(float climbSpeedBonus) {
        this.climbSpeedBonus = climbSpeedBonus;
    }

    public void setWalkSpeedPercent(float walkSpeedPercent) {
        this.walkSpeedPercent = walkSpeedPercent;
    }

    public void setRunSpeedPercent(float runSpeedPercent) {
        this.runSpeedPercent = runSpeedPercent;
    }

    public void setCrouchSpeedPercent(float crouchSpeedPercent) {
        this.crouchSpeedPercent = crouchSpeedPercent;
    }

    // ==================== Utility Methods ====================

    /** Creates a copy of this MovementStats. */
    @Nonnull
    public MovementStats copy() {
        return toBuilder().build();
    }

    /**
     * Resets all values to 0.
     */
    public void reset() {
        movementSpeedPercent = 0;
        jumpForceBonus = 0;
        jumpForcePercent = 0;
        sprintSpeedBonus = 0;
        climbSpeedBonus = 0;
        walkSpeedPercent = 0;
        runSpeedPercent = 0;
        crouchSpeedPercent = 0;
    }

    // ==================== Builder ====================

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return new Builder()
            .movementSpeedPercent(movementSpeedPercent)
            .jumpForceBonus(jumpForceBonus)
            .jumpForcePercent(jumpForcePercent)
            .sprintSpeedBonus(sprintSpeedBonus)
            .climbSpeedBonus(climbSpeedBonus)
            .walkSpeedPercent(walkSpeedPercent)
            .runSpeedPercent(runSpeedPercent)
            .crouchSpeedPercent(crouchSpeedPercent);
    }

    public static final class Builder {
        private float movementSpeedPercent;
        private float jumpForceBonus;
        private float jumpForcePercent;
        private float sprintSpeedBonus;
        private float climbSpeedBonus;
        private float walkSpeedPercent;
        private float runSpeedPercent;
        private float crouchSpeedPercent;

        private Builder() {}

        public Builder movementSpeedPercent(float value) {
            this.movementSpeedPercent = value;
            return this;
        }

        public Builder jumpForceBonus(float value) {
            this.jumpForceBonus = value;
            return this;
        }

        public Builder jumpForcePercent(float value) {
            this.jumpForcePercent = value;
            return this;
        }

        public Builder sprintSpeedBonus(float value) {
            this.sprintSpeedBonus = value;
            return this;
        }

        public Builder climbSpeedBonus(float value) {
            this.climbSpeedBonus = value;
            return this;
        }

        public Builder walkSpeedPercent(float value) {
            this.walkSpeedPercent = value;
            return this;
        }

        public Builder runSpeedPercent(float value) {
            this.runSpeedPercent = value;
            return this;
        }

        public Builder crouchSpeedPercent(float value) {
            this.crouchSpeedPercent = value;
            return this;
        }

        public MovementStats build() {
            return new MovementStats(this);
        }
    }

    // ==================== Object Methods ====================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MovementStats that = (MovementStats) o;
        return Float.compare(movementSpeedPercent, that.movementSpeedPercent) == 0
            && Float.compare(jumpForceBonus, that.jumpForceBonus) == 0
            && Float.compare(jumpForcePercent, that.jumpForcePercent) == 0
            && Float.compare(sprintSpeedBonus, that.sprintSpeedBonus) == 0
            && Float.compare(climbSpeedBonus, that.climbSpeedBonus) == 0
            && Float.compare(walkSpeedPercent, that.walkSpeedPercent) == 0
            && Float.compare(runSpeedPercent, that.runSpeedPercent) == 0
            && Float.compare(crouchSpeedPercent, that.crouchSpeedPercent) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(movementSpeedPercent, jumpForceBonus, jumpForcePercent, sprintSpeedBonus, walkSpeedPercent);
    }

    @Override
    public String toString() {
        return String.format(
            "MovementStats{move=+%.0f%%, jump=+%.1f, sprint=+%.0f%%, walk=+%.0f%%, run=+%.0f%%}",
            movementSpeedPercent * 100, jumpForceBonus,
            sprintSpeedBonus * 100, walkSpeedPercent * 100, runSpeedPercent * 100
        );
    }
}
