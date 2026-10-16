"""The final restrictions queries on stage: resource counts, batched value counts of every class through a pool of
four workers (the permit limit of the value-count queries), and one drill-down page and count for the largest class.

Compares the summed batch rows of each class with the per-class value-count rows of the design measurement
(results/<label>-c5-C5.csv). The drill-down literal lists are every literal of the class's rows, a superset of the
restricted literals, so the drill-down timings are a worst case.

Usage: python3 -I bench_fix.py <label> <project-iri> <golden-dir> <results-dir>
Environment: SPARQL_ENDPOINT, STAGE_DB_PASSWORD.
"""

import concurrent.futures
import csv
import os
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import bench  # noqa: E402
import bench_batch  # noqa: E402

bench.CLIENT_TIMEOUT_S = 120
BATCH_SIZE = 2000
PERMITS = 4
GOLDEN_CLASS = "<http://www.knora.org/ontology/0001/anything#Thing>"
GOLDEN_PROJECT = "<http://rdfh.ch/projects/0001>"


def resource_counts_query(project: str) -> str:
    return bench.PREFIXES + f"""SELECT ?resClass ?permissions (COUNT(*) AS ?cnt)
WHERE {{
  ?resource knora-base:attachedToProject <{project}> .
  ?resource a ?resClass ;
    knora-base:hasPermissions ?permissions ;
    knora-base:isDeleted false .
}}
GROUP BY ?resClass ?permissions"""


def in_list(literals: set[str]) -> str:
    escaped = (lit.replace("\\", "\\\\").replace('"', '\\"') for lit in sorted(literals))
    return ", ".join(f'"{e}"' for e in escaped)


def drill_down(golden: str, project: str, res_class: str, resource_lits: set[str], value_lits: set[str]) -> str:
    query = golden.replace(GOLDEN_CLASS, f"<{res_class}>").replace(GOLDEN_PROJECT, f"<{project}>")
    marker = "STR(?permissions) IN ("
    first = query.index(marker) + len(marker)
    end = query.index("))", first)
    query = query[:first] + in_list(resource_lits) + query[end:]
    second = query.index(marker, first) + len(marker)
    end = query.index("))", second)
    return query[:second] + in_list(value_lits) + query[end:]


def main() -> None:
    label, project, golden_dir, out_dir = sys.argv[1:5]
    timings: list[list] = []

    seconds, status, rows = bench.run_query(resource_counts_query(project))
    timings.append(["resource-counts", "-", round(seconds, 3), status, len(rows)])
    print(f"  resource counts   {seconds:7.2f}s {status} rows={len(rows)}", flush=True)
    resource_classes = set()
    with open(os.path.join(out_dir, f"{label}-c5-resource-classes.csv")) as f:
        resource_classes = {r["resClass"] for r in csv.DictReader(f)}
    resource_lits: dict[str, set[str]] = {}
    sizes: dict[str, int] = {}
    for r in rows:
        if r["resClass"] in resource_classes:
            resource_lits.setdefault(r["resClass"], set()).add(r["permissions"])
            sizes[r["resClass"]] = sizes.get(r["resClass"], 0) + int(r["cnt"])
    classes = sorted(resource_lits)

    jobs: list[tuple[str, list[str]]] = []
    for c in classes:
        seconds, status, listed = bench.run_query(bench_batch.listing_query(project, c))
        timings.append(["listing", c, round(seconds, 3), status, len(listed)])
        iris = sorted(r["resource"] for r in listed)
        jobs += [(c, iris[i : i + BATCH_SIZE]) for i in range(0, len(iris), BATCH_SIZE)]

    bench.PAUSE_S = 0.0
    summed: dict[str, dict] = {c: {} for c in classes}
    slowest, failures = 0.0, 0
    start = time.perf_counter()
    with concurrent.futures.ThreadPoolExecutor(max_workers=PERMITS) as pool:
        futures = {pool.submit(bench.run_query, bench_batch.batch_query(project, c, b)): c for c, b in jobs}
        for future in concurrent.futures.as_completed(futures):
            c = futures[future]
            seconds, status, batch_rows = future.result()
            timings.append(["batch", c, round(seconds, 3), status, len(batch_rows)])
            slowest = max(slowest, seconds)
            failures += status != "ok"
            bench_batch.add_rows(summed[c], batch_rows)
    wall = time.perf_counter() - start
    print(f"  value counts      wall={wall:7.2f}s batches={len(jobs)} slowest={slowest:6.2f}s failures={failures}",
          flush=True)

    design: dict[str, dict] = {c: {} for c in classes}
    with open(os.path.join(out_dir, f"{label}-c5-C5.csv")) as f:
        for r in csv.DictReader(f):
            if r["resClass"] in design:
                design[r["resClass"]][(r["prop"], r["permissions"], r["valueDeleted"])] = [int(r["cnt"]), int(r["commented"])]
    differences = sum(bench_batch.differences(summed[c], design[c]) for c in classes)
    print(f"  comparison        classes={len(classes)} differences={differences}", flush=True)

    bench.PAUSE_S = 1.0
    largest = max(classes, key=lambda c: sizes[c])
    value_lits = {k[1] for k, v in summed[largest].items() if k[2] == "false"}
    for name in ("resourcePage-All", "resourceCount-All"):
        with open(os.path.join(golden_dir, f"ViewRestrictionsQuerySpec__{name}.txt")) as f:
            query = drill_down(f.read(), project, largest, resource_lits[largest], value_lits)
        seconds, status, drill_rows = bench.run_query(query)
        timings.append([name, largest, round(seconds, 3), status, len(drill_rows)])
        print(f"  {name:17s} {seconds:7.2f}s {status} rows={len(drill_rows)} class={largest}", flush=True)

    with open(os.path.join(out_dir, f"{label}-fix-timings.csv"), "w", newline="") as f:
        csv.writer(f).writerows([["step", "class", "seconds", "status", "rows"], *timings])
    with open(os.path.join(out_dir, f"{label}-fix-summary.csv"), "w", newline="") as f:
        csv.writer(f).writerows([
            ["classes", "batches", "value_wall_s", "slowest_batch_s", "failures", "differences"],
            [len(classes), len(jobs), round(wall, 3), round(slowest, 3), failures, differences],
        ])


if __name__ == "__main__":
    main()
