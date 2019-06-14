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

# Updating Repositories

As explained in
@ref:[Knora Ontology Versions](../../02-knora-ontologies/knora-base.md#knora-ontology-versions),
the `knora-base` ontology contains a version string to ensure compatibility
between a repository and a given version of Knora. The same version string
is therefore hard-coded in the Knora source code, in the string constant
`org.knora.webapi.KnoraBaseVersion`. The format of this string is currently
`PR NNNN`, where `NNNN` is the number of a GitHub pull request. Each time
a pull request introduces changes that are not compatible with existing data,
the following must happen:

- The `knora-base` version string must be updated in `knora-base.ttl` and
  in the string constant `org.knora.webapi.KnoraBaseVersion`. This version
  string must contain the number of the last pull request that introduced
  a breaking change.
  
- A plugin must be added to the
  @ref:[repository update program](../../04-deployment/updates.md), to update
  existing repositories to work with the new version of Knora. The plugin will
  be used by `update-repository.py` if needed.

## Adding an Update Plugin

An update plugin is a Python module, which must be put in a subdirectory
of `upgrade/plugins`. The name of the plugin's directory must be `prNNNN`, where
`NNNN` is the pull request number. That directory must have a subdirectory called
`knora-ontologies`, containing the following built-in Knora ontologies with any
modifications introduced by that pull request:

- `knora-admin.ttl`
- `knora-base.ttl`
- `salsah-gui.ttl`
- `standoff-onto.ttl`

The `knora-base.ttl` file must contain a `knora-base:ontologyVersion` that
matches the pull request number.

Each plugin module must be in a file called `update.py`, and must contain
a class called `GraphTransformer`, defined like this:

```python
import rdflib
from updatelib import rdftools

class GraphTransformer(rdftools.GraphTransformer):
    def transform(self, graph):
        # Do the transformation
```

The `transform` method takes an `rdflib.graph.Graph` and returns a transformed
graph (which may be the same graph instance or a new one). See the
[rdflib documentation](https://rdflib.readthedocs.io/en/stable/index.html)
for details of that class.

This method will be called once for each named graph downloaded from the
triplestore.

The `updatelib.rdftools` library provides convenience functions for working
with RDF data.

## Testing Update Plugins

Each plugin should have a unit test that can be run by
[pytest](https://docs.pytest.org/en/latest/index.html). To run all the
plugin unit tests, run the script `./test.sh` in the `upgrade` directory.
