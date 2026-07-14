package com.club.modules.perf;

import com.club.config.ClubConfig;
import com.club.modules.ModuleNotices;
import com.club.ui.IconGlyph;
import com.club.ui.menu.MenuContent;
import net.fabricmc.loader.api.FabricLoader;

import java.util.List;

/**
 * The Performance card — ONE card, not a tab of eight sliders.
 *
 * <p>A "Performance" tab full of toggles is what a cheat client looks like, and it also lies: it implies each
 * row is a dial the player should be tuning. These are not dials. Both culls are invisible by construction —
 * they skip work whose result cannot be seen — so the honest default for both is ON, and the toggles exist
 * only so a player chasing a bug can rule us out.
 *
 * <p>There is deliberately no distance slider and no particle cap. Those trade PIXELS for FRAMES, and the
 * moment we ship one we stop being able to say the sentence that makes this whole feature worth having:
 * nothing here changes what you see.
 *
 * <p>THAT SENTENCE IS A PROMISE ANOTHER FILE KEEPS. The card's subtitle says "Nothing here changes the
 * picture", and it is only true because {@link BlockEntityCull} culls vanilla block entity types and nothing
 * else — a modded renderer that draws beyond its own block cannot be erased by us. Loosen that rule and this
 * string becomes a lie, on by default, in the one module whose entire claim is that it is invisible.
 *
 * <p>The background throttle sits here too, and its row says what it is. It gives ZERO in-game FPS. It is a
 * battery feature.
 */
public final class PerfMenu {
    private PerfMenu() {}

    /** Called once at init. Registers the card's honest self-assessment (see {@link ModuleNotices}). */
    public static void init() {
        ModuleNotices.register("Performance", () -> {
            ClubConfig.Perf p = ClubConfig.get().perf;
            if (p == null) return null;
            if (!p.cullParticles && !p.cullBlockEntities && !p.throttleWhenUnfocused)
                return "Idle — every option is off";
            // The one thing a player deserves to be told without asking: the biggest win in this space is
            // not ours, and it is one mod away.
            if (!FabricLoader.getInstance().isModLoaded("sodium"))
                return "Install Sodium for the entity culling we deliberately don't do";
            return null;
        });
    }

    public static MenuContent.Module card() {
        ClubConfig.Perf p = ClubConfig.get().perf;
        return new MenuContent.Module(
                "Performance",
                "Skip drawing what you cannot see. Nothing here changes the picture.",
                IconGlyph.PERFORMANCE,
                // The master toggle switches everything off at once — the first thing to try when a player
                // suspects us of a rendering bug, and the answer we want them to be able to give in one click.
                () -> p.cullParticles || p.cullBlockEntities || p.throttleWhenUnfocused,
                v -> { p.cullParticles = v; p.cullBlockEntities = v; p.throttleWhenUnfocused = v; save(); },
                () -> { p.cullParticles = true; p.cullBlockEntities = true;
                        p.throttleWhenUnfocused = true; p.backgroundFps = 15; save(); },
                List.of(
                        new MenuContent.ToggleSetting("Cull particles",
                                () -> p.cullParticles, v -> { p.cullParticles = v; save(); }),
                        new MenuContent.ToggleSetting("Cull block entities",
                                () -> p.cullBlockEntities, v -> { p.cullBlockEntities = v; save(); }),
                        new MenuContent.ToggleSetting("Throttle in background",
                                () -> p.throttleWhenUnfocused, v -> { p.throttleWhenUnfocused = v; save(); }),
                        // Floor 15: below that the first frame after you alt-tab back costs 1/cap and the
                        // window feels broken. It is not a number we are being timid about — it is measured.
                        new MenuContent.SliderSetting("Background FPS", 15f, 60f, 5f,
                                () -> p.backgroundFps, v -> { p.backgroundFps = Math.round(v); save(); })));
    }

    private static void save() { ClubConfig.save(); }
}
