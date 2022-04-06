/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora

import sbt.Keys._
import sbt.{Def, _}

object Dependencies {
  // core deps versions
  val scalaVersion    = "2.13.7"
  val akkaVersion     = "2.6.18"
  val akkaHttpVersion = "10.2.8"
  val jenaVersion     = "4.4.0"
  val metricsVersion  = "4.0.1"
  val sipiImage       = "daschswiss/sipi:3.3.4"               // base image the knora-sipi image is created from
  val fusekiImage     = "daschswiss/apache-jena-fuseki:2.0.8" // should be the same version as in docker-compose.yml

  val ZioVersion        = "2.0.0-RC3"
  val ZioHttpVersion    = "2.0.0-RC4"
  val ZioJsonVersion    = "0.3.0-RC3"
  val ZioConfigVersion  = "3.0.0-RC3"
  val ZioSchemaVersion  = "0.2.0-RC1-1"
  val ZioLoggingVersion = "2.0.0-RC6"
  val ZioZmxVersion     = "2.0.0-RC4"
  val ZioPreludeVersion = "1.0.0-RC10"

  // ZIO
  val zio               = "dev.zio" %% "zio"                 % ZioVersion
  val zioHttp           = "io.d11"  %% "zhttp"               % ZioHttpVersion
  val zioJson           = "dev.zio" %% "zio-json"            % ZioJsonVersion
  val zioPrelude        = "dev.zio" %% "zio-prelude"         % ZioPreludeVersion
  val zioLoggingSlf4j   = "dev.zio" %% "zio-logging-slf4j"   % ZioLoggingVersion
  val zioConfig         = "dev.zio" %% "zio-config"          % ZioConfigVersion
  val zioConfigMagnolia = "dev.zio" %% "zio-config-magnolia" % ZioConfigVersion
  val zioConfigTypesafe = "dev.zio" %% "zio-config-typesafe" % ZioConfigVersion
  val zioTest           = "dev.zio" %% "zio-test"            % ZioVersion % Test
  val zioTestSbt        = "dev.zio" %% "zio-test-sbt"        % ZioVersion % Test

  // akka
  val akkaActor         = "com.typesafe.akka" %% "akka-actor"           % akkaVersion
  val akkaStream        = "com.typesafe.akka" %% "akka-stream"          % akkaVersion
  val akkaSlf4j         = "com.typesafe.akka" %% "akka-slf4j"           % akkaVersion
  val akkaHttp          = "com.typesafe.akka" %% "akka-http"            % akkaHttpVersion
  val akkaHttpSprayJson = "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion

  // CORS support
  val akkaHttpCors = "ch.megard" %% "akka-http-cors" % "1.0.0"

  // jena
  val jenaText = "org.apache.jena" % "jena-text" % jenaVersion

  // logging
  val scalaLogging   = "com.typesafe.scala-logging" %% "scala-logging"    % "3.9.4"
  val logbackClassic = "ch.qos.logback"              % "logback-classic"  % "1.2.10"
  val log4jOverSlf4j = "org.slf4j"                   % "log4j-over-slf4j" % "1.7.32"
  val jclOverSlf4j   = "org.slf4j"                   % "jcl-over-slf4j"   % "1.7.32"

  // Metrics
  val kamonCore        = "io.kamon"   %% "kamon-core"         % "2.1.5"
  val kamonScalaFuture = "io.kamon"   %% "kamon-scala-future" % "2.1.5"
  val kamonAkkaHttpd   = "io.kamon"   %% "kamon-akka-http"    % "2.1.5"
  val aspectJWeaver    = "org.aspectj" % "aspectjweaver"      % "1.9.4"

  // input validation
  val commonsValidator =
    "commons-validator" % "commons-validator" % "1.6" exclude ("commons-logging", "commons-logging")

  // authentication
  val springSecurityCore =
    "org.springframework.security" % "spring-security-core" % "5.1.5.RELEASE" exclude ("commons-logging", "commons-logging") exclude ("org.springframework", "spring-aop")
  val jwtSprayJson = "com.pauldijou" %% "jwt-spray-json" % "5.0.0"

  // caching
  val ehcache = "net.sf.ehcache" % "ehcache" % "2.10.9.2"
  val jedis   = "redis.clients"  % "jedis"   % "4.2.0"

  // other
  val jodd        = "org.jodd"          % "jodd"         % "3.2.6"
  val diff        = "com.sksamuel.diff" % "diff"         % "1.1.11"
  val xmlunitCore = "org.xmlunit"       % "xmlunit-core" % "2.1.1"
  val rdf4jClient = "org.eclipse.rdf4j" % "rdf4j-client" % "3.4.4"
  val scallop     = "org.rogach"       %% "scallop"      % "3.5.1"
  val gwtServlet  = "com.google.gwt"    % "gwt-servlet"  % "2.8.0"
  val saxonHE     = "net.sf.saxon"      % "Saxon-HE"     % "9.9.0-2"
  val icu4j       = "com.ibm.icu"       % "icu4j"        % "62.1"

  // swagger (api documentation)
  val swaggerAkkaHttp = "com.github.swagger-akka-http" %% "swagger-akka-http" % "1.6.0"

  // Graph for Scala
  val scalaGraph = "org.scala-graph" %% "graph-core" % "1.13.1"

  // test
  val akkaTestkit          = "com.typesafe.akka"    %% "akka-testkit"              % akkaVersion
  val akkaStreamTestkit    = "com.typesafe.akka"    %% "akka-stream-testkit"       % akkaVersion
  val akkaHttpTestkit      = "com.typesafe.akka"    %% "akka-http-testkit"         % akkaHttpVersion
  val scalaTest            = "org.scalatest"        %% "scalatest"                 % "3.2.2"
  val gatlingHighcharts    = "io.gatling.highcharts" % "gatling-charts-highcharts" % "3.7.5"
  val gatlingTestFramework = "io.gatling"            % "gatling-test-framework"    % "3.2.1"
  val testcontainers       = "org.testcontainers"    % "testcontainers"            % "1.16.0"

  // missing from current BAZEL setup
  val titaniumJSONLD = "com.apicatalog" % "titanium-json-ld" % "1.2.0"
  val jakartaJSON    = "org.glassfish"  % "jakarta.json"     % "2.0.1"

  // found by plugin
  val jwtCore            = "com.pauldijou"     %% "jwt-core"            % "5.0.0"
  val config             = "com.typesafe"       % "config"              % "1.4.0"
  val akkaHttpCore       = "com.typesafe.akka" %% "akka-http-core"      % "10.2.8"
  val commonsIo          = "commons-io"         % "commons-io"          % "2.11.0"
  val sprayJson          = "io.spray"          %% "spray-json"          % "1.3.6"
  val swaggerAnnotations = "io.swagger"         % "swagger-annotations" % "1.6.3"
  val swaggerModels      = "io.swagger"         % "swagger-models"      % "1.6.3"
  val jsr311Api          = "javax.ws.rs"        % "jsr311-api"          % "1.1.1"
  val commonsLang3       = "org.apache.commons" % "commons-lang3"       % "3.12.0"
  val commonsPool2       = "org.apache.commons" % "commons-pool2"       % "2.6.2"
  val commonsText        = "org.apache.commons" % "commons-text"        % "1.8"
  val httpClient =
    "org.apache.httpcomponents" % "httpclient" % "4.5.13" exclude ("commons-logging", "commons-logging")
  val httpCore               = "org.apache.httpcomponents" % "httpcore"                 % "4.4.14"
  val jenaArq                = "org.apache.jena"           % "jena-arq"                 % "4.4.0"
  val jenaCore               = "org.apache.jena"           % "jena-core"                % "4.4.0"
  val jenaShacl              = "org.apache.jena"           % "jena-shacl"               % "4.4.0"
  val jenaTdb                = "org.apache.jena"           % "jena-tdb"                 % "4.4.0"
  val luceneCore             = "org.apache.lucene"         % "lucene-core"              % "8.11.1"
  val rdf4jModel             = "org.eclipse.rdf4j"         % "rdf4j-model"              % "3.4.4"
  val rdf4jQuery             = "org.eclipse.rdf4j"         % "rdf4j-query"              % "3.4.4"
  val rdf4jQueryAlgebraModel = "org.eclipse.rdf4j"         % "rdf4j-queryalgebra-model" % "3.4.4"
  val rdf4jQueryParserApi    = "org.eclipse.rdf4j"         % "rdf4j-queryparser-api"    % "3.4.4"
  val rdf4jQueryParserSparql = "org.eclipse.rdf4j"         % "rdf4j-queryparser-sparql" % "3.4.4"
  val rdf4jRepositoryApi     = "org.eclipse.rdf4j"         % "rdf4j-repository-api"     % "3.4.4"
  val rdf4jRepositorySail    = "org.eclipse.rdf4j"         % "rdf4j-repository-sail"    % "3.4.4"
  val rdf4jRioApi            = "org.eclipse.rdf4j"         % "rdf4j-rio-api"            % "3.4.4"
  val rdf4jSailApi           = "org.eclipse.rdf4j"         % "rdf4j-sail-api"           % "3.4.4"
  val rdf4jSailMemory        = "org.eclipse.rdf4j"         % "rdf4j-sail-memory"        % "3.4.4"
  val rdf4jShacl             = "org.eclipse.rdf4j"         % "rdf4j-shacl"              % "3.4.4"
  val rdf4jUtil              = "org.eclipse.rdf4j"         % "rdf4j-util"               % "3.4.4"
  val scalaReflect           = "org.scala-lang"            % "scala-reflect"            % "2.13.7"
  val scalaXml               = "org.scala-lang.modules"   %% "scala-xml"                % "1.3.0"
  val slf4jApi               = "org.slf4j"                 % "slf4j-api"                % "1.7.35"

  val webapiLibraryDependencies = Seq(
    akkaActor,
    akkaHttp,
    akkaHttpCors,
    akkaHttpSprayJson,
    akkaSlf4j % Runtime,
    akkaStream,
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
    akkaHttpTestkit      % Test,
    akkaStreamTestkit    % Test,
    akkaTestkit          % Test,
    gatlingHighcharts    % Test,
    gatlingTestFramework % Test,
    scalaTest            % Test,
    testcontainers       % Test,
    xmlunitCore          % Test,
    zio,
    zioHttp,
    zioJson,
    zioLoggingSlf4j,
    zioPrelude,
    zioConfig,
    zioConfigMagnolia,
    zioConfigTypesafe,
    zioTest,
    zioTestSbt,
    log4jOverSlf4j,
    jclOverSlf4j,
    slf4jApi,
    jakartaJSON,
    rdf4jClient % Test,
    // found by plugin - to check if are actually needed
    // jwtCore,
    // config,
    // akkaHttpCore,
    // commonsIo,//
    // sprayJson,
    // swaggerAnnotations,
    // swaggerModels,
    // jsr311Api,
    // commonsLang3,
    // commonsPool2,
    // commonsText,//
    // httpClient,//
    // httpCore,
    // jenaArq,
    // jenaCore,
    // jenaShacl,
    // jenaTdb,
    // luceneCore,
    // rdf4jModel,
    // rdf4jQuery,
    // rdf4jQueryAlgebraModel,
    // rdf4jQueryParserApi,
    // rdf4jQueryParserSparql,
    // rdf4jRepositoryApi,
    // rdf4jRepositorySail,
    // rdf4jRioApi,
    // rdf4jSailApi,
    // rdf4jSailMemory,
    rdf4jShacl
    // rdf4jUtil,
    // scalaReflect,
    // scalaXml,//
    // slf4jApi
  )

  val dspApiMainLibraryDependencies = Seq(
    zio
  )

  val schemaApiLibraryDependencies = Seq(
    zioHttp
  )

  val schemaCoreLibraryDependencies = Seq(
    zioPrelude
  )

  val schemaRepoLibraryDependencies                  = Seq()
  val schemaRepoEventStoreServiceLibraryDependencies = Seq()
  val schemaRepoSearchServiceLibraryDependencies     = Seq()
}
