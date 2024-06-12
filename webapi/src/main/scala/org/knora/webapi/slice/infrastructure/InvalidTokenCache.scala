/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.infrastructure

import zio.ZIO
import zio.ZLayer

final case class InvalidTokenCache(cache: EhCache[String, String]) {
  def put(token: String): Unit         = cache.put(token, "")
  def contains(token: String): Boolean = cache.get(token).isDefined
}

object InvalidTokenCache {
  val layer = ZLayer.fromZIO(
    ZIO.serviceWithZIO[CacheManager](_.createCache[String, String]("invalidTokenCache").map(InvalidTokenCache.apply)),
  )
}
