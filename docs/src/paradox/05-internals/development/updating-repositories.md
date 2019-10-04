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
`org.knora.webapi.KnoraBaseVersion`. For new pull requests, the format of this string
is `knora-base vN`, where `N` is an integer that is incremented for
each version. Each time a pull request introduces changes that are not compatible
with existing data, the following must happen:

- The `knora-base` version number must be incremented in `knora-base.ttl` and
  in the string constant `org.knora.webapi.KnoraBaseVersion`.
  
- A plugin must be added in the `upgrade` subproject under `org.knora.upgrade.plugins`,
  and registered in `org.knora.upgrade.Main`, to transform
  existing repositories so that they are compatible with the code changes
  introduced in the pull request.

## Adding an Upgrade Plugin

An upgrade plugin is a Scala class that extends `UpgradePlugin`. The name of the plugin
class should refer to the pull request that made the transformation necessary,
using the format `UpgradePluginPRNNNN`, where `NNNN` is the number of the pull request.

A plugin's `transform` method takes an RDF4J `Model` (a mutable object representing
the repository) and modifies it as needed. For details on how to do this, see
[The RDF Model API](https://rdf4j.eclipse.org/documentation/programming/model/)
in the RDF4J documentation.

The plugin must then be added to the collection `pluginsForVersions` in
`org.knora.upgrade.Main`.

## Testing Update Plugins

Each plugin should have a unit test that extends `UpgradePluginSpec`. A typical
test loads a TriG file containing test data into a `Model`, runs the plugin,
makes an RDF4J `SailRepository` containing the transformed `Model`, and uses
SPARQL to check the result.

## Design Rationale

We tried and rejected other designs:

- Running SPARQL updates in the triplestore: too slow, and no way to report
  progress during the update.
  
- Downloading the repository and transforming it in Python using
  [rdflib](https://rdflib.readthedocs.io/en/stable/): too slow.
  
- Downloading the repository and transforming it in C++ using
  [Redland](http://librdf.org): also too slow.

The Scala implementation is the fastest by far.

The whole repository is uploaded in a single transaction because
GraphDB's consistency checker can enforce dependencies between named
graphs.
