# Bazel

The following section discusses on how to build and run tests for Knora-API
with [Bazel](https://bazel.build).

## Prerequisites
To install the Bazel build tool, follow these steps:

```
$ npm install -g @bazel/bazelisk
```

This will install [bazelisk](https://github.com/bazelbuild/bazelisk) which is
a wrapper to the `bazel` binary. It will, when the `bazel` command ir run,
automatically install the supported Bazel version, defined in the `.bazelversion`
file in the root of the `knora-api` repository.

## Commands

Build `webapi`:

```
# build webapi
$ bazel build //webapi/...

# run all webapi tests
$ bazel test //webapi//...
```

## Build Structure

The Bazel build is defined in a number of files:
  - WORKSPACE - here are external dependencies defined
  - BUILD - there are a number of BUILD files throughout the directory structure
    where each represents a separate package responsible for everything underneath.
  - *.bzl - custom extensions loaded and used in BUILD files

For a more detailed discussion, please see the [Concepts and Terminology](https://docs.bazel.build/versions/master/build-ref.html)
section in the Bazel documentation.

## Some Notes

1. Override some `.bazelrc` settings in your own copy created at `~/.bazelrc`:
    ```
    build --action_env=PATH="/usr/local/bin:/opt/local/bin:/usr/bin:/bin"
    build --strategy=Scalac=worker
    build --worker_sandboxing
    query --package_path %workspace%:/usr/local/bin/bazel/base_workspace
    startup --host_jvm_args=-Djavax.net.ssl.trustStore=/Library/Java/JavaVirtualMachines/adoptopenjdk-11.jdk/Contents/Home/lib/security/cacerts \
            --host_jvm_args=-Djavax.net.ssl.trustStorePassword=changeit
    ```

1. Add Bazel Plugin and Project to IntelliJ
    1. The latest version of the [Bazel plugin](https://plugins.jetbrains.com/plugin/8609-bazel/versions)
       supports only IntelliJ upto version `2019.03.05`. After you make sure to
       run this version of IntelliJ, install the plugin from inside IntelliJ.
    1. Click on `File -> Import Bazel Project` and select twice `next`.
    1. Uncomment the `Scala` language and click `Finish`.


1. Run single spec:
    ```bash
    $ bazel test //webapi/src/test/scala/org/knora/webapi/e2e/v1:SearchV1R2RSpec
    ```

1. Run single spec and only tests containing `gaga` in the description
    ```bash
    $ bazel test //webapi/src/test/scala/org/knora/webapi/e2e/v1:SearchV1R2RSpec --test_arg=-z --test_arg="gaga"
    ```

1. Optimized development workflow

    The first time:
    
    1. `make docker-build` # run at least once so that you have all Docker images
    2. `make init-db-test` # stops webapi, deletes and runs only the DB container, creates the repository and loads the data
    3. `make stack-up-fast` # starts the whole stack and only rebuilds the `knora-api` Docker container if necessary
    
    After you made some changes to webapi:
    
    1. `make init-db-test` # stops, webapi, deletes and runs only the DB container, creates the repository and loads the data
    2. `make stack-up-fast` # starts the whole stack and only rebuilds the `knora-api` Docker container if necessary

1. Start Scala REPL
    ```bash
    $ bazel run //webapi:main_library_repl
    ```

1. xxx 

    ```bash
    $ 
    ```

    ```bash
    $ 
    ```
    
    ```bash
    $ 
    ```
