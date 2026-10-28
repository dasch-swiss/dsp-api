---
plan: /Users/raitisveinbahs/work/dsp-api/docs/specs/2026-10-20-move-ingest-into-webapi/03-feat-ingest-urls-dsp-app-plan.md
target_repo: /Users/raitisveinbahs/work/dsp-app
base_commit: d09e70dfb
branch: feat/ingest-under-api-path
started: 2026-10-26
problem: >
  dsp-api now serves every ingest endpoint under /ingest/ on the API host and drops the standalone ingest
  container from its local stack. dsp-app still points ingestUrl at ingest.<domain> (deployed) and
  0.0.0.0:3340 (local/CI), and its e2e CI job starts dsp-api with `up -d sipi ingest api`, which fails once
  the ingest service is gone. dsp-app must reach ingest only through `<api host>`/ingest.
status: in-progress
---

# Execution Journal: 03-feat-ingest-urls-dsp-app-plan

## Chunk queue

| id | files | depends_on | checkboxes | acceptance | context | replaces |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | libs/vre/core/config/src/lib/app-config/dsp-ingest-config.ts, app-config.service.spec.ts | — | "dsp-ingest-config.ts strips trailing /", "spec fixtures use :3333/ingest", "spec case trailing slash" | trailing slash in ingestUrl is stripped; spec green and fails without the strip | dsp-ingest-config.ts § url getter; spec:19,102,163 | — |
| 2 | apps/dsp-app/src/config/config.{dev,prod,dev-server,stage-server,ls-test-server}.json, libs/vre/shared/app-common-to-move/src/lib/header/header-stories.helper.ts | — | "config.dev.json", "config.prod.json", "config.dev-server.json ingestUrl", "dev-server tracingCorsUrls", "config.stage-server.json", "config.ls-test-server.json", "header-stories.helper.ts" | every checked-in config and story fixture names `<api host>`/ingest | config.*.json:11, dev-server:35; header-stories.helper.ts:15 | — |
| 3 | apps/dsp-app/cypress.config.ts, cypress/support/pages/still-image.page.ts, cypress/e2e/system-admin/file-representation.cy.ts | — | "cypress.config.ts dspIngestUrl", "still-image.page.ts", "file-representation.cy.ts intercept" | Cypress helpers use dspIngestUrl = :3333/ingest; upload intercept requires /ingest/ | cypress.config.ts:20; still-image.page.ts:19; file-representation.cy.ts:40 | — |
| 4 | .github/workflows/ci.yml | — | "ci.yml Start API: up -d sipi api" | e2e job starts dsp-api without ingest service | ci.yml:251 § dsp-app-e2e-tests / Start API | — |
| 5 | CONTEXT.md | — | "CONTEXT.md dsp-ingest entry" | entry says ingest is served by dsp-api under /ingest/ and ingestUrl names that base URL | CONTEXT.md:91-93 § dsp-ingest | — |

Verification checkboxes (git grep, lint-all, test-ci-all, nx build, cypress tsc) are ticked after all chunks land;
the "DSP-APP E2E tests CI check" checkbox runs only after ship and is left for the session.

## Chunks

| id | status | commit(s) | summary | blocker |
| --- | --- | --- | --- | --- |
| 1 | complete | f77e1fa03 | DspIngestConfig.url strips trailing slashes; spec fixtures on :3333/ingest plus a trailing-slash case (fails without the strip) | none |
| 2 | complete | 54e94f134 | config.{dev,prod,dev-server,stage-server,ls-test-server}.json and header story fixture name `<api host>`/ingest; dev-server tracingCorsUrls drops the ingest host | none |
| 3 | complete | 3da01f783 | Cypress dspIngestUrl = :3333/ingest; still-image helper uses it; upload intercept tightened to **/ingest/projects/*/assets/ingest/** (minimatch checked: old :3340 URL no longer matches) | none |
| 4 | complete | caaf19b55 | ci.yml Start API: `up -d sipi api` | none |
| 5 | complete | da96504af | CONTEXT.md dsp-ingest entry: served by dsp-api under /ingest/, ingestUrl names that base URL | none |

## Deferrals

- none

## Deviations

- none

## Side findings

- Verification at branch tip da96504af: git grep for 3340/ingest.`<domain>` (excluding CHANGELOG.md, k6) empty; lint-all,
  test-ci-all, `nx build dsp-app` and the Cypress tsc all green.
- The Cypress `authToken` in apps/dsp-app/cypress.config.ts is a fixed JWT whose `aud` includes
  `http://localhost:3340` (base64, so the grep does not see it). If dsp-api's /ingest/ routes check the audience the
  way standalone ingest did, the e2e upload helpers would get 401; worth confirming on the first E2E CI run.
- The CI commit uses type `ci:`; CONVENTIONS.md's type list omits it (history uses `chore(ci)`/`fix(ci)`). Harmless
  under squash merge: the PR title is what lands.
