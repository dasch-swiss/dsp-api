/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.test

import eu.timepit.refined.types.string.NonEmptyString
import swiss.dasch.domain.{AssetId, AssetRef, ProjectShortcode, Sha256Hash}
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
  object AssetRefs {
    val existingAssetRef: AssetRef = AssetRef(AssetIds.existingAsset, existingProject)
  }
  extension (s: String) {
    def toProjectShortcode: ProjectShortcode = ProjectShortcode.unsafeFrom(s)
    def toAssetId: AssetId                   = AssetId.unsafeFrom(s)
    def toSha256Hash: Sha256Hash             = Sha256Hash.unsafeFrom(s)
    def toNonEmptyString: NonEmptyString     = NonEmptyString.unsafeFrom(s)
  }
}
