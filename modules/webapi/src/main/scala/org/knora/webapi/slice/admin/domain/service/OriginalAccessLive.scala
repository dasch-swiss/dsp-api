/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.service

import zio.*

import org.knora.webapi.messages.util.KnoraSystemInstances.Users.AnonymousUser
import org.knora.webapi.responders.admin.AssetPermissionsCache
import org.knora.webapi.slice.admin.domain.model.InternalFilename
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.admin.domain.model.OriginalAccess as OriginalDecision
import org.knora.webapi.slice.ingest.domain.ProjectShortcode
import org.knora.webapi.slice.ingest.ports.OriginalAccess
import org.knora.webapi.slice.security.Authenticator

/** Decides the ingest original-download route from the same cached decision `GET /admin/files/...` serves. */
final class OriginalAccessLive(authenticator: Authenticator, cache: AssetPermissionsCache) extends OriginalAccess {

  override def isOriginalGranted(
    token: Option[String],
    shortcode: ProjectShortcode,
    derivativeFilename: String,
  ): Task[Boolean] =
    for {
      user     <- token.fold(ZIO.succeed(AnonymousUser))(authenticator.authenticate)
      project  <- ZIO.fromEither(Shortcode.from(shortcode.value)).mapError(IllegalArgumentException(_))
      filename <- ZIO.fromEither(InternalFilename.from(derivativeFilename)).mapError(IllegalArgumentException(_))
      access   <- cache.getAssetAccess(user)(project, filename)
    } yield access.original == OriginalDecision.Grant
}

object OriginalAccessLive {
  val layer: URLayer[Authenticator & AssetPermissionsCache, OriginalAccess] = ZLayer.derive[OriginalAccessLive]
}
