# 6 - GitHub Sync Reference

Source: https://raw.githubusercontent.com/HytaleModding/site/main/content/docs/en/wiki/6-github.mdx

## Overview
Changes pushed to your repository automatically appear on the wiki.
Once enabled, the built-in editor is DISABLED — all edits via Git only.

## Setup
1. Go to mod settings → **GitHub Repository URL** → paste repo link
2. If docs are in a subfolder, set **Repository Path**
3. If entire repo is docs, leave path empty

## Repository structure
```
my-repo/
└── docs/
    ├── intro.md
    ├── installation.md
    └── usage.md
```
Each .md file → one wiki page. Filename (without .md) → page slug.

## Frontmatter fields
| Field | Type | Required | Default | Description |
|-------|------|----------|---------|-------------|
| title | string | No | From filename | Override page title |
| order | integer | No | File position | Sort among siblings (lower = first) |
| published | boolean | No | true* | Whether page is visible |
| draft | boolean | No | false | Whether page is a draft |

*Note: From source code, default is actually `true` (resolvePublished returns true when neither field is set)

## Categories and Hierarchy
- **Folder** = category (child pages go inside)
- **index.md** inside folder = category page with content
- **meta.json** inside folder = category without content (title-only)

```json
{
  "title": "My Category",
  "published": true
}
```

## Slug rules (from source code)
- Slug = Str::slug(title) — Laravel's slug helper (lowercase, hyphens)
- Slugs are set on FIRST creation only (never updated after)
- If slug conflicts, appends -1, -2, etc.
- Empty slug → "page"

## File types processed
- `.md` files → pages
- `meta.json` → category metadata
- `index.md` / `README.md` → treated as folder index pages
- Everything else → ignored

## Sync behavior (from source code)
- Fetches ALL .md files from repo recursively
- Compares by source_path
- Creates new pages, updates existing, prunes deleted (if enabled)
- Existing slugs are NEVER changed (even if title changes)
- Pages that were soft-deleted get restored if file reappears
