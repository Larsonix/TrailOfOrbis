# Hytale Font Character Support

Reference for which characters can be used in player-facing text (chat messages, banners, toasts, HUD labels, tooltips, notifications). Characters outside the supported range render as `?` in-game.

## Font Atlas Analysis (2026.03.26)

Hytale uses MSDF (Multi-channel Signed Distance Field) bitmap font atlases. Each font has a fixed set of pre-rendered glyphs. Characters not in the atlas cannot be rendered.

### Fonts and Their Glyph Ranges

| Font | File | Glyphs | Range | Used For |
|------|------|--------|-------|----------|
| NunitoSans Variable | `NunitoSans-VariableFontGlyphs.json` | 448 | U+0020 - U+04FF | Chat messages (main font) |
| NunitoSans Medium | `Fonts/NunitoSans-Medium.json` | 438 | U+0020 - U+2052 | UI labels |
| NunitoSans ExtraBold | `Fonts/NunitoSans-ExtraBold.json` | 438 | U+0020 - U+2052 | Bold UI text |
| NotoSans Bold | `Fonts/NotoSans-Bold.json` | 686 | U+0020 - U+206F | Secondary bold |
| NotoMono Regular | `Fonts/NotoMono-Regular.json` | 480 | U+0020 - U+2044 | Monospace (console) |
| Lexend Bold | `Fonts/Lexend-Bold.json` | 216 | U+0020 - U+2044 | Headings |

**Location**: `/home/larsonix/work/Hytale-Decompiled-Full-Game/ClientData/Shared/`

### What's Supported

- **Basic Latin** (U+0020 - U+007E): Full ASCII — letters, digits, punctuation, symbols
- **Latin-1 Supplement** (U+0080 - U+00FF): Accented characters (e, a, u, n, etc.)
- **Latin Extended-A/B** (U+0100 - U+024F): Extended European characters
- **Cyrillic** (U+0400 - U+04FF): Russian, Ukrainian, etc. (chat font only)
- **Some General Punctuation** (U+2000 - U+206F): Varies by font, NOT complete

### What's NOT Supported (Renders as `?`)

| Category | Range | Examples | Status |
|----------|-------|----------|--------|
| Arrows | U+2190 - U+21FF | `->` `<-` `<->` | NOT in any font |
| Box Drawing | U+2500 - U+257F | `=` `-` `|` `+` | NOT in any font |
| Block Elements | U+2580 - U+259F | `#` | NOT in any font |
| Geometric Shapes | U+25A0 - U+25FF | `*` `o` `.` | NOT in any font |
| Miscellaneous Symbols | U+2600 - U+26FF | `*` | NOT in any font |
| Dingbats | U+2700 - U+27BF | Various | NOT in any font |
| CJK | U+4E00+ | Chinese/Japanese/Korean | NOT in any font |
| Emoji | U+1F600+ | All emoji | NOT in any font |

## Safe Replacements for Common Decorations

Use these ASCII alternatives in all player-facing text:

| Intent | Bad (renders as `?`) | Good (ASCII) | Notes |
|--------|---------------------|-------------|-------|
| Section border | `═══════` (U+2550) | `=======` | Same visual weight |
| Arrow (transition) | `->` (U+2192) | `>` or `->` | `->` is two chars but reads well |
| Star / milestone | `*` (U+2605) | `*` | Simple, universally understood |
| Bullet point | `*` (U+2022) | `-` or `*` | Standard list markers |
| Dash separator | `-` (U+2014) | `--` or `-` | Double dash for emphasis |
| Check mark | (U+2713) | `[x]` or `OK` | Bracket notation |
| Cross mark | (U+2717) | `[!]` or `NO` | Bracket notation |
| Right pointer | (U+25BA) | `>` | Simple angle bracket |

## Rules for Player-Facing Text

1. **ASCII only** (U+0020 - U+007E) for all decorative/structural characters in messages
2. **Extended Latin OK** for actual text content (names, descriptions with accents)
3. **Cyrillic OK** in the chat font only (NunitoSans Variable)
4. **Never use** box drawing, arrows, geometric shapes, stars, bullets, or any symbol above U+007E for decoration
5. **Test visually** if using characters in the U+0080 - U+024F range — coverage varies by font
6. **HyUI labels** use NunitoSans Medium/ExtraBold — same restrictions apply

## How to Verify a Character

```bash
# Check if a character exists in the main chat font
python3 -c "
import json
with open('/home/larsonix/work/Hytale-Decompiled-Full-Game/ClientData/Shared/NunitoSans-VariableFontGlyphs.json') as f:
    data = json.load(f)
char = 'YOUR_CHAR_HERE'
code = ord(char)
print(f'{char} (U+{code:04X}): {\"YES\" if str(code) in data else \"NO\"}')"
```

## History

- **2026-03-29**: Initial documentation. Discovered via `?` rendering in skill tree allocation feedback and level-up celebration messages. All Unicode decorative characters (borders, arrows, stars) confirmed absent from every Hytale font atlas.
