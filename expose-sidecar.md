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
adding it to webapi's `deps` is a one-line BUILD change. Entry point is
`AssetInfoService.findByAssetRef: Task[Option[AssetInfo]]` (`AssetInfoService.scala:96`);
`AssetInfo` (`:85`) exposes every needed field. (`AssetInfoFileContent` is `private`, but
irrelevant — `AssetInfo` is what the service returns.) `AssetInfoServiceLive` needs only
`StorageService`, which needs only `StorageConfig` — no DB, no Sipi, no HTTP.

**Classpath caveat — check before relying on the one-line dep.** The dep also puts ingest's
`src/main/resources` on webapi's classpath, and *both* modules ship an `application.conf`.
webapi resolves config via `ConfigFactory.load()` (`AppConfig.scala:319`), so two same-named
resources now compete and the winner is classpath-order dependent. Verify webapi's config still
loads correctly after adding the dep; if the confs collide, strip ingest's resources from the
dep (a `srcs`-only Bazel target) rather than renaming either file.

Related: construct `StorageConfig(assetDir, tempDir)` directly and **never** touch ingest's
`Configuration.layer` — it calls `ConfigFactory.defaultApplication()`
(`Configuration.scala:65`), which under a shared classpath would read *webapi's*
`application.conf`. `tempDir` is unused for reads; pass the asset dir.

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

But the sidecar's `originalMimeType` is `Option[NonEmptyString]` (`AssetInfoService.scala:48`),
where the triplestore's is always present. So `mimeType` can now be absent. Fall back to
`internalMimeType` rather than making the field `Option` — it keeps the existing non-optional
`String` shape for consumers, and a derivative mimetype is a better answer than none.

**Missing sidecar.** `findByAssetRef` returns `Option`, so the code must branch even though
prod "never" hits it. Emit `FileLink` with `url` and the triplestore-derived fields, leaving
the sidecar-derived ones absent. Do not fail the export: without the mount, local dev hits this
path for *every* asset, and failing would make the endpoint unusable until ops-deploy lands.

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
files, not an HTTP fake. Cover absent `sizeOriginal`, absent `originalMimeType`, and absent
sidecar.

Two things that shape the work:

- The OAI assertion is a golden test (`assertGolden(json, "oai")`) and
  `ExportServiceSpec__oai.txt` has exactly **one** record with a `file`
  (asset `B1D0OkEgfFp-Cew2Seur7Wi`). Regenerating the golden is unavoidable; write a `.info`
  for that asset into the spec's temp asset dir so the new fields are non-null.
- The helpers worth reusing (`AssetSizeMigrationServiceSpec.scala`,
  `swiss/dasch/test/SpecConstants.scala`) live in ingest's **test** tree, so webapi's test
  target would need `//modules/ingest:test` — a bigger Bazel change than the main-source dep.
  Copying the few lines of temp-dir setup is likely cheaper than wiring the dep or moving the
  helpers to `//modules/testkit`.
