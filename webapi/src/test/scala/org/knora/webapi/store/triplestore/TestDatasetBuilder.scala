package org.knora.webapi.store.triplestore

import zio.ZLayer
import java.io.StringReader
import zio.Ref
import org.apache.jena.query.Dataset
import org.apache.jena.query.ReadWrite
import org.apache.jena.query.DatasetFactory

object TestDatasetBuilder {

  def dataSetFromTurtle(turtle: String): Dataset = {
    val dataset = DatasetFactory.createTxnMem()
    dataset.begin(ReadWrite.WRITE)
    try {
      val model = dataset.getDefaultModel
      model.read(new StringReader(turtle), null, "TTL")
    } catch {
      case e: Exception => println(e)
    } finally {
      dataset.commit()
      dataset.end()
    }
    dataset
  }

  def asLayer(ds: Dataset): ZLayer[Any, Nothing, Ref[Dataset]] = ZLayer.fromZIO(Ref.make[Dataset](ds))
}
