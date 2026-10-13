/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.fair.ports

import zio.*

import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode

/** `calls` records the asset ids looked up, in order. */
final case class FairAssetsInMemory(sidecars: Map[String, FairAsset], calls: Ref[List[String]]) extends FairAssets {
  override def findSidecar(shortcode: Shortcode, assetId: String): UIO[Option[FairAsset]] =
    calls.update(_ :+ assetId).as(sidecars.get(assetId))
}

object FairAssetsInMemory {
  def make(sidecars: Map[String, FairAsset] = Map.empty): UIO[FairAssetsInMemory] =
    Ref.make(List.empty[String]).map(FairAssetsInMemory(sidecars, _))
}
