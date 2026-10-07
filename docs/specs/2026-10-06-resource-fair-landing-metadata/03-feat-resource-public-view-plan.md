---
title: "S1: Resolution and the anonymous public view"
type: feat
date: 2026-10-06
author: "Raitis Veinbahs"
status: implemented
repositories: []
linear: DEV-7420
---

# S1: Resolution and the anonymous public view

Part of the [plan](01-feat-resource-fair-landing-metadata-plan.md); design in
[02](02-resource-fair-landing-metadata-design.md) (Shape, Access, Technical Considerations).

**Needs:** nothing. **Visible change:** none.

## Implementation Phases

### Phase 1: Resolution and the anonymous public view

- [x] Run dsp-repository's `just fair-check` (F-UJI 3.5.0, needs Docker and outbound network) against the
    production ARK of one 0803 resource and one 0868 resource with files; record both baselines in the plan's
    Success Metrics
- [x] Add `DspAppConfig(url, internalUrl, shellCacheTtl)` under `app.dsp-app` in `AppConfig` and
    `application.conf` (envs `KNORA_WEBAPI_DSP_APP_URL`, `KNORA_WEBAPI_DSP_APP_INTERNAL_URL`), both URLs empty by
    default, with `.validate` guards: absolute `http(s)` URL or empty, no path, no trailing slash, TTL > 0
- [x] Log a warning at startup when `app.dsp-app.url` is empty
- [x] Add a typed `ResourceLandingRef` (shortcode, plain resource id, optional `VersionDate`) whose `from` takes
    the raw strings and returns `Either[String, …]`, plus its conversion to `ResourceIri`
- [x] Add `PublicResourceViewService` in `slice/export/fair/`, which reads one resource through
    `getResourcesWithDeletedResource` with `KnoraSystemInstances.Users.AnonymousUser` (ignoring request
    credentials, never `skipRetrievalChecks`) and returns `NotPublic` for `ForbiddenException`,
    `NotFoundException`, a `DeletedResource`, and a version before creation; every other failure propagates
- [x] Classify the access level (Full Open / Restricted) from the anonymous `userPermission` on the resource and
    its file value, per the Access table
- [x] Extract `findDescriptionProperty` and `fileLinkOf` out of `ExportService` into a shared helper that both
    callers use, within `slice/export/`, with `ExportService` output unchanged
- [x] Wire the new services in `ExportApiModule.scala`
- [x] Unit tests for ref parsing: valid, malformed, bad shortcode, check-digit-bearing id, ARK-form version,
    `xsd:dateTimeStamp` version, bad version
- [x] Unit tests for every row of the Access table, on constructed resources (unit tests have no triplestore)
- [x] Unit tests: missing, invisible, deleted (including deleted after the requested version) and
    version-before-creation → `NotPublic`; a triplestore failure is not `NotPublic`
- [ ] (deferred to S3) The same cases against real incunabula data: `http://rdfh.ch/0803/0f4b2ce2a6d7` (resource
    and file `V` for UnknownUser) and `http://rdfh.ch/0803/00014b43f902` (resource and file `RV`), plus a version
    before creation as the live read handles it
- [x] Unit test: config validation rejects a trailing slash, a path, and a relative URL
- [x] Run `bazel test //modules/webapi:test`; it passes
- [x] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

## Outcome

S1 landed in commits `bba7f1473`..`88d092da9`. The review (consistency, simplicity, architecture, Scala/ZIO,
security) found one critical issue, fixed in `2cb0058f0`: with several file values the access level ignored them,
so restricted-view files on a viewable resource read as full open. Other fixes: URL validation for uppercase
schemes and underscore hosts, tighter tests, `ResourceFileLinks` moved into the `fair` package, the configuration
table and ARCH-MAP updated.

Deferred to S3: the access-table cases against real incunabula data (unit tests have no triplestore). Deferred to
S4: the dsp-app settings for the local docker-compose stack. S2 carries the rule that the builder reads only
allow-listed facts and file details only from `openFile`.

**Changed after review with the owner (2026-10-07):** `PublicResourceViewService` was removed again. Its
public/not-public outcome only repackaged the existing read's Forbidden/NotFound, and its file decision duplicated
`AssetAccess.from`, the existing single policy for what a caller may receive of an asset. S2's builder reads as
anonymous itself and asks `AssetPermissionsResponder.getAssetAccess(AnonymousUser)` before advertising a file; the
cases and tests the service had move to S2. The `ResourceFileLinks` extraction was reverted too (no caller yet,
and it bundled the description lookup, which is dropped); S2 extracts only the download link, next to its caller.
