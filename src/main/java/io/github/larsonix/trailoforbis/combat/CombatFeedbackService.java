package io.github.larsonix.trailoforbis.combat;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.elemental.ElementType;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.EnumMap;
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
    private static final String PARTICLE_IMPACT_BLADE = "Combat/Sword/Basic/Impact_Blade_01";
    private static final String PARTICLE_IMPACT_CRITICAL = "Combat/Impact/Critical/Impact_Critical";
    private static final String PARTICLE_IMPACT_FIRE = "Combat/Impact/Misc/Fire/Impact_Fire";
    private static final String PARTICLE_IMPACT_ICE = "Combat/Impact/Misc/Ice/Impact_Ice";
    private static final String PARTICLE_IMPACT_VOID = "Combat/Impact/Misc/Void/VoidImpact";
    private static final String PARTICLE_IMPACT_LIGHTNING = "Weapon/Lightning_Sword/Lightning_Sword";
    private static final String PARTICLE_IMPACT_EARTH = "Block/Stone/Block_Hit_Stone";
    private static final String PARTICLE_IMPACT_WIND = "Combat/Impact/Misc/Feathers_Black/Impact_Feathers_Black";
    private static final String PARTICLE_SHIELD_BASH = "Combat/Shield/Bash/Impact_Shield_Bash";
    private static final String PARTICLE_SWORD_BASH = "Combat/Sword/Bash/Impact_Sword_Bash";
    private static final String PARTICLE_HEAL = "Status_Effect/Heal/Effect_Heal";

    // ── Sound IDs ────────────────────────────────────────────────────
    private static final String SOUND_SHIELD_BLOCK = "SFX_Metal_Hit";

    // ── Rate Limiting ────────────────────────────────────────────────
    // Uses Ref identity (hashCode) as key to avoid UUID allocation per hit.
    // Entries self-evict: on each check, stale entries older than 10× cooldown are purged.
    private final ConcurrentHashMap<Integer, Long> lastParticleTime = new ConcurrentHashMap<>();
    private static final long PARTICLE_COOLDOWN_MS = 150L;
    private static final long EVICTION_THRESHOLD_MS = PARTICLE_COOLDOWN_MS * 10;
    private long lastEvictionTime = 0L;

    // ── Cached Sound Indexes ─────────────────────────────────────────
    private int shieldBlockSoundIndex;

    // ── Elemental Particle Mapping ───────────────────────────────────
    private static final EnumMap<ElementType, String> ELEMENT_PARTICLES = new EnumMap<>(ElementType.class);
    static {
        ELEMENT_PARTICLES.put(ElementType.FIRE, PARTICLE_IMPACT_FIRE);
        ELEMENT_PARTICLES.put(ElementType.WATER, PARTICLE_IMPACT_ICE);
        ELEMENT_PARTICLES.put(ElementType.VOID, PARTICLE_IMPACT_VOID);
        ELEMENT_PARTICLES.put(ElementType.LIGHTNING, PARTICLE_IMPACT_LIGHTNING);
        ELEMENT_PARTICLES.put(ElementType.EARTH, PARTICLE_IMPACT_EARTH);
        ELEMENT_PARTICLES.put(ElementType.WIND, PARTICLE_IMPACT_WIND);
    }

    /**
     * Initializes cached sound indexes. Must be called after asset maps are loaded.
     */
    public void init() {
        shieldBlockSoundIndex = resolveSoundIndex(SOUND_SHIELD_BLOCK, "shield block");
        LOGGER.atInfo().log("CombatFeedbackService initialized (shield=%d)",
                shieldBlockSoundIndex);
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

        if (rpgDamage <= 0) {
            return;
        }

        // Get defender position
        Vector3d position = getEntityPosition(store, defenderRef);
        if (position == null) {
            return;
        }

        // Rate-limit per defender entity
        if (!canSpawnParticle(defenderRef.hashCode())) {
            return;
        }

        try {
            // 1. Basic blade impact (restores vanilla on-hit feedback)
            spawnParticle(PARTICLE_IMPACT_BLADE, position, store);

            // 2. Critical hit overlay
            if (breakdown.wasCritical()) {
                spawnParticle(PARTICLE_IMPACT_CRITICAL, position, store);
            }

            // 3. Elemental impact for the dominant element
            ElementType dominantElement = findDominantElement(breakdown.elementalDamage());
            if (dominantElement != null) {
                String elementParticle = ELEMENT_PARTICLES.get(dominantElement);
                if (elementParticle != null) {
                    spawnParticle(elementParticle, position, store);
                }
            }
        } catch (Exception e) {
            LOGGER.atFine().log("Failed to spawn damage particles: %s", e.getMessage());
        }
    }

    /**
     * Called when damage is avoided (dodged, blocked, parried, missed).
     *
     * <p>Spawns avoidance-specific particles and sounds:
     * <ul>
     *   <li>BLOCKED: shield bash particles + shield block sound</li>
     *   <li>PARRIED: sword bash particles (parry reflect visual)</li>
     *   <li>DODGED: no particles (dodge is absence of contact)</li>
     *   <li>MISSED: no particles (miss is absence of contact)</li>
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

        // Dodge and miss are absence of contact — no visual feedback needed
        if (reason == DamageBreakdown.AvoidanceReason.DODGED
                || reason == DamageBreakdown.AvoidanceReason.MISSED) {
            return;
        }

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
                    spawnParticle(PARTICLE_SHIELD_BASH, position, store);
                    // 3D sound at defender position so nearby players hear it
                    if (shieldBlockSoundIndex != 0) {
                        SoundUtil.playSoundEvent3d(
                                shieldBlockSoundIndex, SoundCategory.SFX,
                                position, store);
                    }
                }
                case PARRIED -> {
                    spawnParticle(PARTICLE_SWORD_BASH, position, store);
                }
                default -> {} // DODGED, MISSED handled above
            }
        } catch (Exception e) {
            LOGGER.atFine().log("Failed to spawn avoidance particles: %s", e.getMessage());
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

        if (attackerRef == null || !attackerRef.isValid()) {
            return;
        }

        Vector3d position = getEntityPosition(store, attackerRef);
        if (position == null) {
            return;
        }

        if (!canSpawnParticle(attackerRef.hashCode())) {
            return;
        }

        try {
            spawnParticle(PARTICLE_HEAL, position, store);
        } catch (Exception e) {
            LOGGER.atFine().log("Failed to spawn recovery particles: %s", e.getMessage());
        }
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

    private void spawnParticle(@Nonnull String particleId, @Nonnull Vector3d position,
                                @Nonnull Store<EntityStore> store) {
        ParticleUtil.spawnParticleEffect(particleId, position, store);
    }

    /**
     * @return The element with the highest damage, or null if no elemental damage
     */
    @Nullable
    private ElementType findDominantElement(@Nonnull EnumMap<ElementType, Float> elementalDamage) {
        ElementType dominant = null;
        float maxDamage = 0f;

        for (var entry : elementalDamage.entrySet()) {
            float dmg = entry.getValue();
            if (dmg > maxDamage) {
                maxDamage = dmg;
                dominant = entry.getKey();
            }
        }

        return dominant;
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
