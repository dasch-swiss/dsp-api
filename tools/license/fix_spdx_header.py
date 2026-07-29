#!/usr/bin/env python3
"""Insert the Apache-2.0 + SPDX header into Scala files that lack it.

Replaces sbt-header's `headerCreateAll`. Run via `just header-fix`
(`bazel run //tools/license:fix`), which sets BUILD_WORKSPACE_DIRECTORY to the
repo root. Walks modules/**/*.scala and prepends the header to any file missing
it. Files that already carry a valid header (any year) are left untouched, so
this does not churn every file's year each January.
"""
import datetime
import os
import re
import sys

COPYRIGHT_RE = re.compile(r"2021 - \d{4} Swiss National Data and Service Center for the Humanities")
SPDX_RE = re.compile(r"SPDX-License-Identifier:\s*Apache-2\.0")


def header(year):
    return (
        "/*\n"
        f" * Copyright © 2021 - {year} Swiss National Data and Service Center "
        "for the Humanities and/or DaSCH Service Platform contributors.\n"
        " * SPDX-License-Identifier: Apache-2.0\n"
        " */\n\n"
    )


def has_header(text):
    head = "\n".join(text.splitlines()[:8])
    return bool(COPYRIGHT_RE.search(head) and SPDX_RE.search(head))


def main():
    root = os.environ.get("BUILD_WORKSPACE_DIRECTORY")
    if not root:
        print(
            "BUILD_WORKSPACE_DIRECTORY unset; run via `bazel run //tools/license:fix`.",
            file=sys.stderr,
        )
        return 2
    hdr = header(datetime.date.today().year)
    added = 0
    for dirpath, _dirs, files in os.walk(os.path.join(root, "modules")):
        for name in files:
            if not name.endswith(".scala"):
                continue
            path = os.path.join(dirpath, name)
            with open(path, encoding="utf-8") as f:
                text = f.read()
            if has_header(text):
                continue
            with open(path, "w", encoding="utf-8") as f:
                f.write(hdr + text)
            added += 1
            print(f"+ {os.path.relpath(path, root)}")
    print(f"Inserted header into {added} file(s).")
    return 0


if __name__ == "__main__":
    sys.exit(main())
