import com.typesafe.sbt.SbtNativePackager.autoImport.NativePackagerHelper.*
import com.typesafe.sbt.packager.docker.DockerPlugin.autoImport.Docker
import com.typesafe.sbt.packager.docker.DockerPlugin.autoImport.dockerRepository
import com.typesafe.sbt.packager.docker.Cmd
import com.typesafe.sbt.packager.docker.ExecCmd
import sbt.*
import sbt.Keys.version

import scala.language.postfixOps
import sbt.io._
import scala.sys.process.*
import org.knora.Dependencies
import org.knora.LocalSettings

import com.typesafe.sbt.packager.docker.Cmd
import com.typesafe.sbt.packager.docker.DockerPlugin.autoImport.{Docker, dockerRepository}
import sbt.Keys.testFrameworks

import scala.collection.Seq
import scala.sys.process.*

//////////////////////////////////////
// GLOBAL SETTINGS
//////////////////////////////////////

// when true enables run cancellation w/o exiting sbt
// use Ctrl-c to stop current task but not quit SBT

Global / cancelable := true

Global / scalaVersion      := Dependencies.ScalaVersion
Global / semanticdbEnabled := true
Global / semanticdbVersion := scalafixSemanticdb.revision

val gitCommit = ("git rev-parse HEAD" !!).trim
val gitBranch = Option("git rev-parse --abbrev-ref HEAD" !!)
  .map(_.trim)
  .filter(b => !(b == "main" || b == "HEAD"))
  .map(_.replace('/', '-'))
val gitVersion = ("git describe --tag --dirty --abbrev=7 --always  " !!).trim + gitBranch.fold("")("-" + _)

ThisBuild / version := gitVersion

lazy val buildCommit = ("git rev-parse --short HEAD" !!).trim
lazy val buildTime   = sys.env.getOrElse("BUILD_TIME", "dev")

lazy val knoraSipiVersion = gitVersion

lazy val aggregatedProjects: Seq[ProjectReference] = Seq(webapi, sipi, testkit, it, e2e, bagit)

lazy val year           = java.time.LocalDate.now().getYear
lazy val projectLicense = Some(
  HeaderLicense.Custom(
    s"""|Copyright © 2021 - $year Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
        |SPDX-License-Identifier: Apache-2.0
        |""".stripMargin,
  ),
)
lazy val buildSettings = Seq(
  organization  := "org.knora",
  version       := (ThisBuild / version).value,
  headerLicense := projectLicense,
)

lazy val rootBaseDir = ThisBuild / baseDirectory

lazy val dockerImageTag = taskKey[String]("Returns the docker image tag")

lazy val root: Project = Project(id = "root", file("."))
  .aggregate(
    webapi,
    sipi,
    testkit,
    it,
    e2e,
    ingest,
    bagit,
  )
  .settings(
    // values set for all sub-projects
    // These are normal sbt settings to configure for release, skip if already defined
    Global / onChangedBuildSource := ReloadOnSourceChanges,
    ThisBuild / licenses          := Seq("Apache License, Version 2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
    ThisBuild / homepage          := Some(url("https://github.com/dasch-swiss/dsp-api")),
    ThisBuild / scmInfo           := Some(
      ScmInfo(url("https://github.com/dasch-swiss/dsp-api"), "scm:git:git@github.com:dasch-swiss/dsp-api.git"),
    ),
    Global / scalaVersion := Dependencies.ScalaVersion,
    // override generated version string because docker hub rejects '+' in tags
    ThisBuild / version ~= (_.replace('+', '-')),
    dockerImageTag := (ThisBuild / version).value,
    publish / skip := true,
    name           := "dsp-api",
  )

addCommandAlias("fmt", "; all root/scalafmtSbt root/scalafmtAll; root/scalafixAll")
addCommandAlias(
  "headerCreateAll",
  "; all webapi/headerCreate webapi/Test/headerCreate testkit/headerCreate test-it/headerCreate test-it/Test/headerCreate test-e2e/headerCreate test-e2e/Test/headerCreate bagit/headerCreate bagit/Test/headerCreate",
)
addCommandAlias(
  "headerCheckAll",
  "; all webapi/headerCheck webapi/Test/headerCheck testkit/headerCheck test-it/headerCheck test-it/Test/headerCheck test-e2e/headerCheck test-e2e/Test/headerCheck bagit/headerCheck bagit/Test/headerCheck",
)
addCommandAlias("check", "; all root/scalafmtSbtCheck root/scalafmtCheckAll; root/scalafixAll --check; headerCheckAll")
addCommandAlias("test-it", "test-it/test")
addCommandAlias("test-e2e", "test-e2e/test")

//////////////////////////////////////
// DSP's custom SIPI
//////////////////////////////////////

lazy val sipi: Project = Project(id = "sipi", base = file("sipi"))
  .enablePlugins(DockerPlugin)
  .settings(
    Compile / packageDoc / mappings := Seq(),
    Compile / packageSrc / mappings := Seq(),
    Docker / dockerRepository       := Some("daschswiss"),
    Docker / packageName            := "knora-sipi",
    dockerUpdateLatest              := true,
    dockerBaseImage                 := Dependencies.sipiImage,
    dockerBuildxPlatforms           := Seq("linux/arm64/v8", "linux/amd64"),
    Docker / maintainer             := "support@dasch.swiss",
    Docker / dockerExposedPorts ++= Seq(1024),
    Docker / defaultLinuxInstallLocation := "/sipi",
    Universal / mappings ++= {
      directory("sipi/scripts")
    },
    dockerCommands += Cmd(
      """HEALTHCHECK --interval=30s --timeout=30s --retries=4 --start-period=30s \
        |CMD bash /sipi/scripts/healthcheck.sh || exit 1""".stripMargin,
    ),
    // use filterNot to return all items that do NOT meet the criteria
    dockerCommands := dockerCommands.value.filterNot {

      // ExecCmd is a case class, and args is a varargs variable, so you need to bind it with @
      // remove ENTRYPOINT
      case ExecCmd("ENTRYPOINT", args @ _*) => true

      // remove CMD
      case ExecCmd("CMD", args @ _*) => true

      case Cmd("USER", args @ _*) => true

      // don't filter the rest; don't filter out anything that doesn't match a pattern
      case cmd => false
    },
  )

//////////////////////////////////////
// WEBAPI (./webapi)
//////////////////////////////////////

run / connectInput := true

lazy val webApiCommonSettings = Seq(
  name := "webapi",
)
testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")

val customScalacOptions = Seq(
  "-feature",
  "-unchecked",
  "-deprecation",
  "-Yresolve-term-conflict:package",
  "-Wconf:src=target/.*:s", // silence TWIRL templates unused imports warnings
  "-Wvalue-discard",
  "-Xmax-inlines:64",
  "-Wunused:all",
  "-Xfatal-warnings",
  "-Dotel.java.global-autoconfigure.enabled=true",
)

lazy val webapi: Project = Project(id = "webapi", base = file("webapi"))
  .dependsOn(bagit)
  .settings(buildSettings)
  .settings(
    inConfig(Test) {
      Defaults.testSettings
    },
    Test / testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
    run / fork                := true, // run in a forked JVM so Ctrl+C shuts down cleanly
    Test / fork               := true, // run tests in a forked JVM
    Test / testForkedParallel := true, // run tests in parallel
    Test / parallelExecution  := true, // run tests in parallel
    libraryDependencies ++= Dependencies.webapiDependencies ++ Dependencies.webapiTestDependencies,
  )
  .enablePlugins(SbtTwirl, JavaAppPackaging, DockerPlugin, JavaAgent, BuildInfoPlugin, HeaderPlugin)
  .settings(
    name := "webapi",
    resolvers ++= Seq(
      "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots",
    ),
    libraryDependencies ++= Dependencies.webapiDependencies ++ Dependencies.webapiTestDependencies,
  )
  .settings(
    // use packaged jars (through packageBin) on classpaths instead of class directories for production
    Compile / exportJars := true,
  )
  .settings(
    scalacOptions ++= customScalacOptions,
    logLevel := Level.Info,
  )
  .settings(LocalSettings.localScalacOptions: _*)
  .settings(
    // prepare for publishing

    // Skip packageDoc and packageSrc task on stage
    Compile / packageDoc / mappings := Seq(),
    Compile / packageSrc / mappings := Seq(),
    // define folders inside container
    Universal / mappings ++= {
      // copy the scripts folder
      directory("webapi/scripts") ++
        // copy configuration files to config directory
        contentOf("webapi/src/main/resources").toMap.mapValues("config/" + _)
    },
    // add 'config' directory to the classpath of the start script,
    Universal / scriptClasspath := Seq(
      "webapi/scripts",
      "webapi/src/main/resources/knora-ontologies",
      "../config/",
    ) ++ scriptClasspath.value,
    // need this here, so that the Manifest inside the jars has the correct main class set.
    Compile / mainClass := Some("org.knora.webapi.Main"),
    // add dockerCommands used to create the image
    // docker:stage, docker:publishLocal, docker:publish, docker:clean
    Docker / dockerRepository := Some("daschswiss"),
    Docker / packageName      := "knora-api",
    dockerUpdateLatest        := true,
    dockerBaseImage           := "eclipse-temurin:21-jre-noble",
    dockerBuildxPlatforms     := Seq("linux/arm64/v8", "linux/amd64"),
    Docker / maintainer       := "support@dasch.swiss",
    Docker / dockerExposedPorts ++= Seq(3333, 3339),
    Docker / defaultLinuxInstallLocation := "/opt/docker",
    dockerLabels                         := Map[String, Option[String]](
      "org.opencontainers.image.version"  -> (ThisBuild / version).value.some,
      "org.opencontainers.image.revision" -> Some(gitCommit),
      "org.opencontainers.image.source"   -> Some("github.com/dasch-swiss/dsp-api"),
    ).collect { case (key, Some(value)) => (key, value) },
    dockerCommands += Cmd(
      "RUN",
      "apt-get update && apt-get install -y jq && rm -rf /var/lib/apt/lists/*",
    ), // install jq for container healthcheck
    dockerCommands += Cmd(
      """HEALTHCHECK --interval=30s --timeout=10s --retries=3 --start-period=30s \
        |CMD bash /opt/docker/scripts/healthcheck.sh || exit 1""".stripMargin,
    ),
    // Add Opentelemetry java agent and Grafana Pyroscope extension
    dockerCommands += Cmd(
      "ADD",
      s"https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/${Dependencies.otelAgentVersion}/opentelemetry-javaagent.jar",
      "/usr/local/lib/opentelemetry-javaagent.jar",
    ),
    dockerCommands += Cmd(
      "ADD",
      s"https://github.com/grafana/otel-profiling-java/releases/download/${Dependencies.otelPyroscopeVersion}/pyroscope-otel.jar",
      "/usr/local/lib/pyroscope-otel.jar",
    ),
    // use filterNot to return all items that do NOT meet the criteria
    dockerCommands := dockerCommands.value.filterNot {
      // Remove USER command
      case Cmd("USER", args @ _*) => true

      // don't filter the rest; don't filter out anything that doesn't match a pattern
      case cmd => false
    },
  )
  .settings(
    buildInfoKeys ++= Seq[BuildInfoKey](
      name,
      version,
      "sipi"        -> Dependencies.sipiImage,
      "fuseki"      -> Dependencies.fusekiImage,
      "buildCommit" -> buildCommit,
      "buildTime"   -> buildTime,
    ),
    buildInfoPackage := "org.knora.webapi.http.version",
  )

//////////////////////////////////////
// BAGIT (RFC 8493 library)
//////////////////////////////////////

lazy val bagit: Project = Project(id = "bagit", base = file("modules/bagit"))
  .settings(buildSettings)
  .settings(
    scalacOptions ++= customScalacOptions,
    logLevel := Level.Info,
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
    libraryDependencies ++= Dependencies.bagitDependencies ++ Dependencies.bagitTestDependencies,
    publish / skip := true,
    name           := "bagit",
  )
  .enablePlugins(HeaderPlugin)

//////////////////////////////////////
// TESTKIT (shared test utilities)
//////////////////////////////////////

run / connectInput := true

lazy val testkit: Project = Project(id = "testkit", base = file("modules/testkit"))
  .dependsOn(webapi, sipi)
  .settings(buildSettings)
  .settings(
    Compile / packageDoc / mappings := Seq(),
    Compile / packageSrc / mappings := Seq(),
    scalacOptions ++= customScalacOptions,
    logLevel := Level.Info,
    // bring in test frameworks as compile-time for base specs
    libraryDependencies ++= Seq(
      Dependencies.zioTest,
      Dependencies.testcontainers,
      Dependencies.wiremock,
      Dependencies.dataFaker,
    ),
    publish / skip := true,
    name           := "testkit",
    resolvers ++= Seq("Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"),
  )
  .enablePlugins(HeaderPlugin)

//////////////////////////////////////
// IT tests (service/repo/util/Sipi)
//////////////////////////////////////

lazy val it: Project = Project(id = "test-it", base = file("modules/test-it"))
  .dependsOn(webapi, sipi, testkit)
  .settings(buildSettings)
  .settings(
    inConfig(Test) {
      Defaults.testSettings ++ Defaults.testTasks ++ headerSettings(Test)
    },
    scalacOptions ++= customScalacOptions,
    logLevel := Level.Info,
    Test / testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
    Test / exportJars         := false,
    Test / fork               := true,
    Test / testForkedParallel := false,
    Test / parallelExecution  := false,
    Test / testOptions += Tests.Argument("-oDF"), // full stack traces and durations
    Test / baseDirectory := (ThisBuild / baseDirectory).value,
    libraryDependencies ++= Dependencies.webapiDependencies ++ Dependencies.webapiTestDependencies ++ Dependencies.integrationTestDependencies,
  )
  .settings(LocalSettings.localScalacOptions: _*)
  .enablePlugins(HeaderPlugin)
  .settings(
    name := "test-it",
    resolvers ++= Seq(
      "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots",
    ),
  )

//////////////////////////////////////
// E2E tests (HTTP API routes)
//////////////////////////////////////

lazy val e2e: Project = Project(id = "test-e2e", base = file("modules/test-e2e"))
  .dependsOn(webapi, sipi, testkit)
  .settings(buildSettings)
  .settings(
    inConfig(Test) {
      Defaults.testSettings ++ Defaults.testTasks ++ headerSettings(Test)
    },
    scalacOptions ++= customScalacOptions,
    logLevel := Level.Info,
    Test / testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
    Test / exportJars         := false,
    Test / fork               := true,
    Test / testForkedParallel := false,
    Test / parallelExecution  := false,
    Test / testOptions += Tests.Argument("-oDF"),
    Test / baseDirectory := (ThisBuild / baseDirectory).value,
    libraryDependencies ++= Dependencies.webapiDependencies ++ Dependencies.webapiTestDependencies ++ Dependencies.integrationTestDependencies,
  )
  .settings(LocalSettings.localScalacOptions: _*)
  .enablePlugins(HeaderPlugin)
  .settings(
    name := "test-e2e",
    resolvers ++= Seq(
      "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots",
    ),
  )

lazy val ingest = {
  import Dependencies._

  Project(id = "ingest", file("ingest"))
    .dependsOn(bagit)
    .enablePlugins(JavaAppPackaging, DockerPlugin, BuildInfoPlugin)
    .settings(
      scalacOptions ++= Seq("-old-syntax", "-rewrite"),
      scalacOptions ++= customScalacOptions,
      buildInfoKeys := Seq[BuildInfoKey](
        name,
        version,
        scalaVersion,
        sbtVersion,
        BuildInfoKey("knoraSipiVersion", knoraSipiVersion),
        BuildInfoKey.action("gitCommit")(gitCommit),
      ),
      buildInfoKeys += BuildInfoKey("buildTime" -> sys.env.getOrElse("BUILD_TIME", "dev")),
      buildInfoPackage    := "swiss.dasch.version",
      Compile / mainClass := Some("swiss.dasch.Main"),
    )
    .settings(
      name          := "dsp-ingest",
      headerLicense := projectLicense,
      libraryDependencies ++= db ++ tapir ++ metrics ++ zioSeq ++ Seq(
        "com.github.jwt-scala"          %% "jwt-zio-json"                      % "11.0.3",
        "commons-io"                     % "commons-io"                        % "2.21.0",
        "dev.zio"                       %% "zio-config"                        % ZioConfigVersion,
        "dev.zio"                       %% "zio-config-magnolia"               % ZioConfigVersion,
        "dev.zio"                       %% "zio-config-typesafe"               % ZioConfigVersion,
        "dev.zio"                       %% "zio-json"                          % ZioJsonVersion,
        "dev.zio"                       %% "zio-json-interop-refined"          % ZioJsonVersion,
        "dev.zio"                       %% "zio-metrics-connectors"            % ZioMetricsConnectorsVersion,
        "dev.zio"                       %% "zio-metrics-connectors-prometheus" % ZioMetricsConnectorsVersion,
        "eu.timepit"                    %% "refined"                           % "0.11.3",
        "com.softwaremill.sttp.client3" %% "zio"                               % "3.11.0",

        // csv for reports
        "com.github.tototoshi" %% "scala-csv" % "2.0.0",

        // logging
        "dev.zio" %% "zio-logging"               % ZioLoggingVersion,
        "dev.zio" %% "zio-logging-slf4j2-bridge" % ZioLoggingVersion,
      ) ++ ingestTest,
      testFrameworks                       := Seq(new TestFramework("zio.test.sbt.ZTestFramework")),
      Docker / dockerRepository            := Some("daschswiss"),
      Docker / packageName                 := "dsp-ingest",
      dockerExposedPorts                   := Seq(3340),
      Docker / defaultLinuxInstallLocation := "/sipi",
      dockerUpdateLatest                   := true,
      dockerBaseImage                      := s"daschswiss/knora-sipi:$knoraSipiVersion",
      dockerBuildxPlatforms                := Seq("linux/arm64/v8", "linux/amd64"),
      dockerCommands += Cmd(
        """HEALTHCHECK --interval=30s --timeout=10s --retries=3 --start-period=30s \
          |CMD curl -sS --fail 'http://localhost:3340/health' || exit 1""".stripMargin,
      ),
      // Install Temurin Java 21 https://adoptium.net/de/installation/linux/
      dockerCommands += Cmd(
        "RUN",
        "apt-get update && apt install -y wget apt-transport-https gpg && wget -qO - https://packages.adoptium.net/artifactory/api/gpg/key/public | gpg --dearmor | tee /etc/apt/trusted.gpg.d/adoptium.gpg > /dev/null",
      ),
      dockerCommands += Cmd(
        "RUN",
        "echo \"deb https://packages.adoptium.net/artifactory/deb $(awk -F= '/^VERSION_CODENAME/{print$2}' /etc/os-release) main\" | tee /etc/apt/sources.list.d/adoptium.list && apt-get update && apt-get install -y temurin-24-jre && rm -rf /var/lib/apt/lists/*",
      ),
      // Add Opentelemetry java agent and Grafana Pyroscope extension
      dockerCommands += Cmd(
        "ADD",
        s"https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/${otelAgentVersion}/opentelemetry-javaagent.jar",
        "/usr/local/lib/opentelemetry-javaagent.jar",
      ),
      dockerCommands += Cmd(
        "ADD",
        s"https://github.com/grafana/otel-profiling-java/releases/download/${otelPyroscopeVersion}/pyroscope-otel.jar",
        "/usr/local/lib/pyroscope-otel.jar",
      ),
      dockerCommands := dockerCommands.value.filterNot {
        case Cmd("USER", args @ _*) => true
        case cmd                    => false
      },
    )
}

lazy val ingestIntegration = (project in file("modules/test-ingest-integration"))
  .dependsOn(ingest)
  .settings(
    publish / skip := true,
    headerLicense  := projectLicense,
    libraryDependencies ++= Dependencies.ingestTest ++ Seq(Dependencies.testcontainers),
    testFrameworks := Seq(new TestFramework("zio.test.sbt.ZTestFramework")),
  )
