# Spike findings: dsp-app under frontend/

The spike behind the plan's "Spike results" section (DEV-7930 step 1). Throwaway branch, never pushed.

**Inputs:** dsp-app `main` at `a0283b09e`, dsp-api `main` at `0c8159eb8`, `git filter-repo` `a40bce548d2c`. The merged tree runs Bazel 9.3.1 (DEV-7931's version); dsp-app's baseline runs its own 9.3.1. macOS arm64, local execution, no remote cache or executor.

## Verdict

Nothing needs upstream work. Every breakage is fixed on the integration branch with the edits listed under "In-branch fixes", and no module raise breaks a build or test, so the spike asks for no pre-alignment PR. The cutover date is not affected by the spike.

## Results

| Check | dsp-app `main` | Merged tree |
| --- | --- | --- |
| `bazel build //...` (app) | green | green (`//frontend/...`) |
| `bazel test //...` (app) | 26 of 26 pass | 26 of 26 pass |
| `bazel query 'tests(//...)'` (app) | 27 | 27 |
| `bazel build --config=lint` (app) | green | green |
| `manual` targets: `storybook`, `storybook_breakpoints`, `dsp-app:production`, `dsp-app:development`, `dsp-js:npm_package` | green | green after fix 4 |
| `app_transitive_typecheck_test` | pass | pass |
| Production bundle `//apps/dsp-app:dist` | 170 files | 170 files, identical after normalising chunk hash names and the `frontend/` path |
| `bazel build //...` (whole repo, API included) | | green |
| `just test-unit` (API) | | 7 of 7 pass |

The bundle's chunk names change once (`main-JZZIWH25.js` becomes `main-AOKXOAIL.js`): esbuild hashes source paths into them. Content and total size (8,333,965 bytes of JS) are unchanged, so the only effect is one browser-cache refresh at the first release from dsp-api.

## Import (Phase 1)

- `filter-repo --to-subdirectory-filter frontend --tag-rename v:dsp-app-v --replace-message` runs in 26 s on a fresh clone. No bare `#NNN` remains in imported messages (count 0).
- Tags after the rewrite: 303 `dsp-app-v*`, 2 `dsp-js-v*`, and one bare `1.2.0`, which the script's fetch refspecs drop. Six `dsp-app-v3.0.0-rc.N` tags match the plan's tag check only because its regex has no `$`; keep it that way.
- The merge is clean: `git diff --stat refs/import/dsp-app HEAD^2` and `git diff --stat origin/main HEAD^1` are both empty.

## Module graph

`bazel mod graph` before (each repo's `main`) and after (merged tree). 16 shared modules differ; the merge raises 8 on each side. None of the raises breaks a build or test above, so all stay in Phase 2.

| Module | dsp-app | dsp-api | Merged | Raised on |
| --- | --- | --- | --- | --- |
| `apple_support` | 2.5.2 | 1.24.2 | 2.5.2 | API |
| `aspect_bazel_lib` | 2.22.5 | 2.21.1 | 2.22.5 | API |
| `bazel_features` | 1.42.1 | 1.50.0 | 1.50.0 | app |
| `bazel_lib` | 3.7.2 | 3.2.2 | 3.7.2 | API |
| `bazel_skylib` | 1.9.0 | 1.8.2 | 1.9.0 | API |
| `gazelle` | 0.51.3 | 0.47.0 | 0.51.3 | API |
| `jq.bzl` | 0.6.1 | 0.1.0 | 0.6.1 | API |
| `package_metadata` | 0.0.6 | 0.0.7 | 0.0.7 | app |
| `rules_android` | 0.1.1 | 0.7.1 | 0.7.1 | app |
| `rules_cc` | 0.2.17 | 0.2.20 | 0.2.20 | app |
| `rules_go` | 0.62.0 | 0.59.0 | 0.62.0 | API |
| `rules_java` | 9.1.0 | 9.3.0 | 9.3.0 | app |
| `rules_jvm_external` | 6.7 | 7.0 | 7.0 | app |
| `rules_kotlin` | 1.9.6 | 2.2.2 | 2.2.2 | app |
| `rules_pkg` | 1.0.1 | 1.2.0 | 1.2.0 | app |
| `yq.bzl` | 0.3.6 | 0.1.1 | 0.3.6 | API |

## Toolchains

`bazel cquery //... --toolchain_resolution_debug='.*'` over the app's targets, dsp-app `main` against the merged tree. Two toolchain types change; Node, esbuild, Sass, the JDK, coreutils, tar, jq, crane and regctl resolve to the same toolchains.

- **C/C++:** the host's autodetected `local_config_cc` becomes the hermetic `llvm` toolchain. The app compiles one C program, `gawk`, which `tar.bzl`'s mtree step pulls into `//apps/dsp-app/image:dist_mtree_public`. It builds with LLVM on macOS, and `bazel build @@gawk+//:gawk --platforms=//platforms:linux_x86_64` cross-compiles it to a Linux x86-64 ELF. The API never compiled `gawk` before, so the first Linux and RBE run is on the integration PR.
- **Python:** 3.11 becomes 3.12, dsp-api's `python.toolchain` pin.
- **`rules_go`:** no app target resolves a Go toolchain; `rules_go` is in the graph only through `aspect_rules_lint`.
- **`rules_angular`:** registers no toolchain. Its `git_override` patch applies from `//frontend/tools/angular:rules_angular-nativelink.patch`.

## dsp-api's global flags on the app

No effect observed: `--incompatible_disallow_empty_glob`, `--cxxopt=-std=c++17` (`gawk` is C), `--action_env=BAZEL_DO_NOT_DETECT_CPP_TOOLCHAIN=1` (the cause of the C/C++ change above, intended) and `build:macos --incompatible_autoload_externally` all build and test green.

## In-branch fixes

The fixes Phase 2 applies, besides the plan's `MODULE.bazel`, `.bazelrc` and `.bazelignore` merge. The plan's Phase 2 carries each of them as a checkbox.

1. **Labels.** 538 lines in 41 `BUILD.bazel` and `.bzl` files: `"//:` to `"//frontend:` and `"//{apps,libs,tools,nginx,mocks}` to `"//frontend/…`. Four more sit inside make variables without a leading quote (`$(execpath //:node_modules/openseadragon/dir)`, three `$(execpath //nginx:…)` in `apps/dsp-app/image/BUILD.bazel`). The plan's completeness check `git grep -nE '//(:|apps/|libs/|tools/(angular|openapi|lint))' -- frontend` misses the six `//nginx` labels; widen it to `nginx/` and `mocks/`.
2. **npm loads.** rules_js generates `package_json.bzl` under the pnpm importer's path, now `frontend`: `@npm//:@angular/cli/package_json.bzl` and `@npm//:eslint/package_json.bzl` become `@npm//frontend:…`. `@npm//:defs.bzl` stays at the repository root of `@npm`.
3. **Path logic in `frontend/tools/angular/defs.bzl`,** which derives names from `label.package` and `native.package_name()`:
    - `_alias` strips `frontend/libs/` instead of `libs/`, and `_is_library` tests `frontend/libs/`;
    - the generated tsconfig `paths` (in `_bazel_tsconfig` and the `testing` entry point's `extra_paths`) strip `frontend/`, since they resolve against `frontend/tsconfig.base.json`'s `baseUrl`;
    - the esbuild alias for `@dasch-swiss/dsp-js` becomes `./frontend/libs/dsp-js/src/index.js` and the generated config imports `frontend/tools/angular/esbuild.config.mjs`; the other aliases are already correct, since esbuild runs in the bin root.
4. **Storybook.** `ng_bin.ng` in `frontend/BUILD.bazel` runs in the bin root and no longer finds `.storybook/` or `angular.json` (`MainFileMissingError`); `chdir = package_name()` fixes both targets. Only the `manual` targets catch it: `bazel build //...` stays green without the fix.
5. **TZ.** `env = dict({"TZ": "Europe/Zurich"}, **kwargs.pop("env", {}))` on `jest_test` in `vre_jest_test` replaces the app's `test --test_env=TZ`; all 26 tests pass with it.
6. **`MODULE.bazel`.** The root raises its own `platforms` 1.0.0 to 1.1.0 and `bazel_skylib` 1.8.2 to 1.9.0; the app section reuses the root's `oci` extension proxy and `http_file` repo rule. `MODULE.bazel.lock` regenerates with 631 lines added and 17 removed.
7. **Platforms.** `frontend/platforms/` moves to the root `platforms/` (DEV-7932's package); no app `BUILD.bazel` references it.

## Phase 3 and 5 checks

- `just -f frontend/justfile` runs recipes in `frontend/`: `node-version-check` fails on the missing `MODULE.bazel` and reads the root pin once changed to `../MODULE.bazel`, as Phase 3 plans.
- Both repos stamp `STABLE_GIT_VERSION`, with different `git describe` patterns. Phase 5's separate `STABLE_APP_GIT_VERSION` is required, not optional.

## Not covered

- Linux, RBE and NativeLink: the integration PR's CI is the first run.
- API integration and E2E tests, the app's Cypress E2E, and stamped or pushed images.
- The Nix dev shells and direnv (no Nix in the spike's shell), and the justfile recipes outside Bazel (`knip`, `visibility-check`, `tsconfig-paths-check`, `test-files`).

**Environment note:** `oci.pull` of nginx fails on a Mac whose Docker credential helper fails, on dsp-app `main` as much as in the merged tree. An empty `DOCKER_CONFIG` (anonymous pulls) through `user.bazelrc` works around it; the merge does not cause it.
