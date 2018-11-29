<!---
Copyright © 2015-2018 the contributors (see Contributors.md).

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

# Knora and Sipi

@@toc

## Configuration

The Knora-specific configuration and scripts for Sipi are in the
`sipi` subdirectory of the Knora source tree. See the `README.md` there for
instructions on how to start Sipi with Knora.

## Lua Scripts

Knora API v2 uses custom Lua scripts to control Sipi. These scripts can be
found in `sipi/scripts` in the Knora source tree.

Each of these scripts expects a [JSON Web Token](https://jwt.io/) in the
URL parameter `token`. In all cases, the token must be signed by Knora,
it must have an expiration date and not have expired, its issuer must be `Knora`,
and its audience must include `Sipi`. The other contents of the expected tokens
are described below.

### upload.lua

The `upload.lua` script is available at Sipi's `upload` route. It processes one
or more file uploads submitted to Sipi. It converts uploaded images to JPEG 2000
format, and stores them in Sipi's `tmp` directory. The usage of this script is described in
@ref:[Creating File Values](../../../03-apis/api-v2/editing-values.md#creating-file-values).

Each time `upload.lua` processes a request, it also deletes old temporary files
from `tmp` and (recursively) from any subdirectories. The maximum allowed age of
temporary files can be set in Sipi's configuration file, using the parameter
`max_temp_file_age`, which takes a value in seconds, and defaults to
86400 (1 day).

### store.lua

The `store.lua` script is available at Sipi's `store` route. It moves a file
from temporary to permanent storage. It expects an HTTP `POST` request containing
`application/x-www-form-urlencoded` data with the parameter `filename`, whose
value is the internal Sipi-generated filename of the file to be moved.

The JWT sent to this script must contain the key `knora-data`, whose value
must be a JSON object containing:

- `permission`: must be `StoreFile`
- `filename`: must be the same as the filename submitted in the form data

### delete_temp_file.lua

The `delete_temp_file.lua` script is available at Sipi's `delete_temp_file` route.
It is used only if Knora rejects a file value update request. It expects an
HTTP `DELETE` request, with a filename as the last component of the URL.

The JWT sent to this script must contain the key `knora-data`, whose value
must be a JSON object containing:

- `permission`: must be `DeleteTempFile`
- `filename`: must be the same as the filename submitted in the URL

## SipiResponderV2

In Knora, the responder `SipiResponderV2` handles all communication with Sipi.
It blocks while processing each request, to ensure that the number of
concurrent requests to Sipi is not greater than
`akka.actor.deployment./responderManager/sipiRouterV2.nr-of-instances`.
If it encounters an error, it returns `SipiException`.

## The Image File Upload Workflow

1. The client uploads an image file to the `upload` route, which runs
  `upload.lua`. The image is converted to JPEG 2000 and stored in Sipi's `tmp`
  directory. In the response, the client receives the JPEG 2000's unique,
  randomly generated filename.
2. The client submits a JSON-LD request to a Knora route (`/v2/values` or `/v2/resources`)
   to create or change a file value. The request includes Sipi's internal filename.
3. During parsing of this JSON-LD request, a `StillImageFileValueContentV2`
   is constructed to represent the file value. During the construction of this
   object, a `GetImageMetadataRequestV2` is sent to `SipiResponderV2`, which
   uses Sipi's built-in `knora.json` route to get the rest of the file's
   metadata.
4. A responder (`ResourcesResponderV2` or `ValuesResponderV2`) validates
   the request and updates the triplestore. (If it is `ResourcesResponderV2`,
   it asks `ValuesResponderV2` to generate SPARQL for the values.)
5. The responder that did the update calls `ValueUtilV2.doSipiPostUpdate`.
   If the triplestore update was successful, this method sends
   `MoveTemporaryFileToPermanentStorageRequestV2` to `SipiResponderV2`, which
   makes a request to Sipi's `store` route. Otherwise, the same method sends
   `DeleteTemporaryFileRequestV2` to `SipiResponderV2`, which makes a request
   to Sipi's `delete_temp_file` route.

If the request to Knora cannot be parsed, the temporary file is not deleted
immediately, but it will be deleted during the processing of a subsequent
request by Sipi's `upload` route.

If Sipi's `store` route fails, Knora returns the `SipiException` to the client.
In this case, manual intervention may be necessary to restore consistency
between Knora and Sipi.

If Sipi's `delete_temp_file` route fails, the error is not returned to the client,
because there is already a Knora error that needs to be returned to the client.
In this case, the Sipi error is simply logged.
