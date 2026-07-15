package com.club.modules.particles;

import com.club.config.ClubConfig;
import net.minecraft.particle.ParticleType;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Should this particle type be dropped at spawn? Reads the player's hidden-set from config.
 *
 * <p>This is a VISIBLE choice, not a cull: unlike {@link com.club.modules.perf.ParticleCull} (which skips
 * building particles the camera cannot see, changing nothing), this removes particles the player asked to
 * stop seeing. Everything is on by default — the hidden-set starts empty, and an empty set short-circuits
 * {@link #hidden} to a single branch, so the common case costs almost nothing on the hot path.
 */
public final class ParticleVisibility {
    private ParticleVisibility() {}

    /** {@code addParticle} runs thousands of times a frame in a storm, and {@code Registries.getId} is a map
     *  lookup; memoise the id string per type singleton so the hot path never re-resolves it. Bounded by what
     *  the game registered. (Same memoisation pattern as {@link com.club.modules.perf.BlockEntityCull}.) */
    private static final Map<ParticleType<?>, String> ID = new ConcurrentHashMap<>();

    private static String idOf(ParticleType<?> type) {
        return ID.computeIfAbsent(type, t -> {
            Identifier id = Registries.PARTICLE_TYPE.getId(t);
            return id == null ? "" : id.toString();
        });
    }

    /** True if the player has hidden this particle type. All-on (the default, empty set) short-circuits here. */
    public static boolean hidden(ParticleType<?> type) {
        ClubConfig.Particles p = ClubConfig.get().particles;
        if (p == null) return false;
        Set<String> h = p.hidden;
        if (h == null || h.isEmpty()) return false;          // the default: nothing hidden, one branch and done
        String id = idOf(type);
        return !id.isEmpty() && h.contains(id);
    }

    // ---- config helpers for the menu (stage 2) -------------------------------------------------

    /** Is this id currently hidden? Keyed on the full id string, e.g. {@code "minecraft:crit"}. */
    public static boolean isHidden(Identifier id) {
        ClubConfig.Particles p = ClubConfig.get().particles;
        return p != null && p.hidden != null && p.hidden.contains(id.toString());
    }

    /** Show ({@code visible=true}) or hide a particle type; persists immediately. */
    public static void setVisible(Identifier id, boolean visible) {
        ClubConfig.Particles p = ClubConfig.get().particles;
        if (p == null || p.hidden == null) return;
        boolean changed = visible ? p.hidden.remove(id.toString()) : p.hidden.add(id.toString());
        if (changed) ClubConfig.save();
    }
}
