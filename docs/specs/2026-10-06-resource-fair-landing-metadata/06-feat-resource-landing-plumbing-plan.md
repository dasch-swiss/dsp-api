---
title: "S4: Landing plumbing"
type: feat
date: 2026-10-06
author: "Raitis Veinbahs"
status: reviewed
repositories:
  - dsp-app
  - ops-deploy
linear: DEV-7420
---

# S4: Landing plumbing

Part of the [plan](01-feat-resource-fair-landing-metadata-plan.md); design in
[02](02-resource-fair-landing-metadata-design.md) (Decisions, Edge, Caching).

**Needs:** nothing from S1–S3, so it can run in parallel with them. **Visible change:** none: the shell, now
served through dsp-api.

Ship the dsp-api part, then the dsp-app part, then wire it per environment (H4), and watch latency and the
fallback in production before S5–S7 depend on it. Three PRs, one per repository.

## Implementation Phases

### dsp-api

- [ ] Add a `ShellSource` trait with a live implementation (sttp via `TracingHttpClient`, `GET
    {app.dsp-app.internal-url}/index.html` with `Accept-Encoding: identity`) and an in-memory one for tests
- [ ] Add `ShellCache`: a `Ref`-held `(fetchedAt, html)` with a single-flight refresh after
    `app.dsp-app.shell-cache-ttl`, keeping the last good copy on fetch failure for at most 1 hour
- [ ] Add the landing endpoint `GET`/`HEAD` `/v3/resources/{shortcode}/{resourceId}/landing` with raw `String`
    path and query inputs, so no input ever decodes to a `4xx`
- [ ] Exempt the landing route from `PassthroughAwareNotAcceptableInterceptor` (`core/DspApiServer.scala`), so any
    `Accept` reaches the server logic
- [ ] Return the cached shell, unchanged, as `text/html; charset=utf-8` with `Cache-Control: no-cache`; `502` when
    there is no shell younger than 1 hour
- [ ] Add the head splice point before the first `</head>` (case-insensitive), with no contributors yet; no
    `</head>` serves the shell unspliced and logs
- [ ] Ignore `?version=` and `?highlightValue=` and never read the resource in this slice
- [ ] Add spans and a fallback counter per `docs/observability/instrumentation-recipe.md`
- [ ] Unit tests (`TestClock`): TTL refresh, single-flight under concurrent requests, last good copy on failure,
    `502` after 1 hour without a successful fetch, `502` with no copy
- [ ] Unit test (WireMock, as in `DspIngestClientLiveSpec`): the live source sends `Accept-Encoding: identity`
- [ ] E2E (in-memory shell source): the served HTML equals the shell for valid, malformed, unknown and
    check-digit-bearing ids, for any `Accept`, and with `Authorization` and `Cookie` set
- [ ] E2E: `HEAD` matches `GET`
- [ ] Run `just test-e2e`; it passes

### dsp-app

- [ ] In `nginx/default.conf.template`, add `location ~ ^/resource/([0-9A-Fa-f]{4})/([A-Za-z0-9_-]+)/?$` as the
    first regex location, with `resolver 127.0.0.11 valid=10s`, `set $dsp_api_upstream "${DSP_API_UPSTREAM}"` and
    `proxy_pass $dsp_api_upstream/v3/resources/$1/$2/landing$is_args$args`
- [ ] Add `ENV DSP_API_UPSTREAM=http://api:3333` to the Dockerfile, next to `NGINX_PORT`
- [ ] Clear `Authorization` and `Cookie` towards dsp-api; pass `Accept` through
- [ ] Include `/etc/nginx/security-headers.conf` in the new location
- [ ] Fall back with `proxy_intercept_errors on`, `error_page 500 502 503 504 = /index.html` and
    `proxy_read_timeout 3s`
- [ ] Verify with Docker: build the image, run it with `DSP_API_UPSTREAM` pointing at a stub that returns a marked
    page, and `curl` that `/resource/0803/abc` returns the marked page with the security headers, that `HEAD`
    works, that a stub `500` and a stopped stub both return the static shell, and that the container starts with
    the stub not running

### ops-deploy

- [ ] (from S1) Set `KNORA_WEBAPI_DSP_APP_URL` and `KNORA_WEBAPI_DSP_APP_INTERNAL_URL` for the local stack in
    dsp-api's `docker-compose.yml`, so FAIR metadata is on locally
- [ ] Set `DSP_API_UPSTREAM=http://api:3333` on the `app` service and `KNORA_WEBAPI_DSP_APP_INTERNAL_URL=http://app`
    on the `api` service in `roles/dsp-deploy/templates/docker-compose-svc.yml.j2`
- [ ] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts
