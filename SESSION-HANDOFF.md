# Session handoff — Bazelify the dsp-api build

Working doc for continuing the Bazel migration on another machine/session. Self-contained
(the original plan lived in `~/.claude/plans/`, which does not travel between machines).

**Last updated:** 2026-07-11
**Branch:** `worktree-bazelify` (Phase 0, 0.5, 1, 2, 3, 4 and 5 done — Phase 6 is next). Phase 2 = commit `176ab2f1b`.
**Base:** rebased onto `origin/main` (`afa1e940a`); force-pushed. Was `d84f7edec`.

---

## Goal

Move the **whole build** to Bazel — compile all Scala with `rules_scala`, run tests under
`bazel test`, and produce all container images (`knora-sipi`, `knora-api`, `dsp-ingest`,
`apache-jena-fuseki`) with `rules_oci`. One coarse Bazel package per module under `modules/`
(finer-grained targets later). **All four images now build with Bazel** (sipi/api/ingest/fuseki,
Phases 0–5); only Makefile/CI/docker-compose still drive the sbt/Docker paths (Phase 6).

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
  tags `docker-compose.yml` consumes — `:latest` for sipi/knora-api/dsp-ingest, but the pinned
  version tag (e.g. `6.1.0-0`) for fuseki, since `docker-compose.yml` references fuseki by version,
  not `:latest` (see Phase 5).

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

### Phase 4 — testkit + test-it + test-e2e + test-ingest-integration + ingest's own specs ✅ COMPLETE

Done: all five test-support modules build + run under `bazel test`, byte-matching sbt's pass
counts exactly, with container startups held to ~1 set per JVM (not per spec class):

| Target | Bazel | sbt |
| --- | --- | --- |
| `//modules/ingest:test` | OK (186 tests) | Passed: Total 186 |
| `//modules/test-it:test` | OK (775 tests), 4 container starts | 775 of the 780 |
| `//modules/test-it:test_gravsearch_span` | OK (5 tests), 3 container starts, isolated | 5 of the 780 |
| `//modules/test-e2e:test` | OK (879 tests), 3 container starts | Passed: Total 879 |
| `//modules/test-ingest-integration:test` | OK (1 test) | Passed: Total 1 |

Regression: `//modules/{sipi,webapi,ingest}:image_amd64` and
`//modules/{bagit,jwt,shacl-validator,webapi}:test` still green.

- `modules/testkit/BUILD.bazel` (new): a **`scala_macro_library`, not `scala_library`.**
  `GoldenTest.scala` (`modules/testkit/src/main/scala/org/knora/webapi/GoldenTest.scala`) defines a
  real Scala 3 macro (`inline def goldenPath(suffix) = ${ goldenPathImpl(...) }`); the compiler
  interprets `goldenPathImpl` via reflection when a *different* Bazel target (test-it/test-e2e)
  calls it. A plain `scala_library` only exposes its **ijar** (interface jar, method bodies
  stripped) on a dependent's compile classpath — the interpreter needs real bytecode and dies with
  `ClassFormatError: Absent Code attribute ... GoldenTest$`. `scala_macro_library` sets
  `ScalaInfo.contains_macros = True`, which makes `rules_scala`'s `collect_jars` (`scala/private/
  common.bzl`) put the *full runtime jar* of that dependency ahead of any ijar on the consumer's
  classpath. webapi's own `GoldenTest.scala` copy (same file, duplicated because testkit depends on
  webapi so webapi can't depend on testkit) never hits this — its callers compile in the *same*
  Bazel target, so the macro never crosses a jar boundary. Symptom → cause was found by decompiling
  the real crash (`bazel-workers/worker-2-Scalac.log`), not guessed.
- **`TestContainerLayers`** (`modules/testkit/.../core/TestContainerLayers.scala`) is now a
  **process-static singleton**: `DspZTestRunnerBase.run` does a fresh
  `Runtime.default.unsafe.run{…}.provide(spec.bootstrap)` per spec class, so the per-spec `lazy val
  all` that memoized fine under sbt's shared zio-test runtime would rebuild the Fuseki/Sipi/
  dsp-ingest containers once per class. Fix: memoize only the container `ZEnvironment` in a Scala
  `lazy val`, built once via `Unsafe.unsafe { Runtime.default.unsafe.run(Scope.global.extend[Any](containersLayer.build)) }`
  — `Scope.global` is a permanently-open `Scope.Closeable` built into ZIO for exactly this
  ("shared for life of the process") — then `ZLayer.succeedEnvironment(cached) >+>
  AppConfigForTestContainers.testcontainers` (the config layer, with its `Runtime.setConfigProvider`
  side effect, stays *per-spec* — cheap, and correctness-sensitive). **Do not use `ZLayer.memoize`**
  — it dedupes within one runtime build, which is exactly what differs across per-class runner
  instances; the memo has to live below ZIO. Verified via `javap` against the real
  `zio_3-2.1.26.jar`, not assumed from docs.
- **Two jar-URI portability fixes**, same root cause as Phase 1/2's `ShaclValidatorSpec`/
  `TriplestoreServiceInMemorySpec` (`getClass.getResource(...).toURI` is a `jar:` URI under Bazel,
  a plain `file:` path under sbt; `Files.walk`/`FileInputStream` on it only works in the sbt case):
  `SharedVolumes.Images.createAssets` now branches on `uri.getScheme == "jar"` and opens a
  `FileSystems.newFileSystem(uri, ...)` to walk the jar-internal dir; `ingest`'s
  `SpecPaths.pathFromResource`/`resourceDir` do the same (single-file stream-copy vs.
  directory `Files.walkFileTree`-copy to a real temp dir).
- **`data`, not just `resources`, for anything read via a raw filesystem path.** A `resources`
  attribute (`java_library`/`scala_junit_test`) only puts files **on the classpath inside a jar** —
  it does not expose them as loose files, so any code doing `new FileInputStream("test_data/...")`
  or `Files.exists(Paths.get(...))` relative to CWD (sbt sets `baseDirectory := repo root` for
  test-it/test-e2e, so this "just works" under sbt) gets `FileNotFoundException` under Bazel.
  Fixed generically, not per call-site: declared `test_data/**` as a **`filegroup`**
  (`//test_data:integration_fixture_files`, replacing the earlier `integration_fixtures`
  `java_library` — sbt proves no code needs it on the classpath, since test_data isn't on sbt's
  classpath either) and `src/test/resources/**` a second time, both wired via `data =` on the
  `scala_junit_test` targets. `bazel test`'s CWD is the runfiles root, so a loose file at
  `test_data/project_data/anything-data.ttl` under that root resolves exactly like sbt's
  CWD-relative read. This fixes both `TestDataFileUtil.readTestData` (a shared helper, no fallback
  at all) and `GoldenTest.assertGolden` (its `goldenPath` macro computes a path by string-replacing
  `.../src/test/scala/...` → `.../src/test/resources/...` in the compiler's own source position,
  then does a raw filesystem check against it — the "resources jar" and "loose data file" paths for
  the *same* directory serve two different reader patterns, keep both attributes).
- **testcontainers' `withClasspathResourceMapping` + Bazel's `TEST_TMPDIR` + macOS Docker Desktop
  ⇒ non-deterministic-looking (actually 100% reproducible) container-start failure**, isolated to
  `SipiIT` (`modules/test-it/src/test/scala/org/knora/sipi/SipiIT.scala`, an independent
  `ZIOSpecDefault` that builds its own Sipi container, not through `TestContainerLayers`).
  `withClasspathResourceMapping` (used by `SipiTestContainer.make`, shared with
  `TestContainerLayers`' Sipi, which never fails) extracts the mapped classpath resource to a fresh
  temp file under `java.io.tmpdir` on every container start. Bazel's `TEST_TMPDIR` (what
  `java.io.tmpdir` defaults to under `bazel test`) lives under the execroot's ephemeral `_tmp`
  scratch dir; a container started well into a long-running JVM (SipiIT runs late relative to the
  earlier, JVM-startup-time `TestContainerLayers` build) hits
  `InternalServerErrorException: ... error while creating mount source path ...: no such file or
  directory` — a brand-new nested execroot path that hasn't propagated through Docker Desktop's
  file-sharing layer in time. Confirmed the cause (not just worked around it) with a discriminating
  test: pinning `-Djava.io.tmpdir=/tmp` (jvm_flags on the `scala_junit_test`) made `SipiIT` pass
  deterministically; reproduced the failure twice identically first, which is what ruled out "random
  flake" as an explanation. This also matches sbt, which never overrides `java.io.tmpdir` from the
  OS default — so the fix makes Bazel behave like sbt, not the other way around. Applied to every
  Docker-testcontainers target (test-it, test_gravsearch_span, test-e2e, test-ingest-integration).
- **`"exclusive"` tag**: `E2EZSpec` starts an in-process API server on a fixed port; two of these
  `scala_junit_test` targets (test-it, test_gravsearch_span, test-e2e) running concurrently on one
  machine race for it (`NativeIoException: bind(..) failed ... Address already in use`). Confirmed
  inter-target, not intra-target, by running test-it alone first (0 bind errors) before running all
  three together (bind errors appeared) — sbt never hits this because it runs one project's tests at
  a time. Fixed by tagging all Docker-dependent targets `"exclusive"`.
- `SearchResponderV2GravsearchSpanE2ESpec` split into its own `scala_junit_test`
  (`test_gravsearch_span`) — mirrors the `Test / testGrouping` isolation `build.sbt` already had for
  it (its in-memory OTel span exporter would otherwise be silently bypassed by whichever spec's
  fiber builds the shared `Tracing` service first).
- 135 further specs converted `object … extends (E2EZSpec|ZIOSpecDefault)` →
  `@RunWith(classOf[DspZTestJUnitRunner]) class …` (32 ingest, 54 test-it, 48 test-e2e, 1
  test-ingest-integration). The usual object→class static-path breakages recurred (Phase 2 gotcha
  #2's three sub-patterns: redundant self-import deleted, same-class self-qualifier stripped,
  genuine cross-file reference moved into a companion object + self-import) — worth calling out
  `SipiIT.scala` specifically: `dspApiPort` was referenced from a *separate* object
  (`MockDspApiServer`) in the same file, not a companion, so the fix had to *create* a companion
  `object SipiIT` rather than just de-qualify.
- Two unused imports removed from `test-ingest-integration` (`swiss.dasch.version.BuildInfo`,
  `sttp.client4`) to satisfy the stricter `-Wunused:all -Werror` on the Bazel toolchain — genuinely
  dead code, not needed under sbt either.
- sbt (`build.sbt` ingest/test-it/test-e2e/ingestIntegration blocks): the usual
  `testFrameworks := Seq(JUnitFramework)` + `junitInterface % Test` + `dependsOn(testRunner % Test)`
  swap (Phase 2 pattern). testkit itself is untouched sbt-side (compile-scope lib; its two main-scope
  specs — `FileModelsSpec`, `core/TestContainerLayersSpec` — still aren't run by either tool).

**Adversarial review (5 parallel lenses over the diff) caught two real issues, fixed:**

1. **Latent `FileSystemAlreadyExistsException` hazard in `SharedVolumes.resourceDirPath`.** The
   memoized `TestContainerLayers.cachedContainers` opens a `jar:` filesystem for `/sipi/testfiles`
   inside `Scope.global` — which never closes it. `SipiIT` independently builds its own
   `SharedVolumes.Images.layer` (bypassing the memo entirely), and the JDK's jar filesystem
   provider only allows one open filesystem per URI — a second `FileSystems.newFileSystem` on the
   same jar throws, and `Images.layer` ends in `.orDie`, so this would be a hard failure. It wasn't
   firing only because of incidental test-discovery ordering (`SipiIT` happens to run and close its
   own properly-scoped jar-fs before any memoized-container spec forces the permanent one) — a
   future spec addition or sharding change could flip it. Fixed by making `resourceDirPath`
   idempotent: on `FileSystemAlreadyExistsException`, attach to the existing filesystem via
   `FileSystems.getFileSystem(uri)` instead of failing, and only close what was actually opened
   (tracked via an `owned` flag) — attaching to someone else's filesystem must not close it out
   from under its real owner. Re-verified: `//modules/test-it:test` still `OK (775 tests)`, 4
   container starts, unchanged.
2. **`"exclusive"` tag rationale was incomplete for `test-ingest-integration`.** It doesn't use
   `E2EZSpec`'s fixed-port API server, so the port-collision reason alone wouldn't justify tagging
   it `exclusive` — but `SharedVolumes.Temp` (both the testkit and test-ingest-integration copies)
   is `ZLayer.succeed(Temp(System.getProperty("java.io.tmpdir")))`, not a freshly-created
   unique-per-run directory, so every Docker target pinning `java.io.tmpdir` to the same literal
   `/tmp` bind-mounts and writes to the *same host tree* through different dsp-ingest containers if
   run concurrently. Documented both reasons explicitly in the `_TAGS` comment
   (`modules/test-it/BUILD.bazel`) so a future edit doesn't "correct" `exclusive` away from
   `test-ingest-integration` on the mistaken belief only the port reason applies there. Also bumped
   `test-ingest-integration`'s `size` from `"medium"` to `"large"` for real headroom above its own
   5-minute `TestAspect.timeout`.

Reviews that found nothing to fix: spec-conversion consistency (all 135 conversions + companion-
object gotcha fixes verified correct; all four sbt project blocks apply the identical pattern),
data-isolation/span-isolation skepticism (traced `Db.initWithTestData`'s drop-then-reload confirms
no cross-spec RDF leak through the shared container; span-spec exclude path and `SipiIT`'s
independence both confirmed by reading the actual code, not assumed), and test-parity (all 5
Bazel pass counts independently re-confirmed via fresh runs or raw log inspection, no fabricated
numbers).

### Phase 5 — fuseki image ✅ COMPLETE

Done: `modules/fuseki/BUILD.bazel` (new) builds the fuseki image with `rules_oci`, both arches,
verified live: loaded as `daschswiss/apache-jena-fuseki:6.1.0-0` (the version tag compose already
consumes, not `:latest`), brought up standalone and in the full 5-container compose stack
(db+sipi+ingest+api+alloy), confirmed real TDB2 write/read/persist-across-restart, confirmed the
knora-api (Bazel-built) successfully loads its ontologies against it (`/health` green, fuseki logs
show the real startup CONSTRUCT queries for knora-base/knora-admin/salsah-gui/standoff, all
`200 OK`), confirmed OTel spans (`DSP_db_db`) land in the local Tempo correctly nested inside the
same distributed trace as `DSP_svc_api`. sbt/Dockerfile path (`make docker-build-fuseki-image`)
still builds, untouched. Regression: `//modules/{sipi,webapi,ingest}:image_amd64` still green.

**Two decisions locked in for this phase (see plan, not re-litigated):** consolidated onto the
existing `@temurin_base_{amd64,arm64}` (`eclipse-temurin:25-jre-noble`) instead of the Dockerfile's
`21-jre-jammy` — one base across all four images; and reused the existing pinned
`@otel_javaagent`/`@pyroscope_otel` (v2.26.1/v1.1.0) instead of the Dockerfile's older v2.21.0/v1.0.4
— killing the fuseki↔api OTel drift.

- **Base-binary audit gate paid off but found nothing to fix.** Before assembling anything,
  swept every command the entrypoint/healthcheck/fuseki-server wrapper invoke
  (`bash sed grep cp mkdir rm date printf od head tr openssl`) against
  `eclipse-temurin:25-jre-noble` via one `docker run`. All present, including `openssl` (the
  entrypoint's `openssl rand -hex 32` healthcheck-password line) — **no entrypoint script change
  needed.** Only `tini` was absent (expected; it was already planned as a separate `http_file`
  prefetch, same as `static_curl`).
- **Java 25 + TDB2 was the real risk, not "does it compile."** `bazel build` proves nothing about
  whether TDB2's mmap/`sun.misc.Unsafe` usage still works under Java 25's tightened native-access
  restrictions — general Jena-on-25 was already proven by webapi's test suite (Phase 2), but TDB2's
  storage layer isn't exercised there. Verified directly: inserted a triple into a named graph,
  read it back, **restarted the container, and read it back again** — confirms both the write path
  and on-disk persistence survive a real restart, not just an in-process cache. (A `java.lang.foreign.Linker`
  warning from Lucene's `PosixNativeAccess` and a `sun.misc.Unsafe` warning from the pyroscope-otel
  extension both appear in the logs — informational deprecation notices, not failures.)
  **⚠️ MONITOR (no action needed yet — do not "fix" this speculatively):** both warnings are
  governed by OpenJDK JEPs on an explicit deprecation-to-enforcement timeline that will eventually
  turn them into hard failures, not just log noise:
  - `java.lang.foreign.Linker`/native-access (Lucene, via `jena-text`): controlled by
    `--illegal-native-access={allow,warn,deny}` (JEP 472). `warn` has been the default since JDK 24
    (unchanged in 25); `deny` (→ `IllegalCallerException`) becomes default in an as-yet-unnumbered
    future JDK release.
  - `sun.misc.Unsafe` (from `pyroscope-otel`'s vendored protobuf, not Fuseki/Jena code): controlled
    by `--sun-misc-unsafe-memory-access={allow,warn,debug,deny}` (JEP 471). Phase 2 (runtime
    warnings, what we're seeing) targets "JDK 25 or earlier"; Phase 3 (exceptions by default) targets
    "JDK 26 or later".
  - `modules/fuseki/docker-entrypoint.sh` already has a commented-out fix on hand
    (`--enable-native-access=ALL-UNNAMED --add-modules=jdk.incubator.vector`, see the comment above
    the `JVM_ARGS` block referencing `apache/jena#2533`/`#2782`) — cheap and purely permissive if
    ever uncommented, but deliberately left as-is per an explicit decision (2026-07-11) not to bundle
    a runtime-behavior change into this migration PR.
  - **Confirmed not already handled elsewhere**: grepped the `ops-deploy` repo for
    `enable-native-access`/`illegal-native-access`/`incubator.vector`/`sun-misc-unsafe` — zero
    matches. Production's `JVM_ARGS` (`roles/dsp-deploy/templates/docker-compose-db.yml.j2`) only
    sets heap size (`-Xmx{{ DSP_DB_HEAP_SIZE }}`, per-host: `1G` default, `2G`–`3G` in prod/stage)
    and `-Dlog4j2.formatMsgNoLookups=true` (Log4Shell mitigation, unrelated).
  - **Revisit when:** OpenJDK actually numbers/ships the `deny`-by-default JDK for JEP 472 (watch
    https://openjdk.org/jeps/472), or sooner if `--illegal-native-access=deny`/an equivalent hard
    failure is ever observed in fuseki's logs.
- **Jena's `tdb2:unionDefaultGraph true` gotcha, unrelated to the migration but easy to
  misdiagnose as one:** an `INSERT DATA` with no `GRAPH` clause lands in the store's actual default
  graph, which `unionDefaultGraph` does **not** include when redefining "the default graph" as the
  union of *named* graphs — so a same-session read-back of an unnamed-graph insert comes back
  empty. This is a pre-existing `dsp-repo.ttl` config characteristic (unchanged by this phase, and
  identical under the original Dockerfile image), not a Bazel-image defect. dsp-api always writes
  to named graphs, so this never surfaces in practice; noted here purely so a future debugging
  session doesn't waste time on it.
- **`pkg_tar`'s own default silently flattens every subdirectory; `pkg_files` +
  `strip_prefix.from_pkg()` doesn't.** This is the first image needing a *whole nested directory
  tree* from an external `http_archive` (sipi/webapi/ingest only ever packed a flat jar list or a
  single local-package glob). Empirically verified via a throwaway probe target + `tar -tvf`
  inspection (not assumed from docs, which are ambiguous about cross-repo/package semantics): plain
  `pkg_tar(srcs=["@fuseki_dist//:dist"], package_dir=...)` collapsed the tarball's
  `service/service/fuseki.initd` down to a top-level `fuseki.initd` — harmless *today* (that
  subdirectory is an unused host sysv-init sample the container never invokes) but would silently
  corrupt a future release that ships a real nested `lib/`or `webapp/` dir onto the classpath.
  Fixed by switching to `pkg_files(strip_prefix = strip_prefix.from_pkg())` → `pkg_tar`, confirmed to
  preserve the nested path exactly.
- **`pkg_files`'s own default mode (`0644`) silently drops the tarball's real exec bits**, unlike
  the Dockerfile's plain `tar zxf` (which preserves them) — a second gap the same probe caught.
  Checked the actual upstream tarball's permissions directly (`tar -tvf`, not assumed): only
  `fuseki-backup`, `fuseki-plain`, `fuseki-server` ship `0755`; everything else is `0644`. Split the
  distribution into two `pkg_files` targets (an explicit-`0755` subset for those three, default
  `0644` for the rest) rather than a single blanket mode.
- **`http_archive`'s `build_file_content` needs its own synthetic files excluded from the
  glob.** A first pass (`glob(["**"], exclude=["fuseki.war"])`) leaked the repo rule's own
  `BUILD.bazel` and bzlmod's `REPO.bazel` marker into the image (caught by the same `tar -tvf`
  inspection). Excluded both explicitly; `fuseki-backup`/`fuseki-plain`/`fuseki-server` are also
  `exports_files()`'d (needed for cross-package `pkg_files` label references into `@fuseki_dist//`).
- **Fuseki dist tarball sourced from `archive.apache.org` (the permanent archive), not
  `downloads.apache.org` or the `mirrors.cgi` redirector** — `downloads.apache.org` serves only the
  *current* release and would 404 a clean fetch once Jena ships past 6.1.0; the mirror redirector
  resolves to a randomly-chosen, non-permanent mirror. `archive.apache.org` never purges old
  releases. `sha256` pin independently computed from the download and cross-checked byte-for-byte
  against the Dockerfile's `ARG FUSEKI_SHA512` (both verify the identical bytes).
- **`tools/oci/check_image_versions.sh` gained two new checks (fuseki dist version, fuseki image
  tag)**, taking two new args (`modules/fuseki/Dockerfile` and `modules/fuseki/BUILD.bazel`, both
  now `exports_files()`'d for cross-package reference from `//tools/oci`). The SHA256/SHA512 pins
  are self-guarding at fetch time (a version bump without a matching hash update fails the
  download, not silently succeeds), so the first check only needs to catch a bumped
  `FUSEKI_VERSION` whose tarball URL wasn't updated to match. **The second check was added after
  adversarial review caught a real gap**: unlike the other three images, fuseki's `IMAGE_VERSION`
  tag (`6.1.0-0`) is a plain literal baked into `modules/fuseki/BUILD.bazel`'s labels/env/
  `repo_tags` (not read from a stamped workspace-status key, since it's a pinned upstream release
  repackaged, not a git-versioned artifact) — so nothing was catching a bumped `IMAGE_VERSION`
  whose `BUILD.bazel` literals weren't updated to match. Verified the fix actually fails on a
  simulated drift (not a rubber stamp) before trusting it.
- **No `OTEL_DISABLE_ENV` on fuseki** (unlike api/ingest) — fuseki has no manual span
  instrumentation of its own, so the javaagent's HTTP/Netty auto-instrumentation is wanted, not
  redundant.
- `oci_image`'s exact attribute names (`workdir`, not `working_dir`; `volumes` as a plain list)
  confirmed via context7 docs before use — none of the other three images needed them, so there was
  no local precedent to copy.
- **Adversarial review (5 parallel Sonnet lenses over the diff)**: image-parity-vs-Dockerfile,
  layer/exec-bit correctness (rebuilt each layer and inspected `tar -tvf` output directly), version/
  label/drift-guard correctness (independently re-downloaded and re-hashed every pinned artifact),
  pattern-consistency-vs-sipi/webapi/ingest + handoff-accuracy cross-check, and an adversarial bug
  hunt (arch-swap check, live rebuild, runtime smoke test). No functional bug found — the image
  builds, runs, and serves data correctly, matching the Dockerfile with only the two intentional,
  pre-approved deviations (base image, OTel version). One real gap fixed (the `IMAGE_VERSION` drift
  check above, found independently by two of the five reviewers). Two cosmetic doc nits also fixed:
  `tools/oci/defs.bzl`'s module docstring updated to mention fuseki as a fourth consumer; this
  doc's own "Decisions locked in" section amended so it no longer implies *every* image's `oci_load`
  emits `:latest` (fuseki's is a pinned version tag, matching `docker-compose.yml`).

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
   - Docker-dependent test targets (`test-it`, `test-e2e`, `test-ingest-integration`) need the
     `:latest` images preloaded first: `bazel run //modules/sipi:load && bazel run
     //modules/ingest:load` (see below).
   - `nix develop --command bazel build //modules/fuseki:image_amd64` (fuseki image builds)
5. **Then start Phase 6** (rewire Makefile/CI/docker-compose; see roadmap above).

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

# Phase 4 — test-support modules (Docker-dependent ones need sipi+ingest :latest loaded first)
bazel test //modules/ingest:test
bazel test //modules/test-it:test //modules/test-it:test_gravsearch_span //modules/test-e2e:test
bazel test //modules/test-ingest-integration:test

# Phase 5 — fuseki image (note: version tag, not :latest — matches docker-compose.yml)
bazel build //modules/fuseki:image_{amd64,arm64}
bazel run //modules/fuseki:load
bazel test //tools/oci:image_versions_match_sbt   # sbt⇄Bazel version-drift guard, now incl. fuseki
docker compose up -d db sipi ingest api alloy
curl -u admin:test http://localhost:3030/$/datasets/dsp-repo && curl http://localhost:3333/health
```
