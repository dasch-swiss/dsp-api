import sbt._
import NativePackagerHelper._

connectInput in run := true

// Bring the sbt-aspectj settings into this build
//aspectjSettings

lazy val webapi = (project in file(".")).
        configs(
            FusekiTest,
            FusekiIntegrationTest,
            GraphDBTest,
            EmbeddedJenaTDBTest,
            IntegrationTest
        ).
        settings(webApiCommonSettings:  _*).
        settings(inConfig(Test)(
            Defaults.testTasks ++ baseAssemblySettings
        ): _*).
        settings(inConfig(FusekiTest)(
            Defaults.testTasks ++ Seq(
                fork := true,
                javaOptions ++= javaFusekiTestOptions,
                testOptions += Tests.Argument("-oDF") // show full stack traces and test case durations
            )
        ): _*).
        settings(inConfig(FusekiIntegrationTest)(
            Defaults.testTasks ++ Seq(
                fork := true,
                javaOptions ++= javaFusekiIntegrationTestOptions,
                testOptions += Tests.Argument("-oDF") // show full stack traces and test case durations
            )
        ): _*).
        settings(inConfig(GraphDBTest)(
            Defaults.testTasks ++ Seq(
                fork := true,
                javaOptions ++= javaGraphDBTestOptions,
                testOptions += Tests.Argument("-oDF") // show full stack traces and test case durations
            )
        ): _*).
        settings(inConfig(EmbeddedJenaTDBTest)(
            Defaults.testTasks ++ Seq(
                fork := true,
                javaOptions ++= javaEmbeddedJenaTDBTestOptions,
                testOptions += Tests.Argument("-oDF") // show full stack traces and test case durations
            )
        ): _*).
        settings(inConfig(IntegrationTest)(
            Defaults.itSettings ++ Seq(
                fork := true,
                javaOptions ++= javaIntegrationTestOptions,
                testOptions += Tests.Argument("-oDF") // show full stack traces and test case durations
            ) ++ baseAssemblySettings
        ): _*).
        settings(
            libraryDependencies ++= webApiLibs,
            scalacOptions ++= Seq("-feature", "-unchecked", "-deprecation", "-Yresolve-term-conflict:package"),
            logLevel := Level.Info,
            fork in run := true,
            javaOptions in run ++= javaRunOptions,
            //javaOptions in run <++= AspectjKeys.weaverOptions in Aspectj,
            //javaOptions in Revolver.reStart <++= AspectjKeys.weaverOptions in Aspectj,
            mainClass in (Compile, run) := Some("org.knora.webapi.Main"),
            fork in Test := true,
            javaOptions in Test ++= javaTestOptions,
            parallelExecution in Test := false,
            // enable publishing the jar produced by `sbt test:package` and `sbt it:package`
            publishArtifact in (Test, packageBin) := true,
            publishArtifact in (IntegrationTest, packageBin) := true
        ).
        settings( // enable deployment staging with `sbt stage`. uses fat jar assembly.
            // we specify the name for our fat jars (main, test, it)
            assemblyJarName in assembly := s"assembly-${name.value}-main-${version.value}.jar",
            assemblyJarName in (Test, assembly) := s"assembly-${name.value}-test-${version.value}.jar",
            assemblyJarName in (IntegrationTest, assembly) := s"assembly-${name.value}-it-${version.value}.jar",
            // disable running of tests before fat jar assembly!
            test in assembly := {},
            test in (Test, assembly) := {},
            test in (IntegrationTest, assembly) := {},
            // Skip packageDoc task on stage
            mappings in (Compile, packageDoc) := Seq(),
            mappings in Universal := {
                // removes all jar mappings in universal and appends the fat jar
                // universalMappings: Seq[(File,String)]
                val universalMappings = (mappings in Universal).value
                val fatJar = (assembly in Compile).value
                // removing means filtering
                val filtered = universalMappings filter {
                    case (file, name) =>  ! name.endsWith(".jar")
                }
                // add the fat jar
                filtered :+ (fatJar -> ("lib/" + fatJar.getName))
            },
            mappings in Universal ++= {
                // copy the scripts folder
                directory("scripts") ++
                // copy the configuration files to config directory
                contentOf("configs").toMap.mapValues("config/" + _) ++
                // copy configuration files to config directory
                contentOf("src/main/resources").toMap.mapValues("config/" + _)
            },
            // the bash scripts classpath only needs the fat jar
            scriptClasspath := Seq( (assemblyJarName in assembly).value ),
            // add 'config' directory first in the classpath of the start script,
            scriptClasspath := Seq("../config/") ++ scriptClasspath.value,
            // add license
            licenses := Seq(("GNU AGPL", url("https://www.gnu.org/licenses/agpl-3.0"))),
            // need this here, so that the Manifest inside the jars has the correct main class set.
            mainClass in Compile := Some("org.knora.webapi.Main"),
            mainClass in Test := Some("org.scalatest.tools.Runner"),
            mainClass in IntegrationTest := Some("org.scalatest.tools.Runner")
        ).
        enablePlugins(SbtTwirl). // Enable the sbt-twirl plugin
        enablePlugins(JavaAppPackaging) // Enable the sbt-native-packager plugin

lazy val webApiCommonSettings = Seq(
    organization := "org.knora",
    name := "webapi",
    version := "0.1.0-beta",
    scalaVersion := "2.12.4"
)

lazy val webApiLibs = Seq(
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
    library.commonsLang3,
    library.commonsValidator,
    library.diff,
    library.ehcache,
    library.gwtServlet,
    library.jacksonScala,
    library.jsonldJava,
    library.jodd,
    library.jodaTime,
    library.jodaConvert,
    library.jenaLibs,
    library.jenaText,
    library.jwt,
    library.logbackClassic,
    library.rdf4jRioTurtle,
    library.rdf4jQueryParserSparql,
    library.sayonHE,
    library.scalaArm,
    library.scalaJava8Compat,
    library.scalaLogging,
    library.scalaTest,
    library.scalaXml,
    library.scallop,
    library.springSecurityCore,
    library.xmlunitCore
)

lazy val library =
    new {
        object Version {
            val akkaBase = "2.5.9"
            val akkaHttp = "10.0.11"
            val jena = "3.4.0"
            val aspectj = "1.8.7"
            val kamon = "0.5.2"
        }

        // akka
        val akkaActor              = "com.typesafe.akka"            %% "akka-actor"               % Version.akkaBase
        val akkaAgent              = "com.typesafe.akka"            %% "akka-agent"               % Version.akkaBase
        val akkaStream             = "com.typesafe.akka"            %% "akka-stream"              % Version.akkaBase
        val akkaSlf4j              = "com.typesafe.akka"            %% "akka-slf4j"               % Version.akkaBase
        val akkaHttp               = "com.typesafe.akka"            %% "akka-http"                % Version.akkaHttp
        val akkaHttpXml            = "com.typesafe.akka"            %% "akka-http-xml"            % Version.akkaHttp
        val akkaHttpSprayJson      = "com.typesafe.akka"            %% "akka-http-spray-json"     % Version.akkaHttp
        val akkaHttpJacksonJava    = "com.typesafe.akka"            %% "akka-http-jackson"        % Version.akkaHttp
        val akkaTestkit            = "com.typesafe.akka"            %% "akka-testkit"             % Version.akkaBase    % "test, fuseki, graphdb, tdb, it, fuseki-it"
        val akkaHttpTestkit        = "com.typesafe.akka"            %% "akka-http-testkit"        % Version.akkaHttp    % "test, fuseki, graphdb, tdb, it, fuseki-it"
        val akkaStreamTestkit      = "com.typesafe.akka"            %% "akka-stream-testkit"      % Version.akkaBase    % "test, fuseki, graphdb, tdb, it, fuseki-it"
        val scalaTest              = "org.scalatest"                %% "scalatest"                % "3.0.4"             % "test, fuseki, graphdb, tdb, it, fuseki-it"

        //CORS support
        val akkaHttpCors           = "ch.megard"                    %% "akka-http-cors"           % "0.1.10"

        // jena
        val jenaLibs               = "org.apache.jena"               % "apache-jena-libs"         % Version.jena exclude("org.slf4j", "slf4j-log4j12") exclude("commons-codec", "commons-codec")
        val jenaText               = "org.apache.jena"               % "jena-text"                % Version.jena exclude("org.slf4j", "slf4j-log4j12") exclude("commons-codec", "commons-codec")

        // logging
        val scalaLogging           = "com.typesafe.scala-logging"   %% "scala-logging"            % "3.5.0"
        val logbackClassic         = "ch.qos.logback"                % "logback-classic"          % "1.1.7"

        // input validation
        val commonsValidator       = "commons-validator"             % "commons-validator"        % "1.6" exclude("commons-logging", "commons-logging")

        // authentication
        val bcprov                 = "org.bouncycastle"              % "bcprov-jdk15on"           % "1.56"
        val springSecurityCore     = "org.springframework.security"  % "spring-security-core"     % "4.2.3.RELEASE" exclude("commons-logging", "commons-logging") exclude("org.springframework", "spring-aop")
        val jwt                    = "io.igl"                       %% "jwt"                      % "1.2.2" exclude("commons-codec", "commons-codec")

        // caching
        val ehcache                = "net.sf.ehcache"                % "ehcache"                  % "2.10.0"

        // monitoring - disabled for now
        val aspectjWeaver          = "org.aspectj"                   % "aspectjweaver"            % Version.aspectj
        val aspectjRt              = "org.aspectj"                   % "aspectjrt"                % Version.aspectj
        val kamonCore              = "io.kamon"                     %% "kamon-core"               % Version.kamon
        val kamonSpray             = "io.kamon"                     %% "kamon-spray"              % Version.kamon
        val kamonStatsD            = "io.kamon"                     %% "kamon-statsd"             % Version.kamon
        val kamonLogReporter       = "io.kamon"                     %% "kamon-log-reporter"       % Version.kamon
        val kamonSystemMetrics     = "io.kamon"                     %% "kamon-system-metrics"     % Version.kamon
        val kamonNewRelic          = "io.kamon"                     %% "kamon-newrelic"           % Version.kamon

        // other
        //"javax.transaction" % "transaction-api" % "1.1-rev-1",
        val commonsLang3           = "org.apache.commons"            % "commons-lang3"            % "3.4"
        val commonsIo              = "commons-io"                    % "commons-io"               % "2.4"
        val commonsBeanUtil        = "commons-beanutils"             % "commons-beanutils"        % "1.9.2" exclude("commons-logging", "commons-logging") // not used by us, but need newest version to prevent this problem: http://stackoverflow.com/questions/14402745/duplicate-classes-in-commons-collections-and-commons-beanutils
        val jodd                   = "org.jodd"                      % "jodd"                     % "3.2.6"
        val jodaTime               = "joda-time"                     % "joda-time"                % "2.9.1"
        val jodaConvert            = "org.joda"                      % "joda-convert"             % "1.8"
        val diff                   = "com.sksamuel.diff"             % "diff"                     % "1.1.11"
        val xmlunitCore            = "org.xmlunit"                   % "xmlunit-core"             % "2.1.1"

        // other
        val rdf4jRioTurtle         = "org.eclipse.rdf4j"             % "rdf4j-rio-turtle"         % "2.2.4"
        val rdf4jQueryParserSparql = "org.eclipse.rdf4j"             % "rdf4j-queryparser-sparql" % "2.2.4"
        val scallop                = "org.rogach"                   %% "scallop"                  % "2.0.5"
        val gwtServlet             = "com.google.gwt"                % "gwt-servlet"              % "2.8.0"
        val sayonHE                = "net.sf.saxon"                  % "Saxon-HE"                 % "9.7.0-14"

        val scalaXml               = "org.scala-lang.modules"       %% "scala-xml"                % "1.0.6"
        val scalaArm               = "com.jsuereth"                  % "scala-arm_2.12"           % "2.0"
        val scalaJava8Compat       = "org.scala-lang.modules"        % "scala-java8-compat_2.12"  % "0.8.0"

        // provides akka jackson (json) support
        val akkaHttpCirce          = "de.heikoseeberger"            %% "akka-http-circe"          % "1.18.0"
        val jacksonScala           = "com.fasterxml.jackson.module" %% "jackson-module-scala"     % "2.9.0"

        val jsonldJava             = "com.github.jsonld-java"        % "jsonld-java"              % "0.10.0"
    }

lazy val javaRunOptions = Seq(
    // "-showversion",
    "-Xms1G",
    "-Xmx1G"
    // "-verbose:gc",
    //"-XX:+UseG1GC",
    //"-XX:MaxGCPauseMillis=500"
)

lazy val javaBaseTestOptions = Seq(
    // "-showversion",
    "-Xms2G",
    "-Xmx2G"
    // "-verbose:gc",
    //"-XX:+UseG1GC",
    //"-XX:MaxGCPauseMillis=500",
    //"-XX:MaxMetaspaceSize=4096m"
)

lazy val javaTestOptions = Seq(
    "-Dconfig.resource=graphdb.conf"
) ++ javaBaseTestOptions

lazy val FusekiTest = config("fuseki") extend(Test)
lazy val javaFusekiTestOptions = Seq(
    "-Dconfig.resource=fuseki.conf"
) ++ javaBaseTestOptions

lazy val FusekiIntegrationTest = config("fuseki-it") extend(IntegrationTest)
lazy val javaFusekiIntegrationTestOptions = Seq(
    "-Dconfig.resource=fuseki.conf"
) ++ javaBaseTestOptions

lazy val GraphDBTest = config("graphdb") extend(Test)
lazy val javaGraphDBTestOptions = Seq(
    "-Dconfig.resource=graphdb.conf"
) ++ javaBaseTestOptions

lazy val EmbeddedJenaTDBTest = config("tdb") extend(Test)
lazy val javaEmbeddedJenaTDBTestOptions = Seq(
    "-Dconfig.resource=jenatdb.conf"
) ++ javaBaseTestOptions

// The 'IntegrationTest' config does not need to be created here, as it is a built-in config!
// The standard testing tasks are available, but must be prefixed with 'it:', e.g., 'it:test'
// The test need to be stored in the 'it' (and not 'test') folder. The standard source hierarchy is used, e.g., 'src/it/scala'
lazy val javaIntegrationTestOptions = Seq(
    "-Dconfig.resource=graphdb.conf"
) ++ javaBaseTestOptions