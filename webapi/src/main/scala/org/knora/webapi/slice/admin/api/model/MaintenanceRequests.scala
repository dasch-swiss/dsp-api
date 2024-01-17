/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api.model

import eu.timepit.refined.api.Refined
import eu.timepit.refined.api.RefinedTypeOps
import eu.timepit.refined.numeric.Positive
import eu.timepit.refined.refineV
import eu.timepit.refined.string.MatchesRegex
import zio.Chunk
import zio.json.DeriveJsonCodec
import zio.json.JsonCodec
import zio.json.interop.refined.*

import org.knora.webapi.slice.admin.api.Codecs.ZioJsonCodec.*
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode

object MaintenanceRequests {

  type AssetId = String Refined MatchesRegex["^[a-zA-Z0-9-_]{4,}$"]
  object AssetId extends RefinedTypeOps[AssetId, String]

  final case class Dimensions(width: Int Refined Positive, height: Int Refined Positive)

  object Dimensions {
    def from(width: Int, height: Int): Either[String, Dimensions] = for {
      widthRefined  <- refineV[Positive](width)
      heightRefined <- refineV[Positive](height)
    } yield Dimensions(widthRefined, heightRefined)

    implicit val codec: JsonCodec[Dimensions] = DeriveJsonCodec.gen[Dimensions]
  }

  case class ReportAsset(id: AssetId, dimensions: Dimensions)

  object ReportAsset {
    implicit val codec: JsonCodec[ReportAsset] = DeriveJsonCodec.gen[ReportAsset]
  }

  case class ProjectWithBakFiles(id: Shortcode, assetIds: Chunk[ReportAsset])

  object ProjectWithBakFiles {
    implicit val codec: JsonCodec[ProjectWithBakFiles] = DeriveJsonCodec.gen[ProjectWithBakFiles]
  }

  case class ProjectsWithBakfilesReport(projects: Chunk[ProjectWithBakFiles])

  object ProjectsWithBakfilesReport {
    implicit val codec: JsonCodec[ProjectsWithBakfilesReport] = DeriveJsonCodec.gen[ProjectsWithBakfilesReport]
  }
}
