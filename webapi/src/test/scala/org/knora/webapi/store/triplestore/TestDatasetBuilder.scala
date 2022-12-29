package org.knora.webapi.store.triplestore

import zio.ZLayer
import java.io.StringReader
import zio.Ref
import org.apache.jena.query.Dataset
import org.apache.jena.query.ReadWrite
import org.apache.jena.query.DatasetFactory
import zio.ULayer

object TestDatasetBuilder {

  def datasetFromTurtle(turtle: String): Dataset = {
    val dataset = DatasetFactory.createTxnMem()
    dataset.begin(ReadWrite.WRITE)
    try {
      val model = dataset.getDefaultModel
      model.read(new StringReader(turtle), null, "TURTLE")
      dataset.commit()
    } finally {
      dataset.end()
    }
    dataset
  }

  def asLayer(ds: Dataset): ULayer[Ref[Dataset]] = ZLayer.fromZIO(Ref.make[Dataset](ds))

  def datasetLayerFromTurtle(turtle: String): ULayer[Ref[Dataset]] = asLayer(datasetFromTurtle(turtle))
}
