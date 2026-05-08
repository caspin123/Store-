---
description: UI/UX design intelligence — generate design systems with 67 styles, 96 palettes, 57 font pairings across 13 stacks
---

Run the UI/UX Pro Max design system generator for the given request.

**User request:** $ARGUMENTS

Follow the workflow in `.claude/skills/ui-ux-pro-max/SKILL.md`:

1. Analyze the request to extract product type, style keywords, industry, and stack (default: `html-tailwind`)
2. Run the design system generator:
```bash
python3 .claude/skills/ui-ux-pro-max/scripts/search.py "$ARGUMENTS" --design-system
```
3. Supplement with domain searches as needed (style, typography, ux, chart, landing)
4. Get stack-specific guidelines:
```bash
python3 .claude/skills/ui-ux-pro-max/scripts/search.py "$ARGUMENTS" --stack html-tailwind
```
5. Implement the design using the generated system, following the Pre-Delivery Checklist in SKILL.md
