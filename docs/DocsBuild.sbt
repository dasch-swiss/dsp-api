import scala.sys.process._

// Define `Configuration` instances representing our different documentation trees
val ParadoxSite = config("paradox")

lazy val root = (project in file(".")).
    configs(
        ParadoxSite
    ).
    enablePlugins(JekyllPlugin, ParadoxPlugin, ParadoxSitePlugin, ParadoxMaterialThemePlugin, GhpagesPlugin).
    settings(
        // Apply default settings to our two custom configuration instances
        ParadoxSitePlugin.paradoxSettings(ParadoxSite),
        ParadoxMaterialThemePlugin.paradoxMaterialThemeGlobalSettings, // paradoxTheme and version
        ParadoxMaterialThemePlugin.paradoxMaterialThemeSettings(ParadoxSite)
    ).
    settings(
        // Set version string
        version in ParadoxSite := "v1.5.0",

        // Ghpages settings
        ghpagesNoJekyll := true,
        git.remoteRepo := "git@github.com:dhlab-basel/Knora.git",
        excludeFilter in ghpagesCleanSite :=
            new FileFilter {
                def accept(f: File) = (ghpagesRepository.value / "CNAME").getCanonicalPath == f.getCanonicalPath
            } || "LICENSE.md" || "README.md",

        // (sbt-site) Customize the source directory
        // sourceDirectory in Jekyll := sourceDirectory.value / "overview",
        sourceDirectory in ParadoxSite := sourceDirectory.value / "paradox",

        // (sbt-site) Customize the output directory (subdirectory of site)
        siteSubdirName in ParadoxSite := "paradox",

        // Set some paradox properties
        paradoxProperties in ParadoxSite ++= Map(
            "project.name" -> "Knora Documentation",
            "github.base_url" -> "https://github.com/dhlab-basel/Knora",
            "image.base_url" -> ".../assets/images",
            "extref.rfc.base_url" -> "http://tools.ietf.org/html/rfc%s"
        ),

        // Paradox Material Theme Settings
        paradoxMaterialTheme in ParadoxSite ~= {
            _.withColor("blue", "yellow")
                .withRepository(uri("https://github.com/dhlab-basel/Knora/docs"))
                .withFavicon("cloud")
                .withLogoIcon("cloud")
                .withSocial(
                    uri("https://github.com/dhlab-basel"),
                    uri("https://twitter.com/dhlabbasel")
                )
                .withLanguage(java.util.Locale.ENGLISH)
                .withCopyright("Copyright 2015-2018 the contributors (see Contributors.md)")
        },
        mappings in makeSite ++= Seq(
            file("src/api-admin/index.html") -> "api-admin/index.html",
            file("src/api-admin/swagger.json") -> "api-admin/swagger.json"
        ),
        makeSite := makeSite.dependsOn(buildPrequisites).value
    )

lazy val buildPrequisites = taskKey[Unit]("Build typescript API documentation and Graphviz diagrams.")

buildPrequisites := {
    val s: TaskStreams = streams.value
    val shell: Seq[String] = if (sys.props("os.name").contains("Windows")) Seq("cmd", "/c") else Seq("bash", "-c")
    val clean: Seq[String] = shell :+ "make clean"
    val jsonformat: Seq[String] = shell :+ "make jsonformat"
    val graphvizfigures: Seq[String] = shell :+ "make graphvizfigures"
    val jsonformattest: Seq[String] = shell :+ "make jsonformattest"
    s.log.info("building typescript documentation and graphviz diagrams...")
    if ((clean #&& jsonformattest #&& jsonformat #&& graphvizfigures !) == 0) {
        Thread.sleep(500)
        s.log.success("typescript documentation and graphviz diagrams built successfully")
    } else {
        throw new IllegalStateException("typescript documentation and graphviz diagrams failed to build")
    }
}
