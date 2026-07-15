package com.club.ui.hud;

import com.club.config.ClubConfig;
import com.club.modules.togglesprint.ToggleSprintModule;
import com.club.ui.Color;
import com.club.ui.Ui;
import com.club.ui.UiContext;
import com.club.ui.text.TextStyle;
import com.club.ui.text.Weight;
import com.club.ui.theme.Tokens;
import net.minecraft.client.MinecraftClient;

/**
 * Toggle Sprint indicator — a QUIET chip in the V4 language: the capsule ground + one Medium 12 label.
 *
 * <p><b>PRESENCE and TONE answer two different questions, and that is the whole design.</b> The owner
 * settled it in two sentences, and the second one only became safe because of the first:</p>
 * <ul>
 *   <li><b>Presence</b> — the chip is on screen only while autosprint is actually DOING something:
 *       {@link ToggleSprintModule#active}, i.e. the module is on AND is not standing down. Module off,
 *       or overruled by vanilla's own "Sprint: Toggle"? No chip at all. "Если модуль выключен —
 *       спринта на экране вообще нет."</li>
 *   <li><b>Tone</b> — given that the chip is there at all, the only thing left to report is whether the
 *       player is MOVING under it. Bright while sprinting, faint while armed and idle. "Если он включён
 *       и не бежит — горит тусклым."</li>
 * </ul>
 *
 * <p><b>Why tone may track the legs now, when it could not before.</b> The owner's own objection to a
 * legs-driven light was that with the module OFF it would light up on a hand-held sprint and mean
 * nothing. That objection dies with the presence rule: when the module is off there is no chip to
 * light. What remains is a chip whose existence means "autosprint is armed" and whose brightness means
 * "and it is carrying you right now" — two facts, two channels, neither one lying.</p>
 *
 * <p><b>The bug was never the logic — it was the contrast.</b> The old code already brightened on
 * {@code isSprinting()}; the owner still read the chip as tracking the module, because the two tones it
 * used ({@code textMuted} #A6ADBB ↔ {@code textHi} #F4F6FA) are ADJACENT steps of the ramp and simply are
 * not legible against a world that is moving. The idle tone is {@code textDesc} (#767E8E) now — two steps
 * down, and the same tone the module card paints an OFF name in, so the HUD and the menu say "idle" the
 * same way. Not {@code textFaint} (#5A6273): that is the quietest step in the ramp, drawn for footnotes on
 * a dark PANEL, and over a bright world it stops being quiet and starts being invisible.</p>
 *
 * <p>The label carries no colour at all: a category tint may not touch text (CategoryAccents, v2.5 rule),
 * and this is auxiliary state — one step above the FPS whisper, below the content chips.</p>
 *
 * <p>The stand-down case is not left silent just because the chip is gone: the module card carries the
 * notice that says WHY it is idle. That is the surface with room for a sentence; a 20px capsule is not.</p>
 *
 * <p><b>Why a screen being open does not mute the chip</b>, even though {@code tick()} stops forcing
 * the key there: the player would watch it strobe every time they opened chat or the inventory, for a
 * suspension that is not a stand-down, that ends the instant the screen closes, and during which there
 * is nothing to sprint for anyway. The chip answers for the MODULE, not for this frame's key press.</p>
 */
public final class SprintElement extends HudElement {
    private static final float TEXT_SIZE = 12f;
    private static final float PAD_X = 8f;
    private static final int CONTENT_H = 20;
    private static final String LABEL = "Sprint";

    public SprintElement() { super("sprint"); }
    @Override public String displayName() { return "Sprint"; }

    private ClubConfig.Hud h() { return ClubConfig.get().hud; }
    @Override public int   cfgX() { return h().sprintX; }
    @Override public int   cfgY() { return h().sprintY; }
    @Override public void  cfgX(int v) { h().sprintX = v; }
    @Override public void  cfgY(int v) { h().sprintY = v; }
    @Override public float cfgScale() { return h().sprintScale; }
    /** The HUD editor's "Enabled" row is the SINGLE owner of this flag (the module popover's duplicate
     *  "Indicator" toggle wrote to the same field from a second place). */
    @Override public boolean cfgEnabled() { return h().sprint; }

    // V4: the chip is the element — capsule painted in paint(), no shared panel.
    @Override protected float panelPadX() { return 0f; }
    @Override protected float panelPadY() { return 0f; }

    /** Default: the mod's top-left stack, one line below the FPS whisper — clear of the vanilla chat
     *  and hotbar (a bottom-left default sat on top of the chat history). Movable in the editor. */
    @Override public int autoX(MinecraftClient mc) { return mc != null ? 8 : -1; }
    @Override public int autoY(MinecraftClient mc) { return mc != null ? 148 : -1; }

    /**
     * In-world the chip exists only while autosprint is actually DOING something — the module is on and is
     * not standing down. Owner: "если модуль выключен — спринта на экране вообще нет".
     *
     * <p>{@link ToggleSprintModule#active} is the right gate rather than the raw {@code enabled} flag,
     * because a module that is switched on but overruled by vanilla's own "Sprint: Toggle" is not doing
     * anything, and a chip for a feature that is doing nothing is the lie v0.1.2 shipped a fix for. That
     * case is not left silent: the module card carries the notice that says WHY it is idle, which is the one
     * surface with room for a sentence. The editor always shows the sample.
     */
    @Override public boolean hasContent(MinecraftClient mc) {
        return !live(mc) || ToggleSprintModule.active(mc);
    }

    @Override public int[] contentSize(MinecraftClient mc, boolean live) {
        float w = 2 * PAD_X + Ui.text().width(LABEL, Weight.MEDIUM, TEXT_SIZE);
        return new int[]{ Math.round(w), CONTENT_H };
    }

    @Override public void paint(UiContext ctx, MinecraftClient mc, float ox, float oy, float s, boolean live) {
        float cw = 2 * PAD_X + Ui.text().width(LABEL, Weight.MEDIUM, TEXT_SIZE);
        // A DENSER GROUND than the shared V4 chip (owner, v0.1.3 #8: "кнопку спринта вообще не видно").
        // The other chips carry bright content — 18px HP, armour icons — that reads over anything. This one
        // is a single quiet word, and on the shared bg2-at-55% wash it vanished over a bright PvP world (on
        // a dark scene it was fine, which is why it flickered in and out). bg1 (#090E16) at 82% is a real
        // dark pill: the word sits on it legibly over grass in daylight, without becoming a loud panel.
        ctx.renderer().roundedRect(ox, oy, cw * s, CONTENT_H * s, HudPaint.CHIP_RAD * s,
                Color.scaleAlpha(Tokens.surface().bg1(), 0.82f * alpha));
        // The chip is only on screen while autosprint is working (see hasContent), so the one thing left to
        // report is whether the player is actually MOVING under it. Bright = running. Idle = one clear step
        // down (textMuted, NOT the near-invisible textDesc): the state must READ, not merely differ. The
        // editor's sample shows the bright state — a sample must show the element at full strength.
        boolean running = !live || (mc != null && mc.player != null && mc.player.isSprinting());
        int col = Color.scaleAlpha(running ? Tokens.palette().textHi() : Tokens.palette().textMuted(), alpha);
        float lh = Ui.text().lineHeight(Weight.MEDIUM, TEXT_SIZE);
        ctx.text().draw(LABEL, ox + PAD_X * s, oy + (CONTENT_H - lh) * 0.5f * s,
                TextStyle.of(Weight.MEDIUM, TEXT_SIZE * s, col).effect(HudPaint.textShadow(alpha)));
    }
}
