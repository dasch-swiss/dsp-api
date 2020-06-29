workspace(name = "io_dasch_knora_api")

# load http_archive method
load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive", "http_file")

# download rules_scala repository
rules_scala_version="0f89c210ade8f4320017daf718a61de3c1ac4773" # update this as needed
rules_scala_version_sha256="37eb013ea3e6a940da70df43fe2dd6f423d1ac0849042aa586f9ac157321018d"
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
    "2.12.8",
    {
       "scala_compiler": "f34e9119f45abd41e85b9e121ba19dd9288b3b4af7f7047e86dc70236708d170",
       "scala_library": "321fb55685635c931eba4bc0d7668349da3f2c09aee2de93a70566066ff25c28",
       "scala_reflect": "4d6405395c4599ce04cea08ba082339e3e42135de9aae2923c9f5367e957315a"
    }
))

#
# Download the protobuf repository (needed by go)
#
protobuf_version="09745575a923640154bcf307fba8aedff47f240a"
protobuf_version_sha256="416212e14481cff8fd4849b1c1c1200a7f34808a54377e22d7447efdf54ad758"

http_archive(
    name = "com_google_protobuf",
    url = "https://github.com/protocolbuffers/protobuf/archive/%s.tar.gz" % protobuf_version,
    strip_prefix = "protobuf-%s" % protobuf_version,
    sha256 = protobuf_version_sha256,
)

# bazel-skylib 0.8.0 released 2019.03.20 (https://github.com/bazelbuild/bazel-skylib/releases/tag/0.8.0)
skylib_version = "0.8.0"
http_archive(
    name = "bazel_skylib",
    type = "tar.gz",
    url = "https://github.com/bazelbuild/bazel-skylib/releases/download/%s/bazel-skylib.%s.tar.gz" % (skylib_version, skylib_version),
    sha256 = "2ef429f5d7ce7111263289644d233707dba35e39696377ebab8b0bc701f7818e",
)

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
rules_jvm_external_version = "2.8"
rules_jvm_external_version_sha256 = "79c9850690d7614ecdb72d68394f994fef7534b292c4867ce5e7dec0aa7bdfad"

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
# download the rules_twirl repository (needed to compile twirl templates)
#
rules_twirl_version = "105c51e4884d56805e51b36d38fb2113b0381a6d"
rules_twirl_version_sha256 = "c89d8460d236ec7d3c2544a72f17d21c4855da0eb79556c9dbdc95938c411057"
http_archive(
  name = "io_bazel_rules_twirl",
  strip_prefix = "rules_twirl-%s" % rules_twirl_version,
  url = "https://github.com/lucidsoftware/rules_twirl/archive/%s.zip" % rules_twirl_version,
  type = "zip",
  sha256 = rules_twirl_version_sha256,
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
# Download the rules_docker repository at release v0.12.0
#
rules_docker_version="0.13.0"
rules_docker_version_sha256="df13123c44b4a4ff2c2f337b906763879d94871d16411bf82dcfeba892b58607"
http_archive(
    name = "io_bazel_rules_docker",
    sha256 = rules_docker_version_sha256,
    strip_prefix = "rules_docker-%s" % rules_docker_version,
    url = "https://github.com/bazelbuild/rules_docker/releases/download/v%s/rules_docker-v%s.tar.gz" % (rules_docker_version, rules_docker_version),
)

# load rules_docker repositories
load(
    "@io_bazel_rules_docker//repositories:repositories.bzl",
    container_repositories = "repositories",
)
container_repositories()

# load further dependencies of this rule
load("@io_bazel_rules_docker//repositories:deps.bzl", container_deps = "deps")
container_deps()

# load container_pull method
load(
    "@io_bazel_rules_docker//container:container.bzl",
    "container_pull"
)
container_pull(
    name = "openjdk11",
    registry = "docker.io",
    repository = "adoptopenjdk",
    tag = "11-hotspot",
    digest = "sha256:755c2308a261ce0aa29cbc511e8292914057163826004b3ccc0681a7fc28b860",
)

load("//third_party:versions.bzl", "SIPI_REPOSITORY", "SIPI_TAG")
container_pull(
    name = "sipi",
    registry = "docker.io",
    repository = SIPI_REPOSITORY,
    tag = SIPI_TAG,
    digest = "sha256:4b4266da659f30f27722d52e55066937de3ef8828f2a86fafcc75b72cc979b28",
)

load("//third_party:versions.bzl", "FUSEKI_REPOSITORY", "FUSEKI_TAG")
container_pull(
    name = "jenafuseki",
    registry = "docker.io",
    repository = FUSEKI_REPOSITORY,
    tag = FUSEKI_TAG,
    # digest = "sha256:a8a5d5dce1de8855ffa2a327b98e0b1ea59b81683d0d02ec8dbbc90838691c65",
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
