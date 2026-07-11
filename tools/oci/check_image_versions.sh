#!/usr/bin/env bash
# Fails if any Bazel-side image pin (temurin base, sipi base, OTel/Pyroscope
# jars) has drifted from build.sbt / project/Dependencies.scala's values. Run
# via `bazel test //tools/oci:image_versions_match_sbt`.
set -euo pipefail

module_bazel="$1"
build_sbt="$2"
dependencies_scala="$3"

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

if [ "$fail" -ne 0 ]; then
  echo "" >&2
  echo "MODULE.bazel's image dependencies have drifted from the sbt build. Bump both together." >&2
  exit 1
fi

echo "OK: MODULE.bazel's temurin/sipi base tags and OTel/Pyroscope versions match sbt."
