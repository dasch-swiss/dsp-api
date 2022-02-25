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
# Buildifier                        #
#####################################
# buildifier is written in Go and hence needs rules_go to be built.
# See https://github.com/bazelbuild/rules_go for the up to date setup instructions.
http_archive(
    name = "io_bazel_rules_go",
    sha256 = "d6b2513456fe2229811da7eb67a444be7785f5323c6708b38d851d2b51e54d83",
    urls = [
        "https://mirror.bazel.build/github.com/bazelbuild/rules_go/releases/download/v0.30.0/rules_go-v0.30.0.zip",
        "https://github.com/bazelbuild/rules_go/releases/download/v0.30.0/rules_go-v0.30.0.zip",
    ],
)

load("@io_bazel_rules_go//go:deps.bzl", "go_register_toolchains", "go_rules_dependencies")

go_rules_dependencies()

go_register_toolchains(version = "1.17.6")

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

# get openjdk
container_pull(
    name = "openjdk11_amd64",
    digest = "sha256:967349ef166d630bceda0370507b096edd6e7220e62e4539db70f04c04c2295f",  # 7.01.2022
    registry = "docker.io",
    repository = "eclipse-temurin",
    # tag = "11-jre-focal", # Ubuntu 20.04
)

container_pull(
    name = "openjdk11_arm64",
    digest = "sha256:e46fac3005d08732931de9671864683f6adf3a3eb0f8a7e8ac27d1bff1955a5c",  # 7.01.2022
    registry = "docker.io",
    repository = "eclipse-temurin",
    # tag = "11-jre-focal", # Ubuntu 20.04
)

# get sipi
load("//third_party:versions.bzl", "FUSEKI_IMAGE_DIGEST_AMD64", "FUSEKI_IMAGE_DIGEST_ARM64", "FUSEKI_REPOSITORY", "SIPI_IMAGE_DIGEST", "SIPI_REPOSITORY")

container_pull(
    name = "sipi",
    digest = SIPI_IMAGE_DIGEST,
    registry = "docker.io",
    repository = SIPI_REPOSITORY,
)

container_pull(
    name = "jenafuseki_amd64",
    digest = FUSEKI_IMAGE_DIGEST_AMD64,
    registry = "docker.io",
    repository = FUSEKI_REPOSITORY,
)

container_pull(
    name = "jenafuseki_arm64",
    digest = FUSEKI_IMAGE_DIGEST_ARM64,
    registry = "docker.io",
    repository = FUSEKI_REPOSITORY,
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
