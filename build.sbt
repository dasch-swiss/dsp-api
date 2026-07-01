import com.typesafe.sbt.SbtNativePackager.autoImport.NativePackagerHelper.*
import com.typesafe.sbt.packager.docker.DockerPlugin.autoImport.Docker
import com.typesafe.sbt.packager.docker.DockerPlugin.autoImport.dockerRepository
import com.typesafe.sbt.packager.docker.Cmd
import com.typesafe.sbt.packager.docker.DockerPermissionStrategy
import com.typesafe.sbt.packager.docker.DockerStageBreak
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

// sttp-client4 4.0.25 pulls in zio-json 0.9.0, while some transitive deps (zio-schema-json,
// tapir-json-zio) still request 0.7.x. zio-json is API-compatible across this range, so allow
// the higher version to be selected instead of failing on the early-semver eviction check.
ThisBuild / libraryDependencySchemes += "dev.zio" %% "zio-json" % VersionScheme.Always

lazy val buildCommit = ("git rev-parse --short HEAD" !!).trim
lazy val buildTime   = sys.env.getOrElse("BUILD_TIME", "dev")

lazy val knoraSipiVersion = gitVersion

lazy val aggregatedProjects: Seq[ProjectReference] = Seq(webapi, sipi, testkit, it, e2e, bagit, jwt, shaclValidator)

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
    jwt,
    shaclValidator,
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
  "; all webapi/headerCreate webapi/Test/headerCreate testkit/headerCreate test-it/headerCreate test-it/Test/headerCreate test-e2e/headerCreate test-e2e/Test/headerCreate bagit/headerCreate bagit/Test/headerCreate jwt/headerCreate jwt/Test/headerCreate shacl-validator/headerCreate shacl-validator/Test/headerCreate",
)
addCommandAlias(
  "headerCheckAll",
  "; all webapi/headerCheck webapi/Test/headerCheck testkit/headerCheck test-it/headerCheck test-it/Test/headerCheck test-e2e/headerCheck test-e2e/Test/headerCheck bagit/headerCheck bagit/Test/headerCheck jwt/headerCheck jwt/Test/headerCheck shacl-validator/headerCheck shacl-validator/Test/headerCheck",
)
addCommandAlias("check", "; all root/scalafmtSbtCheck root/scalafmtCheckAll; root/scalafixAll --check; headerCheckAll")
addCommandAlias("test-it", "test-it/test")
addCommandAlias("test-e2e", "test-e2e/test")

//////////////////////////////////////
// DSP's custom SIPI
//////////////////////////////////////

// Sipi v5.0.0 ships a distroless `daschswiss/sipi` image (rules_oci on
// distroless_base) with the static binary at `/sbin/sipi` and the runtime
// tree at `/sipi/{config,scripts,server,images,cache}`. This subproject is a
// thin overlay: COPY our Lua scripts on top of `/sipi/scripts/` and set an
// exec-form HEALTHCHECK that uses `sipi health` (no curl, no shell).
//
// We override `Docker / dockerCommands` wholesale because the plugin's
// default JVM scaffolding (USER/WORKDIR/useradd/chmod/ADD universal-staging/
// ENTRYPOINT) is irrelevant for an overlay-only image and depends on a
// shell that distroless doesn't ship. `dockerPermissionStrategy := None`
// makes the no-chmod intent explicit. No `USER` directive is emitted —
// the container inherits root from the upstream base because Sipi reads
// NFS-mounted assets owned by orchestrator-controlled uids.
lazy val sipi: Project = Project(id = "sipi", base = file("sipi"))
  .enablePlugins(DockerPlugin)
  .settings(
    Compile / packageDoc / mappings   := Seq(),
    Compile / packageSrc / mappings   := Seq(),
    Docker / dockerRepository         := Some("daschswiss"),
    Docker / packageName              := "knora-sipi",
    Docker / maintainer               := "support@dasch.swiss",
    dockerUpdateLatest                := true,
    dockerBuildxPlatforms             := Seq("linux/arm64/v8", "linux/amd64"),
    Docker / dockerPermissionStrategy := DockerPermissionStrategy.None,
    // Already declared on the upstream `daschswiss/sipi` OCI image; we
    // re-declare here so sbt-native-packager's validation reports happy.
    Docker / dockerExposedPorts ++= Seq(1024),
    // Stage our Lua scripts into the Docker build context at scripts/<name>.
    Docker / mappings := directory("sipi/scripts").map { case (f, _) =>
      f -> s"scripts/${f.getName}"
    },
    Docker / dockerCommands := Seq(
      Cmd("FROM", Dependencies.sipiImage),
      Cmd("LABEL", "maintainer=support@dasch.swiss"),
      Cmd("COPY", "scripts/", "/sipi/scripts/"),
      Cmd("EXPOSE", "1024"),
      Cmd(
        "HEALTHCHECK",
        "--interval=30s",
        "--timeout=10s",
        "--retries=3",
        "--start-period=30s",
        "CMD",
        """["/sbin/sipi", "health", "--port", "1024"]""",
      ),
    ),
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
  .dependsOn(bagit, jwt, shaclValidator)
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
  .enablePlugins(JavaAppPackaging, DockerPlugin, JavaAgent, BuildInfoPlugin, HeaderPlugin)
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
    dockerBaseImage           := "eclipse-temurin:25-jre-noble",
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
      "apt-get update && apt-get install -y curl jq && rm -rf /var/lib/apt/lists/*",
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
    // Disable OTel Java agent auto-instrumentation for HTTP client and Netty
    // to avoid duplicate spans (dsp-api traces these manually via sttp4 tracing backend and ZIO middleware)
    dockerCommands += Cmd("ENV", """OTEL_INSTRUMENTATION_JAVA_HTTP_CLIENT_ENABLED="false""""),
    dockerCommands += Cmd("ENV", """OTEL_INSTRUMENTATION_NETTY_ENABLED="false""""),
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
// JWT (minimal HS256 JWT library)
//////////////////////////////////////

lazy val jwt: Project = Project(id = "jwt", base = file("modules/jwt"))
  .settings(buildSettings)
  .settings(
    scalacOptions ++= customScalacOptions,
    logLevel := Level.Info,
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
    libraryDependencies ++= Seq(Dependencies.zio, Dependencies.zioJson) ++
      Seq(Dependencies.zioTest, Dependencies.zioTestSbt).map(_ % Test),
    publish / skip := true,
    name           := "jwt",
  )
  .enablePlugins(HeaderPlugin)

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
// SHACL VALIDATOR
//////////////////////////////////////

lazy val shaclValidator: Project = Project(id = "shacl-validator", base = file("modules/shacl-validator"))
  .settings(buildSettings)
  .settings(
    scalacOptions ++= customScalacOptions,
    logLevel := Level.Info,
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
    libraryDependencies ++= Dependencies.shaclValidatorDependencies ++ Dependencies.shaclValidatorTestDependencies,
    publish / skip := true,
    name           := "shacl-validator",
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
      Dependencies.openTelemetrySdkTesting,
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
    // SearchResponderV2GravsearchSpanE2ESpec asserts on the OpenTelemetry spans captured by an
    // in-memory exporter that it wires in via the overridable `E2EZSpec.otelLayer`. In a shared
    // forked JVM the OTel `Tracing` service is effectively shared across specs (the first spec to
    // build it wins), which silently bypasses the override and leaves the exporter empty. Run that
    // spec in its own JVM so its exporter-backed layer is the one in effect — every other spec
    // keeps sharing a single JVM as before.
    Test / testGrouping := {
      val opts               = (Test / forkOptions).value
      val (isolated, shared) = (Test / definedTests).value.partition(
        _.name.contains("SearchResponderV2GravsearchSpanE2ESpec"),
      )
      val groups = Tests.Group("shared", shared, Tests.SubProcess(opts)) +:
        isolated.map(t => Tests.Group(t.name, Seq(t), Tests.SubProcess(opts)))
      groups.filter(_.tests.nonEmpty)
    },
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
    .dependsOn(bagit, jwt)
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
      libraryDependencies ++= db ++ tapir ++ metrics ++ zioSeq ++ zioSttpClient ++ openTelemetryWithSentry ++ Seq(
        "commons-io"  % "commons-io"                        % "2.22.0",
        "dev.zio"    %% "zio-config"                        % ZioConfigVersion,
        "dev.zio"    %% "zio-config-magnolia"               % ZioConfigVersion,
        "dev.zio"    %% "zio-config-typesafe"               % ZioConfigVersion,
        "dev.zio"    %% "zio-json"                          % ZioJsonVersion,
        "dev.zio"    %% "zio-json-interop-refined"          % ZioJsonVersion,
        "dev.zio"    %% "zio-metrics-connectors"            % ZioMetricsConnectorsVersion,
        "dev.zio"    %% "zio-metrics-connectors-prometheus" % ZioMetricsConnectorsVersion,
        "eu.timepit" %% "refined"                           % "0.11.4",

        // csv for reports
        "com.github.tototoshi" %% "scala-csv" % "2.0.0",

        // logging
        "dev.zio" %% "zio-logging"               % ZioLoggingVersion,
        "dev.zio" %% "zio-logging-slf4j2-bridge" % ZioLoggingVersion,
      ) ++ ingestTest,
      testFrameworks            := Seq(new TestFramework("zio.test.sbt.ZTestFramework")),
      Docker / dockerRepository := Some("daschswiss"),
      Docker / packageName      := "dsp-ingest",
      dockerExposedPorts        := Seq(3340),
      // Kept at /sipi: dsp-ingest's app tree at /sipi/{bin,lib,conf}
      // doesn't collide with the Sipi runtime tree at
      // /sipi/{config,scripts,server,images,cache} — disjoint subfolder
      // names merge cleanly under /sipi/ via Docker's `COPY` semantics.
      Docker / defaultLinuxInstallLocation := "/sipi",
      dockerUpdateLatest                   := true,
      // Was: s"daschswiss/knora-sipi:$knoraSipiVersion". Sipi v5.0.0 is
      // distroless (no apt-get, no chmod), so we can't keep deriving from
      // it. Move to a Debian + Temurin 25 JRE base that matches knora-api
      // (build.sbt:233) and extract the Sipi runtime via a multi-stage
      // COPY --from=sipi-source below.
      dockerBaseImage       := "eclipse-temurin:25-jre-noble",
      dockerBuildxPlatforms := Seq("linux/arm64/v8", "linux/amd64"),
      // Exec-form HEALTHCHECK. `eclipse-temurin:25-jre-noble` doesn't ship
      // curl, but we extract the static `/sbin/sipi` binary below. `sipi
      // health` probes `http://127.0.0.1:<port>/health` and exits 0/1 — the
      // exact contract dsp-ingest's /health endpoint already satisfies, so
      // we reuse the binary we already need instead of apt-installing curl.
      dockerCommands += Cmd(
        "HEALTHCHECK",
        "--interval=30s",
        "--timeout=10s",
        "--retries=3",
        "--start-period=30s",
        "CMD",
        """["/sbin/sipi", "health", "--port", "3340"]""",
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
      // Disable OTel Java agent auto-instrumentation for HTTP client and Netty
      // to avoid duplicate spans (dsp-ingest traces these manually via sttp4 tracing backend and ZIO middleware)
      dockerCommands += Cmd("ENV", """OTEL_INSTRUMENTATION_JAVA_HTTP_CLIENT_ENABLED="false""""),
      dockerCommands += Cmd("ENV", """OTEL_INSTRUMENTATION_NETTY_ENABLED="false""""),
      // Single transformation that combines three responsibilities:
      //   1. Strip the plugin-default `USER` directive — Sipi reads NFS-
      //      mounted assets owned by orchestrator-controlled uids, so the
      //      container runs as root (uid=0). See knora-api at line 268
      //      and the original filterNot block this replaces.
      //   2. Prepend a Sipi-extraction builder stage. Sipi v5's binary is
      //      static and the runtime tree is just files, so they can be
      //      COPY --from=`-extracted onto any base image.
      //   3. Append COPY --from=sipi-source directives so they land
      //      inside the runtime stage (after the plugin's default
      //      MultiStage stage0 → final-stage chown chain).
      Docker / dockerCommands := {
        val cmds = (Docker / dockerCommands).value.filterNot {
          case Cmd("USER", _*) => true
          case _               => false
        }
        val sipiSourceStage = Seq(
          Cmd("FROM", s"daschswiss/knora-sipi:$knoraSipiVersion", "AS", "sipi-source"),
          DockerStageBreak,
        )
        val copyFromSipi = Seq(
          Cmd("COPY", "--from=sipi-source", "/sbin/sipi", "/sbin/sipi"),
          Cmd("COPY", "--from=sipi-source", "/sipi", "/sipi"),
          Cmd("COPY", "--from=sipi-source", "/usr/bin/tini", "/usr/bin/tini"),
          Cmd("COPY", "--from=sipi-source", "/usr/bin/ffmpeg", "/usr/bin/ffmpeg"),
          Cmd("COPY", "--from=sipi-source", "/usr/bin/ffprobe", "/usr/bin/ffprobe"),
        )
        sipiSourceStage ++ cmds ++ copyFromSipi
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
