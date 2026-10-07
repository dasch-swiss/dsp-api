---
title: "S7: Negotiation"
type: feat
date: 2026-10-06
author: "Raitis Veinbahs"
status: reviewed
repositories: []
linear: DEV-7420
---

# S7: Negotiation

Part of the [plan](01-feat-resource-fair-landing-metadata-plan.md); design in
[02](02-resource-fair-landing-metadata-design.md) (Decisions, Edge, Caching).

**Needs:** S3 (the `303` targets) and S4. Independent of S5 and S6. **Visible change:** `303` to a representation
when `Accept` prefers one, and `Vary: Accept` on every landing response.

## Implementation Phases

### Phase 1: Negotiation

- [ ] Add the `Accept` negotiation, mirroring `shared-fair` `negotiate::decide` (exact type match, `q` strictly
    greater than HTML wins, first-listed tie-break, 20-range / 2048-byte bounds, never a `4xx`), with JSON-LD,
    DataCite and Turtle as candidates and no aliases
- [ ] `303` to the matching representation URL, absolute from `externalKnoraApiBaseUrl`, carrying `?version=`
    through, with `Cache-Control: no-cache`, on `GET` and `HEAD`
- [ ] `Vary: Accept` on every landing response (`200` and `303`, `GET` and `HEAD`)
- [ ] No `303` for resources that are not public or while `app.dsp-app.url` is empty: the plain shell, as for a browser
- [ ] Unit tests: the negotiation decision table, ported case for case from `shared-fair`'s tests
- [ ] E2E: a `303` with the right absolute `Location` for each candidate, on `GET` and `HEAD`, and `Vary` on every
    response
- [ ] E2E: `Accept: */*`, `text/html`, `application/json` and a malformed header all get the page
- [ ] E2E: the body is identical for every `Accept` value that does not redirect
- [ ] Run `just test-e2e`; it passes
- [ ] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts
