---
name: hytale-commands
description: Documents Hytale's command system for creating custom commands in plugins. Covers AbstractAsyncCommand, AbstractPlayerCommand, AbstractTargetPlayerCommand, AbstractTargetEntityCommand, AbstractCommandCollection, arguments (RequiredArg, OptionalArg, DefaultArg, FlagArg), ArgTypes, argument validators, custom validators, permissions, command variants, aliases, subcommands, and registration. Use when creating commands, adding arguments, validating input, requiring permissions, building command trees, or registering commands. Triggers - command, custom command, AbstractPlayerCommand, AbstractAsyncCommand, AbstractTargetPlayerCommand, AbstractTargetEntityCommand, AbstractCommandCollection, CommandContext, RequiredArg, OptionalArg, DefaultArg, FlagArg, ArgTypes, Validator, requirePermission, addUsageVariant, addAliases, addSubCommand, registerCommand, CommandRegistry.

## Quick Reference

| Task | Approach |
|------|----------|
| Basic async command | Extend `AbstractAsyncCommand`, override `executeAsync()` |
| Player-bound command | Extend `AbstractPlayerCommand`, override `execute()` |
| Target another player | Extend `AbstractTargetPlayerCommand` (adds `--player` arg) |
| Target looked-at entity | Extend `AbstractTargetEntityCommand` (uses raycast) |
| Add required argument | `this.withRequiredArg("name", "desc", ArgTypes.STRING)` |
| Add optional argument | `this.withOptionalArg("name", "desc", ArgTypes.STRING)` |
| Add default argument | `this.withDefaultArg("name", "desc", ArgTypes.FLOAT, 100f, "default desc")` |
| Add flag argument | `this.withFlagArg("name", "desc")` |
| Get argument value | `myArg.get(commandContext)` |
| Require permission | `requirePermission(HytalePermissions.fromCommand("name"))` |
| Make command public | Override `canGeneratePermission()` to return `false` |
| Add variant | `addUsageVariant(new OtherCommand())` |
| Add alias | `addAliases("alias1", "alias2")` |
| Group subcommands | Extend `AbstractCommandCollection`, call `addSubCommand(...)` |
| Register command | `getCommandRegistry().registerCommand(new MyCommand())` in `setup()` |

---

## Command Types

### AbstractAsyncCommand

Runs on a background thread. Cannot safely access `Store` or `Ref` without getting the world first. Best for world-independent commands (e.g., displaying rules).

```java
public class ServerRulesCommand extends AbstractAsyncCommand {

    public ServerRulesCommand() {
        super("rules", "Lists the servers rules");
    }

    @Override
    protected CompletableFuture<Void> executeAsync(@Nonnull CommandContext context) {
        context.sendMessage(Message.raw("The only rule is there are no rules."));
        return CompletableFuture.completedFuture(null);
    }
}
```

> **Warning:** `AbstractAsyncCommand` runs asynchronously - it cannot edit Stores or Refs without first getting the desired world. For most commands, prefer the other command types.

### AbstractPlayerCommand

Tied to the executing player and their world. Runs on the world thread - safe to access `Store` and `Ref` directly. Most common command type.

```java
public class ExampleCommand extends AbstractPlayerCommand {

    public ExampleCommand() {
        super("test", "Super test command!");
    }

    @Override
    protected void execute(@Nonnull CommandContext commandContext,
                          @Nonnull Store<EntityStore> store,
                          @Nonnull Ref<EntityStore> ref,
                          @Nonnull PlayerRef playerRef,
                          @Nonnull World world) {
        Player player = store.getComponent(ref, Player.getComponentType());
        UUIDComponent component = store.getComponent(ref, UUIDComponent.getComponentType());
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        player.sendMessage(Message.raw("Transform : " + transform.getPosition()));
    }
}
```

> **Note:** Long-running operations (like IO) in `AbstractPlayerCommand` will block the world thread and cause lag. Use `AbstractAsyncCommand` for heavy IO.

### AbstractTargetPlayerCommand

Like `AbstractPlayerCommand` but adds a `--player <value>` argument to target a different player. Thread-safe - override `execute()`, not `executeAsync()`.

### AbstractTargetEntityCommand

Uses a raycast to target the entity the player is looking at. Runs on the world thread of the targeted entity.

```java
@Override
protected void execute(CommandContext context,
                      Store<EntityStore> store,
                      Ref<EntityStore> ref,
                      World world) {
    // ref is the targeted entity's reference
    EntityStatMap stats = store.getComponent(ref, EntityStatMap.getComponentType());
    if (stats == null) {
        context.sendMessage(Message.raw("This entity has no stats!"));
        return;
    }
    int healthIdx = DefaultEntityStatTypes.getHealth();
    EntityStatValue health = stats.get(healthIdx);
    if (health == null) {
        context.sendMessage(Message.raw("This entity has no health!"));
        return;
    }
    stats.addValue(healthIdx, 100);
}
```

---

## Arguments

Arguments are added in the command's constructor. The value is retrieved during `execute` by passing `commandContext` to the argument's `get()` method.

### Argument Types

| Method | Behavior | Usage |
|--------|----------|-------|
| `withRequiredArg(name, desc, type)` | Must be provided; parsed left-to-right positionally | `RequiredArg<T>` |
| `withOptionalArg(name, desc, type)` | Returns `null` if not provided; uses `--key value` syntax | `OptionalArg<T>` |
| `withDefaultArg(name, desc, type, default, defaultDesc)` | Returns default if not provided | `DefaultArg<T>` |
| `withFlagArg(name, desc)` | Boolean switch; `true` if present, `false` if not; uses `--name` | `FlagArg` |

### ArgTypes

Complete reference of all available command argument types (source: Valeena, HytaleModding.dev).

| Name | Target Class | Description |
|:-----|:-------------|:------------|
| `Boolean` | `java.lang.Boolean` | A 'true' or 'false' input |
| `Integer` | `java.lang.Integer` | A whole number |
| `String` | `java.lang.String` | Words, text, numbers, letters. Wrap multi-word input in double quotes (`"my fancy sentence"`) |
| `Float` | `java.lang.Float` | A floating-point number |
| `Double` | `java.lang.Double` | A decimal number |
| `UUID` | `java.util.UUID` | A UUID (Universally Unique IDentifier) |
| `Player_UUID` | `java.util.UUID` | A UUID or an online player username |
| `Game_Profile_Lookup_Async` | `ProfileServiceClient.PublicGameProfile` | A player UUID or username. Performs remote lookup if not found locally |
| `Relative_Double_Coord` | `Coord` | An x/y/z coordinate as a decimal, optionally relative with tilde (`~`) prefix |
| `Relative_Int_Coord` | `IntCoord` | An x/y/z coordinate as a whole number, optionally relative with tilde (`~`) prefix |
| `Relative_Integer` | `RelativeInteger` | A tilde-relative integer |
| `Relative_Float` | `RelativeFloat` | A tilde-relative float |
| `Player_Ref` | `PlayerRef` | A UUID or an online player's username |
| `World` | `World` | A world folder name |
| `Model_Asset` | `ModelAsset` | A reference to an Asset of type Model |
| `Weather_Asset` | `Weather` | A reference to an Asset of type Weather |
| `Interaction_Asset` | `Interaction` | A reference to an Asset of type Interaction |
| `Root_Interaction_Asset` | `RootInteraction` | A reference to an Asset of type Root Interaction |
| `Effect_Asset` | `EntityEffect` | A reference to an Asset of type EntityEffect |
| `Environment_Asset` | `Environment` | A reference to an Asset of type Environment |
| `Item_Asset` | `Item` | A reference to an Asset of type Item |
| `Block_Type_Asset` | `BlockType` | A reference to an Asset of type BlockType |
| `Particle_System` | `ParticleSystem` | A reference to an Asset of type ParticleSystem |
| `Hitbox_Collision_Config` | `HitboxCollisionConfig` | A reference to an Asset of type HitboxCollisionConfig |
| `Repulsion_Config` | `RepulsionConfig` | A reference to an Asset of type RepulsionConfig |
| `Sound_Event_Asset` | `SoundEvent` | A reference to an Asset of type SoundEvent |
| `Ambiance_Fx_Asset` | `AmbianceFX` | A reference to an Asset of type AmbianceFX |
| `Sound_Category` | `SoundCategory` | A sound category (sfx, music, ambient, ui) |
| `Entity_ID` | `ArgWrapper<EntityWrappedArg, UUID>` | A UUID representing an entity id |
| `Integer_Comparison_Operator` | `IntegerComparisonOperator` | A mathematical sign for integer comparison |
| `Integer_Operation` | `IntegerOperation` | A mathematical sign for performing an operation |
| `Int_Range` | `Pair<Integer, Integer>` | Two integers representing a min-max range |
| `Relative_Int_Range` | `RelativeIntRange` | Two integers representing a min-max range (relative) |
| `Vector2I` | `Vector2i` | Two integers (x/z axis) |
| `Vector3I` | `Vector3i` | Three integers (x/y/z axis) |
| `Relative_Vector3I` | `RelativeVector3i` | Three optionally relative integers (x/y/z axis) |
| `Relative_Block_Position` | `RelativeIntPosition` | A block position with three integer coordinates |
| `Relative_Position` | `Relative` | A world position with three decimal coordinates |
| `Relative_Chunk_Position` | `RelativeChunkPosition` | A chunk position with two integer coordinates (x/z) |
| `Rotation` | `Vector3f` | Three coordinates: pitch, yaw, roll |
| `Block_Type_Key` | `String` | A block type name |
| `Block_ID` | `Integer` | A block type converted to an int id |
| `Color` | `Integer` | A color value in hex format, hex integer, or decimal integer |
| `Weighted_Block_Type` | `Pair<Integer, String>` | A weight + blocktype pair |
| `Weighted_Block_Entry` | `String` | A block with optional weight prefix |
| `Block_Pattern` | `BlockPattern` | A list of blocks with optional weights |
| `Individual_Block_Mask` | `BlockMask` | A block mask using symbols and block names |
| `Block_Mask` | `BlockMask` | A list of block masks that combine together |
| `Tick_Rate` | `Integer` | A tick rate value (e.g., 30tps, 33ms, or 30) |
| `Game_Mode` | `GameMode` | GameMode enum value |

**Common shortcuts for plugin code:**
- `ArgTypes.STRING` → String
- `ArgTypes.INTEGER` → Integer
- `ArgTypes.BOOLEAN` → Boolean
- `ArgTypes.FLOAT` → Float
- `ArgTypes.DOUBLE` → Double
- `ArgTypes.UUID` → UUID
- `ArgTypes.PLAYER_REF` → Player_Ref
- `ArgTypes.PLAYER_UUID` → Player_UUID
- `ArgTypes.ITEM_ASSET` → Item_Asset

### Full Arguments Example

```java
// Usage: /healplayer --health 50 --message "Feels Good" --debug
public class HealPlayerCommand extends AbstractTargetPlayerCommand {
    private final DefaultArg<Float> healthArg;
    private final OptionalArg<String> messageArg;
    private final FlagArg debugArg;

    public HealPlayerCommand() {
        super("healplayer", "Healing a player for an <input> amount of HP (default: 100)");

        this.healthArg = this.withDefaultArg("health", "Amount to heal player",
            ArgTypes.FLOAT, (float) 100, "Desc of Default: 100");
        this.messageArg = this.withOptionalArg("message",
            "Message to print while healing", ArgTypes.STRING);
        this.debugArg = this.withFlagArg("debug", "Add debug logs");
    }

    @Override
    protected void execute(@Nonnull CommandContext commandContext,
                          @Nullable Ref<EntityStore> ref,
                          @Nonnull Ref<EntityStore> ref1,
                          @Nonnull PlayerRef playerRef,
                          @Nonnull World world,
                          @Nonnull Store<EntityStore> store) {

        if (this.debugArg.get(commandContext)) {
            commandContext.sendMessage(Message.raw("We are debugging"));
        }

        EntityStatMap stats = store.getComponent(ref, EntityStatMap.getComponentType());
        int healthIdx = DefaultEntityStatTypes.getHealth();
        stats.addStatValue(healthIdx, healthArg.get(commandContext));
    }
}
```

---

## Argument Validators

Add validators to arguments using `.addValidator()`. Built-in validators are in the `Validators` class:

```java
OptionalArg<Integer> healAmount = withOptionalArg("amount", "Heal Amount", ArgTypes.INTEGER)
    .addValidator(Validators.greaterThan(0))
    .addValidator(Validators.lessThan(1000));
```

### Custom Validators

Implement `com.hypixel.hytale.codec.validation.Validator<T>`:

```java
import com.hypixel.hytale.codec.schema.SchemaContext;
import com.hypixel.hytale.codec.schema.config.Schema;
import com.hypixel.hytale.codec.validation.ValidationResults;
import com.hypixel.hytale.codec.validation.Validator;

public class MyCustomValidator implements Validator<String> {
    @Nonnull
    private final String bannedValue;

    public MyCustomValidator(@Nonnull String bannedValue) {
        this.bannedValue = bannedValue;
    }

    @Override
    public void accept(@Nullable String input, @Nonnull ValidationResults results) {
        if (this.bannedValue.equalsIgnoreCase(input)) {
            results.fail("The given value has been banned.");
        }
    }

    @Override
    public void updateSchema(SchemaContext context, @Nonnull Schema target) {
        // Optional: update schema for dynamic validation
        throw new UnsupportedOperationException("Not implemented yet.");
    }
}
```

Usage:

```java
String bannedRole = "badword";
OptionalArg<String> roleArg = withOptionalArg("role", "Role to assign", ArgTypes.STRING)
    .addValidator(new MyCustomValidator(bannedRole));
```

---

## Permissions

Add permission requirements in the constructor:

```java
public HealPlayerCommand() {
    super("healplayer", "heal a player a given amount of HP");

    // Single permission
    requirePermission(HytalePermissions.fromCommand("rules"));

    // Multiple required permissions (AND)
    requirePermission(HytalePermissions.fromCommand("usercommands"));

    // OR block - needs one from a list
    requirePermission(
        PermissionRules.or(
            HytalePermissions.fromCommand("moderator"),
            HytalePermissions.fromCommand("admin")
        )
    );
}
```

> Use `/perm` in-game to manage player permissions and groups. Run `/perm --help` for usage.

### Making a Command Require No Permission

**Override `canGeneratePermission()` (RECOMMENDED):**
```java
@Override
protected boolean canGeneratePermission() {
    return false; // Prevents auto-generated permission
}
```

For subcommands, **BOTH parent AND child** must return `false`:

```java
public class ParentCommand extends AbstractCommandCollection {
    public ParentCommand() {
        super("parent", "desc");
        this.addSubCommand(new ChildCommand());
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }
}

public class ChildCommand extends AbstractAsyncCommand {
    public ChildCommand() {
        super("child", "desc");
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }
}
```

### Mixed Permission Model

Parent skips permission generation; each child decides individually:

```java
public class ParentCommand extends AbstractCommandCollection {
    public ParentCommand() {
        super("parent", "desc");
        this.addSubCommand(new PublicCommand());  // No permission
        this.addSubCommand(new AdminCommand());   // Requires permission
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }
}

public class PublicCommand extends AbstractPlayerCommand {
    @Override
    protected boolean canGeneratePermission() {
        return false;
    }
}

public class AdminCommand extends AbstractPlayerCommand {
    public AdminCommand() {
        super("admin", "desc");
        this.requirePermission("myplugin.admin.command");
    }
}
```

---

## Command Variants & Aliases

Use `addUsageVariant()` for alternate forms of the same command, and `addAliases()` for shorthand names:

```java
public class GiveCommand extends AbstractPlayerCommand {
    private final RequiredArg<String> itemArg;

    public GiveCommand() {
        super("give", "Give item to yourself");
        this.itemArg = withRequiredArg("item", "Item", ArgTypes.STRING);
        addUsageVariant(new GiveOtherCommand());
        addAliases("gv", "gMe");
    }
    // execute...
}

// Variant - NOTE: no command name in super()
public static class GiveOtherCommand extends AbstractAsyncCommand {
    private final RequiredArg<String> itemArg;
    private final RequiredArg<String> playerArg;

    public GiveOtherCommand() {
        super("Give item to another player"); // description only
        this.playerArg = withRequiredArg("player", "Target Player", ArgTypes.PLAYER_REF);
        this.itemArg = withRequiredArg("item", "Item", ArgTypes.STRING);
    }
}
```

---

## Subcommands & Command Collections

Group commands under a parent using `AbstractCommandCollection`:

```
/admin
    |-- user
    |     |-- rules
    |     |-- teleport
    |-- server
          |-- restart
```

```java
public class UserCommandCollection extends AbstractCommandCollection {
    public UserCommandCollection() {
        super("user", "User commands");
        addSubCommand(new RulesCommand());
        addSubCommand(new TeleportCommand());
    }
}

public class AdminCommand extends AbstractCommandCollection {
    public AdminCommand() {
        super("admin", "Admin commands");
        addSubCommand(new UserCommandCollection());
        addSubCommand(new ServerCommandCollection());
    }
}
```

> `AbstractCommandCollection` itself cannot execute - all logic must be in subcommands. But you can nest collections within collections.

---

## Registration

Register commands in your plugin's `setup()` method:

```java
public class MyPlugin extends JavaPlugin {
    @Override
    public void setup() {
        this.getCommandRegistry().registerCommand(new ExampleCommand());

        // Or:
        CommandRegistry registry = getCommandRegistry();
        registry.registerCommand(new ExampleCommand());
    }
}
```

---

## Sending Messages

```java
// To player via PlayerRef
playerRef.sendMessage(Message.raw("Hello!"));

// With color (use hex string, NOT int)
playerRef.sendMessage(Message.raw("Success!").color("#55FF55"));

// Chained messages using insert()
Message msg = Message.raw("Prefix: ").color("#AAAAAA")
    .insert(Message.raw("Value").color("#FFFFFF"));
playerRef.sendMessage(msg);

// Via CommandContext (works for console and player)
context.sendMessage(Message.raw("Message"));
```

---

## Edge Cases & Gotchas

- `AbstractAsyncCommand` runs on a background thread - cannot safely access `Store`/`Ref` without getting the world first
- `AbstractPlayerCommand` runs on the world thread - long IO operations will cause lag
- Required args are parsed left-to-right positionally; optional/default args use `--key value` syntax
- Flag args are boolean switches using `--name` syntax
- When getting argument values, always pass `commandContext`: `myArg.get(commandContext)`
- `addUsageVariant()` variants must NOT pass a command name to `super()` - only pass the description
- `canGeneratePermission()` must return `false` on BOTH parent and child for fully public subcommands
- `setPermissionGroup(null)` alone is NOT sufficient if auto-generated permissions are still active

---

## Related Packages

- `com.hypixel.hytale.server.core.command.system.AbstractCommand`
- `com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand`
- `com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand`
- `com.hypixel.hytale.server.core.command.system.basecommands.AbstractTargetPlayerCommand`
- `com.hypixel.hytale.server.core.command.system.basecommands.AbstractTargetEntityCommand`
- `com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection`
- `com.hypixel.hytale.codec.validation.Validator`
- `com.hypixel.hytale.codec.validation.Validators`
