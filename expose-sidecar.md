Add the sidecar fields to `MetadataRecord.file` on `POST /v3/export/resources/oai`:
checksum, mimetype, file name, creation date, file size.
Code refs are from `feat/migrate-sidecar-sizes` (d9b332c3d).

# Decisions

- **Read sidecars in-process** via ingest's `AssetInfoService`, from the same asset dir as
  ingest. Not HTTP: ingest is being merged into API (agreed with the architect), so this is the
  target state, and N HTTP calls could hit the export timeouts.
- **Creation date**: the resource's `creationDate` from the triplestore — already read as
  `r.creationDate` (`ExportService.scala:122`). No sidecar field, no migration. There is no
  per-asset timestamp anywhere: ingest's SQLite has only a project table.
- **Original, always**: `checksumOriginal`, `sizeOriginal`, `originalFilename`,
  `originalMimeType`. Matches `FileLink.url`, already pointing at `/assets/{id}/original`.
- **Checksum algorithm**: hardcoded. Only SHA-256 is ever stored, so the original request's
  "hash function name (e.g. md5)" resolves to a constant; no md5 is available.
- **File size**: JSON integer.
- **Fetch all sidecars**: no batching or streaming; expensive by design, cached elsewhere.
- **Missing sidecars**: not a real case on prod; no partial-failure policy.
- **Response shape**: additive change is fine, no coordination needed.

# Wiring

`//modules/ingest` is already `//visibility:public` (`modules/ingest/BUILD.bazel:50`), so
adding it to webapi's `deps` is a one-line change. Entry point is
`AssetInfoService.findByAssetRef: Task[Option[AssetInfo]]` (`AssetInfoService.scala:96`);
`AssetInfo` (`:85`) exposes every needed field. (`AssetInfoFileContent` is `private`, but
irrelevant — `AssetInfo` is what the service returns.) `AssetInfoServiceLive` needs only
`StorageService`, which needs only `StorageConfig` — no DB, no Sipi, no HTTP.

Both wired on `api` in `docker-compose.yml`: mount `./modules/sipi/images:/opt/images:ro`
(same host dir and container path as `ingest`; read-only since ingest owns writes) and
`KNORA_WEBAPI_DSP_INGEST_ASSET_DIR=/opt/images`, matching ingest's `STORAGE_ASSET_DIR`. The
two must always agree.

**TODO: mirror the mount and env var in ops-deploy.** Local dev only until then; external
follow-up, not a blocker.

Config goes in `app.dsp-ingest` (`webapi/src/main/resources/application.conf:37`), which
already holds `base-url`/`audience`. webapi builds ingest's `StorageConfig` from its own
setting rather than parsing ingest's config — `tempDir` is unused for reads.

Fallback if the shared dir turns out unavailable: `DspIngestClient.getAssetInfo`
(`DspIngestClient.scala:68`) already returns everything needed, at the cost of N HTTP calls.

# Implementation notes

**Fields** go on `FileLink` (`MetadataRecord.scala:46`), today `(mimeType, url)`:
`checksum`, `checksumAlgorithm`, `fileName`, `fileSize`, `dateCreated`.

**Change the exported mimetype.** `FileLink.mimeType` currently carries the triplestore's
`internalMimeType` (`ExportService.scala:141`) — the *derivative's*. Under "always original"
it becomes the sidecar's `originalMimeType`. Deliberate change of meaning, not a new field.

**Size type.** Ingest's `SizeInBytes` (`SizeInBytes.scala:16`) is a `Long` value class whose
codec is already `JsonCodec[Long].transform(...)`, so it encodes as a bare number. `Long` or
`SizeInBytes` both work. Keep it `Option` — a `0` default would report a real file as zero
bytes. Don't reuse `MetadataRecord.size`: it's `Option[String]`, meant for a human-readable
record size.

**`fileLinkOf` becomes effectful** (`ExportService.scala:136`) — reading a sidecar is I/O, so
`records = ...map` (`:110`) becomes a `ZIO.foreach`, and `ExportService` gains
`AssetInfoService`. Not driven by the creation date, which is already in hand at `:122`.

**`sizeOriginal` is absent pre-migration.** Distinct from a missing sidecar: the file is there,
the field isn't. Only meaningful where the size migration (`AssetSizeMigrationService.scala`)
has run.

**`AssetId` is duplicated** — webapi's (`MaintenanceRequests.scala:24`) vs ingest's
(`AssetModel.scala:22`), same for `Shortcode`/`ProjectShortcode`. Building an `AssetRef` needs
ingest's. Convert via `String` at the boundary; the merge should collapse them.

**Tests** in `ExportServiceSpec`. Needs a real `AssetInfoService` over a temp dir of `.info`
files, not an HTTP fake — reuse `AssetSizeMigrationServiceSpec.scala` and
`swiss/dasch/test/SpecConstants.scala`. Cover an absent `sizeOriginal`. Per the repo's
test-data rule, check for an existing fixture with a file value before touching a shared
dataset.
