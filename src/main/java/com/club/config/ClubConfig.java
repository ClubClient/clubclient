package com.club.config;

import com.club.ClubMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Persistent settings stored in {@code .minecraft/config/club_settings.json}.
 * Grouped by module, matching the spec. Loaded once on startup, saved
 * immediately on every change made from the GUI.
 */
public class ClubConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static ClubConfig INSTANCE;
    private static transient Path path;

    public int version = 9; // bumped when new fields are added, for migration

    // --- module sections ---
    public Hands hands = new Hands();
    public Animations animations = new Animations();
    public Zoom zoom = new Zoom();
    public ToggleSprint toggleSprint = new ToggleSprint();
    public Freelook freelook = new Freelook();
    public ScreenStretch screenStretch = new ScreenStretch();
    public boolean noHurtCam = true;
    public boolean noFireOverlay = true;
    public boolean noBobbing = true;
    public boolean fullbright = false; // gamma READ override (15.0) — off by default, mirrored in FullbrightModule
    // Per-module toggle keybinds (Stage 43): module name → InputUtil translation key ("key.keyboard.r").
    // Bound from each module's popover; fired by ModuleBinds on key edges while no screen is open.
    public java.util.Map<String, String> moduleBinds = new java.util.HashMap<>();
    // [SEAM:config] Module config sections. Each parallel workstream adds exactly ONE line here, under this
    // anchor, and keeps its own fields in its own class — three branches editing three different lines of the
    // same file is a clean merge; three branches reformatting the same block is not.
    public com.club.modules.itemscroll.ItemScrollConfig itemScroll = new com.club.modules.itemscroll.ItemScrollConfig();

    public Hud hud = new Hud();

    // (menuX/menuY removed 2026-07-02: the menu is fixed centred — stale keys in old files are ignored by Gson)

    /** Position/scale for one visual hand side. */
    public static class HandSide {
        public float scale = 1.0f;
        public float offsetX = 0.0f;
        public float offsetY = 0.0f;
        public float offsetZ = 0.0f;
    }

    public static class Hands {
        public boolean enabled = true; // master toggle; when false hands render vanilla
        public HandSide rightHand = new HandSide();
        public HandSide leftHand = new HandSide();
        // MIGRATION SHIM (pre-v3, read ONLY by migrate()): the old single-hand settings. Boxed so
        // migrate() can detect "present in an old file" (non-null) vs "absent" (null), carry them into
        // rightHand, then null them out. Live code must NEVER read these — the per-side HandSide fields
        // above are the real schema. Kept (not deleted) so a truly old file still upgrades cleanly.
        public Float scale, offsetX, offsetY, offsetZ;
    }

    public static class Animations {
        public boolean enabled = true; // master toggle; when false the vanilla swing is kept
        public String type = "CLASSIC"; // AnimationType name
        public float speed = 1.0f;       // 0.5 - 2.0
        public float amplitude = 1.0f;   // 0.5 - 1.5
    }

    public static class Zoom {
        public boolean enabled = true;   // master toggle; the hold-key only works when on
        public float factor = 4.0f;      // world-FOV divisor while zoomed (2..8, scroll-adjustable)
        public float smoothness = 0.5f;  // 0 = instant, 1 = very smooth (maps to the ease duration)
    }

    public static class ToggleSprint {
        public boolean enabled = true;  // hold the sprint key down for the player (vanilla-toggle-safe)
    }

    public static class Freelook {
        public boolean enabled = true;  // master toggle; the hold-key only works when on
    }

    public static class ScreenStretch {
        public boolean enabled = true;  // master toggle; AUTO makes it a no-op until a ratio is picked
        // AUTO = follow the real window aspect, i.e. DON'T touch the projection. The old default of
        // "R16_9" warped the world and painted black bars for everyone whose monitor isn't 16:9 —
        // 16:10 laptops, ultrawides, 5:4 — the moment they installed the mod (Stage 59 audit).
        public String preset = "AUTO"; // StretchPreset name
        public boolean blackBars = true;
    }

    public static class Hud {
        public boolean armor = true;
        public boolean potions = true;
        public boolean target = true;
        public boolean armorPercent = true; // true=%, false=durability count
        // draggable positions (top-left anchored), set via the HUD editor
        public int armorX = 8;
        public int armorY = 8;
        public int potionX = 8;
        public int potionY = 70;
        public int targetX = -1; // -1 = auto (right of crosshair) until dragged
        public int targetY = -1;
        public int targetDistance = 6; // blocks; how far the target HUD detects entities
        // per-HUD scale (0.5 - 2.0), each element independent
        public float armorScale = 1.0f;
        public float potionScale = 1.0f;
        public float targetScale = 1.0f;
        // MIGRATION SHIM (pre-v3, read ONLY by migrate()): the old global HUD scale, boxed so migrate()
        // can carry it into the three per-element scales above, then null it. Live code must never read it.
        public Float scale;
        public int armorLayout = 0;   // 0 = column [icon value], 1 = line (value above icon, gauge below); ArmorLayout enum

        public boolean potionHorizontal = false; // potions as a row instead of a column
        public boolean hideVanillaEffects = true; // hide the vanilla status-effect HUD overlay
        // V2 HUD: coordinates/FPS readout (new in v5)
        public boolean info = true;
        public int infoX = 8;
        public int infoY = 120;
        public float infoScale = 1.0f;
        // V2 HUD: Toggle Sprint indicator chip (Stage 41); -1/-1 = auto bottom-left
        public boolean sprint = true;
        public int sprintX = -1;
        public int sprintY = -1;
        public float sprintScale = 1.0f;

        /**
         * Which coordinate space the saved x/y above are in (Stage 63).
         *
         * <p>{@code null} — the field predates the Club canvas, so a config carrying it was written when HUD
         * coordinates were absolute pixels of Minecraft's GUI-scaled space, and they have to be converted
         * once (see {@code com.club.hud.HudSpace}). {@code 1} — Club canvas units.</p>
         *
         * <p>Deliberately a boxed Integer: Gson leaves an ABSENT field at its Java default, so an {@code int}
         * defaulting to 1 would make every old config claim it had already been migrated, and an int
         * defaulting to 0 would make every FRESH install migrate its own defaults (which are authored in Club
         * units) and shove the HUD across the screen on any GUI Scale but 2. Null is the only value that
         * means "this file is older than the question".</p>
         */
        public Integer space;
    }

    public static ClubConfig get() {
        if (INSTANCE == null) load();
        return INSTANCE;
    }

    public static void load() {
        path = FabricLoader.getInstance().getConfigDir().resolve("club_settings.json");
        try {
            if (Files.exists(path)) {
                String json = Files.readString(path);
                ClubConfig cfg = GSON.fromJson(json, ClubConfig.class);
                if (cfg == null) throw new IOException("config file is empty/blank");
                INSTANCE = cfg;
                INSTANCE.sanitize();
                INSTANCE.migrate();
            } else {
                INSTANCE = new ClubConfig();
                save();
            }
        } catch (Exception e) {
            // Stage 30: NEVER silently discard the user's settings. Preserve the unreadable file as
            // *.corrupt (HUD layout, hand offsets etc. stay recoverable by hand) and start clean.
            //
            // Stage 59 audit, two hardenings:
            //  • An IOException is NOT corruption — a file locked by a sync client (OneDrive/Dropbox), a
            //    cloud placeholder that hasn't hydrated, or a transient read error would have renamed a
            //    perfectly good config away and handed the user factory defaults. Only a PARSE failure
            //    means the content is actually broken; anything else keeps the file and runs on defaults
            //    for this session, with saving DISABLED so we can't overwrite what we failed to read.
            //  • The .corrupt backup no longer overwrites an older one: a second bad launch used to
            //    replace the only surviving copy of the real settings.
            boolean parseFailure = !(e instanceof IOException);
            if (parseFailure) {
                ClubMod.LOGGER.warn("[Club] Config file is not valid JSON — preserving it and starting with defaults", e);
                try {
                    Path bak = path.resolveSibling(path.getFileName() + ".corrupt");
                    for (int i = 2; Files.exists(bak) && i < 100; i++)
                        bak = path.resolveSibling(path.getFileName() + ".corrupt" + i);
                    Files.move(path, bak);
                } catch (IOException m) {
                    ClubMod.LOGGER.warn("[Club] Could not preserve the broken config file", m);
                }
            } else {
                ClubMod.LOGGER.warn("[Club] Could not READ the config (file locked / not synced?) — running on "
                        + "defaults for this session and NOT saving, so the real settings stay intact", e);
                readOnly = true;
            }
            INSTANCE = new ClubConfig();
        }
    }

    /** Set when load() failed for a reason that is NOT corruption: refuse to write over settings we
     *  could not read, or we would turn a temporary glitch into permanent data loss. */
    private static volatile boolean readOnly;

    // Stage 30: config writes are ASYNC + ATOMIC. save() serializes on the caller thread (the render
    // thread mutates INSTANCE, so the snapshot must be taken there — serializing on the writer thread
    // would race live edits) and hands the string to one background writer, so toggling a module never
    // blocks a frame on disk I/O. The writer stages into *.tmp and atomically moves over the real file:
    // a crash mid-write can no longer leave a truncated JSON that load() would junk.
    private static final java.util.concurrent.ExecutorService IO =
            java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "club-config-io");
                t.setDaemon(true);   // flushed explicitly via close() on client stop
                return t;
            });

    public static void save() {
        if (INSTANCE == null || path == null || readOnly) return;
        String json = GSON.toJson(INSTANCE);
        try {
            IO.execute(() -> write(json));
        } catch (java.util.concurrent.RejectedExecutionException e) {
            write(json);   // writer already closed (client stopping) — write inline, never drop
        }
    }

    /** Drains any queued write and stops the writer (client shutdown — daemon thread wouldn't finish). */
    public static void close() {
        IO.shutdown();
        try {
            if (!IO.awaitTermination(2, java.util.concurrent.TimeUnit.SECONDS))
                ClubMod.LOGGER.warn("[Club] Config writer did not drain before shutdown");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void write(String json) {
        try {
            Files.createDirectories(path.getParent());
            // The staging file is per-PROCESS (Stage 59 audit): two clients sharing one .minecraft (a
            // second instance, a dev launch) both staged into the SAME .tmp and then atomically published
            // whatever mix of the two writes happened to be in it.
            Path tmp = path.resolveSibling(path.getFileName() + "." + ProcessHandle.current().pid() + ".tmp");
            Files.writeString(tmp, json);
            try {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);   // best effort on exotic FS
            }
        } catch (IOException e) {
            ClubMod.LOGGER.warn("[Club] Failed to save config", e);
        }
    }

    /** Guards against null sub-objects when loading an old/partial file. */
    private void sanitize() {
        if (hands == null) hands = new Hands();
        if (hands.rightHand == null) hands.rightHand = new HandSide();
        if (hands.leftHand == null) hands.leftHand = new HandSide();
        if (animations == null) animations = new Animations();
        if (zoom == null) zoom = new Zoom();
        zoom.factor = Math.max(2f, Math.min(8f, zoom.factor));
        zoom.smoothness = Math.max(0f, Math.min(1f, zoom.smoothness));
        if (toggleSprint == null) toggleSprint = new ToggleSprint();
        if (freelook == null) freelook = new Freelook();
        if (moduleBinds == null) moduleBinds = new java.util.HashMap<>();
        if (screenStretch == null) screenStretch = new ScreenStretch();
        if (hud == null) hud = new Hud();
        // Canonicalize armorLayout ONCE here (Stage 29) instead of clamping at every read site: an
        // old/hand-edited value (e.g. the retired 2, or junk) self-heals to 0/1 on load.
        hud.armorLayout = ArmorLayout.fromIndex(hud.armorLayout).index();
    }

    /**
     * Carries values from older config layouts forward (v3 hand/scale fields) and re-seeds
     * defaults per version bump. Note: Gson invokes the no-arg constructors here, so fields
     * ABSENT from the file keep their initializers — the default-seeding blocks are an
     * explicit safety net for hand-edited/partial files, not a Gson workaround (verified:
     * absent infoX deserializes to 8, never 0). Runs once, then persists.
     */
    private void migrate() {
        boolean changed = false;
        if (version < 1) {
            hud.armorX = 8;  hud.armorY = 8;
            hud.potionX = 8; hud.potionY = 70;
            hud.targetX = -1; hud.targetY = -1;
            if (hud.targetDistance <= 0) hud.targetDistance = 6;
            version = 1;
            changed = true;
        }
        if (version < 2) {
            hud.hideVanillaEffects = true;
            version = 2;
            changed = true;
        }
        if (version < 3) {
            // carry the old single-hand settings into the right hand
            if (hands.scale != null) {
                hands.rightHand.scale = hands.scale;
                hands.rightHand.offsetX = hands.offsetX != null ? hands.offsetX : 0f;
                hands.rightHand.offsetY = hands.offsetY != null ? hands.offsetY : 0f;
                hands.rightHand.offsetZ = hands.offsetZ != null ? hands.offsetZ : 0f;
            }
            hands.scale = hands.offsetX = hands.offsetY = hands.offsetZ = null;
            // carry the old global HUD scale into all three per-HUD scales (these
            // fields didn't exist pre-v3, so their default is meaningless here)
            float g = (hud.scale != null && hud.scale > 0f) ? hud.scale : 1.0f;
            hud.armorScale = g;
            hud.potionScale = g;
            hud.targetScale = g;
            hud.scale = null;
            version = 3;
            changed = true;
        }
        if (version < 4) {
            // new per-module master toggles — preserve existing behavior (all on)
            hands.enabled = true;
            animations.enabled = true;
            screenStretch.enabled = true;
            version = 4;
            changed = true;
        }
        if (version < 5) {
            // V2 Info element defaults — safety net for hand-edited/partial files (absent fields already keep initializers)
            hud.info = true;
            hud.infoX = 8; hud.infoY = 120; hud.infoScale = 1.0f;
            version = 5;
            changed = true;
        }
        if (version < 6) {
            // v0.1 kit (Zoom/Fullbright/ToggleSprint/Freelook) + Sprint HUD chip. New modules, so no
            // carry-over — just the safety-net defaults for hand-edited/partial files (Fullbright OFF).
            fullbright = false;
            hud.sprint = true; hud.sprintX = -1; hud.sprintY = -1; hud.sprintScale = 1.0f;
            version = 6;
            changed = true;
        }
        if (version < 7) {
            // Stage 58: Zoom/Freelook are HOLD modules — their key is the hold key (a real vanilla
            // binding), never a toggle bind. A v6 file could carry a toggle bind on the SAME key the
            // module is held with (the default C for Zoom), so one press both zoomed and flipped the
            // module off — the "works every other press" bug. Drop those entries for good.
            moduleBinds.remove("Zoom");
            moduleBinds.remove("Freelook");
            version = 7;
            changed = true;
        }
        if (version < 8) {
            // Screen Stretch shipped ON with a hard-coded 16:9 target, so a 16:10 / ultrawide / 5:4
            // player got a distorted world and black bars over the hotbar without ever opening the menu
            // (invisible on a 16:9 dev monitor, where the scale works out to exactly 1.0). Anyone still
            // carrying that default is moved to AUTO — a no-op until a ratio is deliberately picked.
            if ("R16_9".equals(screenStretch.preset)) screenStretch.preset = "AUTO";
            version = 8;
            changed = true;
        }
        if (version < 9) {
            // Stage 63: the HUD moved onto the Club canvas, so saved x/y no longer mean what they meant.
            // They were absolute pixels of Minecraft's GUI-scaled space — a space whose size is the player's
            // GUI Scale setting. Mark them legacy; the conversion itself needs the live window (which does
            // not exist yet at config-load time) and happens on the first HUD frame — see com.club.hud.HudSpace.
            //
            // This runs for files READ FROM DISK only. A fresh install never reaches migrate(), leaves
            // hud.space null, and is simply stamped as Club-native: its defaults were authored in Club units.
            hud.space = 0;
            version = 9;
            changed = true;
        }
        if (changed) save();
    }
}