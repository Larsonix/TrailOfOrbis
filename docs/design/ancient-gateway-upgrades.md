# Ancient Gateway Upgrades

Every Portal Device in the world is an **Ancient Gateway** — a tiered portal that gates which realm map levels it can channel. Players upgrade gateways by bringing metal bars and essences from progressively harder overworld zones, creating a natural bridge between vanilla exploration and the RPG realm system.

## The Progression Loop

```
Explore overworld → Mine ores → Upgrade gateway → Run harder realms → Get gear → Explore further → Repeat
```

Players always have a clear next objective. The vanilla game stays relevant at every stage.

---

## Gateway Tiers

All Portal Devices start at Tier 0 (Copper Gateway). Upgrading consumes materials and raises the maximum realm map level the portal can channel.

| Tier | Name | Max Realm Level | Upgrade Cost | Zone | Mob Levels |
|------|------|----------------:|--------------|------|------------|
| 0 | Copper Gateway | 10 | *(default — no cost)* | Zone 1 core | 1-8 |
| 1 | Iron Gateway | 20 | 15x Iron Bar, 5x Life Essence | Zone 1 | 5-14 |
| 2 | Gold Gateway | 30 | 12x Gold Bar, 8x Life Essence | Zone 1-2 edge | 10-17 |
| 3 | Cobalt Gateway | 45 | 10x Cobalt Bar, 10x Void Essence | Zone 2 | 14-21 |
| 4 | Thorium Gateway | 60 | 10x Thorium Bar, 10x Life Essence, 10x Void Essence | Zone 2-3 | 17-27 |
| 5 | Mithril Gateway | 80 | 8x Mithril Bar, 15x Void Essence | Zone 3 | 27-41 |
| 6 | Adamantite Gateway | Unlimited | 5x Adamantite Bar, 5x Voidheart | Zone 4 | 47+ |

Each tier's max realm level is set **above** the mob level at the source zone. You fight level ~8 mobs to get Iron, then unlock realms up to level 20. The realms are harder than the mining, but the gear/XP from realms prepares you for the next zone. This "leapfrog" pattern drives the whole loop.

---

## How It Works

### Interacting with a Portal Device (press F)

| What you're holding | Portal state | What happens |
|---------------------|-------------|--------------|
| **Nothing** | Idle | Gateway Upgrade UI — shows current tier, next tier cost, upgrade button |
| **Realm Map** (level OK) | Idle | Realm entry flow — map consumed, portal opens |
| **Realm Map** (level too high) | Idle | Error message: "This [tier name] cannot channel maps above level X. Upgrade to [next tier]!" |
| **Fragment Key** | Idle | Vanilla Fragment Key flow — instance summoned normally |
| Any item | Active (realm) | Realm info page |
| Any item | Active (vanilla) | Vanilla active portal page |

**Key design**: vanilla Fragment Keys are fully preserved. The gateway upgrade UI only appears when **empty-handed on an idle portal**. No vanilla functionality is lost.

### Upgrading

1. Walk up to any Portal Device empty-handed and press F
2. The **Gateway Upgrade UI** shows:
   - Current tier name and max realm level
   - Arrow showing current tier → next tier with new max level
   - Material requirements with 3D item icons and have/need counts (green = satisfied, red = missing)
   - **Upgrade** button (green when all materials present, gray when not)
   - **Close** button
3. Click Upgrade — materials are consumed from hotbar, storage, and backpack
4. Gateway advances to the next tier (persisted per-block in the database)

### Spawn Gateways

A ring of 8 Portal Devices is placed automatically around world spawn (radius 40 blocks). These provide immediate access to the gateway system without requiring the Arcane Workbench craft. Players who want personal portals elsewhere still craft via the vanilla Arcane Bench route — those portals also start at Tier 0 and can be upgraded identically.

---

## Per-Block Storage

Gateway tier is stored in the `rpg_gateway_tiers` SQL table, keyed by `(world_uuid, block_x, block_y, block_z)`. Unregistered portals default to tier 0 — the database only stores blocks that have been upgraded (tier > 0). Each portal in the world can be at a different tier. Upgrades are shared — any player can use or upgrade any gateway.

---

## Architecture

### Package: `io.github.larsonix.trailoforbis.maps.gateway`

| Class | Purpose |
|-------|---------|
| `GatewayUpgradeConfig` | Tier definitions: name, max level, material list. Defaults built-in, configurable via YAML. |
| `GatewayTierRepository` | SQL persistence with in-memory cache. UPSERT for MySQL/H2/PostgreSQL. |
| `GatewayUpgradeManager` | Business logic: material checking, consumption, tier advancement, level validation. |
| `GatewayUpgradePage` | HyUI page using `PageBuilder.fromHtml()` — decorated container with item icons, buttons. |

### Integration Points

| File | Change |
|------|--------|
| `RealmPortalDevicePageSupplier` | Empty-hand on idle portal → opens `GatewayUpgradePage`. Realm map → tier level gate check. Fragment Keys → vanilla flow. |
| `SpawnGatewayManager` | Registers placed spawn portals as tier 0 in `GatewayTierRepository`. |
| `RealmsManager` | Initializes `GatewayUpgradeManager` + `GatewayTierRepository`. Loads cached tiers on world activation. |
| `db/schema.sql` | New `rpg_gateway_tiers` table. |
| `realms.yml` | `spawn-gateway.enabled: true`. |

### Interaction Priority (in handleVanillaPortalDevice)

```
1. Validate block states
2. Active realm destination     → Realm info page
3. Active vanilla destination   → Vanilla PortalDeviceActivePage
4. Invalid destination          → Reset portal
5. Idle + empty-handed          → Gateway Upgrade UI
6. Idle + holding item          → Vanilla PortalDeviceSummonPage (Fragment Keys)
```

### UI Pattern

The upgrade page uses `PageBuilder.fromHtml()` (same as StonePickerPage), not the vanilla PortalDeviceSummon.ui template. This gives full control:
- `decorated-container` with "Ancient Gateway" title
- `<span class="item-icon" data-hyui-item-id="...">` for native 3D item rendering
- `<button class="secondary-button">` for Upgrade/Close with `Activating` events
- `ctx.getPage().ifPresent(page -> page.close())` for dismissal

---

## Overworld Zone Reference

The upgrade materials map to the vanilla biome/zone progression:

| Metal | Zone | Distance from Spawn | Ore Rock Types |
|-------|------|--------------------:|----------------|
| Copper | Zone 1 (Forest) | 0-500 | Stone, Sandstone, Shale |
| Iron | Zone 1 (widespread) | 100-800 | Stone, Basalt, Sandstone, Shale, Slate, Volcanic |
| Gold | Zone 1-2 (deeper caves) | 300-1200 | Stone, Basalt, Sandstone, Shale, Calcite, Volcanic |
| Cobalt | Zone 2 (Desert/Savanna) | 800-1500 | Shale, Slate |
| Thorium | Zone 2-3 transition | 1200-2000 | Sandstone, Mud |
| Mithril | Zone 3 (Tundra) | 2000-3000 | Stone, Magma |
| Adamantite | Zone 4 (Volcanic) | 3000+ | Magma |

Mob scaling: ~1 level per 75 blocks from spawn (`pool_per_block: 0.5`).

---

## Future Considerations

- **Onyxium / Prisma**: Special materials for Void (level 500+) and Corrupted (level 1000+) gateway tiers
- **Visual progression**: Particle color or block appearance per tier
- **Break protection**: BreakBlockEvent listener to make spawn gateways indestructible (pattern verified in Hexcode mod)
- **Gateway compass**: Compass marker pointing to nearest gateway for new players
