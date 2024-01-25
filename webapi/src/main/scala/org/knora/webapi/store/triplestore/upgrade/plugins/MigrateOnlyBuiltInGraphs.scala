/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.triplestore.upgrade.plugins

import org.knora.webapi.messages.util.rdf.RdfModel
import org.knora.webapi.store.triplestore.upgrade.{GraphsForMigration, MigrateSpecificGraphs, UpgradePlugin}

/**
 * An update plugin that itself does no migration itself.
 * Used for updates in which only the built-in named graphs have changed.
 * These will automatically be updated by the [[org.knora.webapi.store.triplestore.upgrade.RepositoryUpdater]].
 */
class MigrateOnlyBuiltInGraphs extends UpgradePlugin {
  override def transform(model: RdfModel): Unit       = {}
  override def graphsForMigration: GraphsForMigration = MigrateSpecificGraphs.builtIn
}
