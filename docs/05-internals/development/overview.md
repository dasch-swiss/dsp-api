# Overview

Developing for DSP-API requires a complete local
installation of Knora. The different parts are:

1. The cloned [DSP-API](https://github.com/dasch-swiss/dsp-api) Github
   repository
1. Fuseki - supplied triplestore in the DSP-API Github repository.
1. Sipi by building from
    [source](https://github.com/dasch-swiss/sipi) or using the docker
    [image](https://hub.docker.com/r/daschswiss/sipi/)

## Knora Github Repository

```bash
git clone https://github.com/dasch-swiss/dsp-api
```

## Triplestore

A number of triplestore implementations are available, including [free
software](https://www.gnu.org/philosophy/free-sw.en.html) as well as
proprietary options. DSP-API is designed to work with any
standards-compliant triplestore. It is primarily tested with [Apache Jena Fuseki](https://jena.apache.org/).

## Sipi

### Build Sipi Docker Image

The Sipi docker image needs to be build by hand, as it requires the
Kakadu distribution.

To build the image, and push it to the docker hub, follow the following
steps:

```bash
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

```bash
docker run --name sipi -d -p 1024:1024 daschswiss/sipi
```

This will create and start a docker container with the `daschswiss/sipi`
image in the background. The default behaviour is to start Sipi by
calling the following command:

```bash
/sipi/local/bin/sipi -config /sipi/config/sipi.test-config.lua
```

To override this default behaviour, start the container by supplying
another config file:

```bash
docker run --name sipi \
             -d \
             -p 1024:1024 \
             daschswiss/sipi \
             /sipi/local/bin/sipi -config /sipi/config/sipi.config.lua
```

You can also mount a directory (the local directory in this example),
and use a config file that is outside of the docker container:

```bash
docker run --name sipi \
             -d \
             -p 1024:1024 \
             -v $PWD:/localdir \
             daschswiss/sipi \
             /sipi/local/bin/sipi -config /localdir/sipi.test-config.lua
```
