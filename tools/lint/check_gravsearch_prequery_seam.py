#!/usr/bin/env python3
"""Fail if a Gravsearch prequery can reach the triplestore other than through `executePrequery`.

`SearchResponderV2Live.executePrequery` is the single seam that runs a Gravsearch prequery (page or count) and
puts a `matchFulltext` prequery behind the fulltext breadth guard. In every main Scala source that constructs a
prequery transformer (`GravsearchToPrequeryTransformer(` or `GravsearchToCountPrequeryTransformer(`, with or
without `new`):

- a `Select` query (`Select(` or `Select.<tier>(`) may be built only inside `def executePrequery`;
- `def executePrequery` is defined exactly once, and its body calls `fulltextBreadthGuard.guarded`.

The check is textual: it catches the likely bypasses (a second `Select` next to the generators, a seam without
the guard), not every conceivable one. Other files may use `Select.gravsearch(` freely: it only picks the
Gravsearch timeout tier. Wiring: see //tools/lint:gravsearch_prequery_seam in tools/lint/BUILD.bazel.
"""
import re
import sys

# A construction, not the class definition itself.
TRANSFORMER_RE = re.compile(r"(?<!class )\bGravsearchTo(Count)?PrequeryTransformer\(")
SELECT_RE = re.compile(r"\bSelect(\.\w+)?\(")
GUARD_CALL = "fulltextBreadthGuard.guarded"
SEAM_DEF_RE = re.compile(r"^(\s*)(private\s+)?def\s+executePrequery\b")
# Any member definition; the seam ends at the next one at the seam's indentation or less.
DEF_RE = re.compile(
    r"^(\s*)((private|protected|override|final|implicit|case)\s+)*"
    r"(def|val|var|lazy\s+val|class|object|trait|type|given)\b"
)


def seam_lines(lines):
    """Line numbers (1-based) of the `executePrequery` body, or an empty set when there is no seam."""
    inside, indent, result = False, None, set()
    for lineno, line in enumerate(lines, start=1):
        seam = SEAM_DEF_RE.match(line)
        if seam:
            inside, indent = True, len(seam.group(1))
            result.add(lineno)
            continue
        if inside:
            member = DEF_RE.match(line)
            if member and len(member.group(1)) <= indent:
                inside = False
            else:
                result.add(lineno)
    return result


def violations(path):
    try:
        with open(path, encoding="utf-8") as f:
            lines = f.read().splitlines()
    except OSError as e:
        return [(0, f"cannot read ({e})")]
    if not any(TRANSFORMER_RE.search(line) for line in lines):
        return []
    seam = seam_lines(lines)
    out = [
        (lineno, line.strip())
        for lineno, line in enumerate(lines, start=1)
        if SELECT_RE.search(line) and lineno not in seam
    ]
    seam_defs = sum(1 for line in lines if SEAM_DEF_RE.match(line))
    if seam_defs != 1:
        out.append((0, f"constructs a prequery transformer but defines `executePrequery` {seam_defs} times, not once"))
    elif not any(GUARD_CALL in lines[n - 1] for n in seam):
        out.append((0, f"`executePrequery` does not call `{GUARD_CALL}`"))
    return out


def main(argv):
    paths = [p for p in argv[1:] if "/src/main/" in p]
    offenders = []
    for path in paths:
        for lineno, text in violations(path):
            offenders.append(f"{path}:{lineno}: {text}")
    if offenders:
        print("A Gravsearch prequery must reach the triplestore only through `executePrequery`:")
        for o in offenders:
            print(f"  {o}")
        print(
            f"\n{len(offenders)} violation(s). Run the prequery through `executePrequery`, which puts a "
            "`matchFulltext` prequery behind the fulltext breadth guard."
        )
        return 1
    print(f"OK: Gravsearch prequeries run only through executePrequery ({len(paths)} Scala files checked).")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
