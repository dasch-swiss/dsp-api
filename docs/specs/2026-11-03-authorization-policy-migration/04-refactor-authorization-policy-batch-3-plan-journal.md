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
| dsp-api | 8c46d1548 | feature/dev-7725-declare-authorization-batch-3 | squash | in-progress | <https://github.com/dasch-swiss/dsp-api/pull/4429> |

## Phases

| phase | status | phase_base | review_fix_rounds |
| --- | --- | --- | --- |
| 18 | reviewed | dsp-api@8c46d1548 | 0 |
| 19 | reviewed | dsp-api@8c46d1548 | 0 |
| 20 | reviewed | dsp-api@db4733303 | 1 |
| 21 | reviewed | dsp-api@db4733303 | 1 |
| 22 | reviewed | dsp-api@fdfbf0f1b | 1 |
| 23 | reviewed | dsp-api@ea6d23f38 | 1 |
| 24 | reviewed | dsp-api@ea6d23f38 | 1 |
| 25 | reviewed | dsp-api@54198ed89 | 1 |
| 26 | reviewed | dsp-api@54198ed89 | 1 |
| 27 | reviewed | dsp-api@54198ed89 | 1 |

## Chunks

| id | repo | status | commit(s) | summary | blocker |
| --- | --- | --- | --- | --- | --- |
| 18-19.1 | dsp-api | complete | db4733303 | SelfOrSystemAdmin and ProjectAdminOfAnyProject policies and factories; user writes and the three membership reads restricted; Self principal in the matrix; baseline 25 | none |
| 20-21.1 | dsp-api | complete | fdfbf0f1b, 5a205d3e0 | permission writes and copyright-holder PUT on SystemAdmin; AP/DOAP create through the grant, scope validated (built-in, shared or own ontology; built-in or own group); review fix: 400 for non-entity class IRIs, IRI-based built-in check, project from the grant; baseline 16 | none |
| 22.1 | dsp-api | complete | 5c39cab72, ea6d23f38 | can-delete checks declared as they are; cardinality checks through the grant; OntologyCacheHelpers permission checks deleted; review fix: RestCardinalityServiceSpec, exact old admin rule; baseline 11 | none |
| 23-24.1 | dsp-api | complete | ed98428c6, 54198ed89 | value erase SystemAdmin; resource erase ProjectAdmin via the shortcode grant, wrong-project test; PUT /v2/resources needs ChangeRights when permissions change (review fix: only on a difference, as values); baseline 7 | none |
| 25-27.1 | dsp-api | complete | 3bcb985cf, 9e77fa738 | listings Public (v3 publicPolicy); counts wait on DEV-7718; dead checks removed or made private, securedEndpoint and publicEndpoint deleted, store reset declared Public; lint rule for Policy.key/withInfo; ADR, CONTEXT, conventions, ARCH-MAP; baseline 5 | none |

## Deferrals

- none

## Deviations

- Phases run in groups, each with one build, test run and review (owner asked for short feedback loops).

## Side findings

- AP/DOAP updates (SystemAdmin only) do not apply the create-time scope validation; left as Q4 decided.
- Shared ontologies are accepted on AP/DOAP create, beyond the plan's "own or built-in" (no cheap test data, so not covered by a test).
- `canreplacecardinalities` on a built-in class answers 400 to authenticated callers (was 403); the cardinality checks admit an inactive admin no more.
- Resource erase: an unknown project in the resource IRI answers 403 (was 404); a SystemAdmin erasing a resource whose IRI shortcode differs from its project gets 403 (inconsistent data only).
- Root `CONTEXT.md` is an index, so the new terms are defined only in `docs/contexts/identity-access/CONTEXT.md`.
- The lint rule exempts all of `slice/common/api` by path and does not catch aliased imports of `Policy.key`.
- `ARCH-MAP.md` updated by hand (three stale lines) instead of a full `/dune:dune-map update`.

## Closeout

- Full local runs (2026-11-09): `just test-unit` green. `//modules/test-it:test` green after `ResourcesResponderV2Spec`'s permission-changing case moved to the project admin (the Modify-only user is now refused, as Q7 decided).
- `//modules/test-e2e:test`: green except `StandoffEndpointsE2ESpec`'s XSL-transformation cases (the API cannot fetch the XSL from the local Sipi `latest` image, which predates the v10.0.1 bump; no standoff or Sipi code changed) and `ValueHasXmlOnValueWriteE2ESpec`'s 6000-tag case (local timeout, known on `main`). CI decides.
