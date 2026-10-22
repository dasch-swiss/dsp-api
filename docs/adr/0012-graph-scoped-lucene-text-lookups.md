# ADR-0012: Store the named graph in the Lucene index and scope project-restricted text lookups to the project graph

Date: 2026-10-20

Relates to: DEV-7377 (spike), DEV-7492 (lifting the Lucene hit limit), DEV-6851 (label search field),
DEV-6809 (c) (GRAPH-scoping the post-Lucene patterns), `modules/fuseki/dsp-repo.ttl` (the index configuration).

## Status

Accepted

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

Measured with 14 real slow prod cases across 7 projects: the hit ratios on stage (a prod mirror), the times on
dev with real scoping, after dev's index was rebuilt with the graph field (count queries, warm medians; the full
tables are on DEV-7377):

- Out-of-project hits are 1.1–4× the in-project ones for LIMC, the largest project, and 6–247× for the others.
- At today's 10k hit cap, scoping is 1.0–8.8× faster (median 1.8×), and every scoped case runs in at most 1.7 s.
  The gain is modest because the matchFulltext prequery rewrite (DEV-7491) already took most of the cost; slow
  restricted searches dropped from about 13 % to 3 % of requests when it reached prod on 2026-10-14.
- With the full hit limit, restricted Gravsearch searches go from up to 18.7 s unscoped to at most 4.7 s scoped,
  and `/v2/search` from up to 30 s to at most 4.6 s, except one class-restricted `/v2/search` case at 13.8 s.
  The limit applied after scoping also makes 6 of the 11 truncated cases complete without lifting the cap.

Verified on a local Fuseki 6.2.0 (the stack's image) loaded with 1.17 M quads pulled from stage: scoped lookups
return only in-graph hits; unscoped lookups are byte-identical with and without the field; `text:query` ignores
FROM clauses in both configurations; writes, the value-versioning shape and graph drops keep the index
correct; the real query shapes return identical sets scoped and unscoped. A full rebuild of the prod-sized
index takes about 6.5 minutes (30 nightly mirror runs, 390–408 s).

Two constraints come from how the configuration is deployed. `dsp-repo.ttl` lives on each host's persistent
`/fuseki` volume and was copied from the image only when missing, so a new image did not change a running
host (DEV-7589 changed that, see Decision 1). And the field only exists in Lucene documents written after
it was enabled: a host with the field configured but the index not rebuilt returns zero scoped hits for all
existing data, while new writes are indexed with their graph, so the index is silently partial.

## Decision

1. **The text index stores the named graph.** `:entMap` in `modules/fuseki/dsp-repo.ttl` carries
   `text:graphField "graph"`. The container entrypoint installs the configuration from the image on every start
   and rebuilds the index first when it changed (DEV-7589), so the field and a complete index arrive together.
2. **A project-restricted text lookup is wrapped in `GRAPH <projectDataGraph> { ... }`, and nothing else is.**
   Gravsearch `matchFulltext`, `matchText`, `matchTextInStandoff` and `matchLabel` where the lookup is anchored on
   the main resource of a project-restricted query, and `/v2/search` with `limitToProject`, scope only the Lucene
   statement. All other patterns stay on the union graph; a blanket GRAPH scope returns zero rows because the
   ontology patterns resolve against other graphs. Scoping to the data graph is exact because of the one-graph
   rule in the Resources & Values context (`docs/contexts/resources-values/CONTEXT.md`): a project's resources,
   values and list nodes live in exactly one data graph.
3. **The list-node branch needs no scope of its own.** It starts from the scoped hit, so it only reaches list
   nodes in the project data graph. No union with the system-lists graph, because no cross-graph list references
   exist and the platform does not allow them.
4. **The breadth probe of `/v2/search` mirrors the restriction.** Otherwise it keeps refusing scoped searches
   on unscoped breadth.
5. **Rollout order is a hard constraint.** The dsp-api query change that relies on scoping reaches a host only
   after that host runs a Fuseki image with the field, which rebuilds the index on the start that installs it.
   The order "rebuild, then revert the configuration" is safe (the GRAPH clause becomes a no-op again); the
   order "configure, then skip the rebuild" (`SKIP_AUTO_INDEX_REBUILD`) is not.
6. **DEV-6851's label field needs its own reindex.** Giving `rdfs:label` its own index field was meant to ride
   the same rebuild, but the graph field shipped without it (dsp-api#4393), so DEV-6851 needs a second rebuild,
   which runs automatically like the first (Decision 1). The fulltext lookup then has to search both fields.
   `text:propField`, which DEV-6851 proposed, does not exist in Jena 6.2.0 and is silently ignored.
7. **Unscoped searches are out of scope.** Scoping gains them nothing; lifting the hit limit for them is
   DEV-7492's own decision.

## Consequences

- Project-restricted fulltext searches can take the full Lucene hit limit without paying for every
  out-of-project hit, and most of them stop being silently truncated. Unrestricted searches are unchanged.
- Scoping applies only to Lucene lookups anchored on the main resource. `limitToProject` restricts the main
  resource; a lookup anchored elsewhere (`matchFulltext` on a linked resource, `matchText` on a linked resource's
  value) may legitimately hit another project's graph, because links across projects are allowed, so it stays
  unscoped.
- Each environment rebuilds its index once, automatically, on the first deploy of an image whose configuration
  differs from the host's, with the database down for the rebuild (about 6.5 minutes plus container start).
  That is an item for the deploy checklist, not for the release notes: users do not see it.
- A test of a scoped lookup needs its fixture in the project data graph (`MatchFulltextE2ESpec` loads it there),
  and the target graph has to exist.
- DEV-6809 (c) is superseded for the main cost. What remained of it, dropping the `attachedToProject` join once
  the hits are project-local, applies to `/v2/search` only. Gravsearch keeps the join on the main resource,
  which need not be the Lucene anchor. Without the join, `/v2/search` relies on the one-graph rule rather than
  enforcing it: a resource whose values or list nodes sat in another project's graph would be found by the wrong
  project's scoped search (stage had none on 2026-10-21).
- Search now emits SPARQL that names a project data graph, which ADR-0011 assigns to Resources & Values. This
  extends the Lucene queries Search already ran, which ADR-0011's ratchet leaves in place, and the
  `ProjectDataGraphs` port only resolves the graph's IRI. The GRAPH wrap moves with the Lucene queries when they
  go behind the `ResourceSearch` port; a new scoped lookup (such as `/v2/searchbylabel`) is the same temporary
  exception, not a precedent for new cross-context SPARQL.
- The scoped `/v2/search` breadth probe is 1.0–4.3× cheaper and counts exactly the in-project hits; the unscoped
  probe overstated small projects 14–74×. The breadth cap therefore applies to the in-project candidate count.
- Once scoped, the remaining cost of a class-restricted `/v2/search` is its per-candidate `rdfs:subClassOf*` walk
  (the 13.8 s case above runs in 4.6 s without it). Gravsearch avoids the walk with a `VALUES` list of classes.
  Removing it from `/v2/search` is tracked separately.
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
