package com.club.policy;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Where Club stands down because the server says so.
 *
 * <p>One server our players use forbids item scrolling by rule. Its admins confirmed the ban and said
 * plainly that a workaround would cost us our reputation — so this is the opposite of a workaround: the
 * client turns the forbidden feature off there itself, and {@code ItemScrollMenu.notice()} tells the
 * player it did. Lunar and Badlion do the same on Hypixel. Nothing here hides anything from anyone.</p>
 *
 * <h2>What this promises, and what it cannot</h2>
 *
 * <p>It promises one thing: on a listed server Club will not send an item-scroll click. It does NOT
 * promise the player cannot scroll items at all — they can install the real Item Scroller, and Club
 * already stands aside for it ({@code ItemScrollModule.sibling()}). This is not a security boundary and
 * cannot be one: the client belongs to the player. It is Club declining to hand them a forbidden tool.</p>
 *
 * <h2>Why the table is hardcoded</h2>
 *
 * <p>A rule in {@code club_settings.json} is a rule the player edits out in ten seconds — that is a
 * bypass, shipped by us, and it is exactly what the admins warned against. The table is in the source,
 * where changing it means shipping a new jar. Not fetched over the network either: a remote switch over
 * someone's client is a far bigger promise than this problem needs.</p>
 *
 * <h2>An unknown address is allowed</h2>
 *
 * <p>Deliberate. The ban is one server's rule, not a default for the world, and silencing the feature
 * everywhere because of it would be wrong. The cost is a miss: joined by an address that is not listed,
 * the feature stays on. The mitigation is not to be quiet about it — the card's notice shows the state,
 * so a miss is visible rather than silent. A silent miss is the reputational hit the admins described.</p>
 */
public final class ServerPolicy {
    private ServerPolicy() {}

    /**
     * One server: every address it answers on, and what it forbids there.
     *
     * <p>Grouped by SERVER, not by address. A server hands out a domain, its subdomains and a pile of bare
     * IPs, and all of them are ONE rule. Keyed by address instead, the feature set would be copy-pasted
     * once per IP — and the day one copy drifts is the day the rule half-applies. Nobody would notice,
     * because a miss here is silent (there is no card left to look wrong).</p>
     *
     * <p>An address matches that host exactly, or any subdomain of it. Bare IPs match exactly — they have
     * no subdomains.</p>
     */
    public record ServerRule(Set<String> addresses, Set<ServerFeature> forbids) {}

    /**
     * The rules. One entry per server:
     * {@code new ServerRule(Set.of("example.ru", "203.0.113.7"), Set.of(ServerFeature.ITEM_SCROLL))}.
     *
     * <p>EMPTY ON PURPOSE, for now: the owner is collecting the full address and IP list from the admins
     * of the two servers that forbid item scrolling. Until it lands, the mechanism is built and tested and
     * Club behaves exactly as it always did on every server. Filling this in is one entry per server — and
     * delete {@code theShippedTableRestrictsNothingYet} in the test when you do.</p>
     *
     * <p>Put EVERY address a server answers on into its entry. A missing one is a miss, and a miss is a
     * player breaking a rule they were told this client would keep for them.</p>
     */
    private static final List<ServerRule> RULES = List.of();

    // ---- the pure rule ----------------------------------------------------------------------------

    /**
     * The host of a Minecraft server address: lower-cased, trimmed, without the port or the root dot.
     * Returns null for anything that is not an address.
     */
    static String host(String address) {
        if (address == null) return null;
        String s = address.trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty()) return null;

        if (s.startsWith("[")) {                      // [::1]:25565 — a bracketed IPv6 literal
            int end = s.indexOf(']');
            if (end < 0) return null;
            s = s.substring(1, end);
        } else {
            // One colon is a port. Two or more make it a bare IPv6 literal, whose colons are the address
            // itself — cutting at the last one would leave a host that matches nothing, and a rule that
            // matches nothing is a rule that quietly does not apply.
            int c = s.indexOf(':');
            if (c >= 0 && s.indexOf(':', c + 1) < 0) s = s.substring(0, c);
        }
        if (s.endsWith(".")) s = s.substring(0, s.length() - 1);   // the DNS root dot
        return s.isEmpty() ? null : s;
    }

    /**
     * What {@code rules} forbid at {@code address}. Pure — the table is a parameter so the rule can be
     * tested while the shipped table is still empty.
     */
    static Set<ServerFeature> lookup(List<ServerRule> rules, String address) {
        String host = host(address);
        if (host == null) return Set.of();

        for (ServerRule rule : rules)
            for (String key : rule.addresses())
                // "." + key, never a bare endsWith: the bare form makes notexample.ru a match for
                // example.ru, which would apply one server's rule to a stranger's.
                if (host.equals(key) || host.endsWith("." + key)) return rule.forbids();

        return Set.of();
    }

    /** What the shipped table forbids at {@code address}. */
    static Set<ServerFeature> restrictionsFor(String address) {
        return lookup(RULES, address);
    }

    // ---- the client adapter -----------------------------------------------------------------------

    /**
     * May {@code f} act where the player is standing right now?
     *
     * <p>Read live, never cached. A cache here would go stale exactly once — on the hop from a listed
     * server to any other, or back — and stale means either a dead feature or a forbidden one firing.
     * The cost is a handful of string operations on a mouse event, which is not a render loop.</p>
     */
    public static boolean allows(ServerFeature f) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.isInSingleplayer()) return true;   // no server, no rules
        ServerInfo entry = mc.getCurrentServerEntry();
        if (entry == null || entry.isLocal()) return true;      // LAN, or nowhere we can name
        return !restrictionsFor(entry.address).contains(f);
    }
}
