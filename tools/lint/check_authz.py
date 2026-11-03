#!/usr/bin/env python3
"""Authorization lint over main sources (ADR-0013).

Rule 1: no `@unused` / `@scala.annotation.unused` on a `User` parameter (same line), unless the file and
owner are in the unused-User allow-list. The owner is the nearest preceding `def`, `class`, `object` or
`trait` outside a comment line, so a constructor parameter belongs to its class. Rule 2: no `SystemUser` token
in code outside the files in the SystemUser allow-list; comment lines and trailing `//` comments do not count. A
listed entry that no longer matches fails, so the lists only shrink.

Allow-list lines: `<path> <owner>  # <issue id> reason` (rule 1) and `<path>  # reason` (rule 2), paths
workspace-relative as `$(rootpaths)` gives them; a line without its reason fails. Only paths containing
`/src/main/` are checked, and none at all fails.
"""
import argparse
import re
import sys

UNUSED_USER_RE = re.compile(
    r"@(?:scala\.annotation\.)?unused\s+(?:(?:implicit|using)\s+)?\w+\s*:\s*(?:[\w.]+\.)?User\b"
)
SYSTEM_USER_RE = re.compile(r"\bSystemUser\b")
OWNER_RE = re.compile(r"\b(?:def|class|object|trait)\s+(\w+)")
COMMENT_LINE_RE = re.compile(r"^\s*(?://|/?\*)")
# Whitespace before `//`, so a URL such as "http://..." in a string is not taken for a comment.
TRAILING_COMMENT_RE = re.compile(r"\s//.*$")
ISSUE_RE = re.compile(r"\b[A-Z]+-\d+\b")

ADR = "docs/adr/0013-authorization-policy-enforcement.md"
UNUSED_USER_HINT = (
    "Use the User in an authorization check, or drop the parameter; do not silence it. "
    f"An entry in tools/lint/unused_user_allowlist.txt names a tracked issue as its reason ({ADR})."
)
SYSTEM_USER_HINT = (
    "Read or write as the caller. Load as SystemUser only to prefetch, then check the caller's permission on "
    f"what was loaded before it reaches the response ({ADR}, decision 7). A new file in "
    "tools/lint/system_user_allowlist.txt is an authorization decision that needs review."
)


def read_allowlist(path, fields, reason_re=None):
    """Return the entries of an allow-list whose lines are `fields` tokens plus a `# reason`."""
    entries, errors = [], []
    with open(path, encoding="utf-8") as f:
        for lineno, line in enumerate(f, start=1):
            body, _, reason = line.partition("#")
            tokens = tuple(body.split())
            if not tokens:
                continue
            if len(tokens) != fields or not reason.strip() or (reason_re and not reason_re.search(reason)):
                errors.append(f"{path}:{lineno}: malformed entry, see the format in tools/lint/check_authz.py")
            else:
                entries.append(tokens)
    return entries, errors


def scan(path):
    """Return ([(lineno, owner)] for rule 1, [lineno] for rule 2) of one file."""
    unused_user, system_user = [], []
    owner = None
    with open(path, encoding="utf-8") as f:
        for lineno, line in enumerate(f, start=1):
            comment = COMMENT_LINE_RE.match(line)
            owners = [] if comment else OWNER_RE.findall(line)
            if owners:
                owner = owners[-1]
            if UNUSED_USER_RE.search(line):
                unused_user.append((lineno, owner))
            if not comment and SYSTEM_USER_RE.search(TRAILING_COMMENT_RE.sub("", line)):
                system_user.append(lineno)
    return unused_user, system_user


def check(paths, unused_allowlist, system_user_allowlist):
    unused_allowed = set(unused_allowlist)
    system_allowed = {e[0] for e in system_user_allowlist}
    unused_seen, system_seen = set(), set()
    errors = []
    main_paths = sorted(p for p in paths if "/src/main/" in p)
    if not main_paths:
        errors.append("no main-source files given; check the _LINT_SRCS wiring in tools/lint/BUILD.bazel")
    for path in main_paths:
        unused_user, system_user = scan(path)
        for lineno, owner in unused_user:
            if (path, owner) in unused_allowed:
                unused_seen.add((path, owner))
            else:
                errors.append(f"{path}:{lineno}: @unused on a User parameter of `{owner}`")
        if system_user:
            if path in system_allowed:
                system_seen.add(path)
            else:
                errors.append(f"{path}:{system_user[0]}: SystemUser outside the allow-list")
    for entry in sorted(unused_allowed - unused_seen):
        errors.append(f"stale unused-User allow-list entry, remove it: {' '.join(entry)}")
    for entry in sorted(system_allowed - system_seen):
        errors.append(f"stale SystemUser allow-list entry, remove it: {entry}")
    return errors, len(main_paths)


def main(argv):
    parser = argparse.ArgumentParser()
    parser.add_argument("--unused-user-allowlist", required=True)
    parser.add_argument("--system-user-allowlist", required=True)
    parser.add_argument("paths", nargs="*")
    args = parser.parse_args(argv[1:])
    unused_allowlist, unused_errors = read_allowlist(args.unused_user_allowlist, 2, ISSUE_RE)
    system_allowlist, system_errors = read_allowlist(args.system_user_allowlist, 1)
    errors, scanned = check(args.paths, unused_allowlist, system_allowlist)
    errors = unused_errors + system_errors + errors
    if errors:
        print("Authorization lint failed:")
        for e in errors:
            print(f"  {e}")
        for marker, hint in (("@unused on a User", UNUSED_USER_HINT), ("SystemUser outside", SYSTEM_USER_HINT)):
            if any(marker in e for e in errors):
                print(f"\n{hint}")
        return 1
    print(f"OK: authorization lint passed over {scanned} main-source Scala files.")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
