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

# Overview

@@toc

Developing for Knora requires a complete local
installation of Knora. The different parts are:

1.  The cloned [Knora](https://github.com/dhlab-basel/Knora) Github
    repository
2.  One of the supplied triplestores in the Knora Github repository
    (GraphDB-SE 8 or Fuseki 3).
3.  Sipi by building from
    [source](https://github.com/dhlab-basel/Sipi) or using the docker
    [image](https://hub.docker.com/r/dhlabbasel/sipi/)

## Knora Github Repository

    $ git clone https://github.com/dhlab-basel/Knora

## Triplestore

A number of triplestore implementations are available, including [free
software](http://www.gnu.org/philosophy/free-sw.en.html) as well as
proprietary options. Knora is designed to work with any
standards-compliant triplestore. It is primarily tested with [Ontotext
GraphDB](http://ontotext.com/products/graphdb/), a high-performance,
proprietary triplestore. We recommend GraphDB Standard Edition, but
GraphDB Free (which is proprietary but available free of charge) also
works.

Knora includes support for [Apache Jena](https://jena.apache.org/),
which is [free software](http://www.gnu.org/philosophy/free-sw.en.html),
but use of Jena is deprecated, and support for it will probably be
removed in the future.

Built-in support and configuration for other triplestores is planned.

See the chapters on @ref:[Starting GraphDB](graphdb.md) and
@ref:[Starting Fuseki](fuseki.md) for more details.

## Sipi

### Build Sipi Docker Image

The Sipi docker image needs to be build by hand, as it requires the
Kakadu distribution.

To build the image, and push it to the docker hub, follow the following
steps:

```
$ git clone https://github.com/dhlab-basel/docker-sipi
(copy the Kakadu distribution ``v7_8-01382N.zip`` to the ``docker-sipi`` directory)
$ docker build -t dhlabbasel/sipi
$ docker run --name sipi --rm -it -p 1024:1024 dhlabbasel/sipi
(Ctrl-c out of terminal will stop and delete container)
$ docker push dhlabbasel/sipi
```

Pushing the image to the docker hub requires prior authentication with
`$ docker login`. The user needs to be registered on `hub.docker.com`.
Also, the user needs to be allowed to push to the `dblabbasel`
organisation.

### Running Sipi

To use the docker image stored locally or on the docker hub repository
type:

```
$ docker run --name sipi -d -p 1024:1024 dhlabbasel/sipi
```

This will create and start a docker container with the `dhlabbasel/sipi`
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
             dhlabbasel/sipi \
             /sipi/local/bin/sipi -config /sipi/config/sipi.config.lua
```

You can also mount a directory (the local directory in this example),
and use a config file that is outside of the docker container:

```
$ docker run --name sipi \
             -d \
             -p 1024:1024 \
             -v $PWD:/localdir \
             dhlabbasel/sipi \
             /sipi/local/bin/sipi -config /localdir/sipi.knora-test-config.lua
```
