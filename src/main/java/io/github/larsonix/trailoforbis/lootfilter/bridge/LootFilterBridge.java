package io.github.larsonix.trailoforbis.lootfilter.bridge;

import com.google.gson.Gson;
import com.hypixel.hytale.logger.HytaleLogger;
import io.github.larsonix.trailoforbis.gear.model.GearRarity;
import io.github.larsonix.trailoforbis.lootfilter.LootFilterManager;
import io.github.larsonix.trailoforbis.lootfilter.model.ConditionType;
import io.github.larsonix.trailoforbis.lootfilter.model.FilterAction;
import io.github.larsonix.trailoforbis.lootfilter.model.FilterCondition;
import io.github.larsonix.trailoforbis.lootfilter.model.FilterProfile;
import io.github.larsonix.trailoforbis.lootfilter.model.FilterRule;
import io.github.larsonix.trailoforbis.lootfilter.model.PlayerFilterState;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.TrailOfOrbis;
import li.kelp.vuetale.app.PlayerUi;
import li.kelp.vuetale.app.PlayerUiManager;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Java bridge exposed to JavaScript via {@code globalThis.lootBridge}.
 *
 * <p>All public methods are callable from Vue event handlers via Javet's proxy converter.
 * Methods run on the V8 thread (invoked from JS callbacks), so they must NOT call
 * {@code App.setData()} (which would deadlock via {@code runOnV8Thread}).
 *
 * <p>Instead, methods return a JSON string of the updated state. The Vue component
 * parses it and updates its local reactive ref — triggering a re-render automatically.
 *
 * <p>Thread safety: {@link LootFilterManager} operations are safe from any thread
 * (ConcurrentHashMap cache + database writes).
 */
public class LootFilterBridge {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final Gson GSON = new Gson();

    private final LootFilterManager filterManager;
    private final TrailOfOrbis plugin;

    public LootFilterBridge(@Nonnull LootFilterManager filterManager, @Nonnull TrailOfOrbis plugin) {
        this.filterManager = filterManager;
        this.plugin = plugin;
    }

    // ═══════════════════════════════════════════════════════════════════
    // PAGE NAVIGATION (dispatches to world thread)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Close the loot filter page. Called from JS when player clicks Close.
     * Dispatches to world thread since page operations require it.
     */
    public void closePage(String playerId) {
        UUID uuid = UUID.fromString(playerId);
        navigateOnWorldThread(uuid, (playerRef, store) -> {
            // Unmount Vue app first
            PlayerUi ui = PlayerUiManager.INSTANCE.get(uuid);
            if (ui != null) {
                ui.closePage();
            }

            // Then close the HyUI page via PageManager to properly release cursor/movement.
            // Vuetale's closePage() only unmounts Vue — it does NOT release input controls.
            // PageManager.setPage(Page.None) is the public equivalent of CustomUIPage.close().
            Ref<EntityStore> ref = playerRef.getReference();
            if (ref != null && ref.isValid()) {
                com.hypixel.hytale.server.core.entity.entities.Player player =
                    store.getComponent(ref, com.hypixel.hytale.server.core.entity.entities.Player.getComponentType());
                if (player != null) {
                    player.getPageManager().setPage(ref, store,
                        com.hypixel.hytale.protocol.packets.interface_.Page.None);
                }
            }
        });
    }

    /**
     * Navigate to the Stats page (closes loot filter, opens Stats).
     */
    public void navigateToStats(String playerId) {
        UUID uuid = UUID.fromString(playerId);
        navigateOnWorldThread(uuid, (playerRef, store) -> {
            new io.github.larsonix.trailoforbis.ui.stats.StatsPage(plugin, playerRef).open(store);
        });
    }

    /**
     * Navigate to the Attributes page (closes loot filter, opens Attributes).
     */
    public void navigateToAttributes(String playerId) {
        UUID uuid = UUID.fromString(playerId);
        navigateOnWorldThread(uuid, (playerRef, store) -> {
            new io.github.larsonix.trailoforbis.ui.attributes.AttributePage(plugin, playerRef).open(store);
        });
    }

    /**
     * Navigate to the Skill Sanctum (closes loot filter, toggles sanctum).
     */
    public void navigateToSkillTree(String playerId) {
        UUID uuid = UUID.fromString(playerId);
        navigateOnWorldThread(uuid, (playerRef, store) -> {
            var realmsManager = plugin.getRealmsManager();
            if (realmsManager != null && realmsManager.isPlayerInCombatRealm(playerRef)) {
                playerRef.sendMessage(com.hypixel.hytale.server.core.Message.raw(
                        "Cannot access Skill Sanctum while in a combat realm !")
                        .color(io.github.larsonix.trailoforbis.util.MessageColors.ERROR));
                return;
            }
            var sanctum = plugin.getSkillSanctumManager();
            if (sanctum != null && sanctum.isEnabled()) {
                if (sanctum.hasActiveSanctum(uuid)) sanctum.closeSanctum(uuid);
                else sanctum.openSanctum(playerRef);
            }
        });
    }

    @FunctionalInterface
    private interface NavigationAction {
        void execute(PlayerRef playerRef, Store<EntityStore> store);
    }

    private void navigateOnWorldThread(UUID playerId, NavigationAction action) {
        PlayerUi ui = PlayerUiManager.INSTANCE.get(playerId);
        if (ui == null) return;

        // Kotlin internal → public with $Vuetale suffix in compiled JAR
        PlayerRef playerRef = ui.getPlayerRef$Vuetale();
        if (playerRef == null) return;

        Ref<EntityStore> ref = playerRef.getReference();
        if (ref == null || !ref.isValid()) return;

        Store<EntityStore> store = ref.getStore();
        World world = store.getExternalData().getWorld();

        world.execute(() -> {
            PlayerRef freshPlayer = store.getComponent(ref, PlayerRef.getComponentType());
            if (freshPlayer != null) {
                action.execute(freshPlayer, store);
            }
        });
    }

    // ═══════════════════════════════════════════════════════════════════
    // QUICK FILTER
    // ═══════════════════════════════════════════════════════════════════

    public String setQuickFilter(String playerId, String rarity) {
        try {
            UUID uuid = UUID.fromString(playerId);
            filterManager.setQuickFilter(uuid, GearRarity.valueOf(rarity.toUpperCase()));
            return serializeStateJson(uuid);
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Bridge: setQuickFilter failed");
            return "null";
        }
    }

    public String clearQuickFilter(String playerId) {
        try {
            UUID uuid = UUID.fromString(playerId);
            filterManager.clearQuickFilter(uuid);
            return serializeStateJson(uuid);
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Bridge: clearQuickFilter failed");
            return "null";
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // FILTERING TOGGLE
    // ═══════════════════════════════════════════════════════════════════

    public String setFilteringEnabled(String playerId, boolean enabled) {
        try {
            UUID uuid = UUID.fromString(playerId);
            filterManager.setFilteringEnabled(uuid, enabled);
            return serializeStateJson(uuid);
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Bridge: setFilteringEnabled failed");
            return "null";
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // PROFILE MANAGEMENT
    // ═══════════════════════════════════════════════════════════════════

    public String activateProfile(String playerId, String profileId) {
        try {
            UUID uuid = UUID.fromString(playerId);
            filterManager.setActiveProfile(uuid, profileId);
            return serializeStateJson(uuid);
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Bridge: activateProfile failed");
            return "null";
        }
    }

    public String createProfile(String playerId, String name) {
        try {
            UUID uuid = UUID.fromString(playerId);
            filterManager.createProfile(uuid, name);
            return serializeStateJson(uuid);
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Bridge: createProfile failed");
            return "null";
        }
    }

    public String copyPreset(String playerId, String presetName) {
        try {
            UUID uuid = UUID.fromString(playerId);
            filterManager.createProfileFromPreset(uuid, presetName);
            return serializeStateJson(uuid);
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Bridge: copyPreset failed");
            return "null";
        }
    }

    public String deleteProfile(String playerId, String profileId) {
        try {
            UUID uuid = UUID.fromString(playerId);
            filterManager.deleteProfile(uuid, profileId);
            return serializeStateJson(uuid);
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Bridge: deleteProfile failed");
            return "null";
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // PROFILE EDITING
    // ═══════════════════════════════════════════════════════════════════

    public String setDefaultAction(String playerId, String profileId, String action) {
        try {
            UUID uuid = UUID.fromString(playerId);
            FilterProfile profile = getProfileOrThrow(uuid, profileId);
            filterManager.saveProfile(uuid, profile.withDefaultAction(FilterAction.valueOf(action.toUpperCase())));
            return serializeStateJson(uuid);
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Bridge: setDefaultAction failed");
            return "null";
        }
    }

    public String toggleRuleEnabled(String playerId, String profileId, int ruleIndex) {
        try {
            UUID uuid = UUID.fromString(playerId);
            FilterProfile profile = getProfileOrThrow(uuid, profileId);
            FilterRule rule = profile.getRules().get(ruleIndex);
            filterManager.saveProfile(uuid, profile.withUpdatedRule(ruleIndex, rule.withEnabled(!rule.enabled())));
            return serializeStateJson(uuid);
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Bridge: toggleRuleEnabled failed");
            return "null";
        }
    }

    public String deleteRule(String playerId, String profileId, int ruleIndex) {
        try {
            UUID uuid = UUID.fromString(playerId);
            FilterProfile profile = getProfileOrThrow(uuid, profileId);
            filterManager.saveProfile(uuid, profile.withRemovedRule(ruleIndex));
            return serializeStateJson(uuid);
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Bridge: deleteRule failed");
            return "null";
        }
    }

    public String moveRule(String playerId, String profileId, int fromIndex, int toIndex) {
        try {
            UUID uuid = UUID.fromString(playerId);
            FilterProfile profile = getProfileOrThrow(uuid, profileId);
            filterManager.saveProfile(uuid, profile.withMovedRule(fromIndex, toIndex));
            return serializeStateJson(uuid);
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Bridge: moveRule failed");
            return "null";
        }
    }

    public String addRule(String playerId, String profileId, String ruleName) {
        try {
            UUID uuid = UUID.fromString(playerId);
            FilterProfile profile = getProfileOrThrow(uuid, profileId);
            FilterRule newRule = new FilterRule(
                    ruleName != null && !ruleName.isEmpty() ? ruleName : "New Rule",
                    true, FilterAction.ALLOW, List.of());
            filterManager.saveProfile(uuid, profile.withAddedRule(newRule));
            return serializeStateJson(uuid);
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Bridge: addRule failed");
            return "null";
        }
    }

    public String setRuleAction(String playerId, String profileId, int ruleIndex, String action) {
        try {
            UUID uuid = UUID.fromString(playerId);
            FilterProfile profile = getProfileOrThrow(uuid, profileId);
            FilterRule rule = profile.getRules().get(ruleIndex);
            FilterRule updated = new FilterRule(rule.name(), rule.enabled(),
                    FilterAction.valueOf(action.toUpperCase()), rule.conditions());
            filterManager.saveProfile(uuid, profile.withUpdatedRule(ruleIndex, updated));
            return serializeStateJson(uuid);
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Bridge: setRuleAction failed");
            return "null";
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // CONDITION MANAGEMENT
    // ═══════════════════════════════════════════════════════════════════

    public String addCondition(String playerId, String profileId, int ruleIndex, String conditionType) {
        try {
            UUID uuid = UUID.fromString(playerId);
            FilterProfile profile = getProfileOrThrow(uuid, profileId);
            FilterRule rule = profile.getRules().get(ruleIndex);
            FilterCondition newCond = createDefaultCondition(ConditionType.valueOf(conditionType.toUpperCase()));
            List<FilterCondition> updatedConds = new ArrayList<>(rule.conditions());
            updatedConds.add(newCond);
            FilterRule updatedRule = new FilterRule(rule.name(), rule.enabled(), rule.action(), updatedConds);
            filterManager.saveProfile(uuid, profile.withUpdatedRule(ruleIndex, updatedRule));
            return serializeStateJson(uuid);
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Bridge: addCondition failed");
            return "null";
        }
    }

    public String removeCondition(String playerId, String profileId, int ruleIndex, int conditionIndex) {
        try {
            UUID uuid = UUID.fromString(playerId);
            FilterProfile profile = getProfileOrThrow(uuid, profileId);
            FilterRule rule = profile.getRules().get(ruleIndex);
            List<FilterCondition> updatedConds = new ArrayList<>(rule.conditions());
            updatedConds.remove(conditionIndex);
            FilterRule updatedRule = new FilterRule(rule.name(), rule.enabled(), rule.action(), updatedConds);
            filterManager.saveProfile(uuid, profile.withUpdatedRule(ruleIndex, updatedRule));
            return serializeStateJson(uuid);
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Bridge: removeCondition failed");
            return "null";
        }
    }

    /**
     * Updates a condition with new parameters. The conditionJson is a JSON object
     * containing the condition type and its type-specific fields.
     */
    public String updateCondition(String playerId, String profileId, int ruleIndex,
                                  int conditionIndex, String conditionJson) {
        try {
            UUID uuid = UUID.fromString(playerId);
            FilterProfile profile = getProfileOrThrow(uuid, profileId);
            FilterRule rule = profile.getRules().get(ruleIndex);
            FilterCondition newCond = deserializeCondition(conditionJson);
            List<FilterCondition> updatedConds = new ArrayList<>(rule.conditions());
            updatedConds.set(conditionIndex, newCond);
            FilterRule updatedRule = new FilterRule(rule.name(), rule.enabled(), rule.action(), updatedConds);
            filterManager.saveProfile(uuid, profile.withUpdatedRule(ruleIndex, updatedRule));
            return serializeStateJson(uuid);
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Bridge: updateCondition failed");
            return "null";
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // SERIALIZATION — PlayerFilterState → JSON
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Serializes the full filter state for a player as a JSON string.
     * Called from the game thread (via UIManager) for initial state push,
     * and from bridge methods (V8 thread) for post-action updates.
     */
    @Nonnull
    public String serializeStateJson(@Nonnull UUID playerId) {
        return GSON.toJson(serializeState(playerId));
    }

    @Nonnull
    public Map<String, Object> serializeState(@Nonnull UUID playerId) {
        PlayerFilterState state = filterManager.getState(playerId);
        Map<String, Object> map = new LinkedHashMap<>();

        map.put("playerId", playerId.toString());
        map.put("filteringEnabled", state.isFilteringEnabled());
        map.put("quickFilterRarity", state.getQuickFilterRarity() != null
                ? state.getQuickFilterRarity().name() : null);
        map.put("activeProfileId", state.getActiveProfileId());
        map.put("maxProfiles", filterManager.getConfig().getMaxProfilesPerPlayer());
        map.put("maxRulesPerProfile", filterManager.getConfig().getMaxRulesPerProfile());
        map.put("maxConditionsPerRule", filterManager.getConfig().getMaxConditionsPerRule());
        map.put("presetNames", filterManager.getPresetNames());

        List<Map<String, Object>> profiles = new ArrayList<>();
        for (FilterProfile profile : state.getProfiles()) {
            profiles.add(serializeProfile(profile));
        }
        map.put("profiles", profiles);

        return map;
    }

    @Nonnull
    private Map<String, Object> serializeProfile(@Nonnull FilterProfile profile) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", profile.getId());
        map.put("name", profile.getName());
        map.put("defaultAction", profile.getDefaultAction().name());

        List<Map<String, Object>> rules = new ArrayList<>();
        for (FilterRule rule : profile.getRules()) {
            rules.add(serializeRule(rule));
        }
        map.put("rules", rules);

        return map;
    }

    @Nonnull
    private Map<String, Object> serializeRule(@Nonnull FilterRule rule) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", rule.name());
        map.put("enabled", rule.enabled());
        map.put("action", rule.action().name());
        map.put("summary", rule.describeSummary());

        List<Map<String, Object>> conditions = new ArrayList<>();
        for (FilterCondition cond : rule.conditions()) {
            conditions.add(serializeCondition(cond));
        }
        map.put("conditions", conditions);

        return map;
    }

    @Nonnull
    private Map<String, Object> serializeCondition(@Nonnull FilterCondition condition) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", condition.type().name());
        map.put("displayName", condition.type().getDisplayName());
        map.put("description", condition.describe());

        switch (condition) {
            case FilterCondition.MinRarity c ->
                    map.put("threshold", c.threshold().name());
            case FilterCondition.MaxRarity c ->
                    map.put("threshold", c.threshold().name());
            case FilterCondition.EquipmentSlotCondition c ->
                    map.put("slots", List.copyOf(c.slots()));
            case FilterCondition.WeaponTypeCondition c ->
                    map.put("types", c.types().stream().map(Enum::name).toList());
            case FilterCondition.ArmorMaterialCondition c ->
                    map.put("materials", c.materials().stream().map(Enum::name).toList());
            case FilterCondition.ItemLevelRange c -> {
                map.put("min", c.min());
                map.put("max", c.max());
            }
            case FilterCondition.QualityRange c -> {
                map.put("min", c.min());
                map.put("max", c.max());
            }
            case FilterCondition.RequiredModifiers c -> {
                map.put("modifierIds", List.copyOf(c.modifierIds()));
                map.put("minCount", c.minCount());
            }
            case FilterCondition.ModifierValueRange c -> {
                map.put("modifierId", c.modifierId());
                map.put("minValue", c.minValue());
                map.put("maxValue", c.maxValue());
            }
            case FilterCondition.ImplicitCondition c -> {
                map.put("minPercentile", c.minPercentile());
                map.put("damageTypes", List.copyOf(c.damageTypes()));
            }
            case FilterCondition.MinModifierCount c ->
                    map.put("count", c.count());
            case FilterCondition.CorruptionStateCondition c ->
                    map.put("filter", c.filter().name());
        }

        return map;
    }

    // ═══════════════════════════════════════════════════════════════════
    // DESERIALIZATION — JSON → FilterCondition
    // ═══════════════════════════════════════════════════════════════════

    @Nonnull
    @SuppressWarnings("unchecked")
    private FilterCondition deserializeCondition(@Nonnull String json) {
        Map<String, Object> map = GSON.fromJson(json, Map.class);
        String type = (String) map.get("type");

        return switch (ConditionType.valueOf(type)) {
            case MIN_RARITY -> new FilterCondition.MinRarity(
                    GearRarity.valueOf((String) map.get("threshold")));
            case MAX_RARITY -> new FilterCondition.MaxRarity(
                    GearRarity.valueOf((String) map.get("threshold")));
            case EQUIPMENT_SLOT -> new FilterCondition.EquipmentSlotCondition(
                    Set.copyOf((List<String>) map.get("slots")));
            case WEAPON_TYPE -> new FilterCondition.WeaponTypeCondition(
                    Set.copyOf(((List<String>) map.get("types")).stream()
                            .map(io.github.larsonix.trailoforbis.gear.model.WeaponType::valueOf)
                            .toList()));
            case ARMOR_MATERIAL -> new FilterCondition.ArmorMaterialCondition(
                    Set.copyOf(((List<String>) map.get("materials")).stream()
                            .map(io.github.larsonix.trailoforbis.gear.model.ArmorMaterial::valueOf)
                            .toList()));
            case ITEM_LEVEL_RANGE -> new FilterCondition.ItemLevelRange(
                    ((Number) map.get("min")).intValue(),
                    ((Number) map.get("max")).intValue());
            case QUALITY_RANGE -> new FilterCondition.QualityRange(
                    ((Number) map.get("min")).intValue(),
                    ((Number) map.get("max")).intValue());
            case REQUIRED_MODIFIERS -> new FilterCondition.RequiredModifiers(
                    Set.copyOf((List<String>) map.get("modifierIds")),
                    ((Number) map.get("minCount")).intValue());
            case MODIFIER_VALUE_RANGE -> new FilterCondition.ModifierValueRange(
                    (String) map.get("modifierId"),
                    ((Number) map.get("minValue")).doubleValue(),
                    ((Number) map.get("maxValue")).doubleValue());
            case IMPLICIT_CONDITION -> new FilterCondition.ImplicitCondition(
                    ((Number) map.get("minPercentile")).doubleValue(),
                    Set.copyOf((List<String>) map.get("damageTypes")));
            case MIN_MODIFIER_COUNT -> new FilterCondition.MinModifierCount(
                    ((Number) map.get("count")).intValue());
            case CORRUPTION_STATE -> new FilterCondition.CorruptionStateCondition(
                    io.github.larsonix.trailoforbis.lootfilter.model.CorruptionFilter.valueOf(
                            (String) map.get("filter")));
        };
    }

    // ═══════════════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════════════

    @Nonnull
    private FilterProfile getProfileOrThrow(@Nonnull UUID playerId, @Nonnull String profileId) {
        return filterManager.getState(playerId).getProfileById(profileId)
                .orElseThrow(() -> new IllegalArgumentException("Profile not found: " + profileId));
    }

    @Nonnull
    private static FilterCondition createDefaultCondition(@Nonnull ConditionType type) {
        return switch (type) {
            case MIN_RARITY -> new FilterCondition.MinRarity(GearRarity.RARE);
            case MAX_RARITY -> new FilterCondition.MaxRarity(GearRarity.LEGENDARY);
            case EQUIPMENT_SLOT -> new FilterCondition.EquipmentSlotCondition(Set.of("weapon"));
            case WEAPON_TYPE -> new FilterCondition.WeaponTypeCondition(
                    Set.of(io.github.larsonix.trailoforbis.gear.model.WeaponType.SWORD));
            case ARMOR_MATERIAL -> new FilterCondition.ArmorMaterialCondition(
                    Set.of(io.github.larsonix.trailoforbis.gear.model.ArmorMaterial.LEATHER));
            case ITEM_LEVEL_RANGE -> new FilterCondition.ItemLevelRange(1, 100);
            case QUALITY_RANGE -> new FilterCondition.QualityRange(50, 100);
            case REQUIRED_MODIFIERS -> new FilterCondition.RequiredModifiers(Set.of(), 1);
            case MODIFIER_VALUE_RANGE -> new FilterCondition.ModifierValueRange("crit_chance", 0, 100);
            case IMPLICIT_CONDITION -> new FilterCondition.ImplicitCondition(0.0, Set.of());
            case MIN_MODIFIER_COUNT -> new FilterCondition.MinModifierCount(3);
            case CORRUPTION_STATE -> new FilterCondition.CorruptionStateCondition(
                    io.github.larsonix.trailoforbis.lootfilter.model.CorruptionFilter.EITHER);
        };
    }
}
