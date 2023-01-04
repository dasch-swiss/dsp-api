package org.knora.webapi.slice.ontology.repo
import zio.Ref
import zio.Task
import zio.UIO
import zio.ZIO
import zio.ZLayer

import org.knora.webapi.responders.v2.ontology.Cache
import org.knora.webapi.responders.v2.ontology.Cache.OntologyCacheData

case class OntologyCacheFake(ref: Ref[OntologyCacheData]) extends OntologyCache {
  override def get: Task[Cache.OntologyCacheData] = ref.get
  def set(data: OntologyCacheData): UIO[Unit]     = ref.set(data)
}

object OntologyCacheFake {

  def set(data: OntologyCacheData): ZIO[OntologyCacheFake, Nothing, Unit] =
    ZIO.service[OntologyCacheFake].flatMap(_.set(data))

  def withCache(data: OntologyCacheData): ZLayer[Any, Nothing, OntologyCacheFake] = ZLayer.fromZIO {
    for {
      ref <- Ref.make[OntologyCacheData](data)
    } yield OntologyCacheFake(ref)
  }

  val emptyData: OntologyCacheData =
    OntologyCacheData(Map.empty, Map.empty, Map.empty, Map.empty, Map.empty, Map.empty, Map.empty, Map.empty, Set.empty)

  val emptyCache: ZLayer[Any, Nothing, OntologyCacheFake] = withCache(emptyData)

}
