---
title: "feat: route every v2 resource and value write through one object-access gate"
type: feat
date: 2026-11-01
author: "Ivan Subotic"
status: draft
repositories: []
linear:
---

# feat: route every v2 resource and value write through one object-access gate

## Overview

Every v2 resource and value write goes through one gate in the Resources & Values domain,
`ObjectAccess.within(caller, apiRequestId, request)(write)`. The gate loads the resource as `SystemUser`, decides
visibility and the permission level in a fixed order, and only then hands the write a `Grant[T]`. Once a write
method takes the grant, these do not compile:

- answering about an object before the decision;
- checking the wrong level;
- validating before the permission.

The decision and the four designs that were weighed are recorded in
[ADR-0013, Addendum 1](../../adr/0013-authorization-policy-enforcement.md) (decisions 11 to 16), which ships in the
same PR as this plan. This plan implements that addendum: first a pilot on the values slice, then the resource writes.

Linear:

- DEV-7727 (reorder leaks hidden value IRIs) and DEV-7730 (validation before permission) close by construction.
- DEV-7723 and DEV-7728 are related; their hand-written fixes become the gate.

## Problem Statement / Motivation

The v2 write handlers each load, check and answer in their own order. Four instances of one bug shipped in a
month:

- DEV-7723 (delete) and DEV-7728 (update) answered class, existence and type before view permission.
- DEV-7727 (reorder) lists hidden value IRIs in its error.
- DEV-7730: `valueValidator.validate` runs before every permission check on value create, value update and
  resource create.

The research for this plan found more of the same shape:

- `canDeleteResource` checks class, edit conflict, incoming links and the delete date before delete permission
  (`responders/v2/ResourcesResponderV2.scala:379-420`). `markResourceAsDeletedV2` then turns every failure,
  including the permission denial, into a 400 (`:319`, `assertGoodRequestEither`, `KnoraResponseV2.scala:208-210`).
- Resource metadata and authorship updates check class and edit conflict before modify permission (`:113-301`).
- Value create checks modify but never view, so a hidden resource and a visible but unmodifiable one get the same
  answer (`responders/v2/ValuesResponderV2.scala:147-155`).
- Value erase takes no write lock and never checks that the resource belongs to the project named in the request
  (`slice/api/v2/values/ValuesRestService.scala:185-207`).

The only guard against reintroducing this is a scaladoc on `findVisibleValue`.

## Proposed Solution

The interface is shaped by ADR-0013 Addendum 1. It is a sketch, so names may settle in Phase 1, but the invariants
below may not change.

```scala
package org.knora.webapi.slice.resources.access

trait ObjectAccess:
  def within[T, R](caller: User, apiRequestId: UUID, request: AccessRequest[T])(write: Grant[T] => Task[R]): Task[R]
  // read-only decision for GET /v2/resources/candelete: no lock, no write
  def decideOnly(caller: User, request: AccessRequest[?]): UIO[Either[Throwable, Unit]]

enum AccessRequest[T]:                   // each case fixes its level rule
  // View, then Modify on the resource
  case CreateValue(resource: ResourceIri, property: PropertyIri) extends AccessRequest[ValueToCreate]
  // Modify on the value; ChangeRights when the submitted permissions differ from the current ones;
  // Modify on the resource as well when the property is a link property
  case UpdateValue(resource: ResourceIri, property: PropertyIri, value: ValueIri, submitted: Option[PermissionsLiteral])
      extends AccessRequest[ValueToUpdate]
  // ChangeRights on the value
  case ChangeValueRights(resource: ResourceIri, property: PropertyIri, value: ValueIri)
      extends AccessRequest[ValueToRescope]
  // Delete on the value
  case DeleteValue(resource: ResourceIri, property: PropertyIri, value: ValueIri) extends AccessRequest[ValueToDelete]
  // Delete on the value; the resource must be in the project named by the request
  case EraseValue(resource: ResourceIri, property: PropertyIri, value: ValueIri, project: KnoraProject, onlyHistory: Boolean)
      extends AccessRequest[ValueToErase]
  // Modify on the resource
  case ReorderValues(resource: ResourceIri, property: PropertyIri) extends AccessRequest[ValuesToReorder]
  case UpdateResource(resource: ResourceIri) extends AccessRequest[ResourceToUpdate]   // Modify
  case DeleteResource(resource: ResourceIri) extends AccessRequest[ResourceToDelete]   // Delete
  // ProjectAdmin of the resource's project, via authorizeProjectAdminById on the loaded project
  case EraseResource(resource: ResourceIri, candidate: ProjectCandidate) extends AccessRequest[ResourceToErase]
  // project create permission and the DOAP ceiling; no object, so no load and no lock in the gate
  case CreateResource(project: KnoraProject, resourceClass: ResourceClassIri, submitted: Option[PermissionsLiteral])
      extends AccessRequest[NewResource]

final class Grant[T] private[access] (
  val caller: User,
  val project: KnoraProject,
  val target: T,
  private[access] val unfiltered: Unfiltered,
):
  def verify(rule: Unfiltered => Option[IntegrityViolation]): IO[BadRequestException, Unit]

enum IntegrityViolation:   // closed; messages render from the request only, never from loaded data
  case CardinalityViolated, ValueSetMismatch(...), ...
```

### Order inside `within`, for requests on an existing object

1. `IriLocker.runWithIriLock(apiRequestId, resourceIri)`.
   - The lock is re-entrant per `apiRequestId`, so a gated call inside another gated call of the same request
     does not deadlock.
   - Reorder and value erase generate an id; they have none today.
2. Refuse anonymous callers with 403. This is defence in depth: the endpoint factory already answers 401.
3. Load as `SystemUser`:
   - value requests: property-scoped values;
   - resource requests: the preview, including deleted resources, without values (today's
     `getResourcePreviewWithDeletedResource`, now as `SystemUser`).
4. Missing resource: 404.
5. Hidden resource: 403 with the message `toResource` produces today (`ResourceMessagesV2.scala:883-886`).
   **Hidden means the caller has no permission at all**, the same filter reads use. A caller with restricted view
   permission (`RV`) sees the resource and fails at step 7 with the level message, exactly as today.
6. Missing or hidden value: the same 404.
7. The level rule of the request case: 403.
8. `write(grant)`.

`CreateResource` has no object, so the gate runs only:

1. refuse anonymous callers;
2. check the project create permission;
3. check the DOAP ceiling (`PermissionUtilADM.ensureWithinDefaultPermissions`).

IRI generation and the duplicate-IRI check (409), and the lock on the new IRI, run inside `write`.

### What may run before the gate

Only steps that use the request and public data:

- JSON-LD parsing and IRI syntax;
- the project lookup by IRI or shortcode (projects are public);
- ontology lookups for the request's property: property not found 404, link-property 400,
  `hasStandoffLinkToValue` 400. These also decide the adjusted link property that the property-scoped load needs.

Two exceptions are outside this plan's scope:

- the dsp-ingest asset check during parsing (`ValueMessagesV2.scala:710-729`);
- permission-literal syntax validation (`validatePermissions`), which checks group IRIs against the admin data.

The ADR records the first under Consequences; it is follow-up DEV-7731. The second reveals no object data.

### What runs inside `write`, in this order

1. Request validation against `grant.target`: `valueValidator`, class and value-type checks, object-class and
   list-node checks, link-target and standoff-target visibility checks (`checkResourceIris`,
   `checkStandoffLinkTargets`).
2. `verify` for the integrity checks that need hidden values.
3. The repository write.

### Levels and the decision

- The decision of steps 4 to 7 is a pure function, `AccessDecision.decide`. It works over:
    - the loaded permission literals of the resource and value, and their creators;
    - the project;
    - the submitted permissions;
    - whether the property is a link property;
    - the caller's `User`.
- It is tested as a table.
- For `UpdateValue` it picks Modify or ChangeRights from the submitted permissions. It adds Modify on the resource
  for link properties, which replaces the hand-written check at `ValuesResponderV2.scala:~685`.

### Endpoints

- Gated endpoints declare `Policy.ObjectWrite(requirement)`. The requirement is one of `ObjectWriteRequirement`'s
  cases: `OnValue(level)`, `OnResource(level)` or `CreateResource`.
- They are built by the new factory `objectWritePolicyEndpoint(requirement)`. It refuses anonymous callers with 401
  and inactive users, and hands the handler a `User`; the gate is the enforcement point.
- The erase endpoints use `systemAdminPolicyEndpoint` (values) and `projectAdminPolicyEndpoint("the resource's
  project")` (resources).

### Status-code changes (for release notes)

| Endpoint | Caller | Before | After |
| --- | --- | --- | --- |
| all migrated v2 value and resource writes, `GET /v2/resources/candelete` | no credentials | 403 "Anonymous users aren't allowed..." (candelete: 200 `canDo: false`) | 401 |
| `POST /v2/values` | resource hidden from the caller | 403 (no Modify) | 403 "You do not have permission to see resource" |
| `POST /v2/values`, `PUT /v2/values`, `POST /v2/resources` | caller without the required permission, invalid body content (legal info, cross-project link) | 400 | 403 |
| `PUT /v2/values/order` | modify on the resource, some values hidden | 400 listing the hidden IRIs | 200; visible values permuted, hidden values keep their slots; `valuesReordered` counts visible values |
| `PUT /v2/values/order` | resource hidden from the caller | 403 (no Modify) | 403 "You do not have permission to see resource" |
| `PUT /v2/resources`, `PUT /v2/resources/authorship` | visible, no modify permission, wrong class or stale date | 400 / 409 | 403 |
| `POST /v2/resources/delete` | visible, no delete permission, clean request | 400 (the 403 is wrapped by `assertGoodRequestEither`) | 403 |
| `POST /v2/resources/delete` | visible, no delete permission, resource in use, wrong class or bad delete date | 400 | 403 |
| `GET /v2/resources/candelete` | any denial | 200 `canDo: false` with the first failing check's reason | 200 `canDo: false` with the gate's reason (hidden, missing or no Delete) before class, conflict and in-use reasons |
| `POST /v2/resources/erase` | not an admin of the resource's project, resource hidden | 403 "see resource" | 403 from the role check (the role check needs no visibility) |
| `POST /v2/values/erase`, `/erasehistory` | no credentials | 403 | 401 |
| `POST /v2/values/erase`, `/erasehistory` | shortcode names a project the resource is not in | erases in the named project's graph (no effect or wrong graph) | 400 |

Unchanged on purpose:

- ontology answers for a nonexistent or link property (404 / 400) still come before 403;
- a deleted resource answers as today for update, delete and erase, pinned by tests.

## Technical Considerations

- **Package and scoping.**
    - The gate lives in `slice/resources/access/` (webapi-resources). `Grant` and `Unfiltered` constructors are
    `private[access]`.
    - `ProjectCandidate` lives in `slice.common.api` with a `private[api]` constructor, so `EraseResource` carries
    the candidate. The gate calls the public `AuthorizationRestService.authorizeProjectAdminById(candidate,
    resource.projectIri)` after the load, as ADR-0013 decision 5 does in a RestService.
- **Loads.**
    - `ValuesResponderV2.getResourceWithPropertyValues` (`:1530-1571`, the property-scoped Gravsearch load) moves
    into the access package in Phase 1, and every value request uses it. Delete, erase and reorder stop loading
    every value of the resource.
    - Resource requests load the preview with deleted resources, without values.
    - The load must use the same "non-deleted" definition as the reorder slot query.
- **Hidden.** Steps 5 and 6 reproduce the read filter (no permission at all), not `checkResourcePermission`'s
  `>= View`. RV-only callers see the object and fail on the level. `AccessDecisionSpec` pins both.
- **Integrity checks that need hidden data** move behind `verify`, or into access-package helpers that return only
  `Unit`, `Boolean`, a count or a write:
    - value cardinality on create (`ValuesResponderV2.scala:206-231`) and on delete and erase (`:1248-1260`, with
    `onlyHistory`);
    - link-value reference counts (`incrementLinkValue` / `decrementLinkValue`, `:1792-1930`);
    - reorder slots.
    - `ValueRepo.checkDuplicateOrder` (`ValueRepo.scala:178-189`) names the resource and property from the request,
    which is fine.
- **Reorder slots.**
    - `valueHasOrder` is not on `ReadValueV2`, so a gate-owned `sparql"..."` SELECT reads `(value, order)` for
    every non-deleted value of the property.
    - The visible set's old slots are sorted as a multiset and reused as is, duplicates and gaps included, and
    assigned to the requested order.
    - Visible values without `valueHasOrder` (file values; learnings `value-has-order-optional-cardinality.md`) get
    fresh slots after the maximum over **all** non-deleted values, hidden ones included.
    - Hidden values are not written.
    - The write keeps `ValueQueries.reorderValues`' `OPTIONAL ?oldOrder` clause and takes an explicit
    `(value, slot)` list instead of the list index.
    - A request naming a hidden, deleted, wrong-property or nonexistent value answers one identical 400, naming no
    loaded IRI.
    - The lock is per JVM (`ValueRepo.scala:152-154`). A multi-instance race stays as it is today.
- **Lock before decision.** The gate locks before it decides, as today's writes do. A caller without permission
  can wait on a busy resource's lock, or get `ApplicationLockException` instead of 403. This is accepted and
  documented in the ADR.
- **Errors.** 403 and 404 go through `BaseEndpoints.errorOutputs`, which sends the exception message verbatim
  (learnings `shared-tapir-error-envelope-leaks-exception-messages.md`). Every message the gate builds uses only
  request IRIs.
- **Lint.**
    - `check_authz.py` gains rule 3: the token `KnoraSystemInstances.Users.SystemUser` (the requesting-user handle)
    in main sources, with its own allow-list.
    - Rule 2 (any `SystemUser`) keeps covering the creator-IRI uses (`KnoraUserRepo.builtIn.SystemUser.id`) in
    `ValuesResponderV2` and `CreateResourceV2Handler`.
- **Test data.** New tests create their own fixtures through the API, as `createThingWithInteger` does
  (`ValuesEndpointsE2ESpec.scala`), and never add to `anything-data.ttl` (CLAUDE.md testing guidelines; learnings
  `test-fixture-isolation-shared-data-scope.md`). Reorder tests use about ten non-alphabetical values (learnings
  `alphabetical-test-data-masks-ordering.md`).
- **Direct callers of changing signatures.** `ValuesEraseSpec` and `ValueHasXmlLifecycleSpec` (test-it) call the
  responders directly and move to grants with them. `BulkImportParityE2ESpec` (the v3 import path) must stay
  green.
- **Conventions.** `CONVENTIONS.md`, `docs/development/dsp-api-conventions.md` and
  `docs/development/dsp-api-iri-handling.md`: no `unsafeFrom` in responders, and new SPARQL uses the `sparql"..."`
  interpolator.

## Implementation Phases

<!-- markdownlint-disable-next-line MD001 -->
#### Phase 1: The gate, its decision table and the write policy

- [ ] Create `slice/resources/access/` with `ObjectAccess`, `ObjectAccessLive`, `AccessRequest`, `Grant`, `Unfiltered` and `IntegrityViolation`, with the constructors restricted to `private[access]`
- [ ] Move `getResourceWithPropertyValues` from `ValuesResponderV2` into the access package as the gate's value-request loader; `ValuesResponderV2` calls it from there until its operations migrate
- [ ] Implement `AccessDecision.decide` as a pure function over the inputs listed under "Levels and the decision", returning permit, 403 or 404 with the exact message
- [ ] Implement `ObjectAccessLive.within` with the fixed order for object requests and for `CreateResource`, and `decideOnly` without lock or write
- [ ] Register `ObjectAccessLive.layer` in `core/LayersLive.scala`
- [ ] Add `AccessDecisionSpec` (webapi unit test). Rows cover:
    - every request case;
    - permission literals for Creator, ProjectMember, KnownUser and UnknownUser, an `RV`-only literal and an empty literal;
    - a creator, a project member, a ProjectAdmin, a SystemAdmin and an anonymous user;
    - a link property;
    - submitted permissions equal to and different from the current ones.
- [ ] Add a compile-negative spec, outside the access package, showing that `Grant` and `Unfiltered` cannot be constructed or read. Assert stable fragments only (`cannot be accessed`), as `ProjectAdminGrantAccessSpec` now does
- [ ] Add `Policy.ObjectWrite(requirement: ObjectWriteRequirement)` with its `requirement` sentence per case in `slice/common/api/Policy.scala`
- [ ] Add `objectWritePolicyEndpoint(requirement)` to `BaseEndpoints`; it refuses anonymous callers with 401 and inactive users
- [ ] Cover the new factory in `BaseEndpointsSpec`: no credentials 401, invalid token 401, a valid user passes, the attribute is set
- [ ] Map `Policy.ObjectWrite` to `None` in `AuthorizationMatrixE2ESpec.allowedBy`; object-level outcomes are covered by per-endpoint hidden-object tests
- [ ] Add rule 3 to `tools/lint/check_authz.py`: `KnoraSystemInstances.Users.SystemUser` in main sources outside `tools/lint/elevated_read_allowlist.txt`, comment lines skipped, stale entries failing
- [ ] Seed `elevated_read_allowlist.txt` with today's files, each with a reason, plus the access package
- [ ] Add rule 3 fixture tests to `tools/lint/check_authz_test.py`: red against an unlisted use, green with the entry, red on a stale entry
- [ ] Wire the new allow-list into `tools/lint/BUILD.bazel`
- [ ] Run `just test-unit`; it passes
- [ ] Run `just check`; it passes
- [ ] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

#### Phase 2: Value delete and erase through the gate

- [ ] Move `deleteValueV2` and `canRemoveValue` onto `within(DeleteValue)`. Class, type and cardinality checks take the grant; cardinality uses `verify`
- [ ] Move `eraseValue` and `eraseValueHistory` onto `within(EraseValue)`, carrying `onlyHistory`. The gate takes the write lock erase lacked, generates an `apiRequestId`, and answers 400 when the resource is not in the project named by the request's shortcode
- [ ] Remove `findDeletableValue`; its load and checks are now the gate's
- [ ] Rebuild `deleteValues` with `objectWritePolicyEndpoint(OnValue(Delete))`
- [ ] Rebuild `postValuesErase` and `postValuesErasehistory` with `systemAdminPolicyEndpoint`; descriptions contain the requirement sentence
- [ ] Remove `POST /v2/values/delete`, `POST /v2/values/erase` and `POST /v2/values/erasehistory` from `endpoints-without-policy.txt`, and lower `baselineSize` to 172 in the same commit
- [ ] Keep the DEV-7723 suite in `ValuesEndpointsE2ESpec` green, except its anonymous case, which now expects 401
- [ ] Add e2e tests for delete:
    - a view-only caller gets 403 before the class and type checks;
    - the creator deletes;
    - a ProjectAdmin deletes.
- [ ] Add e2e tests for erase: no credentials 401, non-SystemAdmin 403, SystemAdmin erases, and a shortcode that does not match the resource's project answers 400 and erases nothing
- [ ] Run `bazel test //modules/test-e2e:test --test_filter=*ValuesEndpointsE2ESpec*`; it passes
- [ ] Run `bazel test //modules/test-it:test --test_filter=*ValuesEraseSpec*`; it passes
- [ ] Run `bazel test //modules/test-it:test --test_filter=*ValuesResponderV2Spec*`; it passes
- [ ] Run `just check`; it passes
- [ ] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

#### Phase 3: Value update through the gate

- [ ] Move content updates in `updateValueV2` onto `within(UpdateValue)`, with the submitted permissions in the request so that the gate picks Modify or ChangeRights
- [ ] Move permissions-only updates onto `within(ChangeValueRights)`
- [ ] Move `valueValidator.validate`, the object-class check, the list-node check and the standoff and link target checks after the grant (DEV-7730, update)
- [ ] Remove the hand-written link-update Modify-on-resource check; the gate's level rule covers it
- [ ] Remove `findVisibleValue` and `checkValueAndRetrieveResourceProperties`
- [ ] Rebuild `putValues` with `objectWritePolicyEndpoint(OnValue(Modify))`; remove `PUT /v2/values` from the baseline and lower `baselineSize` to 171
- [ ] Keep the DEV-7728 suite green, except its anonymous case, which now expects 401
- [ ] Add e2e tests, each denial red before its change:
    - a Modify-only caller who submits different permissions gets 403;
    - a caller with ChangeRights changes them;
    - the creator updates;
    - a caller without modify permission who sends a disallowed license gets 403, not 400;
    - a link update by a caller without Modify on the resource gets 403.
- [ ] Run `bazel test //modules/test-e2e:test --test_filter=*ValuesEndpointsE2ESpec*`; it passes
- [ ] Run `bazel test //modules/test-it:test --test_filter=*ValuesResponderV2Spec*`; it passes
- [ ] Run `bazel test //modules/test-it:test --test_filter=*ValueHasXmlLifecycleSpec*`; it passes
- [ ] Run `just check`; it passes
- [ ] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

#### Phase 4: Value creation through the gate

- [ ] Move `createValueV2` onto `within(CreateValue)`, adding the view check before modify on the resource
- [ ] Move `valueValidator.validate`, the rdf:type check, the object-class check, the list-node check and the standoff link target checks after the grant (DEV-7730, create)
- [ ] Move the create cardinality check (`ValuesResponderV2.scala:206-231`) into `verify`
- [ ] Move link-value reference counting into access-package helpers that return only writes
- [ ] Remove the elevated read at `ValuesResponderV2.scala:~151`, and remove `ValuesResponderV2.scala` from `elevated_read_allowlist.txt`. Its rule 2 entry stays for the link-value creator, with the reason updated
- [ ] Rebuild `postValues` with `objectWritePolicyEndpoint(OnResource(Modify))`; remove `POST /v2/values` from the baseline and lower `baselineSize` to 170
- [ ] Add e2e tests in a new suite `POST /v2/values checks permissions before revealing details`, each denial red before its change:
    - a hidden resource gives the same 403 for a valid body, a wrong class and an invalid value;
    - an RV-only caller gets 403 with the Modify message;
    - no credentials gives 401;
    - a caller without modify permission and with a disallowed license gets 403;
    - a permitted caller still gets 400 for a cardinality violation.
- [ ] Run `bazel test //modules/test-e2e:test --test_filter=*ValuesEndpointsE2ESpec*`; it passes
- [ ] Run `bazel test //modules/test-it:test --test_filter=*ValuesResponderV2Spec*`; it passes
- [ ] Run `bazel test //modules/test-it:test --test_filter=*ValueHasXmlLifecycleSpec*`; it passes
- [ ] Run `just check`; it passes
- [ ] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

#### Phase 5: Value reorder through the gate, with slot-preserving semantics

- [ ] Add the gate-owned `sparql"..."` SELECT for `(value, valueHasOrder)` over the non-deleted values of one property, with the same "non-deleted" definition as the gate's load
- [ ] Implement slot assignment as described under Technical Considerations, Reorder slots
- [ ] Change `ValueQueries.reorderValues` to write an explicit `(value, slot)` list for visible values, keeping the `OPTIONAL ?oldOrder` clause
- [ ] Move `ValuesRestService.reorderValues` onto `within(ReorderValues)`, generating an `apiRequestId` and taking the lock it lacked
- [ ] Rework `verifyCanonicalValues` to compare against `grant.target`. A hidden, deleted, wrong-property or nonexistent IRI in the request answers one identical 400 naming no loaded IRI (DEV-7727)
- [ ] Remove `ValuesRestService.scala` from both SystemUser allow-lists in the same commit
- [ ] Rebuild `putValuesOrder` with `objectWritePolicyEndpoint(OnResource(Modify))`; remove `PUT /v2/values/order` from the baseline and lower `baselineSize` to 169
- [ ] Add e2e tests on a fresh resource with about ten non-alphabetical values, some hidden from the caller:
    - reordering the visible ones succeeds;
    - hidden values keep their `valueHasOrder` and are not renumbered;
    - `valuesReordered` counts the visible values;
    - the response bodies for a hidden IRI and a nonexistent IRI are equal;
    - a visible value without `valueHasOrder` gets a slot after the maximum;
    - a caller who sees no value of the property gets the 400 without loaded IRIs.
- [ ] Keep the existing reorder suite (`ValuesEndpointsE2ESpec.scala:~4310-4700`) green
- [ ] Run `bazel test //modules/test-e2e:test --test_filter=*ValuesEndpointsE2ESpec*`; it passes
- [ ] Run `just check`; it passes
- [ ] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

#### Phase 6: Resource metadata, authorship, deletion and erase through the gate

- [ ] Move `updateResourceMetadataV2` and `updateResourceAuthorshipV2` onto `within(UpdateResource)`, so the class and edit-conflict checks run after modify permission
- [ ] Move `markResourceAsDeletedV2` onto `within(DeleteResource)`, so the class, conflict, in-use and delete-date checks run after delete permission
- [ ] Stop `markResourceAsDeletedV2` wrapping failures into 400 through `assertGoodRequestEither`: a denial answers 403 and the other checks keep their own status
- [ ] Move `canDeleteResource` onto `decideOnly(DeleteResource)` followed by the class, conflict, in-use and date checks, with no lock. Map every failure to a 200 `CanDoResponseV2` with `canDo: false`, as today
- [ ] Move `eraseResourceV2` onto `within(EraseResource)`; the gate obtains a `ProjectAdminGrant` for the loaded resource's project
- [ ] Rebuild these endpoints with `objectWritePolicyEndpoint`: `putResources` and `putResourcesAuthorship` (`OnResource(Modify)`), `postResourcesDelete` and `getResourcesCanDelete` (`OnResource(Delete)`)
- [ ] Rebuild `postResourcesErase` with `projectAdminPolicyEndpoint("the resource's project")`
- [ ] Remove those five endpoints from the baseline and lower `baselineSize` to 164
- [ ] Add matrix rows to `AuthorizationMatrixE2ESpec` for `POST /v2/resources/erase`, using a fresh resource per cell
- [ ] Add e2e hidden-object tests per migrated endpoint in `ResourcesRouteV2E2ESpec`:
    - hidden resource 403 with one message;
    - RV-only 403 with the level message;
    - visible without permission 403 before 400 and 409;
    - missing 404;
    - no credentials 401;
    - the permitted caller unchanged.
    Each denial case must be red before its change.
- [ ] Add e2e tests: delete without permission on a clean request gets 403 (red before: 400); candelete answers 200 `canDo: false` for hidden, missing and no-delete callers, and 401 without credentials
- [ ] Add e2e tests pinning today's outcomes for update, delete and erase on an already deleted resource
- [ ] Run `bazel test //modules/test-e2e:test --test_filter=*ResourcesRouteV2E2ESpec*`; it passes
- [ ] Run `bazel test //modules/test-e2e:test --test_filter=*AuthorizationMatrixE2ESpec*`; it passes
- [ ] Run `bazel test //modules/test-it:test --test_filter=*ResourcesResponderV2Spec*`; it passes
- [ ] Run `just check`; it passes
- [ ] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

#### Phase 7: Resource creation through the gate, and documentation

- [ ] Move `CreateResourceV2Handler` onto `within(CreateResource)`:
    1. the gate refuses anonymous callers and checks the project create permission and the DOAP ceiling;
    2. then `write` runs `valueValidator`, `ensureClassBelongsToProjectOntology`, the standoff link target checks and `checkOrCreateEntityIri` (409);
    3. then it takes the lock on the new IRI.
    This closes DEV-7730 for resource create.
- [ ] Rebuild `postResources` with `objectWritePolicyEndpoint(CreateResource)`; remove `POST /v2/resources` from the baseline and lower `baselineSize` to 163
- [ ] Add e2e tests:
    - a user without create permission who sends a disallowed license gets 403, not 400;
    - a user without create permission who sends an existing custom IRI gets 403, not 409;
    - no credentials gives 401;
    - a permitted caller is unchanged.
- [ ] Update `docs/development/dsp-api-conventions.md`: the factory table gains `objectWritePolicyEndpoint`, and a section says v2 resource and value writes go through `ObjectAccess.within`
- [ ] Update `CONVENTIONS.md` with the same rule
- [ ] Add a `REVIEW.md` item: a resource or value write that loads the resource itself, or answers from loaded data outside a grant, is a finding
- [ ] Update `docs/contexts/resources-values/CONTEXT.md` with the **Grant** and **Access request** terms
- [ ] Update the endpoint docs: in `docs/03-endpoints/api-v2/editing-values.md` and `editing-resources.md`, add the new permission lines, the 401, and the reorder semantics
- [ ] Run `dune:dune-map` update for webapi-resources, webapi-common and webapi-api, which removes the "There is no single chokepoint" note from `ARCH-MAP.md`
- [ ] Add a "Not yet in code" line to ADR-0013 Addendum 1 if any part did not land; otherwise state that it is complete
- [ ] Move DEV-7727 and DEV-7730 to In Review
- [ ] Run `bazel test //modules/test-e2e:test --test_filter=*BulkImportParityE2ESpec*`; it passes
- [ ] Run `just test-unit`; it passes
- [ ] Run `just test-it`; it passes
- [ ] Run `just test-e2e`; it passes, except the known local timeout of `ValueHasXmlOnValueWriteE2ESpec` "a document of 6000 standoff tags"
- [ ] Run `just check`; it passes
- [ ] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

## Human Actions

| Id | Action | Who | When | Why not the agent |
| --- | --- | --- | --- | --- |
| H1 | Merge PR #4410, retarget this plan's PR to `main`, and accept ADR-0013 with Addendum 1 | tech lead | before start | Accepting an ADR and the merge order are owner decisions |
| H2 | Move DEV-7727 and DEV-7730 to In Progress when execution starts | Ivan | before start | Starting the work is the owner's call |
| H3 | Decide the release, and put the status-code table above in the release notes | Ivan | after ship | Release timing is an owner decision |

## Acceptance Criteria

- [ ] Every v2 value write runs through `ObjectAccess.within`: create, update, permission change, delete, erase, erase history and reorder
- [ ] Every v2 resource write runs through `ObjectAccess.within`: create, metadata, authorship, delete and erase
- [ ] `candelete` runs through `decideOnly`, takes no lock and answers 200 for denied cases
- [ ] No migrated responder or RestService method takes a `User` and reads the resource itself; they take a `Grant[T]`
- [ ] `ValuesRestService.scala` is on neither SystemUser allow-list
- [ ] `ValuesResponderV2.scala` is not on `elevated_read_allowlist.txt`
- [ ] Value requests load property-scoped values, and resource requests load the preview without values
- [ ] Endpoint baseline: the 12 endpoints are removed and `baselineSize` is 163
- [ ] `AccessDecisionSpec` covers every `AccessRequest` case, an RV-only caller and a link property
- [ ] The compile-negative spec is red when a constructor becomes public
- [ ] Each new denial e2e test was red before its change
- [ ] Response bodies for hidden and nonexistent IRIs are equal wherever the gate answers 404 or 400
- [ ] A reorder by a caller with hidden values succeeds, and the hidden values keep their `valueHasOrder`
- [ ] Update, delete and erase on a deleted resource keep today's outcomes
- [ ] `ValuesEraseSpec`, `ValueHasXmlLifecycleSpec` and `BulkImportParityE2ESpec` pass
- [ ] `just test-unit`, `just test-it` and `just check` pass
- [ ] `just test-e2e` passes, apart from the known local `ValueHasXmlOnValueWriteE2ESpec` timeout

## Dependencies & Risks

- **Depends on PR #4410** (ADR-0013, `Policy`, the factories, the ratchet, the lint). This plan's PR is stacked on
  it.
- **The `ValuesResponderV2` rewrite is large** (about 2000 lines). The phases follow the operations, so each phase
  is one reviewable change. The existing value e2e suites (more than 100 tests) are the regression net.
  `canRemoveValue` is shared by delete and erase, so both move in Phase 2.
- **Behaviour changes for clients:**
    - 401 instead of 403 for anonymous callers;
    - 403 before 400 and 409;
    - `POST /v2/resources/delete` answers 403 instead of 400 for a denial;
    - reorder no longer fails because of hidden values.
  The status-code table goes in the release notes (H3).
- **Resource writes load as `SystemUser` instead of as the caller.** The 403 and 404 messages must not change;
  the table spec and the e2e tests pin them.
- **A grant escaping its closure, or `Unfiltered` data piped into a response,** is a review item, not a type error
  (ADR-0013 Addendum 1, Consequences).

## Success Metrics

- **SystemUser allow-lists:**
    - Today: 10 files on rule 2.
    - After the plan: `ValuesRestService.scala` is off both lists.
    - The new elevated-read list holds only the access package, `ReadResourcesServiceLive` and files unrelated to
      resource and value writes.
- **Endpoint baseline:** 175 today; 163 after the plan.
- **Hidden-object e2e coverage:** today only delete and update have it; after the plan, every migrated write
  endpoint does.

## References

- [ADR-0013 and Addendum 1](../../adr/0013-authorization-policy-enforcement.md)
- [ADR-0011](../../adr/0011-cross-context-access-ports-and-adapters.md)
- Gate seed: `modules/webapi/src/main/scala/org/knora/webapi/responders/v2/ValuesResponderV2.scala`
    - `findVisibleValue` (`:1132`) and `findDeletableValue` (`:1154`)
    - create (`:89`), update (`:554`), delete (`:1269`), erase (`:1075`)
    - `getResourceWithPropertyValues` (`:1530`)
- Resource writes: `responders/v2/ResourcesResponderV2.scala:113-491`
- Resource create: `responders/v2/resources/CreateResourceV2Handler.scala:78-98`
- Reorder:
    - `slice/api/v2/values/ValuesRestService.scala:102-177`
    - `slice/resources/repo/service/ValueQueries.scala:147-182`
- Permission evaluation:
    - `responders/v2/ResourceUtilV2.scala:37-92`
    - `messages/util/PermissionUtilADM.scala:90-142`
- Hidden and missing answers: `messages/v2/responder/resourcemessages/ResourceMessagesV2.scala:883-886`
- Ratchet and matrix:
    - `modules/test-e2e/src/test/scala/org/knora/webapi/slice/api/EndpointPolicyE2ESpec.scala`
    - `modules/test-e2e/src/test/scala/org/knora/webapi/slice/api/AuthorizationMatrixE2ESpec.scala`
- Lint: `tools/lint/check_authz.py` and `tools/lint/system_user_allowlist.txt`
- Learnings (dasch-specs):
    - `learnings/logic-errors/value-has-order-optional-cardinality.md`
    - `learnings/test-failures/alphabetical-test-data-masks-ordering.md`
    - `learnings/best-practices/test-fixture-isolation-shared-data-scope.md`
    - `learnings/best-practices/shared-tapir-error-envelope-leaks-exception-messages.md`
- Linear: DEV-7727, DEV-7730 (closed by this plan); DEV-7723, DEV-7728 (related); DEV-7731 (follow-up)
