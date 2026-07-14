package com.club.ui.menu;

import com.club.config.ClubConfig;
import com.club.modules.animations.AnimationType;
import com.club.modules.screenstretch.StretchPreset;
import com.club.ui.IconGlyph;
import com.club.ui.component.widget.BoolConsumer;
import com.club.ui.component.widget.FloatConsumer;
import net.minecraft.client.MinecraftClient;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * Pure DATA description of the Club menu (Stage 6): categories → modules → settings, each bound to the
 * real {@link ClubConfig} through getter/setter/reset function refs. This layer builds NO widgets — the
 * {@link ClubMenuScreen} turns these descriptors into Toggle/Slider/option-list/Button rows and wires their
 * callbacks back to the setters here. Categories/names/descriptions and value ranges mirror the original
 * legacy menu exactly.
 */
public final class MenuContent {
    private MenuContent() {}

    private static void save() { ClubConfig.save(); }

    /** Float getter (java.util.function has no FloatSupplier). */
    @FunctionalInterface public interface FloatGet { float get(); }

    // ---- descriptor types (data only, no UI) --------------------------------

    /** A settings row descriptor. The screen builds the matching widget from the concrete subtype. */
    public sealed interface Setting permits SliderSetting, ToggleSetting, CheckSetting, DropdownSetting, ActionSetting {
        String label();
    }
    public record SliderSetting(String label, float min, float max, float step, FloatGet get, FloatConsumer set) implements Setting {}
    public record ToggleSetting(String label, BooleanSupplier get, BoolConsumer set) implements Setting {}
    public record CheckSetting(String label, BooleanSupplier get, BoolConsumer set) implements Setting {}
    public record DropdownSetting(String label, String[] options, IntSupplier get, IntConsumer set) implements Setting {}
    public record ActionSetting(String label, Runnable action) implements Setting {}

    /** A named group of settings shown behind a segment selector (e.g. Hands → Right / Left). */
    public record Tab(String label, List<Setting> settings) {}

    /**
     * A module. {@code enabledGet == null} means an action-only module (no master toggle, e.g. HUD Editor).
     * A module presents its settings either flat ({@code settings}) or split into {@code tabs} (never both).
     */
    public record Module(String name, String desc, IconGlyph icon,
                         BooleanSupplier enabledGet, BoolConsumer enabledSet, Runnable reset,
                         List<Setting> settings, List<Tab> tabs) {
        /** Flat-settings module (no tabs). */
        public Module(String name, String desc, IconGlyph icon,
                      BooleanSupplier enabledGet, BoolConsumer enabledSet, Runnable reset, List<Setting> settings) {
            this(name, desc, icon, enabledGet, enabledSet, reset, settings, List.of());
        }
        public boolean hasToggle() { return enabledGet != null; }
        public boolean enabled()   { return enabledGet != null && enabledGet.getAsBoolean(); }
        public void setEnabled(boolean v) { if (enabledSet != null) enabledSet.accept(v); }
        public boolean hasReset()  { return reset != null; }
        public boolean hasTabs()   { return tabs != null && !tabs.isEmpty(); }
    }

    public record Category(String name, IconGlyph icon, List<Module> modules) {
        public int enabledCount() { int n = 0; for (Module m : modules) if (m.enabled()) n++; return n; }
    }

    // ---- honesty ------------------------------------------------------------

    /**
     * Why this module is doing nothing right now, in the player's words — or null when the card means what
     * it says. Shown as a caption at the top of the popover (Stage 62).
     *
     * <p>Two modules could sit there lit, accent-tinted, indistinguishable from a module that is actually
     * working, while being a deliberate no-op — and the mod never said a word:</p>
     * <ul>
     *   <li><b>Toggle Sprint</b> stands down entirely when vanilla's own "Sprint: Toggle" is on (the sticky
     *       binding flips per press, so forcing it every tick would strobe). Correct — but the card still
     *       read ON, and the player's conclusion is "this mod's sprint toggle is broken".</li>
     *   <li><b>Screen Stretch</b> ships enabled with preset AUTO, and AUTO means "do not touch the
     *       projection" — the right default (a fresh install must not warp a non-16:9 monitor), but a lit
     *       card promising a stretch that is not happening.</li>
     * </ul>
     * A card that lies about one thing makes the player doubt the other eleven.
     */
    public static String notice(String moduleName) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.options == null) return null;
        ClubConfig c = ClubConfig.get();
        return switch (moduleName) {
            case "Toggle Sprint" -> c.toggleSprint.enabled && mc.options.getSprintToggled().getValue()
                    ? "Idle — vanilla Sprint: Toggle is on" : null;
            case "Screen Stretch" -> c.screenStretch.enabled
                    && !com.club.modules.screenstretch.ScreenStretchModule.isActive()
                    ? "Auto — the world is left untouched" : null;
            // Anything else answers for ITSELF, from its own package (com.club.modules.ModuleNotices). A switch
            // in this file was fine while the menu owned every module; it is not fine when parallel workstreams
            // each need a line in it, in a file that is otherwise frozen.
            default -> com.club.modules.ModuleNotices.get(moduleName);
        };
    }

    // ---- tree ---------------------------------------------------------------

    /** Builds the legacy category tree from the live config. {@code openHudEditor} runs the HUD-editor action. */
    public static List<Category> build(Runnable openHudEditor) {
        ClubConfig c = ClubConfig.get();
        return List.of(
            new Category("Combat", IconGlyph.COMBAT, List.of(animations(c))),
            new Category("Visuals", IconGlyph.VISUALS, List.of(
                zoom(c),
                screenStretch(c),
                fullbright(c),
                flag("No Hurt Cam",     "Removes the red damage screen tilt.",       IconGlyph.NO_HURT_CAM, () -> c.noHurtCam,     v -> { c.noHurtCam = v; save(); }),
                flag("No Fire Overlay", "Hides the first-person flames while burning.", IconGlyph.NO_FIRE_OVERLAY, () -> c.noFireOverlay, v -> { c.noFireOverlay = v; save(); }),
                flag("No Bobbing",      "Stops the view bobbing as you walk.",        IconGlyph.NO_BOBBING, () -> c.noBobbing,     v -> { c.noBobbing = v; save(); }))),
            new Category("Player", IconGlyph.PLAYER, List.of(
                hands(c),
                toggleSprint(c),
                flag("Freelook", "Hold the freelook key to swing the camera freely.", IconGlyph.FREELOOK,
                        () -> c.freelook.enabled, v -> { c.freelook.enabled = v; save(); }))),
            new Category("Misc", IconGlyph.MISC, List.of(
                hudEditor(openHudEditor),
                // [SEAM:cards] New module cards go here, one line each, calling a factory in the module's own
                // package. This anchor must survive any refactor of this file (see docs/NEXT-PLAN.md).
                com.club.modules.itemscroll.ItemScrollMenu.card(),
                com.club.modules.perf.PerfMenu.card(),
                flag("Hide Effects", "Hide the vanilla status-effect overlay.", IconGlyph.HIDE_EFFECTS, () -> c.hud.hideVanillaEffects, v -> { c.hud.hideVanillaEffects = v; save(); })))
        );
    }

    /** A flag-only module: its master toggle IS the setting; no extra rows. Reset restores it to enabled. */
    private static Module flag(String name, String desc, IconGlyph icon, BooleanSupplier get, BoolConsumer set) {
        return new Module(name, desc, icon, get, set, () -> set.accept(true), List.of());
    }

    private static Module animations(ClubConfig c) {
        AnimationType[] at = AnimationType.values();
        String[] labels = new String[at.length];
        for (int i = 0; i < at.length; i++) labels[i] = at[i].label();
        return new Module("Animations", "Custom first-person attack animation.", IconGlyph.ANIMATIONS,
            () -> c.animations.enabled, v -> { c.animations.enabled = v; save(); },
            () -> { c.animations.type = "CLASSIC"; c.animations.speed = 1f; c.animations.amplitude = 1f; c.animations.enabled = true; save(); },
            List.of(
                new DropdownSetting("Type", labels,
                        () -> AnimationType.fromName(c.animations.type).ordinal(),
                        i -> { c.animations.type = at[i].name(); save(); }),
                new SliderSetting("Speed", 0.5f, 2.0f, 0.01f, () -> c.animations.speed, v -> c.animations.speed = v),
                new SliderSetting("Amplitude", 0.5f, 1.5f, 0.01f, () -> c.animations.amplitude, v -> c.animations.amplitude = v)));
    }

    private static Module toggleSprint(ClubConfig c) {
        return new Module("Toggle Sprint", "Sprint automatically — no key holding.", IconGlyph.TOGGLE_SPRINT,
            () -> c.toggleSprint.enabled, v -> { c.toggleSprint.enabled = v; save(); },
            () -> { c.toggleSprint.enabled = true; c.hud.sprint = true; save(); },
            List.of(new ToggleSetting("Indicator", () -> c.hud.sprint, v -> { c.hud.sprint = v; save(); })));
    }

    /** Fullbright is a flag module whose state must ALSO mirror into the module's static (the gamma
     *  mixin gates on it) — and unlike the No-* flags its reset returns to OFF (surprise brightness
     *  isn't a default). */
    private static Module fullbright(ClubConfig c) {
        BoolConsumer set = v -> { c.fullbright = v; com.club.modules.fullbright.FullbrightModule.set(v); save(); };
        return new Module("Fullbright", "See in the dark — maximum brightness.", IconGlyph.FULLBRIGHT,
            () -> c.fullbright, set, () -> set.accept(false), List.of());
    }

    private static Module zoom(ClubConfig c) {
        return new Module("Zoom", "Hold the zoom key to magnify the view.", IconGlyph.ZOOM,
            () -> c.zoom.enabled, v -> { c.zoom.enabled = v; save(); },
            () -> { c.zoom.factor = 4f; c.zoom.smoothness = 0.5f; c.zoom.enabled = true; save(); },
            List.of(
                new SliderSetting("Strength", 2f, 8f, 0.5f, () -> c.zoom.factor, v -> c.zoom.factor = v),
                new SliderSetting("Smoothness", 0f, 1f, 0.05f, () -> c.zoom.smoothness, v -> c.zoom.smoothness = v)));
    }

    private static Module screenStretch(ClubConfig c) {
        StretchPreset[] sp = StretchPreset.values();
        String[] labels = new String[sp.length];
        for (int i = 0; i < sp.length; i++) labels[i] = sp[i].label();
        return new Module("Screen Stretch", "Stretch the view to a target aspect ratio.", IconGlyph.SCREEN_STRETCH,
            () -> c.screenStretch.enabled, v -> { c.screenStretch.enabled = v; save(); },
            // Reset goes back to AUTO (no stretch) — resetting must never hand a non-16:9 player a
            // warped world, which "R16_9" did (Stage 59 audit).
            () -> { c.screenStretch.preset = "AUTO"; c.screenStretch.blackBars = true; c.screenStretch.enabled = true; save(); },
            List.of(
                new DropdownSetting("Preset", labels,
                        () -> StretchPreset.fromName(c.screenStretch.preset).ordinal(),
                        i -> { c.screenStretch.preset = sp[i].name(); save(); }),
                new ToggleSetting("Black Bars", () -> c.screenStretch.blackBars, v -> { c.screenStretch.blackBars = v; save(); })));
    }

    private static Module hands(ClubConfig c) {
        ClubConfig.HandSide rh = c.hands.rightHand, lh = c.hands.leftHand;
        List<Setting> right = List.of(
                new SliderSetting("Scale",    0.5f, 2.0f, 0.01f, () -> rh.scale,   v -> rh.scale = v),
                new SliderSetting("Offset X", -1.0f, 1.0f, 0.01f, () -> rh.offsetX, v -> rh.offsetX = v),
                new SliderSetting("Offset Y", -1.0f, 1.0f, 0.01f, () -> rh.offsetY, v -> rh.offsetY = v),
                new SliderSetting("Offset Z", -1.0f, 1.0f, 0.01f, () -> rh.offsetZ, v -> rh.offsetZ = v));
        List<Setting> left = List.of(
                new SliderSetting("Scale",    0.5f, 2.0f, 0.01f, () -> lh.scale,   v -> lh.scale = v),
                new SliderSetting("Offset X", -1.0f, 1.0f, 0.01f, () -> lh.offsetX, v -> lh.offsetX = v),
                new SliderSetting("Offset Y", -1.0f, 1.0f, 0.01f, () -> lh.offsetY, v -> lh.offsetY = v),
                new SliderSetting("Offset Z", -1.0f, 1.0f, 0.01f, () -> lh.offsetZ, v -> lh.offsetZ = v));
        return new Module("Hands", "Reposition and scale the first-person hands.", IconGlyph.HANDS,
            () -> c.hands.enabled, v -> { c.hands.enabled = v; save(); },
            () -> { c.hands.enabled = true;
                    rh.scale = 1f; rh.offsetX = 0f; rh.offsetY = 0f; rh.offsetZ = 0f;
                    lh.scale = 1f; lh.offsetX = 0f; lh.offsetY = 0f; lh.offsetZ = 0f; save(); },
            List.of(), List.of(new Tab("Right", right), new Tab("Left", left)));
    }

    /** Action-only module: no master toggle, no reset — a single button that opens the HUD editor. */
    private static Module hudEditor(Runnable openHudEditor) {
        return new Module("HUD Editor", "Position and configure your HUD elements.", IconGlyph.HUD_EDITOR,
            null, null, null,
            List.of(new ActionSetting("Open Editor", openHudEditor)));
    }
}
