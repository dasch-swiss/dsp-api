/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora

import sbt.Keys._
import sbt.{Def, _}

object Dependencies {

  val scalaVersion = "2.13.7"
  val akkaVersion = "2.6.18"
  val akkaHttpVersion = "10.2.8"
  val jenaVersion = "4.4.0"
  val metricsVersion = "4.0.1"
  val sipiImage = "daschswiss/sipi:3.3.4" // base image the knora-sipi image is created from
  val fusekiImage = "daschswiss/apache-jena-fuseki:2.0.8" // should be the same version as in docker-compose.yml

  val ZioVersion = "2.0.0-RC2"
  val ZioHttpVersion = "2.0.0-RC3"
  val ZioJsonVersion = "0.3.0-RC3"
  val ZioConfigVersion = "3.0.0-RC2"
  val ZioSchemaVersion = "0.2.0-RC1-1"
  val ZioLoggingVersion = "2.0.0-RC5"
  val ZioZmxVersion = "2.0.0-M1"
  val ZioPreludeVersion = "1.0.0-RC10"

  object Compile {

    // ZIO
    val zio = "dev.zio" %% "zio" % ZioVersion
    val zioHttp = "io.d11" %% "zhttp" % ZioHttpVersion
    val zioJson = "dev.zio" %% "zio-json" % ZioJsonVersion
    val zioPrelude = "dev.zio" %% "zio-prelude" % ZioPreludeVersion
    val zioTest = "dev.zio" %% "zio-test" % ZioVersion % Test
    val zioTestSbt = "dev.zio" %% "zio-test-sbt" % ZioVersion % Test

    // akka
    val akkaActor = "com.typesafe.akka" %% "akka-actor" % akkaVersion
    val akkaStream = "com.typesafe.akka" %% "akka-stream" % akkaVersion
    val akkaSlf4j = "com.typesafe.akka" %% "akka-slf4j" % akkaVersion
    val akkaProtobufV3 = "com.typesafe.akka" %% "akka-protobuf-v3" % akkaVersion

    // akka http
    val akkaHttp = "com.typesafe.akka" %% "akka-http" % akkaHttpVersion
    val akkaHttpXml = "com.typesafe.akka" %% "akka-http-xml" % akkaHttpVersion
    val akkaHttpSprayJson = "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion
    val akkaHttpJacksonJava = "com.typesafe.akka" %% "akka-http-jackson" % akkaHttpVersion

    val typesafeConfig = "com.typesafe" % "config" % "1.3.3"

    //CORS support
    val akkaHttpCors = "ch.megard" %% "akka-http-cors" % "1.0.0"

    // jena
    val jenaLibs = "org.apache.jena" % "apache-jena-libs" % jenaVersion
    val jenaText = "org.apache.jena" % "jena-text" % jenaVersion

    // logging
    val scalaLogging = "com.typesafe.scala-logging" %% "scala-logging" % "3.9.4"
    val logbackClassic = "ch.qos.logback" % "logback-classic" % "1.2.10"

    // Metrics
    val kamonCore = "io.kamon" %% "kamon-core" % "2.1.5"
    val kamonScalaFuture = "io.kamon" %% "kamon-scala-future" % "2.1.5"
    val kamonAkkaHttpd = "io.kamon" %% "kamon-akka-http" % "2.1.5"
    val kamonPrometheus = "io.kamon" %% "kamon-prometheus" % "2.1.5"
    val kamonLogback = "io.kamon" %% "kamon-logback" % "2.1.5"
    val aspectJWeaver = "org.aspectj" % "aspectjweaver" % "1.9.4"

    // input validation
    val commonsValidator =
      "commons-validator" % "commons-validator" % "1.6" exclude ("commons-logging", "commons-logging")

    // authentication
    val bcprov = "org.bouncycastle" % "bcprov-jdk15on" % "1.64"
    val springSecurityCore =
      "org.springframework.security" % "spring-security-core" % "5.1.5.RELEASE" exclude ("commons-logging", "commons-logging") exclude ("org.springframework", "spring-aop")
    val jwtSprayJson = "com.pauldijou" %% "jwt-spray-json" % "5.0.0"

    // caching
    val ehcache = "net.sf.ehcache" % "ehcache" % "2.10.9.2"
    val jedis = "redis.clients" % "jedis" % "3.1.0-m4"
    // serialization
    val chill = "com.twitter" %% "chill" % "0.9.5"

    // other
    //"javax.transaction" % "transaction-api" % "1.1-rev-1",
    val commonsText = "org.apache.commons" % "commons-text" % "1.6"
    val commonsIo = "commons-io" % "commons-io" % "2.6"
    val commonsBeanUtil =
      "commons-beanutils" % "commons-beanutils" % "1.9.3" exclude ("commons-logging", "commons-logging") // not used by us, but need newest version to prevent this problem: http://stackoverflow.com/questions/14402745/duplicate-classes-in-commons-collections-and-commons-beanutils
    val jodd = "org.jodd" % "jodd" % "3.2.6"
    val jodaTime = "joda-time" % "joda-time" % "2.9.1"
    val jodaConvert = "org.joda" % "joda-convert" % "1.8"
    val diff = "com.sksamuel.diff" % "diff" % "1.1.11"
    val xmlunitCore = "org.xmlunit" % "xmlunit-core" % "2.1.1"

    // other
    val rdf4jClient = "org.eclipse.rdf4j" % "rdf4j-client" % "3.4.4"
    val rdf4jRuntime = "org.eclipse.rdf4j" % "rdf4j-runtime" % "3.4.4"
    val rdf4jStorage = "org.eclipse.rdf4j" % "rdf4j-storage" % "3.4.4"
    val scallop = "org.rogach" %% "scallop" % "3.5.1"
    val gwtServlet = "com.google.gwt" % "gwt-servlet" % "2.8.0"
    val saxonHE = "net.sf.saxon" % "Saxon-HE" % "9.9.0-2"

    val scalaXml = "org.scala-lang.modules" %% "scala-xml" % "1.2.0"

    // provides akka jackson (json) support
    val akkaHttpCirce = "de.heikoseeberger" %% "akka-http-circe" % "1.36.0"
    val jacksonScala = "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.12.3"

    // swagger (api documentation)
    val swaggerAkkaHttp = "com.github.swagger-akka-http" %% "swagger-akka-http" % "1.6.0"

    // Java EE modules which are deprecated in Java SE 9, 10 and will be removed in Java SE 11
    val jaxbApi = "javax.xml.bind" % "jaxb-api" % "2.2.12"

    val icu4j = "com.ibm.icu" % "icu4j" % "62.1"

    val apacheHttpClient =
      "org.apache.httpcomponents" % "httpclient" % "4.5.6" exclude ("commons-logging", "commons-logging")

    // Graph for Scala            "org.scala-graph:graph-core_2.13:1.13.1",
    val scalaGraph = "org.scala-graph" %% "graph-core" % "1.13.1"

    // missing from current BAZEL setup
    // "ch.qos.logback:logback-core:1.2.9",
    val logbackCore = "ch.qos.logback" % "logback-core" % "1.2.9"
    // "org.slf4j:log4j-over-slf4j:1.7.32",
    val log4jOverSlf4j = "org.slf4j" % "log4j-over-slf4j" % "1.7.32"
    // "org.slf4j:jcl-over-slf4j:1.7.32",
    val jclOverSlf4j = "org.slf4j" % "jcl-over-slf4j" % "1.7.32"
    // "org.slf4j:slf4j-api:1.7.32",
    val slf4jApi = "org.slf4j" % "slf4j-api" % "1.7.32"
    // "com.apicatalog:titanium-json-ld:1.2.0",
    val titaniumJSONLD = "com.apicatalog" % "titanium-json-ld" % "1.2.0"
    // "org.glassfish:jakarta.json:2.0.1",
    val jakartaJSON = "org.glassfish" % "jakarta.json" % "2.0.1"
    // "com.typesafe.play:twirl-api_2.13:1.5.1",
    val twirlApi = "com.typesafe.play" %% "twirl-api" % "1.5.1"
    // "junit:junit:4.13.2",
    val junit = "junit" % "junit" % "4.13.2"
    // "org.seleniumhq.selenium:selenium-support:3.141.59",
    val seleniumSupport = "org.seleniumhq.selenium" % "selenium-support" % "3.141.59"
  }

  object WebapiTest {
    val akkaTestkit = "com.typesafe.akka" %% "akka-testkit" % akkaVersion % "test"
    val akkaStreamTestkit = "com.typesafe.akka" %% "akka-stream-testkit" % akkaVersion % "test"
    val akkaHttpTestkit = "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion % "test"
    val scalaTest = "org.scalatest" %% "scalatest" % "3.2.2" % "test"
    val gatlingHighcharts = "io.gatling.highcharts" % "gatling-charts-highcharts" % "3.7.5" % "test"
    val gatlingTestFramework = "io.gatling" % "gatling-test-framework" % "3.2.1" % "test"
    val testcontainers = "org.testcontainers" % "testcontainers" % "1.16.0" % "test"
  }

  object TestBinaries {
    val akkaTestkit = "com.typesafe.akka" %% "akka-testkit" % akkaVersion
    val akkaStreamTestkit = "com.typesafe.akka" %% "akka-stream-testkit" % akkaVersion
    val akkaHttpTestkit = "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion
    val scalaTest = "org.scalatest" %% "scalatest" % "3.2.2"
    val gatlingHighcharts = "io.gatling.highcharts" % "gatling-charts-highcharts" % "3.7.5"
    val gatlingTestFramework = "io.gatling" % "gatling-test-framework" % "3.2.1"
  }

  import Compile._

  val l = libraryDependencies

  val webapiLibraryDependencies = l ++= Seq[sbt.ModuleID](
    akkaActor,
    akkaHttp,
    akkaHttpCirce,
    akkaHttpCors,
    akkaHttpJacksonJava,
    akkaHttpSprayJson,
    akkaHttpXml,
    akkaProtobufV3,
    akkaSlf4j,
    akkaStream,
    apacheHttpClient,
    bcprov,
    chill,
    commonsBeanUtil,
    commonsIo,
    commonsText,
    commonsValidator,
    diff,
    ehcache,
    gwtServlet,
    icu4j,
    jacksonScala,
    jaxbApi,
    jedis,
    jenaLibs,
    jenaText,
    jodaConvert,
    jodaTime,
    jodd,
    jwtSprayJson,
    kamonCore,
    kamonLogback,
    kamonPrometheus,
    kamonScalaFuture,
    logbackCore,
    logbackClassic,
    rdf4jClient,
    rdf4jRuntime,
    rdf4jStorage,
    saxonHE,
    scalaGraph,
    scalaLogging,
    scalaXml,
    scallop,
    springSecurityCore,
    swaggerAkkaHttp,
    titaniumJSONLD,
    typesafeConfig,
    WebapiTest.akkaHttpTestkit,
    WebapiTest.akkaStreamTestkit,
    WebapiTest.akkaTestkit,
    WebapiTest.gatlingHighcharts,
    WebapiTest.gatlingTestFramework,
    WebapiTest.scalaTest,
    WebapiTest.testcontainers,
    xmlunitCore,
    zio,
    zioHttp,
    zioJson,
    zioPrelude,
    zioTest,
    zioTestSbt,
    log4jOverSlf4j,
    jclOverSlf4j,
    slf4jApi,
    jakartaJSON,
    twirlApi,
    junit,
    seleniumSupport
  )

  val upgradeLibraryDependencies = l ++= Seq[sbt.ModuleID](
    rdf4jRuntime,
    scallop
  )

  val webapiTestAndITLibraryDependencies = l ++= Seq[sbt.ModuleID](
    TestBinaries.gatlingHighcharts,
    TestBinaries.gatlingTestFramework,
    TestBinaries.scalaTest
  )
}
