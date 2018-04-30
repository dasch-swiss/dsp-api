import scala.sys.process._

// Define `Configuration` instances representing our different documentation trees
val ParadoxSite = config("paradox")
val SphinxSite = config("sphinx")


lazy val root = (project in file(".")).
        configs(
            ParadoxSite,
            SphinxSite
        ).
        enablePlugins(JekyllPlugin, ParadoxPlugin, ParadoxSitePlugin, ParadoxMaterialThemePlugin, SphinxPlugin, GhpagesPlugin).
        settings(
            // Apply default settings to our two custom configuration instances
            ParadoxSitePlugin.paradoxSettings(ParadoxSite),
            ParadoxMaterialThemePlugin.paradoxMaterialThemeGlobalSettings, // paradoxTheme and version
            ParadoxMaterialThemePlugin.paradoxMaterialThemeSettings(ParadoxSite),
            SphinxPlugin.sphinxSettings(SphinxSite)
        ).
        settings(
            // Set version string
            version in ParadoxSite := "v1.3.0",
            version in SphinxSite := "v1.3.0",

            // Ghpages settings
            ghpagesNoJekyll := true,
            git.remoteRepo := "git@github.com:dhlab-basel/Knora.git",
            excludeFilter in ghpagesCleanSite :=
                new FileFilter{
                    def accept(f: File) = (ghpagesRepository.value / "CNAME").getCanonicalPath == f.getCanonicalPath
                } || "LICENSE.md" || "README.md",

            // (sbt-site) Customize the source directory
            // sourceDirectory in Jekyll := sourceDirectory.value / "overview",
            sourceDirectory in ParadoxSite := sourceDirectory.value / "paradox",
            sourceDirectory in SphinxSite := sourceDirectory.value / "sphinx",

            // (sbt-site) Customize the output directory (subdirectory of site)
            siteSubdirName in ParadoxSite := "paradox",
            siteSubdirName in SphinxSite := "sphinx",

            // Set some paradox properties
            paradoxProperties in ParadoxSite ++= Map(
                "project.name" -> "Knora Documentation",
                "github.base_url" -> "https://github.com/dhlab-basel/Knora"
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
            // only execute building of typescript docs after makeSite
            makeSite := makeSite.dependsOn(buildTypescriptDocs).value
        )



lazy val buildTypescriptDocs = taskKey[Unit]("Build typescript API V1 and API V2 documentation.")

buildTypescriptDocs := {
    val s: TaskStreams = streams.value
    val shell: Seq[String] = if (sys.props("os.name").contains("Windows")) Seq("cmd", "/c") else Seq("bash", "-c")
    val clean: Seq[String] = shell :+ "make clean"
    val jsonformat: Seq[String] = shell :+ "make jsonformat"
    s.log.info("building typescript documentation...")
    if((clean #&& jsonformat !) == 0) {
        s.log.success("typescript documentation build successful!")
    } else {
        throw new IllegalStateException("typescript documentation build failed!")
    }
}










