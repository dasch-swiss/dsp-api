
import com.typesafe.sbt.SbtNativePackager.autoImport.NativePackagerHelper._
import com.typesafe.sbt.packager.docker.DockerPlugin.autoImport.{Docker, dockerRepository}
import com.typesafe.sbt.packager.docker.{Cmd, ExecCmd}
import org.knora.Dependencies
import sbt.Keys.version

import scala.language.postfixOps
import scala.sys.process.Process

//////////////////////////////////////
// GLOBAL SETTINGS
//////////////////////////////////////

lazy val aggregatedProjects: Seq[ProjectReference] = Seq(webapi)

lazy val buildSettings = Dependencies.Versions ++ Seq(
    organization := "org.knora",
    version := (ThisBuild / version).value
)

lazy val rootBaseDir = ThisBuild / baseDirectory

lazy val root: Project = Project(id = "knora", file("."))
  .aggregate(aggregatedProjects: _*)
  .enablePlugins(DockerComposePlugin, GitVersioning, GitBranchPrompt)
  .settings(Dependencies.Versions)
  .settings(
      // values set for all sub-projects
      // These are normal sbt settings to configure for release, skip if already defined

      Global / onChangedBuildSource := ReloadOnSourceChanges,

      ThisBuild / licenses := Seq("AGPL-3.0" -> url("https://opensource.org/licenses/AGPL-3.0")),
      ThisBuild / homepage := Some(url("https://github.com/dhlab-basel/Knora")),
      ThisBuild / scmInfo := Some(ScmInfo(url("https://github.com/dhlab-basel/Knora"), "scm:git:git@github.com:dhlab-basel/Knora.git")),

      // use 'git describe' for deriving the version
      git.useGitDescribe := true,

      // override generated version string because docker hub rejects '+' in tags
      ThisBuild / version ~= (_.replace('+', '-')),

      // use Ctrl-c to stop current task but not quit SBT
      Global / cancelable := true,

      publish / skip := true,

    //   Dependencies.sysProps := sys.props.toString(),
    //   Dependencies.sysEnvs := sys.env.toString(),

      dockerImageCreationTask := Seq(
        //   (salsah1 / Docker / publishLocal).value,
          (webapi / Docker / publishLocal).value
        //   (knoraJenaFuseki / Docker / publishLocal).value,
        //   (knoraSipi / Docker / publishLocal).value
      )
  )

//////////////////////////////////////
// WEBAPI (./webapi)
//////////////////////////////////////

import com.typesafe.sbt.SbtNativePackager.autoImport.NativePackagerHelper._
import sbt._
import sbt.librarymanagement.Resolver

run / connectInput := true

lazy val webApiCommonSettings = Seq(
    name := "webapi"
)

// custom test and it settings
lazy val GDBSE = config("gdbse") extend Test
lazy val GDBSEIt = config("gdbse-it") extend IntegrationTest
lazy val GDBFree = config("gdbfree") extend Test
lazy val GDBFreeIt = config("gdbfree-it") extend IntegrationTest
lazy val FusekiTest = config("fuseki") extend Test
lazy val FusekiIt = config("fuseki-it") extend IntegrationTest
lazy val EmbeddedJenaTDBTest = config("tdb") extend Test

// GatlingPlugin - load testing
// JavaAgent - adds AspectJ Weaver configuration
// BuildInfoPlugin - allows generation of scala code with version information

lazy val webapi = knoraModule("webapi")
  .enablePlugins(SbtTwirl, JavaAppPackaging, DockerPlugin, GatlingPlugin, JavaAgent, RevolverPlugin, BuildInfoPlugin)
  .configs(
      IntegrationTest,
      Gatling,
      GatlingIt,
      GDBSE,
      GDBSEIt,
      GDBFree,
      GDBFreeIt,
      FusekiTest,
      FusekiIt,
      EmbeddedJenaTDBTest
  )
  .settings(
      webApiCommonSettings,
      resolvers ++= Seq(
          Resolver.bintrayRepo("hseeberger", "maven")
      ),
      Dependencies.webapiLibraryDependencies
  )
  .settings(
      inConfig(Test)(Defaults.testTasks ++ baseAssemblySettings),
      inConfig(IntegrationTest)(Defaults.testSettings),
      inConfig(Gatling)(Defaults.testTasks ++ Seq(forkOptions := Defaults.forkOptionsTask.value)),
      inConfig(GatlingIt)(Defaults.testTasks ++ Seq(forkOptions := Defaults.forkOptionsTask.value)),
      inConfig(GDBSE)(Defaults.testTasks ++ Seq(forkOptions := Defaults.forkOptionsTask.value)),
      inConfig(GDBSEIt)(Defaults.testTasks ++ Seq(forkOptions := Defaults.forkOptionsTask.value)),
      inConfig(GDBFree)(Defaults.testTasks ++ Seq(forkOptions := Defaults.forkOptionsTask.value)),
      inConfig(GDBFreeIt)(Defaults.testTasks ++ Seq(forkOptions := Defaults.forkOptionsTask.value)),
      inConfig(FusekiTest)(Defaults.testTasks ++ Seq(forkOptions := Defaults.forkOptionsTask.value)),
      inConfig(FusekiIt)(Defaults.testTasks ++ Seq(forkOptions := Defaults.forkOptionsTask.value)),
      inConfig(EmbeddedJenaTDBTest)(Defaults.testTasks ++ Seq(forkOptions := Defaults.forkOptionsTask.value))
  )

  .settings(
      exportJars := true,
      Compile / unmanagedResourceDirectories += (rootBaseDir.value / "knora-ontologies"),

      // add needed files to jar
      Compile / packageBin / mappings ++= Seq(
          (rootBaseDir.value / "knora-ontologies" / "knora-admin.ttl") -> "knora-ontologies/knora-admin.ttl",
          (rootBaseDir.value / "knora-ontologies" / "knora-base.ttl") -> "knora-ontologies/knora-base.ttl",
          (rootBaseDir.value / "knora-ontologies" / "salsah-gui.ttl") -> "knora-ontologies/salsah-gui.ttl",
          (rootBaseDir.value / "knora-ontologies" / "standoff-data.ttl") -> "knora-ontologies/standoff-data.ttl",
          (rootBaseDir.value / "knora-ontologies" / "standoff-onto.ttl") -> "knora-ontologies/standoff-onto.ttl",
          (rootBaseDir.value / "webapi" / "scripts" / "fuseki-knora-test-repository-config.ttl") -> "webapi/scripts/fuseki-knora-test-repository-config.ttl",
          (rootBaseDir.value / "webapi" / "scripts" / "fuseki-knora-test-unit-repository-config.ttl") -> "webapi/scripts/fuseki-knora-test-unit-repository-config.ttl",
      ),
      // contentOf("salsah1/src/main/resources").toMap.mapValues("config/" + _)
      // (rootBaseDir.value / "knora-ontologies") -> "knora-ontologies",

      // put additional files into the jar when running tests which are needed by testcontainers
      Test / packageBin / mappings ++= Seq(
          (rootBaseDir.value / "sipi" / "config" / "sipi.init-knora.lua") -> "sipi/config/sipi.init-knora.lua",
          (rootBaseDir.value / "sipi" / "config" / "sipi.knora-docker-config.lua") -> "sipi/config/sipi.knora-docker-config.lua",
          (rootBaseDir.value / "sipi" / "config" / "sipi.knora-docker-config.lua") -> "sipi/config/sipi.knora-docker-config.lua",
      ),
      IntegrationTest / packageBin/ mappings ++= Seq(
          (rootBaseDir.value / "sipi" / "config" / "sipi.init-knora.lua") -> "sipi/config/sipi.init-knora.lua",
          (rootBaseDir.value / "sipi" / "config" / "sipi.knora-docker-config.lua") -> "sipi/config/sipi.knora-docker-config.lua",
      ),
  )

  .settings(
      scalacOptions ++= Seq("-feature", "-unchecked", "-deprecation", "-Yresolve-term-conflict:package"),

      logLevel := Level.Info,

      run / javaOptions := webapiJavaRunOptions,

      reStart / javaOptions ++= resolvedJavaAgents.value map { resolved =>
          "-javaagent:" + resolved.artifact.absolutePath + resolved.agent.arguments
      }, // allows sbt-javaagent to work with sbt-revolver
      reStart / javaOptions ++= webapiJavaRunOptions,

      javaAgents += Dependencies.Compile.aspectJWeaver,

      fork := true, // run tests in a forked JVM
      Test / testForkedParallel := false, // run forked tests in parallel
      Test / parallelExecution := false, // run non-forked tests in parallel
      // Global / concurrentRestrictions += Tags.limit(Tags.Test, 1), // restrict the number of concurrently executing tests in all projects
      Test / javaOptions ++= Seq("-Dconfig.resource=fuseki.conf") ++ webapiJavaTestOptions,
      // Test / javaOptions ++= Seq("-Dakka.log-config-on-start=on"), // prints out akka config
      // Test / javaOptions ++= Seq("-Dconfig.trace=loads"), // prints out config locations
      Test / testOptions += Tests.Argument("-oDF"), // show full stack traces and test case durations

      IntegrationTest / javaOptions := Seq("-Dconfig.resource=fuseki.conf") ++ webapiJavaTestOptions,
      IntegrationTest / testOptions += Tests.Argument("-oDF"), // show full stack traces and test case durations

      Gatling / javaOptions := Seq("-Dconfig.resource=fuseki.conf") ++ webapiJavaTestOptions,
      Gatling / testOptions := Seq(),
      GatlingIt / javaOptions := Seq("-Dconfig.resource=fuseki.conf") ++ webapiJavaTestOptions,
      GatlingIt / testOptions := Seq(),

      GDBSE / javaOptions := Seq("-Dconfig.resource=graphdb-se.conf") ++ webapiJavaTestOptions,
      GDBSEIt / javaOptions := Seq("-Dconfig.resource=graphdb-se.conf") ++ webapiJavaTestOptions,

      GDBFree / javaOptions := Seq("-Dconfig.resource=graphdb-free.conf") ++ webapiJavaTestOptions,
      GDBFreeIt / javaOptions := Seq("-Dconfig.resource=graphdb-free.conf") ++ webapiJavaTestOptions,

      FusekiTest / javaOptions := Seq("-Dconfig.resource=fuseki.conf") ++ webapiJavaTestOptions,
      FusekiIt / javaOptions := Seq("-Dconfig.resource=fuseki.conf") ++ webapiJavaTestOptions,

      EmbeddedJenaTDBTest / javaOptions := Seq("-Dconfig.resource=jenatdb.conf") ++ webapiJavaTestOptions

      // enable publishing the jars for test and it
      // Test / packageBin / publishArtifact := true,
      // IntegrationTest / packageBin / publishArtifact := true,
      // addArtifact(artifact in (IntegrationTest, packageBin), packageBin in IntegrationTest)
  )
  .settings(
      // prepare for publishing

      // Skip packageDoc and packageSrc task on stage
      Compile / packageDoc / mappings := Seq(),
      Compile / packageSrc / mappings := Seq(),

      Universal / mappings ++= {
          // copy the scripts folder
          directory("webapi/scripts") ++
            // add knora-ontologies
            directory("knora-ontologies") ++
            // add test-data directory
            directory("webapi/_test_data") ++
            // copy the configuration files to config directory
            contentOf("webapi/configs").toMap.mapValues("config/" + _) ++
            // copy configuration files to config directory
            contentOf("webapi/src/main/resources").toMap.mapValues("config/" + _)
      },

      // add 'config' directory to the classpath of the start script,
      Universal / scriptClasspath := Seq("../config/") ++ scriptClasspath.value,

      // need this here, so that the Manifest inside the jars has the correct main class set.
      Compile / mainClass := Some("org.knora.webapi.Main"),
      Compile / run / mainClass := Some("org.knora.webapi.Main"),

      // add dockerCommands used to create the image
      // docker:stage, docker:publishLocal, docker:publish, docker:clean

      dockerRepository := Some("dhlabbasel"),

      maintainer := "400790+subotic@users.noreply.github.com",

      Docker / dockerExposedPorts ++= Seq(3333, 10001),
      Docker / dockerCommands := Seq(
          Cmd("FROM", "adoptopenjdk/openjdk11:alpine-jre"),
          Cmd("LABEL", s"""MAINTAINER="${maintainer.value}""""),

          Cmd("RUN apk update && apk upgrade && apk add bash"),

          Cmd("COPY", "opt/docker", "/webapi"),
          Cmd("WORKDIR", "/webapi"),

          Cmd("EXPOSE", "3333"),

          ExecCmd("ENTRYPOINT", "bin/webapi"),
      )

  )
  .settings(
      buildInfoKeys ++= Seq[BuildInfoKey](
          name,
          version,
          "akkaHttp" -> Dependencies.akkaHttpVersion.value,
          "sipi" -> Dependencies.sipiImage.value,
          "gdbSE" -> Dependencies.gdbSEImage.value,
          "gdbFree" -> Dependencies.gdbFreeImage.value
      ),
      buildInfoPackage := "org.knora.webapi"
  )

lazy val webapiJavaRunOptions = Seq(
    // "-showversion",
    "-Xms1G",
    "-Xmx1G",
    // "-verbose:gc",
    //"-XX:+UseG1GC",
    //"-XX:MaxGCPauseMillis=500"
    "-Dcom.sun.management.jmxremote",
    // "-Dcom.sun.management.jmxremote.port=1617",
    "-Dcom.sun.management.jmxremote.authenticate=false",
    "-Dcom.sun.management.jmxremote.ssl=false",
    //"-agentpath:/Applications/YourKit-Java-Profiler-2018.04.app/Contents/Resources/bin/mac/libyjpagent.jnilib"
)

lazy val webapiJavaTestOptions = Seq(
    // "-showversion",
    "-Xms1G",
    "-Xmx1G"
    // "-verbose:gc",
    //"-XX:+UseG1GC",
    //"-XX:MaxGCPauseMillis=500",
    //"-XX:MaxMetaspaceSize=4096m"
)

def knoraModule(name: String): Project =
    Project(id = name, base = file(name))
      .settings(buildSettings)
