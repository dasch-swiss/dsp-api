#!/usr/bin/env python3
"""Analyse the internal package-dependency graph of the Scala modules.

Motivation (see Linear DEV-6776): Bazel compiles each module (`webapi`, `ingest`)
as ONE `scala_library` action, and Bazel targets must be acyclic. Any
strongly-connected cluster of packages is therefore inherently a single compile
action: touching any file recompiles the whole cluster, and no intra-module build
parallelism is possible. To split a module into smaller/parallel targets, the
package-level dependency cycles have to be broken first.

This script builds a coarse component graph (one node per top-level package, and
one per `slice.<x>` sub-package), then:

  1. computes strongly-connected components (Tarjan) — the "blobs" that cannot be
     split until their internal cycles are broken; and
  2. for a module with a declared layering (RANK below), lists the *back-edges* —
     imports that point from a lower layer UP to a higher one. Each back-edge is a
     cycle-maker; removing them turns a lower layer into a peelable foundation.

It is intentionally NOT a Bazel target and NOT a CI check — it's an on-demand
analysis helper. stdlib only, no third-party deps.

Usage:
    python3 tools/analysis/package_cycles.py            # both modules
    python3 tools/analysis/package_cycles.py webapi     # one module
    python3 tools/analysis/package_cycles.py --detail   # + per-file back-edge list

Re-run after each decoupling step: the SCC set should shrink and new leaf
packages become extractable as their own `scala_library` targets.
"""

from __future__ import annotations

import collections
import os
import re
import sys

# Top-level packages that are their own component (everything else directly under
# the module root collapses to "root", e.g. Main.scala, the package object).
WEBAPI_TOPLEVELS = {"messages", "store", "responders", "util", "core", "config"}
INGEST_TOPLEVELS = {"domain", "api", "infrastructure", "db", "config", "version"}

# Intended layering for webapi (lower rank = more foundational). A dependency
# should only ever point downward (to an equal-or-lower rank). An import whose
# target has a HIGHER rank than its source is a back-edge = a layering violation
# = a cycle-maker. `ingest` has no declared layering yet, so it gets SCC-only.
WEBAPI_RANK = {
    "config": 0,
    "util": 1, "messages": 1, "store": 1,
    "slice.common": 2, "slice.infrastructure": 2,
    "slice.security": 3, "slice.admin": 3, "slice.ontology": 3,
    "slice.resources": 3, "slice.search": 3, "slice.standoff": 3, "slice.export": 3,
    "responders": 4, "slice.api": 4,
    "core": 5, "root": 5,
}

MODULES = {
    "webapi": {
        "root": "modules/webapi/src/main/scala/org/knora/webapi",
        "prefix": "org.knora.webapi",
        "toplevels": WEBAPI_TOPLEVELS,
        "rank": WEBAPI_RANK,
    },
    "ingest": {
        "root": "modules/ingest/src/main/scala/swiss/dasch",
        "prefix": "swiss.dasch",
        "toplevels": INGEST_TOPLEVELS,
        "rank": None,
    },
}


def component_of_path(path: str, root: str, toplevels: set[str]) -> str:
    parts = path[len(root) + 1:].split(os.sep)
    if parts[0] == "slice" and len(parts) > 1:
        return "slice." + parts[1]
    if parts[0] in toplevels:
        return parts[0]
    return "root" if len(parts) == 1 else parts[0]


def component_of_fqn(fqn: str, prefix: str, toplevels: set[str]) -> str:
    # Strip Scala-3 backtick escaping (e.g. the `export` package is a keyword and
    # is always written `slice.`export``); a naive parser silently drops it.
    seg = [s.strip("`") for s in fqn[len(prefix) + 1:].split(".")]
    if seg[0] == "slice" and len(seg) > 1 and seg[1]:
        return "slice." + seg[1]
    if seg[0] in toplevels:
        return seg[0]
    return "root"


def build_graph(cfg: dict):
    root, prefix, toplevels = cfg["root"], cfg["prefix"], cfg["toplevels"]
    imp = re.compile(
        r"import\s+(" + re.escape(prefix) + r"(?:\.(?:`[^`]+`|[A-Za-z0-9_]+))+)"
    )
    files = [
        os.path.join(dp, fn)
        for dp, _, fns in os.walk(root)
        for fn in fns
        if fn.endswith(".scala")
    ]
    adj = collections.defaultdict(set)                 # component -> {component}
    detail = collections.defaultdict(list)             # (src,dst) -> [(file, symbol)]
    for f in files:
        src = component_of_path(f, root, toplevels)
        with open(f, encoding="utf-8", errors="ignore") as fh:
            for line in fh:
                for m in imp.finditer(line):
                    fqn = m.group(1)
                    dst = component_of_fqn(fqn, prefix, toplevels)
                    if dst != src:
                        adj[src].add(dst)
                        detail[(src, dst)].append(
                            (f[len(root) + 1:], fqn[len(prefix) + 1:])
                        )
    return adj, detail, len(files)


def sccs(adj: dict) -> list[list[str]]:
    """Tarjan's strongly-connected components (iterative, no recursion limit)."""
    index: dict[str, int] = {}
    low: dict[str, int] = {}
    on_stack: dict[str, bool] = {}
    stack: list[str] = []
    out: list[list[str]] = []
    counter = [0]
    nodes = set(adj) | {d for s in adj for d in adj[s]}

    def strongconnect(root_node: str):
        work = [(root_node, iter(sorted(adj.get(root_node, ()))))]
        index[root_node] = low[root_node] = counter[0]
        counter[0] += 1
        stack.append(root_node)
        on_stack[root_node] = True
        while work:
            v, it = work[-1]
            advanced = False
            for w in it:
                if w not in index:
                    index[w] = low[w] = counter[0]
                    counter[0] += 1
                    stack.append(w)
                    on_stack[w] = True
                    work.append((w, iter(sorted(adj.get(w, ())))))
                    advanced = True
                    break
                elif on_stack.get(w):
                    low[v] = min(low[v], index[w])
            if advanced:
                continue
            if low[v] == index[v]:
                comp = []
                while True:
                    w = stack.pop()
                    on_stack[w] = False
                    comp.append(w)
                    if w == v:
                        break
                out.append(comp)
            work.pop()
            if work:
                parent = work[-1][0]
                low[parent] = min(low[parent], low[v])

    for n in sorted(nodes):
        if n not in index:
            strongconnect(n)
    return out


def report(name: str, cfg: dict, show_detail: bool):
    adj, detail, nfiles = build_graph(cfg)
    comps = sccs(adj)
    print(f"\n{'=' * 72}\n{name.upper()}: {nfiles} source files, {len(comps)} SCCs\n{'=' * 72}")
    for comp in sorted(comps, key=len, reverse=True):
        tag = "   <-- CYCLIC BLOB (one Bazel compile action)" if len(comp) > 1 else ""
        print(f"  size {len(comp):2d}: {sorted(comp)}{tag}")

    rank = cfg["rank"]
    if not rank:
        print(f"\n  ({name} has no declared layering — SCC only.)")
        return

    print(f"\n  --- back-edges (source depends UP on a higher layer) ---")
    # group by source component, foundation-first
    srcs = sorted({s for (s, _) in detail}, key=lambda c: rank.get(c, 9))
    for src in srcs:
        be = [
            (d, detail[(src, d)])
            for (s, d) in detail
            if s == src and rank.get(d, 9) > rank.get(src, 0)
        ]
        if not be:
            continue
        total = sum(len(l) for _, l in be)
        nf = len({f for _, l in be for f, _ in l})
        print(f"\n  {src} (rank {rank[src]}): {total} back-edge imports across {nf} files")
        for d, lst in sorted(be, key=lambda r: -len(r[1])):
            files_hit = len({f for f, _ in lst})
            print(f"      -> {d} (rank {rank[d]}): {len(lst)} imports / {files_hit} files")
        if show_detail:
            by_file = collections.defaultdict(set)
            for d, lst in be:
                for f, sym in lst:
                    by_file[f].add(sym)
            for f in sorted(by_file):
                print(f"        {f}")
                for sym in sorted(by_file[f]):
                    print(f"            -> {sym}")


def main(argv: list[str]):
    show_detail = "--detail" in argv
    wanted = [a for a in argv[1:] if not a.startswith("-")]
    targets = wanted or list(MODULES)
    for name in targets:
        if name not in MODULES:
            print(f"unknown module '{name}'; known: {', '.join(MODULES)}", file=sys.stderr)
            return 2
        report(name, MODULES[name], show_detail)
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
