<!---
Copyright © 2015-2019 the contributors (see Contributors.md).

This file is part of Knora.

Knora is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published
by the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

Knora is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public
License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
-->

# Publishing

Knora is published as a set of [Docker](https://www.docker.com) images under the
[DHLab Basel Dockerhub Organization](https://hub.docker.com/u/dhlabbasel).

The following Docker images are published:

- Knora-API:
  - https://hub.docker.com/r/dhlabbasel/webapi
- GraphDB-SE (includes `KnoraRules.pie`):
  - https://hub.docker.com/r/dhlabbasel/knora-graphdb-se
- GraphDB-Free (includes `KnoraRules.pie`):
  - https://hub.docker.com/r/dhlabbasel/knora-graphdb-free
- Sipi (includes Knora's Sipi scripts):
  - https://hub.docker.com/r/dhlabbasel/knora-sipi
- Knora-Assets (Knora-Base ontologies, test data, and scripts):
  - https://hub.docker.com/r/dhlabbasel/knora-assets
- Knora-Upgrade (Knora upgrade tool):
  - https://hub.docker.com/r/dhlabbasel/knora-upgrade
- Salsah 1:
  - https://hub.docker.com/r/dhlabbasel/salsah1
- Salsah 2:
  - https://hub.docker.com/r/daschswiss/knora-app-web

Knora's Docker images are published automatically through Travis each time a pull-request
is merged into the `develop` branch.

Each image is tagged with a version number, where the version is derived by using the result
of `git describe`. The describe version is built from the
`last tag + number of commits since tag + short hash`, e.g., `8.0.0-7-ga7827e9`.

## GraphDB Licensing

**GraphDB-Free** is the Free Edition of the triplestore from Ontotext (http://ontotext.com).
GraphDB-Free must be licensed separately by the user, by registering with Ontotext, i.e.
filling out the form for downloading the free edition.

**GraphDB-SE** is the Standard Edition of the triplestore from Ontotext (http://ontotext.com).

GraphDB-SE must be licensed separately by the user.