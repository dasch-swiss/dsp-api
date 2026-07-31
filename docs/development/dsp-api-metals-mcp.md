# Scala language intelligence for agents (Metals MCP)

This repo ships a checked-in MCP server config (`.mcp.json` at the repo root) that gives Claude Code and
other agents real Scala language intelligence via [Metals](https://scalameta.org/metals/) — compiler
diagnostics, type-aware usage search, symbol docs, and symbol lookup — instead of relying on `grep`/read.

The server is defined as:

```json
{
  "mcpServers": {
    "metals": {
      "command": "metals-mcp",
      "args": ["--workspace", "${CLAUDE_PROJECT_DIR:-.}", "--transport", "stdio"]
    }
  }
}
```

It runs over **stdio**, so each agent session spawns and manages its own `metals-mcp` process — there is no
long-lived HTTP server or hardcoded port to go stale. `${CLAUDE_PROJECT_DIR:-.}` resolves to the repo root
that Claude Code was launched from.

## Prerequisites

- **The server ships in the Nix dev shell** — `flake.nix` includes the `metals` package (which provides
  the `metals-mcp` binary, currently Metals 1.6.7). With `direnv`/`nix develop` active, `metals-mcp` is on
  your PATH automatically; no separate `cs install` is needed. (Metals bootstraps bazel-bsp itself via its
  embedded coursier.)
- Confirm it resolves: `nix develop --command which metals-mcp` (or just `which metals-mcp` inside the shell).

If `metals-mcp` is not on your PATH, the `metals` MCP server simply fails to start and agents fall back to
grep/read — the repo still builds/tests fine, you just lose (the currently-degraded) language intelligence.

### First-use approval

`metals` is listed in `.claude/settings.json` under `enabledMcpjsonServers`, so it is pre-approved for the
team. On recent Claude Code versions, checked-in approvals are still ignored in a folder you have not marked
as trusted, so you may see a one-time folder-trust / MCP-approval prompt the first time you launch Claude
Code here. Approve it once.

## Bazel-9 support: how it works (a local bazel-bsp patch)

sbt has been removed, so Metals no longer connects via Bloop — it connects to the **Bazel** build via
**bazel-bsp** (Metals bootstraps it via its embedded coursier; no `cs` needed). bazel-bsp 4.0.x (the server
Metals 1.6.7 downloads) predates Bazel 9 and its sync aspect is not Bazel-9-compatible out of the box; two
breakages, each fixed locally so Metals works on Bazel 9.1.1:

1. **Removed Starlark globals.** The aspect uses bare `JavaInfo` / `java_common` / `CcInfo`, which Bazel 8+
   moved out of the global namespace into `@rules_java` / `@rules_cc` behind `--incompatible_autoload_externally`
   (symptom: `name 'JavaInfo' is not defined`, `scala_info.bzl` fails to compile). **Fix:** the macOS-scoped
   `build:macos --incompatible_autoload_externally=+JavaInfo,+JavaPluginInfo,+java_common,+CcInfo,+cc_common`
   in `.bazelrc`. macOS-only because all local dev is macOS and CI (Linux) never runs bazel-bsp; harmless for
   our own build (our rules load these explicitly).
2. **Struct-returning aspect.** `core.bzl`'s sync aspect does `return struct(bsp_info=…, output_groups=…)`,
   which Bazel 9 removed with **no re-enable flag** (symptom: `Returning a struct from an aspect implementation
   function is deprecated`). **Fix:** a small struct→provider rewrite in `tools/metals/patch_bazelbsp_core.py`
   (define a real `BspInfo` provider, return `[OutputGroupInfo(**…), BspInfo(**…)]`, read it on deps as
   `dep[BspInfo]`). bazel-bsp re-copies its bundled aspects into `.bazelbsp/` on every import, so the patch
   can't be applied durably in place — instead `.bazelproject` sets `bazel_binary: tools/metals/bazel-bsp-wrapper.sh`,
   a shim that re-applies the (idempotent) patch at each bazel-bsp invocation, then execs the real bazel
   (directly if it's on PATH, else via `nix develop` — Metals is often launched without the dev-shell PATH).

**Net: `list-modules`, `compile-file`/`compile-module`, and type-aware navigation all work on Bazel 9.**
This is **not** a rules_scala 7.x issue (rules_scala 7.2.6 + Scala 3.8.4 build fine; Metals 1.6.7 already
carries the ruleset-name fix, scalameta/metals#8265).

**This is a vendored patch to bazel-bsp 4.0.x — remove it once bazel-bsp supports Bazel 9 upstream.** Track
[scalameta/metals#8268](https://github.com/scalameta/metals/issues/8268) ("Bazel 8 breaks bazelbsp due to
missing rules_java providers"; the standalone `JetBrains/bazel-bsp` repo is archived, so Metals — which owns
the bundled bazel-bsp version — is the live target). If a Metals upgrade bumps bazel-bsp and the aspect layout
changes, `patch_bazelbsp_core.py` fails loudly (its anchor snippets won't match) — update it, or drop the
whole shim if upstream has fixed it.

**Performance — launch from inside the dev shell.** The wrapper execs bazel directly when `bazel` is on
PATH, else falls back to `nix develop --command bazel` (adds shell-eval overhead to *every* bazel-bsp call,
so the first import and each compile are noticeably slower). Metals launched outside the Nix dev shell
(e.g. Claude Code started from a plain login shell) takes the slow path — the first `compile-file` can run
well over a minute (cold compile + `nix develop` warmup). For the fast path, start Metals/Claude Code from
inside the dev shell (`direnv`/`nix develop`) so `bazel` is on PATH and the fallback is never used.

## Required per checkout: `just metals-bootstrap`

**In a fresh checkout or git worktree, metals needs a one-time bootstrap.** Metals connects to Bazel via
bazel-bsp, driven by the committed **`.bazelproject`** (the projectview listing which targets to import). A
fresh worktree has no `.bsp/` yet, so:

1. Run `just metals-bootstrap` — it verifies `.bazelproject` is present and warms the Bazel repo cache.
2. Call the metals `import-build` tool once. Metals then fetches bazel-bsp (embedded coursier), writes
   `.bsp/bazelbsp.json` + `.bazelbsp/`, and imports the build. A healthy import ends with
   `Imported build` / `indexed workspace` in `.metals/metals.log`.

`.bazelproject` is committed (do **not** delete or gitignore it — a missing projectview makes the bazel-bsp
import fail). `.bsp/` and `.bazelbsp/` are per-worktree generated state and are gitignored.

### If a session is already running: call `import-build`

A `metals-mcp` process that started before `.bsp/` existed **does not recover on its own** — the filesystem
watcher will not rescue it. After running the bootstrap mid-session, call the metals `import-build` tool once
to make the running server connect to bazel-bsp. A successful `import-build` reports `Reconnected to build
server` and `list-modules` then returns the build targets — the Bazel-9 aspect patch above is applied
automatically by the `bazel_binary` wrapper during the import.

This is why the bootstrap cannot be fully automated away in a `just` recipe alone: the recipe warms the
filesystem, `import-build` fixes the running server. Agents that create their own worktree at session start
should run both, in that order.

## The high-value tools

| Tool | What it does |
| --- | --- |
| `compile-file` | Compile a single file and return diagnostics. **Reads fresh from disk on every call** — this is your source of truth for errors after an edit. |
| `compile-module` | Compile a whole module (e.g. `webapi`). Refreshes the index for that module. |
| `compile-full` | Compile the whole build. |
| `get-usages` | Find references to a fully-qualified symbol (the find-references equivalent — there is no tool literally named `find-references`). |
| `get-docs` | Fetch the docs/signature for a symbol. |
| `get-source` | Fetch the source of a symbol. |
| `inspect` | Inspect a symbol (type info). |
| `glob-search` / `typed-glob-search` | Symbol search by name / by type. |
| `list-modules` | List the build's modules. |

Also available: `format-file`, `find-dep`, `test`, `import-build`, and scalafix tools.

## How agents should use it

**Prefer `compile-file` over shelling out to `bazel build` for the tight edit → feedback loop.** A
single-file incremental compile through Metals is faster than a fresh Bazel invocation and returns
structured diagnostics.

Recommended pattern:

> Edit files on disk, then call the metals tool you need. `compile-file` always reflects current disk content —
> treat it as the source of truth for diagnostics after an edit; no editor buffer or `didChange` is needed.
> For navigation (`get-usages`, go-to-definition) that you want to fully trust on a large codebase, run
> `compile-file` / `compile-module` first, then query. A compile is guaranteed to refresh the index and is
> cheap incrementally.

### If metals returns nothing, diagnose it

Distinguish the states before deciding anything:

| Symptom | Meaning | Action |
| --- | --- | --- |
| `list-modules` empty, `.metals/metals.log` shows `scala_info.bzl` / `JavaInfo` / "struct from an aspect" errors | The Bazel-9 aspect patch didn't apply: the `bazel_binary` wrapper wasn't used, or `patch_bazelbsp_core.py`'s anchors no longer match (bazel-bsp bumped). | Confirm `.bazelproject` has `bazel_binary:` and `.bazelbsp/aspects/core.bzl` contains `BspInfo`; see the Bazel-9 section at the top. |
| `list-modules` empty, no import in the log | The build server never connected. | Run `just metals-bootstrap`, then `import-build`. |
| `no build target for: <path>` in `.metals/metals.log` | The file is outside this server's workspace (see the worktree note below). | Check which directory the server was launched against. |
| `list-modules` works but a compile is slow | Genuine cold compile. | Wait; it is doing real work. |

`.metals/metals.log` in the workspace root is authoritative — read it rather than guessing. A healthy Bazel
import reads `Imported build in …` → `indexed workspace in …` (with the aspect errors noted above until
bazel-bsp supports Bazel 9).

**When metals is genuinely unavailable, say so and use `bazel build` / `grep`** — don't pretend it worked.

### Staleness on a large codebase

Metals has a filesystem watcher that triggers a background recompile after edits. On a tiny project this is
effectively instant; on a codebase the size of dsp-api a watcher-triggered reindex genuinely lags, so after an
edit, navigation results (`get-usages`, go-to-def) are transiently stale.

This was confirmed empirically on dsp-api: adding a new reference to a symbol and immediately calling
`get-usages` (without compiling) did **not** include the new call site; the same query *after* a
`compile-file` did. So **treat navigation as stale until you compile.** `compile-file` / `compile-module` are
not subject to this — they read current disk state — so the rule is: after an edit, compile the touched
file/module first, then run `get-usages` / go-to-def.

`compile-file` diagnostics, by contrast, always reflect current disk content on every call (verified: a
plain file write introducing a type error is reported precisely, and fixing it on disk clears it — no editor
buffer or recompile step needed).

## Concurrency, worktrees, and LOOM — read this before running multiple sessions

### One `metals-mcp` per checkout (hard rule)

Each Claude session spawns its own stdio `metals-mcp`. Two sessions pointed at the **same directory** both try
to open the same embedded H2 database (`.metals/metals.mv.db`), which takes an exclusive file lock → the
classic "another Metals server is already running" failure, and both sessions stomp each other's bazel-bsp
imports.

**Never run two agent sessions against the same checkout.** Use separate git worktrees instead.

### Separate worktrees each need their own bootstrap

Distinct directories get their own `.metals/`, `.bsp/`, and `.bazelbsp/`, so there is no DB lock and bazel-bsp
treats each as its own project. Correctness is fine **because** the config uses the repo-relative
`${CLAUDE_PROJECT_DIR:-.}` rather than a hardcoded absolute path — an absolute path would funnel every
worktree's session back onto one directory and reintroduce the H2-lock problem. **Do not hardcode an absolute
`--workspace` path.** (`.bazelproject` is committed, so every worktree shares the same projectview.)

But because `.bsp/`, `.bazelbsp/`, and `.metals/` are gitignored, a new worktree starts without them.
**Every fresh worktree needs its own `just metals-bootstrap`** (plus one `import-build` call) — nothing about
metals carries over from the main checkout, and this is the single most common reason metals "works on main
but not here."

The resource cost is real too: each worktree runs its own bazel-bsp server + JVM. N concurrent cold imports
and N× JVM heap / concurrent full compiles can exhaust RAM/CPU. **Keep the number of simultaneously
metals-active worktrees small.**

### The server is pinned to the launch directory

`${CLAUDE_PROJECT_DIR}` is resolved once, when the MCP server starts. If a session launches in the main
checkout and *then* moves into a worktree, the metals server stays pointed at the main checkout, and every
call against a worktree path fails with:

```text
WARN  no build target for: …/.claude/worktrees/<name>/modules/webapi/…/Foo.scala
```

The build server is connected and healthy; the file is simply not in its workspace. No bootstrap fixes this —
the session has to start from the directory it will actually work in.

### LOOM workspaces need an extra hop

Claude Code discovers `.mcp.json` **only in the directory it was launched from** — it does not recurse into
subdirectories or walk up parents. LOOM starts Claude at the workspace root (`~/workspaces/<name>/`), while the
dsp-api checkout is a *subdirectory* of it. So the checked-in `.mcp.json` is **not** auto-discovered under
LOOM, and even if it were, `${CLAUDE_PROJECT_DIR:-.}` would resolve to the workspace root, not the dsp-api
directory.

To use the metals server under LOOM, **launch Claude Code from inside the dsp-api checkout directory** (not the
workspace root). The checked-in repo `.mcp.json` remains the single source of truth; LOOM users just need to
start from the right cwd.
