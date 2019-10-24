package(default_visibility = ["//visibility:public"])

load("@io_bazel_rules_docker//container:container.bzl", "container_image")

container_image(
    name = "app1",
    base = "@adoptopenjdk11//image",
    # cmd = [""],
    entrypoint = [
        "/webapi/bin/webapi",
    ],
    env = {
        "envVar": "My environment variable",
        "xyz": "321",
    },
    files = [
        "//extended/image_data:Data_file.txt",
        "//extended/image_data:More_data.txt",
        "//extended/image_data:file_to_copy.txt",
    ],
    labels = {
        "desc": "Description for version 7.7",
        "version": "7.7",
    },
    ports = [
        "8080/tcp",
        "9876/udp",
    ],
    symlinks = {
        "/usr/More_data.txt": "/More_data.txt",
    },
    tars = ["//extended/image_data:tarfile.tar"],
    volumes = [
        "/myVol1",
        "/usr/myVol2",
    ],
)

#
#scala_image(
#    name = "app1",
#    deps = ["//salsah1"],
##    layers = [
##        "//salsah1:lib",
##        "//salsah1:public"
##    ],
#    main_class = "org.knora.salsah.Main",
#)
#
#scala_image(
#    name = "api",
#    deps = ["//webapi"],
#    main_class = "org.knora.webapi.Main",
#)

