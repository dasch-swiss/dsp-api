/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

lazy val dspCommon = project
  .in(file("."))
  .settings(
    name := "dsp-common",
    organization := "org.knora",
    scalaVersion := "3.3.6",
    description := "Common value objects, error handling, and utilities for DSP projects",
    
    // Dependencies
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio-prelude" % "1.0.0-RC41",
      "dev.zio" %% "zio-json" % "0.7.44",
      "org.apache.commons" % "commons-lang3" % "3.18.0",
      "commons-validator" % "commons-validator" % "1.10.0" exclude ("commons-logging", "commons-logging"),
      "org.bouncycastle" % "bcprov-jdk15to18" % "1.81",
      "com.google.gwt" % "gwt-servlet" % "2.10.0",
      "com.typesafe.scala-logging" %% "scala-logging" % "3.9.5",
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