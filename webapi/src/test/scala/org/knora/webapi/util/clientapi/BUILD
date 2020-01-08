package(default_visibility = ["//visibility:public"])

load("@io_bazel_rules_scala//scala:scala.bzl", "scala_test")

scala_test(
    name = "ClientCollectionTypeParserSpec",
    size = "small",  # 60s
    srcs = [
        "ClientCollectionTypeParserSpec.scala",
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
        "@maven//:com_typesafe_akka_akka_stream_2_12",
        "@maven//:com_typesafe_config",
    ],
)

scala_test(
    name = "SourceCodeFilePathSpec",
    size = "small",  # 60s
    srcs = [
        "SourceCodeFilePathSpec.scala",
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
        "@maven//:com_typesafe_akka_akka_stream_2_12",
        "@maven//:com_typesafe_config",
    ],
)
