# External Dependencies

Curated registry of every external project Trail of Orbis actively integrates with, credits, or depends on. Each dependency has its own folder with source code, metadata, and auto-generated changelogs.

> This is NOT `vendor/`. Vendor is a private research corpus. `external/` is for **acknowledged, open-source dependencies** we actively use and tell people about.

## Structure

```
external/
  plugins/          Hytale mods we integrate with at runtime
  libraries/        Dev tools, frameworks, build-time dependencies
  platforms/        Publishing platforms, APIs, community infrastructure
  scripts/          Automation (update, diff, changelog generation)
```

Each dependency folder contains:

| File | Tracked by Git | Purpose |
|------|:-:|---------|
| `META.md` | Yes | Identity, version, integration notes, update instructions |
| `CHANGELOG.md` | Yes | Auto-generated diff summaries from each update |
| `repo/` | No | Full git clone of the source code (pulled by update script) |

## Quick Start

### First time — pull all source code

```bash
./external/scripts/update-externals.sh all
```

### Update a single dependency

```bash
./external/scripts/update-externals.sh hexcode
```

### Update everything

```bash
./external/scripts/update-externals.sh all
```

### What happens on update

1. If `repo/` doesn't exist, the script clones it fresh
2. If `repo/` exists, it records the current commit, pulls, then:
   - Generates a diff summary (commits + changed files)
   - Appends the summary to `CHANGELOG.md`
   - Updates the commit hash in `META.md`
3. If nothing changed, it says so and moves on

## Adding a New Dependency

1. Create the folder: `external/<category>/<name>/`
2. Create `META.md` from the template below
3. Create an empty `CHANGELOG.md`
4. Add the entry to `scripts/update-externals.sh` (the `REPOS` array)
5. Add a row to `REGISTRY.md`
6. Run `./external/scripts/update-externals.sh <name>`

### META.md Template

```markdown
# Dependency Name

## Identity

| Field | Value |
|-------|-------|
| Author | GitHub handle |
| Repository | https://github.com/... |
| License | MIT / Apache-2.0 / Unknown |
| Category | Plugin / Library / Platform |

## Tracking

| Field | Value |
|-------|-------|
| Current Version | v0.0.0 or commit hash |
| Last Updated | YYYY-MM-DD |
| Cloned From | branch name (usually main) |

## What It Does

One paragraph describing the project.

## What We Use

Bullet list of specific features/APIs we depend on.

## Our Integration

| File | Purpose |
|------|---------|
| `src/main/java/.../SomeClass.java` | What it does |

## Known Issues

- Any gotchas, compat problems, dead approaches

## Update Instructions

Run: `./external/scripts/update-externals.sh <name>`
```

## Categories

### Plugins (`plugins/`)

Hytale mods that run alongside Trail of Orbis on the same server. We integrate with their APIs, handle compatibility, or extend their functionality.

### Libraries (`libraries/`)

Development tools and frameworks. May be build-time dependencies, code generators, or reference implementations.

### Platforms (`platforms/`)

Publishing platforms, marketplaces, community infrastructure. Includes API documentation snapshots and platform-specific tooling.
