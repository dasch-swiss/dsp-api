package(default_visibility = ["//visibility:public"])

load("@io_bazel_rules_scala//scala:scala.bzl", "scala_test")

filegroup(
    name = "srcs",
    srcs = [
        "CoreSpec.scala",
        "E2ESimSpec.scala",
        "E2ESpec.scala",
        "ManagersWithMockedSipi.scala",
        "R2RSpec.scala",
        "SharedListsTestDataADM.scala",
        "SharedPermissionsTestData.scala",
        "SharedTestDataV1.scala",
        "TestProbeMaker.scala",
        "//webapi/src/test/scala/org/knora/webapi/e2e:srcs",
        "//webapi/src/test/scala/org/knora/webapi/responders:srcs",
        "//webapi/src/test/scala/org/knora/webapi/store:srcs",
        "//webapi/src/test/scala/org/knora/webapi/util:srcs",
    ],
)

scala_test(
    name = "SettingsSpec",
    size = "small",
    srcs = [
        "SettingsSpec.scala",
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
