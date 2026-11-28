---
title: "A per-second temp folder is shared by concurrent uploads, and ZIO TestRandom repeats IDs"
date: 2026-11-28
category: logic-errors
component: ingest
module: modules/ingest/src/main/scala/swiss/dasch/domain/StorageService.scala
problem_type: logic-error
severity: high
symptoms:
  - "Parallel bulk imports into one project lose a share of their files (81 of 1,221 with 4 workers)"
  - "An upload fails because its temp file was deleted by another upload"
root_cause: "The upload temp folder was `<temp>/<yyyyMMdd_HHmmss>/<shortcode>-ingest`, so two uploads to one project in the same second shared it, and the first scope release deleted the second upload's file."
tags: [ingest, temp-directory, concurrency, zio, test-random, uuid]
issue: "DEV-8100"
---

# A per-second temp folder is shared by concurrent uploads, and ZIO TestRandom repeats IDs

## Problem

Ingest gives each upload a scoped temp folder. Its name came from a timestamp with second resolution and the
project shortcode. Two uploads to the same project in the same second got the same folder; the first to finish
deleted it while the second still read from it.

## Root Cause

A timestamp is not an identity. Under parallel clients the same second is common.

## Solution

`createTempDirectoryScoped` creates `<temp>/<timestamp>/<uuid>/<name>`, with the UUID from `UUID.randomUUID()`, and
the scope release deletes the `<uuid>` folder. No lock is needed, so uploads stay parallel.

ZIO `Random` was rejected for the ID: in tests, `TestRandom` has a fixed seed, so parallel tests get the same "random"
folder and collide the same way.

## Prevention

- Name per-request scratch space by a unique ID, never by time alone.
- Do not take a uniqueness-critical ID from ZIO `Random` when tests run in parallel; `TestRandom` is deterministic.
- `StorageServiceLiveSpec` opens two scopes with the same name at one `TestClock` instant.
