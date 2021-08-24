# Bazel

The following section discusses on how to build and run tests for Knora-API with [Bazel](https://bazel.build).

## Prerequisites

To install the Bazel build tool, follow these steps:

```
$ npm install -g @bazel/bazelisk
```

This will install [bazelisk](https://github.com/bazelbuild/bazelisk) which is a wrapper to the `bazel` binary. It will,
when the `bazel` command ir run, automatically install the supported Bazel version, defined in the `.bazelversion`
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
- BUILD - there are a number of BUILD files throughout the directory structure where each represents a separate package
  responsible for everything underneath.
- *.bzl - custom extensions loaded and used in BUILD files

For a more detailed discussion, please see
the [Concepts and Terminology](https://docs.bazel.build/versions/master/build-ref.html)
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
       supports only IntelliJ upto version `2019.03.05`. After you make sure to run this version of IntelliJ, install
       the plugin from inside IntelliJ.
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

1. Start Scala REPL
    ```bash
    $ bazel run //webapi:main_library_repl
    ```

## Build stamping

By default, Bazel tries not to include anything about the system state in build outputs. However, released binaries and
libraries often want to include something like the version they were built at or the branch or tag they came from.

To reconcile this, Bazel has an option called the *workspace status command*. This command is run outside of any
sandboxes on the local machine, so it can access anything about your source control, OS, or anything else you might want
to include. It then dumps its output into `bazel-out/volatile-status.txt`, which you can use (and certain language
rulesets provide support for accessing from code).

Our *workspace status command* is defined in `//tools/buildstamp/get_workspace_status`. To use it on every bazel
command, we need to supply it to each Bazel invocation, which is done by the following line found in `.bazelrc`:

```
build --workspace_status_command=tools/buildstamp/get_workspace_status --stamp=yes
```

Any line added to `.bazelrc` is invoked on each corresponding command.

The `//tools/buildstamp/get_workspace_status` emits additional values to `bazel-out/volatile-status.txt`
whereas `BUILD_TIMESTAMP` is emitted by Bazel itself:

```
BUILD_SCM_REVISION 2d6df6c8fe2d56e3712eb26763f9727916a60164
BUILD_SCM_STATUS Modified
BUILD_SCM_TAG v13.0.0-rc.21-17-g2d6df6c-dirty
BUILD_TIMESTAMP 1604401028
```

The value of `BUILD_SCM_TAG` is used in `//webapi/src/main/scala/org/knora/webapi/http/version/versioninfo`, which emits
a JAR containing `VersionInfo.scala`. This file is generated based on
`VersionInfoTemplate.scala` found in the same Bazel package.

In short, the `versioninfo` target producing the JAR library depends on the `version_info_with_build_tag` target which
emits the `VersionInfo.scala`
file which has the `{BUILD_TAG}` variable replaced by the current value of
`BUILD_SCM_TAG`. In an intermediary step, the `version_info_without_build_tag`
target, replaces variables coming from `//third_party:versions.bzl`.
