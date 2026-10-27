/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.service

import swiss.dasch.FetchAssetPermissions
import swiss.dasch.domain.AssetInfo
import zio.*

import org.knora.webapi.messages.util.KnoraSystemInstances.Users.AnonymousUser
import org.knora.webapi.responders.admin.AssetPermissionsCache
import org.knora.webapi.slice.admin.domain.model.InternalFilename
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.admin.domain.model.OriginalAccess
import org.knora.webapi.slice.security.Authenticator

/**
 * Answers ingest's original-download check from the decision `GET /admin/files/...` serves. An absent token is the
 * anonymous user; a token that does not authenticate fails.
 */
final class IngestFetchAssetPermissions(authenticator: Authenticator, cache: AssetPermissionsCache)
    extends FetchAssetPermissions {

  override def isOriginalGranted(jwt: Option[String], assetInfo: AssetInfo): Task[Boolean] =
    for {
      user      <- jwt.fold(ZIO.succeed(AnonymousUser))(authenticator.authenticate)
      shortcode <- ZIO.fromEither(Shortcode.from(assetInfo.assetRef.belongsToProject.value)).mapError(Exception(_))
      filename  <- ZIO.fromEither(InternalFilename.from(assetInfo.derivative.filename.value)).mapError(Exception(_))
      access    <- cache.getAssetAccess(user)(shortcode, filename)
    } yield access.original == OriginalAccess.Grant
}

object IngestFetchAssetPermissions {
  val layer: URLayer[Authenticator & AssetPermissionsCache, FetchAssetPermissions] =
    ZLayer.derive[IngestFetchAssetPermissions]
}
