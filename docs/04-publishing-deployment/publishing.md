<!---
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
-->

# Publishing

DSP is published as a set of [Docker](https://www.docker.com) images under the
[DaSCH Dockerhub Organization](https://hub.docker.com/u/daschswiss).

The following Docker images are published:

- Knora-API:
  - https://hub.docker.com/r/daschswiss/knora-api
- Sipi (includes DSP's specific Sipi scripts):
  - https://hub.docker.com/r/daschswiss/knora-sipi
- Salsah 1:
  - https://hub.docker.com/r/daschswiss/knora-salsah1
- Salsah 2:
  - https://hub.docker.com/r/daschswiss/knora-app-web

Knora's Docker images are published automatically through Github CI each time a
pull-request is merged into the `main` branch.

Each image is tagged with a version number, where the version is derived by
using the result of `git describe`. The describe version is built from the
`last tag + number of commits since tag + short hash`, e.g., `8.0.0-7-ga7827e9`.

The images can be published locally by running:

```bash
$ make docker-build
```

or to Dockerhub:

```bash
$ make docker-publish
```
