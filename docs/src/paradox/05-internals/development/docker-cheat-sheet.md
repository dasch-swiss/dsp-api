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

# Docker Cheat Sheet

@@toc

A complete cheat sheet can be found
[here](https://github.com/wsargent/docker-cheat-sheet)

## Lifecycle

  - [docker
    create](https://docs.docker.com/engine/reference/commandline/create)
    creates a container but does not start it.
  - [docker run](https://docs.docker.com/engine/reference/commandline/run)
    creates and starts a container in one operation.
  - [docker
    rename](https://docs.docker.com/engine/reference/commandline/rename/)
    allows the container to be renamed.
  - [docker rm](https://docs.docker.com/engine/reference/commandline/rm)
    deletes a container.
  - [docker
    update](https://docs.docker.com/engine/reference/commandline/update/)
    updates a container's resource limits.

If you want a transient container, `docker run --rm` will remove the
container after it stops.

If you want to map a directory on the host to a docker container,
`docker run -v $HOSTDIR:$DOCKERDIR`.

## Starting and Stopping

  - [docker
    start](https://docs.docker.com/engine/reference/commandline/start)
    starts a container so it is running.
  - [docker
    stop](https://docs.docker.com/engine/reference/commandline/stop) stops a
    running container.
  - [docker
    restart](https://docs.docker.com/engine/reference/commandline/restart)
    stops and starts a container.
  - [docker
    pause](https://docs.docker.com/engine/reference/commandline/pause/)
    pauses a running container, "freezing" it in place.
  - [docker
    attach](https://docs.docker.com/engine/reference/commandline/attach)
    will connect to a running container.

## Info

  - [docker ps](https://docs.docker.com/engine/reference/commandline/ps)
    shows running containers.
  - [docker
    logs](https://docs.docker.com/engine/reference/commandline/logs) gets
    logs from container. (You can use a custom log driver, but logs is
    only available for json-file and journald in 1.10)
  - [docker
    inspect](https://docs.docker.com/engine/reference/commandline/inspect)
    looks at all the info on a container (including IP address).
  - [docker
    events](https://docs.docker.com/engine/reference/commandline/events)
    gets events from container.
  - [docker
    port](https://docs.docker.com/engine/reference/commandline/port) shows
    public facing port of container.
  - [docker top](https://docs.docker.com/engine/reference/commandline/top)
    shows running processes in container.
  - [docker
    stats](https://docs.docker.com/engine/reference/commandline/stats) shows
    containers' resource usage statistics.
  - [docker
    diff](https://docs.docker.com/engine/reference/commandline/diff) shows
    changed files in the container's FS.

`docker ps -a` shows running and stopped containers.

`docker stats --all` shows a running list of containers.

## Executing Commands

  - [docker
    exec](https://docs.docker.com/engine/reference/commandline/exec) to
    execute a command in container.

To enter a running container, attach a new shell process to a running
container called foo, use: `docker exec -it foo /bin/bash`.

## Images

  - [docker
    images](https://docs.docker.com/engine/reference/commandline/images)
    shows all images.
  - [docker
    build](https://docs.docker.com/engine/reference/commandline/build)
    creates image from Dockerfile.
