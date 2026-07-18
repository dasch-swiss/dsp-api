# Session handoff — Bazelify the dsp-api build

Working doc for continuing the Bazel migration on another machine/session. Self-contained
(the original plan lived in `~/.claude/plans/`, which does not travel between machines).

**Last updated:** 2026-07-12
**Branch:** `worktree-bazelify` (Phase 0, 0.5, 1, 2, 3, 4, 5 and 6 done — migration complete). Phase 2 = commit `176ab2f1b`.
**Base:** rebased onto `origin/main` (`afa1e940a`); force-pushed. Was `d84f7edec`.

---

## Goal

Move the **whole build** to Bazel — compile all Scala with `rules_scala`, run tests under
`bazel test`, and produce all container images (`knora-sipi`, `knora-api`, `dsp-ingest`,
`apache-jena-fuseki`) with `rules_oci`. One coarse Bazel package per module under `modules/`
(finer-grained targets later). **All four images build with Bazel** (sipi/api/ingest/fuseki,
Phases 0–5), and as of Phase 6 the **driver layer** (Makefile, CI, docker-compose) calls Bazel
too — sbt is no longer in the everyday build/test/publish path except where deliberately retained
(fuseki's publish path, and a temporary tag-drift gate — see Phase 6).

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
      <https://openjdk.org/jeps/472>), or sooner if `--illegal-native-access=deny`/an equivalent hard
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

### Phase 6 — Rewire Makefile + CI + docker-compose ✅ COMPLETE

Done: the Makefile, `justfile`, `docker-compose.yml`, and `build-and-test.yml` now drive
Bazel for the everyday build/test/publish path. Verified live (not just "the target ran"):
all four images built + loaded via `bazel run //modules/<m>:load`, `docker compose up --wait`
reports all 5 containers **healthy** (incl. the three new healthchecks), each healthcheck
command run manually inside its container exits 0, `api`/`ingest`/`fuseki` endpoints respond
correctly, `make test`/`bazel test //modules/bagit:test` pass, and the CI `cp -L` collect step
correctly dereferences the `bazel-testlogs` symlink into a real workspace file (checked locally,
not assumed). **Deliberately deferred, not forgotten:** fuseki's *publish* path (Makefile
`docker-build/publish-fuseki-image`, `docker-publish-fuseki.yml`) stays on the Dockerfile/
`docker buildx` — consistent with the Phase 5 decision to retain the Dockerfile as fuseki's
publish path during the validation window. The Bazel fuseki image is still exercised every CI
run as a test-preload (`bazel run //modules/fuseki:load`) and guarded by
`//tools/oci:image_versions_match_sbt`.

**6a — Makefile (+ justfile):**

- `docker-build-dsp-api-image`/`docker-publish-dsp-api-image` and the ingest equivalents now
  mirror the sipi pattern exactly: extract `TAG` from `docker-image-tag`, `bazel run
  //modules/<m>:load` + `docker tag ...:latest ...:$TAG` for build; `bazel run //modules/<m>:push
  -- -t latest -t "$TAG"` for publish. Fuseki's own build/publish targets are untouched (buildx,
  deferred above); the `docker-build`/`docker-publish` aggregates already excluded fuseki before
  this phase and still do.
- `docker-image-tag` now reads `tools/workspace_status.sh`'s `STABLE_GIT_VERSION` key instead of
  calling sbt — that script already mirrors sbt's `gitVersion` exactly (same `git describe --tag
  --dirty --abbrev=7 --always`, same non-`main` branch suffix, same `+`→`-`). **The two
  computations can't structurally diverge**: `build.sbt` doesn't reimplement any of this in Scala —
  it shells out via `!!` to the literal same `git describe`/`git rev-parse` commands the bash script
  runs, against the same working tree, so there's no "two independent implementations" risk, only a
  "did we transcribe the same flags/stage-order" risk. **New `check-docker-image-tag` target**
  asserts byte-match against sbt's `print dockerImageTag` anyway, as a mechanical safety net for
  that transcription risk — this is the one place sbt is intentionally retained (temporarily) since
  the tag flows to the Jenkins deploy webhook via `docker-publish.yml`. **Verified live on this
  actual worktree** — a dirty tree on a non-`main` branch
  (`v37.0.0-24-gf4d322b-dirty-worktree-bazelify`) exercises both the `-dirty` and branch-suffix
  paths simultaneously and both tools agree byte-for-byte.
- New `docker-load-test-images` target (Makefile + justfile) factors out the three-image preload
  (`bazel run //modules/{sipi,ingest,fuseki}:load`) shared by `test-it`/`test-e2e`/
  `test-ingest-integration`, replacing their old `docker-build-sipi-image` (sbt-tag-bearing)
  prerequisite. Test targets themselves became bare `bazel test //modules/<m>:test` (dropped the
  `coverage ... coverageAggregate copyCoverageReport` sbt chain — coverage reporting is dropped
  this phase, see 6c).

**6b — docker-compose healthchecks:** added `healthcheck:` blocks for `api`, `ingest`, `db` (only
`sipi` had one before). **Commands copied verbatim from the sbt-built images' own `HEALTHCHECK`
directives** (`build.sbt:245–248` api, `build.sbt:533–540` ingest, `modules/fuseki/Dockerfile:90–91`
db) rather than invented fresh — same intervals too. `depends_on` was deliberately left untouched
(no `condition: service_healthy` upgrade — that's a startup-ordering change nobody asked for).
rules_oci can't emit the Docker `HEALTHCHECK` directive itself, and prod runs out-of-repo
(`ops-deploy`) and doesn't consume this compose file for probing, so these are dev-only additions
with no prod regression.

**6c — CI (`build-and-test.yml` + `preparation` action):**

- Every test job now runs `nix develop --command bazel test --test_output=errors //...` instead of
  `./sbtx`. `test-it`/`test-e2e`/`test-ingest-integration` simplified further: since `make test-it`
  (etc.) now embeds the `docker-load-test-images` preload as a Make prerequisite, the CI step for
  each collapsed to a single `nix develop --command make test-it` — the old separate "build fuseki
  if changed" conditional and "build sipi locally" steps were removed entirely, replaced by an
  **unconditional** preload of all three images every run (more correct: previously fuseki was only
  rebuilt when its own files changed, so a stale registry fuseki could silently back an IT run that
  touched shared code).
- **dorny/test-reporter repointed (all 7 usages).** Bazel writes coarse
  `bazel-testlogs/modules/<m>/test/test.xml` (one `<testcase>` per spec class, `classname` null —
  Phase 2 gotcha #5) via a symlink into the Bazel output base, outside `GITHUB_WORKSPACE`. Rather
  than pointing dorny's glob straight through that symlink (fragile — `@actions/glob` following a
  symlink whose target lives outside the workspace is exactly the case that breaks), every job
  gets a `Collect Bazel test XML` step (`if: success() || failure()` — **mandatory**, or the
  collect step is skipped on the one case you need it, a real test failure) that `cp -L`s the real
  file into a workspace-local `test-xml/` dir; dorny then globs `test-xml/*.xml`. **Verified locally
  that `cp -L` actually dereferences correctly** (macOS; Linux CI behaves the same via GNU
  coreutils) — this was the one CI-only risk that turned out to be checkable without pushing.
  Accepted, unavoidable consequence: dorny's per-spec granularity is gone; the actual merge gate is
  each job's `bazel test` exit code, not dorny's rendering — **only a problem if a dorny check-run
  name (not the job name) is a required status check**, see the manual follow-up below.
- **Coverage reporting dropped entirely** (scoverage is sbt-only; `bazel test` emits no cobertura,
  and the decision was to drop rather than wire JaCoCo). Removed: the whole `upload-coverage` job
  (Codacy + Codecov uploads), every per-job "Upload coverage report" artifact step, the
  `coverageAggregate copyCoverageReport` sbt chains, the `id-token: write` permission (existed only
  for Codecov's tokenless OIDC upload), and `.codecov.yml`.
- **Codacy static analysis removed too** (follow-up decision after the coverage drop): deleted
  `.codacy.yml`, the two Codacy README badges (root `README.md`), and retitled `CONVENTIONS.md`'s
  "Static analysis (Codacy)" section to plain code-style guidance (the underlying rules — ASCII
  only, prefer immutability, method-length cap — are kept as team convention, just no longer framed
  as CI-enforced). **Manual step still needed, outside this repo's code**: the "Codacy Static Code
  Analysis" GitHub check is driven by Codacy's GitHub App integration on the repo, not by any file
  here — deleting `.codacy.yml` stops the exclude-paths config from applying but does **not** by
  itself stop the check from running or appearing on PRs. Uninstalling/disabling the Codacy GitHub
  App for this repo (via repo Settings → Integrations, or the Codacy dashboard) requires org/repo
  admin access and is a separate action someone needs to take.
- **`docker-healthcheck` job fix**: its `make docker-build-dsp-api-image` call ran bare (no `nix
  develop`) because that target used to be sbt. Now that 6a made it a Bazel target, the bare call
  would break (`bazel: command not found`) — wrapped under `nix develop --command`. A full sweep of
  every bare `make docker-*`/`make docker-publish*` call across all workflows confirmed this was
  the **only** genuine break; every other bare call either stays non-Bazel (fuseki buildx,
  `docker-image-tag` — now plain git+awk, no sbt either) or was already under `nix develop`.
- **New temporary job `check-docker-image-tag`**: runs `make check-docker-image-tag` on every PR,
  using `preparation`'s `fetch-depth: 0` checkout (full history + tags). This isn't about the two
  *sides* disagreeing (they run the identical `git describe`, so they can't) — it's that without
  tags, `git describe --tag --always` degenerates to the bare short-hash fallback on **both** sides
  identically, which exercises only the weakest code path and proves nothing about whether the
  gate would catch a real transcription bug in the tag-suffix/`+`→`-` logic. `fetch-depth: 0` is
  what makes this a real test of that logic rather than a vacuous one. Remove this job once the
  validation window closes and the tag-source switch is trusted.
- **`preparation` action's `java-version` default bumped `"21"` → `"25"`.** Affects the **four**
  workflows that call it without an explicit version: `docker-publish.yml`, `publish-release.yml`,
  `publish-from-branch.yml`, and `publish-docs.yml` (confirmed via a full grep of every workflow —
  `create-release.yml` and `scala-steward.yml` don't call `preparation` unversioned and don't touch
  any rewired Make target, so they're unaffected). **The bump is invisible until merged** — every
  caller references `dasch-swiss/dsp-api/.github/actions/preparation@main`, so a PR that only edits
  the composite action doesn't exercise the new default from a PR checkout.
- **Publish workflows needed no changes at all.** `docker-publish.yml`/`publish-release.yml`/
  `publish-from-branch.yml` already ran `nix develop --command make docker-publish`; once that
  target's sub-targets became Bazel (6a), the invocation shape was unchanged. `docker-publish.yml`'s
  Jenkins tag-output step (`make docker-image-tag`, bare) also kept working unmodified — it's now
  pure git+awk instead of sbt, which is strictly less fragile bare, not more.

**Items that could only be validated by pushing to CI (not locally) — watch the first real PR run:**

1. Whether a failing spec's coarse `<testcase>` actually carries a `<failure>` element (only
   matters if a dorny check-run name, not the job name, is a required status check).
2. `oci_push` authentication against the runner's `~/.docker/config.json` (a credential-helper
   configuration would break this; GitHub-hosted runners normally write inline base64 auth via
   `docker login`, so expected to work, but unverified in this repo's actual runner image).
3. The `preparation@main` java bump's effect on `publish-docs.yml`'s docs build (expected fine —
   it's a `just`/mkdocs pipeline with no visible Java dependency — but now technically in the
   blast radius of the version bump).
4. **CI wall-time will increase.** `magic-nix-cache-action` persists only the Nix store, not the
   Bazel output base or the Maven repository cache, so every Bazel CI job compiles cold (no
   `cache: sbt`-equivalent exists yet for Bazel in this repo). A slower first run is expected, not a
   regression to investigate. **Follow-up, out of this phase's scope:** wire a Bazel `--disk_cache`
   via `actions/cache`, or a remote cache.

**Real bug this surfaced, found and fixed after the first PR push:** `//modules/ingest:test`
failed on the Linux CI runner (`AssetFilenameSpec` — both a legitimate international filename and
the deliberately-invalid emoji case threw `java.nio.file.InvalidPathException: Malformed input or
input contains unmappable characters`), even though the identical test passed under sbt on the
same commit/runner just before this phase. Root cause: `.bazelrc`'s pre-existing
`--incompatible_strict_action_env` (added for hermetic C++/LLVM builds) strips almost every host
env var from Bazel's actions, including test execution — only `PATH` and one build var were
explicitly re-added via `--action_env`. On Linux, the JVM's native filesystem encoding
(`sun.jnu.encoding`) is derived from `LANG`/`LC_ALL`; stripped, it falls back to POSIX/ASCII, so
`Paths.get(...)` throws on **any** non-ASCII filename — not just invalid ones. sbt's forked JVM
never hit this because it inherits the full host environment (the Actions runner sets a UTF-8
locale). **This is a test-sandbox-only gap, not a production bug**: verified directly that the
runtime base image (`eclipse-temurin:25-jre-noble`) sets `LANG=en_US.UTF-8` and reports
`sun.jnu.encoding=UTF-8` via a plain `docker run`, so the actual dsp-ingest container is unaffected.
Confirmed the exact mechanism with a throwaway Linux repro (a bare `Paths.get(...)` call run twice
in a `eclipse-temurin:25-jre-noble` container with `env -i`): without `LANG`/`LC_ALL` it reports
`sun.jnu.encoding=ANSI_X3.4-1968` and throws on both a legitimate accented filename and the emoji;
with `LC_ALL=C.UTF-8`/`LANG=C.UTF-8` added it reports `UTF-8` and both round-trip correctly (the
emoji then falls through to the regex check exactly as designed). **Do not "fix" this by catching
`InvalidPathException` in `AssetFilename.from`** — that was the first fix attempted and is wrong:
it would silently reject every legitimate non-ASCII filename (German umlauts, Kanji, Arabic, etc. —
`AssetFilenameSpec` explicitly requires these to be *accepted*) whenever the JVM lacks a UTF-8
locale, masking the real problem instead of fixing it. Fixed by adding
`test --test_env=LANG=C.UTF-8` and `test --test_env=LC_ALL=C.UTF-8` to `.bazelrc` (`C.UTF-8` is a
glibc-builtin locale, no `locale-gen` needed) — restores the same environment sbt's forked JVM
already had, for every Bazel test target, not just ingest's.

**Manual coordination needed outside the repo tree (GitHub branch-protection settings):**
remove the now-deleted `Upload coverage` required check if it was ever marked required, and check
whether any required check references a dorny check-run name rather than a job name — if so, prefer
switching it to the job name so the coarse-XML rendering (above) stays cosmetic rather than gating.

### Phase 7 — Remote build execution (RBE) + CI on `just` ✅ COMPLETE (cache + executor, all green in CI)

Closes the Phase 6 "wire a Bazel disk/remote cache for CI" follow-up. Points dsp-api's Bazel CI at
sipi's shared **NativeLink** backend (`dasch-remotebuild-prod-01`, `:50051`, mTLS) for a remote cache
(remote executor deferred to Stage 2), and makes `just` the sole CI/build entry point (the Makefile is
now **deprecated**). Full rationale + the staged rollout live in `docs/development/dsp-api-rbe.md`.

- **`.bazelrc`**: added backend-agnostic tuning flags (safe no-ops without a remote) —
  `--remote_download_toplevel`, `--remote_local_fallback`, `--remote_timeout=600s`,
  `--remote_max_connections=200`, `--repository_cache=~/.cache/bazel-repo`, `test --test_output=errors`,
  `try-import %workspace%/user.bazelrc`. **No `ci.bazelrc`** — connection/cert flags are threaded per
  CI step, not baked into the rc file (Bazel doesn't expand env vars there).
- **`.github/actions/bazel-rbe/action.yml`** (new): ported from sipi, simplified (single-arch, no
  cross-leg machinery). Writes mTLS certs to `$RUNNER_TEMP/.nl/`, emits a `flags` step-output. A
  `stage` input (`cache`|`full`) selects cache-only vs. cache+executor. Fork PRs (no secrets) → empty
  flags → cold local build. Org secrets/vars `REMOTEBUILD_{RUNNER_ENDPOINT,CA_CERT,CLIENT_CERT,CLIENT_KEY}`.
- **`justfile`**: every bazel recipe takes `*FLAGS=''` and appends `{{FLAGS}}`; prereqs pass FLAGS
  through (`test-it *FLAGS='': (docker-load-test-images FLAGS)`). New `test-unit` (all pure-JVM
  modules). Ported the build/publish/image-tag/check surface from the Makefile (canonical home now).
- **Makefile deprecated**: removed the test/publish/tag/check targets (now in `just`); a
  deprecation banner + the entangled `docker-build*`/`docker-image-tag` cluster and the local-dev
  targets (`stack-*`, `init-db-*`, `clean-*`) remain until a follow-up ports them and deletes the file.
- **CI**: every workflow step runs `nix develop --command just <recipe> ${{ steps.rbe.outputs.flags }}`
  (fuseki-publish uses `extractions/setup-just`, no Nix). Each Bazel job gained an `actions/cache`
  (`~/.cache/bazel-repo`, keyed on `MODULE.bazel.lock`) + `bazel-rbe` step. **The 4 pure-JVM unit
  jobs merged into one `unit-tests` job** (`just test-unit`, one consolidated "Unit Test Results"
  dorny report); the 3 Docker jobs stay separate (they're `exclusive`). Docker test targets already
  carried `no-remote` (run local, no stale-pass poisoning); added the RBE rationale to the `_TAGS`
  comment.
- **Verified in CI (PR #4187, all green)**: cold first run green, then a same-SHA re-run cut the
  Bazel jobs 2-3x (Unit tests 12m30s→3m54s, IT 19m→9m, E2E 18m→10m, healthcheck 13m→6.6m) — proves
  the remote cache connects over mTLS and serves compile actions, not just `--remote_local_fallback`.
  Commits `2dd5e8e56` (Phase 7) + `2fa83c158` (fix). **Gotcha that cost the first run** (worth
  remembering): do **not** put a `${{ }}` expression in a composite action's `description:` — GitHub
  evaluates `${{ }}` everywhere in the manifest, so a stray `${{ steps.rbe... }}` (a workflow-step id,
  invalid in the action's own context) makes the whole action fail to load ("Unrecognized named-value:
  'steps'"). The `./`-local action reference works fine on a PR (resolves from checkout — it does NOT
  need to be on main, contrary to first assumption).
- **Stage 2 (executor) — DONE, green** (commits `999b822c9` spike, `705541c34` rollout). The
  unit-tests spike proved rules_scala compiles + JVM unit-test execution run on the NativeLink worker
  (1483/2464 actions remote, all tests pass) — the make-or-break unknown, resolved. Rolled `stage: full`
  to all 5 build-and-test Bazel jobs; the image-build-on-worker → local-load path works (healthcheck
  loaded both knora-api + knora-sipi). Docker/testcontainer test targets stay local via `no-remote`
  (the small "N local" counts in each job's process summary).
- **Publish workflows deliberately stay on `stage: cache`** (release path): local multi-arch image
  assembly on the x86_64 runner is already proven and the remote cache gives the cross-run win; remote
  arm64 assembly on the worker is unproven and the executor speedup doesn't justify the risk on
  infrequent real Docker Hub pushes. Flip to `full` later only if wanted.
- **Branch protection (manual)**: merging the unit jobs renames checks (4 → 1 "Unit Test Results",
  job `unit-tests`); update any required check referencing the old names (`Build and test`,
  `Test bagit`/`Test jwt`/`Test shacl-validator`).

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
     `:latest`/pinned images preloaded first: the `just test-*` recipes do this via their
     `docker-load-test-images` prerequisite (or `bazel run //modules/{sipi,ingest,fuseki}:load` directly).
   - `nix develop --command bazel build //modules/fuseki:image_amd64` (fuseki image builds)
   - `just check-docker-image-tag` (sbt⇄workspace_status tag-drift gate, temporary — see Phase 6)
5. **Migration is code-complete (Phases 0–6); Phase 7 (RBE + CI on `just`) Stage 1 is landed** (see
   above). Remaining work is operational, not code: watch the first real CI run for the Phase 6/7
   push-only risks, do the manual branch-protection coordination, and run the Phase 7 Stage 2 spike
   (flip `stage: cache` → `full`). The `just` recipes are now the entry point; the **Makefile is
   deprecated**. Other natural increments: retire the sbt-parallel paths once the validation window
   closes (remove `check-docker-image-tag`, drop the fuseki-publish Dockerfile/buildx path, remove
   `./sbtx` entirely), and finish deleting the Makefile (port its remaining local-dev targets to `just`).

## Useful commands

```sh
# sbt (build of record) — compile a module (note the hyphenated/val id differences)
./sbtx "webapi/compile"
./sbtx "shacl-validator/compile"   ./sbtx "ingestIntegration/Test/compile"

# Bazel
bazel run @unpinned_maven//:pin          # re-pin maven after changing deps
bazel build //tools/buildinfo:smoke      # verify buildinfo + stamping
bazel build //modules/sipi:image_amd64   # sipi image (regression check)
just docker-build-sipi-image             # sipi image via just (bazel under the hood)

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

# Phase 6/7 — CI drives Bazel via `just` (Makefile deprecated); docker-compose has healthchecks too
just docker-image-tag                # git-describe tag, no sbt (was: sbt "print dockerImageTag")
just check-docker-image-tag          # temporary drift gate: workspace_status.sh vs sbt, byte-match
just docker-build-dsp-api-image      # bazel run //modules/webapi:load + docker tag (mirrors sipi)
just docker-load-test-images         # preload sipi+ingest+fuseki :latest/pinned for IT/E2E tests
just test-unit test-it test-e2e test-ingest-integration   # all bazel test now, no coverage wrapping
docker compose up --wait db sipi ingest api          # all four now have healthcheck: blocks

# Phase 7 — RBE flags are injected by CI only (bazel-rbe action → just <recipe> "$FLAGS"); local dev
# never contacts the backend. See docs/development/dsp-api-rbe.md.
```
