/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora

import sbt._

object Dependencies {
  // should be the same version as in docker-compose.yml,
  // make sure to use the same version in ops-deploy repository when deploying new DSP releases!
  val fusekiImage = "daschswiss/apache-jena-fuseki:5.5.0-1"
  // base image the knora-sipi image is created from
  val sipiImage = "daschswiss/sipi:v3.16.3"

  val ScalaVersion = "3.3.6"

  val PekkoActorVersion = "1.2.0"
  val PekkoHttpVersion  = "1.2.0"

  val MonocleVersion = "3.3.0"

  val Rdf4jVersion         = "5.1.5"
  val TopbraidShaclVersion = "1.4.4"
  val JenaVersion          = "5.2.0" // should be aligned with the version topbraid-shacl uses

  val ZioConfigVersion            = "4.0.5"
  val ZioLoggingVersion           = "2.5.1"
  val ZioNioVersion               = "2.0.2"
  val ZioMetricsConnectorsVersion = "2.5.0"
  val ZioPreludeVersion           = "1.0.0-RC41"
  val ZioSchemaVersion            = "1.7.5"
  val ZioMockVersion              = "1.0.0-RC12"
  val ZioVersion                  = "2.1.21"

  // ZIO
  val zio               = "dev.zio" %% "zio"                 % ZioVersion
  val zioConfig         = "dev.zio" %% "zio-config"          % ZioConfigVersion
  val zioConfigMagnolia = "dev.zio" %% "zio-config-magnolia" % ZioConfigVersion
  val zioConfigTypesafe = "dev.zio" %% "zio-config-typesafe" % ZioConfigVersion

  val ZioJsonVersion        = "0.7.44"
  val zioJson               = "dev.zio" %% "zio-json"                  % ZioJsonVersion
  val zioLogging            = "dev.zio" %% "zio-logging"               % ZioLoggingVersion
  val zioLoggingSlf4jBridge = "dev.zio" %% "zio-logging-slf4j2-bridge" % ZioLoggingVersion
  val zioNio                = "dev.zio" %% "zio-nio"                   % ZioNioVersion
  val zioPrelude            = "dev.zio" %% "zio-prelude"               % ZioPreludeVersion

  val zioMock = "dev.zio" %% "zio-mock" % ZioMockVersion % Test

  val zioSeq = Seq(
    "dev.zio" %% "zio"                   % ZioVersion,
    "dev.zio" %% "zio-streams"           % ZioVersion,
    "dev.zio" %% "zio-schema"            % ZioSchemaVersion,
    "dev.zio" %% "zio-schema-derivation" % ZioSchemaVersion,
    "dev.zio" %% "zio-nio"               % ZioNioVersion,
    "dev.zio" %% "zio-prelude"           % ZioPreludeVersion,
  )

  val ingestTest = Seq(
    "dev.zio"      %% "zio-mock"               % ZioMockVersion % Test,
    "dev.zio"      %% "zio-test"               % ZioVersion     % Test,
    "dev.zio"      %% "zio-test-junit"         % ZioVersion     % Test,
    "dev.zio"      %% "zio-test-magnolia"      % ZioVersion     % Test,
    "dev.zio"      %% "zio-test-sbt"           % ZioVersion     % Test,
    "org.scoverage" % "sbt-scoverage_2.12_1.0" % "2.3.1"        % Test,
  )

  val SttpClientVersion = "4.0.12"
  val zioSttpClient = Seq(
    "com.softwaremill.sttp.client4" %% "zio"      % SttpClientVersion,
    "com.softwaremill.sttp.client4" %% "zio-json" % SttpClientVersion,
  )

  // refined
  val refined = Seq(
    "eu.timepit" %% "refined"                  % "0.11.3",
    "dev.zio"    %% "zio-json-interop-refined" % ZioJsonVersion,
  )

  // monocle
  val monocle = Seq(
    "dev.optics" %% "monocle-core"    % MonocleVersion,
    "dev.optics" %% "monocle-macro"   % MonocleVersion,
    "dev.optics" %% "monocle-refined" % MonocleVersion,
  )

  // zio-test and friends
  val zioTest    = "dev.zio" %% "zio-test"     % ZioVersion
  val zioTestSbt = "dev.zio" %% "zio-test-sbt" % ZioVersion

  // pekko
  val pekkoHttp     = "org.apache.pekko" %% "pekko-http"      % PekkoHttpVersion
  val pekkoHttpCors = "org.apache.pekko" %% "pekko-http-cors" % PekkoHttpVersion
  val pekkoSlf4j    = "org.apache.pekko" %% "pekko-slf4j"     % PekkoActorVersion
  val pekkoStream   = "org.apache.pekko" %% "pekko-stream"    % PekkoActorVersion

  // rdf and graph libraries
  val jenaCore      = "org.apache.jena"   % "jena-core"           % JenaVersion
  val jenaText      = "org.apache.jena"   % "jena-text"           % JenaVersion
  val rdf4jClient   = "org.eclipse.rdf4j" % "rdf4j-client"        % Rdf4jVersion
  val rdf4jShacl    = "org.eclipse.rdf4j" % "rdf4j-shacl"         % Rdf4jVersion
  val rdf4jSparql   = "org.eclipse.rdf4j" % "rdf4j-sparqlbuilder" % Rdf4jVersion
  val topbraidShacl = "org.topbraid"      % "shacl"               % TopbraidShaclVersion

  // logging
  val slf4jApi = "org.slf4j" % "slf4j-api" % "2.0.17" // the logging interface

  // Metrics
  val aspectjweaver = "org.aspectj" % "aspectjweaver" % "1.9.24"

  // input validation
  val commonsValidator =
    "commons-validator" % "commons-validator" % "1.10.0" exclude ("commons-logging", "commons-logging")

  // authentication
  val jwtZioJson = "com.github.jwt-scala" %% "jwt-zio-json" % "11.0.3"
  val springSecurityCore =
    "org.springframework.security" % "spring-security-core" % "6.5.3" exclude (
      "commons-logging",
      "commons-logging",
    ) exclude ("org.springframework", "spring-aop")
  val bouncyCastle = "org.bouncycastle" % "bcprov-jdk15to18" % "1.81"

  // caching
  val ehcache = "org.ehcache" % "ehcache" % "3.11.1"

  // other
  val gwtServlet     = "com.google.gwt"        % "gwt-servlet"      % "2.10.0"
  val icu4j          = "com.ibm.icu"           % "icu4j"            % "77.1"
  val jakartaJSON    = "org.glassfish"         % "jakarta.json"     % "2.0.1"
  val saxonHE        = "net.sf.saxon"          % "Saxon-HE"         % "12.9"
  val scalaGraph     = "org.scala-graph"      %% "graph-core"       % "2.0.2"
  val titaniumJSONLD = "com.apicatalog"        % "titanium-json-ld" % "1.6.0"
  val xmlunitCore    = "org.xmlunit"           % "xmlunit-core"     % "2.10.4"
  val scalaCsv       = "com.github.tototoshi" %% "scala-csv"        % "2.0.0"

  // test
  val scalaTest = "org.scalatest" %% "scalatest" % "3.2.19"

  val testcontainers = "org.testcontainers" % "testcontainers" % "1.21.3"

  val wiremock = "org.wiremock" % "wiremock" % "3.13.1"

  // found/added by the plugin but deleted anyway
  val commonsLang3 = "org.apache.commons" % "commons-lang3" % "3.18.0"

  val tapirVersion = "1.11.44"

  val tapir = Seq(
    "com.softwaremill.sttp.tapir" %% "tapir-pekko-http-server" % tapirVersion,
    "com.softwaremill.sttp.tapir" %% "tapir-zio-http-server"   % tapirVersion,
    "com.softwaremill.sttp.tapir" %% "tapir-json-zio"          % tapirVersion,
    "com.softwaremill.sttp.tapir" %% "tapir-swagger-ui-bundle" % tapirVersion,
    "com.softwaremill.sttp.tapir" %% "tapir-refined"           % tapirVersion,
  )
  val metrics = Seq(
    "dev.zio"                     %% "zio-metrics-connectors"            % ZioMetricsConnectorsVersion,
    "dev.zio"                     %% "zio-metrics-connectors-prometheus" % ZioMetricsConnectorsVersion,
    "com.softwaremill.sttp.tapir" %% "tapir-zio-metrics"                 % tapirVersion,
  )

  val openTelemetryWithSentry = Seq(
    "dev.zio"  %% "zio-opentelemetry"              % "3.1.9",
    "io.sentry" % "sentry-opentelemetry-agentless" % "8.21.1",
  )

  val integrationTestDependencies = Seq(
    rdf4jClient,
    scalaTest,
    testcontainers,
    wiremock,
    xmlunitCore,
    zioTest,
    zioTestSbt,
  ).map(_ % Test)

  val webapiTestDependencies = Seq(zioTest, zioTestSbt, wiremock).map(_ % Test)

  val webapiDependencies = monocle ++ refined ++ Seq(
    pekkoHttp,
    pekkoHttpCors,
    pekkoSlf4j,
    pekkoStream,
    bouncyCastle,
    commonsLang3,
    commonsValidator,
    ehcache,
    gwtServlet,
    icu4j,
    jakartaJSON,
    jenaText,
    jwtZioJson,
    rdf4jShacl,
    rdf4jSparql,
    saxonHE,
    scalaCsv,
    scalaGraph,
    slf4jApi,
    springSecurityCore,
    titaniumJSONLD,
    topbraidShacl,
    zio,
    zioConfig,
    zioConfigMagnolia,
    zioConfigTypesafe,
    zioJson,
    zioLogging,
    zioLoggingSlf4jBridge,
    zioNio,
    zioPrelude,
  ) ++ zioSttpClient ++ metrics ++ tapir ++ openTelemetryWithSentry

  val flywayVersion         = "11.12.0"
  val otelAgentVersion      = "v2.18.1"
  val otelPyroscopeVersion  = "v1.0.4"
  val hikariVersion         = "6.3.3"
  val quillVersion          = "4.8.6"
  val sqliteVersion         = "3.50.3.0"
  val testContainersVersion = "1.20.4"

  val db = Seq(
    "org.xerial"   % "sqlite-jdbc"    % sqliteVersion,
    "org.flywaydb" % "flyway-core"    % flywayVersion,
    "com.zaxxer"   % "HikariCP"       % hikariVersion,
    "io.getquill" %% "quill-jdbc-zio" % quillVersion,
  )
}
