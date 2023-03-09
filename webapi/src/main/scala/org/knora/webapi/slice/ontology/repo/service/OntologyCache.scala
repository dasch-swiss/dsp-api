/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology.repo.service
import zio.Task
import zio.URLayer
import zio.ZLayer
import zio.macros.accessible

import org.knora.webapi.responders.v2.ontology.Cache

@accessible
trait OntologyCache {
  def getCacheData: Task[Cache.OntologyCacheData]
}

final case class OntologyCacheLive(cache: Cache) extends OntologyCache {
  def getCacheData: Task[Cache.OntologyCacheData] = cache.getCacheData
}

object OntologyCache {
  val layer: URLayer[Cache, OntologyCache] = ZLayer.fromFunction(OntologyCacheLive(_))
}
