
import com.typesafe.sbt.SbtNativePackager.autoImport.NativePackagerHelper._
import com.typesafe.sbt.packager.docker.DockerPlugin.autoImport.{Docker, dockerRepository}
import com.typesafe.sbt.packager.docker.{Cmd, ExecCmd}
import org.knora.Dependencies

//////////////////////////////////////
// GLOBAL SETTINGS
//////////////////////////////////////

lazy val aggregatedProjects: Seq[ProjectReference] = Seq(docs, salsah1, webapi)

lazy val buildSettings = Dependencies.Versions ++ Seq(
    organization := "org.knora",
    version := (ThisBuild / version).value
)

lazy val root = Project(id = "knora", file("."))
        .aggregate(aggregatedProjects: _*)
        .enablePlugins(DockerComposePlugin)
        .settings(Dependencies.Versions)
        .settings(
            // values set for all sub-projects
            // These are normal sbt settings to configure for release, skip if already defined

            ThisBuild / licenses := Seq("AGPL-3.0" -> url("https://opensource.org/licenses/AGPL-3.0")),
            ThisBuild / homepage := Some(url("https://github.com/dhlab-basel/Knora")),
            ThisBuild / scmInfo := Some(ScmInfo(url("https://github.com/dhlab-basel/Knora"), "scm:git:git@github.com:dhlab-basel/Knora.git")),

            // override dynver generated version string because docker hub rejects '+' in tags
            ThisBuild / version ~= (_.replace('+', '-')),
            ThisBuild / dynver ~= (_.replace('+', '-')),

            Global / cancelable := true, // use Ctrl-c to stop current task and not quit SBT

            publish / skip := true,

            Dependencies.sysProps := sys.props.toString(),
            Dependencies.sysEnvs := sys.env.toString(),

            ThisBuild / Dependencies.gdbHomePath := sys.env.getOrElse("KNORA_GDB_HOME", sys.props("user.dir") + "/triplestores/graphdb/home"),
            ThisBuild / Dependencies.gdbLicensePath := sys.env.getOrElse("KNORA_GDB_LICENSE", sys.props("user.dir") + "/triplestores/graphdb/graphdb.license"),

            variablesForSubstitution := Map(
                "KNORA_GDB_HOME" -> Dependencies.gdbHomePath.value,
                "KNORA_GDB_LICENSE " -> Dependencies.gdbLicensePath.value,
                "KNORA_GDB_IMAGE" -> Dependencies.gdbImage.value,
                "SIPI_VERSION_TAG" -> Dependencies.sipiVersion.value,
                "KNORA_VERSION_TAG" -> version.value
            ),

            dockerImageCreationTask := Seq(
                (salsah1 / Docker / publishLocal).value,
                (webapi / Docker / publishLocal).value
            )
        )


//////////////////////////////////////
// DOCS (./docs)
//////////////////////////////////////

import scala.sys.process._

// Define `Configuration` instances representing our different documentation trees
lazy val ParadoxSite = config("paradox")

lazy val docs = knoraModule("docs")
        .enablePlugins(JekyllPlugin, ParadoxPlugin, ParadoxSitePlugin, ParadoxMaterialThemePlugin, GhpagesPlugin)
        .configs(
            ParadoxSite
        )
        .settings(
            // Apply default settings to our two custom configuration instances
            ParadoxSitePlugin.paradoxSettings(ParadoxSite),
            ParadoxMaterialThemePlugin.paradoxMaterialThemeGlobalSettings, // paradoxTheme and version
            ParadoxMaterialThemePlugin.paradoxMaterialThemeSettings(ParadoxSite)
        )
        .settings(

            // Ghpages settings
            ghpagesNoJekyll := true,
            git.remoteRepo := "git@github.com:dhlab-basel/Knora.git",
            excludeFilter in ghpagesCleanSite :=
                    new FileFilter {
                        def accept(f: File) = (ghpagesRepository.value / "CNAME").getCanonicalPath == f.getCanonicalPath
                    } || "LICENSE.md" || "README.md",

            // (sbt-site) Customize the source directory
            // sourceDirectory in Jekyll := sourceDirectory.value / "overview",
            sourceDirectory in ParadoxSite := sourceDirectory.value / "paradox",

            // (sbt-site) Customize the output directory (subdirectory of site)
            siteSubdirName in ParadoxSite := "paradox",

            // Set some paradox properties
            paradoxProperties in ParadoxSite ++= Map(
                "project.name" -> "Knora Documentation",
                "github.base_url" -> "https://github.com/dhlab-basel/Knora",
                "image.base_url" -> ".../assets/images",
                "extref.rfc.base_url" -> "http://tools.ietf.org/html/rfc%s",
                "snip.src.base_dir" -> ((baseDirectory in ThisBuild).value / "webapi" / "src" / "main" / "scala").getAbsolutePath,
                "snip.test.base_dir" -> ((baseDirectory in ThisBuild).value / "webapi" / "src" / "test" / "scala").getAbsolutePath
            ),

            // Paradox Material Theme Settings
            paradoxMaterialTheme in ParadoxSite ~= {
                _.withColor("blue", "yellow")
                        .withRepository(uri("https://github.com/dhlab-basel/Knora/docs"))
                        .withFavicon("cloud")
                        .withLogoIcon("cloud")
                        .withSocial(
                            uri("https://github.com/dhlab-basel"),
                            uri("https://twitter.com/dhlabbasel")
                        )
                        .withLanguage(java.util.Locale.ENGLISH)
                        .withCopyright("Copyright 2015-2018 the contributors (see Contributors.md)")
            },
            mappings in makeSite ++= Seq(
                file("src/api-admin/index.html") -> "api-admin/index.html",
                file("src/api-admin/swagger.json") -> "api-admin/swagger.json"
            ),
            makeSite := makeSite.dependsOn(buildPrequisites).value
        )

lazy val buildPrequisites = taskKey[Unit]("Build typescript API documentation and Graphviz diagrams.")

docs / buildPrequisites := {
    val s: TaskStreams = streams.value

    val execDir: Option[File] = if (sys.props("user.dir").endsWith("docs")) {
        // running from docs directory, which is fine
        None
    } else {
        // running from project root directory
        Some(new File(sys.props("user.dir") + "/docs"))
    }

    val shell: Seq[String] = if (sys.props("os.name").contains("Windows")) Seq("cmd", "/c") else Seq("bash", "-c")
    val clean: Seq[String] = shell :+ "make clean"
    val jsonformat: Seq[String] = shell :+ "make jsonformat"
    val graphvizfigures: Seq[String] = shell :+ "make graphvizfigures"
    val jsonformattest: Seq[String] = shell :+ "make jsonformattest"

    s.log.info("building typescript documentation and graphviz diagrams...")

    if ((Process(clean, execDir) #&& Process(jsonformattest, execDir) #&& Process(jsonformat, execDir) #&& Process(graphvizfigures, execDir) !) == 0) {
        Thread.sleep(500)
        s.log.success("typescript documentation and graphviz diagrams built successfully")
    } else {
        throw new IllegalStateException("typescript documentation and graphviz diagrams failed to build")
    }
}


//////////////////////////////////////
// SALSAH1 (./salsah1)
//////////////////////////////////////

lazy val salsahCommonSettings = Seq(
    name := "salsah1"
)

lazy val salsah1 = knoraModule("salsah1")
        .enablePlugins(JavaAppPackaging, DockerPlugin, DockerComposePlugin)
        .configs(
            HeadlessTest
        )
        .settings(
            salsahCommonSettings,
            Revolver.settings
        )
        .settings(inConfig(HeadlessTest)(
            Defaults.testTasks ++ Seq(
                fork := true,
                javaOptions ++= javaHeadlessTestOptions,
                testOptions += Tests.Argument("-oDF") // show full stack traces and test case durations
            )
        ): _*)
        .settings(
            Dependencies.salsahLibraryDependencies,
            logLevel := Level.Info,
            fork in run := true,
            javaOptions in run ++= javaRunOptions,
            mainClass in(Compile, run) := Some("org.knora.salsah.Main"),
            fork in Test := true,
            javaOptions in Test ++= javaTestOptions,
            parallelExecution in Test := false,
            /* show full stack traces and test case durations */
            testOptions in Test += Tests.Argument("-oDF")
        )
        .settings( // enable deployment staging with `sbt stage`
            mappings in Universal ++= {
                // copy the public folder
                directory("salsah1/src/public") ++
                // copy the configuration files to config directory
                contentOf("salsah1/configs").toMap.mapValues("config/" + _) ++
                // copy configuration files to config directory
                contentOf("salsah1/src/main/resources").toMap.mapValues("config/" + _)
            },
            // add 'config' directory first in the classpath of the start script,
            scriptClasspath := Seq("../config/") ++ scriptClasspath.value,
            // need this here, but why?
            mainClass in Compile := Some("org.knora.salsah.Main"),

            // add dockerCommands used to create the image
            // docker:stage, docker:publishLocal, docker:publish, docker:clean

            dockerRepository := Some("dhlabbasel"),

            maintainer := "ivan.subotic@unibas.ch",

            Docker / dockerCommands := Seq(
                Cmd("FROM", "openjdk:10-jre-slim-sid"),
                Cmd("LABEL", s"""MAINTAINER="${maintainer.value}""""),

                Cmd("ENV", """LANG="en_US.UTF-8""""),
                Cmd("ENV", """JAVA_OPTS="-Dsun.jnu.encoding=UTF-8 -Dfile.encoding=UTF-8""""),
                Cmd("ENV", "KNORA_SALSAH1_DEPLOYED=true"),
                Cmd("ENV", "KNORA_SALSAH1_WORKDIR=/salsah"),

                Cmd("ADD", "opt/docker", "/salsah"),
                Cmd("WORKDIR", "/salsah"),

                Cmd("EXPOSE", "3335"),

                ExecCmd("ENTRYPOINT", "bin/salsah1"),
            ),


        )
        .settings()

lazy val javaRunOptions = Seq(
    // "-showversion",
    "-Xms256m",
    "-Xmx256m"
    // "-verbose:gc",
    //"-XX:+UseG1GC",
    //"-XX:MaxGCPauseMillis=500"
)

lazy val javaTestOptions = Seq(
    // "-showversion",
    "-Xms512m",
    "-Xmx512m"
    // "-verbose:gc",
    //"-XX:+UseG1GC",
    //"-XX:MaxGCPauseMillis=500",
    //"-XX:MaxMetaspaceSize=4096m"
)


lazy val HeadlessTest = config("headless") extend (Test)
lazy val javaHeadlessTestOptions = Seq(
    "-Dconfig.resource=headless-testing.conf"
) ++ javaTestOptions



//////////////////////////////////////
// WEBAPI (./webapi)
//////////////////////////////////////

import com.typesafe.sbt.SbtNativePackager.autoImport.NativePackagerHelper._
import sbt._
import sbt.librarymanagement.Resolver

connectInput in run := true

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
            scalacOptions ++= Seq("-feature", "-unchecked", "-deprecation", "-Yresolve-term-conflict:package"),

            logLevel := Level.Info,

            fork := true, // always fork

            run / javaOptions := webapiJavaRunOptions,

            reStart / javaOptions ++= resolvedJavaAgents.value map { resolved =>
                "-javaagent:" + resolved.artifact.absolutePath + resolved.agent.arguments
            }, // allows sbt-javaagent to work with sbt-revolver
            reStart / javaOptions ++= webapiJavaRunOptions,

            javaAgents += Dependencies.Compile.aspectJWeaver,

            Test / parallelExecution := false,
            Test / javaOptions ++= Seq("-Dconfig.resource=graphdb-se.conf") ++ webapiJavaTestOptions,
            // Test / javaOptions ++= Seq("-Dakka.log-config-on-start=on"), // prints out akka config
            // Test / javaOptions ++= Seq("-Dconfig.trace=loads"), // prints out config locations
            Test / testOptions += Tests.Argument("-oDF"), // show full stack traces and test case durations

            IntegrationTest / javaOptions := Seq("-Dconfig.resource=graphdb-se.conf") ++ webapiJavaTestOptions,
            IntegrationTest / testOptions += Tests.Argument("-oDF"), // show full stack traces and test case durations

            Gatling / javaOptions := Seq("-Dconfig.resource=graphdb-se.conf") ++ webapiJavaTestOptions,
            Gatling / testOptions := Seq(),
            GatlingIt / javaOptions := Seq("-Dconfig.resource=graphdb-se.conf") ++ webapiJavaTestOptions,
            GatlingIt / testOptions := Seq(),

            GDBSE / javaOptions := Seq("-Dconfig.resource=graphdb-se.conf") ++ webapiJavaTestOptions,
            GDBSEIt / javaOptions := Seq("-Dconfig.resource=graphdb-se.conf") ++ webapiJavaTestOptions,

            GDBFree / javaOptions := Seq("-Dconfig.resource=graphdb-free.conf") ++ webapiJavaTestOptions,
            GDBFreeIt / javaOptions := Seq("-Dconfig.resource=graphdb-free.conf") ++ webapiJavaTestOptions,

            FusekiTest / javaOptions := Seq("-Dconfig.resource=fuseki.conf") ++ webapiJavaTestOptions,
            FusekiIt / javaOptions := Seq("-Dconfig.resource=fuseki.conf") ++ webapiJavaTestOptions,

            EmbeddedJenaTDBTest / javaOptions := Seq("-Dconfig.resource=jenatdb.conf") ++ webapiJavaTestOptions,

            // enable publishing the jar produced by `sbt test:package` and `sbt it:package`
            Test / packageBin / publishArtifact := true,
            IntegrationTest / packageBin / publishArtifact := true
        )
        .settings(
            // prepare for publishing

            // Skip packageDoc task on stage
            Compile / packageDoc / mappings := Seq(),

            Universal / mappings ++= {
                // copy the scripts folder
                directory("webapi/scripts") ++
                        // copy the configuration files to config directory
                        contentOf("webapi/configs").toMap.mapValues("config/" + _) ++
                        // copy configuration files to config directory
                        contentOf("webapi/src/main/resources").toMap.mapValues("config/" + _)
                // copy the aspectj weaver jar
                // contentOf("vendor").toMap.mapValues("aspectjweaver/" + _)
            },

            // add 'config' directory to the classpath of the start script,
            Universal / scriptClasspath := Seq("../config/") ++ scriptClasspath.value,

            // need this here, so that the Manifest inside the jars has the correct main class set.
            Compile / mainClass := Some("org.knora.webapi.Main"),
            Compile / run / mainClass := Some("org.knora.webapi.Main"),
            Test / mainClass := Some("org.scalatest.tools.Runner"),
            IntegrationTest / mainClass := Some("org.scalatest.tools.Runner"),

            // add dockerCommands used to create the image
            // docker:stage, docker:publishLocal, docker:publish, docker:clean

            dockerRepository := Some("dhlabbasel"),

            maintainer := "ivan.subotic@unibas.ch",

            Docker / dockerCommands := Seq(
                Cmd("FROM", "openjdk:10-jre-slim-sid"),
                Cmd("LABEL", s"""MAINTAINER="${maintainer.value}""""),
                // install wget
                Cmd("RUN", "apt-get -qq update && apt-get install -y --no-install-recommends wget=1.19.5-2 && rm -rf /var/lib/apt/lists/*"),
                // install yourkit profiler
                Cmd("RUN", "wget https://www.yourkit.com/download/docker/YourKit-JavaProfiler-2018.04-docker.zip -P /tmp/ && unzip /tmp/YourKit-JavaProfiler-2018.04-docker.zip -d /usr/local && rm /tmp/YourKit-JavaProfiler-2018.04-docker.zip"),

                Cmd("ADD", "opt/docker", "/webapi"),
                Cmd("WORKDIR", "/webapi"),

                Cmd("EXPOSE", "3333"),
                Cmd("EXPOSE", "10001"),

                ExecCmd("ENTRYPOINT", "bin/webapi", "-J-agentpath:/usr/local/YourKit-JavaProfiler-2018.04/bin/linux-x86-64/libyjpagent.so=port=10001,listen=all"),
            )
        )
        .settings(
            buildInfoKeys ++= Seq[BuildInfoKey](
                name,
                version,
                "akkaHttp" -> Dependencies.akkaHttpVersion
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
    "-Dcom.sun.management.jmxremote.port=1617",
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
