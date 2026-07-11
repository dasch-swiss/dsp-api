#!/usr/bin/env bash
# Fails if any Bazel-side image pin (temurin base, sipi base, OTel/Pyroscope
# jars, fuseki dist/tag) has drifted from build.sbt / project/Dependencies.scala /
# modules/fuseki/Dockerfile's values. Run via
# `bazel test //tools/oci:image_versions_match_sbt`.
set -euo pipefail

module_bazel="$1"
build_sbt="$2"
dependencies_scala="$3"
fuseki_dockerfile="$4"
fuseki_build_bazel="$5"

fail=0

# temurin base tag: MODULE.bazel's oci.pull comment names it; source of truth
# is build.sbt's `dockerBaseImage`.
sbt_temurin="$(grep -o 'dockerBaseImage *:= *"[^"]*"' "$build_sbt" | head -1 | sed -E 's/.*"([^"]*)"/\1/')"
if ! grep -qF "$sbt_temurin" "$module_bazel"; then
  echo "MISMATCH: MODULE.bazel's temurin base comment doesn't mention build.sbt's dockerBaseImage ($sbt_temurin)" >&2
  fail=1
fi

# sipi base tag (modules/sipi/BUILD.bazel's oci.pull -- same duplication
# pattern and the same silent-drift risk as temurin above): MODULE.bazel's
# comment names the repo:tag; source of truth is Dependencies.scala's sipiImage.
sbt_sipi="$(grep -o 'sipiImage *= *"[^"]*"' "$dependencies_scala" | sed -E 's/.*"([^"]*)"/\1/')"
if ! grep -qF "$sbt_sipi" "$module_bazel"; then
  echo "MISMATCH: MODULE.bazel's sipi base comment doesn't mention Dependencies.scala's sipiImage ($sbt_sipi)" >&2
  fail=1
fi

# OTel javaagent version.
sbt_otel="$(grep -o 'otelAgentVersion *= *"[^"]*"' "$dependencies_scala" | sed -E 's/.*"([^"]*)"/\1/')"
if ! grep -qF "download/${sbt_otel}/opentelemetry-javaagent.jar" "$module_bazel"; then
  echo "MISMATCH: MODULE.bazel's otel_javaagent URL doesn't match Dependencies.scala's otelAgentVersion ($sbt_otel)" >&2
  fail=1
fi

# Pyroscope extension version.
sbt_pyroscope="$(grep -o 'otelPyroscopeVersion *= *"[^"]*"' "$dependencies_scala" | sed -E 's/.*"([^"]*)"/\1/')"
if ! grep -qF "download/${sbt_pyroscope}/pyroscope-otel.jar" "$module_bazel"; then
  echo "MISMATCH: MODULE.bazel's pyroscope_otel URL doesn't match Dependencies.scala's otelPyroscopeVersion ($sbt_pyroscope)" >&2
  fail=1
fi

# Fuseki dist version (the tarball MODULE.bazel fetches). The SHA512 pin in
# the Dockerfile is self-guarding at fetch time (a version bump without a
# matching hash change would 404 or fail @fuseki_dist's checksum, not silently
# succeed), so this only needs to catch a bumped FUSEKI_VERSION whose tarball
# URL wasn't updated to match. `ARG FUSEKI_VERSION=` is checked (not the `ENV`
# line a few lines below it, which only ever echoes the same ARG value) so
# this doesn't depend on which of the two comes first in the Dockerfile.
dockerfile_fuseki="$(grep -o 'ARG FUSEKI_VERSION=[^[:space:]]*' "$fuseki_dockerfile" | head -1 | cut -d= -f2)"
if ! grep -qF "apache-jena-fuseki-${dockerfile_fuseki}.tar.gz" "$module_bazel"; then
  echo "MISMATCH: MODULE.bazel's fuseki_dist URL doesn't match modules/fuseki/Dockerfile's FUSEKI_VERSION ($dockerfile_fuseki)" >&2
  fail=1
fi

# Fuseki image tag (IMAGE_VERSION, e.g. "6.1.0-0"). Unlike the other three
# images, modules/fuseki/BUILD.bazel bakes this literal into its labels/env/
# repo_tags rather than reading it from a stamped workspace-status key (it
# isn't a git-versioned artifact -- it's a pinned upstream release
# repackaged), so nothing else here would catch a bumped IMAGE_VERSION that
# the BUILD.bazel literals weren't updated to match.
dockerfile_image_version="$(grep -o 'ARG IMAGE_VERSION=[^[:space:]]*' "$fuseki_dockerfile" | head -1 | cut -d= -f2)"
if ! grep -qF "$dockerfile_image_version" "$fuseki_build_bazel"; then
  echo "MISMATCH: modules/fuseki/BUILD.bazel doesn't mention modules/fuseki/Dockerfile's IMAGE_VERSION ($dockerfile_image_version)" >&2
  fail=1
fi

if [ "$fail" -ne 0 ]; then
  echo "" >&2
  echo "MODULE.bazel's image dependencies have drifted from the sbt build. Bump both together." >&2
  exit 1
fi

echo "OK: MODULE.bazel's temurin/sipi base tags, OTel/Pyroscope versions, and fuseki dist/tag versions match sbt."
