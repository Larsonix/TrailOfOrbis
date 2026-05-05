# HytaleModding Wiki

## Identity

| Field | Value |
|-------|-------|
| Author | HytaleModding community ([HytaleModding](https://github.com/HytaleModding)) |
| Repository | https://github.com/HytaleModding/wiki |
| Website | https://wiki.hytalemodding.dev |
| License | Unknown |
| Category | Platform |

## Tracking

| Field | Value |
|-------|-------|
| Current Version | `92b08e8` |
| Last Updated | 2026-05-01 |
| Cloned From | main |

## What It Does

Community wiki platform for Hytale mods. Provides per-mod wiki pages with GitHub sync, custom CSS theming, alert formatting, and image hosting. Also supports Voile (in-game documentation viewer).

## What We Use

- **Wiki publishing** — Trail of Orbis wiki is hosted on this platform
- **GitHub sync** — our `Wiki/` directory auto-syncs to the wiki on push
- **Custom CSS** — themed wiki pages with our color scheme
- **Voile integration** — in-game documentation via the same markdown source
- **Platform source** — track platform changes that affect our wiki rendering

## Our Integration

| File | Purpose |
|------|---------|
| `Wiki/` | Wiki content directory, auto-synced to HytaleModding.dev |
| `Wiki/content/trailoforbis_TrailOfOrbis.json` | Wiki index configuration |
| `src/.../guide/GuideManager.java` | In-game guide system (Voile) |
| `src/.../guide/GuideRepository.java` | Guide content loading |

## Known Issues

- GitHub sync **disables the web editor** once enabled — all edits must go through Git
- Custom CSS is per-mod global (`:root` variables + `.prose` selectors)
- Voile has its own formatting extras (`<gradient>`, `&c` color codes, admonitions) that differ from web wiki

## Update Instructions

```bash
./external/scripts/update-externals.sh hytalemodding-wiki
```
