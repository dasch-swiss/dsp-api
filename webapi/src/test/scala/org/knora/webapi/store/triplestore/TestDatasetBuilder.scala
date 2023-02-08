/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.triplestore

import org.apache.jena.query.Dataset
import org.apache.jena.query.ReadWrite
import org.apache.jena.rdf.model.Model
import org.apache.jena.tdb2.TDB2Factory
import zio.Ref
import zio.Task
import zio.TaskLayer
import zio.UIO
import zio.ULayer
import zio.ZIO
import zio.ZLayer

import java.io.StringReader

/*
 * Currently does not (yet) support Lucene indexing.
 * TODO: https://jena.apache.org/documentation/query/text-query.html#configuration-by-code
 */
object TestDatasetBuilder {
  val createEmptyDataset: UIO[Dataset] = ZIO.succeed(TDB2Factory.createDataset())

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

  val emptyDataSet: ULayer[Ref[Dataset]] = ZLayer.fromZIO(createEmptyDataset.flatMap(Ref.make(_)))
}
