package io.github.larsonix.trailoforbis.gems.model;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.annotation.Nonnull;

public record SupportGemDefinition(
        @Nonnull String id,
        @Nonnull String name,
        @Nonnull String description,
        @Nonnull List<String> requiresTags,
        @Nonnull List<SupportModification> modifications,
        @Nonnull Map<String, Float> qualityBonuses
) implements GemDefinition {

    public SupportGemDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(description, "description");
        requiresTags = List.copyOf(requiresTags);
        modifications = List.copyOf(modifications);
        qualityBonuses = Map.copyOf(qualityBonuses);
    }

    @Override
    @Nonnull
    public GemType gemType() {
        return GemType.SUPPORT;
    }

    public boolean isCompatibleWith(@Nonnull GemTags activeTags) {
        if (this.requiresTags.isEmpty()) {
            return true;
        }
        return activeTags.hasAllTags(Set.copyOf(this.requiresTags));
    }
}
