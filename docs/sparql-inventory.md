# SPARQL Generation Inventory

The migration input for the SPARQL builder decision
([docs/sparql-builder-approaches/decision.md](sparql-builder-approaches/decision.md)):
where SPARQL is generated in dsp-api today, by pattern. Regenerated 2026-08-27 against
`main` — the original spike inventory predated the Twirl removal (DEV-6231) and described
a world with 25 Twirl templates; Twirl is gone.

## Summary

| Pattern                                                              | Files                         | Key risk                                                    | Burn-down mechanism                                   |
|----------------------------------------------------------------------|-------------------------------|-------------------------------------------------------------|-------------------------------------------------------|
| RDF4J SparqlBuilder (main sources)                                   | 85                            | Low safety risk, poor readability                           | `tools/lint/sparqlbuilder_allowlist.txt` (CI ratchet) |
| — of which also use `s"..."` string interpolation around the builder | 14                            | **Injection risk** — interpolated text bypasses the builder | migrate first                                         |
| Raw `s"""..."""` SPARQL without the builder                          | ~17 (heuristic, needs triage) | **Injection risk** where values are interpolated            | migrate first                                         |
| Gravsearch templates (dsp-api's own query language)                  | subset of the above           | separate concern — Gravsearch is parsed, not passed through | out of scope for now                                  |

The authoritative, machine-checked burn-down list is
[`tools/lint/sparqlbuilder_allowlist.txt`](../tools/lint/sparqlbuilder_allowlist.txt) —
CI fails when a file outside it imports `org.eclipse.rdf4j.sparqlbuilder`, and when a
migrated file's entry isn't removed. This document adds the categories the allowlist
can't express.

## How the numbers are derived

```bash
# RDF4J SparqlBuilder importers (the ratchet's subject)
grep -rl 'org\.eclipse\.rdf4j\.sparqlbuilder' modules --include='*.scala'

# Files rendering via getQueryString (all builder-based query sites)
grep -rln 'getQueryString' modules/webapi/src/main/scala --include='*.scala'

# Heuristic: interpolated triple-quoted strings containing SPARQL keywords
grep -rlE 's"""' modules/webapi/src/main/scala --include='*.scala' \
  | xargs grep -liE 'SELECT |CONSTRUCT|INSERT|DELETE|WHERE \{|ASK'
```

## Migration priority

1. **Hybrid and raw string-interpolation sites** (the injection surface). Candidates
   found by the heuristic above, to be triaged (some hits are Gravsearch templates or
   false positives — constants interpolated into a query are noise, user input is signal):

   - `messages/util/standoff/XMLToStandoffUtil.scala`
   - `responders/admin/ListsResponder.scala`
   - `responders/v2/SearchResponderV2.scala`, `responders/v2/SearchQueries.scala`
   - `responders/v2/ValuesResponderV2.scala`
   - `slice/admin/domain/service/maintenance/TopLeftCorrectionAction.scala`
   - `slice/export/domain/ProjectDataGraphExistsQuery.scala`
   - `slice/ontology/repo/IsClassUsedInDataQuery.scala`, `IsEntityUsedQuery.scala`,
     `IsOntologyUsedQuery.scala`
   - `slice/resources/repo/IsListInUseQuery.scala`, `IsNodeUsedQuery.scala`
   - `slice/resources/repo/ResourceInfoRepoLive.scala`
   - `slice/resources/service/MetadataService.scala`
   - plus 14 files that mix `s"..."` interpolation with builder output (see the grep
     overlap of the first and third commands)

   Gravsearch template files (`slice/search/repo/*GravsearchQuery.scala`) are a separate
   category: Gravsearch is parsed and validated server-side, and migrating its templates
   is out of scope for the initial burn-down.

2. **Complex RDF4J-builder files** — where the readability win is largest, and the
   files most likely to be touched again:
   `slice/resources/repo/service/value/queries/InsertValueQueryBuilder.scala` (~750
   lines), `slice/resources/repo/GetResourceValueVersionHistoryQuery.scala`,
   `slice/resources/repo/service/ResourcesRepoLive.scala`, `responders/v2/SearchQueries.scala`.

3. **The long tail of simple CRUD builder files** — flat triple-list queries under
   `slice/*/repo/`. Mechanical ports; each verified by diffing rendered SPARQL against
   the old builder's `getQueryString` output, then its allowlist entry removed.

## Permanent allowlist entries

`modules/sparql-builder/src/test/scala/org/knora/sparqlbuilder/Rdf4jEscapingSpec.scala`
uses RDF4J as the escaping-parity oracle and stays on the allowlist indefinitely.
