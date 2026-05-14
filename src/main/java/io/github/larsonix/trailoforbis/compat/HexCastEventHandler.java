package io.github.larsonix.trailoforbis.compat;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.EcsEvent;
import com.hypixel.hytale.component.system.WorldEventSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import io.github.larsonix.trailoforbis.attributes.StatMapBridge;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import io.github.larsonix.trailoforbis.attributes.AttributeManager;
import io.github.larsonix.trailoforbis.attributes.ComputedStats;
import io.github.larsonix.trailoforbis.config.ConfigManager;

// Direct Hexcode import — this class is only loaded when Hexcode is present
import com.riprod.hexcode.api.event.HexCastEvent;
import com.riprod.hexcode.core.state.execution.component.HexContext;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Thin event handler for HexCastEvent. Delegates all state to
 * {@link HexCastStateStore} and echo mechanics to {@link HexSpellEchoService}.
 *
 * <p>Handler ordering: Hexcode registers HexCastEventSystem during setup().
 * We register during start(). ECS dispatches in registration order, so by the
 * time this handler fires, the original spell has fully executed. This means:
 * <ul>
 *   <li>getPowerMultiplier() is unchanged (glyphs use it, don't modify it)</li>
 *   <li>getVolatilityTracker().getRemainingBudget() is drained by execution</li>
 *   <li>Spell echo fires AFTER the original — correct visual ordering</li>
 * </ul>
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class HexCastEventHandler extends WorldEventSystem {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static volatile AttributeManager attributeManager;
    private static volatile ConfigManager configManager;

    public HexCastEventHandler(@Nonnull Class eventClass) {
        super(eventClass);
        LOGGER.atInfo().log("[HexCast] Event handler initialized (HexContext unified, true echo)");
    }

    public static void setDependencies(@Nullable AttributeManager attrMgr, @Nullable ConfigManager cfgMgr) {
        attributeManager = attrMgr;
        configManager = cfgMgr;
    }

    @Override
    public void handle(@Nonnull Store store, @Nonnull CommandBuffer buffer, @Nonnull EcsEvent event) {
        if (!(event instanceof HexCastEvent hexCastEvent)) {
            return;
        }

        try {
            HexContext ctx = hexCastEvent.getContext();
            if (ctx == null) return;

            Ref<EntityStore> casterRef = ctx.getCasterRef();
            if (casterRef == null || !casterRef.isValid()) return;

            // Capture base power (unchanged by glyph execution)
            float basePower = ctx.getPowerMultiplier();
            UUID executionId = ctx.getExecutionId();

            // Resolve caster UUID
            UUID casterUuid = null;
            try {
                Object playerRefObj = store.getComponent(casterRef, PlayerRef.getComponentType());
                if (playerRefObj instanceof PlayerRef playerRef) {
                    casterUuid = playerRef.getUuid();
                }
            } catch (Exception e) {
                LOGGER.atFine().log("[HexCast] UUID extraction failed: %s", e.getMessage());
            }
            if (casterUuid == null) return;

            // ── Resolve player's max Volatility stat for across-cast damage scaling ──
            // CastGate sets startingBudget = volMax - cumulativeDecay. The ratio
            // startingBudget/volatilityMax is the damage multiplier the player sees.
            float volatilityMax = 0f;
            int volIndex = StatMapBridge.getHexVolatilityIndex();
            if (volIndex != Integer.MIN_VALUE) {
                Store<EntityStore> typedStore = (Store<EntityStore>) store;
                EntityStatMap statMap = typedStore.getComponent(casterRef, EntityStatMap.getComponentType());
                if (statMap != null) {
                    EntityStatValue volStat = statMap.get(volIndex);
                    if (volStat != null) {
                        volatilityMax = volStat.getMax();
                    }
                }
            }

            // ── Store cast state ──
            HexCastStateStore.CastRecord record = new HexCastStateStore.CastRecord(
                    casterUuid, casterRef, basePower,
                    ctx.getVolatilityTracker(),
                    volatilityMax,
                    System.currentTimeMillis(), System.nanoTime()
            );
            HexCastStateStore.setCurrentCast(record);
            if (executionId != null) {
                HexCastStateStore.putCast(executionId, record);
            }

            LOGGER.atFine().log("[HexCast] Cast captured: caster=%s exec=%s power=%.2f",
                    casterUuid.toString().substring(0, 8),
                    executionId != null ? executionId.toString().substring(0, 8) : "null",
                    basePower);

            Store<EntityStore> typedStore = (Store<EntityStore>) store;
            HexcodeSpellConfig spellCfg = configManager != null
                    ? configManager.getHexcodeSpellConfig() : null;

            // ── Mana cost refund ──
            if (attributeManager != null && spellCfg != null) {
                applyManaCostRefund(casterUuid, casterRef, typedStore, spellCfg);
            }

            // ── Spell echo: roll + immediate re-cast ──
            if (attributeManager != null && spellCfg != null) {
                ComputedStats stats = attributeManager.getStats(casterUuid);
                float echoChance = stats != null ? stats.getSpellEchoChance() : 0f;
                if (echoChance > 0) {
                    CommandBuffer<EntityStore> typedBuffer = (CommandBuffer<EntityStore>) buffer;
                    HexSpellEchoService.tryFireEcho(
                            casterUuid, ctx, typedBuffer, echoChance, spellCfg);
                }
            }

        } catch (Exception e) {
            LOGGER.atWarning().log("[HexCast] Exception in handle(): %s", e.getMessage());
        }
    }

    private void applyManaCostRefund(
            @Nonnull UUID casterUuid,
            @Nonnull Ref<EntityStore> casterRef,
            @Nonnull Store<EntityStore> typedStore,
            @Nonnull HexcodeSpellConfig spellCfg) {

        float maxReduction = spellCfg.getMax_mana_cost_reduction();
        if (maxReduction <= 0) return;

        ComputedStats stats = attributeManager.getStats(casterUuid);
        float costReductionPct = stats != null ? stats.getManaCostReduction() : 0f;
        if (costReductionPct <= 0) return;

        float cappedReduction = Math.min(costReductionPct, maxReduction) / 100f;
        int manaIndex = DefaultEntityStatTypes.getMana();
        EntityStatMap statMap = typedStore.getComponent(casterRef, EntityStatMap.getComponentType());
        if (statMap == null || manaIndex == Integer.MIN_VALUE) return;

        float manaBefore = statMap.get(manaIndex).get();
        final float refundPct = cappedReduction;
        final float manaSnapshot = manaBefore;

        World world = typedStore.getExternalData().getWorld();
        world.execute(() -> {
            if (!casterRef.isValid()) return;
            Store<EntityStore> ws = world.getEntityStore().getStore();
            EntityStatMap postMap = ws.getComponent(casterRef, EntityStatMap.getComponentType());
            if (postMap == null) return;
            float manaAfter = postMap.get(manaIndex).get();
            float manaSpent = manaSnapshot - manaAfter;
            if (manaSpent > 0.01f) {
                float refund = manaSpent * refundPct;
                postMap.addStatValue(manaIndex, refund);
                LOGGER.atFine().log("[HexCast] Mana refund for %s: spent=%.1f × %.0f%% = +%.1f",
                        casterUuid.toString().substring(0, 8),
                        manaSpent, refundPct * 100f, refund);
            }
        });
    }
}
