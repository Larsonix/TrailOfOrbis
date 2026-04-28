package io.github.larsonix.trailoforbis.util;

import javax.annotation.Nonnull;

/**
 * Color utility for perceptually uniform color operations using the OKLCH color space.
 *
 * <p>OKLCH (Oklab in polar coordinates) guarantees that equal numerical distances
 * produce equal perceived color differences — unlike HSL/HSV which lie about this.
 * This is critical for the skill tree galaxy where 15 regions, 3 brightness levels,
 * and dynamic bridge beam colors must all be visually distinguishable.
 *
 * <p>Color pipeline: OKLCH → Oklab (L,a,b) → Linear RGB → sRGB → Packed 0xRRGGBB
 *
 * <p>Based on Björn Ottosson's Oklab paper (2020).
 *
 * @see <a href="https://bottosson.github.io/posts/oklab/">Oklab color space</a>
 */
public final class OklchColorUtil {

    private OklchColorUtil() {
    }

    // ═══════════════════════════════════════════════════════════════════
    // PUBLIC API
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Converts an OKLCH color to a packed 0xRRGGBB integer.
     *
     * @param L Lightness (0.0 = black, 1.0 = white)
     * @param C Chroma (0.0 = gray, ~0.37 = max saturation in sRGB)
     * @param H Hue in degrees (0-360, where 0/360 = red-pink, 90 = yellow, 180 = cyan, 270 = blue)
     * @return Packed 0xRRGGBB color clamped to sRGB gamut
     */
    public static int oklchToPackedRGB(double L, double C, double H) {
        double hRad = Math.toRadians(H);
        double a = C * Math.cos(hRad);
        double b = C * Math.sin(hRad);
        return oklabToPackedRGB(L, a, b);
    }

    /**
     * Converts a packed 0xRRGGBB color to OKLCH components.
     *
     * @param packedRGB Packed 0xRRGGBB color
     * @return double[3] = {L, C, H} where H is in degrees [0, 360)
     */
    @Nonnull
    public static double[] packedRGBToOklch(int packedRGB) {
        int r = (packedRGB >> 16) & 0xFF;
        int g = (packedRGB >> 8) & 0xFF;
        int bCh = packedRGB & 0xFF;

        double lr = srgbToLinear(r / 255.0);
        double lg = srgbToLinear(g / 255.0);
        double lb = srgbToLinear(bCh / 255.0);

        double[] lab = linearRGBToOklab(lr, lg, lb);
        double L = lab[0];
        double a = lab[1];
        double bVal = lab[2];

        double C = Math.sqrt(a * a + bVal * bVal);
        double H = Math.toDegrees(Math.atan2(bVal, a));
        if (H < 0) H += 360.0;

        return new double[]{L, C, H};
    }

    /**
     * Interpolates two colors in OKLCH space using shortest-arc hue blending.
     * Produces perceptually correct midpoint colors — no muddy grays.
     *
     * @param packedA First color (0xRRGGBB)
     * @param packedB Second color (0xRRGGBB)
     * @param t Interpolation factor (0.0 = colorA, 1.0 = colorB)
     * @return Blended color as packed 0xRRGGBB
     */
    public static int blendOklch(int packedA, int packedB, double t) {
        double[] a = packedRGBToOklch(packedA);
        double[] b = packedRGBToOklch(packedB);
        return blendOklch(a[0], a[1], a[2], b[0], b[1], b[2], t);
    }

    /**
     * Interpolates two OKLCH colors directly.
     *
     * @param L1 Lightness of first color
     * @param C1 Chroma of first color
     * @param H1 Hue of first color (degrees)
     * @param L2 Lightness of second color
     * @param C2 Chroma of second color
     * @param H2 Hue of second color (degrees)
     * @param t  Interpolation factor (0.0 = first, 1.0 = second)
     * @return Blended color as packed 0xRRGGBB
     */
    public static int blendOklch(double L1, double C1, double H1,
                                  double L2, double C2, double H2, double t) {
        double L = L1 + (L2 - L1) * t;
        double C = C1 + (C2 - C1) * t;
        double H = interpolateHue(H1, H2, t);
        return oklchToPackedRGB(L, C, H);
    }

    /**
     * Computes the centroid of 3 colors in OKLCH space.
     * Used for octant arm colors (blend of 3 parent elementals).
     *
     * @param packed1 First parent color (0xRRGGBB)
     * @param packed2 Second parent color (0xRRGGBB)
     * @param packed3 Third parent color (0xRRGGBB)
     * @return Centroid color as packed 0xRRGGBB
     */
    public static int centroidOklch(int packed1, int packed2, int packed3) {
        double[] c1 = packedRGBToOklch(packed1);
        double[] c2 = packedRGBToOklch(packed2);
        double[] c3 = packedRGBToOklch(packed3);

        double L = (c1[0] + c2[0] + c3[0]) / 3.0;
        double C = (c1[1] + c2[1] + c3[1]) / 3.0;

        // Circular mean of 3 hues
        double sinSum = Math.sin(Math.toRadians(c1[2]))
                      + Math.sin(Math.toRadians(c2[2]))
                      + Math.sin(Math.toRadians(c3[2]));
        double cosSum = Math.cos(Math.toRadians(c1[2]))
                      + Math.cos(Math.toRadians(c2[2]))
                      + Math.cos(Math.toRadians(c3[2]));
        double H = Math.toDegrees(Math.atan2(sinSum, cosSum));
        if (H < 0) H += 360.0;

        return oklchToPackedRGB(L, C, H);
    }

    /**
     * Adjusts an OKLCH color's lightness while preserving hue and chroma.
     * Chroma is automatically reduced if it exceeds the sRGB gamut at the target lightness.
     *
     * @param packedRGB Original color (0xRRGGBB)
     * @param targetL   Target lightness (0.0-1.0)
     * @return Adjusted color as packed 0xRRGGBB
     */
    public static int adjustLightness(int packedRGB, double targetL) {
        double[] lch = packedRGBToOklch(packedRGB);
        // Reduce chroma if needed at low lightness to stay in gamut
        double maxC = estimateMaxChroma(targetL, lch[2]);
        double C = Math.min(lch[1], maxC);
        return oklchToPackedRGB(targetL, C, lch[2]);
    }

    /**
     * Converts a packed 0xRRGGBB to a hex string like "#FF7755".
     */
    @Nonnull
    public static String toHexString(int packedRGB) {
        return String.format("#%06X", packedRGB & 0xFFFFFF);
    }

    /**
     * Parses a hex color string like "#FF7755" to packed 0xRRGGBB.
     */
    public static int fromHexString(@Nonnull String hex) {
        String clean = hex.startsWith("#") ? hex.substring(1) : hex;
        return Integer.parseInt(clean, 16);
    }

    // ═══════════════════════════════════════════════════════════════════
    // OKLCH ↔ OKLAB ↔ LINEAR RGB ↔ sRGB CONVERSION PIPELINE
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Converts Oklab (L, a, b) to packed 0xRRGGBB.
     */
    private static int oklabToPackedRGB(double L, double a, double b) {
        // Oklab → LMS (cube root space)
        double l_ = L + 0.3963377774 * a + 0.2158037573 * b;
        double m_ = L - 0.1055613458 * a - 0.0638541728 * b;
        double s_ = L - 0.0894841775 * a - 1.2914855480 * b;

        // Undo cube root
        double l = l_ * l_ * l_;
        double m = m_ * m_ * m_;
        double s = s_ * s_ * s_;

        // LMS → linear sRGB
        double lr = +4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s;
        double lg = -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s;
        double lb = -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s;

        // Linear sRGB → sRGB (gamma correction) + clamp
        int r = clamp8(linearToSrgb(lr));
        int g = clamp8(linearToSrgb(lg));
        int bOut = clamp8(linearToSrgb(lb));

        return (r << 16) | (g << 8) | bOut;
    }

    /**
     * Converts linear RGB to Oklab (L, a, b).
     */
    @Nonnull
    private static double[] linearRGBToOklab(double lr, double lg, double lb) {
        // Linear sRGB → LMS
        double l = 0.4122214708 * lr + 0.5363325363 * lg + 0.0514459929 * lb;
        double m = 0.2119034982 * lr + 0.6806995451 * lg + 0.1073969566 * lb;
        double s = 0.0883024619 * lr + 0.2817188376 * lg + 0.6299787005 * lb;

        // Cube root
        double l_ = Math.cbrt(l);
        double m_ = Math.cbrt(m);
        double s_ = Math.cbrt(s);

        // LMS (cube root) → Oklab
        double L = 0.2104542553 * l_ + 0.7936177850 * m_ - 0.0040720468 * s_;
        double a = 1.9779984951 * l_ - 2.4285922050 * m_ + 0.4505937099 * s_;
        double b = 0.0259040371 * l_ + 0.7827717662 * m_ - 0.8086757660 * s_;

        return new double[]{L, a, b};
    }

    // ═══════════════════════════════════════════════════════════════════
    // GAMMA / TRANSFER FUNCTIONS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * sRGB gamma to linear (inverse transfer function).
     */
    private static double srgbToLinear(double c) {
        if (c <= 0.04045) {
            return c / 12.92;
        }
        return Math.pow((c + 0.055) / 1.055, 2.4);
    }

    /**
     * Linear to sRGB gamma (transfer function).
     */
    private static double linearToSrgb(double c) {
        if (c <= 0.0031308) {
            return c * 12.92;
        }
        return 1.055 * Math.pow(c, 1.0 / 2.4) - 0.055;
    }

    // ═══════════════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Clamps a [0.0, 1.0] value to [0, 255] integer.
     */
    private static int clamp8(double v) {
        return Math.max(0, Math.min(255, (int) Math.round(v * 255.0)));
    }

    /**
     * Interpolates two hue angles via shortest arc on the 360° circle.
     */
    private static double interpolateHue(double h1, double h2, double t) {
        double diff = h2 - h1;
        if (diff > 180.0) diff -= 360.0;
        if (diff < -180.0) diff += 360.0;
        double result = h1 + diff * t;
        if (result < 0) result += 360.0;
        if (result >= 360.0) result -= 360.0;
        return result;
    }

    /**
     * Estimates the maximum chroma achievable at a given lightness and hue
     * within the sRGB gamut. Uses binary search on the OKLCH→sRGB pipeline.
     *
     * <p>This prevents colors from falling outside sRGB (which would clamp
     * and shift the perceived hue) when adjusting lightness.
     */
    private static double estimateMaxChroma(double L, double H) {
        double low = 0.0;
        double high = 0.4;

        for (int i = 0; i < 20; i++) {
            double mid = (low + high) / 2.0;
            if (isInGamut(L, mid, H)) {
                low = mid;
            } else {
                high = mid;
            }
        }
        return low;
    }

    /**
     * Checks if an OKLCH color falls within the sRGB gamut.
     */
    private static boolean isInGamut(double L, double C, double H) {
        double hRad = Math.toRadians(H);
        double a = C * Math.cos(hRad);
        double b = C * Math.sin(hRad);

        // Oklab → LMS (cube root space)
        double l_ = L + 0.3963377774 * a + 0.2158037573 * b;
        double m_ = L - 0.1055613458 * a - 0.0638541728 * b;
        double s_ = L - 0.0894841775 * a - 1.2914855480 * b;

        double l = l_ * l_ * l_;
        double m = m_ * m_ * m_;
        double s = s_ * s_ * s_;

        // LMS → linear sRGB
        double lr = +4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s;
        double lg = -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s;
        double lb = -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s;

        double tolerance = -0.001; // Small tolerance for floating point
        return lr >= tolerance && lr <= 1.001
            && lg >= tolerance && lg <= 1.001
            && lb >= tolerance && lb <= 1.001;
    }
}
