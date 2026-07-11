# Session handoff — Bazelify the dsp-api build

Working doc for continuing the Bazel migration on another machine/session. Self-contained
(the original plan lived in `~/.claude/plans/`, which does not travel between machines).

**Last updated:** 2026-07-10
**Branch:** `worktree-bazelify` (Phase 0, 0.5, 1, 2 and 3 done — Phase 4 is next). Phase 2 = commit `176ab2f1b`.
**Base:** rebased onto `origin/main` (`afa1e940a`); force-pushed. Was `d84f7edec`.

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
- **CI coverage** (`.github/workflows/build-and-test.yml`) hard-coded `target/scala-3.3.7/coverage-report/
  cobertura.xml`; the 3.8.4 bump made sbt write to `scala-3.8.4/…`, so the codacy upload failed
  "cobertura.xml does not exist" (advisory, non-blocking — red since the migration). Fixed without
  re-hard-coding the version: a `copyCoverageReport` sbt task copies the aggregated report from the
  version-specific `crossTarget` to a stable `target/coverage-report/cobertura.xml`; CI and the Makefile
  coverage targets run `coverageAggregate copyCoverageReport` and upload only the stable path. **Lesson:
  never hard-code `target/scala-<version>/…` in CI/Make — route through an sbt task so sbt owns the version.**

### Phase 2 — webapi library + BuildInfo + its 153 specs ✅ COMPLETE

Done: `bazel build //modules/webapi:{webapi,app}` compile clean (436 main files + generated
BuildInfo), and **`bazel test //modules/webapi:test` → OK (1602 tests)** matching
**`sbt "webapi/test"` → Passed 1602, Failed 0** exactly (153 spec classes, each run once, no
double-run). Regression: leaf-module tests + `//modules/sipi:image_amd64` still green.

- `modules/webapi/BUILD.bazel`: `buildinfo` target → `org.knora.webapi.http.version.BuildInfo`
  (`name`, `scalaVersion` as `string_fields`; `version`→`STABLE_GIT_VERSION`,
  `buildCommit`→`STABLE_GIT_SHORT`, `buildTime`→`STABLE_BUILD_TIME`, `sipi`→`STABLE_SIPI_IMAGE`,
  `fuseki`→`STABLE_FUSEKI_IMAGE` as `stamp_fields`). `scala_library webapi` (srcs = main glob +
  `:buildinfo`; `resources = glob(src/main/resources/**)` with
  `resource_strip_prefix = "modules/webapi/src/main/resources"` so `application.conf`/
  `knora-ontologies`/`shacl` land at classpath root) on `//modules/{bagit,jwt,shacl-validator}` +
  the webapi `@maven` deps. `scala_binary app` (`org.knora.webapi.Main`). `scala_junit_test test`
  (one JVM, `suffixes=["Spec","IT","Test"]`) + a `java_library export_spec_fixtures`.
- `tools/workspace_status.sh`: added `STABLE_BUILD_TIME` (`${BUILD_TIME:-dev}`, mirrors sbt).
- `test_data/BUILD.bazel` (new): `java_library fixtures` puts the referenced RDF fixtures
  (`project_ontologies/*`, the DefaultRdfData `project_data/*`, and the JsonLD `generated_test_data/*`)
  on the test classpath at their `test_data/...` path.
- sbt (`build.sbt` webapi block): `dependsOn(..., testRunner % Test)`,
  `testFrameworks := Seq(JUnitFramework)` (replaced `ZTestFramework`), `+ junitInterface % Test`.
- 153 specs `object X extends ZIOSpecDefault` → `@RunWith(classOf[DspZTestJUnitRunner]) class X …`
  (script over `*.scala` only, per gotcha #5).

**Phase 2 gotchas (save the rediscovery):**
1. **`com.google.gwt:gwt-servlet` relocates to `org.gwtproject:gwt-servlet`** — the `@maven` label
   is `org_gwtproject_gwt_servlet`, not `com_google_gwt_gwt_servlet`. rules_jvm_external names the
   target by the *resolved* coordinate. Grep the generated `@maven` `BUILD` for the real label when
   a `@maven//:...` target "not declared".
2. **`object`→`class` breaks nested-member static paths.** Specs whose nested `object`/`class` was
   referenced as `SpecName.Member` (self- or cross-import) stop compiling. Fixed by moving the member
   to a **companion `object SpecName`** (companions keep mutual `private` access): `CardinalitySpec`
   (`Generator`), `RestCardinalityServiceSpec` (`StubCardinalitiesService`). `KnoraIrisSpec.test` was
   just a redundant self-import of the inherited zio-test `test` DSL — deleted. Find them with
   `grep -E "^import .*Spec(\.[A-Za-z_]+)+$"` over the test tree; the compiler catches the rest.
3. **File I/O in specs is CWD/classpath-sensitive under Bazel.** sbt runs webapi tests with
   CWD=`modules/webapi`; the in-memory triplestore loader (`TriplestoreServiceInMemory.loadRdfUrl`)
   and `FileUtil` try `getResourceAsStream(path)` **first**, then `../`/`../..` filesystem fallbacks.
   Under the Bazel sandbox the fallbacks miss, so the fixture must be on the classpath at the *exact*
   key the spec passes. Fixes: `GoldenTest` reads the golden `.txt` via classpath (macro now also
   emits the resource-relative path) with the filesystem path kept only for `rewrite`; `JsonLDUtilSpec`
   uses a classpath-first `readFixture` helper; `TriplestoreServiceInMemorySpec` uses the production
   `knora-ontologies/knora-base.ttl` key.
4. **RdfDataObject ordering is hashCode-fragile.** `ExportServiceSpec.exportResourcesOai` compares a
   golden whose resource order comes from `Set[RdfDataObject]` iteration → the case-class hashCode.
   Editing a `RdfDataObject.path` string reorders the Set and breaks the golden **under sbt too**. So
   do NOT rewrite those path strings for Bazel; instead expose the fixtures at the spec's existing
   `webapi/src/test/resources/...` key via `java_library(resource_strip_prefix="modules")`.
5. The DspZTestJUnitRunner reports **coarse** JUnit XML (one testcase per spec class, `classname`
   null); real pass/fail counts live in the zio-test stdout (`OK (N tests)` / `Failures: N`). Phase 6
   must repoint `dorny/test-reporter` accordingly (already flagged).

### Phase 3 — knora-api + dsp-ingest images ✅ COMPLETE

Done: both images build (`bazel build //modules/{webapi,ingest}:image_{amd64,arm64}`) and were
verified live — loaded into Docker at the `:latest` tags docker-compose already uses, brought up
the full stack, hit both health/version endpoints, and confirmed via Tempo that real traces flow
for both services. sbt untouched, still green.

New: `tools/oci/defs.bzl` (`runtime_jars`, `oci_stamped_labels`, `image_rootfs_extract`);
`modules/ingest/BUILD.bazel` (ingest had no Bazel targets before this — `buildinfo` +
`scala_library` + `scala_binary` + image); image blocks appended to `modules/sipi/BUILD.bazel`
(sipi-binary extraction, exported to ingest) and `modules/webapi/BUILD.bazel`; `MODULE.bazel` gained
the temurin base pulls, the two OTel jar `http_file`s, and a `tar.bzl` bazel_dep.

- **Lib-dir classpath, not rules_scala's fat `app_deploy.jar`.** A fat jar's last-wins merge drops
  duplicate `META-INF/services/*` (OTel autoconfigure, sqlite's driver registration) and
  `reference.conf`. `runtime_jars` + `pkg_files`/`pkg_tar` reproduces sbt-native-packager's `lib/`
  dir instead; entrypoint `java -cp '.../*' <MainClass>`. Confirmed correct at runtime, not just by
  inspection (SPI-dependent bits — sqlite driver, OTel javaagent — worked end to end).
- **Sipi binary extraction is hermetic**: `image_rootfs_extract` uses the rules_oci crane toolchain
  (`crane export -` reads an `oci_load` docker tarball from stdin) plus the same hermetic bsdtar
  rules_oci itself registers (`@tar.bzl//tar/toolchain:type`) — needed its own `bazel_dep(tar.bzl)`
  since bzlmod doesn't expose a transitive dep's repos by bare name. No usrmerge surprises on the
  sipi base (`/sbin`, `/usr/bin` are real paths).
- **knora-api's `healthcheck.sh` needs curl+jq**, which the temurin base lacks and rules_oci can't
  apt-install. `moparisthebest/static-curl` (the obvious first choice) dropped its `aarch64` build
  after Nov 2024 — use `stunnel/static-curl` (actively maintained, genuinely multi-arch, musl-static)
  instead; jq's own official releases are already static, no third-party source needed. Verified
  live: `bash -x healthcheck.sh` inside the built container actually invokes both and exits 0.
- **`tools/oci/defs.bzl` and `tools/buildinfo/defs.bzl`'s stamp substitution now fails loudly on a
  missing/mistyped `STABLE_*` key** (a review caught the original `grep ... || true` silently
  writing an empty label/BuildInfo value instead). Rewritten with bash `${var//pattern/replacement}`
  instead of `sed` — also sidesteps sed's `&`/`\` replacement-escaping footgun for free.
- **Two tests catch sbt⇄Bazel dependency drift.** `//tools/oci:image_versions_match_sbt` (temurin
  base tag, sipi base tag, OTel/Pyroscope jar versions) is an `sh_test` (new `rules_shell` bazel_dep
  — native `sh_test` is gone from this Bazel version; bash/grep/sed are an RBE-safe baseline, same
  assumption every `sh_*`/`genrule` in the Bazel ecosystem makes).
  `//tools/deps:maven_versions_match_sbt` (every shared Maven coordinate between `MODULE.bazel`'s
  `maven.install` and `Dependencies.scala` — parses sbt's real `"group" %%/% "artifact" %
  VERSION_EXPR` syntax, resolving `val`-bound version identifiers, plus the two `maven.artifact()`
  exclusion overrides) is a **`py_test`, not an `sh_test` shelling out to a python3 shebang** — that
  would silently depend on whatever (if any) python3 an RBE worker has on PATH. New `rules_python`
  bazel_dep (already a transitive dep of the graph, so free) + `python.toolchain(python_version =
  "3.12")` registers a real hermetic per-platform CPython, confirmed via `cquery`
  (`interpreter_path` points at the fetched `rules_python++python+python_3_12_...` repo, not
  `/usr/bin/python3`). Both verified against several real injected mismatches (single-artifact bump,
  a shared version val touching 3 artifacts, a `maven.artifact()` override, an unresolvable
  identifier handled as a graceful warning) before
  trusting them.

### Phase 4 — testkit + test-it + test-e2e + test-ingest-integration + ingest's own specs

- **`modules/ingest` itself also needs a `scala_junit_test` target** — Phase 3 added its
  `scala_library`/`scala_binary`/image but no test target (out of that phase's scope), and this
  bullet's four modules don't cover it: `test-ingest-integration` is the separate `ingestIntegration`
  sbt project (Testcontainers, spins up the built image), not `modules/ingest/src/test/scala`'s own
  unit specs. Convert those the same way Phase 2 did for webapi (`@RunWith(classOf[DspZTestJUnitRunner])`).
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
   - `nix develop --command bazel build //modules/webapi:image_amd64 //modules/ingest:image_amd64`
     (both remaining images still build)
   - `./sbtx "webapi/compile"` (sbt on 3.8.4 still clean)
   - if maven needs a refetch: `nix develop --command bazel run @unpinned_maven//:pin`
5. **Then start Phase 4** (testkit + test-it + test-e2e + test-ingest-integration; see roadmap
   above).

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

# Phase 3 images — build, load into local Docker as :latest, bring up the stack
bazel build //modules/{sipi,webapi,ingest}:image_{amd64,arm64}
bazel run //modules/sipi:load && bazel run //modules/webapi:load && bazel run //modules/ingest:load
docker compose up -d db sipi ingest api alloy
curl http://localhost:3333/health && curl http://localhost:3340/health
```
