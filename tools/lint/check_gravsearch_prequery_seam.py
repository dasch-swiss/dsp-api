#!/usr/bin/env python3
"""Fail if a Gravsearch prequery can reach the triplestore other than through `executePrequery`.

`SearchResponderV2Live.executePrequery` is the single seam that runs a Gravsearch prequery (page or count), and
the place where a `matchFulltext` prequery is put behind the fulltext breadth guard. A second
`triplestore.query(Select.gravsearch(...))` call next to the prequery generators would bypass that guard. So in
every main Scala source that constructs a prequery transformer (`new GravsearchToPrequeryTransformer(` or
`new GravsearchToCountPrequeryTransformer(`), `Select.gravsearch(` may occur only inside `def executePrequery`.

Other files may use `Select.gravsearch(` freely: it only picks the Gravsearch timeout tier, and the export queries
use it without being prequeries.

File paths are fed on argv as `$(rootpaths //modules/webapi:license_srcs)` by
//tools/lint:gravsearch_prequery_seam; only `src/main` sources are checked. py_test, like
//tools/lint:no_relative_imports, so it resolves the hermetic interpreter registered in MODULE.bazel.
"""
import re
import sys

TRANSFORMER_RE = re.compile(r"new\s+GravsearchTo(Count)?PrequeryTransformer\(")
GRAVSEARCH_SELECT = "Select.gravsearch("
SEAM_DEF_RE = re.compile(r"^(\s*)(private\s+)?def\s+executePrequery\b")
# Any member definition; the seam ends at the next one at the seam's indentation or less.
DEF_RE = re.compile(r"^(\s*)((private|protected|override|final|inline)(\[\w+\])?\s+)*(def|val|lazy\s+val)\s")


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
        if GRAVSEARCH_SELECT in line and lineno not in seam
    ]
    if not seam:
        out.append((0, "constructs a prequery transformer but has no `def executePrequery`"))
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
