package(default_visibility = ["//visibility:public"])

load("@io_bazel_rules_scala//scala:scala.bzl", "scala_test")

filegroup(
    name = "srcs",
    srcs = glob(["*.scala"]),
)

scala_test(
    name = "AuthenticationV2E2ESpec",
    size = "small",  # 60s
    srcs = [
        "AuthenticationV2E2ESpec.scala",
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
    name = "JSONLDHandlingV2R2RSpec",
    size = "medium",
    srcs = [
        "JSONLDHandlingV2R2RSpec.scala",
        "ResponseCheckerV2.scala",
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
        "@maven//:com_typesafe_akka_akka_http_testkit_2_12",
        "@maven//:org_scalatest_scalatest_2_12",
        "@maven//:com_fasterxml_jackson_core_jackson_core",
        "@maven//:com_github_jsonld_java_jsonld_java",
        "@maven//:com_typesafe_akka_akka_actor_2_12",
        "@maven//:com_typesafe_akka_akka_http_2_12",
        "@maven//:com_typesafe_akka_akka_http_core_2_12",
        "@maven//:com_typesafe_akka_akka_stream_2_12",
        "@maven//:com_typesafe_config",
    ],
)

scala_test(
    name = "ListsRouteV2R2RSpec",
    size = "medium",
    srcs = [
        "ListsRouteV2R2RSpec.scala",
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
        "@maven//:com_typesafe_akka_akka_http_testkit_2_12",
        "@maven//:org_scalatest_scalatest_2_12",
        "@maven//:com_typesafe_akka_akka_actor_2_12",
        "@maven//:com_typesafe_akka_akka_http_2_12",
        "@maven//:com_typesafe_akka_akka_http_core_2_12",
        "@maven//:com_typesafe_akka_akka_stream_2_12",
        "@maven//:com_typesafe_config",
        "@maven//:io_spray_spray_json_2_12",
        "@maven//:org_eclipse_rdf4j_rdf4j_client",
    ],
)

scala_test(
    name = "OntologyV2R2RSpec",
    size = "small",  # 60s
    srcs = [
        "OntologyV2R2RSpec.scala",
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
        "@maven//:com_typesafe_akka_akka_http_testkit_2_12",
        "@maven//:org_scalatest_scalatest_2_12",
        "@maven//:com_typesafe_akka_akka_actor_2_12",
        "@maven//:com_typesafe_akka_akka_http_2_12",
        "@maven//:com_typesafe_akka_akka_http_core_2_12",
        "@maven//:com_typesafe_akka_akka_stream_2_12",
        "@maven//:com_typesafe_config",
        "@maven//:io_spray_spray_json_2_12",
        "@maven//:org_eclipse_rdf4j_rdf4j_client",
    ],
)

##
## //webapi/src/test/scala/org/knora/webapi/e2e/v2:RessourcesRouteV2E2ESpec
scala_test(
    name = "RessourcesRouteV2E2ESpec",
    size = "small",  # 60s
    srcs = [
        "ResourcesRouteV2E2ESpec.scala",
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
        "@maven//:com_typesafe_akka_akka_http_testkit_2_12",
        "@maven//:org_scalatest_scalatest_2_12",
        "@maven//:com_typesafe_akka_akka_actor_2_12",
        "@maven//:com_typesafe_akka_akka_http_2_12",
        "@maven//:com_typesafe_akka_akka_http_core_2_12",
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
        "ResponseCheckerV2.scala",
        "ResponseCheckerV2Spec.scala",
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
    name = "SearchRouteV2R2RSpec",
    size = "medium",
    srcs = [
        "ResponseCheckerV2.scala",
        "SchemaHeader.scala",
        "SearchRouteV2R2RSpec.scala",
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
        "@maven//:com_typesafe_akka_akka_http_testkit_2_12",
        "@maven//:org_scalatest_scalatest_2_12",
        "@maven//:com_typesafe_akka_akka_actor_2_12",
        "@maven//:com_typesafe_akka_akka_http_2_12",
        "@maven//:com_typesafe_akka_akka_http_core_2_12",
        "@maven//:com_typesafe_akka_akka_stream_2_12",
        "@maven//:com_typesafe_config",
        "@maven//:io_spray_spray_json_2_12",
        "@maven//:org_xmlunit_xmlunit_core",
    ],
)

scala_test(
    name = "StandoffRouteV2R2RSpec",
    size = "medium",  # 300s
    srcs = [
        "ResponseCheckerV2.scala",
        "StandoffRouteV2R2RSpec.scala",
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
        "@maven//:com_typesafe_akka_akka_http_testkit_2_12",
        "@maven//:org_scalatest_scalatest_2_12",
        "@maven//:com_typesafe_akka_akka_actor_2_12",
        "@maven//:com_typesafe_akka_akka_http_2_12",
        "@maven//:com_typesafe_akka_akka_http_core_2_12",
        "@maven//:com_typesafe_akka_akka_stream_2_12",
        "@maven//:com_typesafe_config",
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
        "//knora-ontologies",
        "//test_data",
    ],
    jvm_flags = ["-Dconfig.resource=graphdb-se.conf"],
    unused_dependency_checker_mode = "warn",
    deps = [
        "//webapi:main_library",
        "//webapi:test_library",
        # Templates
        "@maven//:com_typesafe_play_twirl_api_2_12",
        # Logging
        "@maven//:com_typesafe_scala_logging_scala_logging_2_12",
        "@maven//:com_typesafe_akka_akka_http_testkit_2_12",
        "@maven//:org_scalatest_scalatest_2_12",
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
        "//knora-ontologies",
        "//test_data",
    ],
    jvm_flags = ["-Dconfig.resource=graphdb-se.conf"],
    unused_dependency_checker_mode = "warn",
    deps = [
        "//webapi:main_library",
        "//webapi:test_library",
        # Templates
        "@maven//:com_typesafe_play_twirl_api_2_12",
        # Logging
        "@maven//:com_typesafe_scala_logging_scala_logging_2_12",
        "@maven//:com_typesafe_akka_akka_http_testkit_2_12",
        "@maven//:org_scalatest_scalatest_2_12",
        "@maven//:com_typesafe_akka_akka_actor_2_12",
        "@maven//:com_typesafe_akka_akka_http_2_12",
        "@maven//:com_typesafe_akka_akka_http_core_2_12",
        "@maven//:com_typesafe_akka_akka_stream_2_12",
        "@maven//:com_typesafe_config",
    ],
)
