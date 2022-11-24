<!---
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
-->

# Getting Started with DSP-API

Running DSP-API locally or on a server requires [Docker](https://www.docker.com), which
can be freely downloaded. Please follow the instructions for installing
[Docker Desktop](https://www.docker.com/products/docker-desktop).

Please visit the GitHub repository of [DSP-API](https://github.com/dasch-swiss/dsp-api) to 
get the latest information about how to install and run DSP-API.

## Choosing a Triplestore

DSP-API requires a standards-compliant
[RDF](https://www.w3.org/TR/rdf11-primer/) triplestore. A number of
triplestore implementations are available, including [free
software](http://www.gnu.org/philosophy/free-sw.en.html) as well as
proprietary options.

DSP-API is designed to work with any standards-compliant
triplestore. It is primarily tested with
[Apache Jena Fuseki](https://jena.apache.org), an open source triplestore.

## Running the DSP-Stack

Use `git` to clone the DSP-API repository from [Github](https://github.com/dasch-swiss/dsp-api).

Then from inside the cloned `DSP-API` repository folder, run:

```bash
$ make stack-up
```

## Creating Repositories and Loading Test Data

To create a test repository called `knora-test` and load test data, run:

```
$ make init-db-test
```

The scripts called by `make` can be found under `webapi/scripts`. You can
create your own scripts based on these scripts, to create new
repositories and optionally to load existing DSP-compliant RDF data
into them.

If you need to reload the test data, you need to stop and **delete** the
running Apache Fuseki instance. **Make sure you don't delete important data.**
To stop the instance and delete the repository, run the following command:

```
$ make stack-down-delete-volumes
```

after which you can start the stack again with `make stack-up`, recreate
the repository and load the data with `make init-db-test`.
