# Trail of Orbis - Release Guide

Complete guide for releasing Trail of Orbis across all platforms.

### Future : HytalePublisher

AzureDoom's [HytalePublisher](https://github.com/AzureDoom/HytalePublisher) Gradle plugin can publish to CurseForge, ModTale, and Modifold via a single `./gradlew publishAll`. It's imported in `vendor/HytalePublisher/` and config is ready (commented out) in `build.gradle.kts`.

**Currently blocked** : plugin compiled targeting Java 25, but our Gradle daemon runs Java 21 (Kotlin DSL breaks on Java 25). When AzureDoom recompiles targeting Java 21, uncomment the plugin and config. Until then, we use the GitHub Action workflow below.

---

## Table of Contents

- [Version Management](#version-management)
- [Release Checklist](#release-checklist)
- [Platform Overview](#platform-overview)
- [CurseForge](#curseforge)
- [ModTale](#modtale)
- [Modifold](#modifold)
- [BuiltByBit](#builtbybit)
- [Thunderstore](#thunderstore)
- [GitHub Releases](#github-releases)
- [CI/CD Automation](#cicd-automation)
- [Platform Descriptions](#platform-descriptions)
- [Secrets & Tokens](#secrets--tokens)

---

## Version Management

### Version Format

We use [Semantic Versioning](https://semver.org/): `MAJOR.MINOR.PATCH`

- **MAJOR**: Breaking changes (new save format, config migration required)
- **MINOR**: New features (new biome, new system, new stones)
- **PATCH**: Bug fixes, balance tweaks, small improvements

### Version Locations

All three must be updated together:

| File | Field | Example |
|------|-------|---------|
| `gradle.properties` | `projectVersion` | `1.0.0` |
| `build.gradle.kts` | `version` (reads from gradle.properties) | `1.0.0` |
| `src/main/resources/manifest.json` | `Version` | `1.0.0` |

### Release Flow

```
Development:  1.0.0-SNAPSHOT  (default branch)
     |
     v
Tag release:  v1.0.0          (git tag triggers CI)
     |
     v
Post-release: 1.1.0-SNAPSHOT  (bump for next cycle)
```

### How to Release

```bash
# 1. Update version in gradle.properties (remove -SNAPSHOT)
#    projectVersion=1.0.0

# 2. Update manifest.json Version field
#    "Version": "1.0.0"

# 3. Ensure changelog is up to date
#    Wiki/content/Larsonix_TrailOfOrbis/changelog.md

# 4. Commit
git add gradle.properties src/main/resources/manifest.json
git commit -m "Release v1.0.0"

# 5. Tag (this triggers CI release workflow)
git tag v1.0.0
git push origin main --tags

# 6. Post-release: bump to next SNAPSHOT
#    projectVersion=1.1.0-SNAPSHOT in gradle.properties
#    "Version": "1.1.0-SNAPSHOT" in manifest.json
git commit -am "Bump to 1.1.0-SNAPSHOT"
git push
```

---

## Release Checklist

Before tagging a release:

- [ ] All tests pass: `./gradlew clean build`
- [ ] Asset pack validation passes (CI runs this automatically)
- [ ] Changelog updated in `Wiki/content/Larsonix_TrailOfOrbis/changelog.md`
- [ ] Version bumped in `gradle.properties` and `manifest.json`
- [ ] `manifest.json` description is accurate
- [ ] Wiki documentation reflects current features
- [ ] No `SNAPSHOT` in version strings
- [ ] Asset pack builds correctly (items don't show "Invalid Item")
- [ ] All instance templates exist in `src/main/resources/hytale-assets/Server/Instances/`
- [ ] Tested on a local server with `./scripts/deploy.sh`

After CI completes:

- [ ] GitHub Release created with JAR attached (assets bundled inside)
- [ ] CurseForge upload successful (check Author Console)
- [ ] ModTale upload successful (check Creator Dashboard)
- [ ] Manual uploads done (Modifold, BuiltByBit, Thunderstore)
- [ ] Post-release version bump committed

### What Gets Released

A single JAR file — code and assets are bundled together:

| Artifact | Contents | Install Location |
|----------|----------|-----------------|
| `TrailOfOrbis-{version}.jar` | Java code + configs + asset pack (instances, environments, biomes, damage types, icons) | `mods/` |

The JAR's `manifest.json` declares `"IncludesAssetPack": true`, which tells Hytale to read `Server/` and `Common/` assets directly from inside the JAR. This is the standard Hytale mod distribution pattern (same as RogueTale, Hylamity, etc.).

**For local development**, `deploy.sh` additionally extracts assets to a separate `TrailOfOrbis_Realms/` folder for rapid iteration without rebuilding the JAR.

---

## Platform Overview

| Platform | Automation | Upload Format | Monetization | Audience |
|----------|-----------|---------------|-------------|----------|
| **CurseForge** | Full (API + GitHub Action) | JAR (assets bundled) | Ad revenue sharing | Largest - official Hytale platform |
| **ModTale** | Full (API + GitHub Action) | JAR (assets bundled) | Free only | Community-focused |
| **GitHub Releases** | Full (GitHub Action) | JAR (assets bundled) | N/A | Developers, source viewers |
| **Modifold** | Manual | JAR (assets bundled) | Free only | Growing, has wiki integration |
| **BuiltByBit** | Manual | JAR (assets bundled) | Paid + Free | Premium marketplace |
| **Thunderstore** | Semi (CLI available) | ZIP package | Free only | Mod manager users |
| **Modrinth** | N/A | N/A | N/A | **Does NOT support Hytale** |

### Priority Order

1. **CurseForge** - Official platform, largest audience, fully automated
2. **ModTale** - Community hub, fully automated
3. **GitHub Releases** - Developer audience, fully automated
4. **Modifold** - Manual but has HytaleModding Wiki integration (our wiki syncs automatically)
5. **BuiltByBit** - Manual, good for premium content (we're free/open-source so lower priority)
6. **Thunderstore** - Manual, requires custom package format

---

## CurseForge

### One-Time Setup

1. **Create account**: https://www.curseforge.com (Google/Discord/GitHub/Twitch)
2. **Create project**: Author Console → Projects → Create a Project
   - Game: **Hytale**
   - Class: **Mods** (covers both plugins and asset packs)
   - Name: `Trail of Orbis`
   - Summary: See [Platform Descriptions](#platform-descriptions)
   - Description: See [Platform Descriptions](#platform-descriptions)
   - License: `LGPL-3.0` (Custom License with our text)
   - Categories: `RPG`, `Adventure`, `Items`, `Dungeons`
   - Logo: 400x400 PNG (our mod icon)
3. **Get project ID**: Found in project URL sidebar (numerical)
4. **Generate API token**: https://www.curseforge.com/account/api-tokens
5. **Add to GitHub Secrets**: `CF_API_TOKEN`
6. **Note the project ID**: Add to GitHub Secrets as `CF_PROJECT_ID`

### Upload API Details

- **Endpoint**: `https://hytale.curseforge.com/api/projects/{projectId}/upload-file`
- **Auth**: Header `X-Api-Token: {token}` or query param `?token={token}`
- **Method**: `POST multipart/form-data`
- **Fields**:
  - `file`: The JAR file
  - `metadata`: JSON with changelog, gameVersions, releaseType, relations

```json
{
  "changelog": "## 1.0.0\n- Initial release\n...",
  "changelogType": "markdown",
  "displayName": "Trail of Orbis 1.0.0",
  "gameVersions": [/* query API for Hytale version IDs */],
  "releaseType": "release",
  "relations": {
    "projects": [
      { "slug": "hyui", "type": "embeddedLibrary" }
    ]
  }
}
```

### Game Versions

Query available versions:
```
GET https://hytale.curseforge.com/api/game/versions?token=YOUR_TOKEN
```

The `game_endpoint` for the GitHub Action is `hytale`.

### Automation

Two options (we use the GitHub Action):

**Option A: GitHub Action** (`itsmeow/curseforge-upload@v3`)
```yaml
- uses: itsmeow/curseforge-upload@v3
  with:
    token: ${{ secrets.CF_API_TOKEN }}
    project_id: ${{ secrets.CF_PROJECT_ID }}
    game_endpoint: hytale
    file_path: build/libs/TrailOfOrbis-${{ env.VERSION }}.jar
    release_type: release
    changelog_type: markdown
    changelog: ${{ steps.changelog.outputs.content }}
    relations: "hyui:embeddedLibrary"
```

**Option B: Gradle Plugin** (`io.github.themrmilchmann.curseforge-publish`)
```kotlin
plugins {
    id("io.github.themrmilchmann.curseforge-publish") version "0.8.0"
}
curseforge {
    apiToken = providers.gradleProperty("curseforgeApiToken")
    publications {
        register("curseForge") {
            projectId = "YOUR_PROJECT_ID"
            artifacts.register("main") {
                from(tasks.named("shadowJar"))
                displayName = "Trail of Orbis ${project.version}"
                releaseType = ReleaseType.RELEASE
                changelog { format = ChangelogFormat.MARKDOWN; from(file("CHANGELOG.md")) }
                relations { embeddedLibrary("hyui") }
            }
        }
    }
}
```

---

## ModTale

### One-Time Setup

1. **Create account**: https://modtale.net
2. **Create project manually** on ModTale website
3. **Get project ID**: UUID in project URL or sidebar → `Project ID`
4. **Get API key**: Profile Icon → Creator Dashboard → Developer Settings → Generate Key
5. **Add to GitHub Secrets**: `MODTALE_API_KEY` and `MODTALE_PROJECT_ID`

### Upload API Details

- **Endpoint**: `https://api.modtale.net/api/v1/projects/{projectId}/versions`
- **Auth**: Header `X-MODTALE-KEY: {apiKey}`
- **Method**: `POST multipart/form-data`
- **Fields**:
  - `file`: The JAR file (`@path/to/file.jar`)
  - `versionNumber`: SemVer string (e.g., `1.0.0`)
  - `channel`: `RELEASE`, `BETA`, or `ALPHA`
  - `gameVersions`: Comma-separated Hytale server versions (e.g., `2026.03.26-89796e57b`)
  - `changelog`: Markdown string

### Automation (curl in GitHub Action)

```yaml
- name: Upload to ModTale
  run: |
    curl -X POST \
      "https://api.modtale.net/api/v1/projects/${{ secrets.MODTALE_PROJECT_ID }}/versions" \
      -H "X-MODTALE-KEY: ${{ secrets.MODTALE_API_KEY }}" \
      -F "file=@build/libs/TrailOfOrbis-${VERSION}.jar" \
      -F "versionNumber=${VERSION}" \
      -F "channel=RELEASE" \
      -F "changelog=$(cat CHANGELOG_CURRENT.md)"
```

### Gradle Task (Local Publishing)

```bash
export MODTALE_KEY="your_key"
export MODTALE_PROJECT_ID="your_project_uuid"
./gradlew publishToModtale
```

### Recommended Assets

- **Logo**: 512x512 PNG
- **Banner**: 1920x640 PNG
- **Screenshots**: Multiple, showcasing key features
- **Tags**: RPG, Dungeons, Skills, Items, Combat

### ModTale as Maven Dependency

Other mods can depend on Trail of Orbis via ModTale's Ivy repository:
```
https://api.modtale.net/api/v1/projects/{projectId}/versions/{version}/download
```

---

## Modifold

### One-Time Setup

1. **Sign in**: https://modifold.com (GitHub/Discord/Telegram)
2. **Create Project**: Click "Create Project" in header
3. Fill: Name, Summary (1-2 sentences)
4. **Upload first version** with JAR, version number, changelog
5. **Add gallery images** (one as "Featured" - used as cover)
6. **Add links**: Source Code, Wiki (our HytaleModding wiki), Discord
7. **Select tags**: RPG, etc.
8. **Set license**: LGPL-3.0
9. **Submit for moderation**

### Wiki Integration

Modifold automatically syncs with HytaleModding Wiki. Since our wiki is already at
`https://wiki.hytalemodding.dev/en/docs/Larsonix_TrailOfOrbis`, adding that link
in Modifold settings gives us a Wiki tab on our Modifold page for free.

### Upload Process

**Manual only** - no API for automated uploads. Upload through web UI each release.

---

## BuiltByBit

### One-Time Setup

1. **Register**: https://builtbybit.com/login/register
2. **Set up wallet** (Tebex - even for free resources)
3. **Add resource**: builtbybit.com/resources/add → Plugins category
4. Fill: Title, Description, Tags, Pricing (Free), File upload
5. **Submit for approval** (~4 hour review time)

### Notes

- Primarily a premium marketplace, but free resources welcome
- No upload API - manual each release
- 9.9% platform fee on paid content (we're free, so irrelevant)
- Categories: Plugins, Data assets, Server setups, Builds, Graphics, Textures, Models, Audio, Other

---

## Thunderstore

### One-Time Setup

1. **Create team** on Thunderstore
2. **Prepare package structure** (different from other platforms):

```
TrailOfOrbis/
  icon.png          # 256x256, required
  README.md         # Required
  CHANGELOG.md      # Recommended
  manifest.json     # Thunderstore manifest (NOT Hytale manifest)
  TrailOfOrbis-1.0.0-SNAPSHOT.jar
```

Thunderstore `manifest.json` (separate from Hytale's):
```json
{
  "name": "TrailOfOrbis",
  "description": "Deep RPG overhaul: 6 elements, 485-node skill tree, procedural Realms, gear crafting",
  "version_number": "1.0.0",
  "dependencies": [],
  "website_url": "https://github.com/Larsonix/TrailOfOrbis"
}
```

3. **Upload as ZIP** containing all the above
4. **Tags**: `Pre-Release`, `Plugins`, `Mods`

### Automation

Thunderstore has `tcli` (Thunderstore CLI) but it requires Go setup. Semi-automated - can be scripted but more complex than CurseForge/ModTale.

---

## GitHub Releases

Fully automated via `.github/workflows/release.yml`. On tag push `v*`:
1. Builds shadowJar (assets bundled at JAR root via `from("src/main/resources/hytale-assets")`)
2. Validates asset pack in JAR (fails the release if critical assets are missing)
3. Creates GitHub Release with changelog
4. Attaches the JAR as a release asset
5. Uploads to CurseForge and ModTale

No setup needed beyond pushing a tag.

---

## CI/CD Automation

### Workflows

| Workflow | Trigger | What it does |
|----------|---------|-------------|
| `ci.yml` | Push to `main`, PRs | Build + test |
| `release.yml` | Tag `v*` | Build + GitHub Release + CurseForge + ModTale |

### GitHub Secrets Required

| Secret | Where to get it | Used by |
|--------|----------------|---------|
| `CF_API_TOKEN` | https://www.curseforge.com/account/api-tokens | CurseForge upload |
| `CF_PROJECT_ID` | CurseForge Author Console → project URL | CurseForge upload |
| `MODTALE_API_KEY` | ModTale Creator Dashboard → Developer Settings | ModTale upload |
| `MODTALE_PROJECT_ID` | ModTale project page sidebar | ModTale upload |

### Java 25 in CI

Hytale mods compile with Java 25 but Gradle daemon needs Java 21. Our workflow:
- Sets up Java 21 for Gradle daemon
- Gradle toolchain auto-provisions Java 25 for compilation via Foojay

```yaml
- uses: actions/setup-java@v4
  with:
    java-version: '21'
    distribution: 'temurin'
```

The Gradle toolchain (`org.gradle.java.installations.auto-download=true`) handles Java 25.

---

## Platform Descriptions

### Short Summary (1-2 sentences, ~250 chars)

> A deep RPG overhaul for Hytale inspired by Path of Exile and Last Epoch. 6 elements, 485-node skill tree, procedural Realm dungeons, full gear crafting with 101 modifiers, and an 11-stage damage pipeline.

### Full Description (Markdown)

See `docs/release/PLATFORM_DESCRIPTION.md` for the full platform description ready to paste into CurseForge, ModTale, Modifold, etc.

---

## Secrets & Tokens

**Never commit tokens to the repository.** Store them as:

1. **GitHub Actions Secrets** (for CI/CD): Settings → Secrets → Actions
2. **Local environment variables** (for manual publishing):
   ```bash
   export CF_API_TOKEN="your-curseforge-token"
   export MODTALE_KEY="your-modtale-key"
   export MODTALE_PROJECT_ID="your-project-uuid"
   ```
3. **Gradle properties** (alternative for Gradle plugin):
   Add to `~/.gradle/gradle.properties` (NOT the project's):
   ```properties
   curseforgeApiToken=your-token-here
   ```
