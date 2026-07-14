package com.club.mixin;

import com.club.ClubMod;
import com.club.modules.perf.BackgroundThrottle;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The frame cap, and the only place vanilla asks for it.
 *
 * <p>{@code getFramerateLimit()} is private with exactly ONE call site — {@code render()} — and the Max
 * Framerate slider in the options screen reads {@code GameOptions.getMaxFps()} instead, so tightening the
 * return value here throttles the game without lying to the player about what they set. (Both halves
 * re-verified against the 1.21.1 bytecode for v0.1.3: one {@code invokevirtual getFramerateLimit} in the
 * whole class, and its value flows only into {@code RenderSystem.limitDisplayFPS}.)
 *
 * <p><b>THIS MIXIN DOES NOT TOUCH THE FPS COUNTER, AND MUST NOT.</b> It is named after fps and it is the
 * obvious suspect whenever a frame number looks wrong (owner item 16: two readouts on screen disagreeing),
 * so the invariant is worth writing down. The value returned here decides only how long
 * {@code RenderSystem.limitDisplayFPS} sleeps at the end of a frame. {@code fpsCounter},
 * {@code currentFps} and {@code fpsDebugString} are assigned further down {@code render()} and are never
 * read or written from here. Throttling therefore lowers the frames the player actually gets — which the
 * counter then honestly reports — and it can never make two on-screen readouts of that counter differ.
 */
@Mixin(MinecraftClient.class)
public class MixinMinecraftClientFps {

    /** Latched when the throttle threw — an instance field merged into the (singleton) client, which is the
     *  plainest thing Mixin can carry. See {@link #club$throttleInBackground}. */
    @Unique private boolean club$throttleBroken;

    @Inject(method = "getFramerateLimit", at = @At("RETURN"), cancellable = true)
    private void club$throttleInBackground(CallbackInfoReturnable<Integer> cir) {
        // FAIL OPEN TO VANILLA. This runs inside MinecraftClient.render(), every frame, and nothing up the
        // chain catches an exception out of it: a throw here is not a broken frame cap, it is a crash — and
        // the throttle is a battery feature, i.e. the least important thing in the mod. The Club HUD already
        // has exactly this latch (HudManager.hudFailed); the frame cap had nothing at all. So: log once, then
        // stand down for the session and hand the player back the limit they chose themselves.
        //
        // Exception, not Throwable — deliberately, and for the same reason as HudManager: an Error must reach
        // Minecraft's crash handler instead of being swallowed into a game we no longer understand.
        if (club$throttleBroken) return;
        try {
            int vanilla = cir.getReturnValueI();
            int limited = BackgroundThrottle.limit(vanilla);
            if (limited != vanilla) cir.setReturnValue(limited);   // only ever tighten; identity is a no-op
        } catch (Exception e) {
            club$throttleBroken = true;
            ClubMod.LOGGER.error("[Club] The background frame throttle threw and is now OFF for this session; "
                    + "the game keeps the frame limit you chose. Nothing else in the mod is affected. "
                    + "Please report this:", e);
        }
    }
}
