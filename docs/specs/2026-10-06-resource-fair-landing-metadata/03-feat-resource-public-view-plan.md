---
title: "S1: Resolution and the anonymous public view"
type: feat
date: 2026-10-06
author: "Raitis Veinbahs"
status: draft
repositories: []
linear: DEV-7420
---

# S1: Resolution and the anonymous public view

Part of the [plan](01-feat-resource-fair-landing-metadata-plan.md); design in
[02](02-resource-fair-landing-metadata-design.md) (Shape, Access).

**Needs:** nothing. **Visible change:** none.

## Tasks

- [ ] Run dsp-repository's `just fair-check` (F-UJI 3.5.0, needs Docker and outbound network) against the
    production ARK of one 0803 resource and one 0868 resource with files; record both baselines in the
    plan's Success Metrics
- [ ] Add `app.fair.dsp-app-base-url` to `AppConfig` and `application.conf` (env
    `KNORA_WEBAPI_FAIR_DSP_APP_BASE_URL`), local default `http://localhost:4200`
- [ ] Add a typed `ResourceLandingRef` (shortcode, plain resource id, optional `VersionDate`) whose `from`
    returns `Either[String, …]`, plus its conversion to `ResourceIri`
- [ ] Add `PublicResourceViewService`, which reads one resource with the anonymous user (ignoring request
    credentials) and returns `NotPublic` for missing, invisible or deleted resources and for a version before
    creation
- [ ] Classify the access level (Full Open / Restricted / Metadata only) from the anonymous `userPermission`
    on the resource and its file value
- [ ] Extract `findDescriptionProperty` and `fileLinkOf` out of `ExportService` into a shared helper that both
    callers use, with `ExportService` output unchanged
- [ ] Unit tests for ref parsing: valid, malformed, bad shortcode, check-digit-bearing id, bad version
- [ ] Unit tests for every row of the access table, and for missing, invisible, deleted and
    version-before-creation → `NotPublic`
- [ ] Run `bazel test //modules/webapi:test`; it passes
- [ ] Slice review: adversarial review of this slice's commits; verified findings fixed before merge
