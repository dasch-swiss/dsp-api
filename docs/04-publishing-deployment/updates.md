<!---
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
-->

# Updating Repositories When Upgrading Knora

When a new version of Knora introduces changes that are not backwards-compatible
with existing data, your repository will need to be updated.

## Upgrading from Knora Version 7.0.0 or Later

In most cases, Knora will update your repository automatically when it starts. If
manual changes are needed, these will be described in the release notes, and must be
done first.

Before starting a new version of Knora, back up your repository, so you can restore it
in case the automatic repository update fails. You can use one of these scripts
in `webapi/scripts`:

- `fuseki-dump-repository.sh` for Fuseki
- `graphdb-dump-repository.sh` for GraphDB


For information on command-line options, run the script with no arguments.

## Upgrading from a Knora Version Before 7.0.0

**WARNING**: If you do not follow this procedure, your data may be
corrupted, and Knora may not work.

You must first upgrade to Knora 7.0.0, then upgrade again to the current
version.

The overall procedure is:

1. Back up your repository as described above.

2. Install Knora release 7.0.0, and read the general instructions in
   `upgrade/graphdb-se/old/README.md` in that release.
   
3. Follow the instructions in one of the subsections below for the version you are
   upgrading from.
   
4. Back up your repository again.

5. Install the current release of Knore, and follow any manual update instructions
   in its release notes.
   
6. Start Knora to continue the automatic upgrade.

### Upgrading from Knora 6.0.0 or 6.0.1

1. Follow the instructions in `upgrade/graphdb-se/old/1263-knora-admin/README.md`.

2. Follow the instructions in
   [Upgrading from Knora 7.0.0 or Later](#upgrading-from-knora-7-0-0-or-later).

### Upgrading from Knora 5.0.0 

1. Follow the instructions in `upgrade/graphdb-se/old/1211-datetime/README.md`.

2. Follow the instructions in `upgrade/graphdb-se/old/1230-delete-previews/README.md`.

3. Follow the instructions in `upgrade/graphdb-se/old/1263-knora-admin/README.md`.

4. Follow the instructions in
   [Upgrading from Knora 7.0.0 or Later](#upgrading-from-knora-7-0-0-or-later).
