package io.github.larsonix.trailoforbis.skilltree.conversion;

import javax.annotation.Nonnull;
import java.util.*;

/**
 * Calculates damage after applying conversion effects from skill tree nodes.
 *
 * <p>Conversion follows PoE-style rules:
 * <ol>
 *   <li>Sort conversions by source element priority (Physical → Elemental → Chaos)</li>
 *   <li>Apply each conversion in order</li>
 *   <li>Converted damage can be converted again by later conversions</li>
 *   <li>Total conversion from a source cannot exceed 100%</li>
 * </ol>
 *
 * <p>Example flow for Physical with 50% → Fire and 30% → Cold:
 * <pre>
 * Input: 100 Physical
 * Step 1: 50 Physical → 50 Fire, 50 Physical remains
 * Step 2: 15 Physical → 15 Cold, 35 Physical remains
 * Output: 35 Physical, 50 Fire, 15 Cold
 * </pre>
 *
 * <p>Usage:
 * <pre>
 * ConversionCalculator calc = new ConversionCalculator();
 * List&lt;ConversionEffect&gt; conversions = player.getAllocatedConversions();
 *
 * Map&lt;DamageElement, Float&gt; damage = new EnumMap&lt;&gt;(DamageElement.class);
 * damage.put(DamageElement.PHYSICAL, 100f);
 *
 * Map&lt;DamageElement, Float&gt; converted = calc.applyConversions(damage, conversions);
 * </pre>
 */
public class ConversionCalculator {

    /**
     * Applies conversion effects to damage values.
     *
     * @param baseDamage  Map of element → damage amount (will not be modified)
     * @param conversions List of conversion effects from skill tree nodes
     * @return New map with converted damage values
     */
    @Nonnull
    public Map<DamageElement, Float> applyConversions(
        @Nonnull Map<DamageElement, Float> baseDamage,
        @Nonnull List<ConversionEffect> conversions
    ) {
        if (conversions.isEmpty()) {
            return new EnumMap<>(baseDamage);
        }

        // Copy base damage to working map
        EnumMap<DamageElement, Float> working = new EnumMap<>(DamageElement.class);
        for (Map.Entry<DamageElement, Float> entry : baseDamage.entrySet()) {
            if (entry.getValue() > 0) {
                working.put(entry.getKey(), entry.getValue());
            }
        }

        // Filter and sort conversions by source priority
        List<ConversionEffect> validConversions = conversions.stream()
            .filter(ConversionEffect::isValid)
            .sorted(Comparator.comparingInt(c -> c.source().getPriority()))
            .toList();

        // Group conversions by source element
        Map<DamageElement, List<ConversionEffect>> bySource = new EnumMap<>(DamageElement.class);
        for (ConversionEffect conv : validConversions) {
            bySource.computeIfAbsent(conv.source(), k -> new ArrayList<>()).add(conv);
        }

        // Process each source element in priority order
        for (DamageElement source : DamageElement.values()) {
            List<ConversionEffect> sourceConversions = bySource.get(source);
            if (sourceConversions == null || sourceConversions.isEmpty()) {
                continue;
            }

            float availableDamage = working.getOrDefault(source, 0f);
            if (availableDamage <= 0) {
                continue;
            }

            // Calculate total conversion percentage (capped at 100%)
            float totalConversionPct = 0;
            for (ConversionEffect conv : sourceConversions) {
                totalConversionPct += conv.percent();
            }

            // If over 100%, normalize proportionally
            float scale = totalConversionPct > 100 ? 100 / totalConversionPct : 1.0f;

            // Apply each conversion
            float totalConverted = 0;
            for (ConversionEffect conv : sourceConversions) {
                float effectivePct = conv.percent() * scale;
                float convertedAmount = availableDamage * (effectivePct / 100f);

                // Add to target
                float currentTarget = working.getOrDefault(conv.target(), 0f);
                working.put(conv.target(), currentTarget + convertedAmount);

                totalConverted += convertedAmount;
            }

            // Reduce source by total converted
            float remaining = availableDamage - totalConverted;
            if (remaining > 0.001f) {
                working.put(source, remaining);
            } else {
                working.remove(source);
            }
        }

        return working;
    }

    /**
     * Calculates the total damage after conversions.
     *
     * @param damage Map of element → damage amount
     * @return Total damage across all elements
     */
    public float totalDamage(@Nonnull Map<DamageElement, Float> damage) {
        float total = 0;
        for (Float value : damage.values()) {
            total += value;
        }
        return total;
    }

    /**
     * Aggregates multiple conversion effect lists into one.
     *
     * <p>Use this when collecting conversions from multiple skill nodes.
     *
     * @param conversionLists Multiple lists of conversion effects
     * @return Combined list
     */
    @SafeVarargs
    @Nonnull
    public final List<ConversionEffect> aggregate(@Nonnull List<ConversionEffect>... conversionLists) {
        List<ConversionEffect> result = new ArrayList<>();
        for (List<ConversionEffect> list : conversionLists) {
            if (list != null) {
                result.addAll(list);
            }
        }
        return result;
    }
}
