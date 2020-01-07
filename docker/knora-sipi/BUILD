package(default_visibility = ["//visibility:public"])

load("@io_bazel_rules_docker//container:container.bzl", "container_layer", "container_image")
load("@io_bazel_rules_docker//scala:image.bzl", "scala_image")
load("@debian_jessie_amd64_security//debs:deb_packages.bzl", "debian_jessie_amd64_security")

container_layer(
    name = "bash_layer",
    debs = [
        debian_jessie_amd64_security["bash"],
    ]
)

# FIXME: Use correct base image for Sipi
container_image(
    name = "knora_app1_base_image",
    base = "@openjdk11//image",
    # cmd = [""],
    entrypoint = [
        "/salsah1/app1",
    ],
    env = {
#        "LANG": "en_US.UTF-8",
#        "JAVA_OPTS": "-Dsun.jnu.encoding=UTF-8 -Dfile.encoding=UTF-8",
#        "KNORA_SALSAH1_DEPLOYED": "true",
#        "KNORA_SALSAH1_WORKDIR": "/salsah1"
    },
    directory = "salsah1",
    files = [
        "//salsah1:public_folder",
    ],
    labels = {
        "MAINTAINER": "400790+subotic@users.noreply.github.com",
    },
    ports = [
        "3335/tcp",
    ],
    layers = [":bash_layer"]
)

# create language specific image
# uses scala_binary underneath
scala_image(
    name = "knora-app1",
    deps = ["//salsah1:library"],
    main_class = "org.knora.salsah.Main",
    base = ":knora_app1_base_image",
)


