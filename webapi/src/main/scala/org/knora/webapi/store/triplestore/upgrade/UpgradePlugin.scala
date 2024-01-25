/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.triplestore.upgrade

import org.knora.webapi.messages.util.rdf.RdfModel
import org.knora.webapi.slice.resourceinfo.domain.InternalIri

/**
 * A trait for plugins that update a repository.
 */
trait UpgradePlugin {

  /**
   * Transforms a repository.
   *
   * @param model an [[RdfModel]] containing the repository data.
   */
  def transform(model: RdfModel): Unit

  /**
   * Allows a plugin to restrict itself to a particular named graph.
   * Returns the IRI of the named graphs that contain the necessary repository data.
   * If the plugin doesn't need to restrict itself to a particular named graph, it should return [[MigrateAllGraphs]].
   *
   * @return [[GraphsForMigration]] the graphs the plugin requires.
   *         The default is all graphs in order to be backwards compatible with existing Plugins.
   */
  def graphsForMigration: GraphsForMigration = MigrateAllGraphs
}

sealed trait GraphsForMigration {
  def merge(other: GraphsForMigration): GraphsForMigration
}

case object MigrateAllGraphs extends GraphsForMigration {
  def merge(other: GraphsForMigration): GraphsForMigration = MigrateAllGraphs
}

final case class MigrateSpecificGraphs private (graphIris: Set[InternalIri]) extends GraphsForMigration {
  def merge(other: GraphsForMigration): GraphsForMigration = other match {
    case MigrateAllGraphs                => MigrateAllGraphs.merge(this)
    case specific: MigrateSpecificGraphs => merge(specific)
  }

  def merge(other: MigrateSpecificGraphs): MigrateSpecificGraphs =
    MigrateSpecificGraphs(graphIris ++ other.graphIris)
}

object MigrateSpecificGraphs {
  val builtIn: MigrateSpecificGraphs = MigrateSpecificGraphs(
    RepositoryUpdatePlan.builtInNamedGraphs.map(_.iri).map(InternalIri.apply)
  )

  def from(iri: InternalIri): MigrateSpecificGraphs = MigrateSpecificGraphs.from(Seq(iri))

  def from(iris: Iterable[InternalIri]): MigrateSpecificGraphs =
    // We have to add the builtIn graphs to all [[MigrateSpecificGraphs]], because the [[RepositoryUpdater]] requires them for automatic upgrade of builtIn graphs, e.g. ontologies.
    MigrateSpecificGraphs(iris.toSet) merge builtIn
}
