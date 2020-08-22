""" Maven dependencies loaded into the workspace """

# docs for rules_jvm_external: https://github.com/bazelbuild/rules_jvm_external
load("@rules_jvm_external//:defs.bzl", "maven_install")
load("@rules_jvm_external//:specs.bzl", "maven")
load("//third_party:versions.bzl", "AKKA_HTTP_VERSION", "AKKA_VERSION", "JENA_VERSION", "SCALA_VERSION")

def dependencies():
    #
    # e.g., to reference use: @maven//com_typesafe_akka_akka_actor_2_12
    #
    # ATTENTION: Transitive dependencies need to be explicitly added
    # to query: bazel query @maven//:all --output=build
    # or: bazel query @maven//:all | sort
    #
    maven_install(
        artifacts = [
            # scala
            "org.scala-sbt:compiler-interface:1.2.1",
            "org.scala-sbt:util-interface:1.2.0",
            "org.scala-lang:scala-compiler:%s" % (SCALA_VERSION),
            "org.scala-lang:scala-library:%s" % (SCALA_VERSION),
            "org.scala-lang:scala-reflect:%s" % (SCALA_VERSION),
            "org.scala-sbt:compiler-bridge_2.12:1.3.4",
            "org.scala-lang.modules:scala-java8-compat_2.12:0.8.0",
            "org.scala-lang.modules:scala-xml_2.12:1.2.0",

            # akka
            "com.typesafe.akka:akka-actor_2.12:%s" % (AKKA_VERSION),
            "com.typesafe.akka:akka-stream_2.12:%s" % (AKKA_VERSION),
            "com.typesafe.akka:akka-slf4j_2.12:%s" % (AKKA_VERSION),

            # akka http
            "com.typesafe.akka:akka-http_2.12:%s" % (AKKA_HTTP_VERSION),
            "com.typesafe.akka:akka-http-xml_2.12:%s" % (AKKA_HTTP_VERSION),
            "com.typesafe.akka:akka-http-spray-json_2.12:%s" % (AKKA_HTTP_VERSION),
            "com.typesafe.akka:akka-http-jackson_2.12:%s" % (AKKA_HTTP_VERSION),
            "com.typesafe:config:1.3.3",

            # CORS support
            "ch.megard:akka-http-cors_2.12:1.0.0",

            # Jena
            "org.apache.jena:apache-jena-libs:%s" % (JENA_VERSION),
            maven.artifact(
                group = "org.apache.jena",
                artifact = "jena-text",
                version = JENA_VERSION,
                exclusions = [
                    "org.slf4j:slf4j-log4j12",
                ],
            ),

            # Logging
            "com.typesafe.scala-logging:scala-logging_2.12:3.8.0",
            "ch.qos.logback:logback-classic:1.2.3",
            "ch.qos.logback:logback-core:1.2.3",
            "org.slf4j:log4j-over-slf4j:1.7.29",
            "org.slf4j:jcl-over-slf4j:1.7.29",
            "org.slf4j:slf4j-api:1.7.29",

            # metrics
            "io.kamon:kamon-core_2.12:2.0.0-RC1",
            "io.kamon:kamon-scala-future_2.12:2.0.0-RC1",
            "io.kamon:kamon-akka-http_2.12:2.0.0-RC1",
            "io.kamon:kamon-prometheus_2.12:2.0.0-RC1",
            "io.kamon:kamon-logback_2.12:2.0.0-RC1",
            "org.aspectj:aspectjweaver:1.9.4",

            # input validation
            "commons-validator:commons-validator:1.6",

            # authentication
            "org.bouncycastle:bcprov-jdk15on:1.59",
            "org.springframework.security:spring-security-core:5.1.5.RELEASE",
            "com.pauldijou:jwt-spray-json_2.12:0.19.0",

            # caching
            "net.sf.ehcache:ehcache:2.10.3",
            "redis.clients:jedis:3.1.0-m4",

            # serialization (used in caching)
            "com.twitter:chill_2.12:0.9.3",

            # other
            # "javax.transaction" % "transaction-api" % "1.1-rev-1",
            "org.apache.commons:commons-text:1.6",
            "commons-io:commons-io:2.6",
            "commons-beanutils:commons-beanutils:1.9.3",  # not used by us, but need newest version to prevent this problem: http://stackoverflow.com/questions/14402745/duplicate-classes-in-commons-collections-and-commons-beanutils
            "org.jodd:jodd:3.2.6",
            "joda-time:joda-time:2.9.1",
            "org.joda:joda-convert:1.8",
            "com.sksamuel.diff:diff:1.1.11",
            "org.xmlunit:xmlunit-core:2.1.1",

            # other
            "org.eclipse.rdf4j:rdf4j-runtime:3.0.0",
            "org.rogach:scallop_2.12:3.2.0",
            "com.google.gwt:gwt-servlet:2.8.0",
            "net.sf.saxon:Saxon-HE:9.9.0-2",
            "com.jsuereth:scala-arm_2.12:2.0",

            # provides akka jackson (json) support
            "de.heikoseeberger:akka-http-circe_2.12:1.21.0",
            "com.fasterxml.jackson.module:jackson-module-scala_2.12:2.9.4",
            "com.github.jsonld-java:jsonld-java:0.12.0",
            "com.apicatalog:titanium-json-ld:0.8.3",

            # swagger (api documentation)
            "com.github.swagger-akka-http:swagger-akka-http_2.12:0.14.0",

            # Java EE modules which are deprecated in Java SE 9, 10 and will be removed in Java SE 11
            "javax.xml.bind:jaxb-api:2.2.12",
            "com.ibm.icu:icu4j:62.1",
            "org.apache.httpcomponents:httpclient:4.5.6",

            # Twirl templates
            "com.typesafe.play:twirl-api_2.12:1.3.13",

            # testing
            "com.typesafe.akka:akka-testkit_2.12:%s" % (AKKA_VERSION),
            "com.typesafe.akka:akka-stream-testkit_2.12:%s" % (AKKA_VERSION),
            "com.typesafe.akka:akka-http-testkit_2.12:%s" % (AKKA_HTTP_VERSION),
            "org.scalatest:scalatest_2.12:3.1.2",
            "org.testcontainers:testcontainers:1.15.0-rc1",
            "junit:junit:4.13",
            "io.gatling.highcharts:gatling-charts-highcharts:3.2.1",
            "io.gatling:gatling-test-framework:3.2.1",

            # Additional Selenium libraries besides the ones pulled in during init
            # of io_bazel_rules_webtesting
            "org.seleniumhq.selenium:selenium-support:3.141.59",
        ],
        repositories = [
            "https://repo.maven.apache.org/maven2",
            "https://maven-central.storage-download.googleapis.com/maven2",
            "https://mirror.bazel.build/repo1.maven.org/maven2",
            "https://jcenter.bintray.com",
            "https://dl.bintray.com/typesafe/maven-releases/",
            "https://maven.google.com",
            "https://repo1.maven.org/maven2",
            "https://central.maven.org/maven2",
        ],
    )

ALL_WEBAPI_MAIN_DEPENDENCIES = [
    "//tools/version_info",
    "//webapi/src/main/scala/org/knora/webapi",
    "//webapi/src/main/scala/org/knora/webapi/app",
    "//webapi/src/main/scala/org/knora/webapi/core",
    "//webapi/src/main/scala/org/knora/webapi/exceptions",
    "//webapi/src/main/scala/org/knora/webapi/http/handler",
    "//webapi/src/main/scala/org/knora/webapi/http/version",
    "//webapi/src/main/scala/org/knora/webapi/instrumentation",
    "//webapi/src/main/scala/org/knora/webapi/messages",
    "//webapi/src/main/scala/org/knora/webapi/responders",
    "//webapi/src/main/scala/org/knora/webapi/routing",
    "//webapi/src/main/scala/org/knora/webapi/settings",
    "//webapi/src/main/scala/org/knora/webapi/sharedtestdata",
    "//webapi/src/main/scala/org/knora/webapi/store",
    "//webapi/src/main/scala/org/knora/webapi/util",
    "//webapi/src/main/scala/org/knora/webapi/util/cache",
    "//webapi/src/test/scala/org/knora/webapi",
    "//webapi/src/test/scala/org/knora/webapi/sharedtestdata",
    "//webapi/src/test/scala/org/knora/webapi/util",
]

BASE_TEST_DEPENDENCIES = [
    "@maven//:com_typesafe_scala_logging_scala_logging_2_12",
    "@maven//:org_slf4j_slf4j_api",
    "@maven//:com_typesafe_akka_akka_actor_2_12",
    "@maven//:com_typesafe_akka_akka_testkit_2_12",
    "@maven//:com_typesafe_akka_akka_http_2_12",
    "@maven//:com_typesafe_akka_akka_http_core_2_12",
    "@maven//:com_typesafe_akka_akka_http_testkit_2_12",
    "@maven//:com_typesafe_akka_akka_stream_2_12",
    "@maven//:com_typesafe_config",
    "@maven//:org_scalatest_scalatest_2_12",
    "@maven//:org_scalactic_scalactic_2_12",
]

BASE_TEST_DEPENDENCIES_WITH_JSON = BASE_TEST_DEPENDENCIES + [
    "@maven//:com_typesafe_akka_akka_http_spray_json_2_12",
    "@maven//:com_typesafe_akka_akka_http_xml_2_12",
    "@maven//:io_spray_spray_json_2_12",
]

BASE_TEST_DEPENDENCIES_WITH_JSON_LD = BASE_TEST_DEPENDENCIES + [
    "@maven//:com_fasterxml_jackson_core_jackson_core",
    "@maven//:com_github_jsonld_java_jsonld_java",
    "@maven//:com_apicatalog_titanium_json_ld",
]

# can be copied to a new test target
ALL_TEST_DEPENDENCIES = [
    "//tools/version_info",
    "//webapi/src/main/scala/org/knora/webapi",
    "//webapi/src/main/scala/org/knora/webapi/app",
    "//webapi/src/main/scala/org/knora/webapi/core",
    "//webapi/src/main/scala/org/knora/webapi/exceptions",
    "//webapi/src/main/scala/org/knora/webapi/http/handler",
    "//webapi/src/main/scala/org/knora/webapi/http/version",
    "//webapi/src/main/scala/org/knora/webapi/instrumentation",
    "//webapi/src/main/scala/org/knora/webapi/messages",
    "//webapi/src/main/scala/org/knora/webapi/responders",
    "//webapi/src/main/scala/org/knora/webapi/routing",
    "//webapi/src/main/scala/org/knora/webapi/settings",
    "//webapi/src/main/scala/org/knora/webapi/sharedtestdata",
    "//webapi/src/main/scala/org/knora/webapi/store",
    "//webapi/src/main/scala/org/knora/webapi/util",
    "//webapi/src/main/scala/org/knora/webapi/util/cache",
    "//webapi/src/test/scala/org/knora/webapi",
    "//webapi/src/test/scala/org/knora/webapi/sharedtestdata",
    "//webapi/src/test/scala/org/knora/webapi/util",
    "//webapi/src/test/scala/org/knora/webapi/responders",
    "//webapi/src/test/scala/org/knora/webapi/store",
    "//webapi/src/test/scala/org/knora/webapi/store/iiif",
    "@maven//:com_typesafe_scala_logging_scala_logging_2_12",
    "@maven//:org_slf4j_slf4j_api",
    "@maven//:com_typesafe_akka_akka_actor_2_12",
    "@maven//:com_typesafe_akka_akka_testkit_2_12",
    "@maven//:com_typesafe_akka_akka_http_2_12",
    "@maven//:com_typesafe_akka_akka_http_core_2_12",
    "@maven//:com_typesafe_akka_akka_http_testkit_2_12",
    "@maven//:com_typesafe_akka_akka_stream_2_12",
    "@maven//:com_typesafe_config",
    "@maven//:org_scalatest_scalatest_2_12",
    "@maven//:org_scalactic_scalactic_2_12",
    "@maven//:com_typesafe_akka_akka_http_spray_json_2_12",
    "@maven//:com_typesafe_akka_akka_http_xml_2_12",
    "@maven//:io_spray_spray_json_2_12",
    "@maven//:com_fasterxml_jackson_core_jackson_core",
    "@maven//:com_github_jsonld_java_jsonld_java",
    "@maven//:com_apicatalog_titanium_json_ld",
    "@maven//:org_xmlunit_xmlunit_core",
]
