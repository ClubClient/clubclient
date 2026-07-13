package com.club.modules.itemscroll;

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
