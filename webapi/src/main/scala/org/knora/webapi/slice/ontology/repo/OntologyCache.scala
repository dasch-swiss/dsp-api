package org.knora.webapi.slice.ontology.repo
import zio.Task
import zio.ZIO
import zio.ZLayer

import org.knora.webapi.responders.v2.ontology.Cache

trait OntologyCache {
  def get: Task[Cache.OntologyCacheData]
}
final case class OntologyCacheLive() extends OntologyCache {

  def get: Task[Cache.OntologyCacheData] =
    ZIO.fromFuture(implicit ec => Cache.getCacheData)
}

object OntologyCache {
  val layer = ZLayer.succeed(OntologyCacheLive())
}
