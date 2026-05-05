# Modtale

## Identity

| Field | Value |
|-------|-------|
| Author | Modtale team ([Modtale](https://github.com/Modtale)) |
| Repository | https://github.com/Modtale/modtale |
| Website | https://modtale.net |
| API Docs | https://modtale.net/api-docs |
| License | Unknown |
| Category | Library / Platform |

## Tracking

| Field | Value |
|-------|-------|
| Current Version | `556a7b2` |
| Last Updated | 2026-05-01 |
| Cloned From | develop |

## What It Does

Modding framework and platform for Hytale. Provides standardized APIs, mod management, and community infrastructure for Hytale mod development.

## What We Use

- **Distribution platform** — Trail of Orbis releases are uploaded automatically via CI/CD
- **Maven repo** — other mods can depend on ToO via Modtale's Maven repository
- **Upload API** — `POST https://api.modtale.net/api/v1/projects/{projectId}/versions` with file, versionNumber, channel, gameVersions, changelog

## Our Integration

| File | Purpose |
|------|---------|
| `.github/workflows/release.yml` | Automated release upload to Modtale (lines 114-134) |
| `docs/release/RELEASE_GUIDE.md` | Setup docs and API reference (lines 245-301) |

**Secrets required**: `MODTALE_API_KEY`, `MODTALE_PROJECT_ID`

## Update Instructions

```bash
./external/scripts/update-externals.sh modtale
```
