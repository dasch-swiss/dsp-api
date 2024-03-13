/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.triplestore.upgrade.plugins

import org.eclipse.rdf4j.model.vocabulary.RDF
import org.eclipse.rdf4j.sparqlbuilder.core.SparqlBuilder.`var` as variable
import org.eclipse.rdf4j.sparqlbuilder.core.query.ModifyQuery
import org.eclipse.rdf4j.sparqlbuilder.core.query.*
import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf

import org.knora.webapi.messages.OntologyConstants.KnoraAdmin.KnoraAdminPrefixExpansion
import org.knora.webapi.slice.admin.AdminConstants
import org.knora.webapi.slice.admin.repo.rdf.Vocabulary
import org.knora.webapi.store.triplestore.upgrade.GraphsForMigration
import org.knora.webapi.store.triplestore.upgrade.MigrateSpecificGraphs

/**
 * Transforms a repository for Knora PR 2094.
 * Transforms incorrect value of valueHasUri from node to string type adding also
 * missing datatype ^^<http://www.w3.org/2001/XMLSchema#anyURI>
 */
class UpgradePluginPR3110() extends AbstractSparqlUpdatePlugin {

  override def graphsForMigration: GraphsForMigration =
    MigrateSpecificGraphs.from(AdminConstants.adminDataNamedGraph)

  override def getQuery: ModifyQuery = {
    var (s, p, o) = (variable("s"), variable("p"), variable("o"))
    Queries
      .MODIFY()
      .prefix(Vocabulary.KnoraAdmin.NS, RDF.NS)
      .delete(s.has(p, o))
      .where(
        s.isA(Rdf.iri(KnoraAdminPrefixExpansion, "Institution"))
          .andHas(p, o)
          .from(Vocabulary.NamedGraphs.knoraAdminIri),
      )
  }
}