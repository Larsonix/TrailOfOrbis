# Custom Tooltips in Hytale - A Quick Guide

*For Discord posting - copy everything below this line*

---

__**How Custom Tooltips Work :**__

Hytale items have two fields for display text : **name** and **description**. These are set via translation keys in `ItemTranslationProperties`, not direct text.

You can dynamically register translation values per-player using the `UpdateTranslations` packet, then reference those keys in your item definition.

---

__**Architecture (3 Steps) :**__

**1 - Create custom item definition by cloning a base item :**
```java
Item baseItem = Item.getAssetMap().getAsset("Some_Base_Item");
ItemBase definition = baseItem.toPacket().clone();
definition.id = "rpg.custom.item_12345";  // unique ID

// Point to translation keys (NOT the text itself)
definition.translationProperties = new ItemTranslationProperties(
    "rpg.gear.12345.name",        // name key
    "rpg.gear.12345.description"  // description key
);
```

**2 - Register translations via UpdateTranslations packet :**
```java
Map<String, String> translations = new HashMap<>();
translations.put("rpg.gear.12345.name", "Sharp Iron Sword");
translations.put("rpg.gear.12345.description", formattedTooltipText);

UpdateTranslations packet = new UpdateTranslations();
packet.type = UpdateType.AddOrUpdate;
packet.translations = translations;
playerRef.getPacketHandler().writeNoCache(packet);
```

**3 - Send item definition via UpdateItems packet to the player.**

---

__**Text Formatting :**__

Hytale uses HTML-like markup in translation values :
```
<color is="#55FF55">+25 Strength</color>
<b>Bold text</b>
<i>Italic text</i>
```

You build rich text using Hytale's `Message` API :
```java
Message line = Message.raw("+25 Strength").color("#55FF55");
```

Then serialize to Hytale markup format for the translation value.

---

__**Key Points :**__

- **Translation keys must use a custom prefix** (e.g., `rpg.gear.`). Using `server.items.` doesn't work because that prefix is reserved for static lang files.
- **Each item instance needs a unique ID** - both the `ItemBase.id` and translation keys are derived from it.
- **Per-player :** Translations and item definitions are sent to each player individually. The client caches them.
- **Order matters :** Send `UpdateTranslations` before `UpdateItems`, so the client has the text ready when it receives the item definition.

---

__**What Gets Shown Where :**__

- `translationProperties.name` → Item name in inventory, tooltip header
- `translationProperties.description` → Tooltip body (the "lore")
- `qualityIndex` → Rarity color frame/background

The description field is where you put your multi-line formatted stats, requirements, etc.

---

__**Making Tooltips Reactive (Real-Time Updates) :**__

When something changes that affects a tooltip, you need to **invalidate** the cached definition and **resync** it.

**The pattern :**
```java
// 1 - Remove from player's synced items tracking
syncedItems.get(playerId).remove(customItemId);

// 2 - Unregister old translation
translationService.unregisterTranslation(playerId, instanceId);

// 3 - Build and send NEW UpdateTranslations packet (with updated text)
// 4 - Build and send NEW UpdateItems packet (same item ID)
```

**What triggers re-syncs :**
- Item modified (stone applied, rerolled, etc.) → Invalidate + resync that item
- Player stats changed (affects requirement colors) → Clear player cache, resync all items
- Player reconnects → Full resync

**Important :** There's no push model. The client doesn't automatically refresh. You must send both `UpdateTranslations` AND `UpdateItems` packets for the tooltip to update.

---

__**The Packet Flow for Updates :**__

```
Item modified (e.g., stone applied)
    ↓
Remove from player's syncedItems set
    ↓
Unregister old translation
    ↓
Build new translation content (updated tooltip text)
    ↓
Send UpdateTranslations packet (same key → new text)
    ↓
Send UpdateItems packet (same item ID)
    ↓
Client displays updated tooltip
```

**Key detail :** Keep the same translation keys and item ID. The `UpdateTranslations` packet with `UpdateType.AddOrUpdate` overwrites existing values. The client replaces the cached definition.

---

This is a simplified overview - the actual implementation can get more complex with hash-based change detection, batching for network efficiency, per-player caching, etc. But this covers the core mechanism.

Hope this helps !
