package(default_visibility = ["//visibility:public"])

load("@io_bazel_rules_scala//scala:scala.bzl", "scala_test")

filegroup(
    name = "SessionMessagesV1",
    srcs = [
        "v1/responder/sessionmessages/SessionMessagesV1.scala",
    ],
)

scala_test(
    name = "ListsMessagesADMSpec",
    size = "small",  # 60s
    srcs = [
        "admin/responder/listsmessages/ListsMessagesADMSpec.scala",
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
        "@maven//:com_typesafe_akka_akka_actor_2_12",
        "@maven//:com_typesafe_akka_akka_http_2_12",
        "@maven//:com_typesafe_akka_akka_http_core_2_12",
        "@maven//:com_typesafe_akka_akka_http_spray_json_2_12",
        "@maven//:com_typesafe_akka_akka_stream_2_12",
        "@maven//:io_spray_spray_json_2_12",
        "@maven//:org_scalatest_scalatest_2_12",
    ],
)

scala_test(
    name = "PermissionsMessagesADMSpec",
    size = "small",  # 60s
    srcs = [
        "admin/responder/permissionsmessages/PermissionsMessagesADMSpec.scala",
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
        "@maven//:org_scalatest_scalatest_2_12",
    ],
)

scala_test(
    name = "ProjectsMessagesADMSpec",
    size = "small",  # 60s
    srcs = [
        "admin/responder/projectsmessages/ProjectsMessagesADMSpec.scala",
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
    name = "UsersMessagesADMSpec",
    size = "small",  # 60s
    srcs = [
        "admin/responder/usersmessages/UsersMessagesADMSpec.scala",
    ],
    data = [
        "//knora-ontologies",
        "//test_data",
    ],
    jvm_flags = ["-Dconfig.resource=graphdb-se.conf"],
    unused_dependency_checker_mode = "warn",
    deps = [
        "//webapi:main_library",
        "@maven//:org_scalatest_scalatest_2_12",
        "@maven//:org_springframework_security_spring_security_core",
    ],
)

scala_test(
    name = "TriplestoreMessagesSpec",
    size = "small",  # 60s
    srcs = [
        "store/triplestoremessages/TriplestoreMessagesSpec.scala",
    ],
    data = [
        "//knora-ontologies",
        "//test_data",
    ],
    jvm_flags = ["-Dconfig.resource=graphdb-se.conf"],
    unused_dependency_checker_mode = "warn",
    deps = [
        "//webapi:main_library",
        "@maven//:com_typesafe_akka_akka_actor_2_12",
        "@maven//:com_typesafe_akka_akka_http_2_12",
        "@maven//:com_typesafe_akka_akka_http_core_2_12",
        "@maven//:com_typesafe_akka_akka_http_spray_json_2_12",
        "@maven//:com_typesafe_akka_akka_stream_2_12",
        "@maven//:io_spray_spray_json_2_12",
        "@maven//:org_scalatest_scalatest_2_12",
    ],
)

scala_test(
    name = "PermissionMessagesV1Spec",
    size = "small",  # 60s
    srcs = [
        "v1/responder/permissionmessages/PermissionMessagesV1Spec.scala",
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
        "@maven//:org_scalatest_scalatest_2_12",
    ],
)

scala_test(
    name = "UserMessagesV1Spec",
    size = "small",  # 60s
    srcs = [
        "v1/responder/usermessages/UserMessagesV1Spec.scala",
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
        "@maven//:org_scalatest_scalatest_2_12",
        "@maven//:org_springframework_security_spring_security_core",
    ],
)

scala_test(
    name = "InputOntologyV2Spec",
    size = "small",  # 60s
    srcs = [
        "v2/responder/ontologymessages/InputOntologyV2Spec.scala",
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
