load("@io_bazel_rules_scala//scala:scala_toolchain.bzl", "scala_toolchain")

# scalac options like in KnoraBuild.sbt (warn-unused is added, as it seems good)
scala_toolchain(
    name = "knora_api_toolchain_impl",
    scalacopts = [
        "-Ywarn-unused",
        "-feature",
        "-unchecked",
        "-deprecation",
        "-Yresolve-term-conflict:package",
    ],
    unused_dependency_checker_mode = "off",
    visibility = ["//visibility:public"],
)

toolchain(
    name = "knora_api_scala_toolchain",
    toolchain = "knora_api_toolchain_impl",
    toolchain_type = "@io_bazel_rules_scala//scala:toolchain_type",
    visibility = ["//visibility:public"],
)
