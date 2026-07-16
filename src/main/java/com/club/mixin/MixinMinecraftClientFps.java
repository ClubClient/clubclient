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
 * <h2>Nothing is injected past 1.21.1, because Minecraft took the feature over</h2>
 *
 * <p>1.21.2 deleted {@code getFramerateLimit()} and added {@code InactivityFpsLimiter} in the same release
 * — with {@code MINIMIZED_FPS} and two AFK stages. That is this module's whole job, done by the game, and
 * done better: it notices the player has walked away, not merely that the window lost focus.</p>
 *
 * <p>So on 1.21.2+ the injection is simply absent, and {@code PerfMenu.backgroundFps} returns no card at all
 * (owner: "можем просто убирать функции которые появились в ванильном меню"). Not a card with a notice
 * explaining itself — a feature the game now has is not OUR feature standing down, it is a feature we no
 * longer have, and an absent card states that without saying a word. Same rule as Item Scroll on a server
 * that forbids it. Racing vanilla for the same frame cap would be two throttles fighting over one number,
 * which is how you get a bug report about frames nobody can reproduce.</p>
 *
 * <p><b>This is what the compiler cannot tell you.</b> {@code @Inject(method = "getFramerateLimit")} is a
 * STRING. The 1.21.8 build was clean, every test passed, the jar contained every class — and the client died
 * on startup, because {@code "required": true} makes a missed target fatal. The name was measured out of the
 * mappings afterwards: present in 1.21.1, gone in 1.21.2.</p>
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

    //? if <1.21.2 {
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
    //?}
}
