---
title: "S3: Representation URLs"
type: feat
date: 2026-10-06
author: "Raitis Veinbahs"
status: reviewed
repositories: []
linear: DEV-7420
---

# S3: Representation URLs

Part of the [plan](01-feat-resource-fair-landing-metadata-plan.md); design in
[02](02-resource-fair-landing-metadata-design.md) (Technical Considerations, Signposting, Caching).

**Needs:** S2. **Visible change:** three new public API URLs, live once `app.dsp-app.url` is set (H3).

## Implementation Phases

### Phase 1: Representation URLs

- [ ] Add `head` to `RequiresMethod` (`slice/common/api/RequiresMethod.scala`)
- [ ] Add the three `GET` endpoints (`metadata.jsonld`, `metadata.datacite.json`, `metadata.ttl`) with
    `?version=`, their media types, `Link: <page>; rel="describes"` and `Cache-Control: no-cache`, on
    `V3BaseEndpoint.public` with an endpoint-local error output
- [ ] Add `HEAD` for each representation endpoint, with the same status and headers and an empty body
- [ ] Answer one identical `404` with `Cache-Control: no-store` for invisible, missing and deleted resources, and
    for any resource while `app.dsp-app.url` is empty
- [ ] Wire the endpoints in `*ServerEndpoints` and register them in `ApiV3ServerEndpoints` and `ApiV3Module`
- [ ] E2E: an open resource returns `200`, the correct media type, the `describes` link and `no-cache`
- [ ] E2E: a versioned request carries the versioned ARK, and a field changed between two versions shows that
    version's value
- [ ] E2E: invisible, missing and deleted resources all return an identical `404` body
- [ ] E2E: a request with a project admin's `Authorization` for a restricted resource returns output
    byte-identical to the anonymous one
- [ ] E2E: `HEAD` matches `GET` status and headers for each representation
- [ ] Document the endpoints and the field mapping in `docs/03-endpoints/api-v3/`, with an "Assessment results"
    table seeded with the S1 baselines
- [ ] Add the project data license to `docs/01-introduction/legal-info.md`, which today describes only file-value
    legal info
- [ ] Run `just test-e2e`; it passes
- [ ] Run `just check`; it passes
- [ ] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts
