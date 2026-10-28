---
title: "Move ingest into webapi"
date: 2026-10-20
author: "Raitis Veinbahs"
status: draft
repositories:
  - dsp-app
  - dsp-tools
  - ops-deploy
---

# Project brief: move ingest into webapi

## Background

The ingest does work that is very similar to the API work, although some parts of it are very independent.
It is to be included in the webapi module and its routes are to be prefixed under `/ingest/`.

## Why

- One service fewer to build, deploy, version and monitor
- Shared auth, observability and test setup instead of two copies
- Clients talk to one API base URL

## Tasks

- Move the ingest codebase into the webapi module
- Prefix the routes under `/ingest/`
- Ship Sipi, ffmpeg and ffprobe in the webapi image, which ingest needs for file conversion
- Remove the SQLite database (nothing reads it to decide behaviour)
- Migrate DaSCH's clients (dsp-app, dsp-tools) to the new endpoints
- Proxy the old `ingest.<domain>` URLs to `/ingest/` permanently and retire the ingest container
- Update ops-deploy: route `/ingest/` to the API
- Run ingest work on a dedicated ZIO executor sized as a share of the available CPUs, so large ingests don't starve
  the API

## Success criteria

- dsp-app uses only the `/ingest/` routes; the dsp-tools change is ready and merged once prod serves `/ingest/`
- The design keeps large-project ingests through `/ingest/` from taking over the API's CPU (checked in design review,
  not by load tests)
- Old `ingest.<domain>` URLs, including published download links, keep resolving through the proxy

## Out of scope

- Migrating clients outside DaSCH

## Risks

- Large ingests (CPU, memory, disk I/O) running in the API process can slow down or destabilise regular API traffic.
  Mitigation: the dedicated ingest ZIO executor (see Tasks) bounds CPU; memory and disk I/O stay shared.
