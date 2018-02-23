

// Define two `Configuration` instances.
val SphinxSite = config("sphinx")
val ParadoxSite = config("paradox")

name := "docs"

// Apply the default Paradox settings to the `ParadoxSite` config
ParadoxSitePlugin.paradoxSettings(ParadoxSite)

// Customize the source directory
sourceDirectory in ParadoxSite := sourceDirectory.value / "paradox"

// Customize the output subdirectory
siteSubdirName in ParadoxSite := "paradox"

// Apply the default Paradox settings to the `SphinxSite` config
SphinxPlugin.sphinxSettings(SphinxSite)

// Customize the source directory
sourceDirectory in SphinxSite := sourceDirectory.value / "sphinx"

// Customize the output subdirectory
siteSubdirName in SphinxSite := "sphinx"

// Global Paradox settings
enablePlugins(ParadoxPlugin)
paradoxTheme := Some(builtinParadoxTheme("generic"))

// Global Sphynx settings
enablePlugins(SphinxPlugin)

