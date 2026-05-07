#!/usr/bin/env python3
"""
Skill Tree YAML Generator — Phase 6
Generates skill-tree.yml from compact data tables + preserved structure.
Run: python3 scripts/generate-skill-tree.py
"""

import yaml
import json
import os
import sys
from collections import OrderedDict

PROJECT_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CURRENT_YAML = os.path.join(PROJECT_ROOT, 'src/main/resources/config/skill-tree.yml')
OUTPUT_YAML = CURRENT_YAML  # Overwrite in place

# ─── Load current structure ──────────────────────────────────────────────────

with open(CURRENT_YAML, 'r', encoding='utf-8') as f:
    current = yaml.safe_load(f)

feedback = current['feedback']
old_nodes = current['nodes']

def get_struct(nid):
    nd = old_nodes[nid]
    return {
        'connections': nd.get('connections', []),
        'region': nd['region'],
        'tier': nd['tier'],
        'startNode': nd.get('startNode', False),
        'notable': nd.get('notable', False),
        'keystone': nd.get('keystone', False),
    }

struct = {nid: get_struct(nid) for nid in old_nodes}

# ─── Helpers ─────────────────────────────────────────────────────────────────

def F(stat, val): return {'stat': stat, 'value': val, 'type': 'FLAT'}
def P(stat, val): return {'stat': stat, 'value': val, 'type': 'PERCENT'}
def M(stat, val): return {'stat': stat, 'value': val, 'type': 'MULTIPLIER'}

def node(nid, name, desc, mods, **kw):
    s = struct[nid]
    n = OrderedDict()
    n['name'] = name
    n['description'] = desc
    n['region'] = s['region']
    n['tier'] = s['tier']
    if s['startNode']: n['startNode'] = True
    if s['notable']: n['notable'] = True
    if s['keystone']: n['keystone'] = True
    n['connections'] = s['connections']
    n['modifiers'] = mods
    for k in ('drawbacks', 'conditional', 'synergy'):
        if k in kw: n[k] = kw[k]
    return n

def syn(stype, element, per, stat, val, cap):
    return {'type': stype, 'element': element, 'per_count': per,
            'bonus': {'stat': stat, 'value': val}, 'cap': cap}

def cond(trigger, dur, stack, effects, max_s=None):
    c = {'trigger': trigger, 'duration': dur, 'stacking': stack, 'effects': effects}
    if max_s: c['max_stacks'] = max_s
    return c

# ─── Cluster shorthand ──────────────────────────────────────────────────────
# Each cluster is: (prefix, [name1, name2, branch_name, name3, name4, notable_name],
#                    [mod1, mod2, branch_mods, mod3, mod4, notable_mods])

def cluster(prefix, names, mods_list):
    """Generate 6 nodes for a cluster."""
    suffixes = ['_1', '_2', '_branch', '_3', '_4', '_notable']
    out = {}
    for i, sfx in enumerate(suffixes):
        nid = prefix + sfx
        out[nid] = node(nid, names[i], desc_from_mods(mods_list[i]), mods_list[i])
    return out

def desc_from_mods(mods):
    """Auto-generate description from modifiers."""
    parts = []
    for m in mods:
        stat_name = STAT_DISPLAY.get(m['stat'], m['stat'])
        v = m['value']
        if m['type'] == 'PERCENT':
            parts.append(f"+{v}% {stat_name}")
        elif m['type'] == 'FLAT':
            if v == int(v):
                parts.append(f"+{int(v)} {stat_name}")
            else:
                parts.append(f"+{v} {stat_name}")
        elif m['type'] == 'MULTIPLIER':
            parts.append(f"+{v}% {stat_name} (MORE)")
    return ', '.join(parts) if parts else 'No modifiers'

# ─── Stat display names ─────────────────────────────────────────────────────

STAT_DISPLAY = {
    'PHYSICAL_DAMAGE_PERCENT': 'Physical Damage',
    'SPELL_DAMAGE_PERCENT': 'Spell Damage',
    'CRITICAL_MULTIPLIER': 'Crit Multiplier',
    'CRITICAL_CHANCE': 'Crit Chance',
    'ATTACK_SPEED_PERCENT': 'Attack Speed',
    'MOVEMENT_SPEED_PERCENT': 'Move Speed',
    'CHARGED_ATTACK_DAMAGE_PERCENT': 'Charged Attack Damage',
    'BURN_DAMAGE_PERCENT': 'Burn Damage',
    'IGNITE_CHANCE': 'Ignite Chance',
    'FREEZE_CHANCE': 'Freeze Chance',
    'SHOCK_CHANCE': 'Shock Chance',
    'MAX_HEALTH_PERCENT': 'Max HP',
    'ARMOR': 'Armor',
    'ARMOR_PERCENT': 'Armor',
    'HEALTH_REGEN': 'HP Regen/s',
    'BLOCK_CHANCE': 'Block Chance',
    'PASSIVE_BLOCK_CHANCE': 'Block Chance',
    'KNOCKBACK_RESISTANCE': 'KB Resistance',
    'EVASION': 'Evasion',
    'ACCURACY': 'Accuracy',
    'PROJECTILE_DAMAGE_PERCENT': 'Projectile Damage',
    'JUMP_FORCE_PERCENT': 'Jump Force',
    'PROJECTILE_SPEED_PERCENT': 'Projectile Speed',
    'LIFE_STEAL': 'Life Steal',
    'PERCENT_HIT_AS_TRUE_DAMAGE': 'True Damage',
    'DOT_DAMAGE_PERCENT': 'DoT Damage',
    'MANA_ON_KILL': 'Mana on Kill',
    'STATUS_EFFECT_DURATION': 'Status Duration',
    'ENERGY_SHIELD_PERCENT': 'Energy Shield',
    'ENERGY_SHIELD_REGEN': 'ES Regen/s',
    'MAX_MANA': 'Max Mana',
    'MAX_MANA_PERCENT': 'Max Mana',
    'MANA_REGEN': 'Mana Regen/s',
    'STAMINA_REGEN': 'Stamina Regen/s',
    'FIRE_DAMAGE_PERCENT': 'Fire Damage',
    'WATER_DAMAGE_PERCENT': 'Water Damage',
    'LIGHTNING_DAMAGE_PERCENT': 'Lightning Damage',
    'EARTH_DAMAGE_PERCENT': 'Earth Damage',
    'VOID_DAMAGE_PERCENT': 'Void Damage',
    'WIND_DAMAGE_PERCENT': 'Wind Damage',
    'FIRE_PENETRATION': 'Fire Penetration',
    'WATER_PENETRATION': 'Water Penetration',
    'LIGHTNING_PENETRATION': 'Lightning Penetration',
    'EARTH_PENETRATION': 'Earth Penetration',
    'VOID_PENETRATION': 'Void Penetration',
    'WIND_PENETRATION': 'Wind Penetration',
    'DAMAGE_VS_FROZEN_PERCENT': 'Damage vs Frozen',
    'DAMAGE_VS_SHOCKED_PERCENT': 'Damage vs Shocked',
    'EXECUTE_DAMAGE_PERCENT': 'Execute Damage',
    'BURN_DURATION_PERCENT': 'Burn Duration',
    'LIFE_LEECH': 'Life Leech',
    'MANA_LEECH': 'Mana Leech',
    'DODGE_CHANCE': 'Dodge Chance',
    'BLOCK_DAMAGE_REDUCTION': 'Block Damage Reduction',
    'BLOCK_HEAL_PERCENT': 'Block Heal',
    'PHYSICAL_RESISTANCE': 'Physical Resistance',
    'HEALTH_RECOVERY_PERCENT': 'Health Recovery',
    'THORNS_DAMAGE_PERCENT': 'Thorns Damage',
    'ALL_DAMAGE_PERCENT': 'All Damage',
    'STATUS_EFFECT_CHANCE': 'Status Effect Chance',
    'SPELL_PENETRATION': 'Spell Penetration',
    'CRIT_NULLIFY_CHANCE': 'Crit Nullify Chance',
    'ALL_ELEMENTAL_RESISTANCE': 'All Elemental Resistance',
    'ARMOR_PENETRATION': 'Armor Penetration',
    'MANA_AS_DAMAGE_BUFFER': 'Mana as Damage Buffer',
    'DAMAGE_MULTIPLIER': 'Damage',
}

# =============================================================================
# NODE DEFINITIONS — ALL 485 NODES
# =============================================================================

nodes = OrderedDict()

# ─── ORIGIN ──────────────────────────────────────────────────────────────────
nodes['origin'] = node('origin', 'Origin',
    'The heart of the galaxy — your journey begins here', [])

# =============================================================================
# FIRE — The Berserker
# =============================================================================

nodes['fire_entry'] = node('fire_entry', 'Path of Fire',
    '+5% Physical Damage', [P('PHYSICAL_DAMAGE_PERCENT', 5.0)])

# Cluster 1: Ignition (fire_fury) — Lane 1 Inner — Phys Dmg + Crit Mult
nodes['fire_fury_1'] = node('fire_fury_1', 'Ignition I', '+6% Physical Damage', [P('PHYSICAL_DAMAGE_PERCENT', 6.0)])
nodes['fire_fury_2'] = node('fire_fury_2', 'Ignition II', '+5 Crit Multiplier', [F('CRITICAL_MULTIPLIER', 5.0)])
nodes['fire_fury_branch'] = node('fire_fury_branch', 'Searing Focus', '+3% Physical Damage, +3 Crit Multiplier', [P('PHYSICAL_DAMAGE_PERCENT', 3.0), F('CRITICAL_MULTIPLIER', 3.0)])
nodes['fire_fury_3'] = node('fire_fury_3', 'Ignition III', '+6% Physical Damage', [P('PHYSICAL_DAMAGE_PERCENT', 6.0)])
nodes['fire_fury_4'] = node('fire_fury_4', 'Critical Edge', '+6 Crit Multiplier', [F('CRITICAL_MULTIPLIER', 6.0)])
nodes['fire_fury_notable'] = node('fire_fury_notable', 'Searing Strikes', '+10% Fire Damage, +8 Fire Penetration', [P('FIRE_DAMAGE_PERCENT', 10.0), F('FIRE_PENETRATION', 8.0)])

# Cluster 2: Pyre (fire_inferno) — Lane 2 Inner — Burn + Ignite
nodes['fire_inferno_1'] = node('fire_inferno_1', 'Pyre I', '+6% Burn Damage', [P('BURN_DAMAGE_PERCENT', 6.0)])
nodes['fire_inferno_2'] = node('fire_inferno_2', 'Pyre II', '+4% Ignite Chance', [F('IGNITE_CHANCE', 4.0)])
nodes['fire_inferno_branch'] = node('fire_inferno_branch', 'Burning Path', '+3% Burn Damage, +2% Ignite Chance', [P('BURN_DAMAGE_PERCENT', 3.0), F('IGNITE_CHANCE', 2.0)])
nodes['fire_inferno_3'] = node('fire_inferno_3', 'Pyre III', '+6% Burn Damage', [P('BURN_DAMAGE_PERCENT', 6.0)])
nodes['fire_inferno_4'] = node('fire_inferno_4', 'Ignite Mastery', '+4% Ignite Chance', [F('IGNITE_CHANCE', 4.0)])
nodes['fire_inferno_notable'] = node('fire_inferno_notable', 'Pyromaniac', '+12% DoT Damage, +15% Burn Duration', [P('DOT_DAMAGE_PERCENT', 12.0), P('BURN_DURATION_PERCENT', 15.0)])

# Cluster 3: Eruption (fire_bloodlust) — Lane 1 Outer — Charged Atk + Phys
nodes['fire_bloodlust_1'] = node('fire_bloodlust_1', 'Eruption I', '+5% Charged Attack Damage', [P('CHARGED_ATTACK_DAMAGE_PERCENT', 5.0)])
nodes['fire_bloodlust_2'] = node('fire_bloodlust_2', 'Eruption II', '+5% Physical Damage', [P('PHYSICAL_DAMAGE_PERCENT', 5.0)])
nodes['fire_bloodlust_branch'] = node('fire_bloodlust_branch', 'Volcanic Path', '+3% Charged Attack Damage, +3% Physical Damage', [P('CHARGED_ATTACK_DAMAGE_PERCENT', 3.0), P('PHYSICAL_DAMAGE_PERCENT', 3.0)])
nodes['fire_bloodlust_3'] = node('fire_bloodlust_3', 'Eruption III', '+7% Charged Attack Damage', [P('CHARGED_ATTACK_DAMAGE_PERCENT', 7.0)])
nodes['fire_bloodlust_4'] = node('fire_bloodlust_4', 'Power Surge', '+6% Physical Damage', [P('PHYSICAL_DAMAGE_PERCENT', 6.0)])
nodes['fire_bloodlust_notable'] = node('fire_bloodlust_notable', 'Executioner', '+15% Execute Damage, +5 Crit Multiplier', [P('EXECUTE_DAMAGE_PERCENT', 15.0), F('CRITICAL_MULTIPLIER', 5.0)])

# Cluster 4: Inferno (fire_berserker) — Lane 2 Outer — Burn + Ignite
nodes['fire_berserker_1'] = node('fire_berserker_1', 'Inferno I', '+6% Burn Damage', [P('BURN_DAMAGE_PERCENT', 6.0)])
nodes['fire_berserker_2'] = node('fire_berserker_2', 'Inferno II', '+4% Ignite Chance', [F('IGNITE_CHANCE', 4.0)])
nodes['fire_berserker_branch'] = node('fire_berserker_branch', 'Inferno Path', '+3% Burn Damage, +2% Ignite Chance', [P('BURN_DAMAGE_PERCENT', 3.0), F('IGNITE_CHANCE', 2.0)])
nodes['fire_berserker_3'] = node('fire_berserker_3', 'Critical Inferno', '+5 Crit Multiplier', [F('CRITICAL_MULTIPLIER', 5.0)])
nodes['fire_berserker_4'] = node('fire_berserker_4', 'Inferno III', '+6% Burn Damage', [P('BURN_DAMAGE_PERCENT', 6.0)])
nodes['fire_berserker_notable'] = node('fire_berserker_notable', 'Blazing Fury', '+8 Fire Penetration, +10% Charged Attack Damage', [F('FIRE_PENETRATION', 8.0), P('CHARGED_ATTACK_DAMAGE_PERCENT', 10.0)])

# Fire Synergies
nodes['fire_synergy_1'] = node('fire_synergy_1', 'Strength in Numbers', 'Per 3 Fire nodes: +2% Physical Damage (max 20%)', [], synergy=syn('ELEMENTAL_COUNT', 'FIRE', 3, 'PHYSICAL_DAMAGE_PERCENT', 2.0, 20.0))
nodes['fire_synergy_2'] = node('fire_synergy_2', 'Infernal Mastery', 'Per 3 Fire nodes: +3% Fire Damage (max 30%)', [], synergy=syn('ELEMENTAL_COUNT', 'FIRE', 3, 'FIRE_DAMAGE_PERCENT', 3.0, 30.0))
nodes['fire_synergy_3'] = node('fire_synergy_3', 'Burning Resolve', 'Per 3 Fire nodes: +0.5 Crit Multiplier (max 5)', [], synergy=syn('ELEMENTAL_COUNT', 'FIRE', 3, 'CRITICAL_MULTIPLIER', 0.5, 5.0))
nodes['fire_synergy_4'] = node('fire_synergy_4', 'Charged Momentum', 'Per 3 Fire nodes: +1% Charged Attack Damage (max 10%)', [], synergy=syn('ELEMENTAL_COUNT', 'FIRE', 3, 'CHARGED_ATTACK_DAMAGE_PERCENT', 1.0, 10.0))

# Fire Hub
nodes['fire_synergy_hub'] = node('fire_synergy_hub', 'Heart of Fire', '+5% Fire Damage, +5% Physical Damage', [P('FIRE_DAMAGE_PERCENT', 5.0), P('PHYSICAL_DAMAGE_PERCENT', 5.0)])

# Fire Keystones
nodes['fire_keystone_1'] = node('fire_keystone_1', 'Inferno Master',
    '50% of your Physical Damage is converted to Fire Damage.',
    [P('PHYSICAL_TO_FIRE_CONVERSION', 50.0), F('FIRE_DAMAGE_MULTIPLIER', 15.0)],
    drawbacks=[P('MAX_HEALTH_PERCENT', -25.0)])
nodes['fire_keystone_2'] = node('fire_keystone_2', "Berserker's Rage",
    '+1% Damage per 2% missing HP. Attacks cost 3% of current HP. Life steal capped at 50% HP.',
    [F('MISSING_HP_DAMAGE_SCALING', 0.5), F('ATTACK_SELF_DAMAGE_PERCENT', 3.0)],
    drawbacks=[F('LIFE_STEAL_HP_CAP_PERCENT', 50.0)])

# Fire Bridges
nodes['bridge_fire_lightning_1'] = node('bridge_fire_lightning_1', 'Charged Strike I', '+4% Physical Damage, +3% Attack Speed', [P('PHYSICAL_DAMAGE_PERCENT', 4.0), P('ATTACK_SPEED_PERCENT', 3.0)])
nodes['bridge_fire_lightning_2'] = node('bridge_fire_lightning_2', 'Charged Strike II', '+3 Crit Multiplier, +3% Crit Chance', [F('CRITICAL_MULTIPLIER', 3.0), F('CRITICAL_CHANCE', 3.0)])
nodes['bridge_fire_lightning_3'] = node('bridge_fire_lightning_3', 'Thundering Blows', 'Crits: +10% Shock Chance. Shocked take +8% Fire Damage.', [F('CRIT_SHOCK_CHANCE', 10.0), P('FIRE_DAMAGE_VS_SHOCKED', 8.0)])

nodes['bridge_fire_void_1'] = node('bridge_fire_void_1', 'Burning Void I', '+4% Physical Damage, +4% DoT Damage', [P('PHYSICAL_DAMAGE_PERCENT', 4.0), P('DOT_DAMAGE_PERCENT', 4.0)])
nodes['bridge_fire_void_2'] = node('bridge_fire_void_2', 'Burning Void II', '+3% Burn Damage, +0.2% Life Steal', [P('BURN_DAMAGE_PERCENT', 3.0), F('LIFE_STEAL', 0.2)])
nodes['bridge_fire_void_3'] = node('bridge_fire_void_3', 'Infernal Corruption', 'Burn ticks restore 3% of tick damage as HP.', [F('BURN_LEECH_PERCENT', 3.0)])

nodes['bridge_fire_wind_1'] = node('bridge_fire_wind_1', 'Steam Power I', '+4% Physical Damage, +4% Projectile Damage', [P('PHYSICAL_DAMAGE_PERCENT', 4.0), P('PROJECTILE_DAMAGE_PERCENT', 4.0)])
nodes['bridge_fire_wind_2'] = node('bridge_fire_wind_2', 'Steam Power II', '+3% Charged Attack Damage, +4 Accuracy', [P('CHARGED_ATTACK_DAMAGE_PERCENT', 3.0), F('ACCURACY', 4.0)])
nodes['bridge_fire_wind_3'] = node('bridge_fire_wind_3', 'Boiling Currents', 'Charged projectile attacks: +15% bonus Fire Damage.', [P('CHARGED_PROJECTILE_FIRE_BONUS', 15.0)])

nodes['bridge_earth_fire_1'] = node('bridge_earth_fire_1', 'Burning Earth I', '+4% Physical Damage, +4% Max HP', [P('PHYSICAL_DAMAGE_PERCENT', 4.0), P('MAX_HEALTH_PERCENT', 4.0)])
nodes['bridge_earth_fire_2'] = node('bridge_earth_fire_2', 'Burning Earth II', '+3 Crit Multiplier, +3 Armor', [F('CRITICAL_MULTIPLIER', 3.0), F('ARMOR', 3.0)])
nodes['bridge_earth_fire_3'] = node('bridge_earth_fire_3', 'Wild Fury', '+5% Physical Damage, +5% Max HP', [P('PHYSICAL_DAMAGE_PERCENT', 5.0), P('MAX_HEALTH_PERCENT', 5.0)])

# =============================================================================
# WATER — The Arcane Controller
# =============================================================================

nodes['water_entry'] = node('water_entry', 'Path of Water', '+5% Spell Damage', [P('SPELL_DAMAGE_PERCENT', 5.0)])

# Cluster 1: Torrent (water_frostbite) — Lane 1 Inner — Spell Dmg + Max Mana
nodes['water_frostbite_1'] = node('water_frostbite_1', 'Torrent I', '+6% Spell Damage', [P('SPELL_DAMAGE_PERCENT', 6.0)])
nodes['water_frostbite_2'] = node('water_frostbite_2', 'Torrent II', '+8 Max Mana', [F('MAX_MANA', 8.0)])
nodes['water_frostbite_branch'] = node('water_frostbite_branch', 'Arcane Flow', '+3% Spell Damage, +4 Max Mana', [P('SPELL_DAMAGE_PERCENT', 3.0), F('MAX_MANA', 4.0)])
nodes['water_frostbite_3'] = node('water_frostbite_3', 'Torrent III', '+6% Spell Damage', [P('SPELL_DAMAGE_PERCENT', 6.0)])
nodes['water_frostbite_4'] = node('water_frostbite_4', 'Mana Well', '+8 Max Mana', [F('MAX_MANA', 8.0)])
nodes['water_frostbite_notable'] = node('water_frostbite_notable', 'Arcane Surge', '+8% Water Damage, +6 Water Penetration', [P('WATER_DAMAGE_PERCENT', 8.0), F('WATER_PENETRATION', 6.0)])

# Cluster 2: Glacier (water_precision) — Lane 2 Inner — Freeze + ES
nodes['water_precision_1'] = node('water_precision_1', 'Glacier I', '+4% Freeze Chance', [F('FREEZE_CHANCE', 4.0)])
nodes['water_precision_2'] = node('water_precision_2', 'Glacier II', '+5% Energy Shield', [P('ENERGY_SHIELD_PERCENT', 5.0)])
nodes['water_precision_branch'] = node('water_precision_branch', 'Frost Shield', '+2% Freeze Chance, +3% Energy Shield', [F('FREEZE_CHANCE', 2.0), P('ENERGY_SHIELD_PERCENT', 3.0)])
nodes['water_precision_3'] = node('water_precision_3', 'Glacier III', '+4% Freeze Chance', [F('FREEZE_CHANCE', 4.0)])
nodes['water_precision_4'] = node('water_precision_4', 'Barrier Focus', '+5% Energy Shield', [P('ENERGY_SHIELD_PERCENT', 5.0)])
nodes['water_precision_notable'] = node('water_precision_notable', 'Permafrost', '+15% Damage vs Frozen, +5% Freeze Chance', [P('DAMAGE_VS_FROZEN_PERCENT', 15.0), F('FREEZE_CHANCE', 5.0)])

# Cluster 3: Depths (water_evasion) — Lane 1 Outer — Mana Regen + Mana
nodes['water_evasion_1'] = node('water_evasion_1', 'Depths I', '+1.5 Mana Regen/s', [F('MANA_REGEN', 1.5)])
nodes['water_evasion_2'] = node('water_evasion_2', 'Depths II', '+8 Max Mana', [F('MAX_MANA', 8.0)])
nodes['water_evasion_branch'] = node('water_evasion_branch', 'Deep Current', '+1 Mana Regen/s, +4 Max Mana', [F('MANA_REGEN', 1.0), F('MAX_MANA', 4.0)])
nodes['water_evasion_3'] = node('water_evasion_3', 'Depths III', '+1.5 Mana Regen/s', [F('MANA_REGEN', 1.5)])
nodes['water_evasion_4'] = node('water_evasion_4', 'Arcane Depths', '+6% Spell Damage', [P('SPELL_DAMAGE_PERCENT', 6.0)])
nodes['water_evasion_notable'] = node('water_evasion_notable', 'Wellspring', '+5% Max Mana, +3 Mana Regen/s, +2 ES Regen/s', [P('MAX_MANA_PERCENT', 5.0), F('MANA_REGEN', 3.0), F('ENERGY_SHIELD_REGEN', 2.0)])

# Cluster 4: Confluence (water_shatter) — Lane 2 Outer — Spell + ES + ES Regen
nodes['water_shatter_1'] = node('water_shatter_1', 'Confluence I', '+6% Spell Damage', [P('SPELL_DAMAGE_PERCENT', 6.0)])
nodes['water_shatter_2'] = node('water_shatter_2', 'Confluence II', '+5% Energy Shield', [P('ENERGY_SHIELD_PERCENT', 5.0)])
nodes['water_shatter_branch'] = node('water_shatter_branch', 'Confluent Flow', '+3% Spell Damage, +2 ES Regen/s', [P('SPELL_DAMAGE_PERCENT', 3.0), F('ENERGY_SHIELD_REGEN', 2.0)])
nodes['water_shatter_3'] = node('water_shatter_3', 'Shield Mastery', '+5% Energy Shield', [P('ENERGY_SHIELD_PERCENT', 5.0)])
nodes['water_shatter_4'] = node('water_shatter_4', 'Shield Regen', '+2 ES Regen/s', [F('ENERGY_SHIELD_REGEN', 2.0)])
nodes['water_shatter_notable'] = node('water_shatter_notable', 'Shatter', '+8 Water Penetration, +5% Spell Damage', [F('WATER_PENETRATION', 8.0), P('SPELL_DAMAGE_PERCENT', 5.0)])

# Water Synergies
nodes['water_synergy_1'] = node('water_synergy_1', 'Arcane Accumulation', 'Per 3 Water nodes: +2% Spell Damage (max 20%)', [], synergy=syn('ELEMENTAL_COUNT', 'WATER', 3, 'SPELL_DAMAGE_PERCENT', 2.0, 20.0))
nodes['water_synergy_2'] = node('water_synergy_2', 'Tidal Force', 'Per 3 Water nodes: +3% Water Damage (max 30%)', [], synergy=syn('ELEMENTAL_COUNT', 'WATER', 3, 'WATER_DAMAGE_PERCENT', 3.0, 30.0))
nodes['water_synergy_3'] = node('water_synergy_3', 'Frost Intensification', 'Per 3 Water nodes: +1% Freeze Chance (max 10%)', [], synergy=syn('ELEMENTAL_COUNT', 'WATER', 3, 'FREEZE_CHANCE', 1.0, 10.0))
nodes['water_synergy_4'] = node('water_synergy_4', 'Barrier Growth', 'Per 3 Water nodes: +2% Energy Shield (max 20%)', [], synergy=syn('ELEMENTAL_COUNT', 'WATER', 3, 'ENERGY_SHIELD_PERCENT', 2.0, 20.0))
nodes['water_synergy_hub'] = node('water_synergy_hub', 'Heart of Water', '+5% Water Damage, +3 Mana Regen/s', [P('WATER_DAMAGE_PERCENT', 5.0), F('MANA_REGEN', 3.0)])

# Water Keystones
nodes['water_keystone_1'] = node('water_keystone_1', 'Glacial Mastery',
    'Hitting a Frozen target shatters it for 200% burst damage (4s cooldown). -20% damage vs non-Frozen.',
    [F('FREEZE_SHATTER_MULTIPLIER', 200.0), F('FREEZE_CHANCE', 15.0)],
    drawbacks=[P('NON_FROZEN_DAMAGE_PENALTY', -20.0), F('FREEZE_COOLDOWN', 4.0)])
nodes['water_keystone_2'] = node('water_keystone_2', 'Arcane Reservoir',
    'Spell Damage scales with your Max Mana. Spells cost 30% more Mana.',
    [F('DAMAGE_FROM_MANA_PERCENT', 20.0), P('MAX_MANA_PERCENT', 25.0)],
    drawbacks=[P('MANA_COST_PERCENT', 30.0)])

# Water Bridges
nodes['bridge_water_void_1'] = node('bridge_water_void_1', 'Dark Frost I', '+4% Spell Damage, +4% DoT Damage', [P('SPELL_DAMAGE_PERCENT', 4.0), P('DOT_DAMAGE_PERCENT', 4.0)])
nodes['bridge_water_void_2'] = node('bridge_water_void_2', 'Dark Frost II', '+4% Energy Shield, +0.2% Life Steal', [P('ENERGY_SHIELD_PERCENT', 4.0), F('LIFE_STEAL', 0.2)])
nodes['bridge_water_void_3'] = node('bridge_water_void_3', 'Void Chill', '+5% Freeze Chance. Frozen take +8% Void Damage.', [F('FREEZE_CHANCE', 5.0), P('VOID_DAMAGE_VS_FROZEN', 8.0)])
nodes['bridge_water_wind_1'] = node('bridge_water_wind_1', 'Frigid Waters I', '+4% Spell Damage, +4% Projectile Damage', [P('SPELL_DAMAGE_PERCENT', 4.0), P('PROJECTILE_DAMAGE_PERCENT', 4.0)])
nodes['bridge_water_wind_2'] = node('bridge_water_wind_2', 'Frigid Waters II', '+6 Max Mana, +4 Evasion', [F('MAX_MANA', 6.0), F('EVASION', 4.0)])
nodes['bridge_water_wind_3'] = node('bridge_water_wind_3', 'Arctic Arcana', 'Projectile spells: +10% Spell Damage, +5% Freeze Chance.', [P('PROJECTILE_SPELL_DAMAGE_BONUS', 10.0), F('PROJECTILE_FREEZE_CHANCE', 5.0)])
nodes['bridge_water_earth_1'] = node('bridge_water_earth_1', 'Frozen Earth I', '+4% Spell Damage, +4% Max HP', [P('SPELL_DAMAGE_PERCENT', 4.0), P('MAX_HEALTH_PERCENT', 4.0)])
nodes['bridge_water_earth_2'] = node('bridge_water_earth_2', 'Frozen Earth II', '+4% Energy Shield, +3 Armor', [P('ENERGY_SHIELD_PERCENT', 4.0), F('ARMOR', 3.0)])
nodes['bridge_water_earth_3'] = node('bridge_water_earth_3', 'Glacial Fortress', '+3% Block Chance. Blocking does not interrupt ES regen.', [F('BLOCK_CHANCE', 3.0), F('BLOCK_ES_REGEN_UNINTERRUPTED', 1.0)])
nodes['bridge_lightning_water_1'] = node('bridge_lightning_water_1', 'Frozen Lightning I', '+4% Spell Damage, +3% Attack Speed', [P('SPELL_DAMAGE_PERCENT', 4.0), P('ATTACK_SPEED_PERCENT', 3.0)])
nodes['bridge_lightning_water_2'] = node('bridge_lightning_water_2', 'Frozen Lightning II', '+6 Max Mana, +3% Crit Chance', [F('MAX_MANA', 6.0), F('CRITICAL_CHANCE', 3.0)])
nodes['bridge_lightning_water_3'] = node('bridge_lightning_water_3', 'Storm Shatter', 'Spell crits vs Frozen: +20% bonus Lightning Damage.', [P('SPELL_CRIT_VS_FROZEN_LIGHTNING_BONUS', 20.0)])

# =============================================================================
# LIGHTNING — The Storm Dancer
# =============================================================================

nodes['lightning_entry'] = node('lightning_entry', 'Path of Lightning', '+4% Attack Speed', [P('ATTACK_SPEED_PERCENT', 4.0)])

# Cluster 1: Surge (lightning_storm) — Lane 1 Inner — Atk Speed + Crit Chance
nodes['lightning_storm_1'] = node('lightning_storm_1', 'Surge I', '+4% Attack Speed', [P('ATTACK_SPEED_PERCENT', 4.0)])
nodes['lightning_storm_2'] = node('lightning_storm_2', 'Surge II', '+4% Crit Chance', [F('CRITICAL_CHANCE', 4.0)])
nodes['lightning_storm_branch'] = node('lightning_storm_branch', 'Storm Path', '+2% Attack Speed, +2% Crit Chance', [P('ATTACK_SPEED_PERCENT', 2.0), F('CRITICAL_CHANCE', 2.0)])
nodes['lightning_storm_3'] = node('lightning_storm_3', 'Surge III', '+4% Attack Speed', [P('ATTACK_SPEED_PERCENT', 4.0)])
nodes['lightning_storm_4'] = node('lightning_storm_4', 'Precision Strike', '+4% Crit Chance', [F('CRITICAL_CHANCE', 4.0)])
nodes['lightning_storm_notable'] = node('lightning_storm_notable', 'Lightning Reflexes', '+5% Attack Speed, +5% Crit Chance, +5% Lightning Damage', [P('ATTACK_SPEED_PERCENT', 5.0), F('CRITICAL_CHANCE', 5.0), P('LIGHTNING_DAMAGE_PERCENT', 5.0)])

# Cluster 2: Tempest (lightning_velocity) — Lane 2 Inner — Shock + Move Speed
nodes['lightning_velocity_1'] = node('lightning_velocity_1', 'Tempest I', '+4% Shock Chance', [F('SHOCK_CHANCE', 4.0)])
nodes['lightning_velocity_2'] = node('lightning_velocity_2', 'Tempest II', '+3% Move Speed', [P('MOVEMENT_SPEED_PERCENT', 3.0)])
nodes['lightning_velocity_branch'] = node('lightning_velocity_branch', 'Storm Wind', '+2% Shock Chance, +2% Move Speed', [F('SHOCK_CHANCE', 2.0), P('MOVEMENT_SPEED_PERCENT', 2.0)])
nodes['lightning_velocity_3'] = node('lightning_velocity_3', 'Tempest III', '+4% Shock Chance', [F('SHOCK_CHANCE', 4.0)])
nodes['lightning_velocity_4'] = node('lightning_velocity_4', 'Swift Steps', '+3% Move Speed', [P('MOVEMENT_SPEED_PERCENT', 3.0)])
nodes['lightning_velocity_notable'] = node('lightning_velocity_notable', 'Static Field', '+15% Damage vs Shocked, +5% Shock Chance', [P('DAMAGE_VS_SHOCKED_PERCENT', 15.0), F('SHOCK_CHANCE', 5.0)])

# Cluster 3: Arc (lightning_chain) — Lane 1 Outer — Stamina + Atk Speed
nodes['lightning_chain_1'] = node('lightning_chain_1', 'Arc I', '+1.0 Stamina Regen/s', [F('STAMINA_REGEN', 1.0)])
nodes['lightning_chain_2'] = node('lightning_chain_2', 'Arc II', '+4% Attack Speed', [P('ATTACK_SPEED_PERCENT', 4.0)])
nodes['lightning_chain_branch'] = node('lightning_chain_branch', 'Arc Path', '+0.5 Stamina Regen/s, +2% Attack Speed', [F('STAMINA_REGEN', 0.5), P('ATTACK_SPEED_PERCENT', 2.0)])
nodes['lightning_chain_3'] = node('lightning_chain_3', 'Arc III', '+1.0 Stamina Regen/s', [F('STAMINA_REGEN', 1.0)])
nodes['lightning_chain_4'] = node('lightning_chain_4', 'Swift Arc', '+4% Attack Speed', [P('ATTACK_SPEED_PERCENT', 4.0)])
nodes['lightning_chain_notable'] = node('lightning_chain_notable', 'Quickening', '+8 Lightning Penetration, +5% Attack Speed', [F('LIGHTNING_PENETRATION', 8.0), P('ATTACK_SPEED_PERCENT', 5.0)])

# Cluster 4: Conduit (lightning_overcharge) — Lane 2 Outer — Crit + Move
nodes['lightning_overcharge_1'] = node('lightning_overcharge_1', 'Conduit I', '+4% Crit Chance', [F('CRITICAL_CHANCE', 4.0)])
nodes['lightning_overcharge_2'] = node('lightning_overcharge_2', 'Conduit II', '+3% Move Speed', [P('MOVEMENT_SPEED_PERCENT', 3.0)])
nodes['lightning_overcharge_branch'] = node('lightning_overcharge_branch', 'Conduit Path', '+2% Crit Chance, +2% Move Speed', [F('CRITICAL_CHANCE', 2.0), P('MOVEMENT_SPEED_PERCENT', 2.0)])
nodes['lightning_overcharge_3'] = node('lightning_overcharge_3', 'Conduit III', '+4% Crit Chance', [F('CRITICAL_CHANCE', 4.0)])
nodes['lightning_overcharge_4'] = node('lightning_overcharge_4', 'Lightning Steps', '+3% Move Speed', [P('MOVEMENT_SPEED_PERCENT', 3.0)])
nodes['lightning_overcharge_notable'] = node('lightning_overcharge_notable', 'Chain Strike', '+8 Lightning Penetration, +5% Crit Chance', [F('LIGHTNING_PENETRATION', 8.0), F('CRITICAL_CHANCE', 5.0)])

# Lightning Synergies
nodes['lightning_synergy_1'] = node('lightning_synergy_1', 'Storm Buildup', 'Per 3 Lightning nodes: +1% Attack Speed (max 10%)', [], synergy=syn('ELEMENTAL_COUNT', 'LIGHTNING', 3, 'ATTACK_SPEED_PERCENT', 1.0, 10.0))
nodes['lightning_synergy_2'] = node('lightning_synergy_2', 'Voltage Surge', 'Per 3 Lightning nodes: +3% Lightning Damage (max 30%)', [], synergy=syn('ELEMENTAL_COUNT', 'LIGHTNING', 3, 'LIGHTNING_DAMAGE_PERCENT', 3.0, 30.0))
nodes['lightning_synergy_3'] = node('lightning_synergy_3', 'Chain Reaction', 'Per 3 Lightning nodes: +0.5% Crit Chance (max 5%)', [], synergy=syn('ELEMENTAL_COUNT', 'LIGHTNING', 3, 'CRITICAL_CHANCE', 0.5, 5.0))
nodes['lightning_synergy_4'] = node('lightning_synergy_4', 'Static Charge', 'Per 3 Lightning nodes: +1% Shock Chance (max 10%)', [], synergy=syn('ELEMENTAL_COUNT', 'LIGHTNING', 3, 'SHOCK_CHANCE', 1.0, 10.0))
nodes['lightning_synergy_hub'] = node('lightning_synergy_hub', 'Heart of Lightning', '+5% Lightning Damage, +5% Attack Speed', [P('LIGHTNING_DAMAGE_PERCENT', 5.0), P('ATTACK_SPEED_PERCENT', 5.0)])

# Lightning Keystones
nodes['lightning_keystone_1'] = node('lightning_keystone_1', 'Thundergod',
    'Shock chains to 1 nearby enemy at 50% strength (up to 3 targets). -15% single-target damage.',
    [F('SHOCK_CHAIN_COUNT', 1.0), F('SHOCK_CHAIN_STRENGTH', 50.0), F('SHOCK_CHANCE', 15.0)],
    drawbacks=[F('ARMOR', -30.0), P('SINGLE_TARGET_DAMAGE_PERCENT', -15.0)])
nodes['lightning_keystone_2'] = node('lightning_keystone_2', 'Overcharge',
    'Attack Speed above 30% is converted to Spell Damage at a 1:1 ratio.',
    [F('ATK_SPEED_TO_SPELL_POWER', 100.0), F('ATK_SPEED_CONVERSION_THRESHOLD', 30.0), P('ATTACK_SPEED_PERCENT', 15.0)],
    drawbacks=[P('MAX_HEALTH_PERCENT', -20.0)])

# Lightning Bridges
nodes['bridge_lightning_void_1'] = node('bridge_lightning_void_1', 'Storm Void I', '+3% Attack Speed, +4% DoT Damage', [P('ATTACK_SPEED_PERCENT', 3.0), P('DOT_DAMAGE_PERCENT', 4.0)])
nodes['bridge_lightning_void_2'] = node('bridge_lightning_void_2', 'Storm Void II', '+3% Crit Chance, +3% Status Duration', [F('CRITICAL_CHANCE', 3.0), P('STATUS_EFFECT_DURATION', 3.0)])
nodes['bridge_lightning_void_3'] = node('bridge_lightning_void_3', 'Void Storm', 'Shock ticks: +5% Void Damage. +5% Status Duration.', [P('SHOCK_VOID_DAMAGE_BONUS', 5.0), P('STATUS_EFFECT_DURATION', 5.0)])
nodes['bridge_lightning_wind_1'] = node('bridge_lightning_wind_1', 'Storm Depths I', '+3% Attack Speed, +4% Projectile Damage', [P('ATTACK_SPEED_PERCENT', 3.0), P('PROJECTILE_DAMAGE_PERCENT', 4.0)])
nodes['bridge_lightning_wind_2'] = node('bridge_lightning_wind_2', 'Storm Depths II', '+3% Move Speed, +4 Accuracy', [P('MOVEMENT_SPEED_PERCENT', 3.0), F('ACCURACY', 4.0)])
nodes['bridge_lightning_wind_3'] = node('bridge_lightning_wind_3', 'Abyssal Storm', 'Projectile attacks: +8% Attack Speed. +3% Move Speed.', [P('PROJECTILE_ATTACK_SPEED_BONUS', 8.0), P('MOVEMENT_SPEED_PERCENT', 3.0)])

# =============================================================================
# EARTH — The Unbreakable Wall
# =============================================================================

nodes['earth_entry'] = node('earth_entry', 'Path of Earth', '+5% Max HP', [P('MAX_HEALTH_PERCENT', 5.0)])

# Cluster 1: Bastion (earth_vitality) — Lane 1 Inner — Max HP + Armor
nodes['earth_vitality_1'] = node('earth_vitality_1', 'Bastion I', '+5% Max HP', [P('MAX_HEALTH_PERCENT', 5.0)])
nodes['earth_vitality_2'] = node('earth_vitality_2', 'Bastion II', '+6 Armor', [F('ARMOR', 6.0)])
nodes['earth_vitality_branch'] = node('earth_vitality_branch', 'Iron Path', '+3% Max HP, +3 Armor', [P('MAX_HEALTH_PERCENT', 3.0), F('ARMOR', 3.0)])
nodes['earth_vitality_3'] = node('earth_vitality_3', 'Bastion III', '+5% Max HP', [P('MAX_HEALTH_PERCENT', 5.0)])
nodes['earth_vitality_4'] = node('earth_vitality_4', 'Iron Skin', '+6 Armor', [F('ARMOR', 6.0)])
nodes['earth_vitality_notable'] = node('earth_vitality_notable', 'Ironhide', '+8% Max HP, +5% Physical Resistance, +4 Armor', [P('MAX_HEALTH_PERCENT', 8.0), P('PHYSICAL_RESISTANCE', 5.0), F('ARMOR', 4.0)])

# Cluster 2: Rampart (earth_regen) — Lane 2 Inner — Block + KB Resist
nodes['earth_regen_1'] = node('earth_regen_1', 'Rampart I', '+2% Block Chance', [F('BLOCK_CHANCE', 2.0)])
nodes['earth_regen_2'] = node('earth_regen_2', 'Rampart II', '+4% KB Resistance', [P('KNOCKBACK_RESISTANCE', 4.0)])
nodes['earth_regen_branch'] = node('earth_regen_branch', 'Shield Path', '+1% Block Chance, +2% KB Resistance', [F('BLOCK_CHANCE', 1.0), P('KNOCKBACK_RESISTANCE', 2.0)])
nodes['earth_regen_3'] = node('earth_regen_3', 'Rampart III', '+2% Block Chance', [F('BLOCK_CHANCE', 2.0)])
nodes['earth_regen_4'] = node('earth_regen_4', 'Stalwart', '+4% KB Resistance', [P('KNOCKBACK_RESISTANCE', 4.0)])
nodes['earth_regen_notable'] = node('earth_regen_notable', 'Stoneguard', '+3% Block Chance, +5% Block Damage Reduction, +5% Earth Damage', [F('BLOCK_CHANCE', 3.0), P('BLOCK_DAMAGE_REDUCTION', 5.0), P('EARTH_DAMAGE_PERCENT', 5.0)])

# Cluster 3: Bulwark (earth_fortitude) — Lane 1 Outer — HP Regen + Armor
nodes['earth_fortitude_1'] = node('earth_fortitude_1', 'Bulwark I', '+0.3 HP Regen/s', [F('HEALTH_REGEN', 0.3)])
nodes['earth_fortitude_2'] = node('earth_fortitude_2', 'Bulwark II', '+6 Armor', [F('ARMOR', 6.0)])
nodes['earth_fortitude_branch'] = node('earth_fortitude_branch', 'Stone Path', '+0.2 HP Regen/s, +3 Armor', [F('HEALTH_REGEN', 0.2), F('ARMOR', 3.0)])
nodes['earth_fortitude_3'] = node('earth_fortitude_3', 'Bulwark III', '+0.3 HP Regen/s', [F('HEALTH_REGEN', 0.3)])
nodes['earth_fortitude_4'] = node('earth_fortitude_4', 'Enduring', '+5% Max HP', [P('MAX_HEALTH_PERCENT', 5.0)])
nodes['earth_fortitude_notable'] = node('earth_fortitude_notable', 'Unbreakable', '+5% Health Recovery, +0.5 HP Regen/s, +5% KB Resistance', [P('HEALTH_RECOVERY_PERCENT', 5.0), F('HEALTH_REGEN', 0.5), P('KNOCKBACK_RESISTANCE', 5.0)])

# Cluster 4: Citadel (earth_guardian) — Lane 2 Outer — Max HP + KB
nodes['earth_guardian_1'] = node('earth_guardian_1', 'Citadel I', '+5% Max HP', [P('MAX_HEALTH_PERCENT', 5.0)])
nodes['earth_guardian_2'] = node('earth_guardian_2', 'Citadel II', '+4% KB Resistance', [P('KNOCKBACK_RESISTANCE', 4.0)])
nodes['earth_guardian_branch'] = node('earth_guardian_branch', 'Citadel Path', '+3% Max HP, +2% KB Resistance', [P('MAX_HEALTH_PERCENT', 3.0), P('KNOCKBACK_RESISTANCE', 2.0)])
nodes['earth_guardian_3'] = node('earth_guardian_3', 'Citadel Shield', '+2% Block Chance', [F('BLOCK_CHANCE', 2.0)])
nodes['earth_guardian_4'] = node('earth_guardian_4', 'Citadel IV', '+5% Max HP', [P('MAX_HEALTH_PERCENT', 5.0)])
nodes['earth_guardian_notable'] = node('earth_guardian_notable', "Mountain's Resolve", '+5% Block Heal, +5% Block Damage Reduction, +5% Earth Damage', [P('BLOCK_HEAL_PERCENT', 5.0), P('BLOCK_DAMAGE_REDUCTION', 5.0), P('EARTH_DAMAGE_PERCENT', 5.0)])

# Earth Synergies
nodes['earth_synergy_1'] = node('earth_synergy_1', 'Bedrock Foundation', 'Per 3 Earth nodes: +2% Max HP (max 20%)', [], synergy=syn('ELEMENTAL_COUNT', 'EARTH', 3, 'MAX_HEALTH_PERCENT', 2.0, 20.0))
nodes['earth_synergy_2'] = node('earth_synergy_2', "Mountain's Might", 'Per 3 Earth nodes: +3% Earth Damage (max 30%)', [], synergy=syn('ELEMENTAL_COUNT', 'EARTH', 3, 'EARTH_DAMAGE_PERCENT', 3.0, 30.0))
nodes['earth_synergy_3'] = node('earth_synergy_3', 'Shield Attunement', 'Per 3 Earth nodes: +0.5% Block Chance (max 5%)', [], synergy=syn('ELEMENTAL_COUNT', 'EARTH', 3, 'BLOCK_CHANCE', 0.5, 5.0))
nodes['earth_synergy_4'] = node('earth_synergy_4', 'Enduring Fortitude', 'Per 3 Earth nodes: +3 Armor (max 30)', [], synergy=syn('ELEMENTAL_COUNT', 'EARTH', 3, 'ARMOR', 3.0, 30.0))
nodes['earth_synergy_hub'] = node('earth_synergy_hub', 'Heart of Earth', '+5% Earth Damage, +5% Max HP', [P('EARTH_DAMAGE_PERCENT', 5.0), P('MAX_HEALTH_PERCENT', 5.0)])

# Earth Keystones
nodes['earth_keystone_1'] = node('earth_keystone_1', 'Living Fortress',
    'After 1s stationary: +50% Armor, +15% Block Chance. Blocked attacks heal 5% of blocked damage. Moving breaks Fortified.',
    [F('FORTIFIED_ARMOR_BONUS', 50.0), F('FORTIFIED_BLOCK_BONUS', 15.0), F('FORTIFIED_BLOCK_HEAL', 5.0)],
    drawbacks=[P('DAMAGE_PERCENT', -25.0)])
nodes['earth_keystone_2'] = node('earth_keystone_2', 'Tectonic Bulwark',
    '+1% Physical Damage per 50 Max HP above 200. 20% of your Armor is dealt as Thorns.',
    [F('HP_SCALING_DAMAGE', 1.0), P('MAX_HEALTH_PERCENT', 20.0), F('ARMOR_TO_THORNS_PERCENT', 20.0)],
    drawbacks=[P('EVASION_PERCENT', -30.0), P('ATTACK_SPEED_PERCENT', -15.0)])

# Earth Bridges
nodes['bridge_earth_void_1'] = node('bridge_earth_void_1', 'Decaying Growth I', '+4% Max HP, +4% DoT Damage', [P('MAX_HEALTH_PERCENT', 4.0), P('DOT_DAMAGE_PERCENT', 4.0)])
nodes['bridge_earth_void_2'] = node('bridge_earth_void_2', 'Decaying Growth II', '+3 Armor, +0.2% Life Steal', [F('ARMOR', 3.0), F('LIFE_STEAL', 0.2)])
nodes['bridge_earth_void_3'] = node('bridge_earth_void_3', 'Soul Garden', '+0.5 HP Regen/s. DoTs heal 2% of tick damage.', [F('HEALTH_REGEN', 0.5), F('DOT_LEECH_PERCENT', 2.0)])
nodes['bridge_earth_wind_1'] = node('bridge_earth_wind_1', 'Verdant Flow I', '+4% Max HP, +4% Projectile Damage', [P('MAX_HEALTH_PERCENT', 4.0), P('PROJECTILE_DAMAGE_PERCENT', 4.0)])
nodes['bridge_earth_wind_2'] = node('bridge_earth_wind_2', 'Verdant Flow II', '+3 Armor, +4 Evasion', [F('ARMOR', 3.0), F('EVASION', 4.0)])
nodes['bridge_earth_wind_3'] = node('bridge_earth_wind_3', 'Life Stream', '+8 Evasion. Evading restores 2% Max HP.', [F('EVASION', 8.0), F('EVADE_HP_RESTORE_PERCENT', 2.0)])

# =============================================================================
# VOID — The Parasite
# =============================================================================

nodes['void_entry'] = node('void_entry', 'Path of Void', '+5% DoT Damage', [P('DOT_DAMAGE_PERCENT', 5.0)])

# Cluster 1: Blight (void_corruption) — Lane 1 Inner — Life Steal + DoT
nodes['void_corruption_1'] = node('void_corruption_1', 'Blight I', '+0.4% Life Steal', [F('LIFE_STEAL', 0.4)])
nodes['void_corruption_2'] = node('void_corruption_2', 'Blight II', '+5% DoT Damage', [P('DOT_DAMAGE_PERCENT', 5.0)])
nodes['void_corruption_branch'] = node('void_corruption_branch', 'Blight Path', '+0.2% Life Steal, +3% DoT Damage', [F('LIFE_STEAL', 0.2), P('DOT_DAMAGE_PERCENT', 3.0)])
nodes['void_corruption_3'] = node('void_corruption_3', 'Blight III', '+0.4% Life Steal', [F('LIFE_STEAL', 0.4)])
nodes['void_corruption_4'] = node('void_corruption_4', 'Dark Sustain', '+5% DoT Damage', [P('DOT_DAMAGE_PERCENT', 5.0)])
nodes['void_corruption_notable'] = node('void_corruption_notable', 'Sanguine Drain', '+1% Life Steal, +3% Life Leech, +5% DoT Damage', [F('LIFE_STEAL', 1.0), F('LIFE_LEECH', 3.0), P('DOT_DAMAGE_PERCENT', 5.0)])

# Cluster 2: Entropy (void_entropy) — Lane 2 Inner — Status Duration + Mana on Kill
nodes['void_entropy_1'] = node('void_entropy_1', 'Entropy I', '+5% Status Duration', [P('STATUS_EFFECT_DURATION', 5.0)])
nodes['void_entropy_2'] = node('void_entropy_2', 'Entropy II', '+1 Mana on Kill', [F('MANA_ON_KILL', 1.0)])
nodes['void_entropy_branch'] = node('void_entropy_branch', 'Entropy Path', '+3% Status Duration, +0.5 Mana on Kill', [P('STATUS_EFFECT_DURATION', 3.0), F('MANA_ON_KILL', 0.5)])
nodes['void_entropy_3'] = node('void_entropy_3', 'Entropy III', '+5% Status Duration', [P('STATUS_EFFECT_DURATION', 5.0)])
nodes['void_entropy_4'] = node('void_entropy_4', 'Dark Knowledge', '+1 Mana on Kill', [F('MANA_ON_KILL', 1.0)])
nodes['void_entropy_notable'] = node('void_entropy_notable', 'Lingering Torment', '+10% Status Duration, +6 Void Penetration', [P('STATUS_EFFECT_DURATION', 10.0), F('VOID_PENETRATION', 6.0)])

# Cluster 3: Shadow (void_drain) — Lane 1 Outer — True Dmg + Life Steal
nodes['void_drain_1'] = node('void_drain_1', 'Shadow I', '+0.3% True Damage', [F('PERCENT_HIT_AS_TRUE_DAMAGE', 0.3)])
nodes['void_drain_2'] = node('void_drain_2', 'Shadow II', '+0.4% Life Steal', [F('LIFE_STEAL', 0.4)])
nodes['void_drain_branch'] = node('void_drain_branch', 'Shadow Path', '+0.2% True Damage, +0.2% Life Steal', [F('PERCENT_HIT_AS_TRUE_DAMAGE', 0.2), F('LIFE_STEAL', 0.2)])
nodes['void_drain_3'] = node('void_drain_3', 'Shadow III', '+0.3% True Damage', [F('PERCENT_HIT_AS_TRUE_DAMAGE', 0.3)])
nodes['void_drain_4'] = node('void_drain_4', 'Dark Siphon', '+0.4% Life Steal', [F('LIFE_STEAL', 0.4)])
nodes['void_drain_notable'] = node('void_drain_notable', 'Essence Harvest', '+0.5% True Damage, +3% Life Leech, +1 Mana on Kill', [F('PERCENT_HIT_AS_TRUE_DAMAGE', 0.5), F('LIFE_LEECH', 3.0), F('MANA_ON_KILL', 1.0)])

# Cluster 4: Abyss (void_oblivion) — Lane 2 Outer — DoT + Mana on Kill
nodes['void_oblivion_1'] = node('void_oblivion_1', 'Abyss I', '+5% DoT Damage', [P('DOT_DAMAGE_PERCENT', 5.0)])
nodes['void_oblivion_2'] = node('void_oblivion_2', 'Abyss II', '+1 Mana on Kill', [F('MANA_ON_KILL', 1.0)])
nodes['void_oblivion_branch'] = node('void_oblivion_branch', 'Abyss Path', '+3% DoT Damage, +0.5 Mana on Kill', [P('DOT_DAMAGE_PERCENT', 3.0), F('MANA_ON_KILL', 0.5)])
nodes['void_oblivion_3'] = node('void_oblivion_3', 'Abyss III', '+5% DoT Damage', [P('DOT_DAMAGE_PERCENT', 5.0)])
nodes['void_oblivion_4'] = node('void_oblivion_4', 'Dark Decay', '+5% Status Duration', [P('STATUS_EFFECT_DURATION', 5.0)])
nodes['void_oblivion_notable'] = node('void_oblivion_notable', 'Void Corruption', '+8% DoT Damage, +8 Void Penetration', [P('DOT_DAMAGE_PERCENT', 8.0), F('VOID_PENETRATION', 8.0)])

# Void Synergies
nodes['void_synergy_1'] = node('void_synergy_1', 'Dark Accumulation', 'Per 3 Void nodes: +0.3% Life Steal (max 3%)', [], synergy=syn('ELEMENTAL_COUNT', 'VOID', 3, 'LIFE_STEAL', 0.3, 3.0))
nodes['void_synergy_2'] = node('void_synergy_2', 'Void Resonance', 'Per 3 Void nodes: +3% Void Damage (max 30%)', [], synergy=syn('ELEMENTAL_COUNT', 'VOID', 3, 'VOID_DAMAGE_PERCENT', 3.0, 30.0))
nodes['void_synergy_3'] = node('void_synergy_3', 'Soul Harvest', 'Per 3 Void nodes: +2% DoT Damage (max 20%)', [], synergy=syn('ELEMENTAL_COUNT', 'VOID', 3, 'DOT_DAMAGE_PERCENT', 2.0, 20.0))
nodes['void_synergy_4'] = node('void_synergy_4', 'Entropy Growth', 'Per 3 Void nodes: +1% Status Duration (max 10%)', [], synergy=syn('ELEMENTAL_COUNT', 'VOID', 3, 'STATUS_EFFECT_DURATION', 1.0, 10.0))
nodes['void_synergy_hub'] = node('void_synergy_hub', 'Heart of Void', '+5% Void Damage, +5% DoT Damage', [P('VOID_DAMAGE_PERCENT', 5.0), P('DOT_DAMAGE_PERCENT', 5.0)])

# Void Keystones
nodes['void_keystone_1'] = node('void_keystone_1', 'Void Walker',
    '50% of your damage is converted to Void. 15% of Void damage bypasses defenses as True Damage.',
    [P('DAMAGE_TO_VOID_CONVERSION', 50.0), F('VOID_TO_TRUE_DAMAGE_PERCENT', 15.0)],
    drawbacks=[P('MAX_HEALTH_PERCENT', -20.0), F('ALL_ELEMENTAL_RESISTANCE', -5.0)])
nodes['void_keystone_2'] = node('void_keystone_2', 'Parasitic Link',
    'Health Regeneration disabled. Sustain only through Life Leech.',
    [F('LIFE_LEECH', 10.0), F('PERCENT_HIT_AS_TRUE_DAMAGE', 10.0)],
    drawbacks=[P('DAMAGE_TAKEN_PERCENT', 20.0), F('HEALTH_REGEN_DISABLED', 1.0)])

# =============================================================================
# WIND — The Ghost
# =============================================================================

nodes['wind_entry'] = node('wind_entry', 'Path of Wind', '+5% Projectile Damage', [P('PROJECTILE_DAMAGE_PERCENT', 5.0)])

# Cluster 1: Gale (wind_flow) — Lane 1 Inner — Proj Dmg + Accuracy
nodes['wind_flow_1'] = node('wind_flow_1', 'Gale I', '+5% Projectile Damage', [P('PROJECTILE_DAMAGE_PERCENT', 5.0)])
nodes['wind_flow_2'] = node('wind_flow_2', 'Gale II', '+5 Accuracy', [F('ACCURACY', 5.0)])
nodes['wind_flow_branch'] = node('wind_flow_branch', 'Wind Path', '+3% Projectile Damage, +3 Accuracy', [P('PROJECTILE_DAMAGE_PERCENT', 3.0), F('ACCURACY', 3.0)])
nodes['wind_flow_3'] = node('wind_flow_3', 'Gale III', '+5% Projectile Damage', [P('PROJECTILE_DAMAGE_PERCENT', 5.0)])
nodes['wind_flow_4'] = node('wind_flow_4', 'Keen Eye', '+5 Accuracy', [F('ACCURACY', 5.0)])
nodes['wind_flow_notable'] = node('wind_flow_notable', 'Wind Archer', '+8% Projectile Damage, +6 Wind Penetration, +5% Wind Damage', [P('PROJECTILE_DAMAGE_PERCENT', 8.0), F('WIND_PENETRATION', 6.0), P('WIND_DAMAGE_PERCENT', 5.0)])

# Cluster 2: Zephyr (wind_arcane) — Lane 2 Inner — Evasion + Jump
nodes['wind_arcane_1'] = node('wind_arcane_1', 'Zephyr I', '+8 Evasion', [F('EVASION', 8.0)])
nodes['wind_arcane_2'] = node('wind_arcane_2', 'Zephyr II', '+3% Jump Force', [P('JUMP_FORCE_PERCENT', 3.0)])
nodes['wind_arcane_branch'] = node('wind_arcane_branch', 'Zephyr Path', '+4 Evasion, +2% Jump Force', [F('EVASION', 4.0), P('JUMP_FORCE_PERCENT', 2.0)])
nodes['wind_arcane_3'] = node('wind_arcane_3', 'Zephyr III', '+8 Evasion', [F('EVASION', 8.0)])
nodes['wind_arcane_4'] = node('wind_arcane_4', 'Updraft', '+3% Jump Force', [P('JUMP_FORCE_PERCENT', 3.0)])
nodes['wind_arcane_notable'] = node('wind_arcane_notable', 'Skybound', '+10 Evasion, +3% Dodge Chance, +3% Move Speed', [F('EVASION', 10.0), P('DODGE_CHANCE', 3.0), P('MOVEMENT_SPEED_PERCENT', 3.0)])

# Cluster 3: Marksman (wind_adaptation) — Lane 1 Outer — Proj Speed + Accuracy
nodes['wind_adaptation_1'] = node('wind_adaptation_1', 'Marksman I', '+5% Projectile Speed', [P('PROJECTILE_SPEED_PERCENT', 5.0)])
nodes['wind_adaptation_2'] = node('wind_adaptation_2', 'Marksman II', '+5 Accuracy', [F('ACCURACY', 5.0)])
nodes['wind_adaptation_branch'] = node('wind_adaptation_branch', 'Marksman Path', '+3% Projectile Speed, +3 Accuracy', [P('PROJECTILE_SPEED_PERCENT', 3.0), F('ACCURACY', 3.0)])
nodes['wind_adaptation_3'] = node('wind_adaptation_3', 'Marksman III', '+5% Projectile Speed', [P('PROJECTILE_SPEED_PERCENT', 5.0)])
nodes['wind_adaptation_4'] = node('wind_adaptation_4', 'Sharp Aim', '+5% Projectile Damage', [P('PROJECTILE_DAMAGE_PERCENT', 5.0)])
nodes['wind_adaptation_notable'] = node('wind_adaptation_notable', 'Sharpshooter', '+8% Projectile Damage, +8 Wind Penetration', [P('PROJECTILE_DAMAGE_PERCENT', 8.0), F('WIND_PENETRATION', 8.0)])

# Cluster 4: Drift (wind_depth) — Lane 2 Outer — Evasion + Jump
nodes['wind_depth_1'] = node('wind_depth_1', 'Drift I', '+8 Evasion', [F('EVASION', 8.0)])
nodes['wind_depth_2'] = node('wind_depth_2', 'Drift II', '+3% Jump Force', [P('JUMP_FORCE_PERCENT', 3.0)])
nodes['wind_depth_branch'] = node('wind_depth_branch', 'Drift Path', '+4 Evasion, +2% Jump Force', [F('EVASION', 4.0), P('JUMP_FORCE_PERCENT', 2.0)])
nodes['wind_depth_3'] = node('wind_depth_3', 'Drift III', '+8 Evasion', [F('EVASION', 8.0)])
nodes['wind_depth_4'] = node('wind_depth_4', 'Air Walk', '+3% Jump Force', [P('JUMP_FORCE_PERCENT', 3.0)])
nodes['wind_depth_notable'] = node('wind_depth_notable', 'Tailwind', '+5% Evasion, +5% Projectile Speed, +3% Move Speed', [P('EVASION_PERCENT', 5.0), P('PROJECTILE_SPEED_PERCENT', 5.0), P('MOVEMENT_SPEED_PERCENT', 3.0)])

# Wind Synergies
nodes['wind_synergy_1'] = node('wind_synergy_1', 'Wind Convergence', 'Per 3 Wind nodes: +2% Projectile Damage (max 20%)', [], synergy=syn('ELEMENTAL_COUNT', 'WIND', 3, 'PROJECTILE_DAMAGE_PERCENT', 2.0, 20.0))
nodes['wind_synergy_2'] = node('wind_synergy_2', 'Gale Force', 'Per 3 Wind nodes: +3% Wind Damage (max 30%)', [], synergy=syn('ELEMENTAL_COUNT', 'WIND', 3, 'WIND_DAMAGE_PERCENT', 3.0, 30.0))
nodes['wind_synergy_3'] = node('wind_synergy_3', 'Eye Training', 'Per 3 Wind nodes: +5 Accuracy (max 50)', [], synergy=syn('ELEMENTAL_COUNT', 'WIND', 3, 'ACCURACY', 5.0, 50.0))
nodes['wind_synergy_4'] = node('wind_synergy_4', 'Updraft', 'Per 3 Wind nodes: +5 Evasion (max 50)', [], synergy=syn('ELEMENTAL_COUNT', 'WIND', 3, 'EVASION', 5.0, 50.0))
nodes['wind_synergy_hub'] = node('wind_synergy_hub', 'Heart of Wind', '+5% Wind Damage, +5% Projectile Damage', [P('WIND_DAMAGE_PERCENT', 5.0), P('PROJECTILE_DAMAGE_PERCENT', 5.0)])

# Wind Keystones
nodes['wind_keystone_1'] = node('wind_keystone_1', 'Phantom',
    'On Evade: +15% All Damage and +10% Movement Speed for 3s.',
    [P('EVASION_PERCENT', 30.0), P('PROJECTILE_DAMAGE_PERCENT', 15.0)],
    conditional=cond('ON_EVADE', 3.0, 'REFRESH', [{'stat': 'ALL_DAMAGE_PERCENT', 'value': 15.0, 'modifierType': 'PERCENT'}, {'stat': 'MOVEMENT_SPEED_PERCENT', 'value': 10.0, 'modifierType': 'PERCENT'}]),
    drawbacks=[F('ARMOR', -35.0), P('MAX_HEALTH_PERCENT', -20.0)])
nodes['wind_keystone_2'] = node('wind_keystone_2', 'Sky Piercer',
    'After 1.5s without attacking, your next projectile deals +80% damage and ignores 20% of enemy defenses.',
    [F('FOCUSED_SHOT_DAMAGE_BONUS', 80.0), F('FOCUSED_SHOT_RESIST_IGNORE', 20.0), F('FOCUSED_SHOT_CHARGE_TIME', 1.5)],
    drawbacks=[P('MELEE_DAMAGE_PERCENT', -30.0), P('ATTACK_SPEED_PERCENT', -20.0)])

# =============================================================================
# HAVOC — The Berserker Assassin (Fire + Void + Lightning)
# =============================================================================

nodes['havoc_entry'] = node('havoc_entry', 'Path of Havoc', '+4% Physical Damage', [P('PHYSICAL_DAMAGE_PERCENT', 4.0)])

# Cluster 1: Carnage (havoc_carnage) — Lane 1 Inner — Crit + Speed
nodes['havoc_carnage_1'] = node('havoc_carnage_1', 'Carnage I', '+5 Crit Multiplier', [F('CRITICAL_MULTIPLIER', 5.0)])
nodes['havoc_carnage_2'] = node('havoc_carnage_2', 'Carnage II', '+4% Attack Speed', [P('ATTACK_SPEED_PERCENT', 4.0)])
nodes['havoc_carnage_branch'] = node('havoc_carnage_branch', 'Carnage Path', '+3 Crit Multiplier, +2% Attack Speed', [F('CRITICAL_MULTIPLIER', 3.0), P('ATTACK_SPEED_PERCENT', 2.0)])
nodes['havoc_carnage_3'] = node('havoc_carnage_3', 'Carnage III', '+4% Crit Chance', [F('CRITICAL_CHANCE', 4.0)])
nodes['havoc_carnage_4'] = node('havoc_carnage_4', 'Havoc Strike', '+5% Physical Damage', [P('PHYSICAL_DAMAGE_PERCENT', 5.0)])
nodes['havoc_carnage_notable'] = node('havoc_carnage_notable', 'Killing Spree', 'ON_KILL: +10% Atk Speed, +8% Crit Chance for 4s. +5% Armor Penetration.',
    [F('ARMOR_PENETRATION', 5.0)],
    conditional=cond('ON_KILL', 4.0, 'REFRESH', [{'stat': 'ATTACK_SPEED_PERCENT', 'value': 10.0, 'modifierType': 'PERCENT'}, {'stat': 'CRITICAL_CHANCE', 'value': 8.0, 'modifierType': 'FLAT'}]))

# Cluster 2: Frenzy (havoc_frenzy) — Lane 2 Inner — DoT + Sustain
nodes['havoc_frenzy_1'] = node('havoc_frenzy_1', 'Frenzy I', '+5% DoT Damage', [P('DOT_DAMAGE_PERCENT', 5.0)])
nodes['havoc_frenzy_2'] = node('havoc_frenzy_2', 'Frenzy II', '+0.4% Life Steal', [F('LIFE_STEAL', 0.4)])
nodes['havoc_frenzy_branch'] = node('havoc_frenzy_branch', 'Frenzy Path', '+3% DoT Damage, +0.2% Life Steal', [P('DOT_DAMAGE_PERCENT', 3.0), F('LIFE_STEAL', 0.2)])
nodes['havoc_frenzy_3'] = node('havoc_frenzy_3', 'Frenzy III', '+6% Burn Damage', [P('BURN_DAMAGE_PERCENT', 6.0)])
nodes['havoc_frenzy_4'] = node('havoc_frenzy_4', 'Frenzy IV', '+4% Ignite Chance', [F('IGNITE_CHANCE', 4.0)])
nodes['havoc_frenzy_notable'] = node('havoc_frenzy_notable', "Death's Touch", "+10% DoT Damage, +0.5% True Damage, +5% Status Effect Chance", [P('DOT_DAMAGE_PERCENT', 10.0), F('PERCENT_HIT_AS_TRUE_DAMAGE', 0.5), P('STATUS_EFFECT_CHANCE', 5.0)])

# Cluster 3: Ruin (havoc_ruin) — Lane 1 Outer — Fast Crits
nodes['havoc_ruin_1'] = node('havoc_ruin_1', 'Ruin I', '+4% Crit Chance', [F('CRITICAL_CHANCE', 4.0)])
nodes['havoc_ruin_2'] = node('havoc_ruin_2', 'Ruin II', '+5% Physical Damage', [P('PHYSICAL_DAMAGE_PERCENT', 5.0)])
nodes['havoc_ruin_branch'] = node('havoc_ruin_branch', 'Ruin Path', '+2% Crit Chance, +3% Physical Damage', [F('CRITICAL_CHANCE', 2.0), P('PHYSICAL_DAMAGE_PERCENT', 3.0)])
nodes['havoc_ruin_3'] = node('havoc_ruin_3', 'Ruin III', '+5 Crit Multiplier', [F('CRITICAL_MULTIPLIER', 5.0)])
nodes['havoc_ruin_4'] = node('havoc_ruin_4', 'Ruin IV', '+4% Attack Speed', [P('ATTACK_SPEED_PERCENT', 4.0)])
nodes['havoc_ruin_notable'] = node('havoc_ruin_notable', 'Storm of Blades', '+8% Attack Speed, +8 Crit Multiplier, +5% All Damage', [P('ATTACK_SPEED_PERCENT', 8.0), F('CRITICAL_MULTIPLIER', 8.0), P('ALL_DAMAGE_PERCENT', 5.0)])

# Cluster 4: Mayhem (havoc_mayhem) — Lane 2 Outer — DoT + Sustain
nodes['havoc_mayhem_1'] = node('havoc_mayhem_1', 'Mayhem I', '+0.3% True Damage', [F('PERCENT_HIT_AS_TRUE_DAMAGE', 0.3)])
nodes['havoc_mayhem_2'] = node('havoc_mayhem_2', 'Mayhem II', '+5% DoT Damage', [P('DOT_DAMAGE_PERCENT', 5.0)])
nodes['havoc_mayhem_branch'] = node('havoc_mayhem_branch', 'Mayhem Path', '+0.2% True Damage, +3 Crit Multiplier', [F('PERCENT_HIT_AS_TRUE_DAMAGE', 0.2), F('CRITICAL_MULTIPLIER', 3.0)])
nodes['havoc_mayhem_3'] = node('havoc_mayhem_3', 'Mayhem III', '+4% Attack Speed', [P('ATTACK_SPEED_PERCENT', 4.0)])
nodes['havoc_mayhem_4'] = node('havoc_mayhem_4', 'Mayhem IV', '+0.4% Life Steal', [F('LIFE_STEAL', 0.4)])
nodes['havoc_mayhem_notable'] = node('havoc_mayhem_notable', 'Bloodbath', '+12% Execute Damage, +1% Life Steal', [P('EXECUTE_DAMAGE_PERCENT', 12.0), F('LIFE_STEAL', 1.0)])

# Havoc Synergies
nodes['havoc_synergy_1'] = node('havoc_synergy_1', 'Chaos Resonance', 'Per 3 Havoc nodes: +2 Crit Multiplier (max 20)', [], synergy=syn('BRANCH_COUNT', 'HAVOC', 3, 'CRITICAL_MULTIPLIER', 2.0, 20.0))
nodes['havoc_synergy_2'] = node('havoc_synergy_2', 'Shattered Defenses', 'Per 3 Havoc nodes: +1% Armor Penetration (max 10%)', [], synergy=syn('BRANCH_COUNT', 'HAVOC', 3, 'ARMOR_PENETRATION', 1.0, 10.0))
nodes['havoc_synergy_3'] = node('havoc_synergy_3', 'Bloodlust', 'Per 3 Havoc nodes: +2% Execute Damage (max 20%)', [], synergy=syn('BRANCH_COUNT', 'HAVOC', 3, 'EXECUTE_DAMAGE_PERCENT', 2.0, 20.0))
nodes['havoc_synergy_4'] = node('havoc_synergy_4', 'Rampage Buildup', 'Per 3 Havoc nodes: +0.1% True Damage (max 1%)', [], synergy=syn('BRANCH_COUNT', 'HAVOC', 3, 'PERCENT_HIT_AS_TRUE_DAMAGE', 0.1, 1.0))
nodes['havoc_synergy_hub'] = node('havoc_synergy_hub', 'Nexus of Havoc', '+0.1% Crit Multiplier per (Fire+Void+Lightning) attribute point',
    [], synergy=syn('ATTRIBUTE_SUM_SCALING', 'HAVOC', 1, 'CRITICAL_MULTIPLIER', 0.1, 999.0))

# Havoc Keystones
nodes['havoc_keystone_1'] = node('havoc_keystone_1', 'Rampage',
    'On Kill: +8% Attack Speed, +4 Crit Multiplier, +2% DoT Damage per stack. Max 5 stacks, lasts 6s, refreshes on kill.',
    [F('RAMPAGE_STACK_ATK_SPEED', 8.0), F('RAMPAGE_STACK_CRIT_MULT', 4.0), F('RAMPAGE_STACK_DOT', 2.0), F('RAMPAGE_MAX_STACKS', 5.0), F('RAMPAGE_DURATION', 6.0)],
    drawbacks=[P('MAX_HEALTH_PERCENT', -15.0)])
nodes['havoc_keystone_2'] = node('havoc_keystone_2', 'Chain Detonation',
    'Critical hits detonate all active DoTs on the target as instant Void burst damage.',
    [F('DETONATE_DOT_ON_CRIT', 100.0)],
    drawbacks=[P('STATUS_EFFECT_DURATION', -30.0), F('CRITICAL_CHANCE', -10.0)])

# =============================================================================
# JUGGERNAUT — The War Machine (Fire + Void + Earth)
# =============================================================================

nodes['juggernaut_entry'] = node('juggernaut_entry', 'Path of Juggernaut', '+4% Physical Damage', [P('PHYSICAL_DAMAGE_PERCENT', 4.0)])

# Cluster 1: Conquest (juggernaut_conquest) — Lane 1 Inner
nodes['juggernaut_conquest_1'] = node('juggernaut_conquest_1', 'Conquest I', '+5% Physical Damage', [P('PHYSICAL_DAMAGE_PERCENT', 5.0)])
nodes['juggernaut_conquest_2'] = node('juggernaut_conquest_2', 'Conquest II', '+5% Max HP', [P('MAX_HEALTH_PERCENT', 5.0)])
nodes['juggernaut_conquest_branch'] = node('juggernaut_conquest_branch', 'Conquest Path', '+3% Physical Damage, +3% Max HP', [P('PHYSICAL_DAMAGE_PERCENT', 3.0), P('MAX_HEALTH_PERCENT', 3.0)])
nodes['juggernaut_conquest_3'] = node('juggernaut_conquest_3', 'Conquest III', '+6 Armor', [F('ARMOR', 6.0)])
nodes['juggernaut_conquest_4'] = node('juggernaut_conquest_4', 'Conquest IV', '+5% Physical Damage', [P('PHYSICAL_DAMAGE_PERCENT', 5.0)])
nodes['juggernaut_conquest_notable'] = node('juggernaut_conquest_notable', 'Iron Reaver', '+10% Physical Damage, +5% Max HP, +5 Armor', [P('PHYSICAL_DAMAGE_PERCENT', 10.0), P('MAX_HEALTH_PERCENT', 5.0), F('ARMOR', 5.0)])

# Cluster 2: Dominion (juggernaut_dominion) — Lane 2 Inner
nodes['juggernaut_dominion_1'] = node('juggernaut_dominion_1', 'Dominion I', '+2% Block Chance', [F('BLOCK_CHANCE', 2.0)])
nodes['juggernaut_dominion_2'] = node('juggernaut_dominion_2', 'Dominion II', '+0.3 HP Regen/s', [F('HEALTH_REGEN', 0.3)])
nodes['juggernaut_dominion_branch'] = node('juggernaut_dominion_branch', 'Dominion Path', '+1% Block Chance, +0.2 HP Regen/s', [F('BLOCK_CHANCE', 1.0), F('HEALTH_REGEN', 0.2)])
nodes['juggernaut_dominion_3'] = node('juggernaut_dominion_3', 'Dominion III', '+0.4% Life Steal', [F('LIFE_STEAL', 0.4)])
nodes['juggernaut_dominion_4'] = node('juggernaut_dominion_4', 'Dominion IV', '+6% Burn Damage', [P('BURN_DAMAGE_PERCENT', 6.0)])
nodes['juggernaut_dominion_notable'] = node('juggernaut_dominion_notable', 'Blood Guard', '+5% Block Chance, +1% Life Steal, +5% Thorns Damage', [F('BLOCK_CHANCE', 5.0), F('LIFE_STEAL', 1.0), P('THORNS_DAMAGE_PERCENT', 5.0)])

# Cluster 3: Bloodforge (juggernaut_bloodforge) — Lane 1 Outer
nodes['juggernaut_bloodforge_1'] = node('juggernaut_bloodforge_1', 'Bloodforge I', '+5% Charged Attack Damage', [P('CHARGED_ATTACK_DAMAGE_PERCENT', 5.0)])
nodes['juggernaut_bloodforge_2'] = node('juggernaut_bloodforge_2', 'Bloodforge II', '+5% Max HP', [P('MAX_HEALTH_PERCENT', 5.0)])
nodes['juggernaut_bloodforge_branch'] = node('juggernaut_bloodforge_branch', 'Bloodforge Path', '+3% Charged Attack Damage, +3% Max HP', [P('CHARGED_ATTACK_DAMAGE_PERCENT', 3.0), P('MAX_HEALTH_PERCENT', 3.0)])
nodes['juggernaut_bloodforge_3'] = node('juggernaut_bloodforge_3', 'Bloodforge III', '+0.4% Life Steal', [F('LIFE_STEAL', 0.4)])
nodes['juggernaut_bloodforge_4'] = node('juggernaut_bloodforge_4', 'Bloodforge IV', '+6 Armor', [F('ARMOR', 6.0)])
nodes['juggernaut_bloodforge_notable'] = node('juggernaut_bloodforge_notable', 'Relentless', '+10% Charged Attack Damage, +0.5% True Damage, +5% Block Heal', [P('CHARGED_ATTACK_DAMAGE_PERCENT', 10.0), F('PERCENT_HIT_AS_TRUE_DAMAGE', 0.5), P('BLOCK_HEAL_PERCENT', 5.0)])

# Cluster 4: Tyrant (juggernaut_tyrant) — Lane 2 Outer
nodes['juggernaut_tyrant_1'] = node('juggernaut_tyrant_1', 'Tyrant I', '+5% Max HP', [P('MAX_HEALTH_PERCENT', 5.0)])
nodes['juggernaut_tyrant_2'] = node('juggernaut_tyrant_2', 'Tyrant II', '+5% DoT Damage', [P('DOT_DAMAGE_PERCENT', 5.0)])
nodes['juggernaut_tyrant_branch'] = node('juggernaut_tyrant_branch', 'Tyrant Path', '+3% Max HP, +0.2% Life Steal', [P('MAX_HEALTH_PERCENT', 3.0), F('LIFE_STEAL', 0.2)])
nodes['juggernaut_tyrant_3'] = node('juggernaut_tyrant_3', 'Tyrant III', '+6% Burn Damage', [P('BURN_DAMAGE_PERCENT', 6.0)])
nodes['juggernaut_tyrant_4'] = node('juggernaut_tyrant_4', 'Tyrant IV', '+6 Armor', [F('ARMOR', 6.0)])
nodes['juggernaut_tyrant_notable'] = node('juggernaut_tyrant_notable', 'Indomitable', '+8% Max HP, +8% Thorns Damage, +5% Physical Resistance', [P('MAX_HEALTH_PERCENT', 8.0), P('THORNS_DAMAGE_PERCENT', 8.0), P('PHYSICAL_RESISTANCE', 5.0)])

# Juggernaut Synergies
nodes['juggernaut_synergy_1'] = node('juggernaut_synergy_1', 'Unbreakable Wall', 'Per 3 Juggernaut nodes: +2% Max HP (max 20%)', [], synergy=syn('BRANCH_COUNT', 'JUGGERNAUT', 3, 'MAX_HEALTH_PERCENT', 2.0, 20.0))
nodes['juggernaut_synergy_2'] = node('juggernaut_synergy_2', 'Blood Pact', 'Per 3 Juggernaut nodes: +0.3% Life Steal (max 3%)', [], synergy=syn('BRANCH_COUNT', 'JUGGERNAUT', 3, 'LIFE_STEAL', 0.3, 3.0))
nodes['juggernaut_synergy_3'] = node('juggernaut_synergy_3', 'Iron Thorns', 'Per 3 Juggernaut nodes: +2% Thorns Damage (max 20%)', [], synergy=syn('BRANCH_COUNT', 'JUGGERNAUT', 3, 'THORNS_DAMAGE_PERCENT', 2.0, 20.0))
nodes['juggernaut_synergy_4'] = node('juggernaut_synergy_4', 'Battle Recovery', 'Per 3 Juggernaut nodes: +0.2 HP Regen/s (max 2.0)', [], synergy=syn('BRANCH_COUNT', 'JUGGERNAUT', 3, 'HEALTH_REGEN', 0.2, 2.0))
nodes['juggernaut_synergy_hub'] = node('juggernaut_synergy_hub', 'Nexus of Juggernaut', '+0.15% Max HP per (Fire+Void+Earth) attribute point',
    [], synergy=syn('ATTRIBUTE_SUM_SCALING', 'JUGGERNAUT', 1, 'MAX_HEALTH_PERCENT', 0.15, 999.0))

# Juggernaut Keystones
nodes['juggernaut_keystone_1'] = node('juggernaut_keystone_1', 'Blood Fortress',
    'On Block: next 3 attacks gain 15% of blocked damage as Life Steal. Other Life Steal sources reduced by 50%.',
    [F('BLOCK_CHARGE_LIFE_STEAL', 15.0), F('BLOCK_CHARGE_TEMP_HP', 10.0), F('BLOCK_CHARGE_COUNT', 3.0)],
    drawbacks=[P('ATTACK_SPEED_PERCENT', -25.0), P('LIFE_STEAL_REDUCTION', -50.0)])
nodes['juggernaut_keystone_2'] = node('juggernaut_keystone_2', 'Colossus',
    '+1% Physical Damage per 50 Max HP above 200.',
    [F('HP_SCALING_DAMAGE', 1.0), P('MAX_HEALTH_PERCENT', 15.0)],
    drawbacks=[P('ATTACK_SPEED_PERCENT', -25.0), F('EVASION', -50.0)])

# =============================================================================
# STRIKER — The Blade Dancer (Fire + Wind + Lightning)
# =============================================================================

nodes['striker_entry'] = node('striker_entry', 'Path of Striker', '+3% Attack Speed', [P('ATTACK_SPEED_PERCENT', 3.0)])

# Cluster 1: Quicksilver (striker_quicksilver) — Lane 1 Inner
nodes['striker_quicksilver_1'] = node('striker_quicksilver_1', 'Quicksilver I', '+4% Attack Speed', [P('ATTACK_SPEED_PERCENT', 4.0)])
nodes['striker_quicksilver_2'] = node('striker_quicksilver_2', 'Quicksilver II', '+4% Crit Chance', [F('CRITICAL_CHANCE', 4.0)])
nodes['striker_quicksilver_branch'] = node('striker_quicksilver_branch', 'Quicksilver Path', '+2% Attack Speed, +2% Crit Chance', [P('ATTACK_SPEED_PERCENT', 2.0), F('CRITICAL_CHANCE', 2.0)])
nodes['striker_quicksilver_3'] = node('striker_quicksilver_3', 'Quicksilver III', '+3% Move Speed', [P('MOVEMENT_SPEED_PERCENT', 3.0)])
nodes['striker_quicksilver_4'] = node('striker_quicksilver_4', 'Quicksilver IV', '+5 Crit Multiplier', [F('CRITICAL_MULTIPLIER', 5.0)])
nodes['striker_quicksilver_notable'] = node('striker_quicksilver_notable', 'Quick Draw', '+5% Attack Speed, +5% Move Speed, +5% Projectile Damage', [P('ATTACK_SPEED_PERCENT', 5.0), P('MOVEMENT_SPEED_PERCENT', 5.0), P('PROJECTILE_DAMAGE_PERCENT', 5.0)])

# Cluster 2: Precision (striker_precision) — Lane 2 Inner
nodes['striker_precision_1'] = node('striker_precision_1', 'Precision I', '+8 Evasion', [F('EVASION', 8.0)])
nodes['striker_precision_2'] = node('striker_precision_2', 'Precision II', '+5 Accuracy', [F('ACCURACY', 5.0)])
nodes['striker_precision_branch'] = node('striker_precision_branch', 'Precision Path', '+4 Evasion, +3 Accuracy', [F('EVASION', 4.0), F('ACCURACY', 3.0)])
nodes['striker_precision_3'] = node('striker_precision_3', 'Precision III', '+5% Physical Damage', [P('PHYSICAL_DAMAGE_PERCENT', 5.0)])
nodes['striker_precision_4'] = node('striker_precision_4', 'Precision IV', '+1.0 Stamina Regen/s', [F('STAMINA_REGEN', 1.0)])
nodes['striker_precision_notable'] = node('striker_precision_notable', 'Vital Strike', '+8 Crit Multiplier, +4% Crit Chance, +3% Dodge Chance', [F('CRITICAL_MULTIPLIER', 8.0), F('CRITICAL_CHANCE', 4.0), P('DODGE_CHANCE', 3.0)])

# Cluster 3: Ambush (striker_ambush) — Lane 1 Outer
nodes['striker_ambush_1'] = node('striker_ambush_1', 'Ambush I', '+5% Physical Damage', [P('PHYSICAL_DAMAGE_PERCENT', 5.0)])
nodes['striker_ambush_2'] = node('striker_ambush_2', 'Ambush II', '+4% Attack Speed', [P('ATTACK_SPEED_PERCENT', 4.0)])
nodes['striker_ambush_branch'] = node('striker_ambush_branch', 'Ambush Path', '+3% Physical Damage, +2% Attack Speed', [P('PHYSICAL_DAMAGE_PERCENT', 3.0), P('ATTACK_SPEED_PERCENT', 2.0)])
nodes['striker_ambush_3'] = node('striker_ambush_3', 'Ambush III', '+4% Crit Chance', [F('CRITICAL_CHANCE', 4.0)])
nodes['striker_ambush_4'] = node('striker_ambush_4', 'Ambush IV', '+5 Crit Multiplier', [F('CRITICAL_MULTIPLIER', 5.0)])
nodes['striker_ambush_notable'] = node('striker_ambush_notable', 'Relentless Assault', '+5% Attack Speed, +5% Physical Damage, +5% Charged Attack Damage', [P('ATTACK_SPEED_PERCENT', 5.0), P('PHYSICAL_DAMAGE_PERCENT', 5.0), P('CHARGED_ATTACK_DAMAGE_PERCENT', 5.0)])

# Cluster 4: Flurry (striker_flurry) — Lane 2 Outer
nodes['striker_flurry_1'] = node('striker_flurry_1', 'Flurry I', '+8 Evasion', [F('EVASION', 8.0)])
nodes['striker_flurry_2'] = node('striker_flurry_2', 'Flurry II', '+4% Crit Chance', [F('CRITICAL_CHANCE', 4.0)])
nodes['striker_flurry_branch'] = node('striker_flurry_branch', 'Flurry Path', '+4 Evasion, +2% Crit Chance', [F('EVASION', 4.0), F('CRITICAL_CHANCE', 2.0)])
nodes['striker_flurry_3'] = node('striker_flurry_3', 'Flurry III', '+5% Physical Damage', [P('PHYSICAL_DAMAGE_PERCENT', 5.0)])
nodes['striker_flurry_4'] = node('striker_flurry_4', 'Flurry IV', '+5 Accuracy', [F('ACCURACY', 5.0)])
nodes['striker_flurry_notable'] = node('striker_flurry_notable', 'Feint', 'ON_EVADE: +12% Crit Chance for 3s. +8 Evasion, +3% Dodge Chance.',
    [F('EVASION', 8.0), P('DODGE_CHANCE', 3.0)],
    conditional=cond('ON_EVADE', 3.0, 'REFRESH', [{'stat': 'CRITICAL_CHANCE', 'value': 12.0, 'modifierType': 'FLAT'}]))

# Striker Synergies
nodes['striker_synergy_1'] = node('striker_synergy_1', 'Quicksilver Reflexes', 'Per 3 Striker nodes: +1% Attack Speed (max 10%)', [], synergy=syn('BRANCH_COUNT', 'STRIKER', 3, 'ATTACK_SPEED_PERCENT', 1.0, 10.0))
nodes['striker_synergy_2'] = node('striker_synergy_2', 'Precision Training', 'Per 3 Striker nodes: +0.5% Crit Chance (max 5%)', [], synergy=syn('BRANCH_COUNT', 'STRIKER', 3, 'CRITICAL_CHANCE', 0.5, 5.0))
nodes['striker_synergy_3'] = node('striker_synergy_3', 'Burst Protocol', 'Per 3 Striker nodes: +1% Move Speed (max 10%)', [], synergy=syn('BRANCH_COUNT', 'STRIKER', 3, 'MOVEMENT_SPEED_PERCENT', 1.0, 10.0))
nodes['striker_synergy_4'] = node('striker_synergy_4', 'Shadow Step', 'Per 3 Striker nodes: +1% Dodge Chance (max 10%)', [], synergy=syn('BRANCH_COUNT', 'STRIKER', 3, 'DODGE_CHANCE', 1.0, 10.0))
nodes['striker_synergy_hub'] = node('striker_synergy_hub', 'Nexus of Striker', '+0.1% Attack Speed per (Fire+Wind+Lightning) attribute point',
    [], synergy=syn('ATTRIBUTE_SUM_SCALING', 'STRIKER', 1, 'ATTACK_SPEED_PERCENT', 0.1, 999.0))

# Striker Keystones
nodes['striker_keystone_1'] = node('striker_keystone_1', 'Blade Dance',
    'On Evade: your next hit within 2s is a guaranteed Critical Strike with +25% bonus Crit Multiplier.',
    [F('EVADE_GUARANTEED_CRIT', 1.0), F('EVADE_CRIT_MULT_BONUS', 25.0)],
    drawbacks=[F('ARMOR', -25.0), P('MAX_HEALTH_PERCENT', -15.0)])
nodes['striker_keystone_2'] = node('striker_keystone_2', 'Momentum',
    'Consecutive hits within 2s: +3% damage per stack, up to 10 stacks (+30%).',
    [F('CONSECUTIVE_HIT_BONUS', 3.0)],
    drawbacks=[P('MAX_HEALTH_PERCENT', -20.0)])

# =============================================================================
# WARDEN — The Iron Ranger (Fire + Wind + Earth)
# =============================================================================

nodes['warden_entry'] = node('warden_entry', 'Path of Warden', '+6 Armor', [F('ARMOR', 6.0)])

# Cluster 1: Garrison (warden_garrison) — Lane 1 Inner
nodes['warden_garrison_1'] = node('warden_garrison_1', 'Garrison I', '+6 Armor', [F('ARMOR', 6.0)])
nodes['warden_garrison_2'] = node('warden_garrison_2', 'Garrison II', '+2% Block Chance', [F('BLOCK_CHANCE', 2.0)])
nodes['warden_garrison_branch'] = node('warden_garrison_branch', 'Garrison Path', '+3 Armor, +1% Block Chance', [F('ARMOR', 3.0), F('BLOCK_CHANCE', 1.0)])
nodes['warden_garrison_3'] = node('warden_garrison_3', 'Garrison III', '+5% Max HP', [P('MAX_HEALTH_PERCENT', 5.0)])
nodes['warden_garrison_4'] = node('warden_garrison_4', 'Garrison IV', '+5% Charged Attack Damage', [P('CHARGED_ATTACK_DAMAGE_PERCENT', 5.0)])
nodes['warden_garrison_notable'] = node('warden_garrison_notable', 'Shield Wall', '+5% Block Chance, +5% Max HP, +5% KB Resistance', [F('BLOCK_CHANCE', 5.0), P('MAX_HEALTH_PERCENT', 5.0), P('KNOCKBACK_RESISTANCE', 5.0)])

# Cluster 2: Outrider (warden_outrider) — Lane 2 Inner
nodes['warden_outrider_1'] = node('warden_outrider_1', 'Outrider I', '+5% Projectile Damage', [P('PROJECTILE_DAMAGE_PERCENT', 5.0)])
nodes['warden_outrider_2'] = node('warden_outrider_2', 'Outrider II', '+5 Accuracy', [F('ACCURACY', 5.0)])
nodes['warden_outrider_branch'] = node('warden_outrider_branch', 'Outrider Path', '+3% Projectile Damage, +3 Accuracy', [P('PROJECTILE_DAMAGE_PERCENT', 3.0), F('ACCURACY', 3.0)])
nodes['warden_outrider_3'] = node('warden_outrider_3', 'Outrider III', '+5% Projectile Speed', [P('PROJECTILE_SPEED_PERCENT', 5.0)])
nodes['warden_outrider_4'] = node('warden_outrider_4', 'Outrider IV', '+5% Physical Damage', [P('PHYSICAL_DAMAGE_PERCENT', 5.0)])
nodes['warden_outrider_notable'] = node('warden_outrider_notable', 'Iron Rain', '+10% Projectile Damage, +5 Armor, +5% Wind Damage', [P('PROJECTILE_DAMAGE_PERCENT', 10.0), F('ARMOR', 5.0), P('WIND_DAMAGE_PERCENT', 5.0)])

# Cluster 3: Ironclad (warden_ironclad) — Lane 1 Outer
nodes['warden_ironclad_1'] = node('warden_ironclad_1', 'Ironclad I', '+5% Physical Damage', [P('PHYSICAL_DAMAGE_PERCENT', 5.0)])
nodes['warden_ironclad_2'] = node('warden_ironclad_2', 'Ironclad II', '+6 Armor', [F('ARMOR', 6.0)])
nodes['warden_ironclad_branch'] = node('warden_ironclad_branch', 'Ironclad Path', '+3% Physical Damage, +3 Armor', [P('PHYSICAL_DAMAGE_PERCENT', 3.0), F('ARMOR', 3.0)])
nodes['warden_ironclad_3'] = node('warden_ironclad_3', 'Ironclad III', '+5 Crit Multiplier', [F('CRITICAL_MULTIPLIER', 5.0)])
nodes['warden_ironclad_4'] = node('warden_ironclad_4', 'Ironclad IV', '+5% Max HP', [P('MAX_HEALTH_PERCENT', 5.0)])
nodes['warden_ironclad_notable'] = node('warden_ironclad_notable', 'Power Shot', '+8% Charged Attack Damage, +5 Crit Multiplier, +5% Projectile Damage', [P('CHARGED_ATTACK_DAMAGE_PERCENT', 8.0), F('CRITICAL_MULTIPLIER', 5.0), P('PROJECTILE_DAMAGE_PERCENT', 5.0)])

# Cluster 4: Palisade (warden_palisade) — Lane 2 Outer
nodes['warden_palisade_1'] = node('warden_palisade_1', 'Palisade I', '+5% Projectile Damage', [P('PROJECTILE_DAMAGE_PERCENT', 5.0)])
nodes['warden_palisade_2'] = node('warden_palisade_2', 'Palisade II', '+2% Block Chance', [F('BLOCK_CHANCE', 2.0)])
nodes['warden_palisade_branch'] = node('warden_palisade_branch', 'Palisade Path', '+3% Projectile Damage, +1% Block Chance', [P('PROJECTILE_DAMAGE_PERCENT', 3.0), F('BLOCK_CHANCE', 1.0)])
nodes['warden_palisade_3'] = node('warden_palisade_3', 'Palisade III', '+5 Accuracy', [F('ACCURACY', 5.0)])
nodes['warden_palisade_4'] = node('warden_palisade_4', 'Palisade IV', '+5% Charged Attack Damage', [P('CHARGED_ATTACK_DAMAGE_PERCENT', 5.0)])
nodes['warden_palisade_notable'] = node('warden_palisade_notable', 'Fortified Position', 'ON_BLOCK: +10% Projectile Damage for 3s. +5% Block Chance, +5% Projectile Speed.',
    [F('BLOCK_CHANCE', 5.0), P('PROJECTILE_SPEED_PERCENT', 5.0)],
    conditional=cond('ON_BLOCK', 3.0, 'REFRESH', [{'stat': 'PROJECTILE_DAMAGE_PERCENT', 'value': 10.0, 'modifierType': 'PERCENT'}]))

# Warden Synergies
nodes['warden_synergy_1'] = node('warden_synergy_1', 'Fortified Range', 'Per 3 Warden nodes: +2% Projectile Damage (max 20%)', [], synergy=syn('BRANCH_COUNT', 'WARDEN', 3, 'PROJECTILE_DAMAGE_PERCENT', 2.0, 20.0))
nodes['warden_synergy_2'] = node('warden_synergy_2', 'Power Draw', 'Per 3 Warden nodes: +2% Charged Attack Damage (max 20%)', [], synergy=syn('BRANCH_COUNT', 'WARDEN', 3, 'CHARGED_ATTACK_DAMAGE_PERCENT', 2.0, 20.0))
nodes['warden_synergy_3'] = node('warden_synergy_3', 'Eagle Eye', 'Per 3 Warden nodes: +5 Accuracy (max 50)', [], synergy=syn('BRANCH_COUNT', 'WARDEN', 3, 'ACCURACY', 5.0, 50.0))
nodes['warden_synergy_4'] = node('warden_synergy_4', 'Immovable Stance', 'Per 3 Warden nodes: +1% KB Resistance (max 10%)', [], synergy=syn('BRANCH_COUNT', 'WARDEN', 3, 'KNOCKBACK_RESISTANCE', 1.0, 10.0))
nodes['warden_synergy_hub'] = node('warden_synergy_hub', 'Nexus of Warden', '+0.15% Projectile Damage per (Fire+Wind+Earth) attribute point',
    [], synergy=syn('ATTRIBUTE_SUM_SCALING', 'WARDEN', 1, 'PROJECTILE_DAMAGE_PERCENT', 0.15, 999.0))

# Warden Keystones
nodes['warden_keystone_1'] = node('warden_keystone_1', 'Earthen Volley',
    'While blocking, fire projectiles at -40% damage. Hits grant +5 Armor for 8s (max 6 stacks).',
    [F('BLOCK_SHOOT_ENABLED', 1.0), F('BLOCK_SHOOT_DAMAGE_PENALTY', 40.0), F('BULWARK_ARMOR_PER_HIT', 5.0), F('BULWARK_MAX_STACKS', 6.0)],
    drawbacks=[P('SPELL_DAMAGE_PERCENT', -20.0), P('MELEE_DAMAGE_PERCENT', -10.0)])
nodes['warden_keystone_2'] = node('warden_keystone_2', 'Stalwart Counter',
    'Blocks reflect 150% of blocked damage back to the attacker.',
    [F('BLOCK_COUNTER_DAMAGE', 150.0), F('BLOCK_CHANCE', 10.0)],
    drawbacks=[P('SPELL_DAMAGE_PERCENT', -20.0), P('ATTACK_SPEED_PERCENT', -20.0)])

# =============================================================================
# WARLOCK — The Dark Caster (Water + Void + Lightning)
# =============================================================================

nodes['warlock_entry'] = node('warlock_entry', 'Path of Warlock', '+4% Spell Damage', [P('SPELL_DAMAGE_PERCENT', 4.0)])

# Cluster 1: Hex (warlock_hex) — Lane 1 Inner
nodes['warlock_hex_1'] = node('warlock_hex_1', 'Hex I', '+6% Spell Damage', [P('SPELL_DAMAGE_PERCENT', 6.0)])
nodes['warlock_hex_2'] = node('warlock_hex_2', 'Hex II', '+5% DoT Damage', [P('DOT_DAMAGE_PERCENT', 5.0)])
nodes['warlock_hex_branch'] = node('warlock_hex_branch', 'Hex Path', '+3% Spell Damage, +3% DoT Damage', [P('SPELL_DAMAGE_PERCENT', 3.0), P('DOT_DAMAGE_PERCENT', 3.0)])
nodes['warlock_hex_3'] = node('warlock_hex_3', 'Hex III', '+1 Mana on Kill', [F('MANA_ON_KILL', 1.0)])
nodes['warlock_hex_4'] = node('warlock_hex_4', 'Hex IV', '+4% Crit Chance', [F('CRITICAL_CHANCE', 4.0)])
nodes['warlock_hex_notable'] = node('warlock_hex_notable', 'Dark Arcana', '+8% Spell Damage, +5% DoT Damage, +6 Void Penetration', [P('SPELL_DAMAGE_PERCENT', 8.0), P('DOT_DAMAGE_PERCENT', 5.0), F('VOID_PENETRATION', 6.0)])

# Cluster 2: Ritual (warlock_ritual) — Lane 2 Inner
nodes['warlock_ritual_1'] = node('warlock_ritual_1', 'Ritual I', '+8 Max Mana', [F('MAX_MANA', 8.0)])
nodes['warlock_ritual_2'] = node('warlock_ritual_2', 'Ritual II', '+1.5 Mana Regen/s', [F('MANA_REGEN', 1.5)])
nodes['warlock_ritual_branch'] = node('warlock_ritual_branch', 'Ritual Path', '+4 Max Mana, +1 Mana Regen/s', [F('MAX_MANA', 4.0), F('MANA_REGEN', 1.0)])
nodes['warlock_ritual_3'] = node('warlock_ritual_3', 'Ritual III', '+0.4% Life Steal', [F('LIFE_STEAL', 0.4)])
nodes['warlock_ritual_4'] = node('warlock_ritual_4', 'Ritual IV', '+4% Attack Speed', [P('ATTACK_SPEED_PERCENT', 4.0)])
nodes['warlock_ritual_notable'] = node('warlock_ritual_notable', 'Mind Drain', '+3 Mana Regen/s, +1% Life Steal, +3% Mana Leech', [F('MANA_REGEN', 3.0), F('LIFE_STEAL', 1.0), F('MANA_LEECH', 3.0)])

# Cluster 3: Malice (warlock_malice) — Lane 1 Outer
nodes['warlock_malice_1'] = node('warlock_malice_1', 'Malice I', '+4% Crit Chance', [F('CRITICAL_CHANCE', 4.0)])
nodes['warlock_malice_2'] = node('warlock_malice_2', 'Malice II', '+5% DoT Damage', [P('DOT_DAMAGE_PERCENT', 5.0)])
nodes['warlock_malice_branch'] = node('warlock_malice_branch', 'Malice Path', '+2% Crit Chance, +3% DoT Damage', [F('CRITICAL_CHANCE', 2.0), P('DOT_DAMAGE_PERCENT', 3.0)])
nodes['warlock_malice_3'] = node('warlock_malice_3', 'Malice III', '+6% Spell Damage', [P('SPELL_DAMAGE_PERCENT', 6.0)])
nodes['warlock_malice_4'] = node('warlock_malice_4', 'Malice IV', '+0.3% True Damage', [F('PERCENT_HIT_AS_TRUE_DAMAGE', 0.3)])
nodes['warlock_malice_notable'] = node('warlock_malice_notable', 'Eldritch Blast', '+5% True Damage, +5% Crit Chance, +5% Status Effect Chance', [F('PERCENT_HIT_AS_TRUE_DAMAGE', 5.0), F('CRITICAL_CHANCE', 5.0), P('STATUS_EFFECT_CHANCE', 5.0)])

# Cluster 4: Damnation (warlock_damnation) — Lane 2 Outer
nodes['warlock_damnation_1'] = node('warlock_damnation_1', 'Damnation I', '+6% Spell Damage', [P('SPELL_DAMAGE_PERCENT', 6.0)])
nodes['warlock_damnation_2'] = node('warlock_damnation_2', 'Damnation II', '+5% Status Duration', [P('STATUS_EFFECT_DURATION', 5.0)])
nodes['warlock_damnation_branch'] = node('warlock_damnation_branch', 'Damnation Path', '+3% Spell Damage, +3% Status Duration', [P('SPELL_DAMAGE_PERCENT', 3.0), P('STATUS_EFFECT_DURATION', 3.0)])
nodes['warlock_damnation_3'] = node('warlock_damnation_3', 'Damnation III', '+4% Attack Speed', [P('ATTACK_SPEED_PERCENT', 4.0)])
nodes['warlock_damnation_4'] = node('warlock_damnation_4', 'Damnation IV', '+1 Mana on Kill', [F('MANA_ON_KILL', 1.0)])
nodes['warlock_damnation_notable'] = node('warlock_damnation_notable', 'Cursed Knowledge', '+8% Spell Damage, +8% Status Duration, +6 Spell Penetration', [P('SPELL_DAMAGE_PERCENT', 8.0), P('STATUS_EFFECT_DURATION', 8.0), F('SPELL_PENETRATION', 6.0)])

# Warlock Synergies
nodes['warlock_synergy_1'] = node('warlock_synergy_1', 'Arcane Corruption', 'Per 3 Warlock nodes: +2% Spell Damage (max 20%)', [], synergy=syn('BRANCH_COUNT', 'WARLOCK', 3, 'SPELL_DAMAGE_PERCENT', 2.0, 20.0))
nodes['warlock_synergy_2'] = node('warlock_synergy_2', 'Eldritch Power', 'Per 3 Warlock nodes: +3 Max Mana (max 30)', [], synergy=syn('BRANCH_COUNT', 'WARLOCK', 3, 'MAX_MANA', 3.0, 30.0))
nodes['warlock_synergy_3'] = node('warlock_synergy_3', 'Hex Mastery', 'Per 3 Warlock nodes: +1% Status Effect Chance (max 10%)', [], synergy=syn('BRANCH_COUNT', 'WARLOCK', 3, 'STATUS_EFFECT_CHANCE', 1.0, 10.0))
nodes['warlock_synergy_4'] = node('warlock_synergy_4', 'Mind Siphon', 'Per 3 Warlock nodes: +1% Mana Leech (max 10%)', [], synergy=syn('BRANCH_COUNT', 'WARLOCK', 3, 'MANA_LEECH', 1.0, 10.0))
nodes['warlock_synergy_hub'] = node('warlock_synergy_hub', 'Nexus of Warlock', '+0.15% Spell Damage per (Water+Void+Lightning) attribute point',
    [], synergy=syn('ATTRIBUTE_SUM_SCALING', 'WARLOCK', 1, 'SPELL_DAMAGE_PERCENT', 0.15, 999.0))

# Warlock Keystones
nodes['warlock_keystone_1'] = node('warlock_keystone_1', 'Soul Siphon',
    'Spell kills fully restore Mana. Spell crits heal 8% of damage dealt. Overkill charges your next spell for +50% bonus.',
    [F('SPELL_KILL_MANA_RESTORE', 100.0), F('SPELL_CRIT_HEAL_PERCENT', 8.0), F('OVERKILL_CHARGE_ENABLED', 1.0)],
    drawbacks=[P('MAX_HEALTH_PERCENT', -20.0), F('ARMOR', -15.0), P('NON_SPELL_DAMAGE_PENALTY', -30.0)])
nodes['warlock_keystone_2'] = node('warlock_keystone_2', 'Arcane Overload',
    '25% chance for spells to echo, dealing 50% of the damage again as Void.',
    [F('SPELL_ECHO_CHANCE', 25.0), P('SPELL_DAMAGE_PERCENT', 10.0)],
    drawbacks=[P('MAX_MANA_PERCENT', -20.0), P('MANA_COST_PERCENT', 15.0)])

# =============================================================================
# LICH — The Undying Sorcerer (Water + Void + Earth)
# =============================================================================

nodes['lich_entry'] = node('lich_entry', 'Path of Lich', '+4% Spell Damage', [P('SPELL_DAMAGE_PERCENT', 4.0)])

# Cluster 1: Grasp (lich_grasp) — Lane 1 Inner
nodes['lich_grasp_1'] = node('lich_grasp_1', 'Grasp I', '+6% Spell Damage', [P('SPELL_DAMAGE_PERCENT', 6.0)])
nodes['lich_grasp_2'] = node('lich_grasp_2', 'Grasp II', '+5% DoT Damage', [P('DOT_DAMAGE_PERCENT', 5.0)])
nodes['lich_grasp_branch'] = node('lich_grasp_branch', 'Grasp Path', '+3% Spell Damage, +3% DoT Damage', [P('SPELL_DAMAGE_PERCENT', 3.0), P('DOT_DAMAGE_PERCENT', 3.0)])
nodes['lich_grasp_3'] = node('lich_grasp_3', 'Grasp III', '+5% Energy Shield', [P('ENERGY_SHIELD_PERCENT', 5.0)])
nodes['lich_grasp_4'] = node('lich_grasp_4', 'Grasp IV', '+5% Status Duration', [P('STATUS_EFFECT_DURATION', 5.0)])
nodes['lich_grasp_notable'] = node('lich_grasp_notable', "Death's Embrace", '+8% DoT Damage, +5% Spell Damage, +6 Void Penetration', [P('DOT_DAMAGE_PERCENT', 8.0), P('SPELL_DAMAGE_PERCENT', 5.0), F('VOID_PENETRATION', 6.0)])

# Cluster 2: Crypt (lich_crypt) — Lane 2 Inner
nodes['lich_crypt_1'] = node('lich_crypt_1', 'Crypt I', '+5% Max HP', [P('MAX_HEALTH_PERCENT', 5.0)])
nodes['lich_crypt_2'] = node('lich_crypt_2', 'Crypt II', '+6 Armor', [F('ARMOR', 6.0)])
nodes['lich_crypt_branch'] = node('lich_crypt_branch', 'Crypt Path', '+3% Max HP, +3 Armor', [P('MAX_HEALTH_PERCENT', 3.0), F('ARMOR', 3.0)])
nodes['lich_crypt_3'] = node('lich_crypt_3', 'Crypt III', '+0.3 HP Regen/s', [F('HEALTH_REGEN', 0.3)])
nodes['lich_crypt_4'] = node('lich_crypt_4', 'Crypt IV', '+1.5 Mana Regen/s', [F('MANA_REGEN', 1.5)])
nodes['lich_crypt_notable'] = node('lich_crypt_notable', 'Necrotic Armor', '+5% Max HP, +5 Armor, +5% Physical Resistance', [P('MAX_HEALTH_PERCENT', 5.0), F('ARMOR', 5.0), P('PHYSICAL_RESISTANCE', 5.0)])

# Cluster 3: Requiem (lich_requiem) — Lane 1 Outer
nodes['lich_requiem_1'] = node('lich_requiem_1', 'Requiem I', '+5% DoT Damage', [P('DOT_DAMAGE_PERCENT', 5.0)])
nodes['lich_requiem_2'] = node('lich_requiem_2', 'Requiem II', '+5% Energy Shield', [P('ENERGY_SHIELD_PERCENT', 5.0)])
nodes['lich_requiem_branch'] = node('lich_requiem_branch', 'Requiem Path', '+3% DoT Damage, +3% Energy Shield', [P('DOT_DAMAGE_PERCENT', 3.0), P('ENERGY_SHIELD_PERCENT', 3.0)])
nodes['lich_requiem_3'] = node('lich_requiem_3', 'Requiem III', '+5% Status Duration', [P('STATUS_EFFECT_DURATION', 5.0)])
nodes['lich_requiem_4'] = node('lich_requiem_4', 'Requiem IV', '+6% Spell Damage', [P('SPELL_DAMAGE_PERCENT', 6.0)])
nodes['lich_requiem_notable'] = node('lich_requiem_notable', 'Soul Anchor', '+1% Life Steal, +8% Energy Shield, +5% ES Regen', [F('LIFE_STEAL', 1.0), P('ENERGY_SHIELD_PERCENT', 8.0), P('ENERGY_SHIELD_REGEN_PERCENT', 5.0)])

# Cluster 4: Decay (lich_decay) — Lane 2 Outer
nodes['lich_decay_1'] = node('lich_decay_1', 'Decay I', '+5% Max HP', [P('MAX_HEALTH_PERCENT', 5.0)])
nodes['lich_decay_2'] = node('lich_decay_2', 'Decay II', '+5% DoT Damage', [P('DOT_DAMAGE_PERCENT', 5.0)])
nodes['lich_decay_branch'] = node('lich_decay_branch', 'Decay Path', '+3% Max HP, +3% DoT Damage', [P('MAX_HEALTH_PERCENT', 3.0), P('DOT_DAMAGE_PERCENT', 3.0)])
nodes['lich_decay_3'] = node('lich_decay_3', 'Decay III', '+1.5 Mana Regen/s', [F('MANA_REGEN', 1.5)])
nodes['lich_decay_4'] = node('lich_decay_4', 'Decay IV', '+0.3 HP Regen/s', [F('HEALTH_REGEN', 0.3)])
nodes['lich_decay_notable'] = node('lich_decay_notable', 'Withering Presence', '+8% DoT Damage, +5% Max HP, +5% Mana as Damage Buffer', [P('DOT_DAMAGE_PERCENT', 8.0), P('MAX_HEALTH_PERCENT', 5.0), P('MANA_AS_DAMAGE_BUFFER', 5.0)])

# Lich Synergies
nodes['lich_synergy_1'] = node('lich_synergy_1', 'Necrotic Bond', 'Per 3 Lich nodes: +2% Energy Shield (max 20%)', [], synergy=syn('BRANCH_COUNT', 'LICH', 3, 'ENERGY_SHIELD_PERCENT', 2.0, 20.0))
nodes['lich_synergy_2'] = node('lich_synergy_2', 'Plague Growth', 'Per 3 Lich nodes: +2% DoT Damage (max 20%)', [], synergy=syn('BRANCH_COUNT', 'LICH', 3, 'DOT_DAMAGE_PERCENT', 2.0, 20.0))
nodes['lich_synergy_3'] = node('lich_synergy_3', 'Soul Barrier', 'Per 3 Lich nodes: +1% Mana as Damage Buffer (max 10%)', [], synergy=syn('BRANCH_COUNT', 'LICH', 3, 'MANA_AS_DAMAGE_BUFFER', 1.0, 10.0))
nodes['lich_synergy_4'] = node('lich_synergy_4', 'Lingering Torment', 'Per 3 Lich nodes: +1% Status Duration (max 10%)', [], synergy=syn('BRANCH_COUNT', 'LICH', 3, 'STATUS_EFFECT_DURATION', 1.0, 10.0))
nodes['lich_synergy_hub'] = node('lich_synergy_hub', 'Nexus of Lich', '+0.2 Energy Shield per (Water+Void+Earth) attribute point',
    [], synergy=syn('ATTRIBUTE_SUM_SCALING', 'LICH', 1, 'ENERGY_SHIELD', 0.2, 999.0))

# Lich Keystones
nodes['lich_keystone_1'] = node('lich_keystone_1', 'Plague Resilience',
    'Each unique ailment you inflict grants +4% All Elemental Resistance (max 4 ailments = +16%).',
    [F('AILMENT_RESISTANCE_PER_TYPE', 4.0), P('MAX_HEALTH_PERCENT', 15.0)],
    drawbacks=[P('ATTACK_SPEED_PERCENT', -20.0), F('CRITICAL_CHANCE', -10.0)])
nodes['lich_keystone_2'] = node('lich_keystone_2', 'Undying Shell',
    'Your DoTs restore Energy Shield equal to 5% of each tick damage.',
    [F('SHIELD_REGEN_ON_DOT', 5.0), P('ENERGY_SHIELD_PERCENT', 25.0)],
    drawbacks=[F('EVASION', -25.0), P('ATTACK_SPEED_PERCENT', -10.0)])

# =============================================================================
# TEMPEST — The Battle Mage (Water + Wind + Lightning)
# =============================================================================

nodes['tempest_entry'] = node('tempest_entry', 'Path of Tempest', '+4% Spell Damage', [P('SPELL_DAMAGE_PERCENT', 4.0)])

# Cluster 1: Squall (tempest_squall) — Lane 1 Inner
nodes['tempest_squall_1'] = node('tempest_squall_1', 'Squall I', '+6% Spell Damage', [P('SPELL_DAMAGE_PERCENT', 6.0)])
nodes['tempest_squall_2'] = node('tempest_squall_2', 'Squall II', '+4% Attack Speed', [P('ATTACK_SPEED_PERCENT', 4.0)])
nodes['tempest_squall_branch'] = node('tempest_squall_branch', 'Squall Path', '+3% Spell Damage, +2% Attack Speed', [P('SPELL_DAMAGE_PERCENT', 3.0), P('ATTACK_SPEED_PERCENT', 2.0)])
nodes['tempest_squall_3'] = node('tempest_squall_3', 'Squall III', '+4% Crit Chance', [F('CRITICAL_CHANCE', 4.0)])
nodes['tempest_squall_4'] = node('tempest_squall_4', 'Squall IV', '+8 Max Mana', [F('MAX_MANA', 8.0)])
nodes['tempest_squall_notable'] = node('tempest_squall_notable', 'Swift Cast', '+5% Attack Speed, +5% Crit Chance, +5% Lightning Damage', [P('ATTACK_SPEED_PERCENT', 5.0), F('CRITICAL_CHANCE', 5.0), P('LIGHTNING_DAMAGE_PERCENT', 5.0)])

# Cluster 2: Tailwind (tempest_tailwind) — Lane 2 Inner
nodes['tempest_tailwind_1'] = node('tempest_tailwind_1', 'Tailwind I', '+3% Move Speed', [P('MOVEMENT_SPEED_PERCENT', 3.0)])
nodes['tempest_tailwind_2'] = node('tempest_tailwind_2', 'Tailwind II', '+5% Projectile Damage', [P('PROJECTILE_DAMAGE_PERCENT', 5.0)])
nodes['tempest_tailwind_branch'] = node('tempest_tailwind_branch', 'Tailwind Path', '+2% Move Speed, +3% Projectile Damage', [P('MOVEMENT_SPEED_PERCENT', 2.0), P('PROJECTILE_DAMAGE_PERCENT', 3.0)])
nodes['tempest_tailwind_3'] = node('tempest_tailwind_3', 'Tailwind III', '+8 Evasion', [F('EVASION', 8.0)])
nodes['tempest_tailwind_4'] = node('tempest_tailwind_4', 'Tailwind IV', '+5% Projectile Speed', [P('PROJECTILE_SPEED_PERCENT', 5.0)])
nodes['tempest_tailwind_notable'] = node('tempest_tailwind_notable', 'Arcane Gust', '+8% Spell Damage, +5% Projectile Damage, +5% Wind Damage', [P('SPELL_DAMAGE_PERCENT', 8.0), P('PROJECTILE_DAMAGE_PERCENT', 5.0), P('WIND_DAMAGE_PERCENT', 5.0)])

# Cluster 3: Cyclone (tempest_cyclone) — Lane 1 Outer
nodes['tempest_cyclone_1'] = node('tempest_cyclone_1', 'Cyclone I', '+4% Attack Speed', [P('ATTACK_SPEED_PERCENT', 4.0)])
nodes['tempest_cyclone_2'] = node('tempest_cyclone_2', 'Cyclone II', '+6% Spell Damage', [P('SPELL_DAMAGE_PERCENT', 6.0)])
nodes['tempest_cyclone_branch'] = node('tempest_cyclone_branch', 'Cyclone Path', '+2% Attack Speed, +3% Spell Damage', [P('ATTACK_SPEED_PERCENT', 2.0), P('SPELL_DAMAGE_PERCENT', 3.0)])
nodes['tempest_cyclone_3'] = node('tempest_cyclone_3', 'Cyclone III', '+3% Move Speed', [P('MOVEMENT_SPEED_PERCENT', 3.0)])
nodes['tempest_cyclone_4'] = node('tempest_cyclone_4', 'Cyclone IV', '+8 Max Mana', [F('MAX_MANA', 8.0)])
nodes['tempest_cyclone_notable'] = node('tempest_cyclone_notable', 'Windborne', '+8 Evasion, +5% Move Speed, +5% Shock Chance', [F('EVASION', 8.0), P('MOVEMENT_SPEED_PERCENT', 5.0), F('SHOCK_CHANCE', 5.0)])

# Cluster 4: Maelstrom (tempest_maelstrom) — Lane 2 Outer
nodes['tempest_maelstrom_1'] = node('tempest_maelstrom_1', 'Maelstrom I', '+5% Projectile Damage', [P('PROJECTILE_DAMAGE_PERCENT', 5.0)])
nodes['tempest_maelstrom_2'] = node('tempest_maelstrom_2', 'Maelstrom II', '+4% Crit Chance', [F('CRITICAL_CHANCE', 4.0)])
nodes['tempest_maelstrom_branch'] = node('tempest_maelstrom_branch', 'Maelstrom Path', '+3% Projectile Damage, +2% Crit Chance', [P('PROJECTILE_DAMAGE_PERCENT', 3.0), F('CRITICAL_CHANCE', 2.0)])
nodes['tempest_maelstrom_3'] = node('tempest_maelstrom_3', 'Maelstrom III', '+6% Spell Damage', [P('SPELL_DAMAGE_PERCENT', 6.0)])
nodes['tempest_maelstrom_4'] = node('tempest_maelstrom_4', 'Maelstrom IV', '+8 Evasion', [F('EVASION', 8.0)])
nodes['tempest_maelstrom_notable'] = node('tempest_maelstrom_notable', 'Eye of the Storm', '+8% Spell Damage, +5% Attack Speed, +5% All Damage', [P('SPELL_DAMAGE_PERCENT', 8.0), P('ATTACK_SPEED_PERCENT', 5.0), P('ALL_DAMAGE_PERCENT', 5.0)])

# Tempest Synergies
nodes['tempest_synergy_1'] = node('tempest_synergy_1', 'Storm Surge', 'Per 3 Tempest nodes: +1% All Damage (max 10%)', [], synergy=syn('BRANCH_COUNT', 'TEMPEST', 3, 'ALL_DAMAGE_PERCENT', 1.0, 10.0))
nodes['tempest_synergy_2'] = node('tempest_synergy_2', 'Static Buildup', 'Per 3 Tempest nodes: +1% Shock Chance (max 10%)', [], synergy=syn('BRANCH_COUNT', 'TEMPEST', 3, 'SHOCK_CHANCE', 1.0, 10.0))
nodes['tempest_synergy_3'] = node('tempest_synergy_3', 'Arcane Flow', 'Per 3 Tempest nodes: +1.5 Mana Regen/s (max 15)', [], synergy=syn('BRANCH_COUNT', 'TEMPEST', 3, 'MANA_REGEN', 1.5, 15.0))
nodes['tempest_synergy_4'] = node('tempest_synergy_4', 'Wind Runner', 'Per 3 Tempest nodes: +1% Move Speed (max 10%)', [], synergy=syn('BRANCH_COUNT', 'TEMPEST', 3, 'MOVEMENT_SPEED_PERCENT', 1.0, 10.0))
nodes['tempest_synergy_hub'] = node('tempest_synergy_hub', 'Nexus of Tempest', '+0.1% All Damage per (Water+Wind+Lightning) attribute point',
    [], synergy=syn('ATTRIBUTE_SUM_SCALING', 'TEMPEST', 1, 'ALL_DAMAGE_PERCENT', 0.1, 999.0))

# Tempest Keystones
nodes['tempest_keystone_1'] = node('tempest_keystone_1', 'Arcane Velocity',
    '50% of your Attack Speed bonus is converted to Spell Damage.',
    [F('ATK_SPEED_TO_SPELL_POWER', 50.0), P('ATTACK_SPEED_PERCENT', 10.0)],
    drawbacks=[F('ARMOR', -25.0), F('BLOCK_CHANCE', -10.0)])
nodes['tempest_keystone_2'] = node('tempest_keystone_2', 'Storm Runner',
    '40% of your Movement Speed bonus is converted to Spell Damage.',
    [F('SPEED_TO_SPELL_POWER', 40.0), P('MOVEMENT_SPEED_PERCENT', 10.0)],
    drawbacks=[P('MAX_HEALTH_PERCENT', -25.0)])

# =============================================================================
# SENTINEL — The Guardian (Water + Wind + Earth)
# =============================================================================

nodes['sentinel_entry'] = node('sentinel_entry', 'Path of Sentinel', '+3% Block Chance', [F('BLOCK_CHANCE', 3.0)])

# Cluster 1: Aegis (sentinel_aegis) — Lane 1 Inner
nodes['sentinel_aegis_1'] = node('sentinel_aegis_1', 'Aegis I', '+2% Block Chance', [F('BLOCK_CHANCE', 2.0)])
nodes['sentinel_aegis_2'] = node('sentinel_aegis_2', 'Aegis II', '+5% Energy Shield', [P('ENERGY_SHIELD_PERCENT', 5.0)])
nodes['sentinel_aegis_branch'] = node('sentinel_aegis_branch', 'Aegis Path', '+1% Block Chance, +3% Energy Shield', [F('BLOCK_CHANCE', 1.0), P('ENERGY_SHIELD_PERCENT', 3.0)])
nodes['sentinel_aegis_3'] = node('sentinel_aegis_3', 'Aegis III', '+5% Max HP', [P('MAX_HEALTH_PERCENT', 5.0)])
nodes['sentinel_aegis_4'] = node('sentinel_aegis_4', 'Aegis IV', '+6 Armor', [F('ARMOR', 6.0)])
nodes['sentinel_aegis_notable'] = node('sentinel_aegis_notable', "Guardian's Ward", '+5% Block Chance, +8 Energy Shield, +5% Block Damage Reduction', [F('BLOCK_CHANCE', 5.0), F('ENERGY_SHIELD', 8.0), P('BLOCK_DAMAGE_REDUCTION', 5.0)])

# Cluster 2: Vigilance (sentinel_vigilance) — Lane 2 Inner
nodes['sentinel_vigilance_1'] = node('sentinel_vigilance_1', 'Vigilance I', '+8 Evasion', [F('EVASION', 8.0)])
nodes['sentinel_vigilance_2'] = node('sentinel_vigilance_2', 'Vigilance II', '+5 Accuracy', [F('ACCURACY', 5.0)])
nodes['sentinel_vigilance_branch'] = node('sentinel_vigilance_branch', 'Vigilance Path', '+4 Evasion, +3 Accuracy', [F('EVASION', 4.0), F('ACCURACY', 3.0)])
nodes['sentinel_vigilance_3'] = node('sentinel_vigilance_3', 'Vigilance III', '+0.3 HP Regen/s', [F('HEALTH_REGEN', 0.3)])
nodes['sentinel_vigilance_4'] = node('sentinel_vigilance_4', 'Vigilance IV', '+1.5 Mana Regen/s', [F('MANA_REGEN', 1.5)])
nodes['sentinel_vigilance_notable'] = node('sentinel_vigilance_notable', 'Keen Senses', '+10 Evasion, +8 Accuracy, +3% Crit Nullify Chance', [F('EVASION', 10.0), F('ACCURACY', 8.0), P('CRIT_NULLIFY_CHANCE', 3.0)])

# Cluster 3: Restoration (sentinel_restoration) — Lane 1 Outer
nodes['sentinel_restoration_1'] = node('sentinel_restoration_1', 'Restoration I', '+5% Max HP', [P('MAX_HEALTH_PERCENT', 5.0)])
nodes['sentinel_restoration_2'] = node('sentinel_restoration_2', 'Restoration II', '+5% Energy Shield', [P('ENERGY_SHIELD_PERCENT', 5.0)])
nodes['sentinel_restoration_branch'] = node('sentinel_restoration_branch', 'Restoration Path', '+3% Max HP, +3% Energy Shield', [P('MAX_HEALTH_PERCENT', 3.0), P('ENERGY_SHIELD_PERCENT', 3.0)])
nodes['sentinel_restoration_3'] = node('sentinel_restoration_3', 'Restoration III', '+6 Armor', [F('ARMOR', 6.0)])
nodes['sentinel_restoration_4'] = node('sentinel_restoration_4', 'Restoration IV', '+1.5 Mana Regen/s', [F('MANA_REGEN', 1.5)])
nodes['sentinel_restoration_notable'] = node('sentinel_restoration_notable', 'Restorative Aura', '+3 HP Regen/s, +3 Mana Regen/s, +5% Health Recovery', [F('HEALTH_REGEN', 3.0), F('MANA_REGEN', 3.0), P('HEALTH_RECOVERY_PERCENT', 5.0)])

# Cluster 4: Haven (sentinel_haven) — Lane 2 Outer
nodes['sentinel_haven_1'] = node('sentinel_haven_1', 'Haven I', '+8 Evasion', [F('EVASION', 8.0)])
nodes['sentinel_haven_2'] = node('sentinel_haven_2', 'Haven II', '+2% Block Chance', [F('BLOCK_CHANCE', 2.0)])
nodes['sentinel_haven_branch'] = node('sentinel_haven_branch', 'Haven Path', '+4 Evasion, +1% Block Chance', [F('EVASION', 4.0), F('BLOCK_CHANCE', 1.0)])
nodes['sentinel_haven_3'] = node('sentinel_haven_3', 'Haven III', '+5% Max HP', [P('MAX_HEALTH_PERCENT', 5.0)])
nodes['sentinel_haven_4'] = node('sentinel_haven_4', 'Haven IV', '+5 Accuracy', [F('ACCURACY', 5.0)])
nodes['sentinel_haven_notable'] = node('sentinel_haven_notable', 'Stalwart Defense', '+5% Block Chance, +8 Evasion, +3% All Elemental Resistance', [F('BLOCK_CHANCE', 5.0), F('EVASION', 8.0), P('ALL_ELEMENTAL_RESISTANCE', 3.0)])

# Sentinel Synergies
nodes['sentinel_synergy_1'] = node('sentinel_synergy_1', "Guardian's Resolve", 'Per 3 Sentinel nodes: +1% Block Chance (max 10%)', [], synergy=syn('BRANCH_COUNT', 'SENTINEL', 3, 'BLOCK_CHANCE', 1.0, 10.0))
nodes['sentinel_synergy_2'] = node('sentinel_synergy_2', 'Watchful Eye', 'Per 3 Sentinel nodes: +5 Evasion (max 50)', [], synergy=syn('BRANCH_COUNT', 'SENTINEL', 3, 'EVASION', 5.0, 50.0))
nodes['sentinel_synergy_3'] = node('sentinel_synergy_3', 'Warding Aura', 'Per 3 Sentinel nodes: +1% All Elemental Resistance (max 10%)', [], synergy=syn('BRANCH_COUNT', 'SENTINEL', 3, 'ALL_ELEMENTAL_RESISTANCE', 1.0, 10.0))
nodes['sentinel_synergy_4'] = node('sentinel_synergy_4', 'Stalwart Focus', 'Per 3 Sentinel nodes: +1% Crit Nullify Chance (max 10%)', [], synergy=syn('BRANCH_COUNT', 'SENTINEL', 3, 'CRIT_NULLIFY_CHANCE', 1.0, 10.0))
nodes['sentinel_synergy_hub'] = node('sentinel_synergy_hub', 'Nexus of Sentinel', '+0.1% All Elemental Resistance per (Water+Wind+Earth) attribute point',
    [], synergy=syn('ATTRIBUTE_SUM_SCALING', 'SENTINEL', 1, 'ALL_ELEMENTAL_RESISTANCE', 0.1, 999.0))

# Sentinel Keystones
nodes['sentinel_keystone_1'] = node('sentinel_keystone_1', 'Fortress Aura',
    'Your Evasion rating also contributes to your Armor.',
    [F('EVASION_TO_ARMOR', 25.0), F('BLOCK_CHANCE', 15.0)],
    drawbacks=[M('DAMAGE_MULTIPLIER', -20.0)])
nodes['sentinel_keystone_2'] = node('sentinel_keystone_2', 'Adaptive Guard',
    'When hit by an ailment, gain +80% resistance to that element for 5s.',
    [F('IMMUNITY_ON_AILMENT', 80.0), P('HEALTH_REGEN_PERCENT', 10.0)],
    drawbacks=[P('FIRE_DAMAGE_PERCENT', -15.0), P('WATER_DAMAGE_PERCENT', -15.0), P('LIGHTNING_DAMAGE_PERCENT', -15.0)])

# =============================================================================
# VERIFICATION & OUTPUT
# =============================================================================

print(f"Total nodes generated: {len(nodes)}")
print(f"Expected: 485")

missing = [nid for nid in struct if nid not in nodes]
extra = [nid for nid in nodes if nid not in struct]

if missing:
    print(f"\nMISSING ({len(missing)}):")
    for m in sorted(missing):
        print(f"  {m}")
if extra:
    print(f"\nEXTRA ({len(extra)}):")
    for e in sorted(extra):
        print(f"  {e}")
if not missing and not extra:
    print("All 485 nodes match! Writing YAML...")

    # Build output
    output = OrderedDict()
    output['feedback'] = feedback
    output['nodes'] = nodes

    # Custom YAML dumper for clean output
    class CleanDumper(yaml.Dumper):
        pass

    def str_representer(dumper, data):
        if '\n' in data:
            return dumper.represent_scalar('tag:yaml.org,2002:str', data, style='|')
        if any(c in data for c in ':{}[]&*?|->!%@`'):
            return dumper.represent_scalar('tag:yaml.org,2002:str', data, style="'")
        return dumper.represent_scalar('tag:yaml.org,2002:str', data)

    def ordered_dict_representer(dumper, data):
        return dumper.represent_mapping('tag:yaml.org,2002:map', data.items())

    CleanDumper.add_representer(str, str_representer)
    CleanDumper.add_representer(OrderedDict, ordered_dict_representer)

    with open(OUTPUT_YAML, 'w', encoding='utf-8') as f:
        yaml.dump(output, f, Dumper=CleanDumper, default_flow_style=False, allow_unicode=False, width=200)

    print(f"Written to {OUTPUT_YAML}")
    # Count lines
    with open(OUTPUT_YAML, 'r', encoding='utf-8') as f:
        lines = sum(1 for _ in f)
    print(f"Output: {lines} lines")
