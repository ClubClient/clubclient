package com.club.modules.particles;

import net.minecraft.particle.ParticleType;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Groups every registered particle type for the Particles category, and turns registry ids into names a
 * player recognises.
 *
 * <p>The {@link #GROUP} map is authored from the vanilla 1.21.1 {@code ParticleTypes} registry
 * (javap-verified — {@code ParticleCatalogTest} asserts every vanilla id lands in a real group, never
 * {@link ParticleGroup#OTHER}). The map keys on the id PATH ({@code "crit"}), so it is namespace-agnostic and
 * a modded {@code yourmod:sparkle} simply resolves to Other — listed and togglable, never lost.
 *
 * <p>{@link #all()} is the only method that touches the live registry, and it is built once and cached: the
 * menu reads it when the Particles category first opens, not per frame.
 */
public final class ParticleCatalog {
    private ParticleCatalog() {}

    /** id path -> group. Authored, not derived: the grouping is a product decision, not something the game
     *  tells us. Anything absent resolves to {@link ParticleGroup#OTHER} at runtime. */
    private static final Map<String, ParticleGroup> GROUP = new HashMap<>();
    static {
        put(ParticleGroup.COMBAT, "crit", "enchanted_hit", "damage_indicator", "sweep_attack", "sonic_boom");
        put(ParticleGroup.BLOCKS, "block", "block_marker", "falling_dust", "dust", "dust_color_transition",
                "dust_pillar", "dust_plume", "scrape", "wax_on", "wax_off", "composter", "egg_crack", "infested",
                "item", "item_cobweb", "item_slime", "item_snowball", "spit");
        put(ParticleGroup.AMBIENT, "smoke", "large_smoke", "white_smoke", "campfire_cosy_smoke",
                "campfire_signal_smoke", "cloud", "ash", "white_ash", "warped_spore", "crimson_spore",
                "spore_blossom_air", "falling_spore_blossom", "cherry_leaves", "mycelium", "sneeze", "snowflake", "note");
        put(ParticleGroup.FIRE, "flame", "small_flame", "soul_fire_flame", "soul", "lava", "end_rod", "glow",
                "electric_spark", "flash", "sculk_soul", "sculk_charge", "sculk_charge_pop", "shriek", "vibration");
        put(ParticleGroup.WATER, "dripping_water", "dripping_lava", "dripping_honey", "dripping_obsidian_tear",
                "dripping_dripstone_water", "dripping_dripstone_lava", "falling_water", "falling_lava",
                "falling_honey", "falling_nectar", "falling_obsidian_tear", "falling_dripstone_water",
                "falling_dripstone_lava", "landing_honey", "landing_lava", "landing_obsidian_tear", "splash",
                "rain", "underwater", "bubble", "bubble_pop", "bubble_column_up", "current_down", "nautilus",
                "dolphin", "fishing", "squid_ink", "glow_squid_ink");
        put(ParticleGroup.EXPLOSIONS, "explosion", "explosion_emitter", "poof", "firework", "gust",
                "gust_emitter_large", "gust_emitter_small", "small_gust");
        put(ParticleGroup.STATUS, "effect", "instant_effect", "entity_effect", "angry_villager", "happy_villager",
                "heart", "totem_of_undying", "dragon_breath", "elder_guardian", "witch", "enchant", "portal",
                "reverse_portal", "raid_omen", "trial_omen", "ominous_spawning", "trial_spawner_detection",
                "trial_spawner_detection_ominous", "vault_connection");
    }
    private static void put(ParticleGroup g, String... ids) { for (String id : ids) GROUP.put(id, g); }

    /** The authored map, read-only — for {@code ParticleCatalogTest} (which runs without a live registry). */
    public static Map<String, ParticleGroup> vanillaMap() { return Collections.unmodifiableMap(GROUP); }

    /** A vanilla id -> its group; anything else (modded, or added later) -> {@link ParticleGroup#OTHER}. */
    public static ParticleGroup groupOf(Identifier id) {
        if (id == null || !Identifier.DEFAULT_NAMESPACE.equals(id.getNamespace())) return ParticleGroup.OTHER;
        return GROUP.getOrDefault(id.getPath(), ParticleGroup.OTHER);
    }

    /** One registered particle type, resolved for the UI. */
    public record Entry(Identifier id, String label, ParticleGroup group) {}

    private static List<Entry> cache;

    /** Every registered particle type, sorted by group order then friendly label. Built once, cached — the
     *  menu reads this when the category opens, never per frame. */
    public static List<Entry> all() {
        if (cache != null) return cache;
        List<Entry> list = new ArrayList<>();
        for (ParticleType<?> t : Registries.PARTICLE_TYPE) {
            Identifier id = Registries.PARTICLE_TYPE.getId(t);
            if (id == null) continue;   // unregistered — nothing we could name or persist
            list.add(new Entry(id, label(id), groupOf(id)));
        }
        list.sort(Comparator.comparingInt((Entry e) -> e.group().ordinal()).thenComparing(Entry::label));
        return cache = List.copyOf(list);
    }

    /** Drop the cache so a reload re-reads the registry (e.g. resource/datapack reload adds a type). */
    public static void invalidate() { cache = null; }

    /** {@code "dripping_water"} -> {@code "Dripping water"}. */
    public static String labelOf(String path) {
        String p = path.replace('_', ' ');
        return p.isEmpty() ? p : Character.toUpperCase(p.charAt(0)) + p.substring(1);
    }
    public static String label(Identifier id) { return labelOf(id.getPath()); }
}
