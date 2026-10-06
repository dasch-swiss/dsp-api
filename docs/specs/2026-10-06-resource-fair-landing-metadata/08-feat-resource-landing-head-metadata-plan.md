---
title: "S6: Head content"
type: feat
date: 2026-10-06
author: "Raitis Veinbahs"
status: draft
repositories: []
linear: DEV-7420
---

# S6: Head content

Part of the [plan](01-feat-resource-fair-landing-metadata-plan.md); design in
[02](02-resource-fair-landing-metadata-design.md) (schema.org shape, Access).

**Needs:** S2 (the graph and the JSON-LD writer) and S4. Independent of S3, S5 and S7. **Visible change:**
JSON-LD block and Dublin Core meta in the served head.

## Tasks

- [ ] Add the Dublin Core `<meta>` writer, including `DC.accessRights` COAR URIs
- [ ] Contribute one `<script type="application/ld+json">` (the S2 JSON-LD, with unicode-escaped `<>&`) and
    the DC meta to the head
- [ ] Contribute nothing for `NotPublic` resources, and nothing when graph building fails
- [ ] Agreement test: the embedded block equals the S2 JSON-LD for the same resource and version; DC meta
    agrees with it on ARK, title, license and creators
- [ ] Golden test for the head of one open, one restricted-view and one versioned incunabula resource
- [ ] E2E: a restricted resource's head leaks nothing beyond the public view
- [ ] Run `just test-e2e`; it passes
- [ ] Slice review: adversarial review of this slice's commits; verified findings fixed before merge
