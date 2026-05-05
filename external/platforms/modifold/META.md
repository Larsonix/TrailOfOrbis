# Modifold

## Identity

| Field | Value |
|-------|-------|
| Author | Modifold team |
| Repository | https://github.com/orgs/modifold-website/repositories |
| Website | https://modifold.com |
| API Docs | https://api.modifold.com/api-docs/ |
| License | Unknown |
| Category | Platform |

## Tracking

| Field | Value |
|-------|-------|
| Current Version | — |
| Last Updated | 2026-05-01 |
| Cloned From | org (multiple repos) |

## What It Does

Mod marketplace and distribution platform for Hytale. Provides mod hosting, versioning, discovery, and an API for programmatic access to mod metadata and downloads.

## What We Use

- **Distribution** — Trail of Orbis is published on Modifold (manual upload per release)
- **Wiki integration** — Modifold auto-displays a "Wiki" tab linking to our HytaleModding.dev wiki
- **Community** — mod discovery, rich profiles (logo, banner, screenshots, tags)

## Our Integration

| File | Purpose |
|------|---------|
| `docs/release/RELEASE_GUIDE.md` | Release pipeline setup (lines 305-323) |

**Note**: No API automation — upload is through the web UI. Sign-in via GitHub/Discord/Telegram.

## Special Notes

Modifold is a GitHub **organization** with multiple repositories, not a single repo. The update script skips it — update manually by browsing the org or fetching API docs:

```bash
# Fetch latest API docs
curl -o external/platforms/modifold/api-docs/openapi.json https://api.modifold.com/api-docs/
```

## Update Instructions

Manual — browse https://github.com/orgs/modifold-website/repositories for repo updates.
