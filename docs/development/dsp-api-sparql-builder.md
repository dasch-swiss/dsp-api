# SPARQL Builder: Decision and Migration

The design record for `modules/sparql-builder/` — dsp-api's SPARQL generation library —
and the plan for migrating everything else onto it. For the day-to-day API guide see the
[module README](../../modules/sparql-builder/README.md); for the general query-writing
rules see [dsp-api-sparql-queries.md](dsp-api-sparql-queries.md).

## Decision

All SPARQL generation uses **one style**: whole queries as `sparql"""..."""` interpolated
templates with typed holes (`Iri`, `Variable`, `Literal`), dynamic structure composed as
`Fragment` values. RDF4J SparqlBuilder, the incumbent, is **banned for new code** and
burns down to zero via a CI ratchet (`//tools/lint:sparqlbuilder_ratchet`, in `just check`).

## Why

This started as a design spike ([#4137](https://github.com/dasch-swiss/dsp-api/pull/4137))
against a codebase with three generation patterns: Twirl templates, RDF4J SparqlBuilder,
and raw string concatenation. While the spike sat, the Twirl migration (DEV-6231) completed
onto RDF4J SparqlBuilder — and reading the migrated files sharpened the case:

- **The comments are the real query.** Migrated files annotate nearly every builder line
  with the SPARQL it should produce (`GetResourceValueVersionHistoryQuery.scala`:
  `// ?property rdfs:subPropertyOf* knora-base:hasValue .`). When the notation needs a
  parallel translation to be legible, the notation has failed — and comments silently
  drift from code.
- **Optionality is a staircase.** `InsertValueQueryBuilder.buildFileValuePatterns` threads
  five `match`/`foldLeft` steps to emit five optional patterns; the interpolator writes
  each as one `Option.when(...)` fragment.

The Twirl→RDF4J migration traded readable-but-unsafe templates for safe-but-unreadable
builder code. The interpolated template (the Doobie/Skunk pattern) is the only point in
the design space that is both readable **and** safe:

| Criterion                | Interpolated template (chosen)    | RDF4J SparqlBuilder (incumbent)        |
|--------------------------|-----------------------------------|----------------------------------------|
| Readability              | Reads like SPARQL                 | Needs comment crutches                 |
| Composability            | `Fragment` monoid                 | Patterns compose, clauses don't        |
| Conditionals / iteration | `Option[Fragment]` / `combineAll` | `match`/`foldLeft` staircases          |
| Type safety              | Compile-time                      | Runtime (loosely typed Java API)       |
| Injection safety         | By construction (validated types) | By construction                        |
| New dependency           | None                              | None (present)                         |

### Alternatives rejected in the spike

- **AST case classes** — every triple a constructor call, and the AST had gaps (GRAPH,
  PREFIX, computed SELECT expressions) that fell back to raw escapes.
- **Fluent builders** (own or wrapping Jena `QueryBuilder`) — programmatic clause assembly
  expresses nothing a template can't; Jena's is also mutable, loosely typed, and a new
  dependency without GRAPH-scoped DELETE/INSERT.
- **Template + bind** (Jena `ParameterizedSparqlString` or a Scala equivalent) — no
  fragment composability, so dynamic structure collapses back into string concatenation;
  Jena's own docs call its protection "not foolproof".
- **The spike's own secondary builder surface** (`SparqlQuery.select(...).where(...)`) —
  dropped: two ways to write the same query is a built-in stumbling block, and dropping it
  removed the spike's only rendering bugs. **One style only.**

### Escaping parity makes migration verifiable

The module's literal escaping is byte-for-byte identical to RDF4J's
(`Rdf.literalOf(v).getQueryString`), pinned by `Rdf4jEscapingSpec` with RDF4J as a
test-only oracle. A migrated query is verified by diffing its rendered SPARQL against the
old builder's `getQueryString` output.

## Enforcement: hard rule with a grace period

- New code MUST use `org.knora.sparqlbuilder`; importing `org.eclipse.rdf4j.sparqlbuilder`
  in a file not on
  [`tools/lint/sparqlbuilder_allowlist.txt`](../../tools/lint/sparqlbuilder_allowlist.txt)
  fails CI.
- The allowlist only ever shrinks: when a file is migrated (or deleted), remove its entry —
  stale entries fail the check too.
- Only the `org.eclipse.rdf4j.sparqlbuilder` package is banned. RDF4J model classes
  (`org.eclipse.rdf4j.model.*`, `Vocabulary`) remain legitimate dependencies.
- One permanent entry: the module's own `Rdf4jEscapingSpec` (the escaping oracle).

## Migration plan

The allowlist (94 files at introduction) is the authoritative burn-down list. Derivation
of the categories below:

```bash
# RDF4J SparqlBuilder importers (the ratchet's subject)
grep -rl 'org\.eclipse\.rdf4j\.sparqlbuilder' modules --include='*.scala'

# Heuristic for raw/hybrid string-interpolated SPARQL (triage: constants are noise,
# user input is signal)
grep -rlE 's"""' modules/webapi/src/main/scala --include='*.scala' \
  | xargs grep -liE 'SELECT |CONSTRUCT|INSERT|DELETE|WHERE \{|ASK'
```

Priority order:

1. **Hybrid and raw string-interpolation sites** (~17 raw + 14 mixed with builder output —
   the actual injection surface). Gravsearch template files
   (`slice/search/repo/*GravsearchQuery.scala`) are out of scope: Gravsearch is parsed and
   validated server-side.
2. **Complex builder files** where the readability win is largest:
   `InsertValueQueryBuilder.scala` (~750 lines), `GetResourceValueVersionHistoryQuery.scala`,
   `ResourcesRepoLive.scala`, `SearchQueries.scala`.
3. **The long tail of simple CRUD builder files** — mechanical ports, each verified by the
   `getQueryString` diff, entry removed from the allowlist.

The first migration PR also wires `//modules/sparql-builder` into `//modules/webapi`.

## Later

- A `LuceneQuery` type gating Jena `text:query` strings (today: `Literal.string`)
- Optional parse-validation of rendered queries through Jena at the `TriplestoreService`
  boundary
- A shared vocabulary of `Iri` constants (knora-base etc.) in webapi
