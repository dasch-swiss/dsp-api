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

## Prerequisites (one-time human setup)

- **Install the server** via [Coursier](https://get-coursier.io/): `cs install metals-mcp`. This puts a
  `metals-mcp` launcher on your PATH (typically under the Coursier bin dir).
- **Minimum version: Metals ≥ 1.6.6** (Osmium, 2025) — the release that first shipped the standalone MCP
  server. Anything older will not have `metals-mcp`. Check with `metals-mcp --version`.
- Confirm it is on your PATH: `which metals-mcp`.

If `metals-mcp` is not on your PATH, the `metals` server will simply fail to start and agents fall back to
grep/read — the repo still works, you just lose the language intelligence.

### First-use approval

`metals` is listed in `.claude/settings.json` under `enabledMcpjsonServers`, so it is pre-approved for the
team. On recent Claude Code versions, checked-in approvals are still ignored in a folder you have not marked
as trusted, so you may see a one-time folder-trust / MCP-approval prompt the first time you launch Claude
Code here. Approve it once.

## Required per checkout: `just metals-bootstrap`

**In a fresh checkout or git worktree, metals will not work until you run `just metals-bootstrap`.** This is
not a warmup delay — the build server never connects at all, and every tool that needs a build target returns
empty or errors.

The cause is the Bazel migration. dsp-api now has **both** `build.sbt` and `MODULE.bazel`/`BUILD.bazel`, so
Metals detects two build definitions and has to ask which one to import from. In an editor you answer that
prompt once and it is remembered. Over MCP stdio there is no channel to ask, so the request is dropped:

```text
ERROR [MCP Request not handled] Multiple build definitions found. Which would you like to use?
WARN  Build server is not auto-connectable.
```

`just metals-bootstrap` sidesteps the question rather than answering it. It generates `project/metals.sbt`
(the sbt-bloop plugin, which Metals would normally write itself) and runs `sbt bloopInstall` to produce
`.bloop/`. With `.bloop/` present, Metals finds the **Bloop build server** and connects to that directly,
never needing to resolve the build-tool ambiguity. The "Multiple build definitions found" line still appears
in the log afterwards — it is no longer fatal, so do not read it as failure on its own.

On dsp-api the bootstrap takes ~15s and generates 23 Bloop targets. There is still a genuine one-time cold
compile after it (webapi alone is ~438 sources), but that now happens with a *connected* build server.

### If a session is already running: call `import-build`

A `metals-mcp` process that started before `.bloop/` existed **does not recover on its own** — the filesystem
watcher will not rescue it. After running the bootstrap mid-session, call the metals `import-build` tool once:

- before the bootstrap, `import-build` returns `No changes detected` (there is no build server to import into)
- after it, `import-build` returns `Reconnected to build server`, and `list-modules` starts returning modules

This is why the bootstrap cannot be fully automated away in a `just` recipe alone: the recipe fixes the
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

**Prefer `compile-file` over shelling out to `sbt compile` for the tight edit → feedback loop.** A single-file
incremental compile through Metals is far faster than a fresh sbt invocation and returns structured
diagnostics.

Recommended pattern:

> Edit files on disk, then call the metals tool you need. `compile-file` always reflects current disk content —
> treat it as the source of truth for diagnostics after an edit; no editor buffer or `didChange` is needed.
> For navigation (`get-usages`, go-to-definition) that you want to fully trust on a large codebase, run
> `compile-file` / `compile-module` first, then query. A compile is guaranteed to refresh the index and is
> cheap incrementally.

### If metals returns nothing, diagnose it — do not fall back to sbt

Empty results are **not** a warmup symptom. Distinguish the two states before deciding anything:

| Symptom | Meaning | Action |
| --- | --- | --- |
| `list-modules` returns an empty list | The build server never connected. | Run `just metals-bootstrap`, then `import-build`. |
| `compile-file` → `Compilation cancelled or incorrect file path` | Same — no build target exists for that file. | Same. |
| `no build target for: <path>` in `.metals/metals.log` | The file is outside this server's workspace (see the worktree note below). | Check which directory the server was launched against. |
| `list-modules` works but a compile is slow | Genuine cold compile. | Wait; it is doing real work. |

`.metals/metals.log` in the workspace root is authoritative — read it rather than guessing. A healthy start
reads `Connected to Build server: Bloop vX.Y.Z` → `Imported build in …` → `indexed workspace in …`.

**Do not silently switch to `sbt compile` when metals returns nothing.** That converts a five-second fix into
a permanently slower workflow and hides the breakage from everyone else. Diagnose, bootstrap, or say plainly
that metals is unavailable and why.

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
classic "another Metals server is already running" failure, and both sessions stomp each other's Bloop
compiles.

**Never run two agent sessions against the same checkout.** Use separate git worktrees instead.

### Separate worktrees each need their own bootstrap

Distinct directories get their own `.metals/` and `.bloop/`, so there is no DB lock and Bloop just treats each
as another project. Correctness is fine **because** the config uses the repo-relative `${CLAUDE_PROJECT_DIR:-.}`
rather than a hardcoded absolute path — an absolute path would funnel every worktree's session back onto one
directory and reintroduce the H2-lock problem. **Do not hardcode an absolute `--workspace` path.**

But because `.bloop/` and `.metals/` are gitignored (`.gitignore` lines 4–5 and 46), a new worktree starts with
neither. **Every fresh worktree needs its own `just metals-bootstrap`** — nothing about metals carries over
from the main checkout, and this is the single most common reason metals "works on main but not here."

The resource cost is real too: all worktrees share one global Bloop daemon on the machine. N concurrent cold
imports and N× JVM heap / concurrent full compiles can exhaust RAM/CPU, and Bloop serializes compile requests.
**Keep the number of simultaneously metals-active worktrees small.**

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
