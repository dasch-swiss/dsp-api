---
title: "S3: Representation URLs"
type: feat
date: 2026-10-06
author: "Raitis Veinbahs"
status: draft
repositories: []
linear: DEV-7420
---

# S3: Representation URLs

Part of the [plan](01-feat-resource-fair-landing-metadata-plan.md); design in
[02](02-resource-fair-landing-metadata-design.md) (Technical Considerations, Signposting).

**Needs:** S2, and H3 before it deploys (the `describes` link carries the page URL). **Visible change:**
three new public API URLs.

## Tasks

- [ ] Add the three `GET` endpoints (`metadata.jsonld`, `metadata.datacite.json`, `metadata.ttl`) with
    `?version=`, their media types and `Link: <page>; rel="describes"`, on the v3 public base without a shared
    error variant
- [ ] Add `HEAD` for each representation endpoint, with the same status and headers and an empty body
- [ ] Wire the endpoints in `*ServerEndpoints` and register them in `ApiV3ServerEndpoints`
- [ ] E2E: an open resource returns `200`, the correct media type and the `describes` link
- [ ] E2E: a versioned request carries the versioned ARK, and a field changed between two versions shows
    that version's value
- [ ] E2E: invisible, missing and deleted resources all return an identical `404` body
- [ ] E2E: a logged-in project admin's request for a restricted resource returns output byte-identical to
    the anonymous one
- [ ] E2E: `HEAD` matches `GET` status and headers for each representation
- [ ] Document the endpoints and the field mapping in `docs/03-endpoints/`, with an "Assessment results"
    table seeded with the S1 baselines
- [ ] Run `just test-e2e`; it passes
- [ ] Run `just check`; it passes
- [ ] Slice review: adversarial review of this slice's commits; verified findings fixed before merge
