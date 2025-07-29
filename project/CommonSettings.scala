/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

import sbt.*
import sbt.Keys.*
import com.typesafe.sbt.SbtNativePackager.autoImport.*
import de.heikoseeberger.sbtheader.HeaderPlugin.autoImport.*
import scalafix.sbt.ScalafixPlugin.autoImport.*

object CommonSettings {

  val ScalaVersion = "3.3.6"

  lazy val year = java.time.LocalDate.now().getYear

  val commonScalacOptions = Seq(
    "-feature",
    "-unchecked",
    "-deprecation",
    "-Yresolve-term-conflict:package",
    "-Wvalue-discard",
    "-Xmax-inlines:64",
    "-Wunused:all",
    "-Xfatal-warnings",
  )

  val moduleSettings: Seq[Setting[?]] = Seq(
    organization := "org.knora",
    scalaVersion := ScalaVersion,
    scalacOptions ++= commonScalacOptions,
    headerLicense := Some(
      HeaderLicense.Custom(
        s"""|Copyright © 2021 - $year Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
            |SPDX-License-Identifier: Apache-2.0
            |""".stripMargin,
      ),
    ),
    // Test configuration
    Test / testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
    Test / fork               := true,
    Test / testForkedParallel := true,
    Test / parallelExecution  := true,
    // Compiler settings
    logLevel          := Level.Info,
    semanticdbEnabled := true,
    semanticdbVersion := scalafixSemanticdb.revision,
    // Publishing settings
    publishMavenStyle      := true,
    Test / publishArtifact := false,
  )

  val librarySettings: Seq[Setting[?]] = moduleSettings ++ Seq(
    // Skip packageDoc and packageSrc for faster builds during development
    Compile / packageDoc / mappings := Seq(),
    Compile / packageSrc / mappings := Seq(),
  )
}
