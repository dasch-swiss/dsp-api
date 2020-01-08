package(default_visibility = ["//visibility:public"])

load("@io_bazel_rules_scala//scala:scala.bzl", "scala_test")

filegroup(
    name = "src",
    srcs = glob(["**/*.scala"]),
)

scala_test(
    name = "VersionRouteITSpec",
    size = "small",  # 60s
    srcs = [
        "e2e/VersionRouteITSpec.scala",
    ],
    data = [
        "//knora-ontologies",
        "//test_data",
    ],
    jvm_flags = ["-Dconfig.resource=graphdb-se.conf"],
    unused_dependency_checker_mode = "warn",
    deps = [
        "//webapi:it_library",
        "//webapi:main_library",
        "@maven//:com_typesafe_akka_akka_actor_2_12",
        "@maven//:com_typesafe_akka_akka_http_2_12",
        "@maven//:com_typesafe_akka_akka_http_core_2_12",
        "@maven//:com_typesafe_akka_akka_http_spray_json_2_12",
        "@maven//:com_typesafe_akka_akka_stream_2_12",
        "@maven//:com_typesafe_config",
        "@maven//:com_typesafe_scala_logging_scala_logging_2_12",
        "@maven//:io_spray_spray_json_2_12",
        "@maven//:org_scalatest_scalatest_2_12",
    ],
)

scala_test(
    name = "ErrorV1ITSpec",
    size = "small",  # 60s
    srcs = [
        "e2e/v1/ErrorV1ITSpec.scala",
    ],
    data = [
        "//knora-ontologies",
        "//test_data",
    ],
    jvm_flags = ["-Dconfig.resource=graphdb-se.conf"],
    unused_dependency_checker_mode = "warn",
    deps = [
        "//webapi:it_library",
        "//webapi:main_library",
        "@maven//:com_typesafe_akka_akka_actor_2_12",
        "@maven//:com_typesafe_akka_akka_http_2_12",
        "@maven//:com_typesafe_akka_akka_http_core_2_12",
        "@maven//:com_typesafe_akka_akka_http_spray_json_2_12",
        "@maven//:com_typesafe_akka_akka_stream_2_12",
        "@maven//:com_typesafe_config",
        "@maven//:com_typesafe_scala_logging_scala_logging_2_12",
        "@maven//:io_spray_spray_json_2_12",
        "@maven//:org_scalatest_scalatest_2_12",
    ],
)

scala_test(
    name = "KnoraSipiIntegrationV1ITSpec",
    size = "medium",
    srcs = [
        "e2e/v1/KnoraSipiIntegrationV1ITSpec.scala",
    ],
    data = [
        "//knora-ontologies",
        "//test_data",
    ],
    jvm_flags = ["-Dconfig.resource=graphdb-se.conf"],
    unused_dependency_checker_mode = "warn",
    deps = [
        "//webapi:it_library",
        "//webapi:main_library",
        "@maven//:com_typesafe_akka_akka_actor_2_12",
        "@maven//:com_typesafe_akka_akka_http_2_12",
        "@maven//:com_typesafe_akka_akka_http_core_2_12",
        "@maven//:com_typesafe_akka_akka_http_spray_json_2_12",
        "@maven//:com_typesafe_akka_akka_stream_2_12",
        "@maven//:com_typesafe_config",
        "@maven//:com_typesafe_scala_logging_scala_logging_2_12",
        "@maven//:io_spray_spray_json_2_12",
        "@maven//:org_scala_lang_modules_scala_xml_2_12",
        "@maven//:org_scalatest_scalatest_2_12",
        "@maven//:org_xmlunit_xmlunit_core",
    ],
)

scala_test(
    name = "KnoraSipiPermissionsV1ITSpec",
    size = "small",  # 60s
    srcs = [
        "e2e/v1/KnoraSipiPermissionsV1ITSpec.scala",
    ],
    data = [
        "//knora-ontologies",
        "//test_data",
    ],
    jvm_flags = ["-Dconfig.resource=graphdb-se.conf"],
    unused_dependency_checker_mode = "warn",
    deps = [
        "//webapi:it_library",
        "//webapi:main_library",
        "@maven//:com_typesafe_akka_akka_actor_2_12",
        "@maven//:com_typesafe_akka_akka_http_2_12",
        "@maven//:com_typesafe_akka_akka_http_core_2_12",
        "@maven//:com_typesafe_akka_akka_http_spray_json_2_12",
        "@maven//:com_typesafe_akka_akka_stream_2_12",
        "@maven//:com_typesafe_config",
        "@maven//:com_typesafe_scala_logging_scala_logging_2_12",
        "@maven//:io_spray_spray_json_2_12",
        "@maven//:org_scalatest_scalatest_2_12",
    ],
)

scala_test(
    name = "KnoraSipiScriptsV1ITSpec",
    size = "small",  # 60s
    srcs = [
        "e2e/v1/KnoraSipiScriptsV1ITSpec.scala",
    ],
    data = [
        "//knora-ontologies",
        "//test_data",
    ],
    jvm_flags = ["-Dconfig.resource=graphdb-se.conf"],
    unused_dependency_checker_mode = "warn",
    deps = [
        "//webapi:it_library",
        "//webapi:main_library",
        "@maven//:com_typesafe_akka_akka_actor_2_12",
        "@maven//:com_typesafe_akka_akka_http_2_12",
        "@maven//:com_typesafe_akka_akka_http_core_2_12",
        "@maven//:com_typesafe_akka_akka_http_spray_json_2_12",
        "@maven//:com_typesafe_akka_akka_stream_2_12",
        "@maven//:com_typesafe_config",
        "@maven//:io_spray_spray_json_2_12",
        "@maven//:org_scalatest_scalatest_2_12",
    ],
)

scala_test(
    name = "KnoraSipiIntegrationV2ITSpec",
    size = "small",  # 60s
    srcs = [
        "e2e/v2/KnoraSipiIntegrationV2ITSpec.scala",
    ],
    data = [
        "//knora-ontologies",
        "//test_data",
    ],
    jvm_flags = ["-Dconfig.resource=graphdb-se.conf"],
    unused_dependency_checker_mode = "warn",
    deps = [
        "//webapi:it_library",
        "//webapi:main_library",
        "@maven//:com_typesafe_akka_akka_actor_2_12",
        "@maven//:com_typesafe_akka_akka_http_2_12",
        "@maven//:com_typesafe_akka_akka_http_core_2_12",
        "@maven//:com_typesafe_akka_akka_http_spray_json_2_12",
        "@maven//:com_typesafe_akka_akka_stream_2_12",
        "@maven//:com_typesafe_config",
        "@maven//:com_typesafe_scala_logging_scala_logging_2_12",
        "@maven//:io_spray_spray_json_2_12",
        "@maven//:org_scalatest_scalatest_2_12",
        "@maven//:org_slf4j_slf4j_api",
    ],
)

scala_test(
    name = "DrawingsGodsV1ITSpec",
    size = "small",  # 60s
    srcs = [
        "other/v1/DrawingsGodsV1ITSpec.scala",
    ],
    data = [
        "//knora-ontologies",
        "//test_data",
    ],
    jvm_flags = ["-Dconfig.resource=graphdb-se.conf"],
    unused_dependency_checker_mode = "warn",
    deps = [
        "//webapi:it_library",
        "//webapi:main_library",
        "@maven//:com_typesafe_akka_akka_actor_2_12",
        "@maven//:com_typesafe_akka_akka_http_2_12",
        "@maven//:com_typesafe_akka_akka_http_core_2_12",
        "@maven//:com_typesafe_akka_akka_http_spray_json_2_12",
        "@maven//:com_typesafe_akka_akka_stream_2_12",
        "@maven//:com_typesafe_config",
        "@maven//:com_typesafe_scala_logging_scala_logging_2_12",
        "@maven//:io_spray_spray_json_2_12",
        "@maven//:org_scalatest_scalatest_2_12",
    ],
)
