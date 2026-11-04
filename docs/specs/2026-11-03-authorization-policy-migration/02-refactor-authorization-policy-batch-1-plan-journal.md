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
| dsp-api (stacked, Phase 2) | 64657ad50 | feature/dev-7725-declare-public-endpoint-policies | squash | in-progress | <https://github.com/dasch-swiss/dsp-api/pull/4416> |
| dsp-api (stacked, Phase 3) | e2884b80c | feature/dev-7725-declare-object-access-endpoint-policies | squash | in-progress | <https://github.com/dasch-swiss/dsp-api/pull/4417> |

## Phases

| phase | status | phase_base | review_fix_rounds |
| --- | --- | --- | --- |
| 1 | reviewed | dsp-api@4d8713c48 | 1 |
| 2 | reviewed | dsp-api@64657ad50 | 1 |
| 3 | reviewed | dsp-api@e2884b80c | 1 |

## Chunk queue

| id | files | depends_on | checkboxes | acceptance | context | replaces |
| --- | --- | --- | --- | --- | --- | --- |
| 1.1 | MaintenanceEndpoints, ManagementEndpoints, ProjectsEndpoints, UsersEndpoints + their RestServices, AuthorizationMatrixE2ESpec, endpoints-without-policy.txt, EndpointPolicyE2ESpec, new SA e2e tests | — | all Phase 1 deliverables except the run and review | 7 SA endpoints on systemAdminPolicyEndpoint, exempt set, 3 matrix rows, 403/401 tests, baseline 168 | BaseEndpoints.scala:100-103; AuthorizationMatrixE2ESpec.scala rows/cells; owner: ADR-0013 | — |

## Chunks

| id | repo | status | commit(s) | summary | blocker |
| --- | --- | --- | --- | --- | --- |
| 1.1 | dsp-api | complete | 981077141 | 7 SA endpoints on systemAdminPolicyEndpoint, ensureSystemAdmin removed, matrix exempt set + 3 rows, 403/401 tests, baseline 168 | none |
| 1.2 | dsp-api | complete | 4c9a2a316 | review fixes: test-it compile, unused AuthorizationRestService, discriminating compaction 403 test, exemptions name their proving spec | none |
| 2.1 | dsp-api | complete | 93984ef18 | 20 public endpoints on publicPolicyEndpoint with reasons, requirement in descriptions, Public scaladoc "needs no credentials", baseline 148 | none |
| 2.2 | dsp-api | complete | c6a28d1ad | review fixes: logout gets its own reason; project and ontology-metadata reasons say what is exposed | none |
| 3.1 | dsp-api | complete | b31114720, e92601140 | 27 routes on object-access factories; ObjectAccess.admitsAnonymous + authenticatedObjectAccessPolicyEndpoint; 7 writes answer anonymous 401; AnonymousWritesE2ESpec; docs; baseline 121 | none |
| 3.2 | dsp-api | complete | 2d86f52c5 | review fixes: shared requirement constants on Policy, ratchet guard against anonymous object-access writes (mutation-checked), factory scaladoc | none |

## Deferrals

- none

## Deviations

- Phase 2 on runs on a stacked branch and PR (user request), based on the Phase 1 branch; each phase gets its own stacked PR.
- Phase 3: owner decided anonymous object-access writes answer 401 in the security logic (was 403 from the responder); `PUT /v2/values/order` included as the seventh write; batch-3 Phase 24 updated so `PUT /v2/resources` follows.

## Side findings

- Phase 1 review (refuted): missing 401 tests for the two replace-user-iri routes; both specs already had them (MaintenanceReplaceUserIriE2ESpec.scala:53, MaintenanceReplaceUserIriInProjectE2ESpec.scala:85).
- Phase 1 review (not fixed, Phase 27 owns it): ARCH-MAP.md refresh (DUNE-001); `Secured`/`securedEndpoint` names next to the policy factories (DUNE-003).
- Phase 1 review (not fixed): exempt entries name their proving spec but nothing checks the spec exists (DUNE-002, suggested a static check).
- Phase 1: the compaction 403 test cannot prove nothing ran: `startCompaction` answers 403 when `compact()` fails, which it does against the test Fuseki, so reverting its factory leaves the test green.
- Phase 1: a local dev stack holding port 3333 makes e2e runs report PASSED with 0 tests ("Address already in use"); run with `--test_env=KNORA_WEBAPI_KNORA_API_INTERNAL_PORT=3433 --test_env=KNORA_WEBAPI_KNORA_API_EXTERNAL_PORT=3433 --nocache_test_results`.
- Phase 2 review (not fixed): `publicPolicyEndpoint` could append the requirement to the description itself, so the two cannot drift (DUNE-003, structural); the pilot's `readReason` name differs from `publicReason`.
- Phase 2 review (refuted): DUNE-002's claim that ProjectsEndpoints' public block includes member/admin reads; those are in `Secured`.
- Phase 3: the test Fuseki container twice dropped connections mid-run ("Failed to connect to triplestore"); the affected specs passed when re-run alone.
- Phase 3 review (kept): `CreateResourceV2Handler`'s anonymous 403 is now unreachable over HTTP; kept as a backstop. Files `enforcedIn` names AssetPermissionsResponder, which AssetPermissionsCache delegates to.

## Closeout
