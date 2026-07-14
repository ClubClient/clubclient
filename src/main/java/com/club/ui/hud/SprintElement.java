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
 * Toggle Sprint indicator — a QUIET chip in the V4 language: the capsule ground + one Medium 12
 * label. It answers exactly ONE question: <b>is autosprint doing its job right now?</b>
 *
 * <p><b>Not "are the legs moving" (owner, v0.1.3).</b> The chip used to brighten on
 * {@code player.isSprinting()}. That lit for a player sprinting BY HAND while the module stood
 * down — a light that says nothing about the module it is named after. And it lit brightest in the
 * one case the player most needs to catch: vanilla's own "Sprint: Toggle" is on, so we deliberately
 * do nothing (see {@link ToggleSprintModule}), the player sprints under their own power, and the
 * chip cheerfully reports success. The chip now tracks {@link ToggleSprintModule#active} — the
 * module is on AND is not standing down — which is the same signal the module card's notice uses.</p>
 *
 * <p><b>THREE STATES, NOT TWO.</b> A chip that is always drawn once the HUD element is enabled would
 * hang a permanent grey "Sprint" on the screen of every player who never wanted this module — and
 * {@code hud.sprint} defaults to true, so that is EVERY new player. A chip that vanishes whenever
 * autosprint is not working cannot say "off" at all. Both are wrong, so the element answers a
 * different question in each case:</p>
 * <ul>
 *   <li><b>Module off entirely</b> → no chip. You are not using this feature; the HUD owes you nothing.</li>
 *   <li><b>Module on, standing down</b> (vanilla's own "Sprint: Toggle" is on, so we deliberately do
 *       nothing) → chip drawn, label FAINT. This is the state the old design lied about: it reported
 *       success while the player sprinted under their own power.</li>
 *   <li><b>Module on and working</b> → chip drawn, label BRIGHT.</li>
 * </ul>
 *
 * <p>Geometry and ground never jump between the last two — only the LABEL TONE changes. The chip
 * reports the WHAT; the WHY of a stand-down lives on the module card's notice line, which is the one
 * place with room for a sentence.</p>
 *
 * <p><b>textHi ↔ textFaint, not textHi ↔ textMuted.</b> Two adjacent steps of the text ramp were too
 * close to read against a moving world (owner). textFaint (#767E8E) is the same value the module card
 * paints an OFF name in, so the HUD and the menu say "off" with the same tone. The label is never
 * coloured: a category tint may not touch text (CategoryAccents, v2.5 rule) and this chip carries no
 * colour at all — it is auxiliary state, one step above the FPS whisper, below the content chips.</p>
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
     * In-world the chip exists only while the MODULE is switched on — not while it is merely WORKING.
     * That distinction is the whole point (see the class javadoc): a player who has never touched Toggle
     * Sprint gets nothing, while a player who turned it on and is being quietly overruled by vanilla's own
     * "Sprint: Toggle" gets a faint chip that tells them so. The editor always shows the sample.
     */
    @Override public boolean hasContent(MinecraftClient mc) {
        return !live(mc) || ClubConfig.get().toggleSprint.enabled;
    }

    @Override public int[] contentSize(MinecraftClient mc, boolean live) {
        float w = 2 * PAD_X + Ui.text().width(LABEL, Weight.MEDIUM, TEXT_SIZE);
        return new int[]{ Math.round(w), CONTENT_H };
    }

    @Override public void paint(UiContext ctx, MinecraftClient mc, float ox, float oy, float s, boolean live) {
        float cw = 2 * PAD_X + Ui.text().width(LABEL, Weight.MEDIUM, TEXT_SIZE);
        HudPaint.chip(ctx, ox, oy, cw * s, CONTENT_H * s, HudPaint.CHIP_RAD * s, alpha);
        // Sample data (editor placeholder / probe) shows the WORKING state — a sample must show the
        // element at full strength, never at its dimmest.
        boolean working = !live || ToggleSprintModule.active(mc);
        int col = Color.scaleAlpha(working ? Tokens.palette().textHi() : Tokens.palette().textFaint(), alpha);
        float lh = Ui.text().lineHeight(Weight.MEDIUM, TEXT_SIZE);
        ctx.text().draw(LABEL, ox + PAD_X * s, oy + (CONTENT_H - lh) * 0.5f * s,
                TextStyle.of(Weight.MEDIUM, TEXT_SIZE * s, col).effect(HudPaint.textShadow(alpha)));
    }
}
