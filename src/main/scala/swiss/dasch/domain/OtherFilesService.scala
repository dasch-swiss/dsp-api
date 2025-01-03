/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import swiss.dasch.domain.AugmentedPath.OtherDerivativeFile
import zio.{UIO, ZIO, ZLayer}

final case class OtherFilesService(mimeTypeGuesser: MimeTypeGuesser) {
  def extractMetadata(original: Original, derivative: OtherDerivativeFile): UIO[OtherMetadata] = for {
    _               <- ZIO.when(original.assetId != derivative.assetId)(ZIO.die(new Exception("Asset IDs do not match")))
    originalMimeType = mimeTypeGuesser.guess(original.originalFilename)
    internalMimeType = mimeTypeGuesser.guess(derivative.path)
    _               <- ZIO.logInfo(s"Extracting metadata for ${derivative.assetId}")
  } yield (OtherMetadata(internalMimeType, originalMimeType))
}

object OtherFilesService {
  val layer = ZLayer.derive[OtherFilesService]
}
