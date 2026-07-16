package com.club.modules.perf;

import com.club.config.ClubConfig;
import com.club.ui.IconGlyph;
import com.club.ui.menu.MenuContent;

import java.util.List;

/**
 * The one performance setting that is actually a CHOICE — Background FPS — and nothing else.
 *
 * <p><b>Why this class shrank.</b> It once built a whole "Performance" category of three cards. Two of them,
 * Particles and Block Entities, were culls that are invisible BY CONSTRUCTION — they skip work whose result
 * cannot reach the screen, with no distance limit and no per-frame cap. A switch on a thing the player can
 * never see is not a dial; it only ever earned its place as a KILL SWITCH — the one edit that rules the mod
 * out when a rendering bug is suspected. The owner's call, and the honest one: bake the culls in, on by
 * default, and keep that kill switch in the config file ({@code perf.cullParticles} / {@code perf.cullBlockEntities})
 * instead of a menu toggle pretending to be a quality setting. {@link ParticleCull#enabled()} and
 * {@link BlockEntityCull#enabled()} still read those fields, so the escape hatch is real — it is just no longer a card.
 *
 * <p><b>Why Background FPS stayed.</b> It is the odd one out and always was: it gives ZERO in-game FPS and
 * changes nothing you see while playing — it caps the frame rate only while the window is in the BACKGROUND,
 * for your battery and your fans. That is a genuine preference, not a cull, so it remains a card (now in Misc).
 * Its own subtitle says what it is NOT, because calling it an FPS boost would be a lie the mod has already
 * retracted three of.
 */
public final class PerfMenu {
    private PerfMenu() {}

    /**
     * Cap the frame rate while the window is behind something else — or NULL from 1.21.2 on, because
     * Minecraft does this itself now.
     *
     * <p>1.21.2 deleted {@code MinecraftClient.getFramerateLimit()} (the seam this rode) and added
     * {@code InactivityFpsLimiter} in the same release: {@code MINIMIZED_FPS} plus two AFK stages. That is
     * this card's whole job, done by the game, and done better — it notices the player walked away, not just
     * that the window lost focus.</p>
     *
     * <p>So the card is gone there, rather than lit with a notice explaining itself (owner: "можем просто
     * убирать функции которые появились в ванильном меню"). A feature the game now has is not ours standing
     * down — it is a feature we no longer have, and an absent card says that without a word. Same rule as
     * Item Scroll on a server that forbids it; {@code MenuContent.cards} already drops the null.</p>
     */
    public static MenuContent.Module backgroundFps() {
        //? if <1.21.2 {
        ClubConfig.Perf p = ClubConfig.get().perf;
        return new MenuContent.Module(
                "Background FPS",
                // This card must never be mistaken for an FPS feature, so its own subtitle says what it is
                // NOT. Calling it a boost would be a lie, and the mod has already retracted three of those.
                "Cap the frame rate while the game is behind another window. Zero in-game FPS — this is for "
                        + "your battery and your fans.",
                IconGlyph.BACKGROUND_FPS,
                () -> p.throttleWhenUnfocused, v -> { p.throttleWhenUnfocused = v; save(); },
                () -> { p.throttleWhenUnfocused = true; p.backgroundFps = 15; save(); },
                List.of(
                        // Floor 15: below that the first frame after you alt-tab back costs 1/cap and the
                        // window feels broken. Not timidity — measured.
                        new MenuContent.SliderSetting("Cap", 15f, 60f, 5f,
                                () -> p.backgroundFps, v -> { p.backgroundFps = Math.round(v); save(); })));
        //?} else {
        /*return null;   // Minecraft's own InactivityFpsLimiter does this from 1.21.2 — see the javadoc.*/
        //?}
    }

    private static void save() { ClubConfig.save(); }
}
