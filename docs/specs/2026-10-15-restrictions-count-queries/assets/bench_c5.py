"""Options C5 (per class) and C6 (per project): value counts by property and permission, with the value
delete flag grouped instead of bound, so that Jena cannot evaluate it before ?value is bound.

Stops at the first failed query. Usage: python3 -I bench_c5.py <label> <project-iri> <runs> <results-dir>
"""

import csv
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import bench  # noqa: E402

bench.CLIENT_TIMEOUT_S = 120


def c5_query(project: str, res_class: str) -> str:
    return bench.PREFIXES + f"""SELECT ?prop ?permissions ?valueDeleted (COUNT(*) AS ?cnt) (COUNT(?comment) AS ?commented)
WHERE {{
  ?resource a <{res_class}> .
  ?resource knora-base:attachedToProject <{project}> .
  ?resource knora-base:isDeleted false .
  ?resource ?prop ?value .
  ?value knora-base:hasPermissions ?permissions .
  ?value knora-base:isDeleted ?valueDeleted .
  OPTIONAL {{ ?value knora-base:valueHasComment ?comment . }}
}}
GROUP BY ?prop ?permissions ?valueDeleted"""


def c6_query(project: str) -> str:
    return bench.PREFIXES + f"""SELECT ?resClass ?prop ?permissions ?valueDeleted (COUNT(*) AS ?cnt) (COUNT(?comment) AS ?commented)
WHERE {{
  ?resource knora-base:attachedToProject <{project}> .
  ?resource a ?resClass .
  ?resource knora-base:isDeleted false .
  ?resource ?prop ?value .
  ?value knora-base:hasPermissions ?permissions .
  ?value knora-base:isDeleted ?valueDeleted .
  OPTIONAL {{ ?value knora-base:valueHasComment ?comment . }}
}}
GROUP BY ?resClass ?prop ?permissions ?valueDeleted"""


def check(rec: bench.Recorder, what: str) -> None:
    if rec.timings[-1]["status"] != "ok":
        rec.save_timings()
        sys.exit(f"stopped: {what} failed")


def main() -> None:
    label, project, runs, results = sys.argv[1], sys.argv[2], int(sys.argv[3]), sys.argv[4]
    with open(os.path.join(results, f"{label}-before-classes.csv")) as f:
        classes = sorted(r["resClass"] for r in csv.DictReader(f))
    rec = bench.Recorder(results, label, "c5")
    print(f"== {label} C5/C6 ({len(classes)} classes)", flush=True)
    for run in range(1, runs + 1):
        r_rows = rec.timed(run, "R", "R resource counts", bench.resource_counts_r(project))
        check(rec, "R")
        c5_rows = []
        for c in classes:
            rows = rec.timed(run, "C5-class", f"C5 {c}", c5_query(project, c))
            check(rec, f"C5 {c}")
            c5_rows += [{"resClass": c, **r} for r in rows]
        c6_rows = rec.timed(run, "C6-project", "C6 project matrix", c6_query(project))
        check(rec, "C6")
        if run == 1:
            rec.save_rows("R", r_rows)
            rec.save_rows("C5", c5_rows)
            rec.save_rows("C6", c6_rows)
            props = sorted({r["prop"] for r in c6_rows} | {r["prop"] for r in c5_rows})
            _, _, cls = bench.run_query(bench.property_classification_query(props))
            rec.save_rows("prop-classification", cls)
            all_classes = sorted({r["resClass"] for r in r_rows} | {r["resClass"] for r in c6_rows})
            _, _, rc = bench.run_query(bench.resource_classes_query(all_classes))
            rec.save_rows("resource-classes", rc)
        rec.save_timings()
        c5_total = sum(float(t["seconds"]) for t in rec.timings if t["run"] == run and t["step"] == "C5-class")
        print(f"  run {run} C5 total {c5_total:.1f}s", flush=True)


if __name__ == "__main__":
    main()
