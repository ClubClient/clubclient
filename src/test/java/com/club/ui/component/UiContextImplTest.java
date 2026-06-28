package com.club.ui.component;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UiContextImplTest {
    @Test void timeIsSettable() {
        UiContextImpl ctx = new UiContextImpl();
        assertEquals(0f, ctx.time(), 1e-6);
        ctx.setTime(1.5f);
        assertEquals(1.5f, ctx.time(), 1e-6);
    }
    @Test void delegatesRendererAndText() {
        UiContextImpl ctx = new UiContextImpl();
        assertNotNull(ctx.renderer());     // Ui falls back to LEGACY without init → non-null
        assertNotNull(ctx.text());
    }
}
