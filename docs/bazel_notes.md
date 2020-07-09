# Bazel Notes

This file should be moved to the documentation after the MkDocs PR is merged.

1. Run single spec:
```bash
$ bazel test //webapi/src/test/scala/org/knora/webapi/e2e/v1:SearchV1R2RSpec
```

2. Run single spec and only tests containing `gaga` in the description
```bash
$ bazel test //webapi/src/test/scala/org/knora/webapi/e2e/v1:SearchV1R2RSpec --test_arg=-z --test_arg="gaga"
```

3. Optimized development workflow

    The first time:
    
    1. `make docker-build` # run at least once so that you have all Docker images
    2. `make init-db-test` # stops webapi, deletes and runs only the DB container, creates the repository and loads the data
    3. `make stack-up-fast` # starts the whole stack and only rebuilds the `knora-api` Docker container if necessary
    
    After you made some changes to webapi:
    
    1. `make init-db-test` # stops, webapi, deletes and runs only the DB container, creates the repository and loads the data
    2. `make stack-up-fast` # starts the whole stack and only rebuilds the `knora-api` Docker container if necessary

4. Start Scala REPL
```bash
$ bazel run //webapi:main_library_repl
```

```bash
$ 
```

```bash
$ 
```

```bash
$ 
```
