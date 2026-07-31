#!/usr/bin/env bash
# Emits Bazel workspace status keys consumed by stamped rules (e.g. //tools/buildinfo
# and the image version/revision labels). Wired via `.bazelrc`
# `build --workspace_status_command=tools/workspace_status.sh`.
#
# STABLE_* keys land in bazel-out/stable-status.txt (a change re-triggers stamped
# consumers); everything else goes to volatile-status.txt.
#
# The version string is derived from git:
#   gitVersion = `git describe --tag --dirty --abbrev=7 --always` + non-main branch suffix ('/'->'-')
#   then '+' -> '-' (Docker tags reject '+').
set -euo pipefail

commit="$(git rev-parse HEAD)"
short="$(git rev-parse --short HEAD)"
branch="$(git rev-parse --abbrev-ref HEAD)"
describe="$(git describe --tag --dirty --abbrev=7 --always)"

suffix=""
if [ "$branch" != "main" ] && [ "$branch" != "HEAD" ]; then
  suffix="-$(printf '%s' "$branch" | tr '/' '-')"
fi
version="${describe}${suffix}"
version="${version//+/-}"

# A fixed value per build (CI sets BUILD_TIME; local dev falls back to "dev"), so it
# stays stable within a build and does not needlessly bust the buildinfo cache.
build_time="${BUILD_TIME:-dev}"

# Image versions are NOT emitted here: fuseki comes from MODULE.bazel via the
# @dsp_image_versions module extension, and sipi is read from the pinned base
# image's OCI label (//tools/buildinfo:oci_config_label). This script is pure git.
echo "STABLE_GIT_VERSION ${version}"
echo "STABLE_GIT_COMMIT ${commit}"
echo "STABLE_GIT_SHORT ${short}"
echo "STABLE_BUILD_TIME ${build_time}"
