package io.github.larsonix.trailoforbis.combat;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Provides combat particle and sound feedback to restore the on-hit effects that
 * vanilla suppresses (because RPGDamageSystem sets damage.amount = 0) and to add
 * RPG-specific visual feedback for crits, elemental hits, blocks, parries, and recovery.
 *
 * <p>Vanilla weapon DamageEffects (impact particles, impact sounds) are suppressed
 * because our RPG system zeroes out native damage. Swing animation, swing sound, and
 * swing trail particles still fire from the interaction layer. This service restores
 * the missing ON-HIT impact effects.
 *
 * <p>All particle/sound calls are fire-and-forget; failures are logged and swallowed
 * to never interfere with the damage pipeline.
 */
public final class CombatFeedbackService {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    // ── Particle IDs ─────────────────────────────────────────────────
    // NOTE: Most combat particles are defined in Hytale's decompiled Assets.zip but are
    // NOT shipped in the live client build. Referencing them causes "Could not find particle
    // system settings" warnings on every hit (903+ per session). Only particles confirmed
    // to exist in the live client are kept here.
    //
    // Disabled particles (exist in Assets.zip, not in live client):
    //   Combat/Sword/Basic/Impact_Blade_01, Combat/Impact/Critical/Impact_Critical,
    //   Combat/Impact/Misc/Fire/Impact_Fire, Combat/Impact/Misc/Ice/Impact_Ice,
    //   Combat/Impact/Misc/Void/VoidImpact, Status_Effect/Heal/Effect_Heal,
    //   Weapon/Lightning_Sword/Lightning_Sword,
    //   Combat/Shield/Bash/Impact_Shield_Bash, Combat/Sword/Bash/Impact_Sword_Bash

    // ── Sound IDs ────────────────────────────────────────────────────
    private static final String SOUND_SHIELD_BLOCK = "SFX_Metal_Hit";
    private static final String SOUND_PARRY = "SFX_Sword_T1_Block_Local";
    private static final String SOUND_DODGE = "SFX_Player_Roll";
    private static final String SOUND_MISS = "SFX_Light_Melee_T1_Swing";

    // ── Rate Limiting ────────────────────────────────────────────────
    // Uses Ref identity (hashCode) as key to avoid UUID allocation per hit.
    // Entries self-evict: on each check, stale entries older than 10× cooldown are purged.
    private final ConcurrentHashMap<Integer, Long> lastParticleTime = new ConcurrentHashMap<>();
    private static final long PARTICLE_COOLDOWN_MS = 150L;
    private static final long EVICTION_THRESHOLD_MS = PARTICLE_COOLDOWN_MS * 10;
    private long lastEvictionTime = 0L;

    // ── Cached Sound Indexes ─────────────────────────────────────────
    private int shieldBlockSoundIndex;
    private int parrySoundIndex;
    private int dodgeSoundIndex;
    private int missSoundIndex;

    // ── Elemental Particle Mapping ───────────────────────────────────
    // Disabled — all elemental impact particles are missing from the live client.
    // Re-enable when Hytale ships these particles or when we create custom ones.
    // private static final EnumMap<ElementType, String> ELEMENT_PARTICLES = new EnumMap<>(ElementType.class);

    /**
     * Initializes cached sound indexes. Must be called after asset maps are loaded.
     */
    public void init() {
        shieldBlockSoundIndex = resolveSoundIndex(SOUND_SHIELD_BLOCK, "shield block");
        parrySoundIndex = resolveSoundIndex(SOUND_PARRY, "parry");
        dodgeSoundIndex = resolveSoundIndex(SOUND_DODGE, "dodge");
        missSoundIndex = resolveSoundIndex(SOUND_MISS, "miss");
        LOGGER.atInfo().log("CombatFeedbackService initialized (block=%d, parry=%d, dodge=%d, miss=%d)",
                shieldBlockSoundIndex, parrySoundIndex, dodgeSoundIndex, missSoundIndex);
    }

    // ═══════════════════════════════════════════════════════════════
    // PUBLIC API
    // ═══════════════════════════════════════════════════════════════

    /**
     * Called after successful RPG damage was applied to the defender.
     *
     * <p>Spawns impact particles at the defender's position:
     * <ol>
     *   <li>Basic blade impact (restoring what vanilla lost)</li>
     *   <li>Critical hit overlay (if crit)</li>
     *   <li>Elemental impact for the dominant element (if any elemental damage)</li>
     * </ol>
     *
     * @param store The entity store
     * @param defenderRef The entity that was hit
     * @param breakdown The damage breakdown containing crit/element info
     * @param rpgDamage The final RPG damage amount
     */
    public void onDamageDealt(
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> defenderRef,
            @Nonnull DamageBreakdown breakdown,
            float rpgDamage) {
        // All combat impact particles (Impact_Blade_01, Impact_Critical, elemental impacts,
        // Effect_Heal) are defined in Hytale's decompiled Assets.zip but NOT shipped in the
        // live client build. Re-enable when Hytale ships these particles or when we create
        // custom replacements.
    }

    /**
     * Called when damage is avoided (dodged, blocked, parried, missed).
     *
     * <p>Plays avoidance-specific sounds:
     * <ul>
     *   <li>BLOCKED: shield impact sound</li>
     *   <li>PARRIED: sword block sound</li>
     *   <li>DODGED: dodge/roll sound</li>
     *   <li>MISSED: swing whiff sound</li>
     * </ul>
     *
     * @param store The entity store
     * @param defenderRef The entity that avoided the damage
     * @param reason The avoidance reason
     */
    public void onDamageAvoided(
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> defenderRef,
            @Nonnull DamageBreakdown.AvoidanceReason reason) {

        Vector3d position = getEntityPosition(store, defenderRef);
        if (position == null) {
            return;
        }

        if (!canSpawnParticle(defenderRef.hashCode())) {
            return;
        }

        try {
            switch (reason) {
                case BLOCKED -> {
                    playSound(shieldBlockSoundIndex, position, store);
                }
                case PARRIED -> {
                    playSound(parrySoundIndex, position, store);
                }
                case DODGED -> {
                    playSound(dodgeSoundIndex, position, store);
                }
                case MISSED -> {
                    playSound(missSoundIndex, position, store);
                }
            }
        } catch (Exception e) {
            LOGGER.atFine().log("Failed to spawn avoidance feedback: %s", e.getMessage());
        }
    }

    /**
     * Called when life leech/steal heals the attacker.
     *
     * <p>Spawns heal particles at the attacker's position. No sound — visual
     * feedback only to avoid intrusive audio on rapid lifesteal procs.
     *
     * @param store The entity store
     * @param attackerRef The entity that healed
     */
    public void onRecovery(
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> attackerRef) {
        // Heal particle disabled — Status_Effect/Heal/Effect_Heal is missing
        // from Hytale's live client.
    }

    // ═══════════════════════════════════════════════════════════════
    // RATE LIMITING
    // ═══════════════════════════════════════════════════════════════

    /**
     * Rate-limits particle spawning per entity (max 1 set per PARTICLE_COOLDOWN_MS).
     * Uses int key (ref hashCode) to avoid UUID allocation. Periodically evicts stale entries
     * to prevent unbounded map growth on long-running servers.
     */
    private boolean canSpawnParticle(int entityKey) {
        long now = System.currentTimeMillis();

        // Periodic eviction — runs at most once per EVICTION_THRESHOLD_MS
        if (now - lastEvictionTime > EVICTION_THRESHOLD_MS) {
            lastEvictionTime = now;
            lastParticleTime.entrySet().removeIf(e -> now - e.getValue() > EVICTION_THRESHOLD_MS);
        }

        Long last = lastParticleTime.get(entityKey);
        if (last != null && now - last < PARTICLE_COOLDOWN_MS) {
            return false;
        }
        lastParticleTime.put(entityKey, now);
        return true;
    }

    // ═══════════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════════

    /**
     * @return The entity's position, or null if unavailable
     */
    @Nullable
    private Vector3d getEntityPosition(@Nonnull Store<EntityStore> store,
                                        @Nonnull Ref<EntityStore> entityRef) {
        if (!entityRef.isValid()) {
            return null;
        }
        TransformComponent transform = store.getComponent(entityRef, TransformComponent.getComponentType());
        if (transform == null) {
            return null;
        }
        // Return a copy to avoid mutating the entity's position
        Vector3d pos = transform.getPosition();
        return pos != null ? new Vector3d(pos.x, pos.y, pos.z) : null;
    }

    private void playSound(int soundIndex, @Nonnull Vector3d position,
                           @Nonnull Store<EntityStore> store) {
        if (soundIndex != 0) {
            SoundUtil.playSoundEvent3d(soundIndex, SoundCategory.SFX, position, store);
        }
    }

    private static int resolveSoundIndex(@Nonnull String soundId, @Nonnull String description) {
        int index = SoundEvent.getAssetMap().getIndex(soundId);
        if (index == 0) {
            LOGGER.atWarning().log("Sound '%s' not found for %s — sound will be skipped", soundId, description);
        } else {
            LOGGER.atFine().log("Resolved %s sound: %s -> index %d", description, soundId, index);
        }
        return index;
    }
}
