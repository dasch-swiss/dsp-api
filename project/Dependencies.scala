/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora

import sbt.Keys._
import sbt.{Def, _}

object Dependencies {

  val fusekiImage =
    "daschswiss/apache-jena-fuseki:2.0.8" // should be the same version as in docker-compose.yml, also make sure to use the same version when deploying it (i.e. version in ops-deploy)!
  val sipiImage = "daschswiss/sipi:3.5.0" // base image the knora-sipi image is created from

  val ScalaVersion = "2.13.8"

  val AkkaHttpVersion  = "10.2.9"
  val AkkaActorVersion = "2.6.19"
  val JenaVersion      = "4.5.0"

  val ZioVersion        = "2.0.0"
  val ZioHttpVersion    = "2.0.0-RC4"
  val ZioJsonVersion    = "0.3.0-RC11"
  val ZioConfigVersion  = "3.0.2"
  val ZioSchemaVersion  = "0.2.0"
  val ZioLoggingVersion = "2.1.0"
  val ZioZmxVersion     = "2.0.0-RC4"
  val ZioPreludeVersion = "1.0.0-RC15"

  // ZIO - all Scala 3 compatible
  val zio               = "dev.zio" %% "zio"                 % ZioVersion
  val zioMacros         = "dev.zio" %% "zio-macros"          % ZioVersion
  val zioHttp           = "io.d11"  %% "zhttp"               % ZioHttpVersion
  val zioJson           = "dev.zio" %% "zio-json"            % ZioJsonVersion
  val zioPrelude        = "dev.zio" %% "zio-prelude"         % ZioPreludeVersion
  val zioLogging        = "dev.zio" %% "zio-logging"         % ZioLoggingVersion
  val zioLoggingSlf4j   = "dev.zio" %% "zio-logging-slf4j"   % ZioLoggingVersion
  val zioConfig         = "dev.zio" %% "zio-config"          % ZioConfigVersion
  val zioConfigMagnolia = "dev.zio" %% "zio-config-magnolia" % ZioConfigVersion
  val zioConfigTypesafe = "dev.zio" %% "zio-config-typesafe" % ZioConfigVersion
  val zioTest           = "dev.zio" %% "zio-test"            % "2.0.1"
  val zioTestSbt        = "dev.zio" %% "zio-test-sbt"        % "2.0.1"

  // akka
  val akkaActor         = "com.typesafe.akka" %% "akka-actor"           % AkkaActorVersion // Scala 3 compatible
  val akkaHttp          = "com.typesafe.akka" %% "akka-http"            % AkkaHttpVersion  // Scala 3 incompatible
  val akkaHttpCors      = "ch.megard"         %% "akka-http-cors"       % "1.1.3"          // Scala 3 incompatible
  val akkaHttpSprayJson = "com.typesafe.akka" %% "akka-http-spray-json" % AkkaHttpVersion  // Scala 3 incompatible
  val akkaSlf4j         = "com.typesafe.akka" %% "akka-slf4j"           % AkkaActorVersion // Scala 3 compatible
  val akkaStream        = "com.typesafe.akka" %% "akka-stream"          % AkkaActorVersion // Scala 3 compatible

  // jena
  val jenaText = "org.apache.jena" % "jena-text" % JenaVersion

  // logging
  val logbackClassic = "ch.qos.logback"              % "logback-classic" % "1.2.11"
  val scalaLogging   = "com.typesafe.scala-logging" %% "scala-logging"   % "3.9.5" // Scala 3 compatible
  val slf4j          = "org.slf4j"                   % "slf4j-simple"    % "2.0.0"

  // Metrics
  val aspectjweaver    = "org.aspectj" % "aspectjweaver"      % "1.9.9.1"
  val kamonCore        = "io.kamon"   %% "kamon-core"         % "2.5.7" // Scala 3 compatible
  val kamonScalaFuture = "io.kamon"   %% "kamon-scala-future" % "2.5.7" // Scala 3 incompatible

  // input validation
  val commonsValidator =
    "commons-validator" % "commons-validator" % "1.7" exclude ("commons-logging", "commons-logging")

  // authentication
  val jwtSprayJson = "com.github.jwt-scala" %% "jwt-spray-json" % "9.0.2"
  // jwtSprayJson -> 9.0.2 is the latest version that's compatible with spray-json; if it wasn't for spray, this would be Scala 3 compatible
  val springSecurityCore =
    "org.springframework.security" % "spring-security-core" % "5.7.3" exclude ("commons-logging", "commons-logging") exclude ("org.springframework", "spring-aop")
  val bouncyCastle = "org.bouncycastle" % "bcprov-jdk15to18" % "1.71"

  // caching
  val ehcache = "net.sf.ehcache" % "ehcache" % "2.10.9.2"
  val jedis   = "redis.clients"  % "jedis"   % "4.2.3"

  // serialization
  val chill = "com.twitter" %% "chill" % "0.10.0" // Scala 3 incompatible

  // other
  val diff            = "com.sksamuel.diff"             % "diff"              % "1.1.11"
  val gwtServlet      = "com.google.gwt"                % "gwt-servlet"       % "2.10.0"
  val icu4j           = "com.ibm.icu"                   % "icu4j"             % "71.1"
  val jakartaJSON     = "org.glassfish"                 % "jakarta.json"      % "2.0.1"
  val jodd            = "org.jodd"                      % "jodd"              % "3.2.7"
  val rdf4jClient     = "org.eclipse.rdf4j"             % "rdf4j-client"      % "4.1.0"
  val rdf4jShacl      = "org.eclipse.rdf4j"             % "rdf4j-shacl"       % "4.1.0"
  val saxonHE         = "net.sf.saxon"                  % "Saxon-HE"          % "11.4"
  val scalaGraph      = "org.scala-graph"              %% "graph-core"        % "1.13.5" // Scala 3 incompatible
  val scallop         = "org.rogach"                   %% "scallop"           % "4.1.0"  // Scala 3 compatible
  val swaggerAkkaHttp = "com.github.swagger-akka-http" %% "swagger-akka-http" % "1.6.0"  // Scala 3 incompatible
  val titaniumJSONLD  = "com.apicatalog"                % "titanium-json-ld"  % "1.3.1"
  val xmlunitCore     = "org.xmlunit"                   % "xmlunit-core"      % "2.9.0"
  val jacksonDatabind = "com.fasterxml.jackson.core"    % "jackson-databind"  % "2.13.3"

  // test
  val akkaHttpTestkit      = "com.typesafe.akka"    %% "akka-http-testkit"         % AkkaHttpVersion  // Scala 3 incompatible
  val akkaStreamTestkit    = "com.typesafe.akka"    %% "akka-stream-testkit"       % AkkaActorVersion // Scala 3 compatible
  val akkaTestkit          = "com.typesafe.akka"    %% "akka-testkit"              % AkkaActorVersion // Scala 3 compatible
  val gatlingHighcharts    = "io.gatling.highcharts" % "gatling-charts-highcharts" % "3.8.2"
  val gatlingTestFramework = "io.gatling"            % "gatling-test-framework"    % "3.8.2"
  val scalaTest            = "org.scalatest"        %% "scalatest"                 % "3.2.13"         // Scala 3 compatible
  val testcontainers       = "org.testcontainers"    % "testcontainers"            % "1.17.3"

  // found/added by the plugin but deleted anyway
  val commonsLang3 = "org.apache.commons" % "commons-lang3" % "3.12.0"

  val webapiLibraryDependencies = Seq(
    akkaActor,
    akkaHttp,
    akkaHttpCors,
    akkaHttpSprayJson,
    akkaHttpTestkit % Test,
    akkaSlf4j       % Runtime,
    akkaStream,
    akkaStreamTestkit % Test,
    akkaTestkit       % Test,
    commonsValidator,
    commonsLang3,
    diff,
    ehcache,
    gatlingHighcharts    % Test,
    gatlingTestFramework % Test,
    gwtServlet,
    icu4j,
    jacksonDatabind,
    jakartaJSON,
    jedis,
    jenaText,
    jodd,
    jwtSprayJson,
    kamonCore,
    kamonScalaFuture,
    logbackClassic % Runtime,
    rdf4jClient    % Test,
    rdf4jShacl,
    saxonHE,
    scalaGraph,
    scalaLogging,
    scalaTest % Test,
    scallop,
    springSecurityCore,
    bouncyCastle,
    swaggerAkkaHttp,
    testcontainers % Test,
    titaniumJSONLD,
    xmlunitCore % Test,
    zio,
    zioConfig,
    zioConfigMagnolia,
    zioConfigTypesafe,
    zioHttp,
    zioJson,
    zioLogging,
    zioLoggingSlf4j,
    zioMacros,
    zioPrelude,
    zioTest    % Test,
    zioTestSbt % Test
  )

  val valueObjectsLibraryDependencies = Seq(
    commonsLang3,
    commonsValidator,
    gwtServlet,
    zioPrelude,
    zioTest    % Test,
    zioTestSbt % Test
  )

  val dspApiMainLibraryDependencies = Seq(
    zio,
    zioMacros
  )

  // schema project dependencies
  val schemaApiLibraryDependencies = Seq(
    zioHttp
  )

  val schemaCoreLibraryDependencies = Seq(
    zioPrelude,
    zioTest % Test
  )

  val schemaRepoLibraryDependencies                  = Seq()
  val schemaRepoEventStoreServiceLibraryDependencies = Seq()
  val schemaRepoSearchServiceLibraryDependencies     = Seq()

  // user projects dependencies
  val userInterfaceLibraryDependencies = Seq(
    slf4j % Test,
    zio,
    zioMacros,
    zioTest    % Test,
    zioTestSbt % Test
  )
  val userHandlerLibraryDependencies = Seq(
    bouncyCastle,
    slf4j % Test,
    springSecurityCore,
    zio,
    zioMacros,
    zioTest    % Test,
    zioTestSbt % Test
  )
  val userCoreLibraryDependencies = Seq(
    bouncyCastle,
    slf4j % Test,
    springSecurityCore,
    zio,
    zioMacros,
    zioTest    % Test,
    zioTestSbt % Test
  )
  val userRepoLibraryDependencies = Seq(
    slf4j % Test,
    zio,
    zioMacros,
    zioTest    % Test,
    zioTestSbt % Test
  )

  // role projects dependencies
  val roleInterfaceLibraryDependencies = Seq(
    slf4j % Test,
    zio,
    zioMacros,
    zioTest    % Test,
    zioTestSbt % Test
  )
  val roleHandlerLibraryDependencies = Seq(
    bouncyCastle,
    slf4j % Test,
    springSecurityCore,
    zio,
    zioMacros,
    zioTest    % Test,
    zioTestSbt % Test
  )
  val roleCoreLibraryDependencies = Seq(
    bouncyCastle,
    slf4j % Test,
    springSecurityCore,
    zio,
    zioMacros,
    zioTest    % Test,
    zioTestSbt % Test
  )
  val roleRepoLibraryDependencies = Seq(
    slf4j % Test,
    zio,
    zioMacros,
    zioTest    % Test,
    zioTestSbt % Test
  )

  // shared project dependencies
  val sharedLibraryDependencies = Seq(
    bouncyCastle,
    commonsLang3,
    commonsValidator,
    gwtServlet,
    scalaLogging,
    slf4j % Test,
    springSecurityCore,
    zioPrelude,
    zioTest    % Test,
    zioTestSbt % Test
  )

  // project project dependencies
  val projectInterfaceLibraryDependencies = Seq(
    zio,
    zioMacros,
    zioTest    % Test,
    zioTestSbt % Test
  )
  val projectHandlerLibraryDependencies = Seq(
    zio,
    zioMacros,
    zioTest    % Test,
    zioTestSbt % Test
  )
  val projectCoreLibraryDependencies = Seq(
    zio,
    zioMacros,
    zioTest    % Test,
    zioTestSbt % Test
  )
  val projectRepoLibraryDependencies = Seq(
    zio,
    zioMacros,
    zioTest    % Test,
    zioTestSbt % Test
  )
}
