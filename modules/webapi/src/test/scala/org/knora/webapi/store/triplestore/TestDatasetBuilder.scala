/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.triplestore

import org.apache.commons.io.IOUtils
import org.apache.jena.query.Dataset
import org.apache.jena.query.ReadWrite
import org.apache.jena.rdf.model.Model
import org.apache.jena.riot.Lang
import org.apache.jena.riot.RDFDataMgr
import zio.*

import java.io.StringReader

import org.knora.webapi.store.triplestore.api.TriplestoreServiceInMemory.createEmptyDataset

object TestDatasetBuilder {

  private def readToModel(turtle: String)(model: Model): Model = model.read(new StringReader(turtle), null, "TTL")

  private def transactionalWrite(change: Model => Model, graph: String)(ds: Dataset): Task[Dataset] = ZIO.attempt {
    ds.begin(ReadWrite.WRITE)
    try {
      change apply ds.getNamedModel(graph)
      ds.commit()
    } finally {
      ds.end()
    }
    ds
  }

  private def datasetFromTurtle(turtle: String, graph: String = "http://www.example.org/graph"): Task[Dataset] =
    createEmptyDataset.flatMap(transactionalWrite(readToModel(turtle), graph))

  def datasetFromTriG(trig: String): Task[Dataset] =
    for {
      ds         <- createEmptyDataset
      is          = IOUtils.toInputStream(trig, "UTF-8")
      r: Runnable = () => { RDFDataMgr.read(ds, is, Lang.TRIG) }
      _          <- ZIO.attempt(ds.executeWrite(r))
    } yield ds

  private def asLayer(ds: Task[Dataset]): TaskLayer[Ref[Dataset]] = ZLayer.fromZIO(ds.flatMap(Ref.make[Dataset](_)))

  def datasetLayerFromTurtle(turtle: String): TaskLayer[Ref[Dataset]] = asLayer(datasetFromTurtle(turtle))

  /**
   * As [[datasetLayerFromTurtle]], but the fixture chooses its own named graphs.
   *
   * Needed by queries that scope with `GRAPH <…>` rather than filtering on `attachedToProject`: those only
   * see triples in the graph they name, whereas `datasetFromTurtle` puts everything in one fixed graph
   * (`http://www.example.org/graph`). A graph-scoped query against a Turtle fixture matches nothing and the
   * test passes vacuously, so such fixtures must place their triples in the graph the query will look in.
   */
  def datasetLayerFromTriG(trig: String): TaskLayer[Ref[Dataset]] = asLayer(datasetFromTriG(trig))

  val emptyDataset: ULayer[Ref[Dataset]] = ZLayer.fromZIO(createEmptyDataset.flatMap(Ref.make(_)))
}
