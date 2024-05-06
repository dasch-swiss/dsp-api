/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.triplestore.upgrade.plugins

import org.eclipse.rdf4j.model.vocabulary.RDF
import org.eclipse.rdf4j.sparqlbuilder.core.SparqlBuilder.`var` as variable
import org.eclipse.rdf4j.sparqlbuilder.core.query.*
import org.eclipse.rdf4j.sparqlbuilder.core.query.ModifyQuery
import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf

import org.knora.webapi.messages.OntologyConstants.KnoraAdmin.KnoraAdminPrefixExpansion
import org.knora.webapi.slice.admin.AdminConstants
import org.knora.webapi.slice.admin.repo.rdf.Vocabulary
import org.knora.webapi.store.triplestore.upgrade.GraphsForMigration
import org.knora.webapi.store.triplestore.upgrade.MigrateSpecificGraphs

/**
 * After removing `knora-admin:Institution` class and its properties from the knora-admin ontology this cleans up the DB.
 * Removes all `knora-admin:Institution` classes from the `knora-admin` named graph.
 */
class UpgradePluginPR3110 extends AbstractSparqlUpdatePlugin {

  override def graphsForMigration: GraphsForMigration =
    MigrateSpecificGraphs.from(AdminConstants.adminDataNamedGraph)

  private val removeAllInstitutions: ModifyQuery = {
    val (s, p, o) = (variable("s"), variable("p"), variable("o"))
    Queries
      .MODIFY()
      .prefix(Vocabulary.KnoraAdmin.NS, RDF.NS)
      .delete(s.has(p, o))
      .from(Vocabulary.NamedGraphs.dataAdmin)
      .where(
        s.isA(Rdf.iri(KnoraAdminPrefixExpansion, "Institution"))
          .andHas(p, o)
          .from(Vocabulary.NamedGraphs.dataAdmin),
      )
  }

  private val removeAllBelongsToInstitutionTriples: ModifyQuery = {
    val (s, o)               = (variable("s"), variable("o"))
    val belongsToInstitution = Rdf.iri(KnoraAdminPrefixExpansion, "belongsToInstitution")
    Queries
      .MODIFY()
      .prefix(Vocabulary.KnoraAdmin.NS, RDF.NS)
      .delete(s.has(belongsToInstitution, o))
      .from(Vocabulary.NamedGraphs.dataAdmin)
      .where(s.has(belongsToInstitution, o).from(Vocabulary.NamedGraphs.dataAdmin))
  }

  override def getQueries: List[ModifyQuery] = List(
    removeAllInstitutions,
    removeAllBelongsToInstitutionTriples,
  )
}
