package com.club.ui;

import com.club.ui.text.TextStyle;
import com.club.ui.text.Weight;
import java.util.List;

public interface UiText {
    float draw(String text, float x, float y, TextStyle style);
    void  drawWrapped(String text, float x, float y, float maxWidth, TextStyle style);
    float width(String text, Weight weight, float size);
    float ascent(Weight weight, float size);
    float descent(Weight weight, float size);
    float lineHeight(Weight weight, float size);
    List<String> wrap(String text, Weight weight, float size, float maxWidth);
    boolean isResolutionIndependent();
}
