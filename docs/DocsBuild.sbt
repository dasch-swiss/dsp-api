// Define `Configuration` instances representing our different documentation trees
val WrapperSite = config("wrapper")
val ParadoxSite = config("paradox")
val SphinxSite = config("sphinx")


lazy val root = (project in file(".")).
        configs(
            WrapperSite,
            ParadoxSite,
            SphinxSite
        ).
        enablePlugins(ParadoxPlugin, ParadoxSitePlugin, ParadoxMaterialThemePlugin, SphinxPlugin, GhpagesPlugin).
        settings(
            // Apply default settings to our two custom configuration instances
            ParadoxSitePlugin.paradoxSettings(WrapperSite),
            ParadoxMaterialThemePlugin.paradoxMaterialThemeSettings(WrapperSite),
            ParadoxSitePlugin.paradoxSettings(ParadoxSite),
            ParadoxMaterialThemePlugin.paradoxMaterialThemeGlobalSettings, // paradoxTheme and version
            ParadoxMaterialThemePlugin.paradoxMaterialThemeSettings(ParadoxSite),
            SphinxPlugin.sphinxSettings(SphinxSite)
        ).
        settings(
            // Set version string
            version in ParadoxSite := "v1.3.0",

            // Ghpages settings
            ghpagesNoJekyll := true,
            git.remoteRepo := "git@github.com:dhlab-basel/Knora.git",

            // Customize the source directory
            sourceDirectory in ParadoxSite := sourceDirectory.value / "paradox",
            sourceDirectory in SphinxSite := sourceDirectory.value / "sphinx",

            // Customize the output directory (subdirectory of site)
            siteSubdirName in ParadoxSite := "paradox",
            siteSubdirName in SphinxSite := "sphinx",


            paradoxProperties in WrapperSite ++= Map(
                "project.name" -> "Knora Documentation Overview",
                "github.base_url" -> "https://github.com/dhlab-basel/Knora/docs"
            ),
            // Paradox Material Theme Settings
            paradoxMaterialTheme in WrapperSite ~= {
                _.withColor("blue", "yellow")
                        .withRepository(uri("https://github.com/dhlab-basel/Knora"))
                        .withFavicon("cloud")
                        .withLogoIcon("cloud")
                        .withSocial(
                            uri("https://github.com/dhlab-basel"),
                            uri("https://twitter.com/dhlabbasel")
                        )
                        .withLanguage(java.util.Locale.ENGLISH)
                        .withCopyright("Copyright 2015-2018 the contributors (see Contributors.md)")
            },


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
                file("redoc/index.html") -> "api-admin/index.html",
                file("_format_docu") -> "api-v1",
                file("_format_docu_v2") -> "api-v2"
            )
        )










