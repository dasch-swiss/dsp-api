---
plan: docs/specs/2026-11-01-object-access-gate/01-feat-object-access-gate-plan.md
target_repo: /Users/raitisveinbahs/work/dsp-api/.claude/worktrees/object-access-gate
base_commit: 1ff5f1dd2cd9bc4ef6c7d5083cd02bb640da34f8
branch: plan/object-access-gate
started: 2026-11-24
problem: >
  The v2 resource and value write handlers each load, check and answer in their own order, so the same
  authorization bug (answering about an object, or validating, before the permission check) shipped four
  times in one month (DEV-7723, DEV-7727, DEV-7728, DEV-7730). The plan routes every v2 resource and value
  write through one object-access gate, ObjectAccess.within, which loads as SystemUser, decides visibility
  and the permission level in a fixed order, and only then hands the write a Grant[T] (ADR-0013 Addendum 1).
symptoms:
status: in-progress
autonomy: decide
---

# Execution Journal: 01-feat-object-access-gate-plan

## Repos

| repo | base_commit | branch | merge_strategy | status | pr |
| --- | --- | --- | --- | --- | --- |
| dsp-api | 1ff5f1dd2 | plan/object-access-gate | rebase | in-progress | — |

## Phases

| phase | status | phase_base | review_fix_rounds |
| --- | --- | --- | --- |
| 1 | reviewed | dsp-api@3d3bc22fd | 2 |
| 2 | reviewed | dsp-api@71250f989 | 1 |
| 3 | reviewed | dsp-api@f3c390b84 | 2 |
| 4 | reviewed | dsp-api@7671e8991 | 1 |
| 5 | reviewed | dsp-api@f6d380d3c | 2 |
| 6 | reviewed | dsp-api@71250f989 (resources track) | 1 |
| 7 | reviewed | dsp-api@9fcb80717 (resources track) | 1 |
| Closing | in-progress | dsp-api@fd2b3a901 | 1 |

## Archived phases

- Phase 1: 10 chunks, ids 1.1 to 1.10, highest number 1.10, in 01-feat-object-access-gate-plan-journal-archive.md
- Phase 2: 8 chunks, ids 2.1 to 2-review-1, highest number 2.7, in 01-feat-object-access-gate-plan-journal-archive.md
- Phase 3: 7 chunks, ids 3.1 to 3-review-2, highest number 3.5, in 01-feat-object-access-gate-plan-journal-archive.md
- Phase 4: 6 chunks, ids 4.1 to 4-review-1, highest number 4.5, in 01-feat-object-access-gate-plan-journal-archive.md
- Phase 5: 7 chunks, ids 5.1 to 5-review-2, highest number 5.5, in 01-feat-object-access-gate-plan-journal-archive.md
- Phase 6: 8 chunks, ids 6.1 to 6.8, highest number 6.8, in 01-feat-object-access-gate-plan-resources-journal-archive.md (resources track; its commits are cherry-picked onto this branch with new SHAs, chunk C.1)
- Phase 7: 4 chunks, ids 7.1 to 7.4, highest number 7.4, in 01-feat-object-access-gate-plan-resources-journal-archive.md (resources track; same)

## Chunk queue

| id | repo | files | depends_on | checkboxes | acceptance | context | replaces |
| --- | --- | --- | --- | --- | --- | --- | --- |
| C.1 | dsp-api | resources-track commits 6b81b6e60..3f806a023 (cherry-pick), plan ticks, journal fold | — | Closing: merge the tracks | test-unit green, webapi + test-it compile | plan "Execution tracks" | — |
| C.2 | dsp-api | docs/development/code-conventions.md, CONVENTIONS.md, REVIEW.md | C.1 | Closing: code-conventions, CONVENTIONS, REVIEW item | just check green | plan Closing | — |
| C.3 | dsp-api | docs/contexts/resources-values/CONTEXT.md | C.1 | Closing: CONTEXT terms | just check green | plan Closing | — |
| C.4 | dsp-api | docs/03-endpoints/api-v2/editing-values.md, editing-resources.md | C.1 | Closing: endpoint docs | just check green | plan status-code table; Phase 5 deferral | — |
| C.5 | dsp-api | ARCH-MAP.md | C.1 | Closing: ARCH-MAP | dune-map skill | plan Closing; side findings | — |
| C.6 | dsp-api | docs/adr/0013-authorization-policy-enforcement.md | C.1 | Closing: ADR Not yet in code | just check green | plan Closing | — |
| C.7 | dsp-api | (no edits) test runs | C.1-C.6 | Closing: BulkImportParity, test-unit, test-it, test-e2e, check | suites green bar known env failures | plan Closing | — |

## Chunks

| id | repo | status | commit(s) | summary | blocker |
| --- | --- | --- | --- | --- | --- |
| C.1 | dsp-api | done | 6e4fb2f64..b3dc09e9a, 1e7dd7e1f | Cherry-picked the resources track's 12 code commits (old to new: 6b81b6e60→6e4fb2f64, 2f7de82dc→be31f9c60, df708e0ba→450555223, fcff93210→038ffa5a7, 448a3e2d9→47947d879, 0b458d163→818ad4ebf, 107e98875→ac4e39d33, 8afa177e4→837448de7, add56ef95→924785c04, 95da0cbc2→9b6d85496, 2332ed0ef→d4a53a1d6, 3f806a023→b3dc09e9a); skipped its two spec-state commits and folded their content here. Conflicts only in ObjectAccess.scala (Phase 5's reorderSlots and targetOf's caller parameter beside Phase 6's decideOnly continuation); no allow-list, Grant.scala or AuthorizationMatrixE2ESpec conflict. Removed ResourceUtilV2's check{Resource,Value}Permission, callerless after the merge. Plan ticks unioned. webapi/test-it/test-e2e compile, just test-unit and just check green. | — |
| C.2 | dsp-api | done | 6239805c9 | code-conventions.md section "v2 resource and value writes: the object-access gate", CONVENTIONS.md bullet, REVIEW.md item with the plan's four findings. | — |
| C.3 | dsp-api | done | 6db3530fe | CONTEXT.md: Access request and Grant terms, one relationship line. | — |
| C.4 | dsp-api | done | 126a227ed | editing-values.md: compatibility note rewritten to slot-preserving reorder, new "Reordering Values" section (API v2 complex property IRI, 404/400 property answers, slot rule, 400 body, valuesReordered), 403-before-400 for create, update and delete paragraphs rewritten (permission before class/type/content, RV gets 403); editing-resources.md: create 403 before validation, metadata 403/404 and permission before class/date, delete permission paragraph, candelete section (401, canDo false, gate reason first). Settles the Phase 5 docs deferral. | — |
| C.5 | dsp-api | done | 98c81ba76 | ARCH-MAP.md via dune:dune-map update (targeted; no Explore batches, non-interactive so no overwrite confirmation): access package in webapi-resources, one-chokepoint note, gate boundary rule and its responder/search dependencies, lock owners, webapi-api endpoint rule; fingerprints for webapi-api, webapi-common, webapi-resources (none had one). Settles the Phase 1, Phase 4 and Phase 6 ARCH-MAP deferrals/side findings. | — |
| C.6 | dsp-api | done | ee59b8fd9 | ADR-0013 Addendum 1 "Not yet in code" line: public responder methods still take a User and call the gate. | — |
| C-review-1 | dsp-api | done | eed055b26, 8a0dd8e3e, bb287a562, b7050541a, d07ade9e0, 145e68f9e, d08c509f4 | Closing review round 1 (consistency, simplicity, dune, scala-zio, security over 1ff5f1dd2..HEAD): docs corrected to the code: link update needs Modify on the resource; pre-gate reads include the named project and the permission literal's groups; reorder's pre-gate 400s; ARCH-MAP names decideOnly as grant-less, the create-path and in-use exceptions, and that AuthorizationMatrixE2ESpec skips ObjectAccess endpoints; ADR Not-yet-in-code lists the create path and the in-use IRI list; allow-list header; ObjectAccess class doc (anonymous refused for every request); webapi-resources fingerprint refreshed. All fixups of their targets. | — |
| C.7 | dsp-api | done | — | Test runs on d08c509f4 (test-it/test-e2e on 145e68f9e's code, later commits docs-only): BulkImportParityE2ESpec 1/1 OK; just test-it OK (1010 + 14 tests); just test-unit 7/7 targets; just check green; just test-e2e 1886 run, 42 real failures (84 with the runner's paired "Test mechanism" entries), every one failing on the Sipi `docker run ... convert` of the test ingest upload: AuthorizationMatrixE2ESpec 24 /ingest rows (failed on 71250f989 too, Phase 2/6 evidence), LegalInfoE2ESpec 11, KnoraSipiIntegrationV2ITSpec 4, IngestEndpointsE2ESpec 2, MetadataEndpointsE2ESpec 1, the same 18 failing on base 1ff5f1dd2 (48 run, 18 failed, run in a detached worktree). ValueHasXmlOnValueWriteE2ESpec passed. All with --nocache_test_results. | — |
| F-review-1 | dsp-api | done | be0e65619 | Final review fix (owner decision 2026-11-25): candelete maps only RequestRejectedException to 200 canDo false; other failures and defects propagate (500). New CanDoResponseV2.unlessRejected, unit spec CanDoResponseV2Spec (4 tests, canary: the old mapping fails the two propagate tests), editing-resources.md candelete paragraph. Fixup of 450555223. Gates: just test-unit 7/7, ResourcesRouteV2E2ESpec 79 OK, ResourcesResponderV2Spec 79 OK (both --nocache_test_results), just check green; security + scala-zio review clean (Suggestions only). | — |

## Deferrals

- [settled] Phase 1: review Warning (dune, consistency): ARCH-MAP.md, CONVENTIONS.md, REVIEW.md and code-conventions.md do not yet
  describe the gate or lint rules 4 and 5; the plan's Closing checklist already owns these updates.
  settled: 2026-11-24, landed in the Closing step, commits 6239805c9 and 98c81ba76

- [settled] Phase 2: review Warning (security), pre-existing and kept because deleted values "behave as today": deleting a
  soft-deleted link value is permitted (pinned 200) and the decrement then acts on the live LinkValue to the same
  target, taking the deleted version's creator and permissions; deleting a soft-deleted ordinary value again rewrites
  its deletion date and comment. Fix candidates: refuse a removal whose value has deletionInfo, or match the LinkValue
  by the granted value IRI. Needs an owner call, since it changes a pinned outcome.
  settled: 2026-11-25, owner: pre-existing on main, a follow-up Linear issue (PR #4411 Follow-ups)
- [settled] Phase 2: review Warning (security), pre-existing: IntegrityRules.valueRemovable counts deleted values, as the old
  check did, so a required property with one live and one deleted value lets the live one be removed. Fix: count only
  values without deletionInfo (changes today's behaviour).
  settled: 2026-11-25, owner: pre-existing on main, a follow-up Linear issue (PR #4411 Follow-ups)

- [settled] Phase 3: review Warning (security), pre-existing, needs an owner call: a value update records the caller as the new
  version's creator (attachedToUser), as before the move, so a Modify-only caller who updates a value whose literal
  grants CR to knora-admin:Creator gains ChangeRights on the new version and can then change its permissions. Fix
  candidates: keep the previous version's creator, or stop the Creator group granting more than the updater had.
  settled: 2026-11-25, owner: pre-existing on main, a follow-up Linear issue (PR #4411 Follow-ups)

- [settled] Phase 5: review Warning (consistency), owned by the Closing checkbox on endpoint docs: the "Compatibility note" in
  docs/03-endpoints/api-v2/editing-values.md still says PUT /v2/values/order compacts all non-deleted values into
  0, 1, 2, ...; since Phase 5 the caller's visible values reuse their own slots (gaps and duplicates kept), hidden
  values keep theirs, and a value without a slot gets one after the highest.
  settled: 2026-11-24, compatibility note rewritten, commit 126a227ed

- [settled] Phase 6: scope (Warning), security review: the in-use message of delete, candelete and erase lists every referencing
  resource IRI from an unfiltered triplestore query, so a caller with Delete on a resource learns the IRIs of
  referencing resources hidden from them (ADR-0013 decision 12: messages from the request and the grant only). Today's
  main leaks the same list, and earlier (before the permission check). The fix (list only referencing resources the
  caller can see, plus a count of the rest) needs an elevated read of the referencing resources inside the access
  package and changes the in-use reason that ResourcesResponderV2Spec pins ("protect a Region referenced by a live
  region preview", "protect a resource referenced by a live incoming link"); left for the final review.
  settled: 2026-11-25, owner: pre-existing on main, a follow-up Linear issue (PR #4411 Follow-ups)
- [settled] Phase 6: review Warning (consistency): ARCH-MAP.md still lists ResourcesResponderV2 among the direct
  `IriLocker.runWithIriLock` owners; its locks now go through ObjectAccess. The plan's Closing step owns the
  ARCH-MAP update.
  settled: 2026-11-24, lock-owner lists name ObjectAccess, commit 98c81ba76

## Deviations

- Phase 1: answered: 2026-11-25, accepted by the owner at the final review: AccessDecision has a seventh outcome, CreateDenied, for resource creation, beside the six of
  ADR-0013 decision 14; the alternative, LevelDenied with a level type widened to cover a project create permission,
  mixes an administrative permission into the object levels. Commit 2af75d5b7.
- Phase 1: answered: 2026-11-25, accepted by the owner at the final review: Grant.verify fails with RequestRejectedException, not BadRequestException as sketched, so an
  IntegrityViolation keeps today's exception type (OntologyConstraintException for cardinality,
  DuplicateValueException for a duplicate link), both 400. Commit 598e9e7d6.
- Phase 1: answered: 2026-11-25, accepted by the owner at the final review: value requests on an existing value (update, change rights, delete, erase) require View on the
  resource before the value's level, so a caller with restricted view on the resource keeps getting 403 (now the level
  message), as the plan's status-code table states; for erase, WrongProject is answered after the value is found and
  before the levels. Commit 2af75d5b7.

- Phase 1: answered: 2026-11-25, accepted by the owner at the final review: ADR-0013 Addendum 1 decision 16 and the plan define lint rule 4 as the token
  `KnoraSystemInstances.Users.SystemUser`; as committed (review fix round 1) it also matches `Users.SystemUser`
  after an import and calls of the gate loader's public `resourceWithPropertyValues`, and rule 5 is anchored to
  webapi's main sources. Reason: three reviewers found both evasions; the alternatives were leaving them to rule 2
  and review, or making the loader method private (it still has two unmigrated callers). The ADR text is unchanged;
  proposed wording for decision 16: "The lint gains a rule for elevated reads (the requesting-user handle
  `Users.SystemUser` and the gate loader's transitional public read) with its own allow-list." Commit c51bb1a5e.
- Phase 1: answered: 2026-11-25, accepted by the owner at the final review: the update targets `ValueToUpdate` and `ResourceToUpdate` carry the decided
  `PermissionsChange` beyond the sketch's fields, so the write takes permissions from the grant (security review).
  Commit 148c93242.
- Phase 2: answered: 2026-11-25, accepted by the owner at the final review: the plan loads delete, erase and reorder with getResources scoped to the request's property; as
  committed, DeleteValue loads all of the resource's values (as findDeletableValue did), because deleting a text value
  decrements its standoff links' LinkValues, which sit under knora-base:hasStandoffLinkToValue, outside the property.
  Alternatives: a second, hasStandoffLinkToValue-scoped load in the decrement helper (two loads, and Unfiltered would
  hold a second resource), or the property-scoped load (dies on any text value with standoff links). Erase and reorder
  stay property-scoped. Commit 880c0f416.
- Phase 2: answered: 2026-11-25, accepted by the owner at the final review: deleteValueV2, eraseValue and eraseValueHistory keep their signatures and call the gate
  themselves, so the plan's "migrate the direct callers to obtain grants through the gate" needs no change in
  ValuesResponderV2Spec, ValuesEraseSpec, ValueHasXmlLifecycleSpec or ResourcesResponderV2Spec (the resources track's
  file). Alternative: the responder takes a grant and every caller calls ObjectAccess first, which moves the gate call
  into each RestService and test helper. The responder no longer checks for anonymous callers itself; the gate refuses
  them first. Commit 880c0f416.

- Phase 3: answered: 2026-11-25, accepted by the owner at the final review: updateValueV2 keeps its signature and calls the gate itself, as Phase 2's delete and erase do,
  so the plan's "migrate the direct callers" (ValuesResponderV2Spec, ValueHasXmlLifecycleSpec :222) needs no change.
  The submitted permission literal is validated before the gate (400 for any caller, matching the resources track);
  inside the write the value validator runs before the class, type and creation-date checks, the plan's order.
  ChangeValueRights carries no literal, so a permissions-only update takes the validated literal from the request
  while a content update takes it from the grant. Commit 654cff837.

- Phase 4: answered: 2026-11-25, accepted by the owner at the final review: createValueV2 keeps its signature and calls the gate itself, as Phases 2 and 3 do, so the
  plan's "migrate the direct callers in ValuesResponderV2Spec" needs no change. The submitted permission literal is
  validated before the gate (400 for any caller); the default-permission ceiling runs inside the write. Commit d469e047a.
- Phase 4: answered: 2026-11-25, accepted by the owner at the final review: the create cardinality rule (IntegrityRules.valueAddable) refuses only when the loaded values,
  hidden ones included, reach the cardinality's maximum. The old check also answered 500
  (InconsistentRepositoryDataException) when a resource had no value for a required property; that check is dropped,
  so adding the missing value now succeeds. Alternatives: a second rule outcome that dies (verify only carries 400s),
  or the check on the caller's view (a hidden value would trigger a spurious 500). Commit 454bd8cbd, d469e047a.

- Phase 4: answered: 2026-11-25, accepted by the owner at the final review: lint rule 4 no longer matches calls of `resourceWithPropertyValues`, the loader method this
  phase removed; it still matches `Users.SystemUser` after an import, and rule 5 stays anchored to webapi. This narrows
  the Phase 1 provisional widening back toward ADR-0013 decision 16; the proposed decision-16 wording drops "and the
  gate loader's transitional public read". Commit 8ea138e09.

- Phase 5: answered: 2026-11-25, accepted by the owner at the final review: the plan moves `ValuesRestService.reorderValues` onto `within(ReorderValues)`; as committed the
  RestService parses the request and delegates to a new `ValuesResponderV2.reorderValues`, which runs the pre-gate
  property checks (shared with delete and erase as `existingValuesProperty`) and calls the gate, the shape Phases 2-4
  use. ValuesRestService loses its ResourceUtilV2 and ValueRepo dependencies instead of gaining ObjectAccess, and
  ValueHasXmlLifecycleSpec calls the responder instead of `ValueRepo.reorderValues`. Alternative: the gate call in the
  RestService, which needs OntologyRepo there and a second copy of the property checks. Commit e4a49cbf1.
- Phase 5: answered: 2026-11-25, accepted by the owner at the final review: the reorder's property must be an API v2 complex IRI (`PropertyIri.fromApiV2Complex`); an
  internal-schema property IRI, accepted before, now answers 400. Commit e4a49cbf1.

- Phase 6: answered: 2026-11-25, accepted by the owner at the final review: `ObjectAccess.decideOnly` takes a continuation `check: T => Task[R]` that receives the
  request's target (the caller's view), instead of returning `Unit`; the extracted delete checks take a
  `ResourceToDelete` target, not a `Grant[ResourceToDelete]`. Alternatives set aside: a lock-free `Grant` (breaks
  the grant's "valid only under the lock" contract) or a second load as the caller after `decideOnly` (a read
  outside the gate). Commits 2f7de82dc, df708e0ba.
- Phase 7: answered: 2026-11-25, accepted by the owner at the final review: the plan's "Migrate the direct callers in ResourcesResponderV2Spec and
  OntologyResponderV2Spec" is met without edits: following Phase 6's choice, `CreateResourceV2Handler` keeps its
  request signature and calls the gate itself, so the 37 spec calls stand unchanged. Alternative set aside: callers
  pass a Grant (they would have to obtain one through the gate). Commit 8afa177e4.
- Phase 6: answered: 2026-11-25, accepted by the owner at the final review: the responder's public resource-write methods keep their request signatures and call the
  gate themselves; the write bodies are private functions taking the `Grant`. Alternative set aside: the
  RestService calls the gate and the responder takes a `Grant` (callers in the specs would build grants through the
  gate). Chosen for least scope: no RestService or spec call-site changes. Commit df708e0ba.
- [settled] Phase 6: proposed, security and Scala/ZIO review: candelete maps every failure, defects and triplestore errors
  included, to 200 `canDo: false` with the raw exception message as `cannotDoReason`. As decided (plan: "Map every
  failure to a 200 CanDoResponseV2 with canDo: false, as today"), kept. Reviewers' reason: internal messages
  (Fuseki errors, InconsistentRepositoryDataException) reach the client, and a 500 hides as a 200. Proposed:
  map only `RequestRejectedException` to `canDo: false` and let other failures answer 500. No commit.
  settled: 2026-11-25, answered by the owner at the final review: fix as proposed; fixed, commit be0e65619

- Closing: answered: 2026-11-25, accepted by the owner at the final review: the acceptance criterion "No migrated responder or RestService method takes a `User` and
  reads the resource itself; they take a `Grant[T]`" is answered by the Phase 2/3/4/5/6/7 provisional deviation
  "responders keep their public signatures and call the gate themselves": no migrated method reads the resource
  itself (the gate loads it), but the public responder methods still take a `User`; their private write bodies take
  the grant. Not changed in Closing; recorded in ADR-0013 Addendum 1's "Not yet in code" line (ee59b8fd9,
  b7050541a). The criterion "Every migrated write takes the IRIs it writes from the grant" holds for the value and
  resource update/delete/erase/reorder writes; resource creation reads the caller and project from the request below
  the gate (same project id the gate decided on), also in that line.

## Side findings

- Phase 1: review suggestions after the cap, not fixed: rule 4 matches `resourceWithPropertyValues` by name, so an
  unrelated method of that name would trip it; the rule that a write takes permissions from the grant target is
  enforced by review only until the writes migrate.
- [settled] Phase 1: slice/resources/access depends on org.knora.webapi.responders (IriLocker, SearchResponderV2), the first such
  dependency under slice/resources; record or resolve it when ARCH-MAP.md is updated in the Closing step.
  settled: 2026-11-24, recorded as a docs-only boundary rule of webapi-resources, commit 98c81ba76
- Phase 1: review suggestions not taken: rule 5 sees only leading package clauses (a nested `package access { }` block
  or an unusual leading comment escapes it); the elevated-read allow-list mixes migrating entries with standing ones
  (ReadResourcesServiceLive, ValueHasXmlRenderer); ObjectLoader.resourceWithPropertyValues and its allow-list entries
  should go when value create and update move onto the gate (Phases 3 and 4); 403 vs 404 for hidden vs missing
  resources is the plan's documented trade-off; GrantAccessSpec asserts compiler message fragments.
- Phase 1: callerView keeps a link value whose loaded `nestedResource` is empty (getResources may not attach link
  targets), so its target IRI stays visible; check what the property-scoped getResources load attaches in Phase 2.
- Phase 1: the gate's property-scoped load needs SearchResponderV2 (Gravsearch), so ObjectLoader depends on a responder
  trait despite the plan's "must not depend on any responder"; no layer cycle, since SearchResponderV2 depends on no
  write responder.

- Phase 2: review suggestions not taken: the update path keeps its own findLinkValue/decrementLinkValue (delegating
  to LinkValueWrites.decremented) until Phases 3 and 4 move it; remove decremented and findLinkValue then (dune DUNE-001).
  checkRemoval is called from both delete and erase and takes resource and value beside the grant; a shared target
  type would need a change to Grant.scala, a file the parallel track also edits. ADR-0013 decision 13's step 3 and the
  plan's "Loads" lines still say delete is property-scoped; proposed ADR wording: "scoped to the property a value
  request names, except a value delete, which loads all values for its standoff link counts". Missing unit cases: the
  valueInView fallback for a link to a hidden target, a text value in callerView, a deleted value in IntegrityRulesSpec.
- Phase 2: refuted review findings: anonymous ordering (the pre-gate checks read only the public ontology, and the
  endpoints answer 401 first); the full load for delete (same load as before the move); erasehistory's description
  (it goes through eraseValue and the gate); valueInView dying (decide answers MissingValue when the caller has no
  level on the value, so a permit always has a visible value).
- Phase 2: local gate: the 24 /ingest rows of AuthorizationMatrixE2ESpec fail locally because the sandboxed test runs
  `docker` against /var/run/docker.sock, which Docker Desktop here does not provide; unrelated to this phase.

- Phase 3: review suggestions not taken: LinkValueWrites.incremented stays public for value create (Phase 4 removes
  it with the responder's findLinkValue, ensureNoLinkTo and incrementLinkValue; DUNE-001/006); link property and link
  value property are both SmartIri in LinkValueWrites (DUNE-002); the pre-gate rule is a doc comment, not a check
  (DUNE-003); repo writes take no grant (DUNE-004, for the closing ADR step); enforcedIn is unchecked (DUNE-005); the
  duplicate-link 400 tells a caller with Modify that a hidden link to a chosen target exists, by design of counting
  hidden links; a custom new-version IRI is not tied to the grant's resource (pre-existing); ChangeValueRights carries
  no literal, so a permissions-only write takes the validated literal from the request; the putValues description
  does not name the duplicate-link 400.
- Phase 3: the plan's e2e case "a caller without modify permission who sends a disallowed license gets 403" is
  covered by the cross-project link case instead, the other valueValidator check: a license needs a file value,
  which needs Sipi. Both run inside the gate; the cross-project case was red (400) against the pre-move code.
- Phase 3: refuted review findings: validatePermissions before the gate (the plan allows it: it checks group IRIs
  against admin data and reveals no object data); anonymous ordering (the endpoint answers 401 first, as in Phase 2);
  the cross-project test not reaching valueValidator (it was red against the pre-move responder, where the validator
  ran first).
- Phase 3: another session's uncommitted resources-track edits (ResourcesResponderV2, ObjectAccess, ObjectAccessSpec)
  appeared in this worktree at 15:43; their committed versions are on plan/object-access-gate-resources. They were set
  aside (copies in the executor's scratchpad, stray-resources-track/) so this worktree builds.

- Phase 3: re-review findings not taken: the per-kind updateContentInGate/updatePermissionsInGate helpers (round 1's
  fix) are single-use (simplicity; a matter of taste against round 1's nesting finding); refuted: the ChangeRights e2e
  read-back being vacuous (a permissions-only update rewrites the value in place, so its IRI stays).
- [settled] Phase 3: ARCH-MAP.md component webapi-resources has no fingerprint; the Closing step's map update owns it.
  settled: 2026-11-24, fingerprinted, commit 98c81ba76
- Phase 4: the plan's e2e case "a caller without modify permission and with a disallowed license gets 403" is covered
  by the cross-project link case, as in Phase 3: a license needs a file value, which needs Sipi.
- Phase 4: review suggestions not taken: writableProperty's `verb` string only varies one message (simplicity,
  DUNE-001); the two AfterChecks helpers keep long parameter lists and noDuplicateLink stays in the link helper, as on
  the update path; the postValues level sentence is not a shared Policy constant (DUNE-002, like putValues and
  deleteValues); the pre-gate rule is a doc comment (DUNE-003, as Phase 3); createLinkValueV2AfterChecks' scaladoc
  omits maybeCreationDate and valueHasOrder (pre-existing); ARCH-MAP.md still places the write-lock call in
  ValuesResponderV2 (Closing step's map update); validatePermissions before the gate answers 404 for an unknown group
  and dies (500) on a malformed group IRI, now reachable without Modify (pre-existing bug in PermissionUtilADM, the
  400-before-gate rule is decided); cardinality and duplicate-link 400s reveal hidden values to a Modify caller, by
  design; the lock does not cover link targets (unchanged).
- Phase 4: refuted review findings: the cardinality check after the standoff and object-class checks (the plan's order:
  validation, then verify); ontology and permission-literal checks before the gate (the plan allows them; they reveal
  no object data); anonymous callers reaching the pre-gate checks (the endpoint answers 401 first, as in Phases 2 and
  3).

- Phase 5: slot assignment as committed: the visible values' old slots plus one new slot (after the highest order of
  all non-deleted values, missing counting as 0) per visible value without a slot, sorted, are zipped with the
  requested order, so the whole requested order holds; the new slots go to the last requested positions, not
  necessarily to the values that had none.
- Phase 5: review suggestions not taken: the stringly `verb` of existingValuesProperty and its name (DUNE-003,
  simplicity), and the link-property 400 wording for a reorder; "non-deleted" defined twice, by deletionInfo on the
  read and by the SELECT's isDeleted filter, linked by the ReorderSlots doc (DUNE-002); ValueRepo.reorderValues stays
  public (DUNE-004, as before); ReorderSlotsSpec's query test asserts substrings; some e2e overlap with the unit
  cases; test helpers build SPARQL with s-interpolation, as the spec's existing helpers do; the endpoint description
  spells out the slot rule.
- Phase 5: refuted or verified-clean review findings: the security lens found no leak or bypass (gate order, lock,
  error echo, allow-list removal all verified).
- Phase 5: re-review findings not taken: the REST-layer duplicate check now repeats the gate's (kept as an early
  400 before the gate); ReorderSlotsSpec builds ObjectAccess with null dependencies, as ObjectAccessSpec does; the
  plan's Phase 5 text still names verifyCanonicalValues and the RestService as the gate caller (see the Phase 5
  deviation: the check is ReorderSlots.mismatch, called by ObjectAccess.reorderSlots from
  ValuesResponderV2.reorderValues). Refuted: an unused `import zio.*` in ReorderSlotsSpec (it compiles under -Werror).

- Phase 6: review Suggestions not acted on: `withinAsProjectAdmin` calls `IriLocker.runWithIriLock` directly instead of
  `ObjectAccess.locked` (Phase 1 code, two lock sites); `ResourceUtilV2.checkResourcePermission` stays public for the
  value writes until the values track removes its callers; the gate takes the per-resource lock before the decision,
  so any authenticated caller can hold a hidden or missing resource's lock for one load; the metadata and authorship
  verification re-read runs as the caller and can answer 403/404 after a committed write that removed the caller's
  own view (as on main); decideOnly has no unit test beyond the anonymous refusal (the e2e candelete tests cover it).
- Phase 6: re-review Suggestions: `PermissionsChange.To` takes a raw String, so a later entry point could hand the gate
  an unvalidated literal (a validated type or a Scaladoc line would pin it); the invalid-literal e2e test asserts the
  status only, not the "Invalid permission literal" body.

- Phase 7: review Suggestions not acted on: the gate's `CreateResource` takes a `KnoraProject`, so the handler looks
  the project up before the gate (public data; the parser already resolved it, so its 404 is unreachable in practice);
  the handler keeps `ensureClassBelongsToProjectOntology` reading `projectADM` from the request rather than the grant;
  `ObjectAccess`'s class doc speaks of existing resources only and does not say that `within(CreateResource)` takes no
  lock (the handler locks the new IRI); the cross-project-link e2e body is built by string replacement; no test drives
  the standoff link target or class-ontology checks for a denied caller. Refuted: `ResourceClassIri.from(SmartIri)` is
  always `Right`, so it cannot answer 400 before the gate.

- Phase 6: AuthorizationMatrixE2ESpec has 24 failing rows locally, all `/ingest/` fixtures whose JPEG upload runs
  `docker run daschswiss/knora-sipi:latest convert` and fails; the same 24 fail on 71250f989's main sources, so they
  are environmental, not this phase's. The values-track executor shares port 3333 for e2e runs on this machine; runs
  that collide end "Top-level defect ... Address already in use" with "OK (0 tests)" and exit 0, which Bazel caches as
  PASSED (use --nocache_test_results).

- Closing: review findings not fixed (merge is sound; no Critical): cross-track duplication: link-value property
  adjustment in both ObjectAccess and ValuesResponderV2 with differing "Property not found" wording; four
  resource-class mismatch checks with three wordings (changing them alters pinned messages); both tracks hand-map
  Option[String] to PermissionsChange (a fromOption/toOption on the enum); updateMetadata/updateAuthorship share a
  verification block; decideOnly's CreateResource branch is unused in production and `permitted`/`targetOf` die on a
  Permit without a loaded object (an implicit invariant worth a property test); ResourceUtilV2 now has one method and
  an unused sipiService parameter (pre-existing); ObjectAccess.locked is single-use; `ZIO.attempt` around the pure
  decide; mixed Clock.instant/Instant.now; decideOnly has no unit test beyond anonymous refusal; the gate's rule
  restated in six docs. Security, pre-existing or decided: 403 hidden vs 404 missing resource (the plan's trade-off);
  custom resource/value IRIs reveal existence through the duplicate-IRI 400 (pre-existing); candelete puts defect
  text into cannotDoReason (covered by the Phase 6 proposed deviation); metadata/authorship re-read as the caller can
  500 after a committed write that removed the caller's view (Phase 6 side finding); rule 4 misses a brace or wildcard
  import of Users in a file already on the SystemUser list; no lint keeps write primitives to the gated responders.
  The `.orDie` on UserIri.from in resource delete is as on main. valueRemovable counting deleted values is the Phase 2
  deferral. CreateResourceV2Handler reading caller and project from the request below the gate is recorded in the
  ADR's Not-yet-in-code line (same IDs today, the gate's project comes from the request's project id).
- Closing: re-review of round 1 (consistency, simplicity, dune): no inaccuracy; not taken: the ARCH-MAP exceptions
  sentence repeats the ADR's Not-yet-in-code line (kept: the round-1 dune review asked ARCH-MAP to say it), and two
  new lines exceed 120 characters (MD013 is disabled in just check).
- Final review fix (candelete): Suggestions not acted on: the spec pins only ForbiddenException as a rejection
  (NotFound/BadRequest would show the whole family maps); the matcher maps every RequestRejectedException subclass,
  wider than the five kinds named; no server-level test shows a triplestore failure on candelete answers the fixed
  "Internal server error" body (BaseEndpoints' catch-all does that today).

## Owner decisions

- Closing, answered: 2026-11-25, accepted by the owner at the final review: the `just test-e2e` gate is left to CI for its 42 Sipi-dependent tests (listed in chunk C.7), which fail locally on base too; every other e2e test passes.
- Phase 2, answered: 2026-11-25, accepted by the owner at the final review: the AuthorizationMatrixE2ESpec gate is left to CI. Locally its 24 `/ingest` rows fail because the sandbox has no docker socket; all other rows pass. Alternative: run it locally with a working socket before closing the phase.
