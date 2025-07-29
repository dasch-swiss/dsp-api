/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

import sbt.*

object CommonDependencies {
  
  // Version constants - centralized dependency management
  val ZioVersion = "2.1.19"
  val ZioJsonVersion = "0.7.44"
  val ZioPreludeVersion = "1.0.0-RC41"
  val ZioTestVersion = ZioVersion
  val ZioConfigVersion = "4.0.4"
  val CommonsLang3Version = "3.18.0"
  val CommonsValidatorVersion = "1.10.0"
  val BouncyCastleVersion = "1.81"
  val GwtServletVersion = "2.10.0"
  val EhcacheVersion = "3.10.8"
  val JwtScalaVersion = "11.0.2"
  val ScalaCsvVersion = "2.0.0"
  val ScalaLoggingVersion = "3.9.5"
  val AspectJVersion = "1.9.24"
  val SentryVersion = "8.17.0"
  val ZioOpenTelemetryVersion = "3.1.6"
  
  // Common dependencies used across modules
  object Common {
    val zio = "dev.zio" %% "zio" % ZioVersion
    val zioJson = "dev.zio" %% "zio-json" % ZioJsonVersion
    val zioPrelude = "dev.zio" %% "zio-prelude" % ZioPreludeVersion
    val zioConfig = "dev.zio" %% "zio-config" % ZioConfigVersion
    val zioConfigMagnolia = "dev.zio" %% "zio-config-magnolia" % ZioConfigVersion
    val zioConfigTypesafe = "dev.zio" %% "zio-config-typesafe" % ZioConfigVersion
    val commonsLang3 = "org.apache.commons" % "commons-lang3" % CommonsLang3Version
    val commonsValidator = "commons-validator" % "commons-validator" % CommonsValidatorVersion exclude ("commons-logging", "commons-logging")
    val bouncyCastle = "org.bouncycastle" % "bcprov-jdk15to18" % BouncyCastleVersion
    val scalaLogging = "com.typesafe.scala-logging" %% "scala-logging" % ScalaLoggingVersion
  }
  
  // Test dependencies
  object TestDeps {
    val zioTest = "dev.zio" %% "zio-test" % ZioTestVersion % sbt.Test
    val zioTestSbt = "dev.zio" %% "zio-test-sbt" % ZioTestVersion % sbt.Test
  }
  
  // DSP-Common specific dependencies
  object DspCommon {
    val core = Seq(
      Common.zioPrelude,
      Common.zioJson,
      Common.commonsLang3,
      Common.commonsValidator,
      Common.bouncyCastle,
      "com.google.gwt" % "gwt-servlet" % GwtServletVersion
    )
    
    val test = Seq(
      TestDeps.zioTest,
      TestDeps.zioTestSbt
    )
    
    val all = core ++ test
  }
  
  // Knora-Infrastructure specific dependencies
  object KnoraInfrastructure {
    val core = Seq(
      Common.zio,
      Common.zioJson,
      Common.zioConfig,
      Common.zioConfigMagnolia,
      Common.zioConfigTypesafe,
      Common.scalaLogging,
      "org.ehcache" % "ehcache" % EhcacheVersion,
      "com.github.jwt-scala" %% "jwt-zio-json" % JwtScalaVersion,
      "com.github.tototoshi" %% "scala-csv" % ScalaCsvVersion,
      "org.aspectj" % "aspectjweaver" % AspectJVersion,
      "dev.zio" %% "zio-opentelemetry" % ZioOpenTelemetryVersion,
      "io.sentry" % "sentry-opentelemetry-agentless" % SentryVersion
    )
    
    val test = Seq(
      TestDeps.zioTest,
      TestDeps.zioTestSbt
    )
    
    val all = core ++ test
  }
  
  // Refined dependencies for value validation
  val refined = Seq(
    "eu.timepit" %% "refined" % "0.11.3",
    "dev.zio" %% "zio-json-interop-refined" % ZioJsonVersion
  )
}