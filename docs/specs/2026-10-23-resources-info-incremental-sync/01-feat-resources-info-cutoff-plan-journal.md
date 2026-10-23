---
plan: docs/specs/2026-10-23-resources-info-incremental-sync/01-feat-resources-info-cutoff-plan.md
target_repo: /Users/balduinlandolt/Documents/GitHub/dasch-swiss/dsp-api/.claude/worktrees/DEV-7632
base_commit: 3e0e2f1ed484f0fdd575bc9d7d38842ccea7ca7b
branch: worktree-DEV-7632
started: 2026-10-23
problem: >
  An external sync client (MLS, project 0807) wants to poll GET /v2/resources/info once a minute for
  "what changed since my last sync". Today that takes one call per resource class and each call returns
  every resource of the class. The endpoint gains an optional at-or-after `cutoff` and an optional
  `resourceClass` (project-wide listing), each entry says its class, and the response carries the
  server's `asOf` time to anchor the next poll.
status: in-progress
---

# Execution Journal: 01-feat-resources-info-cutoff-plan

## Repos

| repo | base_commit | branch | merge_strategy | status | pr |
| --- | --- | --- | --- | --- | --- |
| dsp-api | 3e0e2f1ed | worktree-DEV-7632 | squash | in-progress | — |

## Phases

| phase | status | phase_base | review_fix_rounds |
| --- | --- | --- | --- |
| 1 | reviewed | dsp-api@a2a0f6732 | 2 |
| 2 | reviewed | dsp-api@561bbc909 | 1 |

## Chunk queue

- **1.1** (dsp-api) files: `slice/resources/repo/GetResourceInfoQuery.scala` (new), its golden spec (new);
  depends_on: —; checkboxes: "Golden spec for the query object ..." (query-object part of "New query object ...");
  acceptance: single `sparql"..."` template, VALUES first, FILTER last, typed xsd:dateTime with fractional seconds;
  context: plan § Query shape, `GetResourceValueVersionHistoryQuery.scala`; replaces: —
- **1.2** (dsp-api) files: `ResourceInfo.scala`, `ResourceInfoRepo.scala`, `ResourceInfoRepoLive.scala`,
  `ResourceInfoRepoFake.scala`, `ResourceInfoRestService.scala` (call-site only), `ResourceInfoRestServiceSpec.scala`
  (construction only); depends_on: 1.1; checkboxes: "Add resourceClass: InternalIri to ResourceInfo", "Replace
  findByProjectAndResourceClass with find(...)", "New query object ... ResourceInfoRepoLive uses it ...", "Update
  ResourceInfoRepoFake"; acceptance: compiles, existing spec green, no `s"..."` SPARQL left in the live repo;
  context: plan § Row parsing, docs/development/dsp-api-iri-handling.md; replaces: —
- **1.3** (dsp-api) files: none (journal only); depends_on: 1.1; checkboxes: "Re-time the class-restricted call on
  stage"; acceptance: both timings recorded; context: plan § Measurements; replaces: —
- **1.4** (dsp-api) files: `ResourceInfoDto.scala`, `ResourceInfoRestService.scala`, `ResourceInfoEndpoints.scala`,
  `ResourceInfoServerEndpoints.scala`, `ResourceInfoRestServiceSpec.scala`; depends_on: 1.2; checkboxes:
  "ResourceInfoDto gains resourceClass ...", "ResourceInfoRestService: one method ...", "ResourceInfoEndpoints ...",
  "ResourceInfoServerEndpoints wired ...", "ResourceInfoRestServiceSpec ..."; acceptance: all listed spec cases green;
  context: plan § Class IRI output, § Sorting, § asOf; replaces: —
- **1.5** (dsp-api) files: none; depends_on: 1.4; checkboxes: "Run bazel test //modules/webapi:test";
  acceptance: suite passes; context: —; replaces: —

- **2.1** (dsp-api) files: `modules/test-e2e/src/test/scala/org/knora/webapi/e2e/v2/ResourcesInfoE2ESpec.scala` (new);
  depends_on: —; checkboxes: "New E2E spec ResourcesInfoE2ESpec ...", "Check what POST /v2/resources/batch returns
  for a soft-deleted IRI", "Run the new spec ..."; acceptance: every listed case plus the no-subclass case green
  against real Fuseki; context: plan § Phase 2, `ResourcesEndpointsPostBatchE2ESpec.scala`; replaces: —
- **2.2** (dsp-api) files: `docs/03-endpoints/api-v2/reading-and-searching-resources.md`; depends_on: 2.1 (batch
  tombstone behaviour); checkboxes: "Docs: new section ...", "Run just check"; acceptance: section after "Reading
  Resources by Class from a Project", `just check` green; context: plan § Decisions, § Phase 2; replaces: —

## Chunks

| id | repo | status | commit(s) | summary | blocker |
| --- | --- | --- | --- | --- | --- |
| 1.1 | dsp-api | complete | a9d400c53 | GetResourceInfoQuery single sparql template + golden spec (4 combos, Jena-parsed; canary `>` instead of `>=` fails it) | none |
| 1.2 | dsp-api | complete | 0578f85f9 | ResourceInfo.resourceClass; repo `find(project, Option[class], Option[cutoff])` on GetResourceInfoQuery; `from` + InconsistentRepositoryDataException; fake filters like the query | none |
| 1.3 | dsp-api | complete | — | Stage re-time, 0807 `mls:Article` (17,096 rows), see Side findings: compute equal, VALUES form kept | none |
| 1.4 | dsp-api | complete | 4094beafe | DTO resourceClass + asOf; rest service `find` (Clock asOf, asInternalIri in, distinct asExternalIri out, IRI tiebreak); endpoint Option[IriDto] + Option[Instant] cutoff; spec 14 cases, canaries on tiebreak and asOf fail | none |
| 1.5 | dsp-api | complete | — | `bazel test //modules/webapi:test` passes at 4094beafe; license + relative-import gates pass | none |
| 1.6 | dsp-api | complete | 4e5801516 | Review fix round 1: no ?resourceClass column on class-restricted query (perf), one ZIO.attempt for row parsing (perf + typed failure), named stored-class conversion helper, IRI tiebreak direction documented | none |
| 1.7 | dsp-api | complete | a01f810e3 | Review fix round 2 (re-review of 1.6, dune): row mapper returns Either, missing class column is a named InconsistentRepositoryDataException, Scaladoc points at the query's conditional column | none |
| 2.1 | dsp-api | complete | 43f1353c7 | ResourcesInfoE2ESpec, 11 tests green on Fuseki (port 3399 override); covers boundary cutoff = stored timestamp, +1 ms excludes, no-subclass, soft delete; batch returns knora-api:DeletedResource tombstone (isDeleted, no values); canary `>` for `>=` fails it; missing project header is a 400 | none |
| 2.2 | dsp-api | complete | c76319568 | Docs section `### Listing the Resources of a Project with Their Dates` + `#### Polling for Changes` in reading-and-searching-resources.md; `just check` equivalent green (format-test needs `--remote_download_outputs=all` locally) | none |
| 2.3 | dsp-api | complete | cb7ebac90 | Review fix round 1: +02:00 test now distinguishes offset applied vs ignored (newest date at +02:00 listed, same wall clock as Z not listed); entryOf fails with Exception; docs add malformed-project / invalid order 400s and IRI tiebreak also when descending | none |
| 2.4 | dsp-api | complete | b29a0fe24 | Final review fix 1: `ResourceInfoRepoLive.toResourceInfo` (now in the companion, `private[repo]`) is one Either; missing variables, bad dates/booleans and no class all give InconsistentRepositoryDataException naming the resource; `ZIO.foreach` + `ZIO.fromEither`. The `VariableResultsRow.getRequired`/`get(…, mapper)` helpers throw, so they did not fit. New `ResourceInfoRepoLiveSpec` (5 tests) | none |
| 2.5 | dsp-api | complete | af38106b4 | Final review fix 2: `toExternal` returns the IRI; map built at the call site; Scaladoc states fail-fast on an unconvertible stored class | none |
| 2.6 | dsp-api | complete | a561fac52 | Final review fix 3: dropped the duplicate cutoff test; exclusion test renamed to say it judges lastModificationDate first | none |
| 2.7 | dsp-api | complete | 7159a78e8 | Final review fix 4: +02:00 E2E builds the cutoff with ISO_OFFSET_DATE_TIME, UTC variant via `withOffsetSameLocal`; E2E 11/11 green (port 3399) | none |
| 2.8 | dsp-api | complete | bd4aabf22 | Final review fix 5: `ResourceInfoRepo.find` Scaladoc documents projectIri and the returned class | none |
| 2.9 | dsp-api | complete | a28a05001 | Final review fix 6: ARCH-MAP hand-edited (dune-map would need confirmation the executor cannot get): count 34 -> 35, GetResourceInfoQuery, ResourcesInfoE2ESpec | none |
| 2.10 | dsp-api | complete | 619d668fd | Final review fix 7: docs qualify "every write sets lastModificationDate" to writes through the API, pointing at back-dated writes and erasure (both bullets already existed) | none |
| 2.11 | dsp-api | complete | 507ab1b9f | Final fix round re-review (scala-zio + consistency, sonnet; no Critical/Warning): added non-boolean isDeleted and invalid-IRI cases to `ResourceInfoRepoLiveSpec` (7 tests) and the failure rule to `toResourceInfo`'s Scaladoc | none |

## Deferrals

- ARCH-MAP.md (webapi-resources) does not list `GetResourceInfoQuery`, and its "34 files under `slice/resources/repo`"
  sparql-builder count is now one short. ARCH-MAP is owned by `/dune:dune-map update`; left for the session.
- Phase 2 E2E must carry the real-SPARQL boundary cases the unit tests only check against the fake: cutoff equal to a
  stored timestamp, a resource with no `lastModificationDate`, a deleted resource, a class filter not matching
  subclasses (already in the Phase 2 checklist except the no-subclass case).
- Phase 2 dune review (DUNE-001): ARCH-MAP.md test-e2e entry does not list `ResourcesInfoE2ESpec`;
  same `/dune:dune-map update` as the GetResourceInfoQuery deferral, left for the session.

## Deviations

- Decided (2026-10-23): `resourceClass` input via `query[Option[IriDto]]` plus `asInternalIri`, "inputs accepted
  today stay accepted". Committed (4094beafe): `IriDto.from` validates the raw string, whereas the old path
  percent-encoded first, so a class IRI valid only after encoding (raw spaces/non-ASCII) is now a 400. Why: the
  IriDto convention of the sibling endpoint; reported as a side finding by the Phase 1 executor. Owner's answer:
  pending (final review).

## Side findings

- Phase 2 re-review of cb7ebac90 (consistency, simplicity, dune): no Critical or Warning. Suggestions not acted on: the
  "any other value of order/orderBy is a 400" sentence sits under the orderBy bullet and does not say matching is
  case-insensitive; the E2E has no 400 case for a malformed project header or invalid order/orderBy (tapir default
  decode failure).
- Phase 2 review, not acted on (suggestions / refuted): single-use payload helpers and the two overlapping listing
  tests in `ResourcesInfoE2ESpec` (kept: one behaviour per test, two extra round trips); docs section length and some
  restatement between the parameter list and the polling gaps (kept: plan asks for both); 400 tests do not assert which
  parameter caused the 400; a pointer from code to the docs section (DUNE-003); the spec could live under
  `slice/api/v2/resources/` instead of the legacy `e2e/v2/` (DUNE-002, siblings are in `e2e/v2/`).
- Phase 2 review, unverified edge case: `ReadResourceV2.asDeletedResource` substitutes the tombstone only when
  `deleteDate` is before now, so a resource soft-deleted with a future custom delete date is listed with
  `isDeleted = true` but fetched by `POST /v2/resources/batch` as a full resource. Not documented.
- The docs claim that every resource-level write (permissions, links, metadata) sets `lastModificationDate` comes from
  the plan's code reading; the E2E only exercises create, add value and soft delete.
- Local test recipe: E2E needs `--test_env=DOCKER_HOST --test_env=TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
  --test_env=HOME` plus `KNORA_WEBAPI_KNORA_API_{INTERNAL,EXTERNAL}_PORT=3399` (an ssh tunnel holds 3333).
  `just check` fails locally only because `.format-test` needs `--remote_download_outputs=all`; with it every
  format-test, the license/relative-import tests and markdownlint pass.
- `ResourcesEndpointsPostBatchE2ESpec` is an `object` without `@RunWith(classOf[DspZTestJUnitRunner])`, unlike the
  other 63 E2E specs; unverified whether Bazel's JUnit runner executes it at all.
- Stage re-time (2026-10-23, `dsp vre sparql query -s stage --accept csv`, project 0807, class `mls:Article`,
  17,096 rows, 4–6 runs each, interleaved): old `s"..."` query 1.62–2.09 s; new rendered template (leading
  `VALUES ?resourceClass`) 1.84–2.55 s; `?resource a <C>` + `BIND(<C> AS ?resourceClass)` fallback 2.03–2.17 s.
  `COUNT(*)` wrappers (compute only): old 0.53–0.55 s, new 0.53–0.57 s. The evaluation cost is identical; the
  ~0.2–0.4 s on the full queries is transferring the extra `?resourceClass` column, which the BIND fallback carries
  too. Not clearly slower, so the VALUES form stays.
- `just fmt` fails locally in `//modules/bagit:test.format` (`cp: ... BagItSpec.scala.fmt.output: No such file or
  directory`), before reaching webapi; unrelated to this change. Formatting was run per target
  (`bazel run //modules/webapi:{webapi,test}.format` and `.format-test`).
- `IriDto.from` (tapir decode of `resourceClass`) checks `Iri.isIri` on the raw string, while the old path
  (`IriConverter.asInternalIri` -> `Iri.validateAndEscapeIri`) percent-encodes before validating. A class IRI that
  only validates after encoding (e.g. raw non-ASCII or spaces) was accepted before and is a 400 now. Unverified
  whether such a class IRI can match stored data at all; same behaviour as the sibling `/v2/resources` endpoint's
  `query[IriDto]("resourceClass")`.
- Phase 1 review, refuted: "`Iri.unsafeFrom` in `GetResourceInfoQuery.build` can turn a client class IRI into a 500".
  Probed: `"<>\"{}|^`\\` in the fragment, on knora internal, knora external and foreign IRIs, are all rejected by
  `IriDto.from` (tapir 400) and by `asInternalIri` before the query is built.
- Phase 1 review, not acted on (suggestions): `SELECT DISTINCT` likely a no-op (one `rdf:type` per resource on 0807);
  byte-compare with/without on stage before dropping it. A resource with two `rdf:type`s would be listed twice on a
  project-wide call. The `asOf` test does not prove the clock is read before the repo call (would need a fake that
  advances the clock). Unbounded public project-wide listing is owner-decided (no paging, DEV-7543).
- Re-review of fix round 1, refuted: "hoist `sparql\" ?resourceClass\".unless(...)` into a local val" (simplicity,
  consistency): a Fragment-returning local val is what the one-template convention forbids. Not acted on: Scaladoc of
  `ResourceInfoRepo.find` could state the returned class equals the requested one. Cap of 2 fix rounds reached after
  round 2; round 2 itself was not re-reviewed, so the session's final review covers a01f810e3.
- Consistency review: the plan's phase table still says Phase 1 `pending`; left for the session, which owns phase status.
