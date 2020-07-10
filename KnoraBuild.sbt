
import com.typesafe.sbt.SbtNativePackager.autoImport.NativePackagerHelper._
import com.typesafe.sbt.packager.docker.DockerPlugin.autoImport.{Docker, dockerRepository}
import com.typesafe.sbt.packager.docker.{Cmd, ExecCmd}
import org.knora.Dependencies
import sbt.Keys.version

import scala.language.postfixOps
import scala.sys.process.Process

//////////////////////////////////////
// GLOBAL SETTINGS
//////////////////////////////////////

lazy val aggregatedProjects: Seq[ProjectReference] = Seq(docs)

lazy val buildSettings = Dependencies.Versions ++ Seq(
    organization := "org.knora",
    version := (ThisBuild / version).value
)

lazy val rootBaseDir = baseDirectory.in(ThisBuild)

lazy val root: Project = Project(id = "knora", file("."))
  .aggregate(aggregatedProjects: _*)
  .enablePlugins(DockerComposePlugin, GitVersioning, GitBranchPrompt)
  .settings(Dependencies.Versions)
  .settings(
      // values set for all sub-projects
      // These are normal sbt settings to configure for release, skip if already defined

      Global / onChangedBuildSource := ReloadOnSourceChanges,

      ThisBuild / licenses := Seq("AGPL-3.0" -> url("https://opensource.org/licenses/AGPL-3.0")),
      ThisBuild / homepage := Some(url("https://github.com/dhlab-basel/Knora")),
      ThisBuild / scmInfo := Some(ScmInfo(url("https://github.com/dasch-swiss/knora-api"), "scm:git:git@github.com:dasch-swiss/knora-api.git")),

      // use 'git describe' for deriving the version
      git.useGitDescribe := true,

      // override generated version string because docker hub rejects '+' in tags
      ThisBuild / version ~= (_.replace('+', '-')),

      // use Ctrl-c to stop current task but not quit SBT
      Global / cancelable := true,

      publish / skip := true,

      Dependencies.sysProps := sys.props.toString(),
      Dependencies.sysEnvs := sys.env.toString(),
  )


//////////////////////////////////////
// DOCS (./docs)
//////////////////////////////////////

// Define `Configuration` instances representing our different documentation trees
lazy val ParadoxSite = config("paradox")

lazy val docs = knoraModule("docs")
  .enablePlugins(JekyllPlugin, ParadoxPlugin, ParadoxSitePlugin, ParadoxMaterialThemePlugin, GhpagesPlugin)
  .configs(
      ParadoxSite
  )
  .settings(
      // Apply default settings to our two custom configuration instances
      ParadoxSitePlugin.paradoxSettings(ParadoxSite),
      ParadoxMaterialThemePlugin.paradoxMaterialThemeGlobalSettings, // paradoxTheme and version
      ParadoxMaterialThemePlugin.paradoxMaterialThemeSettings(ParadoxSite),

      // Skip packageDoc and packageSrc task on stage
      Compile / packageDoc / mappings := Seq(),
      Compile / packageSrc / mappings := Seq(),
  )
  .settings(

      // Ghpages settings
      ghpagesNoJekyll := true,
      git.remoteRepo := "git@github.com:dhlab-basel/Knora.git",
      ghpagesCleanSite / excludeFilter :=
        new FileFilter {
            def accept(f: File) = (ghpagesRepository.value / "CNAME").getCanonicalPath == f.getCanonicalPath
        } || "LICENSE.md" || "README.md",

      // (sbt-site) Customize the source directory
      // sourceDirectory in Jekyll := sourceDirectory.value / "overview",
      ParadoxSite / sourceDirectory := sourceDirectory.value / "paradox",

      // (sbt-site) Customize the output directory (subdirectory of site)
      ParadoxSite / siteSubdirName := "paradox",

      // Set some paradox properties
      ParadoxSite / paradoxProperties ++= Map(
          "project.name" -> "Knora Documentation",
          "github.base_url" -> "https://github.com/dhlab-basel/Knora",
          "image.base_url" -> ".../assets/images",
          "extref.rfc.base_url" -> "http://tools.ietf.org/html/rfc%s",
          "snip.src.base_dir" -> ((baseDirectory in ThisBuild).value / "webapi" / "src" / "main" / "scala").getAbsolutePath,
          "snip.test.base_dir" -> ((baseDirectory in ThisBuild).value / "webapi" / "src" / "test" / "scala").getAbsolutePath
      ),

      // Paradox Material Theme Settings
      ParadoxSite / paradoxMaterialTheme ~= {
          _.withColor("blue", "yellow")
            .withRepository(uri("https://github.com/dhlab-basel/Knora/docs"))
            .withFavicon("cloud")
            .withLogoIcon("cloud")
            .withSocial(
                uri("https://github.com/dhlab-basel"),
                uri("https://twitter.com/dhlabbasel")
            )
            .withLanguage(java.util.Locale.ENGLISH)
            .withCopyright("Copyright 2015-2019 the contributors (see Contributors.md)")
      },
      makeSite / mappings ++= Seq(
          file("docs/src/api-admin/index.html") -> "api-admin/index.html",
          file("docs/src/api-admin/swagger.json") -> "api-admin/swagger.json"
      ),
      makeSite := makeSite.dependsOn(buildPrequisites).value
  )

lazy val buildPrequisites = taskKey[Unit]("Build typescript API documentation and Graphviz diagrams.")

docs / buildPrequisites := {
    val s: TaskStreams = streams.value

    val execDir: Option[File] = if (sys.props("user.dir").endsWith("docs")) {
        // running from docs directory, which is fine
        None
    } else {
        // running from project root directory
        Some(new File(sys.props("user.dir") + "/docs"))
    }

    val shell: Seq[String] = if (sys.props("os.name").contains("Windows")) Seq("cmd", "/c") else Seq("bash", "-c")
    val clean: Seq[String] = shell :+ "make clean"
    val jsonformat: Seq[String] = shell :+ "make jsonformat"
    val graphvizfigures: Seq[String] = shell :+ "make graphvizfigures"
    val jsonformattest: Seq[String] = shell :+ "make jsonformattest"

    s.log.info("building typescript documentation and graphviz diagrams...")

    if ((Process(clean, execDir) #&& Process(jsonformattest, execDir) #&& Process(jsonformat, execDir) #&& Process(graphvizfigures, execDir) !) == 0) {
        Thread.sleep(500)
        s.log.success("typescript documentation and graphviz diagrams built successfully")
    } else {
        throw new IllegalStateException("typescript documentation and graphviz diagrams failed to build")
    }
}

def knoraModule(name: String): Project =
    Project(id = name, base = file(name))
      .settings(buildSettings)
