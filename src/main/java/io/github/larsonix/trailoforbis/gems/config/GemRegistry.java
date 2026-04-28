package io.github.larsonix.trailoforbis.gems.config;

import io.github.larsonix.trailoforbis.gems.model.ActiveGemDefinition;
import io.github.larsonix.trailoforbis.gems.model.GemDefinition;
import io.github.larsonix.trailoforbis.gems.model.SupportGemDefinition;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nonnull;

public final class GemRegistry {
    private final Map<String, GemDefinition> definitions;

    GemRegistry(@Nonnull Map<String, GemDefinition> definitions) {
        this.definitions = Collections.unmodifiableMap(definitions);
    }

    @Nonnull
    public Optional<GemDefinition> getDefinition(@Nonnull String id) {
        return Optional.ofNullable(this.definitions.get(id));
    }

    @Nonnull
    public Collection<ActiveGemDefinition> getActiveGems() {
        return this.definitions.values().stream()
                .filter(d -> d instanceof ActiveGemDefinition)
                .map(d -> (ActiveGemDefinition) d)
                .toList();
    }

    @Nonnull
    public Collection<SupportGemDefinition> getSupportGems() {
        return this.definitions.values().stream()
                .filter(d -> d instanceof SupportGemDefinition)
                .map(d -> (SupportGemDefinition) d)
                .toList();
    }

    @Nonnull
    public Set<String> getAllIds() {
        return this.definitions.keySet();
    }

    public int size() {
        return this.definitions.size();
    }

    public boolean contains(@Nonnull String id) {
        return this.definitions.containsKey(id);
    }
}
