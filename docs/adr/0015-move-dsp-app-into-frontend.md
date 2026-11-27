# ADR-0015: Move dsp-app into dsp-api under `frontend/`, with its history

Date: 2026-11-26

Relates to: DEV-7930 (planning), DEV-8062 (path-scoped required checks), DEV-8063 (release and deploy), DEV-8064 (access), the Linear project "Move DSP-APP into the dsp-api repository".

## Status

Proposed

## Context

An API change and the app change that uses it cannot land as one change: the app's vendored OpenAPI client trails the API by a dev deploy, and the app's E2E tests run against dsp-api `main`, not against the API a PR needs. Both repositories build with Bazel 9 on the same NativeLink RBE, so the move is a repository move, not a build migration. The frontend team accepts dsp-api's merge rules (rebase-merge, one commit per PR, mandatory commit scope) from the cutover on.

dsp-api's `main` ruleset requires linear history, PRs, rebase-only merges and five checks, and has no bypass actor; the repository has merge commits disabled. Work continues in dsp-app until cutover day, so the import must be repeatable on the newest dsp-app `main`.

## Decision

1. **Layout.** dsp-app's repository root becomes `frontend/`, a peer of `modules/`. Nothing inside it moves (`apps/dsp-app` becomes `frontend/apps/dsp-app`). Its `CLAUDE.md`, `ARCH-MAP.md`, `CONTEXT.md`, `CONVENTIONS.md`, `REVIEW.md`, `mkdocs.yml` and `docs/adr/` stay under `frontend/`, changed only where they assume the repository root. The exception is `eng.yaml`: tooling reads one file at the repository root, so its bindings move into the root file with `frontend/` globs. The API side keeps every path.
2. **One Bazel module.** The app's `MODULE.bazel` merges into the root one, and `frontend/` is a package tree of the root module (`//frontend/apps/dsp-app:dist`), not a nested module or workspace. Shared dependencies are declared once. The API's targets and the app's targets sit in one graph, so the app can depend on a target the API builds in the same commit.
3. **History.** The app's history is imported with `git filter-repo --to-subdirectory-filter frontend`. Imported commit messages get `#NNN` rewritten to `dasch-swiss/dsp-app#NNN`, so references keep pointing at dsp-app PRs. The filter is deterministic (pinned `git filter-repo` version), so a re-run gives the same SHAs for old commits.
4. **Tags.** dsp-app's `v*` tags are imported as `dsp-app-v*`; `dsp-js-v*` tags are imported unchanged; all other tags are dropped. Bare `v*` tags stay the API's.
5. **One merge commit.** The import is one `--allow-unrelated-histories` merge with dsp-api `main` as first parent. The app's history keeps its own merge commits; `git log --first-parent` and `git bisect --first-parent` keep the API's view linear.
6. **Landing.** The import merge and the integration commits are built on an integration branch by a re-runnable script and checked by CI as a draft PR. The PR merge button is never used for it. On cutover day, with dsp-app locked, merges to dsp-api `main` paused, and the script re-run on the frozen dsp-app `main`:
    1. Record the SHA of dsp-api `main`.
    2. Add a bypass actor with bypass mode "always" to the `main` ruleset.
    3. Push the imported `dsp-app-v*` and `dsp-js-v*` tags, so release-please finds the app's last release on the first run.
    4. Fast-forward `main`: `git push origin <integration-branch>:main`.
    5. Remove the bypass actor.
    6. Go or no-go: the first `main` run of every required check is green and release-please proposes one release PR per component; otherwise roll back (Consequences).
7. **Required checks.** Checks are scoped by path with a job-level `if:`, never a workflow-level `paths:` filter, which would leave a required check unreported. Changes to shared root files (`MODULE.bazel`, `.bazelrc`, `flake.nix`, `.github/`) run everything. The app's job names stay (`DSP-APP Unit tests`, `DSP-APP Linting`, `DSP-APP E2E tests`, `Docs Build Test`) and join the ruleset after cutover.
8. **Releases.** release-please gets the app and `libs/dsp-js` as components next to the root package. The app's tags are `dsp-app-v*`; its image stays `daschswiss/dsp-app` with tag `v14.x`, so ops-deploy keeps its version format and only remaps `app` to dsp-api with an environment and a tag prefix. The API's version stamp matches `v[0-9]*` only.
9. **Cutover timing.** Cutover is a Thursday, the day after the weekly prod deployment, so no release is in flight. dsp-app is archived only after the first app release from dsp-api reaches prod; until then it is the fallback.

## Consequences

- One tree holds the API and the app, so a later change can generate the app's client from the API built in the same commit and run E2E against it.
- `main` changes about twice as often, and every merge makes open PRs update and re-run. Accepted; revisit if it hurts.
- `git log` without `--first-parent` interleaves two histories; tools that count commits on `main` count both.
- Two ADR series exist: this one and `frontend/docs/adr/`. App-only decisions go to the latter.
- Rollback on cutover day is a forced push of `main` back to its previous tip through the same bypass, plus deleting the pushed `dsp-app-v*` and `dsp-js-v*` tags; it is possible only while no other merge has landed.

## Alternatives rejected

- **API and app as equal peers** (`api/`, `app/`): renames every API path and conflicts every open dsp-api PR, for no functional gain.
- **The app's folders flat at the root:** collides on `tools/`, `platforms/`, `k6/`, `docs/adr/`, every agent file, `justfile`, `flake.nix`, `mkdocs.yml` and `CHANGELOG.md`.
- **Flattening `frontend/apps/dsp-app`:** rewrites the Angular and TypeScript path configuration for looks and breaks carrying open PRs over with `git apply --directory=frontend`.
- **The app as its own Bazel module or workspace under `frontend/`:** keeps two module graphs, so no app target can depend on an API target, which is the reason for the move.
- **A Bazel module per lib:** modules version external dependencies, while lib boundaries are packages and `visibility`, which the app already enforces (`frontend/tools/angular/visibility.bzl`). Nested modules share one version resolution anyway, overrides take effect only in the root module, and rules_js links one `node_modules` per pnpm workspace.
- **A copy without history:** loses `git blame` and `git log` for the app; the import costs one bypassed push.
- **A linear rebase of the app's history:** rewrites its merge commits and changes every SHA on each run, which the re-runnable import relies on.
