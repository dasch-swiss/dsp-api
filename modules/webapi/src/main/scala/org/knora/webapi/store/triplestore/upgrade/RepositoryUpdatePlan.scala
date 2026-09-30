/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.triplestore.upgrade

import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.store.triplestore.upgrade.plugins.*

/**
 * The plan for updating a repository to work with the current version of Knora.
 */
object RepositoryUpdatePlan {

  /**
   * @param name unique across the plan (e.g. the ticket), so two branches bumping to the same version conflict on merge.
   */
  final case class PluginForKnoraBaseVersion(versionNumber: Int, name: String, plugin: UpgradePlugin)

  /**
   * All repository update plugins in chronological order.
   */
  val pluginsForVersions: Seq[PluginForKnoraBaseVersion] =
    Seq(
      PluginForKnoraBaseVersion(versionNumber = 1, name = "PR1307", plugin = new UpgradePluginPR1307()),
      PluginForKnoraBaseVersion(versionNumber = 2, name = "PR1322", plugin = new UpgradePluginPR1322()),
      PluginForKnoraBaseVersion(versionNumber = 3, name = "PR1367", plugin = new UpgradePluginPR1367()),
      PluginForKnoraBaseVersion(versionNumber = 4, name = "PR1372", plugin = new UpgradePluginPR1372()),
      PluginForKnoraBaseVersion(versionNumber = 8, name = "PR1615", plugin = new UpgradePluginPR1615()),
      PluginForKnoraBaseVersion(versionNumber = 9, name = "PR1746", plugin = new UpgradePluginPR1746()),
      PluginForKnoraBaseVersion(versionNumber = 13, name = "PR1921", plugin = new UpgradePluginPR1921()),
      PluginForKnoraBaseVersion(versionNumber = 20, name = "PR2018", plugin = new UpgradePluginPR2018()),
      PluginForKnoraBaseVersion(versionNumber = 21, name = "PR2079", plugin = new UpgradePluginPR2079()),
      PluginForKnoraBaseVersion(versionNumber = 22, name = "PR2081", plugin = new UpgradePluginPR2081()),
      PluginForKnoraBaseVersion(versionNumber = 23, name = "PR2094", plugin = new UpgradePluginPR2094()),
      PluginForKnoraBaseVersion(versionNumber = 29, name = "PR3110", plugin = new UpgradePluginPR3110()),
      PluginForKnoraBaseVersion(versionNumber = 30, name = "PR3111", plugin = new UpgradePluginPR3111()),
      PluginForKnoraBaseVersion(versionNumber = 31, name = "PR3112", plugin = new UpgradePluginPR3112()),
      PluginForKnoraBaseVersion(versionNumber = 41, name = "PR3383", plugin = new UpgradePluginPR3383()),
      PluginForKnoraBaseVersion(versionNumber = 50, name = "PR3612", plugin = new UpgradePluginPR3612()),
      PluginForKnoraBaseVersion(versionNumber = 51, name = "DEV-5963", plugin = new MigrateOnlyBuiltInGraphs()),
      PluginForKnoraBaseVersion(versionNumber = 52, name = "DEV-6662", plugin = new MigrateOnlyBuiltInGraphs()),
      PluginForKnoraBaseVersion(versionNumber = 53, name = "DEV-6758", plugin = new MigrateOnlyBuiltInGraphs()),
      PluginForKnoraBaseVersion(versionNumber = 54, name = "DEV-6853", plugin = new MigrateOnlyBuiltInGraphs()),
      PluginForKnoraBaseVersion(versionNumber = 55, name = "DEV-7039", plugin = new MigrateRemoveProjectStatus()),
      PluginForKnoraBaseVersion(versionNumber = 56, name = "PR4329", plugin = new UpgradePluginPR4329()),
      PluginForKnoraBaseVersion(versionNumber = 57, name = "DEV-7285", plugin = new MigrateOnlyBuiltInGraphs()),
      PluginForKnoraBaseVersion(versionNumber = 58, name = "DEV-7325", plugin = new MigrateOnlyBuiltInGraphs()),
      PluginForKnoraBaseVersion(versionNumber = 59, name = "PR4375", plugin = new MigrateOnlyBuiltInGraphs()),
    ).ensuring(p => p.map(_.name).distinct.size == p.size, "plan entry names must be unique")

  /**
   * The built-in named graphs that are always updated when there is a new version of knora-base.
   */
  val builtInNamedGraphs: Set[RdfDataObject] = Set(
    RdfDataObject(
      path = "knora-ontologies/knora-admin.ttl",
      name = "http://www.knora.org/ontology/knora-admin",
    ),
    RdfDataObject(
      path = "knora-ontologies/knora-base.ttl",
      name = "http://www.knora.org/ontology/knora-base",
    ),
    RdfDataObject(
      path = "knora-ontologies/salsah-gui.ttl",
      name = "http://www.knora.org/ontology/salsah-gui",
    ),
    RdfDataObject(
      path = "knora-ontologies/standoff-onto.ttl",
      name = "http://www.knora.org/ontology/standoff",
    ),
    RdfDataObject(
      path = "knora-ontologies/standoff-data.ttl",
      name = "http://www.knora.org/data/standoff",
    ),
  )
}
