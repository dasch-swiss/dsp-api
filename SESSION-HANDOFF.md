# Session handoff — Bazelify the dsp-api build

Working doc for continuing the Bazel migration on another machine/session. Self-contained
(the original plan lived in `~/.claude/plans/`, which does not travel between machines).

**Last updated:** 2026-07-10
**Branch:** `worktree-bazelify` (pushed; Phase 0, 0.5 and 1 done — Phase 2 is next)
**Base:** `d84f7edec` on `main`

---

## Goal

Move the **whole build** to Bazel — compile all Scala with `rules_scala`, run tests under
`bazel test`, and produce all three remaining container images (`knora-api`, `dsp-ingest`,
`apache-jena-fuseki`) with `rules_oci`. One coarse Bazel package per module under `modules/`
(finer-grained targets later). The `knora-sipi` image is already on Bazel.

**sbt stays fully functional in parallel** for a validation period; we do not retire it yet.
The one deliberate sbt-side change is the test runner (both tools move to a single custom
JUnit runner — see Phase 1).

---

## Decisions locked in (do not re-litigate)

- **rules_scala**: `bazel-contrib/rules_scala` **v7.2.6 from BCR** (`bazel_dep`, no override). A
  local checkout at `/Users/subotic/_github.com/bazel-contrib/rules_scala` was **research-only**
  and machine-specific — do not depend on it.
- **Scala version**: **3.8.4** for both sbt and Bazel (newest; native in rules_scala v7.2.6).
- **rules_jvm_external**: **7.0** (6.10 pulls a `rules_android` that breaks on Bazel 9.1.1 —
  `CcInfo` moved; 7.0 fixes it). **rules_java 9.1.0** (provides `remotejdk_25`). **bazel_skylib 1.8.2**.
- **Dependencies**: single `@maven` repo, `maven.install` + committed `maven_install.json`,
  `version_conflict_policy = "pinned"`. Scala artifacts use the bare `_3` suffix (no helper).
  Do **not** add `scala3-library` — the toolchain provides the stdlib.
- **scalac options**: on a custom `scala_toolchain` (`//bazel/toolchains`), registered from the
  root MODULE so it wins over the default.
- **Test runner → one custom JUnit `Runner`, both tools** (`DspZTestJUnitRunner`, still to be
  written): used by sbt (`junit-interface`) and Bazel (`scala_junit_test`). Specs become
  `@RunWith`-annotated **classes**. Reason: the stock `zio-test-junit` `ZTestJUnitRunner` builds
  each spec's `bootstrap` **twice** and per-class, which would blow up the 76 container-heavy
  `E2EZSpec` tests (~1 shared container startup today → ~150) and break the OTel span-isolation
  spec. The custom runner builds `bootstrap` once; run one test target per module (shared JVM)
  with `TestContainerLayers` memoized as a JVM singleton to keep container startups ~1.
- **Images**: `rules_oci`, mirroring `modules/sipi/BUILD.bazel` (per-arch `oci_image` →
  `oci_image_index` → `alias`+`select` → `oci_load`/`oci_push`). `oci_load` emits the exact
  `:latest` tags `docker-compose.yml` consumes.

---

## Status — Phase 0 (Foundation) COMPLETE ✅

Two commits on `worktree-bazelify`:

| Commit | What |
| --- | --- |
| `ffb3b52dd` | Scala 3.3.7 → 3.8.4 migration (88 files; sbt clean under `-Werror`) |
| `0aad60547` | Bazel foundation (rules_scala/maven/toolchain/buildinfo) |

### What the Scala 3.8.4 migration touched (commit `ffb3b52dd`)

- `project/Dependencies.scala`: `ScalaVersion = "3.8.4"`
- `build.sbt` `customScalacOptions`: `-Yresolve-term-conflict:package` → `-Xresolve-term-conflict:package`;
  `-Xfatal-warnings` → `-Werror`
- ~85 `.scala` files, mostly via the compiler's own `-source:3.4/3.7-migration -rewrite`:
  `x: _*` → `x*`; context bounds passed explicitly now need `using`; `Random.nextUUID()` →
  `Random.nextUUID`; a few unused imports removed; one guarded `collect` → `filter` + `map`.
- **Migration procedure (for reference):** temporarily drop `-Werror`, add `-source:3.4-migration
  -rewrite`, compile all modules main+test → then `-source:3.7-migration` (levels must be applied
  low→high; `-rewrite` only writes patches when the module fully compiles, so clear cross-level
  blockers manually first). Then restore `-Werror` and fix the residual handful of new-3.8 lints.

### What the Bazel foundation added (commit `0aad60547`)

- `MODULE.bazel`: `rules_scala 7.2.6`, `rules_jvm_external 7.0`, `rules_java 9.1.0`,
  `bazel_skylib 1.8.2`; `scala_config` (3.8.4); `scala_deps.scala()` + `scala_deps.junit()`;
  `maven.install` (declared artifacts → 344 with transitives); `register_toolchains(//bazel/toolchains:dsp_scala_toolchain)`.
  The existing `knora-sipi` oci block is unchanged.
- `maven_install.json`: committed lockfile (repin with `bazel run @unpinned_maven//:pin`).
- `BUILD.bazel` (root): `exports_files(["maven_install.json"])` so the lock label resolves.
- `bazel/toolchains/BUILD.bazel`: custom `setup_scala_toolchain` — our scalacopts (the 3.8-correct
  set) + `enable_semanticdb = True`; `-Dotel...` moved to `scalac_jvm_flags`.
- `.bazelrc`: `--workspace_status_command=tools/workspace_status.sh`; JDK 25 flags
  (`--java_language_version=25`, `--java_runtime_version=remotejdk_25`, `--tool_java_runtime_version=remotejdk_25`).
- `tools/workspace_status.sh`: emits `STABLE_GIT_VERSION` (mirrors build.sbt gitVersion, incl.
  branch suffix and `+`→`-`), `STABLE_GIT_COMMIT`, `STABLE_GIT_SHORT`, and `STABLE_SIPI_IMAGE` /
  `STABLE_FUSEKI_IMAGE` (grepped from `Dependencies.scala`).
- `tools/buildinfo/{defs.bzl,BUILD.bazel}`: stamp-aware rule replacing sbt-buildinfo (+ a `:smoke`
  target). Generates `object BuildInfo` from `string_fields` + `stamp_fields` (status keys).

### Verified at end of Phase 0

- `bazel run @maven//:pin` resolves cleanly (zio-json version conflict handled by `pinned`).
- `bazel build //tools/buildinfo:smoke` → correct stamped version.
- `bazel build //modules/sipi:image_amd64` → **sipi image still builds (no regression)**.
- `sbt` compiles all modules main+test clean under `-Werror` on 3.8.4.

---

## Roadmap — remaining phases (from the approved plan)

### Phase 1 — Leaf libs + custom `DspZTestJUnitRunner` spike ✅ COMPLETE

Done: `modules/{bagit,jwt,shacl-validator}` build + test under Bazel, and sbt ⇄ bazel **agree**
(bagit 125, jwt 30, shacl-validator 20; all pass; no double-run). Deferred toolchain `cquery` ran.

- **`DspZTestJUnitRunner` lives in `modules/test-runner`, NOT `modules/testkit`.** testkit →
  webapi → {bagit,jwt,shacl-validator}, so a leaf test depending on testkit for the runner cycles.
  The new leaf module `test-runner` (zio-test + junit only) is depended on by every test module.
- The runner's real implementation is `zio.test.junit.DspZTestRunnerBase` (package `zio.test.junit`
  is required to reach zio-test's `private[zio]` `runSpecAsApp`); `org.knora.testrunner.DspZTestJUnitRunner`
  is a thin subclass so specs reference a DaSCH type. `getDescription` is coarse
  (`createSuiteDescription(klass)`) → no double-`bootstrap`; per-test `Description`s use the
  `(Class, name)` overload with a path-qualified name → non-null `getTestClass` (Bazel's JUnit
  runner NPEs on a name-only description when a test fails).
- 14 specs converted `object … extends ZIOSpecDefault` → `@RunWith(classOf[DspZTestJUnitRunner]) class …`.
- sbt: `testFrameworks := Seq(new TestFramework("com.novocode.junit.JUnitFramework"))` (**replaced**
  `ZTestFramework`), `junit-interface` added, `zioTestSbt` dropped from the three leaf modules.
- Two test-portability fixes for the Bazel sandbox / RBE (both kept green under sbt too):
  `ShaclValidatorSpec` now materializes its `.ttl` fixture to a temp file via a shared scoped
  `ZLayer` (classpath resource → jar under Bazel, so `Path.of(getResource.toURI)` fails);
  `BagReaderSpec`'s deep-dir test now runs `walkDirectory` on a small-stack thread at a
  path-safe depth (was implicitly testing macOS `PATH_MAX`, not stack-safety).

### Phase 0.5 — Toolchain foundation the Scala build actually needed (discovered in Phase 1)

Phase 0 built only the sipi OCI image + buildinfo, so it never exercised a `scala_library`. The
first real Scala compile needed:

- **Hermetic LLVM C toolchain** (`bazel_dep llvm 0.8.10`, LLVM 22.1.7, sipi's `hermetic_llvm_headers_glob.patch`)
  — the rules_scala worker builds protobuf/upb from source; no system Xcode, RBE-ready. See `.bazelrc`.
- `use_repo(scala_deps, …)` the compiler artifact repos (`io_bazel_rules_scala_scala_*_3_8_4`,
  `org_scala_sbt_compiler_interface_3_8_4`).
- Toolchain **`dependency_mode = "transitive"`** (default "direct" hides zio-stacktracer → ZIO
  auto-trace `given` missing → "No given zio.Trace" on every effect), **`strict_deps_mode = "off"`**,
  and `-Wconf:src=.*external/.*:s` (exempt rules_scala's own from-source helpers from `-Werror`).
- `maven.install(excluded_artifacts = [scala3-library_3, scala-library, tasty-core_3])` — the toolchain
  provides the 3.8.4 stdlib; maven pulls 3.3.7 transitively → `scala.caps` package/object clash.

### Phase 2 — webapi library + BuildInfo + its 153 specs

- `modules/webapi/BUILD.bazel`: `scala_library` (srcs = main glob + generated `:buildinfo`;
  resources include `knora-ontologies`, `shacl`, `application.conf` with `resource_strip_prefix`)
  on `//modules/{bagit,jwt,shacl-validator}` + the webapi `@maven` group; `scala_binary` `app`
  (`org.knora.webapi.Main`). BuildInfo object `org.knora.webapi.http.version.BuildInfo` with
  fields `name, version, scalaVersion, sbtVersion, sipi, fuseki, buildCommit, buildTime`.
- Convert webapi's specs; verify `bazel build` compiles all ~609 files; `sbt`/`bazel test` agree.

### Phase 3 — knora-api + dsp-ingest images

- Add per-arch base pulls to MODULE.bazel: `eclipse-temurin:25-jre-noble` (single-manifest
  digests via `crane`, **not** index digest + platform — the arm64 `v8`-variant gotcha).
- knora-api: deploy-jar at `/opt/docker/lib/webapi.jar`; `scripts/**` → `/opt/docker/scripts`;
  otel jars → `/usr/local/lib` (**do NOT bake `JAVA_TOOL_OPTIONS`** — compose/ops-deploy set it);
  keep the two `OTEL_INSTRUMENTATION_*_ENABLED=false` env; `/tmp` writable (`RepositoryUpdater`);
  a static `curl` layer for the compose healthcheck (image is apt-free).
- ingest: `swiss.dasch.version.BuildInfo` (`name, version, scalaVersion, sbtVersion="bazel",
  knoraSipiVersion=STABLE_GIT_VERSION, gitCommit, buildTime`). Extract `/sbin/sipi`, `/sipi`
  (incl. `/sipi/scripts` — from the **overlay** `//modules/sipi:image_{arch}`, not the base),
  `/usr/bin/{tini,ffmpeg,ffprobe}` via a per-arch `crane export` genrule.

### Phase 4 — testkit + test-it + test-e2e + test-ingest-integration

- testkit `scala_library` (compile-scope test libs). Module-level `scala_junit_test` per test
  module (one JVM each, `tags=["requires-network","no-sandbox"]`, `test_data` wired). Make
  `TestContainerLayers` a JVM-memoized singleton; split `SearchResponderV2GravsearchSpanE2ESpec`
  into its own target (own JVM). Verify container startups not multiplied; span isolation holds.

### Phase 5 — fuseki image

- `modules/fuseki/BUILD.bazel`: base `eclipse-temurin:21-jre-jammy` (has bash+coreutils);
  add **static** `tini` + `curl` via `http_file` (NO apt/distroless); verify `openssl` in base
  (else rewrite the entrypoint's `openssl rand` line to `od`/`tr` on `/dev/urandom`). Fuseki
  tarball via `http_archive` from `archive.apache.org` (sha512 SRI = base64 of raw bytes;
  `strip_prefix`). Config/otel layers; `oci_image` env/volumes/entrypoint/cmd/labels.

### Phase 6 — Rewire Makefile + CI + docker-compose

- Makefile `docker-build/publish-*` → `bazel run //modules/<m>:{load,push}`; test targets →
  `bazel test`. `docker-image-tag` → `workspace_status.sh` `STABLE_GIT_VERSION` **+ a CI gate
  asserting it byte-matches `sbt "print dockerImageTag"`** (deployed tag flows to Jenkins).
- CI: `docker-publish(-fuseki).yml` → bazel; `build-and-test.yml` → `bazel test` + repoint
  `dorny/test-reporter` globs to `bazel-testlogs/**/test.xml`; bump `preparation` action java → 25.
- docker-compose: add `healthcheck:` blocks for `api`, `ingest`, `db` (rules_oci can't emit the
  Docker `HEALTHCHECK` directive; prod uses k8s `httpGet` probes, so no prod regression).

---

## Key gotchas / learnings (save yourself the rediscovery)

1. **rules_jvm_external must be 7.0** on Bazel 9.1.1 (6.10's transitive `rules_android` fails with
   "CcInfo symbol has been removed").
2. **First-pin bootstrap:** the maven extension reads `lock_file` during eval, so it can't
   self-create it. To regenerate from scratch: comment out `lock_file`, empty the root `BUILD.bazel`,
   `bazel run @maven//:pin` (writes `rules_jvm_external++maven+maven_install.json`), `mv` that to
   `maven_install.json`, then restore `lock_file` + `exports_files`. Normal re-pin (lock present):
   `bazel run @unpinned_maven//:pin`.
3. **Scala migration levels apply low→high** (3.4 before 3.7); `-rewrite` only writes patches when
   the module fully compiles — clear cross-level blockers manually first.
4. **sbt project ids ≠ val names**: it's `shacl-validator` (not `shaclValidator`) and
   `ingestIntegration` (not `test-ingest-integration`).
5. **Don't run text transforms over `modules/**` blindly** — a `: _*`→`*` sweep corrupted a binary
   `.jp2` test fixture; restrict to `*.scala` and review `git diff`.
6. **JDK 25 flags in `.bazelrc` only bite when a Java/Scala target builds** — safe for the sipi/oci
   build and the maven pin.

---

## Resume on another machine

1. **Push the branch first** (currently local only):
   `git push -u origin worktree-bazelify` (from this machine), then check it out elsewhere.
   This SESSION-HANDOFF.md is committed on the branch too.
2. **Environment:** Nix dev shell provides bazel (bazelisk, `.bazelversion` = 9.1.1), JDK 25
   (`temurin-bin-25`), `just`, `crane`. Enter via `direnv` (`direnv allow`) or
   `nix develop --command <cmd>`. Docker Desktop for image/integration work.
3. **rules_scala** comes from BCR now — no local checkout needed.
4. **Sanity checks after checkout:**
   - `nix develop --command bazel build //modules/sipi:image_amd64` (sipi still builds)
   - `nix develop --command bazel build //tools/buildinfo:smoke` then inspect
     `bazel-bin/tools/buildinfo/SmokeBuildInfo.scala` (stamping works)
   - `./sbtx "webapi/compile"` (sbt on 3.8.4 still clean)
   - if maven needs a refetch: `nix develop --command bazel run @unpinned_maven//:pin`
5. **Then start Phase 2** (webapi library + BuildInfo + its specs; see roadmap above). Phase 0.5 and
   Phase 1 are done — the Scala toolchain, hermetic LLVM C toolchain, and the custom JUnit runner
   (`//modules/test-runner`) are in place and green.

## Useful commands

```sh
# sbt (build of record) — compile a module (note the hyphenated/val id differences)
./sbtx "webapi/compile"
./sbtx "shacl-validator/compile"   ./sbtx "ingestIntegration/Test/compile"

# Bazel
bazel run @unpinned_maven//:pin          # re-pin maven after changing deps
bazel build //tools/buildinfo:smoke      # verify buildinfo + stamping
bazel build //modules/sipi:image_amd64   # sipi image (regression check)
make docker-build-sipi-image             # sipi image via Makefile (bazel under the hood)
```
