package com.club.compat;

import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;

/**
 * Where the camera is, across Minecraft versions.
 *
 * <p><b>Why this class exists.</b> 1.21.11 deleted {@code Camera.getPos()} outright and replaced it with
 * {@code getCameraPos()} — not a rename of the same method, but a NEW method arriving from a NEW interface:
 * Camera now implements {@code TrackedWaypoint.YawProvider}, and {@code getCameraPos()} is that interface's.
 * The old method's intermediary ({@code method_19326}) does not appear anywhere in the 1.21.11 mappings; the
 * new one ({@code method_71156}) is declared on the interface, not on Camera.</p>
 *
 * <p><b>The boundary is 1.21.11, and it is measured, not guessed.</b> {@code Camera.getPos()} is present in
 * every Yarn mapping from 1.21.1 through 1.21.10 and absent in 1.21.11 — read out of the mappings for all
 * eleven versions on 2026-07-16. Both callers ({@link com.club.modules.perf.BlockEntityCull} and
 * {@link com.club.modules.perf.ParticleCull}) are per-frame culling code, so they ask here and stay
 * version-blind.</p>
 *
 * <p><b>What is NOT here, and why.</b> {@code Camera.getHorizontalPlane()} changed its RETURN TYPE in the
 * same release — {@code org.joml.Vector3f} through 1.21.10, {@code org.joml.Vector3fc} in 1.21.11 — and it
 * needs no seam at all: {@code Vector3f implements Vector3fc} in both JOML 1.10.5 (shipped with 1.21.1) and
 * 1.10.8 (1.21.11), so a caller that declares the READ-ONLY interface and reads {@code x()/y()/z()} compiles
 * unchanged on every version. A seam there would be ceremony around a widening conversion.</p>
 */
public final class Cam {
    private Cam() {}

    /** The camera's position in world space. */
    public static Vec3d pos(Camera cam) {
        //? if <1.21.11 {
        return cam.getPos();
        //?} else {
        /*return cam.getCameraPos();*/
        //?}
    }
}
