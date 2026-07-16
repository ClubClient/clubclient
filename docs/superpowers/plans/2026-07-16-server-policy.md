# Server Policy Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Club voluntarily and visibly stands down from features a server forbids by rule, driven by a hardcoded table that takes one line per server.

**Architecture:** New package `com.club.policy`. A pure, table-injected matcher (`lookup`) plus a thin `MinecraftClient` adapter (`allows`). Item Scroll is gated in three places — door (`active()`), act (`act()`), visibility (`notice()`) — mirroring the creative-inventory refusal already in that module. The rules table ships **empty**: the mechanism is complete and tested, and no server's behaviour changes until the owner supplies addresses.

**Tech Stack:** Java 21, Fabric 1.21.1, Yarn mappings, JUnit 5, Gradle + Loom.

**Spec:** `docs/superpowers/specs/2026-07-16-server-policy-design.md`

## Global Constraints

- **Code, comments, commits and UI strings: English.** Owner-facing docs (`docs/*.md`): Russian, matching `ARCHITECTURE.md`.
- **Notice strings: ~40 characters max** — `Label` does not wrap, longer is cut off (`ModuleNotices` javadoc). Prefix `Idle — ` when the module does nothing at all. No exclamation marks, no emoji, no apology.
- **The rules table is hardcoded.** Never config-backed, never fetched over the network — a player-editable rule is a bypass shipped by us (spec §7).
- **No unverified numbers or claims in comments** — the project has published falsehoods three times (`measurement-discipline`). If a claim isn't measured, don't write it.
- **Commit after each task**, locally, no push unless asked.
- **Rebuild the jar and copy it to `C:\Users\User\Desktop\Club v0.1.3\club-0.1.4.jar` after any code change** — the owner tests with the jar, not `runClient`.
- **Do not run the `runClient` harness.** The rule here is pure and unit-covered; harness runs are reserved and hang in the background (`harness-run-sparingly`).
- Build must stay green: `./gradlew build` → 242 tests passing before this work starts.

---

## File Structure

| File | Responsibility |
|---|---|
| `src/main/java/com/club/policy/ServerFeature.java` | **Create.** The enum of restrictable features. One constant per feature. |
| `src/main/java/com/club/policy/ServerPolicy.java` | **Create.** The rules table + pure matcher (`host`, `lookup`) + client adapter (`allows`). |
| `src/test/java/com/club/policy/ServerPolicyTest.java` | **Create.** Unit tests over the pure matcher with an injected fake table. |
| `src/main/java/com/club/modules/itemscroll/ItemScrollModule.java:51` | **Modify.** `active()` — the door. |
| `src/main/java/com/club/modules/itemscroll/ItemScrollHooks.java:139` | **Modify.** `act()` — the act. |
| `src/main/java/com/club/modules/itemscroll/ItemScrollMenu.java:55` | **Modify.** `notice()` — visibility. |
| `docs/ARCHITECTURE.md` | **Modify.** Register the new package and its seam. |

---

### Task 1: The pure core — feature enum, host normalisation, table lookup

**Files:**
- Create: `src/main/java/com/club/policy/ServerFeature.java`
- Create: `src/main/java/com/club/policy/ServerPolicy.java`
- Test: `src/test/java/com/club/policy/ServerPolicyTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces:
  - `enum com.club.policy.ServerFeature { ITEM_SCROLL }`
  - `static String ServerPolicy.host(String address)` — package-private; normalised host or `null`.
  - `static Set<ServerFeature> ServerPolicy.lookup(Map<String, Set<ServerFeature>> rules, String address)` — package-private; pure.
  - `static Set<ServerFeature> ServerPolicy.restrictionsFor(String address)` — package-private; `lookup(RULES, address)`.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/club/policy/ServerPolicyTest.java`:

```java
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
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew test --tests 'com.club.policy.ServerPolicyTest' --console=plain`
Expected: **compilation failure** — `package com.club.policy does not exist` / cannot find symbol `ServerFeature`.

- [ ] **Step 3: Write the minimal implementation**

Create `src/main/java/com/club/policy/ServerFeature.java`:

```java
package com.club.policy;

/**
 * A Club feature a server may forbid by rule. One constant per feature; the table in {@link ServerPolicy}
 * says where each one is restricted.
 *
 * <p>Only features that are actually forbidden somewhere belong here. This is not a capability list.</p>
 */
public enum ServerFeature {
    /** Moving items by mouse gesture — {@code com.club.modules.itemscroll}. */
    ITEM_SCROLL
}
```

Create `src/main/java/com/club/policy/ServerPolicy.java`:

```java
package com.club.policy;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

// (MinecraftClient/ServerInfo are imported in Task 2, with the adapter that uses them — an import with
//  no use in this commit is a loose end a reviewer has to chase.)

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
     * The rules. One line per server: {@code "example.ru", Set.of(ServerFeature.ITEM_SCROLL)}.
     * A key matches that host exactly, or any subdomain of it, or a bare IP written out in full.
     *
     * <p>EMPTY ON PURPOSE, for now: the owner is collecting the full address and IP list from the
     * server's admins. Until it lands, the mechanism is built and tested and Club behaves exactly as it
     * did on every server. Filling this in is a one-line edit — and delete
     * {@code theShippedTableRestrictsNothingYet} in the test when you do.</p>
     */
    private static final Map<String, Set<ServerFeature>> RULES = Map.of();

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
    static Set<ServerFeature> lookup(Map<String, Set<ServerFeature>> rules, String address) {
        String host = host(address);
        if (host == null) return Set.of();

        Set<ServerFeature> exact = rules.get(host);
        if (exact != null) return exact;

        for (Map.Entry<String, Set<ServerFeature>> e : rules.entrySet())
            // "." + key, never bare endsWith: the bare form makes notexample.ru a match for example.ru,
            // which would apply one server's rule to a stranger's.
            if (host.endsWith("." + e.getKey())) return e.getValue();

        return Set.of();
    }

    /** What the shipped table forbids at {@code address}. */
    static Set<ServerFeature> restrictionsFor(String address) {
        return lookup(RULES, address);
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew test --tests 'com.club.policy.ServerPolicyTest' --console=plain`
Expected: PASS — 11 tests, 0 failures.

- [ ] **Step 5: Run the full build — nothing else may break**

Run: `./gradlew build --console=plain`
Expected: `BUILD SUCCESSFUL`, 253 tests total (242 + 11), 0 failures.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/club/policy src/test/java/com/club/policy
git commit -F - <<'EOF'
feat(policy): the server-rule matcher, with an empty table

One server our players use forbids item scrolling by rule; its admins
confirmed the ban and said a workaround would cost us our reputation. This
is the opposite of a workaround — the client will stand down there itself,
visibly. Lunar and Badlion do the same on Hypixel.

The table is a parameter of lookup() rather than a static the test reads,
because the shipped table is empty until the addresses arrive from the
admins, and a test over an empty table proves nothing.

Hardcoded, not config-backed: a rule the player edits out in ten seconds is
a bypass shipped by us.

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
EOF
```

---

### Task 2: Gate Item Scroll — door, act, visibility

**Files:**
- Modify: `src/main/java/com/club/policy/ServerPolicy.java` → add the `MinecraftClient`/`ServerInfo` imports and `allows(ServerFeature)`
- Modify: `src/main/java/com/club/modules/itemscroll/ItemScrollModule.java:51-53`
- Modify: `src/main/java/com/club/modules/itemscroll/ItemScrollHooks.java:131-142`
- Modify: `src/main/java/com/club/modules/itemscroll/ItemScrollMenu.java:55-59`

**Interfaces:**
- Consumes: `ServerFeature.ITEM_SCROLL`, `ServerPolicy.restrictionsFor(String)` (Task 1).
- Produces: `public static boolean ServerPolicy.allows(ServerFeature f)` — true when the feature may act where the player is standing right now.

**Why there is no unit test for this task:** every line added reads `MinecraftClient.getInstance()`, which is null outside a running game — the project's unit tests never boot the client (that is why Task 1 exists in the shape it does). The rule itself is covered by Task 1; this task is wiring, and its proof is a green build plus the owner's jar. Do **not** reach for the `runClient` harness: it hangs in the background and the owner has asked for it to be run sparingly.

- [ ] **Step 1: Add the client adapter to `ServerPolicy`**

First add the two imports Task 1 deliberately left out:

```java
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
```

Then append to `src/main/java/com/club/policy/ServerPolicy.java`, after `restrictionsFor`:

```java
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
```

- [ ] **Step 2: Gate the door — `ItemScrollModule.active()`**

In `src/main/java/com/club/modules/itemscroll/ItemScrollModule.java`, replace:

```java
    /** Whether a gesture may fire right now: the module is on and no sibling mod owns the same gestures. */
    public static boolean active() {
        return ClubConfig.get().itemScroll.enabled && sibling() == null;
    }
```

with:

```java
    /** Whether a gesture may fire right now: the module is on, no sibling mod owns the same gestures, and
     *  the server we are on does not forbid item scrolling. */
    public static boolean active() {
        return ClubConfig.get().itemScroll.enabled
                && sibling() == null
                && ServerPolicy.allows(ServerFeature.ITEM_SCROLL);
    }
```

and add the imports beside the existing ones:

```java
import com.club.policy.ServerFeature;
import com.club.policy.ServerPolicy;
```

- [ ] **Step 3: Gate the act — `ItemScrollHooks.act()`**

In `src/main/java/com/club/modules/itemscroll/ItemScrollHooks.java`, inside `act()`, immediately after the creative refusal (`if (screen instanceof CreativeInventoryScreen) return;`), insert:

```java
        // The same reason the creative refusal is here and not only at the door: the harness calls act()
        // directly, and so could any future caller. A server whose rules forbid item scrolling must not be
        // able to see a click from us because someone reached past active().
        if (!ServerPolicy.allows(ServerFeature.ITEM_SCROLL)) return;
```

and add the imports:

```java
import com.club.policy.ServerFeature;
import com.club.policy.ServerPolicy;
```

- [ ] **Step 4: Gate visibility — `ItemScrollMenu.notice()`**

In `src/main/java/com/club/modules/itemscroll/ItemScrollMenu.java`, replace the body of `notice()`:

```java
    public static String notice() {
        String sibling = ItemScrollModule.sibling();
        if (sibling != null) return "Idle — " + sibling + " does this";
        return ClubConfig.get().itemScroll.enabled ? "Doesn't work in the creative inventory" : null;
    }
```

with:

```java
    public static String notice() {
        // First, because it is the line with a consequence: the player who does not know the module is off
        // here is the player who wonders why their scroll does nothing — and the player who does not know it
        // is ON here is the one who breaks a server rule without meaning to.
        if (ClubConfig.get().itemScroll.enabled && !ServerPolicy.allows(ServerFeature.ITEM_SCROLL))
            return "Idle — not allowed on this server";
        String sibling = ItemScrollModule.sibling();
        if (sibling != null) return "Idle — " + sibling + " does this";
        return ClubConfig.get().itemScroll.enabled ? "Doesn't work in the creative inventory" : null;
    }
```

and add the imports:

```java
import com.club.policy.ServerFeature;
import com.club.policy.ServerPolicy;
```

Also extend the method's javadoc — it currently promises "Two truths, in priority order". Change that phrase to "Three truths, in priority order" and add a sentence before the existing list:

```
 * <p>The server rule comes first: it is the only one of the three with a consequence outside the game.</p>
```

- [ ] **Step 5: Run the full build**

Run: `./gradlew build --console=plain`
Expected: `BUILD SUCCESSFUL`, 253 tests, 0 failures. The gates compile and nothing regressed.

- [ ] **Step 6: Refresh the owner's jar**

```bash
cp build/libs/club-0.1.4.jar "/c/Users/User/Desktop/Club v0.1.3/club-0.1.4.jar"
```

Expected: the file's timestamp is now. The owner tests with this jar.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/club/policy/ServerPolicy.java src/main/java/com/club/modules/itemscroll
git commit -F - <<'EOF'
feat(itemscroll): obey a server that forbids item scrolling

Three gates, because a guard at the door is not a guard on the act — the
same lesson the creative-inventory refusal in this module already carries:

  active()  the door. The module stands aside and the scroll reaches vanilla.
            Gating only the act would make fire() consume the event and hand
            the player a scroll that does nothing at all.
  act()     the act. The harness calls it directly, past the door.
  notice()  visibility. A miss must be visible; a silent one is the
            reputational hit the admins warned about.

The table is still empty, so no server's behaviour changes yet.

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
EOF
```

---

### Task 3: Document the seam

**Files:**
- Modify: `docs/ARCHITECTURE.md` — the package tree (near the `modules/` entries, ~line 90) and the seam table (~line 263)

**Interfaces:**
- Consumes: everything from Tasks 1–2.
- Produces: nothing in code.

- [ ] **Step 1: Read the two spots**

Run: `grep -n "itemscroll\|MixinHandledScreenAccessor" docs/ARCHITECTURE.md`
Expected: the package tree around line 90 and the seam table around line 263. Match the file's existing Russian prose and table shape exactly — do not restyle it.

- [ ] **Step 2: Add the package to the tree**

Add an entry beside the other top-level packages (`config`, `hud`, `modules`, `ui`, `util`), in the file's own style:

```
│   ├── policy/       (2 файла)      — Club соблюдает правила сервера  → specs/2026-07-16-server-policy-design.md
│   │   ├── ServerFeature           — что вообще бывает запрещено
│   │   └── ServerPolicy            — таблица правил (ЗАШИТА), чистое ядро lookup/host + адаптер allows
```

- [ ] **Step 3: Add the row to the seam table**

```
| Правила сервера (Club отходит в сторону) | [спека](superpowers/specs/2026-07-16-server-policy-design.md) | `policy/*`, гейты в `modules/itemscroll/*` |
```

- [ ] **Step 4: Verify the docs still render as a table**

Run: `grep -n -A2 -B2 "Правила сервера" docs/ARCHITECTURE.md`
Expected: the row sits inside the existing table, with the same column count as its neighbours.

- [ ] **Step 5: Commit**

```bash
git add docs/ARCHITECTURE.md
git commit -F - <<'EOF'
docs: register the policy package and its seam

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
EOF
```

---

## When the admins answer

Not part of this plan's tasks — this is the handover note for the session that gets the data.

1. Fill `ServerPolicy.RULES` — one line per address, domains and bare IPs both:
   ```java
   private static final Map<String, Set<ServerFeature>> RULES = Map.of(
           "example.ru",  Set.of(ServerFeature.ITEM_SCROLL),
           "203.0.113.7", Set.of(ServerFeature.ITEM_SCROLL));
   ```
2. Delete `theShippedTableRestrictsNothingYet` from `ServerPolicyTest` — it asserts the table is empty and it is meant to die here.
3. Add a test pinning the real table: the listed host and its subdomains restrict `ITEM_SCROLL`, a neighbouring host does not.
4. Any feature beyond Item Scroll: add the enum constant, then gate its module the same way — **door, act, visibility**. Never the door alone.
5. Rebuild, refresh the desktop jar, and let the owner confirm in game: join the server, open a chest, scroll — nothing moves, and the card reads "Idle — not allowed on this server".
