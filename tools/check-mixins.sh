#!/usr/bin/env bash
# Do the mixin's startup job, offline, before a human does it for us.
#
# WHY THIS EXISTS
#   An @Inject/@At target is a STRING. javac never reads it, so the mod compiles clean, passes every test,
#   ships every class — and dies on the first frame with "Scanned 0 target(s)", because "required": true
#   makes a missed target fatal. Ported that way, each broken injection costs one full game launch and a
#   human reading a crash log. We spent four of those before writing this.
#
# WHAT IT CHECKS
#   Every @Mixin target class, every method = "...", every @At(target = "L...;name(desc)ret") in the
#   STONECUTTER-RESOLVED sources for one version — against that version's real Minecraft jar.
#
# WHAT IT DOES NOT CHECK (say it out loud, a quiet gap is how instruments lie)
#   That an @At(value="INVOKE") call still OCCURS INSIDE its enclosing method. club$noSwapDipOnDamage
#   proved that gap is real: every name existed, and the injection still failed, because the call had moved
#   one frame down into a new helper. Launch remains the judge; this only makes launch worth doing.
#
# USAGE
#   tools/check-mixins.sh 1.21.8
set -uo pipefail
MC="${1:?usage: check-mixins.sh <mc-version>   e.g. 1.21.8}"

# Stonecutter only GENERATES a resolved copy for the inactive nodes; the active one compiles straight out
# of src/, where the //? comments already read the way that version wants. So: generated if present, src if
# not — and src IS the resolved form for whichever version stonecutter.gradle currently points at.
SRC="versions/$MC/build/generated/stonecutter/main/java/com/club/mixin"
if [ ! -d "$SRC" ]; then
    ACTIVE=$(grep -oP 'stonecutter\.active\s*"\K[^"]+' stonecutter.gradle 2>/dev/null)
    if [ "$MC" = "$ACTIVE" ]; then
        SRC="src/main/java/com/club/mixin"
    else
        echo "no resolved sources for $MC (active is '$ACTIVE') — run ./gradlew :$MC:compileJava first"; exit 2
    fi
fi
[ -d "$SRC" ] || { echo "no sources at $SRC"; exit 2; }

# The loom cache jar is mapped to NAMED (yarn) — the same names the sources use. No remapping needed;
# earlier this script compared intermediary against named and called everything MISSING.
MCJAR=$(find ~/.gradle/caches/fabric-loom/minecraftMaven -name "*merged*$MC*.jar" 2>/dev/null | head -1)
[ -n "$MCJAR" ] || { echo "no Minecraft $MC jar in the loom cache"; exit 2; }

echo "sources : $SRC"
echo "mc jar  : $(basename "$MCJAR")"
echo

# Does CLASS, OR ANY ANCESTOR, have a method called NAME?
#
# javap lists DECLARED members only, and a mixin does not care: an INVOKEVIRTUAL on ClientPlayerEntity
# resolves getAttackCooldownProgress up on PlayerEntity and always has. Without this walk the script cried
# wolf over THREE targets in the SHIPPED, WORKING 1.21.1 build — which is exactly why it gets calibrated
# against that build before it is ever pointed at a new one. An instrument is not trustworthy because it
# found something; it is trustworthy because it stays quiet where nothing is wrong.
has_method() {
    local cls="$1" name="$2" seen=0
    while [ -n "$cls" ] && [ "$seen" -lt 12 ]; do
        local out
        out=$(javap -p -classpath "$MCJAR" "$cls" 2>/dev/null) || return 1
        [ -z "$out" ] && return 1
        echo "$out" | grep -q "[ .]$name(" && return 0
        # NOT head -1: javap's first line is 'Compiled from "Foo.java"' and the declaration with `extends`
        # is the SECOND. Reading line one found no superclass, the walk never climbed, and three inherited
        # targets were reported broken in a build that ships and works.
        cls=$(echo "$out" | grep -m1 -oP '^[a-z ]*(class|interface) \S+ extends \K[A-Za-z0-9_.$]+')
        seen=$((seen + 1))
    done
    return 1
}

FAILFILE=$(mktemp); trap 'rm -f "$FAILFILE"' EXIT   # a counter inside a pipe lives in a subshell and dies there

STRIP=$(mktemp -d); trap 'rm -rf "$STRIP"' EXIT

for F0 in "$SRC"/*.java; do
    B=$(basename "$F0" .java)

    # STRIP COMMENTS FIRST, and it fixes two bugs with one cut:
    #   1. Javadoc prose. This script's first run "found" a broken getFramerateLimit — in a paragraph
    #      EXPLAINING that getFramerateLimit is gone. An instrument that reads documentation as code is an
    #      instrument that cries wolf, and a checker nobody believes is worse than no checker.
    #   2. Stonecutter's inactive branches, which ARE /* */ comments. Those are the other version's code and
    #      have no business being checked against this one.
    F="$STRIP/$B.java"
    perl -0pe 's{/\*.*?\*/}{}gs; s{//[^\n]*}{}g' "$F0" > "$F"

    # ---- @Mixin(Foo.class) -> the class we are injecting into -------------------------------------
    OWNER_SIMPLE=$(grep -oP '@Mixin\(\s*\K[A-Za-z0-9_]+(?=\.class)' "$F" | head -1)
    [ -n "$OWNER_SIMPLE" ] || continue
    OWNER=$(grep -oP "^import \K[a-z0-9.]+\.$OWNER_SIMPLE(?=;)" "$F" | head -1)
    [ -n "$OWNER" ] || OWNER="net.minecraft.$OWNER_SIMPLE"

    if ! javap -p -classpath "$MCJAR" "$OWNER" >/dev/null 2>&1; then
        echo "  CLASS GONE   $B  ->  $OWNER"; echo x >> "$FAILFILE"; continue
    fi
    # ---- method = "name" / "name(desc)ret" : the method we inject INTO ----------------------------
    for M in $(grep -oP 'method\s*=\s*"\K[a-zA-Z0-9_$]+' "$F" | sort -u); do
        if ! has_method "$OWNER" "$M"; then
            echo "  NO METHOD    $B  ->  $OWNER :: $M(...)"; echo x >> "$FAILFILE"
            continue
        fi
        # STATIC-NESS. Mixin refuses a non-static handler on a static target and says so only at startup:
        # "non-static callback method ... targets a static method which is not supported". 1.21.4 split
        # ParticleManager.renderParticles into private STATIC workers and that is exactly how it bit us —
        # after this script had already said the name resolved and the call site was there. Names and call
        # sites were never the whole contract; modifiers are part of it.
        TGT_STATIC=$(javap -p -classpath "$MCJAR" "$OWNER" 2>/dev/null | grep "[ .]$M(" | grep -c 'static')
        TGT_ANY=$(javap -p -classpath "$MCJAR" "$OWNER" 2>/dev/null | grep -c "[ .]$M(")
        # Only meaningful when every overload agrees; a mixed set needs the descriptor to disambiguate and
        # this check stays quiet rather than guessing (a checker that guesses is a checker nobody believes).
        if [ "${TGT_ANY:-0}" -gt 0 ] && [ "${TGT_STATIC:-0}" = "${TGT_ANY:-0}" ]; then
            HANDLER_STATIC=$(grep -A2 "method\s*=\s*\"$M" "$F" | grep -c 'private static\|public static')
            if [ "${HANDLER_STATIC:-0}" = "0" ] && grep -q "method\s*=\s*\"$M" "$F"; then
                echo "  STATIC MISMATCH  $B  ->  $OWNER::$M is static in $MC; the handler is not"
                echo "                   (mixin rejects this at STARTUP, never at compile time)"
                echo x >> "$FAILFILE"
            fi
        fi
    done

    # ---- @ModifyReturnValue : the handler's return type must EQUAL the target's ------------------
    # Mixin checks this at startup and nowhere earlier: the handler is bound to its target by NAME, so
    # javac has no idea the two are related. 1.21.2 narrowed GameRenderer.getFov from double to float and
    # our zoom kept returning double — clean build, clean tests, dead client
    # ("Found unexpected return type double, expected float"). Third class of failure this script missed
    # after names and call sites: first modifiers, now types. Same root every time — the contract between
    # handler and target is a whole SIGNATURE, and only part of it was ever being read.
    perl -0ne 'while (/\@ModifyReturnValue\s*\((.*?)\)\s*\n\s*(?:private|public|protected)\s+(?:static\s+)?(\S+)\s+(\w+\$\w+)/gs) {
                   my ($a, $ret, $h) = ($1, $2, $3);
                   my ($m) = $a =~ /method\s*=\s*"([a-zA-Z0-9_\$]+)/;
                   print "$m\t$ret\t$h\n" if $m;
               }' "$F" | sort -u |
    while IFS=$'\t' read -r M RET H; do
        # javap prints the declared return type in the signature line.
        TRET=$(javap -p -classpath "$MCJAR" "$OWNER" 2>/dev/null | grep -m1 "[ .]$M(" \
               | sed 's/^ *//; s/^\(public\|private|protected\|static\| \)*//' | awk '{for(i=1;i<=NF;i++) if($i ~ /'"$M"'\(/) {print $(i-1); exit}}')
        # Compare only the simple name — javap prints fully-qualified types, the source may import them.
        TSHORT="${TRET##*.}"; RSHORT="${RET##*.}"; RSHORT="${RSHORT%%<*}"
        # A TYPE VARIABLE (T, E, ...) erases to Object, and a handler returning Object is then correct.
        # Without this the script called MixinSimpleOption broken on the SHIPPING 1.21.1 build — the third
        # false positive calibration has caught, and the reason 1.21.1 is run first every single time.
        if echo "$TSHORT" | grep -qE '^[A-Z][0-9]?$'; then continue; fi
        if [ -n "$TSHORT" ] && [ -n "$RSHORT" ] && [ "$TSHORT" != "$RSHORT" ]; then
            echo "  RETURN TYPE  $B  ->  $H returns $RSHORT; $OWNER::$M returns $TSHORT in $MC"
            echo "               (@ModifyReturnValue must match exactly — mixin rejects this at STARTUP)"
            echo x >> "$FAILFILE"
        fi
    done

    # ---- @At(target = "Lowner;name(desc)ret") : the call we inject AT -----------------------------
    # [A-Za-z...] — a NAMED class path has capitals in it (ClientPlayerEntity). A lowercase-only class made
    # every target silently unmatchable, and the script reported "all clear" over a file it never parsed.
    for T in $(grep -oP 'L[A-Za-z0-9/$_]+;[A-Za-z0-9_$]+\([^)]*\)[A-Za-z\[][A-Za-z0-9/$_;]*' "$F" | sort -u); do
        TOWNER="${T%%;*}"; TOWNER="${TOWNER#L}"; TOWNER="${TOWNER//\//.}"
        TNAME="${T#*;}"; TNAME="${TNAME%%(*}"
        if ! javap -p -classpath "$MCJAR" "$TOWNER" >/dev/null 2>&1; then
            echo "  TARGET CLASS GONE  $B  ->  $TOWNER"; echo x >> "$FAILFILE"; continue
        fi
        if ! has_method "$TOWNER" "$TNAME"; then
            echo "  NO TARGET    $B  ->  $TOWNER :: $TNAME(...)"; echo x >> "$FAILFILE"
        fi
    done

    # ---- THE ONE THAT ACTUALLY BIT US ------------------------------------------------------------
    # (method = M, at = @At(INVOKE, target = T)) means: T is CALLED INSIDE M. Both can exist and the
    # injection still fail — that is exactly what happened to club$noSwapDipOnDamage, where 1.21.4 moved
    # the areEqual call out of updateHeldItems into a new helper. Names all resolved; the pair did not.
    # So pair them up and read M's real bytecode.
    # Capture from the annotation up to the method it decorates. NOT /\((.*?)\)/ — an @At target is itself
    # a descriptor full of parentheses, so a lazy match to the first ")" stops inside the target string and
    # finds nothing. This whole check sat silent and green for exactly that reason until it was asked
    # whether it saw ANY input at all. A check that cannot fail is not a check.
    perl -0ne 'while (/\@(?:Inject|WrapOperation|ModifyExpressionValue|Redirect|ModifyArgs?|ModifyVariable)\b(.*?)(?=\n\s*(?:private|public|protected)\s)/gs) {
                   my $a = $1;
                   my ($m) = $a =~ /method\s*=\s*"([a-zA-Z0-9_\$]+)/;
                   my ($t) = $a =~ /target\s*=\s*"L[A-Za-z0-9\/\$_]+;([A-Za-z0-9_\$]+)\(/;
                   print "$m\t$t\n" if $m && $t;
               }' "$F" | sort -u |
    while IFS=$'\t' read -r M T; do
        BODY=$(javap -c -p -classpath "$MCJAR" "$OWNER" 2>/dev/null | awk -v m="$M" '
            $0 ~ "[ .]"m"\\(" {f=1}
            f && /^  [a-zA-Z]/ && $0 !~ "[ .]"m"\\(" {f=0}
            f')
        if [ -n "$BODY" ] && ! echo "$BODY" | grep -q "\.$T:\|//.*$T"; then
            echo "  NOT CALLED   $B  ->  $T is not invoked inside $OWNER::$M in $MC"
            echo "               (the name exists; the CALL SITE moved or went — this is the one that"
            echo "                fails at startup with 'Scanned 0 target(s)' and a green build)"
            echo x >> "$FAILFILE"
        fi
    done
done

echo
if [ -s "$FAILFILE" ]; then
    echo "$(wc -l < "$FAILFILE") broken target(s) above. Fix them before asking anyone to launch."
    exit 1
fi
echo "every @Mixin class, method= and @At target resolves against $MC."
echo "Remember what this does NOT prove: that each INVOKE still occurs inside its enclosing method."
