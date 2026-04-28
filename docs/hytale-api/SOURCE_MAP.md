# Hytale API Docs — Source Map

Tracks each doc file's upstream source for the self-updating documentation pipeline.

**Last full sync:** 2026-03-26
**Upstream repo:** `https://github.com/HytaleModding/site`
**Content root:** `content/docs/en/`
**Raw URL base:** `https://raw.githubusercontent.com/HytaleModding/site/main/content/docs/en/`

---

## Docs with Upstream Sources

| Doc File | Upstream MDX Path(s) | Content Hash |
|----------|---------------------|--------------|
| `ecs.md` | `guides/ecs/entity-component-system.mdx`, `guides/ecs/hytale-ecs-theory.mdx`, `guides/ecs/systems.mdx`, `guides/ecs/example-ecs-plugin.mdx`, `guides/ecs/block-components.mdx` | `775c6981ccbb` |
| `persistent-data.md` | `guides/plugin/store-persistent-data.mdx`, `guides/ecs/hytale-ecs-theory.mdx` | `62a42585ae7e` |
| `events.md` | `guides/plugin/creating-events.mdx`, `server/events.mdx` | `9ac8074ed388` |
| `spawning-entities.md` | `guides/plugin/spawning-entities.mdx`, `server/entities.mdx` | `a9dcb4ee7792` |
| `spawning-npcs.md` | `guides/plugin/spawning-npcs.mdx` | `2c601c97afe9` |
| `npc-templates.md` | `official-documentation/npc/` (chapters 1-12), `guides/plugin/Interactable-NPCs.mdx` | `9eec8bf23555` |
| `items.md` | `guides/plugin/item-interaction.mdx`, `guides/plugin/item-registry.mdx` | `650515548f0b` |
| `inventory.md` | `guides/plugin/inventory-management.mdx` | `f1f4954697cb` |
| `hotbar-actions.md` | `guides/plugin/customizing-hotbar-actions.mdx`, `guides/plugin/listening-to-packets.mdx` | `7c38436654db` |
| `player-stats.md` | `guides/plugin/player-stats.mdx` | `44c2749911bd` |
| `player-death.md` | `guides/plugin/player-death-event.mdx` | `67260bc862bb` |
| `teleporting.md` | `guides/plugin/teleporting-players.mdx` | `226cadaab298` |
| `instances.md` | `guides/plugin/instances.mdx` | `f3da59df7484` |
| `blocks.md` | `guides/plugin/creating-block.mdx`, `guides/plugin/animated-block-textures.mdx` | `f3a63560772d` |
| `prefabs.md` | `guides/prefabs.mdx` | `d88c4d347609` |
| `native-ui.md` | `official-documentation/custom-ui/common-styling.mdx`, `official-documentation/custom-ui/layout.mdx`, `official-documentation/custom-ui/markup.mdx`, `official-documentation/custom-ui/type-documentation/`, `guides/plugin/ui.mdx` | `144e6f8130b1` |
| `text-holograms.md` | `guides/plugin/text-hologram.mdx` | `b0993a6249ba` |
| `notifications.md` | `guides/plugin/send-notifications.mdx`, `server/entities.mdx` | `812b316dceec` |
| `chat-formatting.md` | `guides/plugin/chat-formatting.mdx` | `2492465894ef` |
| `camera-controls.md` | `guides/plugin/customizing-camera-controls.mdx` | `de779a3751c4` |
| `sounds.md` | `guides/plugin/playing-sounds.mdx`, `server/sounds.mdx` | `228cf94e2e40` |
| `commands.md` | `guides/plugin/creating-commands.mdx`, `server/argtypes.mdx` | `4af190efe476` |
| `interactions.md` | `server/interaction-reference.mdx` + community mod analysis | `6a6231044522` |
| `world-gen.md` | `guides/plugin/world-gen.mdx`, `official-documentation/worldgen/` (all subdirs) | `8456e6caf055` |

## Docs Without Upstream Sources (Decompiled Source Only)

These docs were built from decompiled server source and have no matching pages on hytalemodding.dev.

| Doc File | Primary Server Packages | Content Hash |
|----------|------------------------|--------------|
| `entity-effects.md` | `com.hypixel.server.ecs.components.effects`, `com.hypixel.server.entity.effect` | `2f77d5f71662` |
| `permissions.md` | `com.hypixel.server.permission` | `3d871090d4fb` |
| `player-input.md` | `com.hypixel.server.network.packet`, `com.hypixel.server.input` | `711f405ada14` |
| `tag-system.md` | `com.hypixel.server.asset`, `com.hypixel.server.registry`, `com.hypixel.hytale.builtin.tagset` | `c4325161ec1a` |
| `crafting.md` | `com.hypixel.hytale.builtin.crafting` | `01080519d987` |
| `reputation.md` | `com.hypixel.hytale.builtin.adventure.reputation` | `3e79b2214164` |
| `objectives.md` | `com.hypixel.hytale.builtin.adventure.objectives`, `com.hypixel.hytale.builtin.adventure.npcobjectives` | `dce6d1d5baac` |
| `shop.md` | `com.hypixel.hytale.builtin.adventure.shop`, `com.hypixel.hytale.builtin.adventure.npcshop`, `com.hypixel.hytale.builtin.adventure.objectiveshop` | `db4d7fa4457c` |
| `animation-control.md` | `c.h.h.server.core.entity.AnimationUtils`, `c.h.h.server.core.entity.InteractionManager`, `c.h.h.protocol.ItemAnimation`, `c.h.h.protocol.packets.assets.UpdateItemPlayerAnimations` | `04d11cfec942` |
| `particles.md` | `c.h.h.server.core.universe.world.ParticleUtil`, `c.h.h.protocol.packets.world.SpawnParticleSystem`, `c.h.h.protocol.packets.entities.SpawnModelParticles`, `c.h.h.protocol.ModelParticle` | `1ba07eb700a9` |
| `damage-indicators.md` | `c.h.h.server.core.modules.entityui`, `c.h.h.protocol.packets.assets.UpdateEntityUIComponents`, `c.h.h.protocol.CombatTextUpdate` | `f2d6f97dbd2d` |
| `portals.md` | `c.h.h.builtin.portals` | `02b38625a828` |
| `world-map.md` | `c.h.h.protocol.packets.worldmap`, `c.h.h.server.core.modules.worldmap` | `c5fb013a91e1` |
| `entity-tracking.md` | `c.h.h.server.core.modules.entity.tracker` | `894b67d4367d` |
| `inventory-detection.md` | `c.h.h.server.core.inventory`, `c.h.h.protocol.packets.window` | `898b89fdf3a8` |
| `config-system.md` | `c.h.h.server.core.plugin`, `c.h.h.codec.builder.BuilderCodec` | `d0315eb275dc` |

---

## How to Use This File

### Checking for Updates

Run `~/tools/sync-hytale-docs.sh` to:
1. Clone/update the HytaleModding/site repo
2. Compare upstream MDX content hashes against the hashes in this file
3. Report which docs have upstream changes

### Updating a Doc

1. Fetch the raw MDX from the upstream path listed above
2. Compare against the current doc file
3. Update with new API methods, code examples, and patterns
4. Cross-reference changes against decompiled source in `/home/larsonix/work/Hytale-Decompiled-Full-Game/`
5. Verify all import paths against `.index/CLASS_INDEX.txt`
6. Update the content hash in this file: `sha256sum docs/hytale-api/<file>.md | cut -c1-12`

### Hash Format

Content hashes are the first 12 characters of the SHA-256 hash of the doc file. This is enough for change detection without being unwieldy.

```bash
# Regenerate a hash after updating a doc
sha256sum docs/hytale-api/ecs.md | cut -c1-12
```
