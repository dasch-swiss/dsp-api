package(default_visibility = ["//visibility:public"])

load("@io_bazel_rules_scala//scala:scala.bzl", "scala_test")

test_suite(
    name = "all_tests",
    tests = [
        ":AuthenticationV2E2ESpec",
        ":JSONHandlingV2R2RSpec",
        ":ListsRouteV2R2RSpec",
        ":OntologyV2R2RSpec",
        ":ResponseCheckerR2RV2Spec",
        ":SearchRouteV2R2RSpec",
        ":StandoffRouteV2R2RSpec",
        ":ValuesRouteV2E2ESpec",
        ":ValuesV2R2RSpec"
    ],
)

scala_test(
    name = "AuthenticationV2E2ESpec",
    size = "small",  # 60s
    srcs = [
        "AuthenticationV2E2ESpec.scala",
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
        # CORS
        "@maven//:ch_megard_akka_http_cors_2_12",
        "@maven//:com_jsuereth_scala_arm_2_12",
        "@maven//:com_typesafe_akka_akka_actor_2_12",
        "@maven//:com_typesafe_akka_akka_http_2_12",
        "@maven//:com_typesafe_akka_akka_http_core_2_12",
        "@maven//:com_typesafe_akka_akka_http_spray_json_2_12",
        "@maven//:com_typesafe_akka_akka_stream_2_12",
        "@maven//:com_typesafe_config",
        "@maven//:io_spray_spray_json_2_12",
        "@maven//:org_scala_lang_modules_scala_xml_2_12",
        "@maven//:org_xmlunit_xmlunit_core",
    ],
)

scala_test(
    name = "JSONLDHandlingV2R2RSpec",
    size = "small",  # 60s
    srcs = [
        "JSONLDHandlingV2R2RSpec.scala",
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
        # CORS
        "@maven//:ch_megard_akka_http_cors_2_12",
        "@maven//:com_jsuereth_scala_arm_2_12",
        "@maven//:com_typesafe_akka_akka_actor_2_12",
        "@maven//:com_typesafe_akka_akka_http_2_12",
        "@maven//:com_typesafe_akka_akka_http_core_2_12",
        "@maven//:com_typesafe_akka_akka_http_spray_json_2_12",
        "@maven//:com_typesafe_akka_akka_stream_2_12",
        "@maven//:com_typesafe_config",
        "@maven//:io_spray_spray_json_2_12",
        "@maven//:org_scala_lang_modules_scala_xml_2_12",
        "@maven//:org_xmlunit_xmlunit_core",
    ],
)

scala_test(
    name = "ListsRouteV2R2RSpec",
    size = "medium",
    srcs = [
        "ListsRouteV2R2RSpec.scala",
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
        # CORS
        "@maven//:ch_megard_akka_http_cors_2_12",
        "@maven//:com_jsuereth_scala_arm_2_12",
        "@maven//:com_typesafe_akka_akka_actor_2_12",
        "@maven//:com_typesafe_akka_akka_http_2_12",
        "@maven//:com_typesafe_akka_akka_http_core_2_12",
        "@maven//:com_typesafe_akka_akka_http_spray_json_2_12",
        "@maven//:com_typesafe_akka_akka_stream_2_12",
        "@maven//:com_typesafe_config",
        "@maven//:io_spray_spray_json_2_12",
        "@maven//:org_eclipse_rdf4j_rdf4j_client",
        "@maven//:org_scala_lang_modules_scala_xml_2_12",
        "@maven//:org_xmlunit_xmlunit_core",
    ],
)

scala_test(
    name = "OntologyV2R2RSpec",
    size = "small",  # 60s
    srcs = [
        "OntologyV2R2RSpec.scala",
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
        # CORS
        "@maven//:ch_megard_akka_http_cors_2_12",
        "@maven//:com_jsuereth_scala_arm_2_12",
        "@maven//:com_typesafe_akka_akka_actor_2_12",
        "@maven//:com_typesafe_akka_akka_http_2_12",
        "@maven//:com_typesafe_akka_akka_http_core_2_12",
        "@maven//:com_typesafe_akka_akka_http_spray_json_2_12",
        "@maven//:com_typesafe_akka_akka_stream_2_12",
        "@maven//:com_typesafe_config",
        "@maven//:io_spray_spray_json_2_12",
        "@maven//:org_eclipse_rdf4j_rdf4j_client",
        "@maven//:org_scala_lang_modules_scala_xml_2_12",
        "@maven//:org_xmlunit_xmlunit_core",
    ],
)

scala_test(
    name = "ResponseCheckerV2R2RSpec",
    size = "small",  # 60s
    srcs = [
        "ResponseCheckerV2R2RSpec.scala",
        "ResponseCheckerV2.scala",
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
        # CORS
        "@maven//:ch_megard_akka_http_cors_2_12",
        "@maven//:com_jsuereth_scala_arm_2_12",
        "@maven//:com_typesafe_akka_akka_actor_2_12",
        "@maven//:com_typesafe_akka_akka_http_2_12",
        "@maven//:com_typesafe_akka_akka_http_core_2_12",
        "@maven//:com_typesafe_akka_akka_http_spray_json_2_12",
        "@maven//:com_typesafe_akka_akka_stream_2_12",
        "@maven//:com_typesafe_config",
        "@maven//:io_spray_spray_json_2_12",
        "@maven//:org_scala_lang_modules_scala_xml_2_12",
        "@maven//:org_xmlunit_xmlunit_core",
    ],
)

scala_test(
    name = "SearchRouteV2R2RSpec",
    size = "medium",
    srcs = [
        "SearchRouteV2R2RSpec.scala",
        "ResponseCheckerV2.scala",
        "SchemaHeader.scala"
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
        # CORS
        "@maven//:ch_megard_akka_http_cors_2_12",
        "@maven//:com_jsuereth_scala_arm_2_12",
        "@maven//:com_typesafe_akka_akka_actor_2_12",
        "@maven//:com_typesafe_akka_akka_http_2_12",
        "@maven//:com_typesafe_akka_akka_http_core_2_12",
        "@maven//:com_typesafe_akka_akka_http_spray_json_2_12",
        "@maven//:com_typesafe_akka_akka_stream_2_12",
        "@maven//:com_typesafe_config",
        "@maven//:io_spray_spray_json_2_12",
        "@maven//:org_scala_lang_modules_scala_xml_2_12",
        "@maven//:org_xmlunit_xmlunit_core",
    ],
)

scala_test(
    name = "StandoffRouteV2R2RSpec",
    size = "medium",  # 300s
    srcs = [
        "StandoffRouteV2R2RSpec.scala",
        "ResponseCheckerV2.scala"
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
        # CORS
        "@maven//:ch_megard_akka_http_cors_2_12",
        "@maven//:com_jsuereth_scala_arm_2_12",
        "@maven//:com_typesafe_akka_akka_actor_2_12",
        "@maven//:com_typesafe_akka_akka_http_2_12",
        "@maven//:com_typesafe_akka_akka_http_core_2_12",
        "@maven//:com_typesafe_akka_akka_http_spray_json_2_12",
        "@maven//:com_typesafe_akka_akka_stream_2_12",
        "@maven//:com_typesafe_config",
        "@maven//:io_spray_spray_json_2_12",
        "@maven//:org_scala_lang_modules_scala_xml_2_12",
        "@maven//:org_xmlunit_xmlunit_core",
    ],
)

scala_test(
    name = "ValuesRouteV2E2ESpec",
    size = "medium",
    srcs = [
        "ValuesRouteV2E2ESpec.scala",
        "//webapi/src/main:twirl-templates",
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
        # Templates
        "@maven//:com_typesafe_play_twirl_api_2_12",
        # Logging
        "@maven//:com_typesafe_scala_logging_scala_logging_2_12",
        "@maven//:org_slf4j_slf4j_api",
        # Test Libs
        "@maven//:com_typesafe_akka_akka_testkit_2_12",
        "@maven//:com_typesafe_akka_akka_http_testkit_2_12",
        "@maven//:org_scalatest_scalatest_2_12",
        # CORS
        "@maven//:ch_megard_akka_http_cors_2_12",
        "@maven//:com_jsuereth_scala_arm_2_12",
        "@maven//:com_typesafe_akka_akka_actor_2_12",
        "@maven//:com_typesafe_akka_akka_http_2_12",
        "@maven//:com_typesafe_akka_akka_http_core_2_12",
        "@maven//:com_typesafe_akka_akka_http_spray_json_2_12",
        "@maven//:com_typesafe_akka_akka_stream_2_12",
        "@maven//:com_typesafe_config",
        "@maven//:io_spray_spray_json_2_12",
        "@maven//:org_scala_lang_modules_scala_xml_2_12",
        "@maven//:org_xmlunit_xmlunit_core",
    ],
)

scala_test(
    name = "ValuesV2R2RSpec",
    size = "small",  # 60s
    srcs = [
        "ValuesV2R2RSpec.scala",
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
        # Templates
        "@maven//:com_typesafe_play_twirl_api_2_12",
        # Logging
        "@maven//:com_typesafe_scala_logging_scala_logging_2_12",
        "@maven//:org_slf4j_slf4j_api",
        # Test Libs
        "@maven//:com_typesafe_akka_akka_testkit_2_12",
        "@maven//:com_typesafe_akka_akka_http_testkit_2_12",
        "@maven//:org_scalatest_scalatest_2_12",
        # CORS
        "@maven//:ch_megard_akka_http_cors_2_12",
        "@maven//:com_jsuereth_scala_arm_2_12",
        "@maven//:com_typesafe_akka_akka_actor_2_12",
        "@maven//:com_typesafe_akka_akka_http_2_12",
        "@maven//:com_typesafe_akka_akka_http_core_2_12",
        "@maven//:com_typesafe_akka_akka_http_spray_json_2_12",
        "@maven//:com_typesafe_akka_akka_stream_2_12",
        "@maven//:com_typesafe_config",
        "@maven//:io_spray_spray_json_2_12",
        "@maven//:org_scala_lang_modules_scala_xml_2_12",
        "@maven//:org_xmlunit_xmlunit_core",
    ],
)