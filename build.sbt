import com.typesafe.sbt.packager.docker.Cmd
import com.typesafe.sbt.packager.docker.DockerPlugin.autoImport.{Docker, dockerRepository}

import scala.sys.process.*

addCommandAlias("fmt", "scalafmt; Test / scalafmt;")
addCommandAlias("fmtCheck", "scalafmtCheck; Test / scalafmtCheck;")
addCommandAlias("headerCreateAll", "; all root/headerCreate Test/headerCreate")
addCommandAlias("headerCheckAll", "; all root/headerCheck Test/headerCheck")

val sipiVersion                 = "v30.8.0"
val tapirVersion                = "1.9.9"
val testContainersVersion       = "0.40.15"
val zioConfigVersion            = "4.0.1"
val zioHttpVersion              = "3.0.0-RC4"
val zioJsonVersion              = "0.6.2"
val zioLoggingVersion           = "2.2.0"
val zioMetricsConnectorsVersion = "2.3.1"
val zioMockVersion              = "1.0.0-RC12"
val zioNioVersion               = "2.0.2"
val zioPreludeVersion           = "1.0.0-RC23"
val zioVersion                  = "2.0.21"

val gitCommit  = ("git rev-parse HEAD" !!).trim
val gitVersion = ("git describe --tag --dirty --abbrev=7 --always  " !!).trim

ThisBuild / organization      := "dasch.swiss"
ThisBuild / version           := gitVersion
ThisBuild / scalaVersion      := "3.3.1"
ThisBuild / fork              := true
ThisBuild / semanticdbEnabled := true

scalacOptions ++= Seq("-old-syntax", "-rewrite")

val tapir = Seq(
  "com.softwaremill.sttp.tapir" %% "tapir-json-zio"          % tapirVersion,
  "com.softwaremill.sttp.tapir" %% "tapir-refined"           % tapirVersion,
  "com.softwaremill.sttp.tapir" %% "tapir-swagger-ui-bundle" % tapirVersion,
  "com.softwaremill.sttp.tapir" %% "tapir-zio-http-server"   % tapirVersion,
  "com.softwaremill.sttp.tapir" %% "tapir-openapi-docs"      % tapirVersion
)

val metrics = Seq(
  "com.softwaremill.sttp.tapir" %% "tapir-zio-metrics"                 % tapirVersion,
  "dev.zio"                     %% "zio-metrics-connectors"            % zioMetricsConnectorsVersion,
  "dev.zio"                     %% "zio-metrics-connectors-prometheus" % zioMetricsConnectorsVersion
)

lazy val root = (project in file("."))
  .enablePlugins(JavaAppPackaging, DockerPlugin, BuildInfoPlugin)
  .settings(
    buildInfoKeys := Seq[BuildInfoKey](
      name,
      version,
      scalaVersion,
      sbtVersion,
      BuildInfoKey("sipiVersion", sipiVersion),
      BuildInfoKey.action("gitCommit")(gitCommit)
    ),
    buildInfoOptions += BuildInfoOption.BuildTime,
    buildInfoPackage    := "swiss.dasch.version",
    Compile / mainClass := Some("swiss.dasch.Main")
  )
  .settings(
    name := "dsp-ingest",
    headerLicense := Some(
      HeaderLicense.Custom(
        """|Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
           |SPDX-License-Identifier: Apache-2.0
           |""".stripMargin
      )
    ),
    libraryDependencies ++= tapir ++ metrics ++ Seq(
      "com.github.jwt-scala" %% "jwt-zio-json"                      % "10.0.0",
      "commons-io"            % "commons-io"                        % "2.15.1",
      "dev.zio"              %% "zio"                               % zioVersion,
      "dev.zio"              %% "zio-config"                        % zioConfigVersion,
      "dev.zio"              %% "zio-config-magnolia"               % zioConfigVersion,
      "dev.zio"              %% "zio-config-typesafe"               % zioConfigVersion,
      "dev.zio"              %% "zio-http"                          % zioHttpVersion,
      "dev.zio"              %% "zio-json"                          % zioJsonVersion,
      "dev.zio"              %% "zio-json-interop-refined"          % zioJsonVersion,
      "dev.zio"              %% "zio-metrics-connectors"            % zioMetricsConnectorsVersion,
      "dev.zio"              %% "zio-metrics-connectors-prometheus" % zioMetricsConnectorsVersion,
      "dev.zio"              %% "zio-nio"                           % zioNioVersion,
      "dev.zio"              %% "zio-prelude"                       % zioPreludeVersion,
      "dev.zio"              %% "zio-streams"                       % zioVersion,
      "eu.timepit"           %% "refined"                           % "0.11.1",
      // add the silencer lib for scala 2.13 in order to compile with scala 3.3.0 until https://github.com/zio/zio-config/pull/1171 is merged
      // resolves problems when `sbt doc` failed with
      // [error] -- Error: typesafe/shared/src/main/scala/zio/config/typesafe/TypesafeConfigSource.scala:15:0
      // [error] undefined: new com.github.ghik.silencer.silent #
      "com.github.ghik" % "silencer-lib_2.13.11" % "1.17.13",

      // logging
      "dev.zio" %% "zio-logging"               % zioLoggingVersion,
      "dev.zio" %% "zio-logging-slf4j2-bridge" % zioLoggingVersion,

      // test
      "dev.zio"      %% "zio-mock"               % zioMockVersion % Test,
      "dev.zio"      %% "zio-test"               % zioVersion     % Test,
      "dev.zio"      %% "zio-test-junit"         % zioVersion     % Test,
      "dev.zio"      %% "zio-test-magnolia"      % zioVersion     % Test,
      "dev.zio"      %% "zio-test-sbt"           % zioVersion     % Test,
      "org.scoverage" % "sbt-scoverage_2.12_1.0" % "2.0.10"       % Test
    ),
    testFrameworks                       := Seq(new TestFramework("zio.test.sbt.ZTestFramework")),
    Docker / dockerRepository            := Some("daschswiss"),
    Docker / packageName                 := "dsp-ingest",
    dockerExposedPorts                   := Seq(3340),
    Docker / defaultLinuxInstallLocation := "/sipi",
    dockerUpdateLatest                   := true,
    dockerBaseImage                      := s"daschswiss/knora-sipi:$sipiVersion",
    dockerBuildxPlatforms                := Seq("linux/arm64/v8", "linux/amd64"),
    dockerCommands += Cmd(
      """HEALTHCHECK --interval=30s --timeout=10s --retries=3 --start-period=30s \
        |CMD curl -sS --fail 'http://localhost:3340/health' || exit 1""".stripMargin
    ),
    // Install Temurin Java 21 https://adoptium.net/de/installation/linux/
    dockerCommands += Cmd(
      "RUN",
      "apt install -y wget apt-transport-https && wget -qO - https://packages.adoptium.net/artifactory/api/gpg/key/public | gpg --dearmor | tee /etc/apt/trusted.gpg.d/adoptium.gpg > /dev/null"
    ),
    dockerCommands += Cmd(
      "RUN",
      "echo \"deb https://packages.adoptium.net/artifactory/deb $(awk -F= '/^VERSION_CODENAME/{print$2}' /etc/os-release) main\" | tee /etc/apt/sources.list.d/adoptium.list && apt update && apt upgrade -y && apt install -y temurin-21-jre && apt clean"
    ),
    dockerCommands := dockerCommands.value.filterNot {
      case Cmd("USER", args @ _*) => true
      case cmd                    => false
    }
  )
