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

    @Test void theShippedTableRestrictsNothingYet() {
        // Until the owner has the addresses from the server's admins, Club's behaviour is unchanged on
        // every server in the world. This test is the proof of that, and it is meant to be DELETED in the
        // same commit that fills the table in.
        assertTrue(ServerPolicy.restrictionsFor("example.ru").isEmpty());
    }
}
