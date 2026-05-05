package io.github.larsonix.trailoforbis.combat.indicators.color;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.CombatTextEntityUIAnimationEventType;
import com.hypixel.hytale.protocol.CombatTextEntityUIComponentAnimationEvent;
import com.hypixel.hytale.protocol.EntityUIComponent;
import com.hypixel.hytale.protocol.Vector2f;
import com.hypixel.hytale.server.core.modules.entityui.asset.CombatTextUIComponent;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages combat text color profiles and builds template packets on demand.
 *
 * <p>At startup, scans the vanilla EntityUI asset map for the CombatText template
 * to use as a base for colored variants. Templates are built per-hit by cloning
 * the vanilla template and applying profile colors/animations, then sent as a
 * global overwrite at the vanilla index.
 *
 * <p>Previous approach (pre-registering custom templates at indices beyond the
 * vanilla range) caused intermittent client crashes: the inflated {@code maxId}
 * raced with Hytale's asset pipeline during world transitions, causing the
 * client's internal template array to be resized inconsistently.
 */
public class CombatTextTemplateRegistry {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /** Vanilla CombatText template index in the server asset map. */
    private int vanillaCombatTextIndex = -1;

    /** The vanilla template packet used as a base for cloning. */
    @Nullable
    private EntityUIComponent vanillaTemplate;

    /** Profile ID → resolved profile. Used for template building on demand. */
    private final Map<String, CombatTextProfile> profileMap = new HashMap<>();

    /** The vanilla maxId — always use this in UpdateEntityUIComponents to avoid
     *  maxId race with Hytale's asset pipeline during world transitions. */
    private int vanillaMaxId;

    private boolean initialized;

    /**
     * Initializes the registry by scanning the asset map and building custom templates.
     *
     * @param profiles The profiles to register as templates
     * @return true if initialization succeeded, false if CombatText template not found
     */
    public boolean initialize(@Nonnull List<CombatTextProfile> profiles) {
        // Find the vanilla CombatText template in the server-side asset map
        var serverAssetMap = com.hypixel.hytale.server.core.modules.entityui.asset.EntityUIComponent.getAssetMap();

        for (int i = 0; i < serverAssetMap.getNextIndex(); i++) {
            var asset = serverAssetMap.getAsset(i);
            if (asset instanceof CombatTextUIComponent) {
                vanillaCombatTextIndex = i;
                vanillaTemplate = asset.toPacket();
                break;
            }
        }

        if (vanillaCombatTextIndex == -1 || vanillaTemplate == null) {
            LOGGER.atWarning().log("CombatText template not found in EntityUI asset map — colored combat text disabled");
            return false;
        }

        LOGGER.atInfo().log("Found vanilla CombatText template at index %d", vanillaCombatTextIndex);

        // Store vanilla maxId — we MUST use this in all UpdateEntityUIComponents
        // packets. Using a higher maxId (from custom indices) causes a race with
        // Hytale's asset pipeline during world transitions: the client's internal
        // template array can be shrunk by a vanilla update, invalidating custom
        // indices → NullReferenceException crash.
        vanillaMaxId = serverAssetMap.getNextIndex();

        // Store profiles for on-demand template building (no pre-registration
        // at custom indices — all templates are applied at the vanilla index)
        for (CombatTextProfile profile : profiles) {
            profileMap.put(profile.id(), profile);
        }

        initialized = true;

        LOGGER.atInfo().log("Initialized %d combat text profiles (vanilla index=%d, vanillaMaxId=%d)",
            profiles.size(), vanillaCombatTextIndex, vanillaMaxId);

        return true;
    }

    /**
     * Gets the vanilla CombatText template index.
     */
    public int getVanillaCombatTextIndex() {
        return vanillaCombatTextIndex;
    }

    /**
     * Gets the vanilla maxId — always use this in UpdateEntityUIComponents
     * packets to avoid client-side array resize races.
     */
    public int getVanillaMaxId() {
        return vanillaMaxId;
    }

    public boolean isInitialized() {
        return initialized;
    }

    /**
     * Gets a profile by its ID for on-demand template building.
     */
    @Nullable
    public CombatTextProfile getProfile(@Nonnull String profileId) {
        return profileMap.get(profileId);
    }

    // ── Template building ────────────────────────────────────────────────

    /**
     * Builds an EntityUIComponent protocol packet for a profile by cloning
     * the vanilla CombatText template and overriding visual properties.
     * Package-private so CombatTextColorManager can build templates on demand.
     */
    @Nonnull
    EntityUIComponent buildTemplate(@Nonnull CombatTextProfile profile) {
        EntityUIComponent packet = vanillaTemplate.clone();

        // Override visual properties from profile
        packet.combatTextColor = profile.color();
        packet.combatTextFontSize = profile.fontSize();
        packet.combatTextDuration = profile.duration();
        packet.combatTextHitAngleModifierStrength = profile.hitAngleModifierStrength();

        // Override animations if the profile defines any
        if (profile.animations().length > 0) {
            packet.combatTextAnimationEvents = convertAnimations(profile.animations());
        }

        return packet;
    }

    /**
     * Converts our config-friendly CombatTextAnimation records to Hytale protocol events.
     * Package-private and static so CombatTextColorManager can reuse for the fallback path.
     */
    @Nonnull
    static CombatTextEntityUIComponentAnimationEvent[] convertAnimations(@Nonnull CombatTextAnimation[] animations) {
        var events = new CombatTextEntityUIComponentAnimationEvent[animations.length];
        for (int i = 0; i < animations.length; i++) {
            CombatTextAnimation anim = animations[i];
            CombatTextEntityUIAnimationEventType type = switch (anim.type()) {
                case SCALE -> CombatTextEntityUIAnimationEventType.Scale;
                case POSITION -> CombatTextEntityUIAnimationEventType.Position;
                case OPACITY -> CombatTextEntityUIAnimationEventType.Opacity;
            };

            Vector2f posOffset = null;
            if (anim.type() == CombatTextAnimation.AnimationType.POSITION) {
                posOffset = new Vector2f(anim.positionOffsetX(), anim.positionOffsetY());
            }

            events[i] = new CombatTextEntityUIComponentAnimationEvent(
                type,
                anim.startAt(),
                anim.endAt(),
                anim.startScale(),
                anim.endScale(),
                posOffset,
                anim.startOpacity(),
                anim.endOpacity()
            );
        }
        return events;
    }
}
