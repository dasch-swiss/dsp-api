# Ingest

Ingest manages the asset files of the DaSCH Service Platform: upload, transcoding, metadata extraction, storage,
bulk ingest, and asset export and import, so that the files are ready for serving with Sipi.

It is a library of dsp-api (package `swiss.dasch`): dsp-api serves its endpoints under `/ingest/` on the API host
(`slice/ingest/` in `modules/webapi`) and runs its Sipi, `ffmpeg` and `ffprobe` processes from the API image. There
is no separate ingest server or image. Its settings are read from `ingest.conf` (`INGEST_*` environment variables).

# Documentation

The `./docs` folder contains the sources to the documentation.
The documentation is published under <https://docs.dasch.swiss/> and managed
by [DSP-DOCS](https://github.com/dasch-swiss/dsp-docs) repository.
Documentation is written in [Markdown](https://www.markdownguide.org/) and built with [MkDocs](https://www.mkdocs.org/)
using [Material for MkDocs](https://squidfunk.github.io/mkdocs-material/) for layout and styling.

## Writing documentation // Build and serve the docs locally

Follow the installation instructions from the dsp-docs repository in order to build and serve the documentation locally.
Once the necessary software is installed you can use `mkdocs serve` for a live preview on <http://127.0.0.1:8000/>.

## Contributing

Our commit messages follow the [Conventional Commits Guidelines](https://www.conventionalcommits.org/en/v1.0.0/).

Source code must be formatted with `just fmt` (Scalafmt via Bazel, which also inserts license headers).

Copyright headers are inserted with `just header-fix` and checked via
`bazel test //tools/license:spdx_header_check`.

