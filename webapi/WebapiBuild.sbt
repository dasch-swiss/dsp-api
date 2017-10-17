import sbt._
import sbt.Keys._
import spray.revolver.RevolverPlugin._
import NativePackagerHelper._

connectInput in run := true

// Bring the sbt-aspectj settings into this build
//aspectjSettings

lazy val webapi = (project in file(".")).
        configs(
            FusekiTest,
            FusekiIntegrationTest,
            GraphDBTest,
            GraphDBFreeTest,
            GraphDBFreeIntegrationTest,
            EmbeddedJenaTDBTest,
            IntegrationTest
        ).
        settings(webApiCommonSettings:  _*).
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
        settings(inConfig(GraphDBFreeTest)(
            Defaults.testTasks ++ Seq(
                fork := true,
                javaOptions ++= javaGraphDBFreeTestOptions,
                testOptions += Tests.Argument("-oDF") // show full stack traces and test case durations
            )
        ): _*).
        settings(inConfig(GraphDBFreeIntegrationTest)(
            Defaults.testTasks ++ Seq(
                fork := true,
                javaOptions ++= javaGraphDBFreeIntegrationTestOptions,
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
            )
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
            // enable publishing the jar produced by `sbt it:package`
            publishArtifact in (IntegrationTest, packageBin) := true,
            resolvers += Resolver.bintrayRepo("hseeberger", "maven")
        ).
        settings( // enable deployment staging with `sbt stage`
          mappings in Universal ++= {
            // copy the scripts folder
            directory("scripts") ++
            // copy configuration files to config directory
            contentOf("src/main/resources").toMap.mapValues("config/" + _)
          },
          // add 'config' directory first in the classpath of the start script,
          scriptClasspath := Seq("../config/") ++ scriptClasspath.value,
          // add license
          licenses := Seq(("GNU AGPL", url("https://www.gnu.org/licenses/agpl-3.0"))),
          // need this here, but why?
          mainClass in Compile := Some("org.knora.webapi.Main")
        ).
        settings(Revolver.settings: _*).
        enablePlugins(SbtTwirl). // Enable the SbtTwirl plugin
        enablePlugins(JavaAppPackaging) // Enable the sbt-native-packager plugin

lazy val webApiCommonSettings = Seq(
    organization := "org.knora",
    name := "webapi",
    version := "0.1.0-beta",
    ivyScala := ivyScala.value map { _.copy(overrideScalaVersion = true) },
    scalaVersion := "2.12.1"
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
    library.jacksonScala,
    library.jsonldJava,
    library.jodd,
    library.jodaTime,
    library.jodaConvert,
    library.jenaLibs,
    library.jenaTest,
    library.logbackClassic,
    library.scalaLogging,
    library.scalaTest,
    library.scalaXml,
    library.springSecurityCore,
    library.xmlunitCore,
    library.rdf4jRioTurtle,
    library.rdf4jQueryParserSparql,
    library.scallop,
    library.gwtServlet,
    library.sayonHE,
    library.scalaArm,
    library.scalaJava8Compat
)

lazy val library =
    new {
        object Version {
            val akkaBase = "2.4.19"
            val akkaHttp = "10.0.9"
            val jena = "3.0.0"
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
        val scalaTest              = "org.scalatest"                %% "scalatest"                % "3.0.0"             % "test, fuseki, graphdb, tdb, it, fuseki-it"


        //CORS support
        val akkaHttpCors           = "ch.megard"                    %% "akka-http-cors"           % "0.1.10"

        // jena
        val jenaLibs               = "org.apache.jena"               % "apache-jena-libs"         % Version.jena exclude("org.slf4j", "slf4j-log4j12")
        val jenaTest               = "org.apache.jena"               % "jena-text"                % Version.jena exclude("org.slf4j", "slf4j-log4j12")

        // logging
        val scalaLogging           = "com.typesafe.scala-logging"   %% "scala-logging"            % "3.5.0"
        val logbackClassic         = "ch.qos.logback"                % "logback-classic"          % "1.1.7"

        // input validation
        val commonsValidator       = "commons-validator"             % "commons-validator"        % "1.6"

        // authentication
        val bcprov                 = "org.bouncycastle"              % "bcprov-jdk15on"           % "1.56"
        val springSecurityCore     = "org.springframework.security"  % "spring-security-core"     % "4.2.1.RELEASE"

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
        val commonsBeanUtil        = "commons-beanutils"             % "commons-beanutils"        % "1.9.2" // not used by us, but need newest version to prevent this problem: http://stackoverflow.com/questions/14402745/duplicate-classes-in-commons-collections-and-commons-beanutils
        val jodd                   = "org.jodd"                      % "jodd"                     % "3.2.6"
        val jodaTime               = "joda-time"                     % "joda-time"                % "2.9.1"
        val jodaConvert            = "org.joda"                      % "joda-convert"             % "1.8"
        val diff                   = "com.sksamuel.diff"             % "diff"                     % "1.1.11"
        val xmlunitCore            = "org.xmlunit"                   % "xmlunit-core"             % "2.1.1"

        // other
        val rdf4jRioTurtle         = "org.eclipse.rdf4j"             % "rdf4j-rio-turtle"         % "2.2.1"
        val rdf4jQueryParserSparql = "org.eclipse.rdf4j"             % "rdf4j-queryparser-sparql" % "2.2.1"
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

lazy val javaTestOptions = Seq(
    // "-showversion",
    "-Xms2G",
    "-Xmx4G"
    // "-verbose:gc",
    //"-XX:+UseG1GC",
    //"-XX:MaxGCPauseMillis=500",
    //"-XX:MaxMetaspaceSize=4096m"
)

lazy val FusekiTest = config("fuseki") extend(Test)
lazy val javaFusekiTestOptions = Seq(
    "-Dconfig.resource=fuseki.conf"
) ++ javaTestOptions

lazy val FusekiIntegrationTest = config("fuseki-it") extend(IntegrationTest)
lazy val javaFusekiIntegrationTestOptions = Seq(
    "-Dconfig.resource=fuseki.conf"
) ++ javaTestOptions

lazy val GraphDBTest = config("graphdb") extend(Test)
lazy val javaGraphDBTestOptions = Seq(
    "-Dconfig.resource=graphdb.conf"
) ++ javaTestOptions

lazy val GraphDBFreeTest = config("graphdb-free") extend(Test)
lazy val javaGraphDBFreeTestOptions = Seq(
    "-Dconfig.resource=graphdb-free.conf"
) ++ javaTestOptions

lazy val GraphDBFreeIntegrationTest = config("graphdb-free-it") extend(IntegrationTest)
lazy val javaGraphDBFreeIntegrationTestOptions = Seq(
    "-Dconfig.resource=graphdb-free.conf"
) ++ javaTestOptions

lazy val EmbeddedJenaTDBTest = config("tdb") extend(Test)
lazy val javaEmbeddedJenaTDBTestOptions = Seq(
    "-Dconfig.resource=jenatdb.conf"
) ++ javaTestOptions

// The 'IntegrationTest' config does not need to be created here, as it is a built-in config!
// The standard testing tasks are available, but must be prefixed with 'it:', e.g., 'it:test'
// The test need to be stored in the 'it' (and not 'test') folder. The standard source hierarchy is used, e.g., 'src/it/scala'
lazy val javaIntegrationTestOptions = Seq(
    "-Dconfig.resource=graphdb.conf"
) ++ javaTestOptions