package(default_visibility = ["//visibility:public"])

load("@io_bazel_rules_scala//scala:scala.bzl", "scala_test")

test_suite(
    name = "all_tests",
    tests = [
        "//webapi/src/test/scala/org/knora/webapi/e2e/admin:all_tests",
        "//webapi/src/test/scala/org/knora/webapi/e2e/v1:all_tests",
        "//webapi/src/test/scala/org/knora/webapi/e2e/v2:all_tests",
        ":ClientApiRouteE2ESpec",
        ":ExceptionHandlerR2RSpec",
        ":HealthRouteE2ESpec",
        ":InstanceCheckerSpec",
        ":RejectingRouteE2ESpec",
    ],
)

scala_test(
    name = "ClientApiRouteE2ESpec",
    size = "small",  # 60s
    srcs = [
        "ClientApiRouteE2ESpec.scala",
    ],
    data = [
        "//knora-ontologies:all",
        "//test-data:all",
    ],
    jvm_flags = ["-Dconfig.resource=graphdb-se.conf"],
    unused_dependency_checker_mode = "warn",
    deps = [
        "//webapi:library",
        "//webapi:test_library",
        # Logging
        "@maven//:com_typesafe_scala_logging_scala_logging_2_12",
        "@maven//:org_slf4j_slf4j_api",
        # Test Libs
        "@maven//:com_typesafe_akka_akka_testkit_2_12",
        "@maven//:com_typesafe_akka_akka_http_testkit_2_12",
        "@maven//:org_scalatest_scalatest_2_12",
        # Other
        "@maven//:commons_io_commons_io",
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
    name = "ExceptionHandlerR2RSpec",
    size = "small",  # 60s
    srcs = [
        "ExceptionHandlerR2RSpec.scala",
    ],
    data = [
        "//knora-ontologies:all",
        "//test-data:all",
    ],
    jvm_flags = ["-Dconfig.resource=graphdb-se.conf"],
    unused_dependency_checker_mode = "warn",
    deps = [
        "//webapi:library",
        "//webapi:test_library",
        # Logging
        "@maven//:com_typesafe_scala_logging_scala_logging_2_12",
        "@maven//:org_slf4j_slf4j_api",
        # Test Libs
        "@maven//:com_typesafe_akka_akka_testkit_2_12",
        "@maven//:com_typesafe_akka_akka_http_testkit_2_12",
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
     name = "HealthRouteE2ESpec",
     size = "small",  # 60s
     srcs = [
         "HealthRouteE2ESpec.scala",
     ],
     data = [
         "//knora-ontologies:all",
         "//test-data:all",
     ],
     jvm_flags = ["-Dconfig.resource=graphdb-se.conf"],
     unused_dependency_checker_mode = "warn",
     deps = [
         "//webapi:library",
         "//webapi:test_library",
         # Logging
         "@maven//:com_typesafe_scala_logging_scala_logging_2_12",
         "@maven//:org_slf4j_slf4j_api",
         # Test Libs
         "@maven//:com_typesafe_akka_akka_testkit_2_12",
         "@maven//:com_typesafe_akka_akka_http_testkit_2_12",
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
     name = "InstanceCheckerSpec",
     size = "small",  # 60s
     srcs = [
         "InstanceCheckerSpec.scala",
         "InstanceChecker.scala"
     ],
     data = [
         "//knora-ontologies:all",
         "//test-data:all",
     ],
     jvm_flags = ["-Dconfig.resource=graphdb-se.conf"],
     unused_dependency_checker_mode = "warn",
     deps = [
         "//webapi:library",
         "//webapi:test_library",
         # Logging
         "@maven//:com_typesafe_scala_logging_scala_logging_2_12",
         "@maven//:org_slf4j_slf4j_api",
         # Test Libs
         "@maven//:com_typesafe_akka_akka_testkit_2_12",
         "@maven//:com_typesafe_akka_akka_http_testkit_2_12",
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
     name = "RejectingRouteE2ESpec",
     size = "small",  # 60s
     srcs = [
         "RejectingRouteE2ESpec.scala",
     ],
     data = [
         "//knora-ontologies:all",
         "//test-data:all",
     ],
     jvm_flags = ["-Dconfig.resource=graphdb-se.conf"],
     unused_dependency_checker_mode = "warn",
     deps = [
         "//webapi:library",
         "//webapi:test_library",
         # Logging
         "@maven//:com_typesafe_scala_logging_scala_logging_2_12",
         "@maven//:org_slf4j_slf4j_api",
         # Test Libs
         "@maven//:com_typesafe_akka_akka_testkit_2_12",
         "@maven//:com_typesafe_akka_akka_http_testkit_2_12",
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