package com.club.ui;

/** Handed to components/screens at render time. The only sanctioned render entry. */
public interface UiContext {
    UiRenderer renderer();
    UiText text();
    float time(); // seconds, for animations (Stage 2+)
}
