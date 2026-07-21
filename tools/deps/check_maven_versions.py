#!/usr/bin/env python3
"""Fails if any Maven coordinate shared between MODULE.bazel's `maven.install`
(Bazel build) and project/Dependencies.scala (sbt build) has drifted apart.

Usage: check_maven_versions.py <MODULE.bazel> <Dependencies.scala>
Run via `bazel test //tools/deps:maven_versions_match_sbt`.

Dependencies.scala is real Scala, not data, so this does a best-effort parse
of the two sbt dependency shapes it actually uses:
  "group" %% "artifact" % VERSION_EXPR   (Scala lib -- Bazel side gets a _3 suffix)
  "group" %  "artifact" % VERSION_EXPR   (plain Java lib -- no suffix)
where VERSION_EXPR is either a quoted literal or a bare identifier bound
elsewhere via `val NAME = "literal"`. Deps present on only one side (e.g.
sbt-plugin-only or Scala-toolchain-only artifacts) are not an error -- this
only flags shared coordinates whose versions disagree.
"""
import re
import sys

DEP_RE = re.compile(
    r'"(?P<group>[\w.\-]+)"\s*(?P<op>%%|%)\s*"(?P<artifact>[\w.\-]+)"'
    r'\s*%\s*(?:"(?P<lit>[^"]+)"|(?P<ref>[A-Za-z_][A-Za-z0-9_]*))'
)
VAL_RE = re.compile(r'val\s+([A-Za-z_][A-Za-z0-9_]*)\s*=\s*"([^"]*)"')
MAVEN_COORD_RE = re.compile(r'"([\w.\-]+):([\w.\-]+):([\w.\-]+)"')
MAVEN_ARTIFACT_BLOCK_RE = re.compile(r"maven\.artifact\((.*?)\n\)", re.DOTALL)


def parse_dependencies_scala(text):
    """Returns {(group, bazel_artifact): version} for every resolvable
    dependency literal, plus a list of (group, artifact, version_ref) that
    couldn't be resolved (unknown identifier)."""
    val_map = {}
    deps = {}
    unresolved = []
    for line in text.splitlines():
        dep = DEP_RE.search(line)
        if dep:
            group = dep.group("group")
            artifact = dep.group("artifact")
            if dep.group("op") == "%%":
                artifact = artifact + "_3"
            version = dep.group("lit")
            if version is None:
                ref = dep.group("ref")
                version = val_map.get(ref)
                if version is None:
                    unresolved.append((group, artifact, ref))
                    continue
            deps[(group, artifact)] = version
            continue
        # Only consider it a simple version-string binding if the dependency
        # pattern above didn't already match this line (a `val zio = "dev.zio"
        # %% "zio" % ZioVersion` line must not be treated as binding
        # `zio -> "dev.zio"`).
        v = VAL_RE.search(line)
        if v:
            val_map[v.group(1)] = v.group(2)
    return deps, unresolved


def parse_module_bazel(text):
    """Returns {(group, artifact): version} from maven.install's `artifacts`
    list (colon-joined coordinate strings) plus maven.artifact() override
    blocks (separate group=/artifact=/version= keyword args)."""
    deps = {}
    for m in MAVEN_COORD_RE.finditer(text):
        deps[(m.group(1), m.group(2))] = m.group(3)
    for block in MAVEN_ARTIFACT_BLOCK_RE.finditer(text):
        body = block.group(1)
        g = re.search(r'group\s*=\s*"([^"]+)"', body)
        a = re.search(r'artifact\s*=\s*"([^"]+)"', body)
        v = re.search(r'version\s*=\s*"([^"]+)"', body)
        if g and a and v:
            deps[(g.group(1), a.group(1))] = v.group(1)
    return deps


def main():
    module_bazel_path, dependencies_scala_path = sys.argv[1], sys.argv[2]
    with open(module_bazel_path) as f:
        module_bazel_deps = parse_module_bazel(f.read())
    with open(dependencies_scala_path) as f:
        sbt_deps, unresolved = parse_dependencies_scala(f.read())

    if unresolved:
        for group, artifact, ref in unresolved:
            print(
                f"WARNING: could not resolve version identifier '{ref}' for "
                f"{group}:{artifact} in {dependencies_scala_path} -- skipped",
                file=sys.stderr,
            )

    mismatches = []
    checked = 0
    for (group, artifact), sbt_version in sbt_deps.items():
        bazel_version = module_bazel_deps.get((group, artifact))
        if bazel_version is None:
            continue  # Not (yet) a Bazel dependency -- not this check's concern.
        checked += 1
        if bazel_version != sbt_version:
            mismatches.append((group, artifact, sbt_version, bazel_version))

    if mismatches:
        print("MISMATCH: MODULE.bazel's maven.install has drifted from Dependencies.scala:", file=sys.stderr)
        for group, artifact, sbt_version, bazel_version in sorted(mismatches):
            print(
                f"  {group}:{artifact} -- sbt has {sbt_version}, MODULE.bazel has {bazel_version}",
                file=sys.stderr,
            )
        print("\nBump both together (MODULE.bazel's maven.install + Dependencies.scala).", file=sys.stderr)
        return 1

    print(f"OK: {checked} shared Maven coordinates agree between MODULE.bazel and Dependencies.scala.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
