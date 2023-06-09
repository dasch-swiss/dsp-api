addCommandAlias("fmt", "; all root/scalafmtSbt root/scalafmtAll")

val zioVersion            = "2.0.13"
val zioJsonVersion        = "0.5.0"
val zioConfigVersion      = "3.0.7"
val zioLoggingVersion     = "2.1.12"
val logbackClassicVersion = "1.4.7"
val testContainersVersion = "0.40.15"
val zioMockVersion        = "1.0.0-RC11"
val zioNioVersion         = "2.0.1"
val zioPreludeVersion     = "1.0.0-RC19"
val zioHttpVersion        = "3.0.0-RC1"

lazy val root = (project in file("."))
  .settings(
    inThisBuild(
      List(
        organization := "daschswiss",
        scalaVersion := "3.3.0",
      )
    ),
    name                   := "dsp-ingest",
    libraryDependencies ++= Seq(
      "dev.zio"       %% "zio"                      % zioVersion,
      "dev.zio"       %% "zio-streams"              % zioVersion,
      "dev.zio"       %% "zio-http"                 % zioHttpVersion,
      "dev.zio"       %% "zio-config"               % zioConfigVersion,
      "dev.zio"       %% "zio-config-typesafe"      % zioConfigVersion,
      "ch.qos.logback" % "logback-classic"          % logbackClassicVersion,
      "dev.zio"       %% "zio-json"                 % zioJsonVersion,
      "dev.zio"       %% "zio-nio"                  % zioNioVersion,
      "dev.zio"       %% "zio-prelude"              % zioPreludeVersion,
      "dev.zio"       %% "zio-json-interop-refined" % "0.5.0",
      "eu.timepit"    %% "refined"                  % "0.10.3",

      // logging
      "dev.zio"       %% "zio-logging"       % zioLoggingVersion,
      "dev.zio"       %% "zio-logging-slf4j" % zioLoggingVersion,
      "ch.qos.logback" % "logback-classic"   % logbackClassicVersion,

      // test
      "dev.zio" %% "zio-test"          % zioVersion     % Test,
      "dev.zio" %% "zio-test-sbt"      % zioVersion     % Test,
      "dev.zio" %% "zio-test-junit"    % zioVersion     % Test,
      "dev.zio" %% "zio-mock"          % zioMockVersion % Test,
      "dev.zio" %% "zio-test-magnolia" % zioVersion     % Test,
    ),
    testFrameworks         := Seq(new TestFramework("zio.test.sbt.ZTestFramework")),
    jibBaseImage           := "gcr.io/distroless/java17-debian11",
    jibName                := "dsp-ingest",
    jibUseCurrentTimestamp := true,
  )
  .enablePlugins(JavaAppPackaging)
