# Execution Journal Archive: 01-feat-object-access-gate-plan

<!-- markdownlint-disable-file MD024 -->

## Phase 1

### Chunk queue

| 1.1 | dsp-api | tools/lint/check_authz.py, check_authz_test.py, elevated_read_allowlist.txt (new), BUILD.bazel | — | "rule 4", "rule 5", "fixture tests", "Wire the new allow-list" | rules 4 and 5 red on unlisted use / stale entry, green with entry; just check green | plan § Technical Considerations "Lint"; check_authz.py check() | — |
| 1.2 | dsp-api | slice/resources/access/{AccessRequest,Grant}.scala; test slice/resources/GrantAccessSpec.scala | — | "Create slice/resources/access/ with these types" (all but ObjectAccess), "compile-negative spec" | Grant/Unfiltered/targets not constructible, copyable or readable outside access; positive control compiles | plan § Proposed Solution sketch; test precedent slice/common/ProjectAdminGrantAccessSpec.scala | — |
| 1.3 | dsp-api | slice/resources/access/ObjectLoader.scala, ValuesResponderV2.scala, core/LayersLive.scala, both allow-lists | 1.1, 1.2 | "Move getResourceWithPropertyValues", "Seed elevated_read_allowlist", "Add those access-package files to system_user_allowlist" | ValuesResponderV2 loads through the access package; lint green | ValuesResponderV2.scala:1541 getResourceWithPropertyValues | — |
| 1.4 | dsp-api | slice/resources/access/AccessDecision.scala; test slice/resources/access/AccessDecisionSpec.scala | 1.2 | "Implement AccessDecision.decide", "Add AccessDecisionSpec", "Add property tests" | table rows == product; hidden==missing value; no foreign IRI in messages; monotone | plan § "Levels and the decision", § "Order inside within" | — |
| 1.5 | dsp-api | slice/resources/access/ObjectAccess.scala, core/LayersLive.scala, allow-lists; tests ObjectAccessSpec, lock-key test | 1.3, 1.4 | "Create ... ObjectAccess", "Implement ObjectAccess.within, withinAsProjectAdmin and decideOnly", "Register ObjectAccess.layer", "lock-key test" | anonymous refused before load; gate locks the same key as today's write paths | plan § "Order inside within", § "withinAsProjectAdmin", § CreateResource | — |

### Chunks

| 1.1 | dsp-api | complete | 0550528d0 | check_authz rules 4 (requesting SystemUser handle, own allow-list) and 5 (access package home), fixture tests, BUILD wiring, allow-list seeded with today's four files | none |
| 1.2 | dsp-api | complete | 598e9e7d6 | access package types: AccessRequest cases, PermissionsChange, Grant, Unfiltered, IntegrityViolation, target types; GrantAccessSpec compile-negative spec | none |
| 1.3 | dsp-api | complete | 05448a570 | getResourceWithPropertyValues moved to access/ObjectLoader (always SystemUser); ValuesResponderV2 calls it; ObjectLoader layer in LayersLive and on both allow-lists | none |
| 1.4 | dsp-api | complete | 2af75d5b7 | AccessDecision.decide pure outcome ADT + failure messages from request IRIs; AccessDecisionSpec cross-product table vs oracle, row count = product, property tests | none |
| 1.5 | dsp-api | complete | f358ca072 | ObjectAccess within / withinAsProjectAdmin / decideOnly; ObjectLoader property and preview loads; layer in LayersLive; ObjectAccessSpec (anonymous before load, lock key) | none |
| 1.6 | dsp-api | complete | c51bb1a5e | review fix round 1: rule 4 also matches Users.SystemUser and resourceWithPropertyValues calls; rule 5 anchored to webapi main sources | none |
| 1.7 | dsp-api | complete | 49f8884ee | review fix round 1: Level alias for Permission.ObjectAccess, no fall-through Permit, checkAll over the table, literal-normalisation test | none |
| 1.8 | dsp-api | complete | 148c93242 | review fix round 1: update targets carry PermissionsChange, hidden link target stripped from requested value, fail-closed view level, narrower NotFound absorption, shared load-and-decide, anonymous tests assert the refusal | none |
| 1.9 | dsp-api | complete | f0f859fb2 | review fix round 2: rule 4 hint, BUILD comment and allow-list header name the loader call; docstring rewrapped | none |
| 1.10 | dsp-api | complete | a231e8448 | review fix round 2: ObjectLoaderSpec pins the missing-resource message ObjectLoader matches; Grant doc rewrapped | none |

### Settled deferrals

- [settled] Phase 1: review Warning (security, Scala/ZIO): the grant target's values and nested link targets keep the SystemUser
  `userPermission` from the load; only the resource's is rewritten (ReadValueV2 has a dozen case classes and no common
  copy). No write reads it yet; added as a `- [ ]` item to Phase 2, the first consumer.
  settled: 2026-11-24, callerView rewrites it on kept values and link targets (ReadValueV2 has three case classes), commit 8f2407d4c

## Phase 2

### Chunk queue

| 2.1 | dsp-api | slice/resources/access/ObjectAccess.scala, ObjectAccessSpec.scala | — | P2 #1 (userPermission) | grant target's values and nested link targets carry the caller's level; unit test | plan Phase 2; journal Deferrals Phase 1 | — |
| 2.2 | dsp-api | test-e2e ValuesEndpointsE2ESpec | — | P2 #2 (pins) | delete and erase on a deleted resource and a soft-deleted value pinned, green before the move | plan Phase 2 | — |
| 2.3 | dsp-api | slice/resources/access/ (new rules file), its spec, ValuesResponderV2 decrementLinkValue | — | P2 #3, #4 | cardinality rule and link-value decrement helper in the access package; unit tests | plan Technical Considerations "Integrity checks" | — |
| 2.4 | dsp-api | ValuesResponderV2, ObjectAccess.scala, test-it callers | 2.1, 2.2, 2.3 | P2 #5, #6, #7, #8 | delete and erase run inside within(DeleteValue/EraseValue); findDeletableValue gone; pins green | plan Phase 2 | — |
| 2.5 | dsp-api | slice/api/v2/values/ValuesEndpoints.scala | 2.4 | P2 #9 | enforcedIn names the gate; descriptions name Delete level, SystemAdmin role, project check | plan Endpoints | — |
| 2.6 | dsp-api | test-e2e ValuesEndpointsE2ESpec | 2.4 | P2 #10, #11, #12 | new delete and erase e2e tests; RV-only expectations per status table | plan status-code table | — |
| 2.7 | dsp-api | — | 2.1–2.6 | P2 #13–#19 (test runs) | named suites, just test-unit, just check pass | plan Phase 2 | — |

### Chunks

| 2.2 | dsp-api | done | dfe09e99b | e2e pins: delete and erase of a soft-deleted value and of a value of a deleted resource answer 200, green before the move | — |
| 2.1 | dsp-api | done | 8f2407d4c | callerView rewrites userPermission on kept values and link targets; valueInView too; ObjectAccessSpec tests (red without the change) | — |
| 2.3 | dsp-api | done | 45f2bce8f | IntegrityRules.valueRemovable and LinkValueWrites.decrement read Unfiltered; responder's update-path decrement reuses LinkValueWrites.decremented; unit specs | — |
| 2.4 | dsp-api | done | 880c0f416 | deleteValueV2, eraseValue, eraseValueHistory run inside within(DeleteValue/EraseValue); checkRemoval on the grant; findDeletableValue and canRemoveValue removed; DeleteValue loads all values; signatures kept, so test-it callers unchanged; ValuesEraseSpec, ValuesResponderV2Spec, ValueHasXmlLifecycleSpec and ValuesEndpointsE2ESpec green | — |
| 2.5 | dsp-api | done | b85cd14f7 | deleteValues enforcedIn names ObjectAccess (description already names View and Delete); erase descriptions name SystemAdmin and the project check | — |
| 2.6 | dsp-api | done | 8158b505c | new e2e tests for delete (view-only, RV on resource and value, creator, ProjectAdmin) and erase (SystemAdmin, mismatched shortcode 400 erasing nothing, fixture inserted by SPARQL); existing delete suite green without changes | — |
| 2-review-1 | dsp-api | done | 310ed882e | review fix round 1: findVisibleValue comment names value update only; named onlyHistory argument; spec tidy-ups | — |
| 2.7 | dsp-api | done | — | final gates after review: just test-unit, the three test-it suites (114 tests), ValuesEndpointsE2ESpec green, just check green; AuthorizationMatrixE2ESpec green except its 24 /ingest rows (local docker socket), checkbox left open | — |

## Phase 3

### Chunk queue

| 3.1 | dsp-api | ValuesEndpointsE2ESpec.scala | — | pin deleted-resource update | update on a deleted resource and on a soft-deleted value pinned, green before the move | plan Phase 3, checkbox 1 | — |
| 3.2 | dsp-api | access/LinkValueWrites.scala, access/IntegrityRules.scala, their specs | — | link counting helpers, duplicate-link rule | increment/changeMetadata on the grant and IntegrityRules.noLinkTo read Unfiltered; unit tests | plan Phase 3, checkboxes 7-8 | — |
| 3.3 | dsp-api | ValuesResponderV2.scala, access/LinkValueWrites.scala | 3.1, 3.2 | permission literal before gate, UpdateValue, ChangeValueRights, validation after grant, link check removed, ensureNoLinkTo via verify, link counting, remove findVisibleValue | updateValueV2 runs inside within; it suites green | plan Phase 3, checkboxes 2-6, 9-10 | — |
| 3.4 | dsp-api | ValuesEndpoints.scala | 3.3 | putValues enforcedIn and description | enforcedIn names ObjectAccess | plan Phase 3, checkbox 11 | — |
| 3.5 | dsp-api | ValuesEndpointsE2ESpec.scala | 3.3 | PUT suite green, new e2e tests | e2e suite green | plan Phase 3, checkboxes 12-13 | — |

### Chunks

| 3.1 | dsp-api | done | 7aaf8ce26 | e2e pins: update of a soft-deleted value and of a value of a deleted resource answer 404 | — |
| 3.2 | dsp-api | done | 92c63e83f | LinkValueWrites.increment/changeMetadata/incremented and IntegrityRules.noDuplicateLink over Unfiltered, with unit tests (mutation canary red) | — |
| 3.3 | dsp-api | done | 654cff837 | updateValueV2 through within(UpdateValue)/within(ChangeValueRights); literal validated before the gate; validation on the grant; duplicate link via verify; link counts via LinkValueWrites; findVisibleValue, checkValueAndRetrieveResourceProperties, decrementLinkValue, changeLinkValueMetadata removed; LinkValueWrites.decremented private; it suites (110) and values e2e (139) green | — |
| 3.4 | dsp-api | done | c6fbe09aa | putValues enforcedIn names ObjectAccess; description names Modify, ChangeRights and the link rule | — |
| 3.5 | dsp-api | done | 806d3289b | 8 e2e tests for PUT /v2/values through the gate; 4 denials red against the pre-move responder (permissions-only Modify, RV-only, cross-project link, link without resource Modify); the content-and-permissions and creator denials were already enforced | — |
| 3-review-1 | dsp-api | done | 66a395533, 19e1877b5, 40ffb244b | review fixes: one LinkValue update builder; duplicate-link message keeps 'via property'; updateValueV2 split per request kind; ChangeRights and creator e2e tests read back; same-permissions Modify test; rule spec covers another property | — |
| 3-review-2 | dsp-api | done | 281cea724, 4b903117b | re-review fixes: LinkValue builder takes a named Reference (Added, Kept, Removed) and deletes the direct link only on Removed; checkUpdateTarget doc states it runs inside the gate | — |

## Phase 4

### Chunk queue

| 4.1 | dsp-api | access/IntegrityRules.scala, IntegrityRulesSpec | — | create cardinality rule | IntegrityRules.valueAddable over Unfiltered, unit tests red without it | plan Phase 4, checkbox 3 | — |
| 4.2 | dsp-api | ValuesResponderV2.scala, access/LinkValueWrites.scala, access/ObjectLoader.scala, tools/lint allow-lists | 4.1 | createValueV2 through within(CreateValue); validation after the grant; cardinality and duplicate link via verify; link counting via LinkValueWrites; dead helpers removed | it suites green; ValuesResponderV2 off the elevated-read allow-list | plan Phase 4, checkboxes 1-2, 4-7 | — |
| 4.3 | dsp-api | tools/lint/check_authz.py, check_authz_test.py | 4.2 | rule 4 no longer matches the removed loader method | lint tests green | plan Phase 4, checkbox 6 (follow-up) | — |
| 4.4 | dsp-api | ValuesEndpoints.scala | 4.2 | postValues enforcedIn and description | enforcedIn names ObjectAccess | plan Phase 4, checkbox 8 | — |
| 4.5 | dsp-api | ValuesEndpointsE2ESpec.scala | 4.2 | new suite POST /v2/values checks permissions before revealing details | denials red against the pre-move responder; e2e suite green | plan Phase 4, checkboxes 9-10 | — |

### Chunks

| 4.1 | dsp-api | done | 454bd8cbd | IntegrityRules.valueAddable (refuses when loaded values, hidden included, reach the cardinality's max), 3 unit tests, mutation canary red | — |
| 4.2 | dsp-api | done | d469e047a | createValueV2 through within(CreateValue); literal validated before the gate; validator, type, object-class, list-node, standoff checks and default-permission ceiling inside the write; valueAddable and noDuplicateLink via verify; LinkValueWrites.increment; findLinkValue, ensureNoLinkTo, incrementLinkValue, LinkValueWrites.incremented, ObjectLoader.resourceWithPropertyValues removed; ValuesResponderV2 off the elevated-read allow-list; ValuesResponderV2Spec (100), ValueHasXmlLifecycleSpec (6), ValuesEraseSpec+ResourcesResponderV2Spec (87) green without changes | — |
| 4.3 | dsp-api | done | 8ea138e09 | check_authz rule 4 matches only Users.SystemUser; hint, docstring, BUILD comment and fixture test updated | — |
| 4.4 | dsp-api | done | b7df37130 | postValues enforcedIn names ObjectAccess; description names view and modify on the resource | — |
| 4.5 | dsp-api | done | bcc93fd87 | 5 e2e tests in POST /v2/values checks permissions before revealing details; 3 denials red against the pre-move responder (hidden resource with an invalid value, RV-only, cross-project link without Modify); cardinality (hidden value counted) and duplicate link 400s were already enforced; values e2e 144 green; just test-unit and just check green | — |
| 4-review-1 | dsp-api | done | f8e0b6967, 5b3da5961, 44be8fb10 | review round 1: valueAddable unit test for a missing required value (scala-zio Warning); lock comment names the gate's lock (consistency); duplicate-link e2e posts as a caller who cannot see the link (scala-zio); values e2e 144 green | — |

## Phase 5

### Chunk queue

| 5.1 | dsp-api | modules/test-e2e/.../ValuesEndpointsE2ESpec.scala | - | 1 | link-property reorder and equal-order tie pinned, green on f6d380d3c | plan Phase 5; e2e reorder suite :4348 | - |
| 5.2 | dsp-api | access/ReorderSlots.scala, access/ObjectLoader.scala, access/ObjectAccess.scala, core/LayersLive (if needed), access test ReorderSlotsSpec | - | 2, 3 | sparql SELECT of (value, order) over non-deleted values; pure slot assignment unit-tested | plan Technical Considerations, Reorder slots | - |
| 5.3 | dsp-api | repo/service/ValueQueries.scala, ValueRepo.scala, ValueQueriesSpec.scala, ValuesRestService.scala, ValueHasXmlLifecycleSpec.scala | - | 4, 5 | reorderValues takes (value, slot) list; golden SPARQL updated; callers pass index slots | ValueQueries.scala:147-182 | - |
| 5.4 | dsp-api | ValuesResponderV2.scala, ValuesRestService.scala, ValuesEndpoints.scala, tools/lint allow-lists, ValuesRestServiceSpec.scala, ValueHasXmlLifecycleSpec.scala | 5.2, 5.3 | 6, 7, 8, 9, 10, 11 | reorder runs inside within(ReorderValues) with the pre-gate property checks; 400 names only visible/request IRIs; lint passes | ValuesRestService.scala:104-180 | - |
| 5.5 | dsp-api | modules/test-e2e/.../ValuesEndpointsE2ESpec.scala | 5.4 | 12, 13 | gated reorder e2e cases green, red against f6d380d3c where behaviour changed | plan Phase 5 checklist | - |

### Chunks

| 5.1 | dsp-api | done | 9ced296d8 | e2e pins: link value property reorder, link property 400, equal valueHasOrder reads by IRI; green on f6d380d3c | - |
| 5.2 | dsp-api | done | 213d8334d | ReorderSlots (SELECT + assign) in the access package; ObjectLoader.valueOrders, ObjectAccess.reorderSlots; ValuesToReorder carries its property | - |
| 5.3 | dsp-api | done | 487b9717f | ValueQueries/ValueRepo.reorderValues take a (value, slot) list; golden updated; callers pass positions | - |
| 5.4 | dsp-api | done | e4a49cbf1 | reorder via ValuesResponderV2.reorderValues inside within(ReorderValues); pre-gate property checks shared with delete/erase; visible-only 400; allow-lists and endpoint updated; lifecycle spec calls the responder | - |
| 5.5 | dsp-api | done | 16501b904 | 7 gated reorder e2e cases (154 tests green); all 7 red against the pre-gate reorder | - |
| 5-review-1 | dsp-api | done | 15472351f, 9c7c7abb6 | review round 1: valueOrders fails with InconsistentRepositoryDataException; reorderSlots owns the visible-values 400 and refuses duplicates (consistency, scala-zio, DUNE-001); stale ValuesServerEndpoints deps trimmed | - |
| 5-review-2 | dsp-api | done | 43a9ec7ac | re-review round 2: neutral valueOrders failure message; mismatch checks duplicates first (consistency, simplicity) | - |
