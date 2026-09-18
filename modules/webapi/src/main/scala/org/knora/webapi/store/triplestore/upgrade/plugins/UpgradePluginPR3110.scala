/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.triplestore.upgrade.plugins

import org.knora.sparqlbuilder.*
import org.knora.webapi.slice.admin.AdminConstants
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Update
import org.knora.webapi.store.triplestore.upgrade.GraphsForMigration
import org.knora.webapi.store.triplestore.upgrade.MigrateSpecificGraphs

/**
 * After removing `knora-admin:Institution` class and its properties from the knora-admin ontology this cleans up the DB.
 * Removes all `knora-admin:Institution` classes from the `knora-admin` named graph.
 */
class UpgradePluginPR3110 extends AbstractSparqlUpdatePlugin {

  override def graphsForMigration: GraphsForMigration =
    MigrateSpecificGraphs.from(AdminConstants.adminDataNamedGraph)

  private val adminGraph: Iri = Iri.unsafeFrom(AdminConstants.adminDataNamedGraph.value)

  private[plugins] val removeAllInstitutions: Update = Update(
    sparql"""|PREFIX knora-admin: <http://www.knora.org/ontology/knora-admin#>
             |DELETE {
             |  GRAPH $adminGraph { ?s ?p ?o . }
             |}
             |WHERE {
             |  GRAPH $adminGraph {
             |    ?s a knora-admin:Institution ;
             |       ?p ?o .
             |  }
             |}""".render,
  )

  private[plugins] val removeAllBelongsToInstitutionTriples: Update = Update(
    sparql"""|PREFIX knora-admin: <http://www.knora.org/ontology/knora-admin#>
             |DELETE {
             |  GRAPH $adminGraph { ?s knora-admin:belongsToInstitution ?o . }
             |}
             |WHERE {
             |  GRAPH $adminGraph { ?s knora-admin:belongsToInstitution ?o . }
             |}""".render,
  )

  override def getQueries: List[Update] = List(
    removeAllInstitutions,
    removeAllBelongsToInstitutionTriples,
  )
}
