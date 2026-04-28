package io.github.larsonix.trailoforbis.sanctum.layout;

import io.github.larsonix.trailoforbis.ui.skilltree.NodePositionConfig;

/**
 * Static math utilities for procedural skill tree layout generation.
 *
 * <p>Coordinate system:
 * <ul>
 *   <li>X = horizontal (east-west in world)</li>
 *   <li>Y = horizontal depth (north-south in world)</li>
 *   <li>Z = vertical height (becomes world Y after scaling)</li>
 * </ul>
 */
public final class LayoutMath {

    private LayoutMath() {
        // Utility class
    }

    /**
     * Converts polar coordinates to a NodePositionConfig.
     *
     * @param distance Distance from origin
     * @param angleDegrees Angle in degrees (0 = +X axis, counter-clockwise)
     * @param z Vertical height offset
     * @return NodePositionConfig with computed x, y, z
     */
    public static NodePositionConfig polarToCartesian(String nodeId, double distance, double angleDegrees, int z) {
        double angleRadians = Math.toRadians(angleDegrees);
        int x = (int) Math.round(distance * Math.cos(angleRadians));
        int y = (int) Math.round(distance * Math.sin(angleRadians));
        return new NodePositionConfig(nodeId, x, y, z);
    }

    /**
     * Projects a point along an arm direction with perpendicular offset.
     *
     * <p>This is the core function for placing cluster nodes. Given:
     * <ul>
     *   <li>armAngle: The direction the arm extends from center</li>
     *   <li>alongDist: Distance along the arm direction</li>
     *   <li>perpDist: Distance perpendicular to arm (+ = left, - = right)</li>
     * </ul>
     *
     * @param nodeId Node identifier
     * @param baseX Cluster center X coordinate
     * @param baseY Cluster center Y coordinate
     * @param armAngleDegrees Arm direction angle in degrees
     * @param alongDist Distance along arm direction
     * @param perpDist Distance perpendicular to arm (+ = left of arm direction)
     * @param z Vertical height offset
     * @return NodePositionConfig with computed position
     */
    public static NodePositionConfig projectAlongArm(
            String nodeId,
            double baseX,
            double baseY,
            double armAngleDegrees,
            double alongDist,
            double perpDist,
            int z) {

        double angleRadians = Math.toRadians(armAngleDegrees);

        // Unit vector along arm direction
        double alongX = Math.cos(angleRadians);
        double alongY = Math.sin(angleRadians);

        // Unit vector perpendicular to arm (90 degrees counter-clockwise)
        double perpX = -alongY;  // cos(angle + 90) = -sin(angle)
        double perpY = alongX;   // sin(angle + 90) = cos(angle)

        // Compute final position
        double x = baseX + alongDist * alongX + perpDist * perpX;
        double y = baseY + alongDist * alongY + perpDist * perpY;

        return new NodePositionConfig(nodeId, (int) Math.round(x), (int) Math.round(y), z);
    }

    /**
     * Rotates a 2D point around the origin.
     *
     * @param x Original X coordinate
     * @param y Original Y coordinate
     * @param angleDegrees Rotation angle in degrees (counter-clockwise)
     * @return Array of [rotatedX, rotatedY]
     */
    public static double[] rotatePoint(double x, double y, double angleDegrees) {
        double angleRadians = Math.toRadians(angleDegrees);
        double cos = Math.cos(angleRadians);
        double sin = Math.sin(angleRadians);

        return new double[]{
            x * cos - y * sin,
            x * sin + y * cos
        };
    }

    /**
     * Interpolates between two angles, handling wrap-around at 360 degrees.
     *
     * @param angle1 First angle in degrees
     * @param angle2 Second angle in degrees
     * @param t Interpolation factor (0.0 = angle1, 1.0 = angle2)
     * @return Interpolated angle in degrees [0, 360)
     */
    public static double interpolateAngle(double angle1, double angle2, double t) {
        // Normalize angles to [0, 360)
        angle1 = normalizeAngle(angle1);
        angle2 = normalizeAngle(angle2);

        // Find the shortest path between angles
        double diff = angle2 - angle1;

        // Adjust for wrap-around
        if (diff > 180) {
            diff -= 360;
        } else if (diff < -180) {
            diff += 360;
        }

        return normalizeAngle(angle1 + t * diff);
    }

    /**
     * Normalizes an angle to the range [0, 360).
     */
    public static double normalizeAngle(double angleDegrees) {
        double normalized = angleDegrees % 360;
        if (normalized < 0) {
            normalized += 360;
        }
        return normalized;
    }

    /**
     * Calculates the distance between two points.
     */
    public static double distance(double x1, double y1, double x2, double y2) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        return Math.sqrt(dx * dx + dy * dy);
    }

    // ═══════════════════════════════════════════════════════════════════
    // 3D DIRECTION VECTOR METHODS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Represents a 3D direction vector for arm positioning.
     * Each component is -1, 0, or 1 indicating the cardinal direction.
     */
    public record Direction3D(int x, int y, int z) {
        /** +X (East) */
        public static final Direction3D PLUS_X = new Direction3D(1, 0, 0);
        /** -X (West) */
        public static final Direction3D MINUS_X = new Direction3D(-1, 0, 0);
        /** +Y (Up) */
        public static final Direction3D PLUS_Y = new Direction3D(0, 1, 0);
        /** -Y (Down) */
        public static final Direction3D MINUS_Y = new Direction3D(0, -1, 0);
        /** +Z (South) */
        public static final Direction3D PLUS_Z = new Direction3D(0, 0, 1);
        /** -Z (North) */
        public static final Direction3D MINUS_Z = new Direction3D(0, 0, -1);

        // Diagonal directions (octant arms — cube corners)
        /** (+X, +Y, +Z) Havoc */
        public static final Direction3D PLUS_X_PLUS_Y_PLUS_Z = new Direction3D(1, 1, 1);
        /** (+X, +Y, -Z) Juggernaut */
        public static final Direction3D PLUS_X_PLUS_Y_MINUS_Z = new Direction3D(1, 1, -1);
        /** (+X, -Y, +Z) Striker */
        public static final Direction3D PLUS_X_MINUS_Y_PLUS_Z = new Direction3D(1, -1, 1);
        /** (+X, -Y, -Z) Warden */
        public static final Direction3D PLUS_X_MINUS_Y_MINUS_Z = new Direction3D(1, -1, -1);
        /** (-X, +Y, +Z) Warlock */
        public static final Direction3D MINUS_X_PLUS_Y_PLUS_Z = new Direction3D(-1, 1, 1);
        /** (-X, +Y, -Z) Lich */
        public static final Direction3D MINUS_X_PLUS_Y_MINUS_Z = new Direction3D(-1, 1, -1);
        /** (-X, -Y, +Z) Tempest */
        public static final Direction3D MINUS_X_MINUS_Y_PLUS_Z = new Direction3D(-1, -1, 1);
        /** (-X, -Y, -Z) Sentinel */
        public static final Direction3D MINUS_X_MINUS_Y_MINUS_Z = new Direction3D(-1, -1, -1);

        /**
         * Gets the magnitude (length) of this direction vector.
         * Cardinal directions = 1.0, diagonal directions = sqrt(3) ≈ 1.732.
         */
        public double magnitude() {
            return Math.sqrt(x * x + y * y + z * z);
        }

        /**
         * Returns true if this is a diagonal direction (all 3 components nonzero).
         */
        public boolean isDiagonal() {
            return x != 0 && y != 0 && z != 0;
        }

        /**
         * Gets two perpendicular directions for branching nodes.
         * Returns directions that are orthogonal to this direction.
         *
         * <p>For cardinal directions, perpendiculars are the other two axes.
         * For diagonal directions, uses cross products to find two orthogonal vectors:
         * <ul>
         *   <li>perp0 = (-z, 0, x) — lies in the XZ plane (purely horizontal, no Y component)</li>
         *   <li>perp1 = cross(direction, perp0) — completes the orthogonal frame</li>
         * </ul>
         *
         * <p>The horizontal perp0 ensures the two "ways" of a diagonal arm spread
         * left/right without altitude differences. perp1 carries the vertical component.
         */
        public Direction3D[] getPerpendicularDirections() {
            if (x != 0 && y != 0 && z != 0) {
                // Diagonal direction: compute two orthogonal vectors via cross products.
                // perp0 = (-z, 0, x): dot with (x,y,z) = -xz + 0 + xz = 0 ✓  |perp0| = √2
                // perp1 = cross(dir, perp0) = (y·x - z·0, z·(-z) - x·x, x·0 - y·(-z))
                //       = (xy, -(x²+z²), yz)
                // dot(perp1, dir) = x²y - y(x²+z²) + yz² = x²y - x²y - yz² + yz² = 0 ✓
                // dot(perp0, perp1) = (-z)(xy) + 0·(-(x²+z²)) + x(yz) = -xyz + xyz = 0 ✓
                // |perp1| = √(x²y² + (x²+z²)² + y²z²) = √6 (when |x|=|y|=|z|=1)
                return new Direction3D[]{
                    new Direction3D(-z, 0, x),
                    new Direction3D(x * y, -(x * x + z * z), y * z)
                };
            }
            if (x != 0) {
                // Arm along X axis, perpendicular are Y and Z
                return new Direction3D[]{PLUS_Y, PLUS_Z};
            } else if (y != 0) {
                // Arm along Y axis, perpendicular are X and Z
                return new Direction3D[]{PLUS_X, PLUS_Z};
            } else {
                // Arm along Z axis, perpendicular are X and Y
                return new Direction3D[]{PLUS_X, PLUS_Y};
            }
        }

        /**
         * Scales this direction by a distance.
         */
        public int[] scale(double distance) {
            return new int[]{
                (int) Math.round(x * distance),
                (int) Math.round(y * distance),
                (int) Math.round(z * distance)
            };
        }
    }

    /**
     * Creates a NodePositionConfig by projecting along a 3D direction.
     * Distances are normalized by direction magnitude for diagonal directions.
     *
     * @param nodeId Node identifier
     * @param direction The primary direction of the arm
     * @param distance Distance along the direction (in layout units)
     * @return NodePositionConfig at the computed position
     */
    public static NodePositionConfig projectAlongDirection(
            String nodeId,
            Direction3D direction,
            double distance) {
        double mag = direction.magnitude();
        double normDist = mag > 1.0 ? distance / mag : distance;
        int[] pos = direction.scale(normDist);
        return new NodePositionConfig(nodeId, pos[0], pos[1], pos[2]);
    }

    /**
     * Creates a NodePositionConfig by projecting along a 3D direction with perpendicular offsets.
     *
     * <p>Distances are normalized by direction magnitude so that diagonal directions
     * (magnitude √3) produce the same physical arm length as cardinal directions (magnitude 1)
     * at the same layout-unit distance.
     *
     * @param nodeId Node identifier
     * @param direction The primary direction of the arm
     * @param alongDist Distance along the primary direction (in layout units)
     * @param perp1Dist Distance along first perpendicular direction
     * @param perp2Dist Distance along second perpendicular direction
     * @return NodePositionConfig at the computed position
     */
    public static NodePositionConfig projectAlongDirection3D(
            String nodeId,
            Direction3D direction,
            double alongDist,
            double perp1Dist,
            double perp2Dist) {

        Direction3D[] perps = direction.getPerpendicularDirections();

        // Normalize distances by direction magnitude so diagonal arms
        // have the same physical length as cardinal arms at the same layout-unit distance
        double dirMag = direction.magnitude();
        double normAlong = dirMag > 1.0 ? alongDist / dirMag : alongDist;

        double perp0Mag = perps[0].magnitude();
        double perp1Mag = perps[1].magnitude();
        double normPerp1 = perp0Mag > 1.0 ? perp1Dist / perp0Mag : perp1Dist;
        double normPerp2 = perp1Mag > 1.0 ? perp2Dist / perp1Mag : perp2Dist;

        int x = (int) Math.round(direction.x() * normAlong + perps[0].x() * normPerp1 + perps[1].x() * normPerp2);
        int y = (int) Math.round(direction.y() * normAlong + perps[0].y() * normPerp1 + perps[1].y() * normPerp2);
        int z = (int) Math.round(direction.z() * normAlong + perps[0].z() * normPerp1 + perps[1].z() * normPerp2);

        return new NodePositionConfig(nodeId, x, y, z);
    }

    /**
     * Creates a NodePositionConfig at a base position plus offsets along direction and perpendiculars.
     * Distances are normalized by direction magnitude for diagonal directions.
     *
     * @param nodeId Node identifier
     * @param baseX Base X coordinate
     * @param baseY Base Y coordinate
     * @param baseZ Base Z coordinate
     * @param direction The primary direction of the arm
     * @param alongDist Additional distance along the primary direction
     * @param perp1Dist Distance along first perpendicular direction
     * @param perp2Dist Distance along second perpendicular direction
     * @return NodePositionConfig at the computed position
     */
    public static NodePositionConfig projectFromBase3D(
            String nodeId,
            int baseX, int baseY, int baseZ,
            Direction3D direction,
            double alongDist,
            double perp1Dist,
            double perp2Dist) {

        Direction3D[] perps = direction.getPerpendicularDirections();

        double dirMag = direction.magnitude();
        double normAlong = dirMag > 1.0 ? alongDist / dirMag : alongDist;

        double perp0Mag = perps[0].magnitude();
        double perp1Mag = perps[1].magnitude();
        double normPerp1 = perp0Mag > 1.0 ? perp1Dist / perp0Mag : perp1Dist;
        double normPerp2 = perp1Mag > 1.0 ? perp2Dist / perp1Mag : perp2Dist;

        int x = baseX + (int) Math.round(direction.x() * normAlong + perps[0].x() * normPerp1 + perps[1].x() * normPerp2);
        int y = baseY + (int) Math.round(direction.y() * normAlong + perps[0].y() * normPerp1 + perps[1].y() * normPerp2);
        int z = baseZ + (int) Math.round(direction.z() * normAlong + perps[0].z() * normPerp1 + perps[1].z() * normPerp2);

        return new NodePositionConfig(nodeId, x, y, z);
    }
}
