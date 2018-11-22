

lazy val docs = project

lazy val salsah1 = project

lazy val webapi = project

inThisBuild(List(
    // These are normal sbt settings to configure for release, skip if already defined
    licenses := Seq("AGPL-3.0" -> url("https://opensource.org/licenses/AGPL-3.0")),
    homepage := Some(url("https://github.com/dhlab-basel/Knora")),
    scmInfo := Some(ScmInfo(url("https://github.com/dhlab-basel/Knora"), "scm:git:git@github.com:dhlab-basel/Knora.git")),

    // override dynver generated version string because docker hub rejects '+' in tags
    version in ThisBuild ~= (_.replace('+', '-')),
    dynver in ThisBuild ~= (_.replace('+', '-')),
))