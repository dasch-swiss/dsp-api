# Final restrictions queries, measured on stage (2026-10-16)

Script: `bench_fix.py`. Raw data: `results/*-fix-timings.csv`, `results/*-fix-summary.csv`.

Setup: stage Fuseki, read-only `SELECT` over HTTPS, one run for each project. The value counts of every resource
class ran as in the API on a new cache: one listing query for each class, then batches of 2000 resource IRIs
through four parallel workers (the permit limit of the value-count queries). The drill-down queries ran for the
largest class of each project, with every literal of the class's rows in the `STR(?permissions) IN` lists. That set
is a superset of the restricted literals, so the drill-down times are a worst case.

| Project | Resource counts | Value counts (wall, new cache) | Batches | Slowest batch | Differences | Drill-down page | Drill-down count |
| --- | --- | --- | --- | --- | --- | --- | --- |
| drawings-gods | 1.22 s | 10.87 s | 32 | 4.35 s | 0 | 2.63 s | 1.64 s |
| BEOL | 0.78 s | 6.01 s | 42 | 4.48 s | 0 | 2.14 s | 1.58 s |
| LIMC | 4.17 s | 46.04 s | 164 | 1.75 s | 0 | 18.78 s | 13.10 s |

## Result

1. The summed batch rows of every class equal the per-class value-count rows of the design measurement
   (0 differences over 64 classes in three projects).
2. No query goes over the 40 s view-restrictions timeout. The slowest single query is the LIMC drill-down page for
   `CatalogLIMC` (18.8 s, worst case).
3. The first by-property request on a new LIMC cache waits for all 164 batches: 46 s. Later requests read the
   cache. Accepted by the owner on 2026-10-16: the batching bounds each query, not the first request on a new cache.
4. The drill-down page of the largest LIMC class is slow in the worst case. A follow-up measures it with the real
   restricted literals and optimises it if needed.
