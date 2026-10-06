---
title: "S7: Negotiation"
type: feat
date: 2026-10-06
author: "Raitis Veinbahs"
status: draft
repositories: []
linear: DEV-7420
---

# S7: Negotiation

Part of the [plan](01-feat-resource-fair-landing-metadata-plan.md); design in
[02](02-resource-fair-landing-metadata-design.md) (Decisions, Technical Considerations).

**Needs:** S3 (the `303` targets) and S4. Independent of S5 and S6. **Visible change:** `303` to a
representation when `Accept` prefers one, and `Vary: Accept` on every landing response.

## Tasks

- [ ] Add the `Accept` negotiation, mirroring `shared-fair` `negotiate::decide` (exact type match, `q`
    strictly greater than HTML wins, first-listed tie-break, 20-range / 2048-byte bounds, never a `4xx`),
    with JSON-LD, DataCite and Turtle as candidates
- [ ] `303` to the matching representation URL, carrying `?version=` through
- [ ] `Vary: Accept` on every landing response (`200` and `303`, `GET` and `HEAD`)
- [ ] No `303` for `NotPublic` resources: the plain shell, as for a browser
- [ ] Unit tests: the negotiation decision table, ported case for case from `shared-fair`'s tests
- [ ] E2E: a `303` with the right `Location` for each candidate, and `Vary` on every response
- [ ] E2E: the HTML is byte-identical for every `Accept` value that does not redirect
- [ ] Run `just test-e2e`; it passes
- [ ] Slice review: adversarial review of this slice's commits; verified findings fixed before merge
