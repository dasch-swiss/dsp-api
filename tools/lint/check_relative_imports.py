#!/usr/bin/env python3
"""Fail if any Scala source uses a top-level relative import.

Top-level relative imports (e.g. `import OntologyConstants.KnoraBase`, resolved
against a symbol already in scope rather than an absolute package path) are
disallowed: scalafmt is syntactic and reorders the top-level import block by
prefix, so it can hoist a relative import above the wildcard that brings its
prefix into scope and break the build. The old semantic scalafix rule that made
this safe is gone with sbt, so this check enforces "fully-qualified top-level
imports only" instead. Only column-0 imports are checked; local/member imports
inside a class or def (e.g. `import Codecs.*`) are idiomatic and left alone —
scalafmt never reorders them.

Heuristic: dsp-api's top-level packages are all lowercase (`org`, `dsp`, `scala`,
`java`, `zio`, `io`, `com`, `sttp`, ...) and explicit absolute imports use
`_root_.`. So any import whose first segment starts with an uppercase letter is a
relative import. Fix by fully-qualifying it (e.g.
`import org.knora.webapi.messages.OntologyConstants.KnoraBase`).

File paths are fed on argv as `$(rootpaths //modules/<m>:license_srcs)` by
//tools/lint:no_relative_imports. py_test (not sh_test) so it resolves the
hermetic interpreter registered in MODULE.bazel — same rationale as //tools/license.
"""
import re
import sys

# Column-0 `import` whose first path segment starts with an uppercase letter ==
# top-level relative import. Indented (local) imports are intentionally allowed:
# scalafmt never reorders them, so they cannot be hoisted out of scope.
RELATIVE_IMPORT_RE = re.compile(r"^import\s+[A-Z]")


def violations(path):
    out = []
    try:
        with open(path, encoding="utf-8") as f:
            for lineno, line in enumerate(f, start=1):
                if RELATIVE_IMPORT_RE.match(line):
                    out.append((lineno, line.rstrip()))
    except OSError as e:
        out.append((0, f"cannot read ({e})"))
    return out


def main(argv):
    paths = argv[1:]
    offenders = []
    for path in paths:
        for lineno, text in violations(path):
            offenders.append(f"{path}:{lineno}: {text}")
    if offenders:
        print("Relative imports are not allowed (use a fully-qualified import):")
        for o in offenders:
            print(f"  {o}")
        print(
            f"\n{len(offenders)} relative import(s). Replace each with its absolute "
            "package path, e.g. `import org.knora.webapi.messages.OntologyConstants.KnoraBase`."
        )
        return 1
    print(f"OK: no relative imports in {len(paths)} Scala files.")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
