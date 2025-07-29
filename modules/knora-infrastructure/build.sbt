/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

lazy val knoraInfrastructure = project
  .in(file("."))
  .dependsOn(
    ProjectRef(file("../dsp-common"), "dspCommon")
  )
  .settings(
    name := "knora-infrastructure",
    organization := "org.knora",
    scalaVersion := "3.3.6",
    description := "Infrastructure utilities for caching, JWT, metrics, and cross-cutting concerns",
    
    // Dependencies
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio" % "2.1.19",
      "dev.zio" %% "zio-json" % "0.7.44",
      "dev.zio" %% "zio-config" % "4.0.4",
      "dev.zio" %% "zio-config-magnolia" % "4.0.4",
      "dev.zio" %% "zio-config-typesafe" % "4.0.4",
      "com.typesafe.scala-logging" %% "scala-logging" % "3.9.5",
      "org.ehcache" % "ehcache" % "3.10.8",
      "com.github.jwt-scala" %% "jwt-zio-json" % "11.0.2",
      "com.github.tototoshi" %% "scala-csv" % "2.0.0",
      "org.aspectj" % "aspectjweaver" % "1.9.24",
      "dev.zio" %% "zio-opentelemetry" % "3.1.6",
      "io.sentry" % "sentry-opentelemetry-agentless" % "8.17.0",
      // Test dependencies
      "dev.zio" %% "zio-test" % "2.1.19" % Test,
      "dev.zio" %% "zio-test-sbt" % "2.1.19" % Test
    ),
    
    // Compiler settings
    scalacOptions ++= Seq(
      "-feature",
      "-unchecked", 
      "-deprecation",
      "-Yresolve-term-conflict:package",
      "-Wvalue-discard",
      "-Xmax-inlines:64",
      "-Wunused:all",
      "-Xfatal-warnings"
    ),
    
    // Test configuration
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
    Test / fork := true,
    Test / testForkedParallel := true,
    Test / parallelExecution := true
  )