package io.github.larsonix.trailoforbis.combat.indicators.color;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.CombatTextEntityUIAnimationEventType;
import com.hypixel.hytale.protocol.CombatTextEntityUIComponentAnimationEvent;
import com.hypixel.hytale.protocol.EntityUIComponent;
import com.hypixel.hytale.protocol.UpdateType;
import com.hypixel.hytale.protocol.Vector2f;
import com.hypixel.hytale.protocol.packets.assets.UpdateEntityUIComponents;
import com.hypixel.hytale.server.core.modules.entityui.asset.CombatTextUIComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Manages the registration and distribution of custom combat text templates.
 *
 * <p>At startup, scans the vanilla EntityUI asset map for the CombatText template,
 * then creates colored variants at new client-side indices. These are sent to each
 * player via {@link UpdateEntityUIComponents} on connect.
 *
 * <p>The templates only exist on the client — the server-side asset map is not modified.
 */
public class CombatTextTemplateRegistry {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /** Vanilla CombatText template index in the server asset map. */
    private int vanillaCombatTextIndex = -1;

    /** The vanilla template packet used as a base for cloning. */
    @Nullable
    private EntityUIComponent vanillaTemplate;

    /** Profile ID → registered template index. */
    private final Map<String, Integer> profileIndexMap = new HashMap<>();

    /** Template index → protocol packet. All custom templates keyed by their client-side index. */
    private final Map<Integer, EntityUIComponent> customTemplates = new HashMap<>();

    /** Set of all our custom template indices (for detecting already-swapped entities). */
    private final Set<Integer> registeredIndices = new HashSet<>();

    /** The maxId to use when sending UpdateEntityUIComponents. */
    private int maxId;

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

        // Assign client-side template indices starting after the asset map
        int nextIndex = serverAssetMap.getNextIndex();

        for (CombatTextProfile profile : profiles) {
            int templateIndex = nextIndex++;
            EntityUIComponent packet = buildTemplate(profile);
            customTemplates.put(templateIndex, packet);
            profileIndexMap.put(profile.id(), templateIndex);
            registeredIndices.add(templateIndex);
        }

        maxId = nextIndex;
        initialized = true;

        LOGGER.atInfo().log("Registered %d colored combat text templates (indices %d–%d)",
            customTemplates.size(),
            serverAssetMap.getNextIndex(),
            maxId - 1);

        return true;
    }

    /**
     * Sends all custom templates to a player's client.
     *
     * <p>Must be called after the player has connected and is ready to receive packets.
     * Uses {@code writeNoCache} for immediate delivery.
     *
     * @param player The player to sync templates to
     */
    public void syncToPlayer(@Nonnull PlayerRef player) {
        if (!initialized || customTemplates.isEmpty()) return;

        player.getPacketHandler().writeNoCache(
            new UpdateEntityUIComponents(UpdateType.AddOrUpdate, maxId, customTemplates)
        );

        LOGGER.atFine().log("Synced %d combat text templates to player %s",
            customTemplates.size(), player.getUsername());
    }

    /**
     * Gets the template index for a profile.
     *
     * @param profileId The profile ID (e.g., "fire_normal")
     * @return Template index, or -1 if not registered
     */
    public int getTemplateIndex(@Nonnull String profileId) {
        return profileIndexMap.getOrDefault(profileId, -1);
    }

    public int getVanillaCombatTextIndex() {
        return vanillaCombatTextIndex;
    }

    /**
     * Returns all registered custom template indices.
     * Used to detect entities that already had their template swapped.
     */
    @Nonnull
    public Set<Integer> getAllRegisteredIndices() {
        return registeredIndices;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public int getMaxId() {
        return maxId;
    }

    // ── Template building ────────────────────────────────────────────────

    /**
     * Builds an EntityUIComponent protocol packet for a profile by cloning
     * the vanilla CombatText template and overriding visual properties.
     */
    @Nonnull
    private EntityUIComponent buildTemplate(@Nonnull CombatTextProfile profile) {
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
