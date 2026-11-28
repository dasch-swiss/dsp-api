---
title: "fix: harden ingest and value validation after the rdu-13 bulk-import failures"
type: fix
date: 2026-11-27
author: "Ivan Subotic"
status: implemented
repositories: []
linear: DEV-8105
---

# fix: harden ingest and value validation after the rdu-13 bulk-import failures

## Overview

The omeka2dsp bulk import of Stadt.Geschichte.Basel into `rdu-13` (project 4001, 21 to 27 Nov 2026) found ten
dsp-api defects. This plan fixes them as one stack of PRs, one unit per PR:

- upload failures come back as a bare 500, leak server paths, or are not logged (DEV-8105, DEV-8123);
- parallel uploads share a temporary folder (DEV-8100);
- the sipi image pin must move to the Vitrinli release with the ICC, IPTC, EXIF and `--json` fixes (DEV-8099);
- ingest starts sipi conversions that cannot finish (DEV-8101);
- the client waits for its read timeout after the server already answered (DEV-8106);
- the filename and file-type error does not say what is wrong (DEV-8094);
- an unreferenced asset cannot be found (DEV-8096, report only);
- standoff links to Wayback Machine URLs are rejected (DEV-8095);
- an empty text value gives a misleading error (DEV-8098).

## Problem Statement / Motivation

An external research project could not run a parallel import, and every failure reached the client as a bare HTTP
500 or a timeout. The user had to email logs, and DaSCH had to match them by hand. The causes are in dsp-api code:
a per-second temp folder name, `.orDie` and `t.getMessage` in the upload path, no pre-check before sipi, and
validators with unhelpful or wrong rules. The sipi fixes for the same run (DEV-8088 to DEV-8093) reach users only
when dsp-api pins the new sipi image.

## Proposed Solution

Fix each defect in the component that owns it, with a regression test that fails on `main`. One shared contract
comes first (Phase 1): the ingest error body, one function that maps every ingest failure to it, a correlation ID
per request, and logged defects. The sipi pin, the sipi error mapping, the pixel limit and the filename message build
on that contract. Every other unit is independent and runs in parallel.

## Alternative Approaches Considered

| Option | Why not |
| --- | --- |
| Fix the temp-folder race with a lock per project | Serializes uploads that need no serialization; a unique folder per upload removes the shared state instead. |
| Make `Iri.validateAndEscapeIri` accept `//` in paths (`UrlValidator.ALLOW_2_SLASHES`) | The validator also guards project, user, group, list and permission IRIs. A standoff-link validator of its own changes only the path that is wrong. |
| Reject large images by file size (DEV-8101) | Decode memory scales with pixels, not bytes; a small compressed file with huge dimensions still passes (Ivan, 2026-11-27). |
| Wait for `sipi verify` (DEV-8118) for the pre-check | Blocks the fix on unplanned vitrinli work. The header reader can be replaced by `sipi verify` later. |
| Orphaned assets: report plus delete endpoint (DEV-8096) | A delete removes archive data and needs its own design (Ivan, 2026-11-27). |
| Support GIF in ingest (DEV-8094) | An animated GIF has no single JPEG2000 frame; the team decided to reject GIF with a clear error (Balduin's reply to Moritz, 2026-11-27). |
| A custom tapir `DecodeFailureHandler` for the `filename` path parameter | The ingest endpoints are served by webapi's `DspApiServer`; a handler there is global, and ingest's own endpoint tests use a stub interpreter that would not exercise it. Validating the filename in the server logic keeps the rule and its test in ingest. |
| A sealed `IngestError` in the typed channel instead of the `IngestRejected` exception | `IngestService.ingestFile` and its callers are `Task`; moving them to a typed channel is a refactor of its own. One exception type that only `IngestFailure` matches keeps the change small, and the 500 fallback is the only catch-all. |
| A `FiberRef` correlation ID of its own | `DspApiServer` already annotates each request's logs with the OTel `trace_id`. A second ID would not match the traces; reusing `trace_id` links the error body, the logs, the trace and sipi's log lines. |
| `catchAllDefect` in webapi's `IngestServerEndpoints.onIngestPool` | ztapir's `zServerLogic` already turns a defect into a `Throwable` failure (`.either.resurrect`, comment at `DspApiServer.scala:99-102`), so `catchAllDefect` there sees nothing, and the route's error type is existential there. The guard goes into ingest's handlers, inside `serverLogic`. |
| Each failing unit maps its own exception in the upload handler | Four units would edit the same `mapError` lines in parallel. One mapping function owned by Phase 1, and exception types the later phases only construct, removes the conflict. |

## Technical Considerations

**Ingest error contract (Phase 1).** Ingest's `ApiProblem` stays on ingest's own `BaseEndpoints`; it is not added
to webapi's `BaseEndpoints.errorOutputs`.

- New `ApiProblem.UnprocessableContent(correlationId, errorCode, message)` at 422, for a well-formed request whose
  file content is invalid. `ApiProblem.BadRequest` (400) stays for malformed requests, such as the empty upload.
- `ApiProblem.InternalServerError` becomes `(correlationId, errorCode, message)`; its message never comes from a
  `Throwable`. `InternalServerError.apply(t: Throwable)` and `apply(msg, t)` are removed. JSON fields:
  `correlation_id`, `error_code`, `message`. Both types are in `BaseEndpoints.defaultErrorOutputs` (tapir `oneOf`).
- A new exception `IngestRejected(errorCode: String, message: String)` in `swiss.dasch.domain`, always a 422. A 400
  stays an `ApiProblem.BadRequest` built in the handler, as today. Later phases only construct it; they never edit the
  handler mapping. `IngestService.ingestFile`'s two `IllegalArgumentException`s ("File `<path>` is not a supported
  file", "Unsupported file type.") become `IngestRejected("unsupported_format", <message with the upload
  filename, no path>)`.
- One function, `IngestFailure.toApiProblem(t: Throwable): URIO[Any, ApiProblem]` in `swiss.dasch.api`, maps
  `IngestRejected` to 422 and everything else to the structured 500 (`error_code: internal`, a fixed
  message), logging the cause with `ZIO.logErrorCause` for the 500 case. Phase 4 adds the `SipiCliError` case.
- **Correlation ID.** No new `FiberRef`. `DspApiServer` (`DspApiServer.scala:160-163`) annotates every request's
  logs with `trace_id` (32 hex characters, matches sipi's `[A-Za-z0-9._:-]{1,128}`) when the span is valid;
  `IngestExecutor.run` is `onExecutor`, not a fork, so the annotation reaches the ingest pool. A new
  `swiss.dasch.infrastructure.CorrelationId` (an `AnyVal` case class like `AssetFilename`) has:
  `scoped(effect)`, which takes the `correlation_id` log annotation, else `trace_id`, else a fresh ID (`makeNew`:
  a UUID without dashes), and runs the effect under `ZIO.logAnnotate("correlation_id", id)`; `withNew(effect)`, the
  same with a fresh ID; and `current: UIO[CorrelationId]`, which reads `correlation_id` and returns a fresh ID when
  there is none (so a handler interpreted directly in a test still gets one). `IngestFailure.toApiProblem` and
  (Phase 4) `CommandExecutorLive.execute` use `current`.
- **Defects.** `docs/development/error-handling.md`: bad client input is never a defect, and invariants use
  `.orDieWith`, never bare `.orDie`. The upload handler's `.orDie` calls (`Files.size(tmpFile)`,
  `findByAssetRef(...).someOrFailException`) become typed failures through `toApiProblem`. A new
  `HandlerFunctions.guarded[A](z: IO[ApiProblem, A]): IO[ApiProblem, A]` runs `z` in `CorrelationId.scoped` and
  uses `catchAllCause`: a cause with a failure keeps the failure; a cause with only defects is logged once with
  `ZIO.logErrorCause` and becomes the structured 500. Every handler of `ProjectsEndpointsHandler`,
  `MaintenanceEndpointsHandler` and `ReportEndpointsHandler` wraps its logic in `guarded` inside `serverLogic`,
  so the defect is caught before ztapir resurrects it. The monitoring routes (`/info`, `/health`, `/metrics`) are
  not wrapped. Webapi's `IngestServerEndpoints` does not change. Span status follows
  `docs/observability/instrumentation-recipe.md`: no user data.
- **Failed bulk-ingest files.** `IngestFailure.errorCode(t): String` gives the `error_code` of a failure
  (`internal` for anything not typed). `BulkIngestService` (`BulkIngestService.scala:130-134`) runs each file in
  `CorrelationId.withNew` and logs a failed file once with file name, `correlation_id` and `error_code`. Phases 4 and
  5 only construct errors; they do not edit this log.
- Callers of the removed constructors, all changed to `toApiProblem` or a fixed message:
  `ProjectsEndpointsHandler.scala:51,92,124,139,141,143,145,190,234`, `HandlerFunctions.scala:15,19`,
  `MaintenanceEndpointsHandler.scala:36`. Specs that read `errorMessage` change with them, including
  `modules/test-e2e/.../slice/ingest/IngestEndpointsE2ESpec.scala`.
- `docs/03-endpoints/ingest.md` documents the error body and every `error_code` value listed below, including the
  ones later phases start to return. Phase 7 adds only its own section on accepted extensions.

**dsp-api owns these `error_code` values:** `internal`, `unsupported_format`, `sipi_killed`, `sipi_crashed`,
`sipi_no_report`, `image_too_large`. sipi's own codes are passed through. A 400 keeps today's `ApiProblem.BadRequest`
body, which has no `error_code`.

**Unique temp folders (Phase 2).** `StorageServiceLive.createTempDirectoryScoped` creates
`<base>/<yyyyMMdd_HHmmss>/<uuid>/<directoryName>`. The timestamp folder stays, so operators still see when an upload
started. This fixes all three callers: upload (`ProjectsEndpointsHandler.scala:141`), import zip and import bagit
(`ImportService.scala:52,69`). The scope's release (`StorageService.scala:146-147`) deletes the `<uuid>` folder,
not only the leaf, so no empty folder stays behind per upload.

**Sipi pin (Phase 3).** The current pin is `v10.0.1` (`MODULE.bazel:329-344`). `v10.1.0` also carries the LFS
pointer bug (DEV-8099); the new pin is `v10.2.0`. Only the two per-arch digests and the tag and
index digest in the comment above them change (`docs/05-internals/development/third-party.md`, section "Bumping
Sipi"). The comment's pointer to CLAUDE.md is changed to that section. Digests come from
`docker buildx imagetools inspect`, or from `crane digest --platform linux/amd64|linux/arm64/v8` when Docker is not
available (`crane` is in the Nix dev shell). The LFS check reads the binary with
`crane export daschswiss/sipi:<tag> - | tar -xO sbin/sipi | strings | grep -c git-lfs.github.com`.

**Sipi error mapping (Phase 4).** Follows DEV-8105 as written; its Agent notes are the code map.

- `CommandExecutorLive.execute` reads the correlation ID and runs
  `scala.sys.process.Process(command.cmd, None, "SIPI_CORRELATION_ID" -> id)`. In local dev, `buildCommand` puts
  `-e SIPI_CORRELATION_ID=<id>` before the image name in the `docker run` arguments, so `buildCommand` reads the ID
  too.
- `SipiReport` decodes phases `unknown` and `internal`, `correlation_id` and `error_code`; `input_file`,
  `output_file`, `output_format` and `file_size_bytes` are optional.
- The target of a command is a field of the `SipiCommand` case classes: `Transcode` of the `.orig` copy has
  `target = Upload`; `ApplyTopLeft` and `Query` have `target = Derivative`. `SipiCliError` carries `errorCode`,
  `correlationId` and `target`.
- `Query` renders `--json` and runs through `runWithJsonReport`. On success, `query --json` keeps its text output
  on stdout (the sipi contract), so the `StillImageService` parsers stay as they are; only their input is now stdout
  without log lines. `runWithJsonReport` treats exit 0 of `Query` as success and returns stdout without parsing it.
  The `SipiCommand.emitsJson` doc comment (`SipiClient.scala:28-36`), which says `Query` stays `false`, changes with
  it, and the `SipiClientSpec` query tests change with it.
- Today `SipiReport.Err` requires `inputFile`, `outputFile`, `outputFormat` and `fileSizeBytes`, `Phase` fails on an
  unknown value, and `SipiCliError` (`SipiReport.scala:87`) has no `errorCode`, `correlationId` or `target`.
- `runWithJsonReport` classifies exit 137 (`sipi_killed`) and 139 (`sipi_crashed`) before it parses stdout; a
  non-zero exit without a parseable document is `sipi_no_report`. All three are `SipiCliError` with phase
  `internal`.
- The client message replaces every occurrence of the document's `input_file` and `output_file` with the original
  upload filename. No regex search for paths.
- Mapping (Ivan, 2026-11-26), added as a case of `IngestFailure.toApiProblem` and `IngestFailure.errorCode`: target `Upload`, phase `read`, code
  in {`decode_failed`, `unsupported_format`, `malformed_input`, `metadata_parse_failed`} is 422; every other
  `SipiCliError` is 500 with its `error_code`.
- `CommandExecutorLive.execute` logs sipi's stderr lines at info (they carry the `[<id>]` prefix) and stops the warning with
  the full `ProcessOutput`. It no longer logs stdout of a `--json` command line by line; it logs the parsed
  report's `status`, `phase` and `error_code` once.

**Pixel limit (Phase 5).** Ivan, 2026-11-27: read width and height from the file header, reject above a
configurable pixel count.

- `ImageHeaderReader.read(path): UIO[Option[Dimensions]]` reads dimensions without decoding pixels:
  `javax.imageio.ImageReader.getWidth(0)` / `getHeight(0)` with `ImageIO.setUseCache(false)` and the stream
  closed, for TIFF, PNG and JPEG; for JP2 and JPX it walks the boxes to `jp2h` and reads the `ihdr` box inside it.
  Any read error gives `None`.
- Config `ingest.sipi.max-image-pixels` (env `INGEST_SIPI_MAX_IMAGE_PIXELS`), optional, in `ingest.conf` and
  `SipiConfig` as `maxImagePixels: Option[Long] = None` (`Configuration.scala:32`). The default keeps the other
  construction sites compiling (`ConfigurationSpec`, `SpecConfigurations`, `CommandExecutorLiveSpec`, webapi's
  `IngestModuleSpec`, testkit's `TestContainerLayers`). `IngestServiceLive` gets `SipiConfig` through its layer. Unset means no limit. Hosts set it in
  ops-deploy (Human Actions H2).
- The check runs on the `.orig` copy in `IngestService.handleImageFile` before `createDerivative`, only for the
  `StillImage` file types (not SVG, which is not converted). Above the limit it fails with
  `IngestRejected("image_too_large", "<filename> has <w> x <h> = <n> pixels; the limit is <limit>")`.
  Every image's dimensions are logged. `None` lets sipi decide, as today.
- Bulk ingest records the file as failed; the Phase 1 log gives `image_too_large` (no HTTP status there).

**Early answer while the body still streams (Phase 6).** `DspApiServer.onDecodeSuccess` already adds
`Connection: close` when a `ClosesOnUnreadBody` endpoint answers non-2xx with a declared `Content-Length` over
1 MiB. A defect in the logic is resurrected by ztapir and answered as a 500 by tapir's exception handler
(`DspApiServer.scala:99-102`). The risk is that this response is built by an interceptor outside the `.map` in
`onDecodeSuccess` (`DspApiServer.scala:328-339`) and so never gets `Connection: close`. Phase 6 tests it at the
webapi level, independent of Phase 1: a test server with `DspApiServer.serverOptions` (`private[core]`, so the spec
lives in `org.knora.webapi.core`, next to `ClosesOnUnreadBodyInterceptorSpec`, whose helpers it reuses) and a stub
endpoint marked `ClosesOnUnreadBody` whose logic reads one chunk of a streamed body and then fails, once with a
typed error and once with a defect. A client sends 20 MiB with `Content-Length` and must get the 500 with
`Connection: close` within 10 s. If both cases pass on the base branch, the test is committed as the regression
guard and the commit body says that the cause of DEV-8106 was not reproduced at this level. If one fails, the phase
applies the close rule to that path.

**Filename and file type (Phase 7).** The endpoint takes `path[String]("filename")` and validates it in the server
logic with `AssetFilename.from`; a `Left` gives 400 `ApiProblem.BadRequest(Argument("filename", reason))`. For an
unsupported extension, `AssetFilename.from` returns `"File type '.<ext>' is not supported. Supported: <list>"`, the
list from `SupportedFileType`, sorted. `docs/03-endpoints/ingest.md` lists the accepted extensions; a test in
`SupportedFileTypeSpec` reads that file and checks that its list equals `SupportedFileType`'s.

**Unreferenced-assets report (Phase 8).** Ivan, 2026-11-27: report only.

- `GET /v3/projects/{shortcode}/assets/unreferenced`, in `slice/api/v3`, with an OpenAPI schema, built with
  `V3BaseEndpoint.systemAdminPolicy` (docs/adr/0013), so the description carries the policy sentence.
- Paginated as docs/adr/0010 requires: `PageAndSize.queryParams()` in, `PagedResponse[UnreferencedAsset]` out
  (`slice/api/Pagination.scala`, as in `ViewRestrictionsEndpoints`). An item is
  `{ assetId, internalFilename, originalFilename, referencedOnlyByDeletedResources }`, sorted by `assetId`. The
  service computes the full list and the endpoint returns the requested page of it.
- An asset is referenced when some `knora-base:fileValueHasFilename` in the project's data graph, with the extension
  removed, equals its asset ID. This covers every file type (image `.jpx`/`.jp2`, video, audio, documents), because
  internal filenames are `<assetId>.<ext>`. A reference whose resource has `knora-base:isDeleted true` counts as
  "only by deleted resources".
- The query uses the `sparql"..."` interpolator (`docs/development/sparql-queries.md`) on the project's data graph.
- `AuthorizationMatrixE2ESpec` gets a row for the endpoint with the SystemAdmin outcomes.
- The asset list comes from ingest in-process: new `DspIngestClient.listAssets(shortcode)` (from
  `AssetInfoService.findAllInPath` on the project folder, run on `IngestExecutor` like the other methods).
  `TestDspIngestClient` (`modules/testkit`) and `DspIngestClientLiveSpec` get the method too.

**Standoff link URIs (Phase 9).** A new `StandoffLinkUri.validate(s): Option[IRI]` in `messages/util/standoff`
escapes like `Iri.validateAndEscapeIri` (`encodeAllowEscapes`), then validates with commons-validator
`UrlValidator(Array("http", "https"), ALLOW_LOCAL_URLS + ALLOW_2_SLASHES)`. It returns the escaped string, so the
stored value has the same form as today. `StandoffTagUtilV2.scala:856-872` uses it, `www.` fallback included.
`Iri` does not change. Test URLs (from DEV-8095):

- `https://web.archive.org/web/20260616062143/https://schneidernzunft-basel.ch/zunftgeschichte/intarsiensaal2`
- `https://web.archive.org/web/20260717023745/https://www.burgroetteln.de/presse/pressemeldungen/pressemeldungen-detailansicht/6495`
- `https://web.archive.org/web/20260224073346/https://www.nb.admin.ch/snl/de/home/ueber-uns/gs.html`
- `https://web.archive.org/web/20250210135735/https://artifiche.com/plakate/binaca-zahnpaste/`

**Empty text value (Phase 10).** `objectSparqlStringOption` maps a blank string to `None`, so an empty
`valueAsString` without XML reaches the generic "Invalid combination" branch of the private `getTextValue`.
`TextValueContentV2.from` reads whether `knora-api:valueAsString` is present at all. When it is present but blank
and there is no `textValueAsXml`, `from` fails with "Text value must not be empty: knora-api:valueAsString is
blank". The property is not known inside `from`, so the message names the field, not the resource property; the
issue's example with the property name is not met, and that is recorded in the commit body.

**Commits.** One commit per PR (`docs/development/commit-conventions.md`). Each unit is one phase, so each unit PR
is one commit. Scopes: `ingest`, `deps`, `resources`, `standoff`, `api`.

## Implementation Phases

<!-- markdownlint-disable-next-line MD001 -->
#### Phase 1: Ingest error contract and logged defects

depends on: none

- [ ] `ApiProblem`: add `UnprocessableContent`, change `InternalServerError` to `(correlationId, errorCode,
  message)`, remove its `Throwable` constructors, register 422 in `BaseEndpoints.defaultErrorOutputs`
- [ ] Add `CorrelationId` (`scoped`, `withNew`, `current`, `makeNew`), `IngestRejected`, and `IngestFailure`
  (`toApiProblem`, `errorCode`)
- [ ] Add `HandlerFunctions.guarded` and wrap the logic of every handler of `ProjectsEndpointsHandler`,
  `MaintenanceEndpointsHandler` and `ReportEndpointsHandler` in it
- [ ] Change every caller listed under Technical Considerations to `toApiProblem` or a fixed message; replace the
  two `.orDie` calls in `postProjectAssetEndpoint`; turn the two `IllegalArgumentException`s in
  `IngestService.ingestFile` into `IngestRejected`. Check: `grep -rn "InternalServerError(_)" modules/ingest/src/main`
  returns nothing, and `postProjectAssetEndpoint` has no `.orDie`
- [ ] `BulkIngestService`: each file in `CorrelationId.withNew`; one log line per failed file with file name,
  `correlation_id` and `error_code`
- [ ] Document the error body and all six dsp-api `error_code` values in `docs/03-endpoints/ingest.md`
- [ ] Test (`ProjectsEndpointSpec`): with an `IngestService` stub that fails with
  `IOException("Command failed: '/sbin/sipi convert /tmp/x/y.orig' ProcessOutput(...)")`, the response is 500 with a
  `correlation_id` of 32 hex characters and `error_code: internal`, and the body contains neither `/tmp/` nor
  `Command failed`. Catches the leak of DEV-8105
- [ ] Test (`ProjectsEndpointSpec`): when the temp file is deleted before `Files.size` (stub storage), the response is
  a JSON 500, not plain text, and the log output (ZIO `ZTestLogger`) holds the cause. Catches DEV-8123
- [ ] Test (`HandlerFunctionsSpec`, new, in `modules/ingest/src/test`): `guarded` on an effect that dies gives the
  structured 500 and exactly one error log entry (ZIO `ZTestLogger`) with the cause and the same `correlation_id`;
  on an effect that fails with an `ApiProblem` it keeps that problem and logs nothing; under an existing `trace_id`
  annotation the `correlation_id` equals it. Catches DEV-8123
- [ ] Test (`BulkIngestServiceSpec`): a failing file is logged once with its name, a `correlation_id` and
  `error_code: unsupported_format`; two failing files get different `correlation_id`s
- [ ] Test: JSON round trip of both problem types with the snake-case fields
- [ ] Update the specs that read `errorMessage`, including `IngestEndpointsE2ESpec`
- [ ] `bazel test //modules/ingest:test`, then `just test-unit` and `just check`; when Docker is available also
  `bazel test //modules/test-e2e:test --test_filter='.*IngestEndpointsE2ESpec.*'`
- [ ] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

#### Phase 2: Unique temporary folder per upload

depends on: none

- [ ] `StorageServiceLive.createTempDirectoryScoped`: path `<base>/<yyyyMMdd_HHmmss>/<uuid>/<directoryName>`; the
  release deletes the `<uuid>` folder
- [ ] Check that nothing else reads `<temp>/<timestamp>/<name>` by its old shape:
  `grep -rn "getTempFolder\|tempPath" modules/ingest/src/main` and read each hit
- [ ] Test (`StorageServiceLiveSpec`): two scopes opened with the same name at the same `TestClock` instant get
  different directories, closing one leaves the other's file in place, and after closing no `<uuid>` folder is left.
  Catches DEV-8100
- [ ] Test (`IngestServiceSpec`, real `StorageServiceLive`, `TestClock` fixed): two `ingestFile` calls for the same
  project with the same file name, each in its own `createTempDirectoryScoped("<shortcode>-ingest")`, where the first
  scope closes (a `Promise` orders it) before the second ingest reads its file; both succeed. Fails on `main`
- [ ] `bazel test //modules/ingest:test`, then `just test-unit` and `just check`
- [ ] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

#### Phase 3: Pin the new sipi release

depends on: Phase 1 (serialized only, so that Phase 4 starts from a branch that holds both; Phase 3 uses nothing from Phase 1)

- [ ] Pin `daschswiss/sipi:v10.2.0` (published 2026-11-27, the Vitrinli release with vitrinli#862). Confirm with
  `crane ls daschswiss/sipi` that the tag exists; if it does not, report `blocked`
- [ ] LFS check on that tag (command under Technical Considerations) returns 0; if it does not, report `blocked`
- [ ] When Docker is available: `docker run --rm --entrypoint /sbin/sipi daschswiss/sipi:v10.2.0 query --json /nonexistent`
  prints one JSON document with `status: "error"` and `correlation_id`. Without Docker, say in the commit body that
  this check was not run
- [ ] Update `sipi_base_amd64`, `sipi_base_arm64` and the comment in `MODULE.bazel`; point the comment to the section
  "Bumping Sipi" of `docs/05-internals/development/third-party.md`
- [ ] `bazel build //modules/webapi:image_amd64`, `just test-unit`, then `just check`
- [ ] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

#### Phase 4: Map sipi failures to 422 or 500

depends on: Phase 1 (uses `IngestFailure`, `CorrelationId` and the error types); Phase 3 (uses sipi's `--json` error contract)

- [ ] `CommandExecutorLive`: pass `SIPI_CORRELATION_ID` (process environment; `-e` before the image in local dev);
  change the logging as described
- [ ] `SipiReport`: phases `unknown` and `internal`, `correlation_id`, `error_code`, the four optional fields of
  `Err`. `SipiCommand`: `target`. `SipiCliError`: `errorCode`, `correlationId`, `target`
- [ ] `Query` renders `--json` and runs through `runWithJsonReport` (exit 0 returns stdout unparsed); update the
  `emitsJson` doc comment and the `SipiClientSpec` query tests; classify exit 137 and 139 and no report
- [ ] Replace `input_file` and `output_file` in the client message with the upload filename
- [ ] Add the `SipiCliError` case to `IngestFailure.toApiProblem` and `IngestFailure.errorCode`
- [ ] Tests with `CommandExecutorMock` (`SipiClientSpec`, `SipiReportSpec`, `SipiCommandSpec`, `IngestServiceSpec`,
  `BulkIngestServiceSpec`, `ProjectsEndpointSpec`), one per row of the mapping table: convert of the upload, phase
  `read`, each of the four codes is 422; the same codes with target `Derivative` are 500; another phase or code is
  500 with that code; exit 137 is 500 `sipi_killed`; exit 139 is 500 `sipi_crashed`; non-zero exit without JSON is
  500 `sipi_no_report`; the fallback document without the four optional fields decodes. Catches DEV-8105
- [ ] Test: a report with `metadata_parse_failed` and `input_file: /data/x/1234.jpg.orig` gives 422 whose message
  holds the upload filename and not `/data/`
- [ ] Test (`CommandExecutorLiveSpec`): with the prod layer, `executeOrFail` of `printenv SIPI_CORRELATION_ID` under
  `CorrelationId.withNew` prints that ID (as the existing OOM test runs a real command); with the local-dev layer,
  `buildCommand` puts `-e SIPI_CORRELATION_ID=<id>` before the image name. This proves the ID reaches sipi; that sipi
  prefixes its log lines with it is sipi's contract and is not tested here
- [ ] Test (`BulkIngestServiceSpec`): a file whose sipi convert fails is logged with the sipi `error_code`
- [ ] `bazel test //modules/ingest:test`, then `just test-unit` and `just check`
- [ ] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

#### Phase 5: Reject images above the pixel limit before conversion

depends on: Phase 1 (uses `IngestRejected` and `CorrelationId`)

- [ ] Add `ImageHeaderReader` (ImageIO for TIFF, PNG, JPEG; `jp2h`/`ihdr` for JP2 and JPX)
- [ ] Add `max-image-pixels` to `ingest.conf` and `SipiConfig` (`maxImagePixels: Option[Long] = None`); wire
  `SipiConfig` into `IngestServiceLive`; `ConfigurationSpec` reads the env variable
- [ ] The check in `IngestService.handleImageFile`
- [ ] Tests (`ImageHeaderReaderSpec`, new): small fixtures generated in the test (ImageIO writes PNG, JPEG, TIFF; a
  hand-built JP2 box sequence) return the right dimensions; a truncated file returns `None`
- [ ] Tests (`IngestServiceSpec`): a TIFF written in the test whose header declares 100000 x 100000 pixels (a strip
  TIFF with one tiny strip; the pixels are never read) is rejected with `image_too_large` and `SipiClientMock` records
  no call; with no limit set it reaches sipi; an unreadable header reaches sipi. Catches DEV-8101
- [ ] `bazel test //modules/ingest:test`, then `just test-unit` and `just check`
- [ ] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

#### Phase 6: The client sees an early error at once

depends on: none

- [ ] Test (`modules/webapi/src/test/.../core/EarlyErrorStreamingSpec.scala`, new): the two cases under Technical
  Considerations, against a test server built with `DspApiServer`'s server options
- [ ] If a case fails: apply the close rule to that path, and make `ClosesOnUnreadBodyInterceptorSpec` cover it. If
  both pass: commit the test only, and say in the commit body that DEV-8106 was not reproduced at this level
- [ ] `bazel test //modules/webapi:test --test_filter='.*(EarlyErrorStreaming|ClosesOnUnreadBody).*'`, then `just test-unit` and `just check`
- [ ] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

#### Phase 7: Say why a filename or file type is rejected

depends on: Phase 1 (both rewrite `postProjectAssetEndpoint`, and this phase changes the type of its `filename`)

- [ ] `postProjectAsset`: `path[String]("filename")`, validated in the handler; 400 with the reason
- [ ] `AssetFilename.from`: the unsupported-extension reason names the extension and the supported list
- [ ] `docs/03-endpoints/ingest.md`: a section with the accepted extensions; the test in `SupportedFileTypeSpec` that
  checks the list
- [ ] Update `AssetFilenameSpec` for the new reason text
- [ ] Test (`ProjectsEndpointSpec`): `POST .../ingest/m-ds-kirche-18_1.gif` returns 400 whose body contains `gif` and
  `tif`; `POST .../ingest/a%2Fb.tif` returns 400 with the path reason. Catches DEV-8094
- [ ] `bazel test //modules/ingest:test`, then `just test-unit` and `just check`
- [ ] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

#### Phase 8: Report unreferenced assets

depends on: none

- [ ] `DspIngestClient.listAssets`, in `DspIngestClientLive`, `TestDspIngestClient` and `DspIngestClientLiveSpec`
- [ ] Repository query (`sparql"..."`) for the project's `fileValueHasFilename` values and their resources' `isDeleted`
- [ ] Service that computes the set difference; endpoint `GET /v3/projects/{shortcode}/assets/unreferenced` with
  `V3BaseEndpoint.systemAdminPolicy`, `PageAndSize.queryParams()` and `PagedResponse`, registered in the v3 aggregator
- [ ] `AuthorizationMatrixE2ESpec`: a row for the endpoint with the SystemAdmin outcomes
- [ ] Unit test of the service: an asset without a reference is listed; one referenced only by a deleted resource is
  listed with the flag; one with a live reference is not; a `.jp2` and a `.mp4` reference both match their asset IDs
- [ ] E2e test (`modules/test-e2e`): upload a file through the ingest route as in `IngestEndpointsE2ESpec`, create no
  resource, and the report lists it; a resource's asset from the loaded test data is not listed; a project admin who
  is not SystemAdmin gets 403; `page-size=1` returns one item and the right `totalItems`. Catches DEV-8096
- [ ] `bazel test //modules/webapi:test --test_filter='.*Unreferenced.*'`, `bazel test //modules/test-e2e:test
  --test_filter='.*(Unreferenced|AuthorizationMatrix).*'` (needs Docker), then `just test-unit` and `just check`
- [ ] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

#### Phase 9: Accept Wayback Machine URLs in standoff links

depends on: none

- [ ] `StandoffLinkUri` and its use in `StandoffTagUtilV2`
- [ ] Test (`modules/webapi/src/test/.../messages/util/standoff/StandoffLinkUriSpec.scala`, new): the four URLs are
  accepted and returned in escaped form equal to `Iri.validateAndEscapeIri` on a URL without `//`; `javascript:alert(1)`,
  `ftp://example.org/x` and `https://exa mple.org` are rejected. Catches DEV-8095
- [ ] `bazel test //modules/webapi:test --test_filter='.*StandoffLinkUri.*'`; when Docker is available also
  `bazel test //modules/test-it:test --test_filter='.*StandoffTagUtilV2Spec.*'`; then `just test-unit` and `just check`
- [ ] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

#### Phase 10: Say that a text value is empty

depends on: none

- [ ] `TextValueContentV2.from`: the blank-but-present case fails with the new message
- [ ] Test (`ValueContentV2Spec`): `valueAsString: ""` and `"  "` without XML fail with the new message; a missing
  `valueAsString` without XML keeps the old message. Catches DEV-8098
- [ ] `ValuesEndpointsE2ESpec` "not update a text value so it's empty": also assert that the response body contains
  "must not be empty"
- [ ] `bazel test //modules/webapi:test --test_filter='.*ValueContentV2Spec.*'`; when Docker is available also
  `bazel test //modules/test-e2e:test --test_filter='.*ValuesEndpointsE2ESpec.*'`; then `just test-unit` and `just check`
- [ ] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

## Threads

Up to 6 units in parallel.

```yaml
plan_branch: plan/2026-11-27-ingest-hardening
max_parallel: 6
units:
  - unit: contract
    phases: [1]
    depends_on: []
    base: plan
    branch: impl/ingest-hardening-contract
  - unit: tempdir
    phases: [2]
    depends_on: []
    base: plan
    branch: impl/ingest-hardening-tempdir
  - unit: sipi-pin
    phases: [3]
    depends_on: [contract]
    base: contract
    branch: impl/ingest-hardening-sipi-pin
  - unit: sipi-errors
    phases: [4]
    depends_on: [contract, sipi-pin]
    base: sipi-pin
    branch: impl/ingest-hardening-sipi-errors
  - unit: pixel-limit
    phases: [5]
    depends_on: [contract]
    base: contract
    branch: impl/ingest-hardening-pixel-limit
  - unit: early-close
    phases: [6]
    depends_on: []
    base: plan
    branch: impl/ingest-hardening-early-close
  - unit: filename
    phases: [7]
    depends_on: [contract]
    base: contract
    branch: impl/ingest-hardening-filename
  - unit: report
    phases: [8]
    depends_on: []
    base: plan
    branch: impl/ingest-hardening-report
  - unit: standoff-uri
    phases: [9]
    depends_on: []
    base: plan
    branch: impl/ingest-hardening-standoff-uri
  - unit: empty-text
    phases: [10]
    depends_on: []
    base: plan
    branch: impl/ingest-hardening-empty-text
  - unit: final
```

Six units start at once: `contract`, `tempdir`, `early-close`, `report`, `standoff-uri`, `empty-text`. After
`contract`, three more start: `sipi-pin`, `pixel-limit`, `filename`. `sipi-errors` needs `contract` and
`sipi-pin`; `sipi-pin` is serialized on `contract` so that its branch holds both.

## Human Actions

| Id | Action | Who | When | Why not the agent |
| --- | --- | --- | --- | --- |
| H1 | Read the plan PR, then answer the start card | Ivan | before start | Accepting the plan is the owner's |
| H2 | Set `INGEST_SIPI_MAX_IMAGE_PIXELS` per host and the new sipi version in ops-deploy, then deploy to the rdu hosts and prod | Ivan or Infra | after ship | Deployment and host sizing are outside dsp-api |
| H3 | Tell DEV-8114 (dsp-tools) that the ingest 500 body changed from `errorMessage` to `correlation_id`, `error_code`, `message`, and that invalid file content now answers 422 | Ivan | after ship | Another repository's work |
| H4 | Reply to Moritz through Daniela or Balduin: parallel upload fixed, the unreferenced-assets report, GIF not supported | Daniela or Balduin | after ship | A message outside the project |
| H5 | Run `gh stack merge --rebase <top PR>` on the plan's top PR | Ivan | after ship | Merging is the owner's |

## Requirements

The repository has no `bdd.yaml`, so each requirement is proven by the named test.

- REQ-1 [B] (Event-driven): When two uploads to the same project start in the same second, ingest shall give each
  upload its own temporary folder. Check: Phase 2 tests.
- REQ-2 [B] (Unwanted-behaviour): If a request to an ingest route other than `/info`, `/health` and `/metrics` fails
  unexpectedly, then ingest shall answer 500 with a JSON body that holds `correlation_id`, `error_code` and
  `message`. Check: Phase 1 tests.
- REQ-3 [B] (Ubiquitous): Ingest shall not put a filesystem path, a command line or process output in an error body.
  Check: Phase 1 and Phase 4 tests assert the absence of `/tmp/`, `/data/` and `Command failed`.
- REQ-4 (Unwanted-behaviour): If the logic of an ingest route other than `/info`, `/health` and `/metrics` dies, then
  ingest shall log the cause once with the request's `correlation_id`. Check: Phase 1 `HandlerFunctionsSpec`.
- REQ-5 (Ubiquitous): Every sipi run shall get the request's `correlation_id` in `SIPI_CORRELATION_ID`. Check: Phase 4
  `CommandExecutorLiveSpec`.
- REQ-6 [B] (Unwanted-behaviour): If sipi `convert` of the upload fails in phase `read` with `decode_failed`,
  `unsupported_format`, `malformed_input` or `metadata_parse_failed`, then ingest shall answer 422 with that
  `error_code`. Check: Phase 4 mapping tests.
- REQ-7 [B] (Unwanted-behaviour): If sipi is killed (exit 137), crashes (exit 139), or writes no JSON report, then
  ingest shall answer 500 with `sipi_killed`, `sipi_crashed` or `sipi_no_report`. Check: Phase 4 mapping tests.
- REQ-8 (Unwanted-behaviour): If a bulk-ingest file fails, then ingest shall log its file name, `correlation_id` and
  `error_code`. Check: Phase 1 and Phase 4 `BulkIngestServiceSpec`.
- REQ-9 [B] (Optional): Where `max-image-pixels` is set, when an image header declares more pixels, ingest shall
  answer 422 `image_too_large` with the pixel count and the limit, without running sipi. Check: Phase 5 tests.
- REQ-10 [B] (Unwanted-behaviour): If ingest answers an error before it has read a streamed upload body, then the
  client shall receive the response with `Connection: close` within 10 s. Check: Phase 6 test.
- REQ-11 [B] (Unwanted-behaviour): If an upload's filename has an unsupported extension, then ingest shall answer 400
  with a message that names the extension and the supported extensions. Check: Phase 7 test.
- REQ-12 [B] (Event-driven): When a SystemAdmin requests a project's unreferenced assets, dsp-api shall list every
  asset of the project that no live resource references, and mark those referenced only by deleted resources.
  Check: Phase 8 tests.
- REQ-13 [B] (Event-driven): When a text value has a standoff link to an http or https URL with `//` in its path,
  dsp-api shall accept the link. Check: Phase 9 test.
- REQ-14 [B] (Unwanted-behaviour): If a text value has a blank `valueAsString` and no XML, then dsp-api shall answer
  400 with a message that says the text value must not be empty. Check: Phase 10 tests.
- REQ-15 (Ubiquitous): The dsp-api image shall contain a sipi build without Git LFS pointer text in its binary.
  Check: Phase 3 LFS check returns 0.

## Out of Scope

- DEV-8097 (HTTP 429): dsp-api returns no 429 anywhere (grep over `modules/`); the limit is in Traefik in ops-deploy.
  Rate-limit docs belong to DEV-8112.
- Deleting unreferenced assets (the delete part of DEV-8096): its own issue.
- GIF support (DEV-8094): rejected with a clear error instead.
- The property name in the empty-text message (DEV-8098): not known where the value is parsed.
- `sipi verify` as the pre-check (DEV-8118), alerting (DEV-8104), rdu host sizing (DEV-8102, DEV-8103), docs for size
  limits (DEV-8112), dsp-tools display (DEV-8114), the sipi bugs themselves (DEV-8088 to DEV-8093).
- DEV-8079 (in review elsewhere) and DEV-7451 (decimal precision, needs discussion).

## Normative Sources

1. Ivan's decisions: mapping rule (2026-11-26); 422 Unprocessable Content as a new `ApiProblem` type (2026-11-27);
   orphaned assets, report only (2026-11-27); pixel limit from the image header (2026-11-27).
2. The Linear issues DEV-8094, DEV-8095, DEV-8096, DEV-8098, DEV-8099, DEV-8100, DEV-8101, DEV-8105, DEV-8106,
   DEV-8123, and their Agent notes.
3. The sipi contract: vitrinli `docs/src/guide/json-output.md` (Error codes; Rule for callers: 4xx or 5xx;
   Correlation ID), as shipped with vitrinli#862.
4. `docs/development/error-handling.md`, `docs/development/sparql-queries.md`,
   `docs/development/commit-conventions.md`, `docs/observability/instrumentation-recipe.md`.

## Dependencies & Risks

- `sipi-pin` depends on `daschswiss/sipi:v10.2.0` on Docker Hub (present on 2026-11-27). If its LFS check fails,
  `sipi-pin` and `sipi-errors` report `blocked`; the other eight units are not affected.
- Phase 1 changes the 500 body shape of every ingest route except `/info`, `/health` and `/metrics`. dsp-tools reads `errorMessage` today (H3).
- Phase 6 may not reproduce DEV-8106 at the webapi level. Then it adds only the regression test, and the cause stays
  open in DEV-8106.
- Phases 1, 8, 9 and 10 have tests that need Docker (`test-e2e`, `test-it`). A thread without Docker runs the unit tests,
  says in the commit body which Docker tests it did not run, and `final` runs them.
- Phase 8 lists every asset of a project in one response; projects in scope have at most a few thousand assets.

## Success Metrics

- A rerun of Moritz's 4-worker import on rdu-13 has 0 failures from the temp-folder race (baseline: 81 of 1,221).
  Operational, measured after H2.
- Every failed ingest response carries a `correlation_id` that is found in the dsp-api logs. Operational; the unit
  tests prove the mechanism.

## References

- `modules/ingest/src/main/scala/swiss/dasch/domain/StorageService.scala:138-150`
- `modules/ingest/src/main/scala/swiss/dasch/api/ProjectsEndpointsHandler.scala:133-146`
- `modules/ingest/src/main/scala/swiss/dasch/api/ApiProblem.scala:67-76`, `BaseEndpoints.scala:54-63`
- `modules/ingest/src/main/scala/swiss/dasch/domain/IngestService.scala:48-93`
- `modules/ingest/src/main/scala/swiss/dasch/domain/SipiClient.scala`, `SipiReport.scala`
- `modules/ingest/src/main/scala/swiss/dasch/infrastructure/CommandExecutorLive.scala`
- `modules/ingest/src/main/scala/swiss/dasch/domain/AssetFilename.scala:14-32`, `SupportedFileType.scala:61-71`
- `modules/webapi/src/main/scala/org/knora/webapi/core/DspApiServer.scala:99-102`, `:160-163`, `:281-371`
- `modules/webapi/src/main/scala/org/knora/webapi/slice/ingest/IngestServerEndpoints.scala:36-41`
- `modules/webapi/src/main/scala/org/knora/webapi/slice/admin/domain/service/DspIngestClient.scala:45-62`
- `modules/webapi/src/main/scala/org/knora/webapi/messages/util/standoff/StandoffTagUtilV2.scala:856-872`
- `modules/webapi/src/main/scala/org/knora/webapi/messages/v2/responder/valuemessages/ValueMessagesV2.scala:1263-1339`
- `docs/05-internals/development/third-party.md` (Bumping Sipi)
- Post-mortem dasch-swiss/infra#618; omeka2dsp issue Stadt-Geschichte-Basel/omeka2dsp#45
