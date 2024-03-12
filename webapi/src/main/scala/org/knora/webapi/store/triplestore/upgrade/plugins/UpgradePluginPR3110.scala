/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.triplestore.upgrade.plugins

import org.apache.jena.query.QueryExecutionFactory
import org.eclipse.rdf4j.sparqlbuilder.core.SparqlBuilder.`var` as variable
import org.eclipse.rdf4j.sparqlbuilder.core.query.*
import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf

import org.knora.webapi.messages.util.rdf.JenaModel
import org.knora.webapi.messages.util.rdf.RdfModel
import org.knora.webapi.slice.admin.AdminConstants
import org.knora.webapi.slice.admin.repo.rdf.Vocabulary
import org.knora.webapi.store.triplestore.upgrade.GraphsForMigration
import org.knora.webapi.store.triplestore.upgrade.MigrateSpecificGraphs
import org.knora.webapi.store.triplestore.upgrade.UpgradePlugin

/**
 * Transforms a repository for Knora PR 2094.
 * Transforms incorrect value of valueHasUri from node to string type adding also
 * missing datatype ^^<http://www.w3.org/2001/XMLSchema#anyURI>
 */
class UpgradePluginPR3110() extends UpgradePlugin {

  override def graphsForMigration: GraphsForMigration =
    MigrateSpecificGraphs.from(AdminConstants.adminDataNamedGraph)

  override def transform(model: RdfModel): Unit = {
    val dataset = model.asInstanceOf[JenaModel].getDataset
    dataset.begin()

    try {
      var (s, p, o) = (variable("s"), variable("p"), variable("o"))
      val query = Queries
        .MODIFY()
        .prefix(Vocabulary.KnoraAdmin.NS)
        .`with`(Vocabulary.NamedGraphs.knoraAdminIri)
        .delete(
          s.isA(Rdf.iri("http://www.knora.org/ontology/knora-admin#Institution"))
            .andHas(p, o),
        )

      val qExec = QueryExecutionFactory.create(query.getQueryString, dataset)
      qExec.close()
    } finally {
      dataset.commit()
    }
  }
}
