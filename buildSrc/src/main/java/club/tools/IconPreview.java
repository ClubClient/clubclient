package club.tools;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;

/**
 * Software preview of the generated icon atlas — replicates the ui_msdf_text shader's alpha math
 * (bilinear atlas sample -> {@code clamp((v-0.5)*screenPxRange+0.5)}) so icon quality at real UI
 * sizes can be judged from a PNG without launching the client. Board layout: one row per icon,
 * columns = sizes 17 / 24 / 40 / 72 px + a 72px "ghost" at ~9% alpha, on the menu card tone.
 */
public final class IconPreview {
    private IconPreview() {}

    private static final int[] SIZES = {17, 24, 40, 72};
    private static final int BG = 0x131B2A;         // surface (card tone)
    private static final int CELL_PAD = 14;

    public static void run(File srcDir, File outPng, int color) throws Exception {
        IconAtlasGen.Atlas a = IconAtlasGen.generate(srcDir);
        run(a, outPng, color);
    }

    public static void run(IconAtlasGen.Atlas a, File outPng, int color) throws Exception {
        List<IconAtlasGen.Entry> es = a.entries;
        int cellW = 72 + CELL_PAD * 2;
        int cols = SIZES.length + 1;                 // + ghost column
        int w = cols * cellW, h = es.size() * cellW;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) img.setRGB(x, y, BG);

        for (int r = 0; r < es.size(); r++) {
            IconAtlasGen.Entry e = es.get(r);
            for (int c = 0; c < SIZES.length; c++)
                blitIcon(img, a, e, c * cellW + cellW / 2, r * cellW + cellW / 2, SIZES[c], color, 1f);
            blitIcon(img, a, e, SIZES.length * cellW + cellW / 2, r * cellW + cellW / 2, 72, color, 0.09f);
        }
        outPng.getParentFile().mkdirs();
        ImageIO.write(img, "png", outPng);
        System.out.println("[icons] preview -> " + outPng);
    }

    /** Draws one icon centered at (cx,cy) at {@code size} px (content box), tinted, alpha-scaled. */
    private static void blitIcon(BufferedImage out, IconAtlasGen.Atlas a, IconAtlasGen.Entry e,
                                 int cx, int cy, int size, int color, float alpha) {
        // Quad covers the padded plane: content 0..1 em plus padEm on each side (like the runtime quad).
        float padEm = IconAtlasGen.PAD / (IconAtlasGen.SCALE * IconAtlasGen.EM);
        float quadPx = size * (1 + 2 * padEm);
        int q = Math.round(quadPx);
        int left = Math.round(cx - quadPx / 2f), top = Math.round(cy - quadPx / 2f);

        int tileL = e.col * IconAtlasGen.TILE, tileT = e.row * IconAtlasGen.TILE;
        float screenPxRange = IconAtlasGen.PX_RANGE * size / (IconAtlasGen.EM * IconAtlasGen.SCALE);

        int cr = (color >> 16) & 0xFF, cg = (color >> 8) & 0xFF, cb = color & 0xFF;
        for (int py = 0; py < q; py++) {
            for (int px = 0; px < q; px++) {
                float u = tileL + (px + 0.5f) / q * IconAtlasGen.TILE;
                float v = tileT + (py + 0.5f) / q * IconAtlasGen.TILE;
                float sd = bilinear(a.img, u, v);
                float aPix = IconAtlasGen.clamp01((sd - 0.5f) * screenPxRange + 0.5f) * alpha;
                if (aPix <= 0f) continue;
                int ox = left + px, oy = top + py;
                if (ox < 0 || oy < 0 || ox >= out.getWidth() || oy >= out.getHeight()) continue;
                int bgc = out.getRGB(ox, oy);
                int br = (bgc >> 16) & 0xFF, bg = (bgc >> 8) & 0xFF, bb = bgc & 0xFF;
                int rr = Math.round(cr * aPix + br * (1 - aPix));
                int rg = Math.round(cg * aPix + bg * (1 - aPix));
                int rb = Math.round(cb * aPix + bb * (1 - aPix));
                out.setRGB(ox, oy, (rr << 16) | (rg << 8) | rb);
            }
        }
    }

    /** Bilinear sample of the red channel (grayscale SDF) at atlas px coords, 0..1. */
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
