package io.github.larsonix.trailoforbis.gear.item;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.ItemBase;
import com.hypixel.hytale.protocol.ItemUtility;
import com.hypixel.hytale.protocol.ItemTranslationProperties;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemQuality;
import com.hypixel.hytale.server.core.inventory.ItemStack;

import io.github.larsonix.trailoforbis.compat.HexcodeCompat;
import io.github.larsonix.trailoforbis.gear.config.ModifierConfig;
import io.github.larsonix.trailoforbis.gear.model.WeaponType;
import io.github.larsonix.trailoforbis.gear.model.GearData;
import io.github.larsonix.trailoforbis.gear.tooltip.ItemNameFormatter;
import io.github.larsonix.trailoforbis.gear.tooltip.RichTooltipFormatter;
import io.github.larsonix.trailoforbis.gear.util.GearUtils;
import io.github.larsonix.trailoforbis.util.MessageSerializer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;
import java.util.UUID;

/**
 * Builds custom ItemBase definitions for gear items.
 *
 * <p>Creates unique item definitions that can be sent to players via UpdateItems packet.
 * Each gear instance gets a unique item ID based on its {@link GearData#instanceId()},
 * with custom name and tooltip generated from the gear's attributes.
 *
 * <h2>Architecture</h2>
 * <pre>
 * ItemStack + GearData → ItemDefinitionBuilder → ItemBase (with unique ID)
 *                                                    ↓
 *                              UpdateItems Packet → Player Client
 * </pre>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * ItemDefinitionBuilder builder = new ItemDefinitionBuilder(modifierConfig, tooltipFormatter, nameFormatter);
 *
 * // Build definition for a gear item
 * ItemBase definition = builder.build(itemStack, gearData, playerId);
 *
 * // The definition has unique ID from gearData.getItemId()
 * // and custom translationProperties with formatted name/description
 * }</pre>
 *
 * <h2>Thread Safety</h2>
 * <p>This class is thread-safe for concurrent use.
 */
public final class ItemDefinitionBuilder {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final ModifierConfig modifierConfig;
    private final RichTooltipFormatter tooltipFormatter;
    private final ItemNameFormatter nameFormatter;
    private final ItemDisplayNameService displayNameService;

    /**
     * Creates an ItemDefinitionBuilder with the required dependencies.
     *
     * @param modifierConfig Configuration for modifier display names
     * @param tooltipFormatter Formatter for rich tooltip descriptions
     * @param nameFormatter Formatter for styled item names
     * @param displayNameService Service for generating consistent display names
     */
    public ItemDefinitionBuilder(
            @Nonnull ModifierConfig modifierConfig,
            @Nonnull RichTooltipFormatter tooltipFormatter,
            @Nonnull ItemNameFormatter nameFormatter,
            @Nonnull ItemDisplayNameService displayNameService) {
        this.modifierConfig = Objects.requireNonNull(modifierConfig, "modifierConfig cannot be null");
        this.tooltipFormatter = Objects.requireNonNull(tooltipFormatter, "tooltipFormatter cannot be null");
        this.nameFormatter = Objects.requireNonNull(nameFormatter, "nameFormatter cannot be null");
        this.displayNameService = Objects.requireNonNull(displayNameService, "displayNameService cannot be null");
    }

    // =========================================================================
    // MAIN BUILD METHODS
    // =========================================================================

    /**
     * Builds a custom ItemBase definition for a gear item.
     *
     * <p>The returned ItemBase has:
     * <ul>
     *   <li>Unique ID from {@link GearData#getItemId()}</li>
     *   <li>Custom name from {@link ItemNameFormatter}</li>
     *   <li>Custom description from {@link RichTooltipFormatter}</li>
     *   <li>All other properties inherited from the base item</li>
     * </ul>
     *
     * @param itemStack The item stack (for base item properties)
     * @param gearData The gear data (must have instanceId)
     * @param playerId The player viewing the item (for requirement checks)
     * @return Custom ItemBase, or null if build fails
     */
    @Nullable
    public ItemBase build(
            @Nonnull ItemStack itemStack,
            @Nonnull GearData gearData,
            @Nullable UUID playerId) {
        Objects.requireNonNull(itemStack, "itemStack cannot be null");
        Objects.requireNonNull(gearData, "gearData cannot be null");

        // Verify gearData has an instanceId
        if (!gearData.hasInstanceId()) {
            LOGGER.atWarning().log(
                "Cannot build definition for gear without instanceId: %s",
                itemStack.getItemId()
            );
            return null;
        }

        try {
            // Get the base item definition (uses stored base item ID, not custom ID)
            Item baseItem = GearUtils.getBaseItem(itemStack);
            if (baseItem == null || baseItem == Item.UNKNOWN) {
                LOGGER.atWarning().log("Base item not found for: %s", itemStack.getItemId());
                return null;
            }

            // For magic weapons (staffs, wands, spellbooks), use the REGISTERED custom
            // Item as the definition source. The registered Item already has the correct
            // weapon/utility/interactions/animations from injectHexcodeIfApplicable().
            // This ensures the client definition exactly mirrors the server state —
            // e.g., weapon=null for spellbooks, utility.usable=true, hex interactions.
            Item definitionSource = baseItem;
            if (HexcodeCompat.isLoaded() && isMagicWeapon(gearData.baseItemId())) {
                String customItemId = gearData.getItemId();
                if (customItemId != null) {
                    Item registered = Item.getAssetMap().getAsset(customItemId);
                    if (registered != null && registered != Item.UNKNOWN) {
                        definitionSource = registered;
                    }
                }
            }

            // Clone the definition source (preserves model, texture, icon)
            ItemBase definition = definitionSource.toPacket().clone();

            // Override ResourceTypes with the REGISTERED custom item's types.
            // The definition source is the BASE item whose ResourceTypes are the
            // Armory's originals. The registered custom item has our RPG_Reskin_*
            // type injected. The client needs the custom item's ResourceTypes to
            // validate workbench recipe matching.
            String customItemId = gearData.getItemId();
            if (customItemId != null) {
                Item registeredCustom = Item.getAssetMap().getAsset(customItemId);
                if (registeredCustom != null && registeredCustom != Item.UNKNOWN) {
                    com.hypixel.hytale.protocol.ItemResourceType[] customRTs = registeredCustom.getResourceTypes();
                    definition.resourceTypes = customRTs;
                }
            }

            // Set playerAnimationsId. For hex magic weapons, use Hexcode's animation set;
            // for all other items, use the base item's animation.
            String animationsId = resolveAnimationsId(baseItem, gearData);
            definition.playerAnimationsId = animationsId;

            // Clear vanilla stat modifiers - we use our own RPG modifiers in the description
            clearVanillaModifiers(definition);

            // Strip ONLY the problematic OpenItemStackContainer interaction.
            // Base items like TheArmory's Dragon Cult chestplates define
            // "OpenItemStackContainer" on Secondary — but our rpg_gear_* items
            // don't have a server-side ItemStackContainerConfig (capacity defaults
            // to 0), so the interaction crashes with "Capacity must be > 0".
            // DO NOT null all interactions — that removes attack/block/use actions,
            // making the item completely unusable in the active hand.
            stripContainerInteraction(definition);

            // Override interactions for hex magic weapons — the server-side Item was already
            // modified by ItemRegistryService.injectHexcodeIfApplicable(), but the client
            // definition is built from the BASE item and needs the same override.
            overrideHexInteractionsIfApplicable(definition, gearData);

            // Override utility for hex items — reads from registered custom Item (has hex utility)
            overrideHexUtilityIfApplicable(definition, gearData);

            // Diagnostic: log EVERY field that could affect rendering
            if (HexcodeCompat.isLoaded() && isMagicWeapon(gearData.baseItemId())) {
                WeaponType debugWt = WeaponType.fromItemIdOrUnknown(gearData.baseItemId());

                // Log reference Hexcode items for comparison
                Item hexBookItem = Item.getAssetMap().getAsset("Hex_Book");
                ItemBase hexBookDef = hexBookItem != null ? hexBookItem.toPacket() : null;

                // Also log a Hexcode staff for staff/wand comparison
                Item hexStaffItem = Item.getAssetMap().getAsset("Hexstaff_Basic_Crude");
                if (hexStaffItem == null) hexStaffItem = Item.getAssetMap().getAsset("Hexstaff_Basic_Copper");
                ItemBase hexStaffDef = hexStaffItem != null ? hexStaffItem.toPacket() : null;

                LOGGER.atInfo().log("[HexDef] RPG: %s (base: %s, type: %s): "
                        + "weapon=%s, tool=%s, armor=%s, utility=%s, "
                        + "usable=%s, compatible=%s, anim=%s, model=%s, "
                        + "interactions=%s, definitionSource=%s",
                        gearData.getItemId(), gearData.baseItemId(), debugWt,
                        definition.weapon != null ? "PRESENT" : "null",
                        definition.tool != null ? "PRESENT" : "null",
                        definition.armor != null ? "PRESENT" : "null",
                        definition.utility != null ? "PRESENT" : "null",
                        definition.utility != null ? definition.utility.usable : "N/A",
                        definition.utility != null ? definition.utility.compatible : "N/A",
                        definition.playerAnimationsId,
                        definition.model,
                        definition.interactions != null ? definition.interactions.size() + " keys" : "null",
                        definitionSource == baseItem ? "baseItem" : "registeredItem");

                if (hexBookDef != null) {
                    LOGGER.atInfo().log("[HexDef] REF Hex_Book: weapon=%s, tool=%s, armor=%s, utility=%s, "
                            + "usable=%s, compatible=%s, anim=%s, model=%s, interactions=%s",
                            hexBookDef.weapon != null ? "PRESENT" : "null",
                            hexBookDef.tool != null ? "PRESENT" : "null",
                            hexBookDef.armor != null ? "PRESENT" : "null",
                            hexBookDef.utility != null ? "PRESENT" : "null",
                            hexBookDef.utility != null ? hexBookDef.utility.usable : "N/A",
                            hexBookDef.utility != null ? hexBookDef.utility.compatible : "N/A",
                            hexBookDef.playerAnimationsId,
                            hexBookDef.model,
                            hexBookDef.interactions != null ? hexBookDef.interactions.size() + " keys" : "null");
                }
                if (hexStaffDef != null) {
                    LOGGER.atInfo().log("[HexDef] REF HexStaff: weapon=%s, tool=%s, armor=%s, utility=%s, "
                            + "usable=%s, compatible=%s, anim=%s, model=%s, interactions=%s",
                            hexStaffDef.weapon != null ? "PRESENT" : "null",
                            hexStaffDef.tool != null ? "PRESENT" : "null",
                            hexStaffDef.armor != null ? "PRESENT" : "null",
                            hexStaffDef.utility != null ? "PRESENT" : "null",
                            hexStaffDef.utility != null ? hexStaffDef.utility.usable : "N/A",
                            hexStaffDef.utility != null ? hexStaffDef.utility.compatible : "N/A",
                            hexStaffDef.playerAnimationsId,
                            hexStaffDef.model,
                            hexStaffDef.interactions != null ? hexStaffDef.interactions.size() + " keys" : "null");
                }
            }

            // Hide from creative inventory — custom gear items should never appear in the library
            // Exception: preserve Hexcode-specific categories so items appear in hex UI sections
            definition.categories = resolveCategories(baseItem);
            definition.variant = true;

            // Set the custom instance ID (must match ItemStack.itemId for client lookup)
            String itemId = gearData.getItemId();
            definition.id = itemId;

            // Use translation keys (actual text is registered separately via UpdateTranslations)
            // Use compact instance ID for translation keys (without prefix)
            String compactId = gearData.instanceId().toCompactString();
            String nameKey = TranslationSyncService.getNameKey(compactId);
            String descKey = TranslationSyncService.getDescriptionKey(compactId);

            // Set translation properties with keys (not JSON)
            definition.translationProperties = new ItemTranslationProperties(nameKey, descKey);

            // Set quality index by looking up the actual Hytale quality from the asset map
            // Using ordinal() doesn't work because Hytale's quality indices don't match enum ordinals
            String qualityId = gearData.rarity().getHytaleQualityId();
            int qualityIndex = ItemQuality.getAssetMap().getIndex(qualityId);
            if (qualityIndex >= 0) {
                definition.qualityIndex = qualityIndex;
                // Set itemEntity for correct drop VFX (glow color)
                // Without this, drops show the base item's glow instead of the rarity's glow
                ItemQuality targetQuality = ItemQuality.getAssetMap().getAsset(qualityIndex);
                if (targetQuality != null && targetQuality.getItemEntityConfig() != null) {
                    definition.itemEntity = targetQuality.getItemEntityConfig().toPacket();
                }
            } else {
                // Fallback to Common if quality not found
                definition.qualityIndex = ItemQuality.getAssetMap().getIndex("Common");
                LOGGER.atWarning().log("Quality '%s' not found in Hytale asset map, falling back to Common", qualityId);
            }

            // Set item level
            definition.itemLevel = gearData.level();

            return definition;
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log(
                "Failed to build item definition for %s",
                itemStack.getItemId()
            );
            return null;
        }
    }

    /**
     * Builds a custom ItemBase without player context.
     *
     * <p>Requirements in the tooltip will be shown in neutral colors.
     *
     * @param itemStack The item stack
     * @param gearData The gear data (must have instanceId)
     * @return Custom ItemBase, or null if build fails
     */
    @Nullable
    public ItemBase build(@Nonnull ItemStack itemStack, @Nonnull GearData gearData) {
        return build(itemStack, gearData, null);
    }

    // =========================================================================
    // TRANSLATION CONTENT
    // =========================================================================

    /**
     * Builds the translation content (actual text) for a gear item.
     *
     * <p>The returned content should be registered via {@link TranslationSyncService}
     * before sending the item definition to the player.
     *
     * @param itemStack The item stack
     * @param gearData The gear data
     * @param playerId The player viewing the item (for requirement checks)
     * @return Translation content, or null if build fails
     */
    @Nullable
    public TranslationContent buildTranslationContent(
            @Nonnull ItemStack itemStack,
            @Nonnull GearData gearData,
            @Nullable UUID playerId) {
        Objects.requireNonNull(itemStack, "itemStack cannot be null");
        Objects.requireNonNull(gearData, "gearData cannot be null");

        try {
            // Use ItemDisplayNameService for consistent "Lv## [Prefix] Name [Suffix]" format
            // This ensures native pickup UI shows the same name format as Stone Picker and chat
            String nameText = displayNameService.getGearDisplayName(gearData, itemStack);

            // Description: Use Hytale markup format (<color is="#...">text</color>)
            Message tooltipMessage = tooltipFormatter.build(gearData, playerId);
            String descText = MessageSerializer.toFormattedText(tooltipMessage);

            return new TranslationContent(nameText, descText);
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log(
                "Failed to build translation content for %s",
                itemStack.getItemId()
            );
            return null;
        }
    }

    /**
     * Holds the translation text for a gear item.
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
    // CONVENIENCE METHODS
    // =========================================================================

    /**
     * Builds translation properties with keys (not content).
     *
     * <p>Returns properties that reference translation keys. The actual text
     * must be registered via {@link TranslationSyncService} first.
     *
     * @param gearData The gear data (must have instanceId)
     * @return Translation properties with keys, or null if no instanceId
     */
    @Nullable
    public ItemTranslationProperties buildTranslationProperties(@Nonnull GearData gearData) {
        Objects.requireNonNull(gearData, "gearData cannot be null");

        if (!gearData.hasInstanceId()) {
            return null;
        }

        // Use compact instance ID for translation keys (without prefix)
        String compactId = gearData.instanceId().toCompactString();
        String nameKey = TranslationSyncService.getNameKey(compactId);
        String descKey = TranslationSyncService.getDescriptionKey(compactId);

        return new ItemTranslationProperties(nameKey, descKey);
    }

    /**
     * Computes a hash code for the item definition based on gear data.
     *
     * <p>Used by {@link PlayerItemCache} to detect when definitions need updating.
     *
     * <p><b>Note:</b> This overload does not include stats version. For proper
     * cache invalidation when player stats change, use the overload that accepts
     * a statsVersion parameter.
     *
     * @param gearData The gear data
     * @param playerId The player (affects requirement display)
     * @return Hash code for the definition
     */
    public int computeDefinitionHash(@Nonnull GearData gearData, @Nullable UUID playerId) {
        return computeDefinitionHash(gearData, playerId, 0L);
    }

    /**
     * Computes a hash code for the item definition based on gear data and player stats version.
     *
     * <p>Used by {@link PlayerItemCache} to detect when definitions need updating.
     * The stats version ensures that when a player's stats change (level up, allocate
     * attribute points, skill tree changes, gear changes), the hash changes and
     * tooltip updates are triggered.
     *
     * @param gearData The gear data
     * @param playerId The player (affects requirement display)
     * @param statsVersion The player's current stats version (from AttributeManager)
     * @return Hash code for the definition
     */
    // Increment this when the definition format changes to force re-sync of all cached items.
    // This ensures old definitions (missing playerAnimationsId, etc.) get rebuilt.
    private static final int DEFINITION_FORMAT_VERSION = 2;

    public int computeDefinitionHash(@Nonnull GearData gearData, @Nullable UUID playerId, long statsVersion) {
        Objects.requireNonNull(gearData, "gearData cannot be null");

        // Include all factors that affect the visual display
        int result = gearData.hashCode();
        result = 31 * result + (playerId != null ? playerId.hashCode() : 0);
        result = 31 * result + Long.hashCode(statsVersion);
        result = 31 * result + DEFINITION_FORMAT_VERSION;
        return result;
    }

    // =========================================================================
    // HELPER METHODS
    // =========================================================================

    /**
     * Formats a Hytale item ID into a readable base name.
     *
     * <p>Delegates to {@link ItemNameFormatter#resolveDisplayName(String)}.
     *
     * @param itemId The item ID
     * @return Formatted display name
     */
    @Nonnull
    private String formatBaseItemName(@Nonnull String itemId) {
        return ItemNameFormatter.resolveDisplayName(itemId);
    }

    /**
     * Clears vanilla stat modifiers from the item definition.
     *
     * <p>RPG gear uses custom modifiers displayed in the tooltip description,
     * not the vanilla weapon/armor stat modifiers. This prevents the base item's
     * modifiers from applying to the gear (e.g., vanilla damage values).
     *
     * @param definition The item definition to clear modifiers from
     */
    private void clearVanillaModifiers(@Nonnull ItemBase definition) {
        // Clear weapon modifiers (damage, attack speed, etc.)
        if (definition.weapon != null) {
            definition.weapon.statModifiers = null;
        }

        // Clear armor modifiers (defense, resistances, etc.)
        if (definition.armor != null) {
            definition.armor.statModifiers = null;
            definition.armor.damageResistance = null;
            definition.armor.damageEnhancement = null;
            definition.armor.damageClassEnhancement = null;
        }

        // Note: ItemTool does not have statModifiers field in Hytale's protocol
    }

    /**
     * Strips container-opening interactions that crash on RPG gear clones.
     *
     * <p>Some modded armor items (e.g., TheArmory's Dragon Cult chestplates) define
     * "OpenItemStackContainer" as a Secondary interaction. Our rpg_gear_* clones don't
     * have a server-side ItemStackContainerConfig, so that interaction crashes with
     * "Capacity must be > 0" when the player right-clicks.
     *
     * <p><b>Strategy:</b>
     * <ul>
     *   <li>For armor items: remove Secondary from the interactions map (container opener)
     *       and null interactionConfig. Regular armor doesn't use Secondary. This prevents
     *       the crash while keeping Primary and other interactions if any exist.</li>
     *   <li>For weapons/tools: keep ALL interactions intact (Primary=attack, Secondary=block/special).
     *       Weapons never have container interactions.</li>
     * </ul>
     *
     * @param definition The item definition to sanitize
     */
    private void stripContainerInteraction(@Nonnull ItemBase definition) {
        if (definition.armor != null) {
            // Armor: remove Secondary interaction (container opener) and interactionConfig.
            // Armor doesn't need Secondary (right-click) — combat uses the equipment system.
            // Container-armor's Secondary = OpenItemStackContainer which crashes our clones.
            if (definition.interactions != null) {
                definition.interactions.remove(InteractionType.Secondary);
            }
            definition.interactionConfig = null;
        }
        // Weapons, tools, utility: keep all interactions and configs intact.
    }

    // =========================================================================
    // HEXCODE INTEGRATION HELPERS
    // =========================================================================

    /**
     * Whether a base item ID is a magic weapon that should receive Hexcode treatment.
     * All magic weapons (staffs, wands, spellbooks) — regardless of mod origin.
     */
    private static boolean isMagicWeapon(@Nullable String baseItemId) {
        return baseItemId != null && WeaponType.fromItemIdOrUnknown(baseItemId).isMagic();
    }

    /**
     * Resolves the playerAnimationsId for the client definition.
     *
     * <p>Hexcode items use hex-specific animation sets for casting poses.
     * Vanilla magic weapons keep their own animations.
     */
    @Nonnull
    private String resolveAnimationsId(@Nonnull Item baseItem, @Nonnull GearData gearData) {
        if (HexcodeCompat.isLoaded() && isMagicWeapon(gearData.baseItemId())) {
            WeaponType wt = WeaponType.fromItemIdOrUnknown(gearData.baseItemId());
            if (wt == WeaponType.STAFF || wt == WeaponType.WAND) {
                return "HexStaff";
            }
            if (wt == WeaponType.SPELLBOOK) {
                return "HexBook";
            }
        }

        // Non-hex items: use base item's animation
        String animationsId = baseItem.getPlayerAnimationsId();
        if (animationsId != null && !animationsId.isEmpty()) {
            return animationsId;
        }
        return "Item"; // Fallback
    }

    /**
     * Resolves categories for the client definition.
     *
     * <p>Hexcode items preserve Hexcode-specific categories for hex UI sections.
     * All other RPG gear is hidden from the creative library.
     */
    @Nullable
    private String[] resolveCategories(@Nonnull Item baseItem) {
        if (!HexcodeCompat.isLoaded() || !isMagicWeapon(baseItem.getId())) {
            return null;
        }

        WeaponType wt = WeaponType.fromItemIdOrUnknown(baseItem.getId());
        if (wt == WeaponType.STAFF || wt == WeaponType.WAND) {
            return new String[]{"Items.Weapons", "Hexcode.Staves"};
        }
        if (wt == WeaponType.SPELLBOOK) {
            return new String[]{"Items.Weapons", "Hexcode.Books"};
        }

        return null;
    }

    /**
     * Overrides the client definition's interactions with hex interactions.
     *
     * <p>The server-side Item already has hex interactions injected by
     * {@link ItemRegistryService#injectHexcodeIfApplicable}, but the client definition
     * is built from the BASE item (which has vanilla interactions). This method copies
     * the server-side registered Item's interactions into the client definition.
     *
     * <p>Applies to ALL magic weapons (staffs, wands, spellbooks) — any mod origin.
     */
    private void overrideHexInteractionsIfApplicable(
            @Nonnull ItemBase definition,
            @Nonnull GearData gearData) {

        if (!HexcodeCompat.isLoaded() || !isMagicWeapon(gearData.baseItemId())) {
            return;
        }

        WeaponType wt = WeaponType.fromItemIdOrUnknown(gearData.baseItemId());
        if (wt != WeaponType.STAFF && wt != WeaponType.WAND && wt != WeaponType.SPELLBOOK) {
            return;
        }

        // Read the interactions from the REGISTERED custom Item (which has hex interactions)
        String customItemId = gearData.getItemId();
        Item registeredItem = Item.getAssetMap().getAsset(customItemId);
        if (registeredItem == null) {
            return;
        }

        var hexInteractions = registeredItem.getInteractions();
        if (hexInteractions == null || hexInteractions.isEmpty()) {
            return;
        }

        try {
            var intMap = new it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap<InteractionType>();
            for (var entry : hexInteractions.entrySet()) {
                int rootId = com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction
                    .getRootInteractionIdOrUnknown(entry.getValue());
                intMap.put(entry.getKey(), rootId);
            }
            definition.interactions = intMap;
        } catch (Exception e) {
            LOGGER.atFine().log("[Hexcode] Could not override client interactions: %s", e.getMessage());
        }
    }

    /**
     * Overrides the client definition's utility and weapon flags for hex magic weapons.
     *
     * <p>The server and client have DIFFERENT needs:
     * <ul>
     *   <li>Server needs utility.Compatible on staffs for stat application
     *       (StatModifiersManager:77) and interaction routing (InteractionContext:442)</li>
     *   <li>Client shows "Utility" label when utility is present — we want "One-handed"</li>
     * </ul>
     *
     * <p>So the client definition diverges from the server Item:
     * <ul>
     *   <li><b>Staffs/Wands</b>: Null utility on CLIENT only → label becomes "One-handed".
     *       Server keeps utility.Compatible=true intact.</li>
     *   <li><b>Spellbooks</b>: Keep utility (Usable=true for off-hand slot UI).
     *       Null weapon on CLIENT → renders in off-hand model instead of main hand.</li>
     * </ul>
     */
    private void overrideHexUtilityIfApplicable(
            @Nonnull ItemBase definition,
            @Nonnull GearData gearData) {

        if (!HexcodeCompat.isLoaded() || gearData.baseItemId() == null) {
            return;
        }

        // Only apply to magic weapons (staffs, wands, spellbooks) — all mod origins
        if (!isMagicWeapon(gearData.baseItemId())) {
            return;
        }

        WeaponType wt = WeaponType.fromItemIdOrUnknown(gearData.baseItemId());

        // Read from the REGISTERED server-side Item — it has our ensureUtilityFlag
        // injection applied during createCustomItem(). This guarantees the client
        // definition matches the server state exactly.
        String customItemId = gearData.getItemId();
        Item registeredItem = (customItemId != null)
                ? Item.getAssetMap().getAsset(customItemId) : null;

        if (wt == WeaponType.STAFF || wt == WeaponType.WAND) {
            // Staffs/Wands: Keep utility.Compatible=true on CLIENT.
            // Compatible tells the client "this weapon allows an off-hand item" — without it
            // the client treats the weapon as 2-handed and disables the utility slot.
            // Hexcode staffs all have Utility.Compatible=true in Template_HexStaff.
            // Models are in the same Items/Weapons/Staff/ directory — no model override needed.
            if (registeredItem != null) {
                definition.utility = registeredItem.getUtility().toPacket();
            } else {
                ItemUtility staffUtility = new ItemUtility();
                staffUtility.compatible = true;
                definition.utility = staffUtility;
            }
        } else if (wt == WeaponType.SPELLBOOK) {
            // Books: Read utility from registered Item (has Usable=true from ensureUtilityFlag).
            // This handles vanilla/other mod spellbooks that don't have utility originally.
            if (registeredItem != null) {
                definition.utility = registeredItem.getUtility().toPacket();
            } else {
                // Fallback: create utility manually if registered Item not available
                ItemUtility bookUtility = new ItemUtility();
                bookUtility.usable = true;
                definition.utility = bookUtility;
            }

            // Null weapon on CLIENT so it renders in off-hand model instead of main hand.
            definition.weapon = null;

            // Override MODEL to Hex_Book's off-hand model. Vanilla spellbook models
            // (Items/Weapons/Spellbook/*.blockymodel) are rigged for main-hand bone
            // attachment and render in the right hand regardless of slot. The Hex_Book
            // model is rigged for off-hand rendering.
            Item hexBookRef = Item.getAssetMap().getAsset("Hex_Book");
            if (hexBookRef != null) {
                ItemBase hexBookPacket = hexBookRef.toPacket();
                if (hexBookPacket.model != null) {
                    definition.model = hexBookPacket.model;
                }
            }
        }
    }
}
