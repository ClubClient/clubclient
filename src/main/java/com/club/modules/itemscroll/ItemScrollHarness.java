package com.club.modules.itemscroll;

import com.club.mixin.MixinHandledScreenAccessor;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.glfw.GLFW;

/**
 * The module's side of the in-game harness (dev only; nothing calls it in a normal session). It exists
 * because the unit tests can only prove the PLAN — that the clicks are the right clicks. Whether those
 * clicks actually empty a chest, and whether the cursor is empty when they are done, is a question about
 * a real server, a real handler and a real packet round-trip, and it can only be answered in the game.
 *
 * <p>Everything here drives the REAL screen through the REAL entry point ({@link ItemScrollHooks#act}) —
 * no stand-in, no shortcut into the planner.</p>
 */
public final class ItemScrollHarness {
    private ItemScrollHarness() {}

    /** Chest layout the checks are written against: two stone stacks, one dirt, and a gap. */
    public static final int STONE_A = 0, STONE_B = 1, DIRT = 2;

    /**
     * Places a chest next to the player, fills it, and opens it — exactly as a right-click would.
     *
     * <p>All of it is scheduled onto the SERVER thread. The harness ticks on the client thread, and the
     * integrated server is a different thread with its own copy of the world: mutating it from here
     * touches blocks and inventories out from under the tick that owns them, and the screen either never
     * opens or opens onto state the client never hears about.</p>
     *
     * @return what happened, for the report — a silent no-op is the one thing a harness must never do
     */
    public static String openChest(MinecraftClient mc) {
        MinecraftServer server = mc.getServer();
        if (server == null) return "no integrated server";
        if (mc.player == null) return "no client player";

        server.execute(() -> {
            ServerPlayerEntity sp = server.getPlayerManager().getPlayer(mc.player.getUuid());
            if (sp == null) return;
            ServerWorld world = sp.getServerWorld();

            BlockPos pos = sp.getBlockPos().up(2);       // inside the chest handler's own 8-block canUse range
            world.setBlockState(pos, Blocks.CHEST.getDefaultState());
            if (!(world.getBlockEntity(pos) instanceof ChestBlockEntity chest)) return;

            sp.getInventory().clear();                   // a deterministic player side: everything we count is ours
            chest.clear();
            chest.setStack(STONE_A, new ItemStack(Items.STONE, 64));
            chest.setStack(STONE_B, new ItemStack(Items.STONE, 16));
            chest.setStack(DIRT,    new ItemStack(Items.DIRT, 32));
            sp.openHandledScreen(chest);
        });
        return "chest scheduled on the server thread";
    }

    /** Dresses the player (armour + offhand) and stocks the main inventory, on the server thread. The
     *  survival screen is the one place both regions are the player's own inventory — and so the one
     *  place a wrong region rule undresses you. */
    public static String dressPlayer(MinecraftClient mc) {
        MinecraftServer server = mc.getServer();
        if (server == null) return "no integrated server";
        if (mc.player == null) return "no client player";

        server.execute(() -> {
            ServerPlayerEntity sp = server.getPlayerManager().getPlayer(mc.player.getUuid());
            if (sp == null) return;
            sp.closeHandledScreen();
            // SURVIVAL, deliberately: vanilla's InventoryScreen swaps ITSELF for the creative screen when the
            // player is in creative, so a harness that forgets this tests the one screen we refuse to touch.
            sp.changeGameMode(net.minecraft.world.GameMode.SURVIVAL);
            sp.getInventory().clear();
            sp.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
            sp.equipStack(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
            sp.getInventory().setStack(9, new ItemStack(Items.STONE, 64));    // main inventory, first row
            sp.getInventory().setStack(10, new ItemStack(Items.DIRT, 8));
        });
        return "player dressed on the server thread";
    }

    /** Back to creative — the harness found the player in it, and the steps after ours assume it. */
    public static String restoreCreative(MinecraftClient mc) {
        MinecraftServer server = mc.getServer();
        if (server == null || mc.player == null) return "no server";
        server.execute(() -> {
            ServerPlayerEntity sp = server.getPlayerManager().getPlayer(mc.player.getUuid());
            if (sp != null) sp.changeGameMode(net.minecraft.world.GameMode.CREATIVE);
        });
        return "creative restored";
    }

    // ---- driving ----------------------------------------------------------------------------------

    /** Perform an action on a slot of the open screen, exactly as a gesture would. */
    public static void perform(MinecraftClient mc, ScrollAction action, int slotId, boolean out) {
        HandledScreen<?> screen = screen(mc);
        if (screen == null) return;
        ScreenHandler handler = screen.getScreenHandler();
        if (slotId < 0 || slotId >= handler.slots.size()) return;
        ItemScrollHooks.act(screen, action, handler.slots.get(slotId), out);
    }

    // ---- driving a REAL drag ----------------------------------------------------------------------
    //
    // Not through act(): through the same path a hand takes. The OS cursor is moved with GLFW, the press
    // and release go through Fabric's own event invokers, and if an event comes back ALLOWED the harness
    // calls the screen's vanilla handler itself — because that is what the client would do, and a drag
    // test that skips it proves nothing about the thing we are afraid of.

    /** Bind the drag to a bare left click for the duration of the test: the harness cannot hold Shift
     *  (GLFW key state is physical), and a bare LMB drag is also the harshest case — it is the button
     *  vanilla itself drags with. */
    public static void bindDragToLeftClick() {
        ItemScrollBinds.set(ScrollAction.DRAG_MOVE, Gesture.of(GestureInput.LMB, 0));
    }

    public static void restoreDefaultGestures() {
        com.club.config.ClubConfig.get().itemScroll.gestures.clear();
        com.club.config.ClubConfig.save();
    }

    // ---- driving the gesture editor ---------------------------------------------------------------

    /** Click a row's chip, press AND release — the release matters: the arming click is itself a left
     *  click, and a capture that starts on the press binds every row to LMB the instant you touch it. */
    public static void armRow(MinecraftClient mc, int row) {
        if (!(mc.currentScreen instanceof GestureScreen screen)) return;
        double[] p = screen.chipCentre(ScrollAction.values()[row]);
        screen.mouseClicked(p[0], p[1], GLFW.GLFW_MOUSE_BUTTON_LEFT);
        screen.mouseReleased(p[0], p[1], GLFW.GLFW_MOUSE_BUTTON_LEFT);
    }

    /** Perform a bare left click into an armed row. It must be REFUSED — that click is how a player picks
     *  items up, and a row that swallowed it would brick every inventory in the game. */
    public static void captureBareLeftClick(MinecraftClient mc) {
        if (!(mc.currentScreen instanceof GestureScreen screen)) return;
        double[] p = screen.chipCentre(ScrollAction.MOVE_ONE);
        screen.mouseClicked(p[0], p[1], GLFW.GLFW_MOUSE_BUTTON_LEFT);
        screen.mouseReleased(p[0], p[1], GLFW.GLFW_MOUSE_BUTTON_LEFT);
    }

    /** Arm "Move everything" and scroll — which is Move one's gesture. It must be TAKEN, not shared. */
    public static void stealGesture(MinecraftClient mc) {
        if (!(mc.currentScreen instanceof GestureScreen screen)) return;
        double[] p = screen.chipCentre(ScrollAction.MOVE_EVERYTHING);
        screen.mouseClicked(p[0], p[1], GLFW.GLFW_MOUSE_BUTTON_LEFT);
        screen.mouseReleased(p[0], p[1], GLFW.GLFW_MOUSE_BUTTON_LEFT);
        screen.mouseScrolled(p[0], p[1], 0, 1);
    }

    /** Put the real mouse pointer over the centre of a slot. */
    public static void moveCursor(MinecraftClient mc, int slotId) {
        HandledScreen<?> screen = screen(mc);
        if (screen == null) return;
        double[] p = slotCentre(mc, screen, slotId);
        double factor = mc.getWindow().getScaleFactor();
        // Write the position the CLIENT tracks, not the one the OS owns.
        //
        // This used to call glfwSetCursorPos, which merely ASKS the OS to move the pointer; the client only
        // learns about it if the window is focused and the OS delivers the callback. On one machine it did,
        // on another it did not — so the drag test was really testing the window manager, and it failed on a
        // clean tree with one slot moved out of three. Mouse.x/y is the very field a real cursor event
        // writes, and MinecraftClient feeds it straight into the screen's mouseX/mouseY every frame, which
        // is the seam the drag samples. Same path, no OS in it. (MouseAccessor already exists — the menu
        // re-centres the cursor with it.)
        com.club.mixin.MouseAccessor mouse = (com.club.mixin.MouseAccessor) mc.mouse;
        mouse.club$setX(p[0] * factor);
        mouse.club$setY(p[1] * factor);
        // …and ask the OS to put the real pointer in the same place. NOT as the mechanism — as agreement:
        // if the window is focused and a cursor callback does arrive, it now carries the coordinates we
        // already wrote instead of dragging the tracked position back to wherever the physical mouse sits.
        GLFW.glfwSetCursorPos(mc.getWindow().getHandle(), p[0] * factor, p[1] * factor);
    }

    /** The slot the module would see under the cursor right now — the mechanism the drag depends on. */
    public static int hoveredSlotId(MinecraftClient mc) {
        HandledScreen<?> screen = screen(mc);
        if (screen == null) return -1;
        double factor = mc.getWindow().getScaleFactor();
        Slot slot = ((MixinHandledScreenAccessor) screen)
                .club$slotAt(mc.mouse.getX() / factor, mc.mouse.getY() / factor);
        return slot == null ? -1 : slot.id;
    }

    /** @return "consumed" when Club took the press (so vanilla never saw it), or what vanilla then did */
    public static String dragPress(MinecraftClient mc, int slotId) {
        HandledScreen<?> screen = screen(mc);
        if (screen == null) return "no screen";
        moveCursor(mc, slotId);
        double[] p = slotCentre(mc, screen, slotId);
        boolean allowed = ScreenMouseEvents.allowMouseClick(screen).invoker()
                .allowMouseClick(screen, p[0], p[1], GLFW.GLFW_MOUSE_BUTTON_LEFT);
        if (!allowed) return "consumed";
        screen.mouseClicked(p[0], p[1], GLFW.GLFW_MOUSE_BUTTON_LEFT);   // the client would — so we do
        return "NOT consumed — vanilla handled the press";
    }

    public static String dragRelease(MinecraftClient mc) {
        HandledScreen<?> screen = screen(mc);
        if (screen == null) return "no screen";
        double x = mc.mouse.getX() / mc.getWindow().getScaleFactor();
        double y = mc.mouse.getY() / mc.getWindow().getScaleFactor();
        boolean allowed = ScreenMouseEvents.allowMouseRelease(screen).invoker()
                .allowMouseRelease(screen, x, y, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        if (!allowed) return "consumed";
        screen.mouseReleased(x, y, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        return "NOT consumed — vanilla handled the release";
    }

    /** Vanilla's quick-craft never armed: no drag, no collected slots, no shift-drag quick-move in flight. */
    public static boolean vanillaDragIdle(MinecraftClient mc) {
        HandledScreen<?> screen = screen(mc);
        if (screen == null) return false;
        MixinHandledScreenAccessor a = (MixinHandledScreenAccessor) screen;
        return !a.club$cursorDragging() && a.club$cursorDragSlots().isEmpty() && a.club$quickMovingStack().isEmpty();
    }

    /** Would Club eat this event? False means we let it through — the answer REI and EMI care about. */
    public static boolean eventLeftToVanilla(MinecraftClient mc, int button, int slotId) {
        HandledScreen<?> screen = screen(mc);
        if (screen == null) return false;
        double[] p = slotCentre(mc, screen, slotId);
        return ScreenMouseEvents.allowMouseClick(screen).invoker().allowMouseClick(screen, p[0], p[1], button);
    }

    /** A scroll BESIDE the container box — where REI's and EMI's panels live. We must not touch it. */
    public static boolean scrollOutsideLeftToVanilla(MinecraftClient mc) {
        HandledScreen<?> screen = screen(mc);
        if (screen == null) return false;
        return ScreenMouseEvents.allowMouseScroll(screen).invoker()
                .allowMouseScroll(screen, 4, 4, 0, 1);   // top-left corner: outside every container's box
    }

    private static double[] slotCentre(MinecraftClient mc, HandledScreen<?> screen, int slotId) {
        MixinHandledScreenAccessor a = (MixinHandledScreenAccessor) screen;
        Slot slot = screen.getScreenHandler().slots.get(slotId);
        return new double[] { a.club$x() + slot.x + 8.0, a.club$y() + slot.y + 8.0 };
    }

    // ---- reading the world back -------------------------------------------------------------------

    public static HandledScreen<?> screen(MinecraftClient mc) {
        return mc.currentScreen instanceof HandledScreen<?> s ? s : null;
    }

    public static int count(MinecraftClient mc, int slotId) {
        HandledScreen<?> screen = screen(mc);
        if (screen == null) return -1;
        return screen.getScreenHandler().slots.get(slotId).getStack().getCount();
    }

    /** How many slots on the CONTAINER side still hold something. */
    public static int containerStacks(MinecraftClient mc) {
        HandledScreen<?> screen = screen(mc);
        if (screen == null) return -1;
        int n = 0;
        for (Slot slot : screen.getScreenHandler().slots)
            if (!(slot.inventory instanceof PlayerInventory) && !slot.getStack().isEmpty()) n++;
        return n;
    }

    /** How many items of this type the player is holding across their whole inventory. */
    public static int playerItems(MinecraftClient mc, boolean stone) {
        if (mc.player == null) return -1;
        int n = 0;
        PlayerInventory inv = mc.player.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.getStack(i);
            if (s.isOf(stone ? Items.STONE : Items.DIRT)) n += s.getCount();
        }
        return n;
    }

    /** The invariant that stands between a player and a stack on the floor of someone else's base. */
    public static boolean cursorEmpty(MinecraftClient mc) {
        return mc.player != null && mc.player.currentScreenHandler.getCursorStack().isEmpty();
    }

    /** Main inventory (rows 9–35) — what a "move everything" out of it must leave behind. */
    public static boolean mainInventoryEmpty(MinecraftClient mc) {
        if (mc.player == null) return false;
        PlayerInventory inv = mc.player.getInventory();
        for (int i = 9; i <= 35; i++) if (!inv.getStack(i).isEmpty()) return false;
        return true;
    }

    /** Hotbar (0–8) — where a survival-screen bulk move sends them, exactly as vanilla's quick-move does. */
    public static int hotbarItems(MinecraftClient mc, boolean stone) {
        if (mc.player == null) return -1;
        int n = 0;
        PlayerInventory inv = mc.player.getInventory();
        for (int i = 0; i <= 8; i++) {
            ItemStack s = inv.getStack(i);
            if (s.isOf(stone ? Items.STONE : Items.DIRT)) n += s.getCount();
        }
        return n;
    }

    /** Everything a failing check needs to explain itself — a FAIL with no state is a FAIL you debug twice. */
    public static String describe(MinecraftClient mc) {
        if (mc.player == null) return "no player";
        ScreenHandler handler = mc.player.currentScreenHandler;
        StringBuilder sb = new StringBuilder();
        sb.append("screen=").append(mc.currentScreen == null ? "null" : mc.currentScreen.getClass().getSimpleName());
        sb.append(" handler=").append(handler.getClass().getSimpleName());
        sb.append(" mode=").append(mc.interactionManager == null ? "?" : mc.interactionManager.getCurrentGameMode());
        sb.append(" cursor=").append(handler.getCursorStack());
        sb.append(" hotbar[stone=").append(hotbarItems(mc, true)).append(" dirt=").append(hotbarItems(mc, false)).append("]");
        sb.append(" mainEmpty=").append(mainInventoryEmpty(mc));
        sb.append(" slot9=").append(handler.slots.size() > 9 ? handler.slots.get(9).getStack() : "-");
        return sb.toString();
    }

    public static boolean stillWearingHelmet(MinecraftClient mc) {
        return mc.player != null && mc.player.getEquippedStack(EquipmentSlot.HEAD).isOf(Items.IRON_HELMET);
    }

    public static boolean stillHoldingShield(MinecraftClient mc) {
        return mc.player != null && mc.player.getEquippedStack(EquipmentSlot.OFFHAND).isOf(Items.SHIELD);
    }
}
