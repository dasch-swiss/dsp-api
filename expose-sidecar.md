# we're going to iterate on this document, don't delete this line.

in export service we have an endpoint for dsp-repo that sends JSON about records.
read the docker-compose.
the resources come from the triplestore, but for each file, we'd have to access the sidecar, which is in the volume attached to ingest.
we can access this in API, so we can add it to the docker-compose (it hasn't been attached yet).
the fields that have to be added are listed below

-- Fields
Checksum and hash function name (e.g., md5)
Mimetype
File name
Creation date
File size in bytes

---

# Decisions

Settled 2026-08-20. Code references are from `feat/migrate-sidecar-sizes` (d9b332c3d).

- **Creation date**: from the triplestore, the resource's `creationDate` — already read as
  `r.creationDate` and already populating `MetadataRecord.dateCreated`
  (`ExportService.scala:122`). Reuse that same value for the file block. No ingest change,
  no sidecar field, no migration. Granularity is per-resource, which equals per-file here
  because a resource has at most one file value.
- **Original, always**: for every field with an original/derivative pair, expose the original —
  `checksumOriginal`, `sizeOriginal`, `originalFilename`, `originalMimeType`. This also matches
  `FileLink.url`, which already points at `/assets/{id}/original` (`ExportService.scala:142`).
  Includes changing the mimetype the export service already emits, which is currently the
  derivative's — see implementation notes.
- **Fetch all sidecars**: no batching, no lazy loading, no streaming. The endpoint is expected
  to be expensive; caching is handled elsewhere, not in this change.
- **Checksum algorithm**: hardcoded, not read from the sidecar. Only SHA-256 is ever stored
  (`Sha256Hash`, produced by `FileChecksumService.createSha256Hash`).
- **Missing sidecars**: not a real case on prod. No partial-failure policy; let it fail.
- **Response shape**: additive change to the JSON contract is fine, no coordination needed.
- **Shared asset directory**: API reads the same asset dir as ingest, in-process via ingest's
  own classes. Mount and env var are in `docker-compose.yml`; ops-deploy is a follow-up.
- **File size**: exported as a JSON integer. Scala type is free (`Long` / `SizeInBytes`) as
  long as it encodes to a bare number.
- **Test coverage**: going ahead with it; reuse ingest's temp-dir `AssetInfoService` setup.

## Gap 1 — how API reads the sidecar (resolved: direct call into ingest's classes)

Ingest is going to be merged into API (agreed with the architect). That makes a direct
in-process call the target state, not a shortcut — so build toward it rather than adding an
HTTP hop that would be removed again. HTTP per file also risks being slow enough to hit the
export timeouts.

**Chosen: depend on `//modules/ingest` from webapi and call `AssetInfoService` directly.**

Verified feasible:

- `//modules/ingest` is already `visibility = ["//visibility:public"]`
  (`modules/ingest/BUILD.bazel:50`), so adding it to `//modules/webapi`'s `deps`
  (`modules/webapi/BUILD.bazel`) is a one-line change. webapi does not depend on it today.
- `AssetInfoService.findByAssetRef(assetRef): Task[Option[AssetInfo]]`
  (`AssetInfoService.scala:96`) is the entry point; `AssetInfo` (`:85`) exposes
  `original.checksum`, `original.size`, `originalFilename`, and `metadata.originalMimeType` —
  every field needed. Note `AssetInfoFileContent` (`:35`) is `private`, but that does not
  matter: `AssetInfo` is public and is what the service returns.
- `AssetInfoServiceLive` needs only `StorageService`, and `StorageServiceLive` needs only
  `StorageConfig(assetDir, tempDir)` (`config/Configuration.scala:43`) — no DB, no Sipi, no
  HTTP. So the layer is cheap to construct inside webapi without dragging in ingest's app wiring.

Fallback if this turns out to be blocked: `DspIngestClient.getAssetInfo(shortcode, assetId)`
(`DspIngestClient.scala:68`) already exists and returns everything needed — but N HTTP calls
against a whole-project export is exactly the timeout risk above.

### Shared asset directory — committed

API reads the **same asset directory as ingest**. Both are wired in `docker-compose.yml`:

- `api` mounts `./modules/sipi/images:/opt/images:ro` — same host dir and container path as
  the `ingest` service, so both resolve identical asset paths. Read-only: API only reads
  sidecars, ingest owns writes.
- `api` gets `KNORA_WEBAPI_DSP_INGEST_ASSET_DIR=/opt/images`, matching ingest's
  `STORAGE_ASSET_DIR=/opt/images`. The two must always agree.

**TODO: mirror both the mount and the env var in ops-deploy for every environment.** Until
that lands this works in local dev only. Treated as an external follow-up, not a blocker on
this work.

Config placement: `app.dsp-ingest` in `modules/webapi/src/main/resources/application.conf:37`
already holds the ingest-facing settings (`base-url`, `external-base-url`, `audience`), so
`asset-dir` belongs there with the `KNORA_WEBAPI_DSP_INGEST_ASSET_DIR` override, alongside
them. webapi then builds ingest's `StorageConfig(assetDir, tempDir)` from its own setting
rather than parsing ingest's `application.conf` — no shared config schema, and `tempDir` is
irrelevant for reads (pass the same path or a dummy; `AssetInfoService` only uses `assetPath`).

### Remaining sub-question

- **`AssetId` is duplicated.** webapi has its own in
  `slice/api/admin/model/MaintenanceRequests.scala:24`, ingest has
  `domain/AssetModel.scala:22` — both `RefinedTypeOps[.., String]`. Building an ingest
  `AssetRef` requires ingest's `AssetId` plus a `ProjectShortcode` (also duplicated vs.
  webapi's `Shortcode`). Converting via `String` at the boundary is fine for now; worth
  flagging as something the merge should collapse.

# Implementation notes

Consequences of the decisions above, for whoever picks this up.

## Where the fields go

Extend `FileLink` (`slice/api/v3/export/MetadataRecord.scala:46`), currently
`(mimeType, url)`. Adding: `checksum`, `checksumAlgorithm` (hardcoded), `fileName`,
`fileSize`, `dateCreated`.

**Change the exported mimetype.** `FileLink.mimeType` today comes from the triplestore's
`internalMimeType` (`ExportService.scala:141`) — the *derivative's* type. Under "always
original" it becomes the sidecar's `originalMimeType`. This is a deliberate change in meaning
for an existing field, not a new field alongside it.

**File size is a JSON integer.** The Scala type is free to vary as long as it serializes to a
bare JSON number — no quotes, no formatting. Ingest's `SizeInBytes`
(`modules/ingest/.../domain/SizeInBytes.scala:16`) is a `Long` value class whose codec is
already `JsonCodec[Long].transform(...)`, so it serializes as an int for free; `Long` or
`SizeInBytes` both satisfy the requirement, `String` does not.

This rules out reusing `MetadataRecord.size` (`ExportService.scala:126`), which is hardcoded
`None` and typed `Option[String]` — a string field, evidently intended for a human-readable
record size. Leave it alone and put file size in the new `FileLink` field.

Note the field stays optional on the Scala side: `sizeOriginal` is `Option[SizeInBytes]` in the
sidecar and is genuinely absent pre-migration (see below). "JSON int" constrains the *encoding*
when a value is present, not whether the key can be absent — so `Option[Long]` is fine, but a
default of `0` would be wrong (it would report a real file as zero bytes).

## Rewiring `fileLinkOf`

`fileLinkOf` (`ExportService.scala:136`) is currently pure and synchronous — it collects the
first `FileValueContentV2` and builds a URL from the asset id, both from data already in hand.
Reading the sidecar is I/O, so it has to return an effect — `AssetInfoService.findByAssetRef`
is `Task[Option[AssetInfo]]`. Going in-process avoids the network, not the effect.

Consequence: `records = readResources.resources.toList.map { ... }`
(`ExportService.scala:110`) becomes a `ZIO.foreach`, and `ExportService` gains
`AssetInfoService` as a constructor dependency.

Note this is *not* driven by the creation date — that comes from `r.creationDate`, already
available at `ExportService.scala:122` with no fetch. Only the sidecar fields force the effect.

## Caching

Not done here — caching happens elsewhere.

## Sizes are absent pre-migration

`sizeOriginal` is `Option` and is absent for every asset ingested before the size migration on
this branch (`AssetSizeMigrationService.scala`, `MigrateSizes.scala`). Distinct from the
"sidecars are never missing" case — the sidecar is there, the field inside it is not. The
migration must have been run in an environment before file sizes are meaningful there.

## Tests

`ExportServiceSpec` (`modules/webapi/src/test/.../export_/ExportServiceSpec.scala`) is the home
for this.

Going in-process changes what the test needs: not a fake HTTP client, but a real
`AssetInfoService` over a temp dir containing `.info` files. Ingest's own specs already do
exactly that — see `AssetSizeMigrationServiceSpec.scala` for the layer setup and
`swiss/dasch/test/SpecConstants.scala` for asset-id fixtures. Reuse that rather than inventing
one. The existing `getAssetInfo` stubs (`ProjectMigrationExportServiceSpec.scala:58`,
`ProjectMigrationImportServiceSpec.scala:267`) only apply if the HTTP fallback is taken.

Also cover: a sidecar whose `sizeOriginal` is absent (the pre-migration case above), since that
is reachable in real environments and must not fail the export.

Per the repo's test-data rule, check whether an existing fixture already has a resource with a
file value before adding to a shared dataset.
