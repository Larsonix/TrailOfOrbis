package io.github.larsonix.trailoforbis.gems.model;

import java.util.Map;
import javax.annotation.Nonnull;

public sealed interface GemDefinition
        permits ActiveGemDefinition, SupportGemDefinition {

    @Nonnull
    String id();

    @Nonnull
    String name();

    @Nonnull
    String description();

    @Nonnull
    GemType gemType();

    @Nonnull
    Map<String, Float> qualityBonuses();
}
