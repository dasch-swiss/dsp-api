/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology.repo.service
import zio.Ref
import zio.Task
import zio.UIO
import zio.ZIO
import zio.ZLayer

import org.knora.webapi.slice.ontology.repo.model.OntologyCacheData

case class OntologyCacheFake(ref: Ref[OntologyCacheData]) extends OntologyCache {

  override def getCacheData: UIO[OntologyCacheData] = ref.get

  def set(data: OntologyCacheData): UIO[Unit] = ref.set(data)

  /**
   * Loads and caches all ontology information.
   */
  override def refreshCache(): Task[OntologyCacheData] =
    throw new UnsupportedOperationException("Not possible in tests. Provide the respective test data as Ref.")
}

object OntologyCacheFake {

  def set(data: OntologyCacheData): ZIO[OntologyCacheFake, Nothing, Unit] =
    ZIO.serviceWithZIO[OntologyCacheFake](_.set(data))

  def withCache(data: OntologyCacheData): ZLayer[Any, Nothing, OntologyCacheFake] = ZLayer.fromZIO {
    for {
      ref <- Ref.make[OntologyCacheData](data)
    } yield OntologyCacheFake(ref)
  }

  val emptyData: OntologyCacheData =
    OntologyCacheData(Map.empty, Map.empty, Map.empty, Map.empty, Map.empty, Map.empty, Map.empty, Map.empty, Set.empty)

  val emptyCache: ZLayer[Any, Nothing, OntologyCacheFake] = withCache(emptyData)
}
