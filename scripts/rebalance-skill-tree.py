#!/usr/bin/env python3
"""
Skill Tree Full Balance Pass — Rebalances all node values in skill-tree.yml
and regenerates descriptions to match.

Approach: Parse the YAML structure-aware (tracking current node context),
apply scaling rules per stat category and node role, update values and
descriptions in-place, preserving all comments and formatting.
"""

import re
import sys
import copy

# ─── Stat Categories ───────────────────────────────────────────────────────────

# CHANCE stats (FLAT type, base=0, add percentage points)
CHANCE_STATS = {
    'CRITICAL_CHANCE', 'IGNITE_CHANCE', 'FREEZE_CHANCE', 'SHOCK_CHANCE',
    'BLOCK_CHANCE', 'PASSIVE_BLOCK_CHANCE', 'DODGE_CHANCE',
}

# Damage/Speed PERCENT stats (PERCENT type, gear provides base)
DAMAGE_PERCENT_STATS = {
    'PHYSICAL_DAMAGE_PERCENT', 'SPELL_DAMAGE_PERCENT', 'FIRE_DAMAGE_PERCENT',
    'BURN_DAMAGE_PERCENT', 'CHARGED_ATTACK_DAMAGE_PERCENT', 'ATTACK_SPEED_PERCENT',
    'DOT_DAMAGE_PERCENT', 'MELEE_DAMAGE_PERCENT', 'PROJECTILE_DAMAGE_PERCENT',
    'WATER_DAMAGE_PERCENT', 'LIGHTNING_DAMAGE_PERCENT', 'EARTH_DAMAGE_PERCENT',
    'VOID_DAMAGE_PERCENT', 'WIND_DAMAGE_PERCENT', 'MOVEMENT_SPEED_PERCENT',
    'PROJECTILE_SPEED_PERCENT', 'JUMP_FORCE_PERCENT', 'MAX_HEALTH_PERCENT',
    'MAX_MANA_PERCENT', 'BURN_DURATION_PERCENT', 'EXECUTE_DAMAGE_PERCENT',
    'DAMAGE_VS_FROZEN_PERCENT', 'DAMAGE_VS_SHOCKED_PERCENT',
    'HEALTH_REGEN_PERCENT', 'HEALTH_RECOVERY_PERCENT',
    'SHIELD_EFFECTIVENESS_PERCENT', 'ACCURACY_PERCENT',
}

# Critical Multiplier (FLAT type, base=0)
CRIT_MULT_STATS = {'CRITICAL_MULTIPLIER'}

# Flat resource stats
FLAT_RESOURCE_STATS = {
    'MAX_MANA': 'mana',
    'ENERGY_SHIELD': 'es',
    'EVASION': 'evasion',
    'STAMINA_REGEN': 'stamregen',
    'ARMOR': 'armor',
    'MAX_HEALTH': 'maxhp',
    'MANA_REGEN': 'manaregen',
    'HEALTH_REGEN': 'hpregen',
    'ACCURACY': 'accuracy',
    'KNOCKBACK_RESISTANCE': 'kbresist',
}

# Sustain stats (FLAT type)
SUSTAIN_STATS = {'LIFE_STEAL', 'LIFE_LEECH', 'MANA_STEAL', 'MANA_ON_KILL'}

# Flat damage stats (bridge nodes)
FLAT_DAMAGE_STATS = {
    'PHYSICAL_DAMAGE', 'FIRE_DAMAGE', 'WATER_DAMAGE', 'LIGHTNING_DAMAGE',
    'EARTH_DAMAGE', 'VOID_DAMAGE', 'WIND_DAMAGE',
}

# Stats that should NOT be changed (penetration, conversion, resistance, etc.)
KEEP_AS_IS_STATS = {
    'FIRE_PENETRATION', 'WATER_PENETRATION', 'LIGHTNING_PENETRATION',
    'EARTH_PENETRATION', 'VOID_PENETRATION', 'WIND_PENETRATION',
    'PHYSICAL_RESISTANCE', 'ELEMENTAL_RESISTANCE',
    'PHYSICAL_TO_FIRE_CONVERSION', 'DAMAGE_TO_VOID_CONVERSION',
    'DAMAGE_MULTIPLIER', 'FIRE_DAMAGE_MULTIPLIER', 'WATER_DAMAGE_MULTIPLIER',
    'LIGHTNING_DAMAGE_MULTIPLIER', 'VOID_DAMAGE_MULTIPLIER',
    'DAMAGE_FROM_MANA_PERCENT', 'MANA_COST_PERCENT',
    'DAMAGE_TO_MANA_CONVERSION', 'DAMAGE_TAKEN_PERCENT',
    'DAMAGE_PERCENT', 'ARMOR_PERCENT', 'EVASION_PERCENT',
    'BLOCK_HEAL_PERCENT', 'BLOCK_DAMAGE_REDUCTION',
    'THORNS_DAMAGE_PERCENT', 'NON_CRIT_DAMAGE_PERCENT',
    'DETONATE_DOT_ON_CRIT', 'ENEMY_RESISTANCE_REDUCTION',
    'PERCENT_HIT_AS_TRUE_DAMAGE',  # Special: keep as-is (true damage is powerful)
    'STATUS_EFFECT_DURATION',  # Special: keep as-is (already balanced)
}

# ─── Node Role Detection ──────────────────────────────────────────────────────

def get_node_role(node_id):
    """Determine the role of a node from its ID."""
    if node_id == 'origin':
        return 'origin'
    if node_id.endswith('_entry'):
        return 'entry'
    if '_keystone_' in node_id:
        return 'keystone'
    if '_synergy_' in node_id or node_id.endswith('_synergy_hub'):
        return 'synergy'
    if node_id.startswith('bridge_'):
        return 'bridge'
    if '_notable' in node_id:
        return 'notable'
    if '_branch' in node_id:
        return 'branch'
    # Small passives: _1, _2, _3, _4
    m = re.search(r'_(\d)$', node_id)
    if m:
        return f'small_{m.group(1)}'
    return 'unknown'


def get_stat_category(stat_name):
    """Categorize a stat for scaling purposes."""
    if stat_name in CHANCE_STATS:
        return 'chance'
    if stat_name in DAMAGE_PERCENT_STATS:
        return 'damage_pct'
    if stat_name in CRIT_MULT_STATS:
        return 'crit_mult'
    if stat_name in FLAT_RESOURCE_STATS:
        return 'flat_resource'
    if stat_name in SUSTAIN_STATS:
        return 'sustain'
    if stat_name in FLAT_DAMAGE_STATS:
        return 'flat_damage'
    if stat_name in KEEP_AS_IS_STATS:
        return 'keep'
    return 'keep'  # Unknown stats: leave alone


# ─── Scaling Rules ─────────────────────────────────────────────────────────────
# Returns (new_value) for a given stat, node role, and old value.

def scale_value(stat_name, node_role, old_value, mod_type):
    """Apply the balance pass scaling to a modifier value."""
    cat = get_stat_category(stat_name)

    # Never touch keystones, synergy nodes, or origin
    if node_role in ('keystone', 'synergy', 'origin'):
        return old_value

    # Never touch stats in the keep category
    if cat == 'keep':
        return old_value

    # ── Entry Nodes ──
    # Entry nodes are small passives with slightly higher values
    if node_role == 'entry':
        if cat == 'chance':
            return round(old_value * 12.0, 1)  # 0.2 → 2.5 ish
        elif cat == 'damage_pct':
            return round(old_value * 5.0, 1)  # 1.0 → 5.0
        elif cat == 'crit_mult':
            return round(old_value * 3.5, 1)
        elif cat == 'flat_resource':
            return scale_flat_resource(stat_name, old_value, 'entry')
        elif cat == 'sustain':
            return scale_sustain(stat_name, old_value, 'entry')
        elif cat == 'flat_damage':
            return round(old_value * 2.5, 1)
        return old_value

    # ── Bridge Nodes ──
    if node_role == 'bridge':
        if cat == 'flat_damage':
            # 2-3 → 5-8
            if old_value <= 2.0:
                return 5.0
            elif old_value <= 3.0:
                return 7.0
            else:
                return round(old_value * 2.5, 1)
        elif cat == 'damage_pct':
            # Bridge % nodes: 2% → 4%
            return round(old_value * 2.0, 1)
        elif cat == 'flat_resource':
            return scale_flat_resource(stat_name, old_value, 'bridge')
        elif cat == 'chance':
            return round(old_value * 12.0, 1)
        elif cat == 'crit_mult':
            return round(old_value * 3.0, 1)
        elif cat == 'sustain':
            return scale_sustain(stat_name, old_value, 'bridge')
        return old_value

    # ── Notable Nodes ──
    if node_role == 'notable':
        if cat == 'chance':
            # 5-10 → 8-12
            if old_value <= 5.0:
                return round(old_value * 1.6, 1)
            elif old_value <= 10.0:
                return round(old_value * 1.2, 1)
            else:
                return old_value  # Already high
        elif cat == 'damage_pct':
            # 5-10 → 10-15
            if old_value <= 5.0:
                return round(old_value * 2.5, 1)
            elif old_value <= 8.0:
                return round(old_value * 1.75, 1)
            elif old_value <= 10.0:
                return round(old_value * 1.5, 1)
            elif old_value <= 15.0:
                return old_value  # Already good
            else:
                return old_value
        elif cat == 'crit_mult':
            # 5-10 → 8-12
            if old_value <= 5.0:
                return round(old_value * 1.8, 1)
            elif old_value <= 10.0:
                return round(old_value * 1.2, 1)
            else:
                return old_value
        elif cat == 'flat_resource':
            return scale_flat_resource(stat_name, old_value, 'notable')
        elif cat == 'sustain':
            return scale_sustain(stat_name, old_value, 'notable')
        elif cat == 'flat_damage':
            return round(old_value * 2.0, 1)
        return old_value

    # ── Small Passives & Branch Points ──

    # Determine if this is a "primary" or "secondary" stat position
    # _1 nodes: tier 1 primary stat
    # _2 nodes: tier 2 secondary stat
    # _3 nodes: tier 3 (can be primary or secondary)
    # _4 nodes: tier 3 (can be primary or secondary)
    # _branch: dual stat (lower values for each)

    if node_role == 'branch':
        if cat == 'chance':
            # 0.1-0.15 → 1.5-2.5
            return round(max(old_value * 14.0, 1.5), 1)
        elif cat == 'damage_pct':
            # 0.4-0.75 → 3-4
            return round(max(old_value * 5.5, 3.0), 1)
        elif cat == 'crit_mult':
            # 0.45-0.9 → 2-3
            return round(max(old_value * 2.8, 2.0), 1)
        elif cat == 'flat_resource':
            return scale_flat_resource(stat_name, old_value, 'branch')
        elif cat == 'sustain':
            return scale_sustain(stat_name, old_value, 'branch')
        elif cat == 'flat_damage':
            return round(old_value * 2.5, 1)
        return old_value

    # Small passives (_1, _2, _3, _4)
    if node_role.startswith('small_'):
        if cat == 'chance':
            # Primary: 0.15-0.4 → 3-5, Secondary: 0.1-0.2 → 1.5-3
            # Use a flat multiplier approach
            if old_value >= 0.3:
                # Primary (higher values)
                return round(max(old_value * 13.0, 3.0), 1)
            elif old_value >= 0.15:
                # Mid-range
                return round(max(old_value * 15.0, 3.0), 1)
            else:
                # Small secondary
                return round(max(old_value * 18.0, 2.0), 1)
        elif cat == 'damage_pct':
            # Primary: 0.6-1.5 → 5-8, Secondary: 0.45-0.75 → 3-5
            if old_value >= 1.0:
                return round(max(old_value * 5.0, 5.0), 1)
            elif old_value >= 0.6:
                return round(max(old_value * 7.5, 5.0), 1)
            elif old_value >= 0.38:
                return round(max(old_value * 8.0, 3.0), 1)
            else:
                return round(max(old_value * 10.0, 3.0), 1)
        elif cat == 'crit_mult':
            # Primary: 0.9-1.8 → 4-7, Secondary: 0.5-0.9 → 2-4
            if old_value >= 1.5:
                return round(max(old_value * 3.5, 5.0), 1)
            elif old_value >= 0.9:
                return round(max(old_value * 4.0, 4.0), 1)
            else:
                return round(max(old_value * 4.5, 2.5), 1)
        elif cat == 'flat_resource':
            return scale_flat_resource(stat_name, old_value, 'small')
        elif cat == 'sustain':
            return scale_sustain(stat_name, old_value, 'small')
        elif cat == 'flat_damage':
            return round(old_value * 2.5, 1)
        return old_value

    return old_value


def scale_flat_resource(stat_name, old_value, node_role):
    """Scale flat resource stats based on specific stat type."""
    resource_type = FLAT_RESOURCE_STATS.get(stat_name, '')

    if node_role in ('notable', 'keystone', 'synergy'):
        return old_value  # Keep notable/keystone flat resources as-is

    if resource_type == 'mana':
        # MAX_MANA: 2.25-4.5 → 5-10
        if node_role == 'small':
            return round(max(old_value * 2.2, 5.0), 1)
        elif node_role == 'branch':
            return round(max(old_value * 2.0, 4.0), 1)
        elif node_role in ('entry', 'bridge'):
            return round(max(old_value * 1.8, 5.0), 1)
    elif resource_type == 'es':
        # ENERGY_SHIELD: 3-6 → 8-15
        if node_role == 'small':
            return round(max(old_value * 2.2, 8.0), 1)
        elif node_role == 'branch':
            return round(max(old_value * 2.2, 6.0), 1)
        elif node_role in ('entry', 'bridge'):
            return round(max(old_value * 2.0, 8.0), 1)
    elif resource_type == 'evasion':
        # EVASION: 7.5-15 → 10-15 (already decent, minor bump)
        if node_role == 'small':
            return round(max(old_value * 1.2, 10.0), 1)
        elif node_role == 'branch':
            return round(max(old_value * 1.2, 8.0), 1)
        elif node_role in ('entry', 'bridge'):
            return round(max(old_value * 1.2, 10.0), 1)
    elif resource_type == 'stamregen':
        # STAMINA_REGEN: 0.15-0.5 → 1-2
        if node_role == 'small':
            return round(max(old_value * 5.0, 1.0), 1)
        elif node_role == 'branch':
            return round(max(old_value * 5.0, 0.8), 1)
        elif node_role in ('entry', 'bridge'):
            return round(max(old_value * 5.0, 1.0), 1)
    elif resource_type == 'armor':
        # ARMOR: 7.5-15 → already decent range, minor bump
        if node_role == 'small':
            return round(max(old_value * 1.2, 10.0), 1)
        elif node_role == 'branch':
            return round(max(old_value * 1.2, 8.0), 1)
        elif node_role in ('entry', 'bridge'):
            return round(max(old_value * 1.2, 10.0), 1)
    elif resource_type == 'maxhp':
        # MAX_HEALTH flat: 5-8 → already decent, minor bump
        if node_role == 'bridge':
            return round(max(old_value * 1.5, 8.0), 1)
        return round(max(old_value * 1.5, 5.0), 1)
    elif resource_type == 'manaregen':
        # MANA_REGEN: 0.22-0.45 → 1-2
        if node_role == 'small':
            return round(max(old_value * 4.0, 1.0), 1)
        elif node_role == 'branch':
            return round(max(old_value * 4.0, 0.8), 1)
        elif node_role in ('entry', 'bridge'):
            return round(max(old_value * 4.0, 1.0), 1)
    elif resource_type == 'hpregen':
        # HEALTH_REGEN: 0.3-0.6 → 1-2
        if node_role == 'small':
            return round(max(old_value * 3.5, 1.0), 1)
        elif node_role == 'branch':
            return round(max(old_value * 3.5, 0.8), 1)
        elif node_role in ('entry', 'bridge'):
            return round(max(old_value * 3.5, 1.0), 1)
    elif resource_type == 'accuracy':
        # ACCURACY: 4.5-9 → already decent range, minor bump
        if node_role == 'small':
            return round(max(old_value * 1.5, 8.0), 1)
        elif node_role == 'branch':
            return round(max(old_value * 1.5, 6.0), 1)
        elif node_role in ('entry', 'bridge'):
            return round(max(old_value * 1.5, 8.0), 1)
    elif resource_type == 'kbresist':
        # KNOCKBACK_RESISTANCE: 0.45-0.9 → 2-4
        if node_role == 'small':
            return round(max(old_value * 3.5, 2.0), 1)
        elif node_role == 'branch':
            return round(max(old_value * 3.0, 1.5), 1)
        elif node_role in ('entry', 'bridge'):
            return round(max(old_value * 3.5, 2.0), 1)

    return old_value


def scale_sustain(stat_name, old_value, node_role):
    """Scale sustain stats."""
    if node_role in ('notable', 'keystone', 'synergy'):
        return old_value  # Keep notable sustain as-is

    if stat_name == 'LIFE_STEAL':
        # 0.1-0.3 → 0.5-1.0
        if node_role == 'small':
            return round(max(old_value * 3.0, 0.5), 1)
        elif node_role == 'branch':
            return round(max(old_value * 3.0, 0.4), 1)
        elif node_role in ('entry', 'bridge'):
            return round(max(old_value * 3.0, 0.5), 1)
    elif stat_name == 'LIFE_LEECH':
        # 0.1-0.2 → 0.3-0.5
        if node_role == 'small':
            return round(max(old_value * 2.5, 0.3), 1)
        elif node_role == 'branch':
            return round(max(old_value * 2.5, 0.3), 1)
        elif node_role in ('entry', 'bridge'):
            return round(max(old_value * 2.5, 0.3), 1)
    elif stat_name == 'MANA_STEAL':
        # 0.1-0.2 → 0.3-0.5
        if node_role == 'small':
            return round(max(old_value * 2.5, 0.3), 1)
        elif node_role == 'branch':
            return round(max(old_value * 2.5, 0.3), 1)
        elif node_role in ('entry', 'bridge'):
            return round(max(old_value * 2.5, 0.3), 1)
    elif stat_name == 'MANA_ON_KILL':
        # 0.75-1.5 → 2-4
        if node_role == 'small':
            return round(max(old_value * 2.5, 2.0), 1)
        elif node_role == 'branch':
            return round(max(old_value * 2.5, 1.5), 1)
        elif node_role in ('entry', 'bridge'):
            return round(max(old_value * 2.5, 2.0), 1)

    return old_value


# ─── Stat Display Names ───────────────────────────────────────────────────────

STAT_DISPLAY_NAMES = {
    'PHYSICAL_DAMAGE_PERCENT': 'Physical Damage',
    'SPELL_DAMAGE_PERCENT': 'Spell Damage',
    'FIRE_DAMAGE_PERCENT': 'Fire Damage',
    'BURN_DAMAGE_PERCENT': 'Burn Damage',
    'CHARGED_ATTACK_DAMAGE_PERCENT': 'Charged Attack Damage',
    'ATTACK_SPEED_PERCENT': 'Attack Speed',
    'DOT_DAMAGE_PERCENT': 'DoT Damage',
    'MELEE_DAMAGE_PERCENT': 'Melee Damage',
    'PROJECTILE_DAMAGE_PERCENT': 'Projectile Damage',
    'WATER_DAMAGE_PERCENT': 'Water Damage',
    'LIGHTNING_DAMAGE_PERCENT': 'Lightning Damage',
    'EARTH_DAMAGE_PERCENT': 'Earth Damage',
    'VOID_DAMAGE_PERCENT': 'Void Damage',
    'WIND_DAMAGE_PERCENT': 'Wind Damage',
    'MOVEMENT_SPEED_PERCENT': 'Move Speed',
    'PROJECTILE_SPEED_PERCENT': 'Projectile Speed',
    'JUMP_FORCE_PERCENT': 'Jump Force',
    'MAX_HEALTH_PERCENT': 'Max Health',
    'MAX_MANA_PERCENT': 'Max Mana',
    'BURN_DURATION_PERCENT': 'Burn Duration',
    'EXECUTE_DAMAGE_PERCENT': 'Execute Damage',
    'DAMAGE_VS_FROZEN_PERCENT': 'Damage vs Frozen',
    'DAMAGE_VS_SHOCKED_PERCENT': 'Damage vs Shocked',
    'HEALTH_REGEN_PERCENT': 'Health Regen',
    'HEALTH_RECOVERY_PERCENT': 'Health Recovery',
    'SHIELD_EFFECTIVENESS_PERCENT': 'Shield Effectiveness',
    'ACCURACY_PERCENT': 'Accuracy',
    'CRITICAL_CHANCE': 'Crit Chance',
    'IGNITE_CHANCE': 'Ignite Chance',
    'FREEZE_CHANCE': 'Freeze Chance',
    'SHOCK_CHANCE': 'Shock Chance',
    'BLOCK_CHANCE': 'Block Chance',
    'PASSIVE_BLOCK_CHANCE': 'Block Chance',
    'DODGE_CHANCE': 'Dodge Chance',
    'CRITICAL_MULTIPLIER': 'Crit Multiplier',
    'MAX_MANA': 'Max Mana',
    'ENERGY_SHIELD': 'Energy Shield',
    'EVASION': 'Evasion',
    'STAMINA_REGEN': 'Stamina Regen',
    'ARMOR': 'Armor',
    'MAX_HEALTH': 'Max Health',
    'MANA_REGEN': 'Mana Regen/s',
    'HEALTH_REGEN': 'Health Regen/s',
    'ACCURACY': 'Accuracy',
    'KNOCKBACK_RESISTANCE': 'KB Resistance',
    'LIFE_STEAL': 'Life Steal',
    'LIFE_LEECH': 'Life Leech',
    'MANA_STEAL': 'Mana Steal',
    'MANA_ON_KILL': 'Mana on Kill',
    'PHYSICAL_DAMAGE': 'Physical Damage',
    'FIRE_DAMAGE': 'Fire Damage',
    'WATER_DAMAGE': 'Water Damage',
    'LIGHTNING_DAMAGE': 'Lightning Damage',
    'EARTH_DAMAGE': 'Earth Damage',
    'VOID_DAMAGE': 'Void Damage',
    'WIND_DAMAGE': 'Wind Damage',
    'FIRE_PENETRATION': 'Fire Penetration',
    'WATER_PENETRATION': 'Water Penetration',
    'LIGHTNING_PENETRATION': 'Lightning Penetration',
    'EARTH_PENETRATION': 'Earth Penetration',
    'VOID_PENETRATION': 'Void Penetration',
    'WIND_PENETRATION': 'Wind Penetration',
    'PHYSICAL_RESISTANCE': 'Physical Resistance',
    'ELEMENTAL_RESISTANCE': 'Elemental Resistance',
    'PHYSICAL_TO_FIRE_CONVERSION': 'Physical to Fire Conversion',
    'DAMAGE_TO_VOID_CONVERSION': 'Damage to Void Conversion',
    'DAMAGE_MULTIPLIER': 'Damage Multiplier',
    'FIRE_DAMAGE_MULTIPLIER': 'Fire Damage Multiplier',
    'WATER_DAMAGE_MULTIPLIER': 'Water Damage Multiplier',
    'LIGHTNING_DAMAGE_MULTIPLIER': 'Lightning Damage Multiplier',
    'VOID_DAMAGE_MULTIPLIER': 'Void Damage Multiplier',
    'DAMAGE_FROM_MANA_PERCENT': 'Damage from Mana',
    'MANA_COST_PERCENT': 'Mana Cost',
    'DAMAGE_TO_MANA_CONVERSION': 'Damage to Mana Conversion',
    'DAMAGE_TAKEN_PERCENT': 'Damage Taken',
    'DAMAGE_PERCENT': 'Damage',
    'ARMOR_PERCENT': 'Armor',
    'EVASION_PERCENT': 'Evasion',
    'BLOCK_HEAL_PERCENT': 'Block Heal',
    'BLOCK_DAMAGE_REDUCTION': 'Block Damage Reduction',
    'THORNS_DAMAGE_PERCENT': 'Thorns Damage',
    'NON_CRIT_DAMAGE_PERCENT': 'Non-Crit Damage',
    'DETONATE_DOT_ON_CRIT': 'Detonate DoT on Crit',
    'ENEMY_RESISTANCE_REDUCTION': 'Enemy Resistance Reduction',
    'PERCENT_HIT_AS_TRUE_DAMAGE': 'True Damage',
    'STATUS_EFFECT_DURATION': 'Status Effect Duration',
}

def format_value(value):
    """Format a value for display, removing trailing zeros."""
    if value == int(value):
        return str(int(value))
    # Remove trailing zeros but keep at least one decimal
    return f"{value:.1f}".rstrip('0').rstrip('.')


def is_percent_display(stat_name):
    """Whether a stat value should show as percentage in description."""
    return (stat_name in DAMAGE_PERCENT_STATS or
            stat_name in CHANCE_STATS or
            stat_name in CRIT_MULT_STATS or  # Crit mult shows as flat number, not %
            stat_name.endswith('_PERCENT'))


def format_modifier_desc(stat_name, value, mod_type):
    """Format a single modifier for the description string."""
    display_name = STAT_DISPLAY_NAMES.get(stat_name, stat_name.replace('_', ' ').title())
    val_str = format_value(abs(value))
    sign = '+' if value >= 0 else '-'

    # PERCENT type stats always show with %
    if mod_type == 'PERCENT' or stat_name in DAMAGE_PERCENT_STATS:
        return f"{sign}{val_str}% {display_name}"

    # FLAT chance stats show as percentage points
    if stat_name in CHANCE_STATS:
        return f"{sign}{val_str}% {display_name}"

    # Crit Multiplier: show as flat value (not %)
    if stat_name in CRIT_MULT_STATS:
        return f"{sign}{val_str} {display_name}"

    # Flat stats: no %
    return f"{sign}{val_str} {display_name}"


# ─── Main Processing ──────────────────────────────────────────────────────────

def process_file(input_path, output_path):
    """Process the skill-tree.yml file, applying balance changes."""
    with open(input_path, 'r') as f:
        lines = f.readlines()

    # First pass: parse the structure to identify nodes and their modifiers
    nodes = {}  # node_id -> {line_ranges, modifiers, description_line, ...}
    current_node = None
    current_section = None  # 'modifiers', 'drawbacks', 'synergy', 'connections'
    modifier_idx = -1
    in_modifier = False
    modifier_start_line = -1

    for i, line in enumerate(lines):
        stripped = line.strip()

        # Detect node start: "  node_id:" at 2-space indent (top-level under nodes:)
        m = re.match(r'^  (\w+):$', line)
        if m and not stripped.startswith('#') and not stripped.startswith('-'):
            node_id = m.group(1)
            if node_id != 'nodes':
                current_node = node_id
                nodes[current_node] = {
                    'start_line': i,
                    'role': get_node_role(current_node),
                    'modifiers': [],
                    'drawbacks': [],
                    'description_line': None,
                    'is_keystone': False,
                    'is_notable': False,
                    'has_synergy': False,
                }
                current_section = None
                modifier_idx = -1
                in_modifier = False
                continue

        if current_node is None:
            continue

        # Detect keystone/notable flags
        if stripped == 'keystone: true':
            nodes[current_node]['is_keystone'] = True
        if stripped == 'notable: true':
            nodes[current_node]['is_notable'] = True

        # Detect description line
        m = re.match(r'^    description: "(.*)"$', line)
        if m:
            nodes[current_node]['description_line'] = i
            continue

        # Detect section starts
        if stripped == 'modifiers:':
            current_section = 'modifiers'
            modifier_idx = -1
            in_modifier = False
            continue
        elif stripped == 'drawbacks:':
            current_section = 'drawbacks'
            modifier_idx = -1
            in_modifier = False
            continue
        elif stripped == 'synergy:':
            current_section = 'synergy'
            nodes[current_node]['has_synergy'] = True
            continue
        elif stripped == 'connections:':
            current_section = 'connections'
            continue

        # Parse modifiers within current section
        if current_section in ('modifiers', 'drawbacks'):
            # Detect new modifier entry: "      - stat: STAT_NAME"
            m = re.match(r'^      - stat: (\w+)$', line)
            if m:
                modifier_idx += 1
                stat_name = m.group(1)
                mod_info = {
                    'stat': stat_name,
                    'stat_line': i,
                    'value': None,
                    'value_line': None,
                    'type': None,
                    'type_line': None,
                    'section': current_section,
                }
                if current_section == 'modifiers':
                    nodes[current_node]['modifiers'].append(mod_info)
                else:
                    nodes[current_node]['drawbacks'].append(mod_info)
                in_modifier = True
                continue

            if in_modifier:
                # Parse value
                m = re.match(r'^        value: (-?[\d.]+)$', line)
                if m:
                    mod_list = nodes[current_node]['modifiers'] if current_section == 'modifiers' else nodes[current_node]['drawbacks']
                    if mod_list:
                        mod_list[-1]['value'] = float(m.group(1))
                        mod_list[-1]['value_line'] = i
                    continue

                # Parse type
                m = re.match(r'^        type: (\w+)$', line)
                if m:
                    mod_list = nodes[current_node]['modifiers'] if current_section == 'modifiers' else nodes[current_node]['drawbacks']
                    if mod_list:
                        mod_list[-1]['type'] = m.group(1)
                        mod_list[-1]['type_line'] = i
                    continue

    # Second pass: apply scaling and update lines
    changes = 0
    for node_id, node in nodes.items():
        role = node['role']

        # Skip keystones and synergy entirely
        if node['is_keystone'] or node['has_synergy'] or role in ('keystone', 'synergy', 'origin'):
            continue

        mods_changed = False

        for mod in node['modifiers']:
            if mod['value'] is None or mod['type'] is None:
                continue

            old_value = mod['value']
            new_value = scale_value(mod['stat'], role, old_value, mod['type'])

            if abs(new_value - old_value) > 0.001:
                # Update the value line
                line_idx = mod['value_line']
                old_line = lines[line_idx]
                # Format value: use integer if whole number, else one decimal
                if new_value == int(new_value):
                    val_str = f"{int(new_value)}.0"
                else:
                    val_str = f"{new_value:.1f}"
                    # Clean up trailing zeros
                    if '.' in val_str:
                        val_str = val_str.rstrip('0')
                        if val_str.endswith('.'):
                            val_str += '0'

                new_line = re.sub(r'value: -?[\d.]+', f'value: {val_str}', old_line)
                lines[line_idx] = new_line
                mod['value'] = new_value
                mods_changed = True
                changes += 1

        # Update description if any modifiers changed
        if mods_changed and node['description_line'] is not None:
            # Build new description from all modifiers
            desc_parts = []
            for mod in node['modifiers']:
                if mod['value'] is not None and mod['stat'] is not None:
                    desc_parts.append(format_modifier_desc(mod['stat'], mod['value'], mod['type']))

            if desc_parts:
                new_desc = ', '.join(desc_parts)
                line_idx = node['description_line']
                lines[line_idx] = f'    description: "{new_desc}"\n'

    # Write output
    with open(output_path, 'w') as f:
        f.writelines(lines)

    print(f"Processed {len(nodes)} nodes, made {changes} value changes")


if __name__ == '__main__':
    input_file = 'src/main/resources/config/skill-tree.yml'
    output_file = input_file  # Overwrite in place

    if len(sys.argv) > 1:
        input_file = sys.argv[1]
    if len(sys.argv) > 2:
        output_file = sys.argv[2]

    process_file(input_file, output_file)
