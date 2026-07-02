package club.tools;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.imageio.ImageIO;
import javax.xml.parsers.DocumentBuilderFactory;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Offline icon-atlas generator (Stage 11). Reads a restricted SVG subset from
 * {@code tools/icons/src/<HHHH>_<name>.svg} (HHHH = the glyph's PUA code point) and renders an
 * EXACT signed-distance-field atlas + msdf-atlas-gen-compatible JSON that the existing
 * {@code MsdfMetrics}/{@code MsdfAtlas}/{@code ui_msdf_text} runtime consumes unchanged
 * (grayscale SDF written to RGB; median(r,r,r) == r, so the MSDF shader is a no-op superset).
 *
 * <p>Why not msdfgen: our icons are round-cap 2px strokes. For strokes the true distance field is
 * {@code dist(point, centerline) - strokeWidth/2} — computable exactly here, with no
 * stroke-to-outline conversion step and no external tool download. Round joins/caps come for free;
 * sharp-corner preservation (the M in MSDF) is not needed for this style.</p>
 *
 * <p>SVG subset (author icons within it): elements {@code path}, {@code circle}, {@code line},
 * {@code rect}; path commands {@code M m L l H h V v C c Q q Z z}; {@code stroke-width} attr
 * (default 2), round caps/joins implied; {@code fill="solid"} on circle/rect makes them filled.
 * ViewBox is fixed at 24x24. Anything else throws — fail loud at generation time.</p>
 */
public final class IconAtlasGen {
    private IconAtlasGen() {}

    // Geometry contract (mirrored by the JSON metrics — keep in sync with the spec).
    public static final float EM = 24f;         // SVG units per em (the 24-grid)
    public static final float SCALE = 3f;       // atlas px per SVG unit -> 72 px per em
    public static final int   PAD = 8;          // atlas px padding around each tile
    public static final float PX_RANGE = 8f;    // SDF distance range in atlas px
    public static final int   TILE = Math.round(EM * SCALE) + 2 * PAD;   // 88

    // ---- model -----------------------------------------------------------------

    public static final class Entry {
        public final int cp; public final String name;
        public final List<Prim> prims;
        public int col, row;                    // grid cell, assigned at pack time
        Entry(int cp, String name, List<Prim> prims) { this.cp = cp; this.name = name; this.prims = prims; }
    }

    public static final class Atlas {
        public final BufferedImage img;
        public final List<Entry> entries;
        public final int cols, rows;
        Atlas(BufferedImage img, List<Entry> entries, int cols, int rows) {
            this.img = img; this.entries = entries; this.cols = cols; this.rows = rows;
        }
    }

    /** One drawable primitive: signed distance in SVG units (negative inside). */
    public interface Prim { float sd(float x, float y); }

    // ---- entry points ----------------------------------------------------------

    /** Generates the atlas from {@code srcDir} and writes {@code outPng} + {@code outJson}. */
    public static void run(File srcDir, File outPng, File outJson) throws Exception {
        Atlas a = generate(srcDir);
        outPng.getParentFile().mkdirs();
        ImageIO.write(a.img, "png", outPng);
        java.nio.file.Files.writeString(outJson.toPath(), json(a));
        System.out.println("[icons] " + a.entries.size() + " glyph(s) -> " + a.img.getWidth() + "x"
                + a.img.getHeight() + " atlas at " + outPng);
    }

    /** Parses every icon SVG and renders the packed SDF atlas in memory. */
    public static Atlas generate(File srcDir) throws Exception {
        File[] files = srcDir.listFiles((d, n) -> n.toLowerCase(Locale.ROOT).endsWith(".svg"));
        if (files == null || files.length == 0) throw new IllegalStateException("no SVG icons in " + srcDir);
        Pattern namePat = Pattern.compile("(?i)^([0-9a-f]{4})_([a-z0-9_]+)\\.svg$");

        List<Entry> entries = new ArrayList<>();
        for (File f : files) {
            Matcher m = namePat.matcher(f.getName());
            if (!m.matches()) throw new IllegalStateException("icon file must be <HHHH>_<name>.svg: " + f.getName());
            entries.add(new Entry(Integer.parseInt(m.group(1), 16), m.group(2), parseSvg(f)));
        }
        entries.sort(Comparator.comparingInt(e -> e.cp));
        for (int i = 1; i < entries.size(); i++)
            if (entries.get(i).cp == entries.get(i - 1).cp)
                throw new IllegalStateException("duplicate code point U+" + Integer.toHexString(entries.get(i).cp));

        int n = entries.size();
        int cols = (int) Math.ceil(Math.sqrt(n));
        int rows = (int) Math.ceil(n / (double) cols);
        BufferedImage img = new BufferedImage(cols * TILE, rows * TILE, BufferedImage.TYPE_INT_RGB);

        for (int i = 0; i < n; i++) {
            Entry e = entries.get(i);
            e.col = i % cols; e.row = i / cols;
            int ox = e.col * TILE, oy = e.row * TILE;
            for (int py = 0; py < TILE; py++) {
                for (int px = 0; px < TILE; px++) {
                    float sx = (px + 0.5f - PAD) / SCALE;
                    float sy = (py + 0.5f - PAD) / SCALE;
                    float sd = Float.MAX_VALUE;
                    for (Prim p : e.prims) sd = Math.min(sd, p.sd(sx, sy));
                    float v = clamp01(0.5f - sd * SCALE / PX_RANGE);
                    int b = Math.round(v * 255f);
                    img.setRGB(ox + px, oy + py, (b << 16) | (b << 8) | b);
                }
            }
        }
        return new Atlas(img, entries, cols, rows);
    }

    /** msdf-atlas-gen-compatible JSON (the subset MsdfMetrics parses). yOrigin=bottom convention. */
    public static String json(Atlas a) {
        int w = a.img.getWidth(), h = a.img.getHeight();
        float padEm = PAD / (SCALE * EM);
        StringBuilder sb = new StringBuilder(4096);
        sb.append("{\n\"atlas\":{\"type\":\"msdf\",\"distanceRange\":").append(fmt(PX_RANGE))
          .append(",\"size\":").append(fmt(EM * SCALE))
          .append(",\"width\":").append(w).append(",\"height\":").append(h)
          .append(",\"yOrigin\":\"bottom\"},\n");
        sb.append("\"metrics\":{\"emSize\":1,\"lineHeight\":1.0,\"ascender\":1.0,\"descender\":0.0,")
          .append("\"underlineY\":0.0,\"underlineThickness\":0.0},\n");
        sb.append("\"glyphs\":[\n");
        for (int i = 0; i < a.entries.size(); i++) {
            Entry e = a.entries.get(i);
            int left = e.col * TILE, topPx = e.row * TILE;                 // top-origin px
            int bTop = h - topPx, bBottom = h - topPx - TILE;              // bottom-origin
            sb.append("{\"unicode\":").append(e.cp).append(",\"advance\":1.0,")
              .append("\"planeBounds\":{\"left\":").append(fmt(-padEm))
              .append(",\"bottom\":").append(fmt(-padEm))
              .append(",\"right\":").append(fmt(1 + padEm))
              .append(",\"top\":").append(fmt(1 + padEm)).append("},")
              .append("\"atlasBounds\":{\"left\":").append(left)
              .append(",\"bottom\":").append(bBottom)
              .append(",\"right\":").append(left + TILE)
              .append(",\"top\":").append(bTop).append("}}");
            sb.append(i < a.entries.size() - 1 ? ",\n" : "\n");
        }
        sb.append("]\n}\n");
        return sb.toString();
    }

    // ---- SVG subset parsing ------------------------------------------------------

    static List<Prim> parseSvg(File f) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(false);
        Document doc = dbf.newDocumentBuilder().parse(f);
        List<Prim> prims = new ArrayList<>();

        NodeList lines = doc.getElementsByTagName("line");
        for (int i = 0; i < lines.getLength(); i++) {
            Element el = (Element) lines.item(i);
            float hw = strokeW(el) / 2f;
            float x1 = f(el, "x1"), y1 = f(el, "y1"), x2 = f(el, "x2"), y2 = f(el, "y2");
            prims.add((x, y) -> segDist(x, y, x1, y1, x2, y2) - hw);
        }
        NodeList circles = doc.getElementsByTagName("circle");
        for (int i = 0; i < circles.getLength(); i++) {
            Element el = (Element) circles.item(i);
            float cx = f(el, "cx"), cy = f(el, "cy"), r = f(el, "r");
            if (filled(el)) prims.add((x, y) -> len(x - cx, y - cy) - r);
            else { float hw = strokeW(el) / 2f; prims.add((x, y) -> Math.abs(len(x - cx, y - cy) - r) - hw); }
        }
        NodeList rects = doc.getElementsByTagName("rect");
        for (int i = 0; i < rects.getLength(); i++) {
            Element el = (Element) rects.item(i);
            float rx = el.hasAttribute("rx") ? f(el, "rx") : 0f;
            float x0 = f(el, "x"), y0 = f(el, "y"), w = f(el, "width"), h = f(el, "height");
            float cx = x0 + w / 2f, cy = y0 + h / 2f, ex = w / 2f - rx, ey = h / 2f - rx;
            final float frx = rx;
            if (filled(el)) prims.add((x, y) -> roundRectSd(x - cx, y - cy, ex, ey, frx));
            else { float hw = strokeW(el) / 2f; prims.add((x, y) -> Math.abs(roundRectSd(x - cx, y - cy, ex, ey, frx)) - hw); }
        }
        NodeList paths = doc.getElementsByTagName("path");
        for (int i = 0; i < paths.getLength(); i++) {
            Element el = (Element) paths.item(i);
            if (filled(el)) throw new IllegalStateException(f.getName() + ": filled <path> unsupported — use strokes/circles/rects");
            float hw = strokeW(el) / 2f;
            List<float[]> polys = flattenPath(el.getAttribute("d"), f.getName());
            prims.add((x, y) -> {
                float d = Float.MAX_VALUE;
                for (float[] p : polys) d = Math.min(d, polylineDist(x, y, p));
                return d - hw;
            });
        }
        if (prims.isEmpty()) throw new IllegalStateException(f.getName() + ": no supported elements");
        return prims;
    }

    private static boolean filled(Element el) {
        String fl = el.getAttribute("fill");
        return !fl.isEmpty() && !fl.equalsIgnoreCase("none");
    }
    private static float strokeW(Element el) {
        String sw = el.getAttribute("stroke-width");
        return sw.isEmpty() ? 2f : Float.parseFloat(sw);
    }
    private static float f(Element el, String attr) { return Float.parseFloat(el.getAttribute(attr)); }

    /** Flattens the path-data subset (M m L l H h V v C c Q q Z z) into polylines. */
    static List<float[]> flattenPath(String d, String file) {
        List<float[]> out = new ArrayList<>();
        List<Float> cur = new ArrayList<>();
        float x = 0, y = 0, sx = 0, sy = 0;
        PathTok t = new PathTok(d);
        char cmd = 0;
        while (t.hasNext()) {
            char c = t.peekCmd();
            if (c != 0) { cmd = c; t.nextCmd(); }
            else if (cmd == 'M') cmd = 'L';        // implicit lineto after moveto
            else if (cmd == 'm') cmd = 'l';
            switch (cmd) {
                case 'M', 'm' -> {
                    if (cur.size() >= 4) out.add(toArr(cur));
                    cur.clear();
                    float nx = t.num(), ny = t.num();
                    if (cmd == 'm') { nx += x; ny += y; }
                    x = sx = nx; y = sy = ny;
                    cur.add(x); cur.add(y);
                }
                case 'L', 'l' -> {
                    float nx = t.num(), ny = t.num();
                    if (cmd == 'l') { nx += x; ny += y; }
                    x = nx; y = ny; cur.add(x); cur.add(y);
                }
                case 'H', 'h' -> { float nx = t.num(); if (cmd == 'h') nx += x; x = nx; cur.add(x); cur.add(y); }
                case 'V', 'v' -> { float ny = t.num(); if (cmd == 'v') ny += y; y = ny; cur.add(x); cur.add(y); }
                case 'C', 'c' -> {
                    float c1x = t.num(), c1y = t.num(), c2x = t.num(), c2y = t.num(), ex = t.num(), ey = t.num();
                    if (cmd == 'c') { c1x += x; c1y += y; c2x += x; c2y += y; ex += x; ey += y; }
                    for (int k = 1; k <= 32; k++) {
                        float u = k / 32f, v = 1 - u;
                        cur.add(v*v*v*x + 3*v*v*u*c1x + 3*v*u*u*c2x + u*u*u*ex);
                        cur.add(v*v*v*y + 3*v*v*u*c1y + 3*v*u*u*c2y + u*u*u*ey);
                    }
                    x = ex; y = ey;
                }
                case 'Q', 'q' -> {
                    float qx = t.num(), qy = t.num(), ex = t.num(), ey = t.num();
                    if (cmd == 'q') { qx += x; qy += y; ex += x; ey += y; }
                    for (int k = 1; k <= 32; k++) {
                        float u = k / 32f, v = 1 - u;
                        cur.add(v*v*x + 2*v*u*qx + u*u*ex);
                        cur.add(v*v*y + 2*v*u*qy + u*u*ey);
                    }
                    x = ex; y = ey;
                }
                case 'Z', 'z' -> { cur.add(sx); cur.add(sy); x = sx; y = sy; }
                default -> throw new IllegalStateException(file + ": unsupported path command '" + cmd + "'");
            }
        }
        if (cur.size() >= 4) out.add(toArr(cur));
        if (out.isEmpty()) throw new IllegalStateException(file + ": empty path");
        return out;
    }

    private static float[] toArr(List<Float> l) {
        float[] a = new float[l.size()];
        for (int i = 0; i < a.length; i++) a[i] = l.get(i);
        return a;
    }

    /** Minimal tokenizer for path data: commands are letters, numbers separated by space/comma/sign. */
    private static final class PathTok {
        private final String s; private int i;
        PathTok(String s) { this.s = s; }
        boolean hasNext() { skip(); return i < s.length(); }
        char peekCmd() { skip(); if (i < s.length() && Character.isLetter(s.charAt(i))) return s.charAt(i); return 0; }
        void nextCmd() { i++; }
        float num() {
            skip();
            int st = i;
            if (i < s.length() && (s.charAt(i) == '-' || s.charAt(i) == '+')) i++;
            while (i < s.length() && (Character.isDigit(s.charAt(i)) || s.charAt(i) == '.')) i++;
            if (st == i) throw new IllegalStateException("expected number at " + st + " in '" + s + "'");
            return Float.parseFloat(s.substring(st, i));
        }
        private void skip() { while (i < s.length() && (s.charAt(i) == ' ' || s.charAt(i) == ',' || s.charAt(i) == '\n' || s.charAt(i) == '\t' || s.charAt(i) == '\r')) i++; }
    }

    // ---- distance math (SVG units) ----------------------------------------------

    static float polylineDist(float x, float y, float[] pts) {
        float d = Float.MAX_VALUE;
        for (int i = 0; i + 3 < pts.length; i += 2)
            d = Math.min(d, segDist(x, y, pts[i], pts[i + 1], pts[i + 2], pts[i + 3]));
        return d;
    }

    static float segDist(float px, float py, float ax, float ay, float bx, float by) {
        float abx = bx - ax, aby = by - ay;
        float apx = px - ax, apy = py - ay;
        float ab2 = abx * abx + aby * aby;
        float t = ab2 <= 1e-9f ? 0f : clamp01((apx * abx + apy * aby) / ab2);
        return len(apx - t * abx, apy - t * aby);
    }

    /** Signed distance of a rounded rect centered at origin: half-extents (ex,ey) of the CORE box + radius r. */
    static float roundRectSd(float x, float y, float ex, float ey, float r) {
        float qx = Math.abs(x) - ex, qy = Math.abs(y) - ey;
        float ox = Math.max(qx, 0f), oy = Math.max(qy, 0f);
        return Math.min(Math.max(qx, qy), 0f) + len(ox, oy) - r;
    }

    static float len(float x, float y) { return (float) Math.sqrt(x * x + y * y); }
    static float clamp01(float v) { return v < 0f ? 0f : (v > 1f ? 1f : v); }

    /** Locale-independent compact float for JSON output. */
    static String fmt(float v) {
        return String.format(Locale.ROOT, "%s", v == Math.rint(v) ? String.valueOf((long) v) : String.format(Locale.ROOT, "%.6f", v));
    }
}
