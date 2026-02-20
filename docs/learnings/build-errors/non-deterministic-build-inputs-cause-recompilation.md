---
title: "Non-deterministic build inputs cause excessive recompilation"
date: 2026-02-19
category: build-errors
component: build-system
module: build.sbt
problem_type: build-performance
severity: medium
symptoms: |
  Every sbt compile triggers a full recompilation even when no source files have changed.
  Incremental compilation never kicks in, making the development feedback loop unnecessarily slow.
root_cause: |
  Non-deterministic values (e.g. timestamps, random IDs) embedded in build-generated source files
  change on every invocation, invalidating incremental compilation.
tags:
  - sbt
  - buildinfo
  - incremental-compilation
  - build-performance
  - developer-experience
---

## Problem

Running `sbt compile` twice in succession with no code changes still triggered a full recompilation. This made the development feedback loop unnecessarily slow — every compile took as long as a clean build.

## Root Cause

The `build.sbt` used `Instant.now` and `BuildInfoOption.BuildTime` to embed a build timestamp into the generated `BuildInfo.scala` file. Because this value changed on every sbt invocation, the generated source file was always different, which invalidated sbt's incremental compilation cache and forced a full recompile.

More generally: any non-deterministic input to the build pipeline (timestamps, random values, git-dirty flags that fluctuate) that ends up in generated source will break incremental compilation.

## Solution

Replace non-deterministic build values with environment variables that have stable defaults for local development:

```scala
// Before: changes on every invocation, breaks incremental compilation
lazy val buildTime = Instant.now.toString
buildInfoOptions += BuildInfoOption.BuildTime

// After: stable default for local dev, real value injected in CI/Docker
lazy val buildTime = sys.env.getOrElse("BUILD_TIME", "dev")
buildInfoKeys += BuildInfoKey("buildTime" -> sys.env.getOrElse("BUILD_TIME", "dev"))
```

The `Makefile` exports the real value for CI and Docker builds:

```makefile
export BUILD_TIME := $(shell date -u +"%Y-%m-%dT%H:%M:%SZ")
```

This way local development gets fast incremental compilation, while production builds still get accurate metadata.

## Prevention

- **Audit generated sources for non-determinism**: Any value that changes between invocations without code changes will break incremental compilation. Timestamps, random IDs, and volatile git metadata are common culprits.
- **Use env vars with stable defaults**: For build metadata that must vary between environments, read from environment variables with a fixed default for local dev.
- **Test incremental compilation**: After changing `build.sbt`, run `sbt compile` twice and verify the second run reports no recompilation.

## References

- PR #3963
- `build.sbt` — `buildTime` definition
- `Makefile` — `BUILD_TIME` export
