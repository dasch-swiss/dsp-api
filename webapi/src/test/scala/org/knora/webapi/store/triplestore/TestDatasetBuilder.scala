/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.triplestore

import org.apache.jena.query.Dataset
import org.apache.jena.query.DatasetFactory
import org.apache.jena.query.ReadWrite
import org.apache.jena.rdf.model.Model
import zio.Ref
import zio.Task
import zio.TaskLayer
import zio.UIO
import zio.ZIO
import zio.ZLayer
import java.io.StringReader

object TestDatasetBuilder {

  def readToModel(turtle: String)(model: Model): Model = model.read(new StringReader(turtle), null, "TTL")

  def transactionalWrite(change: Model => Model)(ds: Dataset): Task[Dataset] = ZIO.attempt {
    ds.begin(ReadWrite.WRITE)
    try {
      change apply ds.getDefaultModel
      ds.commit()
    } finally {
      ds.end()
    }
    ds
  }

  val createTxnMemDataset: UIO[Dataset] = ZIO.succeed(DatasetFactory.createTxnMem())

  def datasetFromTurtle(turtle: String): Task[Dataset] =
    createTxnMemDataset.flatMap(transactionalWrite(readToModel(turtle)))

  def asLayer(ds: Task[Dataset]): TaskLayer[Ref[Dataset]] = ZLayer.fromZIO(ds.flatMap(Ref.make[Dataset](_)))

  def datasetLayerFromTurtle(turtle: String): TaskLayer[Ref[Dataset]] = asLayer(datasetFromTurtle(turtle))
}
