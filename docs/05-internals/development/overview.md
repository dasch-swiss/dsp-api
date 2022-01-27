<!---
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
-->

# Overview

Developing for DSP-API requires a complete local
installation of Knora. The different parts are:

1. The cloned [DSP-API](https://github.com/dasch-swiss/dsp-api) Github
   repository
1. One of the supplied triplestores in the DSP-API Github repository
    (GraphDB-SE 8 or Fuseki 3).
1. Sipi by building from
    [source](https://github.com/dasch-swiss/sipi) or using the docker
    [image](https://hub.docker.com/r/daschswiss/sipi/)

## Knora Github Repository

    $ git clone https://github.com/dasch-swiss/dsp-api

## Triplestore

A number of triplestore implementations are available, including [free
software](http://www.gnu.org/philosophy/free-sw.en.html) as well as
proprietary options. DSP-API is designed to work with any
standards-compliant triplestore. It is primarily tested with [Ontotext
GraphDB](http://ontotext.com/products/graphdb/), a high-performance,
proprietary triplestore. We recommend GraphDB Standard Edition, but
GraphDB Free (which is proprietary but available free of charge) also
works.

DSP-API includes support for [Apache Jena](https://jena.apache.org/),
which is [free software](http://www.gnu.org/philosophy/free-sw.en.html),
but use of Jena is deprecated, and support for it will probably be
removed in the future.

Built-in support and configuration for other triplestores is planned.

See the chapter on [Starting GraphDB](graphdb.md) for more details.

## Sipi

### Build Sipi Docker Image

The Sipi docker image needs to be build by hand, as it requires the
Kakadu distribution.

To build the image, and push it to the docker hub, follow the following
steps:

```
$ git clone https://github.com/dhlab-basel/docker-sipi
(copy the Kakadu distribution ``v7_8-01382N.zip`` to the ``docker-sipi`` directory)
$ docker build -t daschswiss/sipi
$ docker run --name sipi --rm -it -p 1024:1024 daschswiss/sipi
(Ctrl-c out of terminal will stop and delete container)
$ docker push daschswiss/sipi
```

Pushing the image to the docker hub requires prior authentication with
`$ docker login`. The user needs to be registered on `hub.docker.com`.
Also, the user needs to be allowed to push to the `dblabbasel`
organisation.

### Running Sipi

To use the docker image stored locally or on the docker hub repository
type:

```
$ docker run --name sipi -d -p 1024:1024 daschswiss/sipi
```

This will create and start a docker container with the `daschswiss/sipi`
image in the background. The default behaviour is to start Sipi by
calling the following command:

```
$ /sipi/local/bin/sipi -config /sipi/config/sipi.knora-test-config.lua
```

To override this default behaviour, start the container by supplying
another config file:

```
$ docker run --name sipi \
             -d \
             -p 1024:1024 \
             daschswiss/sipi \
             /sipi/local/bin/sipi -config /sipi/config/sipi.config.lua
```

You can also mount a directory (the local directory in this example),
and use a config file that is outside of the docker container:

```
$ docker run --name sipi \
             -d \
             -p 1024:1024 \
             -v $PWD:/localdir \
             daschswiss/sipi \
             /sipi/local/bin/sipi -config /localdir/sipi.knora-test-config.lua
```

## Redis Server

The DSP-API server uses Redis for caching.

On macOS you can install Redis through [Homebrew](https://brew.sh):

```bash
$ brew install redis
```

If you don't want to use Redis, you can disable caching in `application.conf`
via the `app.use-redis-cache` key, by setting it to `false`.
