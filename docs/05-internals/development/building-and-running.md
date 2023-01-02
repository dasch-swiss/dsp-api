<!---
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
-->


# Building and Running

## Running the stack

With [Docker](https://www.docker.com/) installed,

1. Run the following:

    ```
    $ make init-db-test
    ```

    to create the knora-test repository and initialize it with loading some test data into the triplestore (Fuseki). 

1. Start the entire knora-stack (fuseki (db), sipi, api, salsah1) with the following command:

    ```
    $ make stack-up
    ```

Then try opening [http://localhost:3333/v1/resources/http%3A%2F%2Frdfh.ch%2F0803%2Fc5058f3a](http://localhost:3333/v1/resources/http%3A%2F%2Frdfh.ch%2F0803%2Fc5058f3a) in a web browser. You should see a response in JSON describing a book.

**_Note_**: To delete the existing containers and for a clean start, before creating the knora-test repository explained 
in the first step above, run the following:

```
$ make stack-down-delete-volumes
```

This stops the knora-stack and deletes any created volumes (deletes the database!). 

To only shut down the Knora-Stack without deleting the containers:

```
$ make stack-down
```

To restart the knora-api use the following command:

```
$ make stack-restart-api
```

If a change is made to knora-api code, only its image needs to be rebuilt. In that case, use 

```
$ make stack-up-fast
```

which starts the knora-stack by skipping rebuilding most of the images (only api image is rebuilt).

To work on Metadata, use

```
$ make stack-up-with-metadata
```

which will put three example metadata sets to the projects `anything`, `images` and `dokubib`.
This data can then be consumed from `localhost:3333/v2/metadata/http%3A%2F%2Frdfh.ch%2Fprojects%2F0001`, `localhost:3333/v2/metadata/http%3A%2F%2Frdfh.ch%2Fprojects%2F00FF` and `localhost:3333/v2/metadata/http%3A%2F%2Frdfh.ch%2Fprojects%2F0804`.

## Managing Containers in Docker Dashboard

The Docker Desktop is installed on your computer during the installation of docker, it enables easy management of docker 
containers and access to Docker Hub. To manage your docker containers, docker desktop provides a dashbord.

![screenshot 'Docker Desktop'](figures/dockerDesktop.png)
 
In docker dashboard, you can see all the running containers, stop, start, restart, or completely delete them. For example, when 
you start the knora-stack as explained above, in the docker dashboard you will see following:

![screenshot 'Docker Dashboard'](figures/dockerDashboard.png)
 
### Access the logs
To read information logged out of any container (db, api, etc.), click on the container in the dashboard and choose 
`logs`. The example, below shows the logs of the database (db) container that includes the last SPARQL query sent to the
 triplestore. 

![screenshot 'Docker Logs'](figures/DockerLog.png)
 
Note that, you can also print out the log information directly from the command line. For example, the same logs of the 
database container can be printed out using the following command:

```
$ make stack-logs-db
```
Similarly, the logs of the other containers can be printed out by running make with `stack-logs-api` or `stack-logs-sipi`.
These commands print out and follow the logs, to only print the logs out without following, use 
`-no-follow` version of the commands for example:

 ```
 $ make stack-logs-db-no-follow
 ```

Lastly, to print out the entire logs of the running knora-stack, use 

```
$ make stack-logs
```

With the Docker plugin installed, you can attach a terminal to the docker container within VS Code. This will stream the docker logs to the terminal window of the editor.

![screenshot 'VSCode Docker'](figures/vscode-docker.png)

The docker plugin also allows for a number of other useful features, like inspecting the container's file system or attaching a shell to the container.

## Running the automated tests

To run all test targets, use the following in the command line:

```
$ make test-all
```

To run a single test from the command line, for example `SearchV1R2RSpec`, 
run the following:

 ```bash
    $ sbt " webapi / testOnly *SearchV1R2RSpec* "
 ```

_**Note:** to run tests, the api container must be stopped first!_

## Build and Publish Documentation
First, you need to install the requirements through:

```
$ make docs-install-requirements
```

Then, to build docs into the local `site` folder, run the following command:

```
$ make docs-build
```
At this point, you can serve the docs to view them locally using 

```
$ make docs-serve
```

Lastly, to build and publish docs to Github Pages, use 

```
$ make docs-publish
```

## Build and Publish Docker Images

To build and publish all Docker images locally

```
$ make docker-build 
```
To publish all Docker images to Dockerhub

```
$ make docker-publish
```

## Load Testing on Mac OS X

To test Knora with many concurrent connections on Mac OS
X, you will need to adjust some kernel parameters to allow more open
connections, to recycle ephemeral ports more quickly, and to use a wider
range of ephemeral port numbers. The script
`webapi/scripts/macOS-kernel-test-config.sh` will do this.

## Continuous Integration

For continuous integration testing, we use Github CI Actions. Every commit
pushed to the git repository or every pull request, triggers the build.
Additionally, in Github there is a small checkmark beside every commit,
signaling the status of the build (successful, unsuccessful, ongoing).

The build that is executed on Github CI Actions is defined in
`.github/workflows/main.yml`.

## Webapi Server Startup-Flags

The Webapi-Server can be started with a number of flags.

### `loadDemoData` - Flag

When the webapi-server is started with the `loadDemoData` flag, then at
startup, the data which is configured in `application.conf` under the
`app.triplestore.rdf-data` key is loaded into the triplestore, and any
data in the triplestore is removed beforehand.

### `allowReloadOverHTTP` - Flag

When the webapi.server is started with the `allowReloadOverHTTP` flag (`reStart -r`),
then the `v1/store/ResetTriplestoreContent` route is activated. This
route accepts a `POST` request, with a JSON payload consisting of the
following example content:

```
[
  {
    "path": "knora-ontologies/knora-base.ttl",
    "name": "http://www.knora.org/ontology/knora-base"
  },
  {
    "path": "knora-ontologies/salsah-gui.ttl",
    "name": "http://www.knora.org/ontology/salsah-gui"
  },
  {
    "path": "test_data/ontologies/incunabula-onto.ttl",
    "name": "http://www.knora.org/ontology/0803/incunabula"
  },
  {
    "path": "test_data/all_data/incunabula-data.ttl",
    "name": "http://www.knora.org/data/incunabula"
  }
]
```

This content corresponds to the payload sent with the
`ResetTriplestoreContent` message, defined inside the
`org.knora.webapi.messages.v1.store.triplestoremessages` package. The
`path` being the relative path to the `ttl` file which will be loaded
into a named graph by the name of `name`.
