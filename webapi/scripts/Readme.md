# Triplestore initialization scripts

## Fuseki

### Initialize for CI (continuous integration) and unit testing (running tests inside sbt)

```
$ make fuseki-init-knora-test-unit
$ make fuseki-init-knora-test-unit-minimal
```

### Initialize for using in the test / staging environment

```
$ make fuseki-init-knora-test
$ make fuseki-init-knora-test-minimal
```

## GraphDB

### Initialize for CI (continuous integration) and unit testing (running tests inside sbt)

This script will create the ``knora-test-unit`` repository and not load any data. Data will be loaded during testing.

- with GraphDB running inside a docker container:
  ```$ graphdb-se-docker-init-knora-test-unit.sh```

- with GraphDB running locally, e.g., graphdb distribution:
  ```$ graphdb-se-local-init-knora-test-unit.sh ```

### Initialize for using in the test / staging environment

This script will create the ``knora-test`` repository and load test data specified in ``graphdb-knora-test-data.expect``
.

- with GraphDB running inside a docker container:
  ```$ graphdb-se-docker-init-knora-test.sh```

- with GraphDB running locally, e.g., graphdb distribution:
  ```$ graphdb-se-local-init-knora-test.sh ```

### Initialize for using in the production environment

This script will create the ``knora-prod`` repository and load data specified in ``graphdb-knora-prod-data.expect``.

- with GraphDB running inside a docker container:
  ```$ graphdb-se-docker-init-knora-prod.sh```

- with GraphDB running locally, e.g., graphdb distribution:
  ```$ graphdb-se-local-init-knora-prod.sh ```
