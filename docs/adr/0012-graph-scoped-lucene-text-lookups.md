# ADR-0012: Store the named graph in the Lucene index and scope project-restricted text lookups to the project graph

Date: 2026-10-20

Relates to: DEV-7377 (spike), DEV-7492 (lifting the Lucene hit limit), DEV-6851 (label search field),
DEV-6809 (c) (GRAPH-scoping the post-Lucene patterns), `modules/fuseki/dsp-repo.ttl` (the index configuration).

## Status

Proposed

## Context

Fulltext search in dsp-api (Gravsearch `matchFulltext`, `/v2/search`) starts with a Lucene lookup through
jena-text, `?s text:query ("term" limit)`. That lookup is unscoped: it returns every matching subject in the
repository, and the SPARQL that follows pays its per-hit joins for all of them before `attachedToProject`
discards the out-of-project ones. For a project-restricted search with a common term, the whole repository's
hits are paid for and almost all are thrown away.

The index configuration can store each indexed triple's named graph (`text:graphField`). Then a
`GRAPH <g> { ?s text:query ... }` pattern is evaluated inside Lucene as `(query) AND graph:<g>`, and the hit
limit applies after that filter. Every project's data, including its list nodes, lives in exactly one data
graph, `http://www.knora.org/data/{shortcode}/{shortname}`, and no list value points into another project's
graph or into the system lists (0 of 1.69 M on stage, 2026-10-20). So "the project" and "the project data
graph" are the same thing.

Measured on stage (a prod mirror) with 14 real slow prod cases across 7 projects, by emulating the scoped
lookup in SPARQL, which is a pessimistic bound:

- Out-of-project hits are 1.1–4× the in-project ones for LIMC, the largest project, and 6–247× for the others.
- At today's 10k hit cap, scoping saves at most about 1.6 s, because the matchFulltext prequery rewrite
  (DEV-7491) already took most of the cost; slow restricted searches dropped from about 13 % to 3 % of requests
  when it reached prod on 2026-10-14.
- With the full hit limit, restricted searches cost 30–55 s unscoped and at most 8 s scoped (Gravsearch) or
  17.5 s (`/v2/search` with a class restriction). The limit applied after scoping also makes 6 of the 11
  truncated cases complete without lifting the cap.

Verified on a local Fuseki 6.2.0 (the stack's image) loaded with 1.17 M quads pulled from stage: scoped lookups
return only in-graph hits; unscoped lookups are byte-identical with and without the field; `text:query` ignores
FROM clauses in both configurations; writes, the value-versioning shape and graph drops keep the index
correct; the real query shapes return identical sets scoped and unscoped. A full rebuild of the prod-sized
index takes about 6.5 minutes (30 nightly mirror runs, 390–408 s).

Two constraints come from how the configuration is deployed. `dsp-repo.ttl` lives on each host's persistent
`/fuseki` volume and is copied from the image only when missing, so a new image does not change a running
host. And the field only exists in Lucene documents written after it was enabled: a host with the field
configured but the index not rebuilt returns zero scoped hits for all existing data, while new writes are
indexed with their graph, so the index is silently partial.

## Decision

1. **The text index stores the named graph.** `:entMap` in `modules/fuseki/dsp-repo.ttl` carries
   `text:graphField "graph"`. The container entrypoint warns when a host's existing configuration lacks it.
2. **A project-restricted text lookup is wrapped in `GRAPH <projectDataGraph> { ... }`, and nothing else is.**
   Gravsearch `matchFulltext` (and `matchText`/`matchLabel` where a project restriction is known) and
   `/v2/search` with `limitToProject` scope only the Lucene statement. All other patterns stay on the union
   graph; a blanket GRAPH scope returns zero rows because the ontology patterns resolve against other graphs.
3. **The list-node branch is scoped to the project data graph alone.** No union with the system-lists graph,
   because no cross-graph list references exist and the platform does not allow them.
4. **The breadth probe of `/v2/search` mirrors the restriction.** Otherwise it keeps refusing scoped searches
   on unscoped breadth.
5. **Rollout order is a hard constraint.** The dsp-api query change that relies on scoping reaches a host only
   after that host's configuration has been edited and its index rebuilt. The order "rebuild, then revert the
   configuration" is safe (the GRAPH clause becomes a no-op again); the order "configure, then skip the rebuild"
   is not.
6. **The reindex is shared with DEV-6851.** `rdfs:label` gets its own index field in the same rebuild, and the
   fulltext lookup is changed to search both fields. `text:propField`, which DEV-6851 proposed, does not exist
   in Jena 6.2.0 and is silently ignored.
7. **Unscoped searches are out of scope.** Scoping gains them nothing; lifting the hit limit for them is
   DEV-7492's own decision.

## Consequences

- Project-restricted fulltext searches can take the full Lucene hit limit without the 30–55 s cost, and most
  of them stop being silently truncated. Unrestricted searches are unchanged.
- Each environment needs a one-off manual step: edit `/fuseki/configuration/dsp-repo.ttl` on the host and run
  `make db-rebuild-index`, with the database down for the rebuild (about 6.5 minutes plus container start).
  Mirrored environments pick the change up with their nightly rebuild once the file is edited.
- `MatchFulltextE2ESpec` loads its fixture into a graph that is not the project data graph; it must move, and
  every test of a scoped lookup needs the target graph to exist.
- DEV-6809 (c) is superseded for the main cost. What remains of it is dropping the `attachedToProject` join
  once the hits are already project-local.
- With the field on, `GRAPH ?g { text:query }` binds each hit to its graph. Without it that shape is a silent
  cross product (every hit bound to every graph). Nothing in dsp-api uses it; hand-written passthrough queries
  should know. `FROM NAMED <g>` does not scope a text lookup in either configuration.
- The prequery model gains a GRAPH pattern type, which it did not have.

## Alternatives rejected

- **GRAPH-scope only the post-Lucene patterns (DEV-6809 (c)).** Lucene still returns every hit and SPARQL still
  touches each one. It cannot reach the per-hit cost that scoping the lookup removes, and the measured prize
  of that lookup scoping is where the time goes.
- **Scope by adding a project term to the Lucene query text.** The index has no project field; adding one is
  the same reindex with a dsp-api-specific field instead of the one jena-text already supports and filters on.
- **Index the owning resource and its project alongside each value.** Removes the OPTIONAL join as well, but is
  a custom `TextDocProducer` and a much larger design; it remains open as DEV-6809 (f).
- **Do nothing, since DEV-7491 already made restricted searches fast at the cap.** True at the cap, but it
  leaves 20 % of matchFulltext searches truncated and blocks lifting the cap for restricted searches.
