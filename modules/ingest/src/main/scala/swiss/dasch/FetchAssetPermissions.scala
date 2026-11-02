/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch

import swiss.dasch.domain.AssetInfo
import zio.*

trait FetchAssetPermissions {

  /** Whether dsp-api grants this caller the asset's Original. The Derivative channel is Sipi's and is ignored here. */
  def isOriginalGranted(
    jwt: Option[String],
    assetInfo: AssetInfo,
  ): Task[Boolean]
}
