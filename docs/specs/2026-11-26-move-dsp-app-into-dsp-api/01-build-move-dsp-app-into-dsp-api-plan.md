---
title: "build: Move dsp-app into dsp-api under frontend/"
type: build
date: 2026-11-26
author: "Marcin Procyk"
status: reviewed
linear: DEV-7930
linear_project: move-dsp-app-into-the-dsp-api-repository-36d51c6894df
---

# build: Move dsp-app into dsp-api under frontend/

## Overview

dsp-app's repository root, with its full history, becomes `frontend/` in dsp-api. Inside `frontend/` nothing moves: `apps/dsp-app` becomes `frontend/apps/dsp-app`, `libs/dsp-js` becomes `frontend/libs/dsp-js`. The API side keeps every path. The root gets one Bazel module graph, one Nix flake, the app's CI and release workflows, and pointers for readers and agents.

The work is built as an **integration branch** in dsp-api: an import merge of dsp-app's `main` plus one integration commit per phase of this plan. A re-runnable script rebuilds the branch from the newest dsp-app `main` and dsp-api `main`, so dsp-app stays open until cutover day. The branch is a permanent draft PR for CI. On cutover day it is pushed to `main` through a one-off ruleset bypass, never merged with the button.

This plan covers the Move. The Payoff (dsp-api emits its OpenAPI spec from a Bazel target, the app client and E2E use the same commit) is a separate plan written when the Payoff issue starts; it needs the merged tree to exist.

## Problem Statement / Motivation

See the Linear project. In short: an API change and its client change cannot land as one change today. The app's vendored OpenAPI spec trails the API by a dev deploy, and the app's E2E tests run against dsp-api `main`, not against the API the PR needs.

## Proposed Solution

### Decisions (recorded in ADR-0015)

1. **Layout:** dsp-app's root becomes `frontend/`, a peer of `modules/`. Its internals, including its `CLAUDE.md`, `ARCH-MAP.md`, `CONTEXT.md`, `CONVENTIONS.md`, `REVIEW.md`, `mkdocs.yml` and `docs/adr/`, land under `frontend/` unchanged except for lines that assume the repository root. `frontend/eng.yaml` is the exception: the eng plugin reads one `eng.yaml` at the repository root, so its bindings move into the root file with `frontend/` globs.
2. **History:** imported with `git filter-repo --to-subdirectory-filter frontend`. Imported commit messages get `#NNN` rewritten to `dasch-swiss/dsp-app#NNN` (2,879 references on `origin/main` today), so links keep pointing at dsp-app PRs, not at unrelated dsp-api PRs with the same number. The rewrite is deterministic, so re-runs still give identical SHAs.
3. **Tags:** dsp-app's `v*` tags are imported renamed to `dsp-app-v*` (`--tag-rename v:dsp-app-v`); `dsp-js-v*` tags are imported unchanged; all other tags are dropped. The 46 bare `v*` names that exist in both repos stay the API's.
4. **One merge commit:** the import is one `--allow-unrelated-histories` merge, first parent dsp-api `main`. dsp-app history has 78 merge commits, so replaying it linearly would rewrite it; `git log --first-parent` and `git bisect --first-parent` keep the API's view linear.
5. **Landing:** a temporary bypass actor on the `main` ruleset and a fast-forward push of the integration branch. The ruleset requires linear history, PRs, rebase-only and five checks, and has no bypass actor today; the repository has merge commits disabled.
6. **Checks:** required checks are scoped by path with a job-level `if:` (DEV-8062); the app's required job names stay as they are (`DSP-APP Unit tests`, `DSP-APP Linting`, `DSP-APP E2E tests`, `Docs Build Test`) and join the ruleset after cutover.
7. **Releases:** release-please components; the app's tags are `dsp-app-v*`, the image stays `daschswiss/dsp-app:v14.x` (DEV-8063).

### The import script

`assets/import-dsp-app.sh` in this folder builds and rebuilds the integration branch. It lives on the integration branch as part of the Phase 1 commit; a re-run executes a copy taken from the previous branch tip.

```bash
#!/usr/bin/env bash
# Rebuild the integration branch: import the newest dsp-app main into frontend/,
# merge it into a branch cut from dsp-api main, replay the integration commits.
# Usage: import-dsp-app.sh <integration-branch>   (run from a clean dsp-api checkout)
set -euo pipefail
branch="$1"
lock_subject='build(build-toolchain): regenerate MODULE.bazel.lock after the frontend import'
git filter-repo --version | grep -qx "$FILTER_REPO_VERSION"   # pinned; output must not drift between runs
work="$(mktemp -d)"
git clone --single-branch --branch main https://github.com/dasch-swiss/dsp-app.git "$work/app"
printf '%s\n' 'regex:(?<![\w/-])#([0-9]+)==>dasch-swiss/dsp-app#\1' > "$work/messages.txt"
git -C "$work/app" filter-repo --force \
  --to-subdirectory-filter frontend \
  --tag-rename 'v:dsp-app-v' \
  --replace-message "$work/messages.txt"
git fetch origin main
if git ls-remote --exit-code --heads origin "$branch" >/dev/null; then
  git fetch origin "$branch"
  old_tip="$(git rev-parse "origin/$branch")"
else
  old_tip=""
fi
git fetch --no-tags "$work/app" main:refs/import/dsp-app \
  'refs/tags/dsp-app-v*:refs/tags/dsp-app-v*' 'refs/tags/dsp-js-v*:refs/tags/dsp-js-v*'
git checkout -B "$branch" origin/main
git -c rerere.enabled=true merge --no-ff --allow-unrelated-histories refs/import/dsp-app \
  -m "build(frontend): import the dsp-app history into frontend/"
if [ -n "$old_tip" ]; then
  # The import merge is the only merge between dsp-api main and the old tip.
  [ "$(git rev-list --count --merges --first-parent "origin/main..$old_tip")" -eq 1 ] \
    || { echo "expected exactly one merge on $branch" >&2; exit 1; }
  merges="$(git rev-list --merges --first-parent "origin/main..$old_tip")"
  # Integration commits only; the lockfile commit is regenerated, never replayed.
  while read -r c; do
    [ "$(git log -1 --format=%s "$c")" = "$lock_subject" ] && continue
    git -c rerere.enabled=true cherry-pick "$c"
  done < <(git rev-list --reverse --no-merges "$merges..$old_tip")
fi
bazel mod deps --lockfile_mode=update
git add MODULE.bazel.lock
git commit -m "$lock_subject"
# The replayed rewrites cover only files that existed at the last run; app files added since must be caught here.
! git grep -nE '(^|[^[:alnum:]@:/])//(:|apps/|libs/|tools/|nginx[:/]|mocks[:/])' -- 'frontend/*BUILD.bazel' 'frontend/*.bzl' \
  || { echo "stale app labels: fix them in a fix-up commit" >&2; exit 1; }
for c in frontend frontend/libs/dsp-js; do
  [ "$c" = frontend ] && key="." || key="libs/dsp-js"   # the package's path in dsp-app's own manifest
  app="$(git show "refs/import/dsp-app:frontend/.release-please-manifest.json" | jq -r --arg k "$key" '.[$k]')"
  api="$(jq -r --arg k "$c" '.[$k] // empty' .github/release-please/manifest.json)"
  [ -z "$api" ] || [ "$app" = "$api" ] || { echo "manifest $c: dsp-app $app, root $api" >&2; exit 1; }
done
echo "Review, then: git push --force-with-lease origin $branch"
```

The snippet shows the shape; Phase 1 owns the final version. A cherry-pick conflict stops the script. The usual cause is a root file that changed on dsp-api `main` (`MODULE.bazel`, `build-and-test.yml`, `justfile`, `CLAUDE.md`, `ARCH-MAP.md`); resolve it, `git cherry-pick --continue`, and rerere remembers the resolution for the next run. A modify/delete conflict means dsp-app changed a file an integration commit deletes (a release bump of `.release-please-manifest.json`, a freeze breach in `ci.yml` or `MODULE.bazel`): rerere does not record those, so carry the change to the file that replaced it (the root manifest, `app-ci.yml`, the root `MODULE.bazel`), `git rm` the file, and continue. The lockfile is regenerated on every run because it would conflict on every dsp-api dependency change. The checks at the end catch what a replay cannot: Bazel labels in app files added since the last run, and a dsp-app release that moved its manifest past the root manifest's entry.

### Cutover day

A Thursday, the day after the weekly prod deployment, so no release is in flight. The Human Actions rows H5 to H11 are the runbook, in order; H12 follows when the first `dsp-js` release is due. Monday's release cut is the first one from dsp-api; dsp-app is archived after that release reaches prod.

**Go or no-go after the push:** the first `main` run of every required check is green; release-please opens or updates the expected release PRs (API, `dsp-app`, `dsp-js`), each without the other components' commits; and `app-deploy.yml`, which can run only from the default branch, publishes the app image and triggers DEV. If any fails and no fix lands the same day, roll back: re-add the bypass actor, `git push --force-with-lease=main:<pushed-sha> origin <pre-push-sha>:main`, delete the pushed `dsp-app-v*` and `dsp-js-v*` tags, close the release-please PRs and delete the branches and GitHub Releases its first run created for `dsp-app` and `dsp-js`, re-enable dsp-app's scheduled workflows and unlock it. The app image and DEV deploy that run produced hold the frozen dsp-app tree, so they need no rollback. Auto-merge stays disabled until the decision (H5), so no bot PR lands on top of the push and nothing else is lost.

**App hotfix between cutover and the first Monday cut:** the fix merges to dsp-api `main` like any PR, then the `dsp-app` release PR is merged off-cycle. Its `dsp-app-v*` tag publishes the image through `app-deploy.yml`, and ops-deploy deploys it as before.

### Companion changes outside this plan

- **ops-deploy** (DEV-8063): `config/GITHUB_DEPLOYMENTS_CONFIG.json` maps `app` to `{ "repository": "dasch-swiss/dsp-api", "environment_prefix": "app-", "ref_prefix": "dsp-app-" }`, and the example in `config/README.md` follows. `ref_prefix` turns the deployed version `v14.x` into the tag `dsp-app-v14.x`; without it the GitHub Deployment would point at an API tag. The PR is prepared before cutover and merged after the push (H9). It is executed from DEV-8063 directly, not from this plan: listing ops-deploy under `repositories:` would make `eng:eng-work` treat it as the target repository, and dsp-api's spec convention forbids listing dsp-api itself.
- **dsp-app:** a README notice pointing at `dasch-swiss/dsp-api/frontend`, committed after the final import run (H10).
- **References in other repositories** (dasch-claude-plugins, dasch-specs, dsp-docs, dsp-tools): updated after cutover, tracked in the Move issue.

## Alternative Approaches Considered

| Option | Rejected because |
| --- | --- |
| API and app as equal peers (`api/`, `app/`) | Renames every API path (justfile `scalafmt_targets`, CI, `.bazelproject`, license and lint target lists, ARCH-MAP globs, eng.yaml, docs) and conflicts every open dsp-api PR on cutover day, for no functional gain. |
| dsp-app's folders flat at the dsp-api root | Keeps the labels, but collides on `tools/lint`, `tools/workspace_status.sh`, `k6/`, `platforms/`, `docs/adr/` (two series from 0001), every agent file, `justfile`, `flake.nix`, `mkdocs.yml` and `CHANGELOG.md`. |
| Flatten `frontend/apps/dsp-app` | Rewrites `angular.json`, 30 `tsconfig.base.json` aliases and `tools/angular/visibility.bzl` for looks, and breaks `git apply --directory=frontend` for PRs carried over at the freeze. |
| Fresh copy without history (as for dsp-js in 2026-03) | Loses `git blame` and `git log` for 3,424 app commits; the import costs one bypassed push. |
| Linear rebase of the app history onto `main` | Rewrites 78 merge commits, changes every SHA on each run (the script's replay relies on stable SHAs), and puts 3,424 commits into `git log --first-parent`. |
| Long-lived integration branch maintained by hand | Drifts from both `main`s for weeks; the script makes the branch disposable. |
| One dev shell for both stacks | The stacks need different `nixpkgs` pins (the app's Playwright and Node pins are newer than dsp-api's `nixpkgs` provides), and every API CI job would download Node, pnpm and the Playwright browsers; a separate `frontend` shell keeps the API shell as it is. |

## Technical Considerations

### Spike results

The spike (H1) imported dsp-app `a0283b09e` into dsp-api `0c8159eb8` on Bazel 9.3.1, macOS, local execution; `assets/spike-findings.md` holds the evidence. Nothing needs upstream work and nothing needs a pre-alignment PR: every breakage is fixed on the integration branch by a Phase 2 or Phase 3 item.

- **Module graph:** 16 shared modules resolve to different versions and the merge raises 8 on each side. No raise breaks a build or test, so all stay in Phase 2.
- **Toolchains:** two toolchain types change. C/C++ moves from the host's `local_config_cc` to the hermetic `llvm`, for one program: `gawk`, which `tar.bzl`'s mtree step builds for the app image. Python moves from 3.11 to 3.12. No app target resolves a Go toolchain; esbuild, Node, Sass and the JDK resolve as before; `rules_angular` registers none, and its NativeLink patch applies from `//frontend/tools/angular`.
- **dsp-api's global flags on the app:** no effect; `BAZEL_DO_NOT_DETECT_CPP_TOOLCHAIN=1` causes the C/C++ change above, as intended.
- **Off the root:** after the Phase 2 fixes the app builds, lints and passes all its tests from `frontend/`, its `manual` targets included. The production bundle is unchanged except for the chunk hash names, which esbuild derives from source paths, so browsers refetch the bundle once at the first release from dsp-api.
- **Commands from `frontend/`:** `just -f frontend/justfile` runs recipes in `frontend/`; `node-version-check` needs the `../MODULE.bazel` path Phase 3 sets. direnv and the Nix shells did not run (no Nix in the spike's shell); Phase 3 checks them.
- **Workspace status:** both repos stamp `STABLE_GIT_VERSION`, with different `git describe` patterns, so Phase 5's separate `STABLE_APP_GIT_VERSION` is required.
- **Not covered, first run on the integration PR:** Linux, RBE and NativeLink (including `gawk` built by LLVM for Linux), the API's integration and E2E tests, the app's Cypress E2E, and stamped or pushed images.
- **Local note:** `oci.pull` of nginx fails on a Mac whose Docker credential helper fails, on dsp-app `main` as well; an empty `DOCKER_CONFIG` set through `user.bazelrc` works around it.

### Bazel

- The root `MODULE.bazel` declares every app `bazel_dep`, `git_override`, extension and `oci.pull` with root-relative labels; shared deps are declared once at the higher version. The npm and Angular extensions point at `frontend/`:

```starlark
npm.npm_translate_lock(
    name = "npm",
    data = ["//frontend:package.json", "//frontend:pnpm-workspace.yaml"],
    npmrc = "//frontend:.npmrc",
    pnpm_lock = "//frontend:pnpm-lock.yaml",
)
rules_angular.setup(
    angular_compiler_cli = "//frontend:node_modules/@angular/compiler-cli",
    typescript = "//frontend:node_modules/typescript",
)
```

- `npm_link_all_packages` stays in `frontend/BUILD.bazel`, the package of the pnpm lockfile, so `//frontend:node_modules/...` replaces `//:node_modules/...`.
- `.bazelrc`: union of both files, each identical line once. `test --test_env=TZ=Europe/Zurich` moves into the app's Jest macro (`env` in `frontend/tools/angular/defs.bzl`), so the API's tests keep their environment. The developer disk cache is dsp-api's shared `~/.cache/bazel-disk` (20 GB, 30 days); the app's separate `~/.cache/bazel-disk/dsp-app` goes (decided on DEV-7932). `build:lint` keeps its name with the aspect relabelled to `//frontend/tools/lint:linters.bzl%eslint`.
- `//platforms:linux_x86_64` is the root package DEV-7932 creates, so `frontend/platforms/` is deleted and its label does not change.
- One `--workspace_status_command` serves the whole workspace: the root `tools/workspace_status.sh` emits the API's `STABLE_GIT_VERSION` and a new `STABLE_APP_GIT_VERSION` (Phase 5).
- `.bazelrc` passes `PATH` into actions (`--action_env=PATH`), so switching between the root shell and the `frontend` shell on one machine discards Bazel's analysis cache once per switch. Accepted; `frontend/README.md` says so (Phase 6).

### Nix

The `frontend` and `frontend-e2e` shells use their own `nixpkgs-frontend` input, locked at dsp-app's current `nixpkgs` revision, never `follows = "nixpkgs"`: dsp-api's `nixpkgs` has playwright-driver 1.60.0 and Node 24.15.0, while the app pins Playwright 1.61.1 and `engines.node ^24.20.0` with `engine-strict`. Each frontend shell takes every package from `nixpkgs-frontend`, so no shell mixes two pins, and the default shell stays byte-identical. The agent's shell has no Nix, so the shells are first evaluated by the app CI on the integration PR (Phase 4).

### Integration commits

Each phase lands as exactly one commit on the integration branch (fix-ups are folded in before the next script run), titled `type(scope): subject`. One commit per phase keeps the replay to six cherry-picks and gives `main` a readable record of the move. `commitlint` does not run on the draft PR, but the commits land on `main` and must pass `just commit-lint` anyway.

### CI on the integration PR

The PR changes `MODULE.bazel`, `.bazelrc`, `flake.nix` and `.github/`, so DEV-8062's change detection runs every API job and Phase 4's app classification runs every app job on it. The PR stays a draft through ship and until the cutover push, which skips the commit-count and commitlint steps of `Commit hygiene` that thousands of imported commits would fail. Marking it ready or merging it with the button is never the landing path.

### Accessibility

No control or view changes; the app's UI is moved, not modified.

## Implementation Phases

### Integration branch (dsp-api)

Every phase lands as one commit on the integration branch, in this order.

#### Phase 1: Import script and import merge

- [ ] Write `docs/specs/2026-11-26-move-dsp-app-into-dsp-api/assets/import-dsp-app.sh` as sketched in Proposed Solution, with `FILTER_REPO_VERSION` pinned to the version the first run uses, and `shellcheck` clean
- [ ] Run the script; the branch holds the import merge, `frontend/` contains dsp-app's `origin/main` tree byte for byte (`git diff --stat refs/import/dsp-app HEAD^2` empty), and the API tree is unchanged (`git diff --stat origin/main HEAD^1` empty)
- [ ] Every imported tag matches `^dsp-app-v[0-9]+\.[0-9]+\.[0-9]+` or `^dsp-js-v[0-9]+\.[0-9]+\.[0-9]+`, their count equals dsp-app's `v[0-9]*` plus `dsp-js-v*` tags, and the newest app release commit carries `dsp-app-v<the version in dsp-app's manifest>`
- [ ] Imported commit messages carry no bare `#NNN` (`git log --format=%B refs/import/dsp-app | grep -cE '(^|[^/[:alnum:]-])#[0-9]+'` returns 0)
- [ ] Run the script a second time against the same dsp-app `main`; `git rev-list refs/import/dsp-app | sha256sum` is identical for both runs (each run clones afresh into a new temporary directory)
- [ ] Open the integration branch as a draft PR titled `build(frontend): move dsp-app into dsp-api`, with the PR body stating that it is landed by push on cutover day and never merged with the button
- [ ] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

#### Phase 2: One Bazel module graph

**Gate: H3**: resolve before starting this phase.

- [ ] Merge the app's `bazel_dep`s, `git_override`s, extensions, `http_file` and `oci.pull` into the root `MODULE.bazel` with `//frontend` labels, each shared dep declared once at the higher version (this raises the root's own `platforms` to 1.1.0 and `bazel_skylib` to 1.9.0 unless DEV-7931 already has); the app's section reuses the root's `oci` extension proxy and `http_file` repo rule instead of declaring its own
- [ ] Merge `frontend/.bazelrc` into the root `.bazelrc` as described in Technical Considerations, with the `TZ` test environment moved into the Jest macro: `env = dict({"TZ": "Europe/Zurich"}, **kwargs.pop("env", {}))` on `jest_test` in `vre_jest_test`
- [ ] Rewrite the app's Bazel labels in every `BUILD.bazel` and `.bzl` under `frontend/`, comments included: `//:` to `//frontend:`, and `//apps`, `//libs`, `//tools`, `//nginx`, `//mocks` to their `//frontend/` forms, including labels inside make variables (`$(execpath //:node_modules/openseadragon/dir)`, the `$(execpath //nginx:…)` in `apps/dsp-app/image/BUILD.bazel`); `git grep -nE '(^|[^[:alnum:]@:/])//(:|apps/|libs/|tools/|nginx[:/]|mocks[:/])' -- 'frontend/*BUILD.bazel' 'frontend/*.bzl'` returns nothing (`@npm//:defs.bzl` does not match and stays)
- [ ] Load the npm packages' `package_json.bzl` from the pnpm importer's new path: `@npm//:@angular/cli/package_json.bzl` and `@npm//:eslint/package_json.bzl` become `@npm//frontend:…`
- [ ] In `frontend/tools/angular/defs.bzl`, which derives names from `label.package` and `native.package_name()`: `_alias` strips `frontend/libs/` and `_is_library` tests it; the generated tsconfig `paths` (`_bazel_tsconfig` and the `testing` entry point's `extra_paths`) strip `frontend/`, since they resolve against `frontend/tsconfig.base.json`'s `baseUrl`; the esbuild alias for `@dasch-swiss/dsp-js` becomes `./frontend/libs/dsp-js/src/index.js`, and the generated esbuild config imports `frontend/tools/angular/esbuild.config.mjs`
- [ ] Add `chdir = package_name()` to the Storybook `ng` targets in `frontend/BUILD.bazel`, so they find `.storybook/` and `angular.json`
- [ ] Add the app's ignored directories to the root `.bazelignore` (`frontend/node_modules`, `frontend/dist`, `frontend/coverage`, `frontend/tmp`, `frontend/.angular`, `frontend/.claude`, `frontend/.direnv`)
- [ ] Delete `frontend/MODULE.bazel`, `frontend/MODULE.bazel.lock`, `frontend/.bazelversion`, `frontend/.bazelrc`, `frontend/.bazelignore` and `frontend/platforms/`
- [ ] Exclude `frontend` from the bazel-bsp import in `.bazelproject`, so Metals keeps importing only `//modules/...`
- [ ] `bazel build //...` succeeds
- [ ] `bazel build //frontend:storybook //frontend:storybook_breakpoints //frontend/libs/dsp-js:npm_package` succeeds (`manual` targets, which `//...` skips; only they catch a missing Storybook `chdir`), and so does `bazel build --config=lint //frontend/...`
- [ ] `bazel test //frontend/...` passes, and `bazel query 'tests(//frontend/...)' | wc -l` equals `bazel query 'tests(//...)' | wc -l` run in dsp-app at the imported commit
- [ ] Run `just test-unit`; it passes
- [ ] `just metals-bootstrap` succeeds on the merged module graph
- [ ] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

#### Phase 3: Developer commands from frontend/

- [ ] Add the input `nixpkgs-frontend.url = "github:NixOS/nixpkgs/nixpkgs-unstable"` to the root `flake.nix`, without `follows`, and seed its `flake.lock` node with a copy of dsp-app's `nixpkgs` node (rev `c7def046…` today) via `jq`; `git diff origin/main -- flake.lock` adds only that node and leaves the `nixpkgs` node unchanged
- [ ] Add `devShells.frontend` and `devShells.frontend-e2e` to the root `flake.nix` as dsp-app's `default` and `e2e` shells are today, every package from `nixpkgs-frontend` (the `bazel` and `bazelisk` wrappers, `nodejs_24`, `pnpm_10`, `bazel-watcher`, `miniserve`, `uv`, `gh`, `yq-go`, `curl`, the mkdocs environment from `frontend/nix/mkdocs.nix`, `playwright-driver.browsers`, `agent-browser`, and the `PLAYWRIGHT_*` variables), not the default shell's package list, whose whole `bazelisk` shadows coreutils' `sha256sum`; the `bazel` wrapper gets the default shell's `TMPDIR=/tmp` prefix, since both shells now drive one Bazel server
- [ ] Replace `frontend/.envrc` with `use flake ..#frontend`, `watch_file ../flake.nix ../flake.lock nix/mkdocs.nix` and `source_env_if_exists .envrc.local`; delete `frontend/flake.nix` and `frontend/flake.lock`
- [ ] Rewrite the absolute Bazel labels in `frontend/justfile` and `frontend/scripts/` to their `//frontend/` forms, including the `//...` defaults of `build`, `test` and `lint` and the build flag `--//apps/dsp-app:config` (`--//frontend/apps/dsp-app:config`)
- [ ] Rewrite the labels these recipes compose at run time, which no grep finds: `test-files` builds `"//$pkg:…"` from app-relative paths (`frontend/justfile`, the `labels+=` lines) and resolves its arguments against `{{invocation_directory()}}`, so repository-root paths (`frontend/apps/…`) and `frontend/`-relative ones both work; `frontend/scripts/visibility-check.sh` composes `//$src`, `//$dst`, `//$lib` and `//$plant` labels and reads `"//libs/…"` keys from `tools/angular/visibility.bzl`, and its scratch copy of `git ls-files` must include the root `MODULE.bazel`, `MODULE.bazel.lock`, `.bazelrc` and `.bazelversion` with the app under `frontend/`
- [ ] Set `dsp_api_dir` in `frontend/justfile` to `..` and drop the `dsp-api` sibling-checkout comment and entry
- [ ] Point the root-file references in `frontend/justfile` and `frontend/scripts/` at the repository root: `node-version-check` reads `../MODULE.bazel`, `playwright-version-check` evaluates `nix eval --raw --inputs-from .. nixpkgs-frontend#playwright-driver.version` (plain `nixpkgs` would now be dsp-api's pin), `test-files` treats `../MODULE.bazel*`, `../.bazelrc` and `../.bazelversion` as run-everything inputs, and `visibility-check.sh` resolves the app root as `$(git rev-parse --show-toplevel)/frontend`
- [ ] `git grep -nE '\.\./dsp-api|DSP_API_DIR|show-toplevel' -- frontend` lists only lines this phase has rewritten or checked (`k6/`, `nginx/`, `scripts/`, `justfile`, docs)
- [ ] Exclude `frontend/**` from the root `just check` markdownlint call (`--ignore 'frontend/**'`; the app lints its own docs with `just docs-lint` and its own config)
- [ ] For a source file of any library that has tests, `just -f frontend/justfile test-files -- frontend/libs/<that file>` run from the root and `just test-files -- libs/<that file>` run from `frontend/` select the same `//frontend/libs/…` test targets
- [ ] Run `just check`; it passes
- [ ] The `packages` of the default dev shell in `flake.nix` are unchanged against `origin/main`
- [ ] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

#### Phase 4: App CI in dsp-api

- [ ] Port `ci.yml` as `.github/workflows/app-ci.yml`: triggers `pull_request` and `push` to `main`, `defaults.run.working-directory: frontend`, `nix develop ..#frontend` / `..#frontend-e2e`, every `hashFiles` on `frontend/pnpm-lock.yaml` and the root `MODULE.bazel.lock` (`hashFiles` resolves from the workspace root, not the working directory), artifact paths under `frontend/`, and the job names unchanged
- [ ] Rewrite the ported `changes` job, whose path regexes are anchored at dsp-app's root, as the app's half of DEV-8062's change detection (DEV-8062 emits only `api`): an `app` output that is true when the PR, or on `push` to `main` the pushed range, touches `frontend/**`, `MODULE.bazel`, `MODULE.bazel.lock`, `.bazelrc`, `.bazelversion`, `flake.nix`, `flake.lock`, `.github/workflows/app-*.yml` or `.github/actions/**`; every app job guards on it with the `!cancelled() && … != 'false'` form DEV-8062 uses, and skips `release-please--*` head branches like the API jobs; the `docs_only`, `only_workflows` and `bot_push` regexes take `frontend/` prefixes, and `automation_skip` matches dsp-api's release subject `chore(main): Release`
- [ ] Run the E2E job's stack from the same checkout (`just e2e-stack-start` with the default `dsp_api_dir`), dropping the `dasch-swiss/dsp-api@main` checkout and the job's `DSP_API_DIR: dsp-api` env lines; the stack still runs the published `daschswiss/knora-api:latest` image until the Payoff
- [ ] Port `.github/actions/bazel-setup` with the `bazel-rbe` call DEV-7932 converged, its `hashFiles('pnpm-lock.yaml')` keys as `frontend/pnpm-lock.yaml`, its `hashFiles('MODULE.bazel', …)` keys plus `flake.lock`, and the disk cache path `~/.cache/bazel-disk` (Technical Considerations)
- [ ] In the ported Pixeleye fallback job, the hard-coded status context `Pixeleye – dasch-swiss/dsp-app` names the context the Pixeleye app posts on dsp-api (read it from the first Pixeleye run on the integration PR); Pixeleye holds no baseline for the rewritten SHAs, so the first `main` run after cutover creates them
- [ ] Port `openapi-sync.yml` and the `check-openapi-sync` job: app token scoped to `dsp-api`, `gh pr merge --auto --rebase`, commit subject `chore(open-api): …` (it has the scope commitlint requires); since the ruleset requires an up-to-date branch and API merges keep moving `main`, each run first rebases an open sync PR onto `main` (`gh pr update-branch --rebase`) instead of leaving it behind
- [ ] Port `auto-approve-bots.yml`, restricted to `dasch-bot-app[bot]` PRs whose head branch starts with `chore/update-dsp-api-openapi-spec-`
- [ ] Port `cloud-run-pr-preview.yml` with its `paths` under `frontend/`
- [ ] Port `codeql-analysis.yml` for JavaScript, scoped to `frontend/`
- [ ] Add `.github/dependabot.yml` with the app's npm entry at `directory: /frontend` and `commit-message: { prefix: fix, include: scope }`, so its security PRs pass commitlint (`fix(deps): …`)
- [ ] Rewrite `frontend/scripts/check-required-checks.sh` to read `.github/workflows/app-ci.yml` and the `main` ruleset (`gh api repos/{owner}/{repo}/rules/branches/main`), since dsp-api has no classic branch protection
- [ ] Delete `frontend/.github/` (its `check-pr-title.yml`, `release.yml`, `claude.yml` and `readiness.yml` have dsp-api counterparts or no use under rebase-merge; `deploy.yml`, `release-dsp-js.yml` and `publish-from-branch.yml` are ported in Phase 5)
- [ ] Every `secrets.*` and `vars.*` the ported workflows reference exists for dsp-api, as a repository or organization secret or variable (`gh api repos/dasch-swiss/dsp-api/actions/secrets`, `.../actions/organization-secrets`, `.../actions/variables`); the ones still missing are on DEV-8064's checklist
- [ ] `actionlint` passes on `.github/workflows/`
- [ ] On the integration PR, every API job and every app job reports success, and no required job concluded `skipped`; this is the first evaluation of the `frontend` shells and the first run of `just -f frontend/justfile ci` (`knip`, `visibility-check`, `tsconfig-paths-check`, `storybook-build`), which the agent's shell, without Nix, cannot run, and of `playwright-version-check` against `nixpkgs-frontend`
- [ ] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

#### Phase 5: Release, publish and deploy from dsp-api

- [ ] Add the components to `.github/release-please/config.json`: `frontend` (`release-type: node`, `component: dsp-app`, `include-component-in-tag: true`, `extra-files` `libs/vre/core/config/src/lib/app-version.ts`) and `frontend/libs/dsp-js` (`component: dsp-js`, `include-component-in-tag: true`), with changelog sections limited to the types dsp-api's commitlint accepts (`style` is not one)
- [ ] Add `exclude-paths: ["frontend"]` to the root package, and `separate-pull-requests: true`
- [ ] Add `frontend` and `frontend/libs/dsp-js` to `.github/release-please/manifest.json` at the versions dsp-app's manifest holds at run time
- [ ] Keep one release-please run (`create-release.yml`) on `googleapis/release-please-action@v5` with a dasch-bot app token scoped to `dsp-api` (`actions/create-github-app-token`, as dsp-app's `release.yml` does), replacing v4 with the `GH_TOKEN` PAT (decided on DEV-8063: no PAT; an app token, unlike `GITHUB_TOKEN`, triggers `publish-release.yml` and the tag workflows); delete `frontend/release-please-config.json` and `frontend/.release-please-manifest.json`
- [ ] Root `tools/workspace_status.sh`: `STABLE_GIT_VERSION` from `git describe --tag --match 'v[0-9]*'`, and a new `STABLE_APP_GIT_VERSION` from `--match 'dsp-app-v[0-9]*'` with the `dsp-app-` prefix stripped; delete `frontend/tools/workspace_status.sh`
- [ ] `frontend/apps/dsp-app/image/BUILD.bazel` stamps `{{STABLE_APP_GIT_VERSION}}`, and `build_tag` in `frontend/justfile` derives from `dsp-app-v*` with the prefix stripped
- [ ] Add `paths-ignore: ['frontend/**', '.github/release-please/manifest.json']` to the API's `docker-publish.yml` (push to `main`), so an app change, and an app or `dsp-js` release merge, which also bumps the shared manifest, publishes no API image and triggers no DEV deploy
- [ ] Guard both jobs of `publish-release.yml` (`Publish release` and the chat notification) with `startsWith(github.ref, 'refs/tags/v')`: its trigger is `release: published`, which takes no tag filter, so today every GitHub Release, `dsp-app-v*` and `dsp-js-v*` included, would publish API images
- [ ] Port `deploy.yml` as `app-deploy.yml`: `workflow_run` of the app CI workflow on `main`, and tags `dsp-app-v[0-9]*`, with the DEV trigger `Service=app` and the chat notification naming the `dsp-app-v*` release
- [ ] In `app-deploy.yml`, publish and trigger DEV on a `workflow_run` only when the tested commit changed `frontend/` (the app CI's `changes` job always runs, so the workflow concludes `success` even when every app job is skipped on an API-only merge), so an API merge neither republishes the app nor redeploys it to DEV
- [ ] In `app-deploy.yml`, replace "the commit is still main's head" with "`frontend/` on main's head is the tested tree" (`git rev-parse origin/main:frontend` against `<head_sha>:frontend`), since API merges move dsp-api's head during most app CI runs
- [ ] Port `release-dsp-js.yml` (tags `dsp-js-v*`, `bazel-bin/frontend/libs/dsp-js/npm_package`)
- [ ] Port `publish-from-branch.yml` as `app-publish-from-branch.yml`
- [ ] Set `repository` in `frontend/libs/dsp-js/package.json` to `dasch-swiss/dsp-api` with `"directory": "frontend/libs/dsp-js"`, and `bugs.url` to dsp-api, so `--provenance` matches the publishing repository
- [ ] Run release-please in dry-run mode in a throwaway clone that holds the imported tags, since they reach GitHub only at cutover (H6) and the API-backed dry run cannot see them: `release-please release-pr --dry-run --local --local-path <throwaway clone> --target-branch <integration branch>` (CLI 17.5 or later; `--local` resets the clone to its origin, so never point it at a working checkout; the exact reset semantics unverified, confirm during implementation). Three release PRs are proposed (API, `dsp-app`, `dsp-js`), the API changelog lists no commit under `frontend/`, and the `dsp-app` and `dsp-js` components start from their last releases, found through the manifest versions and the `dsp-app-v*` and `dsp-js-v*` tags. Never set `last-release-sha`: it is a top-level option that stops the commit walk for every package, and release-please never ignores it
- [ ] `just docker-image-tag` prints an API version and `just -f frontend/justfile docker-image-tag` prints the app's `v14.x` version on the integration branch
- [ ] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

#### Phase 6: Readers and agents find the app in frontend/

- [ ] Root `CLAUDE.md`: a short Frontend section (the app lives in `frontend/`, its own `CLAUDE.md` applies there, commands run from `frontend/` in the `frontend` dev shell)
- [ ] Root `eng.yaml`: an override `frontend/**` with `conventions: [frontend/CLAUDE.md, frontend/CONVENTIONS.md, frontend/ARCH-MAP.md]` and `commands` `test: just -f frontend/justfile test`, `lint: just -f frontend/justfile lint`, `gate: just -f frontend/justfile ci`, `test_targeted: just -f frontend/justfile test-files -- {files}` (dsp-app declares it today; `test-files` takes root-relative paths after Phase 3); the app's reviewer and verify overrides with `frontend/`-prefixed globs; delete `frontend/eng.yaml`; `uv run <eng plugin>/scripts/eng_yaml.py validate` passes
- [ ] Register `frontend` as a component of the root `ARCH-MAP.md` with `dune:dune-map`, pointing at `frontend/ARCH-MAP.md` for its internals
- [ ] `docs/development/commit-conventions.md`: commits under `frontend/` take the component names of `frontend/ARCH-MAP.md` as scope, or `frontend`
- [ ] `frontend/CLAUDE.md`, `frontend/CONVENTIONS.md`, `frontend/README.md` and `frontend/docs/contribution/`: replace the lines that assume the repository root, the sibling `../dsp-api` checkout and squash-merge with their merged-repo forms (rebase-merge, one commit per PR, mandatory scope)
- [ ] `frontend/README.md` setup: clone dsp-api, `direnv allow` at the root and once in `frontend/`; switching between the two shells discards Bazel's analysis cache once (Technical Considerations)
- [ ] Move `frontend/.claude/skills/format-project-image` and `frontend/.claude/skills/update-openapi-client` to the root `.claude/skills/` with their paths prefixed by `frontend/`; merge the `just` and `bazel` permissions of `frontend/.claude/settings.json` into the root one and delete `frontend/.claude/`
- [ ] `frontend/mkdocs.yml`: `repo_url` and `repo_name` name dsp-api, `edit_uri` is `blob/main/frontend/docs/`
- [ ] `frontend/package.json` `repository.url` names dsp-api
- [ ] Root `README.md`: the CI badge names dsp-api's workflow (it points at dsp-app today) and the app is listed with a link to `frontend/`
- [ ] Rewrite the app labels left in docs and comments (`frontend/ARCH-MAP.md`'s Bazel packages, the libraries' `README.md` test commands, `frontend/docs/`, `knip.jsonc`, `.storybook/test-runner.ts`); `git grep -I -nE '(^|[^[:alnum:]@:/])//(:|apps/|libs/|tools/|nginx[:/]|mocks[:/])' -- frontend` returns nothing
- [ ] Run `just check`; it passes
- [ ] Every commit after the import merge passes commitlint, which CI never checks on this branch (the draft skips it and the landing is a push): `git rev-list --no-merges --reverse "$(git rev-list --merges --first-parent -1 HEAD)..HEAD" | while read -r c; do git log -1 --format=%B "$c" | commitlint >/dev/null || echo "$c"; done` prints nothing
- [ ] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

## Human Actions

| Id | Action | Who | When | Why not the agent |
| --- | --- | --- | --- | --- |
| H1 | Run the spike and fold its findings into Spike results; open pre-alignment issues for what breaks (done: nothing to pre-align) | Project lead | before start | Decides what to pre-align; throwaway exploration on a branch never opened as a PR |
| H2 | Review this plan once (`eng:eng-plan-review`) and accept ADR-0015 | Project lead, backend and frontend reviewers | before start | Sign-off |
| H3 | Merge the Prepare PRs: DEV-7931 (Bazel 9.3.1), DEV-7932 (`bazel-rbe` and root `platforms`), DEV-8062 (path-scoped checks) | Owners of those issues | before Phase 2 (Why mid-plan: Phase 1 builds the import and opens the integration PR days before the Prepare PRs merge, so its CI is the first Linux and RBE run early enough to matter for the cutover date; Phase 2 merges `MODULE.bazel`, `.bazelrc` and `platforms/` on top of what those PRs change, and Phase 4 builds on DEV-8062's change detection) | Merging |
| H4 | Announce the cutover date (a Thursday after the weekly prod deployment) and the freeze rules | Project lead | before start | Team communication |
| H5 | Cutover: confirm DEV-8064 is done and dsp-app #3620 is merged; close dsp-app's open release-please and bot PRs unmerged, disable its scheduled workflows (`openapi-sync`) and lock dsp-app `main`; pause merges to dsp-api `main` and disable auto-merge in its settings until H11; run the script on the frozen dsp-app `main`, wait for every check on the integration PR | Project lead, repository admin | after ship | Timing and the freeze are a team decision; repository settings need admin rights |
| H6 | Record dsp-api `main`'s SHA; add a bypass actor with mode "always" to the `main` ruleset; push the imported `dsp-app-v*` and `dsp-js-v*` tags first (release-please needs them; a push of more than three tags fires no workflow), then `git push origin <integration-branch>:main`; remove the bypass actor; apply the go or no-go rule from Cutover day | Repository admin | after ship | Needs admin rights on the ruleset |
| H7 | Add `DSP-APP Unit tests`, `DSP-APP Linting`, `DSP-APP E2E tests` and `Docs Build Test` to the ruleset's required checks; `just -f frontend/justfile check-required-checks` reports no drift | Repository admin | after ship | Needs admin rights on the ruleset |
| H8 | Carry over open dsp-app PRs: update each to the frozen `main`, `git diff --binary main...<branch>` in dsp-app, `git apply --3way --directory=frontend` on a dsp-api branch, one commit per PR with the original author; a PR that touches a deleted or moved root file (`MODULE.bazel`, `.bazelrc`, `.github/`, `flake.nix`) is ported by hand, and a PR that adds a `BUILD.bazel` gets the Phase 2 label rewrite; release-please, Dependabot and OpenAPI-sync PRs are not carried, dsp-api regenerates them | PR authors or project lead | after ship | Authors decide which PRs survive |
| H9 | Merge the ops-deploy PR from DEV-8063 | Project lead | after ship | Merge decision |
| H10 | Commit the README notice in dsp-app; archive dsp-app after the first app release from dsp-api has reached prod | Project lead | after ship | Archiving is irreversible in practice and waits on a prod release |
| H11 | Re-enable auto-merge, resume merges to dsp-api `main`, and announce that the app now follows dsp-api's merge rules; a developer with Nix confirms that `direnv` in `frontend/` loads the `frontend` shell | Project lead, repository admin | after ship | Team communication; the agent's shell has no Nix |
| H12 | Within two days before merging the first `dsp-js` release PR from dsp-api, add dsp-api's `release-dsp-js.yml` as a second trusted publisher of `@dasch-swiss/dsp-js`; delete dsp-app's publisher after that release has published | npm admin | after ship | Needs npm admin rights; a new trusted publisher expires unless it publishes within two days, and adding rather than replacing keeps the cutover rollback free of npm steps |

## Acceptance Criteria

- [ ] `frontend/` on dsp-api `main` holds dsp-app's frozen `main` tree, and `git log --follow` on any app file reaches its dsp-app history
- [ ] The cutover push adds exactly one merge commit to `main`'s first-parent history, followed by the integration commits
- [ ] `bazel build //...` succeeds, and the API's and the app's required checks pass on `main` after cutover
- [ ] A PR that touches only `frontend/` skips the Scala test jobs and is mergeable; a PR that touches only `modules/` skips the app jobs and is mergeable
- [ ] The first app release from dsp-api is tagged `dsp-app-v14.x` and publishes `daschswiss/dsp-app:v14.x`
- [ ] That release reaches DEV through `Service=app` and prod through the weekly release, with `versions/RELEASE.json` in ops-deploy unchanged in format
- [ ] An API-only merge to `main` neither publishes an app image nor redeploys the app to DEV
- [ ] An API release publishes no app image, an app release no API image, and the API image version comes from a `v*` tag
- [ ] A `dsp-js` release publishes to npm with provenance from dsp-api
- [ ] The default dev shell's packages are unchanged; `frontend/` loads the `frontend` shell

## Dependencies & Risks

- **Depends on:** the spike (H1); DEV-7931, DEV-7932, DEV-8062 merged before Phase 2 (H3); DEV-8064 done before cutover (H5). DEV-8062 needs the name `frontend/`, which this plan fixes.
- **Executed from:** the Move issue (this plan's phases) and DEV-8063 (Phase 5 plus the ops-deploy PR). DEV-8063's Suggested setup names this plan's release phase.
- **Payoff:** a separate plan in this folder (`02-feat-…-plan.md`) once the Move has landed: a Bazel target that emits the OpenAPI spec, the app client generated from it in the same build, and E2E against the API built from the same tree. It also removes the `openapi-sync` workflow and the bot auto-approval Phase 4 ports.
- **Out of scope:** upgrading `llvm` or other toolchains; restructuring anything inside `frontend/`; the reference updates in other repositories (Move issue).

## Risk Analysis & Mitigation

| Risk | Likelihood | Impact | Mitigation |
| --- | --- | --- | --- |
| A raised module version breaks the API or the app | L | H | The spike found none on macOS (`assets/spike-findings.md`); the integration PR's CI runs every API and app job on Linux and RBE |
| A change the macOS spike could not see breaks on Linux or RBE (`gawk` built by LLVM, Python 3.12) | M | M | Open the integration PR in Phase 1 and push Phase 2 early, so its CI is the first Linux and RBE run well before cutover; `gawk` already cross-compiles for Linux x86-64 locally |
| Replay conflicts because dsp-api `main` changed a root file | H | L | rerere, the lockfile regenerated instead of replayed, one commit per phase; re-run the script weekly so conflicts surface one at a time |
| A dsp-app change between script runs escapes the replayed rewrites (a new `BUILD.bazel`, a release bump) | M | M | The script's closing checks fail on stale labels and on a root manifest behind dsp-app's; the modify/delete resolution rule under The import script |
| release-please mis-attributes commits or misses the last app release | M | H | `exclude-paths`, the root manifest's `frontend` and `frontend/libs/dsp-js` versions with the imported tags, `--local` dry run (Phase 5), release-please PRs checked in go or no-go |
| The API image is versioned from a `dsp-app-v*` tag | H without the fix | H | `--match 'v[0-9]*'` in the root workspace status script (Phase 5) |
| A required check never reports and blocks every PR | M | H | DEV-8062's job-level `if:` lands first in a small PR; the app's checks join the ruleset only after the push (H7) |
| dsp-api `main` moves between the final run and the push | L | M | Merges are paused and auto-merge is disabled for the cutover window (H5); the push is a fast-forward and fails rather than overwrites if `main` moved |
| `main` is red after the push | M | H | Go or no-go rule with a recorded pre-push SHA and a defined rollback (Cutover day) |
| Pushing the imported tags triggers workflows | L | M | GitHub creates no push event when more than three tags are pushed at once, and imported commits hold no root `.github/` (filter-repo moved it under `frontend/`); tags are pushed before `main` (H6) and Actions is watched |
| The npm provenance check rejects the first `dsp-js` release | M | M | `repository` and `directory` set in Phase 5; dsp-api added as a trusted publisher within two days before that release (H12) |

## Success Metrics

- Import reproducibility: two script runs on the same dsp-app `main` give identical SHAs for every imported commit (`git rev-list --count origin/main` in dsp-app gives the number to compare).
- Freeze length on cutover day: from locking dsp-app (H5) to resuming merges (H11), within one working day.
- First app release from dsp-api reaches prod in the first weekly release after cutover.
- PR feedback for app-only PRs: no Scala job runs (DEV-8062), so their wall-clock stays at dsp-app's CI duration, not dsp-api's 20-minute median.

## References

- Linear: DEV-7930 (this planning issue), DEV-8062, DEV-8063, DEV-8064, DEV-7931, DEV-7932; project "Move DSP-APP into the dsp-api repository"
- Precedent: `docs/specs/2026-10-20-move-ingest-into-webapi/` (spec layout, separate companion plans); `dasch-specs/specs/2026-03-17-move-js-lib-to-dsp-das/` (release-please components, npm provenance)
- Ruleset: `gh api repos/dasch-swiss/dsp-api/rulesets` (`main`: deletion, non-fast-forward, linear history, PR with rebase only and one approval, five required checks, strict; no bypass actors)
- dsp-api root: `MODULE.bazel`, `.bazelrc`, `tools/workspace_status.sh` (describe without `--match`), `.github/release-please/config.json`, `justfile` (`check`), `.bazelproject`
- dsp-app root (on `origin/main`): `MODULE.bazel` (labels `//:package.json`, `//:node_modules/...`), `tools/angular/defs.bzl`, `apps/dsp-app/image/BUILD.bazel` (stamps `STABLE_GIT_VERSION`), `justfile` (`build_tag`, `dsp_api_dir`), `.github/workflows/ci.yml`, `release-please-config.json`
- ops-deploy: `config/GITHUB_DEPLOYMENTS_CONFIG.json`, `roles/dasch.github_deployments/README.md` (`environment_prefix`, `ref_prefix`)
- Learnings: `dasch-specs/learnings/best-practices/sipi-nix-to-bazel-migration-lessons.md` (toolchain registration order, `--toolchain_resolution_debug`), `dasch-specs/learnings/best-practices/bzlmod-dev-dependency-upstream-tools-unconsumable-vendor.md` (dev deps of the non-root module), `dasch-specs/learnings/best-practices/docker-image-publish-workflow-design.md` (one publish workflow per versioned artifact)
