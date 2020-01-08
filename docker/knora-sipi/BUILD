package(default_visibility = ["//visibility:public"])

load("@io_bazel_rules_docker//container:container.bzl", "container_image", "container_push")

container_image(
    name = "knora-sipi",
    base = "@sipi//image",
    env = {},
    tars = [
        "//sipi/scripts:sipi-scripts",
    ],
    labels = {
        "MAINTAINER": "400790+subotic@users.noreply.github.com",
    },
    ports = ["1024/tcp"],
)

container_push(
   name = "push",
   image = ":knora-sipi",
   format = "Docker",
   registry = "index.docker.io",
   repository = "dasch-swiss/knora-sipi",
   tag = "{BUILD_SCM_TAG}"
)


