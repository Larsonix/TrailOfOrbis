package io.github.larsonix.trailoforbis.gems.model;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;

public record GemTags(@Nonnull Map<String, Set<String>> categories) {
    public static final GemTags EMPTY = new GemTags(Map.of());

    public GemTags {
        Objects.requireNonNull(categories, "categories cannot be null");
        HashMap<String, Set<String>> copy = new HashMap<>();
        categories.forEach((key, values) -> {
            if (values != null && !values.isEmpty()) {
                copy.put(key, Set.copyOf(values));
            }
        });
        categories = Collections.unmodifiableMap(copy);
    }

    public boolean hasTag(@Nonnull String tagValue) {
        for (Set<String> values : this.categories.values()) {
            if (!values.contains(tagValue)) continue;
            return true;
        }
        return false;
    }

    public boolean hasTag(@Nonnull String category, @Nonnull String tagValue) {
        Set<String> values = this.categories.get(category);
        return values != null && values.contains(tagValue);
    }

    public boolean hasAllTags(@Nonnull Set<String> requiredTags) {
        Set<String> all = this.allTagValues();
        return all.containsAll(requiredTags);
    }

    @Nonnull
    public Set<String> allTagValues() {
        return this.categories.values().stream().flatMap(Collection::stream).collect(Collectors.toUnmodifiableSet());
    }

    @Nonnull
    public Set<String> getCategory(@Nonnull String category) {
        return this.categories.getOrDefault(category, Set.of());
    }

    @Nonnull
    public static GemTags fromYaml(@Nonnull Map<String, Object> yamlMap) {
        HashMap<String, Set<String>> categories = new HashMap<>();
        yamlMap.forEach((category, value) -> {
            if (value instanceof List<?> list) {
                LinkedHashSet<String> tags = new LinkedHashSet<>();
                for (Object item : list) {
                    if (item == null) continue;
                    tags.add(item.toString());
                }
                if (!tags.isEmpty()) {
                    categories.put(category, tags);
                }
            }
        });
        return new GemTags(categories);
    }
}
