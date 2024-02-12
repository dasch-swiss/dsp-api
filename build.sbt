import com.typesafe.sbt.SbtNativePackager.autoImport.NativePackagerHelper.*
import com.typesafe.sbt.packager.docker.DockerPlugin.autoImport.Docker
import com.typesafe.sbt.packager.docker.DockerPlugin.autoImport.dockerRepository
import com.typesafe.sbt.packager.docker.Cmd
import com.typesafe.sbt.packager.docker.ExecCmd
import sbt.*
import sbt.Keys.version

import scala.language.postfixOps
import scala.sys.process.*

import org.knora.Dependencies

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

lazy val aggregatedProjects: Seq[ProjectReference] = Seq(webapi, sipi, integration)

lazy val buildSettings = Seq(
  organization := "org.knora",
  version      := (ThisBuild / version).value,
  headerLicense := Some(
    HeaderLicense.Custom(
      """|Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
         |SPDX-License-Identifier: Apache-2.0
         |""".stripMargin
    )
  )
)

lazy val rootBaseDir = ThisBuild / baseDirectory

lazy val dockerImageTag = taskKey[String]("Returns the docker image tag")

lazy val root: Project = Project(id = "root", file("."))
  .aggregate(
    webapi,
    sipi,
    integration
  )
  .settings(
    // values set for all sub-projects
    // These are normal sbt settings to configure for release, skip if already defined
    Global / onChangedBuildSource := ReloadOnSourceChanges,
    ThisBuild / licenses          := Seq("Apache License, Version 2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
    ThisBuild / homepage          := Some(url("https://github.com/dasch-swiss/dsp-api")),
    ThisBuild / scmInfo := Some(
      ScmInfo(url("https://github.com/dasch-swiss/dsp-api"), "scm:git:git@github.com:dasch-swiss/dsp-api.git")
    ),
    Global / scalaVersion := Dependencies.ScalaVersion,
    // override generated version string because docker hub rejects '+' in tags
    ThisBuild / version ~= (_.replace('+', '-')),
    dockerImageTag := (ThisBuild / version).value,
    publish / skip := true,
    name           := "dsp-api"
  )

addCommandAlias("fmt", "; all root/scalafmtSbt root/scalafmtAll; root/scalafixAll")
addCommandAlias(
  "headerCreateAll",
  "; all webapi/headerCreate webapi/Test/headerCreate integration/headerCreate"
)
addCommandAlias(
  "headerCheckAll",
  "; all webapi/headerCheck webapi/Test/headerCheck integration/headerCheck"
)
addCommandAlias("check", "; all root/scalafmtSbtCheck root/scalafmtCheckAll; root/scalafixAll --check; headerCheckAll")
addCommandAlias("it", "integration/test")

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
        |CMD bash /sipi/scripts/healthcheck.sh || exit 1""".stripMargin
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
    }
  )

//////////////////////////////////////
// WEBAPI (./webapi)
//////////////////////////////////////

run / connectInput := true

lazy val webApiCommonSettings = Seq(
  name := "webapi"
)
testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")

val customScalacOptions = Seq(
  "-Xsource:3",
  "-Wconf:msg=constructor modifiers are assumed:s",
  "-feature",
  "-unchecked",
  "-deprecation",
  "-Yresolve-term-conflict:package",
  "-Ymacro-annotations",
  "-Wunused:imports",
  "-Wunused:privates",
  "-Wunused:locals",
  "-Wunused:explicits",
  "-Wunused:implicits",
  "-Wunused:params",
  "-Wunused:patvars",
  "-Wdead-code",
  "-Wvalue-discard",
  "-Xlint:doc-detached",
  // silence twirl templates unused imports warnings
  "-Wconf:src=target/.*:s"
)

lazy val webapi: Project = Project(id = "webapi", base = file("webapi"))
  .settings(buildSettings)
  .settings(
    inConfig(Test) {
      Defaults.testSettings
    },
    Test / testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
    Test / fork               := true, // run tests in a forked JVM
    Test / testForkedParallel := true, // run tests in parallel
    Test / parallelExecution  := true, // run tests in parallel
    libraryDependencies ++= Dependencies.webapiDependencies ++ Dependencies.webapiTestDependencies
  )
  .enablePlugins(SbtTwirl, JavaAppPackaging, DockerPlugin, JavaAgent, BuildInfoPlugin, HeaderPlugin)
  .settings(
    name := "webapi",
    resolvers ++= Seq(
      "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"
    ),
    libraryDependencies ++= Dependencies.webapiDependencies ++ Dependencies.webapiTestDependencies
  )
  .settings(
    // add needed files to production jar
    Compile / packageBin / mappings ++= Seq(
      (rootBaseDir.value / "webapi" / "scripts" / "fuseki-repository-config.ttl.template") -> "webapi/scripts/fuseki-repository-config.ttl.template" // needed for initialization of triplestore
    ),
    // use packaged jars (through packageBin) on classpaths instead of class directories for production
    Compile / exportJars := true
  )
  .settings(
    scalacOptions ++= customScalacOptions,
    logLevel := Level.Info,
    javaAgents += Dependencies.aspectjweaver
  )
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
      "../config/"
    ) ++ scriptClasspath.value,
    // need this here, so that the Manifest inside the jars has the correct main class set.
    Compile / mainClass := Some("org.knora.webapi.Main"),
    // add dockerCommands used to create the image
    // docker:stage, docker:publishLocal, docker:publish, docker:clean
    Docker / dockerRepository := Some("daschswiss"),
    Docker / packageName      := "knora-api",
    dockerUpdateLatest        := true,
    dockerBaseImage           := "eclipse-temurin:21-jre-jammy",
    dockerBuildxPlatforms     := Seq("linux/arm64/v8", "linux/amd64"),
    Docker / maintainer       := "support@dasch.swiss",
    Docker / dockerExposedPorts ++= Seq(3333, 3339),
    Docker / defaultLinuxInstallLocation := "/opt/docker",
    dockerLabels := Map[String, Option[String]](
      "org.opencontainers.image.version"  -> (ThisBuild / version).value.some,
      "org.opencontainers.image.revision" -> Some(gitCommit),
      "org.opencontainers.image.source"   -> Some("github.com/dasch-swiss/dsp-api")
    ).collect { case (key, Some(value)) => (key, value) },
    dockerCommands += Cmd(
      "RUN",
      "apt-get update && apt-get install -y jq && rm -rf /var/lib/apt/lists/*"
    ), // install jq for container healthcheck
    dockerCommands += Cmd(
      """HEALTHCHECK --interval=30s --timeout=10s --retries=3 --start-period=30s \
        |CMD bash /opt/docker/scripts/healthcheck.sh || exit 1""".stripMargin
    ),
    // use filterNot to return all items that do NOT meet the criteria
    dockerCommands := dockerCommands.value.filterNot {
      // Remove USER command
      case Cmd("USER", args @ _*) => true

      // don't filter the rest; don't filter out anything that doesn't match a pattern
      case cmd => false
    }
  )
  .settings(
    buildInfoKeys ++= Seq[BuildInfoKey](
      name,
      version,
      "sipi"      -> Dependencies.sipiImage,
      "fuseki"    -> Dependencies.fusekiImage,
      "pekkoHttp" -> Dependencies.pekkoHttp
    ),
    buildInfoPackage := "org.knora.webapi.http.version"
  )

//////////////////////////////////////
// INTEGRATION (./integration)
//////////////////////////////////////

run / connectInput := true

lazy val integration: Project = Project(id = "integration", base = file("integration"))
  .dependsOn(webapi, sipi)
  .settings(buildSettings)
  .settings(
    inConfig(Test) {
      Defaults.testSettings ++ Defaults.testTasks ++ baseAssemblySettings ++ headerSettings(Test)
    },
    scalacOptions ++= customScalacOptions,
    logLevel := Level.Info,
    javaAgents += Dependencies.aspectjweaver,
    Test / testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
    Test / exportJars         := false,
    Test / fork               := true, // run tests in a forked JVM
    Test / testForkedParallel := false,
    Test / parallelExecution  := false,
    Test / javaOptions += "-Dkey=" + sys.props.getOrElse("key", "pekko"),
    Test / testOptions += Tests.Argument("-oDF"), // show full stack traces and test case durations
    libraryDependencies ++= Dependencies.webapiDependencies ++ Dependencies.webapiTestDependencies ++ Dependencies.integrationTestDependencies
  )
  .enablePlugins(SbtTwirl, JavaAppPackaging, DockerPlugin, JavaAgent, BuildInfoPlugin, HeaderPlugin)
  .settings(
    name := "integration",
    resolvers ++= Seq(
      "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"
    )
  )
