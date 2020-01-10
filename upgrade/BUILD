package(default_visibility = ["//visibility:public"])

load("@io_bazel_rules_scala//scala:scala.bzl", "scala_binary", "scala_library")

filegroup(
    name = "main_srcs",
    srcs = glob(["src/main/**/*.scala"]),
)

scala_binary(
    name = "upgrade",
    jvm_flags = [
        "-Dlogging.config=logback.xml",
    ],
    main_class = "org.knora.upgrade.Main",
    resources = [
        "//upgrade/src/main/resources",
    ],
    deps = [
        ":main_library",
        "@maven//:com_typesafe_akka_akka_slf4j_2_12",
        "@maven//:org_slf4j_log4j_over_slf4j",
        "@maven//:ch_qos_logback_logback_classic",
        "@maven//:ch_qos_logback_logback_core",
    ],
)

scala_library(
    name = "main_library",
    srcs = [
        "main_srcs",
    ],
    resources = [
        "//upgrade/src/main/resources",
    ],
    unused_dependency_checker_mode = "warn",
    deps = [
        "//webapi:main_library",
        "@maven//:org_eclipse_rdf4j_rdf4j_client",
        "@maven//:org_rogach_scallop_2_12",
    ],
)
