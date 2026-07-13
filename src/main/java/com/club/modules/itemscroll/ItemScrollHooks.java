package com.club.modules.itemscroll;

import com.club.config.ClubConfig;
import com.club.mixin.MixinHandledScreenAccessor;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import org.lwjgl.glfw.GLFW;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Where the mouse meets the module. Zero {@code @Inject}: scroll, click and release come from Fabric's
 * screen events, which fire BEFORE the screen's own handling — returning false consumes the gesture,
 * returning true leaves vanilla (and REI, and EMI) completely untouched. Nothing here can lose a fight
 * with another mod's mixin, because nothing here is a mixin.
 *
 * <p>There is no drag event in the Fabric API, and we do not want one: a drag is the click that armed it,
 * plus the slot under the cursor sampled each frame, plus the release. A {@code mouseDragged} injection
 * would have to out-race vanilla's own quick-craft; this way vanilla's quick-craft never starts.</p>
 */
public final class ItemScrollHooks {
    private ItemScrollHooks() {}

    // The live drag: which screen armed it, which button holds it, and every slot already moved (a slow
    // hand crosses the same slot for many frames — moving it twice would empty the stack behind it too).
    private static Screen dragScreen;
    private static int dragButton = -1;
    private static final Set<Integer> dragged = new HashSet<>();

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (!(screen instanceof HandledScreen<?>)) return;
            // Creative is a different animal: its screen overrides onMouseClick and its non-inventory tabs
            // are fake slots backed by a client-side handler. Half-working here is worse than absent, and
            // the card says so out loud.
            if (screen instanceof CreativeInventoryScreen) return;

            ScreenMouseEvents.allowMouseScroll(screen).register(ItemScrollHooks::onScroll);
            ScreenMouseEvents.allowMouseClick(screen).register(ItemScrollHooks::onClick);
            ScreenMouseEvents.allowMouseRelease(screen).register(ItemScrollHooks::onRelease);
            ScreenEvents.afterRender(screen).register((s, ctx, mouseX, mouseY, delta) -> onFrame(s, mouseX, mouseY));
            ScreenEvents.remove(screen).register(s -> endDrag());
        });
    }

    // ---- events -----------------------------------------------------------------------------------

    private static boolean onScroll(Screen screen, double mouseX, double mouseY, double horizontal, double vertical) {
        if (vertical == 0 || !ItemScrollModule.active()) return true;
        // Up = out of the hovered slot's inventory, down = into it. The reverse toggle is the whole of the
        // reference's REVERSE_SCROLL_DIRECTION_* config, in one line.
        boolean out = (vertical > 0) != ClubConfig.get().itemScroll.reverseScroll;
        return !fire(screen, mouseX, mouseY, new Gesture(mods(), GestureInput.SCROLL), out);
    }

    private static boolean onClick(Screen screen, double mouseX, double mouseY, int button) {
        if (!ItemScrollModule.active()) return true;
        GestureInput input = inputOf(button);
        if (input == null) return true;

        Gesture gesture = new Gesture(mods(), input);
        ScrollAction action = ItemScrollBinds.actionFor(gesture);
        if (action == null) return true;

        if (action == ScrollAction.DRAG_MOVE) {
            // Arm the drag AND move the slot it started on — the press is part of the stroke, exactly as it
            // is in the reference. Consuming the event is also what keeps vanilla's quick-craft from arming.
            dragScreen = screen;
            dragButton = button;
            dragged.clear();
            dragSlot(screen, mouseX, mouseY);
            return false;
        }
        // A click gesture has no direction: it always means "out of the hovered slot's inventory".
        return !fire(screen, mouseX, mouseY, gesture, true);
    }

    private static boolean onRelease(Screen screen, double mouseX, double mouseY, int button) {
        if (screen != dragScreen || button != dragButton) return true;
        endDrag();
        return false;   // the press was ours, so the release is too
    }

    /** Each frame of a live drag: whatever slot the cursor is over now, move it (once). */
    private static void onFrame(Screen screen, int mouseX, int mouseY) {
        if (screen != dragScreen) return;
        if (!ItemScrollModule.active()) { endDrag(); return; }
        dragSlot(screen, mouseX, mouseY);
    }

    private static void endDrag() {
        dragScreen = null;
        dragButton = -1;
        dragged.clear();
    }

    // ---- the act ----------------------------------------------------------------------------------

    private static void dragSlot(Screen screen, double mouseX, double mouseY) {
        Slot slot = slotAt(screen, mouseX, mouseY);
        if (slot == null || !dragged.add(slot.id)) return;
        act((HandledScreen<?>) screen, ScrollAction.DRAG_MOVE, slot, true);
    }

    /** @return true if the gesture was ours and we acted on it (so the event must be consumed) */
    private static boolean fire(Screen screen, double mouseX, double mouseY, Gesture gesture, boolean out) {
        ScrollAction action = ItemScrollBinds.actionFor(gesture);
        if (action == null || action == ScrollAction.DRAG_MOVE) return false;

        Slot slot = slotAt(screen, mouseX, mouseY);
        if (slot == null) return false;

        act((HandledScreen<?>) screen, action, slot, out);
        return true;   // the gesture was ours even if the plan turned out empty — vanilla must not also act
    }

    /** Carry out one action on one slot. The module's entry point: the events call it, and so does the
     *  in-game harness, which drives the real screen rather than a stand-in. */
    public static void act(HandledScreen<?> screen, ScrollAction action, Slot hovered, boolean out) {
        MinecraftClient mc = MinecraftClient.getInstance();
        ScreenHandler handler = screen.getScreenHandler();
        if (mc.player == null || mc.interactionManager == null) return;
        // The creative refusal belongs HERE, not only where the events are hooked. Its tabs are fake slots
        // over a client-side handler, and clicking them means something else entirely — the harness proved
        // it by landing on this screen (vanilla swaps InventoryScreen for it in creative) and walking away
        // with 64 jungle stairs on the cursor. A guard at the door is not a guard on the act.
        if (screen instanceof CreativeInventoryScreen) return;
        // Every composed gesture assumes an empty cursor (PICKUP would swap, THROW is ignored outright).
        // Holding a stack means the player is mid-move by hand: stay out of it.
        if (!handler.getCursorStack().isEmpty()) return;

        // One snapshot, one plan: the hovered view must come from the same list the plan is built over, or
        // an id could mean two different things half a line apart.
        List<SlotView> slots = SlotSnapshot.of(handler, hovered, mc.player);
        List<Click> plan = SlotPlan.plan(action, slots, slots.get(hovered.id), out);
        if (plan.isEmpty()) return;

        int syncId = handler.syncId;
        MixinHandledScreenAccessor accessor = (MixinHandledScreenAccessor) screen;
        for (Click click : plan) {
            // The world can change under a plan: a server resync, a hopper, another player. Anything that
            // moves the goalposts aborts the rest — a click aimed at a slot that is no longer what we
            // planned for is how a cursor ends up holding a stack nobody asked for.
            if (mc.currentScreen != screen || handler.syncId != syncId) break;
            Slot target = handler.slots.get(click.slotId());
            accessor.club$onMouseClick(target, click.slotId(), click.button(), type(click.act()));
        }
    }

    // ---- plumbing ---------------------------------------------------------------------------------

    /**
     * The slot under the cursor, or null — and null also for anything outside the container's own box.
     * That bounds check is the single rule that keeps us out of REI's and EMI's overlays: their panels and
     * search fields live beside the GUI, and a mod that eats their scroll is a mod people uninstall.
     */
    private static Slot slotAt(Screen screen, double mouseX, double mouseY) {
        MixinHandledScreenAccessor accessor = (MixinHandledScreenAccessor) screen;
        int x = accessor.club$x(), y = accessor.club$y();
        if (mouseX < x || mouseX > x + accessor.club$backgroundWidth()
                || mouseY < y || mouseY > y + accessor.club$backgroundHeight()) return null;
        return accessor.club$slotAt(mouseX, mouseY);
    }

    private static GestureInput inputOf(int button) {
        return switch (button) {
            case GLFW.GLFW_MOUSE_BUTTON_LEFT   -> GestureInput.LMB;
            case GLFW.GLFW_MOUSE_BUTTON_RIGHT  -> GestureInput.RMB;
            case GLFW.GLFW_MOUSE_BUTTON_MIDDLE -> GestureInput.MMB;
            default -> null;   // side buttons: not ours, and vanilla ignores them too
        };
    }

    private static int mods() {
        return Gesture.mods(Screen.hasShiftDown(), Screen.hasControlDown(), Screen.hasAltDown());
    }

    private static SlotActionType type(Click.Act act) {
        return switch (act) {
            case PICKUP     -> SlotActionType.PICKUP;
            case QUICK_MOVE -> SlotActionType.QUICK_MOVE;
            case THROW      -> SlotActionType.THROW;
        };
    }
}
