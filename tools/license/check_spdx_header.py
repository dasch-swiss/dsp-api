#!/usr/bin/env python3
"""Fail if any Scala source is missing the Apache-2.0 + SPDX file header.

Replaces sbt-header's `headerCheckAll`. File paths are fed on argv as
`$(rootpaths //modules/<m>:license_srcs)` by //tools/license:spdx_header_check.
Each file's first lines must carry the DaSCH copyright line and the SPDX
identifier. The copyright year is matched as `\\d{4}`, so this never fails just
because the calendar year rolled over (sbt-header pinned the current year and
failed every Jan 1 until every file was re-stamped).

py_test (not an sh_test around a python3 shebang) so it resolves the hermetic
interpreter registered in MODULE.bazel — same rationale as //tools/deps.
"""
import re
import sys

# The `©` char is intentionally omitted from the pattern for encoding
# robustness; this substring is specific enough to identify the header.
COPYRIGHT_RE = re.compile(r"2021 - \d{4} Swiss National Data and Service Center for the Humanities")
SPDX_RE = re.compile(r"SPDX-License-Identifier:\s*Apache-2\.0")


def has_header(text):
    head = "\n".join(text.splitlines()[:8])
    return bool(COPYRIGHT_RE.search(head) and SPDX_RE.search(head))


def main(argv):
    paths = argv[1:]
    offenders = []
    for path in paths:
        try:
            with open(path, encoding="utf-8") as f:
                text = f.read()
        except OSError as e:
            offenders.append(f"{path}: cannot read ({e})")
            continue
        if not has_header(text):
            offenders.append(path)
    if offenders:
        print("Missing or invalid Apache-2.0 SPDX header in:")
        for o in offenders:
            print(f"  {o}")
        print(f"\n{len(offenders)} file(s). Run `just header-fix` to insert the header.")
        return 1
    print(f"OK: all {len(paths)} Scala files carry the Apache-2.0 SPDX header.")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
