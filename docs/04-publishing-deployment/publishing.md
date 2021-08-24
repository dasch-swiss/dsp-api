<!---
Copyright © 2015-2021 the contributors (see Contributors.md).

This file is part of DSP — DaSCH Service Platform.

DSP is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published
by the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

DSP is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public
License along with DSP. If not, see <http://www.gnu.org/licenses/>.
-->

# Publishing

Knora is published as a set of [Docker](https://www.docker.com) images under the
[DaSCH Basel Dockerhub Organization](https://hub.docker.com/u/daschswiss).

The following Docker images are published:

- Knora-API:
    - https://hub.docker.com/r/daschswiss/knora-api
- Jena Fuseki:
    - https://hub.docker.com/r/daschswiss/knora-jena-fuseki
- Sipi (includes Knora's Sipi scripts):
    - https://hub.docker.com/r/daschswiss/knora-sipi
- Salsah 1:
    - https://hub.docker.com/r/daschswiss/knora-salsah1
- Salsah 2:
    - https://hub.docker.com/r/daschswiss/knora-app-web

Knora's Docker images are published automatically through Github CI each time a pull-request is merged into the `main`
branch.

Each image is tagged with a version number, where the version is derived by using the result of `git describe`. The
describe version is built from the
`last tag + number of commits since tag + short hash`, e.g., `8.0.0-7-ga7827e9`.

The images can be published locally by running:

```bash
$ make docker-build
```

or to Dockerhub:

```bash
$ make docker-publish
```
