package(default_visibility = ["//visibility:public"])

load("@io_bazel_rules_scala//scala:scala.bzl", "scala_test")

filegroup(
    name = "srcs",
    srcs = glob(["**/*.scala"]),
)

scala_test(
    name = "DrawingsGodsV1E2ESpec",
    size = "small",  # 60s
    srcs = [
        "v1/DrawingsGodsV1E2ESpec.scala",
    ],
    data = [
        "//knora-ontologies",
        "//test_data",
    ],
    jvm_flags = ["-Dconfig.resource=graphdb-se.conf"],
    unused_dependency_checker_mode = "warn",
    deps = [
        "//webapi:main_library",
        "//webapi:test_library",
        # Logging
        "@maven//:com_typesafe_scala_logging_scala_logging_2_12",
        "@maven//:org_slf4j_slf4j_api",
        "@maven//:org_scalatest_scalatest_2_12",
        "@maven//:com_typesafe_akka_akka_actor_2_12",
        "@maven//:com_typesafe_akka_akka_http_2_12",
        "@maven//:com_typesafe_akka_akka_http_core_2_12",
        "@maven//:com_typesafe_akka_akka_http_spray_json_2_12",
        "@maven//:com_typesafe_akka_akka_stream_2_12",
        "@maven//:com_typesafe_config",
        "@maven//:io_spray_spray_json_2_12",
    ],
)

scala_test(
    name = "DrawingsGodsV1Spec",
    size = "small",  # 60s
    srcs = [
        "v1/DrawingsGodsV1Spec.scala",
    ],
    data = [
        "//knora-ontologies",
        "//test_data",
    ],
    jvm_flags = ["-Dconfig.resource=graphdb-se.conf"],
    unused_dependency_checker_mode = "warn",
    deps = [
        "//webapi:main_library",
        "//webapi:test_library",
        # Logging
        "@maven//:com_typesafe_scala_logging_scala_logging_2_12",
        # Test Libs
        "@maven//:com_typesafe_akka_akka_testkit_2_12",
        "@maven//:org_scalatest_scalatest_2_12",
        "@maven//:com_typesafe_akka_akka_actor_2_12",
        "@maven//:com_typesafe_akka_akka_http_2_12",
        "@maven//:com_typesafe_akka_akka_http_core_2_12",
        "@maven//:com_typesafe_akka_akka_http_spray_json_2_12",
        "@maven//:com_typesafe_akka_akka_stream_2_12",
        "@maven//:com_typesafe_config",
        "@maven//:io_spray_spray_json_2_12",
    ],
)

scala_test(
    name = "LumieresLausanneV2E2ESpec",
    size = "small",  # 60s
    srcs = [
        "v2/LumieresLausanneV2E2ESpec.scala",
    ],
    data = [
        "//knora-ontologies",
        "//test_data",
    ],
    jvm_flags = ["-Dconfig.resource=graphdb-se.conf"],
    unused_dependency_checker_mode = "warn",
    deps = [
        "//webapi:main_library",
        "//webapi:test_library",
        # Logging
        "@maven//:com_typesafe_scala_logging_scala_logging_2_12",
        "@maven//:org_scalatest_scalatest_2_12",
        "@maven//:com_typesafe_akka_akka_actor_2_12",
        "@maven//:com_typesafe_akka_akka_http_2_12",
        "@maven//:com_typesafe_akka_akka_http_core_2_12",
        "@maven//:com_typesafe_akka_akka_http_spray_json_2_12",
        "@maven//:com_typesafe_akka_akka_stream_2_12",
        "@maven//:com_typesafe_config",
        "@maven//:io_spray_spray_json_2_12",
    ],
)
