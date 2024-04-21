package org.knora.webapi.routing

import zio.ZIO
import zio.ZLayer

import org.knora.webapi.slice.infrastructure.CacheManager
import org.knora.webapi.slice.infrastructure.EhCache

final case class InvalidTokenCache(cache: EhCache[String, Boolean]) {
  def put(token: String): Unit         = cache.put(token, true)
  def contains(token: String): Boolean = cache.get(token).isDefined
}

object InvalidTokenCache {
  val layer = ZLayer.fromZIO(
    ZIO.serviceWithZIO[CacheManager](
      _.createCache[String, Boolean]("authenticationInvalidationCache").map(InvalidTokenCache.apply),
    ),
  )
}
