package(default_visibility = ["//visibility:public"])


load("@io_bazel_rules_docker//container:container.bzl", "container_push", "container_image")



container_image(
    name = "knora-salsah1",
    base = "@openjdk11//image",
    cmd = ["java", "-jar", "/salsah1/salsah1_deploy.jar"],
    # entrypoint = ["java", "-jar", "webapi_deploy.jar"],
    env = {
        "LANG": "en_US.UTF-8",
        "JAVA_OPTS": "-Dsun.jnu.encoding=UTF-8 -Dfile.encoding=UTF-8",
        "KNORA_SALSAH1_DEPLOYED": "true",
        "KNORA_SALSAH1_WORKDIR": "/salsah1"
    },
    directory = "salsah1",
    files = [
        "//salsah1:salsah1_deploy.jar",

    ],
    tars = [
        "//salsah1:public_folder_tar"
    ],
    labels = {
        "MAINTAINER": "400790+subotic@users.noreply.github.com",
    },
    ports = [
        "3335/tcp",
    ],
)

container_push(
   name = "push",
   image = ":knora-salsah1",
   format = "Docker",
   registry = "docker.io",
   repository = "dasch-swiss/knora-salsah1",
   tag = "{BUILD_SCM_TAG}"
)
