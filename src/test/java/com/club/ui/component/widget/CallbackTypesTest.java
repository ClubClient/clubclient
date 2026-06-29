package com.club.ui.component.widget;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CallbackTypesTest {
    @Test void boolConsumerReceivesValue() {
        boolean[] got = { false };
        BoolConsumer cb = v -> got[0] = v;
        cb.accept(true);
        assertTrue(got[0]);
    }
    @Test void floatConsumerReceivesValue() {
        float[] got = { 0f };
        FloatConsumer cb = v -> got[0] = v;
        cb.accept(2.5f);
        assertEquals(2.5f, got[0], 1e-6);
    }
}
