package com.club.ui.component.widget;

/** Primitive boolean callback (Toggle/Checkbox.onChange) — avoids boxing on user actions. */
@FunctionalInterface
public interface BoolConsumer { void accept(boolean value); }
