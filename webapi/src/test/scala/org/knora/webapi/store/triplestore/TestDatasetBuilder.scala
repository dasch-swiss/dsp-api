package org.knora.webapi.store.triplestore

import zio.ZLayer
import java.io.StringReader
import zio.Ref
import org.apache.jena.query.Dataset
import org.apache.jena.query.DatasetFactory
import org.apache.jena.query.ReadWrite
import org.apache.jena.rdf.model.Model
import zio.Scope
import zio.Task
import zio.TaskLayer
import zio.URIO
import zio.ZIO

object TestDatasetBuilder {

  val createTxnMemDataset: URIO[Any with Scope, Dataset] = {
    def acquire              = ZIO.succeed(DatasetFactory.createTxnMem())
    def release(ds: Dataset) = ZIO.succeed(ds.end())
    ZIO.acquireRelease(acquire)(release)
  }

  def transactionalWrite(change: Model => Unit)(ds: Dataset): Task[Dataset] =
    ZIO.succeed {
      ds.begin(ReadWrite.WRITE)
      try {
        change apply ds.getDefaultModel
        ds.commit()
      } finally {
        ds.end()
      }
      ds
    }

  def datasetFromTurtle(turtle: String): Task[Dataset] =
    ZIO.scoped(createTxnMemDataset.flatMap(transactionalWrite { model =>
      model.read(new StringReader(turtle), null, "TTL")
    }))

  def asLayer(uioDs: Task[Dataset]): TaskLayer[Ref[Dataset]] = ZLayer.fromZIO(uioDs.flatMap(Ref.make[Dataset](_)))

  def datasetLayerFromTurtle(turtle: String): TaskLayer[Ref[Dataset]] = asLayer(datasetFromTurtle(turtle))
}
