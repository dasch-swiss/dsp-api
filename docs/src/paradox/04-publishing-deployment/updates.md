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

# Updating Repositories When Upgrading Knora

@@toc

When a new version of Knora introduces changes that are not backwards-compatible
with existing data, you will need to update your repository. First, back up
your repository. Then follow the instructions below for the Knora version that
you are upgrading from.

## Upgrading from Knora 7.0.0 or Later

You will need to dump your repository to a file, run Knora's upgrade program
to produce a transformed file, then load the transformed file back into the repository.
The upgrade program automatically determines which changes the repository needs.

### 1. Dump the Repository to a TriG File

You can use the `dump-repository.sh` script in `upgrade/graphdb-se`. See
the `README.md` there for instructions.

### 2. Transform the TriG File

In the `knora-api` directory of the version of Knora you are upgrading to, run:

```
sbt "upgrade/run INPUT_FILE OUTPUT_FILE"
```

For `INPUT_FILE`, use the absolute path of the TriG file you downloaded in
step 1.

For `OUTPUT_FILE`, use the absolute path of the transformed TriG file to
be created.

If the repository is already up to date, the program will say so, and no
output file will be written. In this case, there is nothing more to do.
Otherwise, proceed to step 3.

### 3. Empty the Repository

The transformed TriG file must be loaded into an empty repository.
To empty the repository, you can use the `empty-repository.sh` script in
`upgrade/graphdb-se`. See the `README.md` there for instructions.
Make sure you have backed up the repository first.

### 4. Load the Transformed TriG File into the Repository

You can use the `upload-repository.sh` script in `upgrade/graphdb-se`. See
the `README.md` there for instructions.

If the file is very large, it may be more efficient to load it offline,
using GraphDB's [LoadRDF](http://graphdb.ontotext.com/documentation/free/loading-data-using-the-loadrdf-tool.html)
tool.

## Upgrading from a Knora Version Before 7.0.0

First, read the general instructions in `upgrade/graphdb-se/old/README.md`.

### Upgrading from Knora 6.0.0 or 6.0.1

1. Follow the instructions in `upgrade/graphdb-se/old/1263-knora-admin/README.md`.

2. Follow the instructions in
   @ref:[Upgrading from Knora 7.0.0 or Later](#upgrading-from-knora-7-0-0-or-later).

### Upgrading from Knora 5.0.0 

1. Follow the instructions in `upgrade/graphdb-se/old/1211-datetime/README.md`.

2. Follow the instructions in `upgrade/graphdb-se/old/1230-delete-previews/README.md`.

3. Follow the instructions in `upgrade/graphdb-se/old/1263-knora-admin/README.md`.

4. Follow the instructions in
   @ref:[Upgrading from Knora 7.0.0 or Later](#upgrading-from-knora-7-0-0-or-later).
