# Dsp-Ingest

[![Github](https://img.shields.io/github/v/tag/dasch-swiss/dsp-ingest?include_prereleases&label=Github%20tag)](https://github.com/dasch-swiss/dsp-ingest)
[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
![Build And Test](https://github.com/dasch-swiss/dsp-ingest/actions/workflows/ci.yml/badge.svg)
[![Codacy Badge](https://app.codacy.com/project/badge/Grade/3717de9ffb22413c98c23161a0242799)](https://app.codacy.com/gh/dasch-swiss/dsp-ingest/dashboard?utm_source=gh&utm_medium=referral&utm_content=&utm_campaign=Badge_grade)
[![Codacy Badge](https://app.codacy.com/project/badge/Coverage/3717de9ffb22413c98c23161a0242799)](https://app.codacy.com/gh/dasch-swiss/dsp-ingest/dashboard?utm_source=gh&utm_medium=referral&utm_content=&utm_campaign=Badge_coverage)

The dsp-ingest service is designed to streamline and optimize the process of ingesting and managing data within the
DaSCH Service Platform.
By automating the ingestion process, DSP-Ingest enables efficient data collection, transformation, transcoding,
meta-data extraction, and storage, ensuring that the data is readily available for serving with Sipi.
This service aims to enhance the overall performance and effectiveness of DSP applications by simplifying and
accelerating the data ingestion workflow.

# Documentation

The `./docs` folder contains the sources to the documentation.
The documentation is published under <https://docs.dasch.swiss/> and managed
by [DSP-DOCS](https://github.com/dasch-swiss/dsp-docs) repository.
Documentation is written in [Markdown](https://www.markdownguide.org/) and built with [MkDocs](https://www.mkdocs.org/)
using [Material for MkDocs](https://squidfunk.github.io/mkdocs-material/) for layout and styling.

## Writing documentation // Build and serve the docs locally

Follow the installation instructions from the dsp-docs repository in order to build and serve the documentation locally.
Once the necessary software is installed you can use `mkdocs serve` for a live preview on  http://127.0.0.1:8000/.
