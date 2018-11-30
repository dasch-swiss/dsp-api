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

# Setting Up Sipi for Knora

## Setup and Execution

In order to serve files to the client application like the Salsah GUI,
Sipi must be set up and running. Sipi can be downloaded from its own
GitHub repository: <https://github.com/dhlab-basel/Sipi> (which requires
building from source), or the published [docker image](https://hub.docker.com/r/dhlabbasel/sipi/).
can be used. To start Sipi, run the following command from inside the `sipi/`
folder:

```
$ export DOCKERHOST=LOCAL_IP_ADDRESS
$ docker image rm --force dhlabbasel/sipi:develop // deletes cached image and needs only to be used when newer image is available on dockerhub
$ docker run --rm -it --add-host webapihost:$DOCKERHOST -v $PWD/config:/sipi/config -v $PWD/scripts:/sipi/scripts -v /tmp:/tmp -v $HOME:$HOME -p 1024:1024 dhlabbasel/sipi:develop --config=/sipi/config/sipi.knora-docker-config.lua
```

where `LOCAL_IP_ADDRESS` is the IP of the host running the `Knora`.

`--config=/sipi/config/sipi.knora-docker-config.lua` (or `--config=/sipi/config/sipi.knora-docker-it-config.lua` for
using sipi for integration testing). Please see `sipi.knora-docker-config.lua` for the settings like URL, port number
etc. These settings need to be set accordingly in Knora's `application.conf`. If you use the default settings both in
Sipi and Knora, there is no need to change these settings.

Whenever a file is requested from Sipi (e.g. a browser trying to
dereference an image link served by Knora), a preflight function is
called. This function is defined in `sipi.init-knora.lua` present in the
Sipi root directory. It takes three parameters: `prefix`, `identifier`
(the name of the requested file), and `cookie`. File links created by
Knora use the prefix `knora`, e.g.
`http://localhost:1024/knora/incunabula_0000000002.jp2/full/2613,3505/0/default.jpg`.

Given these information, Sipi asks Knora about the current's users
permissions on the given file. The cookie contains the current user's
Knora session id, so Knora can match Sipi's request with a given user
profile and determine the permissions this user has on the file. If the
Knora response grants sufficient permissions, the file is served in the
requested quality. If the suer has preview rights, Sipi serves a reduced
quality or integrates a watermark. If the user has no permissions, Sipi
refuses to serve the file. However, all of this behaviour is defined in
the preflight function in Sipi and not controlled by Knora. Knora only
provides the permission code.

See @ref:[Sharing the Session ID with Sipi](sipi-and-knora.md#sharing-the-session-id-with-sipi) for more
information about sharing the session id.

## Using Sipi in Test Mode

If you just want to test Sipi with Knora without serving the actual
files (e.g. when executing browser tests), you can simply start Sipi
like this:

```
$ export DOCKERHOST=LOCAL_IP_ADDRESS
$ docker image rm --force dhlabbasel/sipi:develop // deletes cached image and needs only to be used when newer image is available on dockerhub
$ docker run --rm -it --add-host webapihost:$DOCKERHOST -v $PWD/config:/sipi/config -v $PWD/scripts:/sipi/scripts -v /tmp:/tmp -v $HOME:$HOME -p 1024:1024 dhlabbasel/sipi:develop --config=/sipi/config/sipi.knora-docker-test-config.lua
```

Then always the same test file will be served which is included in Sipi. In test mode, Sipi will
not aks Knora about the user's permission on the requested file.

## Using Sipi production behind proxy

For SIPI to work with Salsah1 (non-angular) GUI, we need to define an additional set of
environment variables if we want to run SIPI behind a proxy:

- `SIPI_EXTERNAL_PROTOCOL=https`
- `SIPI_EXTERNAL_HOSTNAME=iiif.example.org`
- `SIPI_EXTERNAL_PORT=443`

These variables are only used by `make_thumbnail.lua`:

@@snip[make_thumbnail.lua](../../../../sipi/scripts/make_thumbnail.lua) { #snip_marker }