/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.admin

import sttp.tapir.ztapir.*
import zio.*

import org.knora.webapi.responders.admin.AssetPermissionsCache
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.admin.domain.model.User

final class FilesServerEndpoints(
  filesEndpoints: FilesEndpoints,
  assetPermissionsCache: AssetPermissionsCache,
) {
  val serverEndpoints: List[ZServerEndpoint[Any, Any]] = List(
    filesEndpoints.getAdminFilesShortcodeFileIri.serverLogic(
      assetPermissionsCache.getPermissionCodeAndProjectRestrictedViewSettings,
    ),
  )
}

object FilesServerEndpoints {
  val layer = ZLayer.derive[FilesServerEndpoints]
}
