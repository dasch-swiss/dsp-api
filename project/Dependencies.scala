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
  val ZioConfigVersion = "3.0.0-RC2"
  val ZioSchemaVersion = "0.2.0-RC1-1"
  val ZioLoggingVersion = "2.0.0-RC5"
  val ZioZmxVersion = "2.0.0-M1"
  val ZioPreludeVersion = "1.0.0-RC10"

  object Compile {

    // ZIO
    val zio = "dev.zio" %% "zio" % ZioVersion
    val zioPrelude = "dev.zio" %% "zio-prelude" % ZioPreludeVersion
    val zioTest = "dev.zio" %% "zio-test" % ZioVersion % Test
    val zioTestSbt = "dev.zio" %% "zio-test-sbt" % ZioVersion % Test

    // akka
    val akkaActor = "com.typesafe.akka" %% "akka-actor" % akkaVersion
    val akkaStream = "com.typesafe.akka" %% "akka-stream" % akkaVersion
    val akkaSlf4j = "com.typesafe.akka" %% "akka-slf4j" % akkaVersion

    // akka http
    val akkaHttp = "com.typesafe.akka" %% "akka-http" % akkaHttpVersion
    val akkaHttpSprayJson = "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion

    //CORS support
    val akkaHttpCors = "ch.megard" %% "akka-http-cors" % "1.0.0"

    // jena
    val jenaText = "org.apache.jena" % "jena-text" % jenaVersion

    // logging
    val scalaLogging = "com.typesafe.scala-logging" %% "scala-logging" % "3.9.4"
    val logbackClassic = "ch.qos.logback" % "logback-classic" % "1.2.10"

    // Metrics
    val kamonCore = "io.kamon" %% "kamon-core" % "2.1.5"
    val kamonScalaFuture = "io.kamon" %% "kamon-scala-future" % "2.1.5"
    val kamonAkkaHttpd = "io.kamon" %% "kamon-akka-http" % "2.1.5"
    val aspectJWeaver = "org.aspectj" % "aspectjweaver" % "1.9.4"

    // input validation
    val commonsValidator =
      "commons-validator" % "commons-validator" % "1.6" exclude ("commons-logging", "commons-logging")

    // authentication
    val springSecurityCore =
      "org.springframework.security" % "spring-security-core" % "5.1.5.RELEASE" exclude ("commons-logging", "commons-logging") exclude ("org.springframework", "spring-aop")
    val jwtSprayJson = "com.pauldijou" %% "jwt-spray-json" % "5.0.0"

    // caching
    val ehcache = "net.sf.ehcache" % "ehcache" % "2.10.9.2"
    val jedis = "redis.clients" % "jedis" % "3.1.0-m4"
    // serialization
    val chill = "com.twitter" %% "chill" % "0.9.5"

    // other
    val jodd = "org.jodd" % "jodd" % "3.2.6"
    val diff = "com.sksamuel.diff" % "diff" % "1.1.11"
    val xmlunitCore = "org.xmlunit" % "xmlunit-core" % "2.1.1"

    // other
    val scallop = "org.rogach" %% "scallop" % "3.5.1"
    val gwtServlet = "com.google.gwt" % "gwt-servlet" % "2.8.0"
    val saxonHE = "net.sf.saxon" % "Saxon-HE" % "9.9.0-2"

    // swagger (api documentation)
    val swaggerAkkaHttp = "com.github.swagger-akka-http" %% "swagger-akka-http" % "1.6.0"

    val icu4j = "com.ibm.icu" % "icu4j" % "62.1"

    // Graph for Scala            "org.scala-graph:graph-core_2.13:1.13.1",
    val scalaGraph = "org.scala-graph" %% "graph-core" % "1.13.1"

    // missing from current BAZEL setup
    val titaniumJSONLD = "com.apicatalog" % "titanium-json-ld" % "1.2.0"
    val jakartaJSON = "org.glassfish" % "jakarta.json" % "2.0.1"

    // those found out by plugin
    val jwtCore = "com.pauldijou" %% "jwt-core" % "5.0.0"
    val config = "com.typesafe" % "config" % "1.4.0"
    val akkaHttpCore = "com.typesafe.akka" %% "akka-http-core" % "10.2.8"
    val commonsIo = "commons-io" % "commons-io" % "2.11.0"
    val sprayJson = "io.spray" %% "spray-json" % "1.3.6"
    val swaggerAnnotations = "io.swagger" % "swagger-annotations" % "1.6.3"
    val swaggerModels = "io.swagger" % "swagger-models" % "1.6.3"
    val jsr311Api = "javax.ws.rs" % "jsr311-api" % "1.1.1"
    val commonsLang3 = "org.apache.commons" % "commons-lang3" % "3.12.0"
    val commonsPool2 = "org.apache.commons" % "commons-pool2" % "2.6.2"
    val commonsText = "org.apache.commons" % "commons-text" % "1.8"
    val httpClient =
      "org.apache.httpcomponents" % "httpclient" % "4.5.13" exclude ("commons-logging", "commons-logging")
    val httpCore = "org.apache.httpcomponents" % "httpcore" % "4.4.14"
    val jenaArq = "org.apache.jena" % "jena-arq" % "4.4.0"
    val jenaCore = "org.apache.jena" % "jena-core" % "4.4.0"
    val jenaShacl = "org.apache.jena" % "jena-shacl" % "4.4.0"
    val jenaTdb = "org.apache.jena" % "jena-tdb" % "4.4.0"
    val luceneCore = "org.apache.lucene" % "lucene-core" % "8.11.1"
    val rdf4jModel = "org.eclipse.rdf4j" % "rdf4j-model" % "3.4.4"
    val rdf4jQuery = "org.eclipse.rdf4j" % "rdf4j-query" % "3.4.4"
    val rdf4jQueryAlgebraModel = "org.eclipse.rdf4j" % "rdf4j-queryalgebra-model" % "3.4.4"
    val rdf4jQueryParserApi = "org.eclipse.rdf4j" % "rdf4j-queryparser-api" % "3.4.4"
    val rdf4jQueryParserSparql = "org.eclipse.rdf4j" % "rdf4j-queryparser-sparql" % "3.4.4"
    val rdf4jRepositoryApi = "org.eclipse.rdf4j" % "rdf4j-repository-api" % "3.4.4"
    val rdf4jRepositorySail = "org.eclipse.rdf4j" % "rdf4j-repository-sail" % "3.4.4"
    val rdf4jRioApi = "org.eclipse.rdf4j" % "rdf4j-rio-api" % "3.4.4"
    val rdf4jSailApi = "org.eclipse.rdf4j" % "rdf4j-sail-api" % "3.4.4"
    val rdf4jSailMemory = "org.eclipse.rdf4j" % "rdf4j-sail-memory" % "3.4.4"
    val rdf4jShacl = "org.eclipse.rdf4j" % "rdf4j-shacl" % "3.4.4"
    val rdf4jUtil = "org.eclipse.rdf4j" % "rdf4j-util" % "3.4.4"
    val scalaReflect = "org.scala-lang" % "scala-reflect" % "2.13.7"
    val scalaXml = "org.scala-lang.modules" %% "scala-xml" % "1.3.0"
    val slf4jApi = "org.slf4j" % "slf4j-api" % "1.7.35"
  }

  object WebapiTest {
    val akkaTestkit = "com.typesafe.akka" %% "akka-testkit" % akkaVersion % Test
    val akkaStreamTestkit = "com.typesafe.akka" %% "akka-stream-testkit" % akkaVersion % Test
    val akkaHttpTestkit = "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion % Test
    val scalaTest = "org.scalatest" %% "scalatest" % "3.2.2" % Test
    val gatlingHighcharts = "io.gatling.highcharts" % "gatling-charts-highcharts" % "3.7.5" % Test
    val gatlingTestFramework = "io.gatling" % "gatling-test-framework" % "3.2.1" % Test
    val testcontainers = "org.testcontainers" % "testcontainers" % "1.16.0" % Test
  }

  import Compile._

  val l = libraryDependencies

  // libraryDependencies ++= Seq[sbt.ModuleID](Compile.akkaActor, Compile.akkaHttp)

  val webapiLibraryDependencies = l ++= Seq[sbt.ModuleID](
    akkaActor,
    akkaHttp,
    akkaHttpCors,
    akkaHttpSprayJson,
    akkaSlf4j % Runtime,
    akkaStream,
    chill,
    commonsValidator,
    diff,
    ehcache,
    gwtServlet,
    icu4j,
    jedis,
    jenaText,
    jodd,
    jwtSprayJson,
    kamonCore,
    kamonScalaFuture,
    saxonHE,
    scalaGraph,
    scalaLogging,
    logbackClassic % Runtime,
    scallop,
    springSecurityCore,
    swaggerAkkaHttp,
    titaniumJSONLD,
    WebapiTest.akkaHttpTestkit,
    WebapiTest.akkaStreamTestkit,
    WebapiTest.akkaTestkit,
    WebapiTest.gatlingHighcharts,
    WebapiTest.gatlingTestFramework,
    WebapiTest.scalaTest,
    WebapiTest.testcontainers,
    xmlunitCore % Test,
    zio,
    zioPrelude,
    zioTest,
    zioTestSbt,
    jakartaJSON,
    // those found out by plugin
    jwtCore,
    config,
    akkaHttpCore,
    commonsIo,
    sprayJson,
    swaggerAnnotations,
    swaggerModels,
    jsr311Api,
    commonsLang3,
    commonsPool2,
    commonsText,
    httpClient,
    httpCore,
    jenaArq,
    jenaCore,
    jenaShacl,
    jenaTdb,
    luceneCore,
    rdf4jModel,
    rdf4jQuery,
    rdf4jQueryAlgebraModel,
    rdf4jQueryParserApi,
    rdf4jQueryParserSparql,
    rdf4jRepositoryApi,
    rdf4jRepositorySail,
    rdf4jRioApi,
    rdf4jSailApi,
    rdf4jSailMemory,
    rdf4jShacl,
    rdf4jUtil,
    scalaReflect,
    scalaXml,
    slf4jApi
  )
}
