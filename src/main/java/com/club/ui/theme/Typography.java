package com.club.ui.theme;

import com.club.ui.text.Weight;

public record Typography(Role display, Role title, Role heading, Role body, Role label, Role caption) {
    public record Role(Weight weight, float size, float lineHeight) {}
}
