---
plan: docs/specs/2026-11-03-authorization-policy-migration/04-refactor-authorization-policy-batch-3-plan.md
target_repo: /Users/raitisveinbahs/work/dsp-api
base_commit: 8c46d1548
branch: feature/dev-7725-declare-authorization-batch-3
started: 2026-11-09
problem: >
  37 endpoints need an owner decision before they can declare a policy (Q1-Q9). Batch 3 implements the answers,
  deletes the checks the migration made dead and tightens the ratchet.
status: in-progress
---

# Execution Journal: 04-refactor-authorization-policy-batch-3-plan

## Repos

| repo | base_commit | branch | merge_strategy | status | pr |
| --- | --- | --- | --- | --- | --- |
| dsp-api | 8c46d1548 | feature/dev-7725-declare-authorization-batch-3 | squash | in-progress | |

## Phases

| phase | status | phase_base | review_fix_rounds |
| --- | --- | --- | --- |

## Chunks

| id | repo | status | commit(s) | summary | blocker |
| --- | --- | --- | --- | --- | --- |

## Deferrals

- none

## Deviations

- Phases run in groups, each with one build, test run and review (owner asked for short feedback loops).

## Side findings

## Closeout
