package com.club.ui.menu;

import com.club.config.ClubConfig;
import com.club.modules.animations.AnimationType;
import com.club.modules.screenstretch.StretchPreset;
import com.club.ui.Icon;
import com.club.ui.component.widget.BoolConsumer;
import com.club.ui.component.widget.FloatConsumer;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * Pure DATA description of the Club menu (Stage 6): categories → modules → settings, each bound to the
 * real {@link ClubConfig} through getter/setter/reset function refs. This layer builds NO widgets — the
 * {@link ClubMenuScreen} turns these descriptors into Toggle/Slider/Dropdown/Button and wires their
 * callbacks back to the setters here. Categories/names/descriptions and value ranges mirror the legacy
 * {@code gui.ClubScreen} exactly.
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
    public record Module(String name, String desc, Icon icon,
                         BooleanSupplier enabledGet, BoolConsumer enabledSet, Runnable reset,
                         List<Setting> settings, List<Tab> tabs) {
        /** Flat-settings module (no tabs). */
        public Module(String name, String desc, Icon icon,
                      BooleanSupplier enabledGet, BoolConsumer enabledSet, Runnable reset, List<Setting> settings) {
            this(name, desc, icon, enabledGet, enabledSet, reset, settings, List.of());
        }
        public boolean hasToggle() { return enabledGet != null; }
        public boolean enabled()   { return enabledGet != null && enabledGet.getAsBoolean(); }
        public void setEnabled(boolean v) { if (enabledSet != null) enabledSet.accept(v); }
        public boolean hasReset()  { return reset != null; }
        public boolean hasTabs()   { return tabs != null && !tabs.isEmpty(); }
    }

    public record Category(String name, Icon icon, List<Module> modules) {
        public int enabledCount() { int n = 0; for (Module m : modules) if (m.enabled()) n++; return n; }
    }

    // ---- tree ---------------------------------------------------------------

    /** Builds the legacy category tree from the live config. {@code openHudEditor} runs the HUD-editor action. */
    public static List<Category> build(Runnable openHudEditor) {
        ClubConfig c = ClubConfig.get();
        return List.of(
            new Category("Combat", Icon.COMBAT, List.of(animations(c))),
            new Category("Visuals", Icon.RENDER, List.of(
                screenStretch(c),
                flag("No Hurt Cam",     "Removes the red damage screen tilt.",       Icon.PLAYER, () -> c.noHurtCam,     v -> { c.noHurtCam = v; save(); }),
                flag("No Fire Overlay", "Hides the first-person flames while burning.", Icon.RENDER, () -> c.noFireOverlay, v -> { c.noFireOverlay = v; save(); }),
                flag("No Bobbing",      "Stops the view bobbing as you walk.",        Icon.PLAYER, () -> c.noBobbing,     v -> { c.noBobbing = v; save(); }))),
            new Category("Player", Icon.PLAYER, List.of(hands(c))),
            new Category("Misc", Icon.SETTINGS, List.of(
                hudEditor(openHudEditor),
                flag("Hide Vanilla Effects", "Hide the vanilla status-effect overlay.", Icon.HUD, () -> c.hud.hideVanillaEffects, v -> { c.hud.hideVanillaEffects = v; save(); })))
        );
    }

    /** A flag-only module: its master toggle IS the setting; no extra rows. Reset restores it to enabled. */
    private static Module flag(String name, String desc, Icon icon, BooleanSupplier get, BoolConsumer set) {
        return new Module(name, desc, icon, get, set, () -> set.accept(true), List.of());
    }

    private static Module animations(ClubConfig c) {
        AnimationType[] at = AnimationType.values();
        String[] labels = new String[at.length];
        for (int i = 0; i < at.length; i++) labels[i] = at[i].label();
        return new Module("Animations", "Custom first-person attack animation.", Icon.COMBAT,
            () -> c.animations.enabled, v -> { c.animations.enabled = v; save(); },
            () -> { c.animations.type = "CLASSIC"; c.animations.speed = 1f; c.animations.amplitude = 1f; c.animations.enabled = true; save(); },
            List.of(
                new DropdownSetting("Type", labels,
                        () -> AnimationType.fromName(c.animations.type).ordinal(),
                        i -> { c.animations.type = at[i].name(); save(); }),
                new SliderSetting("Speed", 0.5f, 2.0f, 0.01f, () -> c.animations.speed, v -> { c.animations.speed = v; save(); }),
                new SliderSetting("Amplitude", 0.5f, 1.5f, 0.01f, () -> c.animations.amplitude, v -> { c.animations.amplitude = v; save(); })));
    }

    private static Module screenStretch(ClubConfig c) {
        StretchPreset[] sp = StretchPreset.values();
        String[] labels = new String[sp.length];
        for (int i = 0; i < sp.length; i++) labels[i] = sp[i].label();
        return new Module("Screen Stretch", "Stretch the view to a target aspect ratio.", Icon.WORLD,
            () -> c.screenStretch.enabled, v -> { c.screenStretch.enabled = v; save(); },
            () -> { c.screenStretch.preset = "R16_9"; c.screenStretch.blackBars = true; c.screenStretch.enabled = true; save(); },
            List.of(
                new DropdownSetting("Preset", labels,
                        () -> StretchPreset.fromName(c.screenStretch.preset).ordinal(),
                        i -> { c.screenStretch.preset = sp[i].name(); save(); }),
                new ToggleSetting("Black Bars", () -> c.screenStretch.blackBars, v -> { c.screenStretch.blackBars = v; save(); })));
    }

    private static Module hands(ClubConfig c) {
        ClubConfig.HandSide rh = c.hands.rightHand, lh = c.hands.leftHand;
        List<Setting> right = List.of(
                new SliderSetting("Scale",    0.5f, 2.0f, 0.01f, () -> rh.scale,   v -> { rh.scale = v;   save(); }),
                new SliderSetting("Offset X", -1.0f, 1.0f, 0.01f, () -> rh.offsetX, v -> { rh.offsetX = v; save(); }),
                new SliderSetting("Offset Y", -1.0f, 1.0f, 0.01f, () -> rh.offsetY, v -> { rh.offsetY = v; save(); }),
                new SliderSetting("Offset Z", -1.0f, 1.0f, 0.01f, () -> rh.offsetZ, v -> { rh.offsetZ = v; save(); }));
        List<Setting> left = List.of(
                new SliderSetting("Scale",    0.5f, 2.0f, 0.01f, () -> lh.scale,   v -> { lh.scale = v;   save(); }),
                new SliderSetting("Offset X", -1.0f, 1.0f, 0.01f, () -> lh.offsetX, v -> { lh.offsetX = v; save(); }),
                new SliderSetting("Offset Y", -1.0f, 1.0f, 0.01f, () -> lh.offsetY, v -> { lh.offsetY = v; save(); }),
                new SliderSetting("Offset Z", -1.0f, 1.0f, 0.01f, () -> lh.offsetZ, v -> { lh.offsetZ = v; save(); }));
        return new Module("Hands", "Reposition and scale the first-person hands.", Icon.PLAYER,
            () -> c.hands.enabled, v -> { c.hands.enabled = v; save(); },
            () -> { c.hands.enabled = true;
                    rh.scale = 1f; rh.offsetX = 0f; rh.offsetY = 0f; rh.offsetZ = 0f;
                    lh.scale = 1f; lh.offsetX = 0f; lh.offsetY = 0f; lh.offsetZ = 0f; save(); },
            List.of(), List.of(new Tab("Right", right), new Tab("Left", left)));
    }

    /** Action-only module: no master toggle, no reset — a single button that opens the HUD editor. */
    private static Module hudEditor(Runnable openHudEditor) {
        return new Module("HUD Editor", "Position and configure your HUD elements.", Icon.HUD,
            null, null, null,
            List.of(new ActionSetting("Open Editor", openHudEditor)));
    }
}
