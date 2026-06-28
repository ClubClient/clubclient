package com.club.ui.theme;
import com.club.ui.text.Weight;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TokensTest {
    @AfterEach void reset() { Tokens.setTheme(com.club.ui.theme.themes.ClubDark.create()); }

    @Test void defaultThemeHasAllGroups() {
        assertNotNull(Tokens.palette()); assertNotNull(Tokens.radius()); assertNotNull(Tokens.spacing());
        assertNotNull(Tokens.type()); assertNotNull(Tokens.surface()); assertNotNull(Tokens.accent());
        assertNotNull(Tokens.border()); assertNotNull(Tokens.shadow()); assertNotNull(Tokens.glow());
        assertNotNull(Tokens.elevation()); assertNotNull(Tokens.motion());
    }
    @Test void clubDarkValues() {
        assertEquals(0xFF0B111A, Tokens.surface().bg1());
        assertEquals(0xFF7CABFF, Tokens.accent().accent());
        assertEquals(0xFF78D7FF, Tokens.accent().gradientB());
        assertEquals(10f, Tokens.radius().md());
        assertEquals(6f,  Tokens.radius().sm());
        assertEquals(12f, Tokens.spacing().md());
        assertEquals(0.20f, Tokens.motion().durations().normal(), 1e-6);
    }
    @Test void surfaceReferencesPalette() {
        assertEquals(Tokens.palette().ink2(), Tokens.surface().bg1());   // bg1 == ink2 (#0B111A)
        assertEquals(Tokens.palette().accent(), Tokens.accent().accent());
    }
    @Test void typographyAllRoles() {
        Typography ty = Tokens.type();
        // display
        assertEquals(Weight.SEMIBOLD, ty.display().weight());
        assertEquals(20f,             ty.display().size());
        assertEquals(26f,             ty.display().lineHeight());
        // title
        assertEquals(Weight.SEMIBOLD, ty.title().weight());
        assertEquals(16f,             ty.title().size());
        assertEquals(22f,             ty.title().lineHeight());
        // heading
        assertEquals(Weight.MEDIUM,   ty.heading().weight());
        assertEquals(15f,             ty.heading().size());
        assertEquals(20f,             ty.heading().lineHeight());
        // body
        assertEquals(Weight.MEDIUM,   ty.body().weight());
        assertEquals(13f,             ty.body().size());
        assertEquals(18f,             ty.body().lineHeight());
        // label
        assertEquals(Weight.MEDIUM,   ty.label().weight());
        assertEquals(12f,             ty.label().size());
        assertEquals(16f,             ty.label().lineHeight());
        // caption
        assertEquals(Weight.REGULAR,  ty.caption().weight());
        assertEquals(12f,             ty.caption().size());
        assertEquals(16f,             ty.caption().lineHeight());
    }
    @Test void elevationLevel1HasShadow() {
        Elevation.Level l1 = Tokens.elevation().level1();
        assertNotNull(l1.shadow());
        assertEquals(Tokens.surface().surface(), l1.surface());
    }
    @Test void setThemeSwaps() {
        Theme base = ClubDarkRef();
        Theme alt = new Theme(base.palette(), new Radius(1,2,3,4,5), base.spacing(), base.type(), base.surface(),
            base.accent(), base.border(), base.shadow(), base.glow(), base.elevation(), base.motion());
        Tokens.setTheme(alt);
        assertEquals(3f, Tokens.radius().md());
    }
    private static Theme ClubDarkRef() { return com.club.ui.theme.themes.ClubDark.create(); }
}
