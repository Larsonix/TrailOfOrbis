# Hytale Plugin Configuration System

How Hytale plugins create, load, save, and manage configuration files. Covers both the native `Config<T>` / `BuilderCodec` system provided by the engine and TrailOfOrbis's custom YAML-based ConfigManager pattern.

> **Source**: [Creating a Configuration File](https://docs.hytalemodding.com/guides/plugin/creating-configuration-file) upstream guide + decompiled `Config`, `PluginBase`, `BuilderCodec`, `KeyedCodec` classes.

## Quick Reference

```java
// Native Hytale config (JSON, BuilderCodec-based)
Config<MyConfig> config = this.withConfig("MyConfig", MyConfig.CODEC);
config.save();                    // creates file if missing, saves current state
MyConfig data = config.get();     // get the loaded config object
data.setSomeValue(42);
config.save();                    // persist changes

// TrailOfOrbis custom config (YAML, SnakeYAML-based)
ConfigManager configManager = new ConfigManager(dataFolder);
configManager.loadConfigs();
RPGConfig rpg = configManager.getConfig();
configManager.reloadConfigs();    // hot-reload all configs
```

## Config File Location

### Data Directory

Each plugin gets a unique data directory at:

```
mods/<group>_<pluginName>/
```

The path is constructed by `PendingLoadJavaPlugin`:

```java
// From decompiled source
Path dataDirectory = PluginManager.MODS_PATH.resolve(
    manifest.getGroup() + "_" + manifest.getName()
);
```

For TrailOfOrbis this resolves to `mods/trailoforbis_TrailOfOrbis/`.

### Native Config Files

The native `Config<T>` class stores files as **JSON** directly inside the data directory:

```
mods/trailoforbis_TrailOfOrbis/MyConfig.json
```

The `.json` extension is appended automatically by the `Config` constructor:

```java
// From Config.java
public Config(@Nonnull Path path, String name, BuilderCodec<T> codec) {
    this.path = path.resolve(name + ".json");
    // ...
}
```

### TrailOfOrbis Config Files

Our plugin uses a `config/` subdirectory with YAML files:

```
mods/trailoforbis_TrailOfOrbis/config/
    config.yml
    mob-scaling.yml
    leveling.yml
    gear-balance.yml
    ... (26 files total)
```

This is set up in `ConfigManager`:

```java
public ConfigManager(Path dataFolder) {
    this.configDir = dataFolder.resolve("config");
}
```

### Accessing the Data Directory

From any class extending `JavaPlugin` or `PluginBase`:

```java
// In the plugin constructor
Path dataFolder = init.getDataDirectory();

// After construction, from the plugin instance
Path dataFolder = plugin.getDataDirectory();
```

`init` is the `JavaPluginInit` object passed to the plugin constructor.

## Creating Config Files (Native System)

### Step 1: Define a Config Class with BuilderCodec

Every config class needs a `BuilderCodec<T>` that defines how fields are serialized/deserialized. **Keys must start with an uppercase letter** -- the engine enforces this and throws `IllegalArgumentException` if violated.

```java
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

public class MyConfig {
    public static final BuilderCodec<MyConfig> CODEC = BuilderCodec.builder(MyConfig.class, MyConfig::new)
            .append(new KeyedCodec<Integer>("SomeValue", Codec.INTEGER),
                    (config, value) -> config.someValue = value,
                    (config) -> config.someValue).add()
            .append(new KeyedCodec<String>("SomeString", Codec.STRING),
                    (config, value) -> config.someString = value,
                    (config) -> config.someString).add()
            .build();

    private int someValue = 12;
    private String someString = "My default string";

    public MyConfig() {}

    public int getSomeValue() { return someValue; }
    public void setSomeValue(int someValue) { this.someValue = someValue; }

    public String getSomeString() { return someString; }
    public void setSomeString(String someString) { this.someString = someString; }
}
```

### Step 2: Register and Load the Config

In your `JavaPlugin` class, call `withConfig()` to register the config. This **must** be called before `setup()` -- the engine throws `IllegalStateException` if called after.

```java
public class ExamplePlugin extends JavaPlugin {
    private final Config<MyConfig> config = this.withConfig("MyConfig", MyConfig.CODEC);

    @Override
    public void setup() {
        config.save(); // Creates the file if it doesn't exist
    }
}
```

- `withConfig("MyConfig", codec)` registers a config file named `MyConfig.json`
- `withConfig(codec)` (no name) defaults to `"config"` -> `config.json`
- Registered configs are automatically loaded during `preLoad()` before `setup()` is called

### Step 3: Use the Config

```java
MyConfig data = config.get();
int value = data.getSomeValue();

// Modify and persist
data.setSomeValue(999);
data.setSomeString("Updated value");
config.save(); // Writes changes to disk (async)
```

## Available Codec Types

The `Codec` class provides built-in codecs for common types:

| Codec | Java Type |
|-------|-----------|
| `Codec.STRING` | `String` |
| `Codec.INTEGER` | `int` / `Integer` |
| `Codec.LONG` | `long` / `Long` |
| `Codec.FLOAT` | `float` / `Float` |
| `Codec.DOUBLE` | `double` / `Double` |
| `Codec.BOOLEAN` | `boolean` / `Boolean` |
| `Codec.PATH` | `java.nio.file.Path` |
| `Codec.UUID_STRING` | `java.util.UUID` |
| `Codec.INSTANT` | `java.time.Instant` |
| `Codec.DURATION` | `java.time.Duration` |
| `Codec.DURATION_DOUBLE_SECONDS` | `Duration` (encoded as seconds) |
| `Codec.STRING_ARRAY` | `String[]` |
| `Codec.FLOAT_ARRAY` | `float[]` |
| `Codec.DOUBLE_ARRAY` | `double[]` |
| `Codec.LONG_ARRAY` | `long[]` |
| `Codec.LOG_LEVEL` | `java.util.logging.Level` |

For enums, use `EnumCodec`:

```java
import com.hypixel.hytale.codec.codecs.EnumCodec;

new KeyedCodec<MyEnum>("Mode", new EnumCodec<>(MyEnum.class))
```

For lists, maps, and nested objects, compose codecs using `BuilderCodec` and the codec combinators from the `com.hypixel.hytale.codec` package.

## Reading Config Values

### Native Pattern

```java
// Get the config object (blocks if still loading)
MyConfig data = config.get();

// Access values directly
int value = data.getSomeValue();
String str = data.getSomeString();
```

The `get()` method:
- Returns the cached config object if already loaded
- Blocks (via `CompletableFuture.join()`) if loading is in progress
- Throws `IllegalStateException` if `load()` was never called

### Async Loading

The engine loads configs asynchronously during `preLoad()`:

```java
// PluginBase.preLoad() — called automatically before setup()
CompletableFuture<Void> preLoad() {
    CompletableFuture<?>[] futures = new CompletableFuture[configs.size()];
    for (int i = 0; i < configs.size(); i++) {
        futures[i] = configs.get(i).load();
    }
    return CompletableFuture.allOf(futures);
}
```

By the time `setup()` runs, all registered configs are loaded and `config.get()` returns immediately.

## Default Values and Merging

### How Defaults Work (Native)

1. **First run** (file doesn't exist): `Config.load()` calls `codec.getDefaultValue()`, which invokes the no-arg constructor (`MyConfig::new`). The config object has whatever defaults the constructor sets. No file is created automatically.
2. **`config.save()`**: Serializes the current state to JSON and writes to disk. Call this in `setup()` to create the file with defaults on first run.
3. **Subsequent runs** (file exists): `Config.load()` reads and deserializes the JSON file. Fields present in the file override defaults; missing fields keep their default values from the constructor.

### No Automatic Merging

The native system does **not** merge new fields into existing config files. If you add a new `KeyedCodec` field to your codec, existing config files won't have that key. The field will use its default value (from the constructor), but the file won't be updated until `config.save()` is called.

### Backup Files

`BsonUtil.writeDocument()` creates a `.bak` backup of the previous config file before writing. If the primary file is corrupted, the engine falls back to the backup via `RawJsonReader.readSyncWithBak()`.

## Saving Configs

### Native Save

```java
// Async save — returns CompletableFuture<Void>
config.save();

// The save is non-blocking. The file write happens on a background thread.
// BsonUtil.writeDocument() handles:
//   1. Creating parent directories if needed
//   2. Backing up the existing file (.bak)
//   3. Writing pretty-printed JSON atomically
```

The output format is strict JSON with indentation (configured via `BsonUtil.SETTINGS`).

### Save Timing

- Call `save()` in `setup()` to ensure the file exists with defaults
- Call `save()` after modifying config values to persist changes
- The engine does **not** auto-save configs on shutdown

## Hot Reload

### Native System

The native `Config<T>` class does not provide a built-in reload mechanism. To reload:

```java
// Manual reload (re-read from disk)
config.load().join(); // blocks until loaded
MyConfig fresh = config.get();
```

### TrailOfOrbis Reload Pattern

Our `ConfigManager` supports hot-reload via `reloadConfigs()`:

```java
// Triggered by /tooadmin reload command
boolean success = configManager.reloadConfigs();
```

This re-reads all 26+ YAML files from disk, re-validates them, and replaces the in-memory config objects. All managers that hold references to config objects will see updated values on their next access.

## TrailOfOrbis ConfigManager Pattern

TrailOfOrbis does **not** use the native `Config<T>` / `BuilderCodec` system. Instead, it uses SnakeYAML for human-editable YAML config files. This was chosen because:

1. YAML supports comments (JSON does not)
2. YAML is more readable for server operators
3. Config files serve as documentation via inline comments

### Architecture

```
ConfigManager (implements ConfigService)
    ├── loadConfigs()        — loads all configs, creates defaults if missing
    ├── reloadConfigs()      — re-reads all configs from disk (hot-reload)
    ├── loadGearConfigs()    — loads gear-specific configs (separate phase)
    ├── getConfig()          — returns main RPGConfig
    ├── getMobScalingConfig()
    ├── getLevelingConfig()
    └── ... (20+ config accessors)
```

### Config Loading Flow

```java
// 1. Plugin constructor receives data folder
this.dataFolder = init.getDataDirectory();

// 2. ConfigManager is created with data folder
configManager = new ConfigManager(dataFolder);
// → configDir = dataFolder.resolve("config")

// 3. loadConfigs() is called during plugin initialization
configManager.loadConfigs();

// 4. For each config file:
//    a. Check if file exists in configDir
//    b. If missing, copy from JAR resources (preserves comments)
//    c. Parse YAML using SnakeYAML
//    d. Validate loaded config
//    e. Log summary
```

### Default Config Creation

When a config file is missing, `ConfigManager` copies the bundled template from JAR resources:

```java
private <T> void saveDefaultConfig(String filename, T config) throws IOException {
    // Try to copy from bundled resources first (preserves comments)
    try (InputStream resourceStream = getClass().getClassLoader()
            .getResourceAsStream("config/" + filename)) {
        if (resourceStream != null) {
            Files.copy(resourceStream, configPath);
            return;
        }
    }

    // Fallback: programmatic dump (no comments)
    Yaml yaml = new Yaml();
    try (Writer writer = Files.newBufferedWriter(configPath)) {
        yaml.dump(config, writer);
    }
}
```

The JAR-bundled templates at `src/main/resources/config/` include comments, formatting, and documentation. The fallback programmatic dump is only used if the resource is missing.

### Custom Loading Patterns

Some configs use custom `fromYaml()` parsers instead of SnakeYAML bean mapping:

```java
// InventoryDetectionConfig, CombatTextColorConfig
Yaml yaml = new Yaml();
Object loaded = yaml.load(input);
if (loaded instanceof Map) {
    return MyConfig.fromYaml((Map<String, Object>) loaded);
}
```

This pattern is used when the config structure has nested maps or complex types that don't map cleanly to SnakeYAML's bean-style deserialization.

### Config Validation

Most config classes include a `validate()` method that throws a `ConfigValidationException` on invalid values:

```java
public void validate() throws ConfigValidationException {
    if (maxLevel < 1) throw new ConfigValidationException("maxLevel must be >= 1");
    if (baseXp <= 0) throw new ConfigValidationException("baseXp must be > 0");
}
```

Validation failures during `loadConfigs()` cause the entire plugin to fail initialization.

## Key Imports

```java
// Native config system
import com.hypixel.hytale.server.core.util.Config;          // Config<T> wrapper
import com.hypixel.hytale.codec.Codec;                       // Built-in codecs
import com.hypixel.hytale.codec.KeyedCodec;                  // Named codec field
import com.hypixel.hytale.codec.builder.BuilderCodec;        // Codec builder
import com.hypixel.hytale.codec.codecs.EnumCodec;            // Enum serialization
import com.hypixel.hytale.server.core.util.BsonUtil;         // JSON file I/O

// Plugin infrastructure
import com.hypixel.hytale.server.core.plugin.JavaPlugin;     // Base plugin class
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;  // Plugin init data
import com.hypixel.hytale.server.core.plugin.PluginBase;      // withConfig(), getDataDirectory()
```

## Plugin Lifecycle and Config Timing

```
Constructor (PluginBase)
  ├─ withConfig() calls allowed here (registers configs)
  │
preLoad() ← engine calls this automatically
  ├─ All registered Config<T> objects are loaded asynchronously
  │
setup()
  ├─ config.get() is safe — data is loaded
  ├─ config.save() — create file if missing
  │
start()
  ├─ Full plugin functionality available
  │
shutdown()
  ├─ Clean up, save final state if needed
```

**Critical**: `withConfig()` must be called before `setup()`. The engine throws `IllegalStateException` if called after the plugin enters the SETUP state.

## Edge Cases and Gotchas

### Server Config Location vs. JAR Resources

**CRITICAL**: The server loads configs from `mods/<group>_<pluginName>/config/`, NOT from the JAR's bundled resources. When you modify a YAML config in `src/main/resources/config/`, you **must** also copy it to the server:

```bash
# After editing src/main/resources/config/mob-scaling.yml
cp src/main/resources/config/mob-scaling.yml \
   "/mnt/c/Users/.../mods/trailoforbis_TrailOfOrbis/config/"
```

Or use the deploy script which syncs all configs automatically:

```bash
./scripts/deploy.sh
```

Forgetting to sync configs is the most common cause of "it works in tests but not on server" bugs.

### KeyedCodec Key Capitalization

Keys in `KeyedCodec` **must** start with an uppercase letter. The constructor validates this:

```java
// From KeyedCodec.java
char firstCharFromKey = key.charAt(0);
if (Character.isLetter(firstCharFromKey) && !Character.isUpperCase(firstCharFromKey)) {
    throw new IllegalArgumentException(
        "Key must start with an upper case character! Key: '" + key + "'");
}
```

This means config JSON keys are `"SomeValue"`, `"MaxHealth"`, etc. -- not `"someValue"` or `"max_health"`.

### Native Config Format is JSON, Not YAML

The native `Config<T>` system reads and writes **JSON** files (via BSON codec layer), not YAML. The files have a `.json` extension and use strict JSON syntax with indentation.

### Config.get() Throws If Not Loaded

Calling `config.get()` before `load()` has been called (or before `preLoad()` completes) throws `IllegalStateException`. Always access config values in `setup()` or later.

### save() Is Asynchronous

`Config.save()` returns a `CompletableFuture<Void>`. The file write happens on a background thread. If the server shuts down immediately after saving, the write may not complete. For critical saves, call `.join()` on the returned future.

### No Config File Watching

Neither the native system nor TrailOfOrbis's ConfigManager watches files for changes. Reload must be triggered explicitly (e.g., via a command like `/tooadmin reload`).

### SnakeYAML Empty File Handling

If a YAML config file exists but is empty, SnakeYAML's `loadAs()` returns `null`. ConfigManager falls back to defaults:

```java
T loaded = yaml.loadAs(input, configClass);
if (loaded == null) {
    LOGGER.atSevere().log("Config file %s is empty, using defaults", filename);
    return defaultConfig;
}
```

## Reference

- Decompiled source: `com/hypixel/hytale/server/core/util/Config.java`
- Decompiled source: `com/hypixel/hytale/server/core/plugin/PluginBase.java` (lines 108-121: `withConfig()`)
- Decompiled source: `com/hypixel/hytale/codec/builder/BuilderCodec.java`
- Decompiled source: `com/hypixel/hytale/codec/KeyedCodec.java`
- Decompiled source: `com/hypixel/hytale/server/core/util/BsonUtil.java`
- Plugin source: `src/main/java/io/github/larsonix/trailoforbis/config/ConfigManager.java`
- Plugin source: `src/main/java/io/github/larsonix/trailoforbis/api/services/ConfigService.java`
- Upstream guide: `guides/plugin/creating-configuration-file`
