package io.github.larsonix.trailoforbis.combat.context;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.attributes.ComputedStats;
import io.github.larsonix.trailoforbis.elemental.ElementalStats;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Cached attacker-side data for the damage pipeline.
 *
 * <p>For AoE spells (Hexcode Combust, Gust, etc.), the SAME attacker hits
 * many targets in a single tick. Without caching, each target resolves
 * the attacker's stats, UUID, level, and elemental stats 6-8 times across
 * different processors. This record caches all attacker data on the first
 * hit and reuses it for subsequent targets.
 *
 * <p>Cache key: {@code attackerPlayerUuid} (null for mob attackers — mob AoE
 * doesn't benefit from caching since mob stats are per-entity, not shared).
 *
 * <p>Thread safety: only used on the world thread (ECS damage systems are
 * single-threaded), so no synchronization needed.
 */
public record AttackerContext(
    @Nullable Ref<EntityStore> attackerRef,
    /** Player UUID of the attacker (null for mob attackers). Used as cache key. */
    @Nullable UUID attackerPlayerUuid,
    @Nullable ComputedStats attackerStats,
    @Nullable ElementalStats attackerElemental,
    int attackerLevel,
    boolean hasRpgWeapon,
    float rpgWeaponDamage,
    @Nullable String weaponItemId,
    /** Stats version at cache time — if AttributeManager's version differs, cache is stale. */
    long statsVersion
) {}
