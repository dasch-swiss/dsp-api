package org.knora.webapi.store.triplestore.upgrade

import com.typesafe.scalalogging.Logger
import org.knora.webapi.feature.FeatureFactoryConfig
import org.knora.webapi.store.triplestore.upgrade.plugins._

/**
 * The plan for updating a repository to work with the current version of Knora.
 */
object RepositoryUpdatePlan {

  /**
   * Constructs list of all repository update plugins in chronological order.
   *
   * @param featureFactoryConfig the feature factor configuration.
   */
  def makePluginsForVersions(log: Logger): Seq[PluginForKnoraBaseVersion] =
    Seq(
      PluginForKnoraBaseVersion(
        versionNumber = 1,
        plugin = new UpgradePluginPR1307(),
        prBasedVersionString = Some("PR 1307")
      ),
      PluginForKnoraBaseVersion(
        versionNumber = 2,
        plugin = new UpgradePluginPR1322(),
        prBasedVersionString = Some("PR 1322")
      ),
      PluginForKnoraBaseVersion(
        versionNumber = 3,
        plugin = new UpgradePluginPR1367(),
        prBasedVersionString = Some("PR 1367")
      ),
      PluginForKnoraBaseVersion(
        versionNumber = 4,
        plugin = new UpgradePluginPR1372(),
        prBasedVersionString = Some("PR 1372")
      ),
      PluginForKnoraBaseVersion(versionNumber = 5, plugin = new NoopPlugin, prBasedVersionString = Some("PR 1440")),
      PluginForKnoraBaseVersion(versionNumber = 6, plugin = new NoopPlugin), // PR 1206
      PluginForKnoraBaseVersion(versionNumber = 7, plugin = new NoopPlugin), // PR 1403
      PluginForKnoraBaseVersion(versionNumber = 8, plugin = new UpgradePluginPR1615()),
      PluginForKnoraBaseVersion(versionNumber = 9, plugin = new UpgradePluginPR1746(log)),
      PluginForKnoraBaseVersion(versionNumber = 10, plugin = new NoopPlugin), // PR 1808
      PluginForKnoraBaseVersion(versionNumber = 11, plugin = new NoopPlugin), // PR 1813
      PluginForKnoraBaseVersion(versionNumber = 12, plugin = new NoopPlugin), // PR 1891
      PluginForKnoraBaseVersion(
        versionNumber = 13,
        plugin = new UpgradePluginPR1921(log),
        prBasedVersionString = Some("PR 1921")
      ),
      PluginForKnoraBaseVersion(versionNumber = 14, plugin = new NoopPlugin), // PR 1992
      PluginForKnoraBaseVersion(
        versionNumber = 20,
        plugin = new UpgradePluginPR2018(log),
        prBasedVersionString = Some("PR 2018")
      )
    )

  /**
   * The built-in named graphs that are always updated when there is a new version of knora-base.
   */
  val builtInNamedGraphs: Set[BuiltInNamedGraph] = Set(
    BuiltInNamedGraph(
      filename = "knora-ontologies/knora-admin.ttl",
      iri = "http://www.knora.org/ontology/knora-admin"
    ),
    BuiltInNamedGraph(
      filename = "knora-ontologies/knora-base.ttl",
      iri = "http://www.knora.org/ontology/knora-base"
    ),
    BuiltInNamedGraph(
      filename = "knora-ontologies/salsah-gui.ttl",
      iri = "http://www.knora.org/ontology/salsah-gui"
    ),
    BuiltInNamedGraph(
      filename = "knora-ontologies/standoff-onto.ttl",
      iri = "http://www.knora.org/ontology/standoff"
    ),
    BuiltInNamedGraph(
      filename = "knora-ontologies/standoff-data.ttl",
      iri = "http://www.knora.org/data/standoff"
    )
  )

  /**
   * Represents an update plugin with its knora-base version number and version string.
   *
   * @param versionNumber        the knora-base version number that the plugin's transformation produces.
   * @param plugin               the plugin.
   * @param prBasedVersionString the plugin's PR-based version string (not used for new plugins).
   */
  case class PluginForKnoraBaseVersion(
    versionNumber: Int,
    plugin: UpgradePlugin,
    prBasedVersionString: Option[String] = None
  ) {
    lazy val versionString: String = {
      prBasedVersionString match {
        case Some(str) => str
        case None      => s"knora-base v$versionNumber"
      }
    }
  }

  /**
   * Represents a Knora built-in named graph.
   *
   * @param filename the filename containing the named graph.
   * @param iri      the IRI of the named graph.
   */
  case class BuiltInNamedGraph(filename: String, iri: String)

}
