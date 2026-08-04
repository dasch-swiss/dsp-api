#!/usr/bin/env python3
"""Patch bazel-bsp's aspect `core.bzl` for Bazel 9 compatibility (Metals support).

bazel-bsp 4.0.x (the server Metals 1.6.7 downloads) predates Bazel 9. Its sync aspect
`_bsp_target_info_aspect_impl` does `return struct(bsp_info=..., output_groups=...)`, and
Bazel 9 removed struct-returning aspect impls (and the legacy `target.bsp_info` provider
access) with no re-enable flag. This rewrites that to a real provider:

  - define `BspInfo = provider()`
  - return `[OutputGroupInfo(**output_groups), BspInfo(**exported_properties)]`
  - read it on deps as `dep[BspInfo]` instead of `dep.bsp_info`
    (with a `BspInfo in dep` presence guard where the code relied on legacy None-ability)

bazel-bsp re-copies its aspects into `.bazelbsp/` on every import, so this is invoked from
the `bazel_binary` wrapper (tools/metals/bazel-bsp-wrapper.sh) at each bazel-bsp invocation
— after the copy, before the build reads the file — and is idempotent. The companion
JavaInfo/CcInfo global fix is the macOS `--incompatible_autoload_externally` flag in .bazelrc.
See docs/development/dsp-api-metals-mcp.md.
"""
import sys

MARKER = "BspInfo = provider"

# (old, new) applied in order. Order matters: guard the truthiness check before the
# blanket `.bsp_info` -> `[BspInfo]` rewrite turns it into an unguarded provider access.
EDITS = [
    # presence guard: providers raise (not None) when absent
    (
        'return len(deps) == 1 and deps[0].bsp_info and deps[0].bsp_info.kind == "proto_library"',
        'return len(deps) == 1 and BspInfo in deps[0] and deps[0][BspInfo].kind == "proto_library"',
    ),
    # every remaining accessor
    (".bsp_info", "[BspInfo]"),
    # modernize the return: legacy struct -> provider list
    (
        "    return struct(\n        bsp_info = struct(**exported_properties),\n        output_groups = output_groups,\n    )",
        "    return [\n        OutputGroupInfo(**output_groups),\n        BspInfo(**exported_properties),\n    ]",
    ),
]

PROVIDER_DEF = (
    "\n# dsp-api vendored patch: Bazel 9 removed struct-returning aspect impls and the\n"
    "# legacy `target.bsp_info` provider. Replace with a real provider.\n"
    "BspInfo = provider(fields = None)\n"
)


def patch(text):
    # insert the provider definition right after the java_info load
    anchor = 'load("//aspects:rules/java/java_info.bzl", "java_info_in_target", "java_info_reference")\n'
    if anchor not in text:
        raise SystemExit("patch_bazelbsp_core: expected java_info load line not found; aspect layout changed")
    text = text.replace(anchor, anchor + PROVIDER_DEF, 1)
    for old, new in EDITS:
        if old not in text:
            raise SystemExit(f"patch_bazelbsp_core: expected snippet not found (aspect layout changed):\n  {old[:60]}...")
        text = text.replace(old, new)
    return text


def main(argv):
    if len(argv) != 2:
        raise SystemExit("usage: patch_bazelbsp_core.py <path-to-core.bzl>")
    path = argv[1]
    with open(path, encoding="utf-8") as f:
        text = f.read()
    if MARKER in text:
        return 0  # already patched (idempotent)
    with open(path, "w", encoding="utf-8") as f:
        f.write(patch(text))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
