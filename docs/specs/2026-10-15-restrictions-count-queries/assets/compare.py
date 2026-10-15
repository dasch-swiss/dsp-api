"""Compare option C matrix counts with the before snapshot of today's count queries.

Usage: python3 -I compare.py <results-dir> <label>
"""

import csv
import os
import statistics
import sys
from collections import defaultdict


def read(results: str, name: str) -> list[dict]:
    path = os.path.join(results, name)
    if not os.path.exists(path):
        return []
    with open(path) as f:
        return list(csv.DictReader(f))


def as_counts(rows: list[dict], keys: tuple[str, ...]) -> dict[tuple, int]:
    out: dict[tuple, int] = defaultdict(int)
    for r in rows:
        out[tuple(r[k] for k in keys)] += int(r["cnt"])
    return dict(out)


def diff(name: str, expected: dict, actual: dict) -> int:
    keys = sorted(set(expected) | set(actual))
    bad = [(k, expected.get(k, 0), actual.get(k, 0)) for k in keys if expected.get(k, 0) != actual.get(k, 0)]
    print(f"- {name}: {len(keys)} keys, {len(bad)} differences, totals {sum(expected.values())} / {sum(actual.values())}")
    for k, e, a in bad[:10]:
        print(f"    {k}: before={e} matrix={a}")
    return len(bad)


def is_true(v: str) -> bool:
    return v.lower() == "true"


def timing_summary(results: str, label: str) -> None:
    for phase in ("before", "after"):
        rows = read(results, f"{label}-{phase}-timings.csv")
        if not rows:
            continue
        per_run: dict[tuple[str, str], dict[int, float]] = defaultdict(dict)
        failures = defaultdict(int)
        for r in rows:
            group = r["step"] if phase == "before" else r["query"]
            run = int(r["run"])
            per_run[(phase, group)][run] = per_run[(phase, group)].get(run, 0.0) + float(r["seconds"])
            if r["status"] != "ok":
                failures[group] += 1
        for (ph, group), runs in sorted(per_run.items()):
            values = [runs[k] for k in sorted(runs)]
            print(
                f"- {ph} {group}: runs {', '.join(f'{v:.1f}' for v in values)} s,"
                f" median {statistics.median(values):.1f} s, failures {failures[group]}"
            )
        if phase == "before":
            slow = sorted(rows, key=lambda r: -float(r["seconds"]))[:5]
            for r in slow:
                print(f"    slowest: run {r['run']} {r['query']} {float(r['seconds']):.1f} s {r['status']}")


def main() -> None:
    results, label = sys.argv[1], sys.argv[2]
    print(f"## {label}\n")
    print("### Timings\n")
    timing_summary(results, label)

    classes = {r["resClass"] for r in read(results, f"{label}-after-resource-classes.csv")}
    props = {r["prop"]: r for r in read(results, f"{label}-after-prop-classification.csv")}
    c1 = read(results, f"{label}-after-C1.csv")
    if not c1:
        print("\nNo matrix result.")
        return

    def kind(prop: str) -> str:
        p = props.get(prop)
        if p is None or not is_true(p["isValue"]):
            return "other"
        if is_true(p["isStandoffLinkValue"]):
            return "standoff-link-value"
        if is_true(p["isLinkValue"]):
            return "link-value"
        if is_true(p["isFileValue"]):
            return "file-value"
        return "value"

    print("\n### Counts\n")
    kept = [r for r in c1 if r["resClass"] in classes]
    by_kind: dict[str, int] = defaultdict(int)
    for r in c1:
        by_kind[kind(r["prop"]) if r["resClass"] in classes else "non-resource-class"] += int(r["cnt"])
    print(f"- C1 rows: {len(c1)}; object counts by kind: {dict(sorted(by_kind.items()))}")

    old_equiv = [r for r in kept if kind(r["prop"]) in ("value", "file-value")]
    before_class = as_counts(read(results, f"{label}-before-value-counts-by-class.csv"), ("resClass", "permissions"))
    diff("values by class and permission (old scope)", before_class, as_counts(old_equiv, ("resClass", "permissions")))

    before_prop = as_counts(read(results, f"{label}-before-value-counts-by-property.csv"), ("prop", "permissions"))
    matrix_prop = {k: v for k, v in as_counts(old_equiv, ("prop", "permissions")).items()}
    diff("values by property and permission (old scope)", before_prop, matrix_prop)

    before_res = as_counts(read(results, f"{label}-before-resource-counts.csv"), ("resClass", "permissions"))
    r_rows = [r for r in read(results, f"{label}-after-R.csv") if r["resClass"] in classes]
    diff("resources by class and permission", before_res, as_counts(r_rows, ("resClass", "permissions")))

    link = [r for r in kept if kind(r["prop"]) == "link-value"]
    link_by_class = as_counts(link, ("resClass",))
    print(f"- DEV-7390 delta: {sum(link_by_class.values())} user link values in {len(link_by_class)} classes,"
          f" {len({r['permissions'] for r in link})} distinct permission texts")
    for (c,), n in sorted(link_by_class.items(), key=lambda kv: -kv[1])[:10]:
        print(f"    {c}: {n}")

    for variant in ("C2", "C3"):
        rows = [r for r in read(results, f"{label}-after-{variant}.csv") if r["resClass"] in classes]
        keep = [r for r in kept if kind(r["prop"]) != "other"]
        diff(f"C1 against {variant} (value and link value rows)",
             as_counts(keep, ("resClass", "prop", "permissions")),
             as_counts([r for r in rows if kind(r["prop"]) != "other"], ("resClass", "prop", "permissions")))

    multi = read(results, f"{label}-before-classes.csv")
    if multi:
        print(f"- multi-typed probes: any={multi[0]['anyMultiTyped']} strict-subclass={multi[0]['multiTyped']}")
    perms = {r["permissions"] for r in kept if kind(r["prop"]) != "other"}
    print(f"- distinct permission texts on values: {len(perms)}")


if __name__ == "__main__":
    main()
