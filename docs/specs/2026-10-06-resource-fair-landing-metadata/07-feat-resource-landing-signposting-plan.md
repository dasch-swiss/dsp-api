---
title: "S5: Signposting"
type: feat
date: 2026-10-06
author: "Raitis Veinbahs"
status: reviewed
repositories: []
linear: DEV-7420
---

# S5: Signposting

Part of the [plan](01-feat-resource-fair-landing-metadata-plan.md); design in
[02](02-resource-fair-landing-metadata-design.md) (Signposting, Access, Edge).

**Needs:** S3 (the `describedby` targets) and S4. Independent of S6 and S7. **Visible change:** `Link` header and
`<link>` mirror on the resource page.

## Implementation Phases

### Phase 1: Signposting

- [ ] Add the link-set builder over `ResourceFairGraph` and its RFC 8288 serializer (ordering, `type` attributes,
    href delimiter escaping); `describedby` hrefs are absolute from `externalKnoraApiBaseUrl` and carry `?version=`
- [ ] Read the resource in the landing endpoint (S1 view, S2 graph), bounded at 2 s; on timeout or graph failure
    serve the plain shell
- [ ] Emit the `Link` header (cite-as, type, describedby ×3, license, author) on `GET` and `HEAD`
- [ ] Contribute the `<link>` mirror to the head, from the same list as the header
- [ ] Emit neither for `NotPublic` resources, when graph building fails, or while `app.dsp-app.url` is empty
- [ ] Unit test: header and `<link>` elements parse to the same set for every incunabula fixture resource
- [ ] Unit test: `cite-as` exactly once; `license` 0 or 1 and only the project's; `author` only for ORCID URIs
- [ ] E2E: `Link` header present and identical on `GET` and `HEAD` for an open and an `RV` resource; absent for a
    resource anonymous cannot see
- [ ] E2E: with `?version=`, `cite-as` is the versioned ARK and every `describedby` href carries the version
- [ ] E2E: with `Authorization` and `Cookie` set, the response equals the anonymous one
- [ ] E2E: `?highlightValue=` (own, foreign, invisible and malformed value) leaves the body and `Link` header
    identical to the response without it
- [ ] E2E: a graph-building failure (fault-injected) still answers `200` with the plain shell
- [ ] If S6 has landed: a golden test of the combined head
- [ ] Run `just test-e2e`; it passes
- [ ] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts
