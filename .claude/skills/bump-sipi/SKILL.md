---
name: bump-sipi
description: Bump the SIPI (or Fuseki) image version in this repo. Use when applying a new `daschswiss/sipi` release, updating the `oci.pull` digests in `MODULE.bazel`, or asking where the sipi/Fuseki image version lives.
allowed-tools: Bash, Read, Edit, Grep
---

# Bumping the SIPI version

The sipi version lives in **one place**: the two `oci.pull` digests in `MODULE.bazel`. There is no
duplicated tag string — the `/version` endpoint reads the tag from the pulled image's own
`org.opencontainers.image.version` OCI label (`//tools/buildinfo:oci_config_label`).

When applying a new `daschswiss/sipi` release, update `MODULE.bazel`: the two `oci.pull` blocks
(`sipi_base_amd64`, `sipi_base_arm64`) are pinned by **per-arch single-manifest digest**, not the tag.
The arm64 index entry carries a `v8` variant that a bare `linux/arm64` request does not match, so pull
each platform's manifest directly. Also update the tag and the index digest in the comment above them
(the comment tag is documentation + a Renovate anchor; the digests are what the build uses).

Get the digests for the target tag with:

```bash
docker buildx imagetools inspect daschswiss/sipi:vX.Y.Z --raw \
  | jq -r '.manifests[] | "\(.platform.os)/\(.platform.architecture)\(.platform.variant // "") \(.digest)"'
docker buildx imagetools inspect daschswiss/sipi:vX.Y.Z | grep -i digest   # index digest
```

`docker-compose.yml` needs no change — it uses `daschswiss/knora-sipi:latest` (the derived image built
from this base), not a pinned version. After bumping, remember to sync the same version in the
**ops-deploy** repo when deploying the DSP release.

(The **Fuseki** image is versioned by release-please like knora-api/dsp-ingest — its tag is the DSP
release/git version, not a hand-typed one. The only in-repo Fuseki version is the **Jena dist version**
(`FUSEKI_DIST_VERSION` in `MODULE.bazel`), which drives the `@fuseki_dist` tarball, the `/version`
report, and the image's OTLP `service.version` resource attribute — see "Updating Jena/Fuseki" in
`modules/fuseki/README.md`.)
