# 5.3 - Custom CSS Reference

Source: https://hytalemodding.dev/en/docs/wiki/5-styling (from GitHub repo)
Platform: shadcn/ui + Tailwind CSS

## CSS Variables (override on :root)

```css
:root {
  /* Page */
  --background: ;         /* Page background */
  --foreground: ;         /* Main text color */
  --border: ;             /* Element borders */
  --radius: ;             /* Border radius */

  /* Cards */
  --card: ;               /* Card background */
  --card-foreground: ;    /* Card text color */

  /* Primary accent */
  --primary: ;            /* Accent color — icons, mod title */
  --primary-foreground: ; /* Text on primary-colored elements */

  /* Secondary */
  --secondary: ;            /* Secondary background */
  --secondary-foreground: ; /* Secondary text */

  /* Muted */
  --muted: ;              /* Subtle background (badges, tags) */
  --muted-foreground: ;   /* Subtle text (descriptions, timestamps) */

  /* Hover / active states */
  --accent: ;             /* Hover background */
  --accent-foreground: ;  /* Hover text */

  /* Danger */
  --destructive: ;        /* Delete buttons, error states */
}
```

## Direct selectors

For things not covered by variables:
```css
[data-slot="card"] {
  border-radius: var(--radius);
}
```

## Prose (Content Styling)

Use `.prose` + element selector:

```css
/* Headings */
.prose h1 { }
.prose h2 { }
.prose h3 { }
.prose h4 { }

/* Text */
.prose p { }
.prose strong { }     /* **Bold** */
.prose em { }         /* *Italic* */
.prose del { }        /* ~~Strikethrough~~ */

/* Links */
.prose a { }

/* Lists */
.prose ul { }
.prose ol { }
.prose li { }
.prose input[type="checkbox"] { }

/* Images */
.prose img { }

/* Code */
.prose code { }       /* `Inline code` */
.prose pre { }        /* Code block */
.prose pre code { }

/* Blockquote */
.prose blockquote { }

/* Alerts */
.prose .markdown-callout { }
.prose .markdown-callout-note { }
.prose .markdown-callout-tip { }
.prose .markdown-callout-important { }
.prose .markdown-callout-warning { }
.prose .markdown-callout-caution { }
.prose .markdown-callout-title { }
.prose .markdown-callout-content { }

/* Table */
.prose table { }
.prose thead { }
.prose th { }
.prose td { }
.prose tr { }

/* Horizontal rule */
.prose hr { }

/* Footnotes */
.prose sup { }
.prose .footnotes { }
```

## Custom Classes (MDX only — requires GitHub sync)

Create a class in CSS:
```css
.prose .green {
  color: green;
}
```

Apply in MDX:
```mdx
<div className="green">Green text!</div>
```

## KEY FINDING: Custom classes via MDX divs!
This means Major Dungeons likely uses custom CSS classes applied via
<div className="..."> wrappers around table cells or sections.
This is only available through GitHub sync (not the web editor).
