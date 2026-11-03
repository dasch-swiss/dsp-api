---
plan: docs/specs/2026-11-03-authorization-policy-migration/02-refactor-authorization-policy-batch-1-plan.md
target_repo: /Users/raitisveinbahs/work/dsp-api
base_commit: 984f74c956d750a53463763192444cea745cbdd9
branch: feature/dev-7725-migrate-remaining-endpoints-to-declared-authorization
started: 2026-11-03
problem: >
  175 endpoints still declare no ADR-0013 authorization policy and authorize through hand-called checks that
  nothing verifies. Batch 1 migrates the 75 that need only the existing BaseEndpoints policy factories and
  authorizeProjectAdminById: SystemAdmin, Public and ObjectAccess declarations, and ProjectAdmin endpoints whose
  project IRI is in the path or body.
status: in-progress
---

# Execution Journal: 02-refactor-authorization-policy-batch-1-plan

## Repos

| repo | base_commit | branch | merge_strategy | status | pr |
| --- | --- | --- | --- | --- | --- |
| dsp-api | 984f74c95 | feature/dev-7725-migrate-remaining-endpoints-to-declared-authorization | squash | in-progress | <https://github.com/dasch-swiss/dsp-api/pull/4415> |

## Phases

| phase | status | phase_base | review_fix_rounds |
| --- | --- | --- | --- |
| 1 | reviewed | dsp-api@4d8713c48 | 1 |

## Chunk queue

| id | files | depends_on | checkboxes | acceptance | context | replaces |
| --- | --- | --- | --- | --- | --- | --- |
| 1.1 | MaintenanceEndpoints, ManagementEndpoints, ProjectsEndpoints, UsersEndpoints + their RestServices, AuthorizationMatrixE2ESpec, endpoints-without-policy.txt, EndpointPolicyE2ESpec, new SA e2e tests | — | all Phase 1 deliverables except the run and review | 7 SA endpoints on systemAdminPolicyEndpoint, exempt set, 3 matrix rows, 403/401 tests, baseline 168 | BaseEndpoints.scala:100-103; AuthorizationMatrixE2ESpec.scala rows/cells; owner: ADR-0013 | — |

## Chunks

| id | repo | status | commit(s) | summary | blocker |
| --- | --- | --- | --- | --- | --- |
| 1.1 | dsp-api | complete | 981077141 | 7 SA endpoints on systemAdminPolicyEndpoint, ensureSystemAdmin removed, matrix exempt set + 3 rows, 403/401 tests, baseline 168 | none |
| 1.2 | dsp-api | complete | 4c9a2a316 | review fixes: test-it compile, unused AuthorizationRestService, discriminating compaction 403 test, exemptions name their proving spec | none |

## Deferrals

- none

## Deviations

- none

## Side findings

- Phase 1 review (refuted): missing 401 tests for the two replace-user-iri routes; both specs already had them (MaintenanceReplaceUserIriE2ESpec.scala:53, MaintenanceReplaceUserIriInProjectE2ESpec.scala:85).
- Phase 1 review (not fixed, Phase 27 owns it): ARCH-MAP.md refresh (DUNE-001); `Secured`/`securedEndpoint` names next to the policy factories (DUNE-003).
- Phase 1 review (not fixed): exempt entries name their proving spec but nothing checks the spec exists (DUNE-002, suggested a static check).
- Phase 1: the compaction 403 test cannot prove nothing ran: `startCompaction` answers 403 when `compact()` fails, which it does against the test Fuseki, so reverting its factory leaves the test green.
- Phase 1: a local dev stack holding port 3333 makes e2e runs report PASSED with 0 tests ("Address already in use"); run with `--test_env=KNORA_WEBAPI_KNORA_API_INTERNAL_PORT=3433 --test_env=KNORA_WEBAPI_KNORA_API_EXTERNAL_PORT=3433 --nocache_test_results`.

## Closeout
