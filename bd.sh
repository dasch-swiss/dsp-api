buildozer 'remove deps @maven//:ch_qos_logback_logback_core' //webapi:main_library
buildozer 'remove deps @maven//:ch_qos_logback_logback_classic' //webapi:main_library
buildozer 'remove deps @maven//:com_typesafe_akka_akka_slf4j_2_12' //webapi:main_library
buildozer 'remove deps @maven//:org_slf4j_slf4j_api' //webapi/src/it/scala/org/knora/webapi/e2e:VersionRouteITSpec
buildozer 'remove deps @maven//:com_typesafe_akka_akka_testkit_2_12' //webapi/src/it/scala/org/knora/webapi/e2e:VersionRouteITSpec
buildozer 'remove deps @maven//:com_typesafe_akka_akka_http_testkit_2_12' //webapi/src/it/scala/org/knora/webapi/e2e:VersionRouteITSpec
