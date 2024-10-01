/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch

import swiss.dasch.domain.AssetInfo
import zio.*

class FetchAssetPermissionsMock(permissionCode: Int) extends FetchAssetPermissions {
  def getPermissionCode(
    jwt: String,
    assetInfo: AssetInfo,
  ): Task[Int] =
    ZIO.succeed(permissionCode)
}

object FetchAssetPermissionsMock {
  def layer(permissionCode: Int): ULayer[FetchAssetPermissions] =
    ZLayer.succeed(new FetchAssetPermissionsMock(permissionCode))
}
