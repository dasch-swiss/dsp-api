#!/usr/bin/env python3
"""Authorization lint over main sources (ADR-0013).

Rule 1: no `@unused` / `@scala.annotation.unused` on a `User` parameter (same line), unless the file and
owner are in the unused-User allow-list. The owner is the nearest preceding `def`, `class`, `object` or
`trait` outside a comment line, so a constructor parameter belongs to its class. Rule 2: no `SystemUser` token
in code outside the files in the SystemUser allow-list; comment lines and trailing `//` comments do not count. Rule 3:
`Policy.key` and `withInfo` only in the policy factories. Rule 4: an elevated read, the requesting-user handle
`Users.SystemUser`, in code only in the files of the elevated-read allow-list; a file using the handle is also on the
SystemUser allow-list, since rule 2 matches it.
Rule 5: a file declaring the package `org.knora.webapi.slice.resources.access` lives in that package's directory in
webapi, because package access is what keeps grants from being issued elsewhere. A listed entry that no longer
matches fails, so the lists only shrink.

Allow-list lines: `<path> <owner>  # <issue id> reason` (rule 1) and `<path>  # reason` (rules 2 and 4), paths
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
POLICY_ATTRIBUTE_RE = re.compile(r"\bPolicy\.key\b|\bwithInfo\b")
POLICY_ATTRIBUTE_HOMES = ("/slice/common/api/", "/slice/api/v3/V3BaseEndpoint.scala")
ELEVATED_READ_RE = re.compile(r"\bUsers\.SystemUser\b")
PACKAGE_RE = re.compile(r"^\s*package\s+([\w.]+)")
ACCESS_PACKAGE = "org.knora.webapi.slice.resources.access"
ACCESS_DIR = "/org/knora/webapi/slice/resources/access/"
ACCESS_HOME = "modules/webapi/src/main/scala" + ACCESS_DIR
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
ELEVATED_READ_HINT = (
    "Only the object-access gate reads resources and values as the requesting SystemUser for a write: go through "
    f"ObjectAccess instead ({ADR}, Addendum 1, decision 16). A new file in tools/lint/elevated_read_allowlist.txt is "
    "an authorization decision that needs review."
)
ACCESS_PACKAGE_HINT = (
    f"Only files under {ACCESS_HOME} may declare the package {ACCESS_PACKAGE}: its package access is what keeps "
    f"grants from being issued elsewhere ({ADR}, Addendum 1, decision 16)."
)
POLICY_ATTRIBUTE_HINT = (
    "Declare the policy through a policy-declaring factory of BaseEndpoints or V3BaseEndpoint; `Policy.key` and "
    f"`withInfo` belong only in slice.common.api and slice.api.v3.V3BaseEndpoint ({ADR}, decision 4)."
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
    """Return the hits of one file: [(lineno, owner)] for rule 1, [lineno] for rules 2 to 4, and its package for rule 5.

    Consecutive package clauses (`package a.b` then `package c`) are joined into one package name.
    """
    unused_user, system_user, policy_attribute, elevated_read = [], [], [], []
    package_parts, in_header = [], True
    owner = None
    with open(path, encoding="utf-8") as f:
        for lineno, line in enumerate(f, start=1):
            comment = COMMENT_LINE_RE.match(line)
            owners = [] if comment else OWNER_RE.findall(line)
            if owners:
                owner = owners[-1]
            if UNUSED_USER_RE.search(line):
                unused_user.append((lineno, owner))
            code = "" if comment else TRAILING_COMMENT_RE.sub("", line)
            if SYSTEM_USER_RE.search(code):
                system_user.append(lineno)
            if POLICY_ATTRIBUTE_RE.search(code):
                policy_attribute.append(lineno)
            if ELEVATED_READ_RE.search(code):
                elevated_read.append(lineno)
            package = PACKAGE_RE.match(code)
            if in_header and package:
                package_parts.append(package.group(1))
            elif in_header and code.strip() and not comment:
                in_header = False
    return unused_user, system_user, policy_attribute, elevated_read, ".".join(package_parts)


def check(paths, unused_allowlist, system_user_allowlist, elevated_read_allowlist):
    unused_allowed = set(unused_allowlist)
    system_allowed = {e[0] for e in system_user_allowlist}
    elevated_allowed = {e[0] for e in elevated_read_allowlist}
    unused_seen, system_seen, elevated_seen = set(), set(), set()
    errors = []
    main_paths = sorted(p for p in paths if "/src/main/" in p)
    if not main_paths:
        errors.append("no main-source files given; check the _LINT_SRCS wiring in tools/lint/BUILD.bazel")
    for path in main_paths:
        unused_user, system_user, policy_attribute, elevated_read, package = scan(path)
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
        if policy_attribute and not any(home in path for home in POLICY_ATTRIBUTE_HOMES):
            errors.append(f"{path}:{policy_attribute[0]}: Policy.key or withInfo outside the policy factories")
        if elevated_read:
            if path in elevated_allowed:
                elevated_seen.add(path)
            else:
                errors.append(f"{path}:{elevated_read[0]}: elevated read outside the allow-list")
        in_access_home = path.startswith(ACCESS_HOME) or ("/" + ACCESS_HOME) in path
        if (package == ACCESS_PACKAGE or package.startswith(ACCESS_PACKAGE + ".")) and not in_access_home:
            errors.append(f"{path}: declares {package} outside {ACCESS_HOME}")
    for entry in sorted(unused_allowed - unused_seen):
        errors.append(f"stale unused-User allow-list entry, remove it: {' '.join(entry)}")
    for entry in sorted(system_allowed - system_seen):
        errors.append(f"stale SystemUser allow-list entry, remove it: {entry}")
    for entry in sorted(elevated_allowed - elevated_seen):
        errors.append(f"stale elevated-read allow-list entry, remove it: {entry}")
    return errors, len(main_paths)


def main(argv):
    parser = argparse.ArgumentParser()
    parser.add_argument("--unused-user-allowlist", required=True)
    parser.add_argument("--system-user-allowlist", required=True)
    parser.add_argument("--elevated-read-allowlist", required=True)
    parser.add_argument("paths", nargs="*")
    args = parser.parse_args(argv[1:])
    unused_allowlist, unused_errors = read_allowlist(args.unused_user_allowlist, 2, ISSUE_RE)
    system_allowlist, system_errors = read_allowlist(args.system_user_allowlist, 1)
    elevated_allowlist, elevated_errors = read_allowlist(args.elevated_read_allowlist, 1)
    errors, scanned = check(args.paths, unused_allowlist, system_allowlist, elevated_allowlist)
    errors = unused_errors + system_errors + elevated_errors + errors
    if errors:
        print("Authorization lint failed:")
        for e in errors:
            print(f"  {e}")
        for marker, hint in (
            ("@unused on a User", UNUSED_USER_HINT),
            (": SystemUser outside", SYSTEM_USER_HINT),
            ("Policy.key or withInfo", POLICY_ATTRIBUTE_HINT),
            ("elevated read outside", ELEVATED_READ_HINT),
            (f"declares {ACCESS_PACKAGE}", ACCESS_PACKAGE_HINT),
        ):
            if any(marker in e for e in errors):
                print(f"\n{hint}")
        return 1
    print(f"OK: authorization lint passed over {scanned} main-source Scala files.")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
