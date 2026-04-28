///usr/bin/env java --enable-preview --source 21 "$0" "$@"; exit $?
// ═══════════════════════════════════════════════════════════════════════
// Skill Tree Texture Re-Tinter
// ═══════════════════════════════════════════════════════════════════════
// Re-tints all 58 skill tree textures (essences, crystals, gems) to match
// the new OKLCH-derived color palette. Preserves luminance/shading structure
// while shifting hue and saturation to target colors.
//
// Usage: java scripts/retint-textures.java
// Run from project root

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

public class RetintTextures {

    // ═══════════════════════════════════════════════════════════════════
    // TARGET COLORS (from OKLCH palette — converted to HSB for tinting)
    // ═══════════════════════════════════════════════════════════════════

    // Elemental essence target colors (these tint the essence textures)
    // Format: region name → hex color from OKLCH palette
    static final Map<String, String> ESSENCE_TARGETS = new LinkedHashMap<>();
    static {
        // Elementals (essence texture filenames use old names for some)
        ESSENCE_TARGETS.put("Fire",       "#FF7881");  // Fire element
        ESSENCE_TARGETS.put("Ice",        "#00CBFF");  // Water element (uses Ice texture name)
        ESSENCE_TARGETS.put("Lightning",  "#F1A900");  // Lightning element
        ESSENCE_TARGETS.put("Life",       "#7BD23B");  // Earth element (uses Life texture name)
        ESSENCE_TARGETS.put("Water",      "#00DEBC");  // Wind element (uses Water texture name)
        ESSENCE_TARGETS.put("Void",       "#DE8DFF");  // Void element
        // Octants
        ESSENCE_TARGETS.put("Havoc",      "#DE77AB");
        ESSENCE_TARGETS.put("Juggernaut", "#FEA8F6");
        ESSENCE_TARGETS.put("Striker",    "#FFAE74");
        ESSENCE_TARGETS.put("Warden",     "#A4A528");
        ESSENCE_TARGETS.put("Warlock",    "#9E8EEF");
        ESSENCE_TARGETS.put("Lich",       "#94CBFF");
        ESSENCE_TARGETS.put("Tempest",    "#00B5CB");
        ESSENCE_TARGETS.put("Sentinel",   "#87E496");
    }

    // Octant crystal/gem target colors
    static final Map<String, String> OCTANT_TARGETS = new LinkedHashMap<>();
    static {
        OCTANT_TARGETS.put("Havoc",      "#DE77AB");
        OCTANT_TARGETS.put("Juggernaut", "#FEA8F6");
        OCTANT_TARGETS.put("Striker",    "#FFAE74");
        OCTANT_TARGETS.put("Warden",     "#A4A528");
        OCTANT_TARGETS.put("Warlock",    "#9E8EEF");
        OCTANT_TARGETS.put("Lich",       "#94CBFF");
        OCTANT_TARGETS.put("Tempest",    "#00B5CB");
        OCTANT_TARGETS.put("Sentinel",   "#87E496");
    }

    // Asset paths relative to project root
    static final String ESSENCE_DIR = "src/main/resources/hytale-assets/Common/Resources/Ingredients/Essence_Textures_Dim";
    static final String CRYSTAL_DIR = "src/main/resources/hytale-assets/Common/Resources/Crystals/Crystal_Big_Textures";
    static final String GEM_DIR     = "src/main/resources/hytale-assets/Common/Resources/Ores/Gem_Textures";

    public static void main(String[] args) throws Exception {
        System.out.println("═══════════════════════════════════════════════════");
        System.out.println("  Skill Tree Texture Re-Tinter (OKLCH Palette)");
        System.out.println("═══════════════════════════════════════════════════\n");

        int count = 0;

        // Re-tint essences (42 files: 14 regions × 3 brightness levels)
        System.out.println("── Essences ────────────────────────────────────────");
        for (var entry : ESSENCE_TARGETS.entrySet()) {
            String region = entry.getKey();
            float[] targetHSB = hexToHSB(entry.getValue());

            for (String level : new String[]{"Light0", "Light50", "Light100"}) {
                // Brightness scaling: Light0 = 15%, Light50 = 50%, Light100 = 100%
                float brightnessFactor = switch (level) {
                    case "Light0"   -> 0.15f;
                    case "Light50"  -> 0.50f;
                    case "Light100" -> 1.00f;
                    default -> 1.0f;
                };

                String filename = region + "_Essence_Texture_" + level + ".png";
                File file = new File(ESSENCE_DIR, filename);
                if (!file.exists()) {
                    System.out.println("  SKIP (not found): " + filename);
                    continue;
                }

                retintImage(file, targetHSB[0], targetHSB[1], brightnessFactor);
                System.out.printf("  ✓ %s → %s (brightness=%.0f%%)%n",
                    filename, entry.getValue(), brightnessFactor * 100);
                count++;
            }
        }

        // Re-tint crystals (8 files: octants only)
        System.out.println("\n── Crystals ────────────────────────────────────────");
        for (var entry : OCTANT_TARGETS.entrySet()) {
            String region = entry.getKey();
            float[] targetHSB = hexToHSB(entry.getValue());

            File file = new File(CRYSTAL_DIR, region + ".png");
            if (!file.exists()) {
                System.out.println("  SKIP (not found): " + region + ".png");
                continue;
            }

            retintImage(file, targetHSB[0], targetHSB[1], 1.0f);
            System.out.printf("  ✓ %s.png → %s%n", region, entry.getValue());
            count++;
        }

        // Re-tint gems (8 files: octants only)
        System.out.println("\n── Gems ────────────────────────────────────────────");
        for (var entry : OCTANT_TARGETS.entrySet()) {
            String region = entry.getKey();
            float[] targetHSB = hexToHSB(entry.getValue());

            File file = new File(GEM_DIR, region + ".png");
            if (!file.exists()) {
                System.out.println("  SKIP (not found): " + region + ".png");
                continue;
            }

            retintImage(file, targetHSB[0], targetHSB[1], 1.0f);
            System.out.printf("  ✓ %s.png → %s%n", region, entry.getValue());
            count++;
        }

        System.out.printf("%n═══════════════════════════════════════════════════%n");
        System.out.printf("  Re-tinted %d textures successfully.%n", count);
        System.out.println("═══════════════════════════════════════════════════");
    }

    /**
     * Re-tints an image by replacing the hue and saturation of every pixel
     * while preserving the original brightness structure (shading, highlights).
     *
     * For essence brightness variants (Light0/50/100), the brightness is also
     * scaled to create the dim/medium/bright progression.
     *
     * @param file             The PNG file to modify in-place
     * @param targetHue        Target hue (0.0-1.0, HSB space)
     * @param targetSaturation Target saturation (0.0-1.0, HSB space)
     * @param brightnessFactor Scale factor for brightness (1.0 = keep original relative brightness)
     */
    static void retintImage(File file, float targetHue, float targetSaturation,
                            float brightnessFactor) throws IOException {
        BufferedImage img = ImageIO.read(file);
        if (img == null) {
            throw new IOException("Failed to read image: " + file);
        }

        // Ensure we have an alpha channel
        BufferedImage output = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);

        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                int argb = img.getRGB(x, y);
                int alpha = (argb >> 24) & 0xFF;

                // Skip fully transparent pixels
                if (alpha == 0) {
                    output.setRGB(x, y, 0);
                    continue;
                }

                int r = (argb >> 16) & 0xFF;
                int g = (argb >> 8) & 0xFF;
                int b = argb & 0xFF;

                // Convert to HSB
                float[] hsb = Color.RGBtoHSB(r, g, b, null);
                float origBrightness = hsb[2];
                float origSaturation = hsb[1];

                // Apply new hue
                float newHue = targetHue;

                // Blend saturation: keep some of the original saturation structure
                // (highlights are less saturated, shadows may be more/less)
                // Use target saturation scaled by the original saturation ratio
                float newSaturation;
                if (origSaturation < 0.05f) {
                    // Near-white or near-gray pixels: keep desaturated (these are highlights/specular)
                    newSaturation = origSaturation;
                } else {
                    // Scale toward target saturation while preserving relative variation
                    newSaturation = targetSaturation * Math.min(1.0f, origSaturation / 0.6f);
                }

                // Apply brightness factor for Light0/50/100 progression
                float newBrightness = Math.min(1.0f, origBrightness * brightnessFactor);

                // Very dark pixels (shadows): reduce saturation to avoid neon shadows
                if (newBrightness < 0.15f) {
                    newSaturation *= newBrightness / 0.15f;
                }

                int rgb = Color.HSBtoRGB(newHue, newSaturation, newBrightness);
                int newArgb = (alpha << 24) | (rgb & 0x00FFFFFF);
                output.setRGB(x, y, newArgb);
            }
        }

        ImageIO.write(output, "PNG", file);
    }

    /**
     * Converts a hex color string to HSB components.
     */
    static float[] hexToHSB(String hex) {
        String clean = hex.startsWith("#") ? hex.substring(1) : hex;
        int r = Integer.parseInt(clean.substring(0, 2), 16);
        int g = Integer.parseInt(clean.substring(2, 4), 16);
        int b = Integer.parseInt(clean.substring(4, 6), 16);
        return Color.RGBtoHSB(r, g, b, null);
    }
}
