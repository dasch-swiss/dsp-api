# Batched value counts, measured on stage (2026-10-16)

The value counts of one class are read in two steps: one query lists the IRIs of the live resources of the class,
then the value-count query runs for each batch of IRIs, with `VALUES ?resource { … }` as its first pattern. Scala
sums the batch rows by property, permission literal and deleted flag.

Setup: stage Fuseki, read-only `SELECT` over HTTPS, queries one at a time with a 1 s pause, one run each. Script:
`bench_batch.py`. Raw data: `results/*-batch-timings.csv`, `results/*-batch-summary.csv`.

| Class | Resources | Listing | Unbatched | Batch size | Batches | Slowest batch | Sum of batches | Differences |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| LIMC `Monument` | 55,824 | 3.28 s | 26.70 s | 5000 | 12 | 3.35 s | 19.67 s | 0 |
| | | | | 2000 | 28 | 0.84 s | 19.17 s | 0 |
| | | | | 500 | 112 | 0.40 s | 26.72 s | 0 |
| BEOL `transcription` | 6,159 | 0.66 s | 2.24 s | 5000 | 2 | 1.18 s | 1.50 s | 0 |
| | | | | 2000 | 4 | 0.58 s | 1.77 s | 0 |
| | | | | 500 | 13 | 0.25 s | 2.34 s | 0 |

## Result

1. The summed batch rows equal the unbatched rows in every run (0 differences).
2. Batching is not slower in total: on LIMC `Monument`, 19.7 s at size 5000 against 26.7 s unbatched.
3. The time of one batch depends on the batch size and the number of values for each resource, not on the size of the
   class or the project.
4. Chosen batch size: **5000**, the largest measured size whose slowest LIMC batch stays under 10 s (3.35 s).
5. The listing query takes 3.3 s for 55,824 resources. If it grows linearly, it reaches the 40 s timeout at about
   680,000 live resources in one class. Above that, the listing needs its own paging.
