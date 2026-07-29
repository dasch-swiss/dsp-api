#!/usr/bin/env python3
"""Extract an OCI config label value from an oci.pull image layout.

Usage: extract_label.py <layout_dir> <label_key> <out_file>

Walks the OCI layout: index.json -> image manifest blob -> image config blob,
then writes config.config.Labels[<label_key>] to <out_file>. Hermetic (reads
only files already fetched into the pinned image layout; no network).

Used by //tools/buildinfo:oci_config_label to source BuildInfo.sipi from the
pinned sipi base image's `org.opencontainers.image.version` label, so the
MODULE.bazel digest is the single source of the sipi version.
"""
import json
import os
import sys


def _read_blob(layout, digest):
    algo, h = digest.split(":", 1)
    with open(os.path.join(layout, "blobs", algo, h), encoding="utf-8") as f:
        return json.load(f)


def main(layout, label, out):
    with open(os.path.join(layout, "index.json"), encoding="utf-8") as f:
        index = json.load(f)
    manifests = index.get("manifests", [])
    if len(manifests) != 1:
        sys.exit(f"expected exactly 1 manifest in index.json, found {len(manifests)}")
    manifest = _read_blob(layout, manifests[0]["digest"])
    config = _read_blob(layout, manifest["config"]["digest"])
    labels = config.get("config", {}).get("Labels") or {}
    value = labels.get(label)
    if not value:
        sys.exit(f"label {label!r} not found; available labels: {sorted(labels)}")
    with open(out, "w", encoding="utf-8") as f:
        f.write(value)


if __name__ == "__main__":
    if len(sys.argv) != 4:
        sys.exit("usage: extract_label.py <layout_dir> <label_key> <out_file>")
    main(sys.argv[1], sys.argv[2], sys.argv[3])
