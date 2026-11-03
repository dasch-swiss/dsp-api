---
title: "refactor: migrate the remaining endpoints to declared authorization policies"
type: refactor
date: 2026-11-03
author: "Raitis Veinbahs"
status: draft
repositories: []
linear: DEV-7725
---

# refactor: migrate the remaining endpoints to declared authorization policies

## Overview

ADR-0013 (`docs/adr/0013-authorization-policy-enforcement.md`) and PR #4410 introduced declared endpoint
policies, the candidate-to-grant flow for project-scoped endpoints, the ratchet `EndpointPolicyE2ESpec` and the
authorization matrix. The pilot migrated the admin lists and v2 standoff slices (17 endpoints). The other 175
endpoints are still listed in `modules/test-e2e/src/test/resources/endpoints-without-policy.txt`.

This is the main plan. It holds the vision and the rules every batch shares; the work itself runs as three batch
plans, one PR each:

| Batch | Plan | Phases | Endpoints | What it needs | Baseline after |
| --- | --- | --- | --- | --- | --- |
| 1 | `02-refactor-authorization-policy-batch-1-plan.md` | 1-7 | 75 | Existing machinery only: the five `BaseEndpoints` policy factories and `authorizeProjectAdminById` | 100 |
| 2 | `03-refactor-authorization-policy-batch-2-plan.md` | 8-17 | 63 | New machinery: ontology parsing without a user, more ways to obtain a grant, a ProjectMember grant, factories for non-standard surfaces and v3, a grant in the ontology responder | 37 |
| 3 | `04-refactor-authorization-policy-batch-3-plan.md` | 18-27 | 37 | Owner decisions first, because the endpoint's behaviour is a gap or fits no policy case; then the close-out | 5, or 0 |

Each batch plan's Appendix A lists its endpoints, so every one of the 175 sits in exactly one phase.

## Problem Statement / Motivation

An endpoint in the baseline authorizes through a hand-called check, and nothing notices when the check is missing,
swallowed or given the wrong project (ADR-0013, Context). The classification behind this plan found:

- 64 endpoints check SystemAdmin-or-ProjectAdmin of one project, through about six different mechanisms:
  `ensureSystemAdminOrProjectAdminById`, `...ByShortcode`, `...ByShortname`, `...OfGroup`, `V3Authorizer` and
  `OntologyCacheHelpers.checkPermissionsForOntologyUpdate` (the last one re-implements the rule in the responder).
  Two more check ProjectMember.
- 28 SystemAdmin, 36 public and 33 object-access endpoints have no project check, but they also declare nothing, so
  a reader cannot tell a deliberate public route from a forgotten check.
- About ten endpoints show a gap or an inconsistency (batch 3's Decisions Q1-Q9) that a policy label would otherwise make
  look intended.

## Proposed Solution

Every endpoint is built with a policy-declaring factory. Project-scoped endpoints receive a `ProjectCandidate` and
obtain a grant from the decision point (`AuthorizationRestService`) before they act. The target is taken from the
grant (ADR-0013 decision 6). Each phase does the same things:

1. Switch the endpoint definition to the factory and put `policy.requirement` into its description.
2. Change the RestService method to take what the factory hands over (a `User`, a `ProjectCandidate`, or nothing),
   and delete the hand-called check that the factory or grant replaces.
3. For project-scoped writes, pass `grant.project`, or the grant itself, into the domain. Where the target is an
   object inside the project (a group, an ontology, a resource), the domain asserts that the object belongs to
   `grant.project` and answers 403 otherwise.
4. Remove the endpoint's line from the baseline and lower `baselineSize` in `EndpointPolicyE2ESpec` in the same
   commit.
5. Add a matrix row per role-based endpoint (SystemAdmin, ProjectAdmin, ProjectMember) to
   `AuthorizationMatrixE2ESpec` (rule below).
6. Record any status-code change in the endpoint docs under `docs/03-endpoints/` and in the PR's status-change
   table.

The code shape follows the pilot. Phase 4's `GET /admin/projects/iri/{projectIri}/members` becomes:

```scala
// ProjectsEndpoints
val getAdminProjectsByProjectIriMembers = baseEndpoints
  .projectAdminPolicyEndpoint(pathProjectIri)
  .get
  .in(projectsByIri / "members")
  .out(jsonBody[ProjectMembersGetResponseADM])
  .description(s"Returns all members of a project identified by its IRI. ${Policy.ProjectAdmin(pathProjectIri).requirement}")

// ProjectRestService
def getProjectMembersById(candidate: ProjectCandidate)(id: ProjectIri): Task[ProjectMembersGetResponseADM] =
  for {
    grant    <- auth.authorizeProjectAdminById(candidate, id)
    response <- projectService.findProjectMembers(grant.project)
  } yield response
```

**Matrix rule.** `AuthorizationMatrixE2ESpec` fails for any endpoint whose policy `allowedBy` maps
(`SystemAdmin`, `ProjectAdmin`, `ProjectMember`, `Authenticated`) and that has no row. So every such endpoint gets a
row. Its `prepare` creates a fresh target through the API, as the list rows do. The denied cells may share one
target per row, which keeps the ontology rows from taking the global ontology lock about 100 times.

Some endpoints cannot be given a repeatable, side-effect-free valid request: `POST /start-compaction`, the
`/admin/maintenance/*` actions, and the v3 import/export runs. Phase 1 adds an explicit `exempt` set to the spec, of
`key -> reason`, which the "every role-based endpoint has a row" assertion subtracts. An exempt endpoint instead
gets an e2e test that a non-admin answers 403 and an anonymous caller 401.

The matrix has no inactive principal. Authentication rejects inactive users, and the PDP unit tests (Phases 9-11)
cover the active check.

## Technical Considerations

- **Order of answers changes where the check moves into the security logic.** `systemAdminPolicyEndpoint` decides
  before the body is decoded, so a non-admin with a malformed body now gets 403 instead of 400. Project-scoped
  endpoints decide in the handler after decoding, as before. Security logic bounds decoding, not buffering
  (dasch-specs `learnings/design-decisions/security-logic-authenticates-body-buffering-precedes-it.md`). No commit
  message or comment may claim otherwise.
- **Anonymous callers.** The `authenticated`, `systemAdmin` and `projectAdmin` factories authenticate with
  `authenticateOrFail`. An anonymous call to an endpoint that used `withUserEndpoint` therefore answers 401 instead
  of 403. This affects the v2 ontology writes, `POST /v3/export/resources/oai` and the PA endpoints that were on
  `withUserEndpoint`.
- **Unknown project.** `authorizeProjectAdminById` answers 403 "Project with id ... not found." Endpoints that
  answered 404 first (`PUT /admin/projects/iri/{projectIri}`, `GET /admin/permissions/ap/{projectIri}` and its
  siblings) change from 404 to 403, as `POST /admin/lists` did in the pilot.
- **Handlers no longer hold a `User`.** A `ProjectCandidate` does not expose its user, so any parser that takes a
  `User` cannot be called before the grant exists. The v2 ontology parsers (`OntologyV2RequestParser`) and
  `deleteOrEraseResourceRequestV2` take one. Phase 8 splits ontology parsing from attaching the user, and Phase 23
  does the same for resource erase.
- **Keep existing 400s and 404s where they come first.** `ensureNotABuiltInUser` (400) runs before the role
  check in `UserRestService`, and stays first wherever the check stays in the handler (Phases 5, 10). An unknown
  ontology answers 404, and `DELETE /v2/ontologies/{ontologyIri}` answers 404 before 403; `authorizeForOntology`
  keeps both. A v3 mapping write on a system ontology answers 403 "Cannot modify a system ontology." to everyone,
  root included; Phase 15 keeps it.
- **Domain error channels.** `KnoraUserService`'s `KnoraProject`/`KnoraGroup` overloads are `UIO` and skip the
  duplicate-membership check that the `Project`/`Group` overloads fail with (`IsProjectMember`, `IsGroupMember`, 400
  in `UserRestService`). Passing `grant.project` must not switch to them: a duplicate add stays 400.
- **`SystemUser`.** The grant admits the system user (`isSystemAdminOrUser`). The ontology and resource-erase
  checks did not, but the system user cannot arrive over HTTP, so nothing changes externally.
- **Package privacy.** `ProjectCandidate` and the grants are constructible only in `slice.common.api`, and the
  compile-negative test pins that. The v3 factories that hand over a candidate are therefore built in
  `slice.common.api`, on an `authorizingEndpoint` that is generic over the error output (Phase 14).
  `V3BaseEndpoint` delegates to them. No public "mint a candidate" function is added.
- **Decision 6 checks are responder tests.** Each responder that asserts an object belongs to `grant.project` gets
  a test with a grant for one project and an object of another (ADR-0013 decision 8).
- **Ontology writes run inside the ontology lock.** The belongs-to-project assertion goes inside the lock, next to
  the existing `lastModificationDate` check, as the list node check does (`ListsResponder`).
- **Conventions.** RestService methods use a second parameter list for the principal (`CONVENTIONS.md`). Typed IRIs
  are converted at the API boundary (`docs/development/dsp-api-iri-handling.md`). SmartIri-backed ontology IRIs
  stay `IriDto` in v3 (`docs/development/dsp-api-v3-iri-handling.md`).

## Implementation Approach

The batches run in order. Each branch starts from the previous batch's branch, or from `main` after the previous
PR has merged. Phase numbers run through all three plans.

A batch plan carries only what is specific to it: its phases, decisions, status-code changes, dependencies, Human
Actions and endpoint appendix. For the routine, the matrix rule and the Technical Considerations it points here. A
shared rule found wrong is changed here first, then in the batch plans not yet executed.

- [ ] Batch 1 executed and merged (`02-refactor-authorization-policy-batch-1-plan.md`)
- [ ] Batch 2 executed and merged (`03-refactor-authorization-policy-batch-2-plan.md`)
- [ ] Batch 3 executed and merged (`04-refactor-authorization-policy-batch-3-plan.md`)

## Acceptance Criteria

- [ ] Every endpoint of the three batch appendices declares a policy, except those batch 3's Q9 leaves for DEV-7718
- [ ] No hand-called project-admin check remains: every project-scoped endpoint obtains a grant from the PDP
- [ ] The three batch plans' acceptance criteria are met

## Dependencies & Risks

- **Front-end breakage.** dsp-das may rely on 403 for anonymous ontology writes, 404 for unknown projects, or public
  membership reads. Mitigation: each batch plan's status-change table and release-notes Human Action.
- **Matrix fixtures.** Fresh targets per cell make the matrix spec slower. Mitigation: the denied cells of a row
  share one target.
- **Ontology responder.** The responder is large and lock-sensitive. Moving the check before the lock must not open
  a window: the belongs-to-project assertion stays inside the lock.
- **Drift between the plans.** Shared rules live only here. A batch plan that finds a rule wrong changes it here
  and in the later batch plans, not only in itself.

## Success Metrics

- Baseline: 175 lines at start. After batch 1: 100. After batch 2: 37. After batch 3: 5, or 0 (batch 3's Q9).
- The number of hand-called project-admin mechanisms goes from six (listed in Problem Statement) to one, the grant.

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
- Batch plans: `02-...-batch-1-plan.md`, `03-...-batch-2-plan.md`, `04-...-batch-3-plan.md`
