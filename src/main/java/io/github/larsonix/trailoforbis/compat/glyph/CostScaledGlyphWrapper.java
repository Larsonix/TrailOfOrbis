package io.github.larsonix.trailoforbis.compat.glyph;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.riprod.hexcode.core.common.glyphs.component.Glyph;
import com.riprod.hexcode.core.common.glyphs.component.GlyphHandler;
import com.riprod.hexcode.core.common.glyphs.registry.GlyphAsset;
import com.riprod.hexcode.core.common.glyphs.variables.HexVar;
import com.riprod.hexcode.core.state.execution.component.HexContext;
import com.riprod.hexcode.core.state.execution.component.VolatilityTracker;
import com.riprod.hexcode.utils.HexVarUtil;
import io.github.larsonix.trailoforbis.attributes.StatMapBridge;
import io.github.larsonix.trailoforbis.compat.HexCastStateStore;

import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Wraps a GlyphHandler to scale MANA cost by a user-controllable slot value.
 *
 * <p>Volatility is consumed at the base rate (complexity budget is not affected by
 * damage parameters). Instead, extra mana is deducted proportional to the slot
 * inflation — more damage costs more energy.
 *
 * <p>If the caster lacks sufficient mana for the inflated cost, the glyph fizzles
 * (returns false from consumeVolatility, triggering Hexcode's fizzle mechanism).
 *
 * <p>Design rationale: volatility = complexity budget (how many glyphs in the chain),
 * mana = energy budget (how powerful each glyph is). Scaling volatility by damage
 * penalizes multi-glyph combos for dealing more damage, which is unintuitive.
 * Scaling mana means players pay energy for power, which is the natural cost.
 *
 * @see HexGlyphPatcher
 */
public final class CostScaledGlyphWrapper implements GlyphHandler {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final GlyphHandler delegate;
    private final String slotName;
    private final double defaultValue;

    /**
     * @param delegate The original GlyphHandler to wrap
     * @param slotName The slot name whose value should scale mana cost (e.g., "power")
     * @param defaultValue The expected default value for that slot (e.g., 5.0 for Bolt's POWER)
     */
    public CostScaledGlyphWrapper(@Nonnull GlyphHandler delegate,
                                   @Nonnull String slotName,
                                   double defaultValue) {
        this.delegate = delegate;
        this.slotName = slotName;
        this.defaultValue = defaultValue;
    }

    @Override
    public String getId() {
        return delegate.getId();
    }

    /**
     * Consumes base volatility (unscaled) and deducts extra mana for inflated slot values.
     *
     * <p>If a player wires 50 into Bolt's POWER slot (default 5.0):
     * ratio = 50 / 5.0 = 10. Base mana = 8. Extra mana = 8 × (10 - 1) = 72.
     * Total mana for this glyph: 8 (pre-cast) + 72 (extra) = 80. If the player
     * has less than 72 mana remaining, the glyph fizzles.
     *
     * <p>Volatility is consumed at the normal base rate — the complexity budget
     * is unaffected by damage parameter choices.
     */
    @Override
    public boolean consumeVolatility(Glyph glyph, HexContext hexContext) {
        // 1. Consume base volatility (unscaled — delegate to default behavior)
        VolatilityTracker tracker = hexContext.getVolatilityTracker();
        if (tracker != null) {
            int repeatCount = tracker.getGlyphUsage(glyph.getId());
            float baseCost = VolatilityTracker.computeGlyphCost(glyph, repeatCount);
            if (baseCost > 0 && !tracker.consumeVolatility(baseCost)) {
                return false; // Out of volatility — fizzle
            }
        }

        // 2. Read the slot value and compute ratio
        HexVar slotVar = glyph.readSlot(slotName, hexContext);
        double actualValue = HexVarUtil.numberOrDefault(slotVar, defaultValue);
        double ratio = Math.max(1.0, Math.abs(actualValue) / defaultValue);

        if (ratio <= 1.0) {
            return true; // No inflation — no extra mana cost
        }

        // 3. Calculate extra mana cost from the glyph's base mana consumption
        GlyphAsset asset = GlyphAsset.getAssetMap().getAsset(glyph.getGlyphId());
        if (asset == null) {
            return true; // No asset — can't compute mana, allow execution
        }

        float baseMana = asset.getManaConsumption()
                * ((1 - glyph.getEfficiency()) * 0.25f + 0.75f);
        float extraMana = baseMana * (float) (ratio - 1.0);

        if (extraMana <= 0) {
            return true;
        }

        // 4. Deduct extra mana from the caster
        Ref<EntityStore> casterRef = hexContext.getCasterRef();
        if (casterRef == null || !casterRef.isValid()) {
            return true; // No caster ref — allow execution (safety fallback)
        }

        CommandBuffer<EntityStore> accessor = hexContext.getAccessor();
        EntityStatMap statMap = accessor.getComponent(casterRef, EntityStatMap.getComponentType());
        if (statMap == null) {
            return true; // No stat map — allow execution
        }

        int manaIndex = DefaultEntityStatTypes.getMana();
        float currentMana = statMap.get(manaIndex).get();

        if (currentMana < extraMana) {
            LOGGER.atFine().log("[GlyphPatch] %s: insufficient mana for inflated slot '%s' " +
                            "(value=%.1f, default=%.1f, ratio=%.1f) — need %.1f extra mana, have %.1f",
                    getId(), slotName, actualValue, defaultValue, ratio, extraMana, currentMana);
            return false; // Insufficient mana — fizzle
        }

        statMap.subtractStatValue(manaIndex, extraMana);

        if (ratio > 2.0) {
            LOGGER.atFine().log("[GlyphPatch] %s: slot '%s' value=%.1f (default=%.1f, ratio=%.1f) " +
                            "→ extra mana cost %.1f (base mana=%.1f)",
                    getId(), slotName, actualValue, defaultValue, ratio, extraMana, baseMana);
        }

        return true;
    }

    /**
     * Executes the wrapped glyph, injecting the live VolatilityTracker into a ThreadLocal
     * so that RPGDamageSystem can read the current remainingBudget during synchronous
     * damage event processing.
     *
     * <p>This runs AFTER consumeVolatility() has drained this glyph's cost, so
     * remainingBudget reflects post-consumption state — exactly the right moment
     * for damage scaling.
     */
    @Override
    public void execute(Glyph glyph, HexContext hexContext) {
        VolatilityTracker tracker = hexContext.getVolatilityTracker();
        if (tracker != null) {
            float volatilityMax = resolveVolatilityMax(hexContext);
            HexCastStateStore.setActiveTracker(tracker, volatilityMax);
        }
        try {
            delegate.execute(glyph, hexContext);
        } finally {
            HexCastStateStore.clearActiveTracker();
        }
    }

    /**
     * Resolves the player's max Volatility stat from the EntityStatMap.
     * Returns 0 if the stat is unavailable (non-player caster, missing stat).
     */
    private float resolveVolatilityMax(@Nonnull HexContext hexContext) {
        int volIndex = StatMapBridge.getHexVolatilityIndex();
        if (volIndex == Integer.MIN_VALUE) return 0f;
        Ref<EntityStore> casterRef = hexContext.getCasterRef();
        if (casterRef == null || !casterRef.isValid()) return 0f;
        CommandBuffer<EntityStore> accessor = hexContext.getAccessor();
        if (accessor == null) return 0f;
        EntityStatMap statMap = accessor.getComponent(casterRef, EntityStatMap.getComponentType());
        if (statMap == null) return 0f;
        EntityStatValue volStat = statMap.get(volIndex);
        return volStat != null ? volStat.getMax() : 0f;
    }

    @Nullable
    @Override
    public HexVar readValue(Glyph glyph, HexContext hexContext) {
        return delegate.readValue(glyph, hexContext);
    }
}
