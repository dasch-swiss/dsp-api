/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora

import sbt.*

import scala.collection.immutable.Seq

object Dependencies {

  val fusekiImage =
    "daschswiss/apache-jena-fuseki:2.1.4" // should be the same version as in docker-compose.yml, also make sure to use the same version when deploying it (i.e. version in ops-deploy)!
  val sipiImage = "daschswiss/sipi:3.8.11" // base image the knora-sipi image is created from

  val ScalaVersion = "2.13.12"

  val PekkoActorVersion = "1.0.2"
  val PekkoHttpVersion  = "1.0.0"
  val JenaVersion       = "4.10.0"

  val ZioConfigVersion            = "4.0.1"
  val ZioLoggingVersion           = "2.1.17"
  val ZioNioVersion               = "2.0.2"
  val ZioMetricsConnectorsVersion = "2.3.1"
  val ZioPreludeVersion           = "1.0.0-RC21"
  val ZioSchemaVersion            = "0.2.0"
  val ZioVersion                  = "2.0.21"

  // ZIO - all Scala 3 compatible
  val zio                   = "dev.zio"                       %% "zio"                       % ZioVersion
  val zioConfig             = "dev.zio"                       %% "zio-config"                % ZioConfigVersion
  val zioConfigMagnolia     = "dev.zio"                       %% "zio-config-magnolia"       % ZioConfigVersion
  val zioConfigTypesafe     = "dev.zio"                       %% "zio-config-typesafe"       % ZioConfigVersion
  val zioJson               = "dev.zio"                       %% "zio-json"                  % "0.6.2"
  val zioLogging            = "dev.zio"                       %% "zio-logging"               % ZioLoggingVersion
  val zioLoggingSlf4jBridge = "dev.zio"                       %% "zio-logging-slf4j2-bridge" % ZioLoggingVersion
  val zioNio                = "dev.zio"                       %% "zio-nio"                   % ZioNioVersion
  val zioMacros             = "dev.zio"                       %% "zio-macros"                % ZioVersion
  val zioPrelude            = "dev.zio"                       %% "zio-prelude"               % ZioPreludeVersion
  val zioSttp               = "com.softwaremill.sttp.client3" %% "zio"                       % "3.9.2"

  // refined
  val refined = Seq(
    "eu.timepit" %% "refined"                  % "0.11.1",
    "dev.zio"    %% "zio-json-interop-refined" % "0.6.2"
  )

  // zio-test and friends
  val zioTest    = "dev.zio" %% "zio-test"     % ZioVersion
  val zioTestSbt = "dev.zio" %% "zio-test-sbt" % ZioVersion

  // pekko
  val pekkoActor         = "org.apache.pekko" %% "pekko-actor"           % PekkoActorVersion
  val pekkoHttp          = "org.apache.pekko" %% "pekko-http"            % PekkoHttpVersion
  val pekkoHttpCors      = "org.apache.pekko" %% "pekko-http-cors"       % PekkoHttpVersion
  val pekkoHttpSprayJson = "org.apache.pekko" %% "pekko-http-spray-json" % PekkoHttpVersion
  val pekkoSlf4j         = "org.apache.pekko" %% "pekko-slf4j"           % PekkoActorVersion
  val pekkoStream        = "org.apache.pekko" %% "pekko-stream"          % PekkoActorVersion

  // jena
  val jenaText = "org.apache.jena" % "jena-text" % JenaVersion

  // logging
  val scalaLogging = "com.typesafe.scala-logging" %% "scala-logging" % "3.9.5"  // Scala 3 compatible
  val slf4jApi     = "org.slf4j"                   % "slf4j-api"     % "2.0.11" // the logging interface

  // Metrics
  val aspectjweaver    = "org.aspectj" % "aspectjweaver"      % "1.9.21"
  val kamonCore        = "io.kamon"   %% "kamon-core"         % "2.7.0" // Scala 3 compatible
  val kamonScalaFuture = "io.kamon"   %% "kamon-scala-future" % "2.7.0" // Scala 3 incompatible

  // input validation
  val commonsValidator =
    "commons-validator" % "commons-validator" % "1.8.0" exclude ("commons-logging", "commons-logging")

  // authentication
  val jwtSprayJson = "com.github.jwt-scala" %% "jwt-spray-json" % "9.0.2"
  // jwtSprayJson -> 9.0.2 is the latest version that's compatible with spray-json; if it wasn't for spray, this would be Scala 3 compatible
  val springSecurityCore =
    "org.springframework.security" % "spring-security-core" % "6.2.1" exclude ("commons-logging", "commons-logging") exclude ("org.springframework", "spring-aop")
  val bouncyCastle = "org.bouncycastle" % "bcprov-jdk15to18" % "1.77"

  // caching
  val ehcache = "net.sf.ehcache" % "ehcache" % "2.10.9.2"

  // serialization
  val chill = "com.twitter" %% "chill" % "0.10.0" // Scala 3 incompatible

  // other
  val diff           = "com.sksamuel.diff" % "diff"             % "1.1.11"
  val gwtServlet     = "com.google.gwt"    % "gwt-servlet"      % "2.10.0"
  val icu4j          = "com.ibm.icu"       % "icu4j"            % "74.2"
  val jakartaJSON    = "org.glassfish"     % "jakarta.json"     % "2.0.1"
  val jodd           = "org.jodd"          % "jodd"             % "3.2.7"
  val rdf4jClient    = "org.eclipse.rdf4j" % "rdf4j-client"     % "4.3.9"
  val rdf4jShacl     = "org.eclipse.rdf4j" % "rdf4j-shacl"      % "4.3.9"
  val saxonHE        = "net.sf.saxon"      % "Saxon-HE"         % "12.4"
  val scalaGraph     = "org.scala-graph"  %% "graph-core"       % "1.13.6" // Scala 3 incompatible
  val scallop        = "org.rogach"       %% "scallop"          % "5.0.1"  // Scala 3 compatible
  val titaniumJSONLD = "com.apicatalog"    % "titanium-json-ld" % "1.3.3"
  val xmlunitCore    = "org.xmlunit"       % "xmlunit-core"     % "2.9.1"

  // test
  val pekkoHttpTestkit   = "org.apache.pekko" %% "pekko-http-testkit"   % PekkoHttpVersion  // Scala 3 incompatible
  val pekkoStreamTestkit = "org.apache.pekko" %% "pekko-stream-testkit" % PekkoActorVersion // Scala 3 compatible
  val pekkoTestkit       = "org.apache.pekko" %% "pekko-testkit"        % PekkoActorVersion // Scala 3 compatible
  val scalaTest          = "org.scalatest"    %% "scalatest"            % "3.2.17"          // Scala 3 compatible
  // The scoverage plugin actually adds its dependencies automatically.
  // Add it redundantly to the IT dependencies in order to fix build issues with IntelliJ
  // Fixes error message when running IT in IntelliJ
  //  A needed class was not found. This could be due to an error in your runpath.Missing class: scoverage / Invoker$
  //  java.lang.NoClassDefFoundError: scoverage / Invoker$
  val scoverage      = "org.scoverage"     %% "scalac-scoverage-runtime" % "2.0.11"
  val testcontainers = "org.testcontainers" % "testcontainers"           % "1.19.4"
  val wiremock       = "org.wiremock"       % "wiremock"                 % "3.3.1"

  // found/added by the plugin but deleted anyway
  val commonsLang3 = "org.apache.commons" % "commons-lang3" % "3.14.0"

  val tapirVersion = "1.9.8"

  val tapir = Seq(
    "com.softwaremill.sttp.tapir" %% "tapir-pekko-http-server" % tapirVersion,
    "com.softwaremill.sttp.tapir" %% "tapir-zio-http-server"   % tapirVersion,
    "com.softwaremill.sttp.tapir" %% "tapir-json-zio"          % tapirVersion,
    "com.softwaremill.sttp.tapir" %% "tapir-json-spray"        % tapirVersion,
    "com.softwaremill.sttp.tapir" %% "tapir-swagger-ui-bundle" % tapirVersion,
    "com.softwaremill.sttp.tapir" %% "tapir-refined"           % "1.9.8"
  )
  val metrics = Seq(
    "dev.zio"                     %% "zio-metrics-connectors"            % ZioMetricsConnectorsVersion,
    "dev.zio"                     %% "zio-metrics-connectors-prometheus" % ZioMetricsConnectorsVersion,
    "com.softwaremill.sttp.tapir" %% "tapir-zio-metrics"                 % tapirVersion
  )

  val integrationTestDependencies = Seq(
    pekkoHttpTestkit,
    pekkoStreamTestkit,
    pekkoTestkit,
    rdf4jClient,
    scalaTest,
    scoverage,
    testcontainers,
    wiremock,
    xmlunitCore,
    zioTest,
    zioTestSbt
  ).map(_ % Test)

  val webapiTestDependencies = Seq(zioTest, zioTestSbt, wiremock).map(_ % Test)

  val webapiDependencies = refined ++ Seq(
    pekkoActor,
    pekkoHttp,
    pekkoHttpCors,
    pekkoHttpSprayJson,
    pekkoSlf4j,
    pekkoStream,
    bouncyCastle,
    commonsLang3,
    commonsValidator,
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
    titaniumJSONLD,
    zio,
    zioConfig,
    zioConfigMagnolia,
    zioConfigTypesafe,
    zioJson,
    zioLogging,
    zioLoggingSlf4jBridge,
    zioMacros,
    zioNio,
    zioPrelude,
    zioSttp
  ) ++ metrics ++ tapir
}
