package com.club.config;

import com.club.ClubMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Persistent settings stored in {@code .minecraft/config/club_settings.json}.
 * Grouped by module, matching the spec. Loaded once on startup, saved
 * immediately on every change made from the GUI.
 */
public class ClubConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static ClubConfig INSTANCE;
    private static transient Path path;

    public int version = 4; // bumped when new fields are added, for migration

    // --- module sections ---
    public Hands hands = new Hands();
    public Animations animations = new Animations();
    public ScreenStretch screenStretch = new ScreenStretch();
    public boolean noHurtCam = true;
    public boolean noFireOverlay = true;
    public boolean noBobbing = true;
    public Hud hud = new Hud();

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
        // legacy single-hand fields (pre-v3) — boxed so migrate() can detect & carry them over
        public Float scale, offsetX, offsetY, offsetZ;
    }

    public static class Animations {
        public boolean enabled = true; // master toggle; when false the vanilla swing is kept
        public String type = "CLASSIC"; // AnimationType name
        public float speed = 1.0f;       // 0.5 - 2.0
        public float amplitude = 1.0f;   // 0.5 - 1.5
    }

    public static class ScreenStretch {
        public boolean enabled = true; // master toggle; when false no stretch is applied
        public String preset = "R16_9"; // StretchPreset name
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
        public Float scale; // legacy global scale (pre-v3) — carried into the three above by migrate()
        public boolean armorVertical = false;  // armor as a column instead of a row
        public boolean potionHorizontal = false; // potions as a row instead of a column
        public boolean hideVanillaEffects = true; // hide the vanilla status-effect HUD overlay
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
                INSTANCE = (cfg != null) ? cfg : new ClubConfig();
                INSTANCE.sanitize();
                 INSTANCE.migrate();
            } else {
                INSTANCE = new ClubConfig();
                save();
            }
        } catch (Exception e) {
            ClubMod.LOGGER.warn("[Club] Failed to load config, using defaults", e);
            INSTANCE = new ClubConfig();
        }
    }

    public static void save() {
        if (INSTANCE == null || path == null) return;
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(INSTANCE));
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
        if (screenStretch == null) screenStretch = new ScreenStretch();
        if (hud == null) hud = new Hud();
    }

    /**
     * Fixes fields that an older config file didn't contain (Gson leaves missing
     * primitives at 0, which would pin HUDs to the corner). Runs once, then
     * persists with the current version.
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
        if (changed) save();
    }
}