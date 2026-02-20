---
title: "Long-lived processes need process isolation from the build tool"
date: 2026-02-19
category: build-errors
component: build-system
module: build.sbt
problem_type: dev-workflow
severity: medium
symptoms: |
  Pressing Ctrl+C during `sbt run` causes NoClassDefFoundError cascades and a hung process
  that requires `kill -9` to terminate.
root_cause: |
  The build tool runs the application in-process (sharing classloaders and threads). On shutdown,
  the build tool tears down shared resources while the application's threads are still running.
tags:
  - sbt
  - process-isolation
  - shutdown
  - developer-experience
  - server-applications
---

## Problem

Running a long-lived server application (HTTP server, ZIO app with Netty) via the build tool and pressing Ctrl+C resulted in a broken shutdown: `NoClassDefFoundError` cascades and a hung process requiring `kill -9`.

## Root Cause

When a build tool runs an application in-process (its default mode), the build tool and the application share the same JVM, classloaders, and thread pool. On Ctrl+C, the build tool initiates its own shutdown — tearing down classloaders and internal state — while the application's threads are still running. This mismatch causes class-loading errors and prevents clean shutdown.

The general principle: **build tools and long-lived applications have conflicting shutdown lifecycles**. The build tool wants to clean up and exit; the application needs to run its own graceful shutdown sequence (draining connections, flushing buffers, etc.). When they share a process, neither can shut down correctly.

## Solution

Run the application in a separate (forked) process so that shutdown signals propagate directly to the application rather than being intercepted by the build tool.

In sbt:

```scala
run / fork := true // run in a forked JVM so Ctrl+C shuts down cleanly
```

The same principle applies to other build tools (Gradle `JavaExec`, Maven exec plugin, etc.): always fork a separate process for server applications.

## Prevention

- **Default to forking for server apps**: Any project that runs a long-lived process (HTTP server, message consumer, etc.) via the build tool should fork by default.
- **Test the shutdown path**: After setting up a `run` configuration, verify that Ctrl+C results in a clean shutdown — not just that the process starts correctly.

## References

- PR #3962
- `build.sbt` — `run / fork := true` setting
