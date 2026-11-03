---
title: "refactor: declare authorization policies with the existing factories (batch 1)"
type: refactor
date: 2026-11-03
author: "Raitis Veinbahs"
status: draft
repositories: []
linear: DEV-7725
---

# refactor: declare authorization policies with the existing factories (batch 1)

## Overview

Batch 1 of the migration in `01-refactor-authorization-policy-migration-plan.md`. It migrates the 75 endpoints that need no new machinery: the five
policy factories in `BaseEndpoints` and `AuthorizationRestService.authorizeProjectAdminById`, as the pilot
(admin lists, v2 standoff) uses them. Phase numbers run through all three batch plans.

## Problem Statement / Motivation

These endpoints already check the right thing by hand, or need no check. They stay invisible to the ratchet
and the matrix until they declare a policy, and the 21 project-scoped ones can drop a hand-called check that a
future edit could lose. They are the cheapest part of the migration and prove the per-phase routine at scale.

## Proposed Solution

- Phases 1-3 swap the endpoint base for `systemAdminPolicyEndpoint`, `publicPolicyEndpoint` or
  `objectAccessPolicyEndpoint` and delete the `ensureSystemAdmin` calls the factory replaces.
- Phases 4-7 move 21 admin endpoints whose project IRI is in the path or the body to `projectAdminPolicyEndpoint`,
  obtain the grant with `authorizeProjectAdminById`, and act on `grant.project`.
- Phase 1 also adds the matrix spec's `exempt` set, which batches 2 and 3 reuse.

## Technical Considerations

The shared rules live in the main plan (`01-refactor-authorization-policy-migration-plan.md`): Proposed Solution (the six steps per phase, the code shape
and the matrix rule) and Technical Considerations (order of answers, anonymous 401, unknown project, handlers
without a `User`, kept 400s and 404s, domain error channels, `SystemUser`, package privacy, decision-6 tests, the
ontology lock). Every phase applies them; this plan repeats only what is specific to the batch.

Batch-specific points:

- `systemAdminPolicyEndpoint` decides before body decoding, so the order of 400 and 403 changes on
  `POST /admin/projects` and `PUT /admin/users/iri/{userIri}/SystemAdmin` (Phase 1).
- `authorizeProjectAdminById` answers 403 for an unknown project, where `PUT /admin/projects/iri/{projectIri}` and
  the four permission reads answered 404 (Phases 4, 6).
- Phase 5 keeps `ensureNotABuiltInUser` first and the duplicate-membership 400s.

## Implementation Phases

Verification commands are those in `CLAUDE.md`: `just test-unit`, `just test-e2e`, `just check`, and filtered
runs such as `bazel test //modules/test-e2e:test --test_filter='.*EndpointPolicyE2ESpec.*'`. Every phase that
removes baseline lines runs `EndpointPolicyE2ESpec`, and every phase that adds matrix rows runs
`AuthorizationMatrixE2ESpec`.

### Batch 1: existing machinery

#### Phase 1: Declare SystemAdmin endpoints

Endpoints (7): Appendix A, phase 1.

- [ ] `MaintenanceEndpoints`: the three `/admin/maintenance/*` routes use `systemAdminPolicyEndpoint`; drop the `ensureSystemAdmin` calls in `MaintenanceRestService`
- [ ] `ManagementEndpoints.postStartCompaction` uses `systemAdminPolicyEndpoint`; drop its `ensureSystemAdmin` in `ManagementRestService`
- [ ] `ProjectsEndpoints`: `POST /admin/projects` and `DELETE /admin/projects/shortcode/{projectShortcode}/erase` use `systemAdminPolicyEndpoint`; drop both `ensureSystemAdmin` calls in `ProjectRestService`; the erase feature-flag check stays
- [ ] `UsersEndpoints`: `PUT /admin/users/iri/{userIri}/SystemAdmin` uses `systemAdminPolicyEndpoint`; drop its `ensureSystemAdmin` in `UserRestService`
- [ ] e2e tests: a non-admin with a malformed body answers 403 on `POST /admin/projects` (it answered 400 before), and a non-admin targeting a built-in user on `PUT .../SystemAdmin` answers 403 (was 400)
- [ ] `AuthorizationMatrixE2ESpec`: the `exempt` set (key -> reason), subtracted by the row-coverage assertion
- [ ] Matrix rows for `POST /admin/projects`, `DELETE .../erase` and `PUT .../SystemAdmin`
- [ ] Compaction and the three maintenance routes: `exempt` entries, plus the non-admin 403 and anonymous 401 e2e tests
- [ ] Baseline: remove the 7 lines and lower `baselineSize`
- [ ] Run `bazel test //modules/test-e2e:test --test_filter='.*(EndpointPolicy|AuthorizationMatrix|Maintenance|Management|Project|User).*'`; it passes
- [ ] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

#### Phase 2: Declare public endpoints

Endpoints (20): Appendix A, phase 2. The reason states why exposure is fine.

- [ ] `Policy.Public`'s scaladoc says "needs no credentials" instead of "reads no credentials" (the authentication endpoints read credentials as regular inputs)
- [ ] `AuthenticationEndpointsV2`: login, credential check and logout use `publicPolicyEndpoint`, reason "the endpoint is itself the credential check"
- [ ] `GroupsEndpoints`: `GET /admin/groups` and `GET /admin/groups/{groupIri}` use `publicPolicyEndpoint`, reason "group names and descriptions are public project metadata"
- [ ] `ProjectsEndpoints`: the 9 project, keyword and restricted-view-settings reads use `publicPolicyEndpoint`, reason "public project metadata"
- [ ] `ProjectsLegalInfoEndpoints`: the 2 license reads use `publicPolicyEndpoint`, reason "the licenses a project allows are shown on its public data"
- [ ] `ManagementEndpoints`: `GET /health` and `GET /version` use `publicPolicyEndpoint`, reason "operational status, no data"
- [ ] `OntologiesEndpoints`: `GET /v2/ontologies/metadata` and `/metadata/*` use `publicPolicyEndpoint`, reason "ontologies are public"
- [ ] Baseline: remove the 20 lines and lower `baselineSize`
- [ ] Run `bazel test //modules/test-e2e:test --test_filter='.*EndpointPolicyE2ESpec.*'`; it passes
- [ ] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

#### Phase 3: Declare object-access endpoints

Endpoints (27): Appendix A, phase 3. Each `enforcedIn` names the place that checks the object, taken from the
"Where today" column.

- [ ] `ResourcesEndpoints`: the 14 resource, graph, history, IIIF, TEI and preview routes in phase 3 use `objectAccessPolicyEndpoint`
- [ ] `ValuesEndpoints`: the 5 value routes in phase 3 use `objectAccessPolicyEndpoint`
- [ ] `SearchEndpoints`: the 7 search routes in phase 3 (not the counts) use `objectAccessPolicyEndpoint`
- [ ] `FilesEndpoints`: `GET /admin/files/{projectShortcode}/{filename}` uses `objectAccessPolicyEndpoint`
- [ ] Baseline: remove the 27 lines and lower `baselineSize`
- [ ] Run `bazel test //modules/test-e2e:test --test_filter='.*(EndpointPolicy|Resources|Values|Search|Files).*'`; it passes
- [ ] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

#### Phase 4: Admin projects by project IRI

Endpoints (12): Appendix A, phase 4. `projectFrom` = "path projectIri".

- [ ] `ProjectsEndpoints`: `AllData`, `members`, `admin-members`, `PUT /iri/{projectIri}` and `POST`/`DELETE RestrictedViewSettings` (by IRI) use `projectAdminPolicyEndpoint`
- [ ] `ProjectRestService`: the six methods take a `ProjectCandidate`, obtain the grant, and act on `grant.project`; the separate project lookup in `PUT /iri/{projectIri}` is removed
- [ ] `ViewRestrictionsEndpoints` and `ViewRestrictionsByPropertyEndpoints`: the 6 `view-restrictions/*` routes use `projectAdminPolicyEndpoint`
- [ ] `ViewRestrictionsRestService` and `ViewRestrictionsByPropertyRestService` take a `ProjectCandidate` and act on `grant.project`
- [ ] e2e test: `PUT /admin/projects/iri/{unknown}` answers 403 (was 404); `docs/03-endpoints/api-admin/projects.md` says so
- [ ] Matrix rows for the 12 endpoints
- [ ] Baseline: remove the 12 lines and lower `baselineSize`
- [ ] Run `bazel test //modules/test-e2e:test --test_filter='.*(EndpointPolicy|AuthorizationMatrix|Project|ViewRestriction).*'`; it passes
- [ ] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

#### Phase 5: Admin user project memberships

Endpoints (4): Appendix A, phase 5. `projectFrom` = "path projectIri".

- [ ] `UsersEndpoints`: `POST`/`DELETE project-memberships/{projectIri}` and `project-admin-memberships/{projectIri}` use `projectAdminPolicyEndpoint`
- [ ] `UserRestService`: the four methods take a `ProjectCandidate`; `ensureNotABuiltInUser` (400) stays before the grant
- [ ] The membership changes act on the granted project and keep their error channels: a duplicate add or a missing membership still answers 400. Where `KnoraUserService` has no overload with that channel for a `KnoraProject` (`removeUserFromProject`, `removeUserFromProjectAsAdmin`), one is added
- [ ] e2e tests: a duplicate add and a remove of a non-member still answer 400 for a ProjectAdmin
- [ ] Matrix rows for the 4 endpoints (fresh user per cell)
- [ ] Baseline: remove the 4 lines and lower `baselineSize`
- [ ] Run `bazel test //modules/test-e2e:test --test_filter='.*(EndpointPolicy|AuthorizationMatrix|User).*'`; it passes
- [ ] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

#### Phase 6: Admin permission reads by project IRI

Endpoints (4): Appendix A, phase 6. `projectFrom` = "path projectIri".

- [ ] `PermissionsEndpoints`: `GET /admin/permissions/{projectIri}`, `ap/{projectIri}`, `ap/{projectIri}/{groupIri}` and `doap/{projectIri}` use `projectAdminPolicyEndpoint`
- [ ] `PermissionRestService`: the four methods take a `ProjectCandidate`, obtain the grant, and query with `grant.project`; `ensureProjectIriExistsAndUserHasAccess` stays, because the Phase 20 create routes still use it
- [ ] e2e test: an unknown project answers 403 (was 404); `docs/03-endpoints/api-admin/permissions.md` says so
- [ ] Matrix rows for the 4 endpoints
- [ ] Baseline: remove the 4 lines and lower `baselineSize`
- [ ] Run `bazel test //modules/test-e2e:test --test_filter='.*(EndpointPolicy|AuthorizationMatrix|Permission).*'`; it passes
- [ ] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

#### Phase 7: Admin group creation

Endpoints (1): `POST /admin/groups`. `projectFrom` = "body project".

- [ ] `GroupsEndpoints`: `POST /admin/groups` uses `projectAdminPolicyEndpoint`
- [ ] `GroupRestService.postGroup` takes a `ProjectCandidate`; the group is created in `grant.project`
- [ ] Matrix row
- [ ] Baseline: remove the line and lower `baselineSize`
- [ ] Run `bazel test //modules/test-e2e:test --test_filter='.*(EndpointPolicy|AuthorizationMatrix|Group).*'`; it passes
- [ ] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

## Human Actions

| Id | Action | Who | When | Why not the agent |
| --- | --- | --- | --- | --- |
| H1 | Release notes list the status-code changes above; check dsp-das against them | owner | after ship | Frontend coordination |

## Acceptance Criteria

- [ ] Every endpoint of phases 1-7 in Appendix A declares the phase's policy and is gone from the baseline
- [ ] `baselineSize` is 100
- [ ] Every project-scoped endpoint of this batch obtains its grant from the PDP, and its domain write takes the grant or `grant.project`
- [ ] Every responder that checks an object against `grant.project` has a wrong-project test
- [ ] Every role-based endpoint of this batch has a matrix row or an `exempt` entry with the 403/401 tests
- [ ] The status-code changes below are in the PR body and in the endpoint docs
- [ ] `just check`, `just test-unit` and `just test-e2e` pass

### Status-code changes

| Endpoint | Caller | Before | After |
| --- | --- | --- | --- |
| `POST /admin/projects` | non-admin, malformed body | 400 | 403 |
| `PUT /admin/users/iri/{userIri}/SystemAdmin` | non-admin, built-in target user | 400 | 403 |
| `PUT /admin/projects/iri/{projectIri}` | unknown project | 404 | 403 |
| `GET /admin/permissions/{projectIri}`, `ap/{projectIri}`, `ap/{projectIri}/{groupIri}`, `doap/{projectIri}` | unknown project | 404 | 403 |

## Dependencies & Risks

- **None upstream.** This batch starts from `main`.
- **Batches 2 and 3 build on it.** They reuse the `exempt` set and the `Policy.Public` scaladoc change, so their
  branches start from this one, or from `main` after this PR has merged.

## Success Metrics

- Baseline: 175 lines before this batch, 100 after.

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
| 1 | `DELETE /admin/projects/shortcode/{projectShortcode}/erase` | SystemAdmin | `slice/api/admin/service/ProjectRestService.scala:116` |
| 1 | `POST /admin/maintenance/projects/{projectShortcode}/replace-user-iri` | SystemAdmin | `slice/api/admin/service/MaintenanceRestService.scala:79` |
| 1 | `POST /admin/maintenance/users/replace-iri` | SystemAdmin | `slice/api/admin/service/MaintenanceRestService.scala:63` |
| 1 | `POST /admin/maintenance/{action-name}` | SystemAdmin | `slice/api/admin/service/MaintenanceRestService.scala:35` |
| 1 | `POST /admin/projects` | SystemAdmin | `slice/api/admin/service/ProjectRestService.scala:92` |
| 1 | `POST /start-compaction` | SystemAdmin | `slice/api/management/ManagementRestService.scala:27` |
| 1 | `PUT /admin/users/iri/{userIri}/SystemAdmin` | SystemAdmin | `slice/api/admin/service/UserRestService.scala:182` |
| 2 | `DELETE /v2/authentication` | Public | `slice/api/v2/authentication/AuthenticationRestService.scala:101` |
| 2 | `GET /admin/groups` | Public | `-` |
| 2 | `GET /admin/groups/{groupIri}` | Public | `-` |
| 2 | `GET /admin/projects` | Public | `-` |
| 2 | `GET /admin/projects/Keywords` | Public | `-` |
| 2 | `GET /admin/projects/iri/{projectIri}` | Public | `-` |
| 2 | `GET /admin/projects/iri/{projectIri}/Keywords` | Public | `-` |
| 2 | `GET /admin/projects/iri/{projectIri}/RestrictedViewSettings` | Public | `-` |
| 2 | `GET /admin/projects/shortcode/{projectShortcode}` | Public | `-` |
| 2 | `GET /admin/projects/shortcode/{projectShortcode}/RestrictedViewSettings` | Public | `-` |
| 2 | `GET /admin/projects/shortcode/{projectShortcode}/legal-info/licenses` | Public | `-` |
| 2 | `GET /admin/projects/shortcode/{projectShortcode}/legal-info/licenses/{licenseIri}` | Public | `-` |
| 2 | `GET /admin/projects/shortname/{projectShortname}` | Public | `-` |
| 2 | `GET /admin/projects/shortname/{projectShortname}/RestrictedViewSettings` | Public | `-` |
| 2 | `GET /health` | Public | `slice/api/management/ManagementEndpoints.scala:74` |
| 2 | `GET /v2/authentication` | Public | `slice/api/v2/authentication/AuthenticationRestService.scala:36` |
| 2 | `GET /v2/ontologies/metadata` | Public | `slice/api/v2/ontologies/OntologiesRestService.scala:93` |
| 2 | `GET /v2/ontologies/metadata/*` | Public | `slice/api/v2/ontologies/OntologiesRestService.scala:100` |
| 2 | `GET /version` | Public | `slice/api/management/ManagementEndpoints.scala:69` |
| 2 | `POST /v2/authentication` | Public | `slice/api/v2/authentication/AuthenticationRestService.scala:86` |
| 3 | `GET /admin/files/{projectShortcode}/{filename}` | Object access | `slice/api/admin/FilesServerEndpoints.scala:20` |
| 3 | `GET /v2/graph/{resourceIri}` | Object access | `responders/v2/ResourcesResponderV2.scala:1055` |
| 3 | `GET /v2/resources` | Object access | `responders/v2/SearchResponderV2.scala:1258` |
| 3 | `GET /v2/resources/*` | Object access | `slice/resources/service/ReadResourcesServiceLive.scala:184` |
| 3 | `GET /v2/resources/candelete` | Object access | `responders/v2/ResourcesResponderV2.scala:414` |
| 3 | `GET /v2/resources/history/{resourceIri}` | Object access | `responders/v2/ResourcesResponderV2.scala:1162` |
| 3 | `GET /v2/resources/iiifmanifest/{resourceIri}` | Object access | `responders/v2/ResourcesResponderV2.scala:1229` |
| 3 | `GET /v2/resources/projectHistoryEvents/{projectIri}` | Object access | `responders/v2/ResourcesResponderV2.scala:1390` |
| 3 | `GET /v2/resources/resourceHistoryEvents/{resourceIri}` | Object access | `responders/v2/ResourcesResponderV2.scala:1162` |
| 3 | `GET /v2/resourcespreview/*` | Object access | `slice/resources/service/ReadResourcesServiceLive.scala:184` |
| 3 | `GET /v2/search/{searchTerm}` | Object access | `responders/v2/SearchResponderV2.scala:785` |
| 3 | `GET /v2/searchIncomingLinks/{resourceIri}` | Object access | `responders/v2/SearchResponderV2.scala:1074` |
| 3 | `GET /v2/searchIncomingRegions/{resourceIri}` | Object access | `responders/v2/SearchResponderV2.scala:1074` |
| 3 | `GET /v2/searchStillImageRepresentations/{resourceIri}` | Object access | `responders/v2/SearchResponderV2.scala:1074` |
| 3 | `GET /v2/searchbylabel/{searchTerm}` | Object access | `responders/v2/SearchResponderV2.scala:1451` |
| 3 | `GET /v2/searchextended/{param1}` | Object access | `responders/v2/SearchResponderV2.scala:1074` |
| 3 | `GET /v2/tei/{resourceIri}` | Object access | `responders/v2/ResourcesResponderV2.scala:719` |
| 3 | `GET /v2/values/{resourceIri}/{valueUuid}` | Object access | `slice/resources/service/ReadResourcesServiceLive.scala:184` |
| 3 | `POST /v2/resources` | Object access | `responders/v2/resources/CreateResourceV2Handler.scala:82` |
| 3 | `POST /v2/resources/batch` | Object access | `slice/resources/service/ReadResourcesServiceLive.scala:184` |
| 3 | `POST /v2/resources/delete` | Object access | `responders/v2/ResourcesResponderV2.scala:414` |
| 3 | `POST /v2/searchextended` | Object access | `responders/v2/SearchResponderV2.scala:1074` |
| 3 | `POST /v2/values` | Object access | `responders/v2/ValuesResponderV2.scala:95` |
| 3 | `POST /v2/values/delete` | Object access | `responders/v2/ValuesResponderV2.scala:1187` |
| 3 | `PUT /v2/resources/authorship` | Object access | `responders/v2/ResourcesResponderV2.scala:242` |
| 3 | `PUT /v2/values` | Object access | `responders/v2/ValuesResponderV2.scala:559` |
| 3 | `PUT /v2/values/order` | Object access | `slice/api/v2/values/ValuesRestService.scala:121` |
| 4 | `DELETE /admin/projects/iri/{projectIri}/RestrictedViewSettings` | ProjectAdmin | `slice/api/admin/service/ProjectRestService.scala:277` |
| 4 | `GET /admin/projects/iri/{projectIri}/AllData` | ProjectAdmin | `slice/api/admin/service/ProjectRestService.scala:169` |
| 4 | `GET /admin/projects/iri/{projectIri}/admin-members` | ProjectAdmin | `slice/api/admin/service/ProjectRestService.scala:184` |
| 4 | `GET /admin/projects/iri/{projectIri}/members` | ProjectAdmin | `slice/api/admin/service/ProjectRestService.scala:175` |
| 4 | `GET /admin/projects/iri/{projectIri}/view-restrictions/classes` | ProjectAdmin | `slice/api/admin/service/ViewRestrictionsRestService.scala:40` |
| 4 | `GET /admin/projects/iri/{projectIri}/view-restrictions/items` | ProjectAdmin | `slice/api/admin/service/ViewRestrictionsRestService.scala:66` |
| 4 | `GET /admin/projects/iri/{projectIri}/view-restrictions/properties` | ProjectAdmin | `slice/api/admin/service/ViewRestrictionsByPropertyRestService.scala:40` |
| 4 | `GET /admin/projects/iri/{projectIri}/view-restrictions/property-items` | ProjectAdmin | `slice/api/admin/service/ViewRestrictionsByPropertyRestService.scala:64` |
| 4 | `GET /admin/projects/iri/{projectIri}/view-restrictions/property-values` | ProjectAdmin | `slice/api/admin/service/ViewRestrictionsByPropertyRestService.scala:51` |
| 4 | `GET /admin/projects/iri/{projectIri}/view-restrictions/values` | ProjectAdmin | `slice/api/admin/service/ViewRestrictionsRestService.scala:51` |
| 4 | `POST /admin/projects/iri/{projectIri}/RestrictedViewSettings` | ProjectAdmin | `slice/api/admin/service/ProjectRestService.scala:260` |
| 4 | `PUT /admin/projects/iri/{projectIri}` | ProjectAdmin | `slice/api/admin/service/ProjectRestService.scala:151` |
| 5 | `DELETE /admin/users/iri/{userIri}/project-admin-memberships/{projectIri}` | ProjectAdmin | `slice/api/admin/service/UserRestService.scala:256` |
| 5 | `DELETE /admin/users/iri/{userIri}/project-memberships/{projectIri}` | ProjectAdmin | `slice/api/admin/service/UserRestService.scala:243` |
| 5 | `POST /admin/users/iri/{userIri}/project-admin-memberships/{projectIri}` | ProjectAdmin | `slice/api/admin/service/UserRestService.scala:230` |
| 5 | `POST /admin/users/iri/{userIri}/project-memberships/{projectIri}` | ProjectAdmin | `slice/api/admin/service/UserRestService.scala:193` |
| 6 | `GET /admin/permissions/ap/{projectIri}` | ProjectAdmin | `slice/api/admin/service/PermissionRestService.scala:72` |
| 6 | `GET /admin/permissions/ap/{projectIri}/{groupIri}` | ProjectAdmin | `slice/api/admin/service/PermissionRestService.scala:175` |
| 6 | `GET /admin/permissions/doap/{projectIri}` | ProjectAdmin | `slice/api/admin/service/PermissionRestService.scala:165` |
| 6 | `GET /admin/permissions/{projectIri}` | ProjectAdmin | `slice/api/admin/service/PermissionRestService.scala:79` |
| 7 | `POST /admin/groups` | ProjectAdmin | `slice/api/admin/service/GroupRestService.scala:62` |
