/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.test

import eu.timepit.refined.types.string.NonEmptyString
import swiss.dasch.domain.{ Asset, AssetId, SimpleAsset, ProjectShortcode, Sha256Hash }
import swiss.dasch.test.SpecConstants.Projects.existingProject

object SpecConstants {
  object Projects {
    val nonExistentProject: ProjectShortcode = "0042".toProjectShortcode
    val existingProject: ProjectShortcode    = "0001".toProjectShortcode
    val emptyProject: ProjectShortcode       = "0002".toProjectShortcode
  }
  object AssetIds {
    val existingAsset: AssetId = "FGiLaT4zzuV-CqwbEDFAFeS".toAssetId
  }
  object Assets   {
    val existingAsset: Asset = SimpleAsset(AssetIds.existingAsset, existingProject)
  }
  extension (s: String) {
    def toProjectShortcode: ProjectShortcode = ProjectShortcode
      .make(s)
      .fold(err => throw new IllegalArgumentException(err), identity)
    def toAssetId: AssetId                   = AssetId
      .make(s)
      .fold(err => throw new IllegalArgumentException(err), identity)
    def toSha256Hash: Sha256Hash             = Sha256Hash
      .make(s)
      .fold(err => throw new IllegalArgumentException(err), identity)
    def toNonEmptyString: NonEmptyString     =
      NonEmptyString.unsafeFrom(s)
  }
}
