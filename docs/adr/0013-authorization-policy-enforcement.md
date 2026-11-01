# ADR-0013: Every endpoint declares its authorization policy; project-scoped policies are enforced by a grant

Date: 2026-10-31

Relates to: DEV-7712, DEV-7713, DEV-7714, DEV-7721, DEV-7722, DEV-7723, DEV-7728 (the fixed bugs), DEV-7718,
DEV-7724, DEV-7725, DEV-7727, DEV-7730, DEV-7731 (follow-ups), `slice/common/api/Policy.scala` (the policy),
`slice/common/api/BaseEndpoints.scala` (endpoint bases), `slice/common/api/AuthorizationRestService.scala` (the role
rules), `slice/common/api/ProjectAdminGrant.scala` (candidate and grant),
[ADR-0011](0011-cross-context-access-ports-and-adapters.md) (context boundaries), the Identity & Access context
(`docs/contexts/identity-access/CONTEXT.md`). Refines the "Authorization is deliberately distributed" decision of
the dsp-api domain modularization reasoning (dasch-specs, 2026-07-30).

## Status

Proposed

## Context

dsp-api endpoints authenticate, but nothing makes them authorize. The bases in `BaseEndpoints`
(`publicEndpoint`, `securedEndpoint`, `withUserEndpoint`) hand a `User` to the server logic, and whether that user
may do what the request asks is left to each handler. The one exception is `bearerSystemAdminEndpoint`, which
decides in the tapir security logic. Everywhere else authorization is a hand-called check: an
`AuthorizationRestService.ensure*` call in a RestService, or one of about a dozen responder-level mechanisms
(`ListsResponder.ensureUserIsAdminOrProjectOwner`, `OntologyCacheHelpers.checkPermissionsForOntologyUpdate`,
`RestCardinalityService`, `ResourceUtilV2.check*Permission` and inline checks). Some of them re-implement the
project-admin rule; the ontology one omits the active-user check (not reachable over HTTP, because
authentication rejects inactive users, but a copy that drifted). The v3 API wraps the same service in its own
`V3Authorizer`.

A hand-called check can be missing, swallowed or given the wrong identity, and nothing notices. Eight bugs of
this class were fixed together in 2026-10, six of them ticketed:

- **Missing.** Deleting list-node comments (DEV-7713) and creating a standoff mapping (DEV-7714) only required a
  logged-in user. The endpoint descriptions said "Requires authentication.", so the prose described the bug. In
  DEV-7713 the `user` parameter was marked `@unused`, which silences the `-Wunused:all -Werror` error that would
  otherwise have flagged it. The ingest asset-overview report dropped its principal (`serverLogic(_ => _ => ...)`).
- **Swallowed.** The anonymous check on resource creation ended in `.ignore`.
- **Wrong identity.** Resource history (DEV-7712) and value deletion (DEV-7723) read the resource as
  `SystemUser`, which passes every role and object check, so callers learned facts about resources they cannot
  see. Two custom-permission checks passed the user IRI where the project IRI was expected (DEV-7721) or did not
  exempt system administrators (DEV-7722).

`SystemUser` passes role checks in four independent places, so reading "as SystemUser" silently bypasses object
permissions. Ten main-source files use it outside comments. No test checks authorization across endpoints: the e2e
suite enumerates every server endpoint but asserts only that it has an HTTP method.

The domain modularization reasoning decided that authorization is deliberately distributed: a pure
permission-policy primitive, permission administration and effective profiles in Identity & Access, and
enforcement by the domain that owns the protected object. The Identity & Access context records the last part:
fine-grained object-access enforcement stays with the context that owns the protected object. That decision says
where object permissions are enforced. It says nothing about who checks that an endpoint's caller may act at all,
and that gap is where the bugs above sit.

## Decision

The vocabulary is that of XACML and NIST ABAC, mapped onto the monolith in-process. A `Policy` here is an
endpoint's function-level requirement. It is neither the shared **Permission policy** (permission levels) nor a
JWT **Scope**, and a grant is a proof of one authorization decision, not an **Administrative permission**.

1. **Every endpoint declares a function-level policy.** The endpoint carries a `Policy` value as a tapir
   attribute, set by the endpoint factory that builds it, the way `NegotiatesOwnAccept.key` is set today. The
   attribute is the machine-readable statement of what the endpoint requires, and tests read it from the completed
   server endpoint. `Policy.requirement` renders the sentence the endpoint description must contain, so the prose
   cannot drift from the attribute again.

2. **The decision point (PDP) is `AuthorizationRestService`.** It is the single home of the role rules. Nothing
   new re-implements them; the responder-level copies are removed as their slices migrate.

3. **The enforcement point (PEP) depends on what the policy needs.**
    - Policies that depend only on the user (`Authenticated`, `SystemAdmin`) are enforced by the endpoint factory
      in the tapir security logic, before the body is decoded. This generalises `bearerSystemAdminEndpoint`.
    - Project-scoped policies need the decoded input to find the project, so the security logic cannot decide
      them. They are enforced in the RestService by the candidate-to-grant flow (decision 5).
    - Object-level permissions (view, modify, delete, change rights on resources and values) stay where the owning
      domain loads the object. The `ObjectAccess` policy declares this (`ObjectWrite` for the gated v2
      writes); it does not move the check. For the v2 resource and value writes, Addendum 1 gives that check one
      place in the owning domain.

4. **The `Policy` ADT.** The cases and their semantics:

    | Case | Who passes |
    | --- | --- |
    | `Public(reason)` | Everyone, without credentials. The reason is reviewed: every public route is an exposure decision. |
    | `Authenticated(reason)` | Any active, non-anonymous user. The reason is reviewed: "logged in" alone was the DEV-7713 and DEV-7714 bug class. |
    | `SystemAdmin` | Active system administrators. |
    | `ProjectAdmin(projectFrom)` | Active system administrators, and active project administrators of the project named by `projectFrom` (for example "path projectIri", "list node"). |
    | `ProjectMember(projectFrom)` | Active system administrators, project administrators and members of the project named by `projectFrom`. |
    | `ObjectAccess(enforcedIn)` | Anonymous callers included; decided per object where the owning domain loads it, and `enforcedIn` names that place. |
    | `ObjectWrite(requirement)` | Any active, non-anonymous user; the object decision is made by the gate of Addendum 1 (decision 15). |

    `ProjectAdmin` and `ProjectMember` keep the semantics of `isSystemOrProjectAdmin` and `isProjectMember`:
    a system administrator is included in both. The active-user check applies to every policy except `Public`: an
    authenticated caller must be active, and `ObjectAccess` also admits anonymous callers. The built-in
    `SystemUser` passes every role case too; decision 7 covers it.
    `ProjectMember` gets its endpoint factory and candidate type with the first endpoint that migrates to it.

5. **Project-scoped policies use a candidate and a grant.** The endpoint factory for `ProjectAdmin` authenticates
   and refuses anonymous and inactive users in the security logic, but does not hand the handler a `User`. It hands
   over a `ProjectCandidate`, which exposes nothing a handler can act on. The only way to turn it into something
   usable is to ask the PDP: `authorizeProjectAdminById(candidate, projectIri)` loads the project and returns a
   `ProjectAdminGrant`, and the domain write methods accept only that grant. Both types have constructors restricted
   to the `slice.common.api` package and its subpackages. `ProjectCandidate` is a plain class so that no `unapply`
   leaks the user; the grant's fields stay readable, because responders act on `grant.user` and `grant.project` after
   authorization. Sketch of a migrated RestService method:

    ```scala
    def deleteListNodeComments(candidate: ProjectCandidate)(iri: ListIri): Task[ListNodeCommentsDeleteResponseADM] =
      for {
        projectIri <- listsResponder.getProjectIriFromNode(iri)
        grant      <- auth.authorizeProjectAdminById(candidate, projectIri)
        result     <- listsResponder.deleteListNodeCommentsADM(iri, grant)
      } yield result
    ```

6. **The target comes from the grant.** A domain write takes its target from `grant.project` wherever the target
   is the project itself. Where the target is an object inside the project (a list node), the responder asserts
   that the object belongs to `grant.project` and fails with 403 otherwise.

7. **`SystemUser` elevation is allow-listed, and new uses need review.** `SystemUser` passes every role check,
   including `isSystemAdminOrUser` and therefore `authorizeProjectAdminById`. It cannot arrive over HTTP, because
   authentication rejects every built-in user, so the risk is in-process code that reads or writes as
   `SystemUser`. The legitimate pattern is prefetch-then-check: load as `SystemUser`, then check the caller's
   permission on what was loaded before anything reaches the response. The ten main-source files that use
   `SystemUser` outside comments are allow-listed, each with a reason. The lint stops new files, not existing uses or
   new uses inside a listed file; a file added to the list is a reviewed decision.

8. **Undeclared endpoints are tracked by a ratchet, and the bug shapes by a lint.** Neither enforces anything at
   runtime: an endpoint built without a policy-declaring factory is still served, and a declared policy is a
   label the tests compare, not a measurement of what the handler checks.
    - A test fails when an endpoint has no `Policy` attribute and is not in a checked-in baseline of endpoints
      without one, and when the baseline names an endpoint that has since gained a policy or no longer exists. The
      baseline only shrinks; new endpoints use a policy-declaring factory and never extend it. The test pins the
      baseline's size, so an appended line fails too; a commit that removes lines lowers the pinned size.
    - The same test fails when a `Public` or `Authenticated` policy has a blank reason, or when an endpoint's
      description does not contain its `policy.requirement`.
    - `just check` fails on `@unused` on a `User` parameter and on a `SystemUser` use outside comments in a
      main-source file, each outside its allow-list (`//tools/lint:check_authz`, allow-lists in `tools/lint/`).
    - An authorization matrix test sends a valid request per migrated endpoint and principal and compares the
      outcome with a hand-written table, which also must agree with the declared policies.
    - Each responder that checks an object against `grant.project` is tested with a grant for one project on an
      object of another.

9. **Migration is incremental.** Unmigrated endpoints, including those authorized through `V3Authorizer`, keep
   their hand-called checks and stay in the baseline until they move. The admin lists and v2 standoff slices
   migrate first and prove the shape; the remaining slices follow, worst first (ontologies, v2 resources and
   values, admin users; DEV-7725).

10. **This refines, and does not reverse, the decision that authorization is deliberately distributed.**
    Object-level enforcement stays in the owning domain, and administration and profiles stay in Identity &
    Access. What is added is the layer that decision left open: a declared, tested function-level policy per
    endpoint, decided in one place.

## Consequences

**What the types guarantee.** A domain write that takes a `ProjectAdminGrant` cannot be reached without one, and
the handler of a migrated endpoint holds a `ProjectCandidate`, not a `User`, so forgetting the project check
before such a write does not compile. A
compile-negative test pins that code outside `slice.common.api` can neither construct a candidate or grant nor
read the candidate's user.

**What they do not guarantee.**

- *Checking the wrong project* still compiles: the grant can be obtained for a project named in the body while
  the write targets a node in another project. Decision 6 and its responder tests cover this, not the types.
- A handler that ignores its candidate and calls no grant-taking write compiles. A `ProjectAdmin` endpoint whose
  write is not migrated yet is only as safe as its hand-called check.
- Package access stops accident, not intent. Any file that declares the `slice.common.api` package can construct a
  candidate. That is how the test-source helper `TestCandidates.of(user)` gives tests a candidate; it exists as two
  identical copies, one in the webapi and one in the test-it test sources. Main sources add no such escape hatch,
  and review watches for one.
- Unmigrated endpoints are only as safe as their hand-called checks. The ratchet makes them visible; it does not
  check them.
- The remaining bypass routes into the domain are `SystemUser` (lint allow-list and review) and `ObjectAccess`
  (the owning domain).
- The policy attribute can be overwritten: a later `.attribute(Policy.key, ...)` in a slice relabels the endpoint,
  and tapir's `withInfo` replaces all attributes. The matrix test catches a mislabelled migrated endpoint;
  elsewhere the rule lives in `REVIEW.md`.
- The lint is line-based and only knows `@unused` on a `User`. It misses a multi-line annotation, a `_` in place
  of the user and an ignored candidate; the compiler still flags an unannotated unused `user`.

**Costs.** Every endpoint chooses a factory; project-scoped writes change their signatures from `User` to a
grant, and their tests obtain grants through `AuthorizationRestService` as production does. The ratchet runs in
the e2e suite, because enumerating server endpoints needs the full layer, so it fails in CI rather than in
`just test-unit`. Moving a check into the security logic changes the order of answers: a caller without the
role gets 401 or 403 before body validation errors. Existence answers that precede the role check (a missing list
node answers 400 before 403) stay where the object is publicly readable anyway. Creating a standoff mapping is the
accepted exception to authorizing before parsing: its project comes from the body, so the handler parses the body
before it obtains the grant, and a malformed body answers 400 to any authenticated caller.

**Enforcement.** The endpoint baseline (`ratchet`: `EndpointPolicyE2ESpec` over
`modules/test-e2e/src/test/resources/endpoints-without-policy.txt`), the lint in `just check` (`static-analysis`),
the matrix and responder tests (`test`), and the `REVIEW.md` items for `Public` and `Authenticated` reasons, policy
relabelling and new `SystemUser` allow-list entries (`review`).

## Alternatives rejected

- **An external policy engine** (Cedar via `cedar-java`, OPA, Oso, OpenFGA or SpiceDB). None of them stops a
  forgotten call, which is the bug class here. The rule set is small: three roles and five object permission levels.
  Object permissions live in RDF, so an engine would need a sync layer that is itself an authorization risk, and
  Cedar would add a native library to the multi-arch images. Revisit only if policies must become data that
  non-developers edit.
- **Enforce every policy in the security logic.** Impossible for project-scoped policies: the project often comes
  from the body (DEV-7714) or from a list node, and the body is decoded after the security logic.
- **Proof types on every service method now.** The strongest guarantee, but it touches about 120 signatures at
  once. Two pilot slices prove the shape first; the rest migrate incrementally under the ratchet.

## Addendum 1: one gate for resource and value writes

Date: 2026-11-01

Status: Proposed, together with the decision above.

### Addendum context

Decision 3 left object-level permissions where the owning domain loads the object, and the `ObjectAccess` policy
only names that place. For the v2 resource and value writes that place is every handler. Each one loads, checks
and answers in its own order, and the same bug shipped four times in one month:

- Value deletion (DEV-7723) and value update (DEV-7728) read the resource as `SystemUser`. They answered the
  resource's class, whether a value exists and its type (400 or 404) before checking the caller's view
  permission.
- Value reorder (DEV-7727) reads as `SystemUser`, checks only modify permission on the resource, and lists the
  IRIs of values hidden from the caller in its 400 message.
- Value creation, value update and resource creation run `valueValidator.validate` before any permission check
  (DEV-7730). Resource metadata, authorship and deletion check the class, edit conflicts and incoming links before
  the permission.

DEV-7723 and DEV-7728 are fixed by one private helper, `findVisibleValue`. Its only guard is a comment that says
which checks must follow it. Prefetch-then-check (decision 7) is the right pattern, but nothing makes a handler
follow it, and a check placed before it compiles.

### Addendum decisions

**11.** **Resource and value writes go through one gate in the owning domain.** Resources & Values owns a gate,
`ObjectAccess.within(caller, apiRequestId, request)(write)`, in `slice.resources.access`. A write states what it
wants as an `AccessRequest[T]` case (update a value, delete a value, reorder the values of a property, create a
resource, and so on), and the case fixes the permission level. Nothing passes a level by hand. For a request on
an existing object the gate runs a fixed sequence that no caller can reorder:

1. refuse anonymous callers;
2. take the per-resource write lock, re-entrant for the same request;
3. load the resource as `SystemUser` (the prefetch of decision 7), scoped to the property a value request names,
   or the preview for a resource request;
4. answer 404 for a missing resource and 403 for a resource hidden from the caller, with one message each. Hidden
   means no permission at all, the filter reads use; a caller with restricted view sees the resource and fails at
   step 7;
5. answer the same 404 for a missing value and for a value hidden from the caller;
6. for erasing a value, answer 400 when the resource is not in the project the request names;
7. answer 403 when the caller lacks the case's level;
8. call `write` with a `Grant[T]`.

Erasing a resource is a role decision, not an object one: the gate loads the resource, answers 404 when it is
missing and otherwise turns the endpoint's `ProjectCandidate` into a `ProjectAdminGrant` for the resource's project
(decision 5), without steps 4 to 7. Resource creation has no object: the gate refuses anonymous callers and checks
the project create permission, and the lock, the new IRI, its duplicate check and the default permission ceilings
run inside `write`. The read-only "can delete" check asks the gate for the decision without a lock.

Only steps that use the request and public data run before the gate: parsing, IRI syntax, the project lookup, the
ontology lookups for the request's property and the syntax of a submitted permission literal.

Object-level enforcement still belongs to the owning domain (decision 3); it now has one place in that domain.

**12.** **A write receives a `Grant[T]`, never the unfiltered object.** `Grant` and its constructor are restricted
to `slice.resources.access`. Its target is the caller's view: the resource and, for a value request, the value,
with values hidden from the caller left out. The unfiltered resource stays inside the access package. Integrity
checks that need hidden values (cardinality, link-value reference counts, order slots) run there, only after the
level check passed, and return only `Unit`, `Boolean`, a count, a write, or an `IntegrityViolation` whose message is
fixed text plus IRIs from the request. Responses and error messages are built from the request and the grant's
target only, and a write takes the IRIs it writes from the grant, never from the request. Every check that depends
on the object (class, value type, validation, edit conflicts) takes the grant, so it cannot run before the
decision.

**13.** **Hidden values keep their place in a reorder.** A caller reorders the values they can see. The request must
name exactly those values; hidden values keep their `valueHasOrder` slots, and the visible ones are permuted
among the slots they held. A mismatch answers 400 and names only values the caller can see or the request named.

**14.** **The decision is a pure function, tested as a table.** Given the permission literals, the creators, the
project and the caller's **Permission profile**, the decision of steps 4 to 7 is computed without IO and returns a
closed outcome (permit, missing resource, hidden resource, missing value, wrong project, level denied). A generated table covers
every request case against every relevant permission literal and caller, without a triplestore, and property tests
check that a hidden value and a missing one get the same outcome.

**15.** **Write endpoints declare `Policy.ObjectWrite(requirement)`.** Each v2 resource and value write endpoint that
goes through the gate is built with a factory that declares what the gate requires: a level on a value, a
level on a resource, or the permission to create a resource in a project. The factory refuses anonymous
callers in the security logic, so the ratchet and the authorization matrix know every such endpoint, and its
description carries the requirement. `Policy.ObjectAccess` stays for endpoints whose object check is not the gate,
such as reads. The erase endpoints keep their role policies (`SystemAdmin` for values, `ProjectAdmin` of the
resource's project for resources) and still go through the gate. Loading stays in the gate: the endpoint does not
parse the body to find its target.

**16.** **Only the gate reads resources and values as the requesting `SystemUser` for writes.** The lint gains a
rule for the requesting-user handle (`KnoraSystemInstances.Users.SystemUser`) with its own allow-list. The
v2 value and resource write files leave it as they migrate. Using the built-in `SystemUser` IRI as the creator
of link values remains allowed, under the existing rule. A second rule fails a main-source file that declares
the `slice.resources.access` package outside that directory, so there is no second issuer of grants.

### Addendum designs considered

Four interface designs were drawn, each under a different constraint.

- **A: one gate with an operation-typed grant (fewest entry points).** One method, `within`, and a grant with
  two. It hides the system read, the order of the checks, the lock and the 403 or 404 choice. Answering about an
  object before the decision, using the wrong level, and validating before the permission do not compile once the
  write methods take the grant. Chosen as the core.
- **B: row-level security.** A request-scoped view of the store that only returns what the caller may see, with
  privileged integrity code that returns only counts and booleans. It is the strongest guarantee, but it moves
  validation and ontology checks into the data-access layer, and a rejected cardinality or duplicate check still
  reveals one bit. Two of its rules are adopted: unfiltered data leaves only as a count, a boolean or a write
  (decision 12), and the reorder semantics (decision 13).
- **C: load and authorize at the endpoint, as Rails `load_and_authorize_resource` does.** The endpoint declares
  its target and the framework loads and authorizes before the handler runs. Handlers become trivial, but v2
  JSON-LD bodies name their target inside the body, so the body is parsed twice. A locator can name the wrong
  object, and the proposed runtime backstop is implicit. Only the declared policy is adopted (decision 15).
- **D: a policy decision point with a policy language, as Cedar and XACML do.** The most flexible: new actions
  and object kinds need no interface change, and the rules are data. Its rules are keyed by strings, a missing
  rule fails only at runtime, and the rule set (three roles, five object levels) does not need a language. This
  ADR rejected an external engine for the same reason. Only the pure, table-tested decision is adopted
  (decision 14).

### Addendum consequences

- **What the types guarantee.** A migrated write cannot reach the object's class, values or validation without a
  grant. The grant exists only after the gate decided, and the gate's order is fixed.
- **What they do not guarantee.** A handler that skips the gate and loads the resource itself still compiles; the
  lint rule of decision 16 and review cover that. Choosing the wrong request case for an endpoint compiles; the
  hidden-resource tests per endpoint cover that. A grant kept beyond its closure outlives the lock; review covers
  that. Package access stops accident, not intent, as for the project grant; a
  lint rule pins the package to its directory.
- **Answers change order.** A caller without permission gets 401 or 403 before validation, class, conflict and
  incoming-link answers. Anonymous callers get 401 from the factory instead of 403 from the handler. A caller with
  modify permission on a resource whose property has hidden values can now reorder the visible ones; before, every
  such reorder failed.
- **Hidden resources stay distinguishable from missing ones.** OWASP's guidance and GitHub's practice answer 404 for
  an object the caller may not see, so that existence is not confirmed. The v2 API answers 403 for a hidden
  resource and 404 for a missing one on every read route, and this addendum keeps that contract for writes:
  changing it is a platform-wide decision, and resource IRIs carry random identifiers. Hidden and missing values
  already answer the same 404.
- **Integrity checks reveal one bit.** A cardinality violation can reveal that a hidden value exists, as a unique
  index does under row-level security. It is reachable only by a caller who passed the request case's level check.
- **Lock before decision.** The gate takes the write lock before it decides, as the writes do today. A caller
  without permission can wait on a busy resource and, after 15 seconds, get the lock failure (500) instead of 403.
  This is accepted: the wait reveals only that the resource exists, which 403 reveals too. The lock is per JVM, so
  concurrent writes on two instances are not serialized, as today.
- **Not covered.** File values are checked against the ingest service while the request body is parsed, before
  any handler runs, so a missing asset answers differently to a caller who would be refused (DEV-7731: make
  parsing pure and move the lookup behind the gate). Read endpoints are out of scope.
- **Costs.** Every v2 resource and value write signature changes from a `User` to a grant, and their tests
  obtain grants through the gate as production does.
