# Wiki HTML Component Patterns — Reference

Based on Major Dungeons' proven approach (GitHub-synced, HTML survives DOMPurify).

## Key Rules
1. HTML blocks must be SEPARATED from markdown by blank lines
2. Each HTML block is self-contained (open tag → content → close tag)
3. Standard markdown works normally BETWEEN HTML blocks
4. DOMPurify allows: div, span, figure, img, a with class/style/src/href attributes

## Our Prefix: `too-` (Trail of Orbis)

## Component 1: Hero Image (Figure)
```html
<figure class="too-figure">
  <img src="URL" alt="Description">
</figure>

Description text here using normal markdown.

<div class="too-figure-end"></div>
```

## Component 2: Loot/Drop Table
```html
<div class="too-loot-table">
  <div class="too-loot-header">
    <span>Item</span>
    <span>Qty</span>
    <span>Chance</span>
  </div>
  <a class="too-loot-row" href="./page-slug">
    <div class="too-loot-item">
      <img class="too-loot-icon" src="ICON_URL">
      <span class="too-loot-name">Item Name</span>
    </div>
    <span class="too-loot-qty">1</span>
    <div class="too-loot-chance-wrap">
      <span class="too-chance too-chance-high">70%</span>
    </div>
  </a>
</div>
```

Chance tiers: `too-chance-max` (100%), `too-chance-high` (50-99%), `too-chance-mid` (20-49%), `too-chance-low` (5-19%), `too-chance-rare` (<5%)

## Component 3: Rarity Table
```html
<div class="too-rarity-table">
  <div class="too-rarity-header">
    <span>Rarity</span><span>Max Mods</span><span>Stat Mult</span><span>Drop Chance</span>
  </div>
  <div class="too-rarity-row too-rarity-common">
    <span class="too-rarity-name">Common</span>
    <span>1</span>
    <span class="too-mult">0.3x</span>
    <span class="too-chance too-chance-high">75%</span>
  </div>
</div>
```

## Component 4: Stat Table (for element/attribute pages)
```html
<div class="too-stat-table">
  <div class="too-stat-header">
    <span>Element</span><span>Stats Per Point</span><span>Ailment</span>
  </div>
  <div class="too-stat-row too-elem-fire">
    <span class="too-elem-name">Fire</span>
    <span>+0.4% Physical Damage, +0.3% Charged Attack</span>
    <span class="too-ailment">Burn</span>
  </div>
</div>
```

## Component 5: Stone Table (item list with icons)
```html
<div class="too-item-table">
  <div class="too-item-header">
    <span>Stone</span><span>Rarity</span><span>Target</span><span>Effect</span>
  </div>
  <a class="too-item-row" href="#stone-name">
    <div class="too-item-cell">
      <img class="too-item-icon" src="ICON_URL">
      <span class="too-item-name">Stone Name</span>
    </div>
    <span class="too-rarity-badge too-rarity-common">Common</span>
    <span>Both</span>
    <span>Effect description</span>
  </a>
</div>
```

## Base URL for our images
`https://wiki.hytalemodding.dev/storage/mods/019da288-1a74-71ad-bb01-6e0ae3d015c6/files/`

## Which pages get HTML components vs stay markdown

### HTML components (visual data tables):
- stones/index.md — All Stones table (icons + rarity), drop source tables
- gear/rarities.md — Rarity tier table
- loot/drop-mechanics.md — Drop chance tables
- loot/container-loot.md — Container tier table
- realms/biomes.md — Biome overview with icons
- attributes/index.md — Element overview table
- gear/index.md — Equipment overview

### Stay as markdown (with link-based coloring):
- All combat pages — text-heavy, tables are formula/reference
- All ailment sub-pages — stat tables are simple
- skill-tree pages — mostly text + simple tables
- leveling pages — formula tables
- mobs pages — stat tables
- All other pages — commands, FAQ, changelog, etc.
