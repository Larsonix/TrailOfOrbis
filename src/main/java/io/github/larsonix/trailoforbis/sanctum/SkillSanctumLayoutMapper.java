/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.hypixel.hytale.math.vector.Vector3d
 *  javax.annotation.Nonnull
 */
package io.github.larsonix.trailoforbis.sanctum;

import com.hypixel.hytale.math.vector.Vector3d;
import io.github.larsonix.trailoforbis.sanctum.GalaxySpiralLayoutMapper;
import io.github.larsonix.trailoforbis.skilltree.config.SkillNode;
import javax.annotation.Nonnull;

@Deprecated
public class SkillSanctumLayoutMapper {
    @Deprecated
    public static final double SCALE = 1.0;
    @Deprecated
    public static final double VERTICAL_SCALE = 1.0;
    @Deprecated
    public static final double PLANE_HEIGHT = 65.0;
    @Deprecated
    public static final double SPAWN_OFFSET_Z = -50.0;
    @Deprecated
    public static final double SPAWN_Y_OFFSET = 60.0;

    @Deprecated
    @Nonnull
    public static Vector3d toWorldPosition(double layoutX, double layoutY) {
        return SkillSanctumLayoutMapper.toWorldPosition(layoutX, layoutY, 0.0);
    }

    @Deprecated
    @Nonnull
    public static Vector3d toWorldPosition(double layoutX, double layoutY, double layoutZ) {
        return new Vector3d(layoutX * 0.08, 65.0 + layoutZ * 0.1, layoutY * 0.08);
    }

    @Nonnull
    public static Vector3d toWorldPosition(@Nonnull SkillNode node) {
        return GalaxySpiralLayoutMapper.toWorldPosition(node);
    }

    @Nonnull
    public static Vector3d getPlayerSpawnPosition() {
        return GalaxySpiralLayoutMapper.getPlayerSpawnPosition();
    }

    @Nonnull
    public static Vector3d getPlayerSpawnRotation() {
        return GalaxySpiralLayoutMapper.getPlayerSpawnRotation();
    }

    public static double getWorldDistance(@Nonnull SkillNode node1, @Nonnull SkillNode node2) {
        return GalaxySpiralLayoutMapper.getWorldDistance(node1, node2);
    }

    public static double getWorldDistanceSquared(@Nonnull SkillNode node1, @Nonnull SkillNode node2) {
        Vector3d pos1 = GalaxySpiralLayoutMapper.toWorldPosition(node1);
        Vector3d pos2 = GalaxySpiralLayoutMapper.toWorldPosition(node2);
        return pos1.distanceSquaredTo(pos2);
    }

    @Deprecated
    public static double getBasicNodeSize() {
        return 2.5;
    }

    @Deprecated
    public static double getNotableNodeSize() {
        return 4.0;
    }

    @Deprecated
    public static double getKeystoneNodeSize() {
        return 6.0;
    }

    @Deprecated
    public static double getOriginNodeSize() {
        return 4.0;
    }

    public static double getNodeSize(@Nonnull SkillNode node) {
        return GalaxySpiralLayoutMapper.getNodeScale(node);
    }

    public static double getLightRadius(@Nonnull SkillNode node) {
        if (node.isStartNode() || "origin".equals(node.getId())) {
            return 15.0;
        }
        if (node.isKeystone()) {
            return 12.0;
        }
        if (node.isNotable()) {
            return 8.0;
        }
        return 5.0;
    }

    public static double getMaxTreeRadius() {
        return GalaxySpiralLayoutMapper.getMaxTreeRadius();
    }

    public static double getSanctumRadius() {
        return GalaxySpiralLayoutMapper.getSanctumRadius();
    }
}
