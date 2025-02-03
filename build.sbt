import com.typesafe.sbt.packager.docker.Cmd
import com.typesafe.sbt.packager.docker.DockerPlugin.autoImport.{Docker, dockerRepository}
import sbt.Keys.testFrameworks

import scala.collection.Seq
import scala.sys.process.*

addCommandAlias("fmt", "scalafmt; Test / scalafmt; integration/Test/scalafmt;")
addCommandAlias("fmtCheck", "scalafmtCheck; Test / scalafmtCheck; integration/Test/ scalafmtCheck;")
addCommandAlias("headerCreateAll", "; all root/headerCreate Test/headerCreate; integration/Test/headerCreate")
addCommandAlias("headerCheckAll", "; all root/headerCheck Test/headerCheck; integration/Test/headerCheck")

val flywayVersion               = "11.1.1"
val hikariVersion               = "6.2.1"
val quillVersion                = "4.8.6"
val sipiVersion                 = "v31.3.0"
val sqliteVersion               = "3.48.0.0"
val tapirVersion                = "1.11.13"
val testContainersVersion       = "1.20.4"
val zioConfigVersion            = "4.0.3"
val zioJsonVersion              = "0.7.4"
val zioLoggingVersion           = "2.4.0"
val zioMetricsConnectorsVersion = "2.3.1"
val zioMockVersion              = "1.0.0-RC12"
val zioNioVersion               = "2.0.2"
val zioPreludeVersion           = "1.0.0-RC37"
val zioSchemaVersion            = "1.5.0"
val zioVersion                  = "2.1.14"

val gitCommit  = ("git rev-parse HEAD" !!).trim
val gitVersion = ("git describe --tag --dirty --abbrev=7 --always  " !!).trim

ThisBuild / organization      := "dasch.swiss"
ThisBuild / version           := gitVersion
ThisBuild / scalaVersion      := "3.3.5"
ThisBuild / fork              := true
ThisBuild / semanticdbEnabled := true

scalacOptions ++= Seq("-old-syntax", "-rewrite")

val tapir = Seq(
  "com.softwaremill.sttp.tapir" %% "tapir-json-zio"          % tapirVersion,
  "com.softwaremill.sttp.tapir" %% "tapir-refined"           % tapirVersion,
  "com.softwaremill.sttp.tapir" %% "tapir-swagger-ui-bundle" % tapirVersion,
  "com.softwaremill.sttp.tapir" %% "tapir-zio-http-server"   % tapirVersion,
  "com.softwaremill.sttp.tapir" %% "tapir-openapi-docs"      % tapirVersion,
)

val metrics = Seq(
  "com.softwaremill.sttp.tapir" %% "tapir-zio-metrics"                 % tapirVersion,
  "dev.zio"                     %% "zio-metrics-connectors"            % zioMetricsConnectorsVersion,
  "dev.zio"                     %% "zio-metrics-connectors-prometheus" % zioMetricsConnectorsVersion,
)

val db = Seq(
  "org.xerial"   % "sqlite-jdbc"    % sqliteVersion,
  "org.flywaydb" % "flyway-core"    % flywayVersion,
  "com.zaxxer"   % "HikariCP"       % hikariVersion,
  "io.getquill" %% "quill-jdbc-zio" % quillVersion,
)

val zio = Seq(
  "dev.zio" %% "zio"                   % zioVersion,
  "dev.zio" %% "zio-streams"           % zioVersion,
  "dev.zio" %% "zio-schema"            % zioSchemaVersion,
  "dev.zio" %% "zio-schema-derivation" % zioSchemaVersion,
  "dev.zio" %% "zio-nio"               % zioNioVersion,
  "dev.zio" %% "zio-prelude"           % zioPreludeVersion,
)

val test = Seq(
  "dev.zio"      %% "zio-mock"               % zioMockVersion % Test,
  "dev.zio"      %% "zio-http"               % "3.0.1"        % Test,
  "dev.zio"      %% "zio-test"               % zioVersion     % Test,
  "dev.zio"      %% "zio-test-junit"         % zioVersion     % Test,
  "dev.zio"      %% "zio-test-magnolia"      % zioVersion     % Test,
  "dev.zio"      %% "zio-test-sbt"           % zioVersion     % Test,
  "org.scoverage" % "sbt-scoverage_2.12_1.0" % "2.3.0"        % Test,
)

val integrationTest = Seq(
  "org.testcontainers" % "testcontainers" % testContainersVersion % Test,
)

lazy val year = java.time.LocalDate.now().getYear
val projectLicense = Some(
  HeaderLicense.Custom(
    s"""|Copyright © 2021 - $year Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
        |SPDX-License-Identifier: Apache-2.0
        |""".stripMargin,
  ),
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
      BuildInfoKey.action("gitCommit")(gitCommit),
    ),
    buildInfoOptions += BuildInfoOption.BuildTime,
    buildInfoPackage    := "swiss.dasch.version",
    Compile / mainClass := Some("swiss.dasch.Main"),
  )
  .settings(
    name          := "dsp-ingest",
    headerLicense := projectLicense,
    libraryDependencies ++= db ++ tapir ++ metrics ++ zio ++ Seq(
      "com.github.jwt-scala"          %% "jwt-zio-json"                      % "10.0.1",
      "commons-io"                     % "commons-io"                        % "2.18.0",
      "dev.zio"                       %% "zio-config"                        % zioConfigVersion,
      "dev.zio"                       %% "zio-config-magnolia"               % zioConfigVersion,
      "dev.zio"                       %% "zio-config-typesafe"               % zioConfigVersion,
      "dev.zio"                       %% "zio-json"                          % zioJsonVersion,
      "dev.zio"                       %% "zio-json-interop-refined"          % zioJsonVersion,
      "dev.zio"                       %% "zio-metrics-connectors"            % zioMetricsConnectorsVersion,
      "dev.zio"                       %% "zio-metrics-connectors-prometheus" % zioMetricsConnectorsVersion,
      "eu.timepit"                    %% "refined"                           % "0.11.3",
      "com.softwaremill.sttp.client3" %% "zio"                               % "3.10.2",

      // csv for reports
      "com.github.tototoshi" %% "scala-csv" % "2.0.0",

      // logging
      "dev.zio" %% "zio-logging"               % zioLoggingVersion,
      "dev.zio" %% "zio-logging-slf4j2-bridge" % zioLoggingVersion,
    ) ++ test,
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
        |CMD curl -sS --fail 'http://localhost:3340/health' || exit 1""".stripMargin,
    ),
    // Install Temurin Java 21 https://adoptium.net/de/installation/linux/
    dockerCommands += Cmd(
      "RUN",
      "apt update && apt install -y wget apt-transport-https && wget -qO - https://packages.adoptium.net/artifactory/api/gpg/key/public | gpg --dearmor | tee /etc/apt/trusted.gpg.d/adoptium.gpg > /dev/null",
    ),
    dockerCommands += Cmd(
      "RUN",
      "echo \"deb https://packages.adoptium.net/artifactory/deb $(awk -F= '/^VERSION_CODENAME/{print$2}' /etc/os-release) main\" | tee /etc/apt/sources.list.d/adoptium.list && apt update && apt upgrade -y && apt install -y temurin-21-jre && apt clean",
    ),
    dockerCommands := dockerCommands.value.filterNot {
      case Cmd("USER", args @ _*) => true
      case cmd                    => false
    },
  )

lazy val integration = (project in file("integration"))
  .dependsOn(root)
  .settings(
    publish / skip := true,
    headerLicense  := projectLicense,
    libraryDependencies ++= test ++ integrationTest,
    testFrameworks := Seq(new TestFramework("zio.test.sbt.ZTestFramework")),
  )
