package(default_visibility = ["//visibility:public"])

load("@io_bazel_rules_scala//scala:scala.bzl", "scala_test")

filegroup(
    name = "srcs",
    srcs = glob(["**/*.scala"]),
)

test_suite(
    name = "all_tests",
    tests = [
        ":GravsearchParserSpec",
        ":GravsearchTypeInspectorSpec",
        ":NonTriplestoreSpecificGravsearchToCountPrequeryGeneratorSpec",
        ":NonTriplestoreSpecificGravsearchToPrequeryGeneratorSpec",
        ":SparqlTransformerSpec",
    ],
)

scala_test(
    name = "SparqlTransformerSpec",
    size = "small",  # 60s
    srcs = [
        "SparqlTransformerSpec.scala",
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
    name = "NonTriplestoreSpecificGravsearchToCountPrequeryGeneratorSpec",
    size = "small",  # 60s
    srcs = [
        "gravsearch/prequery/NonTriplestoreSpecificGravsearchToCountPrequeryGeneratorSpec.scala",
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
        "@maven//:com_typesafe_akka_akka_http_spray_json_2_12",
        "@maven//:com_typesafe_akka_akka_stream_2_12",
        "@maven//:com_typesafe_config",
        "@maven//:io_spray_spray_json_2_12",
    ],
)

scala_test(
    name = "NonTriplestoreSpecificGravsearchToPrequeryGeneratorSpec",
    size = "small",
    srcs = [
        "gravsearch/prequery/NonTriplestoreSpecificGravsearchToPrequeryGeneratorSpec.scala",
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
    name = "GravsearchTypeInspectorSpec",
    size = "small",  # 60s
    srcs = [
        "gravsearch/types/GravsearchTypeInspectorSpec.scala",
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
        "@maven//:com_typesafe_akka_akka_http_spray_json_2_12",
        "@maven//:com_typesafe_akka_akka_stream_2_12",
        "@maven//:com_typesafe_config",
        "@maven//:io_spray_spray_json_2_12",
    ],
)

scala_test(
    name = "GravsearchParserSpec",
    size = "small",  # 300s
    srcs = [
        "gravsearch/GravsearchParserSpec.scala",
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
