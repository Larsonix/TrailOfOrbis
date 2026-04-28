# Hytale Notifications Skill

Use this skill when sending in-game notifications to players in Hytale plugins. Notifications appear similar to item pickup messages and consist of a primary message, secondary message, and an optional icon.

---

## Quick Reference

| Concept | Description |
|---------|-------------|
| **NotificationUtil** | Utility class for sending notifications to players |
| **Primary Message** | Main `Message` displayed in the notification |
| **Secondary Message** | Additional `Message` displayed below the primary |
| **Icon** | An item icon shown on the left side of the notification |
| **PacketHandler** | Required to send notifications to a specific player |

---

## Notification Structure

A notification consists of three main components:

1. **Primary Message**: The main `Message` displayed prominently in the notification
2. **Secondary Message**: Additional `Message` displayed below the primary message
3. **Icon**: An item icon that visually represents the notification, shown on the left side

---

## Required Imports

```java
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.protocol.ItemWithAllMetadata;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.util.NotificationUtil;
```

---

## Sending Notifications

Use the `NotificationUtil` class to send notifications to players. You need access to the `PacketHandler` of the player.

### Getting PacketHandler

The `PacketHandler` can be obtained from a `PlayerRef` using the `getPacketHandler()` method:

```java
// From an event
PlayerRef playerRef = Universe.get().getPlayer(player.getUuid());
var packetHandler = playerRef.getPacketHandler();
```

### NotificationUtil.sendNotification()

| Parameter | Type | Description |
|-----------|------|-------------|
| `packetHandler` | `PacketHandler` | The player's packet handler |
| `primaryMessage` | `Message` | Main notification text |
| `secondaryMessage` | `Message` | Secondary text below primary |
| `icon` | `ItemWithAllMetadata` | Item icon to display |

---

## Basic Example

```java
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.protocol.ItemWithAllMetadata;
import com.hypixel.hytale.server.core.util.NotificationUtil;

public class NotificationExample {
    
    public static void onPlayerReady(PlayerReadyEvent event) {
        var player = event.getPlayer();
        var playerRef = Universe.get().getPlayer(player.getUuid());
        var packetHandler = playerRef.getPacketHandler();
        
        // Create messages
        var primaryMessage = Message.raw("THIS WORKS!!!").color("#00FF00");
        var secondaryMessage = Message.raw("This is the secondary message").color("#228B22");
        
        // Create icon from item
        var icon = new ItemStack("Weapon_Sword_Mithril", 1).toPacket();
        
        // Send notification
        NotificationUtil.sendNotification(
            packetHandler,
            primaryMessage,
            secondaryMessage,
            (ItemWithAllMetadata) icon
        );
    }
}
```

---

## Common Use Cases

### Achievement/Quest Notification

```java
public void sendAchievementNotification(PlayerRef playerRef, String achievement) {
    var packetHandler = playerRef.getPacketHandler();
    
    var primary = Message.raw("Achievement Unlocked!").color("#FFD700");
    var secondary = Message.raw(achievement).color("#FFFFFF");
    var icon = new ItemStack("Item_Trophy_Gold", 1).toPacket();
    
    NotificationUtil.sendNotification(
        packetHandler,
        primary,
        secondary,
        (ItemWithAllMetadata) icon
    );
}
```

### Item Received Notification

```java
public void sendItemReceivedNotification(PlayerRef playerRef, String itemId, int quantity) {
    var packetHandler = playerRef.getPacketHandler();
    
    var primary = Message.raw("Item Received").color("#00FF00");
    var secondary = Message.raw("+" + quantity + " " + itemId).color("#AAAAAA");
    var icon = new ItemStack(itemId, quantity).toPacket();
    
    NotificationUtil.sendNotification(
        packetHandler,
        primary,
        secondary,
        (ItemWithAllMetadata) icon
    );
}
```

### Warning/Alert Notification

```java
public void sendWarningNotification(PlayerRef playerRef, String warning) {
    var packetHandler = playerRef.getPacketHandler();
    
    var primary = Message.raw("Warning!").color("#FF0000");
    var secondary = Message.raw(warning).color("#FFAAAA");
    var icon = new ItemStack("Item_Warning_Sign", 1).toPacket();
    
    NotificationUtil.sendNotification(
        packetHandler,
        primary,
        secondary,
        (ItemWithAllMetadata) icon
    );
}
```

### Level Up Notification

```java
public void sendLevelUpNotification(PlayerRef playerRef, int newLevel) {
    var packetHandler = playerRef.getPacketHandler();
    
    var primary = Message.raw("LEVEL UP!").color("#FFD700");
    var secondary = Message.raw("You are now level " + newLevel).color("#FFFFFF");
    var icon = new ItemStack("Item_Star", 1).toPacket();
    
    NotificationUtil.sendNotification(
        packetHandler,
        primary,
        secondary,
        (ItemWithAllMetadata) icon
    );
}
```

---

## Message Styling

Notifications use the standard `Message` API for styling:

| Method | Description |
|--------|-------------|
| `Message.raw(String)` | Create message from raw text |
| `.color(String)` | Apply hex color (e.g., `"#00FF00"`) |
| `.color(Color)` | Apply named color constant |
| `Message.join(Message...)` | Join multiple messages |

### Hex Color Examples

```java
// Green success
Message.raw("Success!").color("#00FF00");

// Gold achievement
Message.raw("Achievement").color("#FFD700");

// Red warning
Message.raw("Warning").color("#FF0000");

// Blue info
Message.raw("Info").color("#0088FF");
```

---

## Creating Icons

Icons are created from `ItemStack` objects converted to packet format:

```java
// Create from item ID and quantity
var icon = new ItemStack("Weapon_Sword_Mithril", 1).toPacket();

// Cast to ItemWithAllMetadata for sendNotification
NotificationUtil.sendNotification(
    packetHandler,
    primary,
    secondary,
    (ItemWithAllMetadata) icon
);
```

### Common Icon Items

| Item ID | Use Case |
|---------|----------|
| `Weapon_Sword_*` | Combat notifications |
| `Item_Trophy_*` | Achievement notifications |
| `Item_Coin_*` | Currency notifications |
| `Food_*` | Food/buff notifications |
| `Tool_*` | Tool-related notifications |

---

## Utility Wrapper Class

Consider creating a utility wrapper for consistent notification styling:

```java
public class Notifications {
    
    public static void success(PlayerRef player, String title, String message, String iconId) {
        send(player, title, "#00FF00", message, "#AAFFAA", iconId);
    }
    
    public static void warning(PlayerRef player, String title, String message, String iconId) {
        send(player, title, "#FFAA00", message, "#FFDDAA", iconId);
    }
    
    public static void error(PlayerRef player, String title, String message, String iconId) {
        send(player, title, "#FF0000", message, "#FFAAAA", iconId);
    }
    
    public static void info(PlayerRef player, String title, String message, String iconId) {
        send(player, title, "#0088FF", message, "#AADDFF", iconId);
    }
    
    private static void send(PlayerRef player, String title, String titleColor,
                             String message, String messageColor, String iconId) {
        var packetHandler = player.getPacketHandler();
        var primary = Message.raw(title).color(titleColor);
        var secondary = Message.raw(message).color(messageColor);
        var icon = new ItemStack(iconId, 1).toPacket();
        
        NotificationUtil.sendNotification(
            packetHandler,
            primary,
            secondary,
            (ItemWithAllMetadata) icon
        );
    }
}
```

---

## Best Practices

1. **Keep messages concise**: Notifications are meant for quick information
2. **Use appropriate colors**: Green for success, red for errors, gold for achievements
3. **Choose relevant icons**: Match the icon to the notification context
4. **Avoid spamming**: Don't send too many notifications in quick succession
5. **Localize text**: Use translation keys for user-facing notification text

---

## Related APIs

- [Chat Formatting](../hytale-chat-formatting/SKILL.md) - For styled chat messages
- [Message API](#message-styling) - Core message styling
- [PlayerRef](https://hytalemodding.dev/en/docs/server/entities) - Player reference access

---

## EventTitleUtil — Fullscreen Title Banners

For dramatic fullscreen title/subtitle displays (dungeon entry, boss fights, level-up announcements), use `EventTitleUtil`. These are large center-screen titles with configurable timing and optional icons.

### Required Import

```java
import com.hypixel.hytale.server.core.util.EventTitleUtil;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
```

### Show to One Player (Simple)

```java
EventTitleUtil.showEventTitleToPlayer(
    playerRef,                       // PlayerRef — target player
    Message.raw("LEVEL UP!"),       // primaryTitle (large text)
    Message.raw("You reached Lv5"), // secondaryTitle (smaller text below)
    true                             // isMajor — true = larger display style
);
// Uses defaults: no icon, 4s duration, 1.5s fade in, 1.5s fade out
```

### Show to One Player (Full Control)

```java
EventTitleUtil.showEventTitleToPlayer(
    playerRef,                       // PlayerRef
    Message.raw("REALM COMPLETE"),  // primaryTitle
    Message.raw("+500 XP"),         // secondaryTitle
    true,                            // isMajor
    "Icons/ItemsGenerated/Item_Trophy_Gold.png",  // icon (nullable)
    6.0f,                            // duration (seconds)
    1.0f,                            // fadeInDuration (seconds)
    2.0f                             // fadeOutDuration (seconds)
);
```

### Show to All Players in a World

```java
EventTitleUtil.showEventTitleToWorld(
    Message.raw("BOSS INCOMING"),
    Message.raw("Prepare yourselves!"),
    true,                            // isMajor
    null,                            // icon
    5.0f,                            // duration
    1.5f,                            // fadeInDuration
    1.5f,                            // fadeOutDuration
    store                            // Store<EntityStore> — the world's store
);
```

### Show to Entire Universe

```java
EventTitleUtil.showEventTitleToUniverse(
    Message.raw("SERVER EVENT"),
    Message.raw("Double XP is now active!"),
    true,
    null,  // icon
    8.0f,  // duration
    2.0f,  // fadeInDuration
    2.0f   // fadeOutDuration
);
```

### Hide Manually

```java
// Hide from one player (with fade-out)
EventTitleUtil.hideEventTitleFromPlayer(playerRef, 0.5f);

// Hide from all players in a world
EventTitleUtil.hideEventTitleFromWorld(0.5f, store);
```

### Parameter Reference

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `playerRef` | `PlayerRef` | — | Target player |
| `primaryTitle` | `Message` | — | Main title text (large, centered on screen) |
| `secondaryTitle` | `Message` | — | Subtitle text (smaller, below title) |
| `isMajor` | `boolean` | — | `true` = larger display style with decoration |
| `icon` | `String` | `null` | Icon asset path (nullable) |
| `duration` | `float` | `4.0` | Display time in seconds |
| `fadeInDuration` | `float` | `1.5` | Fade-in time in seconds |
| `fadeOutDuration` | `float` | `1.5` | Fade-out time in seconds |
| `store` | `Store<EntityStore>` | — | World's entity store (for world/universe methods) |

### Constants

```java
EventTitleUtil.DEFAULT_DURATION       // 4.0f
EventTitleUtil.DEFAULT_FADE_DURATION  // 1.5f
EventTitleUtil.DEFAULT_ZONE           // "Void"
```

### Method Summary

| Method | Scope | Notes |
|--------|-------|-------|
| `showEventTitleToPlayer(playerRef, title, subtitle, isMajor)` | One player | Default timing |
| `showEventTitleToPlayer(playerRef, title, subtitle, isMajor, icon, duration, fadeIn, fadeOut)` | One player | Full control |
| `showEventTitleToWorld(title, subtitle, isMajor, icon, duration, fadeIn, fadeOut, store)` | All in world | Auto-broadcasts |
| `showEventTitleToUniverse(title, subtitle, isMajor, icon, duration, fadeIn, fadeOut)` | All players | Cross-world |
| `hideEventTitleFromPlayer(playerRef, fadeOutDuration)` | One player | Manual dismiss |
| `hideEventTitleFromWorld(fadeOutDuration, store)` | All in world | Manual dismiss |

### When to Use

- **EventTitleUtil** — Dramatic fullscreen announcements (dungeon start, boss phase, realm entry, level-up)
- **NotificationUtil** — Item-pickup-style toast messages (loot drops, achievements, warnings)

Both APIs use `Message` objects so they support styled text via `Message.raw()`, `Message.translatable()`, color formatting, etc.

---

## References

- [Official Documentation](https://hytalemodding.dev/en/docs/guides/plugin/send-notifications)
