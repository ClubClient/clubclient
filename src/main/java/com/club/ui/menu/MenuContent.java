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

    /**
     * The cards of one category, minus any that are absent right now.
     *
     * <p>A factory returns null when its module has no business existing here at all — today that is a
     * module the SERVER forbids by rule ({@code com.club.policy.ServerPolicy}), where Club stands down and
     * this client is, for that session, a build without the module. Null is not "off": an off module keeps
     * its card and its toggle. Null means there is nothing to show and nothing to explain.</p>
     *
     * <p>Filtering here rather than in the factories keeps the [SEAM:cards] contract intact — one line per
     * card, calling the module's own package, no logic in this file.</p>
     */
    private static List<Module> cards(Module... modules) {
        return java.util.Arrays.stream(modules).filter(java.util.Objects::nonNull).toList();
    }

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
            new Category("Combat", IconGlyph.COMBAT, List.of(animations(c), lowShield(c))),
            new Category("Visuals", IconGlyph.VISUALS, List.of(
                zoom(c),
                screenStretch(c),
                fullbright(c),
                flag("No Hurt Cam",     "Removes the red damage screen tilt.",       IconGlyph.NO_HURT_CAM, () -> c.noHurtCam,     v -> { c.noHurtCam = v; save(); }),
                flag("No Fire Overlay", "Hides the first-person flames while burning.", IconGlyph.NO_FIRE_OVERLAY, () -> c.noFireOverlay, v -> { c.noFireOverlay = v; save(); }),
                flag("No Bobbing",      "Stops the view bobbing as you walk.",        IconGlyph.NO_BOBBING, () -> c.noBobbing,     v -> { c.noBobbing = v; save(); }))),
            // cards(), not List.of(): Freelook's factory returns null where the server forbids it, and
            // List.of() throws on a null element — which would take the whole menu down with it.
            new Category("Player", IconGlyph.PLAYER, cards(
                hands(c),
                toggleSprint(c),
                // A hold module, so NO on/off toggle — see zoom(c) for the reasoning. The key is the switch.
                com.club.modules.freelook.FreelookMenu.card())),   // null where the server forbids it
            // Particles is the one category whose CONTENT is not cards: ClubMenuScreen renders a two-pane for
            // it (groups on the left, per-particle On/Off on the right) — see com.club.ui.menu.ParticlesPane.
            // The empty module list is deliberate; the rail entry, its gold accent and its icon are all this
            // file needs to give it. Everything on by default; nothing is protected (owner).
            new Category("Particles", IconGlyph.PARTICLES, List.of()),
            // PERFORMANCE STOPPED BEING A CATEGORY (owner). It briefly held three cards, but two of them —
            // Particles and Block Entities — were culls that are invisible BY CONSTRUCTION and on by default:
            // never dials a player should be tuning, so they are baked in now, with a config-only kill switch
            // (perf.cullParticles / perf.cullBlockEntities) for the one thing a toggle was ever for — ruling
            // the mod out of a suspected rendering bug in one edit. The third, Background FPS, is a REAL choice
            // (battery / fans while alt-tabbed), so it survives as a lone card in Misc below.
            // cards(), not List.of(): a factory may return null when the server forbids its module outright
            // (see cards() above). List.of() throws on a null element, which would take the whole menu down.
            new Category("Misc", IconGlyph.MISC, cards(
                hudEditor(openHudEditor),
                // [SEAM:cards] New module cards go here, one line each, calling a factory in the module's own
                // package. This anchor must survive any refactor of this file (see docs/NEXT-PLAN.md).
                com.club.modules.itemscroll.ItemScrollMenu.card(),   // null where the server forbids it
                com.club.modules.perf.PerfMenu.backgroundFps(),
                flag("Shulker Tooltip", "Hover a shulker box to see what's inside it.", IconGlyph.SHULKER_TOOLTIP, () -> c.shulkerTooltip, v -> { c.shulkerTooltip = v; save(); }),
                flag("Hide Effects", "Hide Minecraft's own potion icons.", IconGlyph.HIDE_EFFECTS, () -> c.hud.hideVanillaEffects, v -> { c.hud.hideVanillaEffects = v; save(); })))
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
        // Card descriptions are written for a PLAYER, not for us (owner, v0.1.3 item 6: "всё меню написано на
        // каком-то логичном, но НЕЧЕЛОВЕЧЕСКОМ английском"). "Custom first-person attack animation" is a
        // spec line, not a sentence: it names the feature and says nothing about what it does for you.
        return new Module("Animations", "Replace the vanilla swing with one of your own.", IconGlyph.ANIMATIONS,
            () -> c.animations.enabled, v -> { c.animations.enabled = v; save(); },
            () -> { c.animations.type = "CLASSIC"; c.animations.speed = 1f; c.animations.amplitude = 1f; c.animations.enabled = true; save(); },
            List.of(
                new DropdownSetting("Type", labels,
                        () -> AnimationType.fromName(c.animations.type).ordinal(),
                        i -> { c.animations.type = at[i].name(); save(); }),
                new SliderSetting("Speed", 0.5f, 2.0f, 0.01f, () -> c.animations.speed, v -> c.animations.speed = v),
                new SliderSetting("Amplitude", 0.5f, 1.5f, 0.01f, () -> c.animations.amplitude, v -> c.animations.amplitude = v)));
    }

    /** Low Shield — a master toggle plus how far to drop it. The slider exists because "how low" is taste,
     *  and 0.45 is only a starting point; Reset returns to on-at-0.45 (the shipped default), not to off. */
    private static Module lowShield(ClubConfig c) {
        return new Module("Low Shield", "Lower the shield so it stops covering your view.", IconGlyph.LOW_SHIELD,
            () -> c.lowShield, v -> { c.lowShield = v; save(); },
            () -> { c.lowShield = true; c.lowShieldAmount = 0.45f; save(); },
            List.of(
                new SliderSetting("Amount", 0.1f, 1.0f, 0.01f, () -> c.lowShieldAmount, v -> c.lowShieldAmount = v)));
    }

    /**
     * TWO SWITCHES OWNED ONE PIXEL, AND THEY DID NOT EVEN AGREE ON ITS NAME (owner, v0.1.3 item 12).
     *
     * <p>This card carried a row called "Indicator" that wrote {@code hud.sprint} — the SAME boolean the HUD
     * editor exposes on the Sprint element as "Enabled". One field, two screens, two words for it, and no way
     * for a player to know they were the same thing. "Разве настройка такого рода не должна быть в HUD?" —
     * yes. A HUD element's visibility belongs to the HUD editor and nowhere else. The row is gone, and with it
     * the reset that quietly reached across and rewrote the HUD's own setting.
     *
     * <p>The card now has exactly what it is: a master toggle for autosprint, and nothing else. What the chip
     * on screen does with that is {@link com.club.ui.hud.SprintElement}'s business.
     */
    private static Module toggleSprint(ClubConfig c) {
        return new Module("Toggle Sprint", "Sprint automatically — no key holding.", IconGlyph.TOGGLE_SPRINT,
            () -> c.toggleSprint.enabled, v -> { c.toggleSprint.enabled = v; save(); },
            () -> { c.toggleSprint.enabled = true; save(); },
            List.of());
    }

    /** Fullbright is a flag module whose state must ALSO mirror into the module's static (the gamma
     *  mixin gates on it) — and unlike the No-* flags its reset returns to OFF (surprise brightness
     *  isn't a default). */
    private static Module fullbright(ClubConfig c) {
        BoolConsumer set = v -> { c.fullbright = v; com.club.modules.fullbright.FullbrightModule.set(v); save(); };
        return new Module("Fullbright", "See in the dark — maximum brightness.", IconGlyph.FULLBRIGHT,
            () -> c.fullbright, set, () -> set.accept(false), List.of());
    }

    /**
     * A HOLD MODULE HAS NO ON/OFF SWITCH, AND NEVER SHOULD HAVE HAD ONE (owner, v0.1.3):
     * "зум и фрилук — это клавиши-функции… когда ты их не юзаешь, они и так выкл. Что за бред."
     *
     * <p>He is right, and the switch was worse than redundant — it was a second, invisible way to break the
     * feature. A zoom that is "off" is a zoom you are not holding. The KEY is the on/off: bound and held, it
     * zooms; unbound, it does nothing, and unbinding it is how you turn the module off (the Hold key row is
     * right there, and Reset puts C back).
     *
     * <p>So {@code enabledGet}/{@code enabledSet} are null — the card carries no toggle, and
     * {@link ZoomModule} no longer consults {@code zoom.enabled} at all. The field stays in the config only so
     * an old file loads; nothing reads it, and nothing writes it. A flag that no code obeys must not sit in a
     * menu pretending it does.
     */
    private static Module zoom(ClubConfig c) {
        return new Module("Zoom", "Hold the zoom key to magnify the view.", IconGlyph.ZOOM,
            null, null,
            () -> { c.zoom.factor = 4f; c.zoom.smoothness = 0.5f; save(); },
            List.of(
                new SliderSetting("Strength", 2f, 8f, 0.5f, () -> c.zoom.factor, v -> c.zoom.factor = v),
                new SliderSetting("Smoothness", 0f, 1f, 0.05f, () -> c.zoom.smoothness, v -> c.zoom.smoothness = v)));
    }

    private static Module screenStretch(ClubConfig c) {
        StretchPreset[] sp = StretchPreset.values();
        String[] labels = new String[sp.length];
        for (int i = 0; i < sp.length; i++) labels[i] = sp[i].label();
        return new Module("Screen Stretch", "Play at a different aspect ratio than your monitor has.", IconGlyph.SCREEN_STRETCH,
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
        return new Module("HUD Editor", "Drag the HUD wherever you want it.", IconGlyph.HUD_EDITOR,
            null, null, null,
            List.of(new ActionSetting("Open Editor", openHudEditor)));
    }
}
