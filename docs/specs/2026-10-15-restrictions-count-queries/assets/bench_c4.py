"""Option C4: one value-count query per class, anchored on the bound class, grouped by property and permission.

Stops the whole run at the first failed query, so that no long query is repeated on the shared server.

Usage: python3 -I bench_c4.py <label> <project-iri> <runs> <results-dir>
"""

import csv
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import bench  # noqa: E402

bench.CLIENT_TIMEOUT_S = 120


def c4_query(project: str, res_class: str) -> str:
    return bench.PREFIXES + f"""SELECT ?prop ?permissions (COUNT(*) AS ?cnt) (COUNT(?comment) AS ?commented)
WHERE {{
  ?resource a <{res_class}> .
  ?resource knora-base:attachedToProject <{project}> .
  ?resource knora-base:isDeleted false .
  ?resource ?prop ?value .
  ?value knora-base:hasPermissions ?permissions .
  ?value knora-base:isDeleted false .
  OPTIONAL {{ ?value knora-base:valueHasComment ?comment . }}
}}
GROUP BY ?prop ?permissions"""


def main() -> None:
    label, project, runs, results = sys.argv[1], sys.argv[2], int(sys.argv[3]), sys.argv[4]
    with open(os.path.join(results, f"{label}-before-classes.csv")) as f:
        classes = sorted(r["resClass"] for r in csv.DictReader(f))
    rec = bench.Recorder(results, label, "c4")
    print(f"== {label} C4 ({len(classes)} classes)", flush=True)
    for run in range(1, runs + 1):
        r_rows = rec.timed(run, "R", "R resource counts", bench.resource_counts_r(project))
        c4_rows = []
        for c in classes:
            rows = rec.timed(run, "C4-class", f"C4 {c}", c4_query(project, c))
            if rec.timings[-1]["status"] != "ok":
                rec.save_timings()
                sys.exit(f"stopped: C4 failed for {c}")
            c4_rows += [{"resClass": c, **r} for r in rows]
        if run == 1:
            rec.save_rows("R", r_rows)
            rec.save_rows("C4", c4_rows)
            props = sorted({r["prop"] for r in c4_rows})
            _, _, cls = bench.run_query(bench.property_classification_query(props))
            rec.save_rows("prop-classification", cls)
            all_classes = sorted({r["resClass"] for r in r_rows})
            _, _, rc = bench.run_query(bench.resource_classes_query(all_classes))
            rec.save_rows("resource-classes", rc)
        rec.save_timings()
        total = sum(float(t["seconds"]) for t in rec.timings if t["run"] == run and t["step"] == "C4-class")
        print(f"  run {run} C4 total {total:.1f}s", flush=True)


if __name__ == "__main__":
    main()
