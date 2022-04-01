resolvers ++= Seq(
  Resolver.typesafeRepo("releases")
)

// please don't remove or merge uncommented to main
addDependencyTreePlugin

// Knora
addSbtPlugin("com.typesafe.sbt" % "sbt-git" % "1.0.2")
addSbtPlugin("com.github.sbt" % "sbt-native-packager" % "1.9.9")
addSbtPlugin("io.kamon" % "sbt-aspectj-runner" % "1.1.1")

// webapi
addSbtPlugin("io.spray" % "sbt-revolver" % "0.9.1")
addSbtPlugin("com.typesafe.sbt" % "sbt-twirl" % "1.5.1")
addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.14.10")

addSbtPlugin("com.lightbend.sbt" % "sbt-javaagent" % "0.1.5")
addSbtPlugin("io.gatling" % "gatling-sbt" % "2.2.2")
addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.6.1")
addSbtPlugin("com.codacy" % "sbt-codacy-coverage" % "1.3.15")
addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.11.0")

// https://github.com/rtimush/sbt-updates
// addSbtPlugin("com.timushev.sbt" % "sbt-updates" % "0.6.1")
// https://github.com/cb372/sbt-explicit-dependencies
// addSbtPlugin("com.github.cb372" % "sbt-explicit-dependencies" % "0.2.16")
