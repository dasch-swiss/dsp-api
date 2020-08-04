workspace(name = "io_dasch_dsp_api")

# load http_archive method
load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive", "http_file")

# bazel-skylib 1.0.2 released 2019.10.09 (https://github.com/bazelbuild/bazel-skylib/releases/tag/1.0.2)
skylib_version = "1.0.2"
http_archive(
    name = "bazel_skylib",
    type = "tar.gz",
    url = "https://github.com/bazelbuild/bazel-skylib/releases/download/{}/bazel-skylib-{}.tar.gz".format (skylib_version, skylib_version),
    sha256 = "97e70364e9249702246c0e9444bccdc4b847bed1eb03c5a3ece4f83dfe6abc44",
)

# download rules_scala repository
rules_scala_version="056d5921d2c595e7ce2d54a627e8bc68ece7e28d" # 16.06.2020
rules_scala_version_sha256="a39010b90ce921fd627c7158d43c16d8bd540e85d339ce9ac975e37213a843d4"
http_archive(
    name = "io_bazel_rules_scala",
    strip_prefix = "rules_scala-%s" % rules_scala_version,
    type = "zip",
    url = "https://github.com/bazelbuild/rules_scala/archive/%s.zip" % rules_scala_version,
    sha256 = rules_scala_version_sha256,
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
       "scala_reflect": "5f9e156aeba45ef2c4d24b303405db259082739015190b3b334811843bd90d6a"
    }
))

#
# Download the protobuf repository (needed by go and rules_scala_annex)
#
protobuf_tag = "3.12.3"
protobuf_sha256 = "e5265d552e12c1f39c72842fa91d84941726026fa056d914ea6a25cd58d7bbf8"
http_archive(
    name = "com_google_protobuf",
    strip_prefix = "protobuf-{}".format(protobuf_tag),
    type = "zip",
    url = "https://github.com/protocolbuffers/protobuf/archive/v{}.zip".format(protobuf_tag),
    sha256 = protobuf_sha256,
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

browser_repositories(chromium=True, firefox=True)

load("@io_bazel_rules_webtesting//web:java_repositories.bzl", "java_repositories")

java_repositories()

#
# download rules_jvm_external used for maven dependency resolution
# defined in the third_party sub-folder
#
rules_jvm_external_version = "3.2" # 24.02.2020
rules_jvm_external_version_sha256 = "82262ff4223c5fda6fb7ff8bd63db8131b51b413d26eb49e3131037e79e324af"

http_archive(
    name = "rules_jvm_external",
    strip_prefix = "rules_jvm_external-%s" % rules_jvm_external_version,
    url = "https://github.com/bazelbuild/rules_jvm_external/archive/%s.zip" % rules_jvm_external_version,
    sha256 = rules_jvm_external_version_sha256,
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
rules_scala_annex_version = "2503b72a166610c14170b117c51033b42a32e48b" # 29.06.2020
rules_scala_annex_sha256 = "52d677dc8205db25a49824aade45984e3ef1b79c3bf761efede35d921033c3a4"
http_archive(
    name = "rules_scala_annex",
    strip_prefix = "rules_scala-{}".format(rules_scala_annex_version),
    url = "https://github.com/higherkindness/rules_scala/archive/{}.zip".format(rules_scala_annex_version),
    sha256 = rules_scala_annex_sha256,
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

load("@rules_scala_annex//rules/scala_proto:workspace.bzl", "scala_proto_register_toolchains", "scala_proto_repositories",)
scala_proto_repositories()
load("@annex_proto//:defs.bzl", annex_proto_pinned_maven_install = "pinned_maven_install")
annex_proto_pinned_maven_install()
scala_proto_register_toolchains()

# Specify the scala compiler we wish to use; in this case, we'll use the default one specified in rules_scala_annex
bind(
    name = "default_scala",
    actual = "@rules_scala_annex//src/main/scala:zinc_2_12_10",
)

#
# download the rules_twirl repository (needed to compile twirl templates)
#
#rules_twirl_version = "9ac789845e3a481fe520af57bd47a4261edb684f" # 20.07.2020
#rules_twirl_version_sha256 = "b1698a2a59b76dc9df233314c2a1ca8cee4a0477665cff5eafd36f92057b2044"
#http_archive(
#  name = "io_bazel_rules_twirl",
#  strip_prefix = "rules_twirl-%s" % rules_twirl_version,
#  type = "zip",
#  url = "https://github.com/subotic/rules_twirl/archive/%s.zip" % rules_twirl_version,
#  sha256 = rules_twirl_version_sha256,
#)

local_repository(
    name = "io_bazel_rules_twirl",
    path = "../../subotic/rules_twirl",
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
    urls = [
        "https://storage.googleapis.com/bazel-mirror/github.com/bazelbuild/rules_go/releases/download/v0.20.1/rules_go-v0.20.1.tar.gz",
        "https://github.com/bazelbuild/rules_go/releases/download/v0.20.1/rules_go-v0.20.1.tar.gz",
    ],
    sha256 = "842ec0e6b4fbfdd3de6150b61af92901eeb73681fd4d185746644c338f51d4c0",
)

load("@io_bazel_rules_go//go:deps.bzl", "go_rules_dependencies", "go_register_toolchains")

go_rules_dependencies()

go_register_toolchains()

#
# Download the rules_docker repository at release v0.14.4
#
rules_docker_version="0.14.4"
rules_docker_version_sha256="4521794f0fba2e20f3bf15846ab5e01d5332e587e9ce81629c7f96c793bb7036"
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
  registry = "gcr.io",
  repository = "distroless/java",
  # 'tag' is also supported, but digest is encouraged for reproducibility.
  digest = "sha256:deadbeef",
)

# get openjdk
container_pull(
    name = "openjdk11",
    registry = "docker.io",
    repository = "adoptopenjdk",
    tag = "11-jre-hotspot-bionic",
    digest = "sha256:0e51b455654bd162c485a6a6b5b120cc82db453d9265cc90f0c4fb5d14e2f62e",
)

# get sipi
load("//third_party:versions.bzl", "SIPI_REPOSITORY", "SIPI_TAG")
container_pull(
    name = "sipi",
    registry = "docker.io",
    repository = SIPI_REPOSITORY,
    tag = SIPI_TAG,
    digest = "sha256:7b7abd324d0887f3ff46de7d7f066dd699b3acd96b94177f153f750f7572031c",
)

# get fuseki
load("//third_party:versions.bzl", "FUSEKI_REPOSITORY", "FUSEKI_TAG")
container_pull(
    name = "jenafuseki",
    registry = "docker.io",
    repository = FUSEKI_REPOSITORY,
    tag = FUSEKI_TAG,
    digest = "sha256:a019024b94aeecf0ad7ce078a1cc5e7692c16ba32e43528020c70e7c8b7ee86f",
)

#
# download rules_pkg - basic packaging rules
#
rules_package_version="0.2.4"
rules_package_version_sha256="4ba8f4ab0ff85f2484287ab06c0d871dcb31cc54d439457d28fd4ae14b18450a"
http_archive(
    name = "rules_pkg",
    url = "https://github.com/bazelbuild/rules_pkg/releases/download/%s/rules_pkg-%s.tar.gz" % (rules_package_version, rules_package_version),
    sha256 = rules_package_version_sha256
)

# load further dependencies of this rule
load("@rules_pkg//:deps.bzl", "rules_pkg_dependencies")
rules_pkg_dependencies()
