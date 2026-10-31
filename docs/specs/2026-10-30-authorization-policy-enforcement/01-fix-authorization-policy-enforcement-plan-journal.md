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

| repo | base_commit | branch | merge_strategy | status | pr |
| --- | --- | --- | --- | --- | --- |
| dsp-api | cafe3c1caa | plan/authorization-policy-enforcement | squash | in-progress | — |

## Phases

| phase | status | phase_base | review_fix_rounds |
| --- | --- | --- | --- |
| 1 | reviewed | dsp-api@517f1ca17a | 1 |
| 2 | reviewed | dsp-api@291fcab014 | 2 |
| 3 | reviewed | dsp-api@c744e192cb | 1 |

## Chunk queue

| id | repo | files | depends_on | checkboxes | acceptance | context | replaces |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1.1 | dsp-api | DeleteAdminListsEndpointsE2ESpec.scala, AdminListRestService.scala, ListsResponder.scala, ListsResponderSpec.scala, AdminListsEndpoints.scala, docs/03-endpoints/api-admin/lists.md | — | all Phase 1 checkboxes except Phase review | non-admins 403, anonymous 401, admins 200; new 403 tests red before fix | ListsResponder.scala § deleteListNodeCommentsADM; AdminListsEndpoints.scala § deleteListsComment; lists.md § Delete child node comments | — |
| 2.1 | dsp-api | StandoffEndpointsE2ESpec.scala, StandoffRestService.scala, StandoffEndpoints.scala, docs/03-endpoints/api-v2/text/custom-standoff.md | — | all Phase 2 checkboxes except Phase review | anonymous 401/403, normalUser/anythingUser1/incunabula admin 403 with no mapping written; anything admin and root 200; unknown project 400/403; new 403 tests red before fix | StandoffRestService.scala § createMapping; StandoffEndpoints.scala § postMapping; custom-standoff.md § Creating a custom Mapping | — |
| 3.1 | dsp-api | ResourcesResponderV2.scala, ResourcesRouteV2E2ESpec.scala, docs/03-endpoints/api-v2/reading-and-searching-resources.md | — | all Phase 3 checkboxes except Phase review | hidden resource history 403 for anonymous, normalUser, anythingUser1; creator 200; unknown IRI 404; projectHistoryEvents 200 without the hidden IRI for anythingUser1 and anonymous, with it for the creator; new tests red before fix | ResourcesResponderV2.scala § getResourceHistoryV2, getProjectResourceHistoryEvents, getResourceAtGivenTime; reading-and-searching-resources.md § history sections | — |

## Chunks

| id | repo | status | commit(s) | summary | blocker |
| --- | --- | --- | --- | --- | --- |
| 1.1 | dsp-api | complete | aaecf93bb | Role check first in ListsResponder.deleteListNodeCommentsADM; user passed through; description, docs, e2e auth tests (red before fix: member got 200) | none |
| 1.2 | dsp-api | complete | 69c8bb0cf | Review fix round 1: e2e pins 400 for a missing node before 403; test-it Forbidden case (canary red with check moved later); docs bullet no longer swallows the next sentence | none |
| 2.1 | dsp-api | complete | cdc70558b | ensureSystemAdminOrProjectAdminById on the body's project after parse, before the responder; description, docs, e2e auth tests (red before fix: normalUser got 200 and wrote the mapping); three other e2e specs now create mappings as anythingAdminUser; TestApiClient gains an anonymous multipart POST | none |
| 2.2 | dsp-api | complete | 1ea927912 | Review fix round 1 (fixup!): e2e mappingExists answers false only for the not-found failure, fails the test on any other lookup error (passing denied tests prove the not-found branch is reached) | none |
| 2.3 | dsp-api | complete | a73edbdbc | Review fix round 2 (fixup!): mappingExists asks the triplestore (CheckIriExistsQuery) instead of matching the mapping service's not-found message (dune finding on 2.2) | none |
| 3.1 | dsp-api | complete | 64d18fadf | Preview read as the requesting user; getResourceAtGivenTime uses toResource; project history skips a resource on ForbiddenException only; docs; e2e tests in ResourcesRouteV2E2ESpec (red before fix: anythingUser1 got 200 for hidden history, project history answered 403) | none |
| 3.2 | dsp-api | complete | 08e34952a | Review fix round 1 (fixup!): project history catches ForbiddenException only around getResourceHistoryV2, since extractEventsFromHistory also uses it for inconsistent value versions (dune, security, scala lenses); project history endpoint description states the omission | none |

## Deferrals

- Phase 2 (review, after the 2-round cap): `StandoffEndpointsE2ESpec.mappingExists` imports `slice.ontology.repo.CheckIriExistsQuery` from test-e2e, which breaks the docs-only webapi-ontology boundary rule (ARCH-MAP.md:699). Fix: call `IriService.checkIriExists(iri.value)` (confirm `IriService` is in the E2E layer), as another `fixup!` of the DEV-7714 commit. Warning, dune lens.
- Phase 3 (re-review Warning, not fixed in round 2): no test pins that a `ForbiddenException` from `extractEventsFromHistory` still fails project history; inducing it needs inconsistent value-version data or a stubbed `ReadResourcesService` in `ResourcesResponderV2Spec`. The omission side is pinned by the e2e test. Natural home: the re-typing of that inconsistency error to a non-Forbidden type, which removes the hazard structurally.

## Deviations

- none

## Side findings

- `ListsResponder.deleteListNodeCommentsADM`: the `someOrFail(NotFoundException)` on `listNodeInfoGetADM` is now reachable only in a race, since `getProjectFromNode` answers 400 for a missing node first (owner-decided order). Left as is; Phase 12/13 rework this method.
- `docs/03-endpoints/api-admin/lists.md`: pre-existing typo "sipmle" left untouched.
- Review suggestion (dune): a static check against `@unused` on a `User` parameter is already Phase 11's lint.
- Re-review suggestions (not fixed): the e2e rejection tests share testList02 and depend on order (documented by a comment; per-test fixtures would remove it). Test titles state two orderings by design: node existence (400) before the role check, the role check (403) before the node-state checks. Two reviewers claimed the test-it Forbidden test does not pin ordering; refuted: testList03 has no comments and the canary (check moved later) failed with BadRequest.
- Phase 2: `ValueHasXmlOnValueWriteE2ESpec` "a document of 6000 standoff tags ..." times out locally (120 s client read timeout on POST /v2/values); it fails identically on the phase base, so it is not caused by DEV-7714.
- Phase 2: `StandoffResponderV2.createMappingV2` throws `BadRequestException` inside a pure `=` step for an existing mapping IRI, so a duplicate name answers 500 instead of 400 (seen in the red run). Pre-existing; not fixed.
- Phase 2: this journal fails `just check` (MD060 table alignment) already at the phase base; the Chunk queue and Chunks tables are not padded like the Repos table.
- Phase 2 review (refuted or kept by design): reuse of `DeniedMapping` by the project-admin success test is the no-overwrite canary (a denied write would make the admin create fail), and reordering fails loudly rather than passing vacuously. The 400-or-403 and 401-or-403 assertions follow the plan's decided wording; the endpoint answers 403 for an unknown project today.
- Phase 2 review suggestions (not fixed): `test_data/test_route/texts/beol/beolTEI.py` posts mappings as `t.schweizer@unibas.ch`; if that user is not BEOL project admin the manual script now gets 403. Optional doc comment on `StandoffResponderV2.createMappingV2` that callers must authorize (Phase 13 replaces the check with a grant). ARCH-MAP fingerprint refresh not needed (no component or dependency change).

- Phase 3: the plan and the decided behaviours say project history with a hidden resource answers 500 today; the red run answered 403 (`checkResourceIris` in `readResourcesSequence_` fails with `ForbiddenException` before `.resources.head` is reached). The status change is 403 -> 200, not 500 -> 200; the commit body says so. `resourceHistoryEvents` already answered 403 for the hidden resource before the fix.
- Phase 3: `ResourcesResponderV2.scala` no longer references `SystemUser` after DEV-7712 (grep finds none). The plan's Technical Considerations and the decided behaviours say it stays on the Phase 11 SystemUser allow-list because of other legitimate uses; that statement is now untrue. Phase 11 should not allow-list the file (dune DUNE-001).
- Phase 3: `extractEventsFromHistory` (`ResourcesResponderV2.scala`, previous-value date check) fails with `ForbiddenException` for inconsistent value-version data; it should be an inconsistency error. Not changed (out of scope).
- Phase 3 review suggestions (not fixed): no test pins that a non-Forbidden failure still fails project history (needs a stubbed `ReadResourcesService`); no e2e for a hidden deleted resource, for the date-range path on a hidden resource, or for anonymous on `resourceHistoryEvents` (code reading says all answer 403); a debug log for skipped resources; scaladoc of `getResourceHistoryV2` / `getProjectResourceHistoryEvents` could name the 403 and omission; restricted-view (RV) permission counts as "can view", the same as `GET /v2/resources`. ARCH-MAP fingerprint refresh not done (no component or dependency change).
