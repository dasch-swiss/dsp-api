<!---
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
-->

# Setting Up Sipi for DSP-API

## Setup and Execution

In order to serve files to the client application like the Salsah GUI,
Sipi must be set up and running. Sipi can be downloaded from its own
GitHub repository: <https://github.com/dasch-swiss/sipi> (which requires
building from source), or the published [docker image](https://hub.docker.com/r/daschswiss/sipi/).
can be used. To start Sipi, run the following command from inside the `sipi/`
folder:

```
$ export DOCKERHOST=LOCAL_IP_ADDRESS
$ docker image rm --force daschswiss/sipi:main // deletes cached image and needs only to be used when newer image is available on dockerhub
$ docker run --rm -it --add-host webapihost:$DOCKERHOST -v $PWD/config:/sipi/config -v $PWD/scripts:/sipi/scripts -v /tmp:/tmp -v $HOME:$HOME -p 1024:1024 daschswiss/sipi:main --config=/sipi/config/sipi.docker-config.lua
```

where `LOCAL_IP_ADDRESS` is the IP of the host running `DSP-API`.

`--config=/sipi/config/sipi.docker-config.lua`. Please see `sipi.docker-config.lua` for the settings like URL, port number
etc. These settings need to be set according to DSP-API's `application.conf`. If you use the default settings both in
Sipi and DSP-API, there is no need to change these settings.

Whenever a file is requested from Sipi (e.g. a browser trying to
dereference an image link served by DSP-API), a preflight function is
called. This function is defined in `sipi.init.lua` present in the
Sipi root directory. It takes three parameters: `prefix`, `identifier`
(the name of the requested file), and `cookie`. The prefix is the shortcode
of the project that the resource containing the file value belongs to.

Given this information, Sipi asks the API about the current user's
permissions on the given file. The cookie contains the current user's 
session id, so the API can match Sipi's request with a given user
profile and determine the permissions this user has on the file. If the
response grants sufficient permissions, the file is served in the
requested quality. If the user has preview rights, Sipi serves the file in reduced
quality or integrates a watermark. If the user has no permissions, Sipi
refuses to serve the file. However, all of this behaviour is defined in
the preflight function in Sipi and not controlled by the API. DSP-API only
provides the permission code.

See [Authentication of Users with Sipi](sipi-and-dsp-api.md#authentication-of-users-with-sipi) for more
information about sharing the session ID.

## Using Sipi in Test Mode

If you just want to test Sipi with DSP-API without serving the actual
files (e.g. when executing browser tests), you can simply start Sipi
like this:

```
$ export DOCKERHOST=LOCAL_IP_ADDRESS
$ docker image rm --force daschswiss/sipi:main // deletes cached image and needs only to be used when newer image is available on dockerhub
$ docker run --rm -it --add-host webapihost:$DOCKERHOST -v $PWD/config:/sipi/config -v $PWD/scripts:/sipi/scripts -v /tmp:/tmp -v $HOME:$HOME -p 1024:1024 daschswiss/sipi:main --config=/sipi/config/sipi.docker-test-config.lua
```

Then always the same test file will be served which is delivered with Sipi. In test mode, Sipi will
not ask DSP-API about the user's permission on the requested file.

## Additional Sipi Environment Variables

Additionally, these environment variables can be used to further configure Sipi:

- `SIPI_WEBAPI_HOSTNAME=localhost`: overrides `knora_path` in Sipi's config
- `SIPI_WEBAPI_PORT=3333`: overrides `knora_port` in Sipi's config

These variables need to be explicitly used like in `sipi.init.lua`:

```lua
    --
    -- Allows to set SIPI_WEBAPI_HOSTNAME environment variable and use its value.
    --
    local webapi_hostname = os.getenv("SIPI_WEBAPI_HOSTNAME")
    if webapi_hostname == nil then
        webapi_hostname = config.knora_path
    end
    server.log("webapi_hostname: " .. webapi_hostname, server.loglevel.LOG_DEBUG)

    --
    -- Allows to set SIPI_WEBAPI_PORT environment variable and use its value.
    --
    local webapi_port = os.getenv("SIPI_WEBAPI_PORT")
    if webapi_port == nil then
        webapi_port = config.knora_port
    end
    server.log("webapi_port: " .. webapi_port, server.loglevel.LOG_DEBUG)

    knora_url = 'http://' .. webapi_hostname .. ':' .. webapi_port .. '/admin/files/' .. prefix .. '/' ..  identifier
```
