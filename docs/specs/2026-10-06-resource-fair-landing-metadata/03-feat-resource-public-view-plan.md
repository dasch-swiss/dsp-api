---
title: "S1: Resolution and the anonymous public view"
type: feat
date: 2026-10-06
author: "Raitis Veinbahs"
status: reviewed
repositories: []
linear: DEV-7420
---

# S1: Resolution and the anonymous public view

Part of the [plan](01-feat-resource-fair-landing-metadata-plan.md); design in
[02](02-resource-fair-landing-metadata-design.md) (Shape, Access, Technical Considerations).

**Needs:** nothing. **Visible change:** none.

## Implementation Phases

### Phase 1: Resolution and the anonymous public view

- [ ] Run dsp-repository's `just fair-check` (F-UJI 3.5.0, needs Docker and outbound network) against the
    production ARK of one 0803 resource and one 0868 resource with files; record both baselines in the plan's
    Success Metrics
- [ ] Add `DspAppConfig(url, internalUrl, shellCacheTtl)` under `app.dsp-app` in `AppConfig` and
    `application.conf` (envs `KNORA_WEBAPI_DSP_APP_URL`, `KNORA_WEBAPI_DSP_APP_INTERNAL_URL`), both URLs empty by
    default, with `.validate` guards: absolute `http(s)` URL or empty, no path, no trailing slash, TTL > 0
- [ ] Log a warning at startup when `app.dsp-app.url` is empty
- [ ] Add a typed `ResourceLandingRef` (shortcode, plain resource id, optional `VersionDate`) whose `from` takes
    the raw strings and returns `Either[String, …]`, plus its conversion to `ResourceIri`
- [ ] Add `PublicResourceViewService` in `slice/export/fair/`, which reads one resource through
    `getResourcesWithDeletedResource` with `KnoraSystemInstances.Users.AnonymousUser` (ignoring request
    credentials, never `skipRetrievalChecks`) and returns `NotPublic` for `ForbiddenException`,
    `NotFoundException`, a `DeletedResource`, and a version before creation; every other failure propagates
- [ ] Classify the access level (Full Open / Restricted) from the anonymous `userPermission` on the resource and
    its file value, per the Access table
- [ ] Extract `findDescriptionProperty` and `fileLinkOf` out of `ExportService` into a shared helper that both
    callers use, within `slice/export/`, with `ExportService` output unchanged
- [ ] Wire the new services in `ExportApiModule.scala`
- [ ] Unit tests for ref parsing: valid, malformed, bad shortcode, check-digit-bearing id, ARK-form version,
    `xsd:dateTimeStamp` version, bad version
- [ ] Unit tests for every row of the Access table, using incunabula: `http://rdfh.ch/0803/0f4b2ce2a6d7`
    (resource and file `V` for UnknownUser) and `http://rdfh.ch/0803/00014b43f902` (resource and file `RV`)
- [ ] Unit tests: missing, invisible, deleted and version-before-creation → `NotPublic`; a triplestore failure is
    not `NotPublic`
- [ ] Unit test: config validation rejects a trailing slash, a path, and a relative URL
- [ ] Run `bazel test //modules/webapi:test`; it passes
- [ ] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts
