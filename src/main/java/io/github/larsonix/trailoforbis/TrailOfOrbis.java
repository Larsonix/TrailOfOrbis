package io.github.larsonix.trailoforbis;

import com.hypixel.hytale.logger.HytaleLogger;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.event.EventPriority;
import com.hypixel.hytale.event.EventRegistry;
import io.github.larsonix.trailoforbis.listeners.InventoryChangeEventSystem;
import com.hypixel.hytale.server.core.event.events.player.PlayerConnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.DrainPlayerFromWorldEvent;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import io.github.larsonix.trailoforbis.api.ServiceRegistry;
import io.github.larsonix.trailoforbis.api.services.AttributeService;
import io.github.larsonix.trailoforbis.api.services.ConfigService;
import io.github.larsonix.trailoforbis.api.services.SkillTreeMapService;
import io.github.larsonix.trailoforbis.api.services.SkillTreeService;
import io.github.larsonix.trailoforbis.api.services.UIService;
import io.github.larsonix.trailoforbis.attributes.AttributeManager;
import io.github.larsonix.trailoforbis.attributes.ComputedStats;
import io.github.larsonix.trailoforbis.attributes.StatMapBridge;
import io.github.larsonix.trailoforbis.systems.StatsApplicationSystem;
import io.github.larsonix.trailoforbis.config.ConfigManager;
import io.github.larsonix.trailoforbis.database.DataManager;
import io.github.larsonix.trailoforbis.database.repository.PlayerDataRepository;
import io.github.larsonix.trailoforbis.systems.HytaleStatProvider;
import io.github.larsonix.trailoforbis.commands.TooCommand;
import io.github.larsonix.trailoforbis.commands.TooAdminCommand;
import io.github.larsonix.trailoforbis.commands.shortcuts.AttrShortcutCommand;
import io.github.larsonix.trailoforbis.commands.shortcuts.SanctumShortcutCommand;
import io.github.larsonix.trailoforbis.commands.shortcuts.SkillTreeShortcutCommand;
import io.github.larsonix.trailoforbis.commands.shortcuts.StatsShortcutCommand;
import io.github.larsonix.trailoforbis.leveling.LevelUpCelebrationService;
import io.github.larsonix.trailoforbis.util.EmoteCelebrationHelper;
import io.github.larsonix.trailoforbis.skilltree.NodeAllocationFeedbackService;
import io.github.larsonix.trailoforbis.leveling.api.LevelingService;
import io.github.larsonix.trailoforbis.leveling.config.LevelingConfig;
import io.github.larsonix.trailoforbis.leveling.core.LevelingManager;
import io.github.larsonix.trailoforbis.leveling.formula.EffortBasedFormula;
import io.github.larsonix.trailoforbis.leveling.formula.EffortCurve;
import io.github.larsonix.trailoforbis.leveling.formula.ExponentialFormula;
import io.github.larsonix.trailoforbis.leveling.formula.LevelFormula;
import io.github.larsonix.trailoforbis.leveling.formula.MobXpEstimator;
import io.github.larsonix.trailoforbis.mobs.stats.MobStatPoolConfig;
import io.github.larsonix.trailoforbis.leveling.repository.LevelingRepository;
import io.github.larsonix.trailoforbis.leveling.systems.XpGainSystem;
import io.github.larsonix.trailoforbis.leveling.systems.XpLossSystem;
import io.github.larsonix.trailoforbis.leveling.xp.MobStatsXpCalculator;
import io.github.larsonix.trailoforbis.listeners.EquipmentChangeListener;
import io.github.larsonix.trailoforbis.listeners.PlayerJoinListener;
import io.github.larsonix.trailoforbis.ui.UIManager;
import io.github.larsonix.trailoforbis.combat.EnergyShieldTracker;
import io.github.larsonix.trailoforbis.combat.RPGDamageSystem;
import io.github.larsonix.trailoforbis.combat.RPGDamageIndicatorSuppressor;
import io.github.larsonix.trailoforbis.combat.projectile.ProjectileConfig;
import io.github.larsonix.trailoforbis.combat.projectile.ProjectileSystem;
import io.github.larsonix.trailoforbis.combat.deathrecap.DeathRecapConfig;
import io.github.larsonix.trailoforbis.combat.deathrecap.DeathRecapSystem;
import io.github.larsonix.trailoforbis.combat.deathrecap.KillFeedDecedentSystem;
import io.github.larsonix.trailoforbis.combat.deathrecap.KillFeedKillerSystem;
import io.github.larsonix.trailoforbis.combat.deathrecap.DeathRecapTracker;
import io.github.larsonix.trailoforbis.ailments.AilmentCalculator;
import io.github.larsonix.trailoforbis.ailments.AilmentEffectManager;
import io.github.larsonix.trailoforbis.ailments.AilmentTickSystem;
import io.github.larsonix.trailoforbis.ailments.AilmentTracker;
import io.github.larsonix.trailoforbis.ailments.config.AilmentConfig;
import io.github.larsonix.trailoforbis.systems.RegenerationTickSystem;
import io.github.larsonix.trailoforbis.skilltree.SkillTreeManager;
import io.github.larsonix.trailoforbis.skilltree.conditional.ConditionalTriggerSystem;
import io.github.larsonix.trailoforbis.skilltree.config.SkillTreeConfig;
import io.github.larsonix.trailoforbis.sanctum.SkillSanctumManager;
import io.github.larsonix.trailoforbis.sanctum.components.SkillNodeComponent;
import io.github.larsonix.trailoforbis.sanctum.components.SkillNodeSubtitleComponent;
import io.github.larsonix.trailoforbis.gear.GearManager;
import io.github.larsonix.trailoforbis.gear.item.ItemRegistryService;
import io.github.larsonix.trailoforbis.gems.GemManager;
import io.github.larsonix.trailoforbis.stones.ui.StoneApplicationService;
import io.github.larsonix.trailoforbis.stones.tooltip.StoneTooltipSyncService;
import io.github.larsonix.trailoforbis.maps.components.RealmMobComponent;
import io.github.larsonix.trailoforbis.maps.components.RealmPlayerComponent;
import io.github.larsonix.trailoforbis.ui.inventory.InventoryDetectionManager;
import io.github.larsonix.trailoforbis.ui.hud.XpBarHudManager;
import io.github.larsonix.trailoforbis.skilltree.map.SkillTreeMapManager;
import io.github.larsonix.trailoforbis.skilltree.repository.SkillTreeRepository;
import io.github.larsonix.trailoforbis.compat.HexcodeCompat;
import io.github.larsonix.trailoforbis.compat.HexcodeSkillTreeOverlay;
import io.github.larsonix.trailoforbis.compat.HytaleAPICompat;
import com.hypixel.hytale.server.core.permissions.PermissionsModule;
import io.github.larsonix.trailoforbis.mobs.MobScalingConfig;
import io.github.larsonix.trailoforbis.mobs.MobScalingManager;
import io.github.larsonix.trailoforbis.mobs.MobScalingService;
import io.github.larsonix.trailoforbis.mobs.component.MobScalingComponent;
import io.github.larsonix.trailoforbis.mobs.spawn.component.RPGSpawnedMarker;
import io.github.larsonix.trailoforbis.mobs.stats.MobStatComponent;
import io.github.larsonix.trailoforbis.mobs.systems.MobLevelRefreshSystem;
import io.github.larsonix.trailoforbis.mobs.systems.MobRegenerationSystem;
import io.github.larsonix.trailoforbis.mobs.systems.DeferredSpawnSystem;
import io.github.larsonix.trailoforbis.mobs.systems.MobScalingSystem;
import io.github.larsonix.trailoforbis.gear.systems.GameModeChangeSystem;
import io.github.larsonix.trailoforbis.gear.systems.WeaponSlotChangeSystem;
import io.github.larsonix.trailoforbis.gear.systems.HotbarSlotTrackingSystem;
import io.github.larsonix.trailoforbis.config.RPGConfig;
import io.github.larsonix.trailoforbis.maps.RealmsManager;
import io.github.larsonix.trailoforbis.maps.config.RealmsConfigLoader;
import io.github.larsonix.trailoforbis.maps.api.RealmsService;
import io.github.larsonix.trailoforbis.maps.listeners.RealmMapDropListener;
import io.github.larsonix.trailoforbis.maps.listeners.RealmMapUsePageSupplier;
import io.github.larsonix.trailoforbis.maps.listeners.RealmPortalDevicePageSupplier;
import io.github.larsonix.trailoforbis.maps.listeners.RealmMobDeathListener;
import io.github.larsonix.trailoforbis.maps.listeners.RealmPlayerDeathListener;
import io.github.larsonix.trailoforbis.maps.config.RealmsConfig;
import io.github.larsonix.trailoforbis.maps.reward.RealmRewardService;
import io.github.larsonix.trailoforbis.maps.reward.RewardChestInterceptor;
import io.github.larsonix.trailoforbis.maps.reward.RewardChestManager;
import io.github.larsonix.trailoforbis.maps.reward.VictoryRewardConfig;
import io.github.larsonix.trailoforbis.maps.reward.VictoryRewardDistributor;
import io.github.larsonix.trailoforbis.maps.reward.VictoryRewardGenerator;
import io.github.larsonix.trailoforbis.gear.model.GearRarity;
import io.github.larsonix.trailoforbis.maps.spawning.RealmPassiveNPCRemover;
import io.github.larsonix.trailoforbis.maps.systems.RealmAttackSpeedSystem;
import io.github.larsonix.trailoforbis.maps.systems.RealmMobSpeedCacheSystem;
import io.github.larsonix.trailoforbis.maps.systems.RealmModifierSystem;
import io.github.larsonix.trailoforbis.maps.systems.RealmTimerSystem;
import io.github.larsonix.trailoforbis.stones.StoneActionRegistry;
import io.github.larsonix.trailoforbis.stones.StoneDropListener;
import io.github.larsonix.trailoforbis.stones.ui.StonePickerPageSupplier;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.OpenCustomUIInteraction;
import io.github.larsonix.trailoforbis.gear.config.GearBalanceConfig;
import io.github.larsonix.trailoforbis.gear.config.ModifierConfig;
import io.github.larsonix.trailoforbis.gear.equipment.EquipmentValidator;
import io.github.larsonix.trailoforbis.gear.equipment.RequirementCalculator;
import io.github.larsonix.trailoforbis.gear.generation.GearGenerator;
import io.github.larsonix.trailoforbis.gear.listener.GearEquipmentListener;
import io.github.larsonix.trailoforbis.gear.sync.ItemSyncCoordinator;
import io.github.larsonix.trailoforbis.gear.listener.UnifiedPickupListener;
import io.github.larsonix.trailoforbis.gear.tooltip.TooltipConfig;
import io.github.larsonix.trailoforbis.gear.loot.LootCalculator;
import io.github.larsonix.trailoforbis.gear.loot.LootGenerator;
import io.github.larsonix.trailoforbis.gear.loot.LootItemsConfig;
import io.github.larsonix.trailoforbis.gear.loot.LootListener;
import io.github.larsonix.trailoforbis.gear.loot.LootSettings;
import io.github.larsonix.trailoforbis.gear.stats.GearBonusProvider;
import io.github.larsonix.trailoforbis.gear.stats.GearStatApplier;
import io.github.larsonix.trailoforbis.gear.stats.GearStatCalculator;
import io.github.larsonix.trailoforbis.gear.item.ItemDefinitionBuilder;
import io.github.larsonix.trailoforbis.gear.item.ItemSyncConfig;
import io.github.larsonix.trailoforbis.gear.item.ItemSyncService;
import io.github.larsonix.trailoforbis.gear.item.TranslationSyncService;
import io.github.larsonix.trailoforbis.gear.loot.VanillaGearDropSuppressor;
import io.github.larsonix.trailoforbis.gear.systems.ContainerSyncTickSystem;
import io.github.larsonix.trailoforbis.loot.container.ProcessedContainerResource;
import io.github.larsonix.trailoforbis.gear.systems.ItemEntitySpawnSyncSystem;
import io.github.larsonix.trailoforbis.gear.systems.RPGItemPreSyncSystem;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import io.github.larsonix.trailoforbis.util.PlayerWorldCache;
import org.yaml.snakeyaml.Yaml;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Main plugin class for TrailOfOrbis.
 *
 * <p>This plugin provides a full RPG experience including:
 * <ul>
 *   <li>Elemental attribute system (FIRE, WATER, LIGHTNING, EARTH, WIND, VOID)</li>
 *   <li>Computed stats (health, mana, damage, etc.)</li>
 *   <li>LevelingCore integration for XP/leveling</li>
 *   <li>Player data persistence</li>
 * </ul>
 *
 * <p><b>Lifecycle:</b>
 * <ol>
 *   <li>Constructor: Set singleton instance</li>
 *   <li>setup(): Register events and commands (NO I/O)</li>
 *   <li>start(): Load configs, initialize database (I/O allowed)</li>
 *   <li>shutdown(): Save data, cleanup resources</li>
 * </ol>
 */
public class TrailOfOrbis extends JavaPlugin {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static volatile TrailOfOrbis instance;
    private Path dataFolder;

    // Core managers
    private ConfigManager configManager;
    private DataManager dataManager;
    private AttributeManager attributeManager;
    private UIManager uiManager;
    private SkillTreeManager skillTreeManager;
    private RegenerationTickSystem regenerationSystem;
    private MobScalingManager mobScalingManager;

    // Leveling system (native, replaces LevelingCore)
    private LevelingManager levelingManager;
    private MobStatsXpCalculator xpCalculator;
    private LevelUpCelebrationService celebrationService;
    private NodeAllocationFeedbackService nodeAllocationFeedbackService;

    // Mob scaling component type (registered in setup(), used by MobScalingSystem)
    private ComponentType<EntityStore, MobScalingComponent> mobScalingComponentType;

    // New mob stat component type (for Dirichlet-based stat generation)
    private ComponentType<EntityStore, MobStatComponent> mobStatComponentType;

    // RPG spawned marker component type (for spawn loop prevention)
    private ComponentType<EntityStore, RPGSpawnedMarker> rpgSpawnedMarkerType;

    // Energy shield tracker
    private EnergyShieldTracker energyShieldTracker;

    // Death recap system
    private DeathRecapTracker deathRecapTracker;

    // Combat detail mode - tracks which players have detailed damage breakdown enabled
    private final Set<UUID> combatDetailEnabled = ConcurrentHashMap.newKeySet();

    // Ailment system (PoE2-style elemental ailments)
    private AilmentTracker ailmentTracker;
    private AilmentCalculator ailmentCalculator;
    private AilmentEffectManager ailmentEffectManager;
    private AilmentConfig ailmentConfig;

    // Combat trackers (promoted to plugin scope for disconnect cleanup)
    private io.github.larsonix.trailoforbis.combat.tracking.ConsecutiveHitTracker consecutiveHitTracker;
    private io.github.larsonix.trailoforbis.ailments.AilmentImmunityTracker ailmentImmunityTracker;

    // Projectile stats system config (projectile speed/gravity modifiers)
    private ProjectileConfig projectileConfig;

    // Conditional effect trigger system (on-kill, on-crit, threshold bonuses)
    private ConditionalTriggerSystem conditionalTriggerSystem;

    // Gear equipment listener
    private GearEquipmentListener gearEquipmentListener;

    // Unified pickup listener - handles all item pickups (gear, maps, stones)
    private UnifiedPickupListener unifiedPickupListener;

    // Loot system - handles RPG gear drops from mobs
    private LootListener lootListener;

    // Item registry service - registers custom items in server-side asset map
    private ItemRegistryService lootItemRegistryService;

    // Gear manager - handles gear generation, item sync, and equipment stats
    private GearManager gearManager;

    // Gem manager - handles skill gem config loading, socketing, and item creation
    private GemManager gemManager;

    // Realms manager - handles procedural realm instances
    private RealmsManager realmsManager;

    // Stone system listeners and services
    private StoneDropListener stoneDropListener;
    private StoneApplicationService stoneApplicationService;
    private StoneTooltipSyncService stoneTooltipSyncService;

    // Hotbar slot tracking for weapon stat recalculation
    private HotbarSlotTrackingSystem hotbarSlotTrackingSystem;

    // Realm component types (for realm instance tracking)
    private ComponentType<EntityStore, RealmMobComponent> realmMobComponentType;
    private ComponentType<EntityStore, RealmPlayerComponent> realmPlayerComponentType;

    // Skill Sanctum system (3D skill tree exploration)
    private ComponentType<EntityStore, SkillNodeComponent> skillNodeComponentType;
    private ComponentType<EntityStore, SkillNodeSubtitleComponent> skillNodeSubtitleComponentType;
    private SkillSanctumManager skillSanctumManager;

    // Player guide system (contextual milestone popups)
    private io.github.larsonix.trailoforbis.guide.GuideManager guideManager;

    // Party mod integration (PartyPro compatibility)
    private io.github.larsonix.trailoforbis.compat.party.PartyIntegrationManager partyIntegrationManager;

    // Inventory detection system (shows Stats button when inventory is open)
    private InventoryDetectionManager inventoryDetectionManager;

    // XP bar HUD (persistent above hotbar)
    private XpBarHudManager xpBarHudManager;

    // Animation speed sync (visual swing speed matching attack speed stat)
    private io.github.larsonix.trailoforbis.combat.attackspeed.AnimationSpeedSyncManager animationSpeedSyncManager;

    // Loot filter system (blocks pickup of unwanted gear)
    private io.github.larsonix.trailoforbis.lootfilter.LootFilterManager lootFilterManager;

    // Central inventory change event dispatcher (shared across systems)
    private InventoryChangeEventSystem inventoryChangeEventSystem;

    // Colored combat text system (per-element damage number colors)
    private io.github.larsonix.trailoforbis.combat.indicators.color.CombatTextColorManager combatTextColorManager;

    // Persistent container loot tracking (ChunkStore resource, survives restarts)
    private ResourceType<ChunkStore, ProcessedContainerResource> processedContainerResourceType;

    // ==================== CONSTRUCTOR ====================

    /**
     * Creates a new TrailOfOrbis plugin instance.
     *
     * <p><b>CRITICAL:</b> The singleton instance MUST be set in the constructor,
     * not in setup(). This ensures the instance is available before any
     * other initialization occurs.
     */
    public TrailOfOrbis(@Nonnull JavaPluginInit init) {
        super(init);
        instance = this; // CRITICAL: Must be in constructor
        this.dataFolder = init.getDataDirectory();
    }

    // ==================== SETUP (No I/O!) ====================

    /**
     * Sets up the plugin - registers events and commands.
     *
     * <p><b>IMPORTANT:</b> NO I/O operations (file reads, database connections)
     * are allowed in this method. Only lightweight initialization.
     */
    @Override
    protected void setup() {
        getLogger().atInfo().log("Setting up TrailOfOrbis v%s...", getManifest().getVersion());

        // Phase 1: Lightweight manager instantiation (NO I/O)
        configManager = new ConfigManager(dataFolder);
        // Note: DataManager requires RPGConfig, created in start()

        // Set config directory for layout config (actual loading happens lazily)
        // Note: configManager.getConfigDir() already returns the config/ subdirectory
        io.github.larsonix.trailoforbis.ui.skilltree.SkillTreeLayout.setConfigDirectory(
            configManager.getConfigDir());

        // Phase 1.5: Register ChunkStore resources (persistent world-level data)
        processedContainerResourceType = getChunkStoreRegistry().registerResource(
            ProcessedContainerResource.class,
            "ToO_ProcessedContainers",
            ProcessedContainerResource.CODEC);

        // Phase 2: Register event listeners
        // =====================================================================
        // EVENT PRIORITY STRATEGY:
        //   EARLY  - Data loading/initialization (must complete before others)
        //   NORMAL - Standard processing
        //   LATE   - Cleanup, saving, and dependent operations
        // =====================================================================
        EventRegistry eventRegistry = getEventRegistry();

        // PlayerWorldCache: FIRST - Track player→world mapping for O(1) lookups
        PlayerWorldCache.register(eventRegistry);

        // PlayerConnectEvent: FIRST - Register items BEFORE first tick sends inventory
        // CRITICAL: This fires BEFORE PlayerSendInventorySystem.tick()
        // By registering items here, they're in the asset map when inventory is sent
        // This prevents items showing as "?" (unknown.item) on reconnect
        // Note: Uses register() because KeyType=Void
        eventRegistry.register(
            EventPriority.FIRST,
            PlayerConnectEvent.class,
            PlayerJoinListener::onPlayerConnect
        );

        // PlayerReadyEvent: EARLY - Load RPG data BEFORE other plugins need it
        // Other plugins/systems may depend on player stats being available
        // Note: Uses registerGlobal() because KeyType=String
        eventRegistry.registerGlobal(
            EventPriority.EARLY,
            PlayerReadyEvent.class,
            PlayerJoinListener::onPlayerReady
        );

        // DrainPlayerFromWorldEvent: NORMAL - Discard ALL stale HUDs during world transitions.
        // resetManagers() sends CustomHud(clear=true) to the client, destroying all HyUI
        // elements. Any subsequent hide()/Set commands to those cleared elements crash the
        // client. We discard ALL HUD types here — remove from tracking + cancel refresh only.
        eventRegistry.registerGlobal(
            EventPriority.NORMAL,
            DrainPlayerFromWorldEvent.class,
            event -> {
                try {
                    UUIDComponent uuidComp = event.getHolder().getComponent(UUIDComponent.getComponentType());
                    if (uuidComp == null) return;
                    UUID playerId = uuidComp.getUuid();
                    if (xpBarHudManager != null) {
                        xpBarHudManager.discardStaleHud(playerId);
                    }
                    if (skillSanctumManager != null) {
                        skillSanctumManager.getSkillPointHudManager().discardStaleHud(playerId);
                        skillSanctumManager.getSkillNodeHudManager().discardStaleHud(playerId);
                    }
                    if (realmsManager != null) {
                        realmsManager.getHudManager().discardAllHudsForPlayer(playerId);
                    }
                    // Reset Hexcode spell state to prevent crash when CraftingSystem
                    // ticks pedestal refs that point to the old world after teleport
                    HexcodeCompat.forceIdleIfCasting(event.getHolder());
                } catch (Exception e) {
                    // Swallow — cleanup must not break the drain flow
                }
            }
        );

        // PlayerDisconnectEvent: LATE - Save AFTER other plugins finalize state
        // Captures final player state after all other handlers have run
        // Note: Uses register() because KeyType=Void
        eventRegistry.register(
            EventPriority.LATE,
            PlayerDisconnectEvent.class,
            PlayerJoinListener::onPlayerDisconnect
        );

        // Note: InventoryChangeEvent (replaces LivingEntityInventoryChangeEvent) is now an
        // ECS event handled via InventoryChangeEventSystem registered in registerEcsSystems().

        /// Phase 3: Register commands
        // Main command collections
        getCommandRegistry().registerCommand(new TooCommand(this));
        getCommandRegistry().registerCommand(new TooAdminCommand(this));

        // Shortcut commands for convenience
        getCommandRegistry().registerCommand(new StatsShortcutCommand(this));
        getCommandRegistry().registerCommand(new AttrShortcutCommand(this));
        getCommandRegistry().registerCommand(new SkillTreeShortcutCommand(this));
        getCommandRegistry().registerCommand(new SanctumShortcutCommand(this));

        // Phase 4: Register ECS components
        // Components must be registered in setup() so they exist before systems reference them.
        // Systems are registered later in start() per Hytale ECS best practice.

        // MobScalingComponent - tracks mob scaling state
        mobScalingComponentType = getEntityStoreRegistry().registerComponent(
            MobScalingComponent.class,
            "trailoforbis:MobScalingComponent",
            MobScalingComponent.CODEC
        );
        getLogger().atInfo().log("Registered MobScalingComponent (with CODEC)");

        // MobStatComponent - Dirichlet-based stat generation
        mobStatComponentType = getEntityStoreRegistry().registerComponent(
            MobStatComponent.class,
            "trailoforbis:MobStatComponent",
            MobStatComponent.CODEC
        );
        getLogger().atInfo().log("Registered MobStatComponent (with CODEC)");

        // RPGSpawnedMarker - spawn loop prevention
        rpgSpawnedMarkerType = getEntityStoreRegistry().registerComponent(
            RPGSpawnedMarker.class,
            "trailoforbis:RPGSpawnedMarker",
            RPGSpawnedMarker.CODEC
        );
        getLogger().atInfo().log("Registered RPGSpawnedMarker (with CODEC)");

        // Realm components for realm instance tracking
        realmMobComponentType = getEntityStoreRegistry().registerComponent(
            io.github.larsonix.trailoforbis.maps.components.RealmMobComponent.class,
            "trailoforbis:RealmMobComponent",
            io.github.larsonix.trailoforbis.maps.components.RealmMobComponent.CODEC
        );
        io.github.larsonix.trailoforbis.maps.components.RealmMobComponent.TYPE = realmMobComponentType;
        getLogger().atInfo().log("Registered RealmMobComponent (with CODEC)");

        realmPlayerComponentType = getEntityStoreRegistry().registerComponent(
            io.github.larsonix.trailoforbis.maps.components.RealmPlayerComponent.class,
            "trailoforbis:RealmPlayerComponent",
            io.github.larsonix.trailoforbis.maps.components.RealmPlayerComponent.CODEC
        );
        io.github.larsonix.trailoforbis.maps.components.RealmPlayerComponent.TYPE = realmPlayerComponentType;
        getLogger().atInfo().log("Registered RealmPlayerComponent (with CODEC)");

        // SkillNodeComponent for Skill Sanctum node orbs
        skillNodeComponentType = getEntityStoreRegistry().registerComponent(
            SkillNodeComponent.class,
            "trailoforbis:SkillNodeComponent",
            SkillNodeComponent.CODEC
        );
        SkillNodeComponent.TYPE = skillNodeComponentType;
        getLogger().atInfo().log("Registered SkillNodeComponent (with CODEC)");

        // SkillNodeSubtitleComponent for Skill Sanctum subtitle entities
        skillNodeSubtitleComponentType = getEntityStoreRegistry().registerComponent(
            io.github.larsonix.trailoforbis.sanctum.components.SkillNodeSubtitleComponent.class,
            "trailoforbis:SkillNodeSubtitleComponent",
            io.github.larsonix.trailoforbis.sanctum.components.SkillNodeSubtitleComponent.CODEC
        );
        io.github.larsonix.trailoforbis.sanctum.components.SkillNodeSubtitleComponent.setComponentType(skillNodeSubtitleComponentType);
        getLogger().atInfo().log("Registered SkillNodeSubtitleComponent (with CODEC)");

        // Phase 5: Register custom interactions
        registerCustomInteractions();

        getLogger().atInfo().log("TrailOfOrbis setup complete.");
    }

    /**
     * Registers custom interactions with Hytale's Interaction.CODEC.
     *
     * <p>This must be called in setup() (before start()) so interactions
     * are available when block types load their interaction configs.
     */

    /**
     * Grants the Default permission group access to all player-facing commands.
     *
     * <p>Hytale's permission system denies by default — without explicit grants,
     * only OP players can use commands. This method grants the minimum permissions
     * needed for regular players to use the RPG gameplay commands while keeping
     * admin commands ({@code /tooa}) restricted to operators.
     *
     * <p>Called in start() so PermissionsModule is guaranteed to be initialized.
     */
    private void grantDefaultPermissions() {
        try {
            PermissionsModule perms = PermissionsModule.get();
            if (perms == null) {
                getLogger().atWarning().log("PermissionsModule not available - cannot grant default permissions");
                return;
            }

            String base = getBasePermission(); // "trailoforbis.trailoforbis"
            Set<String> playerPermissions = Set.of(
                base + ".command.too.*",       // /too and all subcommands (stats, attr, skilltree, realm, combat, sanctum)
                base + ".command.stats",       // /stats shortcut
                base + ".command.skilltree",   // /skilltree shortcut
                base + ".command.sanctum",     // /sanctum shortcut
                base + ".command.attr",        // /attr shortcut
                base + ".command.lf"           // /lf loot filter
            );

            perms.addGroupPermission("Default", playerPermissions);
            getLogger().atInfo().log("Granted %d player-facing command permissions to Default group", playerPermissions.size());
        } catch (Exception e) {
            getLogger().atWarning().log("Failed to grant default permissions: %s", e.getMessage());
        }
    }

    private void registerCustomInteractions() {
        // Register RealmVictoryPortalInteraction for victory portal exit
        // This custom interaction removes HUDs BEFORE calling exitInstance()
        Interaction.CODEC.register(
            "RealmVictoryPortal",
            io.github.larsonix.trailoforbis.maps.completion.interactions.RealmVictoryPortalInteraction.class,
            io.github.larsonix.trailoforbis.maps.completion.interactions.RealmVictoryPortalInteraction.CODEC
        );
        getLogger().atInfo().log("Registered RealmVictoryPortalInteraction");

        // Register stone picker page supplier so the PAGE_CODEC can resolve "RPG_StonePicker"
        // during asset loading (when inline OpenCustomUI interactions in stone JSONs are deserialized).
        // tryCreate() is only called at runtime (player right-click), so onEnable services are available.
        StonePickerPageSupplier stonePageSupplier = new StonePickerPageSupplier(this);
        OpenCustomUIInteraction.registerCustomPageSupplier(
                this,
                StonePickerPageSupplier.class,
                "RPG_StonePicker",
                stonePageSupplier);
        getLogger().atInfo().log("Registered StonePickerPageSupplier (page ID: RPG_StonePicker)");
    }

    /**
     * Registers all ECS systems with the entity store registry.
     *
     * <p>Called from {@link #start()} after configs and managers are initialized.
     * Systems are registered in start() per Hytale best practice — components
     * define data shapes (setup), systems execute logic (start).
     */
    private void registerEcsSystems() {
        // --- Combat systems ---

        // HexCastEvent interceptor — captures spell caster identity for damage attribution.
        // HexCastEvent is a WorldEventSystem event (same pattern as Hexcode's own
        // HexCastDiagnosticListener). Register via entityStoreRegistry.
        if (HexcodeCompat.isLoaded()) {
            try {
                Class<?> hexCastEventClass = Class.forName("com.riprod.hexcode.api.event.HexCastEvent");
                getEntityStoreRegistry().registerSystem(
                        new io.github.larsonix.trailoforbis.compat.HexCastEventInterceptor(hexCastEventClass));
                getLogger().atInfo().log("Registered HexCastEvent interceptor (WorldEventSystem) for caster tracking");
            } catch (ClassNotFoundException e) {
                getLogger().atWarning().log("HexCastEvent class not found — spell caster tracking disabled");
            }

            // Hex damage attribution — runs in FilterDamageGroup (same as Erode/Fortify)
            // to catch hex spell damage that bypasses RPGDamageSystem. Rewrites
            // EnvironmentSource to EntitySource(caster) for death-time attribution.
            try {
                getEntityStoreRegistry().registerSystem(
                        new io.github.larsonix.trailoforbis.compat.HexDamageAttributionSystem());
                getLogger().atInfo().log("Registered HexDamageAttributionSystem (FilterDamageGroup) for spell kill attribution");
            } catch (Exception e) {
                getLogger().atSevere().log("FAILED to register HexDamageAttributionSystem: %s", e);
            }

            // Casting aura particle injector — sends SpawnModelParticles to Casting_Anchor
            // entities for RPG staffs. The entity tracker's model sync doesn't reliably
            // deliver particles for dynamically spawned entities.
            io.github.larsonix.trailoforbis.compat.CastingAuraInjector.initialize();
            getEntityStoreRegistry().registerSystem(
                    new io.github.larsonix.trailoforbis.compat.CastingAuraTickSystem());
            getLogger().atInfo().log("Registered CastingAuraTickSystem for RPG casting particles");
        }

        // RPGDamageSystem - hooks into Hytale's damage pipeline
        getEntityStoreRegistry().registerSystem(new RPGDamageSystem(this));
        getLogger().atInfo().log("Registered RPGDamageSystem");

        // Damage indicator suppressor - prevents vanilla from showing duplicate indicators
        // CRITICAL: Runs BEFORE EntityUIEvents to zero damage for already-displayed indicators
        getEntityStoreRegistry().registerSystem(new RPGDamageIndicatorSuppressor());
        getLogger().atInfo().log("Registered RPGDamageIndicatorSuppressor");

        // Energy Shield Tracker - per-player shield state for damage absorption
        // 3-second delay before shield starts regenerating after being hit
        energyShieldTracker = new EnergyShieldTracker(3000L);
        getLogger().atInfo().log("Initialized EnergyShieldTracker (regenDelay=%dms)", energyShieldTracker.getRegenDelayMs());

        // Regeneration System - applies health/mana regeneration every second
        regenerationSystem = new RegenerationTickSystem();
        getEntityStoreRegistry().registerSystem(regenerationSystem);
        getLogger().atInfo().log("Registered RegenerationTickSystem");

        // --- Mob systems ---

        // MobScalingSystem - gets config/calculators dynamically from MobScalingManager
        getEntityStoreRegistry().registerSystem(new MobScalingSystem(this));
        getLogger().atInfo().log("Registered MobScalingSystem");

        // RealmPassiveNPCRemover - removes passive wildlife (birds, critters) from realm instances
        getEntityStoreRegistry().registerSystem(new RealmPassiveNPCRemover(this));
        getLogger().atInfo().log("Registered RealmPassiveNPCRemover");

        // DeferredSpawnSystem - processes queued spawn requests outside of store processing
        // This prevents "Store is currently processing!" errors when spawning from onEntityAdd
        getEntityStoreRegistry().registerSystem(new DeferredSpawnSystem(this));
        getLogger().atInfo().log("Registered DeferredSpawnSystem");

        // MobRegenerationSystem - applies health regen to scaled mobs
        getEntityStoreRegistry().registerSystem(new MobRegenerationSystem(this));
        getLogger().atInfo().log("Registered MobRegenerationSystem");

        // --- Equipment tracking systems ---

        // WeaponSlotChangeSystem - triggers stat recalc on utility slot switch (event-based)
        getEntityStoreRegistry().registerSystem(new WeaponSlotChangeSystem());
        getLogger().atInfo().log("Registered WeaponSlotChangeSystem");

        // GameModeChangeSystem - toggles gear requirement bypass on Creative/Adventure switch
        getEntityStoreRegistry().registerSystem(new GameModeChangeSystem());
        getLogger().atInfo().log("Registered GameModeChangeSystem");

        // HotbarSlotTrackingSystem - triggers stat recalc on hotbar slot switch (tick-based)
        // Hotbar changes go through Interaction system without firing events, so we track via tick
        hotbarSlotTrackingSystem = new HotbarSlotTrackingSystem();
        getEntityStoreRegistry().registerSystem(hotbarSlotTrackingSystem);
        getLogger().atInfo().log("Registered HotbarSlotTrackingSystem");

        // InventoryChangeEventSystem - central handler for the new InventoryChangeEvent (ECS event).
        // Replaces the old LivingEntityInventoryChangeEvent registerGlobal() calls.
        // Handlers are added in order of priority: NORMAL first, then LATE.
        inventoryChangeEventSystem = new InventoryChangeEventSystem();
        // NORMAL priority: base stat recalculation on equipment change
        inventoryChangeEventSystem.addHandler(EquipmentChangeListener::onInventoryChange);
        // LATE priority: gear stat recalculation
        if (gearEquipmentListener != null) {
            inventoryChangeEventSystem.addHandler(gearEquipmentListener::onInventoryChange);
        }
        // Pickup notifications: triggers guide milestones on gear/map/stone pickup
        if (gearManager != null && gearManager.getPickupNotificationService() != null) {
            unifiedPickupListener = new UnifiedPickupListener(gearManager.getPickupNotificationService());
            inventoryChangeEventSystem.addHandler(unifiedPickupListener::onInventoryChange);
            unifiedPickupListener.register(getEventRegistry());
        }
        // Reskin data preservation: caches RPG data when items enter Builder's Workbench,
        // re-applies when crafted output appears in inventory
        if (gearManager != null && gearManager.getReskinDataPreserver() != null) {
            inventoryChangeEventSystem.addHandler(gearManager.getReskinDataPreserver()::onInventoryChange);
        }
        // LAST priority: sanctum visual refresh
        if (skillSanctumManager != null) {
            inventoryChangeEventSystem.addHandler(skillSanctumManager::onInventoryChange);
        }
        getEntityStoreRegistry().registerSystem(inventoryChangeEventSystem);
        getLogger().atInfo().log("Registered InventoryChangeEventSystem");

        // --- Realm systems ---

        // RealmModifierSystem - applies realm modifier bonuses to mobs
        getEntityStoreRegistry().registerSystem(new RealmModifierSystem(this));
        getLogger().atInfo().log("Registered RealmModifierSystem");

        // RealmTimerSystem - handles realm timeout checking for players
        getEntityStoreRegistry().registerSystem(new RealmTimerSystem(this));
        getLogger().atInfo().log("Registered RealmTimerSystem");

        // RealmAttackSpeedSystem - applies attack speed modifiers to realm mobs
        getEntityStoreRegistry().registerSystem(new RealmAttackSpeedSystem(this));
        getLogger().atInfo().log("Registered RealmAttackSpeedSystem");

        // RealmMobSpeedCacheSystem - injects movement speed multipliers before steering
        getEntityStoreRegistry().registerSystem(new RealmMobSpeedCacheSystem(this));
        getLogger().atInfo().log("Registered RealmMobSpeedCacheSystem");

        // RealmMobDeathListener - tracks mob deaths within realms
        getEntityStoreRegistry().registerSystem(new RealmMobDeathListener(this));
        getLogger().atInfo().log("Registered RealmMobDeathListener");

        // RealmMapDropListener - drops realm maps from mob deaths
        getEntityStoreRegistry().registerSystem(new RealmMapDropListener(this));
        getLogger().atInfo().log("Registered RealmMapDropListener");

        // StoneDropListener - drops stones (currency items) from mob deaths
        stoneDropListener = new StoneDropListener(this);
        getEntityStoreRegistry().registerSystem(stoneDropListener);
        getLogger().atInfo().log("Registered StoneDropListener");

        // RealmPlayerDeathListener - handles player deaths in realms
        getEntityStoreRegistry().registerSystem(new RealmPlayerDeathListener(this));
        getLogger().atInfo().log("Registered RealmPlayerDeathListener");

        // --- Leveling systems ---

        // XP gain/loss systems (native, replaces LevelingCore)
        getEntityStoreRegistry().registerSystem(new XpGainSystem(this));
        getEntityStoreRegistry().registerSystem(new XpLossSystem(this));
        getLogger().atInfo().log("Registered XpGainSystem and XpLossSystem");

        // DeathRecapSystem - records damage sources for death recap display
        getEntityStoreRegistry().registerSystem(new DeathRecapSystem(this));
        getLogger().atInfo().log("Registered DeathRecapSystem");

        // Kill feed systems - contextual death/kill messages broadcast to all players
        getEntityStoreRegistry().registerSystem(new KillFeedDecedentSystem(this));
        getEntityStoreRegistry().registerSystem(new KillFeedKillerSystem(this));
        getLogger().atInfo().log("Registered KillFeed death message systems");

        // --- Item sync systems ---

        // RPGItemPreSyncSystem - syncs item definitions BEFORE inventory is sent
        // CRITICAL: Has @Dependency(order=BEFORE) on PlayerAddedSystem to prevent "?" glitch
        getEntityStoreRegistry().registerSystem(new RPGItemPreSyncSystem());
        getLogger().atInfo().log("Registered RPGItemPreSyncSystem");

        // ContainerSyncTickSystem - syncs custom item definitions on container open
        getEntityStoreRegistry().registerSystem(new ContainerSyncTickSystem());
        getLogger().atInfo().log("Registered ContainerSyncTickSystem");

        // ItemEntitySpawnSyncSystem - syncs definitions when item entities spawn in world
        getEntityStoreRegistry().registerSystem(new ItemEntitySpawnSyncSystem());
        getLogger().atInfo().log("Registered ItemEntitySpawnSyncSystem");

        // --- Misc systems ---

        // VanillaGearDropSuppressor - suppresses vanilla weapon/armor drops from mobs
        getEntityStoreRegistry().registerSystem(new VanillaGearDropSuppressor(configManager));
        getLogger().atInfo().log("Registered VanillaGearDropSuppressor");

        // ProjectileSystem - modifies projectile velocity/gravity based on player stats
        getEntityStoreRegistry().registerSystem(new ProjectileSystem(this));
        getLogger().atInfo().log("Registered ProjectileSystem");

        getLogger().atInfo().log("All ECS systems registered (%d systems)", 26);
    }

    // ==================== START (I/O allowed) ====================

    /**
     * Starts the plugin - loads configurations and initializes systems.
     *
     * <p>This method performs all I/O operations:
     * <ul>
     *   <li>Load configuration files</li>
     *   <li>Initialize database connections</li>
     *   <li>Initialize managers that require config/database</li>
     *   <li>Set up LevelingCore integration</li>
     * </ul>
     */
    @Override
    protected void start() {
        getLogger().atInfo().log("Starting TrailOfOrbis...");

        // Note: Assets load from JAR - no copying needed.
        // The "Skipping pack at trailoforbis_TrailOfOrbis" message is expected and correct.

        // Phase 1: Load configurations
        if (!configManager.loadConfigs()) {
            getLogger().atSevere().log("Failed to load configuration! Using defaults.");
        }

        // Phase 1.5: Initialize API compatibility layer
        HytaleAPICompat.initialize();

        // Phase 1.5a: Detect Hexcode spell-crafting mod (soft dependency)
        HexcodeCompat.initialize();

        // Phase 1.5a2: Resolve Hexcode stat indices for EntityStatMap bridging
        StatMapBridge.resolveHexcodeStatIndices();

        // Phase 1.5a3: Cache hex interactions from real Hexcode items for magic weapon injection
        ItemRegistryService.initializeHexInteractionCache();

        // Phase 1.5a4: Initialize hex asset map access for RPG item registration
        // Allows pedestal detection, casting particles, and glyph colors to work with rpg_gear_xxx IDs
        HexcodeCompat.initializeHexAssetMaps();

        // Phase 1.5b: Grant Default group permissions for player-facing commands
        // Without this, only OP players can use commands (permissions default to denied).
        grantDefaultPermissions();

        // Phase 1.6: Initialize colored combat text system
        // Must be after HytaleAPICompat (needs asset map to be loaded)
        combatTextColorManager = new io.github.larsonix.trailoforbis.combat.indicators.color.CombatTextColorManager(configManager);
        if (combatTextColorManager.initialize()) {
            getLogger().atInfo().log("Colored combat text system initialized");
        } else {
            getLogger().atInfo().log("Colored combat text system disabled or failed to initialize");
        }

        // Phase 2: Initialize database
        dataManager = new DataManager(dataFolder, configManager.getRPGConfig());
        if (!dataManager.initialize()) {
            getLogger().atSevere().log("Database initialization failed! Plugin cannot function.");
            return;
        }

        // Phase 2.5: Initialize Guide System
        guideManager = new io.github.larsonix.trailoforbis.guide.GuideManager(dataManager);
        getLogger().atInfo().log("Guide system initialized");

        // Phase 3: Initialize AttributeManager
        attributeManager = new AttributeManager(dataManager, configManager, new HytaleStatProvider());

        // Register stats application callback for automatic ECS sync
        // This ensures skill tree allocations, commands, and other stat changes
        // are automatically applied to the player's entity in the ECS
        attributeManager.setStatsApplicationCallback(playerId -> {
            LOGGER.atFine().log("[STATS] Callback entry for player %s", playerId);

            // Find player via cached world lookup
            World playerWorld = PlayerWorldCache.getPlayerWorld(playerId).orElse(null);
            if (playerWorld == null) {
                LOGGER.atFine().log("[STATS] Player %s NOT FOUND in any world - cannot apply ECS stats", playerId);
                return;
            }
            PlayerRef playerRef = PlayerWorldCache.findPlayerRef(playerId, playerWorld);
            if (playerRef == null) {
                LOGGER.atFine().log("[STATS] Player %s NOT FOUND in any world - cannot apply ECS stats", playerId);
                return;
            }
            LOGGER.atFine().log("[STATS] Found player %s in world %s", playerId, playerWorld.getName());

            Ref<EntityStore> entityRef = playerRef.getReference();
            if (entityRef == null || !entityRef.isValid()) {
                LOGGER.atFine().log("[STATS] Entity ref invalid for %s (ref=%s, valid=%s)",
                    playerId, entityRef, entityRef != null ? entityRef.isValid() : "null");
                return;
            }

            ComputedStats stats = attributeManager.getStats(playerId);
            if (stats == null) {
                LOGGER.atWarning().log("[STATS] ComputedStats NULL for %s - cannot apply", playerId);
                return;
            }
            LOGGER.atFine().log("[STATS] ComputedStats ready: HP=%.1f, MovSpeed=%.1f%%",
                stats.getMaxHealth(), stats.getMovementSpeedPercent());

            // Capture final references for lambda
            final PlayerRef finalPlayerRef = playerRef;
            final Ref<EntityStore> finalEntityRef = entityRef;

            // Apply on world thread (CRITICAL for ECS operations)
            LOGGER.atFine().log("[STATS] Scheduling world.execute() for %s", playerId);
            playerWorld.execute(() -> {
                try {
                    // Defensive: re-validate entity ref inside lambda (may have changed)
                    if (!finalEntityRef.isValid()) {
                        LOGGER.atWarning().log("[STATS] Entity ref became invalid in world.execute() for %s", playerId);
                        return;
                    }

                    Store<EntityStore> store = finalEntityRef.getStore();
                    if (store == null) {
                        LOGGER.atWarning().log("[STATS] Store is NULL in world.execute() for %s", playerId);
                        return;
                    }

                    LOGGER.atFine().log("[STATS] Applying stats to ECS inside world.execute() for %s", playerId);
                    StatsApplicationSystem.applyAllStatsAndSync(
                        finalPlayerRef, store, finalEntityRef, stats,
                        attributeManager.getPlayerDataRepository(), playerId
                    );

                    // Bridge mana (and future Hexcode stats) to EntityStatMap
                    StatMapBridge.applyToEntity(store, finalEntityRef, stats, playerId);

                    LOGGER.atFine().log("[STATS] SUCCESS - Applied stats to ECS for %s", playerId);
                } catch (Exception e) {
                    LOGGER.atSevere().withCause(e).log("[STATS] EXCEPTION in world.execute() for %s", playerId);
                }
            });
        });

        // Register AttributeManager EARLY - required by GearManager.initialize()
        ServiceRegistry.register(AttributeService.class, attributeManager);
        ServiceRegistry.register(AttributeManager.class, attributeManager);
        getLogger().atInfo().log("AttributeManager registered in ServiceRegistry");

        // Phase 3.5: Initialize Gear System using GearManager
        // GearManager.initialize() requires AttributeManager to be in ServiceRegistry
        initializeGearSystem();

        // Phase 4: Initialize UI Manager
        uiManager = new UIManager(this);
        getLogger().atInfo().log("UI Manager initialized");

        // Phase 5: Register remaining services in ServiceRegistry
        ServiceRegistry.register(ConfigService.class, configManager);
        ServiceRegistry.register(UIService.class, uiManager);
        getLogger().atInfo().log("Services registered in ServiceRegistry");

        // Phase 5.5: Initialize Skill Tree System
        if (configManager.getRPGConfig().getSkillTree().isEnabled()) {
            SkillTreeRepository skillTreeRepo = new SkillTreeRepository(dataManager);
            skillTreeManager = new SkillTreeManager(skillTreeRepo, configManager);

            // Load skill tree config from YAML
            SkillTreeConfig treeConfig = loadSkillTreeConfig();
            if (treeConfig != null && !treeConfig.getNodes().isEmpty()) {
                // Apply Hexcode overlay if Hexcode is loaded (appends magic stats to tree nodes)
                if (HexcodeCompat.isLoaded()) {
                    HexcodeSkillTreeOverlay.apply(treeConfig, dataFolder, this::copyResourceToFile);
                }

                skillTreeManager.loadConfig(treeConfig);
                ServiceRegistry.register(SkillTreeService.class, skillTreeManager);
                getLogger().atInfo().log("SkillTreeService registered with %d nodes",
                    treeConfig.getNodeCount());

                // Initialize node allocation feedback (banners, sounds, chat breakdown, toast)
                nodeAllocationFeedbackService = new NodeAllocationFeedbackService(treeConfig.getFeedback());
                skillTreeManager.registerNodeAllocatedListener(nodeAllocationFeedbackService::onNodeAllocated);
                skillTreeManager.registerNodeDeallocatedListener(nodeAllocationFeedbackService::onNodeDeallocated);
                getLogger().atInfo().log("NodeAllocationFeedbackService initialized");

                // Initialize conditional trigger system for on-kill, on-crit, threshold effects
                conditionalTriggerSystem = new ConditionalTriggerSystem(treeConfig);
                getLogger().atInfo().log("ConditionalTriggerSystem initialized");

                // Skill tree visualization is handled by the Skill Sanctum (3D world-based UI)
            } else {
                getLogger().atWarning().log("Skill tree config empty or failed to load");
            }
        } else {
            getLogger().atInfo().log("Skill tree system disabled in config");
        }

        // Phase 6: Initialize Native Leveling System (replaces LevelingCore)
        LevelingConfig levelingConfig = configManager.getLevelingConfig();
        if (levelingConfig.isEnabled()) {
            // Create formula from config
            LevelingConfig.FormulaConfig formulaConfig = levelingConfig.getFormula();
            String formulaType = formulaConfig.getType() != null
                ? formulaConfig.getType().toLowerCase() : "effort";

            LevelFormula formula;
            if ("exponential".equals(formulaType)) {
                formula = new ExponentialFormula(
                    formulaConfig.getBaseXp(),
                    formulaConfig.getExponent(),
                    formulaConfig.getMaxLevel()
                );
                getLogger().atInfo().log("Using exponential leveling formula (legacy)");
            } else {
                // Effort-based formula (default)
                LevelingConfig.EffortConfig effortConfig = formulaConfig.getEffort();
                EffortCurve curve = new EffortCurve(
                    effortConfig.getBaseMobs(),
                    effortConfig.getTargetMobs(),
                    effortConfig.getTargetLevel()
                );

                // Build MobXpEstimator from existing configs
                LevelingConfig.XpGainConfig xpGainConfig = levelingConfig.getXpGain();
                MobStatPoolConfig poolConfig = configManager.getMobStatPoolConfig();
                MobXpEstimator estimator = new MobXpEstimator(
                    xpGainConfig.getXpPerMobLevel(),
                    xpGainConfig.getPoolMultiplier(),
                    poolConfig.getPointsPerLevel(),
                    poolConfig.isProgressiveScalingEnabled(),
                    poolConfig.getProgressiveScalingSoftCapLevel(),
                    poolConfig.getProgressiveScalingMinFactor()
                );

                formula = new EffortBasedFormula(curve, estimator, formulaConfig.getMaxLevel());
                getLogger().atInfo().log(
                    "Using effort-based leveling formula (base_mobs=%.1f, target_mobs=%.1f, target_level=%d)",
                    effortConfig.getBaseMobs(), effortConfig.getTargetMobs(), effortConfig.getTargetLevel());
            }

            // Create repository with cache
            LevelingRepository levelingRepo = new LevelingRepository(dataManager);

            // Create leveling manager
            levelingManager = new LevelingManager(levelingRepo, formula, levelingConfig);

            // Create XP calculator for mob kills
            xpCalculator = new MobStatsXpCalculator(
                levelingConfig.getXpGain(),
                configManager.getMobClassificationConfig()
            );

            // Initialize level-up celebration service
            celebrationService = new LevelUpCelebrationService(
                    configManager.getLevelingConfig().getCelebration());

            // Log available emotes for configuration discovery
            EmoteCelebrationHelper.logAvailableEmotes();

            // Register level-up listener to grant points and celebrate
            levelingManager.registerLevelUpListener((playerId, newLevel, oldLevel, totalXp) -> {
                int levelsGained = newLevel - oldLevel;

                // Grant attribute points
                int attrPoints = configManager.getRPGConfig().getAttributes().getPointsPerLevel() * levelsGained;
                if (attrPoints > 0) {
                    attributeManager.modifyUnallocatedPoints(playerId, attrPoints);
                    if (configManager.getRPGConfig().isDebugMode()) {
                        getLogger().atInfo().log("Granted %d attribute points to %s on level up (%d -> %d)",
                            attrPoints, playerId, oldLevel, newLevel);
                    }
                }

                // Grant skill points if skill tree enabled
                int skillPoints = 0;
                if (skillTreeManager != null) {
                    skillPoints = configManager.getRPGConfig().getSkillTree().getPointsPerLevel() * levelsGained;
                    if (skillPoints > 0) {
                        skillTreeManager.grantSkillPoints(playerId, skillPoints);
                    }
                }

                // Query totals after granting (for the chat breakdown)
                int totalAttr = attributeManager.getPlayerDataRepository()
                        .get(playerId)
                        .map(data -> data.getUnallocatedPoints())
                        .orElse(attrPoints);
                int totalSkill = skillTreeManager != null
                        ? skillTreeManager.getAvailablePoints(playerId)
                        : 0;

                // Celebrate!
                celebrationService.celebrate(playerId, oldLevel, newLevel,
                        attrPoints, skillPoints, totalAttr, totalSkill);
            });

            // Register guide milestone triggers on level up
            if (guideManager != null) {
                levelingManager.registerLevelUpListener((playerId, newLevel, oldLevel, totalXp) -> {
                    io.github.larsonix.trailoforbis.guide.GuideMilestone m = io.github.larsonix.trailoforbis.guide.GuideMilestone.FIRST_LEVEL_UP;
                    if (newLevel >= 2) guideManager.tryShowBest(playerId,
                        m,
                        newLevel >= 10 ? io.github.larsonix.trailoforbis.guide.GuideMilestone.MOB_SCALING : m,
                        newLevel >= 20 ? io.github.larsonix.trailoforbis.guide.GuideMilestone.DEATH_PENALTY_ACTIVE : m,
                        newLevel >= 25 ? io.github.larsonix.trailoforbis.guide.GuideMilestone.LARGER_REALMS : m
                    );
                });
            }

            // Register service
            ServiceRegistry.register(LevelingService.class, levelingManager);
            getLogger().atInfo().log("Native leveling system enabled (max level: %d)", formulaConfig.getMaxLevel());

            // Phase 6.1: Initialize XP bar HUD manager
            xpBarHudManager = new io.github.larsonix.trailoforbis.ui.hud.XpBarHudManager(levelingManager);
            xpBarHudManager.registerEventListeners(levelingManager);
            getLogger().atInfo().log("XP bar HUD manager initialized");
        } else {
            getLogger().atInfo().log("Leveling system disabled in config");
        }

        // Phase 6.5: Initialize Mob Scaling System
        // Recreate manager with proper LevelingService
        mobScalingManager = new MobScalingManager(this, configManager, levelingManager);
        if (mobScalingManager.initialize()) {
            if (mobScalingManager.isEnabled()) {
                ServiceRegistry.register(MobScalingService.class, mobScalingManager);
                getLogger().atInfo().log("MobScalingService registered (distance + player level scaling)");
            } else {
                getLogger().atInfo().log("Mob scaling disabled in config");
            }
        } else {
            getLogger().atWarning().log("Mob scaling initialization failed");
        }

        // Phase 6.6: Initialize Projectile Stats System Config
        // Config is already loaded via SnakeYAML in RPGConfig, just grab the reference
        projectileConfig = configManager.getRPGConfig().getProjectile();
        if (projectileConfig != null && projectileConfig.isEnabled()) {
            getLogger().atInfo().log("ProjectileSystem enabled: %s", projectileConfig);
        } else {
            getLogger().atInfo().log("ProjectileSystem disabled in config");
        }

        // Phase 6.9: Register ECS systems
        // Per Hytale best practice, systems are registered in start() after configs/managers
        // are initialized, while components are registered in setup().
        registerEcsSystems();

        // Phase 7: Initialize Mob Level Refresh System (throttled: max 5 mobs/tick)
        MobScalingConfig mobScalingConfig = configManager.getMobScalingConfig();
        if (mobScalingConfig.isEnabled() && mobScalingConfig.getDynamicRefresh().isEnabled()) {
            MobLevelRefreshSystem refreshSystem = new MobLevelRefreshSystem(this);
            getEntityStoreRegistry().registerSystem(refreshSystem);
            getLogger().atInfo().log("MobLevelRefreshSystem enabled (interval: %.1fs, radius: %.0f)",
                mobScalingConfig.getDynamicRefresh().getIntervalSeconds(),
                mobScalingConfig.getDynamicRefresh().getPlayerProximityRadius());
        }

        // Phase 7.6: Initialize Death Recap System
        // Tracker stores combat data for death recap display
        DeathRecapConfig deathRecapConfig = configManager.getDeathRecapConfig();
        if (deathRecapConfig != null && deathRecapConfig.isEnabled()) {
            deathRecapTracker = new DeathRecapTracker(deathRecapConfig);
            getLogger().atInfo().log("Death recap system enabled (mode: %s)",
                deathRecapConfig.getDisplayMode());
        } else {
            getLogger().atInfo().log("Death recap system disabled in config");
        }

        // Phase 7.6.5: Initialize Ailment System (PoE2-style elemental ailments)
        initializeAilmentSystem();

        // Phase 7.7: Initialize Realms System
        initializeRealmsSystem();

        // Phase 7.7.1: Register deferred GearManager systems (now that LevelingService + RealmsManager are available)
        if (gearManager != null) {
            gearManager.registerDeferredSystems();
        }

        // Phase 7.7.2: Register container loot interceptor (ECS system for UseBlockEvent.Pre)
        // Replaces vanilla weapons/armor in world containers with RPG gear on first open.
        registerContainerLootInterceptor();

        // Phase 7.7.5: Initialize Skill Sanctum System (3D skill tree exploration)
        initializeSkillSanctumSystem();

        // Phase 7.7.6: Initialize Party Integration (PartyPro compatibility)
        try {
            var partyConfig = configManager.getPartyConfig();
            if (partyConfig.isEnabled() && levelingManager != null) {
                partyIntegrationManager = new io.github.larsonix.trailoforbis.compat.party.PartyIntegrationManager(
                    partyConfig, levelingManager);
                partyIntegrationManager.initialize();
                io.github.larsonix.trailoforbis.api.ServiceRegistry.register(
                    io.github.larsonix.trailoforbis.compat.party.PartyIntegrationManager.class,
                    partyIntegrationManager);
                getLogger().atInfo().log("Party integration initialized");
            } else {
                getLogger().atInfo().log("Party integration disabled (config=%s, leveling=%s)",
                    partyConfig.isEnabled() ? "enabled" : "disabled",
                    levelingManager != null ? "available" : "unavailable");
            }
        } catch (Exception e) {
            getLogger().atWarning().log("Party integration failed to initialize: %s", e.getMessage());
        }

        // Phase 7.8: Initialize Stone System (currency items for modifying gear/maps)
        initializeStoneSystem();

        // Phase 7.9: Initialize Inventory Detection System (Stats button on inventory open)
        initializeInventoryDetectionSystem();

        // Phase 7.10: Initialize Attack Speed System (event-driven cooldown modification)
        initializeAttackSpeedSystem();

        // Phase 7.10.5: Initialize Loot Filter System
        initializeLootFilterSystem();

        // Phase 8: Verify custom assets loaded
        verifyCustomAssets();

        getLogger().atInfo().log("TrailOfOrbis started successfully!");
    }

    /**
     * Verifies that custom assets (damage causes, etc.) are properly loaded.
     *
     * <p>Uses {@link HytaleAPICompat} for safe asset lookups with caching.
     */
    private void verifyCustomAssets() {
        // Verify Rpg_Physical_Crit damage cause for red crit text
        int critIndex = HytaleAPICompat.getDamageCauseIndex("Rpg_Physical_Crit");
        if (critIndex != HytaleAPICompat.DAMAGE_CAUSE_NOT_FOUND) {
            getLogger().atInfo().log("Custom damage cause 'Rpg_Physical_Crit' loaded (index=%d)", critIndex);
        } else {
            getLogger().atWarning().log("Custom damage cause 'Rpg_Physical_Crit' NOT FOUND - crits will use default color!");
        }

        // Pre-warm cache for other damage types
        HytaleAPICompat.getDamageCauseIndex("Rpg_Physical");
        HytaleAPICompat.getDamageCauseIndex("Rpg_Magic");
        HytaleAPICompat.getDamageCauseIndex("Rpg_Magic_Crit");
    }

    // ==================== SHUTDOWN ====================

    /**
     * Shuts down the plugin - saves data and cleans up resources.
     *
     * <p>Shutdown order is reverse of initialization:
     * <ol>
     *   <li>Save all player data</li>
     *   <li>Close database connections</li>
     *   <li>Clear singleton instance</li>
     * </ol>
     */
    @Override
    protected void shutdown() {
        getLogger().atInfo().log("Shutting down TrailOfOrbis...");

        // Phase 0: Clear ServiceRegistry first
        ServiceRegistry.clear();

        // Phase 1: Save all player data
        if (attributeManager != null) {
            getLogger().atInfo().log("Saving all player data...");
            attributeManager.getPlayerDataRepository().saveAll();
        }

        // Phase 1.5: Save all skill tree data
        if (skillTreeManager != null) {
            getLogger().atInfo().log("Saving all skill tree data...");
            skillTreeManager.saveAll();
        }

        // Phase 1.6: Save all leveling data
        if (levelingManager != null) {
            getLogger().atInfo().log("Saving all leveling data...");
            levelingManager.saveAll();
            levelingManager.shutdown();
        }

        // Phase 1.6.5: Shutdown animation speed sync (restore all players to vanilla)
        if (animationSpeedSyncManager != null) {
            getLogger().atInfo().log("Shutting down animation speed sync...");
            animationSpeedSyncManager.shutdown();
            animationSpeedSyncManager = null;
        }

        // Phase 1.7: Shutdown ailment system
        if (ailmentEffectManager != null) {
            getLogger().atInfo().log("Shutting down ailment effect manager...");
            ailmentEffectManager.shutdown();
            ailmentEffectManager = null;
        }
        if (ailmentTracker != null) {
            getLogger().atInfo().log("Clearing ailment tracker...");
            ailmentTracker.clearAll();
            ailmentTracker = null;
        }
        ailmentCalculator = null;
        ailmentConfig = null;

        // Phase 1.8: Shutdown gear equipment listener
        if (gearEquipmentListener != null) {
            getLogger().atInfo().log("Shutting down gear equipment listener...");
            gearEquipmentListener.shutdown();
            gearEquipmentListener = null;
        }

        // Phase 1.9: Clear loot listener reference
        // Note: ECS systems are managed by getEntityStoreRegistry() proxy, we just clear our reference
        if (lootListener != null) {
            getLogger().atInfo().log("Clearing loot listener reference...");
            lootListener = null;
        }

        // Phase 1.9.5: Shutdown GemManager (before GearManager since it depends on it)
        if (gemManager != null) {
            getLogger().atInfo().log("Shutting down GemManager...");
            gemManager.shutdown();
            gemManager = null;
        }

        // Phase 1.10: Shutdown GearManager (handles item registry, loot, etc.)
        if (gearManager != null) {
            getLogger().atInfo().log("Shutting down GearManager...");
            gearManager.shutdown();
            gearManager = null;
        }

        // Phase 1.10.5: Shutdown Party Integration
        if (partyIntegrationManager != null) {
            getLogger().atInfo().log("Shutting down party integration...");
            partyIntegrationManager.shutdown();
            partyIntegrationManager = null;
        }

        // Phase 1.11: Shutdown Realms Manager
        if (realmsManager != null) {
            getLogger().atInfo().log("Shutting down RealmsManager...");
            realmsManager.shutdown();
            realmsManager = null;
        }

        // Phase 1.11.5: Shutdown Skill Sanctum Manager
        if (skillSanctumManager != null) {
            getLogger().atInfo().log("Shutting down SkillSanctumManager...");
            skillSanctumManager.shutdown();
            skillSanctumManager = null;
        }

        // Phase 1.12: Clear stone listener references
        // Note: ECS systems are managed by getEntityStoreRegistry() proxy, we just clear our references
        if (stoneDropListener != null) {
            getLogger().atInfo().log("Clearing stone drop listener reference...");
            stoneDropListener = null;
        }

        // Phase 1.12.5: Shutdown Loot Filter Manager
        if (lootFilterManager != null) {
            getLogger().atInfo().log("Shutting down loot filter system...");
            io.github.larsonix.trailoforbis.lootfilter.bridge.VuetaleIntegration.shutdown();
            lootFilterManager.shutdown();
            lootFilterManager = null;
        }

        // Phase 1.12.6: Shutdown colored combat text
        if (combatTextColorManager != null) {
            combatTextColorManager.shutdown();
            combatTextColorManager = null;
        }

        // Phase 1.13: Shutdown inventory detection manager
        if (inventoryDetectionManager != null) {
            getLogger().atInfo().log("Shutting down inventory detection manager...");
            inventoryDetectionManager.onDisable();
            inventoryDetectionManager = null;
        }

        // Phase 1.14: Shutdown XP bar HUD manager
        if (xpBarHudManager != null) {
            getLogger().atInfo().log("Shutting down XP bar HUD manager...");
            if (levelingManager != null) {
                xpBarHudManager.unregisterEventListeners(levelingManager);
            }
            xpBarHudManager.removeAllHuds();
            xpBarHudManager = null;
        }

        // Legacy shutdown - remove after confirming GearManager handles everything
        if (lootItemRegistryService != null) {
            getLogger().atInfo().log("Shutting down item registry service...");
            lootItemRegistryService.shutdown();
            lootItemRegistryService = null;
        }

        // Phase 1.15: Shutdown UI Manager
        if (uiManager != null) {
            uiManager.shutdown();
            uiManager = null;
        }

        // Phase 1.16: Clear player world cache
        PlayerWorldCache.shutdown();

        // Phase 1.17: Shutdown guide system
        if (guideManager != null) {
            guideManager.shutdown();
            guideManager = null;
        }

        // Phase 2: Close database connections
        if (dataManager != null) {
            dataManager.shutdown();
        }

        // Phase 3: Clear instance
        instance = null;

        getLogger().atInfo().log("TrailOfOrbis shutdown complete.");
    }

    // ==================== GETTERS ====================

    /**
     * Gets the singleton plugin instance.
     *
     * @throws IllegalStateException if plugin is not initialized
     */
    @Nonnull
    public static TrailOfOrbis getInstance() {
        TrailOfOrbis localInstance = instance;
        if (localInstance == null) {
            throw new IllegalStateException("TrailOfOrbis not initialized");
        }
        return localInstance;
    }

    /**
     * Gets the singleton plugin instance, or null if not initialized.
     *
     * <p>Use this method when the plugin may not be available (e.g., during
     * shutdown or in async operations).
     */
    @Nullable
    public static TrailOfOrbis getInstanceOrNull() {
        return instance;
    }

    @Nonnull
    public Path getDataPath() {
        return dataFolder;
    }

    /**
     * @throws IllegalStateException if called before setup() completes
     */
    @Nonnull
    public ConfigManager getConfigManager() {
        if (configManager == null) {
            throw new IllegalStateException("Plugin not fully initialized - ConfigManager unavailable");
        }
        return configManager;
    }

    /**
     * @throws IllegalStateException if called before start() completes
     */
    @Nonnull
    public DataManager getDataManager() {
        if (dataManager == null) {
            throw new IllegalStateException("Plugin not fully initialized - DataManager unavailable");
        }
        return dataManager;
    }

    /**
     * @throws IllegalStateException if called before start() completes
     */
    @Nonnull
    public AttributeManager getAttributeManager() {
        if (attributeManager == null) {
            throw new IllegalStateException("Plugin not fully initialized - AttributeManager unavailable");
        }
        return attributeManager;
    }

    /**
     * @throws IllegalStateException if called before start() completes
     */
    @Nonnull
    public UIManager getUIManager() {
        if (uiManager == null) {
            throw new IllegalStateException("Plugin not fully initialized - UIManager unavailable");
        }
        return uiManager;
    }

    /** @return null if leveling is disabled */
    @Nullable
    public LevelingManager getLevelingManager() {
        return levelingManager;
    }

    /** @return null if leveling is disabled */
    @Nullable
    public MobStatsXpCalculator getXpCalculator() {
        return xpCalculator;
    }

    /** @return null if disabled */
    @Nullable
    public SkillTreeManager getSkillTreeManager() {
        return skillTreeManager;
    }

    /** @return null if not initialized */
    @Nullable
    public RegenerationTickSystem getRegenerationSystem() {
        return regenerationSystem;
    }

    /** @return null if not initialized */
    @Nullable
    public HotbarSlotTrackingSystem getHotbarSlotTrackingSystem() {
        return hotbarSlotTrackingSystem;
    }

    /**
     * Used by {@link MobScalingComponent#getComponentType()} for static access
     * to the registered component type.
     *
     * @return null if not yet registered
     */
    @Nullable
    public ComponentType<EntityStore, MobScalingComponent> getMobScalingComponentType() {
        return mobScalingComponentType;
    }

    /** @return null if not yet registered */
    @Nullable
    public ComponentType<EntityStore, MobStatComponent> getMobStatComponentType() {
        return mobStatComponentType;
    }

    /**
     * Used by {@link RPGSpawnedMarker#getComponentType()} for static access
     * to the registered component type.
     *
     * @return null if not yet registered
     */
    @Nullable
    public ComponentType<EntityStore, RPGSpawnedMarker> getRPGSpawnedMarkerType() {
        return rpgSpawnedMarkerType;
    }

    /** @return null if disabled or not initialized */
    @Nullable
    public MobScalingManager getMobScalingManager() {
        return mobScalingManager;
    }

    /** @return null if not initialized */
    @Nullable
    public EnergyShieldTracker getEnergyShieldTracker() {
        return energyShieldTracker;
    }

    /**
     * Stores combat data for each player, used to display detailed death recaps when players die.
     *
     * @return null if disabled or not initialized
     */
    @Nullable
    public DeathRecapTracker getDeathRecapTracker() {
        return deathRecapTracker;
    }

    /**
     * When enabled, players see a detailed damage breakdown in chat
     * after each attack they deal.
     */
    public boolean isCombatDetailEnabled(@Nonnull UUID playerUuid) {
        return combatDetailEnabled.contains(playerUuid);
    }

    /** Sets combat detail mode for a player. */
    public void setCombatDetailEnabled(@Nonnull UUID playerUuid, boolean enabled) {
        if (enabled) {
            combatDetailEnabled.add(playerUuid);
        } else {
            combatDetailEnabled.remove(playerUuid);
        }
    }

    /**
     * Manages active ailments (Burn, Freeze, Shock, Poison)
     * for all entities.
     *
     * @return null if ailment system is disabled
     */
    @Nullable
    public AilmentTracker getAilmentTracker() {
        return ailmentTracker;
    }

    /** @return null if combat system is not initialized */
    @Nullable
    public io.github.larsonix.trailoforbis.combat.tracking.ConsecutiveHitTracker getConsecutiveHitTracker() {
        return consecutiveHitTracker;
    }

    /** @return null if combat system is not initialized */
    @Nullable
    public io.github.larsonix.trailoforbis.ailments.AilmentImmunityTracker getAilmentImmunityTracker() {
        return ailmentImmunityTracker;
    }

    /**
     * Pure calculation logic for ailment application chance, duration, and magnitude.
     *
     * @return null if ailment system is disabled
     */
    @Nullable
    public AilmentCalculator getAilmentCalculator() {
        return ailmentCalculator;
    }

    /**
     * Handles Hytale EntityEffect integration for ailment visuals,
     * particularly Freeze slow effects via the movement system.
     *
     * @return null if ailment system is disabled
     */
    @Nullable
    public AilmentEffectManager getAilmentEffectManager() {
        return ailmentEffectManager;
    }

    /** @return null if ailment system is disabled */
    @Nullable
    public AilmentConfig getAilmentConfig() {
        return ailmentConfig;
    }

    /** @return null if not loaded */
    @Nullable
    public ProjectileConfig getProjectileConfig() {
        return projectileConfig;
    }

    /**
     * Manages event-based triggers (ON_KILL, ON_CRIT, WHEN_HIT) and
     * threshold-based persistent effects (LOW_LIFE, FULL_MANA) with
     * per-player duration, stacking, and cooldowns.
     *
     * @return null if skill tree is disabled
     */
    @Nullable
    public ConditionalTriggerSystem getConditionalTriggerSystem() {
        return conditionalTriggerSystem;
    }

    /** @return null if not yet registered */
    @Nullable
    public ComponentType<EntityStore, io.github.larsonix.trailoforbis.maps.components.RealmMobComponent> getRealmMobComponentType() {
        return realmMobComponentType;
    }

    /** @return null if not yet registered */
    @Nullable
    public ComponentType<EntityStore, io.github.larsonix.trailoforbis.maps.components.RealmPlayerComponent> getRealmPlayerComponentType() {
        return realmPlayerComponentType;
    }

    /**
     * Used by {@link RPGItemPreSyncSystem} via a Supplier reference, allowing the
     * system to be registered early in start() while GearManager may not yet be initialized.
     *
     * @return null if gear system is disabled or not initialized
     */
    @Nullable
    public io.github.larsonix.trailoforbis.gear.GearManager getGearManager() {
        return gearManager;
    }

    /** @return null if disabled or not initialized */
    @Nullable
    public RealmsManager getRealmsManager() {
        return realmsManager;
    }

    /** @return null if not registered */
    @Nullable
    public StoneDropListener getStoneDropListener() {
        return stoneDropListener;
    }

    /** @return null if disabled or not initialized */
    @Nullable
    public SkillSanctumManager getSkillSanctumManager() {
        return skillSanctumManager;
    }

    @Nullable
    public io.github.larsonix.trailoforbis.guide.GuideManager getGuideManager() {
        return guideManager;
    }

    /** @return null if disabled or not initialized */
    @Nullable
    public io.github.larsonix.trailoforbis.ui.inventory.InventoryDetectionManager getInventoryDetectionManager() {
        return inventoryDetectionManager;
    }

    /** @return null if leveling is disabled */
    @Nullable
    public io.github.larsonix.trailoforbis.ui.hud.XpBarHudManager getXpBarHudManager() {
        return xpBarHudManager;
    }

    /** @return null if not yet registered */
    @Nullable
    public ComponentType<EntityStore, SkillNodeComponent> getSkillNodeComponentType() {
        return skillNodeComponentType;
    }

    /** @return null if not yet registered */
    @Nullable
    public ComponentType<EntityStore, io.github.larsonix.trailoforbis.sanctum.components.SkillNodeSubtitleComponent> getSkillNodeSubtitleComponentType() {
        return skillNodeSubtitleComponentType;
    }

    /** @return null if stone system not initialized */
    @Nullable
    public io.github.larsonix.trailoforbis.stones.ui.StoneApplicationService getStoneApplicationService() {
        return stoneApplicationService;
    }

    /** @return null if not initialized */
    @Nullable
    public io.github.larsonix.trailoforbis.stones.tooltip.StoneTooltipSyncService getStoneTooltipSyncService() {
        return stoneTooltipSyncService;
    }

    // ==================== PRIVATE HELPERS ====================

    /**
     * Initializes the Realms system for procedural dungeon instances.
     *
     * <p>This method:
     * <ul>
     *   <li>Loads realms configuration from YAML</li>
     *   <li>Creates and initializes the RealmsManager</li>
     *   <li>Registers the RealmsService in ServiceRegistry</li>
     * </ul>
     *
     * <p>Requires the TrailOfOrbis_Realms asset pack to be deployed on the server
     * with valid instance templates.
     */
    private void initializeRealmsSystem() {
        getLogger().atInfo().log("Starting Realms system initialization...");

        RealmsConfigLoader realmsConfigLoader = null;
        try {
            getLogger().atInfo().log("Loading realms configuration...");
            realmsConfigLoader = new RealmsConfigLoader(dataFolder.resolve("config"));
            realmsConfigLoader.loadAll();
            getLogger().atInfo().log("Realms configuration loaded successfully");
        } catch (Exception e) {
            getLogger().atSevere().withCause(e).log(
                "FAILED to load realms configuration - realm features disabled");
            return;
        }

        if (!realmsConfigLoader.getRealmsConfig().isEnabled()) {
            getLogger().atInfo().log("Realms system disabled in config");
            return;
        }

        try {
            getLogger().atInfo().log("Creating RealmsManager...");
            realmsManager = new RealmsManager(realmsConfigLoader);
            getLogger().atInfo().log("RealmsManager created, initializing...");

            realmsManager.initialize(getEventRegistry());
            getLogger().atInfo().log("RealmsManager initialized");

            // Register realm map interaction system (for right-click to work)
            getLogger().atInfo().log("Registering realm map interaction system...");
            registerRealmMapInteractions();
            getLogger().atInfo().log("Realm map interaction system registered");

            // Create and wire up reward service for completion rewards
            getLogger().atInfo().log("Creating RealmRewardService...");

            // Create victory reward system components if gear manager is available
            VictoryRewardGenerator victoryRewardGenerator = null;
            VictoryRewardDistributor victoryRewardDistributor = null;

            if (gearManager != null) {
                try {
                    RealmsConfig realmsConfig = realmsConfigLoader.getRealmsConfig();
                    VictoryRewardConfig victoryRewardConfig = realmsConfig.getVictoryRewardConfig();

                    // Create generator with all dependencies
                    victoryRewardGenerator = new VictoryRewardGenerator(
                        victoryRewardConfig,
                        realmsManager.getMapGenerator(),
                        gearManager.getLootGenerator(),
                        gearManager.getGearGenerator().getRarityRoller(),
                        gearManager.getDropLevelBlender()
                    );

                    // Create distributor with custom item sync service
                    victoryRewardDistributor = new VictoryRewardDistributor(
                        gearManager.getCustomItemSyncService()
                    );

                    getLogger().atInfo().log("Victory reward system initialized");
                } catch (Exception e) {
                    getLogger().atWarning().withCause(e).log(
                        "Failed to initialize victory reward system - victory items disabled");
                }
            } else {
                getLogger().atWarning().log(
                    "Gear system not available - victory item rewards disabled");
            }

            // Create reward chest manager — unclaimed items are discarded when realms close
            // (the physical chest IS the reward mechanism, no fallback delivery)
            RewardChestManager rewardChestManager = new RewardChestManager();
            realmsManager.setRewardChestManager(rewardChestManager);

            // Register the reward chest interceptor (ECS system for UseBlockEvent.Pre)
            // Only used in standalone mode — when L4E is present, it handles chest opens
            if (!rewardChestManager.isLoot4EveryonePresent()) {
                getEntityStoreRegistry().registerSystem(new RewardChestInterceptor(rewardChestManager));
                getLogger().atInfo().log("RewardChestInterceptor ECS system registered (standalone mode)");
            }

            RealmRewardService rewardService = new RealmRewardService(
                this, realmsConfigLoader.getRealmsConfig(),
                victoryRewardGenerator, victoryRewardDistributor, rewardChestManager);
            realmsManager.setRewardService(rewardService);
            getLogger().atInfo().log("RealmRewardService initialized with reward chest support");

            // Register service
            ServiceRegistry.register(RealmsService.class, realmsManager);

            getLogger().atInfo().log("Realms system initialized successfully (max instances: %d)",
                realmsConfigLoader.getRealmsConfig().getMaxConcurrentInstances());

        } catch (Exception e) {
            getLogger().atSevere().withCause(e).log(
                "FAILED to initialize Realms system - realm features disabled");
            realmsManager = null;
        }
    }

    /**
     * Registers the container loot interceptor ECS system.
     *
     * <p>The {@link ContainerLootInterceptor} handles {@code UseBlockEvent.Pre}
     * to intercept container opens (chests, barrels, crates) and replace vanilla
     * weapons/armor with RPG gear. It skips reward chests (handled by
     * {@link RewardChestInterceptor}) and L4E-managed containers.
     *
     * <p>Requires GearManager to be initialized (provides ContainerLootSystem).
     * RewardChestManager is optional (used to avoid processing reward chests).
     */
    private void registerContainerLootInterceptor() {
        if (gearManager == null) {
            getLogger().atInfo().log("Container loot interceptor skipped — gear system not available");
            return;
        }

        var containerLootSystem = gearManager.getContainerLootSystem();
        if (containerLootSystem == null) {
            getLogger().atInfo().log("Container loot interceptor skipped — system disabled in config");
            return;
        }

        // Get reward chest manager (optional — used to skip reward chests)
        RewardChestManager rewardChestManager = null;
        if (realmsManager != null) {
            rewardChestManager = realmsManager.getRewardChestManager();
        }

        var interceptor = containerLootSystem.createInterceptor(
            rewardChestManager, realmsManager, processedContainerResourceType);
        if (interceptor != null) {
            getEntityStoreRegistry().registerSystem(interceptor);
            getLogger().atInfo().log("Registered ContainerLootInterceptor ECS system (scope: %s, clearAll: %s)",
                containerLootSystem.getConfig().getScope(),
                containerLootSystem.getConfig().isClearAllVanilla());
        }
    }

    /**
     * Initializes the Skill Sanctum system for 3D skill tree exploration.
     *
     * <p>The Skill Sanctum is a dedicated realm where players physically walk
     * among floating orbs representing skill tree nodes. They press F to allocate
     * skill points instead of using a 2D UI.
     *
     * <p>Requires the skill tree system to be initialized first.
     */
    private void initializeSkillSanctumSystem() {
        if (skillTreeManager == null) {
            getLogger().atInfo().log("Skill Sanctum disabled - skill tree system not available");
            return;
        }

        try {
            skillSanctumManager = new SkillSanctumManager(this, skillTreeManager,
                configManager.getSkillSanctumConfig());
            if (skillSanctumManager.initialize()) {
                getLogger().atInfo().log("Skill Sanctum system initialized");
            } else {
                getLogger().atWarning().log("Skill Sanctum system failed to initialize");
                skillSanctumManager = null;
            }
        } catch (Exception e) {
            getLogger().atWarning().withCause(e).log(
                "Failed to initialize Skill Sanctum system");
            skillSanctumManager = null;
        }
    }

    /**
     * Initializes the stone system for currency items that modify gear and maps.
     *
     * <p>Stones are consumable items (inspired by Path of Exile's orbs) that can:
     * <ul>
     *   <li>Reroll modifier values (Divine Orb)</li>
     *   <li>Reroll modifiers completely (Chaos Orb)</li>
     *   <li>Add new modifiers (Exalted Orb)</li>
     *   <li>Remove modifiers (Orb of Scouring, Orb of Annulment)</li>
     *   <li>Corrupt items (Vaal Orb)</li>
     *   <li>And more...</li>
     * </ul>
     *
     * <p>Requires the realms system and gear system to be initialized first
     * (for configs and the stone action registry).
     */
    private void initializeStoneSystem() {
        try {
            // StoneDropListener is already registered (ECS system) with lazy initialization.
            // Here we just initialize the UI components that need managers.

            if (realmsManager == null || gearManager == null) {
                getLogger().atInfo().log("Stone UI disabled - realms or gear system not available "
                        + "(stone drops from mobs still work via lazy init)");
                return;
            }

            // Create stone action registry with both realm and gear modifier configs
            StoneActionRegistry actionRegistry = new StoneActionRegistry(
                    realmsManager.getModifierConfig(),
                    gearManager.getModifierConfig(),
                    gearManager.getBalanceConfig(),
                    gearManager.getEquipmentStatConfig());

            // Create stone application service for UI-based stone usage
            stoneApplicationService = new io.github.larsonix.trailoforbis.stones.ui.StoneApplicationService(
                    actionRegistry);
            getLogger().atInfo().log("Created StoneApplicationService");

            // Create stone tooltip sync service for rich native tooltips
            stoneTooltipSyncService = new io.github.larsonix.trailoforbis.stones.tooltip.StoneTooltipSyncService();

            // Page supplier (RPG_StonePicker) is registered in setup() via registerCustomInteractions()
            // so the PAGE_CODEC can resolve it during asset loading of inline stone interactions.

            getLogger().atInfo().log("Stone system initialized - %d stone types available",
                    io.github.larsonix.trailoforbis.stones.StoneType.values().length);

        } catch (Exception e) {
            getLogger().atWarning().withCause(e).log(
                    "Failed to initialize stone UI components - stone picker disabled, "
                    + "but mob stone drops still work via lazy init");
        }
    }

    /**
     * Initializes the inventory detection system.
     *
     * <p>This system detects when players open their inventory using packet
     * analysis (camera freeze + UI click detection) and shows a "Stats" button
     * HUD for quick access to RPG stats.
     */
    private void initializeInventoryDetectionSystem() {
        try {
            io.github.larsonix.trailoforbis.ui.inventory.InventoryDetectionConfig config =
                configManager.getInventoryDetectionConfig();

            inventoryDetectionManager =
                new io.github.larsonix.trailoforbis.ui.inventory.InventoryDetectionManager(this, config);
            inventoryDetectionManager.onEnable();

            getLogger().atInfo().log("Inventory detection system initialized (enabled=%s)",
                config.isEnabled() ? "yes" : "no");

        } catch (Exception e) {
            getLogger().atWarning().withCause(e).log(
                "Failed to initialize inventory detection system - Stats button disabled");
            inventoryDetectionManager = null;
        }
    }

    /**
     * Initializes the attack speed system (Tier 1 + Tier 2).
     *
     * <p><b>Tier 1:</b> {@code InteractionTimeShiftSystem} — server-side ECS system that shifts
     * interaction chain timing so damage windows match the player's attack speed stat.
     *
     * <p><b>Tier 2:</b> {@code AnimationSpeedSyncManager} — sends per-player animation speed
     * packets so the visual swing speed matches the stat.
     *
     * <p>Configuration is loaded from {@code combat.attackSpeed} in config.yml.
     */
    private void initializeAttackSpeedSystem() {
        try {
            io.github.larsonix.trailoforbis.config.RPGConfig.CombatConfig.AttackSpeedConfig configClass =
                configManager.getRPGConfig().getCombat().getAttackSpeed();

            // Initialize animation speed sync (visual swing speed matching attack speed stat)
            io.github.larsonix.trailoforbis.combat.attackspeed.AnimationSpeedSyncConfig animConfig =
                configClass.toAnimationSyncConfig();
            animationSpeedSyncManager = new io.github.larsonix.trailoforbis.combat.attackspeed.AnimationSpeedSyncManager(
                this, animConfig);
            animationSpeedSyncManager.register(getEventRegistry());
            ServiceRegistry.register(
                io.github.larsonix.trailoforbis.combat.attackspeed.AnimationSpeedSyncManager.class,
                animationSpeedSyncManager);
            getLogger().atInfo().log("Animation speed sync initialized");

            // Initialize Tier 1 interaction time shift (damage windows match visual speed)
            if (configClass.isInteractionTimeShiftEnabled()) {
                io.github.larsonix.trailoforbis.combat.attackspeed.InteractionTimeShiftSystem timeShiftSystem =
                    new io.github.larsonix.trailoforbis.combat.attackspeed.InteractionTimeShiftSystem(this, animConfig);
                getEntityStoreRegistry().registerSystem(timeShiftSystem);
                getLogger().atInfo().log("Interaction time shift system initialized");
            }

        } catch (Exception e) {
            getLogger().atWarning().withCause(e).log(
                "Failed to initialize attack speed system - attack speed stats will have no effect");
        }
    }

    /**
     * Programmatically registers the RootInteraction and Interaction assets
     * needed for realm map items to have working Secondary (right-click) behavior.
     *
     * <p>This creates an OpenCustomUI interaction that triggers the RealmMapUsePageSupplier.
     * The RootInteraction ID "RPG_RealmMap_Secondary" is then available to be referenced
     * by custom realm map items when they are registered with ItemRegistryService.
     */
    private void registerRealmMapInteractions() {
        try {
            // The Portal_Device block assets are loaded BEFORE our plugin initializes,
            // so they already have the vanilla PortalDevicePageSupplier embedded.
            // We need to replace the page supplier on the LOADED block's interaction at runtime.
            replacePortalDevicePageSupplier();
            getLogger().atInfo().log("Replaced Portal_Device page supplier with RealmPortalDevicePageSupplier");

            // Register the page supplier for realm map right-click interactions (shows hint message)
            RealmMapUsePageSupplier realmMapPageSupplier = new RealmMapUsePageSupplier(this);
            OpenCustomUIInteraction.registerCustomPageSupplier(
                    this,
                    RealmMapUsePageSupplier.class,
                    "RPG_RealmMapUse",
                    realmMapPageSupplier);
            getLogger().atInfo().log("Registered RealmMapUsePageSupplier (page ID: RPG_RealmMapUse)");

            // Create the OpenCustomUI interaction
            String interactionId = "*RPG_OpenRealmMap";
            OpenCustomUIInteraction openUIInteraction = new OpenCustomUIInteraction();

            // Set the page supplier using reflection
            java.lang.reflect.Field pageSupplierField =
                OpenCustomUIInteraction.class.getDeclaredField("customPageSupplier");
            pageSupplierField.setAccessible(true);

            OpenCustomUIInteraction.CustomPageSupplier pageRef =
                (ref, accessor, playerRef, context) -> {
                    RealmMapUsePageSupplier supplier = new RealmMapUsePageSupplier(this);
                    return supplier.tryCreate(ref, accessor, playerRef, context);
                };
            pageSupplierField.set(openUIInteraction, pageRef);

            // Set the interaction ID
            java.lang.reflect.Field idField =
                com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction.class
                    .getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(openUIInteraction, interactionId);

            // Register the Interaction in the asset store
            com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction
                .getAssetStore()
                .loadAssets("TrailOfOrbis:RealmMaps", java.util.List.of(openUIInteraction));

            getLogger().atInfo().log("Registered Interaction: %s", interactionId);

            // Create the RootInteraction that references our Interaction
            String rootInteractionId = "RPG_RealmMap_Secondary";
            com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction
                rootInteraction = new com.hypixel.hytale.server.core.modules.interaction
                    .interaction.config.RootInteraction(rootInteractionId, interactionId);

            // Register the RootInteraction in the asset store
            com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction
                .getAssetStore()
                .loadAssets("TrailOfOrbis:RealmMaps", java.util.List.of(rootInteraction));

            getLogger().atInfo().log("Registered RootInteraction: %s -> %s",
                rootInteractionId, interactionId);

        } catch (Exception e) {
            getLogger().atWarning().withCause(e).log(
                "Failed to register realm map interactions - right-click may not work");
        }
    }

    /**
     * Replaces the page supplier on the loaded Portal_Device block's interaction.
     *
     * <p>The Portal_Device block assets are loaded BEFORE our plugin initializes,
     * so they already have the vanilla PortalDevicePageSupplier embedded.
     * This method finds the loaded block and replaces its page supplier at runtime
     * using reflection.
     *
     * <p>This allows realm maps to work on Portal_Device blocks (Ancient Gateways)
     * alongside vanilla fragment keys.
     */
    private void replacePortalDevicePageSupplier() {
        try {
            // Get the Portal_Device BlockType from the asset store
            com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType portalDevice =
                com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType.fromString("Portal_Device");

            if (portalDevice == null) {
                getLogger().atWarning().log("Portal_Device block not found in asset store");
                return;
            }

            getLogger().atInfo().log("Found Portal_Device block, looking for Use interaction...");

            // Get the RootInteraction for the Use interaction type
            Map<com.hypixel.hytale.protocol.InteractionType, String> interactions = portalDevice.getInteractions();
            if (interactions == null || !interactions.containsKey(com.hypixel.hytale.protocol.InteractionType.Use)) {
                getLogger().atWarning().log("Portal_Device has no Use interaction");
                return;
            }

            String rootInteractionId = interactions.get(com.hypixel.hytale.protocol.InteractionType.Use);
            getLogger().atInfo().log("Portal_Device Use interaction ID: %s", rootInteractionId);

            // Get the RootInteraction from the asset store
            com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction rootInteraction =
                com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction
                    .getAssetMap().getAsset(rootInteractionId);

            if (rootInteraction == null) {
                getLogger().atWarning().log("RootInteraction '%s' not found", rootInteractionId);
                return;
            }

            // Find the OpenCustomUIInteraction within the root interaction's chain
            OpenCustomUIInteraction openCustomUI = findOpenCustomUIInteraction(rootInteraction);
            if (openCustomUI == null) {
                getLogger().atWarning().log("No OpenCustomUIInteraction found in Portal_Device's Use interaction");
                return;
            }

            getLogger().atInfo().log("Found OpenCustomUIInteraction, replacing page supplier...");

            // Create our custom page supplier
            RealmPortalDevicePageSupplier ourSupplier = new RealmPortalDevicePageSupplier();

            // Copy the config from the existing supplier if possible
            try {
                java.lang.reflect.Field configField =
                    RealmPortalDevicePageSupplier.class.getDeclaredField("config");
                configField.setAccessible(true);

                // Try to get the existing config from vanilla supplier
                java.lang.reflect.Field vanillaPageSupplierField =
                    OpenCustomUIInteraction.class.getDeclaredField("customPageSupplier");
                vanillaPageSupplierField.setAccessible(true);
                Object vanillaSupplier = vanillaPageSupplierField.get(openCustomUI);

                if (vanillaSupplier != null) {
                    // Get config from vanilla supplier
                    java.lang.reflect.Field vanillaConfigField =
                        vanillaSupplier.getClass().getDeclaredField("config");
                    vanillaConfigField.setAccessible(true);
                    Object vanillaConfig = vanillaConfigField.get(vanillaSupplier);

                    if (vanillaConfig instanceof com.hypixel.hytale.builtin.portals.components.PortalDeviceConfig) {
                        configField.set(ourSupplier, vanillaConfig);
                        getLogger().atInfo().log("Copied PortalDeviceConfig from vanilla supplier");
                    }
                }
            } catch (Exception e) {
                getLogger().atFine().log("Could not copy config from vanilla supplier: %s", e.getMessage());
            }

            // Replace the page supplier using reflection
            java.lang.reflect.Field pageSupplierField =
                OpenCustomUIInteraction.class.getDeclaredField("customPageSupplier");
            pageSupplierField.setAccessible(true);
            pageSupplierField.set(openCustomUI, ourSupplier);

            getLogger().atInfo().log("Successfully replaced Portal_Device page supplier!");

        } catch (Exception e) {
            getLogger().atWarning().withCause(e).log(
                "Failed to replace Portal_Device page supplier - realm maps on Ancient Gateway may not work");
        }
    }

    @Nullable
    private OpenCustomUIInteraction findOpenCustomUIInteraction(
            @Nonnull com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction rootInteraction) {

        try {
            // RootInteraction stores interaction IDs, not direct objects
            // We need to get the IDs and look them up from the asset store
            String[] interactionIds = rootInteraction.getInteractionIds();

            if (interactionIds == null || interactionIds.length == 0) {
                getLogger().atFine().log("RootInteraction has no interaction IDs");
                return null;
            }

            getLogger().atInfo().log("RootInteraction has %d interaction IDs: %s",
                interactionIds.length, java.util.Arrays.toString(interactionIds));

            // Get the Interaction asset store
            var interactionAssetMap = com.hypixel.hytale.server.core.modules.interaction
                .interaction.config.Interaction.getAssetStore().getAssetMap();

            for (String interactionId : interactionIds) {
                var interaction = interactionAssetMap.getAsset(interactionId);

                if (interaction == null) {
                    getLogger().atFine().log("Interaction '%s' not found in asset store", interactionId);
                    continue;
                }

                getLogger().atInfo().log("Found interaction '%s' of type %s",
                    interactionId, interaction.getClass().getSimpleName());

                if (interaction instanceof OpenCustomUIInteraction openUI) {
                    return openUI;
                }

                // Check nested interactions if this is a compound interaction
                OpenCustomUIInteraction nested = findInNestedInteractions(interaction);
                if (nested != null) {
                    return nested;
                }
            }
        } catch (Exception e) {
            getLogger().atWarning().withCause(e).log("Error searching for OpenCustomUIInteraction");
        }

        return null;
    }

    @Nullable
    private OpenCustomUIInteraction findInNestedInteractions(
            @Nonnull com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction interaction) {

        var interactionAssetMap = com.hypixel.hytale.server.core.modules.interaction
            .interaction.config.Interaction.getAssetStore().getAssetMap();

        try {
            // Check all fields for nested interactions
            for (java.lang.reflect.Field field : interaction.getClass().getDeclaredFields()) {
                field.setAccessible(true);
                Object value = field.get(interaction);

                // Check List<Interaction> fields
                if (value instanceof java.util.List<?> list) {
                    for (Object item : list) {
                        if (item instanceof OpenCustomUIInteraction openUI) {
                            return openUI;
                        }
                        if (item instanceof com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction nestedInteraction) {
                            OpenCustomUIInteraction found = findInNestedInteractions(nestedInteraction);
                            if (found != null) {
                                return found;
                            }
                        }
                    }
                }

                // Check String[] fields (interaction IDs)
                if (value instanceof String[] ids) {
                    for (String id : ids) {
                        var nestedInteraction = interactionAssetMap.getAsset(id);
                        if (nestedInteraction instanceof OpenCustomUIInteraction openUI) {
                            return openUI;
                        }
                        if (nestedInteraction != null) {
                            OpenCustomUIInteraction found = findInNestedInteractions(nestedInteraction);
                            if (found != null) {
                                return found;
                            }
                        }
                    }
                }

                // Check direct Interaction fields
                if (value instanceof OpenCustomUIInteraction openUI) {
                    return openUI;
                }
                if (value instanceof com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction nestedInteraction) {
                    OpenCustomUIInteraction found = findInNestedInteractions(nestedInteraction);
                    if (found != null) {
                        return found;
                    }
                }
            }
        } catch (Exception e) {
            // Ignore reflection errors
        }

        return null;
    }

    /**
     * Initializes the ailment system for PoE2-style elemental ailments.
     *
     * <p>Ailments are status effects triggered by elemental damage:
     * <ul>
     *   <li>Burn (Fire): Damage over time</li>
     *   <li>Freeze (Cold): Movement/action speed reduction</li>
     *   <li>Shock (Lightning): Increased damage taken</li>
     *   <li>Poison (Chaos): Stacking damage over time</li>
     * </ul>
     *
     * <p>Components initialized:
     * <ul>
     *   <li>AilmentConfig - loaded from ailments.yml</li>
     *   <li>AilmentTracker - per-entity ailment state tracking</li>
     *   <li>AilmentCalculator - pure calculation logic</li>
     *   <li>AilmentEffectManager - Hytale EntityEffect integration (Freeze slow)</li>
     *   <li>AilmentTickSystem - ECS system for DoT processing</li>
     * </ul>
     */
    private void initializeAilmentSystem() {
        try {
            // Load ailment config
            Path configPath = dataFolder.resolve("config").resolve("ailments.yml");
            if (!Files.exists(configPath)) {
                copyResourceToFile("config/ailments.yml", configPath);
            }

            Yaml yaml = new Yaml();
            try (InputStream is = Files.newInputStream(configPath)) {
                ailmentConfig = yaml.loadAs(is, AilmentConfig.class);
            }

            if (ailmentConfig == null) {
                ailmentConfig = new AilmentConfig(); // Use defaults
                getLogger().atWarning().log("Ailment config null, using defaults");
            }

            ailmentConfig.validate();

            if (!ailmentConfig.isEnabled()) {
                getLogger().atInfo().log("Ailment system disabled in config");
                return;
            }

            // Create ailment tracker
            ailmentTracker = new AilmentTracker();

            // Create combat trackers (plugin-managed for disconnect cleanup)
            consecutiveHitTracker = new io.github.larsonix.trailoforbis.combat.tracking.ConsecutiveHitTracker();
            ailmentImmunityTracker = new io.github.larsonix.trailoforbis.ailments.AilmentImmunityTracker();

            // Create ailment calculator
            ailmentCalculator = new AilmentCalculator();

            // Create ailment effect manager (for Freeze slow visual effects)
            ailmentEffectManager = new AilmentEffectManager(this);
            ailmentEffectManager.initialize();

            // Register AilmentTickSystem for DoT processing
            // This runs every tick and processes Burn/Poison damage + duration tracking
            AilmentTickSystem ailmentTickSystem = new AilmentTickSystem(
                ailmentTracker, ailmentConfig, energyShieldTracker,
                playerId -> attributeManager != null ? attributeManager.getStats(playerId) : null);
            getEntityStoreRegistry().registerSystem(ailmentTickSystem);

            getLogger().atInfo().log("Ailment system initialized (tick_rate: %.2fs, burn_chance: %.0f%%, freeze_chance: %.0f%%, shock_chance: %.0f%%, poison_chance: %.0f%%)",
                    ailmentConfig.getTickRateSeconds(),
                    ailmentConfig.getBurn().getBaseChance(),
                    ailmentConfig.getFreeze().getBaseChance(),
                    ailmentConfig.getShock().getBaseChance(),
                    ailmentConfig.getPoison().getBaseChance());

        } catch (AilmentConfig.ConfigValidationException e) {
            getLogger().atWarning().log("Ailment config validation failed: %s - using defaults", e.getMessage());
            ailmentConfig = new AilmentConfig();
        } catch (Exception e) {
            getLogger().atWarning().withCause(e).log(
                    "Failed to initialize ailment system - elemental ailments disabled");
            ailmentTracker = null;
            ailmentCalculator = null;
            ailmentEffectManager = null;
            ailmentConfig = null;
        }
    }

    /**
     * Initializes the gear system for equipment stat bonuses and loot drops.
     *
     * <p>This uses {@link GearManager} which handles:
     * <ul>
     *   <li>Gear balance and modifier configs</li>
     *   <li>RequirementCalculator for gear requirements</li>
     *   <li>EquipmentValidator for requirement checking</li>
     *   <li>GearStatCalculator and GearStatApplier for stat bonuses</li>
     *   <li>GearGenerator for random gear generation</li>
     *   <li>ItemSyncService for client item definitions</li>
     *   <li>LootCalculator for drop chance calculations</li>
     *   <li>LootGenerator for creating gear drops</li>
     *   <li>LootListener ECS system for mob death drops</li>
     * </ul>
     *
     * <p><b>Important:</b> GearManager registers itself with ServiceRegistry as
     * GearService, which is used by RPGItemPreSyncSystem.
     */
    private void initializeGearSystem() {
        try {
            // Load gear-related configs (including vanilla conversion config)
            configManager.loadGearConfigs();

            // Create and initialize GearManager
            // This registers it with ServiceRegistry as GearService
            gearManager = new io.github.larsonix.trailoforbis.gear.GearManager(this, dataFolder.resolve("config"));
            gearManager.initialize();

            // Wire gear bonus provider to AttributeManager
            attributeManager.setGearBonusProvider(gearManager.createGearBonusProvider());

            // Wire conditional trigger system to AttributeManager (enables ON_KILL, ON_CRIT effects)
            if (conditionalTriggerSystem != null) {
                attributeManager.setConditionalTriggerSystem(conditionalTriggerSystem);
            }

            // Create and register gear equipment listener
            // This listener triggers stat recalculation on equipment changes
            gearEquipmentListener = new GearEquipmentListener(
                    gearManager.getStatCalculator(),
                    gearManager.getStatApplier(),
                    gearManager.getItemSyncService(),
                    gearManager.getSyncCoordinator());
            gearEquipmentListener.register(getEventRegistry());

            // Pickup notifications disabled — spammed chat on every inventory change.
            // Item sync is handled by RPGItemPreSyncSystem and ContainerSyncTickSystem.

            // Register loot listener as ECS system (handles mob death drops)
            LootListener lootListener = gearManager.getLootListener();
            if (lootListener != null) {
                getEntityStoreRegistry().registerSystem(lootListener);
                getLogger().atInfo().log("Loot system registered");
            }

            getLogger().atInfo().log("Gear system initialized via GearManager - equipment bonuses, item sync, and loot enabled");

            // Initialize Gem System (requires GearManager's ItemRegistryService and CustomItemSyncService)
            initializeGemSystem();

        } catch (Exception e) {
            getLogger().atWarning().withCause(e).log(
                    "Failed to initialize gear system - equipment bonuses disabled");
            gearManager = null;
        }
    }

    /**
     * Initializes the skill gem system for ability socketing.
     *
     * <p>Requires GearManager to be initialized (provides ItemRegistryService and CustomItemSyncService).
     */
    private void initializeGemSystem() {
        try {
            gemManager = new GemManager(dataFolder.resolve("config"), getClass().getClassLoader());
            gemManager.initialize(
                gearManager.getItemRegistryService(),
                gearManager.getCustomItemSyncService()
            );
            ServiceRegistry.register(GemManager.class, gemManager);
            getLogger().atInfo().log("Gem system initialized and registered in ServiceRegistry");
        } catch (Exception e) {
            getLogger().atWarning().withCause(e).log("Failed to initialize Gem System - gem socketing disabled");
            gemManager = null;
        }
    }


    /** @return null if loading failed */
    @Nullable
    private SkillTreeConfig loadSkillTreeConfig() {
        Path configPath = dataFolder.resolve("config").resolve("skill-tree.yml");
        try {
            if (!Files.exists(configPath)) {
                // Copy default from resources
                copyResourceToFile("config/skill-tree.yml", configPath);
            }
            Yaml yaml = new Yaml();
            try (InputStream is = Files.newInputStream(configPath)) {
                return yaml.loadAs(is, SkillTreeConfig.class);
            }
        } catch (IOException e) {
            getLogger().atWarning().log("Failed to load skill-tree.yml: %s", e.getMessage());
            return null;
        } catch (Exception e) {
            getLogger().atSevere().log("Failed to parse skill-tree.yml: %s", e.getMessage());
            return null;
        }
    }

    /** @return null if disabled or not initialized */
    @Nullable
    public io.github.larsonix.trailoforbis.lootfilter.LootFilterManager getLootFilterManager() {
        return lootFilterManager;
    }

    /** @return null if disabled or not initialized */
    @Nullable
    public io.github.larsonix.trailoforbis.combat.indicators.color.CombatTextColorManager getCombatTextColorManager() {
        return combatTextColorManager;
    }

    /**
     * Initializes the loot filter system (pickup filtering for RPG gear).
     *
     * <p>Creates the manager, registers the ECS pickup system, and registers
     * the {@code /lf} command. The FilteredPickupInteraction is already registered
     * in setup() (before assets load) so item definitions can reference it.
     */
    private void initializeLootFilterSystem() {
        try {
            var lfConfig = configManager.getLootFilterConfig();

            if (!lfConfig.isEnabled()) {
                getLogger().atInfo().log("Loot filter system disabled in config");
                return;
            }

            lootFilterManager = new io.github.larsonix.trailoforbis.lootfilter.LootFilterManager(
                    dataManager, lfConfig);
            lootFilterManager.initialize();

            // Register loot filter as FIRST handler on InventoryChangeEventSystem
            // This ensures filtered items are ejected before other handlers process them
            var feedbackService = new io.github.larsonix.trailoforbis.lootfilter.feedback.BlockFeedbackService(
                    lfConfig.getFeedback());
            var filterHandler = new io.github.larsonix.trailoforbis.lootfilter.system.LootFilterInventoryHandler(
                    lootFilterManager, feedbackService);
            inventoryChangeEventSystem.addFirstHandler(filterHandler::onInventoryChange);

            // Register /lf command
            getCommandRegistry().registerCommand(
                    new io.github.larsonix.trailoforbis.lootfilter.command.LfCommand(lootFilterManager));

            getLogger().atInfo().log("Loot filter system initialized (%d presets)",
                    lootFilterManager.getPresetNames().size());

            // Initialize Vuetale reactive UI for the loot filter page
            try {
                io.github.larsonix.trailoforbis.lootfilter.bridge.VuetaleIntegration.initialize(
                        lootFilterManager, this);
            } catch (Exception ve) {
                getLogger().atWarning().withCause(ve).log(
                        "Vuetale UI initialization failed - loot filter will use fallback commands only");
            }

        } catch (Exception e) {
            getLogger().atWarning().withCause(e).log(
                    "Failed to initialize loot filter system - filtering disabled");
            lootFilterManager = null;
        }
    }

    private void copyResourceToFile(String resourcePath, Path destPath) throws IOException {
        Files.createDirectories(destPath.getParent());
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                getLogger().atWarning().log("Resource not found: %s", resourcePath);
                return;
            }
            Files.copy(is, destPath);
            getLogger().atInfo().log("Created default config: %s", destPath);
        }
    }
}
