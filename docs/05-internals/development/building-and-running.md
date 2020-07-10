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


# Building and Running

## Running the stack

With [Docker](https://www.docker.com/) installed, run the following:

```
$ make stack-up
```

Then to create a test repository and load some test data into the triplestore:

```
$ make init-db-test
```

Then we need to restart knora-api after loading the data:

```
$ make stack-restart-api
```

Then try opening [http://localhost:3333/v1/resources/http%3A%2F%2Frdfh.ch%2F0803%2Fc5058f3a](http://localhost:3333/v1/resources/http%3A%2F%2Frdfh.ch%2F0803%2Fc5058f3a) in a web browser. You should see a response in JSON describing a book.

To shut down the Knora-Stack:

```
$ make stack-down
```

## Running the automated tests

Run the automated tests from sbt:

```
> webapi / test
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
`.github/workflows/main.yml`, and looks like this:

@@snip[main.yml](../../../../../.github/workflows/main.yml) { }

## Webapi Server Startup-Flags

The Webapi-Server can be started with a number of flags. These flags can
be supplied either to the `reStart` or the `run` command in sbt, e.g.,:

```
$ sbt
> webapi / reStart flag
```

or

```
$ sbt
> webapi / run flag
```

### `loadDemoData` - Flag

When the webapi-server is started with the `loadDemoData` flag, then at
startup, the data which is configured in `application.conf` under the
`app.triplestore.rdf-data` key is loaded into the triplestore, and any
data in the triplestore is removed beforehand.

Usage:

```
$ sbt
> webapi / reStart loadDemoData
```
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

Usage:

```
$ sbt
> webapi / reStart allowReloadOverHTTP
```
