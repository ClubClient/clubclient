package com.club.policy;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The matching rule, on its own. The table is injected rather than read from ServerPolicy.RULES: the
 * shipped table is EMPTY until the owner has the addresses, and a test over an empty table proves
 * nothing at all. This way the rule is covered now and the table is a one-line edit later.
 */
class ServerPolicyTest {

    private static final List<ServerPolicy.ServerRule> RULES = List.of(
            new ServerPolicy.ServerRule(Set.of("example.ru"), Set.of(ServerFeature.ITEM_SCROLL)));

    private static Set<ServerFeature> at(String address) { return ServerPolicy.lookup(RULES, address); }

    // ---- the host, normalised ---------------------------------------------------------------------

    @Test void aPortIsNotPartOfTheHost() {
        assertEquals("example.ru", ServerPolicy.host("example.ru:25565"));
    }

    @Test void theHostIsCaseInsensitiveAndUntrimmed() {
        assertEquals("example.ru", ServerPolicy.host("  Example.RU  "));
    }

    @Test void aTrailingRootDotIsNotPartOfTheHost() {
        assertEquals("example.ru", ServerPolicy.host("example.ru."));
    }

    @Test void anIpv6LiteralKeepsItsColons() {
        // A naive lastIndexOf(':') would turn [::1]:25565 into "[:" and match nothing — and a host that
        // matches nothing is a rule that silently does not apply, which is the failure we care about.
        assertEquals("::1", ServerPolicy.host("[::1]:25565"));
        assertEquals("::1", ServerPolicy.host("::1"));
    }

    @Test void aJunkAddressIsNoHost() {
        assertNull(ServerPolicy.host(null));
        assertNull(ServerPolicy.host("   "));
    }

    // ---- the lookup -------------------------------------------------------------------------------

    @Test void theListedHostIsRestricted() {
        assertEquals(Set.of(ServerFeature.ITEM_SCROLL), at("example.ru"));
        assertEquals(Set.of(ServerFeature.ITEM_SCROLL), at("example.ru:25565"));
    }

    @Test void aSubdomainOfTheListedHostIsRestricted() {
        assertEquals(Set.of(ServerFeature.ITEM_SCROLL), at("play.example.ru"));
        assertEquals(Set.of(ServerFeature.ITEM_SCROLL), at("mc.eu.example.ru"));
    }

    @Test void aHostThatMerelyEndsWithTheKeyIsNotRestricted() {
        // endsWith(key) rather than endsWith("." + key) would hand a STRANGER our restriction.
        assertTrue(at("notexample.ru").isEmpty());
        assertTrue(at("badexample.ru").isEmpty());
    }

    @Test void anUnknownHostIsUnrestricted() {
        assertTrue(at("hypixel.net").isEmpty());
        assertTrue(at(null).isEmpty());
    }

    @Test void aBareIpIsMatchedExactlyWhenListed() {
        List<ServerPolicy.ServerRule> byIp = List.of(
                new ServerPolicy.ServerRule(Set.of("203.0.113.7"), Set.of(ServerFeature.ITEM_SCROLL)));
        assertEquals(Set.of(ServerFeature.ITEM_SCROLL), ServerPolicy.lookup(byIp, "203.0.113.7:25565"));
        assertTrue(ServerPolicy.lookup(byIp, "203.0.113.8").isEmpty());
    }

    // ---- one server has many addresses; there is more than one server ------------------------------

    @Test void everyAddressOfOneServerCarriesTheSameRule() {
        // The real shape of the data: one server answers on a domain AND a pile of bare IPs, and they are
        // ONE rule. Keyed by address, the feature set would be copy-pasted per IP — and a copy that drifts
        // is a rule that half-applies, silently, which is the failure this whole file exists to avoid.
        ServerPolicy.ServerRule one = new ServerPolicy.ServerRule(
                Set.of("example.ru", "203.0.113.7", "203.0.113.8"), Set.of(ServerFeature.ITEM_SCROLL));
        for (String a : List.of("example.ru", "play.example.ru", "203.0.113.7", "203.0.113.8:25565"))
            assertEquals(Set.of(ServerFeature.ITEM_SCROLL), ServerPolicy.lookup(List.of(one), a),
                    "every address of the server must carry its rule: " + a);
    }

    @Test void twoServersKeepTheirOwnRules() {
        ServerPolicy.ServerRule a = new ServerPolicy.ServerRule(Set.of("aaa.ru"), Set.of(ServerFeature.ITEM_SCROLL));
        ServerPolicy.ServerRule b = new ServerPolicy.ServerRule(Set.of("bbb.ru"), Set.of());
        List<ServerPolicy.ServerRule> both = List.of(a, b);
        assertEquals(Set.of(ServerFeature.ITEM_SCROLL), ServerPolicy.lookup(both, "aaa.ru"));
        assertTrue(ServerPolicy.lookup(both, "bbb.ru").isEmpty());
        assertTrue(ServerPolicy.lookup(both, "ccc.ru").isEmpty());
    }

    // ---- the shipped table ------------------------------------------------------------------------
    // (theShippedTableRestrictsNothingYet lived here and asserted the table was empty. Astrum's addresses
    //  arrived, so the thing it proved stopped being true and it was deleted in the same commit — a test
    //  kept past its truth is worse than no test.)

    /** What Astrum forbids: item scrolling (ItemScroller/MouseTweaks) AND freelook (Perspective Mod). */
    private static final Set<ServerFeature> ASTRUM = Set.of(ServerFeature.ITEM_SCROLL, ServerFeature.FREELOOK);

    @Test void astrumForbidsItemScrollOnBothItsDomains() {
        for (String a : List.of("astrummc.net", "astrummc.su", "astrummc.net:25565", "ASTRUMMC.NET"))
            assertEquals(ASTRUM, ServerPolicy.restrictionsFor(a),
                    "Astrum forbids item scrolling, whichever door the player came through: " + a);
    }

    @Test void astrumForbidsFreelookBecauseItBansPerspectiveMod() {
        // Astrum's list names "Perspective Mod" and then bans anything "включающее в себя функционал"
        // of a listed mod. Freelook IS that functionality — camera off the aim — so the name it ships
        // under does not matter. Found months after v0.1 shipped it: nobody had read their list against
        // our own feature table.
        for (String a : List.of("astrummc.net", "astrummc.su", "server.astrummc.net"))
            assertTrue(ServerPolicy.restrictionsFor(a).contains(ServerFeature.FREELOOK),
                    "Astrum bans Perspective Mod, and freelook is Perspective Mod: " + a);
    }

    @Test void astrumsOwnSubdomainsAreCovered() {
        // Both SRV records point at server.astrummc.net — this is the host the client actually dials, and
        // it must carry the rule without being listed by hand.
        assertEquals(ASTRUM, ServerPolicy.restrictionsFor("server.astrummc.net"));
        assertEquals(ASTRUM, ServerPolicy.restrictionsFor("play.astrummc.su"));
    }

    @Test void aormioDoesNotForbidFreelook() {
        // A rule is ONE server's, never a default for the world — the whole reason an unknown address is
        // allowed. Aormio's list is written by function (Killaura, AutoTotem, AimAssist, Modified Packets):
        // automation and packet lies. Freelook is neither, and it does not appear there under any name.
        // Cutting it here too would be us inventing someone else's rule for them.
        for (String a : List.of("aormio.ru", "aormio.net", "mc.aormio.ru", "msk.aormio.net"))
            assertFalse(ServerPolicy.restrictionsFor(a).contains(ServerFeature.FREELOOK),
                    "Aormio does not ban freelook — do not invent a rule for a server that did not make it: " + a);
    }

    @Test void aormioForbidsItemScrollOnBothZones() {
        // The owner gave mc.aormio.ru. Looking found a second zone (mc.aormio.net) and, in both SRV
        // records, the host the client actually dials: msk.aormio.{ru,net}. Keying the ZONES covers every
        // one of those without listing them.
        for (String a : List.of("aormio.ru", "aormio.net", "mc.aormio.ru", "mc.aormio.net",
                                "msk.aormio.ru", "msk.aormio.net", "mc.aormio.ru:25565", "MC.AORMIO.RU"))
            assertEquals(Set.of(ServerFeature.ITEM_SCROLL), ServerPolicy.restrictionsFor(a),
                    "Aormio forbids item scrolling, whichever door: " + a);
    }

    @Test void theShippedTableTouchesNobodyElse() {
        // The rule is one server's, not a default for the world. A neighbour that merely ends with the same
        // letters is a stranger.
        for (String a : List.of("hypixel.net", "notastrummc.net", "astrummc.net.evil.com", "mc.example.ru",
                                "notaormio.ru", "aormio.ru.evil.com", "aormio.com"))
            assertTrue(ServerPolicy.restrictionsFor(a).isEmpty(), "must not restrict a stranger: " + a);
    }
}
