package org.knora.webapi.store.triplestore.upgrade.plugins

import org.apache.commons.io.IOUtils
import org.apache.jena.query.Dataset
import org.apache.jena.query.DatasetFactory
import org.apache.jena.query.QueryExecutionFactory
import org.apache.jena.query.QueryFactory
import org.apache.jena.riot.Lang
import org.apache.jena.riot.RDFDataMgr

import org.knora.webapi.messages.util.rdf.JenaModel
import org.knora.webapi.messages.util.rdf.JenaModelFactory

trait UpgradePluginTestOps {

  def createDataset: Dataset = DatasetFactory.create()

  def createJenaModelFromTriG(trig: String): JenaModel = {
    val ds = createDataset
    RDFDataMgr.read(ds, IOUtils.toInputStream(trig, "UTF-8"), Lang.TRIG)
    JenaModelFactory.from(ds)
  }

  def queryAsk(sparql: String, model: JenaModel): Boolean = {
    val qExec = QueryExecutionFactory.create(QueryFactory.create(sparql), model.getDataset)
    qExec.execAsk()
  }

  def printModel(model: JenaModel): Unit = RDFDataMgr.write(System.out, model.getDataset, Lang.TRIG)
}
