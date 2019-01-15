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

# Build Process

@@toc

## SBT Build Configuration

Knora's complete build definition is defined in `KnoraBuild.sbt`:

@@snip[KnoraBuild.sbt](../../../../../KnoraBuild.sbt) { }


## Building Deployment Packages

Deployment packages for `salsah1` and `webapi` can be built by using the following SBT tasks:

- `$ sbt stage` - Stages the app so that it can be run locally without having the app packaged.
- `$ sbt universal:packageBin` - Generates a universal zip file
- `$ sbt universal:packageZipTarball` - Generates a universal tgz file

The tasks can be scoped by prefixing `salsah1/` or `webapi/`, e.g., to only run `sbt stage`
for the `webapi` project, run:
```bash
$ sbt webapi/stage
```


## Building Docker Images

Docker images for `salsah1` and `webapi` can be built by using the following SBT tasks:

- `$ sbt docker:stage` - Generates a directory with the Dockerfile and environment prepared for creating a Docker image.
- `$ sbt docker:publishLocal` - Builds an image using the local Docker server.
- `$ sbt docker:publish` - Builds an image using the local Docker server, and pushes it to the configured remote repository.
- `$ sbt docker:clean` - Removes the built image from the local Docker server.

The tasks can be scoped by prefixing `salsah1/` or `webapi/`, e.g., to only run `sbt docker:stage`
for the `webapi` project, run:
```bash
$ sbt webapi/docker:stage
```` 

## Running the Knora Stack (graphdb, webapi, salsah1, sipi)

The complete knora stack (graphdb, webapi, salsah1, sipi) can be started by invoking the following SBT task:

```bash
$ sbt dockerComposeUp
```

This will build, publish locally and use the docker images for `webapi` and `salsah1`. The versions defined in
`project/Dependencies` will be used for `GraphDB` and `Sipi`.

The following SBT tasks are available:

- `$ sbt dockerComposeUp` - starts the whole stack and prints out a summary
- `$ sbt dockerComposeRestart` - stops and the starts again the whole stack
- `$ sbt dockerComposeStop` - stops the whole stack 


## Building Documentation

The complete Knora documentation site is built by invoking the following tasks:

- `$ sbt docs/makeSite` - generates the documentation which can be found under `docs/target/site/`
- `$ sbt docs/previewSite` - previews the generated site by launching a static web server
- `$ sbt docs/previewAuto` - previews the generated site by launching a dynamic server updating its content at each modification in your source files.

Both preview tasks launch the server on port `4000` and attempt to connect your browser to `http://localhost:4000/`.


## Publishing Documentation

To publish the documentation, you need to be on the `develop` branch and then execute the following task:

```bash
$ sbt docs/ghpagesPushSite
```

This task will build all documentation and publish it to the `gh-pages` branch.