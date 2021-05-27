package(default_visibility = ["//visibility:public"])

load("@bazel_tools//tools/build_defs/pkg:pkg.bzl", "pkg_tar")

filegroup(
    name = "knora-ontologies",
    srcs = [
        "knora-admin.ttl",
        "knora-base.ttl",
        "salsah-gui.ttl",
        "standoff-data.ttl",
        "standoff-onto.ttl",
    ],
)

pkg_tar(
    name = "tar",
    srcs = [":knora-ontologies"],
    package_dir = "knora-ontologies",
)
