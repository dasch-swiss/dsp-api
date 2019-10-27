package(default_visibility = ["//visibility:public"])

load("@io_bazel_rules_docker//container:container.bzl", "container_image", "container_layer")

container_layer(
    name = "bash_layer",
    debs = ["bash"]

)

container_image(
    name = "knora-app1",
    base = "@openjdk11//image",
    # cmd = [""],
    entrypoint = [
        "/salsah1/app1",
    ],
    env = {
        "LANG": "en_US.UTF-8",
        "JAVA_OPTS": "-Dsun.jnu.encoding=UTF-8 -Dfile.encoding=UTF-8",
        "KNORA_SALSAH1_DEPLOYED": "true",
        "KNORA_SALSAH1_WORKDIR": "/salsah1"
    },
    directory = "salsah1",
    files = [
        "//salsah1:app1",
        "//salsah1/src:public",
    ],
    labels = {
        "MAINTAINER": "400790+subotic@users.noreply.github.com",
    },
    ports = [
        "3335/tcp",
    ],
    layers = [":bash_layer"]
)


