resolvers ++= Seq(
  Resolver.typesafeRepo("releases"),
)

addSbtPlugin("com.github.sbt"          % "sbt-native-packager" % "1.10.0")
addSbtPlugin("io.kamon"                % "sbt-aspectj-runner"  % "1.1.2")
addSbtPlugin("org.playframework.twirl" % "sbt-twirl"           % "2.0.7")
addSbtPlugin("com.eed3si9n"            % "sbt-assembly"        % "2.2.0")
addSbtPlugin("com.github.sbt"          % "sbt-javaagent"       % "0.1.8")
addSbtPlugin("org.scoverage"           % "sbt-scoverage"       % "2.1.0")
addSbtPlugin("com.eed3si9n"            % "sbt-buildinfo"       % "0.12.0")
addSbtPlugin("org.scalameta"           % "sbt-scalafmt"        % "2.5.2")
addSbtPlugin("ch.epfl.scala"           % "sbt-scalafix"        % "0.12.1")
addSbtPlugin("de.heikoseeberger"       % "sbt-header"          % "5.10.0")
