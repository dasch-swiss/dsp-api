---
title: "refactor: add the grant and factory machinery for the remaining project-scoped endpoints (batch 2)"
type: refactor
date: 2026-11-03
author: "Raitis Veinbahs"
status: draft
repositories: []
linear: DEV-7725
---

# refactor: add the grant and factory machinery for the remaining project-scoped endpoints (batch 2)

## Overview

Batch 2 of the migration in `01-refactor-authorization-policy-migration-plan.md`. It migrates 63 endpoints. Each needs a piece of new machinery first:
ontology request parsing without a user, grants by shortcode, shortname and group, a ProjectMember grant, a public
factory that reads the optional user, policies for the FAIR and SPARQL routes, v3 policy factories, and a grant in
the v2 ontology responder. Phase numbers run through all three batch plans.

## Problem Statement / Motivation

The project-scoped endpoints here find their project through a shortcode, a shortname, a group, an ontology
or a v3 path. The ontology writes check the project deep in the responder with a re-implemented rule
(`OntologyCacheHelpers`). The remaining endpoints are built outside the standard bases (FAIR, SPARQL passthrough,
v3), so no existing factory can declare their policy.

## Proposed Solution

- Phase 8 splits `OntologyV2RequestParser` so requests are parsed without a user and completed with
  `grant.user`, then migrates ontology creation and deletion.
- Phases 9-11 add `authorizeProjectAdminByShortcode`/`ByShortname`, `authorizeProjectAdminOfGroup`, and the
  ProjectMember factory and grant.
- Phases 12-13 add `publicPolicyEndpointWithUser`, a `publicPolicyEndpoint` overload for a caller-supplied error
  output (FAIR), and a policy on `bearerSystemAdminEndpoint`.
- Phases 14-15 build the v3 factories in `slice.common.api` and migrate v3.
- Phases 16-17 move the v2 ontology writes to a grant taken by the responder and asserted inside the ontology lock.

## Technical Considerations

The shared rules live in the main plan (`01-refactor-authorization-policy-migration-plan.md`): Proposed Solution (the six steps per phase, the code shape
and the matrix rule) and Technical Considerations (order of answers, anonymous 401, unknown project, handlers
without a `User`, kept 400s and 404s, domain error channels, `SystemUser`, package privacy, decision-6 tests, the
ontology lock). Every phase applies them; this plan repeats only what is specific to the batch.

Batch-specific points:

- Every new PDP method and factory gets unit tests in `AuthorizationRestServiceSpec` or `BaseEndpointsSpec`, and the
  compile-negative test keeps passing. No public way to mint a `ProjectCandidate` is added.
- The ontology project is resolved from the cache. An unknown ontology still answers 404, and that check comes
  before any 403.
- `checkOntologyAndEntityIrisForUpdate` keeps its IRI validation. Only its permission part is replaced. The old
  permission check is deleted in batch 3 (Phase 22), because the can-do endpoints still use it.

## Implementation Phases

Verification commands are those in `CLAUDE.md`: `just test-unit`, `just test-e2e`, `just check`, and filtered
runs such as `bazel test //modules/test-e2e:test --test_filter='.*EndpointPolicyE2ESpec.*'`. Every phase that
removes baseline lines runs `EndpointPolicyE2ESpec`, and every phase that adds matrix rows runs
`AuthorizationMatrixE2ESpec`.

### Batch 2: new machinery

#### Phase 8: Ontology request parsing without a user, and ontology creation and deletion

Endpoints (2): `POST /v2/ontologies` (`projectFrom` = "body attachedToProject") and
`DELETE /v2/ontologies/{ontologyIri}` (`projectFrom` = "path ontology's project"). The parser change is the
machinery Phases 16 and 17 reuse.

- [x] `OntologyV2RequestParser`: each parse method returns the request without the requesting user, and the RestService completes it with `grant.user` after the grant; the parser tests are adjusted
- [x] `OntologiesEndpoints`: both routes use `projectAdminPolicyEndpoint`
- [x] `OntologiesRestService`: both methods take a `ProjectCandidate` and obtain the grant in place of `ensureSystemAdminOrProjectAdminById`
- [x] `OntologyResponderV2.createOntology` takes the grant and creates in `grant.project`; any responder-level re-check is removed
- [x] `OntologyResponderV2.deleteOntology` takes the grant and asserts, inside the ontology lock, that the ontology belongs to `grant.project` (403 otherwise); an unknown ontology still answers 404 before any 403
- [x] Responder test: deleting with a grant for another project answers 403 and deletes nothing
- [x] Matrix rows for both endpoints (fresh ontology per cell)
- [x] Baseline: remove the 2 lines and lower `baselineSize`
- [x] Run `bazel test //modules/test-e2e:test --test_filter='.*(EndpointPolicy|AuthorizationMatrix|Ontolog).*'`; it passes
- [x] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

#### Phase 9: Grants by shortcode and shortname

Endpoints (11): Appendix A, phase 9.

- [x] `AuthorizationRestService.authorizeProjectAdminByShortcode(candidate, shortcode)` and `...ByShortname(candidate, shortname)`, built on the existing `ensure...By{Shortcode,Shortname}`
- [x] `AuthorizationRestServiceSpec`: SystemAdmin, ProjectAdmin, other-project admin, member, inactive user and unknown project, for both methods
- [x] `ProjectsEndpoints` and `ProjectRestService`: the 6 shortcode and shortname routes in phase 9 use `projectAdminPolicyEndpoint` and the new grants
- [x] `ProjectsLegalInfoEndpoints` and `ProjectsLegalInfoRestService`: `POST copyright-holders`, `PUT licenses/{licenseIri}/enable` and `/disable`, and `PUT legal-info/resource` use `projectAdminPolicyEndpoint` and the shortcode grant
- [x] `MetadataEndpoints` and `MetadataRestService`: `GET /v2/metadata/projects/{projectShortcode}/resources` uses `projectAdminPolicyEndpoint` and the shortcode grant
- [x] Matrix rows for the 11 endpoints
- [x] Baseline: remove the 11 lines and lower `baselineSize`
- [x] Run `just test-unit`; it passes
- [x] Run `bazel test //modules/test-e2e:test --test_filter='.*(EndpointPolicy|AuthorizationMatrix|Project|LegalInfo|Metadata).*'`; it passes
- [x] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

#### Phase 10: Group-scoped grant

Endpoints (6): Appendix A, phase 10. `projectFrom` = "path groupIri's project".

- [x] `AuthorizationRestService.authorizeProjectAdminOfGroup(candidate, groupIri): IO[ForbiddenException, (KnoraGroup, ProjectAdminGrant)]`; a built-in group (no project) answers 403, as today
- [x] `AuthorizationRestServiceSpec` cases for the new method, including a built-in group
- [x] `GroupsEndpoints` and `GroupRestService`: `PUT /{groupIri}`, `PUT /{groupIri}/status`, `DELETE /{groupIri}` and `GET /{groupIri}/members` use `projectAdminPolicyEndpoint` and the group grant
- [x] `UsersEndpoints` and `UserRestService`: `POST`/`DELETE group-memberships/{groupIri}` use `projectAdminPolicyEndpoint` and the group grant; `ensureNotABuiltInUser` stays before the grant, and the `Group` overloads with the `IsGroupMember`/`NotGroupMember` 400s stay in use
- [x] Matrix rows for the 6 endpoints (fresh group per cell)
- [x] Baseline: remove the 6 lines and lower `baselineSize`
- [x] Run `bazel test //modules/test-e2e:test --test_filter='.*(EndpointPolicy|AuthorizationMatrix|Group|User).*'`; it passes
- [x] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

#### Phase 11: ProjectMember policy

Endpoints (2): `GET legal-info/authorships` and `GET legal-info/copyright-holders`. `projectFrom` = "path shortcode".

- [x] `ProjectMemberGrant` in `ProjectAdminGrant.scala` (constructor `private[api]`), and `AuthorizationRestService.authorizeProjectMemberByShortcode(candidate, shortcode)`; `ProjectCandidate` is reused, since it means "authenticated, not yet authorized for a project"
- [x] `BaseEndpoints.projectMemberPolicyEndpoint(projectFrom)`, declaring `Policy.ProjectMember` and handing over a `ProjectCandidate`
- [x] `BaseEndpointsSpec` and the compile-negative test cover the new factory and grant
- [x] `ProjectsLegalInfoEndpoints` and `ProjectsLegalInfoRestService`: both reads use the factory and the member grant
- [x] The ProjectMember sentence in ADR-0013 decision 4 records that the candidate type is shared
- [x] Matrix rows for both endpoints (members allowed)
- [x] Baseline: remove the 2 lines and lower `baselineSize`
- [x] Run `just test-unit`; it passes
- [x] Run `bazel test //modules/test-e2e:test --test_filter='.*(EndpointPolicy|AuthorizationMatrix|LegalInfo).*'`; it passes
- [x] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

#### Phase 12: Public endpoints that read the optional user

Endpoints (6): Appendix A, phase 12. These are public, but they read the caller for the preferred language.

- [x] `BaseEndpoints.publicPolicyEndpointWithUser(reason)`: declares `Policy.Public`, authenticates optionally (bad credentials 401, none = anonymous), and hands over a `User`
- [x] `BaseEndpointsSpec` covers the factory
- [x] `OntologiesEndpoints`: `allentities`, `classes/{classIri}` and `properties/{propertyIri}` reads use it, reason "ontologies are public"
- [x] `OntologiesEndpoints`: `GET /ontology/*` uses it, reason "ontology IRI dereferencing"
- [x] `ListsEndpointsV2`: `GET /v2/lists/{listIri}` and `GET /v2/node/{listIri}` use it, reason "lists are public"
- [x] Baseline: remove the 6 lines and lower `baselineSize`
- [x] Run `just test-unit`; it passes
- [x] Run `bazel test //modules/test-e2e:test --test_filter='.*(EndpointPolicy|Ontolog|Lists).*'`; it passes
- [x] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

#### Phase 13: Policies for endpoints built outside the standard bases

Endpoints (5): the four FAIR and landing-page routes, and `POST /admin/sparql/query`.

- [x] `bearerSystemAdminEndpoint` declares `Policy.SystemAdmin`; its "Declares no Policy" note is removed; `SparqlPassthroughEndpoints`' description contains the requirement sentence
- [x] `BaseEndpoints.publicPolicyEndpoint[E](errorOut, reason)`: an overload that declares `Policy.Public` on a caller-supplied error output and reads no credentials (the FAIR routes never read the caller, so their answer does not depend on it, which is `Public`, not `ObjectAccess`)
- [x] `FairEndpoints` injects `BaseEndpoints`; `landingBase` and `representationBase` build on the overload, keeping their error outputs and the `NegotiatesOwnAccept` attribute, reason "serves only resources the anonymous user may see"; the four descriptions contain the requirement sentence
- [x] `BaseEndpointsSpec` covers both changes
- [x] Matrix row for `POST /admin/sparql/query` (a read-only `SELECT`)
- [x] Baseline: remove the 5 lines and lower `baselineSize`
- [x] Run `just test-unit`; it passes
- [x] Run `bazel test //modules/test-e2e:test --test_filter='.*(EndpointPolicy|AuthorizationMatrix|Fair|Sparql).*'`; it passes
- [x] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

#### Phase 14: v3 policy factories

Endpoints (12): the 11 v3 SystemAdmin routes and `POST /v3/export/resources`.

- [x] `slice.common.api`: a bearer-only `authorizingEndpoint` generic over the error output and the error mapping, with v3 policy factories on it: `systemAdmin` (SystemAdmin in the security logic), `objectAccess(enforcedIn)`, and `projectAdmin(projectFrom)`, which hands over a `ProjectCandidate`; each sets `Policy.key`
- [x] `V3BaseEndpoint` exposes them with the v3 `Unauthorized`/`Forbidden` variants; the compile-negative test still passes unchanged
- [x] `V3Authorizer.authorizeProjectAdminById(candidate, projectIri): IO[Forbidden, ProjectAdminGrant]`
- [x] Unit tests for the three V3 factories, mirroring `BaseEndpointsSpec`
- [x] `V3ProjectsEndpoints`: the 10 import, export and data-import routes use `systemAdminPolicy`; the `V3Authorizer.ensureSystemAdmin` calls in `V3ProjectsRestService` are removed
- [x] `ExportEndpoints`: `POST /v3/export/resources/oai` uses `systemAdminPolicy` (anonymous now answers 401, was 403; its v3 docs say so); `POST /v3/export/resources` uses `objectAccessPolicy`
- [x] The v3 import, export and data-import runs: `exempt` entries, plus the 403/401 e2e tests
- [x] Baseline: remove the 12 lines and lower `baselineSize`
- [x] Run `just test-unit`; it passes
- [x] Run `bazel test //modules/test-e2e:test --test_filter='.*(EndpointPolicy|V3|Export|Import).*'`; it passes
- [x] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

#### Phase 15: v3 ontology mappings through a grant

Endpoints (4): `PUT`/`DELETE` class and property mappings. `projectFrom` = "path ontology's project".

- [x] `OntologyMappingEndpoints`: the four routes use `projectAdminPolicy`
- [x] `OntologyMappingRestService`: `authorizeByProject` becomes "a system ontology answers 403 'Cannot modify a system ontology.' to everyone, root included (unchanged); otherwise `V3Authorizer.authorizeProjectAdminById` on the ontology's project", and the writes take the grant; the existing mappings-count-before-auth order is kept
- [x] The class or property still must belong to the path ontology (unchanged), and the ontology must belong to `grant.project` (test with a grant for another project)
- [x] Matrix rows for the 4 endpoints
- [x] Baseline: remove the 4 lines and lower `baselineSize`
- [x] Run `bazel test //modules/test-e2e:test --test_filter='.*(EndpointPolicy|AuthorizationMatrix|OntologyMapping).*'`; it passes
- [x] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

#### Phase 16: v2 ontology metadata and class writes through a grant

Endpoints (6): `PUT /v2/ontologies/metadata`, `DELETE /v2/ontologies/comment/{ontologyIri}`, `POST`/`PUT /v2/ontologies/classes`,
`DELETE classes/{resourceClassIri}` and `DELETE classes/comment/{resourceClassIri}`. `projectFrom` = "ontology's project".

- [x] `OntologiesRestService`: a private `authorizeForOntology(candidate, ontologyIri)` that resolves the ontology's project from the cache (404 for an unknown ontology, as today) and obtains the grant; the ontology IRI comes from the path, or from the body parsed without a user (Phase 8)
- [x] `OntologiesEndpoints`: the 6 routes use `projectAdminPolicyEndpoint` (anonymous answers 401, was 403)
- [x] `OntologyCacheHelpers.checkOntologyAndEntityIrisForUpdate` is split: the IRI validation stays, and a variant without the permission part (no `requestingUser`) is what grant-taking methods call
- [x] `OntologyResponderV2`: the 6 methods take a `ProjectAdminGrant` in place of the requesting user's authorization, and assert inside the ontology lock that the ontology belongs to `grant.project`; their direct `checkPermissionsForOntologyUpdate` calls are removed
- [x] Responder test per method family (metadata, class create/change, class delete): a grant for another project answers 403 and writes nothing
- [x] `docs/03-endpoints/api-v2/ontology-information.md` (or the ontology editing page) records the 401 for anonymous callers
- [x] Matrix rows for the 6 endpoints (fresh ontology per cell)
- [x] Baseline: remove the 6 lines and lower `baselineSize`
- [x] Run `bazel test //modules/test-it:test --test_filter='.*OntologyResponderV2.*'`; it passes
- [x] Run `bazel test //modules/test-e2e:test --test_filter='.*(EndpointPolicy|AuthorizationMatrix|Ontolog).*'`; it passes
- [x] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

#### Phase 17: v2 ontology property and cardinality writes through a grant

Endpoints (9): Appendix A, phase 17.

- [x] `OntologiesEndpoints`: the 9 routes use `projectAdminPolicyEndpoint`, and `OntologiesRestService` obtains the grant through `authorizeForOntology`
- [x] `OntologyResponderV2`: the property, GUI-element, GUI-order and cardinality methods take the grant, call the permission-free IRI check from Phase 16, and assert inside the lock that the ontology belongs to `grant.project`
- [x] Responder test per method family (property create/change, property delete, cardinalities): a grant for another project answers 403
- [x] Matrix rows for the 9 endpoints
- [x] Baseline: remove the 9 lines and lower `baselineSize`
- [x] Run `bazel test //modules/test-it:test --test_filter='.*OntologyResponderV2.*'`; it passes
- [x] Run `bazel test //modules/test-e2e:test --test_filter='.*(EndpointPolicy|AuthorizationMatrix|Ontolog|Cardinalit).*'`; it passes
- [x] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

## Human Actions

| Id | Action | Who | When | Why not the agent |
| --- | --- | --- | --- | --- |
| H1 | Merge batch 1's PR before this one | owner | after ship | Merge order is the owner's decision |
| H2 | Release notes list the status-code changes above; check dsp-das against them | owner | after ship | Frontend coordination |

## Acceptance Criteria

- [x] Every endpoint of phases 8-17 in Appendix A declares the phase's policy and is gone from the baseline
- [x] `baselineSize` is 37
- [x] Every project-scoped endpoint of this batch obtains its grant from the PDP, and its domain write takes the grant or `grant.project`
- [x] Every responder that checks an object against `grant.project` has a wrong-project test
- [x] Every role-based endpoint of this batch has a matrix row or an `exempt` entry with the 403/401 tests
- [x] The status-code changes below are in the PR body and in the endpoint docs
- [ ] `just check`, `just test-unit` and `just test-e2e` pass

### Status-code changes

| Endpoint | Caller | Before | After |
| --- | --- | --- | --- |
| v2 ontology writes (Phases 8, 16, 17) | anonymous | 403 | 401 |
| `POST /v3/export/resources/oai` | anonymous | 403 | 401 |

## Dependencies & Risks

- **Depends on batch 1** (the matrix `exempt` set). This branch starts from batch 1's branch, or from `main` after
  batch 1 has merged.
- **The ontology responder** is large and lock-sensitive. The belongs-to-project assertion stays inside the lock.
- **Matrix fixtures.** Ontology rows share one ontology for the denied cells and create a fresh one only for the
  allowed cells.

## Success Metrics

- Baseline: 100 lines before this batch, 37 after.

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
| 8 | `DELETE /v2/ontologies/{ontologyIri}` | ProjectAdmin | `slice/api/v2/ontologies/OntologiesRestService.scala:357` |
| 8 | `POST /v2/ontologies` | ProjectAdmin | `slice/api/v2/ontologies/OntologiesRestService.scala:346` |
| 9 | `DELETE /admin/projects/shortcode/{projectShortcode}/RestrictedViewSettings` | ProjectAdmin | `slice/api/admin/service/ProjectRestService.scala:272` |
| 9 | `GET /admin/projects/shortcode/{projectShortcode}/admin-members` | ProjectAdmin | `slice/api/admin/service/ProjectRestService.scala:186` |
| 9 | `GET /admin/projects/shortcode/{projectShortcode}/members` | ProjectAdmin | `slice/api/admin/service/ProjectRestService.scala:177` |
| 9 | `GET /admin/projects/shortname/{projectShortname}/admin-members` | ProjectAdmin | `slice/api/admin/service/ProjectRestService.scala:188` |
| 9 | `GET /admin/projects/shortname/{projectShortname}/members` | ProjectAdmin | `slice/api/admin/service/ProjectRestService.scala:179` |
| 9 | `GET /v2/metadata/projects/{projectShortcode}/resources` | ProjectAdmin | `slice/api/v2/metadata/MetadataRestService.scala:50` |
| 9 | `POST /admin/projects/shortcode/{projectShortcode}/RestrictedViewSettings` | ProjectAdmin | `slice/api/admin/service/ProjectRestService.scala:254` |
| 9 | `POST /admin/projects/shortcode/{projectShortcode}/legal-info/copyright-holders` | ProjectAdmin | `slice/api/admin/service/ProjectsLegalInfoRestService.scala:100` |
| 9 | `PUT /admin/projects/shortcode/{projectShortcode}/legal-info/licenses/{licenseIri}/disable` | ProjectAdmin | `slice/api/admin/service/ProjectsLegalInfoRestService.scala:85` |
| 9 | `PUT /admin/projects/shortcode/{projectShortcode}/legal-info/licenses/{licenseIri}/enable` | ProjectAdmin | `slice/api/admin/service/ProjectsLegalInfoRestService.scala:79` |
| 9 | `PUT /admin/projects/shortcode/{projectShortcode}/legal-info/resource` | ProjectAdmin | `slice/api/admin/service/ProjectsLegalInfoRestService.scala:119` |
| 10 | `DELETE /admin/groups/{groupIri}` | ProjectAdmin | `slice/api/admin/service/GroupRestService.scala:91` |
| 10 | `DELETE /admin/users/iri/{userIri}/group-memberships/{groupIri}` | ProjectAdmin | `slice/api/admin/service/UserRestService.scala:286` |
| 10 | `GET /admin/groups/{groupIri}/members` | ProjectAdmin | `slice/api/admin/service/GroupRestService.scala:52` |
| 10 | `POST /admin/users/iri/{userIri}/group-memberships/{groupIri}` | ProjectAdmin | `slice/api/admin/service/UserRestService.scala:271` |
| 10 | `PUT /admin/groups/{groupIri}` | ProjectAdmin | `slice/api/admin/service/GroupRestService.scala:72` |
| 10 | `PUT /admin/groups/{groupIri}/status` | ProjectAdmin | `slice/api/admin/service/GroupRestService.scala:91` |
| 11 | `GET /admin/projects/shortcode/{projectShortcode}/legal-info/authorships` | ProjectMember | `slice/api/admin/service/ProjectsLegalInfoRestService.scala:42` |
| 11 | `GET /admin/projects/shortcode/{projectShortcode}/legal-info/copyright-holders` | ProjectMember | `slice/api/admin/service/ProjectsLegalInfoRestService.scala:95` |
| 12 | `GET /ontology/*` | Public | `slice/api/v2/ontologies/OntologiesRestService.scala:67` |
| 12 | `GET /v2/lists/{listIri}` | Public | `slice/api/v2/lists/ListsV2RestService.scala:36` |
| 12 | `GET /v2/node/{listIri}` | Public | `slice/api/v2/lists/ListsV2RestService.scala:55` |
| 12 | `GET /v2/ontologies/allentities/{ontologyIri}` | Public | `slice/api/v2/ontologies/OntologiesRestService.scala:120` |
| 12 | `GET /v2/ontologies/classes/{classIri}` | Public | `slice/api/v2/ontologies/OntologiesRestService.scala:226` |
| 12 | `GET /v2/ontologies/properties/{propertyIri}` | Public | `slice/api/v2/ontologies/OntologiesRestService.scala:319` |
| 13 | `GET /fair/resources/{shortcode}/{resourceId}/metadata.datacite.json` | Object access | `slice/fair/domain/service/ResourceFactsBuilder.scala:37` |
| 13 | `GET /fair/resources/{shortcode}/{resourceId}/metadata.jsonld` | Object access | `slice/fair/domain/service/ResourceFactsBuilder.scala:37` |
| 13 | `GET /internal/landing-pages/resources/{shortcode}/{resourceId}` | Object access | `slice/fair/domain/service/ResourceFactsBuilder.scala:37` |
| 13 | `HEAD /internal/landing-pages/resources/{shortcode}/{resourceId}` | Object access | `slice/fair/domain/service/ResourceFactsBuilder.scala:37` |
| 13 | `POST /admin/sparql/query` | SystemAdmin | `slice/common/api/BaseEndpoints.scala:189` |
| 14 | `DELETE /v3/projects/{projectIri}/data-imports/{importId}` | SystemAdmin | `slice/api/v3/projects/V3ProjectsRestService.scala:286` |
| 14 | `DELETE /v3/projects/{projectIri}/exports/{exportId}` | SystemAdmin | `slice/api/v3/projects/V3ProjectsRestService.scala:101` |
| 14 | `DELETE /v3/projects/{projectIri}/imports/{importId}` | SystemAdmin | `slice/api/v3/projects/V3ProjectsRestService.scala:159` |
| 14 | `GET /v3/projects/{projectIri}/data-imports/{importId}` | SystemAdmin | `slice/api/v3/projects/V3ProjectsRestService.scala:277` |
| 14 | `GET /v3/projects/{projectIri}/exports/{exportId}` | SystemAdmin | `slice/api/v3/projects/V3ProjectsRestService.scala:92` |
| 14 | `GET /v3/projects/{projectIri}/exports/{exportId}/download` | SystemAdmin | `slice/api/v3/projects/V3ProjectsRestService.scala:119` |
| 14 | `GET /v3/projects/{projectIri}/imports/{importId}` | SystemAdmin | `slice/api/v3/projects/V3ProjectsRestService.scala:150` |
| 14 | `POST /v3/export/resources` | Object access | `slice/export/api/service/ExportService.scala:255` |
| 14 | `POST /v3/export/resources/oai` | SystemAdmin | `slice/api/v3/export/ExportRestService.scala:62` |
| 14 | `POST /v3/projects/{projectIri}/data-imports` | SystemAdmin | `slice/api/v3/projects/V3ProjectsRestService.scala:57` |
| 14 | `POST /v3/projects/{projectIri}/exports` | SystemAdmin | `slice/api/v3/projects/V3ProjectsRestService.scala:57` |
| 14 | `POST /v3/projects/{projectIri}/imports` | SystemAdmin | `slice/api/v3/projects/V3ProjectsRestService.scala:138` |
| 15 | `DELETE /v3/ontologies/{ontologyIri}/classes/{classIri}/mapping` | ProjectAdmin | `slice/api/v3/ontology/OntologyMappingRestService.scala:106` |
| 15 | `DELETE /v3/ontologies/{ontologyIri}/properties/{propertyIri}/mapping` | ProjectAdmin | `slice/api/v3/ontology/OntologyMappingRestService.scala:189` |
| 15 | `PUT /v3/ontologies/{ontologyIri}/classes/{classIri}/mapping` | ProjectAdmin | `slice/api/v3/ontology/OntologyMappingRestService.scala:71` |
| 15 | `PUT /v3/ontologies/{ontologyIri}/properties/{propertyIri}/mapping` | ProjectAdmin | `slice/api/v3/ontology/OntologyMappingRestService.scala:150` |
| 16 | `DELETE /v2/ontologies/classes/comment/{resourceClassIri}` | ProjectAdmin | `responders/v2/OntologyResponderV2.scala:1651` |
| 16 | `DELETE /v2/ontologies/classes/{resourceClassIri}` | ProjectAdmin | `responders/v2/OntologyResponderV2.scala:1010` |
| 16 | `DELETE /v2/ontologies/comment/{ontologyIri}` | ProjectAdmin | `responders/v2/OntologyResponderV2.scala:337` |
| 16 | `POST /v2/ontologies/classes` | ProjectAdmin | `responders/v2/OntologyResponderV2.scala:372` |
| 16 | `PUT /v2/ontologies/classes` | ProjectAdmin | `responders/v2/OntologyResponderV2.scala:1596` |
| 16 | `PUT /v2/ontologies/metadata` | ProjectAdmin | `responders/v2/OntologyResponderV2.scala:304` |
| 17 | `DELETE /v2/ontologies/properties/comment/{propertyIri}` | ProjectAdmin | `responders/v2/OntologyResponderV2.scala:1621` |
| 17 | `DELETE /v2/ontologies/properties/{propertyIri}` | ProjectAdmin | `responders/v2/OntologyResponderV2.scala:1099` |
| 17 | `PATCH /v2/ontologies/cardinalities` | ProjectAdmin | `responders/v2/OntologyResponderV2.scala:968` |
| 17 | `POST /v2/ontologies/cardinalities` | ProjectAdmin | `responders/v2/OntologyResponderV2.scala:614` |
| 17 | `POST /v2/ontologies/properties` | ProjectAdmin | `responders/v2/OntologyResponderV2.scala:1423` |
| 17 | `PUT /v2/ontologies/cardinalities` | ProjectAdmin | `responders/v2/OntologyResponderV2.scala:759` |
| 17 | `PUT /v2/ontologies/guiorder` | ProjectAdmin | `responders/v2/OntologyResponderV2.scala:588` |
| 17 | `PUT /v2/ontologies/properties` | ProjectAdmin | `responders/v2/OntologyResponderV2.scala:1538` |
| 17 | `PUT /v2/ontologies/properties/guielement` | ProjectAdmin | `responders/v2/OntologyResponderV2.scala:1511` |
