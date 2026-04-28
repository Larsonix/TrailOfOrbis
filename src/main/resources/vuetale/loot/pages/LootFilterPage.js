import { defineComponent, ref, computed, h, watch } from "vue";
import { Common } from "@core/components/Common.js";
import { useData } from "@core/composables/useData.js";

// ═══════════════════════════════════════════════════════════════════
// COLORS — matches RPGStyles.java
// ═══════════════════════════════════════════════════════════════════

const COLORS = {
  TITLE_GOLD: "#FFD700",
  TEXT_PRIMARY: "#FFFFFF",
  TEXT_SECONDARY: "#D0DCEA",
  TEXT_MUTED: "#777788",
  POSITIVE: "#55FF55",
  NEGATIVE: "#FF5555",
  STAT_VALUE: "#CCDDEE",
  DIVIDER: "#333344",
  SECTION_BG: "#222233",
};

const RARITY_COLORS = {
  COMMON: "#C9D2DD",
  UNCOMMON: "#3E9049",
  RARE: "#2770B7",
  EPIC: "#8B339E",
  LEGENDARY: "#BB8A2C",
  MYTHIC: "#FF4500",
  UNIQUE: "#AF6025",
};

const QUICK_FILTER_RARITIES = ["UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC"];

// Vanilla Hytale scrollbar styling (same textures used by all native UIs)
const SCROLLBAR_STYLE = {
  Spacing: 6,
  Size: 6,
  Background: { TexturePath: "Common/Scrollbar.png", Border: 3 },
  Handle: { TexturePath: "Common/ScrollbarHandle.png", Border: 3 },
  HoveredHandle: { TexturePath: "Common/ScrollbarHandleHovered.png", Border: 3 },
  DraggedHandle: { TexturePath: "Common/ScrollbarHandleDragged.png", Border: 3 },
};

// ═══════════════════════════════════════════════════════════════════
// HELPERS
// ═══════════════════════════════════════════════════════════════════

function capitalize(str) {
  if (!str) return "";
  return str.charAt(0).toUpperCase() + str.slice(1).toLowerCase();
}

function sectionHeader(title, rightText) {
  const children = [
    h("Label", {
      text: title,
      elStyle: { FontSize: 14, TextColor: COLORS.TITLE_GOLD },
    }),
    h("Group", { anchor: { Width: 8 } }),
    h("Group", {
      flexWeight: 1,
      anchor: { Height: 1 },
      background: { Color: COLORS.DIVIDER },
    }),
  ];
  if (rightText) {
    children.push(h("Group", { anchor: { Width: 8 } }));
    children.push(
      h("Label", {
        text: rightText,
        elStyle: { FontSize: 12, TextColor: COLORS.TEXT_MUTED },
      })
    );
  }
  return h(
    "Group",
    {
      layoutMode: "Left",
      anchor: { Horizontal: 0, Height: 24 },
      padding: { Horizontal: 20 },
    },
    children
  );
}

function spacer(height) {
  return h("Group", { anchor: { Height: height } });
}

function noteText(text) {
  return h(
    "Group",
    {
      layoutMode: "Left",
      anchor: { Horizontal: 0 },
      padding: { Horizontal: 25 },
    },
    [
      h("Label", {
        text: text,
        elStyle: { FontSize: 12, TextColor: COLORS.TEXT_MUTED },
      }),
    ]
  );
}

// ═══════════════════════════════════════════════════════════════════
// MAIN PAGE COMPONENT
// ═══════════════════════════════════════════════════════════════════

export default defineComponent({
  name: "LootFilterPage",
  setup() {
    // ── Reactive data from Java bridge ──
    const stateJson = useData("stateJson", "null");
    const playerId = useData("playerId", "");

    const state = computed(() => {
      try {
        const parsed = JSON.parse(stateJson.value);
        return parsed;
      } catch {
        return null;
      }
    });

    // ── Local UI state ──
    const view = ref("home"); // "home" | "rules" | "editRule"
    const editingProfileId = ref(null);
    const editingRuleIndex = ref(-1);

    // ── Bridge calls (return updated state JSON) ──
    function callBridge(method, ...args) {
      try {
        const result = globalThis.lootBridge[method](...args);
        if (result && result !== "null") {
          // Bridge returns fresh state JSON — update via useData won't work
          // because we're on V8 thread. Instead, we'll parse directly.
          // The stateJson ref won't auto-update, but we can force it via
          // the app's setData mechanism by returning the parsed value.
          // For now, store in a local override ref.
          stateOverride.value = JSON.parse(result);
        }
      } catch (e) {
        console.log("[LootFilter] Bridge call failed: " + method + " - " + e);
      }
    }

    // Local state override (when bridge returns fresh state directly)
    const stateOverride = ref(null);

    // Effective state: use override if available, otherwise use pushed data
    const effectiveState = computed(() => {
      return stateOverride.value || state.value;
    });

    // Reset override when new data is pushed from Java
    watch(state, () => {
      stateOverride.value = null;
    });

    // ── Quick Filter handlers ──
    function onQuickFilter(rarity) {
      if (rarity === null) {
        callBridge("clearQuickFilter", playerId.value);
      } else {
        callBridge("setQuickFilter", playerId.value, rarity);
      }
    }

    function onToggleFiltering(enabled) {
      callBridge("setFilteringEnabled", playerId.value, enabled);
    }

    // ── Profile handlers ──
    function onActivateProfile(profileId) {
      callBridge("activateProfile", playerId.value, profileId);
    }

    function onDeleteProfile(profileId) {
      callBridge("deleteProfile", playerId.value, profileId);
    }

    function onCreateProfile() {
      const s = effectiveState.value;
      const count = s ? s.profiles.length : 0;
      callBridge("createProfile", playerId.value, "Filter " + (count + 1));
    }

    function onCopyPreset(presetName) {
      callBridge("copyPreset", playerId.value, presetName);
    }

    function onEditProfile(profileId) {
      editingProfileId.value = profileId;
      view.value = "rules";
    }

    function onBackToHome() {
      view.value = "home";
      editingProfileId.value = null;
    }

    // ── Rules handlers ──
    function onSetDefaultAction(action) {
      callBridge(
        "setDefaultAction",
        playerId.value,
        editingProfileId.value,
        action
      );
    }

    function onToggleRule(ruleIndex) {
      callBridge(
        "toggleRuleEnabled",
        playerId.value,
        editingProfileId.value,
        ruleIndex
      );
    }

    function onDeleteRule(ruleIndex) {
      callBridge(
        "deleteRule",
        playerId.value,
        editingProfileId.value,
        ruleIndex
      );
    }

    function onMoveRule(fromIndex, toIndex) {
      callBridge(
        "moveRule",
        playerId.value,
        editingProfileId.value,
        fromIndex,
        toIndex
      );
    }

    function onAddRule() {
      callBridge(
        "addRule",
        playerId.value,
        editingProfileId.value,
        "New Rule"
      );
    }

    function onEditRule(ruleIndex) {
      editingRuleIndex.value = ruleIndex;
      view.value = "editRule";
    }

    function onBackToRules() {
      view.value = "rules";
      editingRuleIndex.value = -1;
    }

    // ── Condition handlers ──
    function onSetRuleAction(action) {
      callBridge(
        "setRuleAction",
        playerId.value,
        editingProfileId.value,
        editingRuleIndex.value,
        action
      );
    }

    function onAddCondition(conditionType) {
      callBridge(
        "addCondition",
        playerId.value,
        editingProfileId.value,
        editingRuleIndex.value,
        conditionType
      );
    }

    function onRemoveCondition(conditionIndex) {
      callBridge(
        "removeCondition",
        playerId.value,
        editingProfileId.value,
        editingRuleIndex.value,
        conditionIndex
      );
    }

    function onUpdateCondition(conditionIndex, conditionData) {
      callBridge(
        "updateCondition",
        playerId.value,
        editingProfileId.value,
        editingRuleIndex.value,
        conditionIndex,
        JSON.stringify(conditionData)
      );
    }

    // ═══════════════════════════════════════════════════════════════
    // RENDER
    // ═══════════════════════════════════════════════════════════════

    return () => {
      const s = effectiveState.value;
      if (!s) {
        return h("Group", { anchor: { Full: 0 } }, [
          h("Label", {
            text: "Loading...",
            elStyle: { FontSize: 16, TextColor: COLORS.TEXT_MUTED },
          }),
        ]);
      }

      // Determine title and height based on current view
      let title = "Loot Filter";
      let containerHeight = 700;

      if (view.value === "rules") {
        const profile = s.profiles.find(
          (p) => p.id === editingProfileId.value
        );
        title = "Loot Filter  >  " + (profile ? profile.name : "Profile");
        containerHeight = 620;
      } else if (view.value === "editRule") {
        const profile = s.profiles.find(
          (p) => p.id === editingProfileId.value
        );
        const rule =
          profile && editingRuleIndex.value >= 0
            ? profile.rules[editingRuleIndex.value]
            : null;
        title =
          "Loot Filter  >  " +
          (profile ? profile.name : "Profile") +
          "  >  " +
          (rule ? rule.name : "New Rule");
        containerHeight = 700;
      }

      return h("Group", { anchor: { Full: 0 }, layoutMode: "Full" }, [
        // Dark overlay background
        // Dark semi-transparent overlay — must use object format, not string
        // (string would be interpreted as texture path → missing texture red cross)
        h("Group", { anchor: { Full: 0 }, background: { Color: "#000000(0.45)" } }),
        // Centered layout
        h(
          "Group",
          { anchor: { Full: 0 }, layoutMode: "MiddleCenter" },
          [
            h("Group", { layoutMode: "Top" }, [
              // Main container
              h(
                Common.DecoratedContainer,
                {
                  anchor: { Width: 800, Height: containerHeight },
                },
                {
                  title: () => [h(Common.Title, { text: title })],
                  content: () => [
                    h("Group", {
                      layoutMode: "TopScrolling",
                      anchor: { Horizontal: 0 },
                      flexWeight: 1,
                      scrollbarStyle: SCROLLBAR_STYLE,
                    }, (() => {
                      if (view.value === "home") return renderHomeView(s);
                      if (view.value === "rules") return renderRulesView(s);
                      return renderEditRuleView(s);
                    })()),
                  ],
                }
              ),
              // Navigation footer
              spacer(10),
              renderNavFooter(),
            ]),
          ]
        ),
      ]);
    };

    // ═══════════════════════════════════════════════════════════════
    // HOME VIEW
    // ═══════════════════════════════════════════════════════════════

    function renderHomeView(s) {
      const children = [];

      // ── Quick Filter section ──
      children.push(spacer(8));
      children.push(sectionHeader("Quick Filter"));
      children.push(spacer(4));

      // Quick filter buttons — LeftCenterWrap auto-wraps when row exceeds width
      const qfButtonDefs = [
        { key: "qf-off", text: "Off", rarity: null, width: 80 },
        { key: "qf-uncommon", text: "Uncommon", rarity: "UNCOMMON", width: 150 },
        { key: "qf-rare", text: "Rare", rarity: "RARE", width: 100 },
        { key: "qf-epic", text: "Epic", rarity: "EPIC", width: 100 },
        { key: "qf-legendary", text: "Legendary", rarity: "LEGENDARY", width: 160 },
        { key: "qf-mythic", text: "Mythic", rarity: "MYTHIC", width: 120 },
      ];
      children.push(
        h("Group", {
          layoutMode: "LeftCenterWrap",
          anchor: { Horizontal: 0 },
          padding: { Horizontal: 20 },
        }, qfButtonDefs.map(def =>
          h(Common.SmallSecondaryTextButton, {
            key: def.key,
            text: def.text,
            anchor: { Width: def.width, Height: 36 },
            onActivating: () => onQuickFilter(def.rarity),
          })
        ))
      );

      // Status label
      children.push(spacer(4));
      let statusText = "No filter active";
      let statusColor = COLORS.TEXT_MUTED;
      if (s.quickFilterRarity) {
        statusText =
          "Blocking everything below " + capitalize(s.quickFilterRarity);
        statusColor = COLORS.POSITIVE;
      } else if (s.activeProfileId) {
        const activeProfile = s.profiles.find(
          (p) => p.id === s.activeProfileId
        );
        statusText =
          "Using profile: " + (activeProfile ? activeProfile.name : "Unknown");
        statusColor = COLORS.POSITIVE;
      }
      children.push(
        h(
          "Group",
          {
            layoutMode: "Left",
            anchor: { Horizontal: 0 },
            padding: { Horizontal: 25 },
          },
          [
            h("Label", {
              text: statusText,
              elStyle: { FontSize: 12, TextColor: statusColor },
            }),
          ]
        )
      );

      // ── Presets section — always rendered, visibility-controlled ──
      const showPresets = s.presetNames.length > 0 && s.profiles.length < s.maxProfiles;
      children.push(
        h("Group", {
          layoutMode: "Top",
          anchor: { Horizontal: 0 },
          visible: showPresets,
        }, [
          spacer(6),
          sectionHeader("Start From a Preset"),
          spacer(4),
          h("Group", {
            layoutMode: "LeftCenterWrap",
            anchor: { Horizontal: 0 },
            padding: { Horizontal: 20 },
          }, s.presetNames.map(name =>
            h(Common.SmallSecondaryTextButton, {
              key: "preset-" + name,
              text: name,
              anchor: { Width: 185, Height: 36 },
              onActivating: () => onCopyPreset(name),
            })
          )),
          spacer(2),
          noteText("Creates a copy you can customize"),
        ])
      );

      // ── Your Profiles section ──
      children.push(spacer(6));
      children.push(
        sectionHeader(
          "Your Profiles",
          s.profiles.length + "/" + s.maxProfiles
        )
      );
      children.push(spacer(4));

      // Empty state — visibility-controlled
      children.push(
        h("Group", { visible: s.profiles.length === 0, anchor: { Horizontal: 0 } }, [
          noteText("No profiles yet. Try a quick filter above, or start from a preset!"),
        ])
      );

      for (const profile of s.profiles) {
        const isActive = profile.id === s.activeProfileId;
        children.push(renderProfileRow(profile, isActive));
        children.push(spacer(4));
      }

      // New profile button — visibility-controlled
      children.push(
        h(
          "Group",
          {
            layoutMode: "MiddleCenter",
            anchor: { Horizontal: 0, Height: 40 },
            visible: s.profiles.length < s.maxProfiles,
          },
          [
            spacer(4),
            h(Common.SmallSecondaryTextButton, {
              text: "+ New Profile",
              anchor: { Width: 195, Height: 36 },
              onActivating: onCreateProfile,
            }),
          ]
        )
      );

      // ── Filtering toggle ──
      children.push(spacer(8));
      children.push(
        h(
          "Group",
          {
            layoutMode: "Left",
            anchor: { Horizontal: 0, Height: 40 },
            padding: { Horizontal: 20 },
          },
          [
            h("Label", {
              text: "Filtering:",
              elStyle: { FontSize: 13, TextColor: COLORS.TEXT_SECONDARY },
            }),
            h("Group", { anchor: { Width: 10 } }),
            h(Common.SmallSecondaryTextButton, {
              text: "ON",
              anchor: { Width: 70, Height: 34 },
              onActivating: () => onToggleFiltering(true),
            }),
            h("Group", { anchor: { Width: 6 } }),
            h(Common.SmallSecondaryTextButton, {
              text: "OFF",
              anchor: { Width: 75, Height: 34 },
              onActivating: () => onToggleFiltering(false),
            }),
            h("Group", { anchor: { Width: 12 } }),
            h("Label", {
              text: s.filteringEnabled ? "Enabled" : "Disabled",
              elStyle: {
                FontSize: 12,
                TextColor: s.filteringEnabled
                  ? COLORS.POSITIVE
                  : COLORS.NEGATIVE,
              },
            }),
          ]
        )
      );

      return children;
    }

    // ── Profile row ──
    function renderProfileRow(profile, isActive) {
      const rowChildren = [];

      // Gold left-border bar — always rendered, visibility-controlled (no structural changes)
      rowChildren.push(
        h("Group", {
          anchor: { Width: 4, Top: 4, Bottom: 4 },
          background: { Color: COLORS.TITLE_GOLD },
          visible: isActive,
        })
      );
      rowChildren.push(h("Group", { anchor: { Width: isActive ? 10 : 14 } }));

      // Name + info column — ACTIVE badge always rendered, visibility-controlled
      rowChildren.push(
        h("Group", { flexWeight: 1, layoutMode: "Top" }, [
          h("Group", { layoutMode: "Left", anchor: { Height: 24 } }, [
            h("Label", {
              text: profile.name,
              elStyle: { FontSize: 14, TextColor: COLORS.TEXT_PRIMARY },
            }),
            h("Group", { anchor: { Width: 10 }, visible: isActive }),
            h("Label", {
              text: "ACTIVE",
              visible: isActive,
              elStyle: {
                FontSize: 11,
                TextColor: COLORS.POSITIVE,
              },
            }),
          ]),
          h("Label", {
            text:
              profile.rules.length +
              " rules, default: " +
              profile.defaultAction,
            elStyle: { FontSize: 12, TextColor: COLORS.TEXT_MUTED },
          }),
        ])
      );

      // Action buttons — always rendered, Activate visibility-controlled
      rowChildren.push(
        h(Common.SmallTertiaryTextButton, {
          text: "Edit",
          anchor: { Width: 85, Height: 32 },
          onActivating: () => onEditProfile(profile.id),
        })
      );
      rowChildren.push(h("Group", { anchor: { Width: 6 } }));
      rowChildren.push(
        h(Common.SmallTertiaryTextButton, {
          text: "Activate",
          anchor: { Width: 140, Height: 32 },
          visible: !isActive,
          onActivating: () => onActivateProfile(profile.id),
        })
      );
      rowChildren.push(h("Group", { anchor: { Width: 6 }, visible: !isActive }));
      rowChildren.push(
        h(Common.CancelTextButton, {
          text: "X",
          anchor: { Width: 44, Height: 32 },
          onActivating: () => onDeleteProfile(profile.id),
        })
      );
      rowChildren.push(h("Group", { anchor: { Width: 10 } }));

      return h(
        "Group",
        {
          layoutMode: "Left",
          anchor: { Horizontal: 0, Height: 56 },
          background: { Color: COLORS.SECTION_BG },
          padding: { Vertical: 6 },
        },
        rowChildren
      );
    }

    // ═══════════════════════════════════════════════════════════════
    // RULES VIEW
    // ═══════════════════════════════════════════════════════════════

    function renderRulesView(s) {
      const profile = s.profiles.find(
        (p) => p.id === editingProfileId.value
      );
      if (!profile) {
        return [
          noteText("Profile not found."),
          spacer(8),
          renderBackButton(onBackToHome),
        ];
      }

      const children = [];
      children.push(spacer(8));

      // Default action toggle
      children.push(sectionHeader("Default Action"));
      children.push(spacer(4));
      children.push(
        h(
          "Group",
          {
            layoutMode: "Left",
            anchor: { Horizontal: 0, Height: 36 },
            padding: { Horizontal: 20 },
          },
          [
            h(Common.SmallSecondaryTextButton, {
              text: "Allow",
              anchor: { Width: 105, Height: 36 },
              onActivating: () => onSetDefaultAction("ALLOW"),
            }),
            h("Group", { anchor: { Width: 6 } }),
            h(Common.SmallSecondaryTextButton, {
              text: "Block",
              anchor: { Width: 105, Height: 36 },
              onActivating: () => onSetDefaultAction("BLOCK"),
            }),
            h("Group", { anchor: { Width: 12 } }),
            h("Label", {
              text: "Current: " + profile.defaultAction,
              elStyle: {
                FontSize: 12,
                TextColor:
                  profile.defaultAction === "ALLOW"
                    ? COLORS.POSITIVE
                    : COLORS.NEGATIVE,
              },
            }),
          ]
        )
      );

      // Rules list
      children.push(spacer(6));
      children.push(
        sectionHeader(
          "Rules (first match wins)",
          profile.rules.length + "/" + s.maxRulesPerProfile
        )
      );
      children.push(spacer(4));

      if (profile.rules.length === 0) {
        children.push(
          noteText(
            "No rules. Items will be " +
              profile.defaultAction +
              "ED by default."
          )
        );
      }

      for (let i = 0; i < profile.rules.length; i++) {
        children.push(renderRuleCard(profile.rules[i], i, profile.rules.length));
        children.push(spacer(4));
      }

      // Add rule button
      children.push(spacer(4));
      children.push(
        h(
          "Group",
          {
            layoutMode: "MiddleCenter",
            anchor: { Horizontal: 0, Height: 40 },
          },
          [
            h(Common.SmallSecondaryTextButton, {
              text: "+ Add Rule",
              anchor: { Width: 175, Height: 36 },
              onActivating: onAddRule,
            }),
          ]
        )
      );

      // Back button
      children.push(spacer(8));
      children.push(renderBackButton(onBackToHome));

      return children;
    }

    function renderRuleCard(rule, index, totalRules) {
      const actionColor =
        rule.action === "ALLOW" ? COLORS.POSITIVE : COLORS.NEGATIVE;
      const enabledColor = rule.enabled ? COLORS.POSITIVE : COLORS.TEXT_MUTED;
      const nameColor = rule.enabled
        ? COLORS.TEXT_PRIMARY
        : COLORS.TEXT_MUTED;

      return h(
        "Group",
        {
          layoutMode: "Top",
          anchor: { Horizontal: 0 },
          background: { Color: COLORS.SECTION_BG },
          padding: { Horizontal: 14, Vertical: 6 },
        },
        [
          // Line 1: enabled dot + rule name + action
          h(
            "Group",
            {
              layoutMode: "Left",
              anchor: { Horizontal: 0, Height: 24 },
            },
            [
              h(Common.SmallTertiaryTextButton, {
                text: rule.enabled ? "o" : "-",
                anchor: { Width: 36, Height: 28 },
                onActivating: () => onToggleRule(index),
              }),
              h("Group", { anchor: { Width: 4 } }),
              h("Group", { flexWeight: 1 }, [
                h("Label", {
                  text: "#" + (index + 1) + " " + rule.name,
                  elStyle: { FontSize: 13, TextColor: nameColor },
                }),
              ]),
              h("Label", {
                text: rule.action,
                elStyle: { FontSize: 12, TextColor: actionColor },
              }),
            ]
          ),
          // Line 2: summary
          h(
            "Group",
            {
              layoutMode: "Left",
              anchor: { Horizontal: 0, Height: 20 },
              padding: { Left: 32 },
            },
            [
              h("Label", {
                text: rule.summary,
                elStyle: { FontSize: 12, TextColor: COLORS.TEXT_MUTED },
              }),
            ]
          ),
          // Line 3: action buttons
          h(
            "Group",
            {
              layoutMode: "Left",
              anchor: { Horizontal: 0, Height: 28 },
              padding: { Left: 32 },
            },
            [
              h("Group", { flexWeight: 1 }),
              h(Common.SmallTertiaryTextButton, {
                text: "Edit",
                anchor: { Width: 85, Height: 30 },
                onActivating: () => onEditRule(index),
              }),
              h("Group", { anchor: { Width: 4 } }),
              h(Common.SmallTertiaryTextButton, {
                text: "^",
                anchor: { Width: 40, Height: 30 },
                visible: index > 0,
                onActivating: () => onMoveRule(index, index - 1),
              }),
              h("Group", { anchor: { Width: 2 }, visible: index > 0 }),
              h(Common.SmallTertiaryTextButton, {
                text: "v",
                anchor: { Width: 40, Height: 30 },
                visible: index < totalRules - 1,
                onActivating: () => onMoveRule(index, index + 1),
              }),
              h("Group", { anchor: { Width: 2 }, visible: index < totalRules - 1 }),
              h(Common.CancelTextButton, {
                text: "X",
                anchor: { Width: 44, Height: 30 },
                onActivating: () => onDeleteRule(index),
              }),
            ]
          ),
        ]
      );
    }

    // ═══════════════════════════════════════════════════════════════
    // EDIT RULE VIEW
    // ═══════════════════════════════════════════════════════════════

    function renderEditRuleView(s) {
      const profile = s.profiles.find(
        (p) => p.id === editingProfileId.value
      );
      if (!profile) return [noteText("Profile not found.")];

      const rule =
        editingRuleIndex.value >= 0 && editingRuleIndex.value < profile.rules.length
          ? profile.rules[editingRuleIndex.value]
          : null;
      if (!rule) return [noteText("Rule not found."), renderBackButton(onBackToRules)];

      const children = [];
      children.push(spacer(8));

      // Rule name display
      children.push(
        h(
          "Group",
          {
            layoutMode: "Left",
            anchor: { Horizontal: 0, Height: 24 },
            padding: { Horizontal: 20 },
          },
          [
            h("Label", {
              text: "Rule: ",
              elStyle: { FontSize: 13, TextColor: COLORS.TEXT_SECONDARY },
            }),
            h("Label", {
              text: rule.name,
              elStyle: { FontSize: 14, TextColor: COLORS.TEXT_PRIMARY },
            }),
          ]
        )
      );

      // Action + Enabled toggles
      children.push(spacer(4));
      children.push(
        h(
          "Group",
          {
            layoutMode: "Left",
            anchor: { Horizontal: 0, Height: 36 },
            padding: { Horizontal: 20 },
          },
          [
            h("Label", {
              text: "Action:",
              elStyle: { FontSize: 13, TextColor: COLORS.TEXT_SECONDARY },
            }),
            h("Group", { anchor: { Width: 8 } }),
            h(Common.SmallSecondaryTextButton, {
              text: "Allow",
              anchor: { Width: 100, Height: 34 },
              onActivating: () => onSetRuleAction("ALLOW"),
            }),
            h("Group", { anchor: { Width: 4 } }),
            h(Common.SmallSecondaryTextButton, {
              text: "Block",
              anchor: { Width: 100, Height: 34 },
              onActivating: () => onSetRuleAction("BLOCK"),
            }),
            h("Group", { anchor: { Width: 8 } }),
            h("Label", {
              text: rule.action,
              elStyle: {
                FontSize: 12,
                TextColor:
                  rule.action === "ALLOW"
                    ? COLORS.POSITIVE
                    : COLORS.NEGATIVE,
              },
            }),
            h("Group", { anchor: { Width: 20 } }),
            h("Label", {
              text: "Enabled:",
              elStyle: { FontSize: 13, TextColor: COLORS.TEXT_SECONDARY },
            }),
            h("Group", { anchor: { Width: 8 } }),
            h("Label", {
              text: rule.enabled ? "Yes" : "No",
              elStyle: {
                FontSize: 12,
                TextColor: rule.enabled
                  ? COLORS.POSITIVE
                  : COLORS.NEGATIVE,
              },
            }),
          ]
        )
      );

      // Conditions
      children.push(spacer(6));
      children.push(
        sectionHeader(
          "Conditions (ALL must match)",
          rule.conditions.length + "/" + s.maxConditionsPerRule
        )
      );
      children.push(spacer(4));

      if (rule.conditions.length === 0) {
        children.push(noteText("No conditions — matches everything."));
      }

      for (let i = 0; i < rule.conditions.length; i++) {
        children.push(renderConditionCard(rule.conditions[i], i));
        children.push(spacer(4));
      }

      // Add condition buttons
      children.push(spacer(4));
      children.push(sectionHeader("Add Condition"));
      children.push(spacer(4));

      const condTypes = [
        "MIN_RARITY", "MAX_RARITY", "EQUIPMENT_SLOT", "WEAPON_TYPE",
        "ARMOR_MATERIAL", "ITEM_LEVEL_RANGE", "QUALITY_RANGE",
        "MIN_MODIFIER_COUNT", "CORRUPTION_STATE", "IMPLICIT_CONDITION",
        "REQUIRED_MODIFIERS", "MODIFIER_VALUE_RANGE",
      ];
      const condLabels = [
        "Min Rarity", "Max Rarity", "Equipment Slot", "Weapon Type",
        "Armor Material", "Item Level", "Quality Range",
        "Min Modifiers", "Corruption", "Weapon Implicit",
        "Required Mods", "Mod Value Range",
      ];

      // Auto-wrapping grid of condition type buttons
      children.push(
        h("Group", {
          layoutMode: "LeftCenterWrap",
          anchor: { Horizontal: 0 },
          padding: { Horizontal: 20 },
        }, condTypes.map((ct, i) =>
          h(Common.SmallTertiaryTextButton, {
            key: "add-cond-" + ct,
            text: "+ " + condLabels[i],
            anchor: { Width: 230, Height: 34 },
            onActivating: () => onAddCondition(ct),
          })
        ))
      );

      // Back + Save/Cancel footer
      children.push(spacer(8));
      children.push(renderBackButton(onBackToRules));

      return children;
    }

    function renderConditionCard(condition, index) {
      return h(
        "Group",
        {
          layoutMode: "Top",
          anchor: { Horizontal: 0 },
          background: { Color: COLORS.SECTION_BG },
          padding: { Horizontal: 14, Vertical: 6 },
        },
        [
          // Header: type name + [X]
          h(
            "Group",
            { layoutMode: "Left", anchor: { Horizontal: 0, Height: 24 } },
            [
              h("Group", { flexWeight: 1 }, [
                h("Label", {
                  text: condition.displayName,
                  elStyle: { FontSize: 13, TextColor: COLORS.TITLE_GOLD },
                }),
              ]),
              h(Common.CancelTextButton, {
                text: "X",
                anchor: { Width: 44, Height: 28 },
                onActivating: () => onRemoveCondition(index),
              }),
            ]
          ),
          // Type-specific interactive editor
          ...renderConditionEditor(condition, index),
        ]
      );
    }

    // ── Type-specific condition editors ──

    function renderConditionEditor(cond, idx) {
      switch (cond.type) {
        case "MIN_RARITY":
          return [renderRarityPicker(idx, cond.threshold, "MIN_RARITY")];
        case "MAX_RARITY":
          return [renderRarityPicker(idx, cond.threshold, "MAX_RARITY")];
        case "EQUIPMENT_SLOT":
          return [renderMultiToggle(idx, cond.slots,
            ["weapon", "head", "chest", "legs", "hands", "shield"],
            ["Weapon", "Head", "Chest", "Legs", "Hands", "Shield"],
            "EQUIPMENT_SLOT", "slots")];
        case "WEAPON_TYPE":
          return [renderMultiToggle(idx, cond.types,
            ["SWORD", "DAGGER", "AXE", "MACE", "LONGSWORD", "SPEAR", "CROSSBOW", "STAFF", "WAND"],
            ["Sword", "Dagger", "Axe", "Mace", "Longsword", "Spear", "Crossbow", "Staff", "Wand"],
            "WEAPON_TYPE", "types")];
        case "ARMOR_MATERIAL":
          return [renderMultiToggle(idx, cond.materials,
            ["CLOTH", "LEATHER", "PLATE", "WOOD", "SPECIAL"],
            ["Cloth", "Leather", "Plate", "Wood", "Special"],
            "ARMOR_MATERIAL", "materials")];
        case "ITEM_LEVEL_RANGE":
          return [renderRangeEditor(idx, cond.min, cond.max, 1, 1000000,
            "ITEM_LEVEL_RANGE", [1, 10])];
        case "QUALITY_RANGE":
          return [renderRangeEditor(idx, cond.min, cond.max, 1, 100,
            "QUALITY_RANGE", [1, 10])];
        case "MIN_MODIFIER_COUNT":
          return [renderSingleNumber(idx, cond.count, 0, 6,
            "MIN_MODIFIER_COUNT", "count", "modifiers")];
        case "CORRUPTION_STATE":
          return [renderExclusiveToggle(idx, cond.filter,
            ["CORRUPTED_ONLY", "NOT_CORRUPTED", "EITHER"],
            ["Corrupted", "Not Corrupted", "Either"],
            "CORRUPTION_STATE", "filter")];
        case "IMPLICIT_CONDITION":
          return renderImplicitEditor(idx, cond);
        case "REQUIRED_MODIFIERS":
          return [renderReadOnlyCondition(cond.description,
            "Configure via /lf commands or presets")];
        case "MODIFIER_VALUE_RANGE":
          return [renderReadOnlyCondition(cond.description,
            "Configure via /lf commands or presets")];
        default:
          return [renderReadOnlyCondition(cond.description, "")];
      }
    }

    // Rarity picker — row of rarity buttons, selected one highlighted
    function renderRarityPicker(condIdx, selected, condType) {
      const rarities = condType === "MIN_RARITY"
        ? ["COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC"]
        : ["COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC", "UNIQUE"];
      // SmallTertiary: FontSize 17, bold, uppercase, padding 16
      const RARITY_W = { COMMON: 120, UNCOMMON: 145, RARE: 90, EPIC: 85, LEGENDARY: 155, MYTHIC: 110, UNIQUE: 115 };
      const buttons = [];
      for (const r of rarities) {
        const isSel = r === selected;
        buttons.push(
          h(Common.SmallTertiaryTextButton, {
            text: capitalize(r),
            anchor: { Width: RARITY_W[r] || 110, Height: 30 },
            onActivating: () => onUpdateCondition(condIdx, { type: condType, threshold: r }),
          })
        );
        // Gold indicator under selected
        if (isSel) {
          // Wrap the previous button in a Top layout with indicator
          const lastBtn = buttons.pop();
          buttons.push(
            h("Group", { layoutMode: "Top" }, [
              lastBtn,
              h("Group", { anchor: { Width: RARITY_W[r] || 110, Height: 3 }, background: { Color: COLORS.TITLE_GOLD } }),
            ])
          );
        }
        buttons.push(h("Group", { anchor: { Width: 3 } }));
      }
      return h("Group", {
        layoutMode: "LeftCenterWrap", anchor: { Horizontal: 0 }, padding: { Top: 4 },
      }, buttons);
    }

    // Multi-select toggle — each value can be toggled on/off
    function renderMultiToggle(condIdx, currentValues, allValues, allLabels, condType, fieldName) {
      // SmallTertiary: FontSize 17, bold, uppercase, padding 16
      // Estimate width: ~13px per char + 16px padding + margin
      const buttons = [];
      for (let i = 0; i < allValues.length; i++) {
        const val = allValues[i];
        const isSel = currentValues && currentValues.includes(val);
        const btnWidth = Math.max(60, allLabels[i].length * 13 + 28);
        buttons.push(
          h("Group", { layoutMode: "Top" }, [
            h(Common.SmallTertiaryTextButton, {
              text: allLabels[i],
              anchor: { Width: btnWidth, Height: 30 },
              onActivating: () => {
                const newSet = currentValues ? [...currentValues] : [];
                const idx = newSet.indexOf(val);
                if (idx >= 0) {
                  newSet.splice(idx, 1);
                  if (newSet.length === 0) newSet.push(val); // Can't be empty
                } else {
                  newSet.push(val);
                }
                const data = { type: condType };
                data[fieldName] = newSet;
                onUpdateCondition(condIdx, data);
              },
            }),
            h("Group", { anchor: { Width: btnWidth, Height: 3 }, background: { Color: COLORS.TITLE_GOLD }, visible: isSel }),
          ])
        );
        buttons.push(h("Group", { anchor: { Width: 3 } }));
      }
      return h("Group", {
        layoutMode: "LeftCenterWrap", anchor: { Horizontal: 0 }, padding: { Top: 4 },
      }, buttons);
    }

    // Exclusive toggle — only one value can be selected
    function renderExclusiveToggle(condIdx, current, values, labels, condType, fieldName) {
      const buttons = [];
      for (let i = 0; i < values.length; i++) {
        const val = values[i];
        const isSel = val === current;
        const btnWidth = Math.max(60, labels[i].length * 13 + 28);
        buttons.push(
          h("Group", { layoutMode: "Top" }, [
            h(Common.SmallTertiaryTextButton, {
              text: labels[i],
              anchor: { Width: btnWidth, Height: 30 },
              onActivating: () => {
                const data = { type: condType };
                data[fieldName] = val;
                onUpdateCondition(condIdx, data);
              },
            }),
            h("Group", { anchor: { Width: btnWidth, Height: 3 }, background: { Color: COLORS.TITLE_GOLD }, visible: isSel }),
          ])
        );
        buttons.push(h("Group", { anchor: { Width: 4 } }));
      }
      return h("Group", {
        layoutMode: "LeftCenterWrap", anchor: { Horizontal: 0 }, padding: { Top: 4 },
      }, buttons);
    }

    // Range editor — Min row + Max row stacked vertically
    function renderRangeEditor(condIdx, curMin, curMax, floor, ceil, condType, steps) {
      const smallStep = steps[0];
      const bigStep = steps[1];

      function update(newMin, newMax) {
        onUpdateCondition(condIdx, { type: condType, min: newMin, max: newMax });
      }

      function rangeRow(label, value, onDec1, onDec10, onInc1, onInc10) {
        return h("Group", {
          layoutMode: "LeftCenterWrap", anchor: { Horizontal: 0 }, padding: { Top: 4 },
        }, [
          h("Group", { anchor: { Width: 45, Height: 32 } }, [
            h("Label", { text: label,
              elStyle: { FontSize: 13, TextColor: COLORS.TEXT_MUTED, VerticalAlignment: "Center" } }),
          ]),
          h(Common.SmallTertiaryTextButton, { text: "-" + bigStep,
            anchor: { Width: 70, Height: 32 }, onActivating: onDec10 }),
          h(Common.SmallTertiaryTextButton, { text: "-" + smallStep,
            anchor: { Width: 58, Height: 32 }, onActivating: onDec1 }),
          h("Group", { anchor: { Width: 60, Height: 32 } }, [
            h("Label", { text: String(value),
              elStyle: { FontSize: 14, TextColor: COLORS.TEXT_PRIMARY, HorizontalAlignment: "Center" } }),
          ]),
          h(Common.SmallTertiaryTextButton, { text: "+" + smallStep,
            anchor: { Width: 58, Height: 32 }, onActivating: onInc1 }),
          h(Common.SmallTertiaryTextButton, { text: "+" + bigStep,
            anchor: { Width: 70, Height: 32 }, onActivating: onInc10 }),
        ]);
      }

      return h("Group", { layoutMode: "Top", anchor: { Horizontal: 0 } }, [
        rangeRow("Min:", curMin,
          () => update(Math.max(floor, curMin - smallStep), curMax),
          () => update(Math.max(floor, curMin - bigStep), curMax),
          () => update(Math.min(curMax, curMin + smallStep), curMax),
          () => update(Math.min(curMax, curMin + bigStep), curMax)),
        rangeRow("Max:", curMax,
          () => update(curMin, Math.max(curMin, curMax - smallStep)),
          () => update(curMin, Math.max(curMin, curMax - bigStep)),
          () => update(curMin, Math.min(ceil, curMax + smallStep)),
          () => update(curMin, Math.min(ceil, curMax + bigStep))),
      ]);
    }

    // Single number with +/- buttons
    function renderSingleNumber(condIdx, value, floor, ceil, condType, fieldName, suffix) {
      return h("Group", {
        layoutMode: "LeftCenterWrap", anchor: { Horizontal: 0 }, padding: { Top: 4 },
      }, [
        h("Label", { text: "At least:", elStyle: { FontSize: 12, TextColor: COLORS.TEXT_MUTED } }),
        h("Group", { anchor: { Width: 6 } }),
        h(Common.SmallTertiaryTextButton, { text: "-",
          anchor: { Width: 50, Height: 32 },
          onActivating: () => {
            const data = { type: condType };
            data[fieldName] = Math.max(floor, value - 1);
            onUpdateCondition(condIdx, data);
          }}),
        h("Group", { anchor: { Width: 45 } }, [
          h("Label", { text: String(value),
            elStyle: { FontSize: 14, TextColor: COLORS.TEXT_PRIMARY, HorizontalAlignment: "Center" } }),
        ]),
        h(Common.SmallTertiaryTextButton, { text: "+",
          anchor: { Width: 50, Height: 32 },
          onActivating: () => {
            const data = { type: condType };
            data[fieldName] = Math.min(ceil, value + 1);
            onUpdateCondition(condIdx, data);
          }}),
        h("Group", { anchor: { Width: 6 } }),
        h("Label", { text: suffix, elStyle: { FontSize: 12, TextColor: COLORS.TEXT_MUTED } }),
      ]);
    }

    // Implicit condition editor — damage type toggles + percentile
    function renderImplicitEditor(condIdx, cond) {
      const pct = Math.round((cond.minPercentile || 0) * 100);
      const anyType = !cond.damageTypes || cond.damageTypes.length === 0;
      const isPhys = cond.damageTypes && cond.damageTypes.includes("physical_damage");
      const isSpell = cond.damageTypes && cond.damageTypes.includes("spell_damage");

      return [
        // Damage type row
        h("Group", { layoutMode: "LeftCenterWrap", anchor: { Horizontal: 0 }, padding: { Top: 4 } }, [
          h("Group", { anchor: { Width: 55, Height: 30 } }, [
            h("Label", { text: "Type:",
              elStyle: { FontSize: 13, TextColor: COLORS.TEXT_MUTED, VerticalAlignment: "Center" } }),
          ]),
          ...["Any", "Physical", "Spell"].map((label, i) => {
            const types = [[], ["physical_damage"], ["spell_damage"]];
            const isSel = (i === 0 && anyType) || (i === 1 && isPhys) || (i === 2 && isSpell);
            const implW = [70, 140, 100];
            return h("Group", { layoutMode: "Top" }, [
              h(Common.SmallTertiaryTextButton, { text: label,
                anchor: { Width: implW[i], Height: 30 },
                onActivating: () => onUpdateCondition(condIdx, {
                  type: "IMPLICIT_CONDITION", minPercentile: cond.minPercentile, damageTypes: types[i],
                }) }),
              h("Group", { anchor: { Width: implW[i], Height: 3 }, background: { Color: COLORS.TITLE_GOLD }, visible: isSel }),
              h("Group", { anchor: { Width: 3 } }),
            ]);
          }),
        ]),
        // Min roll percentile
        h("Group", { layoutMode: "LeftCenterWrap", anchor: { Horizontal: 0 }, padding: { Top: 4 } }, [
          h("Group", { anchor: { Width: 80, Height: 32 } }, [
            h("Label", { text: "Min roll:",
              elStyle: { FontSize: 13, TextColor: COLORS.TEXT_MUTED, VerticalAlignment: "Center" } }),
          ]),
          h(Common.SmallTertiaryTextButton, { text: "-5",
            anchor: { Width: 58, Height: 32 },
            onActivating: () => onUpdateCondition(condIdx, {
              type: "IMPLICIT_CONDITION", minPercentile: Math.max(0, cond.minPercentile - 0.05),
              damageTypes: cond.damageTypes || [],
            }) }),
          h("Group", { anchor: { Width: 50 } }, [
            h("Label", { text: pct + "%",
              elStyle: { FontSize: 14, TextColor: COLORS.TEXT_PRIMARY, HorizontalAlignment: "Center" } }),
          ]),
          h(Common.SmallTertiaryTextButton, { text: "+5",
            anchor: { Width: 58, Height: 32 },
            onActivating: () => onUpdateCondition(condIdx, {
              type: "IMPLICIT_CONDITION", minPercentile: Math.min(1.0, cond.minPercentile + 0.05),
              damageTypes: cond.damageTypes || [],
            }) }),
        ]),
      ];
    }

    // Read-only display for complex conditions (modifiers)
    function renderReadOnlyCondition(description, hint) {
      const children = [
        h("Group", { layoutMode: "Left", anchor: { Horizontal: 0 }, padding: { Top: 4 } }, [
          h("Label", { text: description,
            elStyle: { FontSize: 12, TextColor: COLORS.TEXT_SECONDARY } }),
        ]),
      ];
      if (hint) {
        children.push(
          h("Group", { layoutMode: "Left", anchor: { Horizontal: 0 }, padding: { Top: 2 } }, [
            h("Label", { text: hint,
              elStyle: { FontSize: 11, TextColor: COLORS.TEXT_MUTED } }),
          ])
        );
      }
      return h("Group", { layoutMode: "Top", anchor: { Horizontal: 0 } }, children);
    }

    // ═══════════════════════════════════════════════════════════════
    // SHARED COMPONENTS
    // ═══════════════════════════════════════════════════════════════

    function renderBackButton(onClick) {
      return h(
        "Group",
        {
          layoutMode: "Left",
          anchor: { Horizontal: 0, Height: 36 },
          padding: { Horizontal: 20 },
        },
        [
          h(Common.SmallSecondaryTextButton, {
            text: "< Back",
            anchor: { Width: 125, Height: 36 },
            onActivating: onClick,
          }),
        ]
      );
    }

    function renderNavFooter() {
      return h(
        "Group",
        {
          layoutMode: "MiddleCenter",
          anchor: { Horizontal: 0, Height: 70 },
        },
        [
          h("Group", { layoutMode: "LeftCenterWrap" }, [
            h(Common.SecondaryTextButton, {
              text: "Close",
              anchor: { Width: 120, Height: 46 },
              onActivating: () => {
                callBridge("closePage", playerId.value);
              },
            }),
            h("Group", { anchor: { Width: 12 } }),
            h(Common.SecondaryTextButton, {
              text: "Stats",
              anchor: { Width: 115, Height: 46 },
              onActivating: () => {
                callBridge("navigateToStats", playerId.value);
              },
            }),
            h("Group", { anchor: { Width: 12 } }),
            h(Common.SecondaryTextButton, {
              text: "Attributes",
              anchor: { Width: 175, Height: 46 },
              onActivating: () => {
                callBridge("navigateToAttributes", playerId.value);
              },
            }),
            h("Group", { anchor: { Width: 12 } }),
            h(Common.SecondaryTextButton, {
              text: "Skill Tree",
              anchor: { Width: 175, Height: 46 },
              onActivating: () => {
                callBridge("navigateToSkillTree", playerId.value);
              },
            }),
          ]),
        ]
      );
    }
  },
});
