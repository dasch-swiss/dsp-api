package(default_visibility = ["//visibility:public"])

filegroup(
    name = "webapi_resources",
    srcs = glob([
        "src/main/resources/**",
    ]),
)

scala_library(
    name = "webapi",
    srcs = glob(["src/main/scala/org/knora/webapi/**/*.scala"]),
    resources = [":webapi_resources"],
    deps = [
        "@scala_xml//jar"
    ],
)

filegroup(
    name = "webapi_test_resources",
    srcs = glob([
        "src/test/resources/**",
    ]),
)

scala_test(
    name = "webapi_test",
    srcs = glob(["src/test/scala/org/knora/webapi/**/*.scala"]),
    resources = ["webapi_test_resources"],
    size = "large", # Expect this test to run slowly
    deps = [
        ":webapi",
        "@scala_test//jar",
    ],
    data = [
        ":webapi_test_resources",
    ],
)

filegroup(
    name = "webapi_it_resources",
    srcs = glob([
        "src/it/resources/**",
    ]),
)

scala_test(
    name = "webapi_it",
    srcs = glob(["src/it/scala/org/knora/webapi/**/*.scala"]),
    resources = [":webapi_it_resources"],
    size = "large", # Expect this test to run slowly
    deps = [
        ":webapi"
    ],
    data = [
        ":webapi_it_resources",
        "@scala_test//jar",
    ],
)