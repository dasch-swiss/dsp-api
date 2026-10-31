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
| 4 | reviewed | dsp-api@6e012a9a4f | 1 |
| 5 | reviewed | dsp-api@a1fea60630 | 2 |
| 6 | reviewed | dsp-api@27f5d538f5 | 1 |

## Chunk queue

| id | repo | files | depends_on | checkboxes | acceptance | context | replaces |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1.1 | dsp-api | DeleteAdminListsEndpointsE2ESpec.scala, AdminListRestService.scala, ListsResponder.scala, ListsResponderSpec.scala, AdminListsEndpoints.scala, docs/03-endpoints/api-admin/lists.md | — | all Phase 1 checkboxes except Phase review | non-admins 403, anonymous 401, admins 200; new 403 tests red before fix | ListsResponder.scala § deleteListNodeCommentsADM; AdminListsEndpoints.scala § deleteListsComment; lists.md § Delete child node comments | — |
| 2.1 | dsp-api | StandoffEndpointsE2ESpec.scala, StandoffRestService.scala, StandoffEndpoints.scala, docs/03-endpoints/api-v2/text/custom-standoff.md | — | all Phase 2 checkboxes except Phase review | anonymous 401/403, normalUser/anythingUser1/incunabula admin 403 with no mapping written; anything admin and root 200; unknown project 400/403; new 403 tests red before fix | StandoffRestService.scala § createMapping; StandoffEndpoints.scala § postMapping; custom-standoff.md § Creating a custom Mapping | — |
| 3.1 | dsp-api | ResourcesResponderV2.scala, ResourcesRouteV2E2ESpec.scala, docs/03-endpoints/api-v2/reading-and-searching-resources.md | — | all Phase 3 checkboxes except Phase review | hidden resource history 403 for anonymous, normalUser, anythingUser1; creator 200; unknown IRI 404; projectHistoryEvents 200 without the hidden IRI for anythingUser1 and anonymous, with it for the creator; new tests red before fix | ResourcesResponderV2.scala § getResourceHistoryV2, getProjectResourceHistoryEvents, getResourceAtGivenTime; reading-and-searching-resources.md § history sections | — |
| 4.1 | dsp-api | CreateResourceV2Handler.scala, ResourcesRouteV2E2ESpec.scala | — | all Phase 4 checkboxes except Phase review | anonymous POST /v2/resources 403 with "Anonymous users aren't allowed to create resources", resource absent afterwards; test red before fix on the message (status was already 403); no spec relies on anonymous creation; `.*Resource.*` e2e filter passes | CreateResourceV2Handler.scala § ensureNotAnonymousUser; ResourcesRouteV2E2ESpec.scala § create tests | — |
| 5.1 | dsp-api | ReportEndpointsHandler.scala, ReportEndpointsSpec.scala (new) | — | all Phase 5 checkboxes except Phase review | POST /report/asset-overview: no token 401, non-admin token 401 with the Unauthorized problem, admin token accepted with "work in progress"; non-admin test red before fix; `just test-ingest` passes | ReportEndpointsHandler.scala § postAssetOverviewReportHandler | — |
| 6.1 | dsp-api | ValuesResponderV2.scala, ValuesEndpointsE2ESpec.scala, ValuesResponderV2Spec.scala (if an expectation moves 403 -> 404), ValuesEndpoints.scala, docs/03-endpoints/api-v2/editing-values.md | — | all Phase 6 checkboxes except Phase review | hidden resource: 403 with one message for correct, wrong class, unknown value, wrong type (anythingUser1, normalUser); anonymous refusal before lookup; unknown resource 404; hidden value on visible resource 404 like a missing value; view-but-not-delete 403 before class check; delete-permitted caller 400 wrong class/type, 200 correct on a self-created resource; new tests red before fix; ValuesResponderV2Spec and ValuesEraseSpec pass | ValuesResponderV2.scala § canRemoveValue; ValuesEndpointsE2ESpec.scala § delete tests | — |

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
| 4.1 | dsp-api | complete | 4d2f098fd | ensureNotAnonymousUser fails instead of being ignored; e2e test asserts the anonymous message and that the resource is absent (red before fix: 403 from the create-permission check with "User anonymous does not have permission ..."); `.*Resource.*` filter passes (94 tests) | none |
| 4.2 | dsp-api | complete | 1c34c0f96 | Review fix round 1 (fixup!): POST /v2/resources endpoint description and editing-resources docs state that anonymous users are refused (consistency lens) | none |
| 5.1 | dsp-api | complete | a77485e52 | ReportEndpointsHandler checks ensureAdminScope before forking the report; AuthorizationHandler injected (Main already provides it); new in-process ReportEndpointsSpec with live JWT config (red before fix: non-admin token got 202); `just test-ingest` passes | none |
| 5.2 | dsp-api | complete | e6177b8d9 | Review fix round 1 (fixup!): admin test waits for the written asset overview report, so the daemon job no longer outlives the test's scoped storage (simplicity, scala-zio, consistency lenses); canary: job disabled, test red | none |
| 5.3 | dsp-api | complete | a75728001 | Review fix round 1 (fixup!): scalafmt of the report wait; 5.2 was committed with `just check` failing (exit hidden by a pipe) | none |
| 5.4 | dsp-api | complete | df0a817df | Review fix round 2 (fixup!): wait for a non-empty report (writer closed) instead of an existing one, polled every 20 ms (consistency, simplicity re-review); canary: job disabled, test red | none |
| 6.1 | dsp-api | complete | 95bf59afd | canRemoveValue checks resource view right after the load, answers the missing-value 404 for a value the caller cannot view, and checks delete before the class and type checks; endpoint description and editing-values docs; e2e suite in ValuesEndpointsE2ESpec (red before fix: hidden resource wrong class 400, hidden value 403, view-but-not-delete wrong class 400; the correct request on the hidden resource, anonymous, unknown resource 404 and the delete-permitted control were already as decided); ValuesResponderV2Spec, ValuesEraseSpec (history-only erase included), ResourcesResponderV2Spec pass | none |
| 6.2 | dsp-api | complete | a729cb4fb | Review fix round 1 (fixup!): resource load plus view and delete checks extracted into findDeletableValue, so later checks in canRemoveValue only see an authorized pair (dune DUNE-001); docs and comment no longer claim 403 whatever the request names, since property checks answer first, and state the 400 for a delete-permitted caller (consistency) | none |

## Deferrals

- Phase 2 (review, after the 2-round cap): `StandoffEndpointsE2ESpec.mappingExists` imports `slice.ontology.repo.CheckIriExistsQuery` from test-e2e, which breaks the docs-only webapi-ontology boundary rule (ARCH-MAP.md:699). Fix: call `IriService.checkIriExists(iri.value)` (confirm `IriService` is in the E2E layer), as another `fixup!` of the DEV-7714 commit. Warning, dune lens.
- Phase 3 (re-review Warning, not fixed in round 2): no test pins that a `ForbiddenException` from `extractEventsFromHistory` still fails project history; inducing it needs inconsistent value-version data or a stubbed `ReadResourcesService` in `ResourcesResponderV2Spec`. The omission side is pinned by the e2e test. Natural home: the re-typing of that inconsistency error to a non-Forbidden type, which removes the hazard structurally.

- Phase 5 (review, scope): admin authorization in ingest is a hand-written `ensureAdminScope` call per handler, and nothing fails if a handler drops its principal (the original bug). Structural fix: an admin endpoint base whose security logic checks the scope, or a check that every endpoint in `Endpoints.scala` is public or admin-tested. Changes the plan's scope; Phase 9/10 policy declarations are the natural home. Warning, dune lens (DUNE-002).

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
- Phase 4: no e2e, test-it or webapi unit spec creates a resource as the anonymous user (searched `AnonymousUser` / `anonymousUser` and every `post*` call to `/v2/resources` in test-e2e, test-it and testkit for a missing user argument), so no spec moved to a real user. Nothing for the PR to list.
- Phase 4: the status for anonymous `POST /v2/resources` was already 403 (create-permission check); the change is the message and the order: an anonymous request now gets 403 before body validation and ontology checks, where a malformed body or a system-project target answered 400 before.
- Phase 4 review suggestions (not fixed): no test pins the new order (anonymous with a malformed body or a system-project target now answers 403, not 400); the 404 "nothing created" check also held before the fix, only the message assertion discriminates; `ZIO.fail(..).when(..).unit` would match `ensureUserHasPermission`'s style; the handler's own anonymous message duplicates `AuthorizationRestService`'s generic one (kept: owner-decided wording). Security lens traced `attachedToUser` substitution: every anonymous combination ends in 403 or 400 before any write. ARCH-MAP refresh not needed (no component or dependency change; dune DUNE-001 on staleness).
- Phase 4 re-review suggestion (not fixed): the endpoint description says "Anonymous users are refused" without the 403 the docs name (simplicity, dune low). Phase 9/10 policy declarations replace this prose.
- Phase 5: the plan and the decided behaviours say an admin-scope token gets 200 "work in progress"; the endpoint declares `statusCode(StatusCode.Accepted)` and answers 202. Unchanged; the spec asserts 202.
- Phase 5: `ARCH-MAP.md` ingest component lists `/reports/...`; the route is `/report/asset-overview`. Pre-existing; ARCH-MAP is owned by `/dune:dune-map`. A fingerprint refresh is not needed for this phase (no component or dependency change); DUNE-001 refuted on that ground.
- Phase 5 review suggestions (not fixed): no test pins that a non-admin request never forks the job (a reordered check would still answer 401; needs a recording `ReportService`, which is a final case class); no `read:project` token case (same code path as `write:project`); the spec's 13-layer list mirrors the sibling specs, no shared ingest test layer exists; `createAssetOverReports` is declared after the val that uses it (pre-existing). Security lens: the job is reachable from no other route.
- Phase 5 re-review suggestions (not fixed, cap reached): the spec hard-codes the `reports` folder and `asset_overview_report` prefix that `ReportService` owns, as `MaintenanceEndpointsSpec` does for `reports` (dune, low); the await helper duplicates `MaintenanceEndpointsSpec.awaitTrue` (no shared test util); the wait still ends just before the job's final `toAbsolutePath` and log line, which touch no storage.

- Phase 6 review (refuted): trimming the delete-permitted control (drop its 200, reuse seeded data) contradicts the Phase 6 checkbox and the self-created-data decision (simplicity); the anonymous and unknown-resource tests do not discriminate the reorder but are plan-mandated regression guards, and the anonymous one passed, so it reaches the responder (scala-zio).
- Phase 6 review suggestions (not fixed): no erase-path test of the new order (SystemAdmin passes every check; ValuesEraseSpec green); erase endpoint description does not repeat the permissions (dune DUNE-002/003); the `!contains("Thing")` assertion is a weak smoke check beside `distinct.size == 1`; a hidden link value on a visible resource is not tested separately (same code path); `deleteValueV2` takes the IRI lock before the permission checks, a negligible lock-contention signal beside the accepted existence oracle (security, pre-existing).
- Phase 6 re-review (refuted): DUNE-003 says a class or type check inserted above `findDeletableValue` would fail nothing; the hidden-resource e2e test (wrong class and wrong type must give the same 403) fails on exactly that, and the ontology-only property checks answering first are owner-decided. DUNE-002 (ARCH-MAP staleness): the map's "no single chokepoint" note is about read and write permission enforcement platform-wide and stays true; no component or dependency change.
- Phase 6 re-review suggestions (not fixed): `findDeletableValue` and `canRemoveValue` each derive the internal property IRI from `deleteValue.propertyIri` (dune DUNE-004, low); the Scaladoc's first sentence could be trimmed (simplicity); the docs paragraph does not repeat that anonymous and unknown-property requests answer first (consistency).
