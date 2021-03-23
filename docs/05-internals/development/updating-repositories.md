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

# Updating Repositories

## Requirements

- When a new version of Knora requires an existing repository to be updated,
  do this automatically when Knora starts, if possible.
  
- Make the update process as fast as possible, with some indication of progress
  as it runs.

## Design

As explained in
[Knora Ontology Versions](../../02-knora-ontologies/knora-base.md#knora-ontology-versions),
the `knora-base` ontology contains a version string to ensure compatibility
between a repository and a given version of Knora. The same version string
is therefore hard-coded in the Knora source code, in the string constant
`org.knora.webapi.KnoraBaseVersion`. For new pull requests, the format of this string
is `knora-base vN`, where `N` is an integer that is incremented for
each version.

During Knora's startup process, `ApplicationActor` sends an `UpdateRepositoryRequest`
message to the `StoreManager`, which forwards it to `TriplestoreManager`, which delegates
it to `org.knora.webapi.store.triplestore.upgrade.RepositoryUpdater`.

`RepositoryUpdater` does the following procedure:

1. Check the `knora-base` version string in the repository.

2. Consult `org.knora.webapi.store.triplestore.upgrade.RepositoryUpdatePlan` to see which
   transformations are needed.

3. Download the entire repository from the triplestore into an N-Quads file.

4. Read the N-Quads file into an `RdfModel`.

5. Update the `RdfModel` by running the necessary transformations, and replacing the
   built-in Knora ontologies with the current ones.

6. Save the `RdfModel` to a new N-Quads file.

7. Empty the repository in the triplestore.

8. Upload the transformed repository file to the triplestore.

To update the `RdfModel`, `RepositoryUpdater` runs a sequence of upgrade plugins, each of which
is a class in `org.knora.webapi.store.triplestore.upgrade.plugins` and is registered
in `RepositoryUpdatePlan`.

## Design Rationale

We tried and rejected several other designs:

- Running SPARQL updates in the triplestore: too slow, and no way to report
  progress during the update.
  
- Downloading the repository and transforming it in Python using
  [rdflib](https://rdflib.readthedocs.io/en/stable/): too slow.
  
- Downloading the repository and transforming it in C++ using
  [Redland](http://librdf.org): also too slow.

The Scala implementation is the fastest by far.

The whole repository is uploaded in a single transaction, rather than uploading one named
graph at a time, because GraphDB's consistency checker can enforce dependencies between
named graphs.

## Adding an Upgrade Plugin

Each time a pull request introduces changes that are not compatible
with existing data, the following must happen:

- The `knora-base` version number must be incremented in `knora-base.ttl` and
  in the string constant `org.knora.webapi.KnoraBaseVersion`.
  
- A plugin must be added in the package `org.knora.webapi.store.triplestore.upgrade.plugins`,
  to transform existing repositories so that they are compatible with the code changes
  introduced in the pull request. Each new plugin must be registered
  by adding it to the sequence returned by `RepositoryUpdatePlan.makePluginsForVersions`.

The order of version numbers (and the plugins) must correspond to the order in which the
pull requests are merged.

An upgrade plugin is a Scala class that extends `UpgradePlugin`. The name of the plugin
class should refer to the pull request that made the transformation necessary,
using the format `UpgradePluginPRNNNN`, where `NNNN` is the number of the pull request.

A plugin's `transform` method takes an `RdfModel` (a mutable object representing
the repository) and modifies it as needed.

Before transforming the data, a plugin can check whether a required manual transformation
has been carried out. If the requirement is not met, the plugin can throw
`InconsistentRepositoryDataException` to abort the upgrade process.

## Testing Update Plugins

Each plugin should have a unit test that extends `UpgradePluginSpec`. A typical
test loads a file containing RDF test data into a `RdfModel`, runs the plugin,
makes an `RdfRepository` containing the transformed `RdfModel`, and uses
SPARQL to check the result.
