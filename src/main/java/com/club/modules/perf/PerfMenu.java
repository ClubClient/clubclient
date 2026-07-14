package com.club.modules.perf;

import com.club.config.ClubConfig;
import com.club.modules.ModuleNotices;
import com.club.ui.IconGlyph;
import com.club.ui.menu.MenuContent;
import net.fabricmc.loader.api.FabricLoader;

import java.util.List;

/**
 * The Performance CATEGORY — three cards, one per thing the mod actually skips.
 *
 * <p><b>What changed in v0.1.3, and what it cost.</b> This was ONE card with three toggles in a popover, and
 * the javadoc that shipped it argued the case well: "a Performance tab full of toggles is what a cheat client
 * looks like, and it also lies — it implies each row is a dial the player should be tuning". That argument
 * still stands against a TAB OF DIALS. It does not stand against three named things, each of which is a
 * separate technique with a separate risk and a separate reason to exist. The owner asked for the category
 * and he is right: the toggles were never dials, they were three different modules hiding in one card.
 *
 * <p><b>The cost, said out loud rather than buried.</b> The old card had a MASTER toggle — one click turned
 * everything off, and its javadoc called that "the first thing to try when a player suspects us of a rendering
 * bug, and the answer we want them to be able to give in one click". Three cards cannot have a master. Ruling
 * the mod out is now three clicks instead of one. They are three clicks in a single place with the category's
 * name on it, which is the honest trade — but it IS a trade, and pretending otherwise would be the kind of
 * quiet loss this project keeps a changelog to prevent.
 *
 * <p><b>Every card must survive the same sentence:</b> nothing here changes what you see. Both culls skip work
 * whose result is invisible BY CONSTRUCTION — no distance limit, no particle cap, nothing that trades pixels
 * for frames. The moment one of these grows a "quality" slider, the sentence dies and the feature is not worth
 * having. The background throttle is the odd one out and says so on its own card: it gives ZERO in-game FPS.
 *
 * <p>THAT SENTENCE IS A PROMISE ANOTHER FILE KEEPS. It is only true because {@link BlockEntityCull} culls
 * vanilla block entity types drawn by vanilla renderers and nothing else — a modded renderer that draws beyond
 * its own block cannot be erased by us. Loosen that and these strings become lies, on by default.
 */
public final class PerfMenu {
    private PerfMenu() {}

    /** Called once at init. Registers the category's honest self-assessment (see {@link ModuleNotices}).
     *
     *  <p>The notice hangs on the CULL cards, not on a master that no longer exists: the biggest win in this
     *  space is not ours and never will be, and a player deserves to be told that without asking. */
    public static void init() {
        ModuleNotices.register("Particles", PerfMenu::sodiumNotice);
        ModuleNotices.register("Block Entities", PerfMenu::sodiumNotice);
    }

    /** The one thing a player deserves to be told without asking: the biggest win here is one mod away, and
     *  it is not us. Kept SHORT — a notice is a single-line 12px caption in a 220px sheet, and Label does not
     *  wrap. Why we do not cull entities ourselves is a paragraph, and it lives where a paragraph fits. */
    private static String sodiumNotice() {
        if (FabricLoader.getInstance().isModLoaded("sodium")) return null;
        return "Install Sodium for entity culling";
    }

    /** Skip tessellating particles the camera cannot see. */
    public static MenuContent.Module particles() {
        ClubConfig.Perf p = ClubConfig.get().perf;
        return new MenuContent.Module(
                "Particles",
                // "Behind you", not "off-screen": behind the camera is invisible BY CONSTRUCTION, and that is
                // the whole safety argument. "Off-screen" would imply a frustum test with edges to get wrong.
                "Stop building the particles behind you. Minecraft builds them anyway.",
                IconGlyph.PARTICLES,
                () -> p.cullParticles, v -> { p.cullParticles = v; save(); },
                () -> { p.cullParticles = true; save(); },
                List.of());
    }

    /** Skip block entities that are off-screen inside a section the frustum kept. */
    public static MenuContent.Module blockEntities() {
        ClubConfig.Perf p = ClubConfig.get().perf;
        return new MenuContent.Module(
                "Block Entities",
                // The sentence a player can actually picture. Vanilla frustum-culls the 16x16x16 section and
                // never the chest inside it — so the chest behind your head, in a section you can see the edge
                // of, is drawn every frame.
                "Stop drawing the chests you cannot see. Vanilla culls the chunk, never the chest in it.",
                IconGlyph.BLOCK_ENTITIES,
                () -> p.cullBlockEntities, v -> { p.cullBlockEntities = v; save(); },
                () -> { p.cullBlockEntities = true; save(); },
                List.of());
    }

    /** Cap the frame rate while the window is behind something else. */
    public static MenuContent.Module backgroundFps() {
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
    }

    private static void save() { ClubConfig.save(); }
}
