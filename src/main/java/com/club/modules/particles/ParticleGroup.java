package com.club.modules.particles;

/**
 * The buckets the Particles category sorts every registered particle type into. Enum order IS display order.
 *
 * <p>{@link #OTHER} is the catch-all for anything not explicitly mapped in {@link ParticleCatalog} — a modded
 * particle, or a type a future Minecraft version adds. It exists so nothing is ever silently un-listed or
 * un-togglable: an unmapped type still shows up, in Other, on by default like everything else. The UI hides
 * the Other group only when it is empty.
 */
public enum ParticleGroup {
    COMBAT("Combat"),
    BLOCKS("Blocks"),
    AMBIENT("Ambient"),
    FIRE("Fire & Light"),
    WATER("Water"),
    EXPLOSIONS("Explosions"),
    STATUS("Status"),
    OTHER("Other");

    private final String label;
    ParticleGroup(String label) { this.label = label; }

    /** English UI label (menu strings are English — see docs/DESIGN.md). */
    public String label() { return label; }
}
