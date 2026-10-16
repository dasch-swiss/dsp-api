"""Batched value counts: list the live resources of one class, then count their values in batches of resource IRIs
with VALUES ?resource first, and compare the summed batch rows with one unbatched value-count query.

Queries run one at a time, with the pause of bench.run_query. Stops a batch size at its first failed query.

Usage:
  python3 -I bench_batch.py project <shortcode>
  python3 -I bench_batch.py run <label> <project-iri> <class-iri> <sizes, comma-separated> <results-dir>

Environment: SPARQL_ENDPOINT, STAGE_DB_PASSWORD.
"""

import csv
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import bench  # noqa: E402
import bench_c5  # noqa: E402

bench.CLIENT_TIMEOUT_S = 120

Key = tuple[str, str, str]


def project_query(shortcode: str) -> str:
    return bench.PREFIXES + f"""PREFIX knora-admin: <http://www.knora.org/ontology/knora-admin#>
SELECT ?project WHERE {{ ?project knora-admin:projectShortcode "{shortcode}" . }}"""


def listing_query(project: str, res_class: str) -> str:
    return bench.PREFIXES + f"""SELECT ?resource
WHERE {{
  ?resource a <{res_class}> .
  ?resource knora-base:attachedToProject <{project}> .
  ?resource knora-base:isDeleted false .
}}"""


def batch_query(project: str, res_class: str, resources: list[str]) -> str:
    values = " ".join(f"<{r}>" for r in resources)
    return bench.PREFIXES + f"""SELECT ?prop ?permissions ?valueDeleted (COUNT(*) AS ?cnt) (COUNT(?comment) AS ?commented)
WHERE {{
  VALUES ?resource {{ {values} }}
  ?resource a <{res_class}> .
  ?resource knora-base:attachedToProject <{project}> .
  ?resource knora-base:isDeleted false .
  ?resource ?prop ?value .
  ?value knora-base:hasPermissions ?permissions .
  ?value knora-base:isDeleted ?valueDeleted .
  OPTIONAL {{ ?value knora-base:valueHasComment ?comment . }}
}}
GROUP BY ?prop ?permissions ?valueDeleted"""


def add_rows(total: dict[Key, list[int]], rows: list[dict]) -> None:
    for r in rows:
        counts = total.setdefault((r["prop"], r["permissions"], r["valueDeleted"]), [0, 0])
        counts[0] += int(r["cnt"])
        counts[1] += int(r["commented"])


def differences(a: dict[Key, list[int]], b: dict[Key, list[int]]) -> int:
    return sum(1 for k in set(a) | set(b) if a.get(k) != b.get(k))


def run(label: str, project: str, res_class: str, sizes: list[int], out_dir: str) -> None:
    os.makedirs(out_dir, exist_ok=True)
    timings: list[list] = []

    seconds, status, rows = bench.run_query(listing_query(project, res_class))
    resources = sorted(r["resource"] for r in rows)
    timings.append(["listing", "-", 1, round(seconds, 3), status, len(resources)])
    print(f"  listing          {seconds:7.2f}s {status} resources={len(resources)}", flush=True)
    if status != "ok":
        return

    seconds, status, rows = bench.run_query(bench_c5.c5_query(project, res_class))
    unbatched: dict[Key, list[int]] = {}
    add_rows(unbatched, rows)
    timings.append(["unbatched", "-", 1, round(seconds, 3), status, len(rows)])
    print(f"  unbatched        {seconds:7.2f}s {status} rows={len(rows)}", flush=True)

    summary: list[list] = []
    for size in sizes:
        summed: dict[Key, list[int]] = {}
        batches = [resources[i : i + size] for i in range(0, len(resources), size)]
        slowest, total_s, failed = 0.0, 0.0, None
        for n, batch in enumerate(batches, start=1):
            seconds, status, rows = bench.run_query(batch_query(project, res_class, batch))
            timings.append(["batch", size, n, round(seconds, 3), status, len(rows)])
            if status != "ok":
                failed = f"batch {n}: {status}"
                break
            add_rows(summed, rows)
            slowest, total_s = max(slowest, seconds), total_s + seconds
        diff = differences(summed, unbatched) if failed is None and unbatched else "-"
        summary.append([size, len(batches), round(slowest, 3), round(total_s, 3), failed or "ok", diff])
        print(f"  size {size:5d}  batches={len(batches):4d} slowest={slowest:6.2f}s total={total_s:7.2f}s "
              f"{failed or 'ok'} differences={diff}", flush=True)

    with open(os.path.join(out_dir, f"{label}-batch-timings.csv"), "w", newline="") as f:
        csv.writer(f).writerows([["step", "size", "n", "seconds", "status", "rows"], *timings])
    with open(os.path.join(out_dir, f"{label}-batch-summary.csv"), "w", newline="") as f:
        csv.writer(f).writerows([["size", "batches", "slowest_s", "total_s", "status", "differences"], *summary])


def main() -> None:
    if sys.argv[1] == "project":
        _, status, rows = bench.run_query(project_query(sys.argv[2]))
        print(status, [r["project"] for r in rows])
    else:
        _, label, project, res_class, sizes, out_dir = sys.argv[1:7]
        print(f"== {label} {res_class}", flush=True)
        run(label, project, res_class, [int(s) for s in sizes.split(",")], out_dir)


if __name__ == "__main__":
    main()
