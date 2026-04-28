# Complete Guide: F Key Entity Interaction in Hytale Plugins

This guide explains how to bind F key interactions to entities and execute custom code when players interact with them.

## The Complete Flow

1. Player looks at entity with `Interactable` component
2. Client shows F key prompt with text from `Interactions.getInteractionHint()`
3. Player presses F → Client sends packet to server
4. Server looks up `InteractionType.Use` in entity's `Interactions` component
5. Server finds interaction ID string → loads corresponding `RootInteraction`
6. `RootInteraction` executes its `Interaction` chain (where your code runs)

---

## Step-by-Step Implementation

### Step 1: Create a Custom Interaction Class

Create a class that extends `SimpleInstantInteraction`. Your code runs in `firstRun()`:

```java
package your.plugin;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

public class MyCustomInteraction extends SimpleInstantInteraction {

    // Required: Codec for asset loading
    public static final BuilderCodec<MyCustomInteraction> CODEC = BuilderCodec.builder(
            MyCustomInteraction.class,
            MyCustomInteraction::new,
            SimpleInstantInteraction.CODEC
        )
        .documentation("My custom entity interaction")
        .build();

    // Your interaction instances (with unique IDs)
    public static final MyCustomInteraction INSTANCE = new MyCustomInteraction("*MyPlugin_CustomInteraction");

    public MyCustomInteraction(String id) {
        super(id);
    }

    protected MyCustomInteraction() {
    }

    @Override
    protected void firstRun(
            @Nonnull InteractionType type,
            @Nonnull InteractionContext context,
            @Nonnull CooldownHandler cooldownHandler) {

        // Get the player who interacted
        Ref<EntityStore> playerRef = context.getEntity();
        CommandBuffer<EntityStore> commandBuffer = context.getCommandBuffer();
        Player player = commandBuffer.getComponent(playerRef, Player.getComponentType());

        if (player == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }

        // Get the target entity that was interacted with
        Ref<EntityStore> targetRef = context.getTargetEntity();
        if (targetRef == null || !targetRef.isValid()) {
            context.getState().state = InteractionState.Failed;
            return;
        }

        // ========================================
        // YOUR CODE HERE - runs when F is pressed!
        // ========================================

        player.sendMessage(Message.of("You interacted with the entity!"));

        // Example: Open a custom UI page
        // player.getPageManager().setPage(playerRef, commandBuffer.getStore(), Page.YourCustomPage);

        // If something fails, set failed state:
        // context.getState().state = InteractionState.Failed;
    }
}
```

---

### Step 2: Create a RootInteraction

The `RootInteraction` is the entry point that connects your interaction ID to your `Interaction` class:

```java
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction;

// Create root interaction - ID must match what you set in Interactions component
public static final RootInteraction MY_ROOT = new RootInteraction(
    "*MyPlugin_CustomInteraction",     // Root ID (matches interaction ID)
    "*MyPlugin_CustomInteraction"      // Interaction ID (the string from your Interaction)
);
```

---

### Step 3: Register in Your Plugin's setup()

In your plugin's `setup()` method:

```java
@Override
protected void setup() {
    // 1. Register the Interaction codec type
    this.getCodecRegistry(Interaction.CODEC)
        .register("MyCustomInteraction", MyCustomInteraction.class, MyCustomInteraction.CODEC);

    // 2. Load the Interaction instance as an asset
    AssetRegistry.getAssetStore(Interaction.class)
        .loadAssets("YourPlugin:YourPlugin", List.of(MyCustomInteraction.INSTANCE));

    // 3. Load the RootInteraction as an asset
    AssetRegistry.getAssetStore(RootInteraction.class)
        .loadAssets("YourPlugin:YourPlugin", List.of(MY_ROOT));
}
```

---

### Step 4: Make an Entity Interactable at Runtime

When spawning or modifying an entity:

```java
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.modules.entity.component.Interactable;
import com.hypixel.hytale.server.core.modules.interaction.Interactions;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public void makeEntityInteractable(Store<EntityStore> store, Ref<EntityStore> entityRef) {
    // 1. Add the Interactable component (shows F key prompt)
    store.ensureComponent(entityRef, Interactable.getComponentType());

    // 2. Set up the Use interaction (F key = InteractionType.Use)
    Interactions interactions = store.ensureAndGetComponent(entityRef, Interactions.getComponentType());
    interactions.setInteractionId(InteractionType.Use, "*MyPlugin_CustomInteraction");

    // 3. Optional: Set the hint text shown to the player
    interactions.setInteractionHint("server.interactionHints.customAction"); // Translation key
}
```

---

## Key Classes Reference

| Class | Package | Purpose |
|-------|---------|---------|
| `Interactable` | `com.hypixel.hytale.server.core.modules.entity.component` | Marker component to show F prompt |
| `Interactions` | `com.hypixel.hytale.server.core.modules.interaction` | Maps InteractionType → interaction ID |
| `SimpleInstantInteraction` | `com.hypixel.hytale.server.core.modules.interaction.interaction.config` | Base class for instant interactions |
| `RootInteraction` | `com.hypixel.hytale.server.core.modules.interaction.interaction.config` | Entry point wrapper |
| `InteractionContext` | `com.hypixel.hytale.server.core.entity` | Context passed to your `firstRun()` |
| `InteractionType` | `com.hypixel.hytale.protocol` | Enum - `Use` = F key (value 5) |

---

## InteractionType Enum Values

| Type | Value | Description |
|------|-------|-------------|
| `Primary` | 0 | Left click |
| `Secondary` | 1 | Right click |
| `Ability1` | 2 | Ability key 1 |
| `Ability2` | 3 | Ability key 2 |
| `Ability3` | 4 | Ability key 3 |
| `Use` | 5 | **F key** |
| `Pick` | 6 | Pick action |
| `Pickup` | 7 | Pickup action |
| `CollisionEnter` | 8 | Collision enter |
| `CollisionLeave` | 9 | Collision leave |
| `Collision` | 10 | Collision |

---

## InteractionContext Useful Methods

Inside `firstRun()`, from the `InteractionContext`:

```java
// Get the player who initiated the interaction
Ref<EntityStore> playerRef = context.getEntity();

// Get the entity that was interacted with (target)
Ref<EntityStore> targetRef = context.getTargetEntity();

// Get CommandBuffer for accessing components
CommandBuffer<EntityStore> commandBuffer = context.getCommandBuffer();

// Get the item the player is holding
ItemStack heldItem = context.getHeldItem();

// Get/set interaction state (set to Failed if something goes wrong)
context.getState().state = InteractionState.Failed;

// Get target block position (if interacting with a block)
BlockPosition targetBlock = context.getTargetBlock();

// Get the original item type
Item originalItem = context.getOriginalItemType();
```

---

## Complete Real Example: CraftingPlugin

This is exactly how Hytale's `CraftingPlugin` registers bench interactions:

```java
// In CraftingPlugin.setup():

// Load Interaction instances
AssetRegistry.getAssetStore(Interaction.class)
    .loadAssets("Hytale:Hytale", List.of(
        OpenBenchPageInteraction.SIMPLE_CRAFTING,
        OpenBenchPageInteraction.DIAGRAM_CRAFTING,
        OpenBenchPageInteraction.STRUCTURAL_CRAFTING
    ));

// Load RootInteraction instances
AssetRegistry.getAssetStore(RootInteraction.class)
    .loadAssets("Hytale:Hytale", List.of(
        OpenBenchPageInteraction.SIMPLE_CRAFTING_ROOT,
        OpenBenchPageInteraction.DIAGRAM_CRAFTING_ROOT,
        OpenBenchPageInteraction.STRUCTURAL_CRAFTING_ROOT
    ));

// Register the codec type
this.getCodecRegistry(Interaction.CODEC)
    .register("OpenBenchPage", OpenBenchPageInteraction.class, OpenBenchPageInteraction.CODEC);
```

---

## NPCs with Behavior Trees (Alternative Method)

For NPCs using behavior tree Roles, the system works differently. The NPC system automatically registers `InteractionType.Use → "*UseNPC"` for all entities with a Role.

In Role JSON files, use:

```json
{
  "InteractionInstruction": {
    "Instructions": [
      {
        "Continue": true,
        "Sensor": { "Type": "Any" },
        "Actions": [
          {
            "Type": "SetInteractable",
            "Interactable": true,
            "Hint": "server.interactionHints.talk"
          }
        ]
      },
      {
        "Sensor": { "Type": "HasInteracted" },
        "Actions": [
          // Your actions when F is pressed
        ]
      }
    ]
  }
}
```

Key behavior tree actions:
- `SetInteractable` - Adds/removes the Interactable component
  - `Interactable`: true/false
  - `Hint`: Translation key for the prompt
  - `ShowPrompt`: true/false (whether to show the F key UI)
- `HasInteracted` sensor - Returns true when the player pressed F on this NPC

---

## Entity JSON Configuration (Alternative Method)

For entities configured via JSON (like minecarts, beds, etc.):

```json
{
  "Interactions": {
    "Interactions": {
      "Use": "Your_RootInteraction_Id"
    }
  },
  "Interactable": {}
}
```

---

## Testing with Commands

There's a built-in command to make entities interactable:

```
/entity interactable          # Makes target entity interactable
/entity interactable --disable  # Removes interactability
```

---

## Summary

The key insight is that the interaction ID you set with `setInteractionId()` must match a registered `RootInteraction`, which then chains to your actual `Interaction` class where `firstRun()` is called.

**Registration order matters:**
1. Register codec type with `getCodecRegistry(Interaction.CODEC).register(...)`
2. Load `Interaction` instances with `AssetRegistry.getAssetStore(Interaction.class).loadAssets(...)`
3. Load `RootInteraction` instances with `AssetRegistry.getAssetStore(RootInteraction.class).loadAssets(...)`

**At runtime:**
1. Add `Interactable` component to entity
2. Set `Interactions` component with your interaction ID for `InteractionType.Use`
3. Optionally set interaction hint text
