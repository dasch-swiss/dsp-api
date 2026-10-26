---
title: "feat: dsp-app reaches ingest under /ingest/ on the API host"
type: feat
date: 2026-10-26
author: "Raitis Veinbahs"
status: draft
prd: 01-move-ingest-into-webapi-PRD.md
repositories:
  - name: dsp-app
    path: /Users/raitisveinbahs/work/dsp-app
---

# feat: dsp-app reaches ingest under /ingest/ on the API host

## Overview

dsp-api now serves every ingest endpoint under `/ingest/` (PRD core feature 1). This plan moves dsp-app onto those
routes. dsp-app keeps the `ingestUrl` config key, and its value becomes the API host plus `/ingest`, for example
`https://api.dev.dasch.swiss/ingest`. The runtime code that builds ingest URLs stays as it is. The work is in the
checked-in configs, the Cypress helpers and the CI job that starts the dsp-api stack. One small hardening change makes
a trailing slash in `ingestUrl` harmless.

## Problem Statement / Motivation

PRD success criterion: "dsp-app reaches ingest only through `/ingest/` on the API host" (Goal 4). Today dsp-app points
at `ingest.<domain>` in deployed environments and at `0.0.0.0:3340` locally and in CI. The PRD also drops the ingest
container from dsp-api's local stack, and dsp-app's e2e job starts that stack with
`docker compose up -d sipi ingest api`. Without this plan, that CI step fails as soon as dsp-api's `main` loses the
`ingest` service.

## Proposed Solution

**Keep `ingestUrl` as a full base URL and point it at `<api-base>/ingest`.** Every runtime ingest URL in dsp-app is
`${dspIngestConfig.url}/projects/...`, which gives `https://api.<domain>/ingest/projects/...` with the new value. That
path is the one dsp-api serves. Call sites on `origin/main`:

- `libs/vre/resource-editor/resource-editor/src/lib/representation/upload/upload-file.service.ts:22`: upload,
  `${url}/projects/${shortcode}/assets/ingest/${encodeURIComponent(file.name)}`
- `upload-file.service.ts:36`: asset info, `${url}/projects/${shortcode}/assets/${assetId}`
- `libs/vre/resource-editor/resource-editor/src/lib/representation/representation.service.ts:43`: download URL
  `${url}/projects/${projectShort}/assets/${assetId}` (plus `/original`)

**The auth interceptor keeps working.** `apps/dsp-app/src/app/main/http-interceptors/auth.interceptor.fn.ts` attaches
the bearer token when `req.url.startsWith(...)` matches any entry in `[apiUrl, ingestUrl, iiifUrl]`. With
`ingestUrl = <apiUrl>/ingest`, every ingest request already matches `apiUrl`, and the `ingestUrl` entry is redundant
but harmless. The token is still attached, and requests to other hosts still don't get it. No code change is needed.
Keeping the entry also means a deployment whose `ingestUrl` still names the old `ingest.<domain>` host (served by the
proxy) gets tokens too.

**Deployed environments need no dsp-app release.** ops-deploy's `config.json.j2` generates the deployed `config.json`.
When ops-deploy sets `ingestUrl` to `https://{{ DSP_API_HOST }}/ingest`, the running dsp-app switches without a code
change. The checked-in `config.*.json` files only drive local dev, the `start-dev`/`start-stage`/`start-ls-test`
scripts and CI.

**Config contract for ops-deploy** (`roles/dsp-deploy/templates/app/conf/config.json.j2`):

```json
"ingestUrl": "https://{{ DSP_API_HOST }}/ingest",
```

Write it as an absolute URL with scheme, the API host and the `/ingest` path, and no trailing slash. The trailing
slash is tolerated after deliverable 1 below, but should not be relied on. In the same template, `tracingCorsUrls`
can drop the `https://{{ DSP_INGEST_HOST }}/.*` entry, because the API host entry already covers `/ingest/`.

## Alternative Approaches Considered

- **Derive the ingest base from `apiUrl` in code and drop the `ingestUrl` key.** Rejected. `AppConfig` (zod) requires
  `ingestUrl`, and ops-deploy generates the config, so dropping the key would require a dsp-app release and an
  ops-deploy change to land together. Keeping the key decouples them, and lets an environment fall back to the proxy
  host with a config change alone.
- **Hard-code an `/ingest` prefix in the URL builders and keep `ingestUrl` as a bare origin.** Rejected. The config
  would carry `https://api.<domain>`, a value that no longer names ingest, and the prefix would be repeated across
  three call sites and two Cypress helpers.

## Technical Considerations

- **Trailing slash.** `ingestUrl` now has a path component, and ops-deploy writes it by hand, so a value like
  `.../ingest/` is a plausible mistake. It would produce `/ingest//projects/...`. `DspIngestConfig.url` strips trailing
  slashes, which keeps every builder correct.
- **CI stack.** `.github/workflows/ci.yml` (job `dsp-app-e2e-tests`, step "Start API") checks out dsp-api `main` and
  runs `docker compose -f docker-compose.yml up -d sipi ingest api`. The `ingest` service goes away, so the step
  becomes `up -d sipi api`. Naming a service that no longer exists makes `docker compose up` fail.
- **Cypress intercept.** `cypress/e2e/system-admin/file-representation.cy.ts:40` intercepts `**/assets/ingest/**`.
  That pattern also matches the old host, so it can't prove the success criterion. Tightening it to
  `**/ingest/projects/*/assets/ingest/**` makes the e2e test check that the browser upload goes through `/ingest/`.
- **CORS** for the app origin on `/ingest/` routes (methods, `Authorization`, `Content-Type: application/octet-stream`)
  is dsp-api's responsibility. dsp-app adds no header.
- **Out of scope:** the commented-out `ingest.dev.dasch.swiss/info` line in `k6/tests/simple-http-get.js`, and ADR
  0006, which records a past decision and is not rewritten.

## Implementation Approach

All changes are in dsp-app. Deliverables:

- [ ] `libs/vre/core/config/src/lib/app-config/dsp-ingest-config.ts`: `url` returns `_ingestUrl` with trailing `/`
  removed.
- [ ] `libs/vre/core/config/src/lib/app-config/app-config.service.spec.ts`: fixtures use
  `ingestUrl: 'http://0.0.0.0:3333/ingest'`, and the expectation at line 163 matches it.
- [ ] Add a spec case asserting that `ingestUrl: 'http://0.0.0.0:3333/ingest/'` yields
  `dspIngestConfig.url === 'http://0.0.0.0:3333/ingest'`.
- [ ] `apps/dsp-app/src/config/config.dev.json`: `"ingestUrl": "http://0.0.0.0:3333/ingest"`.
- [ ] `apps/dsp-app/src/config/config.prod.json`: `"ingestUrl": "http://0.0.0.0:3333/ingest"` (used by
  `serve:production` in CI).
- [ ] `apps/dsp-app/src/config/config.dev-server.json`: `"ingestUrl": "https://api.dev.dasch.swiss/ingest"`.
- [ ] `config.dev-server.json`: drop `https://ingest.dev.dasch.swiss` from `tracingCorsUrls`.
- [ ] `apps/dsp-app/src/config/config.stage-server.json`: `"ingestUrl": "https://api.stage.dasch.swiss/ingest"`.
- [ ] `apps/dsp-app/src/config/config.ls-test-server.json`: `"ingestUrl"` set to `https://<its apiHost>/ingest`,
  that is `https://api.ls-test-server.dasch.swiss/ingest`.
- [ ] `libs/vre/shared/app-common-to-move/src/lib/header/header-stories.helper.ts`: story fixture
  `ingestUrl: 'https://api.example.com/ingest'`.
- [ ] `apps/dsp-app/cypress.config.ts`: `env.dspIngestUrl` becomes `'http://0.0.0.0:3333/ingest'`.
- [ ] `apps/dsp-app/cypress/support/pages/still-image.page.ts:19`: replace the hard-coded
  `http://0.0.0.0:3340/...` with `` `${Cypress.env('dspIngestUrl')}/projects/0803/assets/ingest/screen.png` ``.
- [ ] `apps/dsp-app/cypress/e2e/system-admin/file-representation.cy.ts:40`: intercept
  `**/ingest/projects/*/assets/ingest/**`.
- [ ] `.github/workflows/ci.yml`, step "Start API": `docker compose -f docker-compose.yml up -d sipi api`.
- [ ] `CONTEXT.md`, **dsp-ingest** entry: say that ingest is served by dsp-api under `/ingest/` and that `ingestUrl`
  names that base URL.
- [ ] Run `git grep -nE "3340|ingest\.(dev|stage|ls-test|example)" -- ':!CHANGELOG.md' ':!k6'`; it returns nothing.
- [ ] Run `npm run lint-all`; it is clean.
- [ ] Run `npm run test-ci-all`; it passes.
- [ ] Run `npx nx build dsp-app`; it succeeds.
- [ ] Run `npx tsc --noEmit -p apps/dsp-app/cypress/tsconfig.json`; it passes.
- [ ] The PR's `DSP-APP E2E tests` CI check passes against dsp-api `main` serving `/ingest/`.

## Human Actions

| Id | Action | Who | When | Why not the agent |
| --- | --- | --- | --- | --- |
| H1 | Merge the dsp-api PR that serves `/ingest/` and drops the ingest container before merging this dsp-app PR, then merge this PR promptly | dsp-api / dsp-app maintainer | after ship | Merge order across repos is the owners' decision. dsp-app's e2e job runs against dsp-api `main`, so it is red on `/ingest/` until dsp-api lands, and red on `main` without this PR once dsp-api drops `ingest` |
| H2 | Release and deploy dsp-app as usual. No deploy ordering applies beyond the dsp-api release that serves `/ingest/` in each environment | release manager | after ship | Releases and deploys are human-approved |

## Acceptance Criteria

- [ ] With the new configs, every ingest request dsp-app makes goes to `<apiUrl>/ingest/...`: upload, asset info and
  download/original.
- [ ] The bearer token is attached to `/ingest/` requests and to no other host.
- [ ] A trailing slash in `ingestUrl` does not produce `//` in request paths.
- [ ] No checked-in config, Cypress helper or story fixture references port 3340 or an `ingest.<domain>` host.
- [ ] The e2e upload test asserts that the upload goes through `/ingest/` and returns 200.
- [ ] The CI e2e job starts dsp-api without an `ingest` service.

## Dependencies & Risks

- **Depends on dsp-api `main` serving `/ingest/`** (PRD core feature 1). This PR's e2e check cannot go green before
  that.
- **CI window.** If dsp-api drops the `ingest` compose service in the same merge that adds `/ingest/`, dsp-app `main`
  CI is red until this PR merges (H1). Mitigation: merge this PR right after. Alternatively, dsp-api can keep the
  compose service for one extra merge. That choice is dsp-api's.
- **Deployed config is ops-deploy's.** Production behaviour changes only when ops-deploy sets the `ingestUrl` value
  from the contract in Proposed Solution. Until then, dsp-app uses `ingest.<domain>`, which keeps working through the
  permanent proxy. The order of the ops-deploy change and the dsp-app release is therefore free, as long as the
  dsp-api release serving `/ingest/` is deployed first.
- **Open point for ops-deploy:** the `{{ STACK }}-api-from-app` Traefik router matches API requests that carry the
  app's `Referer`. Browser uploads to `/ingest/` now hit that router instead of the ingest router, so its request-body
  size limits, timeouts and rate limits must allow large uploads. This plan does not verify it. The ops-deploy plan
  has to.
- **Open point for dsp-api:** CORS on `/ingest/` must allow the app origin with `Authorization` and
  `Content-Type: application/octet-stream`. The old ingest service did this on its own. dsp-api's CORS must cover the
  new prefix.
- The `eng:angular-patterns` skill is not installed. The change touches no component code, so nothing depends on it.

## Success Metrics

- `git grep` (deliverable above) finds no old ingest URLs in dsp-app.
- The `DSP-APP E2E tests` check passes, including `file-representation.cy.ts` with the tightened `/ingest/` intercept.
- After deployment, the browser network log on dev shows uploads going to `https://api.dev.dasch.swiss/ingest/...`.

## References

- PRD: 01-move-ingest-into-webapi-PRD.md (Goal 4, repository impact for dsp-app and ops-deploy)
- `apps/dsp-app/src/app/main/http-interceptors/auth.interceptor.fn.ts:10`: token allow-list
- `libs/vre/core/config/src/lib/app-config/app-config.ts:98`: `ingestUrl: z.string()`
- `libs/vre/core/config/src/lib/app-config/dsp-ingest-config.ts`
- `libs/vre/resource-editor/resource-editor/src/lib/representation/upload/upload-file.service.ts:22`
- `libs/vre/resource-editor/resource-editor/src/lib/representation/representation.service.ts:43`
- `apps/dsp-app/cypress/support/helpers/file-uploader.ts:24`: already uses `Cypress.env('dspIngestUrl')`
- `.github/workflows/ci.yml`, job `dsp-app-e2e-tests`, step "Start API"
- ops-deploy `roles/dsp-deploy/templates/app/conf/config.json.j2:12`: the current `ingestUrl` (line 35:
  `tracingCorsUrls`)
