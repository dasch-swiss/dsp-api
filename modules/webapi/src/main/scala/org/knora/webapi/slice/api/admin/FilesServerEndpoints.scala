/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.admin

import sttp.tapir.ztapir.*
import zio.*

import org.knora.webapi.responders.admin.AssetPermissionsCache

final class FilesServerEndpoints(
  filesEndpoints: FilesEndpoints,
  assetPermissionsCache: AssetPermissionsCache,
) {
  val serverEndpoints: List[ZServerEndpoint[Any, Any]] = List(
    filesEndpoints.getAdminFilesShortcodeFileIri.serverLogic(user =>
      (shortcode, filename) =>
        assetPermissionsCache.getAssetAccess(user)(shortcode, filename).map(AssetAccessResponse.from),
    ),
  )
}

object FilesServerEndpoints {
  val layer = ZLayer.derive[FilesServerEndpoints]
}
