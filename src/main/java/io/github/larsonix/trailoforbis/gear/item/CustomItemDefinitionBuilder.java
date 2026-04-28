package io.github.larsonix.trailoforbis.gear.item;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.ItemBase;
import com.hypixel.hytale.protocol.ItemTranslationProperties;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemQuality;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction;

import io.github.larsonix.trailoforbis.gear.model.GearRarity;
import io.github.larsonix.trailoforbis.maps.core.RealmMapData;
import io.github.larsonix.trailoforbis.maps.tooltip.RealmMapTooltipBuilder;
import io.github.larsonix.trailoforbis.util.MessageSerializer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Objects;

/**
 * Builds custom ItemBase definitions for non-gear items (realm maps, stones).
 *
 * <p>Creates unique item definitions that can be sent to players via UpdateItems packet.
 * Each custom item gets a unique item ID based on its {@link CustomItemInstanceId},
 * with custom name and tooltip generated from the item's data.
 *
 * <h2>Architecture</h2>
 * <pre>
 * CustomItemData → CustomItemDefinitionBuilder → ItemBase (with unique ID)
 *                                                     ↓
 *                               UpdateItems Packet → Player Client
 * </pre>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * CustomItemDefinitionBuilder builder = new CustomItemDefinitionBuilder();
 *
 * // Build definition for a custom item
 * ItemBase definition = builder.build(stoneItemData);
 *
 * // The definition has unique ID from CustomItemData.getInstanceId()
 * // and custom translationProperties with formatted name/description
 * }</pre>
 *
 * <h2>Thread Safety</h2>
 * <p>This class is thread-safe for concurrent use.
 */
public final class CustomItemDefinitionBuilder {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final ItemDisplayNameService displayNameService;

    /**
     * Creates a new CustomItemDefinitionBuilder.
     *
     * @param displayNameService Service for generating consistent display names
     */
    public CustomItemDefinitionBuilder(@Nonnull ItemDisplayNameService displayNameService) {
        this.displayNameService = Objects.requireNonNull(displayNameService, "displayNameService cannot be null");
    }

    // =========================================================================
    // MAIN BUILD METHODS
    // =========================================================================

    /**
     * Builds a custom ItemBase definition for a non-gear custom item.
     *
     * <p>The returned ItemBase has:
     * <ul>
     *   <li>Unique ID from {@link CustomItemData#getInstanceId()}</li>
     *   <li>Custom name from {@link CustomItemData#getDisplayName()}</li>
     *   <li>Custom description from {@link CustomItemData#getDescription()}</li>
     *   <li>All other properties inherited from the base item</li>
     * </ul>
     *
     * @param customData The custom item data (maps, stones, etc.)
     * @return Custom ItemBase, or null if build fails
     */
    @Nullable
    public ItemBase build(@Nonnull CustomItemData customData) {
        Objects.requireNonNull(customData, "customData cannot be null");

        // Verify customData has an instanceId
        CustomItemInstanceId instanceId = customData.getInstanceId();
        if (instanceId == null) {
            LOGGER.atWarning().log(
                "Cannot build definition for custom item without instanceId: %s",
                customData.getClass().getSimpleName()
            );
            return null;
        }

        try {
            // Get the base item definition from Hytale's asset map
            String baseItemId = customData.getBaseItemId();
            Item baseItem = Item.getAssetMap().getAsset(baseItemId);
            if (baseItem == null || baseItem == Item.UNKNOWN) {
                LOGGER.atWarning().log("Base item not found: %s", baseItemId);
                return null;
            }

            // Clone the base definition (preserves model, texture, icon)
            ItemBase definition = baseItem.toPacket().clone();

            // Clear vanilla stat modifiers - custom items don't have vanilla modifiers
            clearVanillaModifiers(definition);

            // Hide from creative inventory — custom items (maps, stones) should never appear
            definition.categories = null;
            definition.variant = true;

            // NOTE: We deliberately do NOT override playerAnimationsId here.
            // The base item (e.g., Ingredient_Crystal_Blue) has PlayerAnimationsId="Item",
            // which provides proper FirstPerson animations (Idle_FPS, Walk_FPS, etc.) for
            // hand rendering. Overriding to "Empty" caused the hand to be invisible.

            // Add Secondary interaction explicitly so the client sends right-click events.
            // Neither "Item" nor "Empty" UnarmedInteractions define Secondary, so without
            // this explicit addition, right-clicking with stones/maps would not trigger events.
            // "Block_Secondary" uses UseBlock → PlaceBlock chain, allowing the item to
            // interact with blocks (open chests) and trigger PlayerInteractEvent.
            addSecondaryInteraction(definition);

            // Set the custom instance ID (must match ItemStack.itemId for client lookup)
            String customItemId = instanceId.toItemId();
            definition.id = customItemId;

            // Use translation keys (actual text is registered separately via UpdateTranslations)
            // Use compact instance ID for translation keys (without prefix)
            String compactId = instanceId.toCompactString();
            String nameKey = TranslationSyncService.getNameKey(compactId);
            String descKey = TranslationSyncService.getDescriptionKey(compactId);

            // Set translation properties with keys (not JSON)
            definition.translationProperties = new ItemTranslationProperties(nameKey, descKey);

            // Set quality index based on rarity (with robust fallback)
            GearRarity rarity = getRarity(customData);
            String qualityId = rarity.getHytaleQualityId();
            int qualityIndex = ItemQuality.getAssetMap().getIndex(qualityId);
            if (qualityIndex < 0) {
                // Try Common as fallback
                qualityIndex = ItemQuality.getAssetMap().getIndex("Common");
            }
            if (qualityIndex < 0) {
                // Ultimate fallback: use index 0 (should always be valid)
                LOGGER.atWarning().log("ItemQuality not loaded - using index 0 for %s", customItemId);
                qualityIndex = 0;
            }
            definition.qualityIndex = qualityIndex;

            // Set itemEntity for correct drop VFX (glow color)
            // Without this, drops show the base item's glow instead of the rarity's glow
            ItemQuality targetQuality = ItemQuality.getAssetMap().getAsset(qualityIndex);
            if (targetQuality != null && targetQuality.getItemEntityConfig() != null) {
                definition.itemEntity = targetQuality.getItemEntityConfig().toPacket();
            }

            // Set item level (matches gear behavior, ensures field is initialized)
            definition.itemLevel = getItemLevel(customData);

            return definition;
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log(
                "Failed to build item definition for custom item"
            );
            return null;
        }
    }

    // =========================================================================
    // TRANSLATION CONTENT
    // =========================================================================

    /**
     * Builds the translation content (actual text) for a custom item.
     *
     * <p>The returned content should be registered via {@link TranslationSyncService}
     * before sending the item definition to the player.
     *
     * <p>Uses {@link ItemDisplayNameService} for consistent naming across all plugin systems.
     * For stones, uses {@link StoneTooltipBuilder} to generate rich formatted tooltips.
     * For maps, uses {@link MapTooltipBuilder} to generate rich formatted tooltips.
     *
     * @param customData The custom item data
     * @return Translation content, or null if build fails
     */
    @Nullable
    public TranslationContent buildTranslationContent(@Nonnull CustomItemData customData) {
        Objects.requireNonNull(customData, "customData cannot be null");

        try {
            // Stones are now native Hytale items — only maps need custom definitions
            String nameText;
            if (customData instanceof RealmMapData mapData) {
                nameText = displayNameService.getMapDisplayName(mapData);
            } else {
                nameText = customData.getDisplayName();
            }

            // Build rich description based on item type
            String descText;
            if (customData instanceof RealmMapData mapData) {
                RealmMapTooltipBuilder tooltipBuilder = new RealmMapTooltipBuilder();
                Message tooltipMessage = tooltipBuilder.build(mapData);
                descText = MessageSerializer.toFormattedText(tooltipMessage);
            } else {
                descText = customData.getDescription();
            }

            return new TranslationContent(nameText, descText);
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log(
                "Failed to build translation content for custom item"
            );
            return null;
        }
    }

    /**
     * Holds the translation text for a custom item.
     *
     * @param name The item name text
     * @param description The tooltip description text
     */
    public record TranslationContent(
            @Nonnull String name,
            @Nonnull String description
    ) {
        public TranslationContent {
            Objects.requireNonNull(name, "name cannot be null");
            Objects.requireNonNull(description, "description cannot be null");
        }
    }

    // =========================================================================
    // HASH COMPUTATION
    // =========================================================================

    /**
     * Computes a hash for the item definition to detect changes.
     *
     * <p>Used to determine if a player needs an updated definition.
     *
     * @param customData The custom item data
     * @return Hash value
     */
    public int computeDefinitionHash(@Nonnull CustomItemData customData) {
        Objects.requireNonNull(customData, "customData cannot be null");

        // Hash based on name + description + rarity
        // These are the things that affect the visual display
        int result = customData.getDisplayName().hashCode();
        result = 31 * result + customData.getDescription().hashCode();
        result = 31 * result + getRarity(customData).ordinal();
        return result;
    }

    // =========================================================================
    // HELPER METHODS
    // =========================================================================

    /**
     * Clears vanilla stat modifiers from an ItemBase.
     *
     * <p>Custom items use their own descriptions rather than vanilla stat modifiers.
     * For maps and stones (non-weapons), these fields are typically null anyway.
     *
     * <p>IMPORTANT: Do NOT set interactions = null here. The client needs the
     * interactions list for in-hand model rendering. Setting it to null causes
     * IndexOutOfRangeException crashes on the client when the item is held.
     * Vanilla behavior (like treasure map objectives) should be handled via
     * server-side event cancellation instead.
     */
    private void clearVanillaModifiers(@Nonnull ItemBase definition) {
        // Clear weapon modifiers if present (unlikely for maps/stones)
        if (definition.weapon != null) {
            definition.weapon.statModifiers = null;
        }

        // Clear armor modifiers if present (unlikely for maps/stones)
        if (definition.armor != null) {
            definition.armor.statModifiers = null;
            definition.armor.damageResistance = null;
            definition.armor.damageEnhancement = null;
            definition.armor.damageClassEnhancement = null;
        }

        // NOTE: We intentionally keep definition.interactions from the base item.
        // Clearing it causes client crashes (IndexOutOfRangeException).
    }

    /**
     * Adds a Secondary interaction to enable right-click functionality.
     *
     * <p>The base crystal items have no "Interactions" field defined, and neither
     * "Item" nor "Empty" UnarmedInteractions define Secondary. Without explicit
     * Secondary in definition.interactions, the client never sends right-click events.
     *
     * <p>We use "Block_Secondary" RootInteraction which:
     * <ul>
     *   <li>Tries UseBlock first (allows opening chests/containers)</li>
     *   <li>Falls back to PlaceBlock (fires PlayerInteractEvent for our listeners)</li>
     * </ul>
     *
     * @param definition The ItemBase to modify
     */
    private void addSecondaryInteraction(@Nonnull ItemBase definition) {
        // Get the RootInteraction index for "Block_Secondary"
        int blockSecondaryIndex = RootInteraction.getAssetMap().getIndex("Block_Secondary");
        if (blockSecondaryIndex == Integer.MIN_VALUE) {
            LOGGER.atWarning().log(
                "Block_Secondary RootInteraction not found - Secondary interaction unavailable. " +
                "This may occur if assets are not yet loaded."
            );
            return;
        }

        // Ensure interactions map exists and is mutable
        // The base item may have null interactions (crystals) or an immutable map
        if (definition.interactions == null) {
            definition.interactions = new HashMap<>();
        } else if (!(definition.interactions instanceof HashMap)) {
            // Clone to mutable map if it was immutable
            definition.interactions = new HashMap<>(definition.interactions);
        }

        // Add Secondary interaction (don't override if base item already defined it)
        definition.interactions.putIfAbsent(InteractionType.Secondary, blockSecondaryIndex);

        LOGGER.atFine().log("Added Secondary interaction (index=%d) to custom item", blockSecondaryIndex);
    }

    /**
     * Gets the rarity from a custom item.
     *
     * <p>Tries to extract rarity from common interfaces, falling back to COMMON.
     */
    @Nonnull
    private GearRarity getRarity(@Nonnull CustomItemData customData) {
        // Check if it's RealmMapData (has rarity() directly)
        if (customData instanceof io.github.larsonix.trailoforbis.maps.core.RealmMapData mapData) {
            return mapData.rarity();
        }

        // Fallback
        return GearRarity.COMMON;
    }

    /**
     * Gets an appropriate item level for a custom item.
     *
     * <p>For maps, uses the map's level. For stones, derives from rarity.
     * This ensures the itemLevel field is always initialized, matching gear behavior.
     *
     * @param customData The custom item data
     * @return Item level (1+)
     */
    private int getItemLevel(@Nonnull CustomItemData customData) {
        // Check if it's RealmMapData (has level() directly)
        if (customData instanceof io.github.larsonix.trailoforbis.maps.core.RealmMapData mapData) {
            return Math.max(1, mapData.level());
        }

        // Stones use rarity-based level: Common=1, Uncommon=2, ..., Mythic=6
        return getRarity(customData).ordinal() + 1;
    }
}
