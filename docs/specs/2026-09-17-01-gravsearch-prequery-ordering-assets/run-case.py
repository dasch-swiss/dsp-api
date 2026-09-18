#!/usr/bin/env python3
"""Runs one spike case per the Phase 3 protocol (DEV-7287).

One warm-up round over all layouts (discarded), then 5 timed rounds, each running the
case's layouts round-robin in letter order, so load drift on stage affects all layouts
alike. Each measurement goes through stage.sh, which appends to results.csv.

  python3 run-case.py S4 A B C D --once D

Layouts named by --once run exactly once (protocol exemptions S4-D, S8-C). A layout that
hits the 120 s timeout twice is recorded as 120 for the remaining rounds and not re-run.
"""

import pathlib
import subprocess
import sys

HERE = pathlib.Path(__file__).parent
RESULTS = HERE / "results.csv"


def run(case, layout, run_no):
    out = subprocess.run(
        [str(HERE / "stage.sh"), case, layout, str(run_no), str(HERE / f"{case}-{layout}.rq")],
        capture_output=True,
        text=True,
    )
    line = out.stdout.strip().splitlines()[-1]
    print(line, flush=True)
    return line


def drop(case, layout, run_no):
    prefix = f"{case},{layout},{run_no},"
    kept = [l for l in RESULTS.read_text().splitlines() if not l.startswith(prefix)]
    RESULTS.write_text("\n".join(kept) + "\n")


def main():
    args = sys.argv[1:]
    once = []
    if "--once" in args:
        i = args.index("--once")
        once = args[i + 1:]
        args = args[:i]
    case, layouts = args[0], args[1:]
    timeouts = {l: 0 for l in layouts}

    print(f"== {case} warm-up ==", flush=True)
    for l in layouts:
        line = run(case, l, 1 if l in once else 0)
        if l not in once:
            drop(case, l, 0)

    for round_no in range(1, 6):
        print(f"== {case} round {round_no} ==", flush=True)
        for l in layouts:
            if l in once:
                continue
            if timeouts[l] >= 2:
                with RESULTS.open("a") as f:
                    f.write(f"{case},{l},{round_no},120,-1\n")
                continue
            line = run(case, l, round_no)
            if line.endswith(",120,-1"):
                timeouts[l] += 1


if __name__ == "__main__":
    main()
