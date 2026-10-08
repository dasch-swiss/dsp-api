---
title: "Gravsearch Lucene hit limit: stage measurements"
date: 2026-10-08
author: "Balduin Landolt"
status: reviewed
repositories: []
linear: DEV-6824
---

# Gravsearch Lucene hit limit: stage measurements

These measurements decide which Gravsearch text functions can take an explicit Lucene hit limit
(`("term" 1000000)`) without unacceptable latency. Jena otherwise caps every `text:query` at 10,000 hits.

**Outcome.** `matchText` / `matchTextInStandoff` / `matchLabel` get the full limit (DEV-6824). `matchFulltext`
stays capped until its prequery is made cheaper per hit (DEV-7489).

## Method

- **Instrument:** stage (`db.stage.dasch.swiss`, a prod mirror), read-only via `dsp vre sparql query -s stage
  --timeout 120`. Stage is a shared box, so treat absolute values as ±50 %. The ratios are the robust part.
- **Runs:** cases under 30 s were run three times and are reported as the median; slower cases ran once. The raw
  outputs are in `assets/timings-*.txt`. A line labelled `run2` twice is runs 2 and 3.
- **Traffic sample:** a random 1,200 of the ~6,600 Gravsearch traces from the last 14 days whose prequery
  contains `text:query`, taken from Tempo (`gravsearch.prequery` / `gravsearch.query` events). Prod traffic makes
  up about 1,060 of them.
- **Prequeries:** Tempo truncates the recorded prequery at 2,048 characters, so long `matchFulltext` prequeries
  were rebuilt from the generator's fixed shape (`assets/gen_matchfulltext_prequery.py`) and the recorded
  Gravsearch. The `matchLabel` count prequery is complete in the trace. All queries are in `assets/queries/`.
- **Census:** for each of the 798 distinct prod `matchFulltext` terms in the sample, the limited Lucene hit count
  on stage. Only aggregates are recorded here, since the terms are users' searches.

## Prod traffic

- Nearly all Lucene Gravsearch traffic is dsp-app's `matchFulltext`: 1,061 of the prod requests, of which 891 had
  no project or class restriction.
- `matchText` / `matchLabel` were rare in the sample, and none came from the PIA harvester.
- 20.4 % of prod `matchFulltext` requests (206 of 1,012 with a known hit count) are truncated by the cap today.
    - 194 of the 206 are multi-word terms, which Lucene ORs.
    - 167 of the 206 have no project restriction.
    - Their limited hit counts: 64 at 10k–50k, 46 at 50k–100k, 75 at 100k–200k, 21 at 200k–500k.
- Prequery execute time today (with the cap): truncated requests p50 1.6 s, p90 11 s, max 53 s. Untruncated
  requests p50 0.17 s, p90 1.7 s.

## C1: the 0105 support case (`matchLabel`)

`drawings-gods:DrawingPublic`, `matchLabel(?r, "ir14*")`, count prequery. The raw Lucene hits are 10,000 capped
and 34,306 limited. Results by limit:

| limit | result | time |
| --- | --- | --- |
| none (10k cap) | 0 | 0.37 s |
| 20,000 | 255 | 0.85 s |
| 30,000 | 2,732 | 0.86 s |
| 40,000 | 3,031 | 0.86 s |
| 1,000,000 | **3,031** (correct) | 0.80 s |

## `matchFulltext` with the full limit

Count prequery, with the page prequery in brackets:

| term | scope | resources: cap → limit | time: cap → limit |
| --- | --- | --- | --- |
| `fortuna de ostia` | LIMC project + `Monument` | 657 → 18,370 | 3.8 s → 115 s (3.8 → 100 s) |
| `fortuna de ostia` | none | 11,195 → 183,306 | 3.7 s → 97 s (3.8 → 102 s) |
| `chthonic false door greek tomb` | none | 8,020 → 165,739 | 3.9 s → 87 s (3.9 → 88 s) |
| `Ariane a naxos` | none | 34,134 → 105,933 | 11 s → 51 s (11 → 50 s) |
| `the` | none | 14,543 → 72,900 | 4.4 s → 32 s (4.5 → 32 s) |
| `athena` (control, 2,926 hits) | none | 2,879 → 2,879 | 1.4 s → 1.4 s |
| `LIMC Iason 37` (control, 4,132 hits) | none | 67,211 → 67,211 | 27 s → 27 s |

Terms under the cap are unaffected, in both result and time. Terms over the cap get 3–28× more results, and the
prequery becomes 5–30× slower. Some cases approach the 120 s Gravsearch timeout, and dsp-app sends a page and a
count query per search.

## `matchFulltext` with intermediate limits

Count prequery time and result per limit:

| term | 20k | 30k | 40k | 50k |
| --- | --- | --- | --- | --- |
| `fortuna de ostia`, LIMC | 8.0 s / 1,066 | 14.4 s / 1,076 | 24.2 s / 3,671 | 26.3 s / 3,673 |
| `fortuna de ostia` | 7.1 s / 21,099 | 13.5 s / 44,308 | 23.0 s / 69,148 | 25.6 s / 77,986 |
| `chthonic false door greek tomb` | 7.7 s / 15,049 | 9.7 s / 21,537 | 12.0 s / 27,537 | 14.2 s / 33,942 |
| `Ariane a naxos` | 17.9 s / 45,700 | 19.6 s / 53,212 | 23.7 s / 61,598 | 27.3 s / 68,768 |
| `the` | 9.1 s / 21,995 | 14.1 s / 39,329 | 17.0 s / 47,441 | 21.1 s / 56,188 |

Share of today's truncated prod requests that a limit fully fixes:

| limit | 20k | 30k | 40k | 50k | 100k | 200k | 1M |
| --- | --- | --- | --- | --- | --- | --- | --- |
| fixed | 16 % | 21 % | 28 % | 31 % | 53 % | 90 % | 100 % |

Cost is roughly linear in hits, while most truncated requests are far above any modest limit. So a modest limit
pays real latency for a small correctness gain, and leaves the rest silently wrong.

## The `matchLabel` / `matchText` prequery shape with broad terms

This is a lucene anchor followed by bound-subject patterns (label, type walk, project, `isDeleted`), with no
OPTIONAL value or list-node branches. Count prequery:

| term | scope | resources: cap → limit | time: cap → limit |
| --- | --- | --- | --- |
| `fortuna de ostia` | none | 555 → 63,604 | 0.57 s → 19.2 s |
| `fortuna de ostia` | LIMC project | → 2,695 | → 3.6 s |
| `chthonic false door greek tomb` | none | 267 → 304 | 0.46 s → 4.9 s |
| `chthonic false door greek tomb` | LIMC project | → 127 | → 4.6 s |

Even the worst case (classless, unscoped, a broad OR term) stays well under the timeout. The realistic case, a
single label pattern with a class (C1), costs under 1 s at any limit.

## Why `matchFulltext` is different

Its prequery runs two OPTIONAL branches per Lucene hit: the value branch, with a `subClassOf*` type walk and a
`subPropertyOf* hasValue` walk, and the list-node branch, with a `hasSubListNode*` walk. It then walks
`subClassOf*` to `Resource` for every candidate. `/v2/search` had the same cost structure until DEV-6864's
REWRITE replaced the walks with `creationDate` / `valueCreationDate` probes (count/der 82 s → 13 s). Porting that
rewrite is DEV-7489's first candidate.

## The DEV-6864 rewrite applied to `matchFulltext`, by hand

A prequery with DEV-6864's substitutions (`assets/gen_matchfulltext_prequery_rewrite.py`):

- resource-ness via `creationDate` instead of `rdf:type` + `subClassOf*`;
- value-ness via `valueCreationDate` plus direct-type `FILTER NOT EXISTS` on `LinkValue` / `ListValue`;
- no `subPropertyOf* hasValue` walk;
- the Lucene lookup in an inner `SELECT DISTINCT`.

Count query, single runs (raw: `assets/timings-matchfulltext-rewrite.txt`):

| term | resources (cap / limit) | current shape: cap → limit | rewrite: cap → limit |
| --- | --- | --- | --- |
| `fortuna de ostia`, LIMC `Monument` | 657 / 18,370 | 3.8 s → 115 s | 2.1 s → 24 s |
| `fortuna de ostia` | 11,195 / 183,306 | 3.7 s → 97 s | 1.0 s → 18 s |
| `chthonic false door greek tomb` | 8,020 / 165,739 | 3.9 s → 87 s | 1.5 s → 26 s |
| `Ariane a naxos` | 34,134 / 105,933 | 11 s → 51 s | 2.8 s → 13 s |
| `athena` (under the cap) | 2,879 / 2,879 | 1.4 s → 1.4 s | 0.6 s → 0.5 s |

The rewrite returns identical result counts in every case, so it is a pure speedup: 2–4× at today's cap, and
4–5× against the current shape with the limit. With the limit, the broadest terms still take 13–26 s, where they
take 1–11 s today with the cap. Rewrite plus limit is therefore not free for broad multi-word searches; DEV-7489
has to decide between that latency, AND semantics for multi-word terms, or both.
