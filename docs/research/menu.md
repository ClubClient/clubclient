# menu

## SUMMARY
The complaint decodes into one root cause with two symptoms. SYMPTOM 1 ("вкладки туда-сюда ездят"): the menu animates LAYOUT — four separate things physically travel or resize every time you merely NAVIGATE (rail pill slides Y, the whole card grid is destroyed and re-plays a 42ms/card staggered fade+6px rise on every category click, the RMB popover grows in and re-tweens its height on every dropdown/tab, the Hands segment pill slides). None of these is a state the player changed; they are all consequences of moving around. SYMPTOM 2 ("однообразно и дёшево"): all 12 modules are the identical 58px box (chip 22 + stripe 14×3 + name), all popovers are the identical 236px label-left/control-right sheet, the only per-module variation is a 13px icon and a 2–3.5%-alpha ghost randomised by hashCode — variety by noise, not by meaning. A card never shows what its module IS or what you set it to: Zoom at 8× looks exactly like Zoom at 2×. Combat has ONE module in a 4-column grid, so the richest frame in the menu is also the emptiest. Fix = a motion doctrine (animate state, never layout) + cards that are instruments (each shows its own live value) + footprints that differ by importance + a permanent bench that replaces the popover so nothing has to "grow in". I would build "The Bench".

## PROPOSAL
═══════════════════════════════════════════════════════════════════════
PART 1 — WHAT HE IS ACTUALLY SEEING
═══════════════════════════════════════════════════════════════════════

THE CURRENT INTERACTION MODEL (from ClubMenuScreen.java, read in full)

Fixed 660×380 window, dead centre, in the Club canvas (540 units tall, Stage 60/63).
Header 48 (♣ CLUB on the tray axis, search right-aligned to the well's right line).
Footer 36 (profile chip + version whisper). Body = a shallow tray (rail) + a deep well.

  ┌────────────────────────────────────────────────────────────┐ 660×380
  │ ♣ CLUB                                    [🔍 Search    /]  │ 48
  ├──────────┬─────────────────────────────────────────────────┤
  │▌ Combat  │ ┌───────┐ ┌───────┐ ┌───────┐ ┌───────┐         │  ← 4 cols,
  │  Visuals │ │▪ Zoom │ │▪ Scrn │ │▪ Full │ │▪ NoHur│         │    58px,
  │  Player  │ └───────┘ └───────┘ └───────┘ └───────┘         │    identical
  │  Misc    │ ┌───────┐ ┌───────┐                             │
  │          │ │▪ NoFir│ │▪ NoBob│                             │
  │          │ └───────┘ └───────┘                             │
  │          │       ┌──────────────────┐ ← RMB popover        │
  │          │       │ Strength ──●───  │   GROWS IN (Reveal)  │
  │          │       │ Toggle key   [C] │   + height ValueTween│
  │          │       │ [ Reset ]        │                      │
  │          │       └──────────────────┘                      │
  ├──────────┴─────────────────────────────────────────────────┤
  │ ♣ Default ▾                                   Club 0.1.1   │ 36
  └────────────────────────────────────────────────────────────┘

  • Rail (tray, 172px): 4 rows × 36px. TWO objects travel on a click — a surfaceHi
    pill (indicator Transition) and a 4px category-coloured bar, both easing to the
    new row Y, plus a hue lerp (railBarBlend).                 [:963-965, :1018-1019]
  • Grid: a ScrollArea over a 4-col Grid of ModuleTiles. On EVERY category change
    setCategory() → rebuildGrid(CATEGORY) → tileMotion.clear(); grid.clear(); every
    card re-created with TileMotion.categoryEnter(i++): 42ms stagger, 280ms fade,
    6px rise. Clicking "Player" plays 3 animations; "Visuals" plays 6. [:309-320, :337-360]
  • Popover (RMB): a 236px sheet under the cards. Reveal grows it in, popHTween eases
    its height — and it re-heights whenever a dropdown expands (which HIDES every
    sibling row) or a Hands tab flips.                          [:1032-1064, :650-668]
  • SegmentRow (Hands Right/Left): a fifth travelling object — segSlide pill. [:1713-1750]
  • Search: name-only live filter with the owner-frozen Stage-21 reflow (exit cascade
    from the tail, survivors glide at +70ms, enters at +110ms).

"ВКЛАДКИ ТУДА-СЮДА ЕЗДЯТ" — DECODED

Four candidates, and I don't need to pick one, because they are the same bug:

  (a) the rail pill + bar sliding vertically       ← the most literal reading of "ездят"
  (b) the whole grid dissolving and re-entering    ← the loudest, and it fires on every click
  (c) the popover growing in / re-heighting        ← the one that moves while you're USING it
  (d) the Hands segment pill

ROOT CAUSE: **the menu animates layout, not state.** Every one of (a)–(d) is motion
caused by the player NAVIGATING, not by the player CHANGING anything. Meanwhile the one
thing that genuinely IS a state change — a module going on — animates as a soft colour
lerp you can barely see. The motion budget is spent entirely on the wrong events. "Постоянно"
is the tell: he isn't objecting to one animation, he's objecting to the fact that the
interface is *never still* while he uses it. Expensive software is calm; things move when
YOU move them.

"ОДНООБРАЗНО И ДЁШЕВО" — DECODED

  • 12 modules → 12 identical 58px boxes. TILE_H/CHIP/STRIPE/NAME are constants. [:1315-1318]
  • Within a category every card is even the same COLOUR (one category accent).
  • The only differentiation is a 13px icon + a ghost glyph at 2–3.5% alpha whose size,
    drift and bleed come from `m.name().hashCode()` — variety injected as *noise*, precisely
    because the design had nothing meaningful to vary. [:1342-1348]
  • A card never says what you set. Zoom at 8× is pixel-identical to Zoom at 2×. Hands with
    a +0.4 offset is identical to default. Every value in the client is HIDDEN behind a
    right-click. So the front page of the product carries zero information.
  • Every popover is the same 236px sheet of label-left/control-right rows pinned to a 24px
    lane. Zoom (a magnifier), Hands (a 3-axis rig) and No Bobbing (a flag) all get literally
    the same form.
  • Density is the giveaway: Combat = 1 module in a 4-column grid. The most expensive frame
    in the menu is also the emptiest one. Misc = 2. Visuals = 6. [MenuContent:104-124]
  • And the data model already has the cure and throws it away: every Module carries a
    written `desc` ("Hold the zoom key to magnify the view.") — grep proves it is rendered
    NOWHERE.

So: he is looking at a page that moves when he doesn't want it to, and doesn't say anything
when he does.

═══════════════════════════════════════════════════════════════════════
PART 2 — THREE DIRECTIONS (all inside the frozen language)
═══════════════════════════════════════════════════════════════════════

Common to all three — THE MOTION DOCTRINE (write it into docs/DESIGN.md):

  ANIMATE (state the player caused, in place, box never moves):
     toggle knob · card grey↔hue · slider fill + handle + its number (ValueTween)
     hover / press / focus ring · armed→"Confirm reset?" swap · the search reflow
     (Stage 21 — owner-frozen, and it IS a player-caused content change) · the window
     entrance (one motion, once).
  NEVER ANIMATE (layout consequences of navigation):
     category switch → CUT. rail indicator → lights in place, no Y travel (the bar
     cross-fades hue where it stands). panel appearance → there is no appearance, the
     panel is always there. sheet height → there is no sheet.
  MEASURABLE ACCEPTANCE: switching a category starts ZERO transitions. Frame N and N+1
  after the switch are byte-identical screenshots (the harness can assert this).

───────────────────────────────────────────────────────────────────────
DIRECTION A — "INSTRUMENTS & RHYTHM"  (evolution; keeps rail+grid+popover)
───────────────────────────────────────────────────────────────────────
Structure unchanged. Three changes: cards become instruments, footprints stop being
uniform, layout motion is deleted.

  COMBAT (1 module → a HERO card, 4 cols × 2 rows — the empty category becomes the rich one)
  ┌──────────┬────────────────────────────────────────────────────────┐
  │  Combat ▌│ ┌────────────────────────────────────────────────────┐ │
  │  Visuals │ │ ▣  ANIMATIONS                             Classic  │ │
  │  Player  │ │    Custom first-person attack animation.           │ │
  │  Misc    │ │    speed ▰▰▰▰▰▱▱  1.00×    amp ▰▰▰▰▱▱▱  1.00       │ │
  │          │ └────────────────────────────────────────────────────┘ │
  VISUALS (6 modules → 2 wide + 4 flags; two clean rows, no holes)
  │          │ ┌──────────────────────┐ ┌──────────────────────┐      │
  │          │ │ ▣ Zoom        4.0×   │ │ ▣ Screen Stretch     │      │
  │          │ │   2 ──●──────── 8    │ │   ▭ 16:9   ▮ bars    │      │
  │          │ └──────────────────────┘ └──────────────────────┘      │
  │          │ ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐        │
  │          │ │▣ Fullbr │ │▣ NoHurt │ │▣ NoFire │ │▣ NoBob  │        │
  │          │ │     [F] │ │     [·] │ │     [·] │ │     [·] │        │  ← its bind
  │          │ └─────────┘ └─────────┘ └─────────┘ └─────────┘        │
  └──────────┴────────────────────────────────────────────────────────┘

  The instrument vocabulary (6 forms, NOT 12 bespoke paintings — that's how you avoid
  both "templated" and "circus"), all drawable with rect/roundedRect/border/line/circle:
    1. NUMERAL + RULER   Zoom → "4.0×" and a 2..8 tick scale with the handle at the value
    2. APERTURE FRAME    Screen Stretch → an outline rect of the real aspect + the bar strips
    3. XY FIELD          Hands → a 24×18 field with a dot at (offsetX, offsetY), scale ring
    4. METER PAIR        Animations → two 2px tracks (speed / amplitude) + the type name
    5. KEYCAP            every flag module → the key it is bound to, or a faint "·" when unbound
    6. MINIATURE         HUD Editor → a true 44×26 miniature of the player's own HUD layout,
                         drawn from hud.armorX/Y, potionX/Y, targetX/Y, infoX/Y, sprintX/Y
    (+ a stateWarn dot when MenuContent.notice(m) != null — the "ON but idle" honesty that
     is currently buried inside the popover.)
  Every readout is LIVE config data. Nothing decorative.

  Footprints: flags 1×1, parametric modules 2×1, the lone Combat module 4×2. Requires span
  in Grid (or a screen-private SpanGrid). Density stops being a bug and becomes the rhythm.

  Motion REMOVED: the category cascade (TileMotion.categoryEnter → cards cut in), the rail
  pill/bar Y-travel. Motion KEPT: state colour, hover, search reflow, entrance.
  Motion still WRONG: the popover still grows in and still re-heights. That's why A is not
  the answer on its own.

  Why it stops feeling monotonous: because the page now tells you your own settings without
  a single click, and because three card sizes give the grid a rhythm instead of a checkerboard.

───────────────────────────────────────────────────────────────────────
DIRECTION B — "THE BENCH"  (A + the popover becomes permanent; MY PICK)
───────────────────────────────────────────────────────────────────────
The well splits into a card shelf (top) and a BENCH (bottom) that is ALWAYS there and
ALWAYS occupied by exactly one module. Nothing grows in, because nothing arrives.

  ┌────────────────────────────────────────────────────────────────┐
  │ ♣ CLUB                                     [🔍 Search      /]   │
  ├──────────┬─────────────────────────────────────────────────────┤
  │  Combat  │ ┌────────────────────┐ ┌─────────┐ ┌─────────┐      │
  │▌ Visuals │ │ ▣ Zoom      4.0×   │ │▣ Fullbr │ │▣ NoHurt │      │  shelf:
  │  Player  │ │   2 ──●───── 8     │ │     [F] │ │     [·] │      │  READS ONLY
  │  Misc    │ └────────────────────┘ └─────────┘ └─────────┘      │  (no controls)
  │          │ ┌────────────────────┐ ┌─────────┐ ┌─────────┐      │
  │          │ │ ▣ Screen Stretch   │ │▣ NoFire │ │▣ NoBob  │      │
  │          │ │   ▭ 16:9   ▮ bars  │ │     [·] │ │     [·] │      │
  │          │ └────────────────────┘ └─────────┘ └─────────┘      │
  │          │                                                     │
  │          │  ZOOM                                        ( ●══) │  ← master toggle
  │          │  Hold the zoom key to magnify the view.             │  ← the desc, at last
  │          │  Strength  2 ─────●──── 8   4.0×                    │  THE BENCH
  │          │  Smooth    0 ──●─────── 1   0.50                    │  fixed box,
  │          │  Hold key [ C ]  Also: Sneak         [  Reset  ]    │  never moves
  ├──────────┴─────────────────────────────────────────────────────┤
  │ ♣ Default ▾                                        Club 0.1.1  │
  └────────────────────────────────────────────────────────────────┘

  ARITHMETIC (it fits, exactly): well = 448×288. Shelf = 2 rows × 58 + 8 gap = 124.
  Bench = 132. 12 + 124 + 8 + 132 + 12 = 288. ✔
  The bench is 424 wide — nearly DOUBLE the 236px popover — so it lays controls out in TWO
  columns, and the tallest module (Hands: Right/Left segment + 4 sliders + bind + reset)
  fits with no scrollbar: 28 + 4 + (2 rows × 24) + 4 + 24 + 24 = 132. ✔
  And both "dropdowns" (6 animation types, 6 stretch presets) become an always-visible
  inline chooser column — so `openDrop`, the pick-list, the row-hiding and the height tween
  all DIE. There is no state in which the bench changes size.

  What disappears with it: Reveal, popHTween, positionPopover(), POP_H_MIN, the Stage-58
  "no room below the cards → cover them" escape hatch, the popover mini-halo, the popover
  scrollbar. A whole class of geometry hacks stops existing rather than getting nicer.

  INTERACTION (keyboard-preserving):
    LMB on a card   = toggle it (UNCHANGED — muscle memory + the harness click test) AND the
                      bench becomes that module. Flip Zoom on and its Strength slider is
                      already under your hand. This is the sequence a player actually wants.
    RMB on a card   = make it the bench subject WITHOUT toggling (the inspect gesture; same
                      button as today, one less sheet).
    Arrows          = move the grid cursor; the bench follows (no toggle, no motion).
    Enter           = toggle.  Space = jump keyboard focus INTO the bench.  Esc = back to grid.
                      (Exactly the Stage-27/31 zone model — "popover" becomes "bench" and the
                      popFromGrid hand-back becomes a benchFromGrid hand-back.)
    Bind / conflict rows (Stage 62) move into the bench verbatim: "Hold key"/"Toggle key",
    "Press any key…", "Also: Sneak", "X holds this key", MenuContent.notice() caption.

  MOTION: on subject change the bench CONTENT cross-fades in a fixed box (fade out 100ms /
  in 120ms via Color.scaleAlpha — pushOpacity doesn't reach text). The box itself never
  moves, never resizes, at any GUI scale, for any module. Everything from Direction A's
  doctrine applies to the shelf. Total animations to switch category: 0. To open the menu: 1.

  Why it stops feeling monotonous: the shelf reads (12 different instruments), the bench
  edits (a different composition per module, at double the width). Two organs with two jobs,
  instead of twelve identical boxes each hiding an identical drawer.

───────────────────────────────────────────────────────────────────────
DIRECTION C — "THE PAGE"  (kill the category switch entirely)
───────────────────────────────────────────────────────────────────────
No tabs, so nothing can ride back and forth. One scrollable column of full-width module
ROWS under sticky section headers; the rail stops being a switcher and becomes a scroll-spy
index (clicking scrolls to the header; Ctrl+Tab jumps sections — the binding survives).
The row IS the instrument, and it carries its primary control INLINE.

  ┌────────────────────────────────────────────────────────────────┐
  │ ♣ CLUB                                     [🔍 Search      /]   │
  ├──────────┬─────────────────────────────────────────────────────┤
  │  Combat  │ ░ COMBAT ░░░░░░░░░░░░░░░░░░░░░░░░░  (sticky header) │
  │  Visuals │  ▣ Animations   Classic   speed ▰▰▰▰▱  amp ▰▰▰▱  (●)│
  │▌ Player  │ ░ VISUALS ░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░ │
  │  Misc    │  ▣ Zoom         2 ──●──── 8   4.0×               (●)│
  │          │  ▣ Screen Str.  ▭ 16:9   ▮ bars                  (●)│
  │          │  ▣ Fullbright   See in the dark.          [F]    (○)│
  │          │  ▣ No Hurt Cam  Removes the damage tilt.  [·]    (●)│
  │          │ ░ PLAYER ░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░ │
  │          │  ▣ Hands        ⊹ x+0.10 y−0.05  scale 1.00     (●) │
  └──────────┴─────────────────────────────────────────────────────┘

  Motion inventory: the player's own scroll. That's the entire list. No cascade, no pill,
  no sheet, no reveal. It is the most literal possible answer to "перестань ездить".
  Rows differ by height and by instrument (Hands is taller and carries its XY field; flags
  are one line). Everything the bench would edit is edited in the row.

  RISKS: (1) it is one long list — the exact silhouette of a web settings page, which
  DESIGN.md §1 explicitly forbids ("НЕ должен ощущаться как ... веб-сайт"). (2) It discards
  the category identity colours and the card canvas (Stage 11: chips, ghosts, palette A) —
  a lot of accepted work. (3) Rich rows + inline controls in a 448px column get cramped;
  it really wants the rail gone, and then Ctrl+Tab and the category accents are gone too.
  Offer it, but I would not build it.

═══════════════════════════════════════════════════════════════════════
PART 3 — WHAT I WOULD BUILD, AND WHAT MAKES IT ACCEPTABLE
═══════════════════════════════════════════════════════════════════════

BUILD **DIRECTION B — THE BENCH** (which contains all of A).

WHY:
 • It kills all four "ездят" motions at the source, including the one A can't: nothing grows
   in, because the panel never arrives — it was always there.
 • It's the only direction where "дёшево" is answered with INFORMATION rather than decoration:
   the shelf shows every value you own; the bench shows the description the data model has
   been carrying, unread, since Stage 6.
 • It DELETES code instead of adding chrome: Reveal, popHTween, positionPopover, the scale-4
   overlap hatch, openDrop's row-hiding, the popover halo and scrollbar all go. A redesign
   that removes a class of bugs is one the owner can be shown, not just told about.
 • It preserves everything already bought and approved: the 660×380 window, the Club canvas,
   the cards and their chips/icons/category accents, the search + its Stage-21 reflow, the
   keyboard zone model, the Stage-62 bind/conflict rows.
 • C is more radical and less Club: it would look like a settings website, which the design
   doctrine bans by name.

WHAT MUST BE TRUE FOR HIM TO ACCEPT IT (state these as the acceptance test, up front):
 1. STILLNESS IS PROVABLE. Switching a category starts zero transitions; two consecutive
    harness frames after a switch are identical. If anything still slides, it failed.
 2. NO NEW LANGUAGE. Zero new tokens. Flat fills, 1px hairlines, the ink ramp, the flat
    accent #7CABFF and the four category hues. No glow, no glass, no shadow under the bench
    (it separates by an EDGE + a tone step — the Stage-22 passe-partout grammar, not by a
    drop shadow), no gradient anywhere, and NO gradient on a single number.
 3. EVERY INSTRUMENT IS TRUE. Every readout is live ClubConfig data. A miniature that lies
    (a fake HUD preview, a decorative meter) is worse than no miniature and he will find it.
 4. SIX FORMS, NOT TWELVE. The readouts come from a closed vocabulary (numeral+ruler, aperture,
    XY field, meter pair, keycap, miniature). Twelve bespoke drawings is how this turns into
    a circus; one form for all twelve is how it stayed a list of boxes.
 5. NOTHING IS EMPTY AND NOTHING SCROLLS. Combat's single module fills its frame as a hero
    card. The bench holds Hands — the tallest module in the client — with no scrollbar, at
    every GUI scale, because the canvas is fixed (Stage 60/63).
 6. THE HONESTY WORK SURVIVES INTACT. Bind, "Also: Sneak", "X holds this key", "Idle — vanilla
    Sprint: Toggle is on" — all still visible, now WITHOUT a right-click, and the idle state
    gets a warn dot on the card itself.
 7. IT SURVIVES HIS PIXEL PASS. The bench sits on the same axes the header already uses (the
    module title on the tray axis is wrong — it sits on the WELL's left line; the master toggle
    lands on the well's right line, the same line the search field's right edge lands on).

WHAT MUST NOT CHANGE (hard constraints, carried through the whole design):
 • The Club canvas (Stage 60/63): everything drawn in canvas units through one matrix scale;
   every mouse entry point converts MC units via cx()/cy(). firstCardCentreMc() must keep
   proving the conversion end-to-end.
 • Keyboard navigation end to end (Stage 27/31): Tab zones, Ctrl+Tab category, arrows in the
   grid, Enter = activate, Space = into settings, the Esc chain (settings → clear query →
   blur search → leave grid), full text editing + clipboard in search, and gui-move suspended
   while ANY keyboard zone is active.
 • The bind/conflict rows (Stage 62) and MenuContent.notice() — verbatim, capture semantics
   included (menu key reserved, repeat guard, Esc cancel, Delete clear).
 • The fixed, centred 660×380 window. No drag, no resize, no growth.
 • Search: name-only matching, the "/" keycap, Enter = activate first result, and the
   owner-frozen Stage-21 reflow choreography.

MIGRATION ORDER (so it can be judged in pieces, not as one big-bang):
  1. Motion doctrine only: delete the category cascade + the rail Y-travel. Nothing else.
     Ship it, let him feel the stillness. This alone may be most of the complaint.
  2. SpanGrid + footprints (1×1 / 2×1 / 4×2 hero). No new pixels, just rhythm.
  3. CardReadout: the six instruments.
  4. The Bench replaces the popover (the big one; the keyboard-zone rewrite lives here).
  5. Harness: replace popoverGeometry() checks with benchGeometry() + a stillness assertion;
     re-point ClubPromo's popover shots at the bench.

## RISKS
- It reverses an explicit owner decision. docs/UI-V2-MENU.md §4 records 'Постоянной панели настроек нет — настройки в ПКМ-поповере (решение Stage 6)'. The Bench IS that pane. This must be argued to him openly ('you asked for no permanent pane; the cost of that decision is the sheet that has to grow in every time — here is the trade'), not slipped in.
- The complaint is ambiguous and I removed ALL FOUR moving things. If what he actually likes is the sliding rail indicator and what he hates is only the popover, freezing the rail will read as a downgrade. Cheap mitigation: ship step 1 of the migration (kill the cascade + the rail travel) as a standalone build and get a verdict before touching the popover.
- The Stage-21 search reflow was owner-tuned over six rounds and is explicitly frozen ('do not retune'). It is the one layout animation I am KEEPING, which is a doctrinal inconsistency I am choosing on purpose (it is a player-caused content change inside a container he is staring at). If he reads the doctrine literally he may demand it die too — and that would be a real loss.
- Twelve live readouts is the exact place this design can turn cheap: a fake meter, a decorative ruler, a miniature that doesn't match the real HUD. The owner reviews pixel by pixel and will catch a lying instrument instantly. Mitigation: a closed six-form vocabulary, every value read live from ClubConfig, and a harness check that the HUD miniature's element boxes match hud.*X/*Y.
- LMB currently toggles the card, and the harness asserts exactly that (firstCardEnabled() flips after a click at firstCardCentreMc()). Making the click ALSO select the bench subject means you cannot inspect a module without flipping it — I route inspection to RMB (which today opens the popover), but that is a learned gesture being redefined and needs to be watched in-game.
- Killing the popover kills popoverGeometry() and the Stage-58 checks built on it ('stays a usable sheet on a squeezed window'). Those checks must be REPLACED (bench geometry, bench-fits-Hands at every GUI scale), not deleted — the harness count changes and silently dropping checks is how a regression walks back in.
- Grid has no span support and assumes one uniform row height (rowHeight = max child). A 4×2 hero card breaks that assumption. Either extend Grid (public layout API, used elsewhere) or write a screen-private SpanGrid (precedent exists: ModuleTile/SearchField/OptionRow are deliberately screen-private). Extending the shared Grid is the riskier of the two.
- Bench content cross-fade must use Color.scaleAlpha per draw — pushOpacity does not reach text or glyphs (a known backend limit that already bit Stage 9). Getting this wrong produces a bench whose panel fades but whose text snaps.
- The keyboard-zone rewrite (popFromGrid → benchFromGrid) touches the most heavily-patched logic in the file — Stage 27, 31, 43, 58 all left scars there (repeat guards, reserved menu key, zone hand-back, gui-move suspension). This is where a regression is most likely and least visible.
- Density is a knife: a 2-column masonry with ragged bottoms reads as a blog. I use spans on a fixed 4-column grid with no holes per category (Visuals: 2+2 then 1+1+1+1; Player: 2+1+1; Misc: 2+1 leaves one hole) — Misc still has a gap, and one hole in one category may be the thing he points at.

## OPEN QUESTIONS
- Which of the four motions did he actually mean by 'вкладки туда-сюда ездят' — the rail pill, the grid cascade, the popover grow-in, or the Hands segment pill? The fix removes all four, but if he is fond of the sliding rail indicator we should know before we take it away.
- Is the Stage-6 decision 'no permanent settings pane, settings live in the RMB popover' reversible? The Bench is precisely that pane. Everything else in Direction B survives without it (Direction A), so this is the single load-bearing question.
- Should the master toggle stay on the card (LMB, as today) or move to the bench header? I keep it on the card and add RMB = inspect-without-toggling, but that redefines a gesture he has been using since Stage 6.
- May the module's own one-line description finally be rendered (in the bench header)? It exists in MenuContent for all 12 modules and has never been drawn. He banned 'серые филлер-подписи' — this is real content, not filler, but it is his call.
- Does the Stage-21 search reflow keep its animation under the new doctrine, or does 'nothing moves' mean nothing, including that? It is owner-frozen choreography and the only layout motion I would keep.
- The footer's profile chip ('Default ▾') is a stub for a profile switcher. Is that in scope now? It changes whether the bench should carry a per-profile identity (and whether Reset means 'to factory' or 'to profile').

## FACTS
- [verified] The category switch tears down and re-animates the entire grid: setCategory() closes the popover, clears the query, and calls rebuildGrid(CATEGORY), which does tileMotion.clear(); grid.clear(); and re-creates every card with TileMotion.categoryEnter(i++).
  src/main/java/com/club/ui/menu/ClubMenuScreen.java:309-320 and :337-360
- [verified] That category re-entry is a staggered cascade: 42ms per card, 280ms fade, cards rise 6px into place. So clicking a category costs N animations, where N = the number of modules in it.
  src/main/java/com/club/ui/menu/TileMotion.java:20-23 (ENTER_DUR=0.28f, CAT_STAGGER=0.042f, CAT_DRIFT=6f) and TileMotion.categoryEnter()
- [verified] The rail indicator is a literal travelling object: a surfaceHi pill AND a 4px category-coloured bar both slide vertically on an eased Transition to the new row, plus a colour lerp between category hues.
  src/main/java/com/club/ui/menu/ClubMenuScreen.java:963-965 (indicator.target(railYRel(catIndex))) and :1018-1019 (the bar drawn at indY)
- [verified] The popover grows in AND re-heights: a Reveal drives grow-in/out and a ValueTween (popHTween) eases its height on open and on every resize — and it resizes on every dropdown expand and every Hands tab switch, because expanding a dropdown HIDES all sibling rows and rebuilds the column.
  src/main/java/com/club/ui/menu/ClubMenuScreen.java:1032-1064 (Reveal + popHTween.set(popH)) and :650-668 (openDrop hides every other setting, then rebuildPopover())
- [verified] The Hands popover adds a fourth travelling element — the SegmentRow's pill slides between Right/Left on an eased segSlide Transition.
  src/main/java/com/club/ui/menu/ClubMenuScreen.java:1713-1750
- [verified] Every module card is geometrically identical: TILE_H 58, TILE_PAD 7, CHIP 22 (icon 13), STRIPE 14x3, NAME_BASE 12 — constants, not per-module. State is expressed ONLY as colour (grey to category hue). No card shows a value.
  src/main/java/com/club/ui/menu/ClubMenuScreen.java:1315-1318 and the ModuleTile.render() body at :1353-1452
- [verified] The only visual variation between cards is the ghost underlay, whose size/drift/bleed/alpha are derived from m.name().hashCode() at 2–3.5% alpha — i.e. variety is injected as deliberate noise, not as meaning.
  src/main/java/com/club/ui/menu/ClubMenuScreen.java:1342-1348
- [verified] There are exactly 12 modules and the categories are wildly uneven: Combat 1 (Animations), Visuals 6, Player 3, Misc 2 — so a 4-column grid renders Combat as a single small box in an otherwise empty 448x288 well.
  src/main/java/com/club/ui/menu/MenuContent.java:104-124
- [verified] Every module already carries a written one-line description in the data model (Module.desc), and the menu NEVER renders it. A repo-wide grep for '.desc()' returns zero hits.
  src/main/java/com/club/ui/menu/MenuContent.java:50 (record field) + `grep -rn "\.desc()" src/main/java/` → no matches
- [verified] The content well is 448x288 Club units at the full 660x380 window (railWW clamps to 172, 16px gap, 12px margins; bodyH = 380-48-36 = 296). That is enough for a two-column control layout — a permanent bench of ~132px under two rows of 58px cards fits exactly (124 + 8 + 132 + 24 pad = 288).
  src/main/java/com/club/ui/menu/ClubMenuScreen.java:789-856 (layoutAll: railWW=clamp(winW*0.26,108,172), wellX=railWX+railWW+16, wellW=winX+winW-12-wellX, wellH=bodyH-8)
- [verified] The popover's own geometry is already a source of hacks the design would not need if it were permanent: positionPopover() has an escape hatch that lets the sheet COVER the cards when there is no room below (GUI scale 4), and the harness asserts that hack.
  src/main/java/com/club/ui/menu/ClubMenuScreen.java:524-541 + src/main/java/com/club/harness/ClubHarness.java:580-588
- [verified] Both dropdowns are small enough to become always-visible inline choosers (killing the expand/collapse height animation entirely): AnimationType has 6 values (Vanilla, Classic, Thrust, Overhead, Short Slash, Spin) and StretchPreset has 6 (4:3, 16:9, 16:10, 21:9, 32:9, Auto).
  src/main/java/com/club/modules/animations/AnimationType.java:21-65 and src/main/java/com/club/modules/screenstretch/StretchPreset.java:5-10
- [verified] The renderer can draw everything an 'instrument' card needs with no new backend work: rect, roundedRect, border, line, circle, clip, opacity. (Gradient and glow exist but are forbidden by DESIGN.md outside the two sanctioned places.)
  src/main/java/com/club/ui/UiRenderer.java:4-24
- [verified] Grid is a naive equal-cell layout with no span support and a single uniform row height (rowHeight = max child height), so wide/hero cards require extending it or writing a screen-private span grid.
  src/main/java/com/club/ui/layout/Grid.java (cellW = (w - gap*(cols-1))/cols; layout() places child i at col i%cols, row i/cols)
- [verified] Text cannot be faded through the renderer's opacity stack — any cross-fade of bench content must multiply Color.scaleAlpha per draw. This is a live constraint the screen already works around everywhere (screenAlpha).
  src/main/java/com/club/ui/menu/ClubMenuScreen.java:85-87 comment + every text draw multiplying screenAlpha, e.g. :1441-1442
- [verified] The HUD Editor card can show a TRUE miniature of the player's HUD: every element's position and scale is plain config data (armorX/Y, potionX/Y, targetX/Y, infoX/Y, sprintX/Y + per-element scales).
  src/main/java/com/club/config/ClubConfig.java:92-123
- [verified] A permanent settings pane REVERSES an explicit owner decision from Stage 6, which is recorded as design law: 'Постоянной панели настроек нет — настройки в ПКМ-поповере (решение Stage 6)'. It has to be re-argued, not quietly re-introduced.
  docs/UI-V2-MENU.md §4 (Дизайн-язык Variant D)
- [verified] The harness has public seams that must survive any redesign or be consciously replaced: selectCategory(String), firstCardCentreMc(), firstCardEnabled(), popoverGeometry(). ClubPromo drives selectCategory("Visuals") and expects Zoom to be the first card.
  src/main/java/com/club/harness/ClubHarness.java:580-604, :627 and src/main/java/com/club/harness/ClubPromo.java:280,300