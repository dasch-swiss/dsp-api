/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology.repo.service
import zio.Task
import zio.ULayer
import zio.ZIO
import zio.ZLayer
import zio.macros.accessible

import org.knora.webapi.responders.v2.ontology.Cache

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
