import sbt._
import sbt.Keys._
import spray.revolver.RevolverPlugin._
import com.typesafe.sbt.SbtNativePackager.autoImport._

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
        settings(Revolver.settings: _*)

lazy val salsahCommonSettings = Seq(
    organization := "org.knora",
    name := "salsah",
    version := "0.1.0",
    scalaVersion := "2.11.7"
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
    "-Xms2048m",
    "-Xmx4096m"
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
    "org.seleniumhq.selenium" % "selenium-java" % "2.35.0" % "test"
)