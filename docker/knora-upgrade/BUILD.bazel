package(default_visibility = ["//visibility:public"])

load("@io_bazel_rules_docker//container:container.bzl", "container_image", "container_push")

container_image(
    name = "knora-upgrade",
    base = "@openjdk11//image",
    # cmd = ["/bin/bash"],
    entrypoint = ["java", "-jar", "/upgrade/upgrade_deploy.jar"],
    env = {
        "LANG": "en_US.UTF-8",
        "JAVA_OPTS": "-Dsun.jnu.encoding=UTF-8 -Dfile.encoding=UTF-8 -Dlogging.config=logback.xml",
    },
    directory = "upgrade",
    files = [
        "//upgrade:upgrade_deploy.jar"
    ],
    tars = [
        "//upgrade/graphdb-se:tar",
    ],
    labels = {
        "MAINTAINER": "400790+subotic@users.noreply.github.com",
    },
)

container_push(
   name = "push",
   image = ":knora-upgrade",
   format = "Docker",
   registry = "index.docker.io",
   repository = "dasch-swiss/knora-upgrade",
   tag = "{BUILD_SCM_TAG}"
)
