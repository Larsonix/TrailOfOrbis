package io.github.larsonix.trailoforbis.chat;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.event.EventPriority;
import com.hypixel.hytale.event.EventRegistry;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.ItemBase;
import com.hypixel.hytale.protocol.UpdateType;
import com.hypixel.hytale.protocol.packets.assets.UpdateItems;
import com.hypixel.hytale.protocol.packets.assets.UpdateTranslations;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.player.PlayerChatEvent;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import io.github.larsonix.trailoforbis.gear.item.ItemDefinitionBuilder;
import io.github.larsonix.trailoforbis.gear.item.ItemDisplayNameService;
import io.github.larsonix.trailoforbis.gear.item.TranslationSyncService;
import io.github.larsonix.trailoforbis.gear.model.GearData;
import io.github.larsonix.trailoforbis.gear.tooltip.ItemNameFormatter;
import io.github.larsonix.trailoforbis.gear.util.GearUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Intercepts chat messages containing {@code <Item:xxx>} tags and replaces them
 * with beautifully formatted item names colored by rarity.
 *
 * <h2>Problem</h2>
 * <p>Hytale's client inserts {@code <Item:ItemId>} markup when players shift-click
 * items into chat, but the client never renders this markup — it displays the raw
 * tag as plain text for ALL items (vanilla and custom).
 *
 * <h2>Solution</h2>
 * <p>This handler intercepts {@link PlayerChatEvent}, parses the {@code <Item:xxx>}
 * tags, resolves each item to its display name (with rarity coloring for RPG items),
 * and replaces the event's formatter with one that produces rich {@link Message}
 * objects with styled item name segments.
 *
 * <p>For RPG items ({@code rpg_gear_*}, {@code rpg_map_*}, {@code rpg_gem_*}),
 * item definitions are also broadcast to all chat recipients so their clients
 * have the definition cached for future interactions.
 */
public final class ChatItemLinkHandler {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /** Matches Hytale's client-inserted item tags: {@code <Item:SomeItemId>} */
    private static final Pattern ITEM_TAG = Pattern.compile("<Item:([^>]+)>");

    /** Prefixes for dynamically-registered RPG items that need definition broadcasting. */
    private static final String GEAR_PREFIX = "rpg_gear_";
    private static final String MAP_PREFIX = "rpg_map_";
    private static final String GEM_PREFIX = "rpg_gem_";

    // =========================================================================
    // DEPENDENCIES
    // =========================================================================

    private final ItemDisplayNameService displayNameService;
    private final ItemDefinitionBuilder definitionBuilder;
    private final TranslationSyncService translationService;

    // =========================================================================
    // STATE
    // =========================================================================

    /**
     * Per-viewer dedup cache: tracks which RPG item definitions have been
     * broadcast to each player via chat links. Prevents re-sending the same
     * definition if the same item is linked multiple times.
     *
     * <p>Separate from {@code EquipmentDefinitionBroadcastSystem.viewerSentCache}
     * because the two systems have different lifecycles and triggers.
     */
    private final Map<UUID, Set<String>> viewerSentCache = new ConcurrentHashMap<>();

    // =========================================================================
    // CONSTRUCTOR
    // =========================================================================

    public ChatItemLinkHandler(
            @Nonnull ItemDisplayNameService displayNameService,
            @Nonnull ItemDefinitionBuilder definitionBuilder,
            @Nonnull TranslationSyncService translationService) {
        this.displayNameService = Objects.requireNonNull(displayNameService);
        this.definitionBuilder = Objects.requireNonNull(definitionBuilder);
        this.translationService = Objects.requireNonNull(translationService);
    }

    // =========================================================================
    // LIFECYCLE
    // =========================================================================

    /**
     * Registers the chat event handler.
     */
    public void initialize(@Nonnull EventRegistry eventRegistry) {
        eventRegistry.registerGlobal(
                EventPriority.NORMAL,
                PlayerChatEvent.class,
                this::onChatEvent
        );
        LOGGER.atInfo().log("Chat item link handler initialized");
    }

    /**
     * Cleans up viewer cache on player disconnect.
     */
    public void onPlayerDisconnect(@Nonnull UUID playerId) {
        viewerSentCache.remove(playerId);
    }

    /**
     * Clears all state on plugin shutdown.
     */
    public void shutdown() {
        viewerSentCache.clear();
    }

    // =========================================================================
    // EVENT HANDLER
    // =========================================================================

    private void onChatEvent(@Nonnull PlayerChatEvent event) {
        if (event.isCancelled()) return;

        String content = event.getContent();

        // Fast path: no item tags → do nothing
        Matcher matcher = ITEM_TAG.matcher(content);
        if (!matcher.find()) return;

        // Collect all item IDs and their positions
        matcher.reset();
        List<ItemMatch> matches = new ArrayList<>();
        while (matcher.find()) {
            matches.add(new ItemMatch(matcher.start(), matcher.end(), matcher.group(1)));
        }

        if (matches.isEmpty()) return;

        // Resolve sender's inventory for RPG item lookup
        PlayerRef senderRef = event.getSender();
        Map<String, ItemStack> senderItems = collectSenderItems(senderRef);

        // Resolve each item to a styled Message
        Map<String, Message> resolvedNames = new LinkedHashMap<>();
        for (ItemMatch match : matches) {
            if (resolvedNames.containsKey(match.itemId)) continue;
            resolvedNames.put(match.itemId, resolveItemName(match.itemId, senderItems));
        }

        // Broadcast RPG item definitions to all targets (only items actually linked)
        Set<String> linkedItemIds = resolvedNames.keySet();
        broadcastDefinitionsToTargets(event, senderRef, senderItems, linkedItemIds);

        // Replace the formatter to produce rich message with styled item names
        event.setFormatter((playerRef, msg) ->
                buildFormattedMessage(playerRef, msg, matches, resolvedNames));
    }

    // =========================================================================
    // ITEM RESOLUTION
    // =========================================================================

    /**
     * Resolves an item ID to a styled Message for display in chat.
     *
     * <p>For RPG items found in sender's inventory: full styled name with
     * prefix/suffix and rarity coloring, wrapped in brackets.
     *
     * <p>For vanilla items: clean display name (e.g., "Iron Sword"), wrapped in brackets.
     *
     * <p>For unresolvable RPG items: "[Unknown Item]" in gray italic.
     */
    @Nonnull
    private Message resolveItemName(@Nonnull String itemId,
                                     @Nonnull Map<String, ItemStack> senderItems) {
        boolean isRpgItem = isRpgItemId(itemId);

        if (isRpgItem) {
            ItemStack itemStack = senderItems.get(itemId);
            if (itemStack != null) {
                // Get the full styled name from our display service (handles gear, maps, stones)
                Message styledName = displayNameService.getStyledName(itemStack);
                return wrapInBrackets(styledName);
            }
            // RPG item not in inventory — graceful fallback
            return Message.raw("[Unknown Item]").color("#888888").italic(true);
        }

        // Vanilla item — convert ID to display name
        String displayName = ItemNameFormatter.resolveDisplayName(itemId);
        return Message.raw("[" + displayName + "]").color("#DDDDDD");
    }

    /**
     * Wraps a styled item name Message in brackets: [ItemName]
     *
     * <p>The brackets inherit no special styling (neutral gray), while the item
     * name inside retains its rarity color and bold.
     */
    @Nonnull
    private Message wrapInBrackets(@Nonnull Message styledName) {
        return Message.empty()
                .insert(Message.raw("[").color("#AAAAAA"))
                .insert(styledName)
                .insert(Message.raw("]").color("#AAAAAA"));
    }

    // =========================================================================
    // MESSAGE BUILDING
    // =========================================================================

    /**
     * Builds a FormattedMessage that replaces {@code <Item:xxx>} tags with
     * styled item name segments while preserving the standard chat format.
     */
    @Nonnull
    private Message buildFormattedMessage(
            @Nonnull PlayerRef playerRef,
            @Nonnull String rawContent,
            @Nonnull List<ItemMatch> matches,
            @Nonnull Map<String, Message> resolvedNames) {

        // Build the message content with styled item segments
        Message messageContent = buildCompositeContent(rawContent, matches, resolvedNames);

        // Wrap in the standard chat format: "PlayerName: message"
        return Message.translation("server.chat.playerMessage")
                .param("username", playerRef.getUsername())
                .param("message", messageContent);
    }

    /**
     * Splits the raw content on {@code <Item:xxx>} boundaries and builds
     * a composite Message with plain text and styled item segments.
     */
    @Nonnull
    private Message buildCompositeContent(
            @Nonnull String rawContent,
            @Nonnull List<ItemMatch> matches,
            @Nonnull Map<String, Message> resolvedNames) {

        Message composite = Message.empty();
        int lastEnd = 0;

        for (ItemMatch match : matches) {
            // Add plain text before this item tag
            if (match.start > lastEnd) {
                composite.insert(Message.raw(rawContent.substring(lastEnd, match.start)));
            }

            // Add the styled item name
            Message styledName = resolvedNames.get(match.itemId);
            if (styledName != null) {
                composite.insert(styledName);
            } else {
                // Fallback: show raw ID cleaned up
                composite.insert(Message.raw("[" + match.itemId + "]").color("#888888"));
            }

            lastEnd = match.end;
        }

        // Add any remaining text after the last item tag
        if (lastEnd < rawContent.length()) {
            composite.insert(Message.raw(rawContent.substring(lastEnd)));
        }

        return composite;
    }

    // =========================================================================
    // INVENTORY ACCESS
    // =========================================================================

    /**
     * Collects the sender's inventory items, keyed by item ID, for item lookup.
     *
     * <p>Returns an empty map if the sender's entity is not accessible.
     */
    @Nonnull
    private Map<String, ItemStack> collectSenderItems(@Nonnull PlayerRef senderRef) {
        Ref<EntityStore> ref = senderRef.getReference();
        if (ref == null || !ref.isValid()) return Map.of();

        Store<EntityStore> store = ref.getStore();
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return Map.of();

        @SuppressWarnings("deprecation")
        Inventory inventory = player.getInventory();
        if (inventory == null) return Map.of();

        List<ItemStack> allItems = GearUtils.collectAllInventoryItems(inventory);
        Map<String, ItemStack> itemsById = new HashMap<>(allItems.size());
        for (ItemStack item : allItems) {
            String id = item.getItemId();
            if (id != null && !id.isEmpty()) {
                itemsById.putIfAbsent(id, item); // first occurrence wins
            }
        }
        return itemsById;
    }

    // =========================================================================
    // DEFINITION BROADCASTING
    // =========================================================================

    /**
     * Broadcasts RPG item definitions to all chat recipients so their clients
     * can resolve the item for future interactions (hover, etc.).
     *
     * <p>Follows the proven pattern from {@code EquipmentDefinitionBroadcastSystem}.
     */
    private void broadcastDefinitionsToTargets(
            @Nonnull PlayerChatEvent event,
            @Nonnull PlayerRef senderRef,
            @Nonnull Map<String, ItemStack> senderItems,
            @Nonnull Set<String> linkedItemIds) {

        UUID senderId = senderRef.getUuid();

        // Collect only the RPG items that were actually linked in chat
        List<BroadcastItem> rpgItems = new ArrayList<>();
        for (String itemId : linkedItemIds) {
            if (!isRpgItemId(itemId)) continue;
            ItemStack itemStack = senderItems.get(itemId);
            if (itemStack == null) continue;

            Optional<GearData> gearData = GearUtils.readGearData(itemStack);
            if (gearData.isEmpty() || !gearData.get().hasInstanceId()) continue;

            GearData data = gearData.get();
            ItemBase definition = definitionBuilder.build(itemStack, data, senderId);
            if (definition == null) continue;

            String instanceId = data.instanceId().toCompactString();
            String displayName = displayNameService.getDisplayName(itemStack);

            rpgItems.add(new BroadcastItem(itemId, instanceId, displayName, definition));
        }

        if (rpgItems.isEmpty()) return;

        // Send to each target (except sender)
        for (PlayerRef targetRef : event.getTargets()) {
            UUID targetId = targetRef.getUuid();
            if (targetId.equals(senderId)) continue;

            Set<String> alreadySent = viewerSentCache.computeIfAbsent(
                    targetId, k -> ConcurrentHashMap.newKeySet());

            Map<String, ItemBase> definitionsToSend = new HashMap<>();
            Map<String, String> translationsToSend = new HashMap<>();

            for (BroadcastItem item : rpgItems) {
                if (!alreadySent.add(item.itemId)) continue; // already sent

                definitionsToSend.put(item.itemId, item.definition);

                // Name-only translations (viewers don't need full tooltip)
                String nameKey = TranslationSyncService.getNameKey(item.instanceId);
                String descKey = TranslationSyncService.getDescriptionKey(item.instanceId);
                translationsToSend.put(nameKey, item.displayName);
                translationsToSend.put(descKey, ""); // empty description for viewers
            }

            if (definitionsToSend.isEmpty()) continue;

            // Send translations FIRST, then definitions (packet ordering critical)
            sendTranslations(targetRef, translationsToSend);
            sendDefinitions(targetRef, definitionsToSend);
        }
    }

    /**
     * Sends an UpdateTranslations packet to a player.
     */
    private void sendTranslations(@Nonnull PlayerRef targetRef,
                                   @Nonnull Map<String, String> translations) {
        if (translations.isEmpty()) return;

        UpdateTranslations packet = new UpdateTranslations();
        packet.type = UpdateType.AddOrUpdate;
        packet.translations = translations;
        targetRef.getPacketHandler().writeNoCache(packet);
    }

    /**
     * Sends an UpdateItems packet to a player.
     */
    private void sendDefinitions(@Nonnull PlayerRef targetRef,
                                  @Nonnull Map<String, ItemBase> definitions) {
        if (definitions.isEmpty()) return;

        UpdateItems packet = new UpdateItems();
        packet.type = UpdateType.AddOrUpdate;
        packet.items = definitions;
        targetRef.getPacketHandler().writeNoCache(packet);
    }

    // =========================================================================
    // UTILITIES
    // =========================================================================

    private static boolean isRpgItemId(@Nonnull String itemId) {
        return itemId.startsWith(GEAR_PREFIX)
                || itemId.startsWith(MAP_PREFIX)
                || itemId.startsWith(GEM_PREFIX);
    }

    // =========================================================================
    // DATA CLASSES
    // =========================================================================

    /** Represents a matched {@code <Item:xxx>} tag in the chat content. */
    private record ItemMatch(int start, int end, String itemId) {}

    /** An RPG item ready for definition broadcasting. */
    private record BroadcastItem(
            String itemId,
            String instanceId,
            String displayName,
            ItemBase definition) {}
}
