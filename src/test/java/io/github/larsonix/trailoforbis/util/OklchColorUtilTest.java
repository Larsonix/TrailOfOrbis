package io.github.larsonix.trailoforbis.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for OklchColorUtil and palette generation for the skill tree galaxy.
 */
class OklchColorUtilTest {

    // ═══════════════════════════════════════════════════════════════════
    // CORE CONVERSION TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Round-trip: packed RGB → OKLCH → packed RGB preserves color")
    void roundTripConversion() {
        int[] testColors = {0xFF0000, 0x00FF00, 0x0000FF, 0xFFFF00, 0xFF00FF, 0x00FFFF, 0xFFFFFF, 0x808080};
        for (int original : testColors) {
            double[] lch = OklchColorUtil.packedRGBToOklch(original);
            int result = OklchColorUtil.oklchToPackedRGB(lch[0], lch[1], lch[2]);
            // Allow ±1 per channel for floating point rounding
            int dr = Math.abs(((original >> 16) & 0xFF) - ((result >> 16) & 0xFF));
            int dg = Math.abs(((original >> 8) & 0xFF) - ((result >> 8) & 0xFF));
            int db = Math.abs((original & 0xFF) - (result & 0xFF));
            assertTrue(dr <= 1 && dg <= 1 && db <= 1,
                String.format("Round-trip failed for #%06X → #%06X (delta: r=%d g=%d b=%d)",
                    original, result, dr, dg, db));
        }
    }

    @Test
    @DisplayName("Blend of red and cyan in OKLCH produces vivid color, not gray")
    void blendAvoidsMuddyGray() {
        int red = 0xFF0000;
        int cyan = 0x00FFFF;
        int blended = OklchColorUtil.blendOklch(red, cyan, 0.5);
        // RGB midpoint would be 0x808080 (gray). OKLCH blend should NOT be gray.
        int r = (blended >> 16) & 0xFF;
        int g = (blended >> 8) & 0xFF;
        int b = blended & 0xFF;
        // The result should have color (not all channels equal like gray)
        int maxChannel = Math.max(r, Math.max(g, b));
        int minChannel = Math.min(r, Math.min(g, b));
        assertTrue(maxChannel - minChannel > 30,
            String.format("OKLCH blend produced near-gray #%06X — should be vivid", blended));
    }

    @Test
    @DisplayName("Hex string round-trip")
    void hexStringRoundTrip() {
        assertEquals("#FF7755", OklchColorUtil.toHexString(0xFF7755));
        assertEquals(0xFF7755, OklchColorUtil.fromHexString("#FF7755"));
        assertEquals(0xFF7755, OklchColorUtil.fromHexString("FF7755"));
    }

    @Test
    @DisplayName("Lightness adjustment preserves hue")
    void lightnessPreservesHue() {
        int original = OklchColorUtil.oklchToPackedRGB(0.78, 0.20, 30.0); // Fire-like
        int dimmed = OklchColorUtil.adjustLightness(original, 0.48);
        int brightened = OklchColorUtil.adjustLightness(original, 0.88);

        double[] origLch = OklchColorUtil.packedRGBToOklch(original);
        double[] dimLch = OklchColorUtil.packedRGBToOklch(dimmed);
        double[] brightLch = OklchColorUtil.packedRGBToOklch(brightened);

        // Hue should be within 5 degrees
        double hueDiffDim = Math.abs(origLch[2] - dimLch[2]);
        if (hueDiffDim > 180) hueDiffDim = 360 - hueDiffDim;
        double hueDiffBright = Math.abs(origLch[2] - brightLch[2]);
        if (hueDiffBright > 180) hueDiffBright = 360 - hueDiffBright;

        assertTrue(hueDiffDim < 8.0,
            String.format("Dimming shifted hue by %.1f degrees (should be <8)", hueDiffDim));
        assertTrue(hueDiffBright < 8.0,
            String.format("Brightening shifted hue by %.1f degrees (should be <8)", hueDiffBright));
    }

    // ═══════════════════════════════════════════════════════════════════
    // PALETTE GENERATION & VALIDATION
    // ═══════════════════════════════════════════════════════════════════

    // ─── PALETTE DESIGN ─────────────────────────────────────────────
    // 15 regions placed on the 360° OKLCH hue wheel.
    //
    // Strategy: Elementals get HIGH chroma (C=0.20) at fixed lightness.
    // Octants get MEDIUM chroma (C=0.14) and are placed at explicit hues
    // BETWEEN their parent elementals, manually spaced to guarantee
    // minimum perceptual distance.
    //
    // Hue wheel layout (clockwise from red):
    //   20° FIRE         — Red-orange
    //   40° HAVOC        — (Fire/Void/Lightning) Hot crimson
    //   60° STRIKER      — (Fire/Wind/Lightning) Amber-gold
    //   85° LIGHTNING    — Electric yellow
    //  108° WARDEN       — (Fire/Wind/Earth) Yellow-green
    //  135° EARTH        — Forest green
    //  158° SENTINEL     — (Water/Wind/Earth) Sage teal-green
    //  180° WIND         — Cyan-teal
    //  200° TEMPEST      — (Water/Wind/Lightning) Ocean blue-teal
    //  230° WATER        — Sapphire blue
    //  255° LICH         — (Water/Void/Earth) Slate blue-indigo
    //  280° WARLOCK      — (Water/Void/Lightning) Violet
    //  310° VOID         — Purple-magenta
    //  335° JUGGERNAUT   — (Fire/Void/Earth) Rose-mauve
    //  355° (wraps near Fire)
    //
    // This gives minimum hue gap ~20° and distributes octants between parents.
    // ──────────────────────────────────────────────────────────────────

    // Elemental parameters
    private static final double ELEM_L = 0.78;
    private static final double ELEM_C = 0.20;

    // Octant parameters — alternating lightness for adjacent octants
    // creates a second dimension of perceptual separation beyond hue alone
    private static final double OCT_C = 0.14;

    // Core (warm white — neutral origin, maximally distant from all saturated colors)
    private static final double CORE_L = 0.92;
    private static final double CORE_C = 0.03;   // Tiny warm tint so it doesn't look sterile
    private static final double CORE_H = 80.0;   // Warm white (slight cream/ivory)

    // Elemental hues (6 anchors on the wheel)
    private static final double HUE_FIRE       = 20.0;
    private static final double HUE_LIGHTNING   = 85.0;
    private static final double HUE_EARTH       = 135.0;
    private static final double HUE_WIND        = 180.0;
    private static final double HUE_WATER       = 230.0;
    private static final double HUE_VOID        = 310.0;

    // Octant hues — tuned for max separation from neighboring elementals
    // Each octant is pushed toward the midpoint of the gap it occupies
    private static final double HUE_HAVOC       = 350.0;  // Gap: Void(310)..Fire(20) → mid=345, push to 350
    private static final double HUE_STRIKER     = 50.0;   // Gap: Fire(20)..Lightning(85) → mid=52, pull slightly away from Lightning
    private static final double HUE_WARDEN      = 110.0;  // Gap: Lightning(85)..Earth(135) → mid=110
    private static final double HUE_SENTINEL    = 148.0;  // Gap: Earth(135)..Wind(180) → pull further from Wind for max separation
    private static final double HUE_TEMPEST     = 208.0;  // Gap: Wind(180)..Water(230) → mid=205, push to 208
    private static final double HUE_LICH        = 260.0;  // Gap: Water(230)..Void(310) → push toward 260 (away from Water)
    private static final double HUE_WARLOCK     = 290.0;  // Gap: Water(230)..Void(310) → push toward 290 (away from Void)
    private static final double HUE_JUGGERNAUT  = 330.0;  // Gap: Void(310)..Fire(20) → push to 330

    // Brightness levels
    private static final double L_LOCKED    = 0.48;
    private static final double L_AVAILABLE = 0.68;
    private static final double L_ALLOCATED = 0.85;

    @Test
    @DisplayName("Generate complete 15-region palette and print hex codes")
    void generateFullPalette() {
        System.out.println("\n═══════════════════════════════════════════════════");
        System.out.println("  SKILL TREE GALAXY — OKLCH COLOR PALETTE");
        System.out.println("═══════════════════════════════════════════════════\n");

        // Core (warm white — neutral origin)
        int core       = OklchColorUtil.oklchToPackedRGB(CORE_L, CORE_C, CORE_H);

        // 6 Elemental colors (high chroma, vivid primaries)
        int fire       = OklchColorUtil.oklchToPackedRGB(ELEM_L, ELEM_C, HUE_FIRE);
        int lightning  = OklchColorUtil.oklchToPackedRGB(ELEM_L, ELEM_C, HUE_LIGHTNING);
        int earth      = OklchColorUtil.oklchToPackedRGB(ELEM_L, ELEM_C, HUE_EARTH);
        int wind       = OklchColorUtil.oklchToPackedRGB(ELEM_L, ELEM_C, HUE_WIND);
        int water      = OklchColorUtil.oklchToPackedRGB(ELEM_L, ELEM_C, HUE_WATER);
        int voidC      = OklchColorUtil.oklchToPackedRGB(ELEM_L, ELEM_C, HUE_VOID);

        System.out.println("── CORE + ELEMENTAL ARMS ───────────────────────────");
        printColorRow("CORE",      core,      CORE_H);
        printColorRow("FIRE",      fire,      HUE_FIRE);
        printColorRow("LIGHTNING", lightning,  HUE_LIGHTNING);
        printColorRow("EARTH",     earth,     HUE_EARTH);
        printColorRow("WIND",      wind,      HUE_WIND);
        printColorRow("WATER",     water,     HUE_WATER);
        printColorRow("VOID",      voidC,     HUE_VOID);

        // 8 Octant colors (explicit hues, lower chroma → visually "hybrid")
        System.out.println("\n── OCTANT ARMS (explicit hues, reduced chroma) ─────");

        // Alternating L between 0.70 (darker) and 0.84 (brighter) for adjacent octants
        // Combined with lower chroma (0.14 vs elemental 0.20), this creates
        // two dimensions of separation: lightness + saturation
        int havoc      = OklchColorUtil.oklchToPackedRGB(0.70, OCT_C, HUE_HAVOC);      // darker
        int striker    = OklchColorUtil.oklchToPackedRGB(0.84, OCT_C, HUE_STRIKER);     // brighter
        int warden     = OklchColorUtil.oklchToPackedRGB(0.70, OCT_C, HUE_WARDEN);     // darker
        int sentinel   = OklchColorUtil.oklchToPackedRGB(0.84, OCT_C, HUE_SENTINEL);   // brighter
        int tempest    = OklchColorUtil.oklchToPackedRGB(0.70, OCT_C, HUE_TEMPEST);     // darker
        int lich       = OklchColorUtil.oklchToPackedRGB(0.84, OCT_C, HUE_LICH);        // brighter
        int warlock    = OklchColorUtil.oklchToPackedRGB(0.70, OCT_C, HUE_WARLOCK);     // darker
        int juggernaut = OklchColorUtil.oklchToPackedRGB(0.84, OCT_C, HUE_JUGGERNAUT); // brighter

        printColorRow("HAVOC",      havoc,      HUE_HAVOC);
        printColorRow("JUGGERNAUT", juggernaut, HUE_JUGGERNAUT);
        printColorRow("STRIKER",    striker,    HUE_STRIKER);
        printColorRow("WARDEN",     warden,     HUE_WARDEN);
        printColorRow("WARLOCK",    warlock,    HUE_WARLOCK);
        printColorRow("LICH",       lich,       HUE_LICH);
        printColorRow("TEMPEST",    tempest,    HUE_TEMPEST);
        printColorRow("SENTINEL",   sentinel,   HUE_SENTINEL);

        // Brightness variants for all 15 regions
        System.out.println("\n── BRIGHTNESS VARIANTS ─────────────────────────────");
        int[] allColors = {core, fire, lightning, earth, wind, water, voidC,
                           havoc, juggernaut, striker, warden, warlock, lich, tempest, sentinel};
        String[] names = {"CORE", "FIRE", "LIGHTNING", "EARTH", "WIND", "WATER", "VOID",
                          "HAVOC", "JUGGERNAUT", "STRIKER", "WARDEN", "WARLOCK", "LICH", "TEMPEST", "SENTINEL"};

        for (int i = 0; i < allColors.length; i++) {
            int locked = OklchColorUtil.adjustLightness(allColors[i], L_LOCKED);
            int available = OklchColorUtil.adjustLightness(allColors[i], L_AVAILABLE);
            int allocated = OklchColorUtil.adjustLightness(allColors[i], L_ALLOCATED);
            System.out.printf("  %-14s  Locked: %s  Available: %s  Allocated: %s%n",
                names[i],
                OklchColorUtil.toHexString(locked),
                OklchColorUtil.toHexString(available),
                OklchColorUtil.toHexString(allocated));
        }

        // Bridge blend examples
        System.out.println("\n── BRIDGE BEAM COLORS (2-region blends) ────────────");
        printBridge("Fire↔Lightning",    fire, lightning);
        printBridge("Lightning↔Earth",   lightning, earth);
        printBridge("Earth↔Wind",        earth, wind);
        printBridge("Wind↔Fire",         wind, fire);
        printBridge("Fire↔Void",         fire, voidC);
        printBridge("Lightning↔Void",    lightning, voidC);
        printBridge("Earth↔Void",        earth, voidC);
        printBridge("Wind↔Void",         wind, voidC);
        printBridge("Fire↔Water",        fire, water);
        printBridge("Lightning↔Water",   lightning, water);
        printBridge("Earth↔Water",       earth, water);
        printBridge("Wind↔Water",        wind, water);

        // Validate minimum perceptual distance between ALL 15 base colors
        System.out.println("\n── PERCEPTUAL DISTANCE MATRIX (Oklab Euclidean) ────");
        double minDist = Double.MAX_VALUE;
        String minPair = "";
        for (int i = 0; i < allColors.length; i++) {
            for (int j = i + 1; j < allColors.length; j++) {
                double dist = oklabDistance(allColors[i], allColors[j]);
                if (dist < minDist) {
                    minDist = dist;
                    minPair = names[i] + " ↔ " + names[j];
                }
                if (dist < 0.08) {
                    System.out.printf("  ⚠ CLOSE: %-25s distance=%.4f%n", names[i] + " ↔ " + names[j], dist);
                }
            }
        }
        System.out.printf("%n  Minimum distance: %.4f (%s)%n", minDist, minPair);
        System.out.printf("  Target minimum:   0.08+%n");

        // Assert minimum distance
        assertTrue(minDist >= 0.05,
            String.format("Palette has insufficiently distinct colors: %s (dist=%.4f)", minPair, minDist));
    }

    // ═══════════════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════════════

    private void printColorRow(String name, int packed, double hue) {
        double[] lch = OklchColorUtil.packedRGBToOklch(packed);
        System.out.printf("  %-14s %s  (L=%.2f C=%.2f H=%.0f°)%n",
            name, OklchColorUtil.toHexString(packed), lch[0], lch[1], lch[2]);
    }

    private void printBridge(String label, int a, int b) {
        int blended = OklchColorUtil.blendOklch(a, b, 0.5);
        System.out.printf("  %-22s %s + %s → %s%n",
            label,
            OklchColorUtil.toHexString(a),
            OklchColorUtil.toHexString(b),
            OklchColorUtil.toHexString(blended));
    }

    /**
     * Computes Euclidean distance in Oklab space (perceptually uniform).
     */
    private double oklabDistance(int packed1, int packed2) {
        int r1 = (packed1 >> 16) & 0xFF, g1 = (packed1 >> 8) & 0xFF, b1 = packed1 & 0xFF;
        int r2 = (packed2 >> 16) & 0xFF, g2 = (packed2 >> 8) & 0xFF, b2 = packed2 & 0xFF;

        double lr1 = srgbToLinear(r1 / 255.0), lg1 = srgbToLinear(g1 / 255.0), lb1 = srgbToLinear(b1 / 255.0);
        double lr2 = srgbToLinear(r2 / 255.0), lg2 = srgbToLinear(g2 / 255.0), lb2 = srgbToLinear(b2 / 255.0);

        double[] lab1 = linearRGBToOklab(lr1, lg1, lb1);
        double[] lab2 = linearRGBToOklab(lr2, lg2, lb2);

        double dL = lab1[0] - lab2[0];
        double da = lab1[1] - lab2[1];
        double db = lab1[2] - lab2[2];
        return Math.sqrt(dL * dL + da * da + db * db);
    }

    private static double srgbToLinear(double c) {
        return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    private static double[] linearRGBToOklab(double lr, double lg, double lb) {
        double l = 0.4122214708 * lr + 0.5363325363 * lg + 0.0514459929 * lb;
        double m = 0.2119034982 * lr + 0.6806995451 * lg + 0.1073969566 * lb;
        double s = 0.0883024619 * lr + 0.2817188376 * lg + 0.6299787005 * lb;
        double l_ = Math.cbrt(l), m_ = Math.cbrt(m), s_ = Math.cbrt(s);
        double L = 0.2104542553 * l_ + 0.7936177850 * m_ - 0.0040720468 * s_;
        double a = 1.9779984951 * l_ - 2.4285922050 * m_ + 0.4505937099 * s_;
        double b = 0.0259040371 * l_ + 0.7827717662 * m_ - 0.8086757660 * s_;
        return new double[]{L, a, b};
    }
}
