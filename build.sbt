import com.typesafe.sbt.packager.docker.DockerPlugin.autoImport.{ Docker, dockerRepository }
import com.typesafe.sbt.packager.docker.Cmd
import sys.process.*

addCommandAlias("fmt", "scalafmt; Test / scalafmt;")
addCommandAlias("fmtCheck", "scalafmtCheck; Test / scalafmtCheck;")
addCommandAlias("headerCreateAll", "; all root/headerCreate Test/headerCreate")
addCommandAlias("headerCheckAll", "; all root/headerCheck Test/headerCheck")

val zioVersion                  = "2.0.15"
val zioJsonVersion              = "0.6.0"
val zioConfigVersion            = "3.0.7"
val zioLoggingVersion           = "2.1.14"
val testContainersVersion       = "0.40.15"
val zioMetricsConnectorsVersion = "2.1.0"
val zioMockVersion              = "1.0.0-RC11"
val zioNioVersion               = "2.0.1"
val zioPreludeVersion           = "1.0.0-RC19"
val zioHttpVersion              = "3.0.0-RC2"

val gitCommit  = ("git rev-parse HEAD" !!).trim
val gitVersion = ("git describe --tag --dirty --abbrev=7 --always  " !!).trim

ThisBuild / organization      := "dasch.swiss"
ThisBuild / version           := gitVersion
ThisBuild / scalaVersion      := "3.3.0"
ThisBuild / fork              := true
ThisBuild / semanticdbEnabled := true

scalacOptions ++= Seq("-old-syntax", "-rewrite")

lazy val root = (project in file("."))
  .enablePlugins(JavaAppPackaging, DockerPlugin, BuildInfoPlugin)
  .settings(
    buildInfoKeys    := Seq[BuildInfoKey](
      name,
      version,
      scalaVersion,
      sbtVersion,
      BuildInfoKey.action("gitCommit")(gitCommit),
    ),
    buildInfoOptions += BuildInfoOption.BuildTime,
    buildInfoPackage := "swiss.dasch.version",
  )
  .settings(
    name                                 := "dsp-ingest",
    headerLicense                        := Some(
      HeaderLicense.Custom(
        """|Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
           |SPDX-License-Identifier: Apache-2.0
           |""".stripMargin
      )
    ),
    libraryDependencies ++= Seq(
      "dev.zio"              %% "zio"                               % zioVersion,
      "dev.zio"              %% "zio-config"                        % zioConfigVersion,
      "dev.zio"              %% "zio-config-magnolia"               % zioConfigVersion,
      "dev.zio"              %% "zio-config-typesafe"               % zioConfigVersion,
      "dev.zio"              %% "zio-http"                          % zioHttpVersion,
      "dev.zio"              %% "zio-json"                          % zioJsonVersion,
      "dev.zio"              %% "zio-json-interop-refined"          % "0.6.0",
      "dev.zio"              %% "zio-metrics-connectors"            % zioMetricsConnectorsVersion,
      "dev.zio"              %% "zio-metrics-connectors-prometheus" % zioMetricsConnectorsVersion,
      "dev.zio"              %% "zio-nio"                           % zioNioVersion,
      "dev.zio"              %% "zio-prelude"                       % zioPreludeVersion,
      "dev.zio"              %% "zio-streams"                       % zioVersion,
      "eu.timepit"           %% "refined"                           % "0.11.0",
      "commons-io"            % "commons-io"                        % "2.13.0",
      "com.github.jwt-scala" %% "jwt-zio-json"                      % "9.4.3",
      // add the silencer lib for scala 2.13 in order to compile with scala 3.3.0 until https://github.com/zio/zio-config/pull/1171 is merged
      // resolves problems when `sbt doc` failed with
      // [error] -- Error: typesafe/shared/src/main/scala/zio/config/typesafe/TypesafeConfigSource.scala:15:0
      // [error] undefined: new com.github.ghik.silencer.silent #
      "com.github.ghik"       % "silencer-lib_2.13.11"              % "1.17.13",

      // logging
      "dev.zio" %% "zio-logging"               % zioLoggingVersion,
      "dev.zio" %% "zio-logging-slf4j2-bridge" % zioLoggingVersion,

      // test
      "dev.zio"      %% "zio-test"               % zioVersion     % Test,
      "dev.zio"      %% "zio-test-sbt"           % zioVersion     % Test,
      "dev.zio"      %% "zio-test-junit"         % zioVersion     % Test,
      "dev.zio"      %% "zio-mock"               % zioMockVersion % Test,
      "dev.zio"      %% "zio-test-magnolia"      % zioVersion     % Test,
      "org.scoverage" % "sbt-scoverage_2.12_1.0" % "2.0.8"        % Test,
    ),
    testFrameworks                       := Seq(new TestFramework("zio.test.sbt.ZTestFramework")),
    Docker / dockerRepository            := Some("daschswiss"),
    Docker / packageName                 := "dsp-ingest",
    dockerExposedPorts                   := Seq(3340),
    Docker / defaultLinuxInstallLocation := "/sipi",
    dockerUpdateLatest                   := true,
    dockerBaseImage                      := "daschswiss/knora-sipi:latest",
    dockerBuildxPlatforms                := Seq("linux/arm64/v8", "linux/amd64"),
    dockerCommands += Cmd(
      """HEALTHCHECK --interval=30s --timeout=10s --retries=3 --start-period=30s \
        |CMD curl -sS --fail 'http://localhost:3340/health' || exit 1""".stripMargin
    ),
    dockerCommands += Cmd(
      "RUN",
      "apt-get update && apt-get install -y openjdk-17-jre-headless && apt-get clean",
    ),
    dockerCommands                       := dockerCommands.value.filterNot {
      case Cmd("USER", args @ _*) => true
      case cmd                    => false
    },
  )
