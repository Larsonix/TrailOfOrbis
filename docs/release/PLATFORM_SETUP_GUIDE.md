# Platform Setup Guide - Step by Step

Exactly what to do on each platform. Screenshots are in `docs/images/screenshots/`, logos in `docs/images/logo/`.

## Which file to paste where

| Platform | Format | File to paste |
|----------|--------|---------------|
| **CurseForge** | Markdown | `PLATFORM_DESCRIPTION.md` (between PASTE START/END markers) |
| **ModTale** | Markdown | `PLATFORM_DESCRIPTION.md` (between PASTE START/END markers) |
| **Modifold** | Markdown | `PLATFORM_DESCRIPTION.md` (between PASTE START/END markers) |
| **Thunderstore** | Markdown | `PLATFORM_DESCRIPTION.md` (between PASTE START/END markers) |
| **BuiltByBit** | BBCode | `BUILTBYBIT_DESCRIPTION.bbcode` (entire file) |
| **GitHub** | GitHub Markdown | Already done (README.md) |

---

## Image Assets Summary

### Logo
- **Square** : `docs/images/logo/logo-square.png` (339K) -use for CurseForge, ModTale, Modifold, Thunderstore icons
- **Wide** : `docs/images/logo/logo-wide.png` (312K) -use for banners or headers

### Screenshots (for galleries/carousels)
Upload ALL of these to every platform that has a gallery :

| File | Shows | Good for |
|------|-------|----------|
| `SkilltreeScreenshot.png` | 3D Skill Sanctum with colored nodes | **Hero image** -most unique feature |
| `Volcano.png` | Volcano Realm biome, lava, mobs | Realm system showcase |
| `Beach.png` | Beach Realm, ocean, islands | Realm variety |
| `Jungle.png` | Jungle Realm, dense vegetation | Realm variety |
| `Caverns.png` | Cavern Realm, underground | Realm variety |
| `ElementsAttributesUI.png` | 6 Elements allocation screen | UI / progression depth |
| `OffensiveStatsUI.png` | Offensive stats breakdown | UI / stat system depth |
| `EpicSwordTooltip.png` | Epic sword with modifiers | Gear system depth |
| `UncommonHelmetTooltip.png` | Uncommon helmet tooltip | Gear system |
| `HexcodeStaffRPGLegendaryTooltip.png` | Hexcode staff with RPG integration | Mod compatibility |
| `RaremapTooltip.png` | Rare Realm Map tooltip | Realm map system |
| `Realm-Entrance-UI.png` | Entering a Realm | Realm entrance flow |
| `AncientGatewayUpgradeUI.png` | Gateway upgrade interface | Gateway progression |
| `SkillTreeNodeHUD.png` | Skill node allocation HUD | Skill Tree UI |
| `LootFilterUI.png` | Loot filter configuration | Quality of life |
| `StonesCreativeInventoryFullList.png` | All 25 Stones | Crafting currency |

**Recommended gallery order** : SkilltreeScreenshot → Volcano → ElementsAttributesUI → EpicSwordTooltip → Beach → AncientGatewayUpgradeUI → Jungle → OffensiveStatsUI → RaremapTooltip → Caverns → Realm-Entrance-UI → SkillTreeNodeHUD → LootFilterUI → StonesCreativeInventoryFullList → HexcodeStaffRPGLegendaryTooltip → UncommonHelmetTooltip

---

## 1. CurseForge

### Project Creation

1. Go to https://www.curseforge.com → Sign in
2. Author Console → Projects → **Create a Project**
3. Fill in :
   - **Game** : Hytale
   - **Class** : Mods
   - **Name** : `Trail of Orbis`
   - **Summary** : `RPG overhaul inspired by Path of Exile and Last Epoch. 6 elements, 485-node skill tree, procedural Realm dungeons, 101 gear modifiers, 25 crafting stones, and an 11-stage damage pipeline. No classes -your build is your choices.`
   - **License** : Custom License → paste LGPL-3.0 text (or select LGPL-3.0 if available in dropdown)
   - **Main Category** : pick the most relevant (RPG if available, otherwise Adventure)
   - **Additional Categories** : up to 4 more relevant ones
   - **Logo** : Upload `logo-square.png`

### Description

CurseForge supports Markdown. Paste the description below into the description editor.

**DO NOT paste the README directly** -it has GitHub-specific HTML (`<p align="center">`, `<details>`) that won't render on CurseForge.

Use the clean Markdown description at the bottom of this file (Appendix A).

### Images

CurseForge has a **separate Images tab** in the Author Console. This is NOT part of the description -it's a dedicated gallery.

1. Go to Author Console → your project → **Images** tab
2. Upload all 16 screenshots
3. Set `SkilltreeScreenshot.png` as the **featured/primary** image
4. Add captions to each image (use the "Shows" column from the table above)

### File Upload

1. Author Console → **Files** tab → **Add File**
2. Upload `TrailOfOrbis-1.0.0.jar`
3. Display Name : `Trail of Orbis 1.0.0`
4. Release Type : Release
5. Game Version : select the Hytale version
6. Relations : Add `hyui` as Embedded Library
7. Changelog : paste from `CHANGELOG.md`

### After Upload

- Note the **Project ID** (numerical, from URL or sidebar)
- Generate **API Token** at https://www.curseforge.com/account/api-tokens
- Add both as GitHub Secrets : `CF_PROJECT_ID` and `CF_API_TOKEN`

---

## 2. ModTale

### Project Creation

1. Go to https://modtale.net → Sign in
2. Click **Create** in the navigation bar
3. Content Type : **Server Plugins**
4. Fill in :
   - **Name** : `Trail of Orbis`
   - **Summary** : `RPG overhaul inspired by Path of Exile and Last Epoch. 6 elements, 485-node skill tree, procedural Realm dungeons, 101 gear modifiers, 25 crafting stones, and an 11-stage damage pipeline.`

### Customize Project Page

After creation, you're on the management page :

1. **Logo** : Upload `logo-square.png` (512x512 recommended -ours is close enough)
2. **Banner** : Upload `logo-wide.png` or one of the biome screenshots cropped to 1920x640
3. **Description** : Paste the clean Markdown description (Appendix A below)
4. **Tags** : RPG, Dungeons, Skills, Items, Combat (select all relevant)
5. **License** : LGPL-3.0
6. **Links** :
   - Source Code : `https://github.com/Larsonix/TrailOfOrbis`
   - Wiki : `https://wiki.hytalemodding.dev/en/docs/Larsonix_TrailOfOrbis`
   - Discord : `https://discord.com/channels/1440173445039132724/1464056907005169756`

### Screenshots

ModTale has a **Screenshots** section on your project page.

1. Navigate to project settings → **Gallery** or **Screenshots**
2. Upload all 16 screenshots
3. Add titles/captions
4. The gallery displays as a carousel on your project page

### File Upload

1. Project page → **Files** tab
2. Upload `TrailOfOrbis-1.0.0.jar`
3. Version : `1.0.0`
4. Release channel : Release
5. Changelog : paste from `CHANGELOG.md`

### API Setup

1. Profile Icon → **Creator Dashboard** → **Developer Settings**
2. Generate API key
3. Copy **Project ID** (UUID from project URL or sidebar)
4. Add as GitHub Secrets : `MODTALE_API_KEY` and `MODTALE_PROJECT_ID`

---

## 3. Modifold

### Project Creation

1. Go to https://modifold.com → Sign in (GitHub/Discord/Telegram)
2. Click **Create Project** in header
3. Fill in :
   - **Name** : `Trail of Orbis`
   - **Summary** : `RPG overhaul for Hytale -6 elements, 485-node skill tree, procedural Realm dungeons, 101 gear modifiers, 25 crafting stones.`

### Customize

1. **Icon** : Upload `logo-square.png`
2. **Description** : Paste clean Markdown description (Appendix A)
3. **Gallery** tab : Upload all 16 screenshots. Mark `SkilltreeScreenshot.png` as **Featured** (this becomes the project cover)
4. **Links** tab :
   - Source Code : `https://github.com/Larsonix/TrailOfOrbis`
   - HytaleModding Wiki : `https://wiki.hytalemodding.dev/en/docs/Larsonix_TrailOfOrbis`
   - Discord : `https://discord.com/channels/1440173445039132724/1464056907005169756`
5. **Tags** tab : Select relevant tags (RPG, etc.)
6. **License** tab : LGPL-3.0

### Wiki Integration

Modifold auto-syncs with HytaleModding Wiki. After adding the wiki link in Links tab, your project gets a **Wiki tab** for free with all your wiki pages.

### File Upload

1. Upload `TrailOfOrbis-1.0.0.jar`
2. Version number : `1.0.0`
3. Changelog : paste from `CHANGELOG.md`
4. Release channel : Release

### Submit for Moderation

Go to **Moderation** tab → check all fields are complete → Submit.

---

## 4. BuiltByBit

### Resource Creation

1. Go to https://builtbybit.com → Register/Sign in
2. Set up Tebex wallet (required even for free resources)
3. Navigate to **Resources** → **Add Resource** → **Plugins** (Hytale section)
4. Fill in :
   - **Title** : `Trail of Orbis - RPG Overhaul for Hytale`
   - **Description** : Paste the BBCode description from `BUILTBYBIT_DESCRIPTION.bbcode`.
   - **Tags** : RPG, Hytale, Plugin, ARPG, Dungeons
   - **Pricing** : Free
   - **Icon** : Upload `logo-square.png`
   - **File** : Upload `TrailOfOrbis-1.0.0.jar`
5. Submit for approval (~4 hour review)

### Images

BuiltByBit supports images **inline in the description** using Markdown `![alt](url)` or BBCode `[IMG]url[/IMG]`. You can also add resource updates with screenshots.

For inline images, you'll need to **host the images somewhere** (e.g., upload to Imgur or use your GitHub repo raw URLs) :
```
![Skill Tree](https://raw.githubusercontent.com/Larsonix/TrailOfOrbis/main/docs/images/screenshots/SkilltreeScreenshot.png)
```

---

## 5. Thunderstore

### Package Preparation

Thunderstore uses a custom ZIP format. You need to build the package :

1. Create a folder `thunderstore-package/`
2. Add these files :

**`icon.png`** (256x256, required) -resize `logo-square.png` to 256x256

**`README.md`** -paste the clean Markdown description (Appendix A)

**`CHANGELOG.md`** -copy from repo root

**`manifest.json`** (Thunderstore format, NOT Hytale format) :
```json
{
  "name": "TrailOfOrbis",
  "description": "RPG overhaul : 6 elements, 485-node skill tree, procedural Realm dungeons, 101 gear modifiers, 25 crafting stones",
  "version_number": "1.0.0",
  "dependencies": [],
  "website_url": "https://github.com/Larsonix/TrailOfOrbis"
}
```

**`TrailOfOrbis-1.0.0.jar`** -the built JAR

3. ZIP the folder contents (not the folder itself)
4. Upload to Thunderstore under the Hytale community
5. Tags : `Plugins`, `Mods`

### Images

Thunderstore does NOT have a separate gallery. The `icon.png` is the only visual on the listing. The README renders as the project page -images can be inline via external URLs.

---

## Quick Checklist

For each platform, you need :

- [ ] **CurseForge** : Account → Project → Logo → Description (Appendix A) → Gallery (16 images) → Upload JAR → Get project ID + API token → Add GitHub Secrets
- [ ] **ModTale** : Account → Project → Logo + Banner → Description → Gallery → Tags → Links → Upload JAR → Get API key + project ID → Add GitHub Secrets
- [ ] **Modifold** : Account → Project → Icon → Description → Gallery (set featured) → Links (wiki auto-syncs) → Tags → License → Upload JAR → Submit for moderation
- [ ] **BuiltByBit** : Account → Tebex wallet → Add resource → Description → Icon → Upload JAR → Submit for approval
- [ ] **Thunderstore** : Team → Build ZIP package (icon + README + CHANGELOG + manifest.json + JAR) → Upload → Tags
