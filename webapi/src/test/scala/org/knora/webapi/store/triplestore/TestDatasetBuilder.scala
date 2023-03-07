/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.triplestore

import org.apache.jena.query.Dataset
import org.apache.jena.query.ReadWrite
import org.apache.jena.rdf.model.Model
import zio._

import java.io.StringReader

import org.knora.webapi.store.triplestore.api.TriplestoreServiceInMemory.createEmptyDataset

object TestDatasetBuilder {

  private def readToModel(turtle: String)(model: Model): Model = model.read(new StringReader(turtle), null, "TTL")

  private def transactionalWrite(change: Model => Model)(ds: Dataset): Task[Dataset] = ZIO.attempt {
    ds.begin(ReadWrite.WRITE)
    try {
      change apply ds.getDefaultModel
      ds.commit()
    } finally {
      ds.end()
    }
    ds
  }

  private def datasetFromTurtle(turtle: String): Task[Dataset] =
    createEmptyDataset.flatMap(transactionalWrite(readToModel(turtle)))

  private def asLayer(ds: Task[Dataset]): TaskLayer[Ref[Dataset]] = ZLayer.fromZIO(ds.flatMap(Ref.make[Dataset](_)))

  def datasetLayerFromTurtle(turtle: String): TaskLayer[Ref[Dataset]] = asLayer(datasetFromTurtle(turtle))

  val emptyDataset: ULayer[Ref[Dataset]] = ZLayer.fromZIO(createEmptyDataset.flatMap(Ref.make(_)))
}
