workspace(name = "io_dasch_dsp_api")

# load http_archive method
load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")

#####################################
# Skylib                            #
#####################################
# 1.0.2 released 2019.10.09 (https://github.com/bazelbuild/bazel-skylib/releases/tag/1.0.2)
skylib_version = "1.0.2"

http_archive(
    name = "bazel_skylib",
    sha256 = "97e70364e9249702246c0e9444bccdc4b847bed1eb03c5a3ece4f83dfe6abc44",
    type = "tar.gz",
    url = "https://github.com/bazelbuild/bazel-skylib/releases/download/{}/bazel-skylib-{}.tar.gz".format(skylib_version, skylib_version),
)

#####################################
# Docker                            #
#####################################

rules_docker_version = "0.20.0"  # 12.10.2021

rules_docker_version_sha256 = "92779d3445e7bdc79b961030b996cb0c91820ade7ffa7edca69273f404b085d5"

http_archive(
    name = "io_bazel_rules_docker",
    sha256 = rules_docker_version_sha256,
    strip_prefix = "rules_docker-%s" % rules_docker_version,
    url = "https://github.com/bazelbuild/rules_docker/releases/download/v%s/rules_docker-v%s.tar.gz" % (rules_docker_version, rules_docker_version),
)

load(
    "@io_bazel_rules_docker//repositories:repositories.bzl",
    container_repositories = "repositories",
)

container_repositories()

load("@io_bazel_rules_docker//repositories:deps.bzl", container_deps = "deps")

container_deps()

# load container_pull method
load(
    "@io_bazel_rules_docker//container:container.bzl",
    "container_pull",
)

# get distroless java
container_pull(
    name = "java_base",
    # 'tag' is also supported, but digest is encouraged for reproducibility.
    digest = "sha256:deadbeef",
    registry = "gcr.io",
    repository = "distroless/java",
)

# get openjdk
container_pull(
    name = "openjdk11",
    digest = "sha256:14605eb0f24ce726e13c4a85862325083dee3ab13da847b7af20d0df5966c176",  # 29.11.2021
    registry = "docker.io",
    repository = "eclipse-temurin",
    # tag = "11-jre-focal", # Ubuntu 20.04
)

# get sipi
load("//third_party:versions.bzl", "FUSEKI_IMAGE_DIGEST", "FUSEKI_REPOSITORY", "SIPI_IMAGE_DIGEST", "SIPI_REPOSITORY")

container_pull(
    name = "sipi",
    digest = SIPI_IMAGE_DIGEST,
    registry = "docker.io",
    repository = SIPI_REPOSITORY,
)

container_pull(
    name = "jenafuseki",
    digest = FUSEKI_IMAGE_DIGEST,
    registry = "docker.io",
    repository = FUSEKI_REPOSITORY,
)

#####################################
# Scala                             #
#####################################

rules_scala_version = "0ac75d3a044b8e316d1b11b90a7d044685bd72e8"  # 22.04.2021

rules_scala_version_sha256 = "7624c95c19b60df943dbde90c54d09ecad9aca9432b1211da8352f131776ac36"

http_archive(
    name = "io_bazel_rules_scala",
    sha256 = rules_scala_version_sha256,
    strip_prefix = "rules_scala-%s" % rules_scala_version,
    type = "zip",
    url = "https://github.com/bazelbuild/rules_scala/archive/%s.zip" % rules_scala_version,
)

# Stores Scala version and other configuration
# 2.12 is a default version, other versions can be use by passing them explicitly:
# scala_config(scala_version = "2.11.12")
load("@io_bazel_rules_scala//:scala_config.bzl", "scala_config")

scala_config(scala_version = "2.13.7")

# register default and our custom scala toolchain
load("@io_bazel_rules_scala//scala:toolchains.bzl", "scala_register_toolchains")

scala_register_toolchains()

register_toolchains("//toolchains:dsp_api_scala_toolchain")

# needed by rules_scala
load("@io_bazel_rules_scala//scala:scala.bzl", "scala_repositories")

scala_repositories()

# register the test toolchain for rules_scala
load("@io_bazel_rules_scala//testing:scalatest.bzl", "scalatest_repositories", "scalatest_toolchain")

scalatest_repositories()

scalatest_toolchain()

#####################################
# Protobuf (Scala Annex)            #
#####################################

protobuf_tag = "3.12.3"

protobuf_sha256 = "e5265d552e12c1f39c72842fa91d84941726026fa056d914ea6a25cd58d7bbf8"

http_archive(
    name = "com_google_protobuf",
    sha256 = protobuf_sha256,
    strip_prefix = "protobuf-{}".format(protobuf_tag),
    type = "zip",
    url = "https://github.com/protocolbuffers/protobuf/archive/v{}.zip".format(protobuf_tag),
)

load("@com_google_protobuf//:protobuf_deps.bzl", "protobuf_deps")

protobuf_deps()

#####################################
# JAR Dependencies                  #
#####################################
#
# defined in the third_party sub-folder
#
rules_jvm_external_version = "4.0"  # 6.01.2021

rules_jvm_external_version_sha256 = "31701ad93dbfe544d597dbe62c9a1fdd76d81d8a9150c2bf1ecf928ecdf97169"

http_archive(
    name = "rules_jvm_external",
    sha256 = rules_jvm_external_version_sha256,
    strip_prefix = "rules_jvm_external-%s" % rules_jvm_external_version,
    url = "https://github.com/bazelbuild/rules_jvm_external/archive/%s.zip" % rules_jvm_external_version,
)

# load the dependencies defined in the third_party sub-folder
load("//third_party:dependencies.bzl", "dependencies")

dependencies()

# pin dependencies to the ones stored in maven_install.json in the third_party sub-folder
# to update: bazel run @maven//:pin
load("@maven//:defs.bzl", "pinned_maven_install")

pinned_maven_install()

#####################################
# Twirl templates                   #
#####################################
rules_twirl_version = "9ac789845e3a481fe520af57bd47a4261edb684f"  # 29.04.2020

rules_twirl_version_sha256 = "b1698a2a59b76dc9df233314c2a1ca8cee4a0477665cff5eafd36f92057b2044"

http_archive(
    name = "io_bazel_rules_twirl",
    sha256 = rules_twirl_version_sha256,
    strip_prefix = "rules_twirl-%s" % rules_twirl_version,
    type = "zip",
    url = "https://github.com/lucidsoftware/rules_twirl/archive/%s.zip" % rules_twirl_version,
)

load("@io_bazel_rules_twirl//:workspace.bzl", "twirl_repositories")

twirl_repositories()

load("@twirl//:defs.bzl", twirl_pinned_maven_install = "pinned_maven_install")

twirl_pinned_maven_install()

#####################################
# Buildifier                        #
#####################################
# buildifier is written in Go and hence needs rules_go to be built.
# See https://github.com/bazelbuild/rules_go for the up to date setup instructions.
http_archive(
    name = "io_bazel_rules_go",
    sha256 = "69de5c704a05ff37862f7e0f5534d4f479418afc21806c887db544a316f3cb6b",
    urls = [
        "https://mirror.bazel.build/github.com/bazelbuild/rules_go/releases/download/v0.27.0/rules_go-v0.27.0.tar.gz",
        "https://github.com/bazelbuild/rules_go/releases/download/v0.27.0/rules_go-v0.27.0.tar.gz",
    ],
)

load("@io_bazel_rules_go//go:deps.bzl", "go_register_toolchains", "go_rules_dependencies")

go_rules_dependencies()

go_register_toolchains()

http_archive(
    name = "bazel_gazelle",
    sha256 = "b85f48fa105c4403326e9525ad2b2cc437babaa6e15a3fc0b1dbab0ab064bc7c",
    urls = [
        "https://mirror.bazel.build/github.com/bazelbuild/bazel-gazelle/releases/download/v0.22.2/bazel-gazelle-v0.22.2.tar.gz",
        "https://github.com/bazelbuild/bazel-gazelle/releases/download/v0.22.2/bazel-gazelle-v0.22.2.tar.gz",
    ],
)

# bazel buildtools providing buildifier
http_archive(
    name = "com_github_bazelbuild_buildtools",
    strip_prefix = "buildtools-master",
    url = "https://github.com/bazelbuild/buildtools/archive/master.zip",
)

#####################################
# rules_pkg - basic packaging rules #
#####################################
rules_package_version = "0.2.4"

rules_package_version_sha256 = "4ba8f4ab0ff85f2484287ab06c0d871dcb31cc54d439457d28fd4ae14b18450a"

http_archive(
    name = "rules_pkg",
    sha256 = rules_package_version_sha256,
    url = "https://github.com/bazelbuild/rules_pkg/releases/download/%s/rules_pkg-%s.tar.gz" % (rules_package_version, rules_package_version),
)

load("@rules_pkg//:deps.bzl", "rules_pkg_dependencies")

rules_pkg_dependencies()

#####################################
# rules_stamp - stamping helper     #
#####################################
http_archive(
    name = "ecosia_rules_stamp",
    sha256 = "36d7ea381bfb2520f9353299b162434b25c77365d3c9e9459195c536da5e837d",
    strip_prefix = "rules_stamp-48d5ef2bc0d93bd65fddddbe02f3ae410e25169d",
    url = "https://github.com/ecosia/rules_stamp/archive/48d5ef2bc0d93bd65fddddbe02f3ae410e25169d.tar.gz",
)
