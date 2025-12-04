/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.triplestore.upgrade.plugins

import org.apache.jena.query.Dataset
import org.apache.jena.update.UpdateExecutionFactory
import org.apache.jena.update.UpdateFactory
import org.eclipse.rdf4j.sparqlbuilder.core.query.ModifyQuery

import org.knora.webapi.messages.util.rdf.*
import org.knora.webapi.slice.common.QueryBuilderHelper
import org.knora.webapi.store.triplestore.upgrade.UpgradePlugin

abstract class AbstractSparqlUpdatePlugin extends UpgradePlugin with QueryBuilderHelper {

  def getQueries: List[ModifyQuery]

  override def transform(model: RdfModel): Unit = {
    val dataset = model.asInstanceOf[JenaModel].getDataset
    getQueries.foreach(execute(dataset, _))
  }

  private def execute(dataset: Dataset, query: ModifyQuery) = {
    val queryString = query.getQueryString
    val update      = UpdateFactory.create(queryString)
    val qExec       = UpdateExecutionFactory.create(update, dataset)
    qExec.execute()
  }
}
