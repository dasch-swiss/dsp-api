---
plan: docs/specs/2026-10-30-authorization-policy-enforcement/01-fix-authorization-policy-enforcement-plan.md
target_repo: /Users/subotic/_github.com/dasch-swiss/dsp-api
base_commit: cafe3c1caa174352ca173fa0b28d16b72767421e
branch: plan/authorization-policy-enforcement
started: 2026-10-31
problem: >
  Several triaged security issues (DEV-7712, DEV-7713, DEV-7714, DEV-7721, DEV-7722, DEV-7723) plus two
  fail-open gaps found in research share one cause: endpoints only authenticate, and authorization is a
  hand-called check per handler that can be missing, swallowed or given the wrong identity. The plan fixes
  the eight bugs and adds a declared per-endpoint authorization policy, a ratchet test, a lint step and a pilot.
symptoms:
  - Any logged-in user deletes list-node comments in any project (DEV-7713)
  - Any logged-in user writes a standoff mapping into any project (DEV-7714)
  - Anonymous callers read history metadata of hidden resources; project history answers 500 (DEV-7712)
status: in-progress
---

# Execution Journal: 01-fix-authorization-policy-enforcement-plan

## Repos

| repo    | base_commit | branch                               | merge_strategy | status      | pr |
|---------|-------------|--------------------------------------|----------------|-------------|----|
| dsp-api | cafe3c1caa  | plan/authorization-policy-enforcement | squash         | in-progress | —  |

## Phases

| phase | status      | phase_base          | review_fix_rounds |
|-------|-------------|---------------------|-------------------|
| 1     | reviewed    | dsp-api@517f1ca17a  | 1                 |

## Chunk queue

| id  | repo    | files | depends_on | checkboxes | acceptance | context | replaces |
|-----|---------|-------|------------|------------|------------|---------|----------|
| 1.1 | dsp-api | DeleteAdminListsEndpointsE2ESpec.scala, AdminListRestService.scala, ListsResponder.scala, ListsResponderSpec.scala, AdminListsEndpoints.scala, docs/03-endpoints/api-admin/lists.md | — | all Phase 1 checkboxes except Phase review | non-admins 403, anonymous 401, admins 200; new 403 tests red before fix | ListsResponder.scala § deleteListNodeCommentsADM; AdminListsEndpoints.scala § deleteListsComment; lists.md § Delete child node comments | — |

## Chunks

| id | repo | status | commit(s) | summary | blocker |
|----|------|--------|-----------|---------|---------|
| 1.1 | dsp-api | complete | aaecf93bb | Role check first in ListsResponder.deleteListNodeCommentsADM; user passed through; description, docs, e2e auth tests (red before fix: member got 200) | none |
| 1.2 | dsp-api | complete | 69c8bb0cf | Review fix round 1: e2e pins 400 for a missing node before 403; test-it Forbidden case (canary red with check moved later); docs bullet no longer swallows the next sentence | none |

## Deferrals

- none

## Deviations

- none

## Side findings

- `ListsResponder.deleteListNodeCommentsADM`: the `someOrFail(NotFoundException)` on `listNodeInfoGetADM` is now reachable only in a race, since `getProjectFromNode` answers 400 for a missing node first (owner-decided order). Left as is; Phase 12/13 rework this method.
- `docs/03-endpoints/api-admin/lists.md`: pre-existing typo "sipmle" left untouched.
- Review suggestion (dune): a static check against `@unused` on a `User` parameter is already Phase 11's lint.
- Re-review suggestions (not fixed): the e2e rejection tests share testList02 and depend on order (documented by a comment; per-test fixtures would remove it). Test titles state two orderings by design: node existence (400) before the role check, the role check (403) before the node-state checks. Two reviewers claimed the test-it Forbidden test does not pin ordering; refuted: testList03 has no comments and the canary (check moved later) failed with BadRequest.
