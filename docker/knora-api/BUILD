package(default_visibility = ["//visibility:public"])

load("@io_bazel_rules_docker//container:container.bzl", "container_image", "container_push")
load("@io_bazel_rules_docker//scala:image.bzl", "scala_image")

container_image(
    name = "knora-api",
    base = "@openjdk11//image",
    # cmd = ["java", "-jar", "webapi_deploy.jar"],
    entrypoint = ["java", "-jar", "webapi_deploy.jar"],
    files = [
        "//webapi:webapi_deploy.jar"
    ],
    labels = {
        "MAINTAINER": "400790+subotic@users.noreply.github.com",
    },
    ports = [
        "3333/tcp",
    ],
)

container_push(
   name = "push",
   image = ":knora-api",
   format = "Docker",
   registry = "index.docker.io",
   repository = "dasch-swiss/knora-api",
   tag = "{BUILD_SCM_TAG}"
)
