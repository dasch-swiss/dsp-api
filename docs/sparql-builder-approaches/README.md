# SPARQL Builder

The design record for dsp-api's SPARQL generation library: `sparql"..."` interpolated
templates over a composable `Fragment` monoid, implemented in `modules/sparql-builder/`.

## The decision in two sentences

We prototyped a wide design space and chose an **interpolated-template** API as the only
query style: write whole queries as `sparql"""..."""` with typed holes (`Iri`, `Variable`,
`Literal`), compose dynamic parts as `Fragment` values, with escaping byte-for-byte
identical to RDF4J's. It reads like raw SPARQL, prevents injection by construction, and
replaces RDF4J SparqlBuilder — which is banned for new code and burns down to zero via a
CI ratchet (`//tools/lint:sparqlbuilder_ratchet`).

## Documents

| Document                                                 | What it covers                                                                                                          |
|----------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------|
| [decision.md](decision.md)                               | Why: the design space, the comparison matrix, the enforcement (hard rule + grace period), next steps                    |
| [recommended-approach.md](recommended-approach.md)       | How: the API surface, the safety model, two worked benchmarks, design notes                                             |
| [alternatives-considered.md](alternatives-considered.md) | The rejected approaches — including the RDF4J incumbent and the spike's own dropped builder surface — and why each lost |
| [reference-sparql.md](reference-sparql.md)               | The 6 benchmark queries (scenario + target SPARQL) every approach was tested against                                    |
| [../sparql-inventory.md](../sparql-inventory.md)         | Inventory of the SPARQL-generation sites to migrate (the ratchet's burn-down)                                           |

For the day-to-day "how do I write a query" guidance, see
[docs/development/dsp-api-sparql-queries.md](../development/dsp-api-sparql-queries.md).

## History

This started as a design spike (PR [#4137](https://github.com/dasch-swiss/dsp-api/pull/4137))
against a codebase that still had Twirl templates. While the spike sat, the Twirl migration
(DEV-6231) completed onto RDF4J SparqlBuilder — whose readability problems at scale then
sharpened the case for the interpolator. The spike's core was ported to the Bazel build,
its validation gaps closed, and its secondary builder surface dropped; the stale parts
(sbt wiring, pre-Twirl-removal inventory, drifted examples) were discarded.
