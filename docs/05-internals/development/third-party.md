# Third-Party Dependencies

The following section discusses on how third-party dependencies should be defined.

Third-party dependencies are defined in the `third_party` folder. There are
two main types of dependencies: (1) Maven library dependencies and (2) Docker
image versions.

## Maven Library Dependencies

The Maven library dependencies are defined in the `third_party/dependencies.bzl`
file. To use the external libraries, add the "flattened" Bazel version of it to
the Bazel rule used to compile the code.

Example of a "flattened" Bazel version looks as follows:

- defined: `com.typesafe.akka:akka-actor_2.12:2.6.5`
- flattened: `@maven//:com_typesafe_akka_akka_actor_2_12`

To query Bazel for all defined Maven dependencies: `bazel query @maven//:all | sort`

### Manually Fetching Dependencies

The Maven dependencies can be manually fetched with:
```bash
$ bazel fetch @maven//...
```

If there are any problems downloading the Maven dependencies, set the
`RJE_VERBOSE` environment variable to true to print `coursier`'s verbose output:
```bash
$ RJE_VERBOSE=true bazel fetch @maven//...
```

Note: If you are on macOS Big Sur and have the Cisco VPN client installed,
make sure that the packet filters are not active, or they may be a problem
downloading the dependencies.

## Docker Image Versions

The required Docker image versions of `Sipi` and `Fuseki` are defined in the
`third_party/versions.bzl` file. For the Docker images, the supplied digest
hashes are relevant for getting the image. These digest hashes can be found
on Docker-Hub when inspecting the tag of the Docker image in question.
