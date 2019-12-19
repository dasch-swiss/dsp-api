package(default_visibility = ["//visibility:public"])

load("@io_bazel_rules_scala//scala:scala.bzl", "scala_binary", "scala_library")

filegroup(
    name = "main_srcs",
    srcs = glob(["src/main/**/*.scala"])
)

scala_binary(
    name = "upgrade",
    main_class = "org.knora.upgrage.Main",
    deps = [
        ":main_library",
    ],
)

scala_library(
    name = "main_library",
    srcs = [
        "main_srcs",
    ],
    resources = [
        "//webapi/src/main/resources",
    ],
    unused_dependency_checker_mode = "warn",
    deps = [
        "//webapi:main_library",
        "@maven//:org_rogach_scallop_2_12",
        "@maven//:org_eclipse_rdf4j_rdf4j_client",
        "@maven//:org_eclipse_rdf4j_rdf4j_model",
    ],
)