package club.tools;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Renders the icon set as standalone PNGs for use as Discord custom emoji — one file per icon, on a
 * TRANSPARENT background, tinted with the brand accent.
 *
 * <p>Why this exists rather than a screenshot of the atlas: an emoji is composited over whatever colour
 * the reader's Discord theme is, so it needs a real alpha channel, and it is rasterised once at a fixed
 * size rather than sampled by a shader. The alpha math below is the SAME as the runtime's
 * ({@code clamp((sd-0.5)*screenPxRange+0.5)}) so the shape a reader sees in chat is the shape the game
 * draws — an emoji that does not match its own icon is worse than no emoji.
 *
 * <p>128px is Discord's rendering size for a custom emoji; the file limit is 256 KB and these land around
 * 3 KB, so there is no reason to compress further.
 */
public final class EmojiGen {
    private EmojiGen() {}

    private static final int SIZE = 128;
    /** Padding around the content box, in px — Discord crops nothing, but a glyph flush to the edge reads
     *  as cramped next to the platform's own emoji, which all carry a little air. */
    private static final int MARGIN = 10;

    /** The icons worth having in chat: the modules a player names when asking for help, plus the mark.
     *  Not all 51 — the status-effect glyphs are a HUD alphabet, not a vocabulary anyone types. */
    private static final Set<String> WANTED = new LinkedHashSet<>(Arrays.asList(
            "logo", "zoom", "fullbright", "freelook", "toggle_sprint", "item_scroll",
            "hands", "animations", "screen_stretch", "hud_editor", "performance",
            "no_hurt_cam", "no_fire_overlay", "no_bobbing", "search"));

    public static void run(File srcDir, File outDir, int color) throws Exception {
        IconAtlasGen.Atlas a = IconAtlasGen.generate(srcDir);
        List<IconAtlasGen.Entry> es = a.entries;
        outDir.mkdirs();

        int written = 0;
        for (IconAtlasGen.Entry e : es) {
            if (!WANTED.contains(e.name)) continue;
            BufferedImage img = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
            draw(img, a, e, color);
            File out = new File(outDir, "club_" + e.name + ".png");
            ImageIO.write(img, "png", out);
            written++;
        }
        // A silent partial run would hand the owner an incomplete emoji set and no reason to look.
        if (written != WANTED.size())
            throw new IllegalStateException("wanted " + WANTED.size() + " emoji, wrote " + written
                    + " — an icon in WANTED has no SVG in " + srcDir);
        System.out.println("[emoji] " + written + " PNG(s) -> " + outDir);
    }

    private static void draw(BufferedImage out, IconAtlasGen.Atlas a, IconAtlasGen.Entry e, int color) {
        int content = SIZE - 2 * MARGIN;
        float padEm = IconAtlasGen.PAD / (IconAtlasGen.SCALE * IconAtlasGen.EM);
        float quadPx = content * (1 + 2 * padEm);
        int q = Math.round(quadPx);
        int left = Math.round(SIZE / 2f - quadPx / 2f), top = Math.round(SIZE / 2f - quadPx / 2f);

        int tileL = e.col * IconAtlasGen.TILE, tileT = e.row * IconAtlasGen.TILE;
        float screenPxRange = IconAtlasGen.PX_RANGE * content / (IconAtlasGen.EM * IconAtlasGen.SCALE);

        int cr = (color >> 16) & 0xFF, cg = (color >> 8) & 0xFF, cb = color & 0xFF;
        for (int py = 0; py < q; py++) {
            for (int px = 0; px < q; px++) {
                float u = tileL + (px + 0.5f) / q * IconAtlasGen.TILE;
                float v = tileT + (py + 0.5f) / q * IconAtlasGen.TILE;
                float sd = bilinear(a.img, u, v);
                float alpha = IconAtlasGen.clamp01((sd - 0.5f) * screenPxRange + 0.5f);
                if (alpha <= 0f) continue;
                int ox = left + px, oy = top + py;
                if (ox < 0 || oy < 0 || ox >= SIZE || oy >= SIZE) continue;
                // Straight (non-premultiplied) ARGB: colour is constant, only coverage varies.
                int ai = Math.round(alpha * 255f);
                out.setRGB(ox, oy, (ai << 24) | (cr << 16) | (cg << 8) | cb);
            }
        }
    }

    private static float bilinear(BufferedImage img, float x, float y) {
        float fx = x - 0.5f, fy = y - 0.5f;
        int x0 = (int) Math.floor(fx), y0 = (int) Math.floor(fy);
        float tx = fx - x0, ty = fy - y0;
        float v00 = px(img, x0, y0),     v10 = px(img, x0 + 1, y0);
        float v01 = px(img, x0, y0 + 1), v11 = px(img, x0 + 1, y0 + 1);
        return (v00 * (1 - tx) + v10 * tx) * (1 - ty) + (v01 * (1 - tx) + v11 * tx) * ty;
    }

    private static float px(BufferedImage img, int x, int y) {
        if (x < 0 || y < 0 || x >= img.getWidth() || y >= img.getHeight()) return 0f;
        return ((img.getRGB(x, y) >> 16) & 0xFF) / 255f;
    }
}
