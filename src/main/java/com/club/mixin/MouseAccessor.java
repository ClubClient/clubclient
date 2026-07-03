package com.club.mixin;

import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Lets the Club menu mark the cursor as locked WITHOUT calling {@link Mouse#lockCursor()} — which
 * in 1.21.1 calls {@code setScreen(null)} internally and would kill the close animation. With the
 * flag set (plus a raw GLFW cursor grab), {@code Mouse.tick()} feeds look deltas to the camera
 * while the closing menu still renders its fade — mouse-look returns the instant the close key is
 * pressed, not ~0.28s later.
 */
@Mixin(Mouse.class)
public interface MouseAccessor {
    @Accessor("cursorLocked") void club$setCursorLocked(boolean locked);
}
