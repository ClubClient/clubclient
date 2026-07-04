package com.club.modules.animations;

/** A first-person hand transform: translation (in BLOCKS — raw MatrixStack units) + rotation (degrees). */
public class Pose {
    public float tx, ty, tz;
    public float rx, ry, rz;

    public Pose() {}

    public Pose set(float tx, float ty, float tz, float rx, float ry, float rz) {
        this.tx = tx; this.ty = ty; this.tz = tz;
        this.rx = rx; this.ry = ry; this.rz = rz;
        return this;
    }

    public boolean isIdentity() {
        return tx == 0 && ty == 0 && tz == 0 && rx == 0 && ry == 0 && rz == 0;
    }
}