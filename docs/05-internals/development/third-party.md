# Third-Party Dependencies

The following section discusses on how third-party dependencies should be defined.

Third-party dependencies are defined in the `third_party` folder. There are
two main types of dependencies: (1) Maven library dependencies and (2) Docker
image versions.

TODO add documentation for Dependencies.scala

## Docker Image Versions

The required Docker image versions of `Sipi` and `Fuseki` are defined in the
`third_party/versions.bzl` file. For the Docker images, the supplied digest
hashes are relevant for getting the image. These digest hashes can be found
on Docker-Hub when inspecting the tag of the Docker image in question.
