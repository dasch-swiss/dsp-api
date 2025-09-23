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

final case class FilesServerEndpoints(
  private val filesEndpoints: FilesEndpoints,
  private val assetPermissionsResponder: AssetPermissionsResponder,
) {
  val serverEndpoints = Seq(
    filesEndpoints.getAdminFilesShortcodeFileIri.serverLogic(
      assetPermissionsResponder.getPermissionCodeAndProjectRestrictedViewSettings,
    ),
  )
}

object FilesServerEndpoints {
  val layer = ZLayer.derive[FilesServerEndpoints]
}
