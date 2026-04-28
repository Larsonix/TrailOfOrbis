# Realms System

The Realms system provides a POE-inspired mapping mechanic for TrailOfOrbis. Players can obtain **Realm Maps** from mob drops, which create temporary dimensions called **Realms** containing pre-spawned monsters, modifiers, and completion objectives.

## Documentation Index

- [Overview](docs/01-overview.md) - System concept and core mechanics
- [Realm Maps](docs/02-realm-maps.md) - Map item structure, tiers, and properties
- [Modifiers](docs/03-modifiers.md) - Realm modifier system and effects
- [World Generation](docs/04-world-generation.md) - Realm templates and generation
- [Mob Spawning](docs/05-mob-spawning.md) - Pre-spawning and completion tracking
- [Stone System](docs/06-currency.md) - Unified stone system for map modification
- [Technical Architecture](docs/07-architecture.md) - Implementation details

## Quick Reference

### Core Concepts

| Term | Description |
|------|-------------|
| **Realm** | A temporary instanced dimension created when a Realm Map is used |
| **Realm Map** | An item dropped by mobs that can be consumed to create a Realm |
| **Realm Template** | A pre-designed or procedurally generated world layout |
| **Modifiers** | Affixes on Realm Maps that alter difficulty and rewards |
| **Completion** | Achieved by killing all monsters or meeting specific objectives |

### System Flow

```
Mob Kill → Map Drop (level-based) → Inventory
                                        ↓
                 Player uses map → Portal Created → Realm Spawned
                                                        ↓
                 Completion/Timeout → Rewards → Portal Home → Realm Destroyed
```

## Configuration Files

- `realms.yml` - Main realm configuration
- `realm-templates.yml` - Template definitions and layouts
- `realm-modifiers.yml` - Modifier pool and weights
- `stones.yml` - Unified stone system (shared with gear system)
