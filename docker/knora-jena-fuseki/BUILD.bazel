package(default_visibility = ["//visibility:public"])

load("@io_bazel_rules_docker//container:container.bzl", "container_image", "container_push")

container_image(
    name = "knora-jena-fuseki",
    base = "@jenafuseki//image",
    env = {},
    labels = {
        "MAINTAINER": "400790+subotic@users.noreply.github.com",
    },
    ports = ["3030/tcp"],
)

container_push(
   name = "push",
   image = ":knora-jena-fuseki",
   format = "Docker",
   registry = "index.docker.io",
   repository = "dasch-swiss/knora-jena-fuseki",
   tag = "{BUILD_SCM_TAG}"
)
