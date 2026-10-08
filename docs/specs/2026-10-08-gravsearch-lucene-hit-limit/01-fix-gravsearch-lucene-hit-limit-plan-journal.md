---
plan: docs/specs/2026-10-08-gravsearch-lucene-hit-limit/01-fix-gravsearch-lucene-hit-limit-plan.md
target_repo: /Users/balduinlandolt/Documents/GitHub/dasch-swiss/dsp-api/.claude/worktrees/DEV-6824
base_commit: c05431ceb
branch: worktree-DEV-6824
started: 2026-10-08
problem: >
  Gravsearch's matchText, matchTextInStandoff and matchLabel emit Jena text:query without a hit limit, so the
  Lucene lookup is silently capped at 10,000 hits and, for wildcard terms, returns an arbitrary subset (support
  case: 0105 DrawingPublic label "ir14*" returns 0 or 1,954 instead of 3,031). Gravsearch timeouts surface as a
  bare 500, and the matchText/matchLabel term is not SPARQL-escaped. matchFulltext stays capped (DEV-7489).
symptoms:
  - "Advanced search label 'matches' ir14* on 0105 DrawingPublic returns 1,954 (prod) / 0 (stage) of 3,031"
  - "Results change after a Lucene reindex"
status: in-progress
---

# Execution Journal: 01-fix-gravsearch-lucene-hit-limit-plan

## Repos

| repo | base_commit | branch | merge_strategy | status | pr |
| --- | --- | --- | --- | --- | --- |
| dsp-api | c05431ceb | worktree-DEV-6824 | squash | in-progress | https://github.com/dasch-swiss/dsp-api/pull/4386 |

## Phases

| phase | status | phase_base | review_fix_rounds |
| --- | --- | --- | --- |
| 1 | in-progress | dsp-api@fbd5e5136 | 0 |
| 2 | pending | — | 0 |

## Chunks

| id | repo | status | commit(s) | summary | blocker |
| --- | --- | --- | --- | --- | --- |

## Deferrals

- none

## Side findings

- none
