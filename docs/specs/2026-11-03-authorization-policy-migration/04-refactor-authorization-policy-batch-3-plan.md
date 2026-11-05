---
title: "refactor: migrate the endpoints that need an authorization decision, and close the ratchet (batch 3)"
type: refactor
date: 2026-11-03
author: "Raitis Veinbahs"
status: draft
repositories: []
linear: DEV-7725
---

# refactor: migrate the endpoints that need an authorization decision, and close the ratchet (batch 3)

## Overview

Batch 3 of the migration in `01-refactor-authorization-policy-migration-plan.md`. It migrates the 37 endpoints whose behaviour today is a gap, or does
not fit any policy case. Each phase implements the owner's answer to one of the Decisions Q1-Q9 below. The
last phase deletes the hand-called checks the migration made dead and tightens the ratchet. Phase numbers run through all three batch plans.

## Problem Statement / Motivation

Declaring a policy on these endpoints as they are would label a gap as intended. Examples: public membership
reads that the user endpoint blanks, counts that ignore permissions, and permission changes that need only Modify.
Some endpoints need policy cases that do not exist yet (self-or-SystemAdmin, ProjectAdmin of any project).

## Proposed Solution

- The phases are written for the recommended option of each question. If the owner picks another option,
  the phase's checkboxes are rewritten before execution starts (H1).
- Phase 27 removes the dead `ensure*` helpers, `V3Authorizer` methods and unused endpoint bases. It adds a lint
  rule for `Policy.key`/`withInfo`, updates the ADR, CONTEXT, conventions and ARCH-MAP, and deletes the baseline if
  it is empty.

## Technical Considerations

The shared rules live in the main plan (`01-refactor-authorization-policy-migration-plan.md`): Proposed Solution (the six steps per phase, the code shape
and the matrix rule) and Technical Considerations (order of answers, anonymous 401, unknown project, handlers
without a `User`, kept 400s and 404s, domain error channels, `SystemUser`, package privacy, decision-6 tests, the
ontology lock). Every phase applies them; this plan repeats only what is specific to the batch.

Batch-specific points:

- The new policy cases (`SelfOrSystemAdmin`, `ProjectAdminOfAnyProject`) are added to
  `AuthorizationMatrixE2ESpec.allowedBy`.
- Resource erase obtains its project from the resource IRI's shortcode, so nothing is read as `SystemUser`.

## Decisions

This batch starts once these are answered (H1). Each lists the recommended option first, and the phases are written for
that option. If the owner picks another option, the phase's checkboxes are rewritten before it starts.

| Id | Endpoints (phase) | Question | Options |
| --- | --- | --- | --- |
| Q1 | user writes (18) | `DELETE /admin/users/iri/{userIri}`, `PUT .../BasicUserInformation`, `.../Password` and `.../Status` admit the user themselves or a SystemAdmin. No policy case says that. | **a) New case `Policy.SelfOrSystemAdmin(userFrom)`**, enforced in the security logic with the path `userIri` as a security input. b) `Authenticated(reason)`, with the check left in `UserRestService`. |
| Q2 | user writes (18) | `GET /admin/users` admits a ProjectAdmin of any project. `POST /admin/users` admits the same, but requires SystemAdmin when `systemAdmin=true`. | **a) New case `Policy.ProjectAdminOfAnyProject`**, enforced in the security logic; the `systemAdmin=true` check stays in the handler. b) `Authenticated(reason)`. |
| Q3 | user reads (19) | `GET /admin/users/iri/{userIri}/{group,project,project-admin}-memberships` are public. `GET /admin/users/iri/{userIri}` blanks exactly that data for anyone except the user and a SystemAdmin. | **a) Restrict to self or SystemAdmin (Q1's case).** b) Keep them public and declare it. c) ProjectAdmin of a project the user is a member of. Check dsp-das usage before deciding. |
| Q4 | permissions (20), legal-info (21) | Creating an AP or DOAP is ProjectAdmin, but changing or deleting one is SystemAdmin only. `PUT legal-info/copyright-holders` is SystemAdmin while `POST` is ProjectAdmin. `POST ap/doap` do not check that `forGroup`, `forResourceClass` and `forProperty` belong to the target project. | **a) Keep SystemAdmin for the changes. On create, assert that `forGroup` is built-in or of `grant.project`, and that the class or property is from one of the project's ontologies or a built-in ontology (400 otherwise).** b) Also relax the changes to ProjectAdmin, with the project taken from the permission IRI. |
| Q5 | ontology can-do (22) | `candeleteclass` and `candeleteproperty` admit anonymous callers, `candeleteontology` requires authentication, and all three answer `canDo=false` to non-admins. `canreplacecardinalities` and `candeletecardinalities` answer 403 to non-admins. | **a) Declare what they do now:** class and property `Public` with the optional-user factory (Phase 12), ontology `Authenticated`, and the two cardinality checks ProjectAdmin through the grant. b) Make all five ProjectAdmin. c) Make all five answer `canDo=false` without a 403. |
| Q6 | erase (23) | `/v2/values/erase` and `/erasehistory` are SystemAdmin, while `/v2/resources/erase` admits ProjectAdmin. | **a) Keep both:** values SystemAdmin; resource erase ProjectAdmin through a grant on the resource's project. b) Make resource erase SystemAdmin. c) Make value erase ProjectAdmin. |
| Q7 | `PUT /v2/resources` (24) | Changing a resource's permissions needs only Modify; for values it needs ChangeRights. | **a) Require ChangeRights when the body carries `hasPermissions`** (403 otherwise), then declare `ObjectAccess`. b) Declare as is. |
| Q8 | resource listings (25) | `GET /v2/resources/info` lists every resource of a project, deleted ones included, without filtering; it is documented as public for incremental sync (DEV-7632). `GET /v3/projects/{projectIri}/resourcesPerOntology` counts over the whole data graph. | **a) Keep both public and declare a reason that names the exposure.** b) Require ProjectMember. c) Filter by permission. |
| Q9 | counts (26) | The five count endpoints ignore the caller's permissions (DEV-7718), so a gravsearch count can probe hidden values. | **a) Out of this plan:** they stay in the baseline, and DEV-7718 migrates them to `ObjectAccess` once counts are filtered. b) Fix DEV-7718 here. c) Declare `Public` with a reason naming the gap. |

### Owner answers (2026-11-05)

| Id | Answer |
| --- | --- |
| Q1 | a) `Policy.SelfOrSystemAdmin(userFrom)` |
| Q2 | a) `Policy.ProjectAdminOfAnyProject`; the `systemAdmin=true` check stays in the handler |
| Q3 | Postponed. The membership reads stay in the baseline and are aligned outside this PR; Phase 19 does not run. dsp-app defines the three reads but code search found no caller; dsp-tools `get` calls `project-memberships`. |
| Q4 | Asked in three parts. Create validates `forGroup`, `forResourceClass` and `forProperty` (400 otherwise); changing or deleting a permission stays SystemAdmin; `PUT copyright-holders` stays SystemAdmin. That is option a. |
| Q5 | a) Declare what they do now |
| Q6 | a) Keep both |
| Q7 | Unanswered. Phase 24 waits for it. |
| Q8 | a) Keep both public, with a reason that names the exposure |
| Q9 | a) Out of this plan; DEV-7718 |

## Implementation Phases

Verification commands are those in `CLAUDE.md`: `just test-unit`, `just test-e2e`, `just check`, and filtered
runs such as `bazel test //modules/test-e2e:test --test_filter='.*EndpointPolicyE2ESpec.*'`. Every phase that
removes baseline lines runs `EndpointPolicyE2ESpec`, and every phase that adds matrix rows runs
`AuthorizationMatrixE2ESpec`.

### Batch 3: decided by the owner first

#### Phase 18: Admin user writes (Q1, Q2)

Endpoints (6): Appendix A, phase 18.

- [ ] `Policy.SelfOrSystemAdmin(userFrom)` and `Policy.ProjectAdminOfAnyProject`, with `requirement` sentences
- [ ] `BaseEndpoints.selfOrSystemAdminPolicyEndpoint` (path `userIri` as a security input, decided in the security logic) and `projectAdminOfAnyProjectPolicyEndpoint`
- [ ] `BaseEndpointsSpec` covers both factories, including built-in target users
- [ ] `UsersEndpoints` and `UserRestService`: `DELETE /iri/{userIri}`, `BasicUserInformation`, `Password` and `Status` use the self-or-admin factory; the password re-check stays in the handler
- [ ] `UsersEndpoints` and `UserRestService`: `GET /admin/users` and `POST /admin/users` use the any-project factory; the `systemAdmin=true` check stays in `POST`
- [ ] `AuthorizationMatrixE2ESpec.allowedBy` maps the two new cases
- [ ] e2e test: a non-admin targeting a built-in user now answers 403 (was 400); the users docs say so
- [ ] Matrix rows for the 6 endpoints, with a "self" principal for the self-or-admin rows
- [ ] Baseline: remove the 6 lines and lower `baselineSize`
- [ ] Run `just test-unit`; it passes
- [ ] Run `bazel test //modules/test-e2e:test --test_filter='.*(EndpointPolicy|AuthorizationMatrix|User).*'`; it passes
- [ ] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

#### Phase 19: Admin user reads (Q3)

Endpoints (6): Appendix A, phase 19.

- [ ] `UsersEndpoints`: `GET /iri/{userIri}`, `/email/{email}` and `/username/{username}` use `publicPolicyEndpointWithUser`, reason "answers the public profile; private fields only to the user and SystemAdmin"
- [ ] `UsersEndpoints`: the three `.../memberships` reads use `selfOrSystemAdminPolicyEndpoint`
- [ ] e2e tests: anonymous and another user get 401/403 on the membership reads, and self and SystemAdmin get 200
- [ ] `docs/03-endpoints/api-admin/users.md` records the membership change
- [ ] Matrix rows for the 3 membership reads
- [ ] Baseline: remove the 6 lines and lower `baselineSize`
- [ ] Run `bazel test //modules/test-e2e:test --test_filter='.*(EndpointPolicy|AuthorizationMatrix|User).*'`; it passes
- [ ] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

#### Phase 20: Admin permission writes (Q4)

Endpoints (8): Appendix A, phase 20.

- [ ] `PermissionsEndpoints`: the 6 `PUT`/`DELETE` routes use `systemAdminPolicyEndpoint`, and their `ensureSystemAdmin` calls are removed
- [ ] `PermissionsEndpoints` and `PermissionRestService`: `POST ap` and `POST doap` use `projectAdminPolicyEndpoint` (`projectFrom` = "body forProject") and the grant
- [ ] `PermissionsResponder`: on create, `forGroup` must be built-in or belong to `grant.project`, and `forResourceClass`/`forProperty` must come from an ontology of `grant.project` or a built-in ontology; otherwise 400
- [ ] Responder tests for each mismatch, and for a built-in group and a knora-base class
- [ ] `docs/03-endpoints/api-admin/permissions.md` records the new 400s, and that an unknown `forProject` on create answers 403 (was 404)
- [ ] `PermissionRestService.ensureProjectIriExistsAndUserHasAccess` is deleted (no callers left)
- [ ] Matrix rows for the 8 endpoints
- [ ] Baseline: remove the 8 lines and lower `baselineSize`
- [ ] Run `bazel test //modules/test-e2e:test --test_filter='.*(EndpointPolicy|AuthorizationMatrix|Permission).*'`; it passes
- [ ] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

#### Phase 21: Legal-info copyright-holder replacement (Q4)

Endpoints (1): `PUT /admin/projects/shortcode/{projectShortcode}/legal-info/copyright-holders`.

- [ ] `ProjectsLegalInfoEndpoints`: the route uses `systemAdminPolicyEndpoint`; the `ensureSystemAdmin` in `ProjectsLegalInfoRestService` is removed
- [ ] Matrix row
- [ ] Baseline: remove the line and lower `baselineSize`
- [ ] Run `bazel test //modules/test-e2e:test --test_filter='.*(EndpointPolicy|AuthorizationMatrix|LegalInfo).*'`; it passes
- [ ] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

#### Phase 22: Ontology can-do checks (Q5)

Endpoints (5): Appendix A, phase 22.

- [ ] `OntologiesEndpoints`: `candeleteclass` and `candeleteproperty` use `publicPolicyEndpointWithUser`, reason "answers only whether the caller could delete"; `candeleteontology` uses `authenticatedPolicyEndpoint` with the same reason
- [ ] The three can-delete responders compute "is admin of the ontology's project" with `AuthorizationRestService.isSystemOrProjectAdmin` instead of `OntologyCacheHelpers`
- [ ] `canreplacecardinalities` and `candeletecardinalities` use `projectAdminPolicyEndpoint`; `RestCardinalityService` and the responder obtain and take the grant (anonymous answers 401, was 403)
- [ ] `RestCardinalityService`'s own copy of the admin rule (`isSystemAdmin || isProjectAdmin`) is removed
- [ ] `OntologyCacheHelpers.checkPermissionsForOntologyUpdate` and the permission part of `checkOntologyAndEntityIrisForUpdate` are deleted (no callers left)
- [ ] Matrix rows for the two cardinality checks and for `candeleteontology` (`Authenticated`: every non-anonymous principal is allowed and gets `canDo`)
- [ ] Baseline: remove the 5 lines and lower `baselineSize`
- [ ] Run `bazel test //modules/test-e2e:test --test_filter='.*(EndpointPolicy|AuthorizationMatrix|Ontolog|Cardinalit).*'`; it passes
- [ ] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

#### Phase 23: Erase (Q6)

Endpoints (3): `POST /v2/resources/erase`, `POST /v2/values/erase`, `POST /v2/values/erasehistory`.

- [ ] `ValuesEndpoints`: both value erase routes use `systemAdminPolicyEndpoint`; their `ensureSystemAdmin` calls in `ValuesRestService` are removed
- [ ] `ResourcesEndpoints`: `POST /v2/resources/erase` uses `projectAdminPolicyEndpoint` (`projectFrom` = "body resource IRI's project"; anonymous answers 401, was 403)
- [ ] `ResourcesRestService`: the erase body is parsed without a user (`deleteOrEraseResourceRequestV2` split as in Phase 8); the project comes from the resource IRI's shortcode, so nothing is read as `SystemUser`; then the grant is obtained
- [ ] `ResourcesResponderV2.eraseResourceV2` takes the grant, reads the resource as `grant.user`, and replaces its inline `isProjectAdmin` check with "the resource's `attachedToProject` is `grant.project`" (403 otherwise)
- [ ] Responder test: a grant for another project erases nothing
- [ ] Matrix rows for the 3 endpoints (fresh resource or value per cell)
- [ ] Baseline: remove the 3 lines and lower `baselineSize`
- [ ] Run `bazel test //modules/test-e2e:test --test_filter='.*(EndpointPolicy|AuthorizationMatrix|Erase).*'`; it passes
- [ ] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

#### Phase 24: Resource permission changes (Q7)

Endpoints (1): `PUT /v2/resources`.

- [ ] `ResourcesResponderV2` update: when the request carries `hasPermissions`, the caller needs ChangeRights on the resource (403 otherwise), as for values
- [ ] e2e test: Modify without ChangeRights cannot change permissions, but can still change the label
- [ ] `docs/03-endpoints/api-v2/editing-resources.md` records the rule
- [ ] `ResourcesEndpoints`: the route uses `authenticatedObjectAccessPolicyEndpoint` (a write: anonymous 401, as Phase 3 decided); `AnonymousWritesE2ESpec` lists it
- [ ] Baseline: remove the line and lower `baselineSize`
- [ ] Run `bazel test //modules/test-e2e:test --test_filter='.*(EndpointPolicy|Resources).*'`; it passes
- [ ] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

#### Phase 25: Resource listings (Q8)

Endpoints (2): `GET /v2/resources/info` and `GET /v3/projects/{projectIri}/resourcesPerOntology`.

- [ ] `ResourceInfoEndpoints`: `publicPolicyEndpoint`, reason "incremental sync: lists every resource IRI, class and date of a project, deleted included"
- [ ] V3 public factory: `V3BaseEndpoint.publicPolicy(errorOut, reason)`, declaring `Policy.Public`
- [ ] `V3ProjectsEndpoints`: `resourcesPerOntology` uses it, reason "aggregate counts per class, no resource data"
- [ ] Baseline: remove the 2 lines and lower `baselineSize`
- [ ] Run `bazel test //modules/test-e2e:test --test_filter='.*EndpointPolicyE2ESpec.*'`; it passes
- [ ] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

#### Phase 26: Count endpoints (Q9)

Endpoints (5): the five count routes. With option a they stay in the baseline, and this phase only makes that
visible.

- [ ] The comment at the top of `endpoints-without-policy.txt` says the remaining lines wait on DEV-7718
- [ ] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

#### Phase 27: Remove dead checks and tighten the ratchet

- [ ] `OntologyCacheHelpers.checkPermissionsForOntologyUpdate`: if Q5 left a caller, migrate it and delete the method
- [ ] `AuthorizationRestService`: delete each `ensureSystemAdminOrProjectAdmin*`, `ensureSystemAdminOrProjectAdminInAnyProject` and `ensureProjectMember` overload that has no caller left, and make the ones still used by the PDP private
- [ ] `V3Authorizer.ensureSystemAdmin` and `ensureSystemAdminOrProjectAdmin`: delete if unused
- [ ] `BaseEndpoints.securedEndpoint`, `withUserEndpoint` and `publicEndpoint`: delete each one no endpoint uses; if the counts still use `withUserEndpoint`, its scaladoc names DEV-7718
- [ ] `tools/lint/check_authz.py`: a rule that `Policy.key` and `withInfo` appear only in `slice.common.api` and `slice.api.v3.V3BaseEndpoint`, with tests in `check_authz_test.py`
- [ ] If the baseline is empty: delete it, and `EndpointPolicyE2ESpec` asserts that every endpoint declares a policy
- [ ] `CONTEXT.md` and `docs/contexts/identity-access/CONTEXT.md` define `Policy`, `ProjectCandidate`, `ProjectAdminGrant`, `ProjectMemberGrant` and the new policy cases, distinct from **Administrative permission**
- [ ] ADR-0013: decision 4 lists the new policy cases; decision 9 records the completed migration
- [ ] `CONVENTIONS.md`, `REVIEW.md` and `docs/development/dsp-api-conventions.md` name the new factories and the matrix rule
- [ ] Run `/dune:dune-map update` so `ARCH-MAP.md` names the policy factories, the grants and the ratchet state
- [ ] Run `just check`; it passes
- [ ] Run `just test-unit`; it passes
- [ ] Run `just test-e2e`; it passes, except failures that also fail on `main` (`ValueHasXmlOnValueWriteE2ESpec`'s 6000-tag case times out locally)
- [ ] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

## Human Actions

| Id | Action | Who | When | Why not the agent |
| --- | --- | --- | --- | --- |
| H1 | Answer Q1-Q9 under Decisions; for each, keep the recommendation or pick another option, and rewrite the affected phase here if it changes | owner | before start | Product and security decisions |
| H2 | Merge batch 2's PR before this one | owner | after ship | Merge order is the owner's decision |
| H3 | Release notes list the status-code changes above; check dsp-das against them, in particular the membership reads | owner | after ship | Frontend coordination |

## Acceptance Criteria

- [ ] Every endpoint of phases 18-26 in Appendix A declares the phase's policy and is gone from the baseline (the five counts excepted under Q9 option a)
- [ ] `baselineSize` is 5 (or 0, with the baseline deleted, if Q9 is not option a)
- [ ] Every project-scoped endpoint of this batch obtains its grant from the PDP, and its domain write takes the grant or `grant.project`
- [ ] Every responder that checks an object against `grant.project` has a wrong-project test
- [ ] Every role-based endpoint of this batch has a matrix row or an `exempt` entry with the 403/401 tests
- [ ] `OntologyCacheHelpers.checkPermissionsForOntologyUpdate` no longer exists
- [ ] Every endpoint of the three batch appendices declares a policy, except those Q9 leaves for DEV-7718
- [ ] The status-code changes below are in the PR body and in the endpoint docs
- [ ] `just check`, `just test-unit` and `just test-e2e` pass

### Status-code changes

Under the recommended options:

| Endpoint | Caller | Before | After |
| --- | --- | --- | --- |
| self-or-admin user writes | non-admin, built-in target user | 400 | 403 |
| `GET /admin/users/iri/{userIri}/*-memberships` | anonymous / another user | 200 | 401 / 403 |
| `POST /admin/permissions/ap`, `doap` | unknown `forProject` | 404 | 403 |
| `POST /admin/permissions/ap`, `doap` | group, class or property of another project | 200 | 400 |
| `canreplacecardinalities`, `candeletecardinalities` | anonymous | 403 | 401 |
| `POST /v2/resources/erase` | anonymous | 403 | 401 |
| `PUT /v2/resources` with `hasPermissions` | Modify without ChangeRights | 200 | 403 |
| `PUT /v2/resources` | anonymous | 403 | 401 |

## Dependencies & Risks

- **Depends on batch 2**: `publicPolicyEndpointWithUser` (Phase 12), the parser split (Phase 8), the
  permission-free IRI check (Phase 16) and the v3 factories (Phase 14).
- **Front-end.** The membership-read restriction (Q3) may break dsp-das screens. Check before merging (H3).

## Success Metrics

- Baseline: 37 lines before this batch, 5 after (0 if Q9 is not option a).

## References

- ADR-0013: `docs/adr/0013-authorization-policy-enforcement.md`
- Pilot: `modules/webapi/src/main/scala/org/knora/webapi/slice/api/admin/AdminListsEndpoints.scala`,
  `AdminListRestService.scala`, `slice/api/v2/mapping/StandoffRestService.scala`
- Factories: `modules/webapi/src/main/scala/org/knora/webapi/slice/common/api/BaseEndpoints.scala:86-140`
- PDP: `modules/webapi/src/main/scala/org/knora/webapi/slice/common/api/AuthorizationRestService.scala`
- Ratchet: `modules/test-e2e/src/test/scala/org/knora/webapi/slice/api/EndpointPolicyE2ESpec.scala`
- Matrix: `modules/test-e2e/src/test/scala/org/knora/webapi/slice/api/AuthorizationMatrixE2ESpec.scala`
- Learning: dasch-specs `learnings/design-decisions/security-logic-authenticates-body-buffering-precedes-it.md`
- Linear: DEV-7725 (this work), DEV-7718 (counts)
- Main plan: `01-refactor-authorization-policy-migration-plan.md` (vision and shared rules)

## Appendix A: Endpoint assignment

"Checked today" and "Where today" come from tracing each endpoint at commit `984f74c95`.

| Phase | Endpoint | Checked today | Where today |
| --- | --- | --- | --- |
| 18 | `DELETE /admin/users/iri/{userIri}` | Mixed | `slice/api/admin/service/UserRestService.scala:66` |
| 18 | `GET /admin/users` | Any project admin | `slice/api/admin/service/UserRestService.scala:56` |
| 18 | `POST /admin/users` | Mixed | `slice/api/admin/service/UserRestService.scala:87` |
| 18 | `PUT /admin/users/iri/{userIri}/BasicUserInformation` | Mixed | `slice/api/admin/service/UserRestService.scala:130` |
| 18 | `PUT /admin/users/iri/{userIri}/Password` | Mixed | `slice/api/admin/service/UserRestService.scala:149` |
| 18 | `PUT /admin/users/iri/{userIri}/Status` | Mixed | `slice/api/admin/service/UserRestService.scala:170` |
| 19 | `GET /admin/users/email/{email}` | Mixed | `slice/api/admin/service/UserRestService.scala:76` |
| 19 | `GET /admin/users/iri/{userIri}` | Mixed | `slice/api/admin/service/UserRestService.scala:117` |
| 19 | `GET /admin/users/iri/{userIri}/group-memberships` | Public | `-` |
| 19 | `GET /admin/users/iri/{userIri}/project-admin-memberships` | Public | `-` |
| 19 | `GET /admin/users/iri/{userIri}/project-memberships` | Public | `-` |
| 19 | `GET /admin/users/username/{username}` | Mixed | `slice/api/admin/service/UserRestService.scala:112` |
| 20 | `DELETE /admin/permissions/{permissionIri}` | SystemAdmin | `slice/api/admin/service/PermissionRestService.scala:86` |
| 20 | `POST /admin/permissions/ap` | ProjectAdmin | `slice/api/admin/service/PermissionRestService.scala:56` |
| 20 | `POST /admin/permissions/doap` | ProjectAdmin | `slice/api/admin/service/PermissionRestService.scala:96` |
| 20 | `PUT /admin/permissions/doap/{permissionIri}` | SystemAdmin | `slice/api/admin/service/PermissionRestService.scala:143` |
| 20 | `PUT /admin/permissions/{permissionIri}/group` | SystemAdmin | `slice/api/admin/service/PermissionRestService.scala:155` |
| 20 | `PUT /admin/permissions/{permissionIri}/hasPermissions` | SystemAdmin | `slice/api/admin/service/PermissionRestService.scala:107` |
| 20 | `PUT /admin/permissions/{permissionIri}/property` | SystemAdmin | `slice/api/admin/service/PermissionRestService.scala:121` |
| 20 | `PUT /admin/permissions/{permissionIri}/resourceClass` | SystemAdmin | `slice/api/admin/service/PermissionRestService.scala:132` |
| 21 | `PUT /admin/projects/shortcode/{projectShortcode}/legal-info/copyright-holders` | SystemAdmin | `slice/api/admin/service/ProjectsLegalInfoRestService.scala:109` |
| 22 | `GET /v2/ontologies/candeleteclass/{resourceClassIri}` | Mixed | `responders/v2/OntologyResponderV2.scala:994` |
| 22 | `GET /v2/ontologies/candeleteontology/{ontologyIri}` | Mixed | `responders/v2/OntologyResponderV2.scala:1176` |
| 22 | `GET /v2/ontologies/candeleteproperty/{propertyIri}` | Mixed | `responders/v2/OntologyResponderV2.scala:1073` |
| 22 | `GET /v2/ontologies/canreplacecardinalities/{resourceClassIri}` | ProjectAdmin | `slice/api/v2/ontologies/RestCardinalityService.scala:61` |
| 22 | `POST /v2/ontologies/candeletecardinalities` | ProjectAdmin | `responders/v2/OntologyResponderV2.scala:935` |
| 23 | `POST /v2/resources/erase` | ProjectAdmin | `responders/v2/ResourcesResponderV2.scala:456` |
| 23 | `POST /v2/values/erase` | SystemAdmin | `slice/api/v2/values/ValuesRestService.scala:188` |
| 23 | `POST /v2/values/erasehistory` | SystemAdmin | `slice/api/v2/values/ValuesRestService.scala:200` |
| 24 | `PUT /v2/resources` | Object access | `responders/v2/ResourcesResponderV2.scala:141` |
| 25 | `GET /v2/resources/info` | Public | `slice/api/v2/resources/info/ResourceInfoRestService.scala:73` |
| 25 | `GET /v3/projects/{projectIri}/resourcesPerOntology` | Public | `slice/api/v3/resources/ResourcesRestServiceV3.scala:28` |
| 26 | `GET /v2/search/count/{searchTerm}` | Public | `slice/api/v2/search/SearchRestService.scala:188` |
| 26 | `GET /v2/searchStillImageRepresentationsCount/{resourceIri}` | Public | `responders/v2/SearchResponderV2.scala:482` |
| 26 | `GET /v2/searchbylabel/count/{searchTerm}` | Public | `slice/api/v2/search/SearchRestService.scala:48` |
| 26 | `GET /v2/searchextended/count/{param1}` | Public | `responders/v2/SearchResponderV2.scala:823` |
| 26 | `POST /v2/searchextended/count` | Public | `responders/v2/SearchResponderV2.scala:823` |
