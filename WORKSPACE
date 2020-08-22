workspace(name = "io_dasch_dsp_api")

# load http_archive method
load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive", "http_file")

# bazel-skylib 1.0.2 released 2019.10.09 (https://github.com/bazelbuild/bazel-skylib/releases/tag/1.0.2)
skylib_version = "1.0.2"

http_archive(
    name = "bazel_skylib",
    sha256 = "97e70364e9249702246c0e9444bccdc4b847bed1eb03c5a3ece4f83dfe6abc44",
    type = "tar.gz",
    url = "https://github.com/bazelbuild/bazel-skylib/releases/download/{}/bazel-skylib-{}.tar.gz".format(skylib_version, skylib_version),
)

# download rules_scala repository
rules_scala_version = "056d5921d2c595e7ce2d54a627e8bc68ece7e28d"  # 16.06.2020

rules_scala_version_sha256 = "a39010b90ce921fd627c7158d43c16d8bd540e85d339ce9ac975e37213a843d4"

http_archive(
    name = "io_bazel_rules_scala",
    sha256 = rules_scala_version_sha256,
    strip_prefix = "rules_scala-%s" % rules_scala_version,
    type = "zip",
    url = "https://github.com/bazelbuild/rules_scala/archive/%s.zip" % rules_scala_version,
)

# register default and our custom scala toolchain
load("@io_bazel_rules_scala//scala:toolchains.bzl", "scala_register_toolchains")

scala_register_toolchains()

register_toolchains("//toolchains:knora_api_scala_toolchain")

# set the default scala version
load("@io_bazel_rules_scala//scala:scala.bzl", "scala_repositories")

scala_repositories((
    "2.12.11",
    {
        "scala_compiler": "e901937dbeeae1715b231a7cfcd547a10d5bbf0dfb9d52d2886eae18b4d62ab6",
        "scala_library": "dbfe77a3fc7a16c0c7cb6cb2b91fecec5438f2803112a744cb1b187926a138be",
        "scala_reflect": "5f9e156aeba45ef2c4d24b303405db259082739015190b3b334811843bd90d6a",
    },
))

#
# Download the protobuf repository (needed by go and rules_scala_annex)
#
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

#
# download rules_webtesting (for browser tests of salsah1)
#
rules_webtesting_release = "0.3.3"

rules_webtesting_release_sha256 = "9bb461d5ef08e850025480bab185fd269242d4e533bca75bfb748001ceb343c3"

http_archive(
    name = "io_bazel_rules_webtesting",
    sha256 = rules_webtesting_release_sha256,
    urls = [
        "https://github.com/bazelbuild/rules_webtesting/releases/download/%s/rules_webtesting.tar.gz" % rules_webtesting_release,
    ],
)

load("@io_bazel_rules_webtesting//web:repositories.bzl", "web_test_repositories")

web_test_repositories()

load("@io_bazel_rules_webtesting//web/versioned:browsers-0.3.2.bzl", "browser_repositories")

browser_repositories(
    chromium = True,
    firefox = True,
)

load("@io_bazel_rules_webtesting//web:java_repositories.bzl", "java_repositories")

java_repositories()

#
# download rules_jvm_external used for maven dependency resolution
# defined in the third_party sub-folder
#
rules_jvm_external_version = "3.2"  # 24.02.2020

rules_jvm_external_version_sha256 = "82262ff4223c5fda6fb7ff8bd63db8131b51b413d26eb49e3131037e79e324af"

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

#
# Load rules_scala_annex, required by rules_twirl
#
rules_scala_annex_version = "ff423d8bdd0e5383f8f2c048ffd7704bb51a91bf"  # 17.07.2020

rules_scala_annex_sha256 = "ae53e9ed5fecadc7baf4637b88109471602be73dda4e5ff6b4bf1767932703c0"

http_archive(
    name = "rules_scala_annex",
    sha256 = rules_scala_annex_sha256,
    strip_prefix = "rules_scala-{}".format(rules_scala_annex_version),
    url = "https://github.com/higherkindness/rules_scala/archive/{}.zip".format(rules_scala_annex_version),
)

load("@rules_scala_annex//rules/scala:workspace.bzl", "scala_register_toolchains", "scala_repositories")

scala_repositories()

load("@annex//:defs.bzl", annex_pinned_maven_install = "pinned_maven_install")

annex_pinned_maven_install()

scala_register_toolchains()

load("@rules_scala_annex//rules/scalafmt:workspace.bzl", "scalafmt_default_config", "scalafmt_repositories")

scalafmt_repositories()

load("@annex_scalafmt//:defs.bzl", annex_scalafmt_pinned_maven_install = "pinned_maven_install")

annex_scalafmt_pinned_maven_install()

scalafmt_default_config()

load("@rules_scala_annex//rules/scala_proto:workspace.bzl", "scala_proto_register_toolchains", "scala_proto_repositories")

scala_proto_repositories()

load("@annex_proto//:defs.bzl", annex_proto_pinned_maven_install = "pinned_maven_install")

annex_proto_pinned_maven_install()

scala_proto_register_toolchains()

# Specify the scala compiler we wish to use; in this case, we use our own definition
bind(
    name = "default_scala",
    actual = "//scala:default_scala",
)

#
# download the rules_twirl repository (needed to compile twirl templates)
#
rules_twirl_version = "35389750d178f17f7ddd85b9335f7b8b8d662f78"  # 29.04.2020

rules_twirl_version_sha256 = "d072049d0917b87e1eb677a4255509a7133ca71fc21c8de4b4536ca030eb3d3a"

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

#
# Download the rules_go repository
#
http_archive(
    name = "io_bazel_rules_go",
    sha256 = "842ec0e6b4fbfdd3de6150b61af92901eeb73681fd4d185746644c338f51d4c0",
    urls = [
        "https://storage.googleapis.com/bazel-mirror/github.com/bazelbuild/rules_go/releases/download/v0.20.1/rules_go-v0.20.1.tar.gz",
        "https://github.com/bazelbuild/rules_go/releases/download/v0.20.1/rules_go-v0.20.1.tar.gz",
    ],
)

load("@io_bazel_rules_go//go:deps.bzl", "go_register_toolchains", "go_rules_dependencies")

go_rules_dependencies()

go_register_toolchains()

#
# Download the rules_docker repository at release v0.14.4
#
rules_docker_version = "0.14.4"

rules_docker_version_sha256 = "4521794f0fba2e20f3bf15846ab5e01d5332e587e9ce81629c7f96c793bb7036"

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

load("@io_bazel_rules_docker//repositories:pip_repositories.bzl", "pip_deps")

pip_deps()

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
    digest = "sha256:0e51b455654bd162c485a6a6b5b120cc82db453d9265cc90f0c4fb5d14e2f62e",
    registry = "docker.io",
    repository = "adoptopenjdk",
    tag = "11-jre-hotspot-bionic",
)

# get sipi
load("//third_party:versions.bzl", "SIPI_REPOSITORY", "SIPI_TAG")

container_pull(
    name = "sipi",
    digest = "sha256:7b7abd324d0887f3ff46de7d7f066dd699b3acd96b94177f153f750f7572031c",
    registry = "docker.io",
    repository = SIPI_REPOSITORY,
    tag = SIPI_TAG,
)

# get fuseki
load("//third_party:versions.bzl", "FUSEKI_REPOSITORY", "FUSEKI_TAG")

container_pull(
    name = "jenafuseki",
    digest = "sha256:a019024b94aeecf0ad7ce078a1cc5e7692c16ba32e43528020c70e7c8b7ee86f",
    registry = "docker.io",
    repository = FUSEKI_REPOSITORY,
    tag = FUSEKI_TAG,
)

#
# download rules_pkg - basic packaging rules
#
rules_package_version = "0.2.4"

rules_package_version_sha256 = "4ba8f4ab0ff85f2484287ab06c0d871dcb31cc54d439457d28fd4ae14b18450a"

http_archive(
    name = "rules_pkg",
    sha256 = rules_package_version_sha256,
    url = "https://github.com/bazelbuild/rules_pkg/releases/download/%s/rules_pkg-%s.tar.gz" % (rules_package_version, rules_package_version),
)

# load further dependencies of this rule
load("@rules_pkg//:deps.bzl", "rules_pkg_dependencies")

rules_pkg_dependencies()
