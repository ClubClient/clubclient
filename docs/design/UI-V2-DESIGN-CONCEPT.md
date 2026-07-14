# CLUB UI — Design Concept (V2)

Status: PROPOSAL (awaiting approval before implementation)
Constraint: works entirely within the frozen design spec & existing tokens (no glass/blur/glow,
flat accent #7CABFF, Inter). Where a token genuinely limits quality it is flagged, not changed.

---

## 0. The thesis

Premium is **restraint + space + hierarchy**, not effects. The current build looks like "neat
primitives" because every surface sits on ~3 of the palette's 9 dark tones, spacing is even (no
rhythm), type has no real hierarchy, and accent is sprinkled. We fix the **system**, not the widgets.

**First-impression goal:** opening the menu should read as *expensive commercial software* — a calm,
dark, spacious shell; one disciplined line of accent; confident type; clear depth by tone alone.

**Signature — "one accent spine":** color appears *only where state lives* — the active category
indicator, the selected-module marker, enabled toggles, slider fills, focus. Everything else is a
neutral monochrome canvas. One thread of color through an otherwise quiet UI = the memorable thing.

---

## 1. Surface & depth system (flat — tone + hairline, never shadow/glow)

Depth is built by spending the full ink ramp across **zones**, each a distinct step, divided by 1px
hairlines. Recessed = darker, focus = lighter.

```
LAYER          TOKEN            ROLE
scrim          ink0 @ ~85%      dims the world behind the window (flat wash, NOT blur)
window shell   ink4  #0F1624    the floating frame  · border STRONG · radius lg(14)
nav rail       ink2  #0B111A    recessed navigation column (darkest → sits back)
header/content ink4  #0F1624    the canvas (shell tone) · zones split by hairlines
detail pane    ink5  #131B2A    raised config surface (lightest → draws the eye)
row : hover    ink5  #131B2A    tone-lift on hover
row : selected ink5 + accent marker
controls       ink6  #18212F    inset tracks (toggle/checkbox/slider groove)
```

Why: rail (ink2) → content (ink4) → detail (ink5) are **3 clearly separated tones**, so the screen
has real depth with zero shadow. The previous "Panel vs Card" subtlety disappears because zones jump
multiple ramp steps, not one. Borders strengthen with elevation: subtle (dividers) → default (cards,
controls) → strong (window edge).

---

## 2. Spacing, grid & rhythm

Base unit 4px (tokens 4/8/12/16/24/32). Rhythm comes from **consistent row heights + deliberate
zone padding**, not uniform gaps everywhere.

```
Window            centered, responsive; target ~760×460, min screen margin lg(16)
Header height     48
Nav rail width    180   · item height 36 · left pad lg(16) · item gap xs(4)
Content pad       lg(16) · module row height 48 · row→row 1px divider
Detail pane width 260   · pad lg(16) · setting row height 36 · row gap md(12)
Section header    label, with sm(8) space above its group
```

Why: a fixed 48px module row + 36px setting row creates a steady vertical beat; generous pane
padding (16) gives the "air" that cheap UIs lack. Density is comfortable, never cramped.

---

## 3. Typography (within existing Inter roles)

| UI level            | Role (current)        | Color      |
|---------------------|-----------------------|------------|
| Wordmark "CLUB"     | display 20 semibold   | textHi     |
| Header meta/search  | label 12 medium       | textMuted  |
| Category label      | label 12 medium       | active textHi / idle textMuted |
| Module name         | heading 15 medium     | textHi     |
| Module description  | caption 12 regular    | textDesc   |
| Setting label       | label 12 medium       | textMuted  |
| Value / number      | body 13 medium        | textHi     |
| Section header      | label 12 medium       | textDesc   |

Hierarchy is carried by **size + color tier**, since weight options are limited (REGULAR/MEDIUM/
SEMIBOLD only). KNOWN LIMIT: a SEMIBOLD-13 role would give module names/buttons more "CTA weight";
deferred until the full system is seen (then we decide if it's worth a type-token addition).

---

## 4. Contrast & visual weight

Eye path by design: **active category → selected module → its primary toggle.**
Weight ladder (heaviest first): enabled accent toggle → active category indicator → module name
(textHi) → description (textDesc) → chrome (dividers, rail idle). Text uses 4 deliberate tiers
(textHi/Muted/Desc/Faint) — never one flat grey.

---

## 5. Accent discipline (the spine)

Accent (#7CABFF) appears ONLY on: active-category indicator, enabled toggle/checkbox, slider
fill+knob rim, focus ring, primary button, selected-module marker. Removed from all chrome
(scrollbars, idle borders). Flat — no gradient/glow (the spec's optional toggle-gradient is dropped:
invisible at size, breaks coherence).

Why: luxury reads as restraint. One accent against a disciplined neutral field feels far more
expensive than accent everywhere.

---

## 6. Motion (first impression, restrained)

- Open: scrim fade (fast 0.12) → window fade + scale 0.98→1.0 (normal 0.20, decelerate). No bounce.
- Category switch: accent indicator slides (normal 0.20); content cross-fades (fast).
- Hover/press/focus: as in the component language (lighten / darken+grow / offset ring).

---

## 7. Composition — the screens

### 7a. Main menu (master-detail: rail | list | detail)

```
┌────────────────────────────────────────────────────────────────────────────┐
│  CLUB                                                  v2.5     ⌕ Search       │  header · ink4 · ▁ divider
├──────────────┬───────────────────────────────────────┬─────────────────────-┤
│ ▍ COMBAT     │  KillAura                         [▣]  │  KillAura             │
│   MOVEMENT   │  Auto-attack nearby hostiles           │  ───────────────────  │
│   RENDER     │ ───────────────────────────────────────│  Mode        Single ▾ │
│   PLAYER     │  Velocity                         [□]  │  Range       4.0  ──●──│
│   WORLD      │  Reduce knockback                      │  Delay (ms)  120  ─●───│
│   HUD        │ ───────────────────────────────────────│  Targets     Players ▾│
│   SETTINGS   │  Reach                            [▣]  │                       │
│              │  Extend attack range                   │  [ Reset ]   [ Bind ] │
│              │ ───────────────────────────────────────│                       │
│  ▍ = active  │  Criticals                        [□]  │                       │
└──────────────┴───────────────────────────────────────┴─────────────────────-┘
   ink2 (rail)        ink4 (content canvas)                ink5 (detail pane)
```

- **Rail**: categories; active = 3px accent indicator ▍ + textHi label; idle = textMuted. The
  indicator slides between categories (the spine).
- **Module row** (48px): name (heading) + description (caption) stacked left; enable toggle right;
  whole row clickable to select. Selected row = ink5 lift + a left accent marker echoing the rail.
- **Detail pane**: settings of the selected module — dropdowns, sliders, toggles, keybind — with
  section dividers and footer actions. This "browse → configure" split is what reads as a product.

### 7b. Module row anatomy

```
│ ▏ KillAura                                                     [▣] │  ▏=selected marker (accent)
│   Auto-attack nearby hostiles                                      │  name=heading textHi, desc=caption textDesc
```

### 7c. Settings detail row

```
│  Range                                              4.0  ────────●──────  │  label(muted)  value(textHi)  slider
│  Mode                                                       Single  ▾     │  label         dropdown
```

### 7d. HUD editor

Same shell; canvas becomes a live preview of the game viewport with draggable HUD modules snapping to
an alignment grid; a right detail pane edits the selected HUD element (position, scale, color tier).

### 7e. In-game HUD (no scrim — over gameplay)

```
 CLUB                                   KillAura     ← arraylist, right-aligned
                                        Speed           top item / active = accent,
 ▸ FPS 240                              Reach           rest = textHi, fades to muted
 ▸ BPS 12.4                             Velocity
```

- HUD chips: optional flat dark plate (ink0 @ ~55%) + 1px subtle border for legibility over bright
  scenes (flat, not blur). Numbers tabular-aligned. Accent only on the most important value.
- The HUD shares type + accent language but is lighter/quieter (it is ambient, not focal).

---

## 8. Component roles in the system

The already-built widgets are the **atoms**; this concept defines how they compose:
- Toggle → module enable + boolean settings. Checkbox → multi-select lists. Slider → numeric
  settings. Button → footer actions (Reset/Bind). Panel → setting groups. Card → standalone
  callouts. Window → the shell. ScrollArea → list/detail overflow. Dropdown/Keybind → NEW atoms
  needed (see §10).

---

## 9. Why this is not "just dark + blue"

Differentiators that make it specific & premium rather than a generic client:
1. Full-ramp tone zoning → genuine flat depth (most clients use 2 tones).
2. The "one accent spine" restraint → calm, expensive.
3. Master-detail composition → product, not a toggle list.
4. Strict rhythm (48/36 rows, 16 pads) + type tiers → "designed".
5. A confident header identity block → sets tone on open.

---

## 10. Implementation roadmap (AFTER approval)

1. Shell + zones (scrim, window, rail, content, detail) using full ink ramp.
2. Navigation rail + sliding accent indicator.
3. Module row + selection model.
4. Detail pane layout; wire existing controls.
5. NEW atoms: Dropdown, Keybind field, Search field (built in the same language).
6. HUD language pass + HUD editor.
7. Motion pass (open/switch).

Each step screenshotted via the headless gallery/preview harness.

---

## 11. Open decisions (need your call)

- **Layout model**: master-detail 3-pane (recommended) vs 2-pane with inline-expanding settings.
- **Surface contrast**: if the flat tone ladder still doesn't separate enough in-engine, the first
  justified token tweak is widening 1–2 ink steps (approval required).
- **Type weight**: SEMIBOLD-13 role for names/CTAs (deferred; revisit after seeing the full shell).
