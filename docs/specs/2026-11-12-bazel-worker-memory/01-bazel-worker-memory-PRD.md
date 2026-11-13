---
title: "dsp-api builds keep Bazel's memory use bounded on developer machines"
date: 2026-11-12
author: "Balduin Landolt"
status: implemented
---

# dsp-api builds keep Bazel's memory use bounded on developer machines

## Context

On 2026-11-10 a developer's 32 GB Mac ran out of memory while coding agents built dsp-api. The cause was 330
Bazel persistent workers holding 142 GB in total. macOS attributed the memory to Ghostty, the terminal the agents ran in.
The figures in this section come from that developer's investigation of their process list and Bazel log.

Bazel keeps persistent workers alive between builds so that the next Scala compile or format starts warm. dsp-api
uses two kinds: Scalac (rules_scala's compiler worker) and ScalaFmt. Bazel reuses a worker only when its identity
matches; the identity includes the environment the worker runs with. When the identity differs, Bazel starts a new
worker and keeps the old ones until the server exits. Nothing bounds their number or their memory.

Each `nix develop` creates a new temporary directory (`/tmp/nix-shell.XXXXXX`) and sets `TMPDIR` to it. Agents run
each command as `nix develop --command ...`, the fallback `CLAUDE.md` gives when direnv is not active, and direnv is
not active in an agent's shell. So every agent build sends Bazel a new `TMPDIR`, and Bazel starts new workers.
The developer's Bazel log for 2026-11-10 shows this: the temporary-directory variables were the only settings that
changed between builds, with 151 different temporary directories, 121 Scalac worker identities and 101 ScalaFmt
worker identities. People who enter the dev shell once (by hand or through direnv) and build many times in it do not
see the problem, because their `TMPDIR` stays the same.

Verified on 2026-11-11: two consecutive `nix develop --command` calls returned `TMPDIR=/tmp/nix-shell.gEutzh` and
`TMPDIR=/tmp/nix-shell.NZ7KdM`; three consecutive `direnv exec .` calls returned the same `TMPDIR` each time.
Verified against the Bazel 9.1.1 source in review: Bazel builds the worker identity from the spawn environment after
the local environment provider has rewritten it, and on macOS and Linux that provider always sets `TMPDIR` from the
client environment (`/tmp` if unset). So `--incompatible_strict_action_env` (set in `.bazelrc`) keeps the other
changing variables (`TMP`, `TEMP`, `NIX_BUILD_TOP`) out of the identity, but not `TMPDIR`. A reproduction on a small
repository with a JSON persistent worker and the same `.bazelrc` settings started 11 workers over 3 builds with a new
`TMPDIR` each, and kept the same 4 workers over 3 builds with `TMPDIR=/tmp`. The US-1 verification repeats this on
dsp-api.

A second, smaller cost compounds the first. Each git worktree has its own Bazel output base, so it runs its own Bazel
server (a JVM) and its own worker pool. Agents work in worktrees, often several at once, and a server stays alive for
3 hours after its last build (Bazel's default `--max_idle_secs`). On one developer machine on 2026-11-11 there were 12
dsp-api output bases, of which 8 belonged to worktrees that no longer existed. The pools of idle worktrees hold memory
long after the agent has finished.

## Goals

1. Repeated builds in one Bazel server do not add workers, however the dev shell was entered.
2. The memory held by idle workers has an upper bound, so an unforeseen cause of new worker identities cannot exhaust
   memory again.
3. Bazel servers of idle worktrees release their memory sooner; on a best-effort basis, earlier still when the machine
   runs low on memory.
4. Nothing changes for people who build through direnv or a long-lived shell, through Metals, or in CI, beyond the
   bounds above and the local disk cache of Goal 5. CI keeps building without a disk cache.
5. A new or long-idle worktree builds from cached results, so the cold starts that Goal 3 causes do not mean
   recompiling from scratch.

## Core Features

### Stable temporary directory for Bazel

The dev shell's `bazel` command (a wrapper around bazelisk, defined in `flake.nix`) runs Bazel with a fixed
`TMPDIR`, `/tmp`. Only Bazel gets the fixed value; every other tool in the dev shell keeps the per-shell temporary
directory `nix develop` gives it. `/tmp` is also what Bazel falls back to when `TMPDIR` is unset.

The dev shell's `bazel` command is the one place every route through the dev shell goes through: direnv,
`nix develop --command`, `just` recipes, the Metals BSP wrapper (`tools/metals/bazel-bsp-wrapper.sh` runs
`nix develop --command bazel`), and CI, whose steps run as `nix develop --command just ...`. Calls that bypass
the dev shell's `bazel` command (bazelisk invoked directly, a Bazel installed outside Nix) are not covered.

Builds through direnv already share one `TMPDIR` per user across all worktrees (the macOS per-user temporary
directory), so a shared `/tmp` adds no new kind of sharing between concurrent builds. The macOS sandbox adds `/tmp`
and `/private/tmp` to the writable directories of every sandboxed action, so a fixed `/tmp` works there too.

### Upper bound on idle worker memory

`.bazelrc` sets the worker memory limit to a quarter of the machine's RAM. When, during a build, all workers of one
Bazel server together use more than the limit, Bazel stops idle workers. It checks every 5 seconds while a build
runs and not at all between builds, so idle workers above the limit stay until the next build starts; since workers
are only created during builds, the overshoot is at most the workers one build adds. It never stops a busy worker,
so the limit cannot break a running build; its cost is that a stopped worker starts cold on the next build.
A quarter scales with the machine
(8 GB on 32 GB, 16 GB on 64 GB): high enough that the workers of a normal build (a few Scalac and ScalaFmt workers)
stay warm, low enough that a runaway like the one on 2026-11-10 stops far below the machine's memory. A developer who
wants another value overrides it in `~/.bazelrc`, which Bazel reads after the repository's `.bazelrc`.

The limit applies per Bazel server and to idle workers only, so N busy worktrees hold their busy workers plus up to
N times the limit. The next feature addresses idle worktrees; busy ones are out of scope (see Out of Scope).

### Idle worktrees release their memory

`.bazelrc` sets two startup options:

- An idle timeout of one hour instead of Bazel's default of three: a server with no build for an hour exits, and its
  workers with it. An hour covers a meeting or a lunch break without a cold start, while a finished agent worktree
  gives its memory back the same afternoon.
- Shutdown on low memory: a server that has been idle for 5 minutes exits when the machine is low on memory, before
  the hour is up. On macOS "low" means the system memory pressure level is not normal; on Linux, free RAM below 5%
  and below 1 GiB (Bazel 9.1.1 source, `ServerWatcherRunnable`). It is enabled because it costs nothing when memory
  is plentiful.

Startup options take effect when a server starts. A server already running when the change lands keeps its old
settings until it exits; `bazel shutdown` once applies the new ones at once (Bazel's help states that a changed idle
timeout does not restart a running server).

The expected cost is a cold start: the first build after an idle shutdown starts a new server, repeats the analysis
and starts new workers, so it is slower than a build in a warm server. This is intended, not a fault.

### Shared disk cache for cold starts

The idle timeout and the shutdown on low memory cause more cold server starts. After a cold start, a worktree reuses
nothing it built before unless a disk cache holds the compiled actions. Without one, every new or long-idle worktree
recompiles from scratch. So `.bazelrc` sets a disk cache shared by all checkouts and worktrees of a user, bounded by
garbage collection, and makes the formatter's outputs survive a cache hit:

```text
build --disk_cache=~/.cache/bazel-disk
build --experimental_disk_cache_gc_max_size=20G
build --experimental_disk_cache_gc_max_age=30d
build --remote_download_regex=.*[.]fmt[.]output$
```

- **Path:** the one vitrinli's CI and developers with a disk cache in `~/.bazelrc` already use; entries are content
  addressed, so sharing it between repositories is safe. Bazel coordinates garbage collection between servers through
  a lock file in the cache directory.
- **Size:** 20 GB at most, entries unused for 30 days removed. One developer's cache, shared by dsp-api and other
  repositories over several weeks, held 6.1 GB on 2026-11-13. Bazel collects garbage in the background once a server
  has been idle for 5 minutes, which the one-hour idle timeout leaves room for. A developer who wants another path or
  size sets it in `user.bazelrc` or `~/.bazelrc`.
- **Formatter outputs:** `.bazelrc` sets `--remote_download_toplevel`, which applies to the disk cache as well as the
  remote one: on a cache hit, Bazel writes only the outputs and runfiles of the requested target to disk.
  rules_scala's `<target>.format-test` and `.format` scripts read the `.fmt.output` files by path, but those files are
  neither, so after a cache hit they are missing and the check fails with `diff: ... No such file or directory`.
  `--remote_download_regex` forces their download. The pattern uses `[.]` rather than `\.`, because Bazel strips
  backslashes when it reads an rc file. Other scripts that read outputs by path would fail the same way after a cache
  hit; none is known.
- **CI:** runners are ephemeral and use the remote cache, so a disk cache would only fill their disk. The `bazel-rbe`
  action, which every CI job that runs Bazel uses, appends `build --disk_cache=` to `user.bazelrc` (imported after the
  settings above). Writing the file instead of adding a flag covers recipe steps that do not pass the CI flags on.

A disk cache changes where Bazel finds action results, not what a build produces, so it does not change build or test
results (Goal 4).

## Repository Impact

| File | Change |
| --- | --- |
| `flake.nix` | the dev shell's `bazel` command runs bazelisk with `TMPDIR=/tmp` |
| `.bazelrc` | worker memory limit, idle timeout, shutdown on low memory; shared disk cache |
| `.github/actions/bazel-rbe/action.yml` | turns the disk cache off on CI |
| `justfile`, `docs/development/rbe.md` | comments and docs for the disk cache |

No other repository changes in this PRD; see Follow-up work.

## User Stories

### US-1: Agent builds reuse workers

As a developer who lets coding agents build dsp-api, I want repeated builds to reuse Bazel's workers, so that my
machine's memory does not fill up over a working day.

- REQ-1.1 (Ubiquitous): The dev shell's `bazel` command shall run Bazel with `TMPDIR=/tmp`.
- REQ-1.2 (Ubiquitous): The dev shell shall leave `TMPDIR` unchanged for every command other than `bazel`.
- REQ-1.3 (Event-driven): When a build runs in a new `nix develop --command` invocation against a running Bazel
  server, Bazel shall reuse the server's existing Scalac and ScalaFmt workers instead of starting new ones.
- REQ-1.4 (Ubiquitous): The definition of the dev shell's `bazel` command in `flake.nix` shall carry a comment that
  names the problem the fixed `TMPDIR` prevents, so that it is not simplified back.

Verification for REQ-1.3, once during implementation and before the change is accepted. All builds run from
`nix develop --command`, so that the direnv route does not mix in. `bazel shutdown`; then three
runs, each in a new `nix develop --command`, with one changed Scala file in `modules/webapi` between runs. Each run
builds `//modules/webapi:webapi` (Scalac) and runs `//modules/webapi:webapi.format-test` (ScalaFmt; a plain build
does not start ScalaFmt workers). The evidence is the server log (`java.log` in the output base): its worker-creation
lines for Scalac and ScalaFmt appear in the first run only. The number of worker processes (not all Java processes:
the Bazel server and Metals are JVMs too) stays the same after the first run. As a baseline, the same procedure with
the unchanged command (bazelisk without a fixed `TMPDIR`) shows new workers in every run. If new workers still appear
after the first run with the change, the cause includes more
than `TMPDIR`: the log then names the setting that differs, and this PRD is revised before implementation continues.

Verification for REQ-1.2: inside `nix develop`, `$TMPDIR` outside the `bazel` command is still `/tmp/nix-shell.*`.

### US-2: Idle worker memory has a ceiling

As a developer, I want the memory Bazel's idle workers hold to have a ceiling, so that a cause we have not foreseen
cannot exhaust my machine's memory.

- REQ-2.1 (Ubiquitous): `.bazelrc` shall set the worker memory limit to a quarter of the machine's RAM.
- REQ-2.2 (Ubiquitous): `.bazelrc` shall carry a comment next to the limit that names the problem it bounds.

Verification, once during implementation: with the limit temporarily set low (for example 1 GB) and the unchanged
`bazel` command (a new `TMPDIR` per build, so every build adds workers), four builds of `//modules/webapi`, each in a
new `nix develop --command`. The number of worker processes, counted after each build, stays at about one build's
worth instead of growing with every build, and Bazel's log reports the workers it stopped. The count is taken after
a build and not after a wait, because Bazel evicts only while a build runs.
A low test value is used because the real limit is reached only in a
runaway, which cannot be reproduced on purpose. That Bazel accepts the real value (a quarter of RAM, expressed
relative to the machine's RAM) is shown by any build with the real setting, since Bazel rejects an invalid value.

### US-3: Idle worktrees release their memory

As a developer who runs agents in several worktrees, I want a worktree's Bazel server to exit when nobody is building
in it, so that finished or paused agent work does not hold memory.

- REQ-3.1 (State-driven): While a dsp-api Bazel server has not run a build for one hour, the server shall shut down.
- REQ-3.2 (State-driven): While a dsp-api Bazel server is idle and the machine is low on free RAM, the server shall
  shut down.

Verification, once during implementation, for REQ-3.1: `bazel shutdown` first (a changed idle timeout does not
restart a running server), then a build with the idle timeout temporarily set to 60 seconds; after a little over a
minute, the server process and its workers have exited. Then, with the real setting, a new server's startup options
show the one-hour timeout. The short value keeps the check fast; the second step shows that the hour comes from
`.bazelrc` and not from Bazel's default. REQ-3.2 is not verified: producing memory pressure on purpose is not worth
the effort for a setting that costs nothing when it does not trigger.

### US-4: Existing workflows are unaffected

As a developer who builds through direnv, and as the maintainer of CI, I want these changes to leave builds and test
results as they were, so that the fix costs nothing for setups that never had the problem.

- REQ-4.1 (Ubiquitous): `just check`, `just test-unit`, `just test-it` and `just test-e2e` shall pass with the changes
  on a developer machine. `just test-it` and `just test-e2e` are included because they are the Docker and
  testcontainers tests, which depend most on temporary paths and so are the most likely to notice a changed `TMPDIR`.
- REQ-4.2 (Ubiquitous): CI shall pass on the pull request that makes the changes. No workflow file changes; the
  `bazel-rbe` action changes only to turn the disk cache off (REQ-5.3).

Metals is not verified separately: its BSP wrapper calls the same dev shell's `bazel` command, so REQ-4.1 covers what
it runs.

### US-5: Cold worktrees build from cache

As a developer whose worktree's Bazel server has shut down, or who starts a new worktree, I want the build to reuse
results from my other checkouts, so that the idle timeout does not cost me a full recompile.

- REQ-5.1 (Ubiquitous): `.bazelrc` shall set a disk cache at `~/.cache/bazel-disk`, garbage-collected to 20 GB and
  30 days.
- REQ-5.2 (Event-driven): When a format check (`<target>.format-test`) runs after its `.fmt.output` files came from a
  cache, the files shall be on disk and the check shall pass or fail on formatting alone.
- REQ-5.3 (Ubiquitous): Every Bazel call in a CI job shall run without a disk cache.
- REQ-5.4 (Ubiquitous): `.bazelrc` shall carry comments that name why the disk cache is off on CI and why
  `.fmt.output` files are always downloaded.

Verification, once during implementation:

- REQ-5.1: with no `~/.bazelrc` (`bazel --nohome_rc`), a build of `//modules/bagit:bagit` after `bazel clean`
  reports disk cache hits.
- REQ-5.2: after `bazel clean` with a warm disk cache, `bazel run //modules/bagit:bagit.format-test` fails with a
  missing `.fmt.output` without the `--remote_download_regex` setting and passes with it; `just check`, unchanged,
  passes after `bazel clean`.
- REQ-5.3: with `build --disk_cache=` in `user.bazelrc` and `--nohome_rc`, `--announce_rc` shows it applied last and
  the build reports no disk cache hits; on the pull request, CI passes (REQ-4.2).

## Constraints

- Bazel 9.1.1 (`.bazelversion`). The worker memory limit, the idle timeout and the shutdown on low memory
  exist in that version (checked with `bazel help` on 2026-11-11).
- The changes live in the repository, not on developer machines: a developer who never hears of this gets them.
- Bazel behaviour checked against the 9.1.1 source in review: the worker memory limit stops idle workers only,
  never a worker executing an action, and only while a build runs; the shutdown on low memory applies only when an
  idle timeout is set. A Bazel upgrade checks that the worker memory limit, an experimental flag, still exists and
  still behaves this way.
- rules_scala 7.2.6 does not set `use_default_shell_env` on its Scalac and ScalaFmt actions, so the `PATH` that
  `.bazelrc` forwards with `--action_env=PATH` does not reach their worker identity. A rules_scala upgrade that
  changes this would split workers by `PATH` (direnv shell versus `nix develop --command`).

## Success Criteria

1. The US-1 verification shows no new workers after the first build.
2. The US-2 verification shows the worker count staying flat across builds, with Bazel stopping idle workers above
   the limit.
3. The US-3 verification shows an idle server exiting after its idle timeout, and the one-hour value in effect. The
   shutdown on low memory is configured but not verified (US-3).
4. REQ-4.1 and REQ-4.2 hold.
5. The US-5 verification shows disk cache hits from the repository's setting, the format check passing after a cache
   hit, and no disk cache on CI.

These are checked once, during implementation. No ongoing check is added: a CI check would need several local builds
with changing temporary directories and is slow and fragile on CI runners, which compile mostly on the remote executor.
The comments required by REQ-1.4, REQ-2.2 and REQ-5.4 are the guard against a regression.

## Considered and rejected

- **Change how agents enter the dev shell (`direnv exec .` instead of `nix develop --command`).** `direnv exec` keeps
  `TMPDIR` stable (verified), but direnv has to approve each worktree's `.envrc` (`direnv allow`). A user-level
  whitelist prefix could cover every worktree: it is a plain string prefix, so a prefix on the parent folder also
  matches worktrees created next to the repository. It still falls short in two ways. Every agent would have to call
  `direnv exec . <cmd>` instead of the `nix develop --command` that `CLAUDE.md` gives, and any call that does not
  falls back to the original problem; and the whitelist trusts every `.envrc` under the prefix, which each developer
  would have to set up and accept. The wrapper covers every route without either. `direnv exec` remains a possible
  speed-up for agents (it skips the dev shell's start-up), not a fix.
- **Only a memory limit, no change to the dev shell's `bazel` command.** Bounds the damage but keeps starting cold
  workers on every agent build, so agents keep building slowly and the limit is reached routinely instead of
  exceptionally.
- **Settings in each developer's `~/.bazelrc` or the system rc.** Reaches only developers who set it up, and the
  system rc is read before the repository's `.bazelrc`, which overrides it. The same holds for the disk cache: a
  developer who has one in `~/.bazelrc` keeps it (read last), and everyone else now gets the repository's.
- **One output base shared by all worktrees.** Bazel runs one server per output base; worktrees sharing it would wait
  for each other and overwrite each other's outputs.
- **`--worker_quit_after_build`.** Stops all workers after every build, which gives up the purpose of persistent workers.

## Follow-up work: other Bazel repositories

vitrinli (formerly sipi), ark-resolver and dasch-ops-platform have the same `bazel` wrapper in their `flake.nix`
(`exec bazelisk "$@"`, without a fixed `TMPDIR`). None of them uses a ruleset with persistent workers today (C++,
Rust, Go and Python rules; rules_rust has an optional worker mode, not enabled as far as checked), so they do not leak
workers now. Their status is:

- **Stable `TMPDIR`:** preventive. They would leak as soon as one of them adds a worker-based ruleset (Java, Kotlin,
  Scala).
- **Worker memory limit:** no effect until they have workers.
- **Idle timeout and shutdown on low memory:** effective now, because every Bazel server is a JVM that holds memory
  while idle; vitrinli's C++ builds produce large servers.

To be assessed per repository after this PRD is implemented.

## Out of Scope

- Removing output bases of deleted worktrees. It concerns disk, not memory, and needs a cleanup outside the build.
- Changing the `CLAUDE.md` advice to use `nix develop --command` without direnv. With the stable `TMPDIR` that
  advice is safe.
- A shared output base or fewer parallel agent worktrees.
- Memory of busy workers in several worktrees building at the same time. Each server runs its own Scalac and
  ScalaFmt workers, and neither the memory limit (idle workers only) nor the idle timeout bounds them. The incident
  was a leak in one server (330 workers), not concurrency; on 2026-11-10 the workers averaged about 430 MB each.
  Possible measures, none checked yet: a lower `--worker_max_instances` for `Scalac` and `ScalaFmt` locally, a heap
  limit for the Scalac worker JVM, a lower total limit than a quarter of RAM, or agent builds on the NativeLink RBE
  so that no Scalac workers run locally (not checked whether developer machines can reach it).
- An upstream Bazel issue about the worker identity depending on the client's `TMPDIR`.
- `bazel` calls that bypass the dev shell's `bazel` command.

## Open Questions

1. Whether the Bazel server started by the Metals BSP wrapper uses the same output base as command-line builds, or adds
   a second server per checkout. The fixed `TMPDIR` covers it either way; the answer affects only how much memory a
   checkout with Metals holds.
