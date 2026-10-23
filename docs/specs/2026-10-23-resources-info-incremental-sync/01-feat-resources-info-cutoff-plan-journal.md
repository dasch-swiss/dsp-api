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

## Deferrals

- ARCH-MAP.md (webapi-resources) does not list `GetResourceInfoQuery`, and its "34 files under `slice/resources/repo`"
  sparql-builder count is now one short. ARCH-MAP is owned by `/dune:dune-map update`; left for the session.
- Phase 2 E2E must carry the real-SPARQL boundary cases the unit tests only check against the fake: cutoff equal to a
  stored timestamp, a resource with no `lastModificationDate`, a deleted resource, a class filter not matching
  subclasses (already in the Phase 2 checklist except the no-subclass case).

## Deviations

- Decided (2026-10-23): `resourceClass` input via `query[Option[IriDto]]` plus `asInternalIri`, "inputs accepted
  today stay accepted". Committed (4094beafe): `IriDto.from` validates the raw string, whereas the old path
  percent-encoded first, so a class IRI valid only after encoding (raw spaces/non-ASCII) is now a 400. Why: the
  IriDto convention of the sibling endpoint; reported as a side finding by the Phase 1 executor. Owner's answer:
  pending (final review).

## Side findings

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
