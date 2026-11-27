/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.service

import zio.*

import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.repo.AssetReferencesRepo

final case class UnreferencedAsset(
  assetId: String,
  internalFilename: String,
  originalFilename: String,
  referencedOnlyByDeletedResources: Boolean,
)

/**
 * Lists a project's assets that no live resource references. An asset is referenced when a file value of the
 * project's data graph names an internal filename whose part before the first `.` is the asset ID, so every file
 * type matches, whatever the derivative's extension. Reports only: it never deletes an asset.
 */
final class UnreferencedAssetsService(ingest: DspIngestClient, references: AssetReferencesRepo) {

  def find(project: KnoraProject): Task[Chunk[UnreferencedAsset]] =
    for {
      assets <- ingest.listAssets(project.shortcode)
      refs   <- references.findFileReferences(project)
      live    = refs.groupMapReduce(r => assetIdOf(r.internalFilename))(!_.resourceDeleted)(_ || _)
    } yield assets
      .filterNot(asset => live.getOrElse(asset.assetId, false))
      .map(asset =>
        UnreferencedAsset(asset.assetId, asset.internalFilename, asset.originalFilename, live.contains(asset.assetId)),
      )
      .sortBy(_.assetId)

  private def assetIdOf(internalFilename: String): String = internalFilename.takeWhile(_ != '.')
}

object UnreferencedAssetsService {
  val layer = ZLayer.derive[UnreferencedAssetsService]
}
