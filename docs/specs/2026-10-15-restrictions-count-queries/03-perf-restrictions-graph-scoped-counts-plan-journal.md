---
title: "Journal: graph-scoped restriction counts"
date: 2026-10-16
author: "Nora Ammann"
status: draft
repositories: []
---

# Journal: graph-scoped restriction counts

## 2026-10-16

- Plan approved. Decisions: keep Comment (F2), classify in SPARQL, 120 s count tier, cache TTL 60 min, same PR.
- Step 1 (`21fb264ab`): `SparqlTimeout.ViewRestrictionsCounts`, `view-restrictions-count-timeout = 120 seconds`,
  cache TTL 60 minutes.
- Steps 2–4 (`728b4bfab`), one commit: `ValueCountRow` changes shape, so repo, cache and services do not compile
  apart. `ViewRestrictionsCountQueriesSpec` is rewritten as a TriG integration test (two data graphs, one ontology
  graph), not deleted.
- Steps 2–4, behaviour change: `/property-values` no longer filters rows by the ontology cache's resource classes.
  A value of a class outside the cache now counts, as the property drill-down already listed it. The tests that
  asserted "loads no rows" for unknown and standoff properties are gone: the project rows load once and are
  filtered.
- Steps 2–4, fixtures: the service-spec fixtures now carry the property hierarchy (`rdfs:subPropertyOf`), because
  the counts classify in SPARQL.
- Step 5 (`07718d9c6`): page and count share one selection fragment. The only `join` in the optimised algebra is
  the cross product of the two `VALUES` tables; the barrier is a `sequence`. A test pins that.
- Step 6 (`4a78df5b7`): same shape for the property drill-down. The golden files lose the executable bit that
  earlier copies from Bazel outputs set.
- Step 7 (`71e513cf3`): docs. The link into the engine facts names Fact 1 without an anchor, because GitHub and
  mkdocs slug the em dash differently.
- Local TDB2 explain (Jena 6.2.0, handoff LIMC sample): the value count runs `?value isDeleted false`,
  `hasPermissions`, `?resource ?prop ?value`, `?resource isDeleted ?resDeleted`, `?resource rdf:type ?resClass`.
  The class and property drill-downs run `hasPermissions` from the literal first, then the fan-out, then the
  bound checks in the `sequence`.
- Verification: `just check` and `just test-unit` pass. The E2E specs did not run: the Docker daemon was not
  running. The stage check (verification step 5) is open and needs the owner's go-ahead.
