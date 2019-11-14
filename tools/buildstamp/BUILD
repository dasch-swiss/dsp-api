package(default_visibility = ["//visibility:public"])

load("@io_bazel_rules_scala//scala:scala.bzl", "scala_library")

filegroup(
    name = "all",
    srcs = [
        "get_workspace_status",
    ],
)

filegroup(
    name = "srcs",
    srcs = glob(["**"]),
)

# bring our version constants in
load("//third_party:versions.bzl", "AKKA_VERSION", "AKKA_HTTP_VERSION", "SIPI_VERSION")

# Generates a scala source file that includes version information
load("//tools/buildstamp:gen_build_info.bzl", "gen_build_info")
gen_build_info(
    name = "build_info_src",
    akka_version = AKKA_VERSION,
    akka_http_version = AKKA_HTTP_VERSION,
    sipi_version = SIPI_VERSION,
)

scala_library(
    name = "build_info_jar",
    srcs = [":build_info_src"],
)