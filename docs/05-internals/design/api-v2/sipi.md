<!---
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
-->

# DSP-API and Sipi

## Configuration

The DSP-API specific configuration and scripts for Sipi are in the
`sipi` subdirectory of the DSP-API source tree. See the `README.md` for
instructions on how to start Sipi with DSP-API.

## Lua Scripts

DSP-API v2 uses custom Lua scripts to control Sipi. These scripts can be
found in `sipi/scripts` in the DSP-API source tree.

Each of these scripts expects a [JSON Web Token](https://jwt.io/) in the
URL parameter `token`. In all cases, the token must be signed by DSP-API,
it must have an expiration date and not have expired, its issuer must equal
the hostname and port of the API, and its audience must include `Sipi`.
The other contents of the expected tokens are described below.
The `clean_temp_dir` route requires basic authentication.

## SipiConnector

In DSP-API, the `org.knora.webapi.iiif.SipiConnector` handles all communication
with Sipi. It blocks while processing each request, to ensure that the number of
concurrent requests to Sipi is not greater than
`akka.actor.deployment./storeManager/iiifManager/sipiConnector.nr-of-instances`.
If it encounters an error, it returns `SipiException`.
