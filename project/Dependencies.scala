/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora

import sbt._

object Dependencies {

  val fusekiImage =
    "daschswiss/apache-jena-fuseki:2.0.11" // should be the same version as in docker-compose.yml, also make sure to use the same version when deploying it (i.e. version in ops-deploy)!
  val sipiImage = "daschswiss/sipi:3.8.1" // base image the knora-sipi image is created from

  val ScalaVersion = "2.13.10"

  val AkkaActorVersion = "2.6.20"
  val AkkaHttpVersion  = "10.2.10"
  val JenaVersion      = "4.8.0"

  val ZioConfigVersion            = "3.0.7"
  val ZioHttpVersionOld           = "2.0.0-RC11"
  val ZioHttpVersion              = "0.0.3"
  val ZioJsonVersion              = "0.5.0"
  val ZioLoggingVersion           = "2.1.12"
  val ZioNioVersion               = "2.0.1"
  val ZioMetricsConnectorsVersion = "2.0.8"
  val ZioPreludeVersion           = "1.0.0-RC19"
  val ZioSchemaVersion            = "0.2.0"
  val ZioVersion                  = "2.0.13"

  // ZIO - all Scala 3 compatible
  val zio                   = "dev.zio" %% "zio"                       % ZioVersion
  val zioConfig             = "dev.zio" %% "zio-config"                % ZioConfigVersion
  val zioConfigMagnolia     = "dev.zio" %% "zio-config-magnolia"       % ZioConfigVersion
  val zioConfigTypesafe     = "dev.zio" %% "zio-config-typesafe"       % ZioConfigVersion
  val zioHttpOld            = "io.d11"  %% "zhttp"                     % ZioHttpVersionOld
  val zioHttp               = "dev.zio" %% "zio-http"                  % ZioHttpVersion
  val zioJson               = "dev.zio" %% "zio-json"                  % ZioJsonVersion
  val zioLogging            = "dev.zio" %% "zio-logging"               % ZioLoggingVersion
  val zioLoggingSlf4jBridge = "dev.zio" %% "zio-logging-slf4j2-bridge" % ZioLoggingVersion
  val zioNio                = "dev.zio" %% "zio-nio"                   % ZioNioVersion
  val zioMacros             = "dev.zio" %% "zio-macros"                % ZioVersion
  val zioMetricsConnectors  = "dev.zio" %% "zio-metrics-connectors"    % ZioMetricsConnectorsVersion
  val zioPrelude            = "dev.zio" %% "zio-prelude"               % ZioPreludeVersion

  // zio-test and friends
  val zioTest    = "dev.zio" %% "zio-test"     % ZioVersion
  val zioTestSbt = "dev.zio" %% "zio-test-sbt" % ZioVersion
  val zioMock    = "dev.zio" %% "zio-mock"     % "1.0.0-RC11"

  // akka
  val akkaActor         = "com.typesafe.akka" %% "akka-actor"           % AkkaActorVersion // Scala 3 compatible
  val akkaHttp          = "com.typesafe.akka" %% "akka-http"            % AkkaHttpVersion  // Scala 3 incompatible
  val akkaHttpCors      = "ch.megard"         %% "akka-http-cors"       % "1.2.0"          // Scala 3 incompatible
  val akkaHttpSprayJson = "com.typesafe.akka" %% "akka-http-spray-json" % AkkaHttpVersion  // Scala 3 incompatible
  val akkaSlf4j         = "com.typesafe.akka" %% "akka-slf4j"           % AkkaActorVersion // Scala 3 compatible
  val akkaStream        = "com.typesafe.akka" %% "akka-stream"          % AkkaActorVersion // Scala 3 compatible

  // jena
  val jenaText = "org.apache.jena" % "jena-text" % JenaVersion

  // logging
  val scalaLogging = "com.typesafe.scala-logging" %% "scala-logging" % "3.9.5" // Scala 3 compatible
  val slf4jApi     = "org.slf4j"                   % "slf4j-api"     % "2.0.7" // the logging interface

  // Metrics
  val aspectjweaver    = "org.aspectj" % "aspectjweaver"      % "1.9.19"
  val kamonCore        = "io.kamon"   %% "kamon-core"         % "2.6.0" // Scala 3 compatible
  val kamonScalaFuture = "io.kamon"   %% "kamon-scala-future" % "2.6.0" // Scala 3 incompatible

  // input validation
  val commonsValidator =
    "commons-validator" % "commons-validator" % "1.7" exclude ("commons-logging", "commons-logging")

  // authentication
  val jwtSprayJson = "com.github.jwt-scala" %% "jwt-spray-json" % "9.0.2"
  // jwtSprayJson -> 9.0.2 is the latest version that's compatible with spray-json; if it wasn't for spray, this would be Scala 3 compatible
  val springSecurityCore =
    "org.springframework.security" % "spring-security-core" % "6.0.3" exclude ("commons-logging", "commons-logging") exclude ("org.springframework", "spring-aop")
  val bouncyCastle = "org.bouncycastle" % "bcprov-jdk15to18" % "1.73"

  // caching
  val ehcache = "net.sf.ehcache" % "ehcache" % "2.10.9.2"

  // serialization
  val chill = "com.twitter" %% "chill" % "0.10.0" // Scala 3 incompatible

  // other
  val diff           = "com.sksamuel.diff" % "diff"             % "1.1.11"
  val gwtServlet     = "com.google.gwt"    % "gwt-servlet"      % "2.10.0"
  val icu4j          = "com.ibm.icu"       % "icu4j"            % "73.1"
  val jakartaJSON    = "org.glassfish"     % "jakarta.json"     % "2.0.1"
  val jodd           = "org.jodd"          % "jodd"             % "3.2.7"
  val rdf4jClient    = "org.eclipse.rdf4j" % "rdf4j-client"     % "4.2.4"
  val rdf4jShacl     = "org.eclipse.rdf4j" % "rdf4j-shacl"      % "4.2.4"
  val saxonHE        = "net.sf.saxon"      % "Saxon-HE"         % "12.1"
  val scalaGraph     = "org.scala-graph"  %% "graph-core"       % "1.13.6" // Scala 3 incompatible
  val scallop        = "org.rogach"       %% "scallop"          % "4.1.0"  // Scala 3 compatible
  val titaniumJSONLD = "com.apicatalog"    % "titanium-json-ld" % "1.3.2"
  val xmlunitCore    = "org.xmlunit"       % "xmlunit-core"     % "2.9.1"

  // test
  val akkaHttpTestkit   = "com.typesafe.akka" %% "akka-http-testkit"   % AkkaHttpVersion  // Scala 3 incompatible
  val akkaStreamTestkit = "com.typesafe.akka" %% "akka-stream-testkit" % AkkaActorVersion // Scala 3 compatible
  val akkaTestkit       = "com.typesafe.akka" %% "akka-testkit"        % AkkaActorVersion // Scala 3 compatible
  val scalaTest         = "org.scalatest"     %% "scalatest"           % "3.2.15"         // Scala 3 compatible
  val testcontainers    = "org.testcontainers" % "testcontainers"      % "1.18.0"

  // found/added by the plugin but deleted anyway
  val commonsLang3 = "org.apache.commons" % "commons-lang3" % "3.12.0"

  val webapiIntegrationTestDependencies = Seq(
    akkaHttpTestkit,
    akkaStreamTestkit,
    akkaTestkit,
    rdf4jClient,
    scalaTest,
    testcontainers,
    xmlunitCore,
    zioTest,
    zioTestSbt
  ).map(_ % IntegrationTest)

  val webapiTestDependencies = Seq(zioTest, zioTestSbt, zioMock).map(_ % Test)

  val webapiDependencies = Seq(
    akkaActor,
    akkaHttp,
    akkaHttpCors,
    akkaHttpSprayJson,
    akkaSlf4j,
    akkaStream,
    commonsValidator,
    commonsLang3,
    diff,
    ehcache,
    gwtServlet,
    icu4j,
    jakartaJSON,
    jenaText,
    jodd,
    jwtSprayJson,
    kamonCore,
    kamonScalaFuture,
    rdf4jShacl,
    saxonHE,
    scalaGraph,
    scalaLogging,
    scallop,
    slf4jApi,
    springSecurityCore,
    bouncyCastle,
    titaniumJSONLD,
    zio,
    zioConfig,
    zioConfigMagnolia,
    zioConfigTypesafe,
    zioHttp,
    zioJson,
    zioLogging,
    zioLoggingSlf4jBridge,
    zioNio,
    zioMacros,
    zioMetricsConnectors,
    zioPrelude
  )

  // schema project dependencies
  val schemaApiLibraryDependencies = Seq(
    zioHttpOld
  )

  val schemaCoreLibraryDependencies = Seq(
    zioPrelude,
    zioTest    % Test,
    zioTestSbt % Test
  )

  val schemaRepoLibraryDependencies                  = Seq()
  val schemaRepoEventStoreServiceLibraryDependencies = Seq()
  val schemaRepoSearchServiceLibraryDependencies     = Seq()

  // shared project dependencies
  val sharedLibraryDependencies = Seq(
    bouncyCastle,
    commonsLang3,
    commonsValidator,
    gwtServlet,
    scalaLogging,
    springSecurityCore,
    zioPrelude,
    zioConfig,
    zioConfigMagnolia,
    zioConfigTypesafe,
    zioTest    % Test,
    zioTestSbt % Test,
    zioLogging,
    zioLoggingSlf4jBridge,
    zioJson
  )
}
