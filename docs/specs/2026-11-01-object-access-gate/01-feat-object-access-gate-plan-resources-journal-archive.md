# Execution Journal Archive: 01-feat-object-access-gate-plan-resources

<!-- markdownlint-disable-file MD024 -->

## Phase 6

### Chunk queue

| 6.1 | dsp-api | ResourcesRouteV2E2ESpec | — | pin update/delete/erase on a deleted resource | green on today's code | ResourcesRouteV2E2ESpec "mark a resource as deleted" | — |
| 6.2 | dsp-api | access/ObjectAccess, ObjectAccessSpec | — | (enabler for candelete) | decideOnly hands its check the caller's target, no lock | ObjectAccess.decideOnly | — |
| 6.3 | dsp-api | ResourcesResponderV2, ResourcesResponderV2Spec, ValuesEraseSpec | 6.2 | extract delete checks; metadata+authorship onto within(UpdateResource); delete onto within(DeleteResource); no assertGoodRequestEither; candelete onto decideOnly; erase onto withinAsProjectAdmin; migrate spec callers | test-it ResourcesResponderV2Spec green | ResourcesResponderV2.scala:108-505 | — |
| 6.4 | dsp-api | ResourcesEndpoints | 6.3 | candelete to authenticatedObjectAccessPolicyEndpoint; enforcedIn + level descriptions | EndpointPolicyE2ESpec, matrix green | ResourcesEndpoints.scala:207-277 | — |
| 6.5 | dsp-api | ResourcesRouteV2E2ESpec | 6.3, 6.4 | hidden-object e2e per endpoint; Modify-only vs ChangeRights; delete clean 403; candelete 200/401 | red against 71250f989 main sources, green after | ResourcesRouteV2E2ESpec | — |
| 6.6 | dsp-api | — | 6.5 | run ResourcesRouteV2E2ESpec, AuthorizationMatrixE2ESpec, ResourcesResponderV2Spec, test-unit, check | all pass | — | — |

### Chunks

| 6.1 | dsp-api | done | 6b81b6e60 | pin update/authorship/delete 400 and erase 200 on a deleted resource (delete date must be past, or the preview does not mark it deleted) | — |
| 6.2 | dsp-api | done | 2f7de82dc | decideOnly takes a check over the target (caller's view), no lock, no grant | — |
| 6.3 | dsp-api | done | df708e0ba | metadata/authorship via within(UpdateResource), delete via within(DeleteResource), candelete via decideOnly, erase via withinAsProjectAdmin; ensureDeletable/ensureClass after the decision; no assertGoodRequestEither; ResourcesResponderV2Spec unchanged and green (79) | — |
| 6.4 | dsp-api | done | fcff93210 | candelete on authenticatedObjectAccessPolicyEndpoint (401 anonymous); enforcedIn "ObjectAccess, per resource" and level descriptions on the four gated endpoints | — |
| 6.5 | dsp-api | done | 448a3e2d9 | gate e2e tests per endpoint (hidden/RV/view-only/missing/creator), ChangeRights, clean-delete 403, candelete reasons and 401; all 17 denial tests red on 71250f989 main sources, green after | — |
| 6.7 | dsp-api | done | 0b458d163 | review fix 1 (security Warning): validate the submitted permission literal before the gate (400 for every caller, was 500 inside the decision); e2e red before; test rename | — |
| 6.8 | dsp-api | done | 107e98875 | review fix 1 (dune Suggestion): decideOnly Scaladoc says check must not write | — |
| 6.6 | dsp-api | done | — | after fix round 1: ResourcesRouteV2E2ESpec 76/76, ResourcesResponderV2Spec 79/79, ValuesEraseSpec 8/8, ValueHasXmlLifecycleSpec 6/6, EndpointPolicyE2ESpec 7/7, AnonymousWritesE2ESpec 8/8, AuthorizationMatrixE2ESpec green except 24 environmental /ingest/ rows (same on 71250f989), just test-unit and just check pass | — |

## Phase 7

### Chunk queue

| 7.1 | dsp-api | CreateResourceV2Handler.scala | — | Move CreateResourceV2Handler onto within(CreateResource); Migrate the direct callers | gate decides create permission before validator, class check, standoff targets, IRI check, lock, ceilings; ResourcesResponderV2Spec and OntologyResponderV2Spec pass with unchanged call sites | CreateResourceV2Handler.scala § apply/create | — |
| 7.2 | dsp-api | ResourcesEndpoints.scala | 7.1 | Set enforcedIn of postResources | enforcedIn names ObjectAccess; description names the project create permission | ResourcesEndpoints.scala § postResources | — |
| 7.3 | dsp-api | ResourcesRouteV2E2ESpec.scala | 7.1 | Add e2e tests; run gates | no-create-permission caller with a disallowed license or cross-project link gets 403 (red before: 400); permitted caller keeps 400; duplicate custom IRI 400 | ResourcesRouteV2E2ESpec.scala § create tests | — |

### Chunks

| 7.1 | dsp-api | done | 8afa177e4 | CreateResourceV2Handler.apply looks up the KnoraProject and calls within(CreateResource); validator, class check, standoff targets, IRI check, lock and ceilings run in `create(request, grant)` in the plan's order; own anonymous and permission checks removed. Public signature kept, so the spec call sites need no change; ResourcesResponderV2Spec 79 tests pass | — |
| 7.2 | dsp-api | done | add56ef95 | postResources enforcedIn "ObjectAccess, on the project and resource class"; description names the project's create permission | — |
| 7.3 | dsp-api | done | 95da0cbc2 | e2e: no-create caller (normalUser) with a disallowed license (TextFile, AI_GENERATED) or a cross-project link gets 403, anythingUser1 400; both red on the reverted handler; ResourcesRouteV2E2ESpec 78 tests pass. Existing duplicate-custom-IRI test covers the permitted 400 | — |
| 7.4 | dsp-api | done | 2332ed0ef, 3f806a023 | review fix round 1: apply's Scaladoc no longer claims nothing runs before the gate (the public project lookup does); e2e pins that a no-create caller sending an existing custom IRI gets 403, the permitted caller 400. ResourcesRouteV2E2ESpec 79 tests, OntologyResponderV2Spec 144, just test-unit and just check pass | — |
