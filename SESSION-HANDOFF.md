# SESSION-HANDOFF — dsp-api modularization / dependency cleanup

**Linear:** [DEV-6776](https://linear.app/dasch/issue/DEV-6776/dsp-api-modularization-dependency-cleanup-break-package-cycles)
**Branch:** `feature/dev-6776-dsp-api-modularization-dependency-cleanup-break-package`
**Status:** groundwork only — analysis tooling + this handoff. No refactoring done yet.

## Why this work exists

The Bazel build compiles each module (`webapi`, `ingest`) as **one** `scala_library`
action. Bazel targets must be acyclic, so a strongly-connected cluster of packages is
inherently a single compile action: touching any file recompiles the whole cluster, and
there is no intra-module build parallelism.

Current reality (run `tools/analysis/package_cycles.py` for live numbers):

- **webapi** — ~430 source files collapse into **one ~429-file cyclic blob** (`core`,
  `messages`, `store`, `responders`, `util`, and every `slice.*`). Only `config` (1 file)
  sits outside it.
- **ingest** — ~60 files → **one ~54-file cyclic blob** (`api`, `domain`,
  `infrastructure`) plus `config`, `db`, `version` sinks.

No build-file trick splits a cyclic blob. To get parallel Bazel targets (and, downstream,
faster incremental rebuilds, independent testability, eventual sbt subprojects), the
**package dependency cycles must be broken first**. That is the whole point of this issue.

### Precedent that motivated it

The related quick win already merged to `main`: the generated `BuildInfo.scala` used to be
compiled *inside* each module's monolithic `scala_library` (`srcs`). Its git-stamped
version changes every push, so the single compile action's input hash changed and the
**entire module recompiled on every push**. Fix (commit `ee251af5f`,
`modules/{webapi,ingest}/BUILD.bazel`): move `BuildInfo` into its own tiny `scala_library`
that the module depends on. rules_scala hands downstream the *interface jar*
(`buildinfo-ijar.jar`); the stamped values live only in `<clinit>` with no `ConstantValue`
attribute, so the ijar is byte-stable and the module cache-hits. **This is the reference
example** for how splitting a target buys cache isolation — the same mechanic is why
breaking cycles will pay off.

## The goal

Refactor `webapi` (and later `ingest`) so packages form an acyclic layering, then peel the
now-independent leaves into their own `scala_library` targets. Success is measured
mechanically: each decoupling step shrinks the SCC set (re-run the script), and eventually
a package can be extracted as its own target and shown to cache-hit independently.

Intended layering (bottom → top; dependencies only ever point downward):

```
config
  └─ util · messages · store            (foundation: types, domain models, persistence)
       └─ slice.common                   (shared modern base)
            └─ slice.admin · ontology · resources · search · standoff · security · infrastructure
                 └─ responders · slice.api
                      └─ core · Main
```

## The tool: `tools/analysis/package_cycles.py`

stdlib-only, not a Bazel target, not a CI check. On-demand analysis helper.

```
python3 tools/analysis/package_cycles.py            # both modules: SCCs + back-edges
python3 tools/analysis/package_cycles.py webapi      # one module
python3 tools/analysis/package_cycles.py --detail    # + per-file, per-symbol back-edge list
```

It builds a coarse component graph (one node per top-level package + per `slice.<x>`),
computes SCCs (Tarjan), and — for webapi, which has a declared `RANK` layering — lists the
**back-edges**: imports pointing from a lower layer up to a higher one. Each back-edge is a
cycle-maker; the counts are a heat map for sequencing. The layering (`WEBAPI_RANK`) is
editable in the script as packages move.

Caveat worth knowing: the parser handles Scala-3 backtick-escaped package names (the
`export` keyword is always written `` slice.`export` ``); a naive import regex silently
drops those edges (this bit the first draft of the analysis and made `export` look like a
leaf when it is in the blob).

## Where the cycles are (heat map, sequence foundation-first)

Full file/symbol detail is in DEV-6776 and via `--detail`. Highest-leverage first:

1. **`root` package-object primitives** — `IRI` (type alias) and schema enums
   (`ApiV2Schema`, `InternalSchema`, `ApiV2Complex/Simple`, `SchemaOptions`, `Rendering`,
   `SchemaRendering`) live at `org.knora.webapi.*` and are imported *down* into
   `messages`/`store`/`slice.common`. Relocating them to a foundation location erases a
   whole class of back-edges cheaply. Do this first.
2. **`util → slice.common`** — 1 file (`util/ApacheLuceneSupport.scala`). Warm-up.
3. **`store → slice.common` / `slice.admin`** (~37 imports) — two clusters: triplestore
   upgrade plugins referencing `slice.admin.AdminConstants` / `slice.common...Vocabulary`,
   and Sipi/triplestore services referencing `slice.admin`/`slice.api`/`slice.infrastructure`.
4. **`messages → slice.*`** (~140 imports, ~37 files) — the big one. Legacy message classes
   embed modern value objects (`ProjectIri`, `User`, `Group`, `Permission`, `KnoraIris.*`,
   `Cardinality`, `LanguageCode`) and API DTOs (`slice.api.admin.model.Project`). Likely
   resolved by moving those value objects into a foundation both `messages` and the slices
   depend on downward. Worst files: `ValueMessagesV2`, `ConstructResponseUtilV2`,
   `ResourceMessagesV2`, `OntologyMessagesV2`, `PermissionUtilADM`, `StringFormatter`,
   the `gravsearch` utils.
5. **`slice.common → slice.admin` / `slice.api`** (~50 imports) — invert so `common` holds
   no feature/DTO deps; the modern-slice cycle collapses next.

Note: `responders` is legacy glue. Its only *upward* edges are to the `root` package object;
`responders → slice.*` is forward/expected. Long-term it should shrink toward deletion, not
be re-layered — don't spend effort re-layering it.

## How to make progress (suggested loop)

1. Pick the next item above. Run `package_cycles.py --detail webapi` to get exact sites.
2. Move the shared type down (or invert the dependency). Keep the change small and typed.
3. Recompile: prefer the Metals MCP (`compile-module`) over `sbt compile`. **sbt must stay
   green** — it remains the Scala build of record during the Bazel validation window and is
   Zinc-incremental, so it never had the recompile problem; this work is for Bazel's benefit.
4. Re-run `package_cycles.py`: confirm the targeted back-edge count dropped and, ideally,
   the SCC shrank.
5. When a package finally has no back-edges and nothing depends back on it, extract it as
   its own `scala_library` in the module `BUILD.bazel` (pattern: the buildinfo split) and
   confirm the ijar-isolation behaviour.

## Constraints / gotchas

- **Do not rename `Knora` packages/classes** (`org.knora.webapi`, `KnoraXxx`) — allowed in
  code, but no refactoring-rename without an explicit task. Moving *types between packages*
  for layering is in scope; wholesale renames are not.
- **No "Knora" in human-readable text** (commits, PR text, docs) — use "dsp-api".
- Keep the layering model (`WEBAPI_RANK`) in the script updated as packages move, or the
  back-edge classification drifts.
- Numbers in DEV-6776 were captured at `ee251af5f`; `main` has moved a few PRs ahead, so
  live counts differ slightly. The script is the source of truth, not the issue snapshot.

## State of this branch

- `tools/analysis/package_cycles.py` — the analysis tool (new).
- `SESSION-HANDOFF.md` — this file (new).
- Nothing else. No source refactoring yet. Draft PR opened for continuation.
