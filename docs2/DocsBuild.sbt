
lazy val root = (project in file(".")).
    enablePlugins(ParadoxPlugin).
    settings(
        name := "docs",
        paradoxTheme := Some(builtinParadoxTheme("generic"))
    )