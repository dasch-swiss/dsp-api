# Remote Build Execution (RBE) for dsp-api

dsp-api's Bazel CI runs against a shared, DaSCH-hosted **NativeLink** backend for a remote **cache**
and **executor**. This eliminates the cold-compile penalty every CI job used to pay (Nix's build cache
persists only the Nix store, not the Bazel output base or the Maven repo cache).

## Backend

- **`dasch-remotebuild-prod-01`** — the same NativeLink backend sipi uses. NativeLink-only, gRPC +
  mTLS on `:50051`, serving both the remote cache (Action Cache + CAS) and the remote executor.
- Provisioned **outside this repo** in the private `ops-tf` (the libvirt VM) and `ops-infra` (the
  NativeLink service config) repositories. **Do not** mutate the backend infra from here.
- Credentials are **org-level** GitHub secrets/vars, already available to dsp-api:
  `vars.REMOTEBUILD_RUNNER_ENDPOINT`, `secrets.REMOTEBUILD_CA_CERT`, `secrets.REMOTEBUILD_CLIENT_CERT`,
  `secrets.REMOTEBUILD_CLIENT_KEY`.

## Why the cache is the primary win

sipi runs RBE primarily for **cross-compilation** (building linux-amd64/arm64 + darwin-arm64 from one
x86_64 worker); its own docs note RBE "buys no per-PR speed on a single worker" on a warm cache.
dsp-api compiles to **JVM bytecode** — arch-independent, no cross-compile need — so the **remote
cache** is what directly kills the cold-compile pain. The executor adds parallel fan-out of Scala
compiles and JVM unit-test suites across the 250-core backend on top.

## Compile remote, test local

- **Pure-JVM unit tests** (`//modules/{webapi,ingest,bagit,jwt,shacl-validator}:test`) run on the
  remote executor and result-cache — they are hermetic.
- **Docker/testcontainer suites** (`//modules/test-it:*`, `//modules/test-e2e:test`,
  `//modules/test-ingest-integration:test`) carry a **`no-remote`** tag (see
  `modules/test-it/BUILD.bazel`'s `_TAGS` comment). They must run locally (host Docker socket), and
  `no-remote` also keeps their results out of the remote cache — the images they load
  (`//modules/*:load`) are a side effect Bazel does not model as a test input, so a cached "pass"
  could otherwise be served after an image changed.

## How the flags reach Bazel

Bazel does **not** expand env vars in `.bazelrc`, and the connection/cert values are only known at CI
runtime. So they are **not** committed to `.bazelrc` and **no** `ci.bazelrc` is generated. Instead:

1. The `bazel-rbe` composite action (`.github/actions/bazel-rbe`) writes the mTLS material to
   `$RUNNER_TEMP/.nl/` (outside the checkout) and emits a `flags` step-output string enabling the
   remote cache and executor.
2. Each CI step runs a `just` recipe with the flags appended:
   `nix develop --command just <recipe> ${{ steps.rbe.outputs.flags }}`.
3. Each bazel-invoking `just` recipe takes `*FLAGS=''` and appends `{{FLAGS}}` to its `bazel` command.

`just` is the single CI/build entry point. The backend-agnostic tuning
flags (`--remote_download_toplevel`, `--remote_local_fallback`, `--remote_timeout`,
`--remote_max_connections`, `--repository_cache`) live in `.bazelrc` — they are safe no-ops when no
remote is configured. External archives (http_archive, Maven) are cached via `--repository_cache`
(`~/.cache/bazel-repo`) plus an `actions/cache` step keyed on `MODULE.bazel.lock`.

Fork PRs (no secrets) get empty flags → a cold local build, unchanged from before.

## Local development

RBE is **CI-only**. Developers never contact the backend; Bazel's own local incremental cache is the
inner-loop. Machine-local flags (e.g. `--disk_cache=…`) go in a gitignored `user.bazelrc`
(`try-import`ed by `.bazelrc`).

## What runs remotely

Every RBE-using CI job runs the `bazel-rbe` action, which enables the remote cache **and** executor:
`build-and-test.yml` (PR + push-to-main build/test jobs) and the three publish workflows
(`docker-publish.yml`, `publish-release.yml`, `publish-from-branch.yml`). Build, compile, and
unit-test actions run on the NativeLink worker; only `bazel run //modules/*:push` (registry auth) and
the `no-remote` Docker/testcontainer suites run locally.

- `--remote_local_fallback` degrades to a local build on a backend outage, so an outage is never a CI
  break — just a slower run.
- The `-Xss8m` on the scalac toolchain (`bazel/toolchains/BUILD.bazel`) keeps the local-fallback
  `:webapi` compile from overflowing the worker's stack. Keep it whenever compiler JVM flags move.
- If a target fights the persistent-worker default under remote execution, try
  `--strategy=Scalac=remote`/`Javac=remote`, or pin that one target local.

## OCI images (both arches)

All four images build for `image_amd64` and `image_arm64` on the x86_64 worker under RBE: assembly is
arch-neutral (the per-arch base is pulled by digest at the repository-fetch phase, JVM jars / the
fuseki dist are packed, and the sipi binary is extracted from a local OCI tarball). `:load`/`:push`
run locally (Docker daemon / registry auth), which `--remote_download_toplevel` supports by
materializing the image tarball on the runner. This is the same path the publish workflows take for
`bazel run //modules/*:push`, and the `docker-healthcheck` job's `bazel run //modules/*:load`
exercises it on every PR.

## Tuning

`--jobs=128` is the executor fan-out starting point (sipi runs 192 across a 3-leg matrix). Retune with
the ops team as more repositories onboard, watching the backend's operator queue metric.
