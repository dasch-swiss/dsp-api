---
title: "feat: route every v2 resource and value write through one object-access gate"
type: feat
date: 2026-11-01
author: "Ivan Subotic"
status: reviewed
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
- DEV-7731 (the dsp-ingest check during parsing) is a follow-up, not part of this plan.

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
- Resource metadata and authorship updates check class and edit conflict before modify permission (`:128-146`,
  `:230-250`).
- Value create checks modify but never view, so a hidden resource and a visible but unmodifiable one get the same
  answer (`responders/v2/ValuesResponderV2.scala:147-155`).
- Value erase takes no write lock and never checks that the resource belongs to the project named by the request's
  shortcode, whose graph it writes to (`slice/api/v2/values/ValuesRestService.scala:185-207`,
  `ValueRepo.scala:103-112`).

The only guard against reintroducing this is a scaladoc on `findVisibleValue`.

## Proposed Solution

The interface is shaped by ADR-0013 Addendum 1. It is a sketch, so names may settle in Phase 1, but the invariants
below may not change.

```scala
package org.knora.webapi.slice.resources.access

final class ObjectAccess(/* readResources, ontology, projects, auth: AuthorizationRestService, ... */):
  // object and create requests; the caller is a User
  def within[T, R](caller: User, apiRequestId: UUID, request: AccessRequest[T])(write: Grant[T] => Task[R]): Task[R]
  // erase a resource: a role decision, the caller arrives as a candidate from projectAdminPolicyEndpoint
  def withinAsProjectAdmin[R](candidate: ProjectCandidate, apiRequestId: UUID, resource: ResourceIri)(
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
  /** Modify on the resource. */
  final case class UpdateResource(resource: ResourceIri) extends AccessRequest[ResourceToUpdate]
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
  def verify(rule: Unfiltered => Option[IntegrityViolation]): IO[BadRequestException, Unit]

final class Unfiltered private[access] (private[access] val resource: ReadResourceV2)

enum IntegrityViolation:   // closed; render(request) builds fixed text plus request IRIs, never loaded data
  case CardinalityViolated, ValueSetMismatch, ...
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
    - create value, update value and change value rights: the property-scoped Gravsearch load (`getResourceWithPropertyValues`). It excludes
      deleted resources and values, as today.
    - delete, erase and reorder value: `ReadResourcesService.getResources` with the request's property
      (`propertyIri = Some(..)`). It includes deleted values and resources, as today's `findDeletableValue` load
      does. Confirm in Phase 1 that the property filter keeps deleted values (unverified).
    - update and delete resource: `getResourcePreviewWithDeletedResource`, as today, now as `SystemUser`. A deleted
      resource comes back as `knora-base:DeletedResource` and fails the class check, as today.
4. Missing resource: 404, with one gate message.
5. Hidden resource: 403 "You do not have permission to see resource" followed by the IRI (the `toResource` wording,
   `ResourceMessagesV2.scala:883-886`). **Hidden means the caller has no permission at all**, the filter reads use
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

1. Take the lock.
2. Load with `getResourcePreview` as `SystemUser`. Today's erase loads as the caller; the role decision replaces
   the visibility answer. Anonymous callers are refused by the endpoint factory.
3. Missing resource: 404.
4. Turn the candidate into a `ProjectAdminGrant` with `AuthorizationRestService.authorizeProjectAdminById(candidate,
   resource.projectIri)` (`AuthorizationRestService.scala:92`): 403 otherwise. There is no visibility step, because a
   project admin sees every object of the project.
5. `Grant.caller` is `projectAdminGrant.user`.

### `CreateResource`

The gate refuses anonymous callers and checks the project create permission
(`hasPermissionFor(ResourceCreateOperation(...))`, `CreateResourceV2Handler.scala:~145`). The following run inside
`write`, in this order:

1. `valueValidator` and `ensureClassBelongsToProjectOntology`;
2. the standoff link target checks;
3. `checkOrCreateEntityIri` (duplicate IRI: 400);
4. the lock on the new IRI;
5. the default permission ceilings for the resource and each value (`ensureWithinDefaultPermissions`, today at
   `CreateResourceV2Handler.scala:356` and `:707`).

### What may run before the gate

Only steps that use the request and public data:

- JSON-LD parsing and IRI syntax;
- the project lookup by IRI or shortcode (projects are public);
- ontology lookups for the request's property: property not found 404, link-property 400,
  `hasStandoffLinkToValue` 400. These also decide the adjusted link property that the property-scoped load needs;
- the syntax of a submitted permission literal (`validatePermissions`). It checks group IRIs against admin data and
  reveals no object data.

The dsp-ingest asset check during parsing (`ValueMessagesV2.scala:710-729`) also runs before the gate. It is the
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
  `LevelDenied(level, on)`. Anonymous callers never reach it: `within` refuses them first.
  The gate maps it to exceptions in one place.
- For `UpdateValue` it picks Modify or ChangeRights from the `PermissionsChange` and the current literal. It adds
  Modify on the resource for link properties, which replaces the hand-written check at
  `ValuesResponderV2.scala:~685`. A Modify-only caller who changes both content and permissions gets 403 and no
  partial write.

### Endpoints

- Gated endpoints declare `Policy.ObjectWrite(requirement)`. The requirement is one of `ObjectWriteRequirement`'s
  cases: `OnValue(level)`, `OnValueUpdate` (Modify on the value, ChangeRights when the permissions change, and
  Modify on the resource for a link), `OnResource(level)` or `CreateResource`. Each renders the sentence the endpoint
  description must contain, so the description names every level the gate can require.
- They are built by the new factory `objectWritePolicyEndpoint(requirement)`. It refuses anonymous callers with 401
  and inactive users, and hands the handler a `User`; the gate is the enforcement point.
- `Policy.ObjectAccess` and `objectAccessPolicyEndpoint` (`Policy.scala:32`, `BaseEndpoints.scala:122`) stay for
  endpoints whose object check is not the gate, such as reads.
- The erase endpoints use `systemAdminPolicyEndpoint` (values) and `projectAdminPolicyEndpoint("the resource's
  project")` (resources).

### Status-code changes (for release notes)

Changes:

| Endpoint | Caller | Before | After |
| --- | --- | --- | --- |
| all migrated v2 value and resource writes except the value erase endpoints, plus `GET /v2/resources/candelete` | no credentials | 403 "Anonymous users aren't allowed..." (candelete: 200 `canDo: false`) | 401 |
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
| `POST /v2/resources/erase` | not an admin of the resource's project, resource hidden | 403 "see one or more resources" | 403 from the role check |
| `POST /v2/values/erase`, `/erasehistory` | not a SystemAdmin, malformed body | 400 (body parsed before the role check) | 403 |
| `POST /v2/values/erase`, `/erasehistory` | shortcode names a project the resource is not in | erases in the named project's graph (no effect or wrong graph) | 400 |
| `POST /v2/values`, resource writes | caller with restricted view on the resource | 403 "does not have M" or "does not have V" | 403 with the level message |
| resource writes | resource hidden or missing | 403 "see one or more resources: `<iri>`" / 404 "One or more resources were not found" | 403 / 404 with the gate's messages (wording only) |
| value writes | resource missing | 404 "Expected `<iri>`, but no resources were returned" or "One or more resources were not found" | 404 with the gate's message (wording only) |

Unchanged on purpose:

- the value erase endpoints already answer 401 without credentials (`securedEndpoint`);
- ontology answers for a nonexistent or link property (404 / 400) still come before 403 on create, update and
  delete;
- deleted resources and values behave as today for every value and resource write; tests pin this before each
  move;
- resource erase without credentials: Phase 6 confirms today's answer, and the table gains a row if it changes.

## Technical Considerations

- **Package and scoping.**
    - The gate lives in `slice/resources/access/` (webapi-resources). `Grant` and `Unfiltered` are plain final
    classes with `private[access]` constructors, so there is no `copy`, `apply` or `unapply`.
    - `Grant` is covariant in `T`, so helpers can take `Grant[?]`.
    - `ProjectCandidate` lives in `slice.common.api` with a `private[api]` constructor, so the gate cannot read its
    user. `withinAsProjectAdmin` passes the candidate to the public `authorizeProjectAdminById` and takes the caller
    from the returned `ProjectAdminGrant`.
- **Loads.**
    - `ValuesResponderV2.getResourceWithPropertyValues` (`:1530-1571`) moves into the access package in Phase 1 and
    stays the loader for create and update.
    - Delete, erase and reorder keep `getResources`, narrowed to the request's property.
    - Resource requests keep their preview loaders.
    - The reorder slot query must use the same "non-deleted" definition as `verifyCanonicalValues`
    (`deletionInfo.isEmpty`, `ValuesRestService.scala:162`).
- **Lock key.** `runWithIriLock` has overloads for `IRI`, `SmartIri`, `KnoraIri` and `StringValue`
  (`IriLocker.scala:75-88`). A typed `ResourceIri` must lock the same string as today's write paths, or a migrated and
  an unmigrated path could lock different keys for one resource during the phased migration. A Phase 1 test pins the
  key.
- **Hidden.** Steps 5 and 6 reproduce the read filter (no permission at all), not `checkResourcePermission`'s
  `>= View`. RV-only callers see the object and fail on the level. `AccessDecisionSpec` pins both. The status-code
  table lists the resulting message changes for RV-only callers.
- **Integrity checks that need hidden data** move behind `verify`, or into access-package helpers that return only
  `Unit`, `Boolean`, a count or a write:
    - value cardinality on create (`ValuesResponderV2.scala:206-231`) and on delete and erase (`:~1244-1260`, with
    `onlyHistory`);
    - link-value reference counts (`incrementLinkValue` `:1792`, `decrementLinkValue` `:1877`);
    - reorder slots.
    - The rules themselves (cardinality, reference counts, reorder slots) are defined in the access package,
      because `Unfiltered` is unreadable outside it; responders pass them to `verify` by name.
    - `ValueRepo.checkDuplicateOrder` (`ValueRepo.scala:178-189`) names the resource and property from the request,
    which is fine.

  Their outcomes can reveal one bit about hidden values, as a unique index does under row-level security. They run
  only after the level passed, which the ADR accepts.
- **Reorder slots.**
    - `valueHasOrder` is not on `ReadValueV2`, so a gate-owned `sparql"..."` SELECT reads `(value, order)` for every
    non-deleted value of the property.
    - The visible set's old slots are sorted as a multiset and reused as is, duplicates and gaps included, and
    assigned to the requested order.
    - Visible values without `valueHasOrder` (file values; learnings `value-has-order-optional-cardinality.md`) get
    fresh slots after the maximum over **all** non-deleted values, hidden ones included.
    - Hidden values are not written.
    - The write keeps `ValueQueries.reorderValues`' `OPTIONAL ?oldOrder` clause and takes an explicit
    `(value, slot)` list instead of the list index.
    - Duplicate slots survive a reorder, so reads must order ties deterministically. Phase 5 confirms how reads order
    values with equal `valueHasOrder` (unverified) and pins it with a test.
    - Reordering the values of a link property keeps today's behaviour, which Phase 5 pins before the change.
    - The lock is per JVM (`ValueRepo.scala:152-154`). A multi-instance race stays as it is today.
- **Messages.** 403 and 404 go through `BaseEndpoints.errorOutputs`, which sends the exception message verbatim
  (learnings `shared-tapir-error-envelope-leaks-exception-messages.md`). Every message the gate builds is fixed
  text plus request IRIs. `DuplicateValueException` and `EditConflictException` map to 400 there
  (`BaseEndpoints.scala:33-41`).
- **Lint.**
    - `check_authz.py` gains rule 3: the token `KnoraSystemInstances.Users.SystemUser` (the requesting-user handle)
    in main sources, with its own allow-list.
    - Rule 2 (any `SystemUser`) matches every rule 3 hit, so a file on the new list is also on
    `system_user_allowlist.txt`.
    - Rule 2 keeps covering the creator-IRI uses (`KnoraUserRepo.builtIn.SystemUser.id`) in `ValuesResponderV2` and
    `CreateResourceV2Handler`.
    - Rule 4: a main-source file that declares `package org.knora.webapi.slice.resources.access` must live in that
    directory. Package access is the grant's only protection, so this stops a second issuer.
- **Test data.** New tests create their own fixtures through the API, as `createThingWithInteger` does
  (`ValuesEndpointsE2ESpec.scala:673`), and never add to `anything-data.ttl` (CLAUDE.md testing guidelines;
  learnings `test-fixture-isolation-shared-data-scope.md`). Reorder tests use about ten non-alphabetical values
  (learnings `alphabetical-test-data-masks-ordering.md`).
- **Direct callers of changing signatures.** test-it compiles as one target, so each phase migrates every direct
  caller of the methods it changes in the same phase:
    - `ValuesResponderV2Spec`: about 100 calls; Phases 2, 3 and 4.
    - `ValuesEraseSpec`: Phase 2.
    - `ValueHasXmlLifecycleSpec`: Phases 3 and 4.
    - `ResourcesResponderV2Spec`: about 25 calls; Phases 6 and 7.
    - `OntologyResponderV2Spec`: `createResource` (`:4731`); Phase 7.
    - `ValueQueriesSpec` (webapi unit): the `reorderValues` query; Phase 5.

  `BulkImportParityE2ESpec` (the v3 import path) calls none of them and must stay green.
- **Conventions.** `CONVENTIONS.md`, `docs/development/dsp-api-conventions.md`,
  `docs/development/dsp-api-error-handling.md` (Style A: `Task` with dsp exceptions) and
  `docs/development/dsp-api-iri-handling.md`: no `unsafeFrom` in responders, and new SPARQL uses the `sparql"..."`
  interpolator.

## Implementation Phases

<!-- markdownlint-disable-next-line MD001 -->
#### Phase 1: The gate, its decision table and the write policy

- [ ] Move DEV-7727 and DEV-7730 to In Progress in Linear
- [ ] Create `slice/resources/access/` with these types, with the constructors restricted to `private[access]`:
    - `ObjectAccess`;
    - the `AccessRequest` sealed trait and its cases;
    - `PermissionsChange`;
    - `ObjectWriteRequirement` with its cases (`OnValue`, `OnValueUpdate`, `OnResource`, `CreateResource`);
    - the target types;
    - `Grant`;
    - `Unfiltered`;
    - `IntegrityViolation`.
- [ ] Move `getResourceWithPropertyValues` from `ValuesResponderV2` into the access package; `ValuesResponderV2` calls it from there until its operations migrate
- [ ] Implement `AccessDecision.decide` as a pure function over the inputs listed under "Levels and the decision", returning the closed outcome ADT
- [ ] Implement `ObjectAccess.within`, `withinAsProjectAdmin` and `decideOnly` with the orders described above
- [ ] Register `ObjectAccess.layer` in `core/LayersLive.scala`
- [ ] Confirm that `getResources` with a property filter keeps deleted values; if it does not, use the unfiltered `getResources` for delete, erase and reorder, and note it in the plan
- [ ] Add `AccessDecisionSpec` (webapi unit test, ZIO Test), generated as a cross product. The dimensions are:
    - every request case;
    - permission literals for Creator, ProjectMember, KnownUser and UnknownUser, an `RV`-only literal, and a literal no group of the caller matches;
    - a creator, a project member, a ProjectAdmin and a SystemAdmin (anonymous callers are refused before `decide`, and an `ObjectAccess` test covers that);
    - a link property;
    - each `PermissionsChange`;
    - and an assertion that the row count equals the product, so a new case or caller fails until it has rows.
- [ ] Add property tests to `AccessDecisionSpec`:
    - a hidden value and a missing one get the same outcome and message;
    - no message contains an IRI that is not in the request (loaded IRIs carry a marker prefix);
    - raising the caller's permission never turns permit into deny.
- [ ] Add a compile-negative spec in another package. It shows that `Grant` and `Unfiltered` cannot be constructed, copied or read, and that `grant.unfiltered` cannot be read. It asserts stable fragments per case, with a positive control
- [ ] Add a lock-key test showing that the gate and today's write paths lock the same string for one resource
- [ ] Add `Policy.ObjectWrite(requirement: ObjectWriteRequirement)` with its `requirement` sentence per case in `slice/common/api/Policy.scala`
- [ ] Add `objectWritePolicyEndpoint(requirement)` to `BaseEndpoints`; it refuses anonymous callers with 401 and inactive users
- [ ] Cover the new factory in `BaseEndpointsSpec`: no credentials 401, invalid token 401, a valid user passes, the attribute is set
- [ ] Map `Policy.ObjectWrite` to `None` in `AuthorizationMatrixE2ESpec.allowedBy`; object-level outcomes are covered by per-endpoint hidden-object tests
- [ ] Add rule 3 to `tools/lint/check_authz.py`: `KnoraSystemInstances.Users.SystemUser` in main sources outside `tools/lint/elevated_read_allowlist.txt`, comment lines skipped, stale entries failing
- [ ] Add rule 4 to `tools/lint/check_authz.py`: a main-source file declaring `package org.knora.webapi.slice.resources.access` outside `slice/resources/access/` fails
- [ ] Seed `elevated_read_allowlist.txt` with today's files, each with a reason, plus the access package
- [ ] Add the access package to `system_user_allowlist.txt` too, because rule 2 matches every rule 3 hit
- [ ] Add rule 3 and rule 4 fixture tests to `tools/lint/check_authz_test.py`. Each must be red against an unlisted use, green with the entry, and red on a stale entry
- [ ] Wire the new allow-list into `tools/lint/BUILD.bazel`
- [ ] Run `just test-unit`; it passes
- [ ] Run `just check`; it passes
- [ ] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

#### Phase 2: Value delete and erase through the gate

- [ ] Add e2e tests pinning today's outcomes for value delete and value erase on a deleted resource and on a soft-deleted value; they are green before the move and stay green
- [ ] Create the cardinality rule in the access package for the delete and erase check (`ValuesResponderV2.scala:~1244-1260`, with `onlyHistory`)
- [ ] Move `decrementLinkValue` and its `findLinkValue` use into access-package helpers that read `Unfiltered` and return only the write, for the delete path (`ValuesResponderV2.scala:~1370`, `:~1420`)
- [ ] Move `deleteValueV2` and `canRemoveValue` onto `within(DeleteValue)`. Class, type and cardinality checks take the grant; cardinality uses the access package's cardinality rule through `verify`. This removes the elevated read in `findDeletableValue` (`ValuesResponderV2.scala:~1167`)
- [ ] Move `eraseValue` and `eraseValueHistory` onto `within(EraseValue)`, carrying `onlyHistory`. The gate takes the write lock erase lacked, generates an `apiRequestId`, and answers 400 when the resource is not in the project named by the request's shortcode
- [ ] Remove `findDeletableValue`; its load and checks are now the gate's
- [ ] Migrate the direct callers in `ValuesResponderV2Spec` and `ValuesEraseSpec` to obtain grants through the gate
- [ ] Rebuild `deleteValues` with `objectWritePolicyEndpoint(OnValue(Delete))`
- [ ] Rebuild `postValuesErase` and `postValuesErasehistory` with `systemAdminPolicyEndpoint`; descriptions contain the requirement sentence
- [ ] Remove `POST /v2/values/delete`, `POST /v2/values/erase` and `POST /v2/values/erasehistory` from `endpoints-without-policy.txt`, and lower `baselineSize` to 172 in the same commit
- [ ] Add matrix rows to `AuthorizationMatrixE2ESpec` for `POST /v2/values/erase` and `POST /v2/values/erasehistory`, using a fresh value per cell
- [ ] Keep the DEV-7723 suite in `ValuesEndpointsE2ESpec` green. Its anonymous case now expects 401; adjust any RV-only expectation to the status-code table
- [ ] Add e2e tests for delete:
    - a view-only caller gets 403 before the class and type checks;
    - an RV-only caller gets the level message;
    - the creator deletes;
    - a ProjectAdmin deletes.
- [ ] Add e2e tests for erase:
    - a non-SystemAdmin gets 403;
    - a SystemAdmin erases;
    - a shortcode that does not match the resource's project answers 400 and erases nothing.
- [ ] Run `bazel test //modules/test-e2e:test --test_filter=*ValuesEndpointsE2ESpec*`; it passes
- [ ] Run `bazel test //modules/test-e2e:test --test_filter=*AuthorizationMatrixE2ESpec*`; it passes
- [ ] Run `bazel test //modules/test-it:test --test_filter=*ValuesEraseSpec*`; it passes
- [ ] Run `bazel test //modules/test-it:test --test_filter=*ValuesResponderV2Spec*`; it passes
- [ ] Run `just test-unit`; it passes
- [ ] Run `just check`; it passes
- [ ] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

#### Phase 3: Value update through the gate

- [ ] Add e2e tests pinning today's outcome for a value update on a deleted resource; green before the move and after
- [ ] Validate the submitted permission literal's syntax before the gate, and pass it as `PermissionsChange` in `UpdateValue`
- [ ] Move content updates in `updateValueV2` onto `within(UpdateValue)`
- [ ] Move permissions-only updates onto `within(ChangeValueRights)`
- [ ] Move `valueValidator.validate`, the object-class check, the list-node check and the standoff and link target checks after the grant (DEV-7730, update)
- [ ] Remove the hand-written link-update Modify-on-resource check; the gate's level rule covers it
- [ ] Move link-value reference counting for the update paths (`ValuesResponderV2.scala:~905`, `:~920`, `:~1002`, `:~1012`) into the access-package helpers
- [ ] Remove `findVisibleValue` and `checkValueAndRetrieveResourceProperties`, and with them the elevated read at `ValuesResponderV2.scala:~802`
- [ ] Migrate the direct callers in `ValuesResponderV2Spec` and `ValueHasXmlLifecycleSpec`
- [ ] Rebuild `putValues` with `objectWritePolicyEndpoint(OnValueUpdate)`; remove `PUT /v2/values` from the baseline and lower `baselineSize` to 171
- [ ] Keep the DEV-7728 suite green. Its anonymous case now expects 401; adjust any RV-only expectation to the status-code table
- [ ] Add e2e tests, each denial red before its change:
    - a Modify-only caller who submits different permissions gets 403;
    - a Modify-only caller who changes content and permissions together gets 403 and the value is unchanged;
    - a caller with ChangeRights changes the permissions;
    - the creator updates;
    - a caller without modify permission who sends a disallowed license gets 403, not 400;
    - a link update by a caller without Modify on the resource gets 403;
    - an RV-only caller gets 403 with the level message.
- [ ] Run `bazel test //modules/test-e2e:test --test_filter=*ValuesEndpointsE2ESpec*`; it passes
- [ ] Run `bazel test //modules/test-it:test --test_filter=*ValuesResponderV2Spec*`; it passes
- [ ] Run `bazel test //modules/test-it:test --test_filter=*ValueHasXmlLifecycleSpec*`; it passes
- [ ] Run `just test-unit`; it passes
- [ ] Run `just check`; it passes
- [ ] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

#### Phase 4: Value creation through the gate

- [ ] Move `createValueV2` onto `within(CreateValue)`, adding the view check before modify on the resource
- [ ] Move `valueValidator.validate`, the rdf:type check, the object-class check, the list-node check and the standoff link target checks after the grant (DEV-7730, create)
- [ ] Extend the access package's cardinality rule with the create check (`ValuesResponderV2.scala:206-231`), run through `verify`
- [ ] Move link-value reference counting for the create paths (`ValuesResponderV2.scala:~435`, `:~501`) into the access-package helpers
- [ ] Remove the elevated read at `ValuesResponderV2.scala:~151`, and remove `ValuesResponderV2.scala` from `elevated_read_allowlist.txt`. Its rule 2 entry stays for the link-value creator, with the reason updated
- [ ] Migrate the direct callers in `ValuesResponderV2Spec` and `ValueHasXmlLifecycleSpec`
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
- [ ] Run `just test-unit`; it passes
- [ ] Run `just check`; it passes
- [ ] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

#### Phase 5: Value reorder through the gate, with slot-preserving semantics

- [ ] Add e2e tests pinning today's reorder behaviour for a link property and for values with equal `valueHasOrder` (the read tie-break); green before the change
- [ ] Add the gate-owned `sparql"..."` SELECT for `(value, valueHasOrder)` over the non-deleted values of one property, with the same "non-deleted" definition as `verifyCanonicalValues`
- [ ] Implement slot assignment as described under Technical Considerations, Reorder slots
- [ ] Change `ValueQueries.reorderValues` to write an explicit `(value, slot)` list for visible values, keeping the `OPTIONAL ?oldOrder` clause
- [ ] Update `ValueQueriesSpec` and its golden SPARQL for the new query
- [ ] Add the property lookup before the gate in `ValuesRestService.reorderValues`: not found 404, link property 400, `hasStandoffLinkToValue` 400, reusing the checks `canRemoveValue` runs
- [ ] Move `ValuesRestService.reorderValues` onto `within(ReorderValues)`, generating an `apiRequestId` and taking the lock it lacked
- [ ] Rework `verifyCanonicalValues` to compare against the grant's visible values. The 400 names only visible values the request omitted and request IRIs that are not visible values. A hidden IRI and a nonexistent one give the same body (DEV-7727)
- [ ] Remove `ValuesRestService.scala` from both SystemUser allow-lists in the same commit
- [ ] Rebuild `putValuesOrder` with `objectWritePolicyEndpoint(OnResource(Modify))`; remove `PUT /v2/values/order` from the baseline and lower `baselineSize` to 169
- [ ] Add e2e tests on a fresh resource with about ten non-alphabetical values, some hidden from the caller:
    - reordering the visible ones succeeds;
    - hidden values keep their `valueHasOrder` and are not renumbered;
    - `valuesReordered` counts the visible values;
    - the response bodies for a hidden IRI and a nonexistent IRI are equal;
    - a visible value without `valueHasOrder` gets a slot after the maximum;
    - duplicate slots survive and read back in a deterministic order;
    - a caller who sees no value of the property gets the 400 without loaded IRIs;
    - an unknown property answers 404, and a link or `hasStandoffLinkToValue` property answers 400;
    - a hidden resource answers 403 "see resource", and an RV-only or no-Modify caller gets the level message;
    - no credentials gives 401.
- [ ] Keep the existing reorder suite (`ValuesEndpointsE2ESpec.scala:4311-4701`) green
- [ ] Run `bazel test //modules/test-e2e:test --test_filter=*ValuesEndpointsE2ESpec*`; it passes
- [ ] Run `bazel test //modules/test-it:test --test_filter=*ValuesResponderV2Spec*`; it passes
- [ ] Run `just test-unit`; it passes
- [ ] Run `just check`; it passes
- [ ] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

#### Phase 6: Resource metadata, authorship, deletion and erase through the gate

- [ ] Add e2e tests pinning today's outcomes for update, delete and erase on an already deleted resource; green before the moves and after
- [ ] Extract the class, conflict, in-use and delete-date checks of `canDeleteResource` into one function that takes a `Grant[ResourceToDelete]` and returns the original exceptions
- [ ] Move `updateResourceMetadataV2` and `updateResourceAuthorshipV2` onto `within(UpdateResource)`, so the class and edit-conflict checks run after modify permission
- [ ] Move `markResourceAsDeletedV2` onto `within(DeleteResource)` plus the extracted checks
- [ ] Stop `markResourceAsDeletedV2` wrapping failures into 400 through `assertGoodRequestEither`: a denial answers 403 and the other checks keep their own status
- [ ] Move `canDeleteResource` onto `decideOnly(DeleteResource)` followed by the extracted checks, with no lock. Map every failure to a 200 `CanDoResponseV2` with `canDo: false`, as today
- [ ] Move `eraseResourceV2` onto `withinAsProjectAdmin`
- [ ] Migrate the direct callers in `ResourcesResponderV2Spec`
- [ ] Rebuild these endpoints with `objectWritePolicyEndpoint`:
    - `putResources` and `putResourcesAuthorship`, with `OnResource(Modify)`;
    - `postResourcesDelete` and `getResourcesCanDelete`, with `OnResource(Delete)`.
- [ ] Rebuild `postResourcesErase` with `projectAdminPolicyEndpoint("the resource's project")`
- [ ] Remove those five endpoints from the baseline and lower `baselineSize` to 164
- [ ] Add matrix rows to `AuthorizationMatrixE2ESpec` for `POST /v2/resources/erase`, using a fresh resource per cell; the anonymous cell expects 401
- [ ] Add e2e hidden-object tests per migrated endpoint in `ResourcesRouteV2E2ESpec`, each denial red before its change:
    - hidden resource 403 with one message;
    - RV-only 403 with the level message;
    - visible without permission 403 before the 400s;
    - missing 404;
    - no credentials 401;
    - the permitted caller unchanged.
- [ ] Add an e2e test: delete without permission on a clean request gets 403 (red before: 400)
- [ ] Add e2e tests for candelete:
    - 200 `canDo: false` for hidden, missing and no-delete callers;
    - for a no-delete caller on a resource in use or of the wrong class, the reason is the gate's;
    - 401 without credentials.
- [ ] Add an e2e test: resource erase by a non-admin on a hidden resource answers 403 from the role check
- [ ] Run `bazel test //modules/test-e2e:test --test_filter=*ResourcesRouteV2E2ESpec*`; it passes
- [ ] Run `bazel test //modules/test-e2e:test --test_filter=*AuthorizationMatrixE2ESpec*`; it passes
- [ ] Run `bazel test //modules/test-it:test --test_filter=*ResourcesResponderV2Spec*`; it passes
- [ ] Run `just test-unit`; it passes
- [ ] Run `just check`; it passes
- [ ] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

#### Phase 7: Resource creation through the gate, and documentation

- [ ] Move `CreateResourceV2Handler` onto `within(CreateResource)`, with the order under "CreateResource" above (DEV-7730, resource create)
- [ ] Migrate the direct callers in `ResourcesResponderV2Spec` and `OntologyResponderV2Spec`
- [ ] Rebuild `postResources` with `objectWritePolicyEndpoint(CreateResource)`; remove `POST /v2/resources` from the baseline and lower `baselineSize` to 163
- [ ] Add e2e tests:
    - a user without create permission who sends a disallowed license gets 403, not 400;
    - no credentials gives 401;
    - a permitted caller is unchanged, including 400 for an existing custom IRI.
- [ ] Update `docs/development/dsp-api-conventions.md`: the factory table gains `objectWritePolicyEndpoint`, and a section says v2 resource and value writes go through `ObjectAccess.within`
- [ ] Update `CONVENTIONS.md` with the same rule
- [ ] Add a `REVIEW.md` item. Each of these is a finding:
    - a resource or value write that loads the resource itself;
    - a write that answers from loaded data outside a grant;
    - a write that takes a written IRI from the request rather than the grant;
    - a grant kept beyond its closure.
- [ ] Update `docs/contexts/resources-values/CONTEXT.md` with the **Grant** and **Access request** terms
- [ ] Update the endpoint docs: in `docs/03-endpoints/api-v2/editing-values.md` and `editing-resources.md`, add the new permission lines, the 401, and the reorder semantics
- [ ] Update `ARCH-MAP.md` for webapi-resources, webapi-common and webapi-api with the `dune:dune-map` skill: add the access package, and rewrite the "There is no single chokepoint" note to say that v2 resource and value writes have one chokepoint, `ObjectAccess`, while read enforcement is still split
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
| H1 | Decide the release, and put the status-code table above in the release notes | Ivan | after ship | Release timing is an owner decision |

## Acceptance Criteria

- [ ] Every v2 value write runs through `ObjectAccess.within`: create, update, permission change, delete, erase, erase history and reorder
- [ ] Every v2 resource write runs through the gate: create, metadata and authorship through `within`, delete through `within`, and erase through `withinAsProjectAdmin`
- [ ] `candelete` runs through `decideOnly`, takes no lock and answers 200 for denied cases
- [ ] No migrated responder or RestService method takes a `User` and reads the resource itself; they take a `Grant[T]`
- [ ] Every migrated write takes the IRIs it writes from the grant
- [ ] `ValuesRestService.scala` is on neither SystemUser allow-list
- [ ] `ValuesResponderV2.scala` is not on `elevated_read_allowlist.txt`
- [ ] Each request kind keeps today's loader, so deleted resources and values behave as today; the pinning tests stay green
- [ ] Endpoint baseline: the 12 endpoints are removed and `baselineSize` is 163
- [ ] `AccessDecisionSpec` covers the full cross product, including an RV-only caller and a link property, and its property tests pass
- [ ] The compile-negative spec is red when a constructor becomes public
- [ ] Each new denial e2e test was red before its change
- [ ] Response bodies for a hidden value and a nonexistent one are equal wherever the gate answers 404 or 400
- [ ] A reorder by a caller with hidden values succeeds, and the hidden values keep their `valueHasOrder`
- [ ] `ValuesEraseSpec`, `ValueHasXmlLifecycleSpec`, `ResourcesResponderV2Spec`, `OntologyResponderV2Spec` and `BulkImportParityE2ESpec` pass
- [ ] Lint rules 3 and 4 exist with fixture tests, and `elevated_read_allowlist.txt` is wired into `just check`
- [ ] Every migrated endpoint except the value erase endpoints answers 401 without credentials where it answered 403
- [ ] Value create, value update and resource create answer 403 before validation for a caller without the required permission (DEV-7730)
- [ ] Value erase takes the write lock and answers 400 when the resource is not in the shortcode's project
- [ ] `docs/development/dsp-api-conventions.md`, `CONVENTIONS.md`, `REVIEW.md`, `CONTEXT.md`, the endpoint docs and `ARCH-MAP.md` describe the gate
- [ ] `just test-unit`, `just test-it` and `just check` pass
- [ ] `just test-e2e` passes, apart from the known local `ValueHasXmlOnValueWriteE2ESpec` timeout

## Dependencies & Risks

- **Depends on PR #4410** (ADR-0013, `Policy`, the factories, the ratchet, the lint). This plan's PR is stacked on
  it.
- **The `ValuesResponderV2` rewrite is large** (about 2000 lines). The phases follow the operations, so each phase
  is one reviewable change. The existing value e2e suites (more than 100 tests) are the regression net.
  `canRemoveValue` is shared by delete and erase, so both move in Phase 2.
- **test-it compiles as one target.** A phase that changes a responder signature must migrate every direct caller
  in the same phase, or every test-it suite fails to compile.
- **Behaviour changes for clients:**
    - 401 instead of 403 for anonymous callers;
    - 403 before 400;
    - `POST /v2/resources/delete` answers 403 instead of 400 for a denial;
    - RV-only callers get the level message;
    - reorder no longer fails because of hidden values;
    - 404 wording is unified.
  The status-code table goes in the release notes (H1).
- **Resource writes load as `SystemUser` instead of as the caller.** The gate computes permissions from the loaded
  literals; a mistake there would grant everyone the system's maximum. `AccessDecisionSpec` and the per-endpoint
  hidden-object tests guard this.
- **A grant escaping its closure, or `Unfiltered` data piped into a response,** is a review item, not a type error
  (ADR-0013 Addendum 1, Consequences).

## Success Metrics

- **SystemUser allow-lists:**
    - Today: 10 files on rule 2.
    - After the plan: `ValuesRestService.scala` is off both lists, and the access package is on both, so rule 2
      still lists 10 files and rule 3 lists 3 (the access package, `ReadResourcesServiceLive`, `ValueHasXmlRenderer`).
    - The new elevated-read list holds only the access package, `ReadResourcesServiceLive` and
      `ValueHasXmlRenderer`, none of them a resource or value write.
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
- Resource create: `responders/v2/resources/CreateResourceV2Handler.scala:78-98`, with the ceilings at `:356` and `:707`
- Reorder:
    - `slice/api/v2/values/ValuesRestService.scala:102-177`
    - `slice/resources/repo/service/ValueQueries.scala:147-182`
- Lock: `responders/IriLocker.scala:37-166`
- Permission evaluation:
    - `responders/v2/ResourceUtilV2.scala:37-92`
    - `messages/util/PermissionUtilADM.scala:90-142`
    - the read filter in `messages/util/ConstructResponseRdfDataParser.scala:56-125` and `:235-247`
- Hidden and missing answers: `messages/v2/responder/resourcemessages/ResourceMessagesV2.scala:883-917`
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
