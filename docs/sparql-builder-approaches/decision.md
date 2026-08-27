# Decision: SPARQL Builder

## Recommendation

Use an **interpolated-template API** — whole queries as `sparql"""..."""` templates with
typed holes, dynamic structure composed as `Fragment` values — as the **only** SPARQL
generation style in dsp-api. Full detail in [recommended-approach.md](recommended-approach.md);
the implementation lives in `modules/sparql-builder/`.

RDF4J SparqlBuilder, the current incumbent, is **banned for new code** and burns down to
zero via a CI ratchet (`//tools/lint:sparqlbuilder_ratchet`, wired into `just check`).

## Context: how the landscape changed under this spike

The spike originally targeted a three-pattern world: Twirl templates, RDF4J SparqlBuilder,
and raw string concatenation. While the spike branch sat, the Twirl migration (DEV-6231)
completed — all ~25 templates were rewritten onto RDF4J SparqlBuilder, which became the
documented house convention.

Reading the migrated files motivates this decision more sharply than the original framing:

- **The comments are the real query.** Migrated files annotate nearly every builder line
  with the SPARQL it is supposed to produce (e.g. `GetResourceValueVersionHistoryQuery.scala`:
  `// ?property rdfs:subPropertyOf* knora-base:hasValue .`). When the notation needs a
  parallel translation to be legible, the notation has failed — and the comments can
  silently drift from the code.
- **Optionality is a staircase.** `InsertValueQueryBuilder.buildFileValuePatterns` threads
  five `match`/`foldLeft` steps to emit five optional patterns; the interpolator writes
  each as one `Option.when(...)` fragment.

The Twirl→RDF4J migration traded readable-but-unsafe templates for safe-but-unreadable
builder code. The interpolated template is the only point in the design space that is both
readable **and** safe.

## Design space

The space has two independent dimensions:

| Dimension              | Options prototyped                                                          |
|------------------------|-----------------------------------------------------------------------------|
| **API style**          | Interpolator, AST case classes, fluent builder, template + bind             |
| **Escaping / backend** | Custom escaping, RDF4J escaping, Jena ARQ, Jena `ParameterizedSparqlString` |

The chosen point is **interpolator × RDF4J-identical escaping** (implemented in the module
itself, byte-for-byte equal to RDF4J's and pinned by a parity spec with RDF4J as the
test-only oracle — the module has no main-source dependencies).

## Comparison matrix

| Criterion                | Interpolated template (chosen)    | RDF4J SparqlBuilder (incumbent)        | AST case classes | Fluent builder  | Jena wrapper       | Template + bind             |
|--------------------------|-----------------------------------|----------------------------------------|------------------|-----------------|--------------------|-----------------------------|
| Readability              | Excellent (reads like SPARQL)     | Poor (needs comment crutches)          | Good             | Good            | Poor (Java API)    | Good (raw SPARQL)           |
| Composability            | Excellent (`Fragment` monoid)     | Poor (patterns compose, clauses don't) | Good             | Good            | Poor (mutable)     | Poor (monolithic)           |
| Conditionals / iteration | Natural (`Option[Fragment]`)      | Staircase (`match`/`foldLeft` chains)  | Verbose          | Natural         | Imperative         | String concat               |
| Type safety              | Compile-time                      | Runtime (loosely typed Java API)       | Runtime          | Runtime         | Runtime (`Object`) | Bind-time                   |
| Injection safety         | By construction (validated types) | By construction                        | By construction  | By construction | Jena validation    | "Not foolproof" (Jena docs) |
| New dependency           | None                              | None (present)                         | None             | None            | jena-querybuilder  | jena-arq                    |
| Benchmark coverage       | All 6                             | All 6 (in production)                  | 3 of 6           | 3 of 6          | 3 of 6             | 2 of 6                      |

## Key findings

- **Interpolation reads closest to SPARQL** — decisive for a team maintaining ~90 query
  files. `sparql"$s a $cls ."` is self-documenting; `s.isA(cls)` needs a comment showing
  what it renders, and the migrated codebase demonstrates that at scale.
- **Conditionals are the real test.** Immutable interpolation handles dynamic structure
  naturally with `Option[Fragment]`; the RDF4J builder forces conditional chaining, the
  Jena wrapper forces imperative mutation, and template+bind collapses back into string
  concatenation — the exact anti-pattern this initiative exists to remove.
- **One style only.** An earlier spike revision exposed a secondary programmatic builder
  surface next to the templates. It was dropped: a second way to write the same query is a
  built-in stumbling block, and it re-implemented what templates already express.
- **Escaping parity makes migration verifiable.** Because literals render byte-for-byte as
  RDF4J renders them, a migrated query can be verified by diffing its rendered SPARQL
  against the old builder's `getQueryString` output.

## Enforcement: hard rule with a grace period

- New code MUST use `org.knora.sparqlbuilder`; importing `org.eclipse.rdf4j.sparqlbuilder`
  in a file not on the allowlist fails CI (`//tools/lint:sparqlbuilder_ratchet`).
- Existing importers are grandfathered in `tools/lint/sparqlbuilder_allowlist.txt`. The
  list only ever shrinks: when a file is migrated (or deleted), its entry must be removed,
  and stale entries fail the check.
- Only the `org.eclipse.rdf4j.sparqlbuilder` package is banned. RDF4J model classes
  (`org.eclipse.rdf4j.model.*`, `Vocabulary`, values) remain legitimate dependencies.
- The grace period ends when the allowlist is empty (one permanent entry remains: the
  module's own `Rdf4jEscapingSpec`, which uses RDF4J as the escaping oracle).

## What to add later

- A `LuceneQuery` type gating Jena `text:query` strings (today: `Literal.string`)
- Optional query validation by parsing rendered SPARQL through Jena at the
  `TriplestoreService` boundary
- A shared vocabulary of `Iri` constants (knora-base etc.) in webapi, so query sites don't
  re-derive them

## What to avoid

- **A second query-building style** — no programmatic clause builders, in this library or
  next to it
- **Jena `QueryBuilder` wrapping** — mutable API, new dependency, no composition
- **`ParameterizedSparqlString`** — incompatible with composable dynamic queries
- **Path operators inside `Iri` values** — the star sits outside the hole:
  `sparql"$cls $subClassOf* $target"`

## Next steps

1. Migrate 2–3 representative query sites (one per legacy pattern) to validate the API
   against real requirements; verify by diffing rendered SPARQL against the incumbent's
   `getQueryString` output. Good first targets: `GetResourceValueVersionHistoryQuery`
   (every builder pathology in one small file) and one hybrid `getQueryString`
   string-interpolation site (real injection exposure).
2. Wire `//modules/sparql-builder` into `//modules/webapi` with the first migration.
3. Burn down `tools/lint/sparqlbuilder_allowlist.txt` (~85 main-source files + hybrid
   sites; see [../sparql-inventory.md](../sparql-inventory.md)).

## Test summary

The module carries tests across the API surface and the benchmark queries, including an
injection-safety specification (`InjectionSafetySpec`) that pins down what may be
interpolated, how raw strings are gated, how validated construction rejects breakout
payloads in IRIs, variable names, and language tags, and how SPARQL and Lucene injection
are prevented — plus a byte-for-byte escaping-parity spec against RDF4J
(`Rdf4jEscapingSpec`).
