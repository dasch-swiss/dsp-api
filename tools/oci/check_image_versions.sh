#!/usr/bin/env bash
# Fails if any Bazel-side image pin (temurin base, sipi base, OTel/Pyroscope
# jars, fuseki dist version) has drifted from build.sbt / project/Dependencies.scala.
# Run via `bazel test //tools/oci:image_versions_match_sbt`.
set -euo pipefail

module_bazel="$1"
build_sbt="$2"
dependencies_scala="$3"
fuseki_build_bazel="$4"

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

# Fuseki dist version (the tarball MODULE.bazel fetches, e.g. "6.1.0"). The
# @fuseki_dist sha256 pin is self-guarding at fetch time (a version bump without a
# matching hash change fails the checksum, not silently succeeds), so this only
# catches a bumped FUSEKI_VERSION in modules/fuseki/BUILD.bazel whose MODULE.bazel
# tarball URL wasn't updated to match. (The image tag IMAGE_VERSION is checked
# against Dependencies.scala + docker-compose.yml by the check-fuseki-version-consistency
# CI job.)
bazel_fuseki="$(grep -oE '"FUSEKI_VERSION": "[^"]+"' "$fuseki_build_bazel" | head -1 | cut -d'"' -f4)"
if ! grep -qF "apache-jena-fuseki-${bazel_fuseki}.tar.gz" "$module_bazel"; then
  echo "MISMATCH: MODULE.bazel's fuseki_dist URL doesn't match modules/fuseki/BUILD.bazel's FUSEKI_VERSION ($bazel_fuseki)" >&2
  fail=1
fi

if [ "$fail" -ne 0 ]; then
  echo "" >&2
  echo "MODULE.bazel's image dependencies have drifted from the sbt build. Bump both together." >&2
  exit 1
fi

echo "OK: MODULE.bazel's temurin/sipi base tags, OTel/Pyroscope versions, and fuseki dist/tag versions match sbt."
