---
title: "fix: close the triaged authorization gaps and make every endpoint declare its authorization policy"
type: fix
date: 2026-10-30
author: "Ivan Subotic"
status: implemented
repositories: []
linear:
---

# fix: close the triaged authorization gaps and make every endpoint declare its authorization policy

## Overview

Three security issues in triage (DEV-7712, DEV-7713, DEV-7714) are real. Two more fail-open gaps of the
same kind turned up during research. Three filed follow-ups were verified and pulled in: DEV-7723 (fail-open),
DEV-7721 and DEV-7722 (latent: wrong admin checks that the permission comparison masks today, see Phase 7). This
plan fixes all eight and adds the structure that makes this bug class visible:

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
| DEV-7712 | `ResourcesResponderV2.scala:1160-1165`, `:1467-1481` | Preview read as `SystemUser`; `.resources.head` on a filtered result | Anonymous callers get dates and author IRIs of hidden resources; project history fails (403, measured; 500 was assumed) when one resource is hidden |
| new | `CreateResourceV2Handler.scala:102-105` | `ensureNotAnonymousUser` ends in `.ignore` | The anonymous check never fires; only the later create-permission check stands |
| new | `modules/ingest/.../ReportEndpointsHandler.scala:21` | `serverLogic(_ => _ => ...)` drops the principal | Any valid JWT starts a forked all-project report, although the endpoint is documented as admin-only |
| DEV-7723 | `ValuesResponderV2.scala:1144` (`canRemoveValue`) | Resource read as `SystemUser`; class, value and type errors before the permission check | Any logged-in user probes a hidden resource's class and its values' IRIs and types |
| DEV-7721 | `ValuesResponderV2.scala:268` | `isProjectAdmin(requestingUser.id)` passes the user IRI | The admin exemption never applies; masked today (Phase 7) |
| DEV-7722 | `CreateResourceV2Handler.scala:359-361` | `!(isProjectAdmin && !isSystemAdmin)` | SystemAdmins are not exempt; masked today (Phase 7) |

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
  object permissions (DEV-7712). Non-test `SystemUser` references sit in 13 main-source files (word-bounded match
  on `*.scala` under `modules/*/src/main`, so `isSystemUser` does not count; some hits are comments).
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
- **Lint:** `just check` fails on `@unused` on a `User` parameter, and on `SystemUser` in a main-source file, each
  outside its allow-list.

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
  and `serverLogic`. `NegotiatesOwnAccept.isSet(ep: AnyEndpoint)` is the precedent, and it already survives: it is
  set after `bearerSystemAdminEndpoint(...)` (`SparqlPassthroughEndpoints.scala:119`) and read in the server
  interceptor (`DspApiServer.scala`). Set the attribute inside the `BaseEndpoints` factory. Two hazards stay: a later
  `.attribute(Policy.key, x)` in a slice silently relabels the endpoint (the Phase 14 table-vs-policy assertion
  catches it), and `withInfo` replaces all attributes (unused in `modules/webapi/src/main` today; REVIEW.md item).
- **Type-level access:** `private[api]` covers `org.knora.webapi.slice.common.api` and all its subpackages. For
  `final case class ProjectAdminGrant private[api] (...)`, Scala 3 restricts `apply` and `copy` to that access;
  `unapply` and the fields stay public, which is deliberate: after authorization, responders read `grant.user` and
  `grant.project`. `ProjectCandidate` stays a plain `final class`, because a case class would let `unapply` leak
  the user. Package access stops accident, not intent: any file that declares the package gets in, which is also
  how tests obtain a candidate (Phase 9).
- **Description drift:** tapir's `.description(...)` replaces rather than appends, so a factory cannot inject the
  text. Instead, `Policy.requirement: String` renders the sentence (e.g. "Requires SystemAdmin or ProjectAdmin
  permissions for the list's project."). The ratchet test asserts that every endpoint with a policy has a
  description containing it, so the prose cannot drift from the attribute again.
- **Status code changes:** DEV-7713 and DEV-7714 move from 200 to 403; the ingest report moves from 202 to 401 (its
  `Unauthorized` problem type). DEV-7712 moves from 200 to 403 for hidden resources, and from 403 to 200 for
  project history (measured in Phase 3; `checkResourceIris` refuses before `.resources.head`). Anonymous resource creation is refused earlier. DEV-7723 moves value deletion on a hidden
  resource from 400/404 to 403, and on a visible resource with a hidden value from 403 to 404. All are breaking for a
  caller that relied on the hole, so the PR description and release notes list them.
- **DEV-7712 decisions:**
    - A hidden resource answers 403, consistent with `GET /v2/resources` (`ResourceMessagesV2.toResource`).
    - Project history skips forbidden resources silently. This is documented in
      `docs/03-endpoints/api-v2/reading-and-searching-resources.md`.
    - Value-level history entries for values the caller cannot see stay unfiltered in this plan. That needs
      per-version value permissions and is a follow-up (DEV-7720).
- **SystemUser allow-list:** the main-source files that use `SystemUser` in code (10 after the final review; comment
  mentions do not count) are allow-listed as they are. The lint stops new
  files, not existing uses. The legitimate prefetch-then-check pattern (`ValuesResponderV2:152,818`,
  `ValuesRestService:114`, `ReadResourcesServiceLive:267`) stays. Phase 3 removed the last `SystemUser`
  reference from `ResourcesResponderV2`, so it is not on the list.
- **Ingest** is a separate release artifact with its own scope-based authorization (`AuthorizationHandler`). Its
  commit uses the `fix(ingest):` scope. `AuthorizationHandlerLive.Empty` (with `JWT_DISABLE_AUTH`) stays a no-op.
- **Grant callers:** the only non-test callers of `ListsResponder.deleteListNodeCommentsADM` and
  `StandoffResponderV2.createMappingV2` are the REST paths. The test-it specs (`ListsResponderSpec`,
  `StandoffResponderV2Spec`) obtain a grant through `AuthorizationRestService`, the same as production. They sit
  outside `slice.common.api` and cannot construct a `ProjectCandidate`, so a test-source helper declared in package
  `org.knora.webapi.slice.common.api` (`TestCandidates.of(user)`, in both `webapi` test and `test-it` sources)
  provides one. No `Grant.system` escape hatch is added to main sources.
- **SystemUser and grants:** `isSystemAdminOrUser` (`AuthorizationRestService.scala:120`) lets `SystemUser` through
  `authorizeProjectAdmin`. It cannot arrive over HTTP: `AuthenticatorLive.ensureActiveUser` rejects every built-in
  user. In-process callers holding `SystemUser` are what the Phase 11 allow-list tracks.
- **Existence answers before authorization:** `ensureUserIsAdminOrProjectOwner` resolves the node's project before
  it checks the role (`ListsResponder.scala:732-733`), so a missing node answers 400 before the 403. Accepted: list
  nodes are publicly readable (`getListsByIri` is public), so their existence is no secret. Phases 12 and 13 keep
  this order. Path and query decode errors also answer before authentication, which is tapir's order.

## Implementation Phases

<!-- markdownlint-disable-next-line MD001 -->
#### Phase 1: DEV-7713 list-node comment deletion requires ProjectAdmin

- [x] Add e2e tests to `DeleteAdminListsEndpointsE2ESpec`: anonymous gets 401 (`securedEndpoint` without
      credentials); `anythingUser1` (member, not admin) gets 403; `incunabulaProjectAdminUser` (admin of another
      project) gets 403; none removes the comments (read back).
- [x] Add e2e tests: `anythingAdminUser` and `rootUser` get 200 and the comments are gone.
- [x] Pass the user from `AdminListRestService.deleteListNodeCommentsADM` to `ListsResponder.deleteListNodeCommentsADM`
      and drop `@unused`.
- [x] Call `ensureUserIsAdminOrProjectOwner(nodeIri, user)` first in `ListsResponder.deleteListNodeCommentsADM`, before
      the node checks, so a non-admin learns nothing about the node beyond its existence (see Technical
      Considerations).
- [x] Update `ListsResponderSpec` callers for the new signature.
- [x] Change the description at `AdminListsEndpoints.scala:138` to "Delete a list node's comments. Requires SystemAdmin
      or ProjectAdmin permissions for the list's project."
- [x] Add the required-permission line to `docs/03-endpoints/api-admin/lists.md`.
- [x] Run `bazel test //modules/test-e2e:test --test_filter='.*DeleteAdminListsEndpointsE2ESpec.*'`; the new 403 tests
      fail before the fix and all pass after.
- [x] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

#### Phase 2: DEV-7714 creating a standoff mapping requires ProjectAdmin

- [x] Add e2e tests for `POST /v2/mapping`: anonymous gets 401/403; `normalUser` (no project) gets 403; `anythingUser1`
      gets 403; `incunabulaProjectAdminUser` gets 403 for the anything project; for each, a read of the mapping IRI
      afterwards finds nothing.
- [x] Add e2e tests: `anythingAdminUser` and `rootUser` get 200 for the anything project.
- [x] Add an e2e test: a body naming a non-existent project gets 400 or 403, never 200 or 500.
- [x] Call `auth.ensureSystemAdminOrProjectAdminById(user, createRequest.projectIri)` in
      `StandoffRestService.createMapping` after the request is parsed and before `standoffResponder.createMappingV2`.
- [x] Change the description at `StandoffEndpoints.scala:32` to state the SystemAdmin or ProjectAdmin requirement.
- [x] Add the rule to `docs/03-endpoints/api-v2/text/custom-standoff.md`.
- [x] Run the new mapping e2e spec with `bazel test //modules/test-e2e:test --test_filter=...`; it passes.
- [x] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

#### Phase 3: DEV-7712 resource history respects view permission

- [x] Add a failing e2e test first: `GET /v2/resources/projectHistoryEvents/{p}` as a user who cannot see one resource
      of the project answers 500 today. Commit it red with the next deliverable, or mark it pending until the fix lands.
- [x] Replace `.resources.head` in `getResourceAtGivenTime` (`ResourcesResponderV2.scala:1467-1481`) with
      `toResource(resourceIri)`.
- [x] Pass `resourceHistoryRequest.requestingUser` instead of `SystemUser` to the preview read in
      `getResourceHistoryV2`, and correct the comment above it.
- [x] In `getProjectResourceHistoryEvents`, skip a resource whose history answers `ForbiddenException` (only that type;
      other failures still fail the request). Land this in the same commit as the previous deliverable.
- [x] Add e2e tests for `GET /v2/resources/history/{iri}` on the hidden fixture
      `http://rdfh.ch/0001/XTxSMt0ySraVmwXD-bD2wQ` (`V knora-admin:Creator`, created by `anythingUser2`): anonymous, `normalUser` and `anythingUser1` get 403;
      `anythingUser2` (its creator) gets 200.
- [x] Add an e2e test: a non-existent resource IRI answers 404 (unchanged; `checkResourceIris`).
- [x] Add an e2e test: `projectHistoryEvents` for the anything project as `anythingUser1` answers 200 and that IRI is
      absent; as anonymous it also answers 200.
- [x] Document in `docs/03-endpoints/api-v2/reading-and-searching-resources.md` that history endpoints require view
      permission and that project history omits resources the caller cannot see.
- [x] Run the history e2e specs with `bazel test //modules/test-e2e:test --test_filter=...`; they pass, including the
      formerly red one.
- [x] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

#### Phase 4: anonymous resource creation is refused

- [x] Add an e2e test: anonymous `POST /v2/resources` answers 403 with "Anonymous users aren't allowed to create
      resources" and creates nothing.
- [x] Remove `.ignore` from `ensureNotAnonymousUser` in `CreateResourceV2Handler.scala:102-105`.
- [x] Search the e2e and test-it specs for resource creation as `AnonymousUser`; a spec that relied on the swallowed
      check is updated to a real user, and the PR lists it.
- [x] Run `bazel test //modules/test-e2e:test --test_filter='.*Resource.*'`; it passes.
- [x] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

#### Phase 5: ingest asset-overview report requires admin scope

- [x] Add `ReportEndpointsSpec` next to `MaintenanceEndpointsSpec` (`modules/ingest/src/test/scala/swiss/dasch/api/`),
      in-process the same way (tapir `ZioHttpInterpreter`, no Docker), for `POST /report/asset-overview`: no token
      gets 401; a token without admin scope gets 401 (the ingest `Unauthorized` problem); an admin-scope token gets
      202 "work in progress".
- [x] Call `authorizationHandler.ensureAdminScope(principal)` (`AuthorizationHandler.scala:16`) in
      `ReportEndpointsHandler` before forking the report job, and take `AuthorizationHandler` as a dependency; the
      handler's `ZLayer.derive` then needs `AuthorizationHandler` in the ingest layer.
- [x] Commit with the `fix(ingest):` scope.
- [x] Run `just test-ingest`; it passes.
- [x] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

#### Phase 6: DEV-7723 value deletion checks permission before revealing value details

`canRemoveValue` (`ValuesResponderV2.scala:1144`) loads the resource as `SystemUser`, then answers 400/404 for a wrong
resource class, a value not on the resource, or a wrong value type, all before `checkValuePermission`. Any logged-in
user can probe a hidden resource's class and its values' IRIs and types through `POST /v2/values/delete`. Erase is
SystemAdmin-only (`ValuesRestService.eraseValue`), so only delete is exposed, but erase shares `canRemoveValue` and
must keep working.

Decisions:

- A hidden resource answers 403 for every variant of the request, consistent with `GET /v2/resources` and the
  DEV-7712 decision above. A missing resource stays 404, so resource existence remains observable, as it is on every
  v2 read route; a uniform 404 for hidden resources would be a platform-wide change and is out of scope.
- Within one resource, the answer must not depend on the class, value or type in the request. The 403 message
  (`ResourceUtilV2.scala:54`) embeds the caller's email and the resource IRI, which are the same across variants,
  so the tests compare status and full message per resource.
- The checks before the resource load (property lookup, link-property rejection, `HasStandoffLinkToValue`) read only
  the ontology and leak no data; they stay first.

Fixtures (anything data, `test_data/project_data/anything-data.ttl`; verified instances, nothing added):

- Hidden resource: `http://rdfh.ch/0001/XTxSMt0ySraVmwXD-bD2wQ` (`V knora-admin:Creator`, created by `anythingUser2`)
  with int value `.../values/PVPAa37xR--K_wxQwlvSsg`; hidden from `anythingUser1` and `normalUser`.
- Visible resource with hidden values: `http://rdfh.ch/0001/a-thing-with-text-values`
  (`CR knora-admin:Creator|V knora-admin:ProjectMember`) with `.../values/1` and `.../values/2` (`CR knora-admin:Creator`,
  created by `anythingUser1`); the caller is `anythingUser2`.
- View-but-not-delete: a value with `V knora-admin:UnknownUser|M knora-admin:ProjectMember` on
  `http://rdfh.ch/0001/iqW_PBiHRdyTFzik8tuSog`, for `anythingUser2` (not its creator); pick the value IRI during
  implementation and check its creator is not the caller.
- Positive controls that really delete create their own resource and value first, as `ValuesEndpointsE2ESpec`
  already does, so the shared data stays intact.

- [x] Add e2e tests to `ValuesEndpointsE2ESpec` for `POST /v2/values/delete` on the hidden resource, as
      `anythingUser1` and as `normalUser`: the correct request, a wrong resource class, a non-existent value IRI and a
      wrong value type each answer 403 with the same message; none names the class or the value type.
- [x] Add e2e tests: anonymous gets the anonymous refusal before any lookup; a non-existent resource IRI gets 404.
- [x] Add an e2e test as `anythingUser2` on `a-thing-with-text-values`: deleting `values/1` and deleting a
      non-existent value IRI answer the same 404 and message apart from the value IRI.
- [x] Add an e2e test: the view-but-not-delete value answers 403 before any class or type check (a wrong class gives
      the same 403).
- [x] Add an e2e test on a self-created resource: a caller with delete permission still gets 400 for a wrong class and
      a wrong value type, and 200 for a correct request (the checks still run, only later).
- [x] In `canRemoveValue`, call `resourceUtilV2.checkResourcePermission(resourceInfo, Permission.ObjectAccess.View,
      requestingUser)` right after the resource is loaded.
- [x] Answer the missing-value `NotFoundException` also when the value exists but the caller lacks view permission
      on it.
- [x] Move the `checkValuePermission(..., Delete, ...)` call directly after the value lookup, before the resource-class
      and value-type checks.
- [x] Run `bazel test //modules/test-e2e:test --test_filter='.*ValuesEndpointsE2ESpec.*'` and
      `bazel test //modules/test-it:test --test_filter='.*(ValuesResponderV2Spec|ValuesEraseSpec).*'`; all pass,
      including erase and history-only erase, unchanged.
- [x] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

#### Phase 7: DEV-7721 and DEV-7722 admin checks on custom permissions, behind one shared helper

Three sites decide whether a caller may set custom permissions higher than the defaults, each with its own copy of
the "SystemAdmin or ProjectAdmin is exempt" rule. Two copies are wrong:

- DEV-7721: `ValuesResponderV2.scala:268` calls `userPermissions.isProjectAdmin(requestingUser.id)` with the user IRI,
  through the `String` overload (`PermissionsMessagesADM.scala:282`), so it is always false.
- DEV-7722: `CreateResourceV2Handler.scala:359-361` tests `!(isProjectAdmin && !isSystemAdmin)`, which is
  `!isProjectAdmin || isSystemAdmin`. The intended test is `!(isProjectAdmin || isSystemAdmin)`.
- Correct copy: `CreateResourceV2Handler.scala:723-725` (values in a new resource).

**Both bugs are masked today, so they cannot be shown over HTTP with the test data.** `comparePermissionsADM`
evaluates both literals through `getUserPermissionADM` (`PermissionUtilADM.scala:87-130`, `:175-209`), which gives
SystemUser, SystemAdmin and every holder of `ProjectAdminAllPermission` the maximum permission on both sides, so they
always compare equal. It also evaluates both literals with the caller as creator, and the anything default (the only
anything DOAP, `permissions-data.ttl`) grants `CR knora-admin:Creator`, the maximum, so no custom literal exceeds it
for any caller. DEV-7722 is therefore unreachable: a SystemAdmin is always short-circuited. DEV-7721 shows only for a
member of the ProjectAdmin group who lacks `ProjectAdminAllPermission`, under a default that gives the creator less
than the custom literal. New projects give the ProjectAdmin group `ProjectAdminAllPermission`
(`PermissionsResponder.scala:546-551`), so that needs edited admin permissions; production data is unchecked. The
fixes stay, because the wrong checks become live the moment either condition changes,
but the proof moves to unit tests with hand-built users and literals. The e2e and test-it checks are regression
checks, not red/green.

One helper replaces all three copies. It takes a typed `ProjectIri` and uses `User.isProjectAdmin(ProjectIri)`
(`User.scala:135`), which has no `String` overload, so passing a user IRI no longer compiles. It keeps today's
exemption, `User.isSystemAdmin` (same answer as `PermissionsDataADM.isSystemAdmin`) or `User.isProjectAdmin(project)`.
It does not add `SystemUser`, which `getUserPermissionADM` already treats as maximum.

```scala
// PermissionUtilADM (object), next to comparePermissionsADM
def ensureWithinDefaultPermissions(
  project: ProjectIri,
  customPermissions: String,
  defaultPermissions: String,
  requestingUser: User,
  message: String,
): IO[ForbiddenException, Unit]
```

`defaultPermissions` is a plain `String`: site 723 passes `defaultPropertyPermissions(propertyIri)`, a map built over
exactly the request's properties (`CreateResourceV2Handler.scala:208`) and already read unguarded at `:762`, so
evaluating it for admins is safe. `comparePermissionsADM` takes `entityProject: IRI`, so the helper passes
`project.value`.

The helper commit is a refactor with no behaviour change; each fix commit then moves one wrong site onto it. The two
`fix:` commits stay cherry-pickable once the helper commit is picked with them.

- [x] Add `PermissionUtilADM.ensureWithinDefaultPermissions`: skip the check for SystemAdmin or ProjectAdmin of
      `project`; otherwise fail with `ForbiddenException(message)` when `comparePermissionsADM` answers
      `AGreaterThanB`.
- [x] Add unit tests to `PermissionUtilADMSpec` with hand-built users and literals: default
      `M knora-admin:ProjectMember`, custom `CR knora-admin:ProjectMember`, project `0001`. A user in the ProjectAdmin
      group of `0001` without `ProjectAdminAllPermission` passes; a SystemAdmin passes; a plain member of `0001` and
      a ProjectAdmin of another project fail with the given message; a plain member passes with custom
      `M knora-admin:ProjectMember` (equal) and `V knora-admin:ProjectMember` (lower).
- [x] Move `CreateResourceV2Handler.scala:723-725` onto the helper, keeping its error message; commit as
      `refactor: share the custom-permission ceiling check`. Existing resource-creation tests pass unchanged.
- [x] Move `ValuesResponderV2.scala:268` onto the helper with `resourceInfo.projectADM.id`; commit as
      `fix: apply the admin exemption to custom value permissions (DEV-7721)`.
- [x] Move `CreateResourceV2Handler.scala:359-361` onto the helper with `internalCreateResource.projectADM.id`; commit
      as `fix: exempt system admins from the resource permission ceiling (DEV-7722)`.
- [x] Add regression tests: in `ValuesResponderV2Spec`, `anythingAdminUser` creates a value with custom permissions
      `CR knora-admin:ProjectMember` and succeeds; in the resource-creation spec, `rootUser` and `anythingAdminUser`
      create a resource with the same custom permissions and succeed. Both pass before and after the fixes.
- [x] Confirm no inline copy remains: `grep -rn "comparePermissionsADM" modules/webapi/src/main` finds only
      `PermissionUtilADM`.
- [x] Run `bazel test //modules/test-it:test
      --test_filter='.*(PermissionUtilADMSpec|ValuesResponderV2Spec|ResourcesResponderV2Spec).*'`; all pass; the unit
      test for the ProjectAdmin-group user fails against a helper that reproduces the DEV-7721 condition.
- [x] Measured: through `comparePermissionsADM` a SystemAdmin gets the maximum on both sides, so a helper with the old
      DEV-7722 condition passes all 19 tests.
- [x] Owner decision (2026-11-01): DEV-7722 still gets a red test. Make the comparison in
      `ensureWithinDefaultPermissions` replaceable in tests (package-private overload or parameter, no main-source
      escape hatch beyond that), and add a SystemAdmin case with a comparison forced to `AGreaterThanB`; it fails
      against the old condition and passes after the fix.
- [x] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

#### Phase 8: ADR-0013 authorization architecture

- [x] Write `docs/adr/0013-authorization-policy-enforcement.md`, standing alone (no links into `docs/specs/`).
- [x] ADR content: declared function-level policy on every endpoint; PDP = `AuthorizationRestService`; PEP = security
      logic for user-only policies, RestService grant for project-scoped ones; object-level enforcement stays in the
      owning domain.
- [x] ADR content: the Policy ADT and its semantics, including SystemAdmin in ProjectAdmin and ProjectMember.
- [x] ADR content: the candidate-to-grant flow for project-scoped policies, what it guarantees (a forgotten check does
      not compile), and what it does not (the wrong project is caught by the target-from-grant rule and the matrix test,
      not by types; package access stops accident, not intent, since any file declaring the package gets in, which is
      how the test-source `TestCandidates` works).
- [x] ADR content: SystemUser elevation is allow-listed and new uses need review.
- [x] ADR content: the ratchet and lint as enforcement; the rejected alternatives (policy engines, everything in
      security logic).
- [x] ADR content: state that it refines, not reverses, the "authorization is deliberately distributed" decision of the
      domain modularization work.
- [x] Register the ADR in the `mkdocs.yml` nav next to 0012.
- [x] Run `just check`; markdownlint passes.
- [x] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

#### Phase 9: Policy model and policy-declaring endpoint bases

- [x] Add `Policy` (enum, `key`, `requirement` sentence per case) in `slice/common/api/Policy.scala`.
- [x] Add `ProjectCandidate` (`private[api]` constructor and `user` field) in `slice/common/api/`.
- [x] Add `ProjectAdminGrant` with a `private[api]` constructor in `slice/common/api/`.
- [x] Add `AuthorizationRestService.authorizeProjectAdmin(candidate, project): IO[ForbiddenException,
      ProjectAdminGrant]`, reusing `ensureSystemAdminOrProjectAdmin`. Owner decision (2026-11-01): removed at the final
      review; it had no production caller and trusted the caller's `KnoraProject`. `...ById` is the only grant source.
- [x] Add `authorizeProjectAdminById(candidate, projectIri)`, reusing `ensureSystemAdminOrProjectAdminById`.
- [x] Add a compile-negative unit test (`scala.compiletime.testing.typeCheckErrors`): code outside `slice/common/api`
      can construct neither `ProjectCandidate` nor `ProjectAdminGrant`, cannot read `candidate.user`, and cannot call
      `ProjectAdminGrant.apply` or `copy`. Place the spec in a package outside `org.knora.webapi.slice.common.api`
      and its subpackages (inside it every probe compiles, so the test proves nothing). Assert on message
      substrings, not on `nonEmpty`: "cannot be accessed" for `new`, `copy` and `.user`, "does not take parameters"
      for `ProjectAdminGrant(...)`.
- [x] Add the test-source helper `TestCandidates.of(user)` in package `org.knora.webapi.slice.common.api`, in the
      `webapi` test sources and in the `test-it` sources.
- [x] Add a `BaseEndpoints` factory for `Public(reason)`: the public base with the attribute set.
- [x] Add a `BaseEndpoints` factory for `Authenticated` and `SystemAdmin`: authenticate and authorize in the security
      logic (reuse `authorization.ensureUserIsNotAnonymous` / `ensureSystemAdmin`), with the attribute set.
- [x] Add a `BaseEndpoints` factory for `ProjectAdmin`: authenticate and refuse anonymous or inactive users in the
      security logic; the security output is a `ProjectCandidate`, not a `User`; the attribute is set.
- [x] Add a `BaseEndpoints` factory for `ObjectAccess` (anonymous allowed, security output `User`), with the attribute
      set. `ProjectMember` gets its factory and candidate type when the first endpoint migrates to it, not in this plan.
- [x] Wrap each factory's base in `RequiresMethod`, like the existing bases in `BaseEndpoints`.
- [x] Unit tests in a new `BaseEndpointsSpec` (none exists today): each factory sets the attribute, readable from the
      completed server endpoint.
- [x] Unit tests: the `SystemAdmin` and `Authenticated` factories reject the wrong users, anonymous, and an inactive
      user before the server logic runs (server logic stubbed to fail the test if reached).
- [x] Unit tests in `AuthorizationRestServiceSpec` (`slice.api.admin.service`, outside the `api` package tree): grants
      are issued to SystemAdmin and the project's admin, refused to a member, an admin of another project,
      anonymous, and the inactive system admin fixture.
- [x] Run `just test-unit`; it passes.
- [x] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

#### Phase 10: endpoint policy ratchet and convention docs

- [x] Add `EndpointPolicyE2ESpec` in `modules/test-e2e/.../slice/api/` next to `EndpointsE2ESpec` (the only place
      `Endpoints.serverEndpoints` is enumerable with the full layer). Key each endpoint as `METHOD /path/template`,
      rendered the way `DspApiServer.scala` already does (`ep.showPathTemplate(showQueryParam = None,
      showQueryParamsAs = None)`); assert the keys are unique.
- [x] Ratchet assertion 1: every endpoint without a `Policy` attribute is listed in
      `modules/test-e2e/src/test/resources/endpoints-without-policy.txt`.
- [x] Ratchet assertion 2: no line of that file names an endpoint that has a policy or no longer exists.
- [x] Owner decision (2026-11-01): pin the baseline size with an `==` constant in `EndpointPolicyE2ESpec`, lowered in
      the same commit that removes baseline lines, so the baseline cannot grow unnoticed.
- [x] Assertion: every `Public` and `Authenticated` policy has a non-blank reason.
- [x] Assertion: every endpoint with a policy has a description containing `policy.requirement`.
- [x] Make the ratchet's failure message print the computed missing set, sorted, one key per line, and write the
      baseline file from that output; check it in.
- [x] Document the rule in `docs/development/dsp-api-conventions.md`: new endpoints use a policy-declaring factory and
      never extend the baseline.
- [x] Update the three-tier API section in `CONVENTIONS.md` to the same rule.
- [x] Add a reviewer checklist item to `REVIEW.md`: the declared policy matches what the code enforces,
      `Public`/`Authenticated` reasons are justified, and no slice re-sets `Policy.key` or calls `withInfo` (which
      replaces all attributes).
- [x] Run `bazel test //modules/test-e2e:test --test_filter='.*EndpointPolicyE2ESpec.*'`; it passes.
- [x] Run `just check`; it passes.
- [x] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

#### Phase 11: authorization lint in `just check`

- [x] Add `tools/lint/check_authz.py`, rule 1: fail on `@unused` or `@scala.annotation.unused` directly annotating a
      parameter whose type is `User` (same line), in `src/main` files, unless the file and method are listed in
      `tools/lint/unused_user_allowlist.txt`. After Phase 1, two hits remain:
      `SearchRestService.searchResourcesByLabelCountV2` and `SearchRestService.fullTextSearchCount`, the DEV-7718
      count endpoints. Allow-list both with the reason "DEV-7718"; a stale entry fails, as for rule 2.
      As committed and accepted by the owner (2026-11-01): rule 1 also matches `implicit`/`using`
      parameters and a fully qualified `User`, keys a constructor parameter by its class, requires a `# reason` on
      every allow-list line (an issue id for unused-User entries), and fails when it scans no main-source file.
- [x] The `_LINT_SRCS` filegroups glob `src/**/*.scala`, test sources included, so the script itself filters paths
      to `/src/main/`.
- [x] Rule 2: fail on the token `SystemUser` (not `isSystemUser`) in a `src/main` file that is not listed in
      `tools/lint/system_user_allowlist.txt`.
- [x] Rule 2 also fails when an allow-listed file no longer contains `SystemUser` (stale entry).
- [x] Create `tools/lint/system_user_allowlist.txt` with the current main-source files (12 after Phase 3, 10 once rule 2 skips comment
      lines at the final review), one path per line, each with a short reason comment.
- [x] Add `tools/lint/check_authz_test.py` with inline fixtures: an `@unused user: User` param, an
      `@scala.annotation.unused requestingUser: User` param, a non-allow-listed `SystemUser` reference, and a stale
      allow-list entry each fail; `isSystemUser` and `@unused x: Unit` pass.
- [x] Wire both as `py_test` targets in `tools/lint/BUILD.bazel`, reusing `_LINT_SRCS`.
- [x] Add both targets to the `bazel test` line of the `check` recipe in `justfile`.
- [x] Run `just check`; it passes on the current tree.
- [x] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

#### Phase 12: pilot migration, admin lists

- [x] Move the 5 public list reads and `getListsCanDeleteByIri` (6 public endpoints in `AdminListsEndpoints`) to the
      `Public(reason)` factory.
- [x] Move the 9 list writes (create root, create child, name, labels, comments, position, `putListsByIri`, delete,
      delete comments) to the `ProjectAdmin(...)` factory, with `projectFrom` naming the path, body or list node.
- [x] Change the `ListsResponder` write methods to take a `ProjectAdminGrant` instead of a `User`, and remove
      `ensureUserIsAdminOrProjectOwner` and its call sites (`ListsResponder.scala:752, 777, 802, 1007, 1169`, plus
      the one Phase 1 adds). The grant is obtained in `AdminListRestService`, before the responder takes its
      per-node `IriLocker` lock; the node-belongs-to-grant assertion runs inside the lock. Only create-root and
      create-child take the global list lock.
- [x] In each `ListsResponder` write that targets an existing node, assert that the node's project equals
      `grant.project` and fail with 403 otherwise. Create-root takes its project from `grant.project`.
- [x] Change the `AdminListRestService` write methods to take a `ProjectCandidate`; each resolves the project (from the
      body for create-root, from the node otherwise) and obtains the grant.
- [x] Add a responder test: a grant for project A on a node of project B is refused with 403.
- [x] Align the 15 endpoint descriptions with `policy.requirement`.
- [x] Remove the 15 entries from `endpoints-without-policy.txt`.
- [x] Update `ListsResponderSpec` and the admin list e2e specs to the new signatures.
- [x] Run `just test-unit` and `bazel test //modules/test-e2e:test --test_filter='.*AdminLists.*'`; they pass.
- [x] Run `bazel test //modules/test-it:test --test_filter='.*ListsResponderSpec.*'`; it passes.
- [x] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

#### Phase 13: pilot migration, v2 standoff

- [x] Move `postMapping` to the `ProjectAdmin("body projectIri")` factory.
- [x] Move `postCanonicalize` to `Authenticated(reason)`, so it is checked in the security logic. The reason: it reads
      no project data.
- [x] Change `StandoffResponderV2.createMappingV2` to take a `ProjectAdminGrant` and write into `grant.project`'s data
      graph, dropping the request's own project lookup.
- [x] Change `StandoffRestService.createMapping` to take a `ProjectCandidate` and obtain the grant from the body's
      `projectIri` (replacing the Phase 2 `ensure*` call).
- [x] Align both descriptions with `policy.requirement`.
- [x] Remove the two entries from `endpoints-without-policy.txt`.
- [x] Update `StandoffResponderV2Spec`.
- [x] Run `bazel test //modules/test-e2e:test --test_filter='.*(Standoff|Mapping|EndpointPolicy).*'`; it passes.
- [x] Run `bazel test //modules/test-it:test --test_filter='.*StandoffResponderV2Spec.*'`; it passes.
- [x] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

#### Phase 14: authorization matrix for the migrated endpoints

- [x] Add `AuthorizationMatrixE2ESpec` in `modules/test-e2e` with principals from `SharedTestDataADM`: anonymous,
      `normalUser`, `anythingUser1`, `incunabulaProjectAdminUser`, `anythingAdminUser`, `rootUser`. Each cell targets a
      fresh list or mapping created in the anything project through the API; add no records to shared datasets.
- [x] Hand-write the expected outcome per migrated write endpoint and principal: allowed, or denied with 401/403. This
      table is the independent oracle. Include `postCanonicalize` (`Authenticated`).
- [x] Map each `Policy` case to its allowed principals for the consistency assertion: `ProjectAdmin` to
      `anythingAdminUser` and `rootUser` (for anything targets); `Authenticated` to every principal but anonymous;
      `SystemAdmin` to `rootUser`.
- [x] For each pair, send a valid request and assert the outcome. On denial, assert by read-back that nothing changed.
- [x] Assert that the hand-written table agrees with each endpoint's declared `Policy`, so a mislabelled policy fails
      too.
- [x] Run `bazel test //modules/test-e2e:test --test_filter='.*AuthorizationMatrixE2ESpec.*'`; it passes.
- [x] Run `just test-unit`, `just test-e2e`, `just test-ingest` and `just check`; all pass.
- [x] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

## Human Actions

| Id | Action | Who | When | Why not the agent |
| --- | --- | --- | --- | --- |
| H2 | Review and accept ADR-0013 | Balduin (tech lead) | after ship | Architecture acceptance is an owner decision |
| H3 | Decide release timing and whether to hotfix DEV-7713, DEV-7714, DEV-7723 and the ingest report to stage/prod ahead of the release train; release notes list the status-code changes | Ivan | after ship | Deploy and disclosure decisions |

## Acceptance Criteria

- [x] DEV-7713: only SystemAdmin and ProjectAdmin of the list's project can delete list-node comments; others get 403
      and the comments remain.
- [x] DEV-7714: only SystemAdmin and ProjectAdmin of the named project can create a mapping; others get 401/403 and no
      mapping is written.
- [x] DEV-7712: history of a resource the caller cannot see answers 403; project history answers 200 and omits such
      resources; no 500.
- [x] Anonymous resource creation answers 403.
- [x] The ingest report endpoint refuses callers without admin scope.
- [x] DEV-7723: on a resource the caller cannot see, value deletion answers the same 403 whatever class, value and
      type the request names; on a visible resource, a value the caller cannot see answers the same 404 as a missing
      one. Resource existence (404 vs 403) stays observable, as on every v2 read route.
- [x] DEV-7721 and DEV-7722: one helper holds the custom-permission ceiling; it exempts SystemAdmin and the
      ProjectAdmin of the target project and nobody else (unit tests); no inline copy remains.
- [x] ADR-0013 exists, is in the mkdocs nav, and passes `just check`.
- [x] Every endpoint either declares a `Policy` or is in `endpoints-without-policy.txt`; adding an endpoint without one
      fails `EndpointPolicyE2ESpec`.
- [x] `just check` fails on `@unused` on a `User` parameter and on a new `SystemUser` reference, each outside its
      allow-list (proved by the lint's fixture tests), and passes on the tree with the two DEV-7718 entries listed.
- [x] All admin list and v2 standoff endpoints declare a policy; their write handlers receive a `ProjectCandidate`, not
      a `User`; their domain writes take a `ProjectAdminGrant`; `ensureUserIsAdminOrProjectOwner` is gone.
- [x] On a migrated endpoint, a handler that skips the PDP call does not compile (compile-negative test).
- [x] The authorization matrix passes for every migrated write endpoint and principal.
- [x] `just test-unit`, `just test-e2e`, `just test-ingest` and `just check` pass (locally `just test-e2e` had 1117 of
      1118 green; the one failure, `ValueHasXmlOnValueWriteE2ESpec` with 6000 standoff tags, times out on the branch
      base too; CI decides).

## Dependencies & Risks

- **One PR holds the security fixes behind the architecture review.** Phases 1-7 are self-contained commits
  at the bottom of the branch: one `fix:` per issue, plus the Phase 7 `refactor:` that the two Phase 7 fixes build
  on. If review of Phases 8-14 stalls, they can be cherry-picked into their own PR without rework.
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
    - Not filed: the custom-permission ceiling is inert wherever the default grants `CR knora-admin:Creator`, because
      `comparePermissionsADM` evaluates both literals with the caller as creator (Phase 7). Whether the ceiling should
      compare against the creator's real default is a permission-model question, not this plan's.

## Risk Analysis & Mitigation

| Risk | Likelihood | Impact | Mitigation |
| --- | --- | --- | --- |
| A fix changes behaviour a client depends on | M | M | Status changes listed in PR and release notes; H3 decides rollout |
| Policy attribute lost through `zServerSecurityLogic` | VL | H | Precedent already survives (`NegotiatesOwnAccept`); Phase 9 unit test reads it from the completed server endpoint |
| Declared project policy not enforced on an unmigrated endpoint | M | H | Ratchet tracks unmigrated endpoints; migrated ones cannot compile without the grant |
| Grant obtained for the wrong project | L | H | Domain writes take the target from the grant or assert ownership; matrix test with hand-written oracle |
| Baseline becomes a dumping ground | M | M | Stale entries fail; convention docs and REVIEW.md forbid extending it |
| SystemUser allow-list normalises elevation | M | M | Per-entry reason; new files need review; ADR states the rule |
| Fixes delayed by the architecture review | M | H | Phases 1-7 are cherry-pickable commits |

## Success Metrics

- Endpoints without a declared policy: the baseline line count at the end of Phase 10 (192 measured, all endpoints;
  193 server-logic call sites in `modules/webapi/src/main`),
  minus 17 after Phase 13 (15 admin lists, 2 v2 standoff). The PR description records both numbers.
- Authenticated-only endpoints without a role check: 3 before (list-comments delete, standoff mapping,
  canonicalize). After: 0 unreviewed. Canonicalize becomes a reviewed `Authenticated` policy.
- Main-source files referencing `SystemUser`: 13 before, 12 after Phase 3 (counted as in the Problem Statement), 10 in
  code once rule 2 skips comment lines (owner decision, 2026-11-01). It must not grow;
  the allow-list count is that number.
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
