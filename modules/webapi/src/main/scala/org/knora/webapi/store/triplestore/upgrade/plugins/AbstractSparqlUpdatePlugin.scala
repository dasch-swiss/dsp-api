/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.triplestore.upgrade.plugins

import org.apache.jena.query.Dataset
import org.apache.jena.update.UpdateExecutionFactory
import org.apache.jena.update.UpdateFactory

import org.knora.sparqlbuilder.*
import org.knora.webapi.messages.OntologyConstants.KnoraAdmin.KnoraAdminPrefixExpansion
import org.knora.webapi.messages.util.rdf.*
import org.knora.webapi.slice.admin.AdminConstants
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Update
import org.knora.webapi.store.triplestore.upgrade.UpgradePlugin

abstract class AbstractSparqlUpdatePlugin extends UpgradePlugin {

  def getQueries: List[Update]

  override def transform(model: RdfModel): Unit = {
    val dataset = model.asInstanceOf[JenaModel].getDataset
    getQueries.foreach(execute(dataset, _))
  }

  private def execute(dataset: Dataset, query: Update) = {
    val update = UpdateFactory.create(query.sparql)
    val qExec  = UpdateExecutionFactory.create(update, dataset)
    qExec.execute()
  }
}

object AbstractSparqlUpdatePlugin {

  /** The `PREFIX knora-admin: <...>` declaration for the top of an update template. */
  private[plugins] val knoraAdminPrefix: Fragment =
    sparql"PREFIX knora-admin: ${Iri.unsafeFrom(KnoraAdminPrefixExpansion)}"

  /** The named graph holding the admin data. */
  private[plugins] val adminGraph: Iri = Iri.unsafeFrom(AdminConstants.adminDataNamedGraph.value)

  /** The named graph holding the permissions data. */
  private[plugins] val permissionsGraph: Iri = Iri.unsafeFrom(AdminConstants.permissionsDataNamedGraph.value)
}
