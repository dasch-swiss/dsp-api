import sbt._
import sbt.Keys._
import spray.revolver.RevolverPlugin._
import com.typesafe.sbt.SbtNativePackager.autoImport._


// Bring the sbt-aspectj settings into this build
//aspectjSettings

lazy val root = (project in file("."))

lazy val webapi = (project in file("webapi")).
        configs(
            FusekiTest,
            FusekiTomcatTest,
            GraphDBTest,
            GraphDBFreeTest,
            SesameTest,
            EmbeddedJenaTDBTest
        ).
        settings(webApiCommonSettings:  _*).
        settings(inConfig(FusekiTest)(
            Defaults.testTasks ++ Seq(
                fork := true,
                javaOptions ++= javaFusekiTestOptions,
                testOptions += Tests.Argument("-oDF")
            )
        ): _*).
        settings(inConfig(FusekiTomcatTest)(
            Defaults.testTasks ++ Seq(
                fork := true,
                javaOptions ++= javaFusekiTomcatTestOptions,
                testOptions += Tests.Argument("-oDF")
            )
        ): _*).
        settings(inConfig(GraphDBTest)(
            Defaults.testTasks ++ Seq(
                fork := true,
                javaOptions ++= javaGraphDBTestOptions,
                testOptions += Tests.Argument("-oDF")
            )
        ): _*).
        settings(inConfig(GraphDBFreeTest)(
            Defaults.testTasks ++ Seq(
                fork := true,
                javaOptions ++= javaGraphDBFreeTestOptions,
                testOptions += Tests.Argument("-oDF")
            )
        ): _*).
        settings(inConfig(SesameTest)(
            Defaults.testTasks ++ Seq(
                fork := true,
                javaOptions ++= javaSesameTestOptions,
                testOptions += Tests.Argument("-oDF")
            )
        ): _*).
        settings(inConfig(EmbeddedJenaTDBTest)(
            Defaults.testTasks ++ Seq(
                fork := true,
                javaOptions ++= javaEmbeddedJenaTDBTestOptions,
                testOptions += Tests.Argument("-oDF")
            )
        ): _*).
        settings(
            //javaOptions in FusekiTest ++= javaFusekiTestOptions,
            //javaOptions in FusekiTomcatTest ++= javaFusekiTomcatTestOptions,
            //javaOptions in GraphDBTest ++= javaGraphDBTestOptions,
            //javaOptions in EmbeddedJenaTDBTest ++= javaEmbeddedJenaTDBTestOptions,
            //fork in FusekiTest := true,
            //parallelExecution in FusekiTest := false,
            //testOptions in FusekiTest += Tests.Argument("-oDF")
        ).
        settings(
            libraryDependencies ++= webApiLibs,
            scalacOptions ++= Seq("-feature", "-deprecation", "-Yresolve-term-conflict:package"),
            logLevel := Level.Info,
            fork in run := true,
            javaOptions in run ++= javaRunOptions,
            //javaOptions in run <++= AspectjKeys.weaverOptions in Aspectj,
            //javaOptions in Revolver.reStart <++= AspectjKeys.weaverOptions in Aspectj,
            mainClass in (Compile, run) := Some("org.knora.webapi.Main"),
            fork in Test := true,
            javaOptions in Test ++= javaTestOptions,
            parallelExecution in Test := false,
            testOptions in Test += Tests.Argument("-oDF") // show full stack traces and test case durations
        ).
        settings(Revolver.settings: _*).
        enablePlugins(SbtTwirl) // Enable the SbtTwirl plugin

lazy val webApiCommonSettings = Seq(
    organization := "org.knora",
    name := "webapi",
    version := "0.1.0",
    ivyScala := ivyScala.value map { _.copy(overrideScalaVersion = true) },
    scalaVersion := "2.11.7"
)

lazy val webApiLibs = Seq(
    // akka
    "com.typesafe.akka" % "akka-actor_2.11" % "2.4.0",
    // "com.typesafe.akka" % "akka-stream-experimental_2.11" % "1.0-M3",
    // "com.typesafe.akka" % "akka-http-experimental_2.11" % "1.0-M3",
    // "com.typesafe.akka" % "akka-http-core-experimental_2.11" % "1.0-M3",
    // spray
    "io.spray" %% "spray-http" % "1.3.3",
    "io.spray" %% "spray-httpx" % "1.3.3",
    "io.spray" %% "spray-util" % "1.3.3",
    "io.spray" %% "spray-io" % "1.3.3",
    "io.spray" %% "spray-can" % "1.3.3",
    "io.spray" %% "spray-caching" % "1.3.3",
    "io.spray" %% "spray-routing" % "1.3.3",
    "io.spray" %% "spray-json" % "1.3.2",
    "io.spray" %% "spray-client" % "1.3.2",
    // jena
    "org.apache.jena" % "apache-jena-libs" % "3.0.0" exclude("org.slf4j", "slf4j-log4j12"),
    "org.apache.jena" % "jena-text" % "3.0.0" exclude("org.slf4j", "slf4j-log4j12"),
    // http client
    "net.databinder.dispatch" %% "dispatch-core" % "0.11.2",
    // logging
    "org.slf4j" % "slf4j-api" % "1.7.12",
    "org.slf4j" % "jcl-over-slf4j" % "1.7.12",
    "ch.qos.logback" % "logback-core" % "1.1.3",
    "ch.qos.logback" % "logback-classic" % "1.1.3",
    "com.typesafe.akka" % "akka-slf4j_2.11" % "2.4.0",
    "com.typesafe.scala-logging" %% "scala-logging" % "3.1.0",
    // input validation
    "commons-validator" % "commons-validator" % "1.4.1",
    // pretty printing
    "com.googlecode.kiama" % "kiama_2.11" % "1.8.0",
    // authentication
    "com.github.t3hnar" %% "scala-bcrypt" % "2.4",
    // caching
    "net.sf.ehcache" % "ehcache" % "2.10.0",
    // monitoring
    //"org.aspectj" % "aspectjweaver" % "1.8.7",
    //"org.aspectj" % "aspectjrt" % "1.8.7",
    //"io.kamon" %% "kamon-core" % "0.5.2",
    //"io.kamon" %% "kamon-spray" % "0.5.2",
    //"io.kamon" %% "kamon-statsd" % "0.5.2",
    //"io.kamon" %% "kamon-log-reporter" % "0.5.2",
    //"io.kamon" %% "kamon-system-metrics" % "0.5.2",
    //"io.kamon" %% "kamon-newrelic" % "0.5.2",
    // other
    //"javax.transaction" % "transaction-api" % "1.1-rev-1",
    "org.apache.commons" % "commons-lang3" % "3.4",
    "commons-io" % "commons-io" % "2.4",
    "commons-beanutils" % "commons-beanutils" % "1.9.2", // not used by us, but need newest version to prevent this problem: http://stackoverflow.com/questions/14402745/duplicate-classes-in-commons-collections-and-commons-beanutils
    "org.jodd" % "jodd" % "3.2.6",
    "joda-time" % "joda-time" % "2.9.1",
    "org.joda" % "joda-convert" % "1.8",
    // testing
    "com.typesafe.akka" %% "akka-testkit" % "2.4.0" % "test, fuseki, fuseki-tomcat, graphdb, tdb",
    "org.scalatest" %% "scalatest" % "2.2.5" % "test, fuseki, fuseki-tomcat, graphdb, tdb",
    "io.spray" %% "spray-testkit" % "1.3.3" % "test, fuseki, fuseki-tomcat, graphdb, tdb"
)

lazy val javaRunOptions = Seq(
    // "-showversion",
    "-Xms2048m",
    "-Xmx4096m"
    // "-verbose:gc",
    //"-XX:+UseG1GC",
    //"-XX:MaxGCPauseMillis=500"
)

lazy val javaTestOptions = Seq(
    // "-showversion",
    "-Xms8192m",
    "-Xmx8192m",
    // "-verbose:gc",
    //"-XX:+UseG1GC",
    //"-XX:MaxGCPauseMillis=500",
    "-XX:MaxMetaspaceSize=4096m"
)

lazy val FusekiTest = config("fuseki") extend(Test)
lazy val javaFusekiTestOptions = Seq(
    "-Dconfig.resource=fuseki.conf"
) ++ javaTestOptions

lazy val FusekiTomcatTest = config("fuseki-tomcat") extend(Test)
lazy val javaFusekiTomcatTestOptions = Seq(
    "-Dconfig.resource=fuseki-tomcat.conf"
) ++ javaTestOptions

lazy val GraphDBTest = config("graphdb") extend(Test)
lazy val javaGraphDBTestOptions = Seq(
    "-Dconfig.resource=graphdb.conf"
) ++ javaTestOptions

lazy val GraphDBFreeTest = config("graphdb-free") extend(Test)
lazy val javaGraphDBFreeTestOptions = Seq(
    "-Dconfig.resource=graphdb-free.conf"
) ++ javaTestOptions

lazy val SesameTest = config("sesame") extend(Test)
lazy val javaSesameTestOptions = Seq(
    "-Dconfig.resource=sesame.conf"
) ++ javaTestOptions


lazy val EmbeddedJenaTDBTest = config("tdb") extend(Test)
lazy val javaEmbeddedJenaTDBTestOptions = Seq(
    "-Dconfig.resource=jenatdb.conf"
) ++ javaTestOptions

// skip test before creating fat-jar
test in assembly := {}

// set fat-jar main class
mainClass in assembly := Some("org.knora.webapi.Main")

// change merge strategy for fat-jar
assemblyMergeStrategy in assembly := {
    case PathList("org", "apache", "commons", "logging", xs @ _*)   => MergeStrategy.first
    case PathList("META-INF", xs @ _*) =>
    xs.map(_.toLowerCase) match {
        case ("manifest.mf" :: Nil) | ("index.list" :: Nil) | ("dependencies" :: Nil) =>
        MergeStrategy.discard
        case ps @ (x :: xs) if ps.last.endsWith(".sf") || ps.last.endsWith(".dsa") || ps.last.endsWith("license") || ps.last.endsWith("license.txt") || ps.last.endsWith("notice") || ps.last.endsWith("notice.txt") =>
        MergeStrategy.discard
        case "plexus" :: xs =>
        MergeStrategy.discard
        case "services" :: xs =>
        MergeStrategy.filterDistinctLines
        case ("spring.schemas" :: Nil) | ("spring.handlers" :: Nil) =>
        MergeStrategy.filterDistinctLines
        case ps@(x :: xs) if ps.last.endsWith("aop.xml") => MergeStrategy.first
        case _ => MergeStrategy.deduplicate
    }
    case x =>
    val oldStrategy = (assemblyMergeStrategy in assembly).value
    oldStrategy(x)
}

// Custom run task
//lazy val generateFakeTriplestore = taskKey[Unit]("Generate fake triplestore from a list of requests")

//fullRunTask(generateFakeTriplestore, Test, "org.knora.webapi.GenFakeTripleStore")
