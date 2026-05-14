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
# FIRE — Power (Raw Damage Output)
# =============================================================================

nodes['fire_entry'] = node('fire_entry', 'Path of Fire',
    '+5% Physical Damage', [P('PHYSICAL_DAMAGE_PERCENT', 5.0)])

# Cluster 1: Ignition (fire_fury) — Lane 1 Inner — Phys Dmg + Crit Mult
nodes['fire_fury_1'] = node('fire_fury_1', 'Ignition I', '+6% Physical Damage', [P('PHYSICAL_DAMAGE_PERCENT', 6.0)])
nodes['fire_fury_2'] = node('fire_fury_2', 'Ignition II', '+6 Crit Multiplier', [F('CRITICAL_MULTIPLIER', 6.0)])
nodes['fire_fury_branch'] = node('fire_fury_branch', 'Searing Focus', '+4% Physical Damage, +4 Crit Multiplier', [P('PHYSICAL_DAMAGE_PERCENT', 4.0), F('CRITICAL_MULTIPLIER', 4.0)])
nodes['fire_fury_3'] = node('fire_fury_3', 'Ignition III', '+6% Physical Damage', [P('PHYSICAL_DAMAGE_PERCENT', 6.0)])
nodes['fire_fury_4'] = node('fire_fury_4', 'Critical Edge', '+6 Crit Multiplier', [F('CRITICAL_MULTIPLIER', 6.0)])
nodes['fire_fury_notable'] = node('fire_fury_notable', 'Savage Strikes', '+10% Physical Damage, +10 Crit Multiplier, +5% All Damage', [P('PHYSICAL_DAMAGE_PERCENT', 10.0), F('CRITICAL_MULTIPLIER', 10.0), P('ALL_DAMAGE_PERCENT', 5.0)])

# Cluster 2: Pyre (fire_inferno) — Lane 2 Inner — All Dmg + Atk Speed
nodes['fire_inferno_1'] = node('fire_inferno_1', 'Wrath I', '+5% All Damage', [P('ALL_DAMAGE_PERCENT', 5.0)])
nodes['fire_inferno_2'] = node('fire_inferno_2', 'Wrath II', '+4% Attack Speed', [P('ATTACK_SPEED_PERCENT', 4.0)])
nodes['fire_inferno_branch'] = node('fire_inferno_branch', 'Furious Path', '+3% All Damage, +3% Attack Speed', [P('ALL_DAMAGE_PERCENT', 3.0), P('ATTACK_SPEED_PERCENT', 3.0)])
nodes['fire_inferno_3'] = node('fire_inferno_3', 'Wrath III', '+5% All Damage', [P('ALL_DAMAGE_PERCENT', 5.0)])
nodes['fire_inferno_4'] = node('fire_inferno_4', 'Swift Fury', '+4% Attack Speed', [P('ATTACK_SPEED_PERCENT', 4.0)])
nodes['fire_inferno_notable'] = node('fire_inferno_notable', 'Relentless', '+8% All Damage, +6% Attack Speed, +8 Crit Multiplier', [P('ALL_DAMAGE_PERCENT', 8.0), P('ATTACK_SPEED_PERCENT', 6.0), F('CRITICAL_MULTIPLIER', 8.0)])

# Cluster 3: Eruption (fire_bloodlust) — Lane 1 Outer — Charged Atk + Phys Dmg
nodes['fire_bloodlust_1'] = node('fire_bloodlust_1', 'Eruption I', '+6% Charged Attack Damage', [P('CHARGED_ATTACK_DAMAGE_PERCENT', 6.0)])
nodes['fire_bloodlust_2'] = node('fire_bloodlust_2', 'Eruption II', '+6% Physical Damage', [P('PHYSICAL_DAMAGE_PERCENT', 6.0)])
nodes['fire_bloodlust_branch'] = node('fire_bloodlust_branch', 'Volcanic Path', '+4% Charged Attack Damage, +4% Physical Damage', [P('CHARGED_ATTACK_DAMAGE_PERCENT', 4.0), P('PHYSICAL_DAMAGE_PERCENT', 4.0)])
nodes['fire_bloodlust_3'] = node('fire_bloodlust_3', 'Eruption III', '+6% Charged Attack Damage', [P('CHARGED_ATTACK_DAMAGE_PERCENT', 6.0)])
nodes['fire_bloodlust_4'] = node('fire_bloodlust_4', 'Power Surge', '+6% Physical Damage', [P('PHYSICAL_DAMAGE_PERCENT', 6.0)])
nodes['fire_bloodlust_notable'] = node('fire_bloodlust_notable', 'Devastation', '+8% Charged Attack Damage, +10 Crit Multiplier, +6% All Damage', [P('CHARGED_ATTACK_DAMAGE_PERCENT', 8.0), F('CRITICAL_MULTIPLIER', 10.0), P('ALL_DAMAGE_PERCENT', 6.0)])

# Cluster 4: Inferno (fire_berserker) — Lane 2 Outer — Crit Mult + Phys Dmg
nodes['fire_berserker_1'] = node('fire_berserker_1', 'Inferno I', '+6 Crit Multiplier', [F('CRITICAL_MULTIPLIER', 6.0)])
nodes['fire_berserker_2'] = node('fire_berserker_2', 'Inferno II', '+5% Physical Damage', [P('PHYSICAL_DAMAGE_PERCENT', 5.0)])
nodes['fire_berserker_branch'] = node('fire_berserker_branch', 'Inferno Path', '+4 Crit Multiplier, +3% Physical Damage', [F('CRITICAL_MULTIPLIER', 4.0), P('PHYSICAL_DAMAGE_PERCENT', 3.0)])
nodes['fire_berserker_3'] = node('fire_berserker_3', 'Ruthless I', '+6 Crit Multiplier', [F('CRITICAL_MULTIPLIER', 6.0)])
nodes['fire_berserker_4'] = node('fire_berserker_4', 'Ruthless II', '+5% Physical Damage', [P('PHYSICAL_DAMAGE_PERCENT', 5.0)])
nodes['fire_berserker_notable'] = node('fire_berserker_notable', 'Ruthless', '+8 Crit Multiplier, +8% All Damage, +6% Physical Damage', [F('CRITICAL_MULTIPLIER', 8.0), P('ALL_DAMAGE_PERCENT', 8.0), P('PHYSICAL_DAMAGE_PERCENT', 6.0)])

# Fire Synergies
nodes['fire_synergy_1'] = node('fire_synergy_1', 'Strength in Numbers', 'Per 3 Fire nodes: +2% Physical Damage (max 20%)', [], synergy=syn('ELEMENTAL_COUNT', 'FIRE', 3, 'PHYSICAL_DAMAGE_PERCENT', 2.0, 20.0))
nodes['fire_synergy_2'] = node('fire_synergy_2', 'Raw Power', 'Per 3 Fire nodes: +1% All Damage (max 10%)', [], synergy=syn('ELEMENTAL_COUNT', 'FIRE', 3, 'ALL_DAMAGE_PERCENT', 1.0, 10.0))
nodes['fire_synergy_3'] = node('fire_synergy_3', 'Keen Edge', 'Per 3 Fire nodes: +2.4 Crit Multiplier (max 24)', [], synergy=syn('ELEMENTAL_COUNT', 'FIRE', 3, 'CRITICAL_MULTIPLIER', 2.4, 24.0))
nodes['fire_synergy_4'] = node('fire_synergy_4', 'Charged Momentum', 'Per 3 Fire nodes: +1.5% Charged Attack Damage (max 15%)', [], synergy=syn('ELEMENTAL_COUNT', 'FIRE', 3, 'CHARGED_ATTACK_DAMAGE_PERCENT', 1.5, 15.0))

# Fire Hub
nodes['fire_synergy_hub'] = node('fire_synergy_hub', 'Heart of Fire', '+5% All Damage, +5% Physical Damage', [P('ALL_DAMAGE_PERCENT', 5.0), P('PHYSICAL_DAMAGE_PERCENT', 5.0)])

# Fire Keystones
nodes['fire_keystone_1'] = node('fire_keystone_1', 'Unleashed',
    '+20% All Damage, +15 Crit Multiplier. -15% Max HP, -10 Armor.',
    [P('ALL_DAMAGE_PERCENT', 20.0), F('CRITICAL_MULTIPLIER', 15.0)],
    drawbacks=[P('MAX_HEALTH_PERCENT', -15.0), F('ARMOR', -10.0)])
nodes['fire_keystone_2'] = node('fire_keystone_2', "Berserker's Rage",
    '+1% Damage per 2% missing HP. Attacks cost 3% of current HP. Life steal capped at 50% HP.',
    [F('MISSING_HP_DAMAGE_SCALING', 0.5), F('ATTACK_SELF_DAMAGE_PERCENT', 3.0)],
    drawbacks=[P('MAX_HEALTH_PERCENT', -15.0)])

# Fire Bridges — universal parent stats only
nodes['bridge_fire_lightning_1'] = node('bridge_fire_lightning_1', 'Charged Strike I', '+4% Physical Damage, +3% Attack Speed', [P('PHYSICAL_DAMAGE_PERCENT', 4.0), P('ATTACK_SPEED_PERCENT', 3.0)])
nodes['bridge_fire_lightning_2'] = node('bridge_fire_lightning_2', 'Thundering Blows', '+5% All Damage, +5% Attack Speed, +5 Crit Multiplier', [P('ALL_DAMAGE_PERCENT', 5.0), P('ATTACK_SPEED_PERCENT', 5.0), F('CRITICAL_MULTIPLIER', 5.0)])
nodes['bridge_fire_lightning_3'] = node('bridge_fire_lightning_3', 'Charged Strike II', '+4 Crit Multiplier, +4% Crit Chance', [F('CRITICAL_MULTIPLIER', 4.0), F('CRITICAL_CHANCE', 4.0)])

nodes['bridge_fire_void_1'] = node('bridge_fire_void_1', 'Burning Void I', '+5% Physical Damage, +3% All Damage, +0.3% Life Steal', [P('PHYSICAL_DAMAGE_PERCENT', 5.0), P('ALL_DAMAGE_PERCENT', 3.0), F('LIFE_STEAL', 0.3)])
nodes['bridge_fire_void_2'] = node('bridge_fire_void_2', 'Dark Flame', '+7% Physical Damage, +7% All Damage, +0.5% Life Steal', [P('PHYSICAL_DAMAGE_PERCENT', 7.0), P('ALL_DAMAGE_PERCENT', 7.0), F('LIFE_STEAL', 0.5)])
nodes['bridge_fire_void_3'] = node('bridge_fire_void_3', 'Burning Void II', '+4 Crit Multiplier, +4% All Damage', [F('CRITICAL_MULTIPLIER', 4.0), P('ALL_DAMAGE_PERCENT', 4.0)])

nodes['bridge_fire_wind_1'] = node('bridge_fire_wind_1', 'Wind Strike I', '+4% Physical Damage, +4% Projectile Damage', [P('PHYSICAL_DAMAGE_PERCENT', 4.0), P('PROJECTILE_DAMAGE_PERCENT', 4.0)])
nodes['bridge_fire_wind_2'] = node('bridge_fire_wind_2', 'Power Shot', '+5% Charged Attack Damage, +5% Projectile Damage, +5 Crit Multiplier', [P('CHARGED_ATTACK_DAMAGE_PERCENT', 5.0), P('PROJECTILE_DAMAGE_PERCENT', 5.0), F('CRITICAL_MULTIPLIER', 5.0)])
nodes['bridge_fire_wind_3'] = node('bridge_fire_wind_3', 'Wind Strike II', '+4% Charged Attack Damage, +5 Accuracy', [P('CHARGED_ATTACK_DAMAGE_PERCENT', 4.0), F('ACCURACY', 5.0)])

nodes['bridge_earth_fire_1'] = node('bridge_earth_fire_1', 'Forged Earth I', '+4% Physical Damage, +4% Max HP', [P('PHYSICAL_DAMAGE_PERCENT', 4.0), P('MAX_HEALTH_PERCENT', 4.0)])
nodes['bridge_earth_fire_2'] = node('bridge_earth_fire_2', 'Iron Fury', '+5% Physical Damage, +5% Max HP, +5 Armor', [P('PHYSICAL_DAMAGE_PERCENT', 5.0), P('MAX_HEALTH_PERCENT', 5.0), F('ARMOR', 5.0)])
nodes['bridge_earth_fire_3'] = node('bridge_earth_fire_3', 'Forged Earth II', '+4 Crit Multiplier, +5 Armor', [F('CRITICAL_MULTIPLIER', 4.0), F('ARMOR', 5.0)])

# =============================================================================
# WATER — Mind (Spellcasting & Resources)
# =============================================================================

nodes['water_entry'] = node('water_entry', 'Path of Water', '+5% Spell Damage', [P('SPELL_DAMAGE_PERCENT', 5.0)])

# Cluster 1: Torrent (water_frostbite) — Lane 1 Inner — Spell Dmg + Max Mana
nodes['water_frostbite_1'] = node('water_frostbite_1', 'Torrent I', '+6% Spell Damage', [P('SPELL_DAMAGE_PERCENT', 6.0)])
nodes['water_frostbite_2'] = node('water_frostbite_2', 'Torrent II', '+8 Max Mana', [F('MAX_MANA', 8.0)])
nodes['water_frostbite_branch'] = node('water_frostbite_branch', 'Arcane Flow', '+4% Spell Damage, +5 Max Mana', [P('SPELL_DAMAGE_PERCENT', 4.0), F('MAX_MANA', 5.0)])
nodes['water_frostbite_3'] = node('water_frostbite_3', 'Torrent III', '+6% Spell Damage', [P('SPELL_DAMAGE_PERCENT', 6.0)])
nodes['water_frostbite_4'] = node('water_frostbite_4', 'Mana Well', '+8 Max Mana', [F('MAX_MANA', 8.0)])
nodes['water_frostbite_notable'] = node('water_frostbite_notable', 'Arcane Surge', '+10% Spell Damage, +10 Max Mana, +3 Mana Regen/s', [P('SPELL_DAMAGE_PERCENT', 10.0), F('MAX_MANA', 10.0), F('MANA_REGEN', 3.0)])

# Cluster 2: Glacier (water_precision) — Lane 2 Inner — Energy Shield + Mana Regen
nodes['water_precision_1'] = node('water_precision_1', 'Glacier I', '+6% Energy Shield', [P('ENERGY_SHIELD_PERCENT', 6.0)])
nodes['water_precision_2'] = node('water_precision_2', 'Glacier II', '+2 Mana Regen/s', [F('MANA_REGEN', 2.0)])
nodes['water_precision_branch'] = node('water_precision_branch', 'Arcane Shield', '+4% Energy Shield, +1 Mana Regen/s', [P('ENERGY_SHIELD_PERCENT', 4.0), F('MANA_REGEN', 1.0)])
nodes['water_precision_3'] = node('water_precision_3', 'Glacier III', '+6% Energy Shield', [P('ENERGY_SHIELD_PERCENT', 6.0)])
nodes['water_precision_4'] = node('water_precision_4', 'Barrier Focus', '+2 Mana Regen/s', [F('MANA_REGEN', 2.0)])
nodes['water_precision_notable'] = node('water_precision_notable', 'Crystal Barrier', '+10% Energy Shield, +4 Mana Regen/s, +4% Damage Reduction', [P('ENERGY_SHIELD_PERCENT', 10.0), F('MANA_REGEN', 4.0), P('DAMAGE_TAKEN_PERCENT', -4.0)])

# Cluster 3: Depths (water_evasion) — Lane 1 Outer — Spell Dmg + Max Mana
nodes['water_evasion_1'] = node('water_evasion_1', 'Depths I', '+6% Spell Damage', [P('SPELL_DAMAGE_PERCENT', 6.0)])
nodes['water_evasion_2'] = node('water_evasion_2', 'Depths II', '+8 Max Mana', [F('MAX_MANA', 8.0)])
nodes['water_evasion_branch'] = node('water_evasion_branch', 'Deep Current', '+4% Spell Damage, +5 Max Mana', [P('SPELL_DAMAGE_PERCENT', 4.0), F('MAX_MANA', 5.0)])
nodes['water_evasion_3'] = node('water_evasion_3', 'Depths III', '+6% Spell Damage', [P('SPELL_DAMAGE_PERCENT', 6.0)])
nodes['water_evasion_4'] = node('water_evasion_4', 'Arcane Depths', '+8 Max Mana', [F('MAX_MANA', 8.0)])
nodes['water_evasion_notable'] = node('water_evasion_notable', 'Deep Reserves', '+8% Spell Damage, +10 Max Mana, +4 Mana Regen/s', [P('SPELL_DAMAGE_PERCENT', 8.0), F('MAX_MANA', 10.0), F('MANA_REGEN', 4.0)])

# Cluster 4: Confluence (water_shatter) — Lane 2 Outer — ES Regen + Energy Shield
nodes['water_shatter_1'] = node('water_shatter_1', 'Confluence I', '+2 ES Regen/s', [F('ENERGY_SHIELD_REGEN', 2.0)])
nodes['water_shatter_2'] = node('water_shatter_2', 'Confluence II', '+6% Energy Shield', [P('ENERGY_SHIELD_PERCENT', 6.0)])
nodes['water_shatter_branch'] = node('water_shatter_branch', 'Confluent Flow', '+1.5 ES Regen/s, +4% Energy Shield', [F('ENERGY_SHIELD_REGEN', 1.5), P('ENERGY_SHIELD_PERCENT', 4.0)])
nodes['water_shatter_3'] = node('water_shatter_3', 'Shield Mastery', '+2 ES Regen/s', [F('ENERGY_SHIELD_REGEN', 2.0)])
nodes['water_shatter_4'] = node('water_shatter_4', 'Shield Surge', '+6% Energy Shield', [P('ENERGY_SHIELD_PERCENT', 6.0)])
nodes['water_shatter_notable'] = node('water_shatter_notable', 'Arcane Mastery', '+8% Spell Damage, +8% Energy Shield, +5 ES Regen/s', [P('SPELL_DAMAGE_PERCENT', 8.0), P('ENERGY_SHIELD_PERCENT', 8.0), F('ENERGY_SHIELD_REGEN', 5.0)])

# Water Synergies
nodes['water_synergy_1'] = node('water_synergy_1', 'Arcane Accumulation', 'Per 3 Water nodes: +2% Spell Damage (max 20%)', [], synergy=syn('ELEMENTAL_COUNT', 'WATER', 3, 'SPELL_DAMAGE_PERCENT', 2.0, 20.0))
nodes['water_synergy_2'] = node('water_synergy_2', 'Barrier Growth', 'Per 3 Water nodes: +2% Energy Shield (max 20%)', [], synergy=syn('ELEMENTAL_COUNT', 'WATER', 3, 'ENERGY_SHIELD_PERCENT', 2.0, 20.0))
nodes['water_synergy_3'] = node('water_synergy_3', 'Mana Reserves', 'Per 3 Water nodes: +2 Max Mana (max 20)', [], synergy=syn('ELEMENTAL_COUNT', 'WATER', 3, 'MAX_MANA', 2.0, 20.0))
nodes['water_synergy_4'] = node('water_synergy_4', 'Mana Flow', 'Per 3 Water nodes: +0.5 Mana Regen/s (max 5)', [], synergy=syn('ELEMENTAL_COUNT', 'WATER', 3, 'MANA_REGEN', 0.5, 5.0))
nodes['water_synergy_hub'] = node('water_synergy_hub', 'Heart of Water', '+5% Spell Damage, +3 Mana Regen/s', [P('SPELL_DAMAGE_PERCENT', 5.0), F('MANA_REGEN', 3.0)])

# Water Keystones
nodes['water_keystone_1'] = node('water_keystone_1', 'Arcane Reservoir',
    '+15% Spell Damage, +25% Max Mana, +5 Mana Regen/s. Spells cost 20% more Mana. -10% Max HP.',
    [P('SPELL_DAMAGE_PERCENT', 15.0), P('MAX_MANA_PERCENT', 25.0), F('MANA_REGEN', 5.0)],
    drawbacks=[P('MANA_COST_PERCENT', 20.0), P('MAX_HEALTH_PERCENT', -10.0)])
nodes['water_keystone_2'] = node('water_keystone_2', 'Mind Over Matter',
    '15% of damage taken is absorbed by Mana instead of HP. +15% Max Mana, +3 Mana Regen/s. -15% Max HP.',
    [F('MANA_AS_DAMAGE_BUFFER', 15.0), P('MAX_MANA_PERCENT', 15.0), F('MANA_REGEN', 3.0)],
    drawbacks=[P('MAX_HEALTH_PERCENT', -15.0)])

# Water Bridges — universal parent stats only
nodes['bridge_water_void_1'] = node('bridge_water_void_1', 'Dark Waters I', '+5% Spell Damage, +3% Energy Shield, +0.3% Life Steal', [P('SPELL_DAMAGE_PERCENT', 5.0), P('ENERGY_SHIELD_PERCENT', 3.0), F('LIFE_STEAL', 0.3)])
nodes['bridge_water_void_2'] = node('bridge_water_void_2', 'Soul Drain', '+7% Spell Damage, +7% All Damage, +0.5% Life Steal', [P('SPELL_DAMAGE_PERCENT', 7.0), P('ALL_DAMAGE_PERCENT', 7.0), F('LIFE_STEAL', 0.5)])
nodes['bridge_water_void_3'] = node('bridge_water_void_3', 'Dark Waters II', '+4% Energy Shield, +4% All Damage', [P('ENERGY_SHIELD_PERCENT', 4.0), P('ALL_DAMAGE_PERCENT', 4.0)])
nodes['bridge_water_wind_1'] = node('bridge_water_wind_1', 'Arcane Gale I', '+4% Spell Damage, +4% Projectile Damage', [P('SPELL_DAMAGE_PERCENT', 4.0), P('PROJECTILE_DAMAGE_PERCENT', 4.0)])
nodes['bridge_water_wind_2'] = node('bridge_water_wind_2', 'Spell Archer', '+5% Spell Damage, +5% Projectile Damage, +5 Accuracy', [P('SPELL_DAMAGE_PERCENT', 5.0), P('PROJECTILE_DAMAGE_PERCENT', 5.0), F('ACCURACY', 5.0)])
nodes['bridge_water_wind_3'] = node('bridge_water_wind_3', 'Arcane Gale II', '+6 Max Mana, +5 Evasion', [F('MAX_MANA', 6.0), F('EVASION', 5.0)])
nodes['bridge_water_earth_1'] = node('bridge_water_earth_1', 'Sacred Ground I', '+4% Spell Damage, +4% Max HP', [P('SPELL_DAMAGE_PERCENT', 4.0), P('MAX_HEALTH_PERCENT', 4.0)])
nodes['bridge_water_earth_2'] = node('bridge_water_earth_2', 'Arcane Bulwark', '+5% Energy Shield, +5% Max HP, +5 Armor', [P('ENERGY_SHIELD_PERCENT', 5.0), P('MAX_HEALTH_PERCENT', 5.0), F('ARMOR', 5.0)])
nodes['bridge_water_earth_3'] = node('bridge_water_earth_3', 'Sacred Ground II', '+4% Energy Shield, +5 Armor', [P('ENERGY_SHIELD_PERCENT', 4.0), F('ARMOR', 5.0)])
nodes['bridge_lightning_water_1'] = node('bridge_lightning_water_1', 'Storm Surge I', '+4% Spell Damage, +3% Attack Speed', [P('SPELL_DAMAGE_PERCENT', 4.0), P('ATTACK_SPEED_PERCENT', 3.0)])
nodes['bridge_lightning_water_2'] = node('bridge_lightning_water_2', 'Spell Storm', '+5% Spell Damage, +5% Attack Speed, +5% Crit Chance', [P('SPELL_DAMAGE_PERCENT', 5.0), P('ATTACK_SPEED_PERCENT', 5.0), F('CRITICAL_CHANCE', 5.0)])
nodes['bridge_lightning_water_3'] = node('bridge_lightning_water_3', 'Storm Surge II', '+6 Max Mana, +4% Crit Chance', [F('MAX_MANA', 6.0), F('CRITICAL_CHANCE', 4.0)])

# =============================================================================
# LIGHTNING — Speed (Tempo & Precision)
# =============================================================================

nodes['lightning_entry'] = node('lightning_entry', 'Path of Lightning', '+4% Attack Speed', [P('ATTACK_SPEED_PERCENT', 4.0)])

# Cluster 1: Surge (lightning_storm) — Lane 1 Inner — Atk Speed + Crit Chance
nodes['lightning_storm_1'] = node('lightning_storm_1', 'Surge I', '+4% Attack Speed', [P('ATTACK_SPEED_PERCENT', 4.0)])
nodes['lightning_storm_2'] = node('lightning_storm_2', 'Surge II', '+5% Crit Chance', [F('CRITICAL_CHANCE', 5.0)])
nodes['lightning_storm_branch'] = node('lightning_storm_branch', 'Storm Path', '+3% Attack Speed, +3% Crit Chance', [P('ATTACK_SPEED_PERCENT', 3.0), F('CRITICAL_CHANCE', 3.0)])
nodes['lightning_storm_3'] = node('lightning_storm_3', 'Surge III', '+4% Attack Speed', [P('ATTACK_SPEED_PERCENT', 4.0)])
nodes['lightning_storm_4'] = node('lightning_storm_4', 'Precision Strike', '+5% Crit Chance', [F('CRITICAL_CHANCE', 5.0)])
nodes['lightning_storm_notable'] = node('lightning_storm_notable', 'Lightning Reflexes', '+8% Attack Speed, +8% Crit Chance, +3% Dodge Chance', [P('ATTACK_SPEED_PERCENT', 8.0), F('CRITICAL_CHANCE', 8.0), P('DODGE_CHANCE', 3.0)])

# Cluster 2: Tempest (lightning_velocity) — Lane 2 Inner — Move Speed + Stamina Regen
nodes['lightning_velocity_1'] = node('lightning_velocity_1', 'Tempest I', '+3% Move Speed', [P('MOVEMENT_SPEED_PERCENT', 3.0)])
nodes['lightning_velocity_2'] = node('lightning_velocity_2', 'Tempest II', '+1.0 Stamina Regen/s', [F('STAMINA_REGEN', 1.0)])
nodes['lightning_velocity_branch'] = node('lightning_velocity_branch', 'Storm Wind', '+2% Move Speed, +0.5 Stamina Regen/s', [P('MOVEMENT_SPEED_PERCENT', 2.0), F('STAMINA_REGEN', 0.5)])
nodes['lightning_velocity_3'] = node('lightning_velocity_3', 'Tempest III', '+3% Move Speed', [P('MOVEMENT_SPEED_PERCENT', 3.0)])
nodes['lightning_velocity_4'] = node('lightning_velocity_4', 'Swift Steps', '+1.0 Stamina Regen/s', [F('STAMINA_REGEN', 1.0)])
nodes['lightning_velocity_notable'] = node('lightning_velocity_notable', 'Swift Advance', '+8% Move Speed, +6% Attack Speed, +3.0 Stamina Regen/s, +5% Max Stamina', [P('MOVEMENT_SPEED_PERCENT', 8.0), P('ATTACK_SPEED_PERCENT', 6.0), F('STAMINA_REGEN', 3.0), P('MAX_STAMINA_PERCENT', 5.0)])

# Cluster 3: Voltage (lightning_chain) — Lane 1 Outer — Crit Chance + Atk Speed
nodes['lightning_chain_1'] = node('lightning_chain_1', 'Voltage I', '+5% Crit Chance', [F('CRITICAL_CHANCE', 5.0)])
nodes['lightning_chain_2'] = node('lightning_chain_2', 'Voltage II', '+4% Attack Speed', [P('ATTACK_SPEED_PERCENT', 4.0)])
nodes['lightning_chain_branch'] = node('lightning_chain_branch', 'Voltage Path', '+3% Crit Chance, +3% Attack Speed', [F('CRITICAL_CHANCE', 3.0), P('ATTACK_SPEED_PERCENT', 3.0)])
nodes['lightning_chain_3'] = node('lightning_chain_3', 'Voltage III', '+5% Crit Chance', [F('CRITICAL_CHANCE', 5.0)])
nodes['lightning_chain_4'] = node('lightning_chain_4', 'Quick Strike', '+4% Attack Speed', [P('ATTACK_SPEED_PERCENT', 4.0)])
nodes['lightning_chain_notable'] = node('lightning_chain_notable', 'Precision Strikes', '+8% Crit Chance, +8% Attack Speed, +4% Dodge Chance', [F('CRITICAL_CHANCE', 8.0), P('ATTACK_SPEED_PERCENT', 8.0), P('DODGE_CHANCE', 4.0)])

# Cluster 4: Stormwind (lightning_overcharge) — Lane 2 Outer — Stamina Regen + Move Speed
nodes['lightning_overcharge_1'] = node('lightning_overcharge_1', 'Stormwind I', '+1.0 Stamina Regen/s', [F('STAMINA_REGEN', 1.0)])
nodes['lightning_overcharge_2'] = node('lightning_overcharge_2', 'Stormwind II', '+3% Move Speed', [P('MOVEMENT_SPEED_PERCENT', 3.0)])
nodes['lightning_overcharge_branch'] = node('lightning_overcharge_branch', 'Stormwind Path', '+0.5 Stamina Regen/s, +2% Move Speed', [F('STAMINA_REGEN', 0.5), P('MOVEMENT_SPEED_PERCENT', 2.0)])
nodes['lightning_overcharge_3'] = node('lightning_overcharge_3', 'Quick Recovery', '+5% Stamina Recovery Speed', [P('STAMINA_REGEN_START_DELAY', 5.0)])
nodes['lightning_overcharge_4'] = node('lightning_overcharge_4', 'Lightning Steps', '+3% Move Speed', [P('MOVEMENT_SPEED_PERCENT', 3.0)])
nodes['lightning_overcharge_notable'] = node('lightning_overcharge_notable', 'Storm Tempo', '+6% Move Speed, +5% Attack Speed, +3.0 Stamina Regen/s, +5% Stamina Recovery Speed', [P('MOVEMENT_SPEED_PERCENT', 6.0), P('ATTACK_SPEED_PERCENT', 5.0), F('STAMINA_REGEN', 3.0), P('STAMINA_REGEN_START_DELAY', 5.0)])

# Lightning Synergies
nodes['lightning_synergy_1'] = node('lightning_synergy_1', 'Storm Buildup', 'Per 3 Lightning nodes: +1% Attack Speed (max 10%)', [], synergy=syn('ELEMENTAL_COUNT', 'LIGHTNING', 3, 'ATTACK_SPEED_PERCENT', 1.0, 10.0))
nodes['lightning_synergy_2'] = node('lightning_synergy_2', 'Precision', 'Per 3 Lightning nodes: +1.5% Crit Chance (max 15%)', [], synergy=syn('ELEMENTAL_COUNT', 'LIGHTNING', 3, 'CRITICAL_CHANCE', 1.5, 15.0))
nodes['lightning_synergy_3'] = node('lightning_synergy_3', 'Swiftness', 'Per 3 Lightning nodes: +1.6% Move Speed (max 16%)', [], synergy=syn('ELEMENTAL_COUNT', 'LIGHTNING', 3, 'MOVEMENT_SPEED_PERCENT', 1.6, 16.0))
nodes['lightning_synergy_4'] = node('lightning_synergy_4', 'Endurance', 'Per 3 Lightning nodes: +1.5 Stamina Regen/s (max 15)', [], synergy=syn('ELEMENTAL_COUNT', 'LIGHTNING', 3, 'STAMINA_REGEN', 1.5, 15.0))
nodes['lightning_synergy_hub'] = node('lightning_synergy_hub', 'Heart of Lightning', '+5% Attack Speed, +3% Crit Chance', [P('ATTACK_SPEED_PERCENT', 5.0), F('CRITICAL_CHANCE', 3.0)])

# Lightning Keystones
nodes['lightning_keystone_1'] = node('lightning_keystone_1', 'Velocity',
    '+15% Attack Speed, +10% Crit Chance, +5% Move Speed. -15% Max HP, -10 Armor.',
    [P('ATTACK_SPEED_PERCENT', 15.0), F('CRITICAL_CHANCE', 10.0), P('MOVEMENT_SPEED_PERCENT', 5.0)],
    drawbacks=[P('MAX_HEALTH_PERCENT', -15.0), F('ARMOR', -10.0)])
nodes['lightning_keystone_2'] = node('lightning_keystone_2', 'Chain Momentum',
    'On Critical Strike: +5% Attack Speed, +3% Move Speed for 3s. Stacks up to 5 times.',
    [F('CRITICAL_CHANCE', 5.0)],
    conditional=cond('ON_CRIT', 3.0, 'STACK', [{'stat': 'ATTACK_SPEED_PERCENT', 'value': 5.0, 'modifierType': 'PERCENT'}, {'stat': 'MOVEMENT_SPEED_PERCENT', 'value': 3.0, 'modifierType': 'PERCENT'}], max_s=5),
    drawbacks=[F('ARMOR', -10.0), P('MAX_HEALTH_PERCENT', -5.0)])

# Lightning Bridges — universal parent stats only
nodes['bridge_lightning_void_1'] = node('bridge_lightning_void_1', 'Storm Void I', '+3% Attack Speed, +4% All Damage', [P('ATTACK_SPEED_PERCENT', 3.0), P('ALL_DAMAGE_PERCENT', 4.0)])
nodes['bridge_lightning_void_2'] = node('bridge_lightning_void_2', 'Dark Thunder', '+6% Attack Speed, +6% All Damage, +5% Crit Chance', [P('ATTACK_SPEED_PERCENT', 6.0), P('ALL_DAMAGE_PERCENT', 6.0), F('CRITICAL_CHANCE', 5.0)])
nodes['bridge_lightning_void_3'] = node('bridge_lightning_void_3', 'Storm Void II', '+4% Crit Chance, +3% Attack Speed, +0.3% Life Steal', [F('CRITICAL_CHANCE', 4.0), P('ATTACK_SPEED_PERCENT', 3.0), F('LIFE_STEAL', 0.3)])
nodes['bridge_lightning_wind_1'] = node('bridge_lightning_wind_1', 'Gale Rush I', '+3% Attack Speed, +4% Projectile Damage', [P('ATTACK_SPEED_PERCENT', 3.0), P('PROJECTILE_DAMAGE_PERCENT', 4.0)])
nodes['bridge_lightning_wind_2'] = node('bridge_lightning_wind_2', 'Swift Gale', '+5% Attack Speed, +5 Accuracy, +5% Move Speed', [P('ATTACK_SPEED_PERCENT', 5.0), F('ACCURACY', 5.0), P('MOVEMENT_SPEED_PERCENT', 5.0)])
nodes['bridge_lightning_wind_3'] = node('bridge_lightning_wind_3', 'Gale Rush II', '+3% Move Speed, +5 Accuracy', [P('MOVEMENT_SPEED_PERCENT', 3.0), F('ACCURACY', 5.0)])

# =============================================================================
# EARTH — Fortitude (Survivability)
# =============================================================================

nodes['earth_entry'] = node('earth_entry', 'Path of Earth', '+5% Max HP', [P('MAX_HEALTH_PERCENT', 5.0)])

# Cluster 1: Bastion (earth_vitality) — Lane 1 Inner — Max HP + Armor
nodes['earth_vitality_1'] = node('earth_vitality_1', 'Bastion I', '+6% Max HP', [P('MAX_HEALTH_PERCENT', 6.0)])
nodes['earth_vitality_2'] = node('earth_vitality_2', 'Bastion II', '+8 Armor', [F('ARMOR', 8.0)])
nodes['earth_vitality_branch'] = node('earth_vitality_branch', 'Iron Path', '+4% Max HP, +5 Armor', [P('MAX_HEALTH_PERCENT', 4.0), F('ARMOR', 5.0)])
nodes['earth_vitality_3'] = node('earth_vitality_3', 'Bastion III', '+6% Max HP', [P('MAX_HEALTH_PERCENT', 6.0)])
nodes['earth_vitality_4'] = node('earth_vitality_4', 'Iron Skin', '+8 Armor', [F('ARMOR', 8.0)])
nodes['earth_vitality_notable'] = node('earth_vitality_notable', 'Stone Skin', '+10% Max HP, +10 Armor, +5% Physical Resistance', [P('MAX_HEALTH_PERCENT', 10.0), F('ARMOR', 10.0), P('PHYSICAL_RESISTANCE', 5.0)])

# Cluster 2: Rampart (earth_regen) — Lane 2 Inner — Block Chance + Health Recovery
nodes['earth_regen_1'] = node('earth_regen_1', 'Rampart I', '+3% Block Chance', [F('BLOCK_CHANCE', 3.0)])
nodes['earth_regen_2'] = node('earth_regen_2', 'Rampart II', '+5% Health Recovery', [P('HEALTH_RECOVERY_PERCENT', 5.0)])
nodes['earth_regen_branch'] = node('earth_regen_branch', 'Shield Path', '+2% Block Chance, +3% Health Recovery', [F('BLOCK_CHANCE', 2.0), P('HEALTH_RECOVERY_PERCENT', 3.0)])
nodes['earth_regen_3'] = node('earth_regen_3', 'Rampart III', '+3% Block Chance', [F('BLOCK_CHANCE', 3.0)])
nodes['earth_regen_4'] = node('earth_regen_4', 'Resilience', '+5% Health Recovery', [P('HEALTH_RECOVERY_PERCENT', 5.0)])
nodes['earth_regen_notable'] = node('earth_regen_notable', 'Shield Wall', '+5% Block Chance, +10% Health Recovery, +6 Armor', [F('BLOCK_CHANCE', 5.0), P('HEALTH_RECOVERY_PERCENT', 10.0), F('ARMOR', 6.0)])

# Cluster 3: Bulwark (earth_fortitude) — Lane 1 Outer — Armor + Max HP
nodes['earth_fortitude_1'] = node('earth_fortitude_1', 'Bulwark I', '+8 Armor', [F('ARMOR', 8.0)])
nodes['earth_fortitude_2'] = node('earth_fortitude_2', 'Bulwark II', '+6% Max HP', [P('MAX_HEALTH_PERCENT', 6.0)])
nodes['earth_fortitude_branch'] = node('earth_fortitude_branch', 'Stone Path', '+5 Armor, +4% Max HP', [F('ARMOR', 5.0), P('MAX_HEALTH_PERCENT', 4.0)])
nodes['earth_fortitude_3'] = node('earth_fortitude_3', 'Bulwark III', '+8 Armor', [F('ARMOR', 8.0)])
nodes['earth_fortitude_4'] = node('earth_fortitude_4', 'Enduring', '+6% Max HP', [P('MAX_HEALTH_PERCENT', 6.0)])
nodes['earth_fortitude_notable'] = node('earth_fortitude_notable', 'Unbreakable', '+10 Armor, +8% Max HP, +3% Crit Nullify Chance', [F('ARMOR', 10.0), P('MAX_HEALTH_PERCENT', 8.0), P('CRIT_NULLIFY_CHANCE', 3.0)])

# Cluster 4: Citadel (earth_guardian) — Lane 2 Outer — HP Regen + Block Chance
nodes['earth_guardian_1'] = node('earth_guardian_1', 'Citadel I', '+0.5 HP Regen/s', [F('HEALTH_REGEN', 0.5)])
nodes['earth_guardian_2'] = node('earth_guardian_2', 'Citadel II', '+3% Block Chance', [F('BLOCK_CHANCE', 3.0)])
nodes['earth_guardian_branch'] = node('earth_guardian_branch', 'Citadel Path', '+0.3 HP Regen/s, +2% Block Chance', [F('HEALTH_REGEN', 0.3), F('BLOCK_CHANCE', 2.0)])
nodes['earth_guardian_3'] = node('earth_guardian_3', 'Citadel III', '+0.5 HP Regen/s', [F('HEALTH_REGEN', 0.5)])
nodes['earth_guardian_4'] = node('earth_guardian_4', 'Citadel IV', '+3% Block Chance', [F('BLOCK_CHANCE', 3.0)])
nodes['earth_guardian_notable'] = node('earth_guardian_notable', 'Iron Will', '+5% Block Chance, +1.5 HP Regen/s, +10% Health Recovery', [F('BLOCK_CHANCE', 5.0), F('HEALTH_REGEN', 1.5), P('HEALTH_RECOVERY_PERCENT', 10.0)])

# Earth Synergies
nodes['earth_synergy_1'] = node('earth_synergy_1', 'Bedrock Foundation', 'Per 3 Earth nodes: +3% Max HP (max 30%)', [], synergy=syn('ELEMENTAL_COUNT', 'EARTH', 3, 'MAX_HEALTH_PERCENT', 3.0, 30.0))
nodes['earth_synergy_2'] = node('earth_synergy_2', 'Iron Fortitude', 'Per 3 Earth nodes: +2 Armor (max 20)', [], synergy=syn('ELEMENTAL_COUNT', 'EARTH', 3, 'ARMOR', 2.0, 20.0))
nodes['earth_synergy_3'] = node('earth_synergy_3', 'Vital Endurance', 'Per 3 Earth nodes: +0.2 HP Regen/s (max 2.0)', [], synergy=syn('ELEMENTAL_COUNT', 'EARTH', 3, 'HEALTH_REGEN', 0.2, 2.0))
nodes['earth_synergy_4'] = node('earth_synergy_4', 'Regeneration', 'Per 3 Earth nodes: +3% Health Recovery (max 30%)', [], synergy=syn('ELEMENTAL_COUNT', 'EARTH', 3, 'HEALTH_RECOVERY_PERCENT', 3.0, 30.0))
nodes['earth_synergy_hub'] = node('earth_synergy_hub', 'Heart of Earth', '+5% Max HP, +5 Armor', [P('MAX_HEALTH_PERCENT', 5.0), F('ARMOR', 5.0)])

# Earth Keystones
nodes['earth_keystone_1'] = node('earth_keystone_1', 'Ironclad',
    '+25% Max HP, +20 Armor, +5% Block Chance. -15% All Damage, -10% Attack Speed.',
    [P('MAX_HEALTH_PERCENT', 25.0), F('ARMOR', 20.0), F('BLOCK_CHANCE', 5.0)],
    drawbacks=[P('ALL_DAMAGE_PERCENT', -15.0), P('ATTACK_SPEED_PERCENT', -10.0)])
nodes['earth_keystone_2'] = node('earth_keystone_2', 'Fortified',
    'When hit: +3 Armor, +2% Damage Reduction for 3s. Stacks up to 5 times. +10% Max HP, +5 Armor. -8% Move Speed.',
    [P('MAX_HEALTH_PERCENT', 10.0), F('ARMOR', 5.0)],
    conditional=cond('WHEN_HIT', 3.0, 'STACK', [{'stat': 'ARMOR', 'value': 3.0, 'modifierType': 'FLAT'}, {'stat': 'DAMAGE_TAKEN_PERCENT', 'value': -2.0, 'modifierType': 'PERCENT'}], max_s=5),
    drawbacks=[P('MOVEMENT_SPEED_PERCENT', -8.0)])

# Earth Bridges — universal parent stats only
nodes['bridge_earth_void_1'] = node('bridge_earth_void_1', 'Vital Roots I', '+4% Max HP, +4% All Damage', [P('MAX_HEALTH_PERCENT', 4.0), P('ALL_DAMAGE_PERCENT', 4.0)])
nodes['bridge_earth_void_2'] = node('bridge_earth_void_2', 'Enduring Drain', '+7% Max HP, +7% All Damage, +0.5% Life Steal', [P('MAX_HEALTH_PERCENT', 7.0), P('ALL_DAMAGE_PERCENT', 7.0), F('LIFE_STEAL', 0.5)])
nodes['bridge_earth_void_3'] = node('bridge_earth_void_3', 'Vital Roots II', '+5 Armor, +4% Max HP, +0.3% Life Steal', [F('ARMOR', 5.0), P('MAX_HEALTH_PERCENT', 4.0), F('LIFE_STEAL', 0.3)])
nodes['bridge_earth_wind_1'] = node('bridge_earth_wind_1', 'Verdant Flow I', '+4% Max HP, +4% Projectile Damage', [P('MAX_HEALTH_PERCENT', 4.0), P('PROJECTILE_DAMAGE_PERCENT', 4.0)])
nodes['bridge_earth_wind_2'] = node('bridge_earth_wind_2', 'Nature\'s Guard', '+5% Max HP, +8 Evasion, +5 Armor', [P('MAX_HEALTH_PERCENT', 5.0), F('EVASION', 8.0), F('ARMOR', 5.0)])
nodes['bridge_earth_wind_3'] = node('bridge_earth_wind_3', 'Verdant Flow II', '+5 Armor, +5 Evasion', [F('ARMOR', 5.0), F('EVASION', 5.0)])

# =============================================================================
# VOID — Sustain (Life Steal & Close-Combat Drain)
# =============================================================================

nodes['void_entry'] = node('void_entry', 'Path of Void', '+0.4% Life Steal', [F('LIFE_STEAL', 0.4)])

# Cluster 1: Blight (void_corruption) — Lane 1 Inner — Life Steal + All Damage
nodes['void_corruption_1'] = node('void_corruption_1', 'Blight I', '+0.5% Life Steal', [F('LIFE_STEAL', 0.5)])
nodes['void_corruption_2'] = node('void_corruption_2', 'Blight II', '+5% All Damage', [P('ALL_DAMAGE_PERCENT', 5.0)])
nodes['void_corruption_branch'] = node('void_corruption_branch', 'Blight Path', '+0.3% Life Steal, +3% All Damage', [F('LIFE_STEAL', 0.3), P('ALL_DAMAGE_PERCENT', 3.0)])
nodes['void_corruption_3'] = node('void_corruption_3', 'Blight III', '+0.5% Life Steal', [F('LIFE_STEAL', 0.5)])
nodes['void_corruption_4'] = node('void_corruption_4', 'Dark Sustain', '+5% All Damage', [P('ALL_DAMAGE_PERCENT', 5.0)])
nodes['void_corruption_notable'] = node('void_corruption_notable', 'Vampiric Touch', '+1.5% Life Steal, +12% All Damage, +5% Melee Damage', [F('LIFE_STEAL', 1.5), P('ALL_DAMAGE_PERCENT', 12.0), P('MELEE_DAMAGE_PERCENT', 5.0)])

# Cluster 2: Entropy (void_entropy) — Lane 2 Inner — Life Leech + Mana on Kill
nodes['void_entropy_1'] = node('void_entropy_1', 'Entropy I', '+3% Life Leech', [F('LIFE_LEECH', 3.0)])
nodes['void_entropy_2'] = node('void_entropy_2', 'Entropy II', '+1 Mana on Kill', [F('MANA_ON_KILL', 1.0)])
nodes['void_entropy_branch'] = node('void_entropy_branch', 'Entropy Path', '+2% Life Leech, +0.5 Mana on Kill', [F('LIFE_LEECH', 2.0), F('MANA_ON_KILL', 0.5)])
nodes['void_entropy_3'] = node('void_entropy_3', 'Entropy III', '+3% Life Leech', [F('LIFE_LEECH', 3.0)])
nodes['void_entropy_4'] = node('void_entropy_4', 'Dark Knowledge', '+1 Mana on Kill', [F('MANA_ON_KILL', 1.0)])
nodes['void_entropy_notable'] = node('void_entropy_notable', 'Draining Presence', '+5% Life Leech, +10% All Damage, +1.5 Mana on Kill', [F('LIFE_LEECH', 5.0), P('ALL_DAMAGE_PERCENT', 10.0), F('MANA_ON_KILL', 1.5)])

# Cluster 3: Shadow (void_drain) — Lane 1 Outer — Melee Damage + Life Steal
nodes['void_drain_1'] = node('void_drain_1', 'Shadow I', '+6% Melee Damage', [P('MELEE_DAMAGE_PERCENT', 6.0)])
nodes['void_drain_2'] = node('void_drain_2', 'Shadow II', '+0.5% Life Steal', [F('LIFE_STEAL', 0.5)])
nodes['void_drain_branch'] = node('void_drain_branch', 'Shadow Path', '+4% Melee Damage, +0.3% Life Steal', [P('MELEE_DAMAGE_PERCENT', 4.0), F('LIFE_STEAL', 0.3)])
nodes['void_drain_3'] = node('void_drain_3', 'Shadow III', '+6% Melee Damage', [P('MELEE_DAMAGE_PERCENT', 6.0)])
nodes['void_drain_4'] = node('void_drain_4', 'Dark Siphon', '+0.5% Life Steal', [F('LIFE_STEAL', 0.5)])
nodes['void_drain_notable'] = node('void_drain_notable', 'Dark Reaver', '+10% Melee Damage, +1.0% Life Steal, +6% All Damage', [P('MELEE_DAMAGE_PERCENT', 10.0), F('LIFE_STEAL', 1.0), P('ALL_DAMAGE_PERCENT', 6.0)])

# Cluster 4: Abyss (void_oblivion) — Lane 2 Outer — All Damage + Damage Reduction
nodes['void_oblivion_1'] = node('void_oblivion_1', 'Abyss I', '+5% All Damage', [P('ALL_DAMAGE_PERCENT', 5.0)])
nodes['void_oblivion_2'] = node('void_oblivion_2', 'Abyss II', '+2% Damage Reduction', [P('DAMAGE_TAKEN_PERCENT', -2.0)])
nodes['void_oblivion_branch'] = node('void_oblivion_branch', 'Abyss Path', '+3% All Damage, +1% Damage Reduction', [P('ALL_DAMAGE_PERCENT', 3.0), P('DAMAGE_TAKEN_PERCENT', -1.0)])
nodes['void_oblivion_3'] = node('void_oblivion_3', 'Abyss III', '+5% All Damage', [P('ALL_DAMAGE_PERCENT', 5.0)])
nodes['void_oblivion_4'] = node('void_oblivion_4', 'Dark Resilience', '+2% Damage Reduction', [P('DAMAGE_TAKEN_PERCENT', -2.0)])
nodes['void_oblivion_notable'] = node('void_oblivion_notable', 'Endless Hunger', '+10% All Damage, +5% Damage Reduction, +1.0% Life Steal', [P('ALL_DAMAGE_PERCENT', 10.0), P('DAMAGE_TAKEN_PERCENT', -5.0), F('LIFE_STEAL', 1.0)])

# Void Synergies
nodes['void_synergy_1'] = node('void_synergy_1', 'Dark Accumulation', 'Per 3 Void nodes: +0.5% Life Steal (max 5%)', [], synergy=syn('ELEMENTAL_COUNT', 'VOID', 3, 'LIFE_STEAL', 0.5, 5.0))
nodes['void_synergy_2'] = node('void_synergy_2', 'Raw Power', 'Per 3 Void nodes: +1% All Damage (max 10%)', [], synergy=syn('ELEMENTAL_COUNT', 'VOID', 3, 'ALL_DAMAGE_PERCENT', 1.0, 10.0))
nodes['void_synergy_3'] = node('void_synergy_3', 'Soul Harvest', 'Per 3 Void nodes: +1% Life Leech (max 10%)', [], synergy=syn('ELEMENTAL_COUNT', 'VOID', 3, 'LIFE_LEECH', 1.0, 10.0))
nodes['void_synergy_4'] = node('void_synergy_4', 'Tenacity', 'Per 3 Void nodes: +1% Damage Reduction (max 10%)', [], synergy=syn('ELEMENTAL_COUNT', 'VOID', 3, 'DAMAGE_TAKEN_PERCENT', -1.0, -10.0))
nodes['void_synergy_hub'] = node('void_synergy_hub', 'Heart of Void', '+5% All Damage, +0.5% Life Steal', [P('ALL_DAMAGE_PERCENT', 5.0), F('LIFE_STEAL', 0.5)])

# Void Keystones
nodes['void_keystone_1'] = node('void_keystone_1', 'Predator',
    '+2% Life Steal, +12% All Damage, +3% Damage Reduction. -15% Max HP, -10 Armor.',
    [F('LIFE_STEAL', 2.0), P('ALL_DAMAGE_PERCENT', 12.0), P('DAMAGE_TAKEN_PERCENT', -3.0)],
    drawbacks=[P('MAX_HEALTH_PERCENT', -15.0), F('ARMOR', -10.0)])
nodes['void_keystone_2'] = node('void_keystone_2', 'Feast or Famine',
    'On Kill: heal 8% Max HP, +10% All Damage for 5s. +5% All Damage, +0.5% Life Steal. -0.5 HP Regen, -10% Energy Shield.',
    [P('ALL_DAMAGE_PERCENT', 5.0), F('LIFE_STEAL', 0.5)],
    conditional=cond('ON_KILL', 5.0, 'REFRESH', [{'stat': 'ALL_DAMAGE_PERCENT', 'value': 10.0, 'modifierType': 'PERCENT'}]),
    drawbacks=[F('HEALTH_REGEN', -0.5), P('ENERGY_SHIELD_PERCENT', -10.0)])

# =============================================================================
# WIND — Finesse (Evasion & Precision)
# =============================================================================

nodes['wind_entry'] = node('wind_entry', 'Path of Wind', '+8 Evasion', [F('EVASION', 8.0)])

# Cluster 1: Gale (wind_flow) — Lane 1 Inner — Evasion + Accuracy
nodes['wind_flow_1'] = node('wind_flow_1', 'Gale I', '+10 Evasion', [F('EVASION', 10.0)])
nodes['wind_flow_2'] = node('wind_flow_2', 'Gale II', '+8 Accuracy', [F('ACCURACY', 8.0)])
nodes['wind_flow_branch'] = node('wind_flow_branch', 'Wind Path', '+6 Evasion, +5 Accuracy', [F('EVASION', 6.0), F('ACCURACY', 5.0)])
nodes['wind_flow_3'] = node('wind_flow_3', 'Gale III', '+10 Evasion', [F('EVASION', 10.0)])
nodes['wind_flow_4'] = node('wind_flow_4', 'Keen Senses', '+8 Accuracy', [F('ACCURACY', 8.0)])
nodes['wind_flow_notable'] = node('wind_flow_notable', 'Wind Dancer', '+12 Evasion, +10 Accuracy, +5% All Damage', [F('EVASION', 12.0), F('ACCURACY', 10.0), P('ALL_DAMAGE_PERCENT', 5.0)])

# Cluster 2: Zephyr (wind_arcane) — Lane 2 Inner — Dodge Chance + Move Speed
nodes['wind_arcane_1'] = node('wind_arcane_1', 'Zephyr I', '+3% Dodge Chance', [P('DODGE_CHANCE', 3.0)])
nodes['wind_arcane_2'] = node('wind_arcane_2', 'Zephyr II', '+3% Move Speed', [P('MOVEMENT_SPEED_PERCENT', 3.0)])
nodes['wind_arcane_branch'] = node('wind_arcane_branch', 'Zephyr Path', '+2% Dodge Chance, +2% Move Speed', [P('DODGE_CHANCE', 2.0), P('MOVEMENT_SPEED_PERCENT', 2.0)])
nodes['wind_arcane_3'] = node('wind_arcane_3', 'Zephyr III', '+3% Dodge Chance', [P('DODGE_CHANCE', 3.0)])
nodes['wind_arcane_4'] = node('wind_arcane_4', 'Fleet Foot', '+3% Move Speed', [P('MOVEMENT_SPEED_PERCENT', 3.0)])
nodes['wind_arcane_notable'] = node('wind_arcane_notable', 'Ghost Step', '+5% Dodge Chance, +5% Move Speed, +10 Evasion', [P('DODGE_CHANCE', 5.0), P('MOVEMENT_SPEED_PERCENT', 5.0), F('EVASION', 10.0)])

# Cluster 3: Marksman (wind_adaptation) — Lane 1 Outer — Proj Dmg + Accuracy
nodes['wind_adaptation_1'] = node('wind_adaptation_1', 'Marksman I', '+6% Projectile Damage', [P('PROJECTILE_DAMAGE_PERCENT', 6.0)])
nodes['wind_adaptation_2'] = node('wind_adaptation_2', 'Marksman II', '+8 Accuracy', [F('ACCURACY', 8.0)])
nodes['wind_adaptation_branch'] = node('wind_adaptation_branch', 'Marksman Path', '+4% Projectile Damage, +5 Accuracy', [P('PROJECTILE_DAMAGE_PERCENT', 4.0), F('ACCURACY', 5.0)])
nodes['wind_adaptation_3'] = node('wind_adaptation_3', 'Marksman III', '+6% Projectile Damage', [P('PROJECTILE_DAMAGE_PERCENT', 6.0)])
nodes['wind_adaptation_4'] = node('wind_adaptation_4', 'Sharp Aim', '+8 Accuracy', [F('ACCURACY', 8.0)])
nodes['wind_adaptation_notable'] = node('wind_adaptation_notable', 'Sharpshooter', '+10% Projectile Damage, +8 Accuracy, +5% All Damage', [P('PROJECTILE_DAMAGE_PERCENT', 10.0), F('ACCURACY', 8.0), P('ALL_DAMAGE_PERCENT', 5.0)])

# Cluster 4: Drift (wind_depth) — Lane 2 Outer — Move Speed + Dodge Chance
nodes['wind_depth_1'] = node('wind_depth_1', 'Drift I', '+3% Move Speed', [P('MOVEMENT_SPEED_PERCENT', 3.0)])
nodes['wind_depth_2'] = node('wind_depth_2', 'Drift II', '+3% Dodge Chance', [P('DODGE_CHANCE', 3.0)])
nodes['wind_depth_branch'] = node('wind_depth_branch', 'Drift Path', '+2% Move Speed, +2% Dodge Chance', [P('MOVEMENT_SPEED_PERCENT', 2.0), P('DODGE_CHANCE', 2.0)])
nodes['wind_depth_3'] = node('wind_depth_3', 'Drift III', '+3% Move Speed', [P('MOVEMENT_SPEED_PERCENT', 3.0)])
nodes['wind_depth_4'] = node('wind_depth_4', 'Air Walk', '+3% Dodge Chance', [P('DODGE_CHANCE', 3.0)])
nodes['wind_depth_notable'] = node('wind_depth_notable', 'Slippery', '+5% Dodge Chance, +5% Move Speed, +4% Damage Reduction, +5 Evasion', [P('DODGE_CHANCE', 5.0), P('MOVEMENT_SPEED_PERCENT', 5.0), P('DAMAGE_TAKEN_PERCENT', -4.0), F('EVASION', 5.0)])

# Wind Synergies
nodes['wind_synergy_1'] = node('wind_synergy_1', 'Wind Convergence', 'Per 3 Wind nodes: +3 Evasion (max 30)', [], synergy=syn('ELEMENTAL_COUNT', 'WIND', 3, 'EVASION', 3.0, 30.0))
nodes['wind_synergy_2'] = node('wind_synergy_2', 'Gale Force', 'Per 3 Wind nodes: +1.5% Projectile Damage (max 15%)', [], synergy=syn('ELEMENTAL_COUNT', 'WIND', 3, 'PROJECTILE_DAMAGE_PERCENT', 1.5, 15.0))
nodes['wind_synergy_3'] = node('wind_synergy_3', 'Quick Reflexes', 'Per 3 Wind nodes: +1.6% Dodge Chance (max 16%)', [], synergy=syn('ELEMENTAL_COUNT', 'WIND', 3, 'DODGE_CHANCE', 1.6, 16.0))
nodes['wind_synergy_4'] = node('wind_synergy_4', 'Swiftness', 'Per 3 Wind nodes: +1.6% Move Speed (max 16%)', [], synergy=syn('ELEMENTAL_COUNT', 'WIND', 3, 'MOVEMENT_SPEED_PERCENT', 1.6, 16.0))
nodes['wind_synergy_hub'] = node('wind_synergy_hub', 'Heart of Wind', '+5% Projectile Damage, +8 Evasion', [P('PROJECTILE_DAMAGE_PERCENT', 5.0), F('EVASION', 8.0)])

# Wind Keystones
nodes['wind_keystone_1'] = node('wind_keystone_1', 'Ghost',
    'On Evade: +15% All Damage, +5% Move Speed for 3s. +15 Evasion, +5% Dodge Chance. -15 Armor, -10% Max HP.',
    [F('EVASION', 15.0), P('DODGE_CHANCE', 5.0)],
    conditional=cond('ON_EVADE', 3.0, 'REFRESH', [{'stat': 'ALL_DAMAGE_PERCENT', 'value': 15.0, 'modifierType': 'PERCENT'}, {'stat': 'MOVEMENT_SPEED_PERCENT', 'value': 5.0, 'modifierType': 'PERCENT'}]),
    drawbacks=[F('ARMOR', -15.0), P('MAX_HEALTH_PERCENT', -10.0)])
nodes['wind_keystone_2'] = node('wind_keystone_2', 'Keen Eye',
    'While moving: +12% All Damage, +8 Evasion. +10 Accuracy, +5% Dodge Chance. -15% Max HP, -10 Armor.',
    [F('ACCURACY', 10.0), P('DODGE_CHANCE', 5.0)],
    conditional=cond('WHILE_MOVING', 0.0, 'REFRESH', [{'stat': 'ALL_DAMAGE_PERCENT', 'value': 12.0, 'modifierType': 'PERCENT'}, {'stat': 'EVASION', 'value': 8.0, 'modifierType': 'FLAT'}]),
    drawbacks=[P('MAX_HEALTH_PERCENT', -15.0), F('ARMOR', -10.0)])

# =============================================================================
# HAVOC — Berserker Assassin (Fire + Void + Lightning)
# =============================================================================

nodes['havoc_entry'] = node('havoc_entry', 'Path of Havoc', '+4% Physical Damage', [P('PHYSICAL_DAMAGE_PERCENT', 4.0)])

# Cluster 1: Carnage (havoc_carnage) — Lane 1 Inner — Fast Crits (Crit Mult + Atk Speed)
nodes['havoc_carnage_1'] = node('havoc_carnage_1', 'Carnage I', '+6 Crit Multiplier', [F('CRITICAL_MULTIPLIER', 6.0)])
nodes['havoc_carnage_2'] = node('havoc_carnage_2', 'Carnage II', '+4% Attack Speed', [P('ATTACK_SPEED_PERCENT', 4.0)])
nodes['havoc_carnage_branch'] = node('havoc_carnage_branch', 'Carnage Path', '+4 Crit Multiplier, +3% Attack Speed', [F('CRITICAL_MULTIPLIER', 4.0), P('ATTACK_SPEED_PERCENT', 3.0)])
nodes['havoc_carnage_3'] = node('havoc_carnage_3', 'Carnage III', '+6 Crit Multiplier', [F('CRITICAL_MULTIPLIER', 6.0)])
nodes['havoc_carnage_4'] = node('havoc_carnage_4', 'Havoc Strike', '+4% Attack Speed', [P('ATTACK_SPEED_PERCENT', 4.0)])
nodes['havoc_carnage_notable'] = node('havoc_carnage_notable', 'Killing Spree', 'ON_KILL: +10% Atk Speed, +8% Crit Chance for 4s. +5% Armor Penetration.',
    [F('ARMOR_PENETRATION', 5.0)],
    conditional=cond('ON_KILL', 4.0, 'REFRESH', [{'stat': 'ATTACK_SPEED_PERCENT', 'value': 10.0, 'modifierType': 'PERCENT'}, {'stat': 'CRITICAL_CHANCE', 'value': 8.0, 'modifierType': 'FLAT'}]))

# Cluster 2: Frenzy (havoc_frenzy) — Lane 2 Inner — Aggressive Sustain (Life Steal + All Dmg)
nodes['havoc_frenzy_1'] = node('havoc_frenzy_1', 'Frenzy I', '+0.5% Life Steal', [F('LIFE_STEAL', 0.5)])
nodes['havoc_frenzy_2'] = node('havoc_frenzy_2', 'Frenzy II', '+5% All Damage', [P('ALL_DAMAGE_PERCENT', 5.0)])
nodes['havoc_frenzy_branch'] = node('havoc_frenzy_branch', 'Frenzy Path', '+0.3% Life Steal, +3% All Damage', [F('LIFE_STEAL', 0.3), P('ALL_DAMAGE_PERCENT', 3.0)])
nodes['havoc_frenzy_3'] = node('havoc_frenzy_3', 'Frenzy III', '+0.5% Life Steal', [F('LIFE_STEAL', 0.5)])
nodes['havoc_frenzy_4'] = node('havoc_frenzy_4', 'Frenzy IV', '+5% All Damage', [P('ALL_DAMAGE_PERCENT', 5.0)])
nodes['havoc_frenzy_notable'] = node('havoc_frenzy_notable', 'Bloodlust', '+1.5% Life Steal, +12% All Damage, +5% Life Leech', [F('LIFE_STEAL', 1.5), P('ALL_DAMAGE_PERCENT', 12.0), F('LIFE_LEECH', 5.0)])

# Cluster 3: Ruin (havoc_ruin) — Lane 1 Outer — Precision Damage (Crit Chance + Phys Dmg)
nodes['havoc_ruin_1'] = node('havoc_ruin_1', 'Ruin I', '+5% Crit Chance', [F('CRITICAL_CHANCE', 5.0)])
nodes['havoc_ruin_2'] = node('havoc_ruin_2', 'Ruin II', '+6% Physical Damage', [P('PHYSICAL_DAMAGE_PERCENT', 6.0)])
nodes['havoc_ruin_branch'] = node('havoc_ruin_branch', 'Ruin Path', '+3% Crit Chance, +4% Physical Damage', [F('CRITICAL_CHANCE', 3.0), P('PHYSICAL_DAMAGE_PERCENT', 4.0)])
nodes['havoc_ruin_3'] = node('havoc_ruin_3', 'Ruin III', '+5% Crit Chance', [F('CRITICAL_CHANCE', 5.0)])
nodes['havoc_ruin_4'] = node('havoc_ruin_4', 'Ruin IV', '+6% Physical Damage', [P('PHYSICAL_DAMAGE_PERCENT', 6.0)])
nodes['havoc_ruin_notable'] = node('havoc_ruin_notable', 'Storm of Blades', '+8% Crit Chance, +8% Physical Damage, +8% Execute Damage', [F('CRITICAL_CHANCE', 8.0), P('PHYSICAL_DAMAGE_PERCENT', 8.0), P('EXECUTE_DAMAGE_PERCENT', 8.0)])

# Cluster 4: Mayhem (havoc_mayhem) — Lane 2 Outer — Fire Offense (Fire Dmg + Ignite — SPECIALIST)
nodes['havoc_mayhem_1'] = node('havoc_mayhem_1', 'Mayhem I', '+5% Fire Damage', [P('FIRE_DAMAGE_PERCENT', 5.0)])
nodes['havoc_mayhem_2'] = node('havoc_mayhem_2', 'Mayhem II', '+4% Ignite Chance', [F('IGNITE_CHANCE', 4.0)])
nodes['havoc_mayhem_branch'] = node('havoc_mayhem_branch', 'Mayhem Path', '+3% Fire Damage, +2% Ignite Chance', [P('FIRE_DAMAGE_PERCENT', 3.0), F('IGNITE_CHANCE', 2.0)])
nodes['havoc_mayhem_3'] = node('havoc_mayhem_3', 'Mayhem III', '+5% Fire Damage', [P('FIRE_DAMAGE_PERCENT', 5.0)])
nodes['havoc_mayhem_4'] = node('havoc_mayhem_4', 'Mayhem IV', '+4% Ignite Chance', [F('IGNITE_CHANCE', 4.0)])
nodes['havoc_mayhem_notable'] = node('havoc_mayhem_notable', 'Pyromaniac', '+10% Fire Damage, +8 Fire Penetration, +5% Ignite Chance', [P('FIRE_DAMAGE_PERCENT', 10.0), F('FIRE_PENETRATION', 8.0), F('IGNITE_CHANCE', 5.0)])

# Havoc Synergies
nodes['havoc_synergy_1'] = node('havoc_synergy_1', 'Chaos Resonance', 'Per 3 Havoc nodes: +2 Crit Multiplier (max 20)', [], synergy=syn('BRANCH_COUNT', 'HAVOC', 3, 'CRITICAL_MULTIPLIER', 2.0, 20.0))
nodes['havoc_synergy_2'] = node('havoc_synergy_2', 'Shattered Defenses', 'Per 3 Havoc nodes: +1% Armor Penetration (max 10%)', [], synergy=syn('BRANCH_COUNT', 'HAVOC', 3, 'ARMOR_PENETRATION', 1.0, 10.0))
nodes['havoc_synergy_3'] = node('havoc_synergy_3', 'Finishing Blow', 'Per 3 Havoc nodes: +2% Execute Damage (max 20%)', [], synergy=syn('BRANCH_COUNT', 'HAVOC', 3, 'EXECUTE_DAMAGE_PERCENT', 2.0, 20.0))
nodes['havoc_synergy_4'] = node('havoc_synergy_4', 'Infernal Rage', 'Per 3 Havoc nodes: +2% Fire Damage (max 20%)', [], synergy=syn('BRANCH_COUNT', 'HAVOC', 3, 'FIRE_DAMAGE_PERCENT', 2.0, 20.0))
nodes['havoc_synergy_hub'] = node('havoc_synergy_hub', 'Nexus of Havoc', '+0.1 Crit Multiplier per (Fire+Void+Lightning) attribute point',
    [], synergy=syn('ATTRIBUTE_SUM_SCALING', 'HAVOC', 1, 'CRITICAL_MULTIPLIER', 0.1, 999.0))

# Havoc Keystones
nodes['havoc_keystone_1'] = node('havoc_keystone_1', 'Rampage',
    'On Kill: +8% Attack Speed, +4 Crit Multiplier, +2% DoT Damage per stack. Max 5 stacks, lasts 6s, refreshes on kill.',
    [F('RAMPAGE_STACK_ATK_SPEED', 8.0), F('RAMPAGE_STACK_CRIT_MULT', 4.0), F('RAMPAGE_STACK_DOT', 2.0), F('RAMPAGE_MAX_STACKS', 5.0), F('RAMPAGE_DURATION', 6.0)],
    drawbacks=[P('MAX_HEALTH_PERCENT', -15.0)])
nodes['havoc_keystone_2'] = node('havoc_keystone_2', 'Chain Detonation',
    'Critical hits detonate all active DoTs on the target as instant Void burst damage.',
    [F('DETONATE_DOT_ON_CRIT', 100.0)],
    drawbacks=[P('STATUS_EFFECT_DURATION', -15.0), F('CRITICAL_CHANCE', -5.0)])

# =============================================================================
# JUGGERNAUT — War Machine (Fire + Void + Earth)
# =============================================================================

nodes['juggernaut_entry'] = node('juggernaut_entry', 'Path of Juggernaut', '+4% Physical Damage', [P('PHYSICAL_DAMAGE_PERCENT', 4.0)])

# Cluster 1: Conquest (juggernaut_conquest) — Lane 1 Inner — Armored Warrior (Fire+Earth)
nodes['juggernaut_conquest_1'] = node('juggernaut_conquest_1', 'Conquest I', '+6% Physical Damage', [P('PHYSICAL_DAMAGE_PERCENT', 6.0)])
nodes['juggernaut_conquest_2'] = node('juggernaut_conquest_2', 'Conquest II', '+6% Max HP', [P('MAX_HEALTH_PERCENT', 6.0)])
nodes['juggernaut_conquest_branch'] = node('juggernaut_conquest_branch', 'Conquest Path', '+4% Physical Damage, +4% Max HP', [P('PHYSICAL_DAMAGE_PERCENT', 4.0), P('MAX_HEALTH_PERCENT', 4.0)])
nodes['juggernaut_conquest_3'] = node('juggernaut_conquest_3', 'Conquest III', '+6% Physical Damage', [P('PHYSICAL_DAMAGE_PERCENT', 6.0)])
nodes['juggernaut_conquest_4'] = node('juggernaut_conquest_4', 'Conquest IV', '+6% Max HP', [P('MAX_HEALTH_PERCENT', 6.0)])
nodes['juggernaut_conquest_notable'] = node('juggernaut_conquest_notable', 'Iron Reaver', '+10% Physical Damage, +8% Max HP, +5 Armor', [P('PHYSICAL_DAMAGE_PERCENT', 10.0), P('MAX_HEALTH_PERCENT', 8.0), F('ARMOR', 5.0)])

# Cluster 2: Dominion (juggernaut_dominion) — Lane 2 Inner — Draining Tank (Void+Earth)
nodes['juggernaut_dominion_1'] = node('juggernaut_dominion_1', 'Dominion I', '+0.5% Life Steal', [F('LIFE_STEAL', 0.5)])
nodes['juggernaut_dominion_2'] = node('juggernaut_dominion_2', 'Dominion II', '+8 Armor', [F('ARMOR', 8.0)])
nodes['juggernaut_dominion_branch'] = node('juggernaut_dominion_branch', 'Dominion Path', '+0.3% Life Steal, +5 Armor', [F('LIFE_STEAL', 0.3), F('ARMOR', 5.0)])
nodes['juggernaut_dominion_3'] = node('juggernaut_dominion_3', 'Dominion III', '+0.5% Life Steal', [F('LIFE_STEAL', 0.5)])
nodes['juggernaut_dominion_4'] = node('juggernaut_dominion_4', 'Dominion IV', '+8 Armor', [F('ARMOR', 8.0)])
nodes['juggernaut_dominion_notable'] = node('juggernaut_dominion_notable', 'Blood Guard', '+1% Life Steal, +10 Armor, +5% Block Chance', [F('LIFE_STEAL', 1.0), F('ARMOR', 10.0), F('BLOCK_CHANCE', 5.0)])

# Cluster 3: Bloodforge (juggernaut_bloodforge) — Lane 1 Outer — Heavy Hitter (Fire+Void)
nodes['juggernaut_bloodforge_1'] = node('juggernaut_bloodforge_1', 'Bloodforge I', '+6% Charged Attack Damage', [P('CHARGED_ATTACK_DAMAGE_PERCENT', 6.0)])
nodes['juggernaut_bloodforge_2'] = node('juggernaut_bloodforge_2', 'Bloodforge II', '+6% Melee Damage', [P('MELEE_DAMAGE_PERCENT', 6.0)])
nodes['juggernaut_bloodforge_branch'] = node('juggernaut_bloodforge_branch', 'Bloodforge Path', '+4% Charged Attack Damage, +4% Melee Damage', [P('CHARGED_ATTACK_DAMAGE_PERCENT', 4.0), P('MELEE_DAMAGE_PERCENT', 4.0)])
nodes['juggernaut_bloodforge_3'] = node('juggernaut_bloodforge_3', 'Bloodforge III', '+6% Charged Attack Damage', [P('CHARGED_ATTACK_DAMAGE_PERCENT', 6.0)])
nodes['juggernaut_bloodforge_4'] = node('juggernaut_bloodforge_4', 'Bloodforge IV', '+6% Melee Damage', [P('MELEE_DAMAGE_PERCENT', 6.0)])
nodes['juggernaut_bloodforge_notable'] = node('juggernaut_bloodforge_notable', 'Relentless', '+10% Charged Attack Damage, +8% Melee Damage, +5% All Damage', [P('CHARGED_ATTACK_DAMAGE_PERCENT', 10.0), P('MELEE_DAMAGE_PERCENT', 8.0), P('ALL_DAMAGE_PERCENT', 5.0)])

# Cluster 4: Tyrant (juggernaut_tyrant) — Lane 2 Outer — Retaliation (Earth specialist)
nodes['juggernaut_tyrant_1'] = node('juggernaut_tyrant_1', 'Tyrant I', '+5% Thorns Damage', [P('THORNS_DAMAGE_PERCENT', 5.0)])
nodes['juggernaut_tyrant_2'] = node('juggernaut_tyrant_2', 'Tyrant II', '+5% Max HP', [P('MAX_HEALTH_PERCENT', 5.0)])
nodes['juggernaut_tyrant_branch'] = node('juggernaut_tyrant_branch', 'Tyrant Path', '+3% Thorns Damage, +3% Max HP', [P('THORNS_DAMAGE_PERCENT', 3.0), P('MAX_HEALTH_PERCENT', 3.0)])
nodes['juggernaut_tyrant_3'] = node('juggernaut_tyrant_3', 'Tyrant III', '+5% Thorns Damage', [P('THORNS_DAMAGE_PERCENT', 5.0)])
nodes['juggernaut_tyrant_4'] = node('juggernaut_tyrant_4', 'Tyrant IV', '+5% Max HP', [P('MAX_HEALTH_PERCENT', 5.0)])
nodes['juggernaut_tyrant_notable'] = node('juggernaut_tyrant_notable', 'Indomitable', '+8% Thorns Damage, +8% Max HP, +5% Physical Resistance', [P('THORNS_DAMAGE_PERCENT', 8.0), P('MAX_HEALTH_PERCENT', 8.0), P('PHYSICAL_RESISTANCE', 5.0)])

# Juggernaut Synergies
nodes['juggernaut_synergy_1'] = node('juggernaut_synergy_1', 'Unbreakable Wall', 'Per 3 Juggernaut nodes: +2% Max HP (max 20%)', [], synergy=syn('BRANCH_COUNT', 'JUGGERNAUT', 3, 'MAX_HEALTH_PERCENT', 2.0, 20.0))
nodes['juggernaut_synergy_2'] = node('juggernaut_synergy_2', 'Blood Pact', 'Per 3 Juggernaut nodes: +0.3% Life Steal (max 3%)', [], synergy=syn('BRANCH_COUNT', 'JUGGERNAUT', 3, 'LIFE_STEAL', 0.3, 3.0))
nodes['juggernaut_synergy_3'] = node('juggernaut_synergy_3', 'Iron Thorns', 'Per 3 Juggernaut nodes: +2% Thorns Damage (max 20%)', [], synergy=syn('BRANCH_COUNT', 'JUGGERNAUT', 3, 'THORNS_DAMAGE_PERCENT', 2.0, 20.0))
nodes['juggernaut_synergy_4'] = node('juggernaut_synergy_4', 'Battle Armor', 'Per 3 Juggernaut nodes: +2 Armor (max 20)', [], synergy=syn('BRANCH_COUNT', 'JUGGERNAUT', 3, 'ARMOR', 2.0, 20.0))
nodes['juggernaut_synergy_hub'] = node('juggernaut_synergy_hub', 'Nexus of Juggernaut', '+0.15% Max HP per (Fire+Void+Earth) attribute point',
    [], synergy=syn('ATTRIBUTE_SUM_SCALING', 'JUGGERNAUT', 1, 'MAX_HEALTH_PERCENT', 0.15, 999.0))

# Juggernaut Keystones
nodes['juggernaut_keystone_1'] = node('juggernaut_keystone_1', 'Blood Fortress',
    'On Block: next 3 attacks gain 15% of blocked damage as Life Steal. Other Life Steal sources reduced by 50%.',
    [F('BLOCK_CHARGE_LIFE_STEAL', 15.0), F('BLOCK_CHARGE_TEMP_HP', 10.0), F('BLOCK_CHARGE_COUNT', 3.0)],
    drawbacks=[P('ATTACK_SPEED_PERCENT', -15.0), P('MAX_HEALTH_PERCENT', -10.0)])
nodes['juggernaut_keystone_2'] = node('juggernaut_keystone_2', 'Colossus',
    '+1% Physical Damage per 50 Max HP. +15% Max HP. -15% Attack Speed, -10% Move Speed.',
    [F('HP_SCALING_DAMAGE', 1.0), P('MAX_HEALTH_PERCENT', 15.0)],
    drawbacks=[P('ATTACK_SPEED_PERCENT', -15.0), P('MOVEMENT_SPEED_PERCENT', -10.0)])

# =============================================================================
# STRIKER — Blade Dancer (Fire + Wind + Lightning)
# =============================================================================

nodes['striker_entry'] = node('striker_entry', 'Path of Striker', '+3% Attack Speed', [P('ATTACK_SPEED_PERCENT', 3.0)])

# Cluster 1: Quicksilver (striker_quicksilver) — Lane 1 Inner — Fast Crits (Fire+Lightning)
nodes['striker_quicksilver_1'] = node('striker_quicksilver_1', 'Quicksilver I', '+4% Attack Speed', [P('ATTACK_SPEED_PERCENT', 4.0)])
nodes['striker_quicksilver_2'] = node('striker_quicksilver_2', 'Quicksilver II', '+6 Crit Multiplier', [F('CRITICAL_MULTIPLIER', 6.0)])
nodes['striker_quicksilver_branch'] = node('striker_quicksilver_branch', 'Quicksilver Path', '+3% Attack Speed, +4 Crit Multiplier', [P('ATTACK_SPEED_PERCENT', 3.0), F('CRITICAL_MULTIPLIER', 4.0)])
nodes['striker_quicksilver_3'] = node('striker_quicksilver_3', 'Quicksilver III', '+4% Attack Speed', [P('ATTACK_SPEED_PERCENT', 4.0)])
nodes['striker_quicksilver_4'] = node('striker_quicksilver_4', 'Quicksilver IV', '+6 Crit Multiplier', [F('CRITICAL_MULTIPLIER', 6.0)])
nodes['striker_quicksilver_notable'] = node('striker_quicksilver_notable', 'Quick Draw', '+8% Attack Speed, +8 Crit Multiplier, +5% Move Speed', [P('ATTACK_SPEED_PERCENT', 8.0), F('CRITICAL_MULTIPLIER', 8.0), P('MOVEMENT_SPEED_PERCENT', 5.0)])

# Cluster 2: Precision (striker_precision) — Lane 2 Inner — Agile Precision (Wind+Lightning)
nodes['striker_precision_1'] = node('striker_precision_1', 'Precision I', '+10 Evasion', [F('EVASION', 10.0)])
nodes['striker_precision_2'] = node('striker_precision_2', 'Precision II', '+8 Accuracy', [F('ACCURACY', 8.0)])
nodes['striker_precision_branch'] = node('striker_precision_branch', 'Precision Path', '+6 Evasion, +5 Accuracy', [F('EVASION', 6.0), F('ACCURACY', 5.0)])
nodes['striker_precision_3'] = node('striker_precision_3', 'Precision III', '+10 Evasion', [F('EVASION', 10.0)])
nodes['striker_precision_4'] = node('striker_precision_4', 'Precision IV', '+8 Accuracy', [F('ACCURACY', 8.0)])
nodes['striker_precision_notable'] = node('striker_precision_notable', 'Vital Strike', '+10 Evasion, +8 Accuracy, +4% Dodge Chance', [F('EVASION', 10.0), F('ACCURACY', 8.0), P('DODGE_CHANCE', 4.0)])

# Cluster 3: Ambush (striker_ambush) — Lane 1 Outer — Burst Damage (Fire+Lightning)
nodes['striker_ambush_1'] = node('striker_ambush_1', 'Ambush I', '+6% Physical Damage', [P('PHYSICAL_DAMAGE_PERCENT', 6.0)])
nodes['striker_ambush_2'] = node('striker_ambush_2', 'Ambush II', '+5% Crit Chance', [F('CRITICAL_CHANCE', 5.0)])
nodes['striker_ambush_branch'] = node('striker_ambush_branch', 'Ambush Path', '+4% Physical Damage, +3% Crit Chance', [P('PHYSICAL_DAMAGE_PERCENT', 4.0), F('CRITICAL_CHANCE', 3.0)])
nodes['striker_ambush_3'] = node('striker_ambush_3', 'Ambush III', '+6% Physical Damage', [P('PHYSICAL_DAMAGE_PERCENT', 6.0)])
nodes['striker_ambush_4'] = node('striker_ambush_4', 'Ambush IV', '+5% Crit Chance', [F('CRITICAL_CHANCE', 5.0)])
nodes['striker_ambush_notable'] = node('striker_ambush_notable', 'Relentless Assault', '+8% Physical Damage, +8% Crit Chance, +5% All Damage', [P('PHYSICAL_DAMAGE_PERCENT', 8.0), F('CRITICAL_CHANCE', 8.0), P('ALL_DAMAGE_PERCENT', 5.0)])

# Cluster 4: Flurry (striker_flurry) — Lane 2 Outer — Evasive Mobility (Wind+Lightning)
nodes['striker_flurry_1'] = node('striker_flurry_1', 'Flurry I', '+3% Dodge Chance', [P('DODGE_CHANCE', 3.0)])
nodes['striker_flurry_2'] = node('striker_flurry_2', 'Flurry II', '+3% Move Speed', [P('MOVEMENT_SPEED_PERCENT', 3.0)])
nodes['striker_flurry_branch'] = node('striker_flurry_branch', 'Flurry Path', '+2% Dodge Chance, +2% Move Speed', [P('DODGE_CHANCE', 2.0), P('MOVEMENT_SPEED_PERCENT', 2.0)])
nodes['striker_flurry_3'] = node('striker_flurry_3', 'Flurry III', '+3% Dodge Chance', [P('DODGE_CHANCE', 3.0)])
nodes['striker_flurry_4'] = node('striker_flurry_4', 'Flurry IV', '+3% Move Speed', [P('MOVEMENT_SPEED_PERCENT', 3.0)])
nodes['striker_flurry_notable'] = node('striker_flurry_notable', 'Feint', 'ON_EVADE: +12% Crit Chance for 3s. +5% Dodge Chance, +5% Move Speed.',
    [P('DODGE_CHANCE', 5.0), P('MOVEMENT_SPEED_PERCENT', 5.0)],
    conditional=cond('ON_EVADE', 3.0, 'REFRESH', [{'stat': 'CRITICAL_CHANCE', 'value': 12.0, 'modifierType': 'FLAT'}]))

# Striker Synergies
nodes['striker_synergy_1'] = node('striker_synergy_1', 'Quicksilver Reflexes', 'Per 3 Striker nodes: +1% Attack Speed (max 10%)', [], synergy=syn('BRANCH_COUNT', 'STRIKER', 3, 'ATTACK_SPEED_PERCENT', 1.0, 10.0))
nodes['striker_synergy_2'] = node('striker_synergy_2', 'Precision Training', 'Per 3 Striker nodes: +1% Crit Chance (max 10%)', [], synergy=syn('BRANCH_COUNT', 'STRIKER', 3, 'CRITICAL_CHANCE', 1.0, 10.0))
nodes['striker_synergy_3'] = node('striker_synergy_3', 'Fleet Foot', 'Per 3 Striker nodes: +1% Move Speed (max 10%)', [], synergy=syn('BRANCH_COUNT', 'STRIKER', 3, 'MOVEMENT_SPEED_PERCENT', 1.0, 10.0))
nodes['striker_synergy_4'] = node('striker_synergy_4', 'Shadow Step', 'Per 3 Striker nodes: +1% Dodge Chance (max 10%)', [], synergy=syn('BRANCH_COUNT', 'STRIKER', 3, 'DODGE_CHANCE', 1.0, 10.0))
nodes['striker_synergy_hub'] = node('striker_synergy_hub', 'Nexus of Striker', '+0.1% Attack Speed per (Fire+Wind+Lightning) attribute point',
    [], synergy=syn('ATTRIBUTE_SUM_SCALING', 'STRIKER', 1, 'ATTACK_SPEED_PERCENT', 0.1, 999.0))

# Striker Keystones
nodes['striker_keystone_1'] = node('striker_keystone_1', 'Blade Dance',
    'On Evade: your next hit within 2s is a guaranteed Critical Strike with +25% bonus Crit Multiplier.',
    [F('EVADE_GUARANTEED_CRIT', 1.0), F('EVADE_CRIT_MULT_BONUS', 25.0)],
    drawbacks=[F('ARMOR', -15.0), P('MAX_HEALTH_PERCENT', -10.0)])
nodes['striker_keystone_2'] = node('striker_keystone_2', 'Momentum',
    'Consecutive hits within 2s: +3% All Damage per stack, up to 10 stacks (+30%).',
    [F('CONSECUTIVE_HIT_BONUS', 3.0)],
    drawbacks=[P('MAX_HEALTH_PERCENT', -20.0)])

# =============================================================================
# WARDEN — Iron Ranger (Fire + Wind + Earth)
# =============================================================================

nodes['warden_entry'] = node('warden_entry', 'Path of Warden', '+6 Armor', [F('ARMOR', 6.0)])

# Cluster 1: Garrison (warden_garrison) — Lane 1 Inner — Armored Defense (Earth)
nodes['warden_garrison_1'] = node('warden_garrison_1', 'Garrison I', '+8 Armor', [F('ARMOR', 8.0)])
nodes['warden_garrison_2'] = node('warden_garrison_2', 'Garrison II', '+6% Max HP', [P('MAX_HEALTH_PERCENT', 6.0)])
nodes['warden_garrison_branch'] = node('warden_garrison_branch', 'Garrison Path', '+5 Armor, +4% Max HP', [F('ARMOR', 5.0), P('MAX_HEALTH_PERCENT', 4.0)])
nodes['warden_garrison_3'] = node('warden_garrison_3', 'Garrison III', '+8 Armor', [F('ARMOR', 8.0)])
nodes['warden_garrison_4'] = node('warden_garrison_4', 'Garrison IV', '+6% Max HP', [P('MAX_HEALTH_PERCENT', 6.0)])
nodes['warden_garrison_notable'] = node('warden_garrison_notable', 'Fortress', '+10 Armor, +8% Max HP, +5% Block Chance', [F('ARMOR', 10.0), P('MAX_HEALTH_PERCENT', 8.0), F('BLOCK_CHANCE', 5.0)])

# Cluster 2: Outrider (warden_outrider) — Lane 2 Inner — Ranged Precision (Wind)
nodes['warden_outrider_1'] = node('warden_outrider_1', 'Outrider I', '+6% Projectile Damage', [P('PROJECTILE_DAMAGE_PERCENT', 6.0)])
nodes['warden_outrider_2'] = node('warden_outrider_2', 'Outrider II', '+8 Accuracy', [F('ACCURACY', 8.0)])
nodes['warden_outrider_branch'] = node('warden_outrider_branch', 'Outrider Path', '+4% Projectile Damage, +5 Accuracy', [P('PROJECTILE_DAMAGE_PERCENT', 4.0), F('ACCURACY', 5.0)])
nodes['warden_outrider_3'] = node('warden_outrider_3', 'Outrider III', '+6% Projectile Damage', [P('PROJECTILE_DAMAGE_PERCENT', 6.0)])
nodes['warden_outrider_4'] = node('warden_outrider_4', 'Outrider IV', '+8 Accuracy', [F('ACCURACY', 8.0)])
nodes['warden_outrider_notable'] = node('warden_outrider_notable', 'Iron Rain', '+10% Projectile Damage, +8 Accuracy, +5% All Damage', [P('PROJECTILE_DAMAGE_PERCENT', 10.0), F('ACCURACY', 8.0), P('ALL_DAMAGE_PERCENT', 5.0)])

# Cluster 3: Ironclad (warden_ironclad) — Lane 1 Outer — Power Shots (Fire)
nodes['warden_ironclad_1'] = node('warden_ironclad_1', 'Ironclad I', '+6% Charged Attack Damage', [P('CHARGED_ATTACK_DAMAGE_PERCENT', 6.0)])
nodes['warden_ironclad_2'] = node('warden_ironclad_2', 'Ironclad II', '+6% Physical Damage', [P('PHYSICAL_DAMAGE_PERCENT', 6.0)])
nodes['warden_ironclad_branch'] = node('warden_ironclad_branch', 'Ironclad Path', '+4% Charged Attack Damage, +4% Physical Damage', [P('CHARGED_ATTACK_DAMAGE_PERCENT', 4.0), P('PHYSICAL_DAMAGE_PERCENT', 4.0)])
nodes['warden_ironclad_3'] = node('warden_ironclad_3', 'Ironclad III', '+6% Charged Attack Damage', [P('CHARGED_ATTACK_DAMAGE_PERCENT', 6.0)])
nodes['warden_ironclad_4'] = node('warden_ironclad_4', 'Ironclad IV', '+6% Physical Damage', [P('PHYSICAL_DAMAGE_PERCENT', 6.0)])
nodes['warden_ironclad_notable'] = node('warden_ironclad_notable', 'Power Shot', '+10% Charged Attack Damage, +8% Physical Damage, +6 Crit Multiplier', [P('CHARGED_ATTACK_DAMAGE_PERCENT', 10.0), P('PHYSICAL_DAMAGE_PERCENT', 8.0), F('CRITICAL_MULTIPLIER', 6.0)])

# Cluster 4: Palisade (warden_palisade) — Lane 2 Outer — Fortified Ranged (Earth+Wind)
nodes['warden_palisade_1'] = node('warden_palisade_1', 'Palisade I', '+3% Block Chance', [F('BLOCK_CHANCE', 3.0)])
nodes['warden_palisade_2'] = node('warden_palisade_2', 'Palisade II', '+6% Projectile Damage', [P('PROJECTILE_DAMAGE_PERCENT', 6.0)])
nodes['warden_palisade_branch'] = node('warden_palisade_branch', 'Palisade Path', '+2% Block Chance, +4% Projectile Damage', [F('BLOCK_CHANCE', 2.0), P('PROJECTILE_DAMAGE_PERCENT', 4.0)])
nodes['warden_palisade_3'] = node('warden_palisade_3', 'Palisade III', '+3% Block Chance', [F('BLOCK_CHANCE', 3.0)])
nodes['warden_palisade_4'] = node('warden_palisade_4', 'Palisade IV', '+6% Projectile Damage', [P('PROJECTILE_DAMAGE_PERCENT', 6.0)])
nodes['warden_palisade_notable'] = node('warden_palisade_notable', 'Fortified Position', 'ON_BLOCK: +10% Projectile Damage for 3s. +5% Block Chance, +5% Projectile Damage.',
    [F('BLOCK_CHANCE', 5.0), P('PROJECTILE_DAMAGE_PERCENT', 5.0)],
    conditional=cond('ON_BLOCK', 3.0, 'REFRESH', [{'stat': 'PROJECTILE_DAMAGE_PERCENT', 'value': 10.0, 'modifierType': 'PERCENT'}]))

# Warden Synergies
nodes['warden_synergy_1'] = node('warden_synergy_1', 'Fortified Range', 'Per 3 Warden nodes: +2% Projectile Damage (max 20%)', [], synergy=syn('BRANCH_COUNT', 'WARDEN', 3, 'PROJECTILE_DAMAGE_PERCENT', 2.0, 20.0))
nodes['warden_synergy_2'] = node('warden_synergy_2', 'Power Draw', 'Per 3 Warden nodes: +2% Charged Attack Damage (max 20%)', [], synergy=syn('BRANCH_COUNT', 'WARDEN', 3, 'CHARGED_ATTACK_DAMAGE_PERCENT', 2.0, 20.0))
nodes['warden_synergy_3'] = node('warden_synergy_3', 'Eagle Eye', 'Per 3 Warden nodes: +5 Accuracy (max 50)', [], synergy=syn('BRANCH_COUNT', 'WARDEN', 3, 'ACCURACY', 5.0, 50.0))
nodes['warden_synergy_4'] = node('warden_synergy_4', 'Heavy Armor', 'Per 3 Warden nodes: +2 Armor (max 20)', [], synergy=syn('BRANCH_COUNT', 'WARDEN', 3, 'ARMOR', 2.0, 20.0))
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
    drawbacks=[P('SPELL_DAMAGE_PERCENT', -10.0), P('ATTACK_SPEED_PERCENT', -15.0)])

# =============================================================================
# WARLOCK — Dark Caster (Water + Void + Lightning)
# =============================================================================

nodes['warlock_entry'] = node('warlock_entry', 'Path of Warlock', '+4% Spell Damage', [P('SPELL_DAMAGE_PERCENT', 4.0)])

# Cluster 1: Hex (warlock_hex) — Lane 1 Inner — Spell Crits (Water+Lightning)
nodes['warlock_hex_1'] = node('warlock_hex_1', 'Hex I', '+6% Spell Damage', [P('SPELL_DAMAGE_PERCENT', 6.0)])
nodes['warlock_hex_2'] = node('warlock_hex_2', 'Hex II', '+5% Crit Chance', [F('CRITICAL_CHANCE', 5.0)])
nodes['warlock_hex_branch'] = node('warlock_hex_branch', 'Hex Path', '+4% Spell Damage, +3% Crit Chance', [P('SPELL_DAMAGE_PERCENT', 4.0), F('CRITICAL_CHANCE', 3.0)])
nodes['warlock_hex_3'] = node('warlock_hex_3', 'Hex III', '+6% Spell Damage', [P('SPELL_DAMAGE_PERCENT', 6.0)])
nodes['warlock_hex_4'] = node('warlock_hex_4', 'Hex IV', '+5% Crit Chance', [F('CRITICAL_CHANCE', 5.0)])
nodes['warlock_hex_notable'] = node('warlock_hex_notable', 'Dark Arcana', '+10% Spell Damage, +8% Crit Chance, +5% All Damage', [P('SPELL_DAMAGE_PERCENT', 10.0), F('CRITICAL_CHANCE', 8.0), P('ALL_DAMAGE_PERCENT', 5.0)])

# Cluster 2: Ritual (warlock_ritual) — Lane 2 Inner — Dark Sustain (Water+Void)
nodes['warlock_ritual_1'] = node('warlock_ritual_1', 'Ritual I', '+8 Max Mana', [F('MAX_MANA', 8.0)])
nodes['warlock_ritual_2'] = node('warlock_ritual_2', 'Ritual II', '+0.5% Life Steal', [F('LIFE_STEAL', 0.5)])
nodes['warlock_ritual_branch'] = node('warlock_ritual_branch', 'Ritual Path', '+5 Max Mana, +0.3% Life Steal', [F('MAX_MANA', 5.0), F('LIFE_STEAL', 0.3)])
nodes['warlock_ritual_3'] = node('warlock_ritual_3', 'Ritual III', '+8 Max Mana', [F('MAX_MANA', 8.0)])
nodes['warlock_ritual_4'] = node('warlock_ritual_4', 'Ritual IV', '+0.5% Life Steal', [F('LIFE_STEAL', 0.5)])
nodes['warlock_ritual_notable'] = node('warlock_ritual_notable', 'Mind Drain', '+12 Max Mana, +1% Life Steal, +3% Mana Leech, +3 Mana Regen/s', [F('MAX_MANA', 12.0), F('LIFE_STEAL', 1.0), F('MANA_LEECH', 3.0), F('MANA_REGEN', 3.0)])

# Cluster 3: Malice (warlock_malice) — Lane 1 Outer — Fast Casting (Lightning+Water)
nodes['warlock_malice_1'] = node('warlock_malice_1', 'Malice I', '+4% Attack Speed', [P('ATTACK_SPEED_PERCENT', 4.0)])
nodes['warlock_malice_2'] = node('warlock_malice_2', 'Malice II', '+6% Spell Damage', [P('SPELL_DAMAGE_PERCENT', 6.0)])
nodes['warlock_malice_branch'] = node('warlock_malice_branch', 'Malice Path', '+3% Attack Speed, +4% Spell Damage', [P('ATTACK_SPEED_PERCENT', 3.0), P('SPELL_DAMAGE_PERCENT', 4.0)])
nodes['warlock_malice_3'] = node('warlock_malice_3', 'Malice III', '+4% Attack Speed', [P('ATTACK_SPEED_PERCENT', 4.0)])
nodes['warlock_malice_4'] = node('warlock_malice_4', 'Malice IV', '+6% Spell Damage', [P('SPELL_DAMAGE_PERCENT', 6.0)])
nodes['warlock_malice_notable'] = node('warlock_malice_notable', 'Eldritch Blast', '+8% Spell Damage, +8% Attack Speed, +5% All Damage', [P('SPELL_DAMAGE_PERCENT', 8.0), P('ATTACK_SPEED_PERCENT', 8.0), P('ALL_DAMAGE_PERCENT', 5.0)])

# Cluster 4: Damnation (warlock_damnation) — Lane 2 Outer — Void Specialist (Void)
nodes['warlock_damnation_1'] = node('warlock_damnation_1', 'Damnation I', '+5% Void Damage', [P('VOID_DAMAGE_PERCENT', 5.0)])
nodes['warlock_damnation_2'] = node('warlock_damnation_2', 'Damnation II', '+5% DoT Damage', [P('DOT_DAMAGE_PERCENT', 5.0)])
nodes['warlock_damnation_branch'] = node('warlock_damnation_branch', 'Damnation Path', '+3% Void Damage, +3% DoT Damage', [P('VOID_DAMAGE_PERCENT', 3.0), P('DOT_DAMAGE_PERCENT', 3.0)])
nodes['warlock_damnation_3'] = node('warlock_damnation_3', 'Damnation III', '+5% Void Damage', [P('VOID_DAMAGE_PERCENT', 5.0)])
nodes['warlock_damnation_4'] = node('warlock_damnation_4', 'Damnation IV', '+5% DoT Damage', [P('DOT_DAMAGE_PERCENT', 5.0)])
nodes['warlock_damnation_notable'] = node('warlock_damnation_notable', 'Cursed Knowledge', '+10% Void Damage, +8% DoT Damage, +6 Void Penetration', [P('VOID_DAMAGE_PERCENT', 10.0), P('DOT_DAMAGE_PERCENT', 8.0), F('VOID_PENETRATION', 6.0)])

# Warlock Synergies
nodes['warlock_synergy_1'] = node('warlock_synergy_1', 'Arcane Corruption', 'Per 3 Warlock nodes: +2% Spell Damage (max 20%)', [], synergy=syn('BRANCH_COUNT', 'WARLOCK', 3, 'SPELL_DAMAGE_PERCENT', 2.0, 20.0))
nodes['warlock_synergy_2'] = node('warlock_synergy_2', 'Eldritch Power', 'Per 3 Warlock nodes: +3 Max Mana (max 30)', [], synergy=syn('BRANCH_COUNT', 'WARLOCK', 3, 'MAX_MANA', 3.0, 30.0))
nodes['warlock_synergy_3'] = node('warlock_synergy_3', 'Dark Resonance', 'Per 3 Warlock nodes: +2% Void Damage (max 20%)', [], synergy=syn('BRANCH_COUNT', 'WARLOCK', 3, 'VOID_DAMAGE_PERCENT', 2.0, 20.0))
nodes['warlock_synergy_4'] = node('warlock_synergy_4', 'Mind Siphon', 'Per 3 Warlock nodes: +1% Mana Leech (max 10%)', [], synergy=syn('BRANCH_COUNT', 'WARLOCK', 3, 'MANA_LEECH', 1.0, 10.0))
nodes['warlock_synergy_hub'] = node('warlock_synergy_hub', 'Nexus of Warlock', '+0.15% Spell Damage per (Water+Void+Lightning) attribute point',
    [], synergy=syn('ATTRIBUTE_SUM_SCALING', 'WARLOCK', 1, 'SPELL_DAMAGE_PERCENT', 0.15, 999.0))

# Warlock Keystones
nodes['warlock_keystone_1'] = node('warlock_keystone_1', 'Soul Siphon',
    'On Spell Crit Kill: restore 100% of the spell\'s Mana cost and heal 8% of damage dealt. -15% Max HP, -10 Armor.',
    [F('SPELL_KILL_MANA_RESTORE', 100.0), F('SPELL_CRIT_HEAL_PERCENT', 8.0)],
    drawbacks=[P('MAX_HEALTH_PERCENT', -15.0), F('ARMOR', -10.0)])
nodes['warlock_keystone_2'] = node('warlock_keystone_2', 'Arcane Overload',
    '25% chance for spells to echo, dealing 50% of the damage again as Void.',
    [F('SPELL_ECHO_CHANCE', 25.0), P('SPELL_DAMAGE_PERCENT', 10.0)],
    drawbacks=[P('MAX_MANA_PERCENT', -10.0), P('MANA_COST_PERCENT', 15.0)])

# =============================================================================
# LICH — Undying Sorcerer (Water + Void + Earth)
# =============================================================================

nodes['lich_entry'] = node('lich_entry', 'Path of Lich', '+4% Spell Damage', [P('SPELL_DAMAGE_PERCENT', 4.0)])

# Cluster 1: Grasp (lich_grasp) — Lane 1 Inner — Spell Power (Water+Void)
nodes['lich_grasp_1'] = node('lich_grasp_1', 'Grasp I', '+6% Spell Damage', [P('SPELL_DAMAGE_PERCENT', 6.0)])
nodes['lich_grasp_2'] = node('lich_grasp_2', 'Grasp II', '+5% All Damage', [P('ALL_DAMAGE_PERCENT', 5.0)])
nodes['lich_grasp_branch'] = node('lich_grasp_branch', 'Grasp Path', '+4% Spell Damage, +3% All Damage', [P('SPELL_DAMAGE_PERCENT', 4.0), P('ALL_DAMAGE_PERCENT', 3.0)])
nodes['lich_grasp_3'] = node('lich_grasp_3', 'Grasp III', '+6% Spell Damage', [P('SPELL_DAMAGE_PERCENT', 6.0)])
nodes['lich_grasp_4'] = node('lich_grasp_4', 'Grasp IV', '+5% All Damage', [P('ALL_DAMAGE_PERCENT', 5.0)])
nodes['lich_grasp_notable'] = node('lich_grasp_notable', "Death's Embrace", '+10% Spell Damage, +8% All Damage, +5% DoT Damage', [P('SPELL_DAMAGE_PERCENT', 10.0), P('ALL_DAMAGE_PERCENT', 8.0), P('DOT_DAMAGE_PERCENT', 5.0)])

# Cluster 2: Crypt (lich_crypt) — Lane 2 Inner — Necrotic Defense (Earth)
nodes['lich_crypt_1'] = node('lich_crypt_1', 'Crypt I', '+6% Max HP', [P('MAX_HEALTH_PERCENT', 6.0)])
nodes['lich_crypt_2'] = node('lich_crypt_2', 'Crypt II', '+8 Armor', [F('ARMOR', 8.0)])
nodes['lich_crypt_branch'] = node('lich_crypt_branch', 'Crypt Path', '+4% Max HP, +5 Armor', [P('MAX_HEALTH_PERCENT', 4.0), F('ARMOR', 5.0)])
nodes['lich_crypt_3'] = node('lich_crypt_3', 'Crypt III', '+6% Max HP', [P('MAX_HEALTH_PERCENT', 6.0)])
nodes['lich_crypt_4'] = node('lich_crypt_4', 'Crypt IV', '+8 Armor', [F('ARMOR', 8.0)])
nodes['lich_crypt_notable'] = node('lich_crypt_notable', 'Necrotic Armor', '+8% Max HP, +8 Armor, +5% Physical Resistance', [P('MAX_HEALTH_PERCENT', 8.0), F('ARMOR', 8.0), P('PHYSICAL_RESISTANCE', 5.0)])

# Cluster 3: Requiem (lich_requiem) — Lane 1 Outer — Shield Sustain (Water+Void)
nodes['lich_requiem_1'] = node('lich_requiem_1', 'Requiem I', '+6% Energy Shield', [P('ENERGY_SHIELD_PERCENT', 6.0)])
nodes['lich_requiem_2'] = node('lich_requiem_2', 'Requiem II', '+0.5% Life Steal', [F('LIFE_STEAL', 0.5)])
nodes['lich_requiem_branch'] = node('lich_requiem_branch', 'Requiem Path', '+4% Energy Shield, +0.3% Life Steal', [P('ENERGY_SHIELD_PERCENT', 4.0), F('LIFE_STEAL', 0.3)])
nodes['lich_requiem_3'] = node('lich_requiem_3', 'Requiem III', '+6% Energy Shield', [P('ENERGY_SHIELD_PERCENT', 6.0)])
nodes['lich_requiem_4'] = node('lich_requiem_4', 'Requiem IV', '+0.5% Life Steal', [F('LIFE_STEAL', 0.5)])
nodes['lich_requiem_notable'] = node('lich_requiem_notable', 'Soul Anchor', '+12% Energy Shield, +1.5% Life Steal, +5 ES Regen/s', [P('ENERGY_SHIELD_PERCENT', 12.0), F('LIFE_STEAL', 1.5), F('ENERGY_SHIELD_REGEN', 5.0)])

# Cluster 4: Decay (lich_decay) — Lane 2 Outer — DoT Specialist (Void)
nodes['lich_decay_1'] = node('lich_decay_1', 'Decay I', '+5% DoT Damage', [P('DOT_DAMAGE_PERCENT', 5.0)])
nodes['lich_decay_2'] = node('lich_decay_2', 'Decay II', '+5% Status Duration', [P('STATUS_EFFECT_DURATION', 5.0)])
nodes['lich_decay_branch'] = node('lich_decay_branch', 'Decay Path', '+3% DoT Damage, +3% Status Duration', [P('DOT_DAMAGE_PERCENT', 3.0), P('STATUS_EFFECT_DURATION', 3.0)])
nodes['lich_decay_3'] = node('lich_decay_3', 'Decay III', '+5% DoT Damage', [P('DOT_DAMAGE_PERCENT', 5.0)])
nodes['lich_decay_4'] = node('lich_decay_4', 'Decay IV', '+5% Status Duration', [P('STATUS_EFFECT_DURATION', 5.0)])
nodes['lich_decay_notable'] = node('lich_decay_notable', 'Withering Presence', '+10% DoT Damage, +8% Status Duration, +5% Mana as Damage Buffer', [P('DOT_DAMAGE_PERCENT', 10.0), P('STATUS_EFFECT_DURATION', 8.0), P('MANA_AS_DAMAGE_BUFFER', 5.0)])

# Lich Synergies
nodes['lich_synergy_1'] = node('lich_synergy_1', 'Necrotic Bond', 'Per 3 Lich nodes: +2% Energy Shield (max 20%)', [], synergy=syn('BRANCH_COUNT', 'LICH', 3, 'ENERGY_SHIELD_PERCENT', 2.0, 20.0))
nodes['lich_synergy_2'] = node('lich_synergy_2', 'Plague Growth', 'Per 3 Lich nodes: +2% DoT Damage (max 20%)', [], synergy=syn('BRANCH_COUNT', 'LICH', 3, 'DOT_DAMAGE_PERCENT', 2.0, 20.0))
nodes['lich_synergy_3'] = node('lich_synergy_3', 'Soul Barrier', 'Per 3 Lich nodes: +1% Mana as Damage Buffer (max 10%)', [], synergy=syn('BRANCH_COUNT', 'LICH', 3, 'MANA_AS_DAMAGE_BUFFER', 1.0, 10.0))
nodes['lich_synergy_4'] = node('lich_synergy_4', 'Undying Will', 'Per 3 Lich nodes: +2% Max HP (max 20%)', [], synergy=syn('BRANCH_COUNT', 'LICH', 3, 'MAX_HEALTH_PERCENT', 2.0, 20.0))
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
    drawbacks=[F('EVASION', -15.0), P('ATTACK_SPEED_PERCENT', -10.0)])

# =============================================================================
# TEMPEST — Battle Mage (Water + Wind + Lightning)
# =============================================================================

nodes['tempest_entry'] = node('tempest_entry', 'Path of Tempest', '+4% Spell Damage', [P('SPELL_DAMAGE_PERCENT', 4.0)])

# Cluster 1: Squall (tempest_squall) — Lane 1 Inner — Fast Casting (Water+Lightning)
nodes['tempest_squall_1'] = node('tempest_squall_1', 'Squall I', '+6% Spell Damage', [P('SPELL_DAMAGE_PERCENT', 6.0)])
nodes['tempest_squall_2'] = node('tempest_squall_2', 'Squall II', '+4% Attack Speed', [P('ATTACK_SPEED_PERCENT', 4.0)])
nodes['tempest_squall_branch'] = node('tempest_squall_branch', 'Squall Path', '+4% Spell Damage, +3% Attack Speed', [P('SPELL_DAMAGE_PERCENT', 4.0), P('ATTACK_SPEED_PERCENT', 3.0)])
nodes['tempest_squall_3'] = node('tempest_squall_3', 'Squall III', '+6% Spell Damage', [P('SPELL_DAMAGE_PERCENT', 6.0)])
nodes['tempest_squall_4'] = node('tempest_squall_4', 'Squall IV', '+4% Attack Speed', [P('ATTACK_SPEED_PERCENT', 4.0)])
nodes['tempest_squall_notable'] = node('tempest_squall_notable', 'Swift Cast', '+8% Spell Damage, +8% Attack Speed, +5% Crit Chance', [P('SPELL_DAMAGE_PERCENT', 8.0), P('ATTACK_SPEED_PERCENT', 8.0), F('CRITICAL_CHANCE', 5.0)])

# Cluster 2: Tailwind (tempest_tailwind) — Lane 2 Inner — Mobile Ranged (Wind+Lightning)
nodes['tempest_tailwind_1'] = node('tempest_tailwind_1', 'Tailwind I', '+3% Move Speed', [P('MOVEMENT_SPEED_PERCENT', 3.0)])
nodes['tempest_tailwind_2'] = node('tempest_tailwind_2', 'Tailwind II', '+6% Projectile Damage', [P('PROJECTILE_DAMAGE_PERCENT', 6.0)])
nodes['tempest_tailwind_branch'] = node('tempest_tailwind_branch', 'Tailwind Path', '+2% Move Speed, +4% Projectile Damage', [P('MOVEMENT_SPEED_PERCENT', 2.0), P('PROJECTILE_DAMAGE_PERCENT', 4.0)])
nodes['tempest_tailwind_3'] = node('tempest_tailwind_3', 'Tailwind III', '+3% Move Speed', [P('MOVEMENT_SPEED_PERCENT', 3.0)])
nodes['tempest_tailwind_4'] = node('tempest_tailwind_4', 'Tailwind IV', '+6% Projectile Damage', [P('PROJECTILE_DAMAGE_PERCENT', 6.0)])
nodes['tempest_tailwind_notable'] = node('tempest_tailwind_notable', 'Arcane Gust', '+10% Projectile Damage, +5% Move Speed, +5% Spell Damage', [P('PROJECTILE_DAMAGE_PERCENT', 10.0), P('MOVEMENT_SPEED_PERCENT', 5.0), P('SPELL_DAMAGE_PERCENT', 5.0)])

# Cluster 3: Cyclone (tempest_cyclone) — Lane 1 Outer — Spell Crits (Water+Lightning)
nodes['tempest_cyclone_1'] = node('tempest_cyclone_1', 'Cyclone I', '+5% Crit Chance', [F('CRITICAL_CHANCE', 5.0)])
nodes['tempest_cyclone_2'] = node('tempest_cyclone_2', 'Cyclone II', '+6% Spell Damage', [P('SPELL_DAMAGE_PERCENT', 6.0)])
nodes['tempest_cyclone_branch'] = node('tempest_cyclone_branch', 'Cyclone Path', '+3% Crit Chance, +4% Spell Damage', [F('CRITICAL_CHANCE', 3.0), P('SPELL_DAMAGE_PERCENT', 4.0)])
nodes['tempest_cyclone_3'] = node('tempest_cyclone_3', 'Cyclone III', '+5% Crit Chance', [F('CRITICAL_CHANCE', 5.0)])
nodes['tempest_cyclone_4'] = node('tempest_cyclone_4', 'Cyclone IV', '+6% Spell Damage', [P('SPELL_DAMAGE_PERCENT', 6.0)])
nodes['tempest_cyclone_notable'] = node('tempest_cyclone_notable', 'Eye of the Storm', '+8% Crit Chance, +8% Spell Damage, +5% All Damage', [F('CRITICAL_CHANCE', 8.0), P('SPELL_DAMAGE_PERCENT', 8.0), P('ALL_DAMAGE_PERCENT', 5.0)])

# Cluster 4: Maelstrom (tempest_maelstrom) — Lane 2 Outer — Evasive Caster (Wind)
nodes['tempest_maelstrom_1'] = node('tempest_maelstrom_1', 'Maelstrom I', '+10 Evasion', [F('EVASION', 10.0)])
nodes['tempest_maelstrom_2'] = node('tempest_maelstrom_2', 'Maelstrom II', '+6% Energy Shield', [P('ENERGY_SHIELD_PERCENT', 6.0)])
nodes['tempest_maelstrom_branch'] = node('tempest_maelstrom_branch', 'Maelstrom Path', '+6 Evasion, +4% Energy Shield', [F('EVASION', 6.0), P('ENERGY_SHIELD_PERCENT', 4.0)])
nodes['tempest_maelstrom_3'] = node('tempest_maelstrom_3', 'Maelstrom III', '+10 Evasion', [F('EVASION', 10.0)])
nodes['tempest_maelstrom_4'] = node('tempest_maelstrom_4', 'Maelstrom IV', '+6% Energy Shield', [P('ENERGY_SHIELD_PERCENT', 6.0)])
nodes['tempest_maelstrom_notable'] = node('tempest_maelstrom_notable', 'Windborne', '+10 Evasion, +8% Energy Shield, +3% Dodge Chance', [F('EVASION', 10.0), P('ENERGY_SHIELD_PERCENT', 8.0), P('DODGE_CHANCE', 3.0)])

# Tempest Synergies
nodes['tempest_synergy_1'] = node('tempest_synergy_1', 'Storm Surge', 'Per 3 Tempest nodes: +2% Spell Damage (max 20%)', [], synergy=syn('BRANCH_COUNT', 'TEMPEST', 3, 'SPELL_DAMAGE_PERCENT', 2.0, 20.0))
nodes['tempest_synergy_2'] = node('tempest_synergy_2', 'Swiftness', 'Per 3 Tempest nodes: +1% Attack Speed (max 10%)', [], synergy=syn('BRANCH_COUNT', 'TEMPEST', 3, 'ATTACK_SPEED_PERCENT', 1.0, 10.0))
nodes['tempest_synergy_3'] = node('tempest_synergy_3', 'Arcane Flow', 'Per 3 Tempest nodes: +1% All Damage (max 10%)', [], synergy=syn('BRANCH_COUNT', 'TEMPEST', 3, 'ALL_DAMAGE_PERCENT', 1.0, 10.0))
nodes['tempest_synergy_4'] = node('tempest_synergy_4', 'Wind Runner', 'Per 3 Tempest nodes: +1% Move Speed (max 10%)', [], synergy=syn('BRANCH_COUNT', 'TEMPEST', 3, 'MOVEMENT_SPEED_PERCENT', 1.0, 10.0))
nodes['tempest_synergy_hub'] = node('tempest_synergy_hub', 'Nexus of Tempest', '+0.1% All Damage per (Water+Wind+Lightning) attribute point',
    [], synergy=syn('ATTRIBUTE_SUM_SCALING', 'TEMPEST', 1, 'ALL_DAMAGE_PERCENT', 0.1, 999.0))

# Tempest Keystones
nodes['tempest_keystone_1'] = node('tempest_keystone_1', 'Arcane Velocity',
    '50% of your Attack Speed bonus is converted to Spell Damage.',
    [F('ATK_SPEED_TO_SPELL_POWER', 50.0), P('ATTACK_SPEED_PERCENT', 10.0)],
    drawbacks=[F('ARMOR', -15.0), F('BLOCK_CHANCE', -10.0)])
nodes['tempest_keystone_2'] = node('tempest_keystone_2', 'Storm Runner',
    'While moving: +15% Spell Damage, +3 Mana Regen/s. +10% Move Speed. -20% Max HP.',
    [P('MOVEMENT_SPEED_PERCENT', 10.0)],
    conditional=cond('WHILE_MOVING', 0.0, 'REFRESH', [{'stat': 'SPELL_DAMAGE_PERCENT', 'value': 15.0, 'modifierType': 'PERCENT'}, {'stat': 'MANA_REGEN', 'value': 3.0, 'modifierType': 'FLAT'}]),
    drawbacks=[P('MAX_HEALTH_PERCENT', -20.0)])

# =============================================================================
# SENTINEL — Guardian (Water + Wind + Earth)
# =============================================================================

nodes['sentinel_entry'] = node('sentinel_entry', 'Path of Sentinel', '+3% Block Chance', [F('BLOCK_CHANCE', 3.0)])

# Cluster 1: Aegis (sentinel_aegis) — Lane 1 Inner — Layered Defense (Water+Earth)
nodes['sentinel_aegis_1'] = node('sentinel_aegis_1', 'Aegis I', '+6% Energy Shield', [P('ENERGY_SHIELD_PERCENT', 6.0)])
nodes['sentinel_aegis_2'] = node('sentinel_aegis_2', 'Aegis II', '+8 Armor', [F('ARMOR', 8.0)])
nodes['sentinel_aegis_branch'] = node('sentinel_aegis_branch', 'Aegis Path', '+4% Energy Shield, +5 Armor', [P('ENERGY_SHIELD_PERCENT', 4.0), F('ARMOR', 5.0)])
nodes['sentinel_aegis_3'] = node('sentinel_aegis_3', 'Aegis III', '+6% Energy Shield', [P('ENERGY_SHIELD_PERCENT', 6.0)])
nodes['sentinel_aegis_4'] = node('sentinel_aegis_4', 'Aegis IV', '+8 Armor', [F('ARMOR', 8.0)])
nodes['sentinel_aegis_notable'] = node('sentinel_aegis_notable', "Guardian's Ward", '+10% Energy Shield, +10 Armor, +5% Block Damage Reduction', [P('ENERGY_SHIELD_PERCENT', 10.0), F('ARMOR', 10.0), P('BLOCK_DAMAGE_REDUCTION', 5.0)])

# Cluster 2: Vigilance (sentinel_vigilance) — Lane 2 Inner — Evasive Awareness (Wind)
nodes['sentinel_vigilance_1'] = node('sentinel_vigilance_1', 'Vigilance I', '+10 Evasion', [F('EVASION', 10.0)])
nodes['sentinel_vigilance_2'] = node('sentinel_vigilance_2', 'Vigilance II', '+8 Accuracy', [F('ACCURACY', 8.0)])
nodes['sentinel_vigilance_branch'] = node('sentinel_vigilance_branch', 'Vigilance Path', '+6 Evasion, +5 Accuracy', [F('EVASION', 6.0), F('ACCURACY', 5.0)])
nodes['sentinel_vigilance_3'] = node('sentinel_vigilance_3', 'Vigilance III', '+10 Evasion', [F('EVASION', 10.0)])
nodes['sentinel_vigilance_4'] = node('sentinel_vigilance_4', 'Vigilance IV', '+8 Accuracy', [F('ACCURACY', 8.0)])
nodes['sentinel_vigilance_notable'] = node('sentinel_vigilance_notable', 'Keen Senses', '+10 Evasion, +10 Accuracy, +3% Crit Nullify Chance', [F('EVASION', 10.0), F('ACCURACY', 10.0), P('CRIT_NULLIFY_CHANCE', 3.0)])

# Cluster 3: Restoration (sentinel_restoration) — Lane 1 Outer — Sustain Tank (Water+Earth)
nodes['sentinel_restoration_1'] = node('sentinel_restoration_1', 'Restoration I', '+6% Max HP', [P('MAX_HEALTH_PERCENT', 6.0)])
nodes['sentinel_restoration_2'] = node('sentinel_restoration_2', 'Restoration II', '+6% Energy Shield', [P('ENERGY_SHIELD_PERCENT', 6.0)])
nodes['sentinel_restoration_branch'] = node('sentinel_restoration_branch', 'Restoration Path', '+4% Max HP, +4% Energy Shield', [P('MAX_HEALTH_PERCENT', 4.0), P('ENERGY_SHIELD_PERCENT', 4.0)])
nodes['sentinel_restoration_3'] = node('sentinel_restoration_3', 'Restoration III', '+6% Max HP', [P('MAX_HEALTH_PERCENT', 6.0)])
nodes['sentinel_restoration_4'] = node('sentinel_restoration_4', 'Restoration IV', '+6% Energy Shield', [P('ENERGY_SHIELD_PERCENT', 6.0)])
nodes['sentinel_restoration_notable'] = node('sentinel_restoration_notable', 'Restorative Aura', '+8% Max HP, +8% Energy Shield, +5% Health Recovery', [P('MAX_HEALTH_PERCENT', 8.0), P('ENERGY_SHIELD_PERCENT', 8.0), P('HEALTH_RECOVERY_PERCENT', 5.0)])

# Cluster 4: Haven (sentinel_haven) — Lane 2 Outer — Elemental Defense (specialist)
nodes['sentinel_haven_1'] = node('sentinel_haven_1', 'Haven I', '+3% Block Chance', [F('BLOCK_CHANCE', 3.0)])
nodes['sentinel_haven_2'] = node('sentinel_haven_2', 'Haven II', '+3% Dodge Chance', [P('DODGE_CHANCE', 3.0)])
nodes['sentinel_haven_branch'] = node('sentinel_haven_branch', 'Haven Path', '+2% Block Chance, +2% Dodge Chance', [F('BLOCK_CHANCE', 2.0), P('DODGE_CHANCE', 2.0)])
nodes['sentinel_haven_3'] = node('sentinel_haven_3', 'Haven III', '+3% Block Chance', [F('BLOCK_CHANCE', 3.0)])
nodes['sentinel_haven_4'] = node('sentinel_haven_4', 'Haven IV', '+3% Dodge Chance', [P('DODGE_CHANCE', 3.0)])
nodes['sentinel_haven_notable'] = node('sentinel_haven_notable', 'Stalwart Defense', '+6% Block Chance, +6% Dodge Chance, +4% All Elemental Resistance', [F('BLOCK_CHANCE', 6.0), P('DODGE_CHANCE', 6.0), P('ALL_ELEMENTAL_RESISTANCE', 4.0)])

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
    drawbacks=[P('ALL_DAMAGE_PERCENT', -10.0), P('ATTACK_SPEED_PERCENT', -10.0)])

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
