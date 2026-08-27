#!/usr/bin/env python3
"""Ratchet down (and keep out new) usage of RDF4J SparqlBuilder.

dsp-api is migrating all SPARQL generation to the in-house `sparql"..."` interpolator
(//modules/sparql-builder, see docs/sparql-builder-approaches/decision.md). RDF4J's
SparqlBuilder (`org.eclipse.rdf4j.sparqlbuilder.*`) is banned for new code; existing
usage sites are grandfathered in `sparqlbuilder_allowlist.txt` and burn down to zero
as they are migrated.

Two failure modes keep the ratchet honest:

  * a file OUTSIDE the allowlist imports `org.eclipse.rdf4j.sparqlbuilder` — new usage,
    write the query with the `sparql"..."` interpolator instead;
  * a file ON the allowlist no longer imports it (migrated, renamed, or deleted) —
    remove its line from the allowlist so the list only ever shrinks.

Note: only the `org.eclipse.rdf4j.sparqlbuilder` package is banned. RDF4J model classes
(`org.eclipse.rdf4j.model.*`, vocabularies, escaping utilities) remain legitimate
dependencies. One permanent allowlist entry is the sparql-builder module's own
`Rdf4jEscapingSpec`, which uses RDF4J as the escaping-parity oracle.

File paths are fed on argv as `$(rootpaths //modules/<m>:license_srcs)` by
//tools/lint:sparqlbuilder_ratchet; the allowlist arrives as the first argument.
py_test (not sh_test) so it resolves the hermetic interpreter registered in
MODULE.bazel — same rationale as //tools/license.
"""
import re
import sys

IMPORT_RE = re.compile(r"^import\s+org\.eclipse\.rdf4j\.sparqlbuilder\b")


def imports_sparqlbuilder(path):
    try:
        with open(path, encoding="utf-8") as f:
            return any(IMPORT_RE.match(line) for line in f)
    except OSError:
        return False


def main(argv):
    allowlist_path = argv[1]
    paths = argv[2:]

    with open(allowlist_path, encoding="utf-8") as f:
        allowlist = {line.strip() for line in f if line.strip() and not line.startswith("#")}

    importers = {p for p in paths if imports_sparqlbuilder(p)}

    new_usage = sorted(importers - allowlist)
    stale = sorted(allowlist - importers)

    ok = True
    if new_usage:
        ok = False
        print("New RDF4J SparqlBuilder usage is not allowed — write the query with the")
        print("`sparql\"...\"` interpolator from //modules/sparql-builder instead")
        print("(see docs/development/dsp-api-sparql-queries.md):")
        for p in new_usage:
            print(f"  {p}")
    if stale:
        ok = False
        print("Stale allowlist entries (file migrated, renamed, or deleted) — remove them")
        print(f"from {allowlist_path} so the ratchet only ever shrinks:")
        for p in stale:
            print(f"  {p}")

    if not ok:
        return 1
    print(
        f"OK: {len(importers)} grandfathered RDF4J SparqlBuilder file(s) remaining, "
        "no new usage."
    )
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
