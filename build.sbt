import rapture.core.booleanRepresentations.trueFalse
import com.typesafe.sbt.SbtNativePackager.autoImport.NativePackagerHelper._
import com.typesafe.sbt.packager.docker.DockerPlugin.autoImport.{Docker, dockerRepository}
import com.typesafe.sbt.packager.docker.{Cmd, ExecCmd}
import org.knora.Dependencies

import sbt._
import sbt.Keys.version
import sbt.librarymanagement.Resolver

import scala.language.postfixOps
import scala.sys.process.Process

//////////////////////////////////////
// GLOBAL SETTINGS
//////////////////////////////////////

// when true enables run cancellation w/o exiting sbt
cancelable in Global := true

lazy val aggregatedProjects: Seq[ProjectReference] = Seq(webapi, sipi)

lazy val buildSettings = Seq(
  organization := "org.knora",
  version := (ThisBuild / version).value
)

lazy val rootBaseDir = ThisBuild / baseDirectory

lazy val root: Project = Project(id = "root", file("."))
  .aggregate(webapi, apiMain)
  .enablePlugins(GitVersioning, GitBranchPrompt)
  .settings(
    // values set for all sub-projects
    // These are normal sbt settings to configure for release, skip if already defined
    Global / onChangedBuildSource := ReloadOnSourceChanges,
    ThisBuild / licenses := Seq("Apache License, Version 2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
    ThisBuild / homepage := Some(url("https://github.com/dasch-swiss/dsp-api")),
    ThisBuild / scmInfo := Some(
      ScmInfo(url("https://github.com/dasch-swiss/dsp-api"), "scm:git:git@github.com:dasch-swiss/dsp-api.git")
    ),
    Global / scalaVersion := Dependencies.scalaVersion,
    // use 'git describe' for deriving the version
    git.useGitDescribe := true,
    // override generated version string because docker hub rejects '+' in tags
    ThisBuild / version ~= (_.replace('+', '-')),
    // use Ctrl-c to stop current task but not quit SBT
    Global / cancelable := true,
    publish / skip := true
  )

addCommandAlias("fmt", "all root/scalafmtSbt root/scalafmtAll")
addCommandAlias("check", "all root/scalafmtSbtCheck root/scalafmtCheckAll")

//////////////////////////////////////
// DSP's custom SIPI
//////////////////////////////////////

lazy val sipi: Project = Project(id = "sipi", base = file("sipi"))
  .enablePlugins(DockerPlugin)
  .settings(
    Compile / packageDoc / mappings := Seq(),
    Compile / packageSrc / mappings := Seq(),
    Docker / dockerRepository := Some("daschswiss"),
    Docker / packageName := "knora-sipi",
    dockerUpdateLatest := true,
    dockerBaseImage := Dependencies.sipiImage,
    Docker / maintainer := "support@dasch.swiss",
    Docker / dockerExposedPorts ++= Seq(1024),
    Docker / defaultLinuxInstallLocation := "/sipi",
    Universal / mappings ++= {
      // copy the sipi/scripts folder
      directory("sipi/scripts")
    },
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

lazy val webapi: Project = Project(id = "webapi", base = file("webapi"))
  .settings(buildSettings)
  .enablePlugins(SbtTwirl, JavaAppPackaging, DockerPlugin, GatlingPlugin, JavaAgent, BuildInfoPlugin)
  .settings(
    name := "webapi",
    resolvers ++= Seq(
      Resolver.bintrayRepo("hseeberger", "maven")
    ),
    libraryDependencies ++= Dependencies.webapiLibraryDependencies
  )
  .settings(
    inConfig(Test)(Defaults.testTasks ++ baseAssemblySettings)
  )
  .settings(
    // add needed files to production jar
    Compile / packageBin / mappings ++= Seq(
      (rootBaseDir.value / "knora-ontologies" / "knora-admin.ttl")                         -> "knora-ontologies/knora-admin.ttl",
      (rootBaseDir.value / "knora-ontologies" / "knora-base.ttl")                          -> "knora-ontologies/knora-base.ttl",
      (rootBaseDir.value / "knora-ontologies" / "salsah-gui.ttl")                          -> "knora-ontologies/salsah-gui.ttl",
      (rootBaseDir.value / "knora-ontologies" / "standoff-data.ttl")                       -> "knora-ontologies/standoff-data.ttl",
      (rootBaseDir.value / "knora-ontologies" / "standoff-onto.ttl")                       -> "knora-ontologies/standoff-onto.ttl",
      (rootBaseDir.value / "webapi" / "scripts" / "fuseki-repository-config.ttl.template") -> "webapi/scripts/fuseki-repository-config.ttl.template" // needed for initialization of triplestore
    ),
    // use packaged jars (through packageBin) on classpaths instead of class directories for production
    Compile / exportJars := true,
    // add needed files to test jar
    Test / packageBin / mappings ++= Seq(
      (rootBaseDir.value / "webapi" / "scripts" / "fuseki-repository-config.ttl.template") -> "webapi/scripts/fuseki-repository-config.ttl.template", // needed for initialization of triplestore
      (rootBaseDir.value / "sipi" / "config" / "sipi.docker-config.lua")                   -> "sipi/config/sipi.docker-config.lua"
    ),
    // use packaged jars (through packageBin) on classpaths instead of class directories for test
    Test / exportJars := true
  )
  .settings(
    scalacOptions ++= Seq(
      "-feature",
      "-unchecked",
      "-deprecation",
      "-Yresolve-term-conflict:package",
      "-Ymacro-annotations"
    ),
    logLevel := Level.Info,
    run / javaOptions := webapiJavaRunOptions,
    javaAgents += Dependencies.aspectjweaver,
    Test / fork := true,                // run tests in a forked JVM
    Test / testForkedParallel := false, // not run forked tests in parallel
    Test / parallelExecution := false,  // not run non-forked tests in parallel
    // Global / concurrentRestrictions += Tags.limit(Tags.Test, 1), // restrict the number of concurrently executing tests in all projects
    Test / javaOptions ++= Seq("-Dconfig.resource=fuseki.conf") ++ webapiJavaTestOptions,
    // Test / javaOptions ++= Seq("-Dakka.log-config-on-start=on"), // prints out akka config
    // Test / javaOptions ++= Seq("-Dconfig.trace=loads"), // prints out config locations
    Test / testOptions += Tests.Argument("-oDF"), // show full stack traces and test case durations
    // add test framework for running zio-tests
    Test / testFrameworks ++= Seq(new TestFramework("zio.test.sbt.ZTestFramework"))
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
        // add knora-ontologies
        directory("knora-ontologies") ++
        // copy configuration files to config directory
        contentOf("webapi/src/main/resources").toMap.mapValues("config/" + _)
    },
    // add 'config' directory to the classpath of the start script,
    Universal / scriptClasspath := Seq("webapi/scripts", "knora-ontologies", "../config/") ++ scriptClasspath.value,
    // need this here, so that the Manifest inside the jars has the correct main class set.
    Compile / mainClass := Some("org.knora.webapi.app.Main"),
    // add dockerCommands used to create the image
    // docker:stage, docker:publishLocal, docker:publish, docker:clean
    Docker / dockerRepository := Some("daschswiss"),
    Docker / packageName := "knora-api",
    dockerUpdateLatest := true,
    dockerBaseImage := "eclipse-temurin:17-jre-focal",
    Docker / maintainer := "support@dasch.swiss",
    Docker / dockerExposedPorts ++= Seq(3333),
    Docker / defaultLinuxInstallLocation := "/opt/docker",
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
      "akkaHttp" -> Dependencies.akkaHttpVersion,
      "sipi"     -> Dependencies.sipiImage,
      "fuseki"   -> Dependencies.fusekiImage
    ),
    buildInfoPackage := "org.knora.webapi.http.version"
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
  "-Dcom.sun.management.jmxremote.ssl=false"
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

//////////////////////////////////////
// DSP's new codebase
//////////////////////////////////////

lazy val apiMain = project
  .in(file("dsp-api-main"))
  .settings(
    name := "dsp-api-main",
    libraryDependencies ++= Dependencies.dspApiMainLibraryDependencies,
    testFrameworks := Seq(new TestFramework("zio.test.sbt.ZTestFramework"))
  )
  .dependsOn(schemaCore, schemaRepo, schemaApi)

lazy val schemaApi = project
  .in(file("dsp-schema/api"))
  .settings(
    name := "schemaApi",
    libraryDependencies ++= Dependencies.schemaApiLibraryDependencies,
    testFrameworks := Seq(new TestFramework("zio.test.sbt.ZTestFramework"))
  )
  .dependsOn(schemaCore)

lazy val schemaCore = project
  .in(file("dsp-schema/core"))
  .settings(
    name := "schemaCore",
    libraryDependencies ++= Dependencies.schemaCoreLibraryDependencies,
    testFrameworks := Seq(new TestFramework("zio.test.sbt.ZTestFramework"))
  )

lazy val schemaRepo = project
  .in(file("dsp-schema/repo"))
  .settings(
    name := "schemaRepo",
    libraryDependencies ++= Dependencies.schemaRepoLibraryDependencies,
    testFrameworks := Seq(new TestFramework("zio.test.sbt.ZTestFramework"))
  )
  .dependsOn(schemaCore)

lazy val schemaRepoEventStoreService = project
  .in(file("dsp-schema/repo-eventstore-service"))
  .settings(
    name := "schemaRepoEventstoreService",
    libraryDependencies ++= Dependencies.schemaRepoEventStoreServiceLibraryDependencies,
    testFrameworks := Seq(new TestFramework("zio.test.sbt.ZTestFramework"))
  )
  .dependsOn(schemaRepo)

lazy val schemaRepoSearchService = project
  .in(file("dsp-schema/repo-search-service"))
  .settings(
    name := "dsp-schema-repo-search-service",
    libraryDependencies ++= Dependencies.schemaRepoSearchServiceLibraryDependencies,
    testFrameworks := Seq(new TestFramework("zio.test.sbt.ZTestFramework"))
  )
  .dependsOn(schemaRepo)
