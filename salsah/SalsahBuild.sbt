import sbt._
import sbt.Keys.{licenses, mainClass, mappings, _}
import spray.revolver.RevolverPlugin._
import com.typesafe.sbt.SbtNativePackager.autoImport._
import com.typesafe.sbt.packager.MappingsHelper.{contentOf, directory}

lazy val salsah = (project in file(".")).
        settings(salsahCommonSettings:  _*).
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
    "-Xms1024m",
    "-Xmx1024m"
    // "-verbose:gc",
    //"-XX:+UseG1GC",
    //"-XX:MaxGCPauseMillis=500"
)

lazy val javaTestOptions = Seq(
    // "-showversion",
    "-Xms1024m",
    "-Xmx1024m"
    // "-verbose:gc",
    //"-XX:+UseG1GC",
    //"-XX:MaxGCPauseMillis=500",
    //"-XX:MaxMetaspaceSize=4096m"
)

lazy val salsahLibs = Seq(
    // akka
    "com.typesafe.akka" % "akka-http-core-experimental_2.11" % "2.0-M2",
    "com.typesafe.akka" % "akka-http-experimental_2.11" % "2.0-M2",
    "com.typesafe.akka" % "akka-http-spray-json-experimental_2.11" % "2.0-M2",
    "com.typesafe.akka" % "akka-http-xml-experimental_2.11" % "2.0-M2",
    // testing
    "com.typesafe.akka" %% "akka-http-testkit-experimental" % "2.0-M2" % "test",
    "org.scalatest" %% "scalatest" % "2.2.5" % "test",
    "org.seleniumhq.selenium" % "selenium-java" % "2.35.0" % "test",
    "io.spray" %% "spray-http" % "1.3.3",
    "io.spray" %% "spray-httpx" % "1.3.3",
    "io.spray" %% "spray-util" % "1.3.3",
    "io.spray" %% "spray-io" % "1.3.3",
    "io.spray" %% "spray-can" % "1.3.3",
    "io.spray" %% "spray-caching" % "1.3.3",
    "io.spray" %% "spray-routing" % "1.3.3",
    "io.spray" %% "spray-json" % "1.3.2",
    "io.spray" %% "spray-client" % "1.3.2"
)