# Wiki Platform Rendering Engine — Complete Technical Reference

Source: HytaleModding/wiki GitHub repo (open source, MIT)

## Stack

- **Markdown parser**: `marked` (npm) with GFM enabled + `breaks: true`
- **Sanitizer**: `DOMPurify` — ALL HTML goes through sanitization
- **Syntax highlighting**: `highlight.js` with github-dark theme
- **Diagrams**: `mermaid` — detects by language tag or content pattern
- **Footnotes**: `marked-footnote` plugin
- **Heading IDs**: `marked-gfm-heading-id` plugin
- **Frontend**: React (TSX), Inertia.js (SPA)
- **Backend**: Laravel (PHP)
- **CSS**: Tailwind + @tailwindcss/typography (.prose class)

## What the renderer does

1. `marked.parse(content)` — converts markdown to HTML
2. `DOMPurify.sanitize(html)` — strips unsafe HTML
3. `addHeadingAnchors(html)` — adds copy-link buttons to headings
4. Renders into a `<div className="prose dark:prose-invert">` container

## Key implications for our wiki

### DOMPurify sanitization
- ALL inline HTML passes through DOMPurify
- Standard HTML elements (`<div>`, `<span>`, `<strong>`, `<em>`) survive
- `className` attributes survive (React uses className, but DOMPurify allows class)
- `style` attributes survive DOMPurify by default
- Script tags, event handlers, iframes are stripped
- **This means: `<div class="my-class">text</div>` WORKS in markdown**

### marked with GFM + breaks
- `breaks: true` — single newlines become `<br>` (important for table content)
- GFM tables fully supported
- GFM task lists supported
- Inline HTML mixed with markdown is supported (standard marked behavior)

### Custom code rendering
- Inline code: `text` → `<code class="inline-code">text</code>`
- Code blocks: highlighted via hljs, wrapped in `<pre class="hljs-code-block">`
- Mermaid blocks: rendered as diagrams (detected by language tag)

### Alert/callout system
- `> [!NOTE]` syntax → `<div class="markdown-callout markdown-callout-note">`
- Supports collapsible: `> [!NOTE]+` (open) or `> [!NOTE]-` (collapsed)
- Custom title: `> [!NOTE] My Custom Title`
- Types: note, tip, important, warning, caution

## CSS sanitizer (CustomCssSanitizer.php)

Custom CSS goes through server-side sanitization. BLOCKED:
- `</style>` tags
- ANY HTML/script-like markup (<script, <iframe, <object, <embed, <link, <meta, <img, <svg)
- `@import` statements
- `expression()` (legacy IE)
- `behavior:` (legacy IE)
- `-moz-binding:` (legacy Firefox)
- `javascript:`, `vbscript:`, `data:`, `file:` protocols

ALLOWED (everything else):
- All standard CSS properties
- CSS variables (:root, var())
- CSS selectors (class, id, attribute, pseudo-class, pseudo-element)
- @media queries
- @keyframes animations
- @font-face (though external URLs may be blocked by CSP)
- calc(), min(), max(), clamp()
- oklch(), hsl(), rgb() color functions
- CSS Grid, Flexbox
- All units (px, em, rem, %, vh, vw)

## How Major Dungeons likely does colored badges

Given that:
1. DOMPurify allows `<div>`, `<span>` with `class` attributes
2. Custom CSS can target any class
3. `style` attributes survive DOMPurify

Major Dungeons can use ANY of these approaches:

### Approach A: Inline HTML with classes (most likely)
```markdown
| Item | Qty | Chance |
|------|-----|--------|
| ![](icon) Item Name | 1 | <span class="chance high">100%</span> |
```
```css
.prose .chance { border-radius: 999px; padding: 2px 10px; font-weight: 600; font-size: 0.8rem; }
.prose .chance.high { background: #22c55e20; color: #22c55e; }
.prose .chance.mid { background: #eab30820; color: #eab308; }
.prose .chance.low { background: #ef444420; color: #ef4444; }
```

### Approach B: Inline styles (simplest, no CSS needed)
```markdown
| <span style="background:#22c55e20;color:#22c55e;border-radius:999px;padding:2px 10px">100%</span> |
```

### Approach C: Bold/italic wrappers with CSS
```markdown
| **100%** |  ← bold = high
| *70%* |    ← italic = mid
| 70% |      ← plain = low
```
```css
.prose td:last-child strong { color: green; }
.prose td:last-child em { color: goldenrod; }
```

## Supported markdown features (complete list)

### Text
- **Bold**, *Italic*, ~~Strikethrough~~, ***Bold+Italic***

### Structure
- Headings (h1-h6) with auto-generated IDs + copy-link buttons
- Paragraphs with automatic line breaks (breaks: true)
- Horizontal rules (---)
- Blockquotes (>)

### Lists
- Unordered (-)
- Ordered (1.)
- Task lists (- [x])

### Links & Images
- Standard links: [text](url "title")
- Images: ![alt](url "title")

### Code
- Inline: `code`
- Blocks: ```lang with syntax highlighting
- Mermaid diagrams: ```mermaid

### Tables (GFM)
- Standard | pipe | tables
- Left/center/right alignment (:---|:---:|---:)
- Inline HTML survives inside cells

### Alerts (GFM-style)
- [!NOTE], [!TIP], [!IMPORTANT], [!WARNING], [!CAUTION]
- Collapsible: [!NOTE]+ (open), [!NOTE]- (collapsed)
- Custom titles: [!NOTE] My Title

### Footnotes
- Reference: [^1]
- Definition: [^1]: content

### Inline HTML
- <div>, <span>, <p>, <strong>, <em> — ALL survive DOMPurify
- class attributes survive
- style attributes survive
- id attributes survive
- data-* attributes survive

### Escaping
- \* \# \[ etc.
