# Third-Party Dependencies

Third party libraries are declared for the Bazel build in `MODULE.bazel` via the `maven.install`
extension. The resolved dependency graph is pinned in `maven_install.json`, which is the lock file
Bazel reads. There is no longer an sbt build or a `project/Dependencies.scala` file — `MODULE.bazel`
is the single source of truth for third-party versions.

## Adding or updating a dependency

### Declaring a coordinate

Maven coordinates are listed in the `maven.install` block in `MODULE.bazel`, one string per
artifact in the usual `group:artifact:version` form:

```starlark
maven.install(
    artifacts = [
        "dev.zio:zio_3:2.1.14",
        "com.softwaremill.sttp.tapir:tapir-core_3:1.11.10",
        # ...
    ],
    # ...
)
```

To add a library, add its coordinate to that list.

### Re-pinning the lock file

After changing any coordinate, re-pin the lock so `maven_install.json` matches the declared
artifacts:

```shell
bazel run @unpinned_maven//:pin
```

Commit the resulting `MODULE.bazel` and `maven_install.json` changes together.

### Using a dependency in a module

Reference the resolved artifact from a `BUILD.bazel` target's `deps` using its `@maven` label,
e.g. `@maven//:dev_zio_zio_3`. Each Bazel module under `modules/` declares only the dependencies
it actually needs.

## Automated updates

Third-party version bumps are proposed automatically by **Renovate** (configured in
`.github/renovate.json`), which opens PRs updating the coordinates in `MODULE.bazel` and re-pinning
`maven_install.json`. The old sbt-native `scala-steward` tooling, the
`//tools/deps:maven_versions_match_sbt` drift check, and the `Sync Bazel deps with sbt` workflow
have all been removed together with the sbt build.

## Docker Image Versions

The required Docker image versions of Sipi and Fuseki are also declared in `MODULE.bazel`:

- **Sipi** is pinned by digest in the `oci.pull` blocks; its human-readable version is read from
  the image's OCI label rather than a separate version string.
- **Fuseki** is declared via `image_versions.fuseki` and consumed through `@dsp_image_versions`.

### Bumping Sipi

The Sipi version lives in **one place**: the two `oci.pull` digests in `MODULE.bazel`. There is no
duplicated tag string — the `/version` endpoint reads the tag from the pulled image's own
`org.opencontainers.image.version` OCI label (`//tools/buildinfo:oci_config_label`).

When applying a new `daschswiss/sipi` release, update the two `oci.pull` blocks
(`sipi_base_amd64`, `sipi_base_arm64`). They are pinned by **per-arch single-manifest digest**, not
by tag: the arm64 index entry carries a `v8` variant that a bare `linux/arm64` request does not
match, so pull each platform's manifest directly. Also update the tag and the index digest in the
comment above them — the comment tag is documentation and a Renovate anchor, while the digests are
what the build actually uses.

Get the digests for the target tag with:

```bash
docker buildx imagetools inspect daschswiss/sipi:vX.Y.Z --raw \
  | jq -r '.manifests[] | "\(.platform.os)/\(.platform.architecture)\(.platform.variant // "") \(.digest)"'
docker buildx imagetools inspect daschswiss/sipi:vX.Y.Z | grep -i digest   # index digest
```

`docker-compose.yml` needs no change — it uses `daschswiss/knora-sipi:latest`, the derived image
built from this base, rather than a pinned version. After bumping, sync the same version in the
[ops-deploy](https://github.com/dasch-swiss/ops-deploy) repository when deploying the DSP release.

### Bumping Fuseki

The Fuseki image is versioned by release-please like knora-api and dsp-ingest — its tag is the DSP
release/git version, not a hand-typed one. The only in-repo Fuseki version is the **Jena dist
version** (`FUSEKI_DIST_VERSION` in `MODULE.bazel`), which drives the `@fuseki_dist` tarball, the
`/version` report, and the image's OTLP `service.version` resource attribute. See
"Updating Jena/Fuseki" in `modules/fuseki/README.md`.
