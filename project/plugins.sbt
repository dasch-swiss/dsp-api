resolvers ++= Seq(
  Resolver.typesafeRepo("releases")
)

// please don't remove or merge uncommented to main
//addDependencyTreePlugin

addSbtPlugin("com.github.sbt"    % "sbt-git"             % "2.0.0")
addSbtPlugin("com.github.sbt"    % "sbt-native-packager" % "1.9.9")
addSbtPlugin("io.kamon"          % "sbt-aspectj-runner"  % "1.1.2")
addSbtPlugin("com.typesafe.sbt"  % "sbt-twirl"           % "1.5.1")
addSbtPlugin("com.eed3si9n"      % "sbt-assembly"        % "1.2.0")
addSbtPlugin("com.lightbend.sbt" % "sbt-javaagent"       % "0.1.6")
addSbtPlugin("io.gatling"        % "gatling-sbt"         % "4.2.4")
addSbtPlugin("org.scoverage"     % "sbt-scoverage"       % "1.9.3")
addSbtPlugin("com.eed3si9n"      % "sbt-buildinfo"       % "0.11.0")
addSbtPlugin("org.scalameta"     % "sbt-scalafmt"        % "2.4.6")
addSbtPlugin("ch.epfl.scala"     % "sbt-scalafix"        % "0.10.1")

// ad-hoc plugins - uncomment on demenad and keep it commented out in main branch

// https://github.com/rtimush/sbt-updates
// addSbtPlugin("com.timushev.sbt" % "sbt-updates" % "0.6.1")

// https://github.com/cb372/sbt-explicit-dependencies
// addSbtPlugin("com.github.cb372" % "sbt-explicit-dependencies" % "0.2.16")
