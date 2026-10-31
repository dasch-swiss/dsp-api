---
title: "fix: close the triaged authorization gaps and make every endpoint declare its authorization policy"
type: fix
date: 2026-10-30
author: "Ivan Subotic"
status: draft
repositories: []
linear:
---

# fix: close the triaged authorization gaps and make every endpoint declare its authorization policy

## Overview

Three security issues in triage (DEV-7712, DEV-7713, DEV-7714) are real. Two more fail-open gaps of the
same kind turned up during research. Three filed follow-ups were verified and pulled in: DEV-7723 (fail-open),
DEV-7721 and DEV-7722 (fail-closed). This plan fixes all eight and adds the structure that makes this bug class
visible:

- every endpoint declares an authorization **policy**;
- a ratchet test fails on any new endpoint without one;
- a lint step fails on the two code shapes behind the bugs;
- a pilot migrates the admin lists and v2 standoff slices to the new model;
- an authorization matrix test checks the pilot endpoints per principal.

One plan, one PR, as the owner decided. The eight fixes come first as self-contained `fix:` commits.

## Problem Statement / Motivation

**The bugs.** Every one is a hand-written check that is missing, swallowed, or given the wrong identity:

| Id | Where | Defect | Effect |
| --- | --- | --- | --- |
| DEV-7713 | `AdminListRestService.scala:116`, `ListsResponder.scala:1048-1059` | `@unused user`; no role check | Any logged-in user deletes list-node comments in any project, irreversibly |
| DEV-7714 | `StandoffRestService.scala:36`, `StandoffResponderV2.scala:54-69` | Only `ensureUserIsNotAnonymous` | Any logged-in user writes a mapping into any project's data graph (no overwrite: `StandoffResponderV2.scala:277-282`) |
| DEV-7712 | `ResourcesResponderV2.scala:1160-1165`, `:1467-1481` | Preview read as `SystemUser`; `.resources.head` on a filtered result | Anonymous callers get dates and author IRIs of hidden resources; project history answers 500 when one resource is hidden |
| new | `CreateResourceV2Handler.scala:102-105` | `ensureNotAnonymousUser` ends in `.ignore` | The anonymous check never fires; only the later create-permission check stands |
| new | `modules/ingest/.../ReportEndpointsHandler.scala:21` | `serverLogic(_ => _ => ...)` drops the principal | Any valid JWT starts a forked all-project report, although the endpoint is documented as admin-only |

**The structure behind them.** These are verified in code, not just suspected:

- `BaseEndpoints` (`slice/common/api/BaseEndpoints.scala`) only *authenticates*. `securedEndpoint`, `withUserEndpoint`
  and `publicEndpoint` hand a `User` to the server logic. Whether that user is authorized is up to each handler.
  The one exception is `bearerSystemAdminEndpoint`, which authorizes in the security logic and is used by one route.
- Authorization is a hand-called `AuthorizationRestService.ensure*` in about 86 RestService methods. About 37 more
  are checked in responders through other mechanisms: `ListsResponder.ensureUserIsAdminOrProjectOwner`,
  `OntologyCacheHelpers.checkPermissionsForOntologyUpdate`, `RestCardinalityService`,
  `ResourceUtilV2.check*Permission`, and inline checks. That is about 13 overlapping mechanisms. The ontology one
  re-implements the project-admin rule without the active-user check.
- Nothing ties an endpoint to a required role. The `.description("Requires ...")` strings are prose, and for
  DEV-7713 and DEV-7714 they describe the bug ("Requires authentication.").
- `SystemUser` passes every role check, in four independent places (`AuthorizationRestService.isSystemAdminOrUser`,
  `PermissionUtilADM:95`, `ScopeResolver`, `UserRestService:217`). So reading "as SystemUser" silently bypasses
  object permissions (DEV-7712). Non-test `SystemUser` references sit in 19 main-source files.
- `-Wunused:all -Werror` would flag an ignored `user` parameter, but `@unused` silences it (DEV-7713).
- No test checks authorization across endpoints. `test-e2e/.../EndpointsE2ESpec.scala` enumerates every server
  endpoint but only asserts that it has an HTTP method. Negative authorization tests are per endpoint and uneven:
  the delete-comments test only uses `rootUser`.

## Proposed Solution

Use the XACML / NIST ABAC vocabulary, mapped onto the monolith in-process:

- **PDP (decision point):** `AuthorizationRestService`, the existing single home of the role rules. Nothing new
  re-implements them.
- **PEP (enforcement point):**
    - Function-level policies that depend only on the user (`SystemAdmin`, `Authenticated`) are enforced by the
      endpoint base in the tapir security logic, before the body is decoded. This generalises
      `bearerSystemAdminEndpoint`.
    - Project-scoped policies need the decoded input to find the project, so they cannot be decided in the security
      logic. The endpoint factory for these policies therefore does not hand the handler a `User`. It hands over a
      `ProjectCandidate`, which exposes nothing a handler can act on. The only way to turn it into something usable is
      to ask the PDP: `authorizeProjectAdmin(candidate, projectIri)` returns a **grant**, and the domain write methods
      accept only that grant. A handler that skips the check has no user to pass on and no grant to call the domain
      with, so the omission does not compile.
    - Object-level permissions (view, modify, delete, change-rights on resources and values) stay where the owning
      domain loads the object, as the domain modularization reasoning (dasch-specs, 2026-07-30) decided.
- **Declaration:** every endpoint carries a `Policy` value as a tapir attribute (precedent: `NegotiatesOwnAccept.key`).
  The attribute is the machine-readable statement of what the endpoint requires. Tests read it.
- **Default-deny by ratchet:** a test fails when an endpoint has no `Policy` and is not in a checked-in baseline,
  and when the baseline lists an endpoint that has since gained one. The baseline can only shrink.
- **Lint:** `just check` fails on `@unused` on a `User` parameter, and on `SystemUser` in a main-source file outside
  an allow-list.

No external policy engine (Cedar, OPA, OpenFGA). Not one of them stops a forgotten call, which is the bug class
here. The rule set is small: three roles plus four object permissions. And object permissions live in RDF, so an
engine would need a sync layer that is itself an authorization risk.

### Policy model (sketch)

```scala
// slice/common/api/Policy.scala
enum Policy {

  /** No credentials needed; the reason is reviewed because every public route is an exposure decision. */
  case Public(reason: String)

  /** Any active, non-anonymous user. Reviewed because "logged in" alone was the DEV-7713/DEV-7714 bug class. */
  case Authenticated(reason: String)

  case SystemAdmin

  /** SystemAdmin, or ProjectAdmin of the project named by `projectFrom` (e.g. "path projectIri", "list node"). */
  case ProjectAdmin(projectFrom: String)

  /** SystemAdmin, ProjectAdmin or member of the project named by `projectFrom`. */
  case ProjectMember(projectFrom: String)

  /** Per-object permissions, checked where the owning domain loads the object. */
  case ObjectAccess(enforcedIn: String)
}

object Policy {
  val key: AttributeKey[Policy] = AttributeKey[Policy]
}
```

```scala
// slice/common/api/ProjectAdminGrant.scala
/** An authenticated caller not yet authorized for any project; handlers cannot read the user out of it. */
final class ProjectCandidate private[api] (private[api] val user: User)

/** Proof that `user` passed the ProjectAdmin rule for `project`; only AuthorizationRestService issues it. */
final case class ProjectAdminGrant private[api] (user: User, project: KnoraProject)
```

```scala
// AdminListRestService, after migration: the user is unreachable until the PDP has decided.
def deleteListNodeComments(candidate: ProjectCandidate)(iri: ListIri): Task[ListNodeCommentsDeleteResponseADM] =
  for {
    project <- listsResponder.projectOfNode(iri)
    grant   <- auth.authorizeProjectAdmin(candidate, project)
    result  <- listsResponder.deleteListNodeCommentsADM(grant, iri)
  } yield result
```

`ProjectAdmin` and `ProjectMember` keep the existing semantics of `isSystemOrProjectAdmin` and `isProjectMember`.
SystemAdmin is included. The active-user check applies to every non-`Public` policy, as in `checkActiveUser`.

## Alternative Approaches Considered

- **External policy engine** (Cedar via `cedar-java`, OPA, Oso, OpenFGA/SpiceDB): rejected, see above. Cedar would
  also add a native library to the Bazel multi-arch images. Revisit only if policies must become data that
  non-developers edit.
- **Enforce every policy in the security logic:** impossible for project-scoped policies. The project often comes
  from the body (DEV-7714) or from a list node, and the body is decoded after the security logic.
- **Proof types on every service method now:** strongest guarantee, but it touches about 120 signatures. The pilot
  proves the shape on two slices first.
- **Two plans, fixes shipped first:** recommended in planning. The owner chose one plan. See Risks.

## Technical Considerations

- **What the types guarantee, and what they do not (stated in the ADR):**
    - On a migrated endpoint, *forgetting* the project check does not compile. The security output is a
      `ProjectCandidate`, not a `User`, and the domain write accepts only a `ProjectAdminGrant`.
    - *Checking the wrong project* still compiles: for example, the grant is resolved from a body field while the
      write targets a node in another project. Two things close that. First, the domain write takes the target from
      the grant (`grant.project`) wherever the target is the project itself. Second, where the target is an object
      inside the project (a list node), the responder asserts that the object belongs to `grant.project` and fails with
      403 otherwise. The matrix test, with hand-written expectations as an independent oracle, covers the rest.
    - Unmigrated endpoints keep the hand-called `ensure*`. The ratchet tracks them until they move.
    - The remaining gaps are bypass routes into the domain: `SystemUser` (lint allow-list) and object-level
      `ObjectAccess` (owning domain).
- **Attribute survival:** the policy must be readable from `ZServerEndpoint.endpoint` after `zServerSecurityLogic`
  and `serverLogic`. `NegotiatesOwnAccept.isSet(ep: AnyEndpoint)` is the precedent. Set the attribute on the
  underlying endpoint inside the `BaseEndpoints` factory, before the security logic is attached.
- **Description drift:** tapir's `.description(...)` replaces rather than appends, so a factory cannot inject the
  text. Instead, `Policy.requirement: String` renders the sentence (e.g. "Requires SystemAdmin or ProjectAdmin
  permissions for the list's project."). The ratchet test asserts that every endpoint with a policy has a
  description containing it, so the prose cannot drift from the attribute again.
- **Status code changes:** DEV-7713, DEV-7714 and the ingest report move from 200 to 403 (ingest: 401, which is its
  `Unauthorized` problem type). DEV-7712 moves from 200 to 403 for hidden resources, and from 500 to 200 for
  project history. Anonymous resource creation is refused earlier. All are breaking for a caller that relied on the
  hole, so the PR description and release notes list them.
- **DEV-7712 decisions:**
    - A hidden resource answers 403, consistent with `GET /v2/resources` (`ResourceMessagesV2.toResource`).
    - Project history skips forbidden resources silently. This is documented in
      `docs/03-endpoints/api-v2/reading-and-searching-resources.md`.
    - Value-level history entries for values the caller cannot see stay unfiltered in this plan. That needs
      per-version value permissions and is a follow-up (DEV-7720).
- **SystemUser allow-list:** the 19 current main-source files are allow-listed as they are. The lint stops new
  files, not existing uses. The legitimate prefetch-then-check pattern (`ValuesResponderV2:152,818`,
  `ValuesRestService:114`, `ReadResourcesServiceLive:267`) stays. After Phase 3, `ResourcesResponderV2` remains on
  the list because it has other legitimate uses.
- **Ingest** is a separate release artifact with its own scope-based authorization (`AuthorizationHandler`). Its
  commit uses the `fix(ingest):` scope. `AuthorizationHandlerLive.Empty` (with `JWT_DISABLE_AUTH`) stays a no-op.
- **Grant callers:** the only non-test callers of `ListsResponder.deleteListNodeCommentsADM` and
  `StandoffResponderV2.createMappingV2` are the REST paths. The test-it specs (`ListsResponderSpec`,
  `StandoffResponderV2Spec`) obtain a grant through `AuthorizationRestService`, the same as production. No
  `Grant.system` escape hatch is added.

## Implementation Phases

<!-- markdownlint-disable-next-line MD001 -->
#### Phase 1: DEV-7713 list-node comment deletion requires ProjectAdmin

- [ ] Add e2e tests to `DeleteAdminListsEndpointsE2ESpec`: `anythingUser1` (member, not admin) gets 403;
      `incunabulaProjectAdminUser` (admin of another project) gets 403; neither removes the comments (read back).
- [ ] Add e2e tests: `anythingAdminUser` and `rootUser` get 200 and the comments are gone.
- [ ] Pass the user from `AdminListRestService.deleteListNodeCommentsADM` to `ListsResponder.deleteListNodeCommentsADM`
      and drop `@unused`.
- [ ] Call `ensureUserIsAdminOrProjectOwner(nodeIri, user)` first in `ListsResponder.deleteListNodeCommentsADM`, before
      the node checks, so a non-admin learns nothing about the node.
- [ ] Update `ListsResponderSpec` callers for the new signature.
- [ ] Change the description at `AdminListsEndpoints.scala:138` to "Delete a list node's comments. Requires SystemAdmin
      or ProjectAdmin permissions for the list's project."
- [ ] Add the required-permission line to `docs/03-endpoints/api-admin/lists.md`.
- [ ] Run `bazel test //modules/test-e2e:test --test_filter='.*DeleteAdminListsEndpointsE2ESpec.*'`; the new 403 tests
      fail before the fix and all pass after.
- [ ] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

#### Phase 2: DEV-7714 creating a standoff mapping requires ProjectAdmin

- [ ] Add e2e tests for `POST /v2/mapping`: anonymous gets 401/403; `normalUser` (no project) gets 403; `anythingUser1`
      gets 403; `incunabulaProjectAdminUser` gets 403 for the anything project; for each, a read of the mapping IRI
      afterwards finds nothing.
- [ ] Add e2e tests: `anythingAdminUser` and `rootUser` get 200 for the anything project.
- [ ] Add an e2e test: a body naming a non-existent project gets 400 or 403, never 200 or 500.
- [ ] Call `auth.ensureSystemAdminOrProjectAdminById(user, createRequest.projectIri)` in
      `StandoffRestService.createMapping` after the request is parsed and before `standoffResponder.createMappingV2`.
- [ ] Change the description at `StandoffEndpoints.scala:32` to state the SystemAdmin or ProjectAdmin requirement.
- [ ] Add the rule to `docs/03-endpoints/api-v2/text/custom-standoff.md`.
- [ ] Run the new mapping e2e spec with `bazel test //modules/test-e2e:test --test_filter=...`; it passes.
- [ ] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

#### Phase 3: DEV-7712 resource history respects view permission

- [ ] Add a failing e2e test first: `GET /v2/resources/projectHistoryEvents/{p}` as a user who cannot see one resource
      of the project answers 500 today. Commit it red with the next deliverable, or mark it pending until the fix lands.
- [ ] Replace `.resources.head` in `getResourceAtGivenTime` (`ResourcesResponderV2.scala:1467-1481`) with
      `toResource(resourceIri)`.
- [ ] Pass `resourceHistoryRequest.requestingUser` instead of `SystemUser` to the preview read in
      `getResourceHistoryV2`, and correct the comment above it.
- [ ] In `getProjectResourceHistoryEvents`, skip a resource whose history answers `ForbiddenException` (only that type;
      other failures still fail the request). Land this in the same commit as the previous deliverable.
- [ ] Add e2e tests for `GET /v2/resources/history/{iri}` on a restricted resource: anonymous gets 403; a non-member
      gets 403; a project member with view permission gets 200.
- [ ] Add an e2e test: `projectHistoryEvents` for that caller answers 200 and the hidden resource's IRI is absent.
- [ ] Document in `docs/03-endpoints/api-v2/reading-and-searching-resources.md` that history endpoints require view
      permission and that project history omits resources the caller cannot see.
- [ ] Run the history e2e specs with `bazel test //modules/test-e2e:test --test_filter=...`; they pass, including the
      formerly red one.
- [ ] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

#### Phase 4: anonymous resource creation is refused

- [ ] Add an e2e test: anonymous `POST /v2/resources` answers 403 with "Anonymous users aren't allowed to create
      resources" and creates nothing.
- [ ] Remove `.ignore` from `ensureNotAnonymousUser` in `CreateResourceV2Handler.scala:102-105`.
- [ ] Search the e2e and test-it specs for resource creation as `AnonymousUser`; a spec that relied on the swallowed
      check is updated to a real user, and the PR lists it.
- [ ] Run `bazel test //modules/test-e2e:test --test_filter='.*Resource.*'`; it passes.
- [ ] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

#### Phase 5: ingest asset-overview report requires admin scope

- [ ] Add tests next to `MaintenanceEndpointsSpec` for `POST /report/asset-overview`: no token gets 401; a token without
      admin scope gets 401 (the ingest `Unauthorized` problem); an admin-scope token gets 200 "work in progress".
- [ ] Call `authorizationHandler.ensureAdminScope(principal)` in `ReportEndpointsHandler` before forking the report job,
      and take `AuthorizationHandler` as a dependency.
- [ ] Commit with the `fix(ingest):` scope.
- [ ] Run `just test-ingest`; it passes.
- [ ] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

#### Phase 6: DEV-7723 value deletion checks permission before revealing value details

`canRemoveValue` (`ValuesResponderV2.scala:1144`) loads the resource as `SystemUser`, then answers 400/404 for a wrong
resource class, a value not on the resource, or a wrong value type, all before `checkValuePermission`. Any logged-in
user can probe a hidden resource's class and its values' IRIs and types through `POST /v2/values/delete`. Erase is
SystemAdmin-only (`ValuesRestService.eraseValue`), so only delete is exposed.

- [ ] Add e2e tests to `ValuesEndpointsE2ESpec` for `POST /v2/values/delete` on a resource `anythingUser1` cannot see,
      as `anythingUser1` and as `normalUser`: a wrong resource class, a non-existent value IRI, and a wrong value type
      each answer the same status and message as a correct request; no answer names the class or the value type.
- [ ] Add an e2e test: on a visible resource, a value the caller cannot view answers the same 404 as a missing value.
- [ ] Add an e2e test: a caller with delete permission still gets 400 for a wrong class and a wrong value type, and 200
      for a correct request (the checks still run, only later).
- [ ] In `canRemoveValue`, call `resourceUtilV2.checkResourcePermission(resourceInfo, Permission.ObjectAccess.View,
      requestingUser)` right after the resource is loaded.
- [ ] Answer the missing-value `NotFoundException` also when the value exists but the caller lacks view permission
      on it.
- [ ] Move the `checkValuePermission(..., Delete, ...)` call directly after the value lookup, before the resource-class
      and value-type checks.
- [ ] Run `bazel test //modules/test-e2e:test --test_filter='.*ValuesEndpointsE2ESpec.*'` and
      `bazel test //modules/test-it:test --test_filter='.*ValuesResponderV2Spec.*'`; both pass.
- [ ] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

#### Phase 7: DEV-7721 and DEV-7722 admin checks on custom permissions

Both checks decide whether a caller may set custom permissions higher than the defaults. Both fail closed: callers
who should be exempt are not. The fixes widen access, so each gets a test proving who gains it and who does not.

- DEV-7721: `ValuesResponderV2.scala:268` calls `userPermissions.isProjectAdmin(requestingUser.id)` with the user IRI,
  so it is always false. A project admin cannot set value permissions above the defaults.
- DEV-7722: `CreateResourceV2Handler.scala:359-361` tests `!(isProjectAdmin && !isSystemAdmin)`, which is
  `!isProjectAdmin || isSystemAdmin`. The comparison runs for every SystemAdmin and is skipped only for a
  ProjectAdmin who is not SystemAdmin. The intended test is `!(isProjectAdmin || isSystemAdmin)`.

- [ ] Add a test-it test to `ValuesResponderV2Spec`: `anythingAdminUser` creates a value with permissions above the
      defaults and succeeds; `anythingUser1` gets 403 for the same request; `incunabulaProjectAdminUser` (admin of
      another project) gets 403.
- [ ] Pass `resourceInfo.projectADM.id` to `isProjectAdmin` in `ValuesResponderV2.scala:268`; commit as
      `fix: let project admins set custom value permissions (DEV-7721)`.
- [ ] Add a resource-creation test: `rootUser` (SystemAdmin, not a project admin) creates a resource with permissions
      above the defaults and succeeds; `anythingAdminUser` succeeds; `anythingUser1` gets 403.
- [ ] Change the condition at `CreateResourceV2Handler.scala:359-361` to
      `!(isProjectAdmin(...) || isSystemAdmin)`; commit as
      `fix: exempt system admins from the resource permission ceiling (DEV-7722)`.
- [ ] Run `bazel test //modules/test-it:test --test_filter='.*ValuesResponderV2Spec.*'` and the resource-creation spec;
      the new tests fail before each fix and pass after.
- [ ] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

#### Phase 8: ADR-0013 authorization architecture

- [ ] Write `docs/adr/0013-authorization-policy-enforcement.md`, standing alone (no links into `docs/specs/`).
- [ ] ADR content: declared function-level policy on every endpoint; PDP = `AuthorizationRestService`; PEP = security
      logic for user-only policies, RestService grant for project-scoped ones; object-level enforcement stays in the
      owning domain.
- [ ] ADR content: the Policy ADT and its semantics, including SystemAdmin in ProjectAdmin and ProjectMember.
- [ ] ADR content: the candidate-to-grant flow for project-scoped policies, what it guarantees (a forgotten check does
      not compile), and what it does not (the wrong project is caught by the target-from-grant rule and the matrix test,
      not by types).
- [ ] ADR content: SystemUser elevation is allow-listed and new uses need review.
- [ ] ADR content: the ratchet and lint as enforcement; the rejected alternatives (policy engines, everything in
      security logic).
- [ ] ADR content: state that it refines, not reverses, the "authorization is deliberately distributed" decision of the
      domain modularization work.
- [ ] Register the ADR in the `mkdocs.yml` nav next to 0012.
- [ ] Run `just check`; markdownlint passes.
- [ ] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

#### Phase 9: Policy model and policy-declaring endpoint bases

- [ ] Add `Policy` (enum, `key`, `requirement` sentence per case) in `slice/common/api/Policy.scala`.
- [ ] Add `ProjectCandidate` (`private[api]` constructor and `user` field) in `slice/common/api/`.
- [ ] Add `ProjectAdminGrant` with a `private[api]` constructor in `slice/common/api/`.
- [ ] Add `AuthorizationRestService.authorizeProjectAdmin(candidate, project): IO[ForbiddenException,
      ProjectAdminGrant]`, reusing `ensureSystemAdminOrProjectAdmin`.
- [ ] Add `authorizeProjectAdminById(candidate, projectIri)`, reusing `ensureSystemAdminOrProjectAdminById`.
- [ ] Add a compile-negative unit test (`scala.compiletime.testing.typeCheckErrors`): code outside `slice/common/api`
      can construct neither `ProjectCandidate` nor `ProjectAdminGrant`, cannot read `candidate.user`, and cannot call
      `ProjectAdminGrant.apply` or `copy`.
- [ ] Add a `BaseEndpoints` factory for `Public(reason)`: the public base with the attribute set.
- [ ] Add a `BaseEndpoints` factory for `Authenticated` and `SystemAdmin`: authenticate and authorize in the security
      logic (reuse `authorization.ensureUserIsNotAnonymous` / `ensureSystemAdmin`), with the attribute set.
- [ ] Add a `BaseEndpoints` factory for `ProjectAdmin`: authenticate and refuse anonymous or inactive users in the
      security logic; the security output is a `ProjectCandidate`, not a `User`; the attribute is set.
- [ ] Add a `BaseEndpoints` factory for `ObjectAccess` (anonymous allowed, security output `User`), with the attribute
      set. `ProjectMember` gets its factory and candidate type when the first endpoint migrates to it, not in this plan.
- [ ] Unit tests in `modules/webapi/src/test/.../BaseEndpointsSpec` (or the existing spec for `BaseEndpoints`): each
      factory sets the attribute, readable from the completed server endpoint.
- [ ] Unit tests: the `SystemAdmin` and `Authenticated` factories reject the wrong users before the server logic runs
      (server logic stubbed to fail the test if reached).
- [ ] Unit tests in `AuthorizationRestServiceSpec`: grants are issued to SystemAdmin and the project's admin, refused to
      a member, an admin of another project, anonymous, and the inactive system admin fixture.
- [ ] Run `just test-unit`; it passes.
- [ ] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

#### Phase 10: endpoint policy ratchet and convention docs

- [ ] Add `EndpointPolicyE2ESpec` in `modules/test-e2e/.../slice/api/` next to `EndpointsE2ESpec` (the only place
      `Endpoints.serverEndpoints` is enumerable with the full layer). Key each endpoint as `METHOD /path/template`.
- [ ] Ratchet assertion 1: every endpoint without a `Policy` attribute is listed in
      `modules/test-e2e/src/test/resources/endpoints-without-policy.txt`.
- [ ] Ratchet assertion 2: no line of that file names an endpoint that has a policy or no longer exists.
- [ ] Assertion: every `Public` and `Authenticated` policy has a non-blank reason.
- [ ] Assertion: every endpoint with a policy has a description containing `policy.requirement`.
- [ ] Generate the baseline file from the test's failure output (the computed missing set), sorted, one endpoint per
      line; check it in.
- [ ] Document the rule in `docs/development/dsp-api-conventions.md`: new endpoints use a policy-declaring factory and
      never extend the baseline.
- [ ] Update the three-tier API section in `CONVENTIONS.md` to the same rule.
- [ ] Add a reviewer checklist item to `REVIEW.md`: the declared policy matches what the code enforces, and
      `Public`/`Authenticated` reasons are justified.
- [ ] Run `bazel test //modules/test-e2e:test --test_filter='.*EndpointPolicyE2ESpec.*'`; it passes.
- [ ] Run `just check`; it passes.
- [ ] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

#### Phase 11: authorization lint in `just check`

- [ ] Add `tools/lint/check_authz.py`, rule 1: fail on `@unused` or `@scala.annotation.unused` directly annotating a
      parameter whose type is `User` (same line), in `src/main` files.
- [ ] Rule 2: fail on the token `SystemUser` (not `isSystemUser`) in a `src/main` file that is not listed in
      `tools/lint/system_user_allowlist.txt`.
- [ ] Rule 2 also fails when an allow-listed file no longer contains `SystemUser` (stale entry).
- [ ] Create `tools/lint/system_user_allowlist.txt` with the current main-source files, one path per line, each with a
      short reason comment.
- [ ] Add `tools/lint/check_authz_test.py` with inline fixtures: an `@unused user: User` param, an
      `@scala.annotation.unused requestingUser: User` param, a non-allow-listed `SystemUser` reference, and a stale
      allow-list entry each fail; `isSystemUser` and `@unused x: Unit` pass.
- [ ] Wire both as `py_test` targets in `tools/lint/BUILD.bazel`, reusing `_LINT_SRCS`.
- [ ] Add both targets to the `bazel test` line of the `check` recipe in `justfile`.
- [ ] Run `just check`; it passes on the current tree.
- [ ] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

#### Phase 12: pilot migration, admin lists

- [ ] Move the 6 public list reads and `getListsCanDeleteByIri` to the `Public(reason)` factory.
- [ ] Move the 9 list writes (create root, create child, name, labels, comments, position, `putListsByIri`, delete,
      delete comments) to the `ProjectAdmin(...)` factory, with `projectFrom` naming the path, body or list node.
- [ ] Change the `ListsResponder` write methods to take a `ProjectAdminGrant` instead of a `User`, and remove
      `ensureUserIsAdminOrProjectOwner`.
- [ ] In each `ListsResponder` write that targets an existing node, assert that the node's project equals
      `grant.project` and fail with 403 otherwise. Create-root takes its project from `grant.project`.
- [ ] Change the `AdminListRestService` write methods to take a `ProjectCandidate`; each resolves the project (from the
      body for create-root, from the node otherwise) and obtains the grant.
- [ ] Add a responder test: a grant for project A on a node of project B is refused with 403.
- [ ] Align the 15 endpoint descriptions with `policy.requirement`.
- [ ] Remove the 15 entries from `endpoints-without-policy.txt`.
- [ ] Update `ListsResponderSpec` and the admin list e2e specs to the new signatures.
- [ ] Run `just test-unit` and `bazel test //modules/test-e2e:test --test_filter='.*AdminLists.*'`; they pass.
- [ ] Run `bazel test //modules/test-it:test --test_filter='.*ListsResponderSpec.*'`; it passes.
- [ ] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

#### Phase 13: pilot migration, v2 standoff

- [ ] Move `postMapping` to the `ProjectAdmin("body projectIri")` factory.
- [ ] Move `postCanonicalize` to `Authenticated(reason)`, so it is checked in the security logic. The reason: it reads
      no project data.
- [ ] Change `StandoffResponderV2.createMappingV2` to take a `ProjectAdminGrant` and write into `grant.project`'s data
      graph, dropping the request's own project lookup.
- [ ] Change `StandoffRestService.createMapping` to take a `ProjectCandidate` and obtain the grant from the body's
      `projectIri` (replacing the Phase 2 `ensure*` call).
- [ ] Align both descriptions with `policy.requirement`.
- [ ] Remove the two entries from `endpoints-without-policy.txt`.
- [ ] Update `StandoffResponderV2Spec`.
- [ ] Run `bazel test //modules/test-e2e:test --test_filter='.*(Standoff|Mapping|EndpointPolicy).*'`; it passes.
- [ ] Run `bazel test //modules/test-it:test --test_filter='.*StandoffResponderV2Spec.*'`; it passes.
- [ ] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

#### Phase 14: authorization matrix for the migrated endpoints

- [ ] Add `AuthorizationMatrixE2ESpec` in `modules/test-e2e` with principals from `SharedTestDataADM`: anonymous,
      `normalUser`, `anythingUser1`, `incunabulaProjectAdminUser`, `anythingAdminUser`, `rootUser`. Use the anything
      project's existing lists and data; add no records to shared datasets.
- [ ] Hand-write the expected outcome per migrated write endpoint and principal: allowed, or denied with 401/403. This
      table is the independent oracle.
- [ ] For each pair, send a valid request and assert the outcome. On denial, assert by read-back that nothing changed.
- [ ] Assert that the hand-written table agrees with each endpoint's declared `Policy`, so a mislabelled policy fails
      too.
- [ ] Run `bazel test //modules/test-e2e:test --test_filter='.*AuthorizationMatrixE2ESpec.*'`; it passes.
- [ ] Run `just test-unit`, `just test-e2e`, `just test-ingest` and `just check`; all pass.
- [ ] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

## Human Actions

| Id | Action | Who | When | Why not the agent |
| --- | --- | --- | --- | --- |
| H2 | Review and accept ADR-0013 | Balduin (tech lead) | after ship | Architecture acceptance is an owner decision |
| H3 | Decide release timing and whether to hotfix DEV-7713, DEV-7714 and the ingest report to stage/prod ahead of the release train; release notes list the status-code changes | Ivan | after ship | Deploy and disclosure decisions |

## Acceptance Criteria

- [ ] DEV-7713: only SystemAdmin and ProjectAdmin of the list's project can delete list-node comments; others get 403
      and the comments remain.
- [ ] DEV-7714: only SystemAdmin and ProjectAdmin of the named project can create a mapping; others get 401/403 and no
      mapping is written.
- [ ] DEV-7712: history of a resource the caller cannot see answers 403; project history answers 200 and omits such
      resources; no 500.
- [ ] Anonymous resource creation answers 403.
- [ ] The ingest report endpoint refuses callers without admin scope.
- [ ] DEV-7723: value deletion on a resource or value the caller cannot see reveals neither the resource class nor
      the value's existence or type.
- [ ] DEV-7721: a project admin can set custom value permissions above the defaults; a member cannot.
- [ ] DEV-7722: a SystemAdmin can set custom resource permissions above the defaults; a member cannot.
- [ ] ADR-0013 exists, is in the mkdocs nav, and passes `just check`.
- [ ] Every endpoint either declares a `Policy` or is in `endpoints-without-policy.txt`; adding an endpoint without one
      fails `EndpointPolicyE2ESpec`.
- [ ] `just check` fails on `@unused` on a `User` parameter and on a new `SystemUser` reference outside the allow-list
      (proved by the lint's fixture tests).
- [ ] All admin list and v2 standoff endpoints declare a policy; their write handlers receive a `ProjectCandidate`, not
      a `User`; their domain writes take a `ProjectAdminGrant`; `ensureUserIsAdminOrProjectOwner` is gone.
- [ ] On a migrated endpoint, a handler that skips the PDP call does not compile (compile-negative test).
- [ ] The authorization matrix passes for every migrated write endpoint and principal.
- [ ] `just test-unit`, `just test-e2e`, `just test-ingest` and `just check` pass.

## Dependencies & Risks

- **One PR holds the security fixes behind the architecture review.** Phases 1-7 are self-contained `fix:`
  commits at the bottom of the branch. If review of Phases 8-14 stalls, they can be cherry-picked into their own
  PR without rework.
- **Behaviour changes break callers that relied on the holes** (DSP-TOOLS, DSP-APP, scripts). They are listed in
  the PR description and in the release notes (H3).
- **Ratchet test placement:** e2e only, because the server endpoints need the full layer. It runs in CI, not in
  `just test-unit`.
- **Lint precision:** rule 1 is line-based and misses a multi-line `@unused` before a `User` parameter. The
  compiler still catches an unannotated unused `user`, so the gap is narrow.
- **Local e2e** needs a Colima VM larger than 2 GiB.
- **Follow-ups out of scope (filed 2026-10-30; DEV-7721, DEV-7722 and DEV-7723 moved into Phases 6-7 on 2026-10-31):**
    - DEV-7718: search count endpoints ignore permissions (`SearchRestService.scala:48,188`,
      `SearchResponderV2.gravsearchCountV2`, `fulltextSearchCountV2`, `searchResourcesByLabelCountV2`), although their
      descriptions require object permissions. Fail-open and reachable anonymously (`withUserEndpoint`): a Gravsearch
      count with a `FILTER` on a hidden resource's values works as an oracle on those values. Not a fix commit:
      permissions are evaluated in Scala after the query, so a correct count needs either permission filtering in
      SPARQL or counting the permission-filtered results. Needs a design decision; highest priority of the follow-ups.
    - DEV-7719: `GET /v2/resources/info` is public and unfiltered, including deleted resources. Confirm intent. PR #4402
      changes the same endpoint.
    - DEV-7720: value-history entries for invisible values (DEV-7712 open question).
    - DEV-7724: `OntologyCacheHelpers` skips the active-user check. Not exploitable through the API:
      `AuthenticatorLive` reloads the user and rejects inactive accounts on every request, including JWT ones. It is a
      consistency fix and belongs to the ontology migration (DEV-7725).
    - DEV-7725: migrate the remaining slices, worst first: ontologies (re-implemented rule), v2 resources/values,
      admin users.

## Risk Analysis & Mitigation

| Risk | Likelihood | Impact | Mitigation |
| --- | --- | --- | --- |
| A fix changes behaviour a client depends on | M | M | Status changes listed in PR and release notes; H3 decides rollout |
| Policy attribute lost through `zServerSecurityLogic` | L | H | Phase 9 unit test reads it from the completed server endpoint |
| Declared project policy not enforced on an unmigrated endpoint | M | H | Ratchet tracks unmigrated endpoints; migrated ones cannot compile without the grant |
| Grant obtained for the wrong project | L | H | Domain writes take the target from the grant or assert ownership; matrix test with hand-written oracle |
| Baseline becomes a dumping ground | M | M | Stale entries fail; convention docs and REVIEW.md forbid extending it |
| SystemUser allow-list normalises elevation | M | M | Per-entry reason; new files need review; ADR states the rule |
| Fixes delayed by the architecture review | M | H | Phases 1-7 are cherry-pickable commits |

## Success Metrics

- Endpoints without a declared policy: the baseline line count at the end of Phase 10 (about 195, all endpoints),
  minus 17 after Phase 13 (15 admin lists, 2 v2 standoff). The PR description records both numbers.
- Authenticated-only endpoints without a role check: 3 before (list-comments delete, standoff mapping,
  canonicalize). After: 0 unreviewed. Canonicalize becomes a reviewed `Authenticated` policy.
- Main-source files referencing `SystemUser`: 19 before. It must not grow; the allow-list count is that number.
- Responder-level copies of the project-admin rule: `ListsResponder.ensureUserIsAdminOrProjectOwner` removed. The
  ontology and cardinality copies remain for follow-up.

## References

- Bugs: DEV-7712, DEV-7713, DEV-7714 (Linear triage, 2026-10-29), DEV-7721, DEV-7722, DEV-7723; follow-ups
  DEV-7718, DEV-7719, DEV-7720, DEV-7724, DEV-7725
- Explainer of the root problem and the target model: <https://claude.ai/artifact/7Pa7MKquPJskjxGKCAvHqo>
- `modules/webapi/src/main/scala/org/knora/webapi/slice/common/api/BaseEndpoints.scala` (`bearerSystemAdminEndpoint`:
  authorization in security logic)
- `modules/webapi/src/main/scala/org/knora/webapi/slice/common/api/AuthorizationRestService.scala`
- `modules/webapi/src/main/scala/org/knora/webapi/slice/common/api/NegotiatesOwnAccept.scala` (tapir attribute
  precedent)
- `modules/test-e2e/src/test/scala/org/knora/webapi/slice/api/EndpointsE2ESpec.scala` (endpoint enumeration)
- `tools/lint/BUILD.bazel`, `tools/lint/check_relative_imports.py` (lint pattern)
- `ARCH-MAP.md` (read permissions "enforced late and in two places"), `docs/contexts/identity-access/CONTEXT.md`
- dasch-specs `specs/2026-07-30-dsp-api-domain-modularization/02-domain-modularization-reasoning.md` (authorization is
  deliberately distributed)
- dasch-specs `learnings/design-decisions/security-logic-authenticates-body-buffering-precedes-it.md`
- OWASP API Security Top 10 2023 (API1 BOLA, API5 BFLA): <https://owasp.org/API-Security/editions/2023/en/0x11-t10/>
- OWASP Authorization Cheat Sheet: <https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html>
- tapir server logic and security: <https://tapir.softwaremill.com/en/latest/server/logic.html>
