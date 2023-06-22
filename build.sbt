import com.typesafe.sbt.SbtNativePackager.autoImport.NativePackagerHelper._
import com.typesafe.sbt.packager.docker.DockerPlugin.autoImport.{ Docker, dockerRepository }
import com.typesafe.sbt.packager.docker.Cmd
import sys.process._

addCommandAlias("fmt", "; all root/scalafmtSbt root/scalafmtAll")
addCommandAlias("headerCreateAll", "; all root/headerCreate Test/headerCreate")
addCommandAlias("headerCheckAll", "; all root/headerCheck Test/headerCheck")

val zioVersion            = "2.0.13"
val zioJsonVersion        = "0.5.0"
val zioConfigVersion      = "3.0.7"
val zioLoggingVersion     = "2.1.12"
val logbackClassicVersion = "1.4.7"
val testContainersVersion = "0.40.15"
val zioMockVersion        = "1.0.0-RC11"
val zioNioVersion         = "2.0.1"
val zioPreludeVersion     = "1.0.0-RC19"
val zioHttpVersion        = "3.0.0-RC2"

lazy val root = (project in file("."))
  .enablePlugins(JavaAppPackaging, DockerPlugin, BuildInfoPlugin)
  .settings(
    buildInfoKeys    := Seq[BuildInfoKey](
      name,
      version,
      scalaVersion,
      sbtVersion,
      BuildInfoKey.action("gitCommit") {
        "git rev-parse HEAD" !!
      },
    ),
    buildInfoOptions += BuildInfoOption.BuildTime,
    buildInfoPackage := "swiss.dasch.version",
  )
  .settings(
    inThisBuild(
      List(
        organization := "daschswiss",
        scalaVersion := "3.3.0",
      )
    ),
    name                                 := "dsp-ingest",
    headerLicense                        := Some(
      HeaderLicense.Custom(
        """|Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
           |SPDX-License-Identifier: Apache-2.0
           |""".stripMargin
      )
    ),
    libraryDependencies ++= Seq(
      "dev.zio"              %% "zio"                      % zioVersion,
      "dev.zio"              %% "zio-streams"              % zioVersion,
      "dev.zio"              %% "zio-http"                 % zioHttpVersion,
      "dev.zio"              %% "zio-config"               % zioConfigVersion,
      "dev.zio"              %% "zio-config-typesafe"      % zioConfigVersion,
      "ch.qos.logback"        % "logback-classic"          % logbackClassicVersion,
      "dev.zio"              %% "zio-json"                 % zioJsonVersion,
      "dev.zio"              %% "zio-nio"                  % zioNioVersion,
      "dev.zio"              %% "zio-prelude"              % zioPreludeVersion,
      "dev.zio"              %% "zio-json-interop-refined" % "0.5.0",
      "eu.timepit"           %% "refined"                  % "0.10.3",
      "com.github.jwt-scala" %% "jwt-zio-json"             % "9.4.0",
      // add the silencer lib for scala 2.13 in order to compile with scala 3.3.0 until https://github.com/zio/zio-config/pull/1171 is merged
      // resolves problems when `sbt doc` failed with
      // [error] -- Error: typesafe/shared/src/main/scala/zio/config/typesafe/TypesafeConfigSource.scala:15:0
      // [error] undefined: new com.github.ghik.silencer.silent #
      "com.github.ghik"       % "silencer-lib_2.13.11"     % "1.7.13",

      // logging
      "dev.zio"       %% "zio-logging"       % zioLoggingVersion,
      "dev.zio"       %% "zio-logging-slf4j" % zioLoggingVersion,
      "ch.qos.logback" % "logback-classic"   % logbackClassicVersion,

      // test
      "dev.zio" %% "zio-test"          % zioVersion     % Test,
      "dev.zio" %% "zio-test-sbt"      % zioVersion     % Test,
      "dev.zio" %% "zio-test-junit"    % zioVersion     % Test,
      "dev.zio" %% "zio-mock"          % zioMockVersion % Test,
      "dev.zio" %% "zio-test-magnolia" % zioVersion     % Test,
    ),
    testFrameworks                       := Seq(new TestFramework("zio.test.sbt.ZTestFramework")),
    Docker / dockerRepository            := Some("daschswiss"),
    Docker / packageName                 := "dsp-ingest",
    dockerExposedPorts ++= Seq(9999),
    Docker / defaultLinuxInstallLocation := "/sipi",
    dockerUpdateLatest                   := true,
    dockerBaseImage                      := "daschswiss/knora-sipi:latest",
    dockerBuildxPlatforms                := Seq("linux/arm64/v8", "linux/amd64"),
    dockerCommands += Cmd(
      "RUN",
      "apt-get update && apt-get install -y openjdk-17-jre-headless && apt-get clean",
    ),
    dockerCommands                       := dockerCommands.value.filterNot {
      case Cmd("USER", args @ _*) => true
      case cmd                    => false
    },
  )
