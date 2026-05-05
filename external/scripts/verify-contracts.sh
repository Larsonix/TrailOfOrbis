#!/bin/bash
# ============================================================================
# verify-contracts.sh — Check if reflected API surfaces changed between versions
# ============================================================================
#
# Usage:
#   ./external/scripts/verify-contracts.sh              # Check all deps
#   ./external/scripts/verify-contracts.sh hexcode      # Check one dep
#
# Compares vendor/ (baseline) against external/ (latest) for watched files.
# Reports: SAFE (no change), REVIEW (file changed, method exists), BREAKING (method gone).
#
# ============================================================================

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
EXTERNAL_DIR="$PROJECT_DIR/external"
VENDOR_DIR="$PROJECT_DIR/vendor"

# ── Colors ──────────────────────────────────────────────────────────────────

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'
BOLD='\033[1m'

# ── Contract Definitions ────────────────────────────────────────────────────
# Format: "dep_name|vendor_subdir|external_subdir|watched_file|method1,method2,..."

HEXCODE_CONTRACTS=(
    "hexcode|hexcode|plugins/hexcode/repo|src/main/java/com/riprod/hexcode/core/common/hexcaster/component/HexcasterComponent.java|getComponentType,getState,requestStateChange"
    "hexcode|hexcode|plugins/hexcode/repo|src/main/java/com/riprod/hexcode/state/HexState.java|IDLE"
    "hexcode|hexcode|plugins/hexcode/repo|src/main/java/com/riprod/hexcode/core/common/construct/component/HexEffectsComponent.java|getComponentType,getEffects"
    "hexcode|hexcode|plugins/hexcode/repo|src/main/java/com/riprod/hexcode/builtin/glyphs/projectile/component/ProjectileState.java|getComponentType,getHexContext"
    "hexcode|hexcode|plugins/hexcode/repo|src/main/java/com/riprod/hexcode/builtin/glyphs/shatter/component/ShatterState.java|getComponentType,getHexContext"
    "hexcode|hexcode|plugins/hexcode/repo|src/main/java/com/riprod/hexcode/core/common/stats/HexcodeEntityStatTypes.java|getVolatility,getMagicPower,getMagicCharges"
)

L4E_CONTRACTS=(
    "loot4everyone|Loot4Everyone|plugins/loot4everyone/repo|src/main/java/org/mimstar/plugin/Loot4Everyone.java|get,getlootChestTemplateResourceType,getPlayerLootcomponentType,getLootChestConfigResourceType"
    "loot4everyone|Loot4Everyone|plugins/loot4everyone/repo|src/main/java/org/mimstar/plugin/resources/LootChestTemplate.java|saveTemplate,removeTemplate,hasTemplate"
    "loot4everyone|Loot4Everyone|plugins/loot4everyone/repo|src/main/java/org/mimstar/plugin/components/PlayerLoot.java|setInventory,resetChest"
    "loot4everyone|Loot4Everyone|plugins/loot4everyone/repo|src/main/java/org/mimstar/plugin/resources/LootChestConfig.java|setCanPlayerBreakLootChests,setParticlesAppear,setParticlesColor,setMessageAppear"
)

# PartyPro: external repo is docs-only, can only verify vendor internally
PARTYPRO_CONTRACTS=(
    "partypro|PartyPro/decompiled|NONE|me/tsumori/partypro/api/PartyProAPI.java|isAvailable,getInstance,isInParty,areInSameParty,getPartyByPlayer,getPartyMembers,getOnlinePartyMembers,getPartyLeader,setPlayerCustomText,clearPlayerCustomText,registerListener,unregisterListener"
    "partypro|PartyPro/decompiled|NONE|me/tsumori/partypro/api/PartySnapshot.java|id,leader,getAllMembers,pvpEnabled"
)

# ── Verification Logic ──────────────────────────────────────────────────────

safe_count=0
review_count=0
breaking_count=0
skip_count=0

verify_contract() {
    local entry="$1"
    local dep_name vendor_subdir external_subdir watched_file methods_csv
    dep_name=$(echo "$entry" | cut -d'|' -f1)
    vendor_subdir=$(echo "$entry" | cut -d'|' -f2)
    external_subdir=$(echo "$entry" | cut -d'|' -f3)
    watched_file=$(echo "$entry" | cut -d'|' -f4)
    methods_csv=$(echo "$entry" | cut -d'|' -f5)

    local vendor_path="$VENDOR_DIR/$vendor_subdir/$watched_file"
    local external_path="$EXTERNAL_DIR/$external_subdir/$watched_file"
    local filename
    filename=$(basename "$watched_file")

    # Skip if external repo doesn't have source (PartyPro case)
    if [[ "$external_subdir" == "NONE" ]]; then
        # Verify methods exist in vendor only
        if [[ ! -f "$vendor_path" ]]; then
            echo -e "  ${RED}[MISSING]${NC} $filename — not found in vendor/"
            ((breaking_count++))
            return
        fi
        IFS=',' read -ra methods <<< "$methods_csv"
        local all_found=true
        for method in "${methods[@]}"; do
            if ! grep -q "$method" "$vendor_path" 2>/dev/null; then
                echo -e "  ${RED}[BREAKING]${NC} $filename.$method — not found in vendor source"
                ((breaking_count++))
                all_found=false
            fi
        done
        if [[ "$all_found" == true ]]; then
            echo -e "  ${GREEN}[SAFE]${NC} $filename — all ${#methods[@]} methods present (vendor-only check)"
            ((safe_count++))
        fi
        return
    fi

    # Normal case: compare vendor vs external
    if [[ ! -f "$vendor_path" ]]; then
        echo -e "  ${YELLOW}[SKIP]${NC} $filename — not in vendor/"
        ((skip_count++))
        return
    fi

    if [[ ! -f "$external_path" ]]; then
        echo -e "  ${RED}[BREAKING]${NC} $filename — FILE REMOVED in external/"
        ((breaking_count++))
        return
    fi

    # Check if file changed
    if diff -q "$vendor_path" "$external_path" > /dev/null 2>&1; then
        echo -e "  ${GREEN}[SAFE]${NC} $filename — identical"
        ((safe_count++))
        return
    fi

    # File changed — check if our methods still exist
    IFS=',' read -ra methods <<< "$methods_csv"
    local all_methods_found=true
    local missing_methods=()

    for method in "${methods[@]}"; do
        if ! grep -q "$method" "$external_path" 2>/dev/null; then
            all_methods_found=false
            missing_methods+=("$method")
        fi
    done

    if [[ "$all_methods_found" == true ]]; then
        echo -e "  ${YELLOW}[REVIEW]${NC} $filename — file changed but all ${#methods[@]} methods still present"
        ((review_count++))
    else
        echo -e "  ${RED}[BREAKING]${NC} $filename — MISSING methods: ${missing_methods[*]}"
        ((breaking_count++))
    fi
}

# ── Run Checks ──────────────────────────────────────────────────────────────

run_dep() {
    local dep_name="$1"
    shift
    local contracts=("$@")

    echo -e "\n${BOLD}${CYAN}━━━ $dep_name ━━━${NC}"
    for contract in "${contracts[@]}"; do
        verify_contract "$contract"
    done
}

run_all() {
    run_dep "Hexcode" "${HEXCODE_CONTRACTS[@]}"
    run_dep "Loot4Everyone" "${L4E_CONTRACTS[@]}"
    run_dep "PartyPro" "${PARTYPRO_CONTRACTS[@]}"
}

# ── Main ────────────────────────────────────────────────────────────────────

echo -e "${BOLD}API Contract Verification${NC}"
echo -e "Comparing vendor/ (baseline) vs external/ (latest upstream)\n"

target="${1:-all}"

case "$target" in
    hexcode)      run_dep "Hexcode" "${HEXCODE_CONTRACTS[@]}" ;;
    loot4everyone) run_dep "Loot4Everyone" "${L4E_CONTRACTS[@]}" ;;
    partypro)     run_dep "PartyPro" "${PARTYPRO_CONTRACTS[@]}" ;;
    all)          run_all ;;
    *)            echo "Usage: $0 [hexcode|loot4everyone|partypro|all]"; exit 1 ;;
esac

# ── Summary ─────────────────────────────────────────────────────────────────

echo -e "\n${BOLD}━━━ Summary ━━━${NC}"
echo -e "  ${GREEN}SAFE${NC}: $safe_count  ${YELLOW}REVIEW${NC}: $review_count  ${RED}BREAKING${NC}: $breaking_count  SKIP: $skip_count"

if [[ $breaking_count -gt 0 ]]; then
    echo -e "\n  ${RED}${BOLD}ACTION REQUIRED: $breaking_count breaking changes detected!${NC}"
    echo -e "  Update the affected bridge files before upgrading."
    exit 1
elif [[ $review_count -gt 0 ]]; then
    echo -e "\n  ${YELLOW}Review recommended: files changed but reflected methods stable.${NC}"
    exit 0
else
    echo -e "\n  ${GREEN}All clear — safe to upgrade.${NC}"
    exit 0
fi
