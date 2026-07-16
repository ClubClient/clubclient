package com.club.compat;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;

import static org.lwjgl.glfw.GLFW.*;

/**
 * Is a physical key held right now, across Minecraft versions.
 *
 * <p><b>Why this class exists.</b> 1.21.9 rewrote the client's input layer, and three of its changes land on
 * the same question. {@code InputUtil.isKeyPressed} stopped taking the GLFW window HANDLE (a {@code long})
 * and started taking the {@code Window} OBJECT. {@code Screen.hasShiftDown()} / {@code hasControlDown()} /
 * {@code hasAltDown()} — the static "is a modifier down" queries — were DELETED outright, the modifier state
 * having moved onto the event objects ({@code Click}, {@code KeyInput}) that the same release introduced. And
 * {@code MinecraftClient.IS_SYSTEM_MAC}, which the Control query needs (see below), went with them. Code that
 * asks OUTSIDE an event — a scroll handler, a per-tick poll — has no event to ask, so it asks the keyboard.</p>
 *
 * <p><b>Every boundary here is 1.21.9, and every one is measured, not guessed.</b> Read out of the Yarn
 * mappings for all eleven versions 1.21.1..1.21.11 on 2026-07-16: {@code isKeyPressed} is {@code (JI)Z}
 * through 1.21.8 and {@code (Lnet/minecraft/class_1041;I)Z} — {@code Window} — from 1.21.9 on;
 * {@code hasShiftDown} and its two siblings exist on {@code Screen} through 1.21.8 and on NO class from
 * 1.21.9 on; {@code MinecraftClient.IS_SYSTEM_MAC} ({@code field_1703}) is present through 1.21.8 and absent
 * from 1.21.9 on. The same release renamed {@code KeyBinding.getTranslationKey} to {@code getId} and turned
 * the keybind category from a {@code String} into a {@code KeyBinding.Category}. One input refactor, one
 * boundary.</p>
 *
 * <p><b>The modifier trio is vanilla's own implementation, kept — including the part that surprises.</b>
 * These are not "shift is 340", they are what {@code Screen} actually did, read out of the 1.21.8 bytecode:
 * {@code hasShiftDown()} is left-or-right shift (340/344) and {@code hasAltDown()} is left-or-right alt
 * (342/346), but {@code hasControlDown()} IS NOT the symmetric case — <b>on macOS it reads COMMAND</b>
 * (LEFT/RIGHT_SUPER, 343/347), and only elsewhere reads Control (341/345). A "reasonable" symmetric
 * re-implementation would compile, pass every test we own, and quietly break the Ctrl gesture for every Mac
 * player — the exact shape of bug this project measures to avoid. The Mac test itself moved in the same
 * release, so it is guarded on the same boundary: {@code MinecraftClient.IS_SYSTEM_MAC} through 1.21.8,
 * {@code MacWindowUtil.IS_MAC} from 1.21.9 on (measured present 1.21.5..1.21.11, so the 1.21.9 branch is
 * safely inside its range).</p>
 */
public final class Kbd {
    private Kbd() {}

    /**
     * Is the GLFW key {@code code} physically down? False when there is no window yet — the tick loop can ask
     * before the client is ready, and "no window" is not "key held".
     */
    public static boolean pressed(int code) {
        if (code == GLFW_KEY_UNKNOWN) return false;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.getWindow() == null) return false;
        //? if <1.21.9 {
        return InputUtil.isKeyPressed(mc.getWindow().getHandle(), code);
        //?} else {
        /*return InputUtil.isKeyPressed(mc.getWindow(), code);*/
        //?}
    }

    /** Either shift key. The replacement for {@code Screen.hasShiftDown()}, with its exact meaning. */
    public static boolean shift() {
        return pressed(GLFW_KEY_LEFT_SHIFT) || pressed(GLFW_KEY_RIGHT_SHIFT);
    }

    /**
     * The replacement for {@code Screen.hasControlDown()}, with its exact meaning: <b>Command on macOS</b>,
     * Control everywhere else. See the class javadoc — this asymmetry is vanilla's, and it is deliberate.
     */
    public static boolean ctrl() {
        return isMac() ? pressed(GLFW_KEY_LEFT_SUPER) || pressed(GLFW_KEY_RIGHT_SUPER)
                       : pressed(GLFW_KEY_LEFT_CONTROL) || pressed(GLFW_KEY_RIGHT_CONTROL);
    }

    /** Either alt key. The replacement for {@code Screen.hasAltDown()}. */
    public static boolean alt() {
        return pressed(GLFW_KEY_LEFT_ALT) || pressed(GLFW_KEY_RIGHT_ALT);
    }

    /** Is this a Mac? Asked only by {@link #ctrl}, and asked of the game rather than of {@code os.name}, so
     *  that our answer cannot drift from the one vanilla's own keybinds are using. */
    private static boolean isMac() {
        //? if <1.21.9 {
        return MinecraftClient.IS_SYSTEM_MAC;
        //?} else {
        /*return net.minecraft.client.util.MacWindowUtil.IS_MAC;*/
        //?}
    }
}
