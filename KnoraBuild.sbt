import com.typesafe.sbt.SbtNativePackager.autoImport.NativePackagerHelper._
import sbt._

//////////////////////////////////////
// GLOBAL SETTINGS
//////////////////////////////////////

inThisBuild(List(
    // These are normal sbt settings to configure for release, skip if already defined
    licenses := Seq("AGPL-3.0" -> url("https://opensource.org/licenses/AGPL-3.0")),
    homepage := Some(url("https://github.com/dhlab-basel/Knora")),
    scmInfo := Some(ScmInfo(url("https://github.com/dhlab-basel/Knora"), "scm:git:git@github.com:dhlab-basel/Knora.git")),

    // override dynver generated version string because docker hub rejects '+' in tags
    version in ThisBuild ~= (_.replace('+', '-')),
    dynver in ThisBuild ~= (_.replace('+', '-')),

    cancelable in Global := true, // use Ctrl-c to stop current task and not quit SBT

    organization := "org.knora",
    scalaVersion := "2.12.4"
))

lazy val akkaVersion = "2.5.18"
lazy val akkaHttpVersion = "10.1.5"

//////////////////////////////////////
// DOCS (./docs)
//////////////////////////////////////

import scala.sys.process._

// Define `Configuration` instances representing our different documentation trees
lazy val ParadoxSite = config("paradox")

lazy val docs = (project in file("docs")).
        configs(
            ParadoxSite
        ).
        enablePlugins(JekyllPlugin, ParadoxPlugin, ParadoxSitePlugin, ParadoxMaterialThemePlugin, GhpagesPlugin).
        settings(
            // Apply default settings to our two custom configuration instances
            ParadoxSitePlugin.paradoxSettings(ParadoxSite),
            ParadoxMaterialThemePlugin.paradoxMaterialThemeGlobalSettings, // paradoxTheme and version
            ParadoxMaterialThemePlugin.paradoxMaterialThemeSettings(ParadoxSite)
        ).
        settings(

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
                "extref.rfc.base_url" -> "http://tools.ietf.org/html/rfc%s"
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
    name := "salsah",
)

lazy val salsah1 = (project in file("salsah1")).
        configs(
            HeadlessTest
        ).
        settings(salsahCommonSettings:  _*).
        settings(inConfig(HeadlessTest)(
            Defaults.testTasks ++ Seq(
                fork := true,
                javaOptions ++= javaHeadlessTestOptions,
                testOptions += Tests.Argument("-oDF") // show full stack traces and test case durations
            )
        ): _*).
        settings(
            libraryDependencies ++= salsahLibs,
            logLevel := Level.Info,
            fork in run := true,
            javaOptions in run ++= javaRunOptions,
            mainClass in (Compile, run) := Some("org.knora.salsah.Main"),
            fork in Test := true,
            javaOptions in Test ++= javaTestOptions,
            parallelExecution in Test := false,
            /* show full stack traces and test case durations */
            testOptions in Test += Tests.Argument("-oDF")
        ).
        settings( // enable deployment staging with `sbt stage`
            mappings in Universal ++= {
                // copy the public folder
                directory("src/public") ++
                // copy the configuration files to config directory
                contentOf("configs").toMap.mapValues("config/" + _) ++
                // copy configuration files to config directory
                contentOf("src/main/resources").toMap.mapValues("config/" + _)
            },
            // add 'config' directory first in the classpath of the start script,
            scriptClasspath := Seq("../config/") ++ scriptClasspath.value,
            // add license
            licenses := Seq(("GNU AGPL", url("https://www.gnu.org/licenses/agpl-3.0"))),
            // need this here, but why?
            mainClass in Compile := Some("org.knora.salsah.Main")).
        settings(Revolver.settings: _*).
        enablePlugins(JavaAppPackaging) // Enable the sbt-native-packager plugin

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


lazy val HeadlessTest = config("headless") extend(Test)
lazy val javaHeadlessTestOptions = Seq(
    "-Dconfig.resource=headless-testing.conf"
) ++ javaTestOptions



lazy val salsahLibs = Seq(
    // akka
    "com.typesafe.akka" %% "akka-actor" % akkaVersion,
    "com.typesafe.akka" %% "akka-agent" % akkaVersion,
    "com.typesafe.akka" %% "akka-stream" % akkaVersion,
    "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
    "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
    "com.typesafe.akka" %% "akka-http-xml" % akkaHttpVersion,
    "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion,
    // testing
    "com.typesafe.akka" %% "akka-testkit" % akkaVersion % "test",
    "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion % "test",
    "com.typesafe.akka" %% "akka-stream-testkit" % akkaVersion % "test",
    "org.scalatest" %% "scalatest" % "3.0.0" % "test",
    "org.seleniumhq.selenium" % "selenium-java" % "3.4.0" % "test"
)


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

lazy val webapi = (project in file("webapi")).
        configs(
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
        ).
        settings(
            webApiCommonSettings,
            resolvers ++= Seq(
                Resolver.bintrayRepo("hseeberger", "maven")
            ),
            libraryDependencies ++= webApiLibs
        ).
        settings(
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
        ).
        settings(
            scalacOptions ++= Seq("-feature", "-unchecked", "-deprecation", "-Yresolve-term-conflict:package"),

            logLevel := Level.Info,

            fork := true, // always fork

            run / javaOptions := webapiJavaRunOptions,

            reStart / javaOptions ++= resolvedJavaAgents.value map { resolved =>
                "-javaagent:" + resolved.artifact.absolutePath + resolved.agent.arguments
            },// allows sbt-javaagent to work with sbt-revolver
            reStart / javaOptions ++= webapiJavaRunOptions,

            javaAgents += library.aspectJWeaver,

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
        ).
        settings(
            // prepare for publishing

            // Skip packageDoc task on stage
            Compile / packageDoc / mappings := Seq(),

            Universal / mappings ++= {
                // copy the scripts folder
                directory("scripts") ++
                        // copy the configuration files to config directory
                        contentOf("configs").toMap.mapValues("config/" + _) ++
                        // copy configuration files to config directory
                        contentOf("src/main/resources").toMap.mapValues("config/" + _)
                // copy the aspectj weaver jar
                // contentOf("vendor").toMap.mapValues("aspectjweaver/" + _)
            },

            // add 'config' directory first in the classpath of the start script,
            scriptClasspath := Seq("../config/") ++ scriptClasspath.value,

            // add license
            licenses := Seq(("GNU AGPL", url("https://www.gnu.org/licenses/agpl-3.0"))),

            // need this here, so that the Manifest inside the jars has the correct main class set.
            Compile / mainClass := Some("org.knora.webapi.Main"),
            Compile / run / mainClass := Some("org.knora.webapi.Main"),
            Test / mainClass := Some("org.scalatest.tools.Runner"),
            IntegrationTest / mainClass := Some("org.scalatest.tools.Runner"),

            // add dockerCommands used to create the image
            // docker:stage, docker:publishLocal, docker:publish, docker:clean

            // wipe out all default docker commands
            dockerCommands := Seq(),

            maintainer := "ivan.subotic@unibas.ch",
            packageName := packageName.value,
            dockerRepository := Some("dhlabbasel"),
            dockerBaseImage := "openjdk:10-jre-slim-sid",
            dockerExposedPorts := Seq(3333, 10001),
            //dockerCommands ++= Seq(
            //    // install wget
            //    ExecCmd("RUN", "apt-get -qq update && apt-get install -y --no-install-recommends wget=1.19.5-2 && rm -rf /var/lib/apt/lists/*"),
            //    // install yourkit profiler
            //    ExecCmd("RUN", "wget https://www.yourkit.com/download/docker/YourKit-JavaProfiler-2018.04-docker.zip -P /tmp/ && unzip /tmp/YourKit-JavaProfiler-2018.04-docker.zip -d /usr/local && rm /tmp/YourKit-JavaProfiler-2018.04-docker.zip"),
            //),
            dockerEntrypoint := Seq("/opt/bin/webapi", "-J-agentpath:/usr/local/YourKit-JavaProfiler-2018.04/bin/linux-x86-64/libyjpagent.so=port=10001,listen=all")

        ).
        settings(
            buildInfoKeys ++= Seq[BuildInfoKey](
                name,
                version,
                "akkaHttp" -> akkaHttpVersion
            ),
            buildInfoPackage := "org.knora.webapi"
        ).
        enablePlugins(SbtTwirl). // enable the sbt-twirl plugin
        enablePlugins(JavaAppPackaging). // enable the sbt-native-packager plugin
        enablePlugins(DockerPlugin). // enable docker for the sbt-native-packager plugin
        enablePlugins(GatlingPlugin). // load testing
        enablePlugins(JavaAgent). // adds AspectJ Weaver configuration
        enablePlugins(RevolverPlugin).
        enablePlugins(BuildInfoPlugin) // allows generation of scala code with version information



lazy val webApiLibs = Seq(
    library.aspectJWeaver,
    library.akkaActor,
    library.akkaAgent,
    library.akkaHttp,
    library.akkaHttpCirce,
    library.akkaHttpCors,
    library.akkaHttpSprayJson,
    library.akkaHttpJacksonJava,
    library.akkaHttpTestkit,
    library.akkaHttpXml,
    library.akkaSlf4j,
    library.akkaStream,
    library.akkaStreamTestkit,
    library.akkaTestkit,
    library.bcprov,
    library.commonsBeanUtil,
    library.commonsIo,
    library.commonsText,
    library.commonsValidator,
    library.diff,
    library.ehcache,
    library.gatlingHighcharts,
    library.gatlingTestFramework,
    library.gwtServlet,
    library.jacksonScala,
    library.jaxbApi,
    library.jsonldJava,
    library.jodd,
    library.jodaTime,
    library.jodaConvert,
    library.jenaLibs,
    library.jenaText,
    library.jwt,
    //library.kamonCore,
    //library.kamonAkka,
    //library.kamonAkkaHttp,
    //library.kamonPrometheus,
    //library.kamonZipkin,
    //library.kamonJaeger,
    library.logbackClassic,
    library.rdf4jRuntime,
    library.saxonHE,
    library.scalaArm,
    library.scalaJava8Compat,
    library.scalaLogging,
    library.scalaTest,
    library.scalaXml,
    library.scallop,
    library.springSecurityCore,
    library.swaggerAkkaHttp,
    library.typesafeConfig,
    library.xmlunitCore,
    library.icu4j
)

lazy val library =
    new {
        object Version {
            val akkaBase = akkaVersion
            val akkaHttp = akkaHttpVersion
            val jena = "3.4.0"
            val metrics = "4.0.1"
        }

        // akka
        val akkaActor              = "com.typesafe.akka"            %% "akka-actor"               % Version.akkaBase
        val akkaAgent              = "com.typesafe.akka"            %% "akka-agent"               % Version.akkaBase
        val akkaStream             = "com.typesafe.akka"            %% "akka-stream"              % Version.akkaBase
        val akkaSlf4j              = "com.typesafe.akka"            %% "akka-slf4j"               % Version.akkaBase
        val akkaTestkit            = "com.typesafe.akka"            %% "akka-testkit"             % Version.akkaBase    % "test, it, gdbse, gdbse-it, gdbfree, gdbfree-it, tdb, fuseki, fuseki-it"
        val akkaStreamTestkit      = "com.typesafe.akka"            %% "akka-stream-testkit"      % Version.akkaBase    % "test, it, gdbse, gdbse-it, gdbfree, gdbfree-it, tdb, fuseki, fuseki-it"

        // akka http
        val akkaHttp               = "com.typesafe.akka"            %% "akka-http"                % Version.akkaHttp
        val akkaHttpXml            = "com.typesafe.akka"            %% "akka-http-xml"            % Version.akkaHttp
        val akkaHttpSprayJson      = "com.typesafe.akka"            %% "akka-http-spray-json"     % Version.akkaHttp
        val akkaHttpJacksonJava    = "com.typesafe.akka"            %% "akka-http-jackson"        % Version.akkaHttp
        val akkaHttpTestkit        = "com.typesafe.akka"            %% "akka-http-testkit"        % Version.akkaHttp    % "test, it, gdbse, gdbse-it, gdbfree, gdbfree-it, tdb, fuseki, fuseki-it"

        val typesafeConfig         = "com.typesafe"                  % "config"                   % "1.3.3"

        // testing
        val scalaTest              = "org.scalatest"                %% "scalatest"                % "3.0.4"             % "test, it, gdbse, gdbse-it, gdbfree, gdbfree-it, tdb, fuseki, fuseki-it"
        val gatlingHighcharts      = "io.gatling.highcharts"         % "gatling-charts-highcharts"% "2.3.1"             % "test, it, gdbse, gdbse-it, gdbfree, gdbfree-it, tdb, fuseki, fuseki-it"
        val gatlingTestFramework   = "io.gatling"                    % "gatling-test-framework"   % "2.3.1"             % "test, it, gdbse, gdbse-it, gdbfree, gdbfree-it, tdb, fuseki, fuseki-it"

        //CORS support
        val akkaHttpCors           = "ch.megard"                    %% "akka-http-cors"           % "0.3.0"

        // jena
        val jenaLibs               = "org.apache.jena"               % "apache-jena-libs"         % Version.jena exclude("org.slf4j", "slf4j-log4j12") exclude("commons-codec", "commons-codec")
        val jenaText               = "org.apache.jena"               % "jena-text"                % Version.jena exclude("org.slf4j", "slf4j-log4j12") exclude("commons-codec", "commons-codec")

        // logging
        val commonsLogging         = "commons-logging"               % "commons-logging"          % "1.2"
        val scalaLogging           = "com.typesafe.scala-logging"   %% "scala-logging"            % "3.8.0"
        val logbackClassic         = "ch.qos.logback"                % "logback-classic"          % "1.2.3"

        // input validation
        val commonsValidator       = "commons-validator"             % "commons-validator"        % "1.6" exclude("commons-logging", "commons-logging")

        // authentication
        val bcprov                 = "org.bouncycastle"              % "bcprov-jdk15on"           % "1.59"
        val springSecurityCore     = "org.springframework.security"  % "spring-security-core"     % "4.2.5.RELEASE" exclude("commons-logging", "commons-logging") exclude("org.springframework", "spring-aop")
        val jwt                    = "io.igl"                       %% "jwt"                      % "1.2.2" exclude("commons-codec", "commons-codec")

        // caching
        val ehcache                = "net.sf.ehcache"                % "ehcache"                  % "2.10.3"

        // monitoring
        val kamonCore              = "io.kamon"                     %% "kamon-core"               % "1.1.3"
        val kamonAkka              = "io.kamon"                     %% "kamon-akka-2.5"           % "1.1.1"
        val kamonAkkaHttp          = "io.kamon"                     %% "kamon-akka-http-2.5"      % "1.1.0"
        val kamonPrometheus        = "io.kamon"                     %% "kamon-prometheus"         % "1.1.1"
        val kamonZipkin            = "io.kamon"                     %% "kamon-zipkin"             % "1.0.0"
        val kamonJaeger            = "io.kamon"                     %% "kamon-jaeger"             % "1.0.2"
        val aspectJWeaver          = "org.aspectj"                   % "aspectjweaver"            % "1.9.1"

        // other
        //"javax.transaction" % "transaction-api" % "1.1-rev-1",
        val commonsText            = "org.apache.commons"            % "commons-text"             % "1.6"
        val commonsIo              = "commons-io"                    % "commons-io"               % "2.6"
        val commonsBeanUtil        = "commons-beanutils"             % "commons-beanutils"        % "1.9.3" exclude("commons-logging", "commons-logging") // not used by us, but need newest version to prevent this problem: http://stackoverflow.com/questions/14402745/duplicate-classes-in-commons-collections-and-commons-beanutils
        val jodd                   = "org.jodd"                      % "jodd"                     % "3.2.6"
        val jodaTime               = "joda-time"                     % "joda-time"                % "2.9.1"
        val jodaConvert            = "org.joda"                      % "joda-convert"             % "1.8"
        val diff                   = "com.sksamuel.diff"             % "diff"                     % "1.1.11"
        val xmlunitCore            = "org.xmlunit"                   % "xmlunit-core"             % "2.1.1"

        // other
        val rdf4jRuntime           = "org.eclipse.rdf4j"             % "rdf4j-runtime"            % "2.3.2"
        val scallop                = "org.rogach"                   %% "scallop"                  % "2.0.5"
        val gwtServlet             = "com.google.gwt"                % "gwt-servlet"              % "2.8.0"
        val saxonHE                = "net.sf.saxon"                  % "Saxon-HE"                 % "9.7.0-14"

        val scalaXml               = "org.scala-lang.modules"       %% "scala-xml"                % "1.1.1"
        val scalaArm               = "com.jsuereth"                  % "scala-arm_2.12"           % "2.0"
        val scalaJava8Compat       = "org.scala-lang.modules"        % "scala-java8-compat_2.12"  % "0.8.0"

        // provides akka jackson (json) support
        val akkaHttpCirce          = "de.heikoseeberger"            %% "akka-http-circe"          % "1.21.0"
        val jacksonScala           = "com.fasterxml.jackson.module" %% "jackson-module-scala"     % "2.9.4"

        val jsonldJava             = "com.github.jsonld-java"        % "jsonld-java"              % "0.12.0"

        // swagger (api documentation)
        val swaggerAkkaHttp        = "com.github.swagger-akka-http" %% "swagger-akka-http"        % "0.14.0"

        // Java EE modules which are deprecated in Java SE 9, 10 and will be removed in Java SE 11
        val jaxbApi                = "javax.xml.bind"                % "jaxb-api"                 % "2.2.12"

        val icu4j                  = "com.ibm.icu"                   % "icu4j"                    % "62.1"
    }

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
