///usr/bin/env java --enable-preview --source 21 "$0" "$@"; exit $?
// ═══════════════════════════════════════════════════════════════════════
// Create Custom Elemental Gem, Crystal & Essence Assets
// ═══════════════════════════════════════════════════════════════════════
// Creates properly-named custom items for the 6 elemental arms + Core,
// fixing the vanilla dependency and the essence naming mismatch.
//
// What this does:
// 1. Creates gem textures for 7 regions (Core + 6 elementals) by
//    re-tinting an existing octant gem texture
// 2. Creates crystal textures for 7 regions (same approach)
// 3. Creates JSON item definitions for new gems (3 light levels each)
// 4. Creates JSON item definitions for new crystals (3 sizes × 3 light levels)
// 5. Creates properly-named essence items + textures for Water/Earth/Wind
//    (copies existing Ice/Life/Water textures with correct names)
// 6. Updates Light.Color and ParticleColor in ALL existing JSON files
//
// Usage: java scripts/create-elemental-assets.java
// Run from project root

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public class CreateElementalAssets {

    // ═══════════════════════════════════════════════════════════════════
    // OKLCH PALETTE (from OklchColorUtilTest output)
    // ═══════════════════════════════════════════════════════════════════

    record RegionColor(String name, String themeHex, String lightColorHex,
                       String particleHex, String sparkHex) {}

    // Theme color → dark version for Light.Color (about 30% brightness of theme)
    // Theme color → particle color (theme itself)
    // Theme color → spark color (lighter/pastel version)
    static final RegionColor[] ELEMENTALS = {
        new RegionColor("Core",      "#EFE3CF", "#5D5850", "#BFB6A6", "#E8DFD2"),
        new RegionColor("Fire",      "#FF7881", "#662F33", "#FF7881", "#FFB9BA"),
        new RegionColor("Lightning", "#F1A900", "#604300", "#F1A900", "#FFC25A"),
        new RegionColor("Earth",     "#7BD23B", "#315417", "#7BD23B", "#91E956"),
        new RegionColor("Wind",      "#00DEBC", "#00584B", "#00DEBC", "#31EDCA"),
        new RegionColor("Water",     "#00CBFF", "#005166", "#00CBFF", "#80DCFF"),
        new RegionColor("Void",      "#DE8DFF", "#583866", "#DE8DFF", "#EAB8FF"),
    };

    static final RegionColor[] OCTANTS = {
        new RegionColor("Havoc",      "#DE77AB", "#592F44", "#DE77AB", "#FFB4D7"),
        new RegionColor("Juggernaut", "#FEA8F6", "#664363", "#FEA8F6", "#FFADF7"),
        new RegionColor("Striker",    "#FFAE74", "#66452E", "#FFAE74", "#FFBE90"),
        new RegionColor("Warden",     "#A4A528", "#424210", "#A4A528", "#D3D661"),
        new RegionColor("Warlock",    "#9E8EEF", "#3F3960", "#9E8EEF", "#CCC6FF"),
        new RegionColor("Lich",       "#94CBFF", "#3B5166", "#94CBFF", "#A5D3FF"),
        new RegionColor("Tempest",    "#00B5CB", "#004851", "#00B5CB", "#57E3FA"),
        new RegionColor("Sentinel",   "#87E496", "#365B3C", "#87E496", "#8AE799"),
    };

    // Base paths
    static final String ASSETS = "src/main/resources/hytale-assets";
    static final String GEM_TEX_DIR = ASSETS + "/Common/Resources/Ores/Gem_Textures";
    static final String CRYSTAL_TEX_DIR = ASSETS + "/Common/Resources/Crystals/Crystal_Big_Textures";
    static final String ESSENCE_TEX_DIR = ASSETS + "/Common/Resources/Ingredients/Essence_Textures_Dim";
    static final String GEM_JSON_DIR = ASSETS + "/Server/Item/Items/SkillTree/Gem";
    static final String CRYSTAL_JSON_DIR = ASSETS + "/Server/Item/Items/SkillTree/Crystal";
    static final String ESSENCE_JSON_DIR = ASSETS + "/Server/Item/Items/SkillTree/Essence";

    public static void main(String[] args) throws Exception {
        System.out.println("═══════════════════════════════════════════════════");
        System.out.println("  Create Custom Elemental Assets");
        System.out.println("═══════════════════════════════════════════════════\n");

        int count = 0;

        // ── STEP 1: Create gem textures for elementals + Core ─────────
        System.out.println("── Step 1: Gem Textures ────────────────────────────");
        // Use Havoc gem as the base template (it has good shading structure)
        File baseGem = new File(GEM_TEX_DIR, "Havoc.png");
        for (RegionColor region : ELEMENTALS) {
            File outFile = new File(GEM_TEX_DIR, region.name + ".png");
            float[] targetHSB = hexToHSB(region.themeHex);
            copyAndRetint(baseGem, outFile, targetHSB[0], targetHSB[1], 1.0f);
            System.out.printf("  + %s.png → %s%n", region.name, region.themeHex);
            count++;
        }

        // ── STEP 2: Create crystal textures for elementals + Core ─────
        System.out.println("\n── Step 2: Crystal Textures ─────────────────────────");
        File baseCrystal = new File(CRYSTAL_TEX_DIR, "Havoc.png");
        for (RegionColor region : ELEMENTALS) {
            File outFile = new File(CRYSTAL_TEX_DIR, region.name + ".png");
            float[] targetHSB = hexToHSB(region.themeHex);
            copyAndRetint(baseCrystal, outFile, targetHSB[0], targetHSB[1], 1.0f);
            System.out.printf("  + %s.png → %s%n", region.name, region.themeHex);
            count++;
        }

        // ── STEP 3: Create gem JSON items ─────────────────────────────
        System.out.println("\n── Step 3: Gem JSON Items ──────────────────────────");
        for (RegionColor region : ELEMENTALS) {
            for (String level : new String[]{"Light0", "Light50", "Light100"}) {
                String filename = "Rock_Gem_" + region.name + "_" + level + ".json";
                float lightRadiusScale = switch (level) {
                    case "Light0" -> 0.0f;
                    case "Light50" -> 0.5f;
                    default -> 1.0f;
                };
                String json = gemJson(region, level, lightRadiusScale);
                Files.writeString(Path.of(GEM_JSON_DIR, filename), json);
                System.out.printf("  + %s%n", filename);
                count++;
            }
        }

        // ── STEP 4: Create crystal JSON items ─────────────────────────
        System.out.println("\n── Step 4: Crystal JSON Items ──────────────────────");
        for (RegionColor region : ELEMENTALS) {
            for (String size : new String[]{"Small", "Medium", "Large"}) {
                for (String level : new String[]{"Light0", "Light50", "Light100"}) {
                    String filename = "Rock_Crystal_" + region.name + "_" + size + "_" + level + ".json";
                    String json = crystalJson(region, size, level);
                    Files.writeString(Path.of(CRYSTAL_JSON_DIR, filename), json);
                    System.out.printf("  + %s%n", filename);
                    count++;
                }
            }
        }

        // ── STEP 5: Fix essence naming (Water/Earth/Wind) ─────────────
        System.out.println("\n── Step 5: Fix Essence Naming ──────────────────────");

        // Create properly-named essence textures (copy + rename)
        String[][] essenceRenames = {
            {"Ice",   "Water"},   // Ice_Essence → Water_Essence (for WATER element)
            {"Life",  "Earth"},   // Life_Essence → Earth_Essence (for EARTH element)
            {"Water", "Wind"},    // Water_Essence → Wind_Essence (for WIND element)
        };

        for (String[] rename : essenceRenames) {
            String oldName = rename[0];
            String newName = rename[1];

            // Copy textures
            for (String level : new String[]{"Light0", "Light50", "Light100"}) {
                String oldTex = oldName + "_Essence_Texture_" + level + ".png";
                String newTex = newName + "_Essence_Texture_" + level + ".png";
                File src = new File(ESSENCE_TEX_DIR, oldTex);
                File dst = new File(ESSENCE_TEX_DIR, newTex);
                if (src.exists() && !dst.exists()) {
                    Files.copy(src.toPath(), dst.toPath());
                    System.out.printf("  + %s (copied from %s)%n", newTex, oldTex);
                    count++;
                } else if (dst.exists()) {
                    System.out.printf("  ~ %s (already exists)%n", newTex);
                }
            }

            // Create JSON item definitions
            RegionColor regionColor = findRegion(newName);
            if (regionColor != null) {
                for (String level : new String[]{"Light0", "Light50", "Light100"}) {
                    String filename = "Ingredient_" + newName + "_Essence_" + level + ".json";
                    float brightnessFactor = switch (level) {
                        case "Light0" -> 0.15f;
                        case "Light50" -> 0.50f;
                        default -> 1.0f;
                    };
                    String json = essenceJson(newName, regionColor, level, brightnessFactor);
                    Files.writeString(Path.of(ESSENCE_JSON_DIR, filename), json);
                    System.out.printf("  + %s%n", filename);
                    count++;
                }
            }
        }

        // ── STEP 6: Update colors in ALL existing octant JSONs ────────
        System.out.println("\n── Step 6: Update Octant JSON Colors ───────────────");
        for (RegionColor region : OCTANTS) {
            // Update gem JSONs
            for (String level : new String[]{"Light0", "Light50", "Light100"}) {
                String filename = "Rock_Gem_" + region.name + "_" + level + ".json";
                File file = new File(GEM_JSON_DIR, filename);
                if (file.exists()) {
                    updateGemColors(file, region);
                    System.out.printf("  ~ %s (colors updated)%n", filename);
                    count++;
                }
            }

            // Update crystal JSONs
            for (String size : new String[]{"Small", "Medium", "Large"}) {
                for (String level : new String[]{"Light0", "Light50", "Light100"}) {
                    String filename = "Rock_Crystal_" + region.name + "_" + size + "_" + level + ".json";
                    File file = new File(CRYSTAL_JSON_DIR, filename);
                    if (file.exists()) {
                        updateCrystalColors(file, region);
                        count++;
                    }
                }
            }
        }
        System.out.println("  (Updated ParticleColor + Light.Color for all octant items)");

        // ── STEP 7: Update essence JSON colors ────────────────────────
        System.out.println("\n── Step 7: Update Essence JSON Colors ──────────────");
        RegionColor[] allRegions = concat(ELEMENTALS, OCTANTS);
        for (RegionColor region : allRegions) {
            String essenceName = getEssenceName(region.name);
            for (String level : new String[]{"Light0", "Light50", "Light100"}) {
                String filename = "Ingredient_" + essenceName + "_Essence_" + level + ".json";
                File file = new File(ESSENCE_JSON_DIR, filename);
                if (file.exists()) {
                    updateEssenceColors(file, region);
                    count++;
                }
            }
        }
        System.out.println("  (Updated Light.Color for all essence items)");

        System.out.printf("%n═══════════════════════════════════════════════════%n");
        System.out.printf("  Created/updated %d assets.%n", count);
        System.out.println("═══════════════════════════════════════════════════");
    }

    // ═══════════════════════════════════════════════════════════════════
    // JSON GENERATORS
    // ═══════════════════════════════════════════════════════════════════

    static String gemJson(RegionColor region, String level, float lightRadiusScale) {
        int radius = (int)(6 * lightRadiusScale);
        return """
            {
              "TranslationProperties": {
                "Name": "server.items.Rock_Gem_%s_%s.name"
              },
              "Icon": "Icons/ItemsGenerated/Rock_Gem_Ruby.png",
              "Parent": "Rock_Gem_Emerald",
              "BlockType": {
                "CustomModelTexture": [
                  {
                    "Texture": "Resources/Ores/Gem_Textures/%s.png",
                    "Weight": 1
                  }
                ],
                "ParticleColor": "%s",
                "Light": {
                  "Color": "%s",
                  "Radius": %d
                },
                "Particles": [
                  {
                    "Color": "%s",
                    "SystemId": "Block_Gem_Sparks"
                  }
                ]
              }
            }
            """.formatted(
                region.name, level,
                region.name,
                region.particleHex,
                region.lightColorHex,
                radius,
                region.sparkHex
            ).stripIndent();
    }

    static String crystalJson(RegionColor region, String size, String level) {
        String model = "Resources/Crystals/Crystal_" + size + ".blockymodel";
        return """
            {
              "TranslationProperties": {
                "Name": "server.items.Rock_Crystal_%s_%s_%s.name"
              },
              "Icon": "Icons/ItemsGenerated/Rock_Crystal_Red_Medium.png",
              "Categories": [
                "Blocks.Rocks"
              ],
              "PlayerAnimationsId": "Block",
              "BlockType": {
                "Material": "Solid",
                "DrawType": "Model",
                "Opacity": "Transparent",
                "CustomModel": "%s",
                "CustomModelTexture": [
                  {
                    "Texture": "Resources/Crystals/Crystal_Big_Textures/%s.png",
                    "Weight": 1
                  }
                ],
                "HitboxType": "Plant_Large",
                "Flags": {},
                "VariantRotation": "DoublePipe",
                "RandomRotation": "YawStep1",
                "BlockParticleSetId": "Crystal",
                "ParticleColor": "%s",
                "BlockSoundSetId": "Crystal",
                "Light": {
                  "Color": "%s"
                },
                "Support": {
                  "Down": [
                    {
                      "FaceType": "Full"
                    }
                  ]
                }
              },
              "Tags": {
                "Type": [
                  "Rock"
                ],
                "Family": [
                  "Crystal"
                ]
              }
            }
            """.formatted(
                region.name, size, level,
                model,
                region.name,
                region.particleHex,
                region.lightColorHex
            ).stripIndent();
    }

    static String essenceJson(String elementName, RegionColor region,
                               String level, float brightnessFactor) {
        int radius = (int)(6 * brightnessFactor);
        return """
            {
              "TranslationProperties": {
                "Name": "server.items.Ingredient_%s_Essence_%s.name",
                "Description": "server.items.Ingredient_%s_Essence_%s.description"
              },
              "Icon": "Icons/ItemsGenerated/Ingredient_Fire_Essence.png",
              "Categories": [
                "Items.Ingredients"
              ],
              "Model": "Resources/Ingredients/Essence.blockymodel",
              "Texture": "Resources/Ingredients/Essence_Textures_Dim/%s_Essence_Texture_%s.png",
              "PlayerAnimationsId": "Item",
              "IconProperties": {
                "Scale": 0.6,
                "Rotation": [0, 0, 0],
                "Translation": [0, -13]
              },
              "Tags": {
                "Type": ["Ingredient"]
              },
              "ItemEntity": {
                "ParticleSystemId": null,
                "ShowItemParticles": false
              },
              "Scale": 0.8,
              "Light": {
                "Color": "%s",
                "Radius": %d
              },
              "ItemSoundSetId": "ISS_Items_Gems",
              "DropOnDeath": true
            }
            """.formatted(
                elementName, level,
                elementName, level,
                elementName, level,
                region.lightColorHex,
                radius
            ).stripIndent();
    }

    // ═══════════════════════════════════════════════════════════════════
    // JSON COLOR UPDATERS
    // ═══════════════════════════════════════════════════════════════════

    static void updateGemColors(File file, RegionColor region) throws IOException {
        String content = Files.readString(file.toPath());
        // Update ParticleColor
        content = content.replaceAll("\"ParticleColor\": \"#[0-9a-fA-F]+\"",
            "\"ParticleColor\": \"" + region.particleHex + "\"");
        // Update Light.Color
        content = content.replaceAll("\"Color\": \"#[0-9a-fA-F]+\"",
            "\"Color\": \"" + region.lightColorHex + "\"");
        // Update spark color
        content = content.replaceAll("(\"Color\": \"#[0-9a-fA-F]+\",\\s*\"SystemId\": \"Block_Gem_Sparks\")",
            "\"Color\": \"" + region.sparkHex + "\", \"SystemId\": \"Block_Gem_Sparks\"");
        Files.writeString(file.toPath(), content);
    }

    static void updateCrystalColors(File file, RegionColor region) throws IOException {
        String content = Files.readString(file.toPath());
        content = content.replaceAll("\"ParticleColor\": \"#[0-9a-fA-F]+\"",
            "\"ParticleColor\": \"" + region.particleHex + "\"");
        // Only update the Light.Color (not other Color fields)
        content = content.replaceFirst("(\"Light\":\\s*\\{\\s*\"Color\": )\"#[0-9a-fA-F]+\"",
            "$1\"" + region.lightColorHex + "\"");
        Files.writeString(file.toPath(), content);
    }

    static void updateEssenceColors(File file, RegionColor region) throws IOException {
        String content = Files.readString(file.toPath());
        content = content.replaceFirst("(\"Light\":\\s*\\{\\s*\"Color\": )\"#[0-9a-fA-F]+\"",
            "$1\"" + region.lightColorHex + "\"");
        Files.writeString(file.toPath(), content);
    }

    // ═══════════════════════════════════════════════════════════════════
    // TEXTURE RE-TINTING
    // ═══════════════════════════════════════════════════════════════════

    static void copyAndRetint(File source, File dest, float targetHue,
                               float targetSaturation, float brightnessFactor) throws IOException {
        BufferedImage img = ImageIO.read(source);
        BufferedImage output = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);

        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                int argb = img.getRGB(x, y);
                int alpha = (argb >> 24) & 0xFF;
                if (alpha == 0) { output.setRGB(x, y, 0); continue; }

                int r = (argb >> 16) & 0xFF;
                int g = (argb >> 8) & 0xFF;
                int b = argb & 0xFF;
                float[] hsb = Color.RGBtoHSB(r, g, b, null);

                float newSat = hsb[1] < 0.05f ? hsb[1] :
                    targetSaturation * Math.min(1.0f, hsb[1] / 0.6f);
                float newBri = Math.min(1.0f, hsb[2] * brightnessFactor);
                if (newBri < 0.15f) newSat *= newBri / 0.15f;

                int rgb = Color.HSBtoRGB(targetHue, newSat, newBri);
                output.setRGB(x, y, (alpha << 24) | (rgb & 0x00FFFFFF));
            }
        }
        ImageIO.write(output, "PNG", dest);
    }

    // ═══════════════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════════════

    static float[] hexToHSB(String hex) {
        String clean = hex.startsWith("#") ? hex.substring(1) : hex;
        int r = Integer.parseInt(clean.substring(0, 2), 16);
        int g = Integer.parseInt(clean.substring(2, 4), 16);
        int b = Integer.parseInt(clean.substring(4, 6), 16);
        return Color.RGBtoHSB(r, g, b, null);
    }

    static String getEssenceName(String regionName) {
        // Map element names to their current essence texture names
        return switch (regionName) {
            case "Water" -> "Water";  // New properly-named essence (we're creating it)
            case "Earth" -> "Earth";  // New properly-named essence
            case "Wind"  -> "Wind";   // New properly-named essence
            default -> regionName;    // Fire, Lightning, Void, and all octants match
        };
    }

    static RegionColor findRegion(String name) {
        for (RegionColor r : ELEMENTALS) if (r.name.equals(name)) return r;
        for (RegionColor r : OCTANTS) if (r.name.equals(name)) return r;
        return null;
    }

    static RegionColor[] concat(RegionColor[] a, RegionColor[] b) {
        RegionColor[] result = new RegionColor[a.length + b.length];
        System.arraycopy(a, 0, result, 0, a.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }
}
