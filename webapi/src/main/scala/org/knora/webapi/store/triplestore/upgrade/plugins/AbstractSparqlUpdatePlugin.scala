package org.knora.webapi.store.triplestore.upgrade.plugins

import org.apache.jena.update.UpdateExecutionFactory
import org.apache.jena.update.UpdateFactory
import org.eclipse.rdf4j.sparqlbuilder.core.query.ModifyQuery
import org.knora.webapi.messages.util.rdf.JenaModel
import org.knora.webapi.messages.util.rdf.RdfModel
import org.knora.webapi.store.triplestore.upgrade.UpgradePlugin

abstract class AbstractSparqlUpdatePlugin extends UpgradePlugin {

  def getQuery: ModifyQuery

  override def transform(model: RdfModel): Unit = {
    val dataset = model.asInstanceOf[JenaModel].getDataset
    val update = UpdateFactory.create(getQuery.getQueryString)
    val qExec  = UpdateExecutionFactory.create(update, dataset)
    qExec.execute()
  }
}
