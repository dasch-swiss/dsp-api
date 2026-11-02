---
title: "Ingest runs inside dsp-api under /ingest/"
date: 2026-10-20
author: "Raitis Veinbahs"
status: reviewed
brief: 00-project-brief.md
repositories:
  - dsp-app
  - dsp-tools
  - ops-deploy
---

# Ingest runs inside dsp-api under /ingest/

## Revisions

- 2026-10-27, owner decision: minimal diff. Ingest's code stays in `modules/ingest` with its structure and package,
  as a library dsp-api builds in and serves under `/ingest/`; it is not moved into a webapi slice and its API layer is
  not rebuilt. The context, core feature 1 and the dsp-api repository impact are reworded accordingly; the
  requirements are unchanged.

## Context

dsp-ingest runs as its own service, with its own container, configuration and database. Its work is close to the
API's: it authenticates with tokens the API already issues, and it writes the asset storage the API already reads.
Running it separately means two services to build, deploy and monitor, a second configuration, and a second address
for clients.

The move builds ingest's existing code into dsp-api as a library, served under `/ingest/`. The `ingest.<domain>`
host stays permanently as a proxy to those routes. The ingest container and its SQLite database go away.

## Goals

1. Ingest runs inside dsp-api as one service: one container, one authentication.
2. All existing ingest URLs keep working permanently, including the download links already published in FAIR
   metadata and exports.
3. Large ingests can't take over the API's CPU.
4. dsp-app uses only the `/ingest/` routes. dsp-tools has a ready, unmerged change that does the same, to merge once
   prod serves `/ingest/`.
5. The ingest SQLite database is removed.

## Core features

1. **Ingest served by dsp-api.** Every ingest endpoint (projects, assets, bulk ingest, export, import, erase, reports,
   maintenance, info, health, metrics) is served by dsp-api under `/ingest/`, with the same requests and responses
   as today, except for two deliberate changes: the `ImportProjectsToDb` maintenance action is removed, and `/health`
   no longer reports a database. The endpoints keep ingest's own token check, configured with dsp-api's JWT
   settings; the tokens and scopes they accept are unchanged.
2. **Separate configuration.** Ingest keeps its own configuration inside dsp-api, separate from dsp-api's; no
   setting is shared or merged. Ingest settings that no longer apply (its own server address, database, JWT and API
   URL) are dropped.
3. **In-process calls.** dsp-api's own calls to ingest (asset info, project export, import and erase) no longer go
   over HTTP.
4. **Contained ingest workload.** Ingest work runs on a dedicated executor sized as a share of the available CPUs.
   The dsp-api image ships the Sipi CLI, ffmpeg and ffprobe. The container's memory limit grows by ingest's current
   prod limit, with the JVM settings unchanged, so conversions have headroom without touching the API's heap.
5. **Permanent compatibility proxy.** `ingest.<domain>/<path>` is proxied to dsp-api's `/ingest/<path>`, so old
   clients and already published links keep working.
6. **New public links.** FAIR metadata and exports emit original-file download links on
   `api.<domain>/ingest/...`.
7. **No SQLite.** The ingest database is removed together with its health check, the `ImportProjectsToDb`
   maintenance action and its configuration.

## Repository impact

- **dsp-api:** ingest's code built into dsp-api as a library and served under `/ingest/`, ingest's configuration
  kept separate inside dsp-api, Sipi/ffmpeg in the dsp-api image, and removal of the standalone ingest application,
  its image and its publishing, and of the ingest container from the local stack.
- **dsp-app:** the upload paths (including the Cypress helpers) and the `ingestUrl` configuration point at
  `/ingest/` on the API host.
- **dsp-tools:** the ingest client paths and the ingest URL derivation point at `/ingest/` on the API host;
  `start-stack` drops the ingest container and its database setting. Opened as a PR, merged only once prod serves
  `/ingest/`.
- **ops-deploy:** the ingest service and its database volume are removed; the `ingest.<domain>` route and the local
  metrics route go to dsp-api with the `/ingest` prefix added; dsp-api gets ingest's remaining settings, mounts the
  image and temp directories read-write, gets the larger memory limit and drops the ingest base URL setting; dsp-app's
  `ingestUrl` points at the API host.

## User stories

### US-1: Ingest under one address

As a DaSCH client (dsp-app or dsp-tools), I want to reach every ingest endpoint under `/ingest/` on the API host, so
that I talk to one address.

- REQ-1.1 (Ubiquitous): dsp-api shall serve every ingest endpoint under the `/ingest/` path prefix.
- REQ-1.2 (Ubiquitous): For each ingest endpoint, dsp-api shall accept the same requests and return the same
  responses as the standalone ingest service, except that the `ImportProjectsToDb` maintenance action is removed and
  `/health` reports no database.
- REQ-1.3 (Event-driven): When a request to an `/ingest/` endpoint carries a token the standalone ingest service
  accepts, dsp-api shall accept that token with the same scopes.
- REQ-1.4 (Unwanted-behaviour): If a request to a secured `/ingest/` endpoint has no valid token, then dsp-api shall
  reject it with HTTP 401.
- REQ-1.5 (Event-driven): When a client uploads a file larger than a small buffer (ballpark 1 MiB), dsp-api shall
  stream the body to storage without holding it fully in memory.

### US-2: Old ingest URLs keep working

As an existing client, or someone following a published download link, I want the old `ingest.<domain>` URLs to keep
working, so that nothing already out there breaks.

- REQ-2.1 (Event-driven): When a request arrives at `ingest.<domain>/<path>`, the proxy shall forward it to dsp-api's
  `/ingest/<path>`, with method, query, body and `Authorization` header unchanged (the proxy may add the usual
  `X-Forwarded-*` headers).
- REQ-2.2 (Ubiquitous): The proxy shall return dsp-api's response to the caller unchanged.
- REQ-2.3 (Event-driven): When dsp-api generates an original-file download link in FAIR metadata or an export, dsp-api
  shall build it on the API's public URL under `/ingest/`.
- REQ-2.4 (State-driven): While dsp-api is healthy, `ingest.<domain>/health` shall answer with HTTP 200, so released
  dsp-tools versions that check it keep working.

### US-3: The API stays responsive during large ingests

As a researcher using dsp-api, I want the API to stay responsive while a large ingest runs, so that someone else's
upload doesn't slow down my work.

- REQ-3.1 (Ubiquitous): dsp-api shall run ingest work on a dedicated executor sized as a share of the available CPUs.
- REQ-3.2 (State-driven): While a bulk ingest, export or import is running, dsp-api shall serve non-ingest requests
  outside the ingest executor.
- REQ-3.3 (Unwanted-behaviour): If a Sipi, ffmpeg or ffprobe process fails, then dsp-api shall fail only the ingest
  request or job that started it.
- REQ-3.4 (Unwanted-behaviour): If the container runs out of memory during a conversion, then the conversion process
  shall be terminated rather than the dsp-api JVM.

These requirements are verified in design review, not by load tests. REQ-3.4 matters because the Linux
out-of-memory killer picks the largest process in the container by default, which is the JVM.

### US-4: One service to operate

As a DaSCH operator, I want to deploy and configure one service instead of two, so that there's less to build, deploy
and monitor.

- REQ-4.1 (Ubiquitous): dsp-api shall read ingest's settings from a configuration of their own, sharing no setting
  with dsp-api's configuration.
- REQ-4.2 (Ubiquitous): dsp-api shall start without any ingest database or database setting.
- REQ-4.3 (Event-driven): When dsp-api needs asset info, or a project export, import or erase, dsp-api shall call the
  ingest slice in-process rather than over HTTP.
- REQ-4.4 (Ubiquitous): The ops-deploy deployment shall run no separate ingest container.
- REQ-4.5 (Ubiquitous): The dsp-api release shall not build or publish a standalone dsp-ingest image.

## Constraints

- The `ingest.<domain>` host and its proxy to `/ingest/` stay permanently.
- Ingest endpoints keep their requests and responses; only the path prefix changes.
- The dsp-tools change is merged only once prod serves `/ingest/`.
- dsp-api's container memory limit grows by ingest's current prod limit; the JVM memory settings stay unchanged.

## Success criteria

- dsp-app reaches ingest only through `/ingest/` on the API host.
- A dsp-tools PR is open that moves every ingest call to `/ingest/` and drops the ingest container from
  `start-stack`.
- Prod runs no ingest container, and existing `ingest.<domain>` URLs, including published download links, still
  resolve.
- A design review confirms that large ingests can't take over the API's CPU or crash it (REQ-3.1 to REQ-3.4).
- dsp-api runs without the ingest SQLite database.

## Out of scope

- Migrating clients outside DaSCH.
- Retiring the `ingest.<domain>` host.
- Merging ingest's configuration into dsp-api's.
- Load tests for the separation of ingest and API traffic.

## Open questions

- The client research read local checkouts that may be stale (one dsp-tools file found locally is not on its
  `main`). The plan re-locates the ingest call sites in dsp-app and dsp-tools on their current `main`.
