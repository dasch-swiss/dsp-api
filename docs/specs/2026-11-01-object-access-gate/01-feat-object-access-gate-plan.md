---
title: "feat: route every v2 resource and value write through one object-access gate"
type: feat
date: 2026-11-01
author: "Ivan Subotic"
status: implemented
repositories: []
linear:
---

# feat: route every v2 resource and value write through one object-access gate

## Overview

Every v2 resource and value write goes through one gate in the Resources & Values domain,
`ObjectAccess.within(caller, apiRequestId, request)(write)`. The gate loads the resource as `SystemUser`, decides
visibility and the permission level in a fixed order, and only then hands the write a `Grant[T]`. Once a write
method takes the grant, these do not compile for data that reaches the write through the grant:

- answering about an object before the decision;
- checking the wrong level;
- validating before the permission.

The decision and the four designs that were weighed are recorded in
[ADR-0013, Addendum 1](../../adr/0013-authorization-policy-enforcement.md) (decisions 11 to 16), which ships in the
same PR as this plan. This plan implements that addendum: first a pilot on the values slice, then the resource writes.

Linear:

- DEV-7727 (reorder leaks hidden value IRIs) and DEV-7730 (validation before permission) close by construction.
- DEV-7723 and DEV-7728 are related; their hand-written fixes become the gate.
- DEV-7731 (the dsp-ingest check during parsing) is a follow-up, not part of this plan.

## Problem Statement / Motivation

The v2 write handlers each load, check and answer in their own order. Four instances of one bug shipped in a
month:

- DEV-7723 (delete) and DEV-7728 (update) answered class, existence and type before view permission.
- DEV-7727 (reorder) lists hidden value IRIs in its error (`slice/api/v2/values/ValuesRestService.scala:157-180`).
- DEV-7730: `valueValidator.validate` runs before every permission check on value create
  (`responders/v2/ValuesResponderV2.scala:96`), value update (`:611`) and resource create
  (`responders/v2/resources/CreateResourceV2Handler.scala:83`, before `ensureUserHasPermission` at `:87`).

The research for this plan found more of the same shape:

- `canDeleteResource` checks class, edit conflict, incoming links and the delete date before delete permission
  (`responders/v2/ResourcesResponderV2.scala:395-436`, permission at `:430`). `markResourceAsDeletedV2` then turns
  every failure, including the permission denial, into a 400 (`:335`, `assertGoodRequestEither`,
  `KnoraResponseV2.scala:208-210`).
- Resource metadata and authorship updates check class and edit conflict before modify permission (`:133-146`,
  `:252-262`).
- Value create checks modify but never view, so a hidden resource and a visible but unmodifiable one get the same
  answer (`ValuesResponderV2.scala:155`).
- Value erase takes no write lock and never checks that the resource belongs to the project named by the request's
  shortcode, whose graph it writes to (`ValuesRestService.scala:188-208`, `ValuesResponderV2.scala:1074-1092`,
  `ValueRepo.scala:103-112`).
- Value reorder takes no write lock.

The only guard against reintroducing this is a scaladoc on `findVisibleValue`.

Already in place on main, and not part of this plan: every v2 value and resource write endpoint declares
`Policy.ObjectAccess(enforcedIn, admitsAnonymous = false)` through `authenticatedObjectAccessPolicyEndpoint`, which
answers 401 to anonymous callers; the erase endpoints declare `SystemAdmin` (values) and `ProjectAdmin` (resources);
the authorization matrix has rows for all three erase endpoints.

## Proposed Solution

The interface is shaped by ADR-0013 Addendum 1. It is a sketch, so names may settle in Phase 1, but the invariants
below may not change.

```scala
package org.knora.webapi.slice.resources.access

final class ObjectAccess(/* readResources, ontology, projects, ... */):
  // object and create requests; the caller is a User
  def within[T, R](caller: User, apiRequestId: UUID, request: AccessRequest[T])(write: Grant[T] => Task[R]): Task[R]
  // erase a resource: a role decision, already made by the RestService
  def withinAsProjectAdmin[R](grant: ProjectAdminGrant, apiRequestId: UUID, resource: ResourceIri)(
    write: Grant[ResourceToErase] => Task[R],
  ): Task[R]
  // read-only decision for GET /v2/resources/candelete: no lock, no write, no grant
  def decideOnly(caller: User, request: AccessRequest[?]): Task[Unit]
object ObjectAccess:
  val layer = ZLayer.derive[ObjectAccess]

sealed trait AccessRequest[T]
object AccessRequest:
  /** View, then Modify on the resource. */
  final case class CreateValue(resource: ResourceIri, property: PropertyIri) extends AccessRequest[ValueToCreate]
  /** Modify on the value; ChangeRights when `permissions` changes them; Modify on the resource too for a link. */
  final case class UpdateValue(resource: ResourceIri, property: PropertyIri, value: ValueIri, permissions: PermissionsChange)
      extends AccessRequest[ValueToUpdate]
  /** ChangeRights on the value. */
  final case class ChangeValueRights(resource: ResourceIri, property: PropertyIri, value: ValueIri)
      extends AccessRequest[ValueToRescope]
  /** Delete on the value. */
  final case class DeleteValue(resource: ResourceIri, property: PropertyIri, value: ValueIri)
      extends AccessRequest[ValueToDelete]
  /** Delete on the value; the resource must be in `project` (the request's shortcode). */
  final case class EraseValue(resource: ResourceIri, property: PropertyIri, value: ValueIri, project: KnoraProject, onlyHistory: Boolean)
      extends AccessRequest[ValueToErase]
  /** Modify on the resource. */
  final case class ReorderValues(resource: ResourceIri, property: PropertyIri) extends AccessRequest[ValuesToReorder]
  /** Modify on the resource; ChangeRights when `permissions` changes them. */
  final case class UpdateResource(resource: ResourceIri, permissions: PermissionsChange)
      extends AccessRequest[ResourceToUpdate]
  /** Delete on the resource. */
  final case class DeleteResource(resource: ResourceIri) extends AccessRequest[ResourceToDelete]
  /** Project create permission for the class. */
  final case class CreateResource(project: KnoraProject, resourceClass: ResourceClassIri)
      extends AccessRequest[NewResource]

enum PermissionsChange:   // what an update submits, not "maybe a literal"
  case Unchanged
  case To(literal: String) // syntax already validated before the gate

final class Grant[+T] private[access] (
  val caller: User,
  val project: KnoraProject,
  val target: T,                       // the caller's view, see the target types below
  private[access] val unfiltered: Unfiltered,
):
  def verify(rule: Unfiltered => Option[IntegrityViolation]): IO[RequestRejectedException, Unit] // keeps today's 400 exception types

final class Unfiltered private[access] (private[access] val resource: ReadResourceV2)

enum IntegrityViolation:   // closed; render(request) builds fixed text plus request IRIs, never loaded data
  case CardinalityViolated, ValueSetMismatch, DuplicateLink, ...
```

**Target types** are new final case classes in the access package. Each holds the caller-filtered `ReadResourceV2`
view and, for value requests, the `ReadValueV2`:

- `ValueToCreate(resource)`
- `ValueToUpdate(resource, value)`
- `ValueToRescope(resource, value)`
- `ValueToDelete(resource, value)`
- `ValueToErase(resource, value)`
- `ValuesToReorder(resource, visibleValues)`
- `ResourceToUpdate(resource)`
- `ResourceToDelete(resource)`
- `ResourceToErase(resource)`
- `NewResource(project, resourceClass)`

`ObjectAccess` is a `final class` with `ZLayer.derive`, per `CONVENTIONS.md`. The responder specs run against the
real layers, so no trait seam is needed. It must not depend on any responder; the loader moves into the access
package so that `ValuesResponderV2` can depend on `ObjectAccess`, not the other way round.

### Order inside `within`, for requests on an existing object

1. Refuse anonymous callers with 403. This is defence in depth: the endpoint factory already answers 401. It
   runs before the lock, because it costs nothing.
2. `IriLocker.runWithIriLock(apiRequestId, resourceIri)` on the same string key today's write paths use.
   - The lock is re-entrant per `apiRequestId` (`IriLocker.scala:75`, entry count at `:37`).
   - Reorder and value erase generate an id; they have none today.
   - After 15 seconds (150 retries of 100 ms) the lock fails with `ApplicationLockException`, a 500.
3. Load as `SystemUser`, keeping today's loader per request so that deleted resources and values behave as today:
    - create value, update value and change value rights: the property-scoped Gravsearch load
      (`getResourceWithPropertyValues`). It excludes deleted resources and values, as today.
    - delete, erase and reorder value: `ReadResourcesService.getResources` with the request's property
      (`propertyIri = Some(..)`). Verified: the property filter keeps deleted values
      (`GetResourcePropertiesAndValuesQuery.scala:155-162` adds no `isDeleted` filter, and
      `ConstructResponseUtilV2.scala:724` attaches `deletionInfo` instead of dropping the value), so it matches
      today's unfiltered `findDeletableValue` load (`ValuesResponderV2.scala:1169-1176`).
    - update and delete resource: `getResourcePreviewWithDeletedResource`, as today, now as `SystemUser`. A deleted
      resource comes back as `knora-base:DeletedResource` and fails the class check, as today.
4. Missing resource: 404, with one gate message.
5. Hidden resource: 403 "You do not have permission to see resource" followed by the IRI (the `toResource` wording,
   `ResourceMessagesV2.scala:898-902`). **Hidden means the caller has no permission at all**, the filter reads use
   (`ConstructResponseRdfDataParser.scala:96-98`). A caller with restricted view (`RV`) sees the resource and fails
   at step 8.
6. Missing or hidden value: the same 404, with one gate message.
7. For `EraseValue`, a resource outside the project named by the request's shortcode: 400.
8. The level rule of the request case: 403.
9. `write(grant)`.

`SystemUser` gets the maximum permission in every load (`PermissionUtilADM.scala:98-102`), so steps 5 to 8 compute
the caller's permission from the loaded permission literals and creators with `getUserPermissionADM`. They never
use the permission the load reports.

### `withinAsProjectAdmin`, for erasing a resource

The role decision stays where main makes it: `ResourcesRestService` (`:197`) calls
`authorizeProjectAdminByShortcode(candidate, resourceIri.shortcode)` before any load and passes the
`ProjectAdminGrant` on. Then the gate:

1. Takes the lock.
2. Loads with `getResourcePreview` as `SystemUser`. Today `eraseResourceV2` (`ResourcesResponderV2.scala:458`) loads
   as `grant.user`; a project admin sees every object of the project, so the answers do not change.
3. Missing resource: 404.
4. The resource's project differs from the grant's project: 403, as today.
5. `Grant.caller` is `projectAdminGrant.user`.

There is no visibility step, because a project admin sees every object of the project.

### `CreateResource`

The gate refuses anonymous callers and checks the project create permission
(`hasPermissionFor(ResourceCreateOperation(...))`, `CreateResourceV2Handler.scala:146`). The following run inside
`write`, in this order:

1. `valueValidator` and `ensureClassBelongsToProjectOntology`;
2. the standoff link target checks;
3. `checkOrCreateEntityIri` (duplicate IRI: 400);
4. the lock on the new IRI;
5. the default permission ceilings for the resource and each value (`ensureWithinDefaultPermissions`,
   `PermissionUtilADM.scala:218`, called at `CreateResourceV2Handler.scala:356` and `:711`).

### What may run before the gate

Only steps that use the request and public data:

- JSON-LD parsing and IRI syntax;
- the project lookup by IRI or shortcode (projects are public);
- ontology lookups for the request's property: property not found 404, link-property 400,
  `hasStandoffLinkToValue` 400. These also decide the adjusted link property that the property-scoped load needs;
- the syntax of a submitted permission literal (`validatePermissions`). It checks group IRIs against admin data and
  reveals no object data.

The dsp-ingest asset check during parsing (`ValueMessagesV2.scala:~713-731`) also runs before the gate. It is the
exception the ADR records, and follow-up DEV-7731 removes it.

### What runs inside `write`, in this order

1. Request validation against the grant's target:
    - `valueValidator`;
    - class and value-type checks;
    - object-class and list-node checks;
    - link-target and standoff-target visibility checks (`checkResourceIris`, `checkStandoffLinkTargets`).
2. `verify` for the integrity checks that need hidden values. These run only after the level passed.
3. The repository write. It takes the resource and value IRIs from the grant, never from the request.

### Levels and the decision

- The decision of steps 4 to 8 is a pure function, `AccessDecision.decide`, with no ZIO. It works over:
    - the loaded permission literals of the resource and value, and their creators;
    - the project;
    - the `PermissionsChange`;
    - whether the property is a link property;
    - the caller's `User`.
- It returns a closed ADT: `Permit`, `MissingResource`, `HiddenResource`, `MissingValue`, `WrongProject`,
  `LevelDenied(level, on)`, and `CreateDenied` for `CreateResource`. Anonymous callers never reach it: `within` refuses them first.
  The gate maps it to exceptions in one place.
- For `UpdateValue` it picks Modify or ChangeRights from the `PermissionsChange` and the current literal. It adds
  Modify on the resource for link properties, which replaces the hand-written check at
  `ValuesResponderV2.scala:~677`. A Modify-only caller who changes both content and permissions gets 403 and no
  partial write.
- For `UpdateResource` it requires Modify, and ChangeRights when the `PermissionsChange` differs from the current
  literal. This keeps main's rule for resource metadata updates (`ResourcesResponderV2.scala:~148-161`).

### Endpoints

- The gated write endpoints keep `authenticatedObjectAccessPolicyEndpoint` (`BaseEndpoints.scala:~210`), which
  declares `Policy.ObjectAccess(enforcedIn, admitsAnonymous = false)` and answers 401 to anonymous and inactive
  callers. As each endpoint migrates, its `enforcedIn` names the gate (`ObjectAccess`) and its description names
  every level the gate can require for it.
- `GET /v2/resources/candelete` moves from `objectAccessPolicyEndpoint` to `authenticatedObjectAccessPolicyEndpoint`
  in Phase 6.
- The erase endpoints keep `systemAdminPolicyEndpoint` (values) and `projectAdminPolicyEndpoint("body resource IRI's
  project")` (resources).

### Status-code changes (for release notes)

Changes:

| Endpoint | Caller | Before | After |
| --- | --- | --- | --- |
| `GET /v2/resources/candelete` | no credentials | 200 `canDo: false` | 401 |
| `POST /v2/values` | resource hidden from the caller | 403 (no Modify) | 403 "You do not have permission to see resource" |
| `POST /v2/values`, `PUT /v2/values`, `POST /v2/resources` | caller without the required permission, invalid body content (legal info, cross-project link) | 400 | 403 |
| `POST /v2/values/delete`, `PUT /v2/values` | caller with restricted view on the resource or value | 403 "does not have V" (resource) or 404 (value) | 403 with the level message in both cases |
| `PUT /v2/values/order` | modify on the resource, some values hidden | 400 listing the hidden IRIs | 200; visible values permuted, hidden values keep their slots; `valuesReordered` counts visible values |
| `PUT /v2/values/order` | resource hidden from the caller | 403 (no Modify) | 403 "You do not have permission to see resource" |
| `PUT /v2/values/order` | property unknown, or a link or `hasStandoffLinkToValue` property | (no ontology lookup today) | 404 / 400, as for the other value writes |
| `PUT /v2/resources`, `PUT /v2/resources/authorship` | visible, no modify permission, wrong class or stale date | 400 | 403 |
| `POST /v2/resources/delete` | visible, no delete permission, clean request | 400 (the 403 is wrapped by `assertGoodRequestEither`) | 403 |
| `POST /v2/resources/delete` | visible, no delete permission, resource in use, wrong class or bad delete date | 400 | 403 |
| `GET /v2/resources/candelete` | any denial | 200 `canDo: false` with the first failing check's reason | 200 `canDo: false` with the gate's reason (missing, hidden or no Delete) before class, conflict and in-use reasons |
| `POST /v2/values/erase`, `/erasehistory` | shortcode names a project the resource is not in | success, but erases nothing (the DELETE runs in the named project's graph) | 400 |
| `POST /v2/values`, resource writes | caller with restricted view on the resource | 403 "does not have M" or "does not have V" | 403 with the level message |
| resource writes | resource hidden or missing | 403 "see one or more resources: `<iri>`" / 404 "One or more resources were not found" | 403 / 404 with the gate's messages (wording only) |
| value writes | resource missing | 404 "Expected `<iri>`, but no resources were returned" or "One or more resources were not found" | 404 with the gate's message (wording only) |

Unchanged on purpose:

- anonymous callers of every v2 value and resource write already get 401 from `authenticatedObjectAccessPolicyEndpoint`,
  `systemAdminPolicyEndpoint` or `projectAdminPolicyEndpoint`;
- a non-SystemAdmin already gets 403 from the value erase endpoints before the body is parsed;
- resource erase: the role check by the IRI's shortcode and the 403 for a resource of another project stay as they
  are;
- ontology answers for a nonexistent or link property (404 / 400) still come before 403 on create, update and
  delete;
- deleted resources and values behave as today for every value and resource write; tests pin this before each
  move.

## Execution tracks

- Phase 1 runs first and alone; every other phase depends on it.
- Then two tracks run in parallel, each in its own worktree branched from the Phase 1 commit:
    - **values track:** Phases 2, 3, 4 and 5, in that order. They rewrite the same files (`ValuesResponderV2`,
      `ValuesResponderV2Spec`, `ValuesEndpointsE2ESpec`), so they cannot run in parallel with each other.
    - **resources track:** Phases 6 and 7 (without Phase 7's documentation steps).
- Shared files both tracks touch: `tools/lint/elevated_read_allowlist.txt`, `tools/lint/system_user_allowlist.txt`,
  the access package and `AuthorizationMatrixE2ESpec`. Keep edits to them small and additive; the merge resolves
  them.
- The tracks merge into the PR branch, and then the closing step runs the documentation and the full test suites.

## Technical Considerations

- **Package and scoping.**
    - The gate lives in `slice/resources/access/` (webapi-resources). `Grant` and `Unfiltered` are plain final
    classes with `private[access]` constructors, so there is no `copy`, `apply` or `unapply`.
    - `Grant` is covariant in `T`, so helpers can take `Grant[?]`.
    - `ProjectAdminGrant` (`slice/common/api/ProjectAdminGrant.scala`) is issued only by `AuthorizationRestService`;
    `withinAsProjectAdmin` takes the grant the RestService already holds and reads `grant.user` and `grant.project`.
- **Loads.**
    - `ValuesResponderV2.getResourceWithPropertyValues` (`:1541-~1592`) moves into the access package in Phase 1 and
    stays the loader for create and update.
    - Delete, erase and reorder use `getResources`, narrowed to the request's property.
    - Resource requests keep their preview loaders.
    - The reorder slot query must use the same "non-deleted" definition as `verifyCanonicalValues`
    (`deletionInfo.isEmpty`, `ValuesRestService.scala:165`).
- **Lock key.** `runWithIriLock` has overloads for `IRI`, `SmartIri`, `KnoraIri` and `StringValue`
  (`IriLocker.scala:75-88`). A typed `ResourceIri` must lock the same string as today's write paths, or a migrated and
  an unmigrated path could lock different keys for one resource during the phased migration. A Phase 1 test pins the
  key.
- **Hidden.** Steps 5 and 6 reproduce the read filter (no permission at all), not `checkResourcePermission`'s
  `>= View`. RV-only callers see the object and fail on the level. `AccessDecisionSpec` pins both. The status-code
  table lists the resulting message changes for RV-only callers.
- **Integrity checks that need hidden data** move behind `verify`, or into access-package helpers that return only
  `Unit`, `Boolean`, a count or a write:
    - value cardinality on create (`ValuesResponderV2.scala:~184-237`) and on delete and erase (`:1255-1268`, with
    `onlyHistory`);
    - the duplicate-link check `ensureNoLinkTo` (DEV-7755, `:1786`, called on create at `:494` and on update at
    `:998`), which answers 400 `DuplicateValueException`;
    - link-value reference counts (`incrementLinkValue` `:1822`, `decrementLinkValue` `:1907`);
    - reorder slots.
    - The rules themselves (cardinality, duplicate link, reference counts, reorder slots) are defined in the access
      package, because `Unfiltered` is unreadable outside it; responders pass them to `verify` by name.
    - `ValueRepo.checkDuplicateOrder` (`ValueRepo.scala:178-189`) names the resource and property from the request,
    which is fine.

  Their outcomes can reveal one bit about hidden values, as a unique index does under row-level security. They run
  only after the level passed, which the ADR accepts.
- **Reorder slots.**
    - `valueHasOrder` is not on `ReadValueV2`, so a gate-owned `sparql"..."` SELECT reads `(value, order)` for every
    non-deleted value of the property.
    - The visible set's old slots are sorted as a multiset and reused as is, duplicates and gaps included, and
    assigned to the requested order.
    - Reads sort by `valueHasOrder`, a missing order counting as 0, then by value IRI
    (`ConstructResponseUtilV2.sortValuesByOrderThenIri`, `:674-679`). Visible values without `valueHasOrder` (file
    values; learnings `value-has-order-optional-cardinality.md`) therefore read first today. After a reorder they get
    explicit slots after the maximum over **all** non-deleted values, hidden ones included, so they read at the
    requested position among the visible values that also had no slot. This changes their read order; the release
    notes say so.
    - Hidden values are not written.
    - The write keeps `ValueQueries.reorderValues`' `OPTIONAL ?oldOrder` clause (`:178`) and takes an explicit
    `(value, slot)` list instead of the list index.
    - Duplicate slots survive a reorder; reads break ties by value IRI, so the order stays deterministic. Phase 5 pins
    it with a test.
    - Reordering the values of a link property keeps today's behaviour, which Phase 5 pins before the change.
    - The lock is per JVM (`ValueRepo.scala:152-154`). A multi-instance race stays as it is today.
- **Messages.** 403 and 404 go through `BaseEndpoints.errorOutputs` (`:33-43`), which sends the exception message
  verbatim (learnings `shared-tapir-error-envelope-leaks-exception-messages.md`). Every message the gate builds is
  fixed text plus request IRIs. `DuplicateValueException` and `EditConflictException` map to 400 there.
- **Lint.**
    - `check_authz.py` has three rules today; rule 3 is the `Policy.key` / `withInfo` home check
    (`POLICY_ATTRIBUTE_HOMES`, `:22-23`).
    - It gains rule 4: the token `KnoraSystemInstances.Users.SystemUser` (the requesting-user handle) in main sources,
    with its own allow-list, `tools/lint/elevated_read_allowlist.txt`. `check()` (`:82`) takes two allow-lists today,
    so it gains a third argument and `tools/lint/BUILD.bazel` passes the new file.
    - Allow-list entries are exact file paths, compared as set membership (`check()` `:84-95`), so each access-package
    file that reads as `SystemUser` is listed by path, on both lists.
    - Rule 2 (any `SystemUser`) matches every rule 4 hit, so a file on the new list is also on
    `system_user_allowlist.txt`.
    - Rule 2 keeps covering the creator-IRI uses (`KnoraUserRepo.builtIn.SystemUser.id`) in `ValuesResponderV2` and
    `CreateResourceV2Handler`.
    - Rule 5: a main-source file that declares `package org.knora.webapi.slice.resources.access` must live in that
    directory. Package access is the grant's only protection, so this stops a second issuer.
- **Test data.** New tests create their own fixtures through the API, as `createThingWithInteger` does
  (`ValuesEndpointsE2ESpec.scala:674`), and never add to `anything-data.ttl` (CLAUDE.md testing guidelines;
  learnings `test-fixture-isolation-shared-data-scope.md`). Reorder tests use about ten non-alphabetical values
  (learnings `alphabetical-test-data-masks-ordering.md`).
- **Direct callers of changing signatures.** test-it compiles as one target, so each phase migrates every direct
  caller of the methods it changes in the same phase:
    - `ValuesResponderV2Spec`: 101 calls; Phases 2, 3 and 4.
    - `ValuesEraseSpec`: Phase 2.
    - `ValueHasXmlLifecycleSpec`: `deleteValueV2` (`:185`) and `eraseValueHistory` (`:203`) in Phase 2,
    `updateValueV2` (`:222`) in Phase 3, `valueRepo.reorderValues` (`:232`) in Phase 5.
    - `ResourcesResponderV2Spec`: 53 calls; 9 metadata, 4 delete, 2 candelete and 1 erase in Phase 6, 37
    `createResource` in Phase 7.
    - `OntologyResponderV2Spec`: `createResource` (`:4718`); Phase 7.
    - `ValueQueriesSpec` (webapi unit): the `reorderValues` query; Phase 5.
    - `ValuesRestServiceSpec` (webapi unit, `:29`) builds `ValuesRestService(...)` by named parameters, so the new
    `ObjectAccess` dependency breaks it; Phase 5.

  `BulkImportParityE2ESpec` (the v3 import path) calls none of them and must stay green.
- **Conventions.** `CONVENTIONS.md`, `docs/development/code-conventions.md`, `docs/development/error-handling.md`
  (Style A: `Task` with dsp exceptions) and `docs/development/iri-handling.md`: no `unsafeFrom` in responders, and
  new SPARQL uses the `sparql"..."` interpolator.

## Implementation Phases

<!-- markdownlint-disable-next-line MD001 -->
#### Phase 1: The gate, its decision table and the lint rules

- [x] Create `slice/resources/access/` with these types, with the constructors restricted to `private[access]`:
    - `ObjectAccess`;
    - the `AccessRequest` sealed trait and its cases;
    - `PermissionsChange`;
    - the target types;
    - `Grant`;
    - `Unfiltered`;
    - `IntegrityViolation`.
- [x] Move `getResourceWithPropertyValues` from `ValuesResponderV2` into the access package; `ValuesResponderV2` calls it from there until its operations migrate
- [x] Implement `AccessDecision.decide` as a pure function over the inputs listed under "Levels and the decision", returning the closed outcome ADT
- [x] Implement `ObjectAccess.within`, `withinAsProjectAdmin` and `decideOnly` with the orders described above
- [x] Register `ObjectAccess.layer` in `core/LayersLive.scala`
- [x] Add `AccessDecisionSpec` (webapi unit test, ZIO Test), generated as a cross product. The dimensions are:
    - every request case;
    - permission literals for Creator, ProjectMember, KnownUser and UnknownUser, an `RV`-only literal, and a literal no group of the caller matches;
    - a creator, a project member, a ProjectAdmin and a SystemAdmin (anonymous callers are refused before `decide`, and an `ObjectAccess` test covers that);
    - a link property;
    - each `PermissionsChange`, for `UpdateValue` and `UpdateResource`;
    - and an assertion that the row count equals the product, so a new case or caller fails until it has rows.
- [x] Add property tests to `AccessDecisionSpec`:
    - a hidden value and a missing one get the same outcome and message;
    - no message contains an IRI that is not in the request (loaded IRIs carry a marker prefix);
    - raising the caller's permission never turns permit into deny.
- [x] Add a compile-negative spec in another package. It shows that `Grant` and `Unfiltered` cannot be constructed, copied or read, and that `grant.unfiltered` cannot be read. It asserts stable fragments per case, with a positive control
- [x] Add a lock-key test showing that the gate and today's write paths lock the same string for one resource
- [x] Add rule 4 to `tools/lint/check_authz.py`: `KnoraSystemInstances.Users.SystemUser` in main sources outside `tools/lint/elevated_read_allowlist.txt`, comment lines skipped, stale entries failing; `check()` takes the new allow-list as a third argument
- [x] Add rule 5 to `tools/lint/check_authz.py`: a main-source file declaring `package org.knora.webapi.slice.resources.access` outside `slice/resources/access/` fails
- [x] Seed `elevated_read_allowlist.txt` with today's files (`ValuesResponderV2`, `ValuesRestService`, `ReadResourcesServiceLive`, `ValueHasXmlRenderer`), each with a reason, plus each access-package file that reads as `SystemUser`, by path
- [x] Add those access-package files to `system_user_allowlist.txt` too, because rule 2 matches every rule 4 hit
- [x] Add rule 4 and rule 5 fixture tests to `tools/lint/check_authz_test.py`. Each must be red against an unlisted use, green with the entry, and red on a stale entry
- [x] Wire the new allow-list into `tools/lint/BUILD.bazel`
- [x] Run `just test-unit`; it passes
- [x] Run `just check`; it passes
- [x] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

#### Phase 2: Value delete and erase through the gate

- [x] Rewrite `userPermission` to the caller's level on the grant target's values and nested link targets (the view keeps the SystemUser maximum there; only the resource's is rewritten), or pin that no write reads it (deferred from the Phase 1 review)
- [x] Add e2e tests pinning today's outcomes for value delete and value erase on a deleted resource and on a soft-deleted value; they are green before the move and stay green
- [x] Create the cardinality rule in the access package for the delete and erase check (`ValuesResponderV2.scala:1255-1268`, with `onlyHistory`)
- [x] Move `decrementLinkValue` and its `findLinkValue` use into access-package helpers that read `Unfiltered` and return only the write, for the delete path (`ValuesResponderV2.scala:~1378`, `:~1431`)
- [x] Move `deleteValueV2` and `canRemoveValue` onto `within(DeleteValue)`. Class, type and cardinality checks take the grant; cardinality uses the access package's cardinality rule through `verify`. This removes the elevated read in `findDeletableValue` (`ValuesResponderV2.scala:~1175`)
- [x] Move `eraseValue` and `eraseValueHistory` onto `within(EraseValue)`, carrying `onlyHistory`. The gate takes the write lock erase lacked, generates an `apiRequestId`, and answers 400 when the resource is not in the project named by the request's shortcode
- [x] Remove `findDeletableValue`; its load and checks are now the gate's
- [x] Migrate the direct callers in `ValuesResponderV2Spec`, `ValuesEraseSpec` and `ValueHasXmlLifecycleSpec` (`:185`, `:203`) to obtain grants through the gate
- [x] Set `enforcedIn` of `deleteValues` to the gate and name the Delete level in its description; name the SystemAdmin role and the project check in the descriptions of `postValuesErase` and `postValuesErasehistory`
- [x] Keep the delete suite in `ValuesEndpointsE2ESpec` green; adjust any RV-only expectation to the status-code table
- [x] Add e2e tests for delete:
    - a view-only caller gets 403 before the class and type checks;
    - an RV-only caller gets the level message;
    - the creator deletes;
    - a ProjectAdmin deletes.
- [x] Add e2e tests for erase:
    - a SystemAdmin erases;
    - a shortcode that does not match the resource's project answers 400 and erases nothing.
- [x] Run `bazel test //modules/test-e2e:test --test_filter=*ValuesEndpointsE2ESpec*`; it passes
- [ ] Run `bazel test //modules/test-e2e:test --test_filter=*AuthorizationMatrixE2ESpec*`; it passes (deferred to CI: the local sandbox has no docker socket for the `/ingest` rows; all other rows pass)
- [x] Run `bazel test //modules/test-it:test --test_filter=*ValuesEraseSpec*`; it passes
- [x] Run `bazel test //modules/test-it:test --test_filter=*ValuesResponderV2Spec*`; it passes
- [x] Run `bazel test //modules/test-it:test --test_filter=*ValueHasXmlLifecycleSpec*`; it passes
- [x] Run `just test-unit`; it passes
- [x] Run `just check`; it passes
- [x] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

#### Phase 3: Value update through the gate

- [x] Add e2e tests pinning today's outcome for a value update on a deleted resource; green before the move and after
- [x] Validate the submitted permission literal's syntax before the gate, and pass it as `PermissionsChange` in `UpdateValue`
- [x] Move content updates in `updateValueV2` onto `within(UpdateValue)`
- [x] Move permissions-only updates onto `within(ChangeValueRights)`
- [x] Move `valueValidator.validate` (`ValuesResponderV2.scala:611`), the object-class check, the list-node check and the standoff and link target checks after the grant (DEV-7730, update)
- [x] Remove the hand-written link-update Modify-on-resource check (`:~677`); the gate's level rule covers it
- [x] Move `ensureNoLinkTo` on the update path (`:998`) behind `verify`, as the access package's duplicate-link rule
- [x] Move link-value reference counting for the update paths (`ValuesResponderV2.scala:~902`, `:~917`, `:~1002`, `:~1012`) into the access-package helpers
- [x] Remove `findVisibleValue` and `checkValueAndRetrieveResourceProperties`, and with them the elevated read at `ValuesResponderV2.scala:~794`
- [x] Migrate the direct callers in `ValuesResponderV2Spec` and `ValueHasXmlLifecycleSpec` (`:222`)
- [x] Set `enforcedIn` of `putValues` to the gate and name Modify, ChangeRights and the link rule in its description
- [x] Keep the suite "PUT /v2/values checks view permission…" (`ValuesEndpointsE2ESpec.scala:4864`) green; adjust any RV-only expectation to the status-code table
- [x] Add e2e tests, each denial red before its change:
    - a Modify-only caller who submits different permissions gets 403;
    - a Modify-only caller who changes content and permissions together gets 403 and the value is unchanged;
    - a caller with ChangeRights changes the permissions;
    - the creator updates;
    - a caller without modify permission who sends a disallowed license gets 403, not 400;
    - a link update by a caller without Modify on the resource gets 403;
    - a link update to a target the resource already links to still answers 400 for a permitted caller;
    - an RV-only caller gets 403 with the level message.
- [x] Run `bazel test //modules/test-e2e:test --test_filter=*ValuesEndpointsE2ESpec*`; it passes
- [x] Run `bazel test //modules/test-it:test --test_filter=*ValuesResponderV2Spec*`; it passes
- [x] Run `bazel test //modules/test-it:test --test_filter=*ValueHasXmlLifecycleSpec*`; it passes
- [x] Run `just test-unit`; it passes
- [x] Run `just check`; it passes
- [x] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

#### Phase 4: Value creation through the gate

- [x] Move `createValueV2` onto `within(CreateValue)`, adding the view check before modify on the resource
- [x] Move `valueValidator.validate` (`ValuesResponderV2.scala:96`), the rdf:type check, the object-class check, the list-node check and the standoff link target checks after the grant (DEV-7730, create)
- [x] Extend the access package's cardinality rule with the create check (`ValuesResponderV2.scala:~184-237`), run through `verify`
- [x] Move `ensureNoLinkTo` on the create path (`:494`) behind `verify`, through the duplicate-link rule from Phase 3
- [x] Move link-value reference counting for the create paths (`ValuesResponderV2.scala:~430`, `:~497`) into the access-package helpers
- [x] Remove the elevated read at `ValuesResponderV2.scala:~151`, and remove `ValuesResponderV2.scala` from `elevated_read_allowlist.txt`. Its rule 2 entry stays for the link-value creator, with the reason updated
- [x] Migrate the direct callers in `ValuesResponderV2Spec`
- [x] Set `enforcedIn` of `postValues` to the gate and name View and Modify on the resource in its description
- [x] Add e2e tests in a new suite `POST /v2/values checks permissions before revealing details`, each denial red before its change:
    - a hidden resource gives the same 403 for a valid body, a wrong class and an invalid value;
    - an RV-only caller gets 403 with the Modify message;
    - a caller without modify permission and with a disallowed license gets 403;
    - a permitted caller still gets 400 for a cardinality violation and for a link that already exists.
- [x] Run `bazel test //modules/test-e2e:test --test_filter=*ValuesEndpointsE2ESpec*`; it passes
- [x] Run `bazel test //modules/test-it:test --test_filter=*ValuesResponderV2Spec*`; it passes
- [x] Run `bazel test //modules/test-it:test --test_filter=*ValueHasXmlLifecycleSpec*`; it passes
- [x] Run `just test-unit`; it passes
- [x] Run `just check`; it passes
- [x] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

#### Phase 5: Value reorder through the gate, with slot-preserving semantics

- [x] Add e2e tests pinning today's reorder behaviour for a link property and for values with equal `valueHasOrder` (reads break ties by value IRI); green before the change
- [x] Add the gate-owned `sparql"..."` SELECT for `(value, valueHasOrder)` over the non-deleted values of one property, with the same "non-deleted" definition as `verifyCanonicalValues`
- [x] Implement slot assignment as described under Technical Considerations, Reorder slots
- [x] Change `ValueQueries.reorderValues` to write an explicit `(value, slot)` list for visible values, keeping the `OPTIONAL ?oldOrder` clause
- [x] Update `ValueQueriesSpec` and its golden SPARQL for the new query
- [x] Add the property lookup before the gate in `ValuesRestService.reorderValues`: not found 404, link property 400, `hasStandoffLinkToValue` 400, reusing the checks `canRemoveValue` runs
- [x] Move `ValuesRestService.reorderValues` onto `within(ReorderValues)`, generating an `apiRequestId` and taking the lock it lacked
- [x] Rework `verifyCanonicalValues` to compare against the grant's visible values. The 400 names only visible values the request omitted and request IRIs that are not visible values. A hidden IRI and a nonexistent one give the same body (DEV-7727)
- [x] Remove `ValuesRestService.scala` from both SystemUser allow-lists in the same commit
- [x] Migrate `ValuesRestServiceSpec` (new constructor dependency) and `ValueHasXmlLifecycleSpec` (`:232`)
- [x] Set `enforcedIn` of `putValuesOrder` to the gate and name Modify on the resource and the slot semantics in its description
- [x] Add e2e tests on a fresh resource with about ten non-alphabetical values, some hidden from the caller:
    - reordering the visible ones succeeds;
    - hidden values keep their `valueHasOrder` and are not renumbered;
    - `valuesReordered` counts the visible values;
    - the response bodies for a hidden IRI and a nonexistent IRI are equal;
    - a visible value without `valueHasOrder` gets an explicit slot after the maximum and reads at its requested position;
    - duplicate slots survive and read back in a deterministic order;
    - a caller who sees no value of the property gets the 400 without loaded IRIs;
    - an unknown property answers 404, and a link or `hasStandoffLinkToValue` property answers 400;
    - a hidden resource answers 403 "see resource", and an RV-only or no-Modify caller gets the level message.
- [x] Keep the existing reorder suite (`ValuesEndpointsE2ESpec.scala:4346-4739`) green
- [x] Run `bazel test //modules/test-e2e:test --test_filter=*ValuesEndpointsE2ESpec*`; it passes
- [x] Run `bazel test //modules/test-it:test --test_filter=*ValuesResponderV2Spec*`; it passes
- [x] Run `bazel test //modules/test-it:test --test_filter=*ValueHasXmlLifecycleSpec*`; it passes
- [x] Run `just test-unit`; it passes
- [x] Run `just check`; it passes
- [x] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

#### Phase 6: Resource metadata, authorship, deletion and erase through the gate

- [x] Add e2e tests pinning today's outcomes for update, delete and erase on an already deleted resource; green before the moves and after
- [x] Extract the class, conflict, in-use and delete-date checks of `canDeleteResource` (`ResourcesResponderV2.scala:395-436`) into one function that takes a `Grant[ResourceToDelete]` and returns the original exceptions
- [x] Move `updateResourceMetadataV2` and `updateResourceAuthorshipV2` onto `within(UpdateResource)`, passing the submitted permissions as `PermissionsChange`, so the class and edit-conflict checks run after the Modify and ChangeRights decision
- [x] Move `markResourceAsDeletedV2` onto `within(DeleteResource)` plus the extracted checks
- [x] Stop `markResourceAsDeletedV2` wrapping failures into 400 through `assertGoodRequestEither` (`:335`): a denial answers 403 and the other checks keep their own status
- [x] Move `canDeleteResource` onto `decideOnly(DeleteResource)` followed by the extracted checks, with no lock. Map every failure to a 200 `CanDoResponseV2` with `canDo: false`, as today
- [x] Move `eraseResourceV2` (`:458`) onto `withinAsProjectAdmin`, with the grant `ResourcesRestService` already obtains
- [x] Migrate the direct callers in `ResourcesResponderV2Spec` (metadata, delete, candelete, erase)
- [x] Switch `getResourcesCanDelete` to `authenticatedObjectAccessPolicyEndpoint`
- [x] Set `enforcedIn` of `putResources`, `putResourcesAuthorship`, `postResourcesDelete` and `getResourcesCanDelete` to the gate, and name the levels in their descriptions (Modify, plus ChangeRights for changed permissions; Delete)
- [x] Add e2e hidden-object tests per migrated endpoint in `ResourcesRouteV2E2ESpec`, each denial red before its change:
    - hidden resource 403 with one message;
    - RV-only 403 with the level message;
    - visible without permission 403 before the 400s;
    - missing 404;
    - the permitted caller unchanged.
- [x] Add an e2e test: a Modify-only caller who submits different resource permissions gets 403, and a caller with ChangeRights succeeds
- [x] Add an e2e test: delete without permission on a clean request gets 403 (red before: 400)
- [x] Add e2e tests for candelete:
    - 200 `canDo: false` for hidden, missing and no-delete callers;
    - for a no-delete caller on a resource in use or of the wrong class, the reason is the gate's;
    - 401 without credentials.
- [x] Run `bazel test //modules/test-e2e:test --test_filter=*ResourcesRouteV2E2ESpec*`; it passes
- [x] Run `bazel test //modules/test-e2e:test --test_filter=*AuthorizationMatrixE2ESpec*`; it passes
- [x] Run `bazel test //modules/test-it:test --test_filter=*ResourcesResponderV2Spec*`; it passes
- [x] Run `just test-unit`; it passes
- [x] Run `just check`; it passes
- [x] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

#### Phase 7: Resource creation through the gate

- [x] Move `CreateResourceV2Handler` onto `within(CreateResource)`, with the order under "CreateResource" above (DEV-7730, resource create)
- [x] Migrate the direct callers in `ResourcesResponderV2Spec` (37 `createResource` calls) and `OntologyResponderV2Spec` (`:4718`)
- [x] Set `enforcedIn` of `postResources` to the gate and name the project create permission in its description
- [x] Add e2e tests:
    - a user without create permission who sends a disallowed license gets 403, not 400;
    - a permitted caller is unchanged, including 400 for an existing custom IRI.
- [x] Run `bazel test //modules/test-e2e:test --test_filter=*ResourcesRouteV2E2ESpec*`; it passes
- [x] Run `bazel test //modules/test-it:test --test_filter=*ResourcesResponderV2Spec*`; it passes
- [x] Run `bazel test //modules/test-it:test --test_filter=*OntologyResponderV2Spec*`; it passes
- [x] Run `just test-unit`; it passes
- [x] Run `just check`; it passes
- [x] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

#### Closing: merge the tracks, documentation and full test run

- [x] Merge the values track and the resources track into the PR branch; reconcile the shared allow-lists, the access package and `AuthorizationMatrixE2ESpec`
- [x] Update `docs/development/code-conventions.md`: a section says v2 resource and value writes go through `ObjectAccess.within`, with `authenticatedObjectAccessPolicyEndpoint` naming the gate as `enforcedIn`
- [x] Update `CONVENTIONS.md` with the same rule
- [x] Add a `REVIEW.md` item. Each of these is a finding:
    - a resource or value write that loads the resource itself;
    - a write that answers from loaded data outside a grant;
    - a write that takes a written IRI from the request rather than the grant;
    - a grant kept beyond its closure.
- [x] Update `docs/contexts/resources-values/CONTEXT.md` with the **Grant** and **Access request** terms
- [x] Update the endpoint docs: in `docs/03-endpoints/api-v2/editing-values.md` and `editing-resources.md`, add the new permission lines and the reorder semantics
- [x] Update `ARCH-MAP.md` for webapi-resources, webapi-common and webapi-api with the `dune:dune-map` skill: add the access package, and rewrite the "There is no single chokepoint" note (`:919`) to say that v2 resource and value writes have one chokepoint, `ObjectAccess`, while read enforcement is still split
- [x] Add a "Not yet in code" line to ADR-0013 Addendum 1 if any part did not land; otherwise state that it is complete
- [x] Run `bazel test //modules/test-e2e:test --test_filter=*BulkImportParityE2ESpec*`; it passes
- [x] Run `just test-unit`; it passes
- [x] Run `just test-it`; it passes
- [ ] Run `just test-e2e`; it passes, except the known local timeout of `ValueHasXmlOnValueWriteE2ESpec` "a document of 6000 standoff tags" (deferred to CI: 42 of 1886 fail locally, all on the Sipi `docker run` convert the sandbox cannot reach; the same tests fail on the base)
- [x] Run `just check`; it passes
- [x] Review: adversarial review of the merged branch; verified findings fixed

## Human Actions

| Id | Action | Who | When | Why not the agent |
| --- | --- | --- | --- | --- |
| H1 | Decide the release, and put the status-code table above in the release notes | Ivan | after ship | Release timing is an owner decision |

## Acceptance Criteria

- [ ] Every v2 value write runs through `ObjectAccess.within`: create, update, permission change, delete, erase, erase history and reorder
- [ ] Every v2 resource write runs through the gate: create, metadata and authorship through `within`, delete through `within`, and erase through `withinAsProjectAdmin`
- [ ] `candelete` runs through `decideOnly`, takes no lock, answers 200 for denied cases and 401 without credentials
- [ ] No migrated responder or RestService method takes a `User` and reads the resource itself; they take a `Grant[T]`
- [ ] Every migrated write takes the IRIs it writes from the grant
- [ ] Every gated endpoint names the gate as `enforcedIn` and names its levels in its description
- [ ] `ValuesRestService.scala` is on neither SystemUser allow-list
- [ ] `ValuesResponderV2.scala` is not on `elevated_read_allowlist.txt`
- [ ] Each request kind keeps today's loader, so deleted resources and values behave as today; the pinning tests stay green
- [ ] `AccessDecisionSpec` covers the full cross product, including an RV-only caller, a link property and each `PermissionsChange`, and its property tests pass
- [ ] The compile-negative spec is red when a constructor becomes public
- [ ] Each new denial e2e test was red before its change
- [ ] Response bodies for a hidden value and a nonexistent one are equal wherever the gate answers 404 or 400
- [ ] A reorder by a caller with hidden values succeeds, and the hidden values keep their `valueHasOrder`
- [ ] `ValuesEraseSpec`, `ValueHasXmlLifecycleSpec`, `ValuesRestServiceSpec`, `ResourcesResponderV2Spec`, `OntologyResponderV2Spec` and `BulkImportParityE2ESpec` pass
- [ ] Lint rules 4 and 5 exist with fixture tests, and `elevated_read_allowlist.txt` is wired into `just check`
- [ ] Value create, value update and resource create answer 403 before validation for a caller without the required permission (DEV-7730)
- [ ] Value erase takes the write lock and answers 400 when the resource is not in the shortcode's project
- [ ] `docs/development/code-conventions.md`, `CONVENTIONS.md`, `REVIEW.md`, `CONTEXT.md`, the endpoint docs and `ARCH-MAP.md` describe the gate
- [ ] `just test-unit`, `just test-it` and `just check` pass
- [ ] `just test-e2e` passes, apart from the known local `ValueHasXmlOnValueWriteE2ESpec` timeout

## Dependencies & Risks

- **The `ValuesResponderV2` rewrite is large** (about 2000 lines). The phases follow the operations, so each phase
  is one reviewable change. The existing value e2e suites are the regression net. `canRemoveValue` is shared by
  delete and erase, so both move in Phase 2.
- **test-it compiles as one target.** A phase that changes a responder signature must migrate every direct caller
  in the same phase, or every test-it suite fails to compile.
- **Two parallel tracks.** The values and resources tracks touch shared allow-lists and the access package; the
  closing merge reconciles them. Each track keeps test-it compiling on its own.
- **Behaviour changes for clients:**
    - 403 before 400;
    - `POST /v2/resources/delete` answers 403 instead of 400 for a denial;
    - `candelete` answers 401 without credentials;
    - RV-only callers get the level message;
    - reorder no longer fails because of hidden values, and values without `valueHasOrder` read at their requested
      position after a reorder;
    - value erase with a mismatched shortcode answers 400;
    - 404 wording is unified.
  The status-code table goes in the release notes (H1).
- **Resource writes load as `SystemUser` instead of as the caller.** The gate computes permissions from the loaded
  literals; a mistake there would grant everyone the system's maximum. `AccessDecisionSpec` and the per-endpoint
  hidden-object tests guard this.
- **A grant escaping its closure, or `Unfiltered` data piped into a response,** is a review item, not a type error
  (ADR-0013 Addendum 1, Consequences).

## Success Metrics

- **SystemUser allow-lists:**
    - Today: 10 files on rule 2; 4 files use the requesting-user handle (`ValuesResponderV2`, `ValuesRestService`,
      `ReadResourcesServiceLive`, `ValueHasXmlRenderer`).
    - After the plan: `ValuesRestService.scala` is off both lists, and the access-package files that read as
      `SystemUser` are on both, so rule 2 lists 9 files plus those, and rule 4 lists `ReadResourcesServiceLive`,
      `ValueHasXmlRenderer` and those access-package files.
    - The elevated-read list holds no resource or value write.
- **Hidden-object e2e coverage:** today only value delete and value update have it; after the plan, every migrated
  write endpoint does.

## References

- [ADR-0013 and Addendum 1](../../adr/0013-authorization-policy-enforcement.md)
- [ADR-0011](../../adr/0011-cross-context-access-ports-and-adapters.md)
- Gate seed: `modules/webapi/src/main/scala/org/knora/webapi/responders/v2/ValuesResponderV2.scala`
    - `findVisibleValue` (`:1140`) and `findDeletableValue` (`:1162`)
    - create (`:89`), update (`:550`), delete (`:1277`), erase (`:1074`)
    - `getResourceWithPropertyValues` (`:1541`)
    - `ensureNoLinkTo` (`:1786`)
- Resource writes: `responders/v2/ResourcesResponderV2.scala:114-505`
- Resource erase role check: `slice/api/v2/resources/ResourcesRestService.scala:197`,
  `slice/common/api/AuthorizationRestService.scala:100`
- Resource create: `responders/v2/resources/CreateResourceV2Handler.scala:78-98`, with the ceilings at `:356` and `:711`
- Reorder:
    - `slice/api/v2/values/ValuesRestService.scala:104-180`
    - `slice/resources/repo/service/ValueQueries.scala:147-182`
    - read order: `ConstructResponseUtilV2.sortValuesByOrderThenIri` (`:674-679`)
- Lock: `responders/IriLocker.scala:37-166`
- Permission evaluation:
    - `responders/v2/ResourceUtilV2.scala:37-92`
    - `messages/util/PermissionUtilADM.scala:90-142`
    - the read filter in `messages/util/ConstructResponseRdfDataParser.scala:56-125` and `:235-247`
- Hidden and missing answers: `messages/v2/responder/resourcemessages/ResourceMessagesV2.scala:895-937`
- Endpoint policies: `slice/common/api/Policy.scala:49`, `slice/common/api/BaseEndpoints.scala:196-216`
- Ratchet and matrix:
    - `modules/test-e2e/src/test/scala/org/knora/webapi/slice/api/EndpointPolicyE2ESpec.scala`
    - `modules/test-e2e/src/test/scala/org/knora/webapi/slice/api/AuthorizationMatrixE2ESpec.scala`
- Lint: `tools/lint/check_authz.py` and `tools/lint/system_user_allowlist.txt`
- External practice:
    - OWASP Authorization and IDOR Prevention cheat sheets
    - GitHub REST troubleshooting (404 for private resources)
    - PostgreSQL row security (constraint side channels)
    - Pundit and CanCanCan controller helpers
    - Figma's fractional indexing (ordering prior art)
- Learnings (dasch-specs):
    - `learnings/logic-errors/value-has-order-optional-cardinality.md`
    - `learnings/test-failures/alphabetical-test-data-masks-ordering.md`
    - `learnings/best-practices/test-fixture-isolation-shared-data-scope.md`
    - `learnings/best-practices/shared-tapir-error-envelope-leaks-exception-messages.md`
- Linear: DEV-7727, DEV-7730 (closed by this plan); DEV-7723, DEV-7728 (related); DEV-7731 (follow-up)
