package com.club.modules.particles;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The grouping is a hand-authored product decision, so it is exactly the kind of data that rots silently: a
 * new particle in the next Minecraft version, or a typo, and something quietly falls into "Other". These
 * tests pin the map against the real vanilla 1.21.1 id list (javap of {@code net.minecraft.particle.ParticleTypes},
 * minus its two codecs) WITHOUT booting the game — pure map data, no registry.
 *
 * <p>Runtime is not left unguarded either: {@link ParticleCatalog#groupOf} sends anything unmapped to
 * {@link ParticleGroup#OTHER}, so even a genuine miss degrades to "listed in Other, still togglable" rather
 * than to a crash or a lost particle. This test just makes sure we NOTICE the miss.
 */
class ParticleCatalogTest {

    /** Every vanilla particle id in 1.21.1 (javap {@code ParticleTypes}). If Mojang adds one, this list and
     *  the map in {@link ParticleCatalog} must both learn about it — that is the point of the test. */
    private static final String[] VANILLA = {
            "angry_villager", "ash", "block", "block_marker", "bubble", "bubble_column_up", "bubble_pop",
            "campfire_cosy_smoke", "campfire_signal_smoke", "cherry_leaves", "cloud", "composter", "crimson_spore",
            "crit", "current_down", "damage_indicator", "dolphin", "dragon_breath", "dripping_dripstone_lava",
            "dripping_dripstone_water", "dripping_honey", "dripping_lava", "dripping_obsidian_tear", "dripping_water",
            "dust", "dust_color_transition", "dust_pillar", "dust_plume", "effect", "egg_crack", "elder_guardian",
            "electric_spark", "enchant", "enchanted_hit", "end_rod", "entity_effect", "explosion", "explosion_emitter",
            "falling_dripstone_lava", "falling_dripstone_water", "falling_dust", "falling_honey", "falling_lava",
            "falling_nectar", "falling_obsidian_tear", "falling_spore_blossom", "falling_water", "firework", "fishing",
            "flame", "flash", "glow", "glow_squid_ink", "gust", "gust_emitter_large", "gust_emitter_small",
            "happy_villager", "heart", "infested", "instant_effect", "item", "item_cobweb", "item_slime",
            "item_snowball", "landing_honey", "landing_lava", "landing_obsidian_tear", "large_smoke", "lava",
            "mycelium", "nautilus", "note", "ominous_spawning", "poof", "portal", "raid_omen", "rain",
            "reverse_portal", "scrape", "sculk_charge", "sculk_charge_pop", "sculk_soul", "shriek", "small_flame",
            "small_gust", "smoke", "sneeze", "snowflake", "sonic_boom", "soul", "soul_fire_flame", "spit", "splash",
            "spore_blossom_air", "squid_ink", "sweep_attack", "totem_of_undying", "trial_omen",
            "trial_spawner_detection", "trial_spawner_detection_ominous", "underwater", "vault_connection",
            "vibration", "warped_spore", "wax_off", "wax_on", "white_ash", "white_smoke", "witch"
    };

    @Test
    void everyVanillaTypeIsGroupedIntoARealBucket() {
        Map<String, ParticleGroup> map = ParticleCatalog.vanillaMap();
        List<String> ungrouped = new ArrayList<>();
        for (String id : VANILLA) {
            ParticleGroup g = map.get(id);
            if (g == null || g == ParticleGroup.OTHER) ungrouped.add(id);
        }
        assertTrue(ungrouped.isEmpty(), "vanilla particles missing a real group (would fall into Other): " + ungrouped);
    }

    @Test
    void theMapHoldsNothingButVanillaAndNothingInOther() {
        for (Map.Entry<String, ParticleGroup> e : ParticleCatalog.vanillaMap().entrySet()) {
            assertNotEquals(ParticleGroup.OTHER, e.getValue(), e.getKey() + " must not be authored into OTHER");
            assertTrue(new java.util.HashSet<>(java.util.Arrays.asList(VANILLA)).contains(e.getKey()),
                    e.getKey() + " is mapped but is not a known vanilla 1.21.1 particle — typo?");
        }
    }

    @Test
    void spotChecksLandInTheRightBucket() {
        Map<String, ParticleGroup> m = ParticleCatalog.vanillaMap();
        assertEquals(ParticleGroup.COMBAT, m.get("crit"));
        assertEquals(ParticleGroup.STATUS, m.get("totem_of_undying"));   // functional, but togglable now
        assertEquals(ParticleGroup.STATUS, m.get("effect"));             // the potion swirls people most want gone
        assertEquals(ParticleGroup.WATER, m.get("rain"));
        assertEquals(ParticleGroup.EXPLOSIONS, m.get("firework"));
        assertEquals(null, m.get("yourmod_sparkle"));   // unmapped -> groupOf() sends it to OTHER at runtime
    }

    @Test
    void labelsReadLikeEnglish() {
        assertEquals("Dripping water", ParticleCatalog.labelOf("dripping_water"));
        assertEquals("Crit", ParticleCatalog.labelOf("crit"));
        assertEquals("End rod", ParticleCatalog.labelOf("end_rod"));
    }
}
