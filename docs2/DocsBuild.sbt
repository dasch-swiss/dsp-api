
lazy val root = (project in file(".")).
        settings(
            name := "docs",
            paradoxTheme := Some(builtinParadoxTheme("generic")),
        ).
        enablePlugins(ParadoxPlugin)