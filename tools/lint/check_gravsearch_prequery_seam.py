#!/usr/bin/env python3
"""Fail if a Gravsearch prequery can reach the triplestore other than through `executePrequery`.

`SearchResponderV2Live.executePrequery` is the single seam that runs a Gravsearch prequery (page or count) and
puts a `matchFulltext` prequery behind the fulltext breadth guard. In every main Scala source that constructs a
prequery transformer (`GravsearchToPrequeryTransformer(` or `GravsearchToCountPrequeryTransformer(`, with or
without `new`):

- a `Select` query (`Select(` or `Select.<tier>(`) may be built only inside `def executePrequery`;
- `def executePrequery` is defined exactly once, and its body calls `fulltextBreadthGuard.guarded`.

The check is textual: it catches the likely bypasses (a second `Select` next to the generators, a seam without
the guard), not every conceivable one; comment lines are ignored. Other files may use `Select.gravsearch(`
freely: it only picks the Gravsearch timeout tier. The check fails when no scanned file constructs a transformer,
so moving the construction out of the scanned files cannot make it pass vacuously. Wiring: see
//tools/lint:gravsearch_prequery_seam in tools/lint/BUILD.bazel.
"""
import re
import sys

# A construction, not the class definition itself.
TRANSFORMER_RE = re.compile(r"(?<!class )\bGravsearchTo(Count)?PrequeryTransformer\(")
SELECT_RE = re.compile(r"\bSelect(\.\w+)?\(")
GUARD_CALL = "fulltextBreadthGuard.guarded"
FIX_HINT = "a Gravsearch prequery must run only through `executePrequery`, which puts `matchFulltext` behind the guard"
SEAM_DEF_RE = re.compile(r"^(\s*)(private\s+)?def\s+executePrequery\b")
# Any member definition; the seam ends at the next one at the seam's indentation or less.
DEF_RE = re.compile(
    r"^(\s*)((private|protected|override|final|implicit|case)\s+)*"
    r"(def|val|var|lazy\s+val|class|object|trait|type|given)\b"
)


def is_comment(line):
    return line.lstrip().startswith(("//", "/*", "*"))


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
    """(violations, whether the file constructs a prequery transformer)."""
    try:
        with open(path, encoding="utf-8") as f:
            lines = f.read().splitlines()
    except (OSError, UnicodeDecodeError) as e:
        return [(0, f"cannot read as UTF-8 ({e})")], False
    code = [(n, line) for n, line in enumerate(lines, start=1) if not is_comment(line)]
    if not any(TRANSFORMER_RE.search(line) for _, line in code):
        return [], False
    seam = seam_lines(lines)
    out = [(n, line.strip()) for n, line in code if SELECT_RE.search(line) and n not in seam]
    seam_defs = sum(1 for line in lines if SEAM_DEF_RE.match(line))
    if seam_defs != 1:
        out.append((0, f"constructs a prequery transformer but has {seam_defs} `[private] def executePrequery`, not 1"))
    elif not any(GUARD_CALL in line for n, line in code if n in seam):
        out.append((0, f"`executePrequery` does not call `{GUARD_CALL}`"))
    return out, True


def main(argv):
    paths = [p for p in argv[1:] if "/src/main/" in p]
    offenders, constructing = [], 0
    for path in paths:
        found, constructs = violations(path)
        constructing += constructs
        offenders += [f"{path}:{lineno}: {text}" for lineno, text in found]
    if not constructing:
        offenders.append(
            f"no prequery transformer construction in the {len(paths)} scanned main Scala files; "
            "the check would pass vacuously. Point //tools/lint:gravsearch_prequery_seam at the files that build one."
        )
    if offenders:
        print(f"FAIL ({len(offenders)} seam violation(s)): {FIX_HINT}.")
        for o in offenders:
            print(f"  {o}")
        return 1
    print(f"OK: Gravsearch prequeries run only through executePrequery ({len(paths)} Scala files checked).")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
