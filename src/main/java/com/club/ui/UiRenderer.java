package com.club.ui;

public interface UiRenderer {
    void rect(float x, float y, float w, float h, int color);
    void roundedRect(float x, float y, float w, float h, float radius, int color);
    void roundedRect(float x, float y, float w, float h, Radii radii, int color);
    void border(float x, float y, float w, float h, float radius, float thickness, int color);

    void gradient(float x, float y, float w, float h, float radius, int colorA, int colorB, Axis axis);

    void shadow(float x, float y, float w, float h, float radius, float dx, float dy, float blur, int color);
    void glow(float x, float y, float w, float h, float radius, float size, int color);

    void line(float x1, float y1, float x2, float y2, float thickness, int color);
    void circle(float cx, float cy, float r, int color);

    void pushClip(float x, float y, float w, float h);
    void pushRoundedClip(float x, float y, float w, float h, float radius);
    void popClip();

    void pushOpacity(float multiplier);
    void popOpacity();

    boolean isResolutionIndependent();
}
