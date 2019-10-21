scala_binary(
    name = "app1",
    deps = [
        "//salsah1"
    ],
    data = [
        "//salsah1:lib",
        "//salsah1:public",
    ],
    main_class = "org.knora.salsah.Main"
)

scala_binary(
    name = "api",
    deps = [
        "//webapi"
    ],
    main_class = "org.knora.webapi.Main"
)
