# 5.1 - Markdown Reference

Source: https://hytalemodding.dev/en/docs/wiki/5-styling (from GitHub repo)

## Text formatting
```md
**Bold text**
*Italic text*
~~Strikethrough~~
**_Bold and italic_**
```

## Headings
```md
# Heading 1
## Heading 2
### Heading 3
#### Heading 4
```

## Lists
### Unordered
```md
- Item
- Item
  - Nested item
```

### Ordered
```md
1. First
2. Second
   1. Nested
```

### Task list
```md
- [x] Done
- [ ] Not done
```

## Links
```md
[Link text](https://example.com)
[Link with title](https://example.com "Hover title")
```

## Images
```md
![Alt text](https://example.com/image.png)
![Alt text](https://example.com/image.png "Image title")
```

## Code
### Inline: Use `code` inside text.
### Block: Supports js, ts, css, html, json, md, bash, and others.

## Blockquotes
```md
> This is a blockquote.
```

## Alerts
```md
> [!NOTE]
> Useful information the reader should know.

> [!TIP]
> Helpful advice for doing things better.

> [!IMPORTANT]
> Key information the reader must be aware of.

> [!WARNING]
> Potential issues the reader should watch out for.

> [!CAUTION]
> Actions that may cause problems or data loss.
```

## Tables
```md
| Column A | Column B | Column C |
| -------- | -------- | -------- |
| Cell     | Cell     | Cell     |
```

Alignment:
```md
| Left     | Center   | Right    |
| :------- | :------: | -------: |
| Cell     | Cell     | Cell     |
```

## Footnotes
```md
Some text with a footnote.[^1]
[^1]: This is the footnote content.
```

## Horizontal rule: `---`

## Escaping: `\*Not italic\*`

> NOTE: Imported components are only available via GitHub sync.
> The built-in editor supports custom components registered on the platform.
