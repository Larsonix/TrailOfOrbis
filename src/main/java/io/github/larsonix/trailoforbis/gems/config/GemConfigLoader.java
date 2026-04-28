package io.github.larsonix.trailoforbis.gems.config;

import com.hypixel.hytale.logger.HytaleLogger;
import io.github.larsonix.trailoforbis.gems.model.ActiveGemDefinition;
import io.github.larsonix.trailoforbis.gems.model.CastType;
import io.github.larsonix.trailoforbis.gems.model.GemDefinition;
import io.github.larsonix.trailoforbis.gems.model.GemTags;
import io.github.larsonix.trailoforbis.gems.model.GemType;
import io.github.larsonix.trailoforbis.gems.model.SupportGemDefinition;
import io.github.larsonix.trailoforbis.gems.model.SupportModification;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.yaml.snakeyaml.Yaml;

public final class GemConfigLoader {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private final Yaml yaml = new Yaml();

    @Nonnull
    public GemRegistry loadAll(@Nonnull Path gemsDir) {
        LinkedHashMap<String, GemDefinition> definitions = new LinkedHashMap<>();
        Path activeDir = gemsDir.resolve("active");
        Path supportDir = gemsDir.resolve("support");
        if (Files.isDirectory(activeDir)) {
            this.loadDirectory(activeDir, GemType.ACTIVE, definitions);
        } else {
            LOGGER.atInfo().log("No active gems directory found at %s", activeDir);
        }
        if (Files.isDirectory(supportDir)) {
            this.loadDirectory(supportDir, GemType.SUPPORT, definitions);
        } else {
            LOGGER.atInfo().log("No support gems directory found at %s", supportDir);
        }
        LOGGER.atInfo().log("Loaded %d gem definitions (%d active, %d support)",
                definitions.size(),
                definitions.values().stream().filter(d -> d instanceof ActiveGemDefinition).count(),
                definitions.values().stream().filter(d -> d instanceof SupportGemDefinition).count());
        return new GemRegistry(definitions);
    }

    private void loadDirectory(Path dir, GemType type, Map<String, GemDefinition> definitions) {
        try (Stream<Path> files = Files.walk(dir)) {
            files.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".yml") || p.toString().endsWith(".yaml"))
                    .forEach(path -> this.loadFile(path, type, definitions));
        } catch (IOException e) {
            LOGGER.atSevere().withCause(e).log("Failed to scan gem directory: %s", dir);
        }
    }

    @SuppressWarnings("unchecked")
    private void loadFile(Path path, GemType type, Map<String, GemDefinition> definitions) {
        try (InputStream input = Files.newInputStream(path)) {
            Object loaded = this.yaml.load(input);
            if (!(loaded instanceof Map)) {
                LOGGER.atWarning().log("Gem file is not a valid YAML map: %s", path);
                return;
            }
            Map<String, Object> yamlMap = (Map<String, Object>) loaded;
            GemDefinition definition = switch (type) {
                case ACTIVE -> this.parseActiveGem(yamlMap, path);
                case SUPPORT -> this.parseSupportGem(yamlMap, path);
            };
            if (definition == null) {
                return;
            }
            if (definitions.containsKey(definition.id())) {
                LOGGER.atWarning().log("Duplicate gem ID '%s' in %s \u2014 skipping (already loaded)", definition.id(), path);
                return;
            }
            definitions.put(definition.id(), definition);
            LOGGER.atFine().log("Loaded %s gem: %s (%s)", type.name().toLowerCase(), definition.id(), path.getFileName());
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to load gem file: %s", path);
        }
    }

    @SuppressWarnings("unchecked")
    @Nullable
    private ActiveGemDefinition parseActiveGem(Map<String, Object> yaml, Path source) {
        String id = GemConfigLoader.getString(yaml, "id");
        if (id == null || id.isBlank()) {
            LOGGER.atWarning().log("Active gem missing 'id' in %s", source);
            return null;
        }
        String name = GemConfigLoader.getString(yaml, "name", id);
        String description = GemConfigLoader.getString(yaml, "description", "");
        GemTags tags = GemTags.EMPTY;
        Object tagsObj = yaml.get("tags");
        if (tagsObj instanceof Map) {
            Map<String, Object> tagsMap = (Map<String, Object>) tagsObj;
            tags = GemTags.fromYaml(tagsMap);
        }
        List<String> weaponCategories = GemConfigLoader.getStringList(yaml, "weapon_categories");
        List<String> gearSlots = GemConfigLoader.getStringList(yaml, "gear_slots");
        if (gearSlots.isEmpty()) {
            gearSlots = List.of("Any");
        }
        ActiveGemDefinition.DamageConfig damage = ActiveGemDefinition.DamageConfig.NONE;
        Object damageObj = yaml.get("damage");
        if (damageObj instanceof Map) {
            Map<String, Object> dm = (Map<String, Object>) damageObj;
            damage = new ActiveGemDefinition.DamageConfig(
                    GemConfigLoader.getFloat(dm, "base_percent", 0.0f),
                    GemConfigLoader.getString(dm, "element"),
                    GemConfigLoader.getFloat(dm, "aoe_radius", 0.0f));
        }
        ActiveGemDefinition.CostConfig cost = ActiveGemDefinition.CostConfig.FREE;
        Object costObj = yaml.get("cost");
        if (costObj instanceof Map) {
            Map<String, Object> cm = (Map<String, Object>) costObj;
            cost = new ActiveGemDefinition.CostConfig(
                    GemConfigLoader.getString(cm, "type", "none"),
                    GemConfigLoader.getFloat(cm, "amount", 0.0f),
                    GemConfigLoader.getFloat(cm, "stamina_amount", 0.0f),
                    GemConfigLoader.getFloat(cm, "mana_amount", 0.0f),
                    GemConfigLoader.getFloat(cm, "amount_per_second", 0.0f));
        }
        float cooldown = GemConfigLoader.getFloat(yaml, "cooldown", 0.0f);
        CastType castType = GemConfigLoader.parseCastType(GemConfigLoader.getString(yaml, "cast_type", "INSTANT"));
        ActiveGemDefinition.ChannelConfig channel = null;
        Object channelObj = yaml.get("channel");
        if (channelObj instanceof Map) {
            Map<String, Object> chm = (Map<String, Object>) channelObj;
            channel = new ActiveGemDefinition.ChannelConfig(
                    GemConfigLoader.getFloat(chm, "max_duration", 5.0f),
                    GemConfigLoader.getFloat(chm, "tick_rate", 0.5f),
                    GemConfigLoader.getFloat(chm, "movement_multiplier", 0.6f));
        }
        ActiveGemDefinition.AilmentConfig ailment = null;
        Object ailmentObj = yaml.get("ailment");
        if (ailmentObj instanceof Map) {
            Map<String, Object> am = (Map<String, Object>) ailmentObj;
            String ailType = GemConfigLoader.getString(am, "type");
            if (ailType != null) {
                ailment = new ActiveGemDefinition.AilmentConfig(
                        ailType,
                        GemConfigLoader.getFloat(am, "base_chance", 0.0f));
            }
        }
        Map<String, Float> qualityBonuses = GemConfigLoader.getFloatMap(yaml, "quality_bonuses");
        ActiveGemDefinition.VisualsConfig visuals = ActiveGemDefinition.VisualsConfig.EMPTY;
        Object visualsObj = yaml.get("visuals");
        if (visualsObj instanceof Map) {
            Map<String, Object> vm = (Map<String, Object>) visualsObj;
            visuals = new ActiveGemDefinition.VisualsConfig(
                    GemConfigLoader.getString(vm, "projectile"),
                    GemConfigLoader.getString(vm, "cast_sound"),
                    GemConfigLoader.getString(vm, "impact_sound"),
                    GemConfigLoader.getString(vm, "impact_particles"));
        }
        return new ActiveGemDefinition(id, name, description, tags, weaponCategories, gearSlots,
                damage, cost, cooldown, castType, channel, ailment, qualityBonuses, visuals);
    }

    @SuppressWarnings("unchecked")
    @Nullable
    private SupportGemDefinition parseSupportGem(Map<String, Object> yaml, Path source) {
        String id = GemConfigLoader.getString(yaml, "id");
        if (id == null || id.isBlank()) {
            LOGGER.atWarning().log("Support gem missing 'id' in %s", source);
            return null;
        }
        String name = GemConfigLoader.getString(yaml, "name", id);
        String description = GemConfigLoader.getString(yaml, "description", "");
        List<String> requiresTags = GemConfigLoader.getStringList(yaml, "requires_tags");
        ArrayList<SupportModification> modifications = new ArrayList<>();
        Object modsObj = yaml.get("modifications");
        if (modsObj instanceof List) {
            List<?> modsList = (List<?>) modsObj;
            for (Object modObj : modsList) {
                if (!(modObj instanceof Map)) continue;
                Map<String, Object> mm = (Map<String, Object>) modObj;
                String type = GemConfigLoader.getString(mm, "type");
                if (type == null) continue;
                LinkedHashMap<String, Object> props = new LinkedHashMap<>(mm);
                props.remove("type");
                modifications.add(new SupportModification(type, props));
            }
        }
        Map<String, Float> qualityBonuses = GemConfigLoader.getFloatMap(yaml, "quality_bonuses");
        return new SupportGemDefinition(id, name, description, requiresTags, modifications, qualityBonuses);
    }

    @Nullable
    private static String getString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }

    @Nonnull
    private static String getString(Map<String, Object> map, String key, @Nonnull String defaultValue) {
        Object value = map.get(key);
        return value != null ? value.toString() : defaultValue;
    }

    private static float getFloat(Map<String, Object> map, String key, float defaultValue) {
        Object value = map.get(key);
        if (value instanceof Number num) {
            return num.floatValue();
        }
        return defaultValue;
    }

    @SuppressWarnings("unchecked")
    @Nonnull
    private static List<String> getStringList(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof List) {
            List<?> list = (List<?>) value;
            return list.stream().filter(Objects::nonNull).map(Object::toString).toList();
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    @Nonnull
    private static Map<String, Float> getFloatMap(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Map) {
            Map<Object, Object> rawMap = (Map<Object, Object>) value;
            LinkedHashMap<String, Float> result = new LinkedHashMap<>();
            rawMap.forEach((k, v) -> {
                if (k != null && v instanceof Number num) {
                    result.put(k.toString(), num.floatValue());
                }
            });
            return result;
        }
        return Map.of();
    }

    private static CastType parseCastType(String str) {
        if (str == null) {
            return CastType.INSTANT;
        }
        try {
            return CastType.valueOf(str.toUpperCase());
        } catch (IllegalArgumentException e) {
            LOGGER.atWarning().log("Unknown cast type '%s', defaulting to INSTANT", str);
            return CastType.INSTANT;
        }
    }
}
