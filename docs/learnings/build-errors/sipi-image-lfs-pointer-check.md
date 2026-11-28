---
title: "Check a sipi image's binary for Git LFS pointer text before pinning it"
date: 2026-11-28
category: build-errors
component: sipi
module: MODULE.bazel
problem_type: build-error
severity: high
symptoms:
  - "The sipi binary in a released image contains Git LFS pointer text instead of real content"
  - "//modules/sipi:runtime_fs_amd64 fails in a sandbox: bsdtar cannot chown to 997/987"
root_cause: "sipi v10.0.1 and v10.1.0 were built from a checkout where Git LFS objects were not fetched."
tags: [sipi, docker, git-lfs, oci, digest, bazel, bsdtar]
issue: "DEV-8099"
---

# Check a sipi image's binary for Git LFS pointer text before pinning it

## Problem

The pinned sipi images v10.0.1 and v10.1.0 carry Git LFS pointer text in their binary. v10.2.0 is the first release
without it.

## Root Cause

The release build ran without fetching LFS objects, so pointer files went into the build.

## Solution

`MODULE.bazel` pins `daschswiss/sipi:v10.2.0` by index and per-arch digests, verified against the registry index.

Check before pinning a new version:

```bash
strings sbin/sipi | grep -c git-lfs.github.com   # must print 0
```

## Prevention

- Run the LFS check on each image binary in the bump procedure ("Docker Image Versions" in
  `docs/05-internals/development/third-party.md`).
- In a Linux sandbox that runs Bazel as root in a user namespace, `//modules/sipi:runtime_fs_amd64` fails because
  bsdtar cannot chown to the image's 997/987; `--strategy=CraneRootfsExtract=local` builds it.
