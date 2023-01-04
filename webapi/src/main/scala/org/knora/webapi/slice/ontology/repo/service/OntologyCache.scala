package org.knora.webapi.slice.ontology.repo.service
import org.knora.webapi.responders.v2.ontology.Cache
import zio.Task
import zio.macros.accessible
import zio.ULayer
import zio.ZIO
import zio.ZLayer

@accessible
trait OntologyCache {
  def get: Task[Cache.OntologyCacheData]
}

final case class OntologyCacheLive() extends OntologyCache {
  def get: Task[Cache.OntologyCacheData] = ZIO.fromFuture(implicit ec => Cache.getCacheData)
}

object OntologyCache {
  val layer: ULayer[OntologyCacheLive] = ZLayer.succeed(OntologyCacheLive())
}
