"""Compare C5/C6/R counts with the before snapshot. Usage: python3 -I compare_c5.py <results-dir> <label>"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from compare import as_counts, diff, is_true, read  # noqa: E402


def main() -> None:
    results, label = sys.argv[1], sys.argv[2]
    p = f"{label}-c5"
    classes = {r["resClass"] for r in read(results, f"{p}-resource-classes.csv")}
    props = {r["prop"]: r for r in read(results, f"{p}-prop-classification.csv")}

    def kind(prop: str) -> str:
        c = props.get(prop)
        if c is None or not is_true(c["isValue"]):
            return "other"
        if is_true(c["isStandoffLinkValue"]):
            return "standoff-link-value"
        if is_true(c["isLinkValue"]):
            return "link-value"
        return "file-value" if is_true(c["isFileValue"]) else "value"

    print(f"## {label}")
    for variant in ("C5", "C6"):
        rows = read(results, f"{p}-{variant}.csv")
        live = [r for r in rows if r["valueDeleted"] == "false" and r["resClass"] in classes]
        dropped = [r for r in rows if r not in live or kind(r["prop"]) == "other"]
        print(f"### {variant}: {len(rows)} rows, {len(dropped)} dropped in Scala"
              f" ({sum(int(r['cnt']) for r in dropped)} objects)")
        old = [r for r in live if kind(r["prop"]) in ("value", "file-value")]
        diff("values by class and permission", as_counts(read(results, f"{label}-before-value-counts-by-class.csv"),
             ("resClass", "permissions")), as_counts(old, ("resClass", "permissions")))
        diff("values by property and permission", as_counts(read(results, f"{label}-before-value-counts-by-property.csv"),
             ("prop", "permissions")), as_counts(old, ("prop", "permissions")))
        link = [r for r in live if kind(r["prop"]) == "link-value"]
        so = [r for r in live if kind(r["prop"]) == "standoff-link-value"]
        print(f"- user link values (DEV-7390, missing today): {sum(int(r['cnt']) for r in link)};"
              f" standoff link values (stay excluded): {sum(int(r['cnt']) for r in so)}")
    r_rows = [r for r in read(results, f"{p}-R.csv") if r["resClass"] in classes]
    diff("resources by class and permission (R)",
         as_counts(read(results, f"{label}-before-resource-counts.csv"), ("resClass", "permissions")),
         as_counts(r_rows, ("resClass", "permissions")))
    m = read(results, f"{label}-before-classes.csv")
    print(f"- multi-typed probes: any={m[0]['anyMultiTyped']} strict-subclass={m[0]['multiTyped']}")


if __name__ == "__main__":
    main()
