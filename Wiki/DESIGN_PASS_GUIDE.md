# Wiki Design Pass — Agent Reference Guide

DO NOT rewrite any text content. Only ADD visual elements using inline HTML.

## Our mod base URL
`https://wiki.hytalemodding.dev/storage/mods/019da288-1a74-71ad-bb01-6e0ae3d015c6/files/`

## Screenshot URLs (for hero images)
- elements-attributes: f8ab6f94-c708-4df0-badd-be1585d4353f
- gateway-upgrade: 4c997637-a960-44ec-a91b-127e9ee4236d
- loot-filter: 515373c1-3d26-42b7-9dcd-0c6cad4ca15f
- realm-entrance: fde371b3-b724-4d48-8a49-1cefc2acad5d
- skill-tree: 272105d8-e2d3-450d-b95c-34782e3de1f6
- skill-tree-node-hud: 588026db-55a3-4e52-baa0-f8e4419cacc0
- stats-offensive: 92721ff2-0c75-46bf-a8fe-37d304f6d8f2
- stones-inventory: 5f74398e-5439-4309-a466-26a2ff5a21b6
- epic-sword: 51f7b15a-75d8-456b-960b-ae3d7b0bdad0
- hexcode-staff-legendary: 64e3b30f-5046-4ef6-b61d-7b134c927f89
- rare-map: 20cb9ed1-151f-4266-af81-17b4c47125b4
- uncommon-helmet: 80be68e5-fcc0-450d-8bad-cdec5ccbe9ab
- logo-square: 46efc448-03a6-41df-94c0-58afafe567a8
- logo-wide: b9f31518-07a1-4a25-bd20-277bb21d722d

## Biome screenshot URLs
- beach: 65752b3a-cd46-4287-bb04-91d927ffb5ac
- caverns: 4699def8-70a2-4016-aca6-bf4f88d805a2
- desert: 1acc0607-df8b-48da-8c23-a46b743fd1ed
- forest: bab96708-a976-4ee8-97ea-e29289a1d938
- frozen-crypts: 50bd4924-763e-460e-b2ea-7ac10d7f106c
- jungle: 329a2600-3e14-4386-acaa-0ac26ea6ccec
- sand-tombs: 6fb4a9b5-a02c-4827-bfc1-454c57935545
- swamp: 17400c90-9bfb-4fa7-acc8-201ccfc9b9f8
- tundra: 01b956b5-daf6-463a-a0da-49ba71d4d121
- volcano: bec50545-87db-4f36-848c-9670e3fbe3c8

## Realm map icon URLs
- beach: 44478b88-37f8-4de9-b9bf-2b1be459da5e
- caverns: 3ebd9437-eac5-4d4f-b219-b916790a5c45
- desert: 03df9491-348c-46a5-a9ba-c0a4340bff01
- forest: aeb8933f-6886-4c83-8c93-4f37abd6b777
- frozen-crypts: dd23b3a8-1f6c-415d-bdec-63f4cb58210b
- realm-map: e5dd9919-1e54-457e-a1ca-4379aaf3d298
- sand-tombs: 9e95e90f-52e2-4161-9715-0dce31b38f45
- swamp: 88c4deaf-2bc5-4833-a7c5-6835b3399669
- tundra: ecff1abb-8477-41fc-9662-480ffe0445fb
- volcano: 0830e55b-08b3-44f5-9e3d-6bd8e08d8484

## Available CSS classes

### Percentage badges
```html
<span class="pct high">100%</span>     <!-- green, for guaranteed/90%+ -->
<span class="pct mid-high">70%</span>  <!-- teal, for 50-89% -->
<span class="pct mid">30%</span>       <!-- gold, for 20-49% -->
<span class="pct low">12%</span>       <!-- orange, for 5-19% -->
<span class="pct rare">3%</span>       <!-- red, for <5% -->
```

### Rarity badges
```html
<span class="rarity common">Common</span>
<span class="rarity uncommon">Uncommon</span>
<span class="rarity rare">Rare</span>
<span class="rarity epic">Epic</span>
<span class="rarity legendary">Legendary</span>
<span class="rarity mythic">Mythic</span>
<span class="rarity unique">Unique</span>
```

### Element badges
```html
<span class="elem fire">Fire</span>
<span class="elem water">Water</span>
<span class="elem lightning">Lightning</span>
<span class="elem earth">Earth</span>
<span class="elem wind">Wind</span>
<span class="elem void">Void</span>
```

### Ailment badges
```html
<span class="ailment burn">Burn</span>
<span class="ailment freeze">Freeze</span>
<span class="ailment shock">Shock</span>
<span class="ailment poison">Poison</span>
```

### Mob tier badges
```html
<span class="mob-tier normal">Normal</span>
<span class="mob-tier elite">Elite</span>
<span class="mob-tier boss">Boss</span>
<span class="mob-tier miniboss">Mini-Boss</span>
```

### Container/realm tier badges
```html
<span class="tier basic">Basic</span>
<span class="tier dungeon">Dungeon</span>
<span class="tier boss">Boss</span>
<span class="tier special">Special</span>
```

### Realm size badges
```html
<span class="realm-size small">Small</span>
<span class="realm-size medium">Medium</span>
<span class="realm-size large">Large</span>
<span class="realm-size huge">Huge</span>
```

### Damage type badges
```html
<span class="dmg physical">Physical</span>
<span class="dmg fire">Fire</span>
<span class="dmg water">Water</span>
<!-- etc — same elements as .elem -->
```

### Skill tree node badges
```html
<span class="node basic">Basic</span>
<span class="node notable">Notable</span>
<span class="node keystone">Keystone</span>
<span class="node origin">Origin</span>
<span class="node entry">Entry</span>
<span class="node synergy">Synergy</span>
```

### Multiplier/stat values
```html
<span class="mult">2.8x</span>      <!-- gold, for multipliers -->
<span class="stat-val">+50</span>   <!-- green, for positive stats -->
<span class="stat-neg">-20%</span>  <!-- red, for penalties -->
```

### Craft arrow
```html
<span class="craft-arrow">⏱ 5s</span>  <!-- renders as "← ⏱ 5s" -->
```

## Rules for applying design

1. **DO NOT** change any existing text content or explanations
2. **DO** wrap rarity names in `<span class="rarity X">` where they appear in tables
3. **DO** wrap element names in `<span class="elem X">` where they appear in tables
4. **DO** wrap percentage/chance values in `<span class="pct X">` in tables
5. **DO** wrap multiplier values in `<span class="mult">` in tables
6. **DO** add hero images at the top of pages where a relevant screenshot exists
7. **DO** add inline item icons in tables where relevant uploaded images exist
8. **DO** keep existing link-based coloring for inline text (only use span badges in tables)
9. Percentage badge thresholds: 90%+ = high, 50-89% = mid-high, 20-49% = mid, 5-19% = low, <5% = rare
10. For multiplier values like "0.3x" to "2.8x", just use `<span class="mult">` (neutral gold color)
