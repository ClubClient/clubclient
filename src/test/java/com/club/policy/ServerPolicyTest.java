package com.club.policy;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The matching rule, on its own. The table is injected rather than read from ServerPolicy.RULES: the
 * shipped table is EMPTY until the owner has the addresses, and a test over an empty table proves
 * nothing at all. This way the rule is covered now and the table is a one-line edit later.
 */
class ServerPolicyTest {

    private static final Map<String, Set<ServerFeature>> RULES =
            Map.of("example.ru", Set.of(ServerFeature.ITEM_SCROLL));

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
        Map<String, Set<ServerFeature>> byIp = Map.of("203.0.113.7", Set.of(ServerFeature.ITEM_SCROLL));
        assertEquals(Set.of(ServerFeature.ITEM_SCROLL), ServerPolicy.lookup(byIp, "203.0.113.7:25565"));
        assertTrue(ServerPolicy.lookup(byIp, "203.0.113.8").isEmpty());
    }

    // ---- the shipped table ------------------------------------------------------------------------

    @Test void theShippedTableRestrictsNothingYet() {
        // Until the owner has the addresses from the server's admins, Club's behaviour is unchanged on
        // every server in the world. This test is the proof of that, and it is meant to be DELETED in the
        // same commit that fills the table in.
        assertTrue(ServerPolicy.restrictionsFor("example.ru").isEmpty());
    }
}
