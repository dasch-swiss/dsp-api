/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch

import swiss.dasch.domain.AssetInfo
import zio.*

class FetchAssetPermissionsMock(granted: Boolean) extends FetchAssetPermissions {
  def isOriginalGranted(
    jwt: Option[String],
    assetInfo: AssetInfo,
  ): Task[Boolean] =
    ZIO.succeed(granted)
}

object FetchAssetPermissionsMock {
  def layer(granted: Boolean): ULayer[FetchAssetPermissions] =
    ZLayer.succeed(new FetchAssetPermissionsMock(granted))
}
