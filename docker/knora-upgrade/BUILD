package(default_visibility = ["//visibility:public"])

load("@io_bazel_rules_docker//container:container.bzl", "container_layer", "container_image", "container_push")
load("@io_bazel_rules_docker//scala:image.bzl", "scala_image")

container_image(
    name = "knora-upgrade",
    base = "@openjdk11//image",
    # cmd = ["/bin/bash",],
    # entrypoint = ["/upgrade/upgrade"],
    env = {},
    tars = [
        "//upgrade/graphdb-se:graphdb-se-scripts",
    ],
    labels = {
        "MAINTAINER": "400790+subotic@users.noreply.github.com",
    },
    # ports = ["3335/tcp"],
    # layers = [":bash_layer"],
)

# create language specific image
# uses scala_binary underneath
#scala_image(
#    name = "knora-upgrade",
#    deps = ["//upgrade:main_library"],
#    main_class = "org.knora.upgrade.Main",
#    base = ":knora_upgrade_base_image",
#
#)

container_push(
   name = "push",
   image = ":knora-upgrade",
   format = "Docker",
   registry = "index.docker.io",
   repository = "dasch-swiss/knora-upgrade",
   tag = "{BUILD_SCM_TAG}"
)
