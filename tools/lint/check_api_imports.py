#!/usr/bin/env python3
"""Fail if webapi main code outside `slice/api` references `org.knora.webapi.slice.api`.

The HTTP layer (`slice/api`: endpoints, DTOs, Tapir and zio-json codecs) sits above the domain, so the
dependency points one way only (ARCH-MAP.md, component webapi-api). Any non-comment line that names the
package counts, whether it is an import or a fully qualified reference; `package` clauses do not.
//tools/lint:no_relative_imports rules out reaching the package through a relative import.

The files that already break the rule are listed in api_imports_allowlist.txt, one `<path>  # reason` per
line, paths workspace-relative as `$(rootpaths)` gives them. A listed file that no longer references the
package fails as stale, so the list only shrinks. No webapi main-source file given at all fails.
"""
import argparse
import re
import sys

WEBAPI_MAIN = "modules/webapi/src/main/scala/"
API_DIR = WEBAPI_MAIN + "org/knora/webapi/slice/api/"
API_REF_RE = re.compile(r"\borg\.knora\.webapi\.slice\.api\b")
PACKAGE_RE = re.compile(r"^\s*package\s")
COMMENT_LINE_RE = re.compile(r"^\s*(?://|/?\*)")
# Whitespace before `//`, so a URL such as "http://..." in a string is not taken for a comment.
TRAILING_COMMENT_RE = re.compile(r"\s//.*$")

HINT = (
    "Domain code must not depend on the HTTP layer: move the type it needs out of slice/api, or the code that "
    "needs it into slice/api. Do not add a file to tools/lint/api_imports_allowlist.txt."
)


def read_allowlist(path):
    entries, errors = set(), []
    with open(path, encoding="utf-8") as f:
        for lineno, line in enumerate(f, start=1):
            body, _, reason = line.partition("#")
            tokens = body.split()
            if not tokens:
                continue
            if len(tokens) != 1 or not reason.strip():
                errors.append(f"{path}:{lineno}: malformed entry, expected `<path>  # reason`")
            else:
                entries.add(tokens[0])
    return entries, errors


def first_api_ref(path):
    """Line number of the first reference to the api package, or None."""
    with open(path, encoding="utf-8") as f:
        for lineno, line in enumerate(f, start=1):
            if COMMENT_LINE_RE.match(line) or PACKAGE_RE.match(line):
                continue
            if API_REF_RE.search(TRAILING_COMMENT_RE.sub("", line)):
                return lineno
    return None


def check(paths, allowlist):
    scanned = sorted(p for p in paths if WEBAPI_MAIN in p and API_DIR not in p and p.endswith(".scala"))
    errors, seen = [], set()
    if not scanned:
        errors.append("no webapi main-source files given; check the wiring in tools/lint/BUILD.bazel")
    for path in scanned:
        lineno = first_api_ref(path)
        if lineno is None:
            continue
        if path in allowlist:
            seen.add(path)
        else:
            errors.append(f"{path}:{lineno}: references org.knora.webapi.slice.api")
    for entry in sorted(allowlist - seen):
        errors.append(f"stale allow-list entry, remove it: {entry}")
    return errors, len(scanned)


def main(argv):
    parser = argparse.ArgumentParser()
    parser.add_argument("--allowlist", required=True)
    parser.add_argument("paths", nargs="*")
    args = parser.parse_args(argv[1:])
    allowlist, errors = read_allowlist(args.allowlist)
    check_errors, scanned = check(args.paths, allowlist)
    errors += check_errors
    if errors:
        print(f"FAIL ({len(errors)} problem(s)): {HINT}")
        for e in errors:
            print(f"  {e}")
        return 1
    print(f"OK: no new reference to slice.api outside it ({scanned} files checked).")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
