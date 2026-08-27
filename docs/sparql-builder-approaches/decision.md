# Decision: SPARQL Builder

## Recommendation

Use an **interpolated-template API** — `sparql"..."` fragments with typed holes —
**backed by RDF4J's string escaping**. This is the synthesis of two prototyped ideas:
whole-query interpolation (former "Approach H") over a composable `Fragment` foundation
(former "Approach A"). Full detail in [recommended-approach.md](recommended-approach.md).

## Design space

The space has two independent dimensions:

| Dimension | Options prototyped |
|-----------|--------------------|
| **API style** | Interpolator, AST case classes, fluent builder, template + bind |
| **Escaping / backend** | Custom escaping, RDF4J escaping, Jena ARQ, Jena `ParameterizedSparqlString` |

The chosen point is **interpolator × RDF4J escaping**.

## Comparison matrix

| Criterion | Interpolated template (chosen) | AST case classes | Fluent builder | Jena wrapper | Template + bind |
|---|---|---|---|---|---|
| Readability | Excellent (reads like SPARQL) | Good | Good | Poor (Java API) | Good (raw SPARQL) |
| Composability | Excellent (`Fragment` monoid) | Good | Good | Poor (mutable) | Poor (monolithic) |
| Conditionals / iteration | Natural (`Option[Fragment]`) | Verbose | Natural | Imperative | String concat |
| Type safety | Compile-time | Runtime | Runtime | Runtime (`Object`) | Bind-time |
| Injection safety | By construction | By construction | By construction | Jena validation | "Not foolproof" (Jena docs) |
| New dependency | None (RDF4J already present) | None | None | jena-querybuilder | jena-arq |
| Benchmark coverage | All 6 | 3 of 6 | 3 of 6 | 3 of 6 | 2 of 6 |

## Key findings

- **Interpolation reads closest to SPARQL** — decisive for a team maintaining ~70 query
  files. `sparql"$s a $cls ."` is self-documenting; `TriplePattern(s, rdfType, cls)` and
  `triple(s, rdfType, cls)` are not.
- **Conditionals are the real test.** Immutable interpolation handles the Twirl `@if`
  equivalent naturally with `Option[Fragment]`; the Jena wrapper forces imperative
  mutation and template+bind collapses back into string concatenation — the exact
  anti-pattern this initiative exists to remove.
- **RDF4J escaping beats custom.** RDF4J's escaping is a strict superset of the hand-rolled
  version (it also handles `\f`, `\b`, and single quotes). Since RDF4J is already a
  dependency, using it internally costs nothing.
- **Jena's builders produce a validated AST but fight Scala idioms** — mutable, loosely
  typed (`Object`), can't compose/fork, and add a dependency.

## What to add later

- RDF4J escaping wired into the core `Literal`/`Fragment` (the prototype still uses custom escaping)
- IRI and variable-name validation (currently `unsafeFrom` bypasses it)
- Optional query validation by parsing rendered SPARQL through Jena
- AST nodes (from the rejected AST approach) only if query introspection is ever needed

## What to avoid

- **Jena `QueryBuilder` wrapping** — mutable API, new dependency, no composition
- **`ParameterizedSparqlString`** — incompatible with composable dynamic queries
- **Custom escaping** — RDF4J does it better and is already on the classpath

## Next steps (Phase 2)

1. Swap custom `escapeSparqlString` for RDF4J's escaping inside the core types
2. Promote the spike types from `modules/sparql-builder/` into a production module
3. Migrate 2–3 representative query sites (one per legacy pattern); see the
   [SPARQL inventory](../sparql-inventory.md)
4. Validate the API against real query requirements
5. Add IRI and variable-name validation
6. Integrate with `TriplestoreService`

## Test summary

The prototype carries 59 passing tests across the API surface and all six benchmarks,
including an injection-safety specification (`InjectionSafetySpec`) that pins down what
may be interpolated, how raw strings are gated, and how SPARQL and Lucene injection are
prevented.
