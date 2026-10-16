---
title: "Restrictions overview: count queries with classification in Scala (option C), measured on stage"
date: 2026-10-15
author: "Nora Ammann"
status: draft
repositories: []
linear: DEV-7390
---

# Restrictions overview: count queries with classification in Scala (option C)

## Result

1. One shape works: anchor on the project, read every value of each resource with an unbound predicate,
   and group by property and permission text. Scala then decides which properties count. The shape uses
   no property path, no `FILTER`, no `REGEX`, and no `FILTER NOT EXISTS`.
2. The counts are identical to the current queries on all three projects (0 differences). In addition, the
   shape counts the user link values that the current queries drop (DEV-7390): 59,963 in drawings-gods,
   42,635 in BEOL, 428,682 in LIMC.
3. Resource counts (R) take 0.4–2.8 s, against 2.5–16.4 s for the current step 1.
4. Value counts per class (C5) take as long as the current step 2, or less (LIMC: 64 s against 91 s in
   total). The slowest single LIMC query is 22 s, against 30 s today.
5. The same result rows also give the by-property counts. The current by-property loop (23–156 s for
   each project) is no longer needed.
6. One query for the whole project (C6) is the fastest in total, but takes 56 s on LIMC. That is more
   than the 40 s view-restrictions timeout.
7. Pattern order decides everything. A plain `?value knora-base:isDeleted false` makes Jena scan every
   non-deleted object in the store before `?value` is bound. That made the first four variants time out.

## Context

The restrictions overview reads its counts in two steps: step 1 (resource counts for all classes) and
step 2 (value counts, one request for each class). The by-property report sends one request for each
property. Large projects are slow, and the value queries drop every `knora-base:LinkValue`, which hides
restricted user links (DEV-7390).

Option C moves the classification into Scala. These decisions depend only on the ontology or on the
permission text:

- Is the property a value property?
- Is the value a file value?
- Is the value a standoff link value?
- Is the class a resource class?

SPARQL keeps the scan and the aggregation, so no per-object data crosses the wire. Only counts do.

This measurement covers the count queries only. It does not cover the drill-down queries.

## Setup

- Server: stage Fuseki (`db.stage.dasch.swiss`), read-only `SELECT` over HTTPS.
- Projects:

  | Project | Shortcode | Resources | Values (current scope) |
  | --- | --- | --- | --- |
  | drawings-gods | 0105 | 41,699 | 268,360 |
  | BEOL | 0801 | 29,960 | 114,374 |
  | LIMC | 080E | 308,257 | 1,241,216 |

- Queries run one at a time. Each variant ran 3 times; run 1 is the cold run. The tables give the median.
  For C5 and C6, there was a 1 s pause before each query.
- The current queries are rendered exactly as on `main` (72dd89360). The by-property property list is the
  value properties of the project ontologies and knora-base, as `isValueProperty` selects them.
- Times are the sum of sequential queries. The frontend sends step-2 requests in parallel. Its wall time
  is shorter, but the server does the same total work.
- Incident: in run 3 of the LIMC by-property loop, stage stopped responding and then returned HTTP 403
  for all requests, also without credentials. Later, access worked again. Run 3 of that loop is not used.
  The other runs are complete.

## Measured queries

### R: resource counts

Replaces `projectClassesQuery`, both multi-type probes and `resourceCountsByClassAndPermissionQuery`.
Scala drops classes that are not resource classes (list nodes, ontologies).

```sparql
SELECT ?resClass ?permissions (COUNT(*) AS ?cnt)
WHERE {
  ?resource knora-base:attachedToProject <project> .
  ?resource a ?resClass ;
    knora-base:hasPermissions ?permissions ;
    knora-base:isDeleted false .
}
GROUP BY ?resClass ?permissions
```

### C5: value counts, one query per class

Replaces `valueCountsByPermissionQuery`.

```sparql
SELECT ?prop ?permissions ?valueDeleted (COUNT(*) AS ?cnt) (COUNT(?comment) AS ?commented)
WHERE {
  ?resource a <class> .
  ?resource knora-base:attachedToProject <project> .
  ?resource knora-base:isDeleted false .
  ?resource ?prop ?value .
  ?value knora-base:hasPermissions ?permissions .
  ?value knora-base:isDeleted ?valueDeleted .
  OPTIONAL { ?value knora-base:valueHasComment ?comment . }
}
GROUP BY ?prop ?permissions ?valueDeleted
```

### C6: value counts, one query per project

The same as C5, with `?resource a ?resClass` and `?resClass` in `GROUP BY`.

### Scala-side classification (used for the comparison)

- Keep rows where `?valueDeleted` is `false` and the class is a resource class.
- Keep rows whose property is a sub-property of `knora-base:hasValue`. This drops literals and link
  targets: a resource behind `hasLinkTo` also has `hasPermissions`.
- File value: the property is a sub-property of `knora-base:hasFileValue`. Comment count: `?commented`.
- Exclude `knora-base:hasStandoffLinkToValue`. Keep all other link values (the DEV-7390 fix).
- `COUNT(*)` is exact: a value belongs to one resource and one property, and no resource in the three
  projects has two types (both multi-type probes: no).

## Why the first variants failed

| Variant | Shape | drawings-gods result |
| --- | --- | --- |
| C1 | Project matrix, `?value knora-base:isDeleted false` | >300 s (timeout), 2 runs |
| C2 | C1 with `VALUES ?prop` (307 properties) | 79 s |
| C3 | C1 with the project anchor in a sub-select | >300 s (timeout), 2 runs |
| C4 | C5 with `?value knora-base:isDeleted false` | >120 s (timeout) on the first class |

Fuseki runs TDB2 without `stats.opt`. Jena then orders the triple patterns by the number of bound
terms. `tdb2.tdbquery --explain` for C4 shows this execution order:

```text
?resource knora-base:attachedToProject <project>
?resource rdf:type <class>
?resource knora-base:isDeleted false
?value knora-base:isDeleted false        <- ?value not bound: every non-deleted object in the store
?resource ?prop ?value
?value knora-base:hasPermissions ?permissions
```

`?value knora-base:isDeleted false` has two bound terms, so it comes before `?resource ?prop ?value`.
The current queries avoid this by accident: their property path splits the pattern group, and the
group after the path sees `?value` bound. In C5 and C6, `?value knora-base:isDeleted ?valueDeleted`
has only one bound term. Jena then runs it last, when `?value` is bound. Scala drops the deleted rows.

A sub-select barrier (C3) does not help. The plan of C3 was not checked. A likely cause is that Jena
evaluates the outer group on its own and then joins.

## Timings

Medians of 3 runs, in seconds. "By property" is the current loop over all properties.

| Project | Step 1 | **R** | Step 2 (all classes) | **C5** (all classes) | By property | **C6** |
| --- | --- | --- | --- | --- | --- | --- |
| drawings-gods | 2.5 | **0.5** | 12.9 | **12.6** | 76.3 (280 queries) | **9.8** |
| BEOL | 3.0 | **0.4** | 10.1 | **9.0** | 22.9 (103 queries) | **4.8** |
| LIMC | 16.4 | **2.8** | 90.6 | **64.1** | 155.8 (78 queries) | **55.7** |

Slowest single query (warm runs), against the 40 s view-restrictions timeout:

| Project | Step 1 | Step 2 | C5 | C6 |
| --- | --- | --- | --- | --- |
| drawings-gods | 1.9 | 3.4 | 3.6 | 9.8 |
| BEOL | 2.5 | 1.3 | 1.3 | 4.8 |
| LIMC | 12.9 | 30.2 (`Monument`) | 22.3 (`Monument`) | **55.7** |

C5 and C6 also count the user link values, and they read objects that Scala drops (link targets, deleted
values): 63,203 in drawings-gods, 83,917 in BEOL, 428,689 in LIMC. They are faster than the current
queries anyway.

## Correctness

Comparison of C5 and C6 (current scope: link values dropped) with the current queries:

| Project | Values by class and permission | Values by property and permission | Resources by class and permission (R) |
| --- | --- | --- | --- |
| drawings-gods | 57 keys, 0 differences | 485 keys, 0 differences | 31 keys, 0 differences |
| BEOL | 38 keys, 0 differences | 89 keys, 0 differences | 36 keys, 0 differences |
| LIMC | 16 keys, 0 differences | 60 keys, 0 differences | 15 keys, 0 differences |

DEV-7390 impact (user link values that the current report does not show):

| Project | User link values | Standoff link values (stay excluded) |
| --- | --- | --- |
| drawings-gods | 59,963 | 0 |
| BEOL | 42,635 | 41,189 |
| LIMC | 428,682 | 0 |

In LIMC, the fix adds 35 % more values to the report.

## Drawbacks and open points

1. **C6 does not fit LIMC.** 56 s exceeds the 40 s timeout. A per-project query needs a higher timeout
   and a result cache, or it stays per class.
2. **The shape depends on the pattern order.** A later edit that adds `?value knora-base:isDeleted false`,
   or any pattern with two bound terms on `?value`, brings back the store-wide scan. A test must pin the
   rendered query, and a review rule must explain why.
3. **The by-property report needs all classes.** Deriving it from C5 means all class queries must run
   first (LIMC: 64 s in total, but no single query over 22 s). Caching the C5 rows for each project lets
   both reports share them.
4. **Classification depends on the ontology cache.** A property or class missing from the cache drops
   its rows without a warning. The comparison used the triplestore ontology; production would use the
   cache.
5. **Multi-typed resources.** None of the three projects has one. R, C5 and C6 count such a resource once
   for each type. The current most-specific-class filter goes away. A cheap probe could show a warning.
6. **Deleted values and link targets cost time.** Grouping by `?valueDeleted` reads deleted values, and
   the unbound predicate reads link targets. `VALUES ?prop` (C2) to avoid that was slower (79 s).
7. **Drill-down not measured.** The drill-down queries still use `REGEX` and `FILTER NOT EXISTS`. That
   regex also disagrees with `PermissionUtilADM` for texts such as
   `V knora-admin:UnknownUser|RV knora-admin:KnownUser`. They need their own measurement.
8. **Stage is a shared server.** Times vary with its load. The ratios held over all runs.

## Recommendation

- Replace step 1 with R (5–8× faster, identical counts).
- Replace step 2 with C5: same speed or faster, under the timeout, fixes DEV-7390.
- Derive the by-property counts from the C5 rows of all classes, cached for each project, instead of one
  query for each property.
- Keep C6 as an option for later, behind a higher timeout, if one request is preferred over one request
  for each class.

## Follow-up measurements (stage, 2026-10-16)

Batched value counts: one query lists the live resource IRIs of a class, then the value-count query runs for each
batch of IRIs (`VALUES ?resource { … }`). Scala sums the batch rows.

- The summed batch rows equal the unbatched rows (0 differences on LIMC `Monument` and BEOL `transcription`).
- Chosen batch size: 2000. The slowest LIMC batch takes 0.84 s, and the request body is about 118 KB. Size 5000
  is about 295 KB, above the Jetty form limit of 200,000 bytes.
- The listing query takes 3.3 s for 55,824 resources. At linear growth, it reaches the 40 s timeout at about
  680,000 live resources in one class.

Final queries, all classes on a new cache:

| Project | Value counts (wall) | Slowest batch | Drill-down page (worst case) | Differences |
| --- | --- | --- | --- | --- |
| drawings-gods | 10.9 s | 4.4 s | 2.6 s | 0 |
| BEOL | 6.0 s | 4.5 s | 2.1 s | 0 |
| LIMC | 46.0 s | 1.8 s | 18.8 s | 0 |

No single query goes over the 40 s timeout. The first by-property request on a new LIMC cache takes 46 s.
The owner accepted this on 2026-10-16.
