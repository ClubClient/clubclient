package com.club.ui.component.widget;

/** Primitive float callback (Slider.onChange) — avoids boxing on user actions. */
@FunctionalInterface
public interface FloatConsumer { void accept(float value); }
