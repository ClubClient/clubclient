# itemscroll

## SUMMARY
Item Scrolling for Club: I read the 1.21.1 decompiled/bytecode API (SlotActionType, ScreenHandler.internalOnSlotClick, ClientPlayerInteractionManager.clickSlot, HandledScreen, ServerPlayNetworkHandler.onClickSlot), the reference mods' real feature set and config (masa's Item Scroller / Inventory Profiles), and the anti-cheat numbers that decide whether this gets players kicked (NoCheatPlus FastClick defaults: 4 clicks/tick shortterm, 15 clicks/sec normal — and Inventory Profiles' own documented "set intervalBetweenClicksMs to 67" workaround, which is exactly 15/s). Design: every gesture is composed from QUICK_MOVE / PICKUP / THROW clickSlot calls; a tick-paced token-bucket pacer with ATOMIC click groups (never split a 3-click "move one" across ticks, or a screen close drops the cursor stack on the ground); intent-based batches re-scanned from the live handler each tick so a server resync self-corrects instead of desyncing; a per-action (input × modifier-mask) binding model that steals-on-collision like ModuleBinds and NAMES the vanilla action it overrides like KeyConflicts; and a mixin surface of exactly ONE accessor/invoker interface plus Fabric's ScreenEvents/ScreenMouseEvents — zero @Inject, so Sodium/REI/EMI/IPN cannot conflict.

## PROPOSAL
## 0. Scope call, up front

Ship the **core scroll/click/drag grammar** and the **binding surface**. Explicitly DEFER (and say so in the UI, not in a changelog nobody reads): villager trade automation, the crafting recipe memory, and the creative inventory. Those three are (a) the highest anti-cheat surface, (b) the parts of Item Scroller that are visibly bolted on, and (c) not what "itemscroll" means to the players asking for it. A `MenuContent.notice()` line — the mechanism already exists — tells the truth on the card: *"Creative inventory: not handled."*

Also, one improvement over the reference that falls straight out of the ban-safety requirement: **Club never does the per-item fallback.** Item Scroller's ~1000-packet worst case (issue #70) is its `clickSlotsToMoveItemsFromSlot` fallback grinding items one at a time when QUICK_MOVE can't place a stack. Club stops and says *"Target inventory is full"*. A refusal is cheaper than a kick.

---

## 1. The reference feature set, exactly

Item Scroller's default grammar (CurseForge project description; config names corroborate each one):

| Gesture | What moves | IS config toggle |
|---|---|---|
| scroll | ONE item | `SCROLL_MOVE_ONE` |
| Shift + scroll | the entire STACK | `SCROLL_MOVE_ENTIRE_STACKS` |
| Ctrl + scroll | ALL MATCHING stacks | `SCROLL_MOVE_ALL_MATCHING` |
| Ctrl+Shift + scroll | EVERYTHING | `SCROLL_MOVE_EVERYTHING` |
| Alt + click | all matching (click alias of Ctrl+scroll) | — |
| Alt+Shift + click | everything (click alias) | — |
| Shift + LMB drag | move every stack dragged over | — |
| Shift + RMB drag | move all but the LAST item of each | — |
| Ctrl + drag (either button) | move ONE item from each | — |
| Shift + click an empty slot | move matching items *in* | `SHIFT_PLACE_ITEMS` |
| Shift + click outside | drop all matching | `SHIFT_DROP_ITEMS`, `DROP_MATCHING` |
| Drop-key + drag variants | drop instead of move | — |
| Shift+scroll on trade output | fast villager trading | `SCROLL_VILLAGER` |
| middle-click crafting output | store recipe (18-slot memory) | `CRAFTING_FEATURES` |

Direction: scroll UP over a slot moves items **out of** it, scroll DOWN pulls items **into** its inventory. `REVERSE_SCROLL_DIRECTION_SINGLE` / `_STACKS` invert per-class; `SLOT_POSITION_AWARE_SCROLL_DIRECTION` derives the direction from which half of the GUI the slot sits in.

Edge cases the reference hits and we must decide on: **creative** (masa special-cases it — the CreativeScreenHandler's non-inventory tabs are fake slots), **crafting result slot** (one QUICK_MOVE crafts *repeatedly* — a PICKUP-composed "move one" over it would craft twice and waste), **armour/offhand slots** (in PlayerInventory but must be excluded from bulk moves), **the cursor stack** (every PICKUP-composed gesture is wrong if the cursor is already holding something), **the survival 2×2 grid** (both "sides" are PlayerInventory, so "the other inventory" needs the hotbar↔main split), **shulker/chest GUIs** (the normal case; nothing special).

---

## 2. Protocol layer (all verified against 1.21.1 bytecode)

Everything goes through the ONE seam:

```java
mc.interactionManager.clickSlot(handler.syncId, slotId, button, SlotActionType, mc.player);
```

which applies the click to the local handler, diffs the slots, and sends exactly **one** `ClickSlotC2SPacket(syncId, revision, slot, button, actionType, cursorStack, modifiedStacks)`. N clicks = N packets. There is no batch packet.

**Composition table.** This is the whole engine:

| Club action | clickSlot calls | Cursor at group boundary |
|---|---|---|
| **Move stack** | `QUICK_MOVE(slot, 0)` × 1 | untouched (QUICK_MOVE ignores the cursor) |
| **Move all matching** | `QUICK_MOVE(s, 0)` for each slot `s` in the source region whose stack matches the hovered one | untouched |
| **Move everything** | `QUICK_MOVE(s, 0)` for each non-empty slot `s` in the source region | untouched |
| **Move one** | `PICKUP(src,0)` → `PICKUP(dst,1)` → `PICKUP(src,0)` — take all, place ONE, put the rest back | **must be empty before, is empty after** |
| **Move one**, fast path | if `src.getStack().getCount() == 1` → just `QUICK_MOVE(src,0)` | untouched |
| **Drop one** | `THROW(slot, 0)` | must be empty (vanilla ignores THROW otherwise) |
| **Drop stack** | `THROW(slot, 1)` | must be empty |
| **Drag-move** | `QUICK_MOVE(s,0)` per slot the drag crosses (dedup by slot id) | untouched |
| **Drop cursor** (helper) | `PICKUP(-999, 0)` whole / `PICKUP(-999, 1)` one | — |

Notes that matter:
- `PICKUP`/`QUICK_MOVE` **reject any button other than 0 or 1** (verified in `internalOnSlotClick`). Middle-click gestures therefore cannot be implemented as a middle *slot action* — they are just an input the mod listens to and translates into 0/1 clicks.
- `QUICK_MOVE` on a crafting-result slot loops `quickMove` until inputs run out — that is the *correct and cheap* way to bulk-craft, and the reason result slots get QUICK_MOVE-only treatment.
- `CLONE` is creative-only. `SWAP` (3-click hotbar exchange) and `PICKUP_ALL` (the double-click gather) are not needed by any of the seven actions; leave them unused. `QUICK_CRAFT` is vanilla's own drag-spread — we must *not* emit it, and when our drag modifier matches we cancel vanilla's mouseClicked so its quick-craft never starts.

**Region model** (mod-screen-proof, no index arithmetic): a slot is "player side" iff `slot.inventory instanceof PlayerInventory`. The other region is everything else. Special case for `PlayerScreenHandler` (survival inventory), where both sides are PlayerInventory: split by `slot.getIndex()` — hotbar 0–8, main 9–35 (the two regions), armour 36–39 and offhand 40 **excluded from bulk moves**, crafting grid 1–4 and result 0 handled as the container side. Skip any slot where `!slot.isEnabled()` (loom/merchant), and never target a slot where `!slot.canInsert(stack)`.

---

## 3. The ban problem, and the pacer

**The numbers.** Vanilla server: no limit at all (I read `onClickSlot` — it validates syncId, spectator, `canUse`, slot index, revision, then applies; there is no counter). Grim: punishes *desync*, not volume. **NoCheatPlus is the one that will hurt**, and its defaults are: `limit.shortterm = 4` (clicks in one tick) and `limit.normal = 15` (clicks in one second), with the default action **`cancel`** — so the player doesn't get a kick message, they get *items snapping back and ghost stacks*, which is worse because it looks like Club is broken. Independent confirmation of the safe number: Inventory Profiles' own README tells users to set `intervalBetweenClicksMs` to **67ms** — exactly 15 clicks/second — "then you can use this mod on NoCheatPlus".

**`ClickPacer`** — a token bucket pumped from `ScreenEvents.afterTick(screen)` (20 Hz, no mixin needed):

```java
enum Rate {
    SAFE (12, 3),   // 12 clicks/s, burst 3  — under NCP's 15/s and 4/tick, with headroom
    FAST (20, 4),   // 1 click/tick
    INSTANT(0, 0);  // no throttle — flush the batch inline (Item Scroller behaviour)
}
```
- Default **SAFE** on a multiplayer server; **INSTANT** auto-selected in singleplayer (`mc.isInSingleplayer()` — there is no anti-cheat behind an integrated server, and paced item moves in your own world are just insulting). The dropdown's caption says which one is live and why.
- `tokens += rate/20` per tick, capped at `burst`.
- **Atomic groups.** The queue holds *groups*, not clicks. A "move one" is a 3-click group and is **never split across a tick boundary** — because if the player closes the screen (or the server closes it) between click 2 and click 3, the cursor is holding a stack and **the server drops it on the ground**. A group larger than `burst` is emitted whole once `tokens >= burst`, letting the bucket go negative and repay over the following ticks. Invariant, asserted in the harness: *the cursor is empty at every tick boundary.*
- **Intent, not a baked click list.** A batch stores `{sourceRegion, targetRegion, predicate, amount}`. Each tick it **re-scans the live handler** and emits the next group. This is the anti-desync design: our own clicks are applied optimistically to the local handler, so we see them; if the server *rejects* one and resyncs (`ScreenHandlerSlotUpdateS2CPacket` / `InventoryS2CPacket`), the next re-scan sees the corrected world and we self-correct instead of blindly firing clicks at slots that no longer hold what we thought.
- **Abort conditions** (all silent-but-honest): screen closed or `syncId` changed; handler slot count changed; player null; cursor unexpectedly non-empty; no work left; hard ceiling (512 clicks or 10 s per batch); **any user click or Esc cancels immediately** — a player who realises they scrolled the wrong way must be able to stop the machine.
- **Never** a per-item fallback. If QUICK_MOVE can't place a stack, stop, with a caption.

**Cost, so nobody is surprised:** a double chest "move everything" is 54 QUICK_MOVE clicks = **4.5 s at SAFE**, 2.7 s at FAST, instant in singleplayer. A "move one" is 3 clicks = 0.25 s. This is a real trade and the UI must not hide it —

**Progress affordance.** While a batch is in flight, draw (via `ScreenEvents.afterRender`, using the existing `PixelIcons` DrawContext seam, so we stay off the Club canvas here): a **2px flat accent rule** along the bottom edge of the container's background box (`x..x+backgroundWidth`), width = fraction done, plus a small right-aligned tabular count (`HudText`). Flat, no glow, no glass — DESIGN.md holds. That bar is the honest answer to "why is this slow" and it is the thing that makes the throttle feel deliberate instead of laggy.

---

## 4. The config surface

**Data model** (new package `com.club.modules.itemscroll`):

```java
public enum ScrollAction { MOVE_ONE, MOVE_STACK, MOVE_ALL_MATCHING, MOVE_EVERYTHING,
                           DROP_ONE, DROP_STACK, DRAG_MOVE }

public enum GestureInput { SCROLL,        // either direction; the DIRECTION picks to/from
                           SCROLL_UP, SCROLL_DOWN,   // explicit one-way, for players who want it
                           LEFT_CLICK, RIGHT_CLICK, MIDDLE_CLICK }

public static final int SHIFT = 1, CTRL = 2, ALT = 4;   // mods bitmask 0..7

public record Gesture(GestureInput input, int mods) { }  // 6 × 8 = 48 addressable combos
```

`SCROLL` exists because the reference's grammar is fundamentally *directional*: one binding covers both wheel directions and the direction chooses "out of this slot" vs "into this inventory". A global `Invert scroll` toggle flips it (Item Scroller's `REVERSE_SCROLL_DIRECTION_*`). `SCROLL_UP`/`SCROLL_DOWN` remain bindable for one-way actions — and a `SCROLL` binding **conflicts with both** on the same mask.

**Persistence** — identical shape to the proven `moduleBinds`:
```java
public java.util.Map<String,String> itemScrollBinds = new java.util.HashMap<>();
// "MOVE_ALL_MATCHING" -> "SCROLL+CTRL",  "DRAG_MOVE" -> "LEFT_CLICK+SHIFT"
```
Parsing goes through a `BAD`-set exactly like `ModuleBinds.key()`: a hand-edited typo must never throw; it is dropped once and self-heals. (`ClubConfig.version` 9 → 10, `migrate()` seeds the defaults.)

**Defaults** (Item Scroller's muscle memory carries over 1:1):

| Action | Default |
|---|---|
| Move one | `SCROLL` (no mods) |
| Move stack | `SCROLL` + Shift |
| Move all matching | `SCROLL` + Ctrl |
| Move everything | `SCROLL` + Shift+Ctrl |
| Drag move | `LEFT_CLICK` + Shift |
| Drop one | **unbound** |
| Drop stack | **unbound** |

Drops ship unbound on purpose, and the row says why: dropping is destructive, and a mis-aimed default that scatters a stack on the floor of a server is exactly the kind of thing the owner would (rightly) reject.

**Ambiguity is structurally impossible**, in three layers — mirroring the keybind work exactly:

1. **Steal-on-collision.** `ItemScrollBinds.set(action, gesture)` removes that gesture from every other action first (literally the `binds.entrySet().removeIf(...)` idiom from `ModuleBinds.set`). Two Club actions can never sit on one combo. The row that lost it goes to "Not set", and the assigning row says **"Taken from Move Stack"** for one beat.
2. **Reserved combos, refused.** `LEFT_CLICK`/no-mods and `RIGHT_CLICK`/no-mods are how you pick items up — binding over them bricks the inventory. Refuse them, with the same voice as the existing `bindReserved` state: *"That's how you pick items up"*. (Precedent: the popover already refuses the key that opens the menu.)
3. **Vanilla overrides, NAMED.** `GestureConflicts.vanilla(input, mods)` — the direct analogue of `KeyConflicts.other()` — returns what vanilla already does with that combo: Shift+LMB → *"Quick move"*, Shift+RMB → *"Quick move"*, LMB-drag → *"Spread stack"*, RMB-drag → *"Spread one each"*, MMB (creative) → *"Clone stack"*, Ctrl+Q → *"Drop stack"*. These are **allowed** (a player may genuinely want to replace quick-move with move-all-matching) but the row carries the amber caption **"Overrides: Quick move"** in `Tokens.palette().stateWarn()`, exactly like `bindWarning()`. Club names what it takes.

**Where it lives in the menu.** The settings popover is a **fixed 236px sheet below the card grid**, and its own code comments say captions wider than that get clipped mid-word. Seven action rows with combo fields and conflict captions *will not fit*, and cramming them in would be the cheap-looking thing the owner rejects. So:

*Card:* **Item Scrolling** (`IconGlyph.ITEM_SCROLL` = 0xE01C, new SVG → `./gradlew genIconAtlas`), in **Misc**. (A 5th "Inventory" category is defensible but churns the rail and its icon; Misc is the smaller change — flag for the owner.)

*Popover* (fits comfortably):
```java
new ToggleSetting  ("Drag move",  ...)
new ToggleSetting  ("Invert scroll", ...)
new DropdownSetting("Click rate", new String[]{"Safe", "Fast", "Instant"}, ...)
new ActionSetting  ("Edit Gestures…", () -> openGestureBinds.run())   // ← opens the screen
// + the free "Toggle key" row (ModuleBinds) and "Reset to Default" the popover already builds
```
plus a `MenuContent.notice("Item Scrolling")` returning, when true:
- *"Idle — Item Scroller is installed"* (or Mouse Tweaks / IPN / Mouse Wheelie) — see §5;
- *"Instant — no server to protect"* in singleplayer;
- *"Safe pace — 12 clicks/s (anti-cheat)"* on a server, so the 4.5-second double chest is a stated promise, not a mystery.

*Screen:* **`ItemScrollBindsScreen`** — a real Club-canvas screen, using the HUD Editor's precedent (`ActionSetting` → `Screen`). Seven rows: action name (left) · combo field (right, a `Button.Variant.GHOST` armed field reading `"Ctrl + Scroll"` / `"Not set"`), with the amber conflict caption under any row that overrides vanilla. Click a field → `"Perform a gesture…"`; the **next** scroll/click with its modifiers held is captured (Esc cancels, Delete clears) — the identical idiom to the bind capture at `ClubMenuScreen.java:699-723`.

> **Capture gotcha to solve at build time:** arming the field *is itself* a left click. The arming press must be consumed and the capture must begin on the *next* press — otherwise every field instantly binds itself to `LEFT_CLICK`+whatever was held. Latch on the arming click's **release**.

---

## 5. Mixin surface — one accessor, zero injections

This is the strongest part of the design and it should stay that way.

**The entire mixin footprint is one interface with no method bodies:**

```java
@Mixin(HandledScreen.class)
public interface HandledScreenAccessor {
    @Accessor("focusedSlot")      Slot club$focusedSlot();
    @Accessor("x")                int  club$x();
    @Accessor("y")                int  club$y();
    @Accessor("backgroundWidth")  int  club$bgW();
    @Accessor("backgroundHeight") int  club$bgH();
    @Invoker("getSlotAt")         Slot club$slotAt(double x, double y);   // it's PRIVATE in 1.21.1
    @Invoker("onMouseClick")      void club$onMouseClick(Slot s, int id, int button, SlotActionType t);
}
```
→ add `"HandledScreenAccessor"` to the `client` list in `club.mixins.json`.

`@Accessor`/`@Invoker` **generate** methods; they never rewrite a body, never claim an injection point, and cannot lose a `"required": true` fight. Sodium (world render — never touches HandledScreen), REI/EMI (overlay widgets), and IPN can all mixin the same class and nothing collides. This is a deliberate contrast with `MixinHeldItemRenderer`, which `docs/ARCHITECTURE.md §8` already admits is a minefield.

Routing clicks through the screen's own **`onMouseClick` invoker** rather than calling `interactionManager.clickSlot` directly is the second compat win: `CreativeInventoryScreen` overrides it, and any modded screen that overrides it keeps its own semantics for free. (We still hard-disable the creative screen in v1 — its non-inventory tabs are fake slots.)

**Everything else is Fabric API events — no mixin at all:**

```java
ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
    if (!(screen instanceof HandledScreen<?>)) return;
    ScreenMouseEvents.allowMouseScroll(screen) .register(ItemScrollHooks::onScroll);   // false = we consumed it
    ScreenMouseEvents.allowMouseClick(screen)  .register(ItemScrollHooks::onClick);
    ScreenMouseEvents.allowMouseRelease(screen).register(ItemScrollHooks::onRelease);  // ends a drag
    ScreenEvents.afterTick(screen)   .register(ItemScrollHooks::pump);      // ← the 20 Hz pacer
    ScreenEvents.afterRender(screen) .register(ItemScrollHooks::progress);  // ← the progress rule
    ScreenEvents.remove(screen)      .register(ItemScrollHooks::abort);
});
```
`allow*` fires **before** the screen's own handling, so returning `false` cleanly consumes the gesture (and returning `true` leaves vanilla/REI/EMI completely untouched). Note there is **no drag event** in the Fabric API: a drag is implemented as `allowMouseClick` (arm) → sample the hovered slot each `afterRender` via `club$slotAt` → `allowMouseRelease` (commit). That's *better* than a `mouseDragged` mixin anyway — no injection, and it can't start vanilla's QUICK_CRAFT.

**The three compat rules that keep the screen-mod minefield quiet:**

1. **Only act when a slot is hovered AND the cursor is inside `[x, x+backgroundWidth] × [y, y+backgroundHeight]`.** REI's and EMI's overlays and search fields live *outside* that box; JEI/EMI ingredient panes too. If we're outside, return `true` and never look again. This single rule is what stops us stealing REI's scroll.
2. **Sibling-mod detection → stand down, loudly.** If `FabricLoader.getInstance().isModLoaded("itemscroller" | "mousetweaks" | "inventoryprofilesnext" | "mousewheelie")`, Club's module defaults **off** and `MenuContent.notice()` says *"Idle — Item Scroller is installed"*. Two mods both moving items on Shift+scroll is a double-move and a desync, and it will read as Club's bug. (`notice()` exists for precisely this class of lie.)
3. **Creative:** `screen instanceof CreativeInventoryScreen` → return `true` unconditionally.

---

## 6. Verification

- **JUnit** (already wired): `ClickPacerTest` (token bucket never exceeds burst; an oversized atomic group is never split; the bucket repays), `GestureParseTest` (round-trip + a hand-edited junk value self-heals, never throws — the `ModuleBinds.BAD` contract), `GestureConflictTest` (steal-on-collision leaves exactly one owner; `SCROLL` collides with `SCROLL_UP` and `SCROLL_DOWN`; reserved combos are refused).
- **`ClubHarness`** (64 checks today) — the real proof: open a chest in the dev world, fire *move everything*, and assert (a) **no tick ever emits more than `burst` clicks**, (b) **the cursor is empty at every tick boundary**, (c) the batch completes and the chest is empty, (d) closing the screen mid-batch aborts and drops nothing. (a) and (b) are the two checks that stand between a Club user and a kick.
- **`-PclubCompat`** already launches Sodium + Iris + Freecam; add REI or EMI to that runtime set and confirm scrolling their overlay still scrolls their overlay.

## RISKS
- THE PACE IS THE PRODUCT RISK. A 54-slot chest takes 4.5 s at the SAFE default while Item Scroller does it instantly. Players will read that as Club being slow/broken unless the progress rule and the 'Safe pace — 12 clicks/s (anti-cheat)' notice are shipped in the SAME commit as the feature. If the owner rejects the visible progress bar, the honest move is to raise the default to FAST and accept NCP cancels — not to ship a silent 4.5-second stall.
- NCP's `cancel` action is silent to the player. When a server DOES throttle us, items snap back and ghost stacks appear, and every player will blame Club. Mitigation: detect it — if a batch's re-scan shows that a click we sent had no effect on the local handler N times in a row, abort the batch and drop to a lower rate for that session, with an honest caption. This is a real feature, not a nicety, and it is not in the reference mods.
- Cursor-stack loss. A 'move one' is 3 PICKUP clicks; if the screen closes between clicks 2 and 3 the server drops the held stack on the ground. The atomic-group invariant is the whole defence, and it must be asserted in the harness, not just intended. Any future gesture added to the queue MUST declare its group boundaries.
- Double-move with sibling mods. Item Scroller / Mouse Tweaks / IPN / Mouse Wheelie all bind Shift+scroll. Both mods firing = a desync that looks like Club's bug. The isModLoaded stand-down covers the four I know; a fifth mod nobody has heard of will still collide, and there is no general detection for 'someone else also moved that item'.
- Creative inventory is genuinely hard (CreativeInventoryScreen overrides onMouseClick and its non-inventory tabs are fake slots backed by a client-side handler). Shipping it half-working is worse than not shipping it. Deferring is the right call but it IS a visible gap versus the reference.
- Modded container screens (Applied Energistics terminals, Sophisticated Backpacks, Storage Drawers) often have virtual/paged slots where `slot.id` is not a real handler index or where clickSlot means something custom. Routing through the screen's own `onMouseClick` invoker covers most of them; a slot-position/GUI blacklist (the way Item Scroller ships `Ctrl+Alt+Shift+I` to blacklist a GUI) is the escape hatch and is NOT in this design's v1 scope.
- The gesture-capture UI has a real trap: the click that ARMS the field is itself a left click. Latch on the arming click's release, or every field self-binds to LEFT_CLICK instantly. This is the exact class of bug the Stage 43 key capture already hit (the GLFW repeat landing on the close route).
- The bindings screen is a NEW full screen in the Club canvas — the only precedent is HudEditorScreen. It is more UI work than it looks, and the owner reviews pixel by pixel. Budget for it as its own stage, not as a rider on the module.
- `SCROLL` vs `SCROLL_UP`/`SCROLL_DOWN` as separate inputs doubles the conflict rules and is a place where a subtle ambiguity can hide (a SCROLL binding must collide with BOTH directional ones). If the owner wants the simplest possible model, drop SCROLL_UP/SCROLL_DOWN entirely and keep only SCROLL + an Invert toggle — that is what the reference actually does.

## OPEN QUESTIONS
- CATEGORY: Misc, or a new 5th 'Inventory' category on the rail? Misc is the smaller change (no new rail icon, no rail churn); a dedicated category is more honest if item scrolling grows (villager, crafting, sorting). Owner's call — it changes the rail, which he has reviewed pixel by pixel.
- DEFAULT RATE on a server: SAFE (12/s, 4.5 s for a double chest, passes NCP untouched) or FAST (20/s, 2.7 s, trips NCP's `limit.normal` of 15 and gets clicks CANCELLED)? I recommend SAFE + the visible progress rule. If the owner finds 4.5 s unacceptable, the honest alternative is FAST plus the adaptive back-off (detect ignored clicks → drop to SAFE for the session), not a silent gamble.
- Does the owner accept the DEFERRALS — villager trade automation, crafting recipe memory, creative inventory — for v1, stated openly on the card via notice()? These are ~40% of Item Scroller's surface area and the parts most likely to be asked for by name.
- SCROLL only (+ Invert), or also explicit SCROLL_UP / SCROLL_DOWN as bindable inputs? The owner's brief listed 'scroll up, scroll down' as separate inputs, but the reference's actual grammar is direction-as-argument. Supporting both is more powerful and strictly more conflict rules to get right.
- Drop One / Drop Stack: ship UNBOUND by default (my recommendation — dropping is destructive and a mis-scroll scatters a stack on a server floor), or give them defaults (e.g. Alt+scroll)?
- Progress affordance: a 2px flat accent rule under the container box + a tabular remaining-count. That is a Club-designed element drawn INSIDE a vanilla screen via the PixelIcons DrawContext seam. Does the owner want Club's visual language appearing over vanilla GUIs at all, or should the progress live somewhere else (the hotbar-adjacent HUD)?
- Should the module also expose Item Scroller's per-GUI blacklist escape hatch (a hotkey that blacklists the currently open screen) for modded containers where clickSlot semantics are custom? Not in this design's v1 scope, but it is the only general defence against exotic modded screens.

## FACTS
- [verified] SlotActionType in 1.21.1 has exactly 7 constants: PICKUP, QUICK_MOVE, SWAP, CLONE, THROW, QUICK_CRAFT, PICKUP_ALL.
  javap -p net/minecraft/screen/slot/SlotActionType.class from ~/.gradle/caches/fabric-loom/minecraftMaven/.../minecraft-merged-1.21.1-net.fabricmc.yarn.1_21_1.1.21.1+build.3-v2.jar
- [verified] The one and only client entry point is `public void ClientPlayerInteractionManager.clickSlot(int syncId, int slotId, int button, SlotActionType actionType, PlayerEntity player)`. It (1) bails with a WARN "Ignoring click in mismatching container" if syncId != handler.syncId, (2) snapshots every slot stack, (3) applies the click LOCALLY via handler.onSlotClick, (4) diffs the slots, (5) sends ONE ClickSlotC2SPacket(syncId, handler.getRevision(), slotId, button, actionType, cursorStack.copy(), modifiedStacksDiff). One clickSlot call == one packet. There is no batching primitive.
  javap -c -p ClientPlayerInteractionManager.class, clickSlot bytecode offsets 0..233
- [verified] THROW semantics (ScreenHandler.internalOnSlotClick): requires getCursorStack().isEmpty() AND slotIndex >= 0; then `int n = (button == 0) ? 1 : slot.getStack().getCount(); player.dropItem(slot.takeStackRange(n, Integer.MAX_VALUE, player), true)`. So THROW/button0 = drop ONE, THROW/button1 = drop the STACK, and both are no-ops while the cursor holds anything.
  javap -c -p ScreenHandler.class, internalOnSlotClick bytecode 1453–1527
- [verified] QUICK_MOVE semantics: button must be 0 or 1 (any other button returns immediately); slotIndex must be >= 0; slot.canTakeItems(player) must be true; then `ItemStack s = quickMove(player, i); while (!s.isEmpty() && areItemsEqual(slot.getStack(), s)) s = quickMove(player, i);` — i.e. ONE QUICK_MOVE click moves the whole stack (and on a crafting-result slot crafts repeatedly until the inputs run out). It does NOT require an empty cursor.
  javap -c -p ScreenHandler.class, internalOnSlotClick bytecode 556–701
- [verified] PICKUP with slotId == -999 is the "drop outside the window" click: with a non-empty cursor, button 0 drops the whole cursor stack, button 1 splits one item off and drops it.
  javap -c -p ScreenHandler.class, internalOnSlotClick bytecode 594–655 (sipush -999 → dropItem)
- [verified] HandledScreen (1.21.1) does NOT declare mouseScrolled at all — it inherits ParentElement's default. So there is no `HandledScreen.mouseScrolled` method to @Inject into; scroll must be caught at the Screen/Mouse level or via Fabric's screen events.
  javap -p net/minecraft/client/gui/screen/ingame/HandledScreen.class — full member list contains mouseClicked/mouseDragged/mouseReleased/keyPressed but no mouseScrolled; javap -p Screen.class also has none (only Element declares `default boolean mouseScrolled(double,double,double,double)`)
- [verified] HandledScreen's useful members: `protected Slot focusedSlot`, `protected int x, y, backgroundWidth, backgroundHeight`, `private Slot getSlotAt(double,double)`, `protected void onMouseClick(Slot, int, int, SlotActionType)`. getSlotAt is PRIVATE → needs an @Invoker; focusedSlot/x/y are protected → @Accessor.
  javap -p net/minecraft/client/gui/screen/ingame/HandledScreen.class
- [verified] CreativeInventoryScreen OVERRIDES `protected void onMouseClick(Slot, int, int, SlotActionType)` and keeps its own `private List<Slot> slots` + `deleteItemSlot` + `isCreativeInventorySlot(Slot)`. Its non-inventory tabs are fake slots, so calling interactionManager.clickSlot on them directly is wrong.
  javap -p net/minecraft/client/gui/screen/ingame/CreativeInventoryScreen.class
- [verified] Vanilla's server has NO click-rate limit. ServerPlayNetworkHandler.onClickSlot only: forceMainThread → updateLastActionTime → syncId match → spectator resync → canUse (debug "Player {} interacted with invalid menu {}") → ScreenHandler.isValid(slot) (debug "Player {} clicked invalid slot index: {}, available slots: {}") → revision compare → disableSyncing → onSlotClick → verify modifiedStacks → resync on mismatch. No counter, no throttle, no kick. (The only per-tick counters in that class are movePacketsCount/lastTickMovePacketsCount — movement only.)
  javap -c -p ServerPlayNetworkHandler.class, onClickSlot region; javap -p shows only movePacketsCount/lastTickMovePacketsCount fields
- [verified] NoCheatPlus FastClick defaults, verbatim from DefaultConfig.java: INVENTORY_FASTCLICK_LIMIT_SHORTTERM = 4, INVENTORY_FASTCLICK_LIMIT_NORMAL = 15, SPARECREATIVE = true, TWEAKS1_5 = true, ACTIONS = "cancel vl>50 log:fastclick:3:5:cif cancel". The docs define shortterm as "how many clicks in one tick (50ms)" and normal as "how many clicks in one second". The default action is CANCEL (not kick) — which manifests to the player as ghost items / items snapping back.
  https://raw.githubusercontent.com/NoCheatPlus/NoCheatPlus/master/NCPCore/src/main/java/fr/neatmonster/nocheatplus/config/DefaultConfig.java + https://github.com/NoCheatPlus/Docs/wiki/%5BInventory%5D-Fastclick
- [verified] FastClick counts via an ActionFrequency bucket: `data.fastClickFreq.add(now, amount)`; `float shortTerm = data.fastClickFreq.bucketScore(0)` vs fastClickShortTermLimit and `float normal = data.fastClickFreq.score(1f)` vs fastClickNormalLimit; `violation = Math.max(shortTerm, normal)` accumulates into fastClickVL; the configured action decides cancel.
  https://raw.githubusercontent.com/NoCheatPlus/NoCheatPlus/master/NCPCore/src/main/java/fr/neatmonster/nocheatplus/checks/inventory/FastClick.java
- [verified] Independent corroboration of the safe rate: the Inventory Profiles mod's own README says — verbatim — "Configable click interval for sorting (set intervalBetweenClicksMs to 67ms then you can use this mod on NoCheatPlus ot inventory.fastclick disabled server!)". 67ms == ~15 clicks/second == NCP's `limit normal` of 15.
  https://raw.githubusercontent.com/jsnimda/Inventory-Profiles/fabric_1.16/README.md
- [verified] Item Scroller has NO rate limiting whatsoever — no delay, no tick spreading, no click budget. It fires the whole batch inline. Its bulk move is `tryMoveStacks(slot, gui, matchingOnly, toOtherInventory, firstOnly)` which shift-clicks each slot, with a fallback to per-item `clickSlotsToMoveItemsFromSlot` when shift-click fails.
  https://raw.githubusercontent.com/maruohon/itemscroller/master/src/main/java/fi/dy/masa/itemscroller/util/InventoryUtils.java
- [verified] The consequence of that, reported against Item Scroller itself: "Worst case scenario for moving a stack items requires sending about 1000 slot click packets" (Alt+Left-click move-all when the destination has no empty slot). The issue is open with no fix. Inventory Tweaks users report outright disconnection ("Sending too many packages") from Spigot servers on a middle-click sort.
  https://github.com/maruohon/itemscroller/issues/70 ; https://github.com/Inventory-Tweaks/inventory-tweaks/issues/417
- [verified] Item Scroller's real config toggle names (so we can map feature-for-feature): SCROLL_MOVE_ONE, SCROLL_MOVE_ENTIRE_STACKS, SCROLL_MOVE_ALL_MATCHING, SCROLL_MOVE_EVERYTHING, SCROLL_STACKS_FALLBACK, SCROLL_VILLAGER, SHIFT_DROP_ITEMS, SHIFT_PLACE_ITEMS, DROP_MATCHING, RIGHT_CLICK_CRAFT_STACK, CRAFTING_FEATURES, MOD_FEATURES_ENABLED, VILLAGER_TRADE_LIST; plus REVERSE_SCROLL_DIRECTION_SINGLE, REVERSE_SCROLL_DIRECTION_STACKS, SLOT_POSITION_AWARE_SCROLL_DIRECTION, CLIENT_CRAFTING_FIX, CARPET_CTRL_Q_CRAFTING.
  https://raw.githubusercontent.com/maruohon/itemscroller/master/src/main/java/fi/dy/masa/itemscroller/config/Configs.java
- [likely] Item Scroller's default gesture map (from the official CurseForge description): plain scroll = move ONE item; Shift+scroll = move the entire STACK; Ctrl+scroll = move ALL MATCHING stacks; Ctrl+Shift+scroll = move EVERYTHING. Alt+click = all matching; Alt+Shift+click = everything. Shift+LMB-drag = move every stack dragged over; Shift+RMB-drag = move all but the last item of each; Ctrl+drag (either button) = move ONE item from each stack dragged over. Drop-key+drag variants drop instead of move. Shift+click on an empty slot moves matching items in; Shift+click outside drops all matching.
  https://www.curseforge.com/minecraft/mc-mods/item-scroller (official project description)
- [likely] Item Scroller's special screens: villager trading (hover the trade OUTPUT slot; Shift+scroll-down fills the trade inputs, Shift+scroll-up moves the output to the player inventory — repeat-scroll = fast trading) and a crafting grid with an 18-slot recipe memory (middle-click the output to store a recipe; hotkeys to craft-everything / throw-output / move-output). These are separate subsystems, not part of the core scroll grammar.
  https://www.curseforge.com/minecraft/mc-mods/item-scroller ; corroborated by the CRAFTING_* / VILLAGER_* config names in Configs.java
- [verified] Fabric API 0.116.12+1.21.1 (the version Club pins in gradle.properties) ships fabric-screen-api-v1 2.0.25 with `ScreenEvents.BEFORE_INIT/AFTER_INIT` + per-screen `remove/beforeRender/afterRender/beforeTick/afterTick`, and `ScreenMouseEvents.allowMouseScroll(Screen) -> boolean allowMouseScroll(Screen, double x, double y, double horiz, double vert)`, `allowMouseClick(Screen, double, double, int)`, `allowMouseRelease(...)`. The module ships its own MouseMixin/ScreenMixin/HandledScreenMixin, i.e. Fabric already owns those seams and every mod that uses the events composes cleanly.
  javap -p of net/fabricmc/fabric/api/client/screen/v1/{ScreenEvents,ScreenMouseEvents}*.class + `jar tf` of fabric-screen-api-v1-2.0.25+8b68f1c719.jar (gradle cache); gradle.properties fabric_api_version=0.116.12+1.21.1
- [verified] Club's existing scroll hook cannot fight an inventory-scroll feature: MixinMouse @Inject(method="onMouseScroll", at=HEAD, cancellable) only cancels when ZoomModule.onScroll returns true, and ZoomModule.active() already requires `MinecraftClient.getInstance().currentScreen == null`.
  src/main/java/com/club/mixin/MixinMouse.java:19-22 and src/main/java/com/club/modules/zoom/ZoomModule.java:35-38
- [verified] Club's conflict idiom already exists and is exactly the shape the owner asked for: `ModuleBinds.set()` STEALS a key from any other Club action (`binds.entrySet().removeIf(...)` + `HoldKeys.releaseKey`), `KeyConflicts.other(translationKey)` names the non-Club action already on the key, and `ClubMenuScreen.bindWarning()` renders it as a short amber caption ("Also: Sneak", "Zoom holds this key"). The bind row itself is an armed GHOST Button ("Press any key…" / "Esc to cancel · Delete to remove").
  src/main/java/com/club/modules/binds/ModuleBinds.java:143-153, KeyConflicts.java:38-50, ClubMenuScreen.java:599-608 and 687-732
- [verified] The settings popover is a FIXED 236px sheet (POP_W = 236f, POP_W_MIN = 140f) that lives BELOW the card grid, and its own comments say a caption wider than that is clipped mid-word. A 7-action × (input + 3 modifiers) binding matrix cannot live in it — it needs a dedicated screen, for which HUD Editor (an ActionSetting that opens HudEditorScreen) is the established precedent.
  src/main/java/com/club/ui/menu/ClubMenuScreen.java:1322 and :718-719; MenuContent.hudEditor() at MenuContent.java:208-212
- [verified] MenuContent's descriptor vocabulary is a sealed interface Setting permitting exactly SliderSetting(label,min,max,step,get,set), ToggleSetting(label,get,set), CheckSetting(label,get,set), DropdownSetting(label,String[] options,get,set), ActionSetting(label,Runnable) — plus Tab(label,settings) and Module(name,desc,IconGlyph,enabledGet,enabledSet,reset,settings,tabs). MenuContent.notice(moduleName) already exists to say, in amber, why an enabled module is doing nothing.
  src/main/java/com/club/ui/menu/MenuContent.java:34-44, 50-63, 87-99
- [verified] Next free icon codepoint is 0xE01C (module icons run E010..E01B; the atlas is generated from tools/icons/src/<HEX>_<name>.svg via `./gradlew genIconAtlas`, and IconAtlasTest keeps IconGlyph in sync).
  src/main/java/com/club/ui/IconGlyph.java:29-40 and `ls tools/icons/src/` (E01B_freelook.svg is the last module icon); docs/UI-V2-MENU.md §7
- [verified] ClubConfig is at version 9 with a migrate() chain; `moduleBinds` is already a `Map<String,String>` of name→InputUtil translation key, and ModuleBinds hardens parsing with a BAD-set that never throws on a hand-edited value and self-heals by dropping it. A new `Map<String,String> itemScrollBinds` follows the identical, already-proven shape.
  src/main/java/com/club/config/ClubConfig.java:23,38 and migrate() 275-364; ModuleBinds.java:68-76,92
- [unsure] Grim's inventory work is a lag-compensating state simulation with ghost-item resync, not a clicks-per-second counter; it explicitly removed BadPacketsM for falsing because a vanilla client can send two slot changes in one tick. So Grim punishes DESYNC (impossible resulting inventory state), not raw click volume — which means an intent-based, re-validated batch is the right shape for Grim, and a rate cap is the right shape for NCP.
  https://github.com/GrimAnticheat/Grim (+ SpigotMC "Inventory rewrite, compat fixes" update notes)
- [likely] Slot ownership is determined mod-proof by `slot.inventory instanceof PlayerInventory` (not index arithmetic), and within PlayerInventory `slot.getIndex()` is 0–8 hotbar, 9–35 main, 36–39 armour, 40 offhand.
  net.minecraft.screen.slot.Slot exposes `public final Inventory inventory` and `public int getIndex()` (javap); the PlayerInventory index layout is the standard 1.21 mapping