---
title: "S5: Signposting"
type: feat
date: 2026-10-06
author: "Raitis Veinbahs"
status: draft
repositories: []
linear: DEV-7420
---

# S5: Signposting

Part of the [plan](01-feat-resource-fair-landing-metadata-plan.md); design in
[02](02-resource-fair-landing-metadata-design.md) (Signposting, Access).

**Needs:** S3 (the `describedby` targets) and S4. Independent of S6 and S7. **Visible change:** `Link`
header and `<link>` mirror on the resource page.

## Tasks

- [ ] Add the link-set builder over `ResourceFairGraph` and its RFC 8288 serializer (ordering, `type`
    attributes, href delimiter escaping)
- [ ] Emit the `Link` header (cite-as, type, describedby ×3, license, author) on `GET` and `HEAD`
- [ ] Contribute the `<link>` mirror to the head, from the same list as the header
- [ ] Emit neither for `NotPublic` resources, and neither when graph building fails
- [ ] Unit test: header and `<link>` elements parse to the same set for every incunabula fixture resource
- [ ] Unit test: `cite-as` exactly once; `license` 0 or 1; `author` only for ORCID URIs
- [ ] E2E: `Link` header present and identical on `GET` and `HEAD`; absent for a restricted resource
- [ ] E2E: `?highlightValue=` (own, foreign, invisible and malformed value) leaves the response
    byte-identical to the one without it
- [ ] Run `just test-e2e`; it passes
- [ ] Slice review: adversarial review of this slice's commits; verified findings fixed before merge
