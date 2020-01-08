package(default_visibility = ["//visibility:public"])

load("@io_bazel_rules_docker//container:container.bzl", "container_pull", "container_image", "container_push")

container_image(
    name = "knora-graphdb-free",
    base = "@graphdbfree//image",
    env = {},
    directory = "/graphdb",
    files = [
        "//webapi:knora_rules_pie",
    ],
    labels = {
        "MAINTAINER": "400790+subotic@users.noreply.github.com",
    },
    ports = ["7200/tcp"],
)

container_push(
   name = "push",
   image = ":knora-graphdb-free",
   format = "Docker",
   registry = "index.docker.io",
   repository = "dasch-swiss/knora-graphdb-free",
   tag = "{BUILD_SCM_TAG}"
)
