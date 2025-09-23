/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api

import zio.*

import sttp.tapir.ztapir.*

import org.knora.webapi.responders.admin.AssetPermissionsResponder
import org.knora.webapi.slice.admin.api.model.PermissionCodeAndProjectRestrictedViewSettings
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.common.domain.SparqlEncodedString

final case class FilesEndpointsHandler(
  filesEndpoints: FilesEndpoints,
  assetPermissionsResponder: AssetPermissionsResponder,
) {

  val allHandlers: List[ZServerEndpoint[Any, Any]] =
    List(
      filesEndpoints.getAdminFilesShortcodeFileIri.serverLogic(
        assetPermissionsResponder.getPermissionCodeAndProjectRestrictedViewSettings,
      ),
    )
}

object FilesEndpointsHandler {
  val layer = ZLayer.derive[FilesEndpointsHandler]
}
