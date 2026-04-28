package io.github.larsonix.trailoforbis.gear.loot;

import io.github.larsonix.trailoforbis.attributes.AttributeManager;
import io.github.larsonix.trailoforbis.attributes.AttributeType;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Calculates the player's rarity bonus from the WIND attribute.
 *
 * <p>WIND serves as the "fortune/luck" stat in the elemental system.
 * The formula is: {@code wind × luckToRarityPercent}.
 *
 * <p>This utility is shared across all drop pathways (mob gear, mob stones,
 * container loot) so the formula lives in one place.
 */
public final class RarityBonusCalculator {

    private final AttributeManager attributeManager;
    private final double luckToRarityPercent;

    /**
     * @param attributeManager For looking up player attributes
     * @param luckToRarityPercent Config value (gear-balance.yml wind_to_rarity_percent)
     */
    public RarityBonusCalculator(
            @Nonnull AttributeManager attributeManager,
            double luckToRarityPercent) {
        this.attributeManager = Objects.requireNonNull(attributeManager, "attributeManager cannot be null");
        this.luckToRarityPercent = luckToRarityPercent;
    }

    /**
     * Calculates the player's rarity bonus from their WIND attribute.
     *
     * @param playerId The player UUID
     * @return Rarity bonus as a percentage (e.g., 2.5 means +2.5%)
     */
    public double calculatePlayerBonus(@Nonnull UUID playerId) {
        Map<AttributeType, Integer> attributes = attributeManager.getPlayerAttributes(playerId);
        int wind = attributes.getOrDefault(AttributeType.WIND, 0);
        return wind * luckToRarityPercent;
    }
}
