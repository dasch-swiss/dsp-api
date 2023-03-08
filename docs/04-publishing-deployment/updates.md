<!---
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
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
in case the automatic repository update fails. For Fuseki use `fuseki-dump-repository.sh`
script located in `webapi/scripts`.

For information on command-line options, run the script with no arguments.
