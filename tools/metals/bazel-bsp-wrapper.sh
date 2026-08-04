#!/usr/bin/env bash
# bazel_binary shim for bazel-bsp (wired via .bazelproject `bazel_binary:`).
#
# bazel-bsp re-copies its bundled aspects into .bazelbsp/ on every import, and its
# core.bzl returns a struct from the sync aspect impl — which Bazel 9 rejects. We can't
# edit the copy durably (each import clobbers it), so re-apply the struct->provider patch
# HERE, at every bazel-bsp invocation (after the copy, before the build reads the file).
# Idempotent + fast. The JavaInfo/CcInfo global fix is the macOS autoload flag in .bazelrc.
# See docs/development/dsp-api-metals-mcp.md.
set -euo pipefail

core=".bazelbsp/aspects/core.bzl"
if [ -f "$core" ]; then
  /usr/bin/python3 tools/metals/patch_bazelbsp_core.py "$core"
fi

# Resolve bazel. When Metals is launched inside the Nix dev shell, `bazel` is a real
# executable on PATH — use it directly (fast). Otherwise (Metals launched without the
# dev-shell PATH, as is common) it is unresolvable — a bare `bazel` can even hit the
# workspace's `bazel/` DIRECTORY via an empty PATH entry — so fall back to `nix develop`
# (nix is on PATH; CWD is the workspace with flake.nix). Either way the macOS autoload
# flag from .bazelrc applies. See docs/development/dsp-api-metals-mcp.md.
bazel_bin="$(command -v bazel 2>/dev/null || true)"
if [ -n "$bazel_bin" ] && [ -x "$bazel_bin" ] && [ ! -d "$bazel_bin" ]; then
  exec "$bazel_bin" "$@"
else
  exec nix develop --command bazel "$@"
fi
