package com.club.ui;

public final class Ui {
    private Ui() {}
    public enum Backend { MODERN, LEGACY }

    private static UiRenderer renderer;
    private static UiText text;
    private static Backend backend = Backend.MODERN;

    public static UiRenderer renderer() {
        if (renderer == null) throw new IllegalStateException("Ui not initialised");
        return renderer;
    }
    public static UiText text() {
        if (text == null) throw new IllegalStateException("Ui not initialised");
        return text;
    }
    public static Backend backend() { return backend; }
    public static void setBackend(Backend b) { backend = b; } // wired in Task 9
    public static boolean modernAvailable() { return false; }  // wired in Task 9
}
