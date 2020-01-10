package(default_visibility = ["//visibility:public"])

load("@bazel_tools//tools/build_defs/pkg:pkg.bzl", "pkg_tar")

filegroup(
    name = "graphdb-se",
    srcs = [
        "auto-upgrade.sh",
        "dump-repository.sh",
        "empty-repository.sh",
        "upload-repository.sh"
    ]
)

pkg_tar(
    name = "graphdb-se-scripts",
    package_dir = "graphdb-se",
    srcs = [":graphdb-se"]
)