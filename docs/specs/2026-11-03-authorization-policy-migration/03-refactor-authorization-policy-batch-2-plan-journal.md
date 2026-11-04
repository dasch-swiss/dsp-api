---
plan: docs/specs/2026-11-03-authorization-policy-migration/03-refactor-authorization-policy-batch-2-plan.md
target_repo: /Users/raitisveinbahs/work/dsp-api
base_commit: fc57cdf1d
branch: feature/dev-7725-declare-authorization-batch-2
started: 2026-11-04
problem: >
  63 endpoints find their project through an ontology, shortcode, shortname, group or v3 path, or are built outside
  the standard bases. Batch 2 adds the grants and factories they need and migrates them.
status: in-progress
---

# Execution Journal: 03-refactor-authorization-policy-batch-2-plan

## Repos

| repo | base_commit | branch | merge_strategy | status | pr |
| --- | --- | --- | --- | --- | --- |
| dsp-api (stacked on #4415) | fc57cdf1d | feature/dev-7725-declare-authorization-batch-2 | squash | in-progress | <https://github.com/dasch-swiss/dsp-api/pull/4419> |

## Phases

| phase | status | phase_base | review_fix_rounds |
| --- | --- | --- | --- |
| 8 | reviewed | dsp-api@fc57cdf1d | 0 |
| 9 | reviewed | dsp-api@80c57de1d | 0 |
| 10 | reviewed | dsp-api@80c57de1d | 0 |
| 11 | reviewed | dsp-api@80c57de1d | 0 |
| 12 | reviewed | dsp-api@39e61810f | 1 |
| 13 | reviewed | dsp-api@39e61810f | 1 |
| 14 | reviewed | dsp-api@1d6a63681 | 1 |
| 15 | reviewed | dsp-api@1d6a63681 | 1 |
| 16 | reviewed | dsp-api@328a09118 | 0 |
| 17 | reviewed | dsp-api@328a09118 | 0 |

## Chunks

| id | repo | status | commit(s) | summary | blocker |
| --- | --- | --- | --- | --- | --- |
| 8.1 | dsp-api | complete | 80c57de1d | ontology create/delete on projectAdminPolicyEndpoint; create parsed without a user, responder creates in grant.project; delete asserts project inside the lock; wrong-project test; 2 matrix rows; baseline 98 | none |
| 9-11.1 | dsp-api | complete | 39e61810f | shortcode, shortname, group and ProjectMember grants; projectMemberPolicyEndpoint; 19 routes; matrix rows; baseline 79 | none |
| 12-13.1 | dsp-api | complete | 1d6a63681 | publicPolicyEndpointWithUser; Public overload on a caller-supplied error output (FAIR); bearer SystemAdmin policy; SPARQL matrix row; baseline 68 | none |
| 14-15.1 | dsp-api | complete | 328a09118 | v3 systemAdmin/objectAccess/projectAdmin factories in slice.common.api; 12 v3 routes; V3SystemAdminRoutesE2ESpec + exempt entries; mapping writes via grant; review fix: no-op grant check and debug log removed, inactive-admin test added, dereference reason reworded; baseline 52 | none |
| 16-17.1 | dsp-api | complete | c6a246fb9 | 15 v2 ontology writes via authorizeForOntology; permission-free IRI validation; project asserted inside the lock; wrong-project tests per family; 15 matrix rows; 401 doc line; baseline 37 | none |

## Deferrals

- none

## Deviations

- Phases run in groups ([8], [9-11], [12-13], [14-15], [16-17]), each group with one build, test run and review (owner asked for short feedback loops).
- Phase 8 changes only the create-ontology parse method; the other parse methods drop their user in the phase that migrates their route (16, 17).

## Side findings

- Reviews (refuted): ontology create in an unknown project and the unknown-group routes did not change from 400/404 to 403; authorization already ran first.
- No v3 export or OAI docs exist outside docs/specs, so the OAI 401 is recorded only in the PR body.
- Phases 16-17 review (not fixed, cosmetic): some responder methods re-run the IRI validation `authorizeForOntology` already ran; `ZIO.succeed` binds kept for the for-comprehension shape; the permission-checking `checkOntologyAndEntityIrisForUpdate` now serves only `candeletecardinalities` (batch 3 removes it).
- The FAIR test specs build `BaseEndpoints(null, null)`; the overload they use touches neither field.

## Closeout
