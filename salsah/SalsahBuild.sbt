import sbt._
import sbt.Keys.{licenses, mainClass, mappings, _}
import spray.revolver.RevolverPlugin._
import com.typesafe.sbt.SbtNativePackager.autoImport._
import com.typesafe.sbt.packager.MappingsHelper.{contentOf, directory}

lazy val salsah = (project in file(".")).
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

lazy val salsahCommonSettings = Seq(
    organization := "org.knora",
    name := "salsah",
    version := "0.1.0",
    scalaVersion := "2.11.7"
)

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

lazy val akkaVersion = "2.4.19"
lazy val akkaHttpVersion = "10.0.7"

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