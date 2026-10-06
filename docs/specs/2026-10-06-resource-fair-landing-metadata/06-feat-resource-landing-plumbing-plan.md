---
title: "S4: Landing plumbing"
type: feat
date: 2026-10-06
author: "Raitis Veinbahs"
status: draft
repositories:
  - dsp-app
  - ops-deploy
linear: DEV-7420
---

# S4: Landing plumbing

Part of the [plan](01-feat-resource-fair-landing-metadata-plan.md); design in
[02](02-resource-fair-landing-metadata-design.md) (Technical Considerations).

**Needs:** H1 (edge, landing path, shell source). Nothing from S1–S3, so it can run in parallel with them.
**Visible change:** none: the shell, now served through dsp-api.

Ship alone, then wire the edge (H4) and watch latency and the fallback in production before S5–S7 depend
on it.

## Tasks

- [ ] Add the landing endpoint (`GET`/`HEAD`) at the path H1 fixes, returning the dsp-app shell from the
    source H1 fixes, cached, byte-identical to what nginx serves today
- [ ] Add the head splice point before `</head>`, with no contributors yet
- [ ] Accept and ignore `?version=` and `?highlightValue=`; never read the resource in this slice
- [ ] Fall back to the plain shell when the shell source or any later contributor fails; never a `5xx` for
    a page a browser asked for
- [ ] Add the dsp-app side of the edge (or the Traefik router) with an upstream-error fallback to the static
    `index.html`
- [ ] E2E: the served HTML equals the shell for open, restricted and unknown resources and for any `Accept`
- [ ] E2E: `HEAD` matches `GET`
- [ ] Run `just test-e2e`; it passes
- [ ] Slice review: adversarial review of this slice's commits; verified findings fixed before merge
