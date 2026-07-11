package com.club.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;

import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.glfw.GLFW.*;

/**
 * Reads whether the PHYSICAL key/button a {@link KeyBinding} maps to is held right now — bypassing
 * Minecraft's keybind conflict system. A hold-to-activate feature (Zoom, Freelook) must fire even
 * when its key is ALSO bound to something else; {@code KeyBinding.isPressed()} can go dead on a
 * conflict, so we poll GLFW directly. Parsed keys are cached per translation string (a rebind writes
 * a new string and re-parses).
 */
public final class Keys {
    private Keys() {}

    private static final Map<String, InputUtil.Key> CACHE = new HashMap<>();

    public static boolean held(KeyBinding kb) {
        if (kb == null) return false;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return false;
        InputUtil.Key key = CACHE.computeIfAbsent(kb.getBoundKeyTranslationKey(), InputUtil::fromTranslationKey);
        long h = mc.getWindow().getHandle();
        if (key.getCategory() == InputUtil.Type.KEYSYM) {
            int code = key.getCode();
            return code != GLFW_KEY_UNKNOWN && InputUtil.isKeyPressed(h, code);
        }
        if (key.getCategory() == InputUtil.Type.MOUSE) {
            return glfwGetMouseButton(h, key.getCode()) == GLFW_PRESS;
        }
        return kb.isPressed();   // exotic binding — fall back to the vanilla state
    }
}
