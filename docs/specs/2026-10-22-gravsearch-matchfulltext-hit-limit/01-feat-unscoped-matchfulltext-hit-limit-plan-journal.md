---
plan: docs/specs/2026-10-22-gravsearch-matchfulltext-hit-limit/01-feat-unscoped-matchfulltext-hit-limit-plan.md
target_repo: /Users/balduinlandolt/Documents/GitHub/dasch-swiss/dsp-api/.claude/worktrees/DEV-7492
base_commit: 372c3a5aeb2bf21e56bebe45634318676a74b694
branch: dev-7492-unscoped-matchfulltext-limit
started: 2026-10-22
problem: >
  An unscoped Gravsearch matchFulltext lookup (no limitToProject, or matchFulltext on a linked resource) still
  inherits Jena's silent 10,000-hit Lucene cap and returns truncated results for broad terms. The plan lifts the
  cap for every matchFulltext lookup and puts Gravsearch matchFulltext behind the FulltextBreadthGuard that
  /v2/search already has, so an over-broad term is refused with a 400 instead of running unbounded.
status: in-progress
---

# Execution Journal: 01-feat-unscoped-matchfulltext-hit-limit-plan

## Repos

| repo    | base_commit | branch                                | merge_strategy | status      | pr  |
|---------|-------------|---------------------------------------|----------------|-------------|-----|
| dsp-api | 372c3a5ae   | dev-7492-unscoped-matchfulltext-limit | squash         | in-progress | —   |

## Phases

| phase | status   | phase_base        | review_fix_rounds |
|-------|----------|-------------------|-------------------|
| 1     | reviewed | dsp-api@ce3c7ac0e | 2                 |

## Chunk queue

| id  | repo    | files                                                                                                                                                                                  | depends_on | checkboxes                                                                                                 | acceptance                                                                                                                                                                | context                                                                                                                                  | replaces |
|-----|---------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|------------|------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------|----------|
| 1.1 | dsp-api | AbstractPrequeryGenerator.scala, SearchResponderV2.scala, new test-it GravsearchFulltextBreadthGuardSpec.scala                                                                         | —          | "Record the matchFulltext term and data graph...", "Wrap prequery execution...", "Tests for the wiring..." | gravsearchV2/gravsearchCountV2 run the prequery behind FulltextBreadthGuard iff a matchFulltext lookup exists; refuse/admit/fail-open/not-probed/scoped-probe tests green | AbstractPrequeryGenerator.scala § handleMatchFulltextFunction; SearchResponderV2.scala § gravsearchCountV2/gravsearchV2 prequery.execute | —        |
| 1.2 | dsp-api | AbstractPrequeryGenerator.scala, SparqlQuery.scala, 5 unscoped *MatchFulltext* prequery goldens                                                                                        | 1.1        | "Generator always passes luceneHitLimit...", "Update the unscoped prequery goldens..."                     | every matchFulltext text:query object is LuceneQueryArgs; prequery golden specs green                                                                                     | AbstractPrequeryGenerator.scala § matchFulltextLuceneLookup                                                                              | —        |
| 1.3 | dsp-api | docs/03-endpoints/api-v2/query-language.md, docs/05-internals/design/api-v2/gravsearch.md, docs/development/dsp-api-fuseki-query-execution.md, ARCH-MAP.md, FulltextBreadthGuard.scala | 1.2        | "Docs: remove every unscoped matchFulltext keeps the 10,000-hit cap statement..."                          | no stale DEV-7492 cap statements outside docs/specs; just check green                                                                                                     | grep DEV-7492                                                                                                                            | —        |

## Chunks

| id  | repo    | status   | commit(s) | summary                                                                                                                                                                                                                  | blocker |
|-----|---------|----------|-----------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------|
| 1.1 | dsp-api | complete | 0f25b4dd3 | generator records MatchFulltextLookup; gravsearchV2/CountV2 run the prequery behind FulltextBreadthGuard when present; GravsearchFulltextBreadthGuardSpec (probe double) covers refuse/admit/fail-open/not-probed/scoped | none    |
| 1.2 | dsp-api | complete | a49701f40 | matchFulltextLuceneLookup always emits LuceneQueryArgs(term, luceneHitLimit); five unscoped goldens updated; LuceneQueryArgs scaladoc exception removed                                                                  | none    |
| 1.3 | dsp-api | complete | 233d7c03b | docs, ARCH-MAP Search rule and FulltextBreadthGuard scaladoc describe the limit + guard for matchFulltext (400 for over-broad terms); ADR-0012 left as is                                                                | none    |
| 1.4 | dsp-api | complete | 00717a445 | review fix round 1: single guarded executePrequery seam, named guard args, discriminating fail-open/admit probe counts, ARCH-MAP bullet + entity, tier/config/AppConfig wording                                          | none    |
| 1.5 | dsp-api | complete | 919dbf41c | review fix round 2: executePrequery takes the generator (lookup no longer a free argument), scaladoc + return type, doc/scaladoc reflow                                                                                  | none    |

## Deferrals

- (review, after the 2-round cap) DUNE-001 structural form: make the matchFulltext lookup travel inside the
  generated prequery value (transformer/QueryTraverser returns sparql + lookup) so executePrequery cannot be fed
  a prequery without its generator. Round 2 bound it to the generator instead; a full fix touches QueryTraverser.
- (review, after the cap) ARCH-MAP Search rule for the guard is labelled static-analysis though its evidence is
  goldens and an integration spec (consistent with the map's other spec-backed rules); a CI grep asserting a
  single `Select.gravsearch(` call site in SearchResponderV2.scala would make the label exact.

## Deviations

- none

## Side findings

- FulltextBreadthGuard forks the real query before consulting the cache, so a term already cached as over-cap
  still starts (then interrupts) the expensive query; and probes queued on the semaphore delay the verdict with
  no bounded wait. Pre-existing /v2/search guard design, now also reached by Gravsearch matchFulltext; a
  pre-fork cache check / bounded permit wait would change /v2/search too (performance review, unmeasured).
- The guard's fork/interrupt inherits the caller's interruptibility; in an uninterruptible region the 400 waits
  for the full query (zio/zio#11044 pattern). Production handlers are interruptible; pre-existing.
- A guard refusal surfaces as an ERROR span on gravsearch.prequery.execute (same as other 400s in that stage).
- BreadthKey keeps resourceClass although the probe ignores it, so class-restricted /v2/search fragments the
  cache; Gravsearch always passes None.
- The probe counts Lucene candidates only, so a broad matchFulltext combined with a very selective structured
  constraint is refused even if the join would be cheap (follows decided behaviour 2).
- Local E2E: MatchFulltextE2ESpec fails identically on the phase base and on HEAD (31 assertions) while a local
  dsp-api stack occupies port 3333; not caused by this phase. Re-run it in CI or with the stack stopped.
