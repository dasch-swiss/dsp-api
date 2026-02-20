---
title: "SPARQL: prefer FILTER NOT EXISTS over positive match for rare predicates"
date: 2026-02-19
category: performance
component: triplestore
module: webapi/slice/resources/repo
problem_type: query-performance
severity: high
symptoms: |
  The resourcesPerOntology endpoint was extremely slow (timeouts after ~120s for large projects).
  Total query time across 53 projects was ~302 seconds.
root_cause: |
  The SPARQL query used a positive triple pattern (`?s kb:isDeleted false`) to exclude deleted
  resources. Since almost no resources are deleted, this required Fuseki to scan and join against
  a massive index for the overwhelmingly common case.
tags:
  - sparql
  - fuseki
  - query-optimization
  - filter-not-exists
  - data-distribution
---

## Problem

The `resourcesPerOntology` endpoint was extremely slow for projects with many resources. Two projects intermittently timed out after ~120s, and the total time across all 53 projects on the dev database was ~302 seconds.

## Root Cause

The SPARQL query filtered out deleted resources with a positive triple pattern match:

```sparql
?s kb:isDeleted false
```

This required Fuseki to join against every `isDeleted` triple in the graph. Since virtually no resources are ever deleted, the triplestore was scanning a huge index just to confirm what is true for 99%+ of resources.

More generally: **data distribution matters for query optimization**. When one branch of a condition covers the vast majority of cases, the query pattern should be optimized for that majority. A positive match on a near-universal condition is expensive because it touches every matching triple. A negative existence check on the rare exception is cheap because it only needs to verify the absence of a few triples.

## Solution

Replace the positive triple pattern with a negative existence check:

```scala
// Before: matches every non-deleted resource (expensive — scans huge index)
val where = s.isA(toRdfIri(c)).andHas(KB.isDeleted, false).from(graph)

// After: skips the rare deleted resources (cheap — checks for absence)
val where = s.isA(toRdfIri(c)).filterNotExists(s.has(KB.isDeleted, true)).from(graph)
```

In SPARQL terms:

```sparql
# Before
?s a <class> .
?s kb:isDeleted false .

# After
?s a <class> .
FILTER NOT EXISTS { ?s kb:isDeleted true }
```

**Result:** 25.8x overall speedup (302s to 12s), with individual projects seeing up to 120x improvement. Projects that previously timed out now complete in 1-9 seconds.

## Prevention

- **Consider data distribution when writing queries**: Ask "which branch is the common case?" When filtering on a condition that is almost always true (or almost always false), structure the query to optimize for the common case.
- **Prefer `FILTER NOT EXISTS` for rare exceptions**: When excluding a small minority of results (soft-deleted records, flagged items, etc.), use `FILTER NOT EXISTS` to check for the rare exception rather than positively matching the common case.
- **Benchmark against realistic data**: This issue was invisible in unit tests with small datasets. Always validate query performance against production-scale data when optimizing.

## References

- PR #3961
- `webapi/src/main/scala/org/knora/webapi/slice/resources/repo/service/ResourcesRepoLive.scala:296`
