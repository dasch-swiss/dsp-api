/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api

import sttp.capabilities.zio.ZioStreams
import sttp.tapir.ztapir.ZServerEndpoint
import zio.ZLayer

import org.knora.webapi.responders.admin.AssetPermissionsResponder
import org.knora.webapi.slice.admin.api.model.PermissionCodeAndProjectRestrictedViewSettings
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.common.domain.SparqlEncodedString

final case class FilesServerEndpoints(
  private val filesEndpoints: FilesEndpoints,
  private val assetPermissionsResponder: AssetPermissionsResponder,
) {
  val serverEndpoints: List[ZServerEndpoint[Any, ZioStreams]] = List(
    filesEndpoints.getAdminFilesShortcodeFileIri
      .serverLogic(assetPermissionsResponder.getPermissionCodeAndProjectRestrictedViewSettings),
  )
}
object FilesServerEndpoints {
  val layer = ZLayer.derive[FilesServerEndpoints]
}
