# External Dependencies Registry

Quick-reference table of all tracked external dependencies. See each dependency's `META.md` for full details.

## Plugins

| Name | Author | Repository | Integration Status | What We Use |
|------|--------|------------|-------------------|-------------|
| [Hexcode](plugins/hexcode/META.md) | itsriprod | [GitHub](https://github.com/itsriprod/hexcode) | Active — deep integration | Spell damage pipeline, mana sync, ailments |
| [HyUI](plugins/hyui/META.md) | Elliesaur | [GitHub](https://github.com/Elliesaur/HyUI) | Active — foundational | UI parsing, builders, styles, multi-HUD |
| [MHUD](plugins/mhud/META.md) | Buuz135 | [GitHub](https://github.com/Buuz135/MHUD) | Active | Multi-HUD coexistence |
| [PartyPro](plugins/partypro/META.md) | Tsum0ri | [GitHub](https://github.com/Tsum0ri/PartyPro_Hytale) | Active — reflection bridge | Party queries, XP sharing, HUD sync |
| [The Armory](plugins/the-armory/META.md) | azurefoxx98 | [GitHub](https://github.com/azurefoxx98/The-Armory-Mod) | Tracking — not yet integrated | Equipment compat (future) |
| [Loot4Everyone](plugins/loot4everyone/META.md) | MimStar | [GitHub](https://github.com/MimStar/Loot4Everyone-Hytale) | Tracking — needs compat review | Loot distribution compat |

## Libraries

| Name | Author | Repository | Integration Status | What We Use |
|------|--------|------------|-------------------|-------------|
| [Vuetale](libraries/vuetale/META.md) | KelpyCode | [GitHub](https://github.com/KelpyCode/Vuetale) | Reference only | Vue-to-HyUI patterns |
| [Modtale](libraries/modtale/META.md) | Modtale | [GitHub](https://github.com/Modtale/modtale) | Tracking | Framework patterns, potential distribution |

## Platforms

| Name | Author | Repository / Site | Integration Status | What We Use |
|------|--------|-------------------|-------------------|-------------|
| [Modifold](platforms/modifold/META.md) | Modifold | [API](https://api.modifold.com/api-docs/) / [GitHub Org](https://github.com/orgs/modifold-website/repositories) | Active | Mod publishing, API |
| [HytaleModding Wiki](platforms/hytalemodding-wiki/META.md) | HytaleModding | [GitHub](https://github.com/HytaleModding/wiki) / [Site](https://wiki.hytalemodding.dev) | Active | Wiki hosting, GitHub sync, Voile |

## Commands

```bash
# List all tracked dependencies
./external/scripts/update-externals.sh --list

# Show clone status
./external/scripts/update-externals.sh --status

# Update everything
./external/scripts/update-externals.sh all

# Update one dependency
./external/scripts/update-externals.sh hexcode
```
