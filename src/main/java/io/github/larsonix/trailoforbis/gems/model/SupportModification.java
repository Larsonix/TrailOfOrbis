package io.github.larsonix.trailoforbis.gems.model;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Nonnull;

public record SupportModification(@Nonnull String type, @Nonnull Map<String, Object> properties) {
    public SupportModification {
        Objects.requireNonNull(type, "modification type");
        Objects.requireNonNull(properties, "properties");
        properties = Collections.unmodifiableMap(properties);
    }

    public String getString(@Nonnull String key) {
        Object value = this.properties.get(key);
        return value != null ? value.toString() : null;
    }

    public float getFloat(@Nonnull String key, float defaultValue) {
        Object value = this.properties.get(key);
        if (value instanceof Number num) {
            return num.floatValue();
        }
        return defaultValue;
    }

    public int getInt(@Nonnull String key, int defaultValue) {
        Object value = this.properties.get(key);
        if (value instanceof Number num) {
            return num.intValue();
        }
        return defaultValue;
    }
}
