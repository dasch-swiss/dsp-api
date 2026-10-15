"""Benchmark the restrictions-overview count queries against a Fuseki endpoint.

Phase "before" runs the count queries as rendered on dsp-api main (72dd89360).
Phase "after" runs the option C matrix variants.
All queries are read-only SELECTs and run one at a time.

Environment: SPARQL_ENDPOINT, STAGE_DB_PASSWORD.
Usage: python3 -I bench.py <before|after> <label> <project-iri> <runs> <out-dir>
"""

import base64
import csv
import json
import os
import statistics
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

PREFIXES = (
    "PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>\n"
    "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\n\n"
)
CLIENT_TIMEOUT_S = 300
PAUSE_S = 1.0


def run_query(query: str) -> tuple[float, str, list[dict]]:
    endpoint = os.environ["SPARQL_ENDPOINT"]
    token = base64.b64encode(f"admin:{os.environ['STAGE_DB_PASSWORD']}".encode()).decode()
    body = urllib.parse.urlencode({"query": query}).encode()
    request = urllib.request.Request(
        endpoint,
        data=body,
        headers={
            "Authorization": f"Basic {token}",
            "Accept": "application/sparql-results+json",
            "Content-Type": "application/x-www-form-urlencoded",
        },
    )
    time.sleep(PAUSE_S)
    start = time.perf_counter()
    try:
        with urllib.request.urlopen(request, timeout=CLIENT_TIMEOUT_S) as response:
            payload = json.load(response)
        elapsed = time.perf_counter() - start
        rows = [{k: v["value"] for k, v in b.items()} for b in payload["results"]["bindings"]]
        return elapsed, "ok", rows
    except urllib.error.HTTPError as e:
        return time.perf_counter() - start, f"http-{e.code}", []
    except TimeoutError:
        return time.perf_counter() - start, "client-timeout", []
    except urllib.error.URLError as e:
        return time.perf_counter() - start, f"error-{e.reason}", []


# --- queries as rendered on main -------------------------------------------------------------


def project_classes_query(project: str) -> str:
    return PREFIXES + f"""SELECT DISTINCT ?resClass
WHERE {{
  {{
    SELECT DISTINCT ?resClass
    WHERE {{
      ?resource knora-base:attachedToProject <{project}> ;
        a ?resClass .
    }}
  }}
  ?resClass rdfs:subClassOf* knora-base:Resource .
}}"""


def any_multi_typed_query(project: str) -> str:
    return PREFIXES + f"""SELECT ?resource
WHERE {{
  ?resource a ?c1 ;
    knora-base:attachedToProject <{project}> ;
    knora-base:isDeleted false ;
    a ?c2 .
  FILTER (?c1 != ?c2)
}}
LIMIT 1"""


def multi_typed_query(project: str) -> str:
    return PREFIXES + f"""SELECT ?resource
WHERE {{
  ?resource a ?resClass ;
    knora-base:attachedToProject <{project}> ;
    knora-base:isDeleted false ;
    a ?subClass .
  ?subClass rdfs:subClassOf+ ?resClass .
}}
LIMIT 1"""


def values_clause(classes: list[str]) -> str:
    if not classes:
        return ""
    return "VALUES ?resClass { " + " ".join(f"<{c}>" for c in classes) + " }"


def res_class_patterns(classes: list[str], multi_typed: bool) -> str:
    lines = []
    if not classes:
        lines.append("?resClass rdfs:subClassOf* knora-base:Resource .")
    if multi_typed:
        lines.append(
            "FILTER NOT EXISTS {\n  ?resource a ?subClass .\n  ?subClass rdfs:subClassOf+ ?resClass .\n}"
        )
    return "\n  ".join(lines)


def resource_counts_query(project: str, classes: list[str], multi_typed: bool) -> str:
    return PREFIXES + f"""SELECT ?resClass ?permissions (COUNT(DISTINCT ?resource) AS ?cnt)
WHERE {{
  {values_clause(classes)}
  ?resource a ?resClass ;
    knora-base:attachedToProject <{project}> ;
    knora-base:hasPermissions ?permissions ;
    knora-base:isDeleted false .
  {res_class_patterns(classes, multi_typed)}
}}
GROUP BY ?resClass ?permissions"""


def value_counts_by_class_query(project: str, group_class: str, classes: list[str], multi_typed: bool) -> str:
    return PREFIXES + f"""SELECT ?permissions (COUNT(DISTINCT ?value) AS ?cnt)
WHERE {{
  {values_clause(classes)}
  ?resource a ?resClass ;
    knora-base:attachedToProject <{project}> ;
    knora-base:isDeleted false .
  {res_class_patterns(classes, multi_typed)}
  {{
    ?resource ?prop ?value .
    ?prop rdfs:subPropertyOf* knora-base:hasValue .
  }}
  ?value knora-base:hasPermissions ?permissions ;
    knora-base:isDeleted false .
  FILTER NOT EXISTS {{ ?value a knora-base:LinkValue . }}
  FILTER (?resClass = <{group_class}>)
}}
GROUP BY ?permissions"""


def value_counts_by_property_query(project: str, prop: str) -> str:
    return PREFIXES + f"""SELECT ?permissions (COUNT(DISTINCT ?value) AS ?cnt)
WHERE {{
  ?resource knora-base:attachedToProject <{project}> ;
    knora-base:isDeleted false ;
    <{prop}> ?value .
  ?value knora-base:hasPermissions ?permissions ;
    knora-base:isDeleted false .
  FILTER NOT EXISTS {{ ?value a knora-base:LinkValue . }}
}}
GROUP BY ?permissions"""


def project_value_properties_query(project: str) -> str:
    """Untimed stand-in for the ontology-cache read in projectValueProperties (isValueProperty)."""
    return PREFIXES + f"""PREFIX owl: <http://www.w3.org/2002/07/owl#>
SELECT DISTINCT ?prop
WHERE {{
  {{ ?onto knora-base:attachedToProject <{project}> . }}
  UNION
  {{ BIND(<http://www.knora.org/ontology/knora-base> AS ?onto) }}
  GRAPH ?onto {{ ?prop rdfs:subPropertyOf ?anySuper . }}
  ?prop rdfs:subPropertyOf* knora-base:hasValue .
  FILTER NOT EXISTS {{ ?prop rdfs:subPropertyOf* knora-base:hasLinkToValue . }}
}}"""


# --- option C ------------------------------------------------------------------------------


def matrix_c1(project: str) -> str:
    return PREFIXES + f"""SELECT ?resClass ?prop ?permissions (COUNT(*) AS ?cnt) (COUNT(?comment) AS ?commented)
WHERE {{
  ?resource knora-base:attachedToProject <{project}> .
  ?resource a ?resClass ;
    ?prop ?value .
  ?value knora-base:hasPermissions ?permissions .
  ?value knora-base:isDeleted false .
  ?resource knora-base:isDeleted false .
  OPTIONAL {{ ?value knora-base:valueHasComment ?comment . }}
}}
GROUP BY ?resClass ?prop ?permissions"""


def matrix_c2(project: str, props: list[str]) -> str:
    values = "VALUES ?prop { " + " ".join(f"<{p}>" for p in props) + " }"
    return PREFIXES + f"""SELECT ?resClass ?prop ?permissions (COUNT(*) AS ?cnt) (COUNT(?comment) AS ?commented)
WHERE {{
  ?resource knora-base:attachedToProject <{project}> .
  {values}
  ?resource a ?resClass ;
    ?prop ?value .
  ?value knora-base:hasPermissions ?permissions .
  ?value knora-base:isDeleted false .
  ?resource knora-base:isDeleted false .
  OPTIONAL {{ ?value knora-base:valueHasComment ?comment . }}
}}
GROUP BY ?resClass ?prop ?permissions"""


def matrix_c3(project: str) -> str:
    return PREFIXES + f"""SELECT ?resClass ?prop ?permissions (COUNT(*) AS ?cnt) (COUNT(?comment) AS ?commented)
WHERE {{
  {{
    SELECT ?resource
    WHERE {{ ?resource knora-base:attachedToProject <{project}> . }}
  }}
  ?resource a ?resClass ;
    ?prop ?value .
  ?value knora-base:hasPermissions ?permissions .
  ?value knora-base:isDeleted false .
  ?resource knora-base:isDeleted false .
  OPTIONAL {{ ?value knora-base:valueHasComment ?comment . }}
}}
GROUP BY ?resClass ?prop ?permissions"""


def resource_counts_r(project: str) -> str:
    return PREFIXES + f"""SELECT ?resClass ?permissions (COUNT(*) AS ?cnt)
WHERE {{
  ?resource knora-base:attachedToProject <{project}> .
  ?resource a ?resClass ;
    knora-base:hasPermissions ?permissions ;
    knora-base:isDeleted false .
}}
GROUP BY ?resClass ?permissions"""


def property_classification_query(props: list[str]) -> str:
    """Untimed: classify the properties the matrix returned (Scala would read the ontology cache)."""
    values = "VALUES ?prop { " + " ".join(f"<{p}>" for p in props) + " }"
    return PREFIXES + f"""SELECT ?prop
  (EXISTS {{ ?prop rdfs:subPropertyOf* knora-base:hasValue }} AS ?isValue)
  (EXISTS {{ ?prop rdfs:subPropertyOf* knora-base:hasLinkToValue }} AS ?isLinkValue)
  (EXISTS {{ ?prop rdfs:subPropertyOf* knora-base:hasStandoffLinkToValue }} AS ?isStandoffLinkValue)
  (EXISTS {{ ?prop rdfs:subPropertyOf* knora-base:hasFileValue }} AS ?isFileValue)
WHERE {{ {values} }}"""


def resource_classes_query(classes: list[str]) -> str:
    """Untimed: which matrix classes are resource classes (Scala would read the ontology cache)."""
    values = "VALUES ?resClass { " + " ".join(f"<{c}>" for c in classes) + " }"
    return PREFIXES + f"""SELECT ?resClass
WHERE {{ {values} ?resClass rdfs:subClassOf* knora-base:Resource . }}"""


# --- runner --------------------------------------------------------------------------------


class Recorder:
    def __init__(self, out_dir: str, label: str, phase: str):
        self.out_dir = out_dir
        self.prefix = f"{label}-{phase}"
        self.timings: list[dict] = []
        os.makedirs(out_dir, exist_ok=True)

    def timed(self, run: int, step: str, name: str, query: str) -> list[dict]:
        elapsed, status, rows = run_query(query)
        self.timings.append(
            {"run": run, "step": step, "query": name, "seconds": f"{elapsed:.3f}", "status": status, "rows": len(rows)}
        )
        print(f"  run {run} {step:<14} {name[:70]:<70} {elapsed:8.2f}s {status} rows={len(rows)}", flush=True)
        return rows

    def save_rows(self, name: str, rows: list[dict]) -> None:
        path = os.path.join(self.out_dir, f"{self.prefix}-{name}.csv")
        keys = sorted({k for r in rows for k in r})
        with open(path, "w", newline="") as f:
            writer = csv.DictWriter(f, fieldnames=keys)
            writer.writeheader()
            writer.writerows(rows)

    def save_timings(self) -> None:
        path = os.path.join(self.out_dir, f"{self.prefix}-timings.csv")
        with open(path, "w", newline="") as f:
            writer = csv.DictWriter(f, fieldnames=["run", "step", "query", "seconds", "status", "rows"])
            writer.writeheader()
            writer.writerows(self.timings)


def before(rec: Recorder, project: str, runs: int) -> None:
    _, status, prop_rows = run_query(project_value_properties_query(project))
    props = sorted(r["prop"] for r in prop_rows)
    print(f"  {len(props)} value properties from the ontology ({status})", flush=True)
    for run in range(1, runs + 1):
        classes = [r["resClass"] for r in rec.timed(run, "step1", "projectClassesQuery", project_classes_query(project))]
        any_multi = bool(rec.timed(run, "step1", "anyMultiTypedResourceQuery", any_multi_typed_query(project)))
        multi = any_multi and bool(rec.timed(run, "step1", "multiTypedQuery", multi_typed_query(project)))
        res_rows = rec.timed(
            run, "step1", "resourceCountsByClassAndPermissionQuery", resource_counts_query(project, classes, multi)
        )
        value_rows = []
        for c in sorted(classes):
            rows = rec.timed(
                run, "step2-class", f"valueCountsByPermissionQuery {c}", value_counts_by_class_query(project, c, classes, multi)
            )
            value_rows += [{"resClass": c, **r} for r in rows]
        prop_value_rows = []
        for p in props:
            rows = rec.timed(run, "step2-property", f"valueCountsQuery {p}", value_counts_by_property_query(project, p))
            prop_value_rows += [{"prop": p, **r} for r in rows]
        if run == 1:
            rec.save_rows("classes", [{"resClass": c, "multiTyped": multi, "anyMultiTyped": any_multi} for c in classes])
            rec.save_rows("resource-counts", res_rows)
            rec.save_rows("value-counts-by-class", value_rows)
            rec.save_rows("value-counts-by-property", prop_value_rows)
        rec.save_timings()


def after(rec: Recorder, project: str, runs: int) -> None:
    _, _, prop_rows = run_query(project_value_properties_query(project))
    # C2 binds value and link value properties; standoff link values stay out.
    _, _, link_rows = run_query(
        PREFIXES
        + f"""SELECT DISTINCT ?prop WHERE {{
  {{ ?onto knora-base:attachedToProject <{project}> . }} UNION {{ BIND(<http://www.knora.org/ontology/knora-base> AS ?onto) }}
  GRAPH ?onto {{ ?prop rdfs:subPropertyOf ?anySuper . }}
  ?prop rdfs:subPropertyOf* knora-base:hasLinkToValue .
  FILTER (?prop != knora-base:hasStandoffLinkToValue)
}}"""
    )
    c2_props = sorted({r["prop"] for r in prop_rows} | {r["prop"] for r in link_rows})
    print(f"  {len(c2_props)} properties bound in C2", flush=True)
    for run in range(1, runs + 1):
        r_rows = rec.timed(run, "after", "R resource counts", resource_counts_r(project))
        c1_rows = rec.timed(run, "after", "C1 matrix unbound ?prop", matrix_c1(project))
        c2_rows = rec.timed(run, "after", "C2 matrix VALUES ?prop", matrix_c2(project, c2_props))
        c3_rows = rec.timed(run, "after", "C3 matrix sub-select anchor", matrix_c3(project))
        if run == 1:
            rec.save_rows("R", r_rows)
            rec.save_rows("C1", c1_rows)
            rec.save_rows("C2", c2_rows)
            rec.save_rows("C3", c3_rows)
            matrix_props = sorted({r["prop"] for r in c1_rows})
            if matrix_props:
                _, _, cls = run_query(property_classification_query(matrix_props))
                rec.save_rows("prop-classification", cls)
            matrix_classes = sorted({r["resClass"] for r in c1_rows} | {r["resClass"] for r in r_rows})
            if matrix_classes:
                _, _, rc = run_query(resource_classes_query(matrix_classes))
                rec.save_rows("resource-classes", rc)
        rec.save_timings()


def main() -> None:
    phase, label, project, runs, out_dir = sys.argv[1], sys.argv[2], sys.argv[3], int(sys.argv[4]), sys.argv[5]
    rec = Recorder(out_dir, label, phase)
    print(f"== {label} {phase} ({project})", flush=True)
    (before if phase == "before" else after)(rec, project, runs)
    by_step: dict[tuple[int, str], float] = {}
    for t in rec.timings:
        key = (t["run"], t["step"])
        by_step[key] = by_step.get(key, 0.0) + float(t["seconds"])
    for step in sorted({s for _, s in by_step}):
        values = [by_step[(r, step)] for r in range(1, runs + 1) if (r, step) in by_step]
        print(f"  total {step:<14} runs={['%.1f' % v for v in values]} median={statistics.median(values):.1f}s")


if __name__ == "__main__":
    main()
