# Bazel Notes

This file should be moved to the documentation after the MkDocs PR is merged.

1. Install [Bazel](https://bazel.build/). On macOS use `brew install bazel`.

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
