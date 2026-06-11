# SPARQL Builder Spike

A design spike to replace dsp-api's three fragmented SPARQL-generation patterns
(Twirl templates, hand-built RDF4J `SparqlBuilder` code, and raw string concatenation)
with one small, safe, composable query-building library.

## The decision in two sentences

We prototyped a wide design space and chose an **interpolated-template** API: write
queries as `sparql"..."` with typed holes (`Iri`, `Variable`, `Literal`, `Prefix`),
compose dynamic parts as `Fragment` values, and delegate string escaping to RDF4J.
It reads like raw SPARQL, prevents injection by construction, and adds no new dependency.

## Documents

| Document | What it covers |
|----------|----------------|
| [decision.md](decision.md) | The design space, comparison matrix, recommendation, and Phase 2 next steps |
| [recommended-approach.md](recommended-approach.md) | The chosen API in detail — vocabulary, two worked benchmarks, design notes, open questions |
| [alternatives-considered.md](alternatives-considered.md) | The rejected approaches (AST, fluent builder, Jena wrapper, template+bind) and why each lost |
| [reference-sparql.md](reference-sparql.md) | The 6 benchmark queries (scenario + target SPARQL) every approach was tested against |
| [../sparql-inventory.md](../sparql-inventory.md) | Inventory of existing SPARQL-generation sites in the codebase (input for migration) |

## Status

Phase 1 (design exploration) is complete: the API is chosen and a prototype module
lives in `modules/sparql-builder/`. Phase 2 (productionisation — RDF4J escaping in the
core types, migrating real query sites, integrating with `TriplestoreService`) has not
started. See [decision.md](decision.md#next-steps-phase-2).
