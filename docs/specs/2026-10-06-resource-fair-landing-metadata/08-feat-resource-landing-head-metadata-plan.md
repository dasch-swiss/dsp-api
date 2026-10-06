---
title: "S6: Head content"
type: feat
date: 2026-10-06
author: "Raitis Veinbahs"
status: reviewed
repositories: []
linear: DEV-7420
---

# S6: Head content

Part of the [plan](01-feat-resource-fair-landing-metadata-plan.md); design in
[02](02-resource-fair-landing-metadata-design.md) (schema.org shape, Access, Technical Considerations).

**Needs:** S2 (the graph and the JSON-LD writer) and S4. Independent of S3, S5 and S7. **Visible change:**
JSON-LD block and Dublin Core meta in the served head.

## Implementation Phases

### Phase 1: Head content

- [ ] Add the Dublin Core `<meta>` writer, including `DC.accessRights` COAR URIs and `DC.rights` from the project's
    data license
- [ ] Read the resource in the landing endpoint (S1 view, S2 graph), bounded at 2 s, if S5 has not already added
    it; on timeout or graph failure serve the plain shell
- [ ] Contribute one `<script type="application/ld+json">` (the S2 JSON-LD, with unicode-escaped `<>&`) and the DC
    meta to the head
- [ ] Contribute nothing for `NotPublic` resources, when graph building fails, or while `app.dsp-app.url` is empty
- [ ] Agreement test: the embedded block equals the S2 JSON-LD for the same resource and version; DC meta agrees
    with it on ARK, title, license and creators
- [ ] Shape test: the embedded block's `identifier` and `license` still match DPE's shapes after unicode-escaping
- [ ] Golden test for the head of one open, one restricted-view and one versioned incunabula resource
- [ ] E2E: the head of an `RV` resource contains no file URL, file name or asset id; a resource anonymous cannot
    see gets the shell unchanged
- [ ] If S5 has landed: a golden test of the combined head
- [ ] Run `just test-e2e`; it passes
- [ ] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts
