/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.testservices
import sttp.capabilities.zio.ZioStreams
import sttp.client4.ResponseAs
import sttp.client4.StreamBackend
import sttp.client4.asString
import sttp.client4.wrappers.ResolveRelativeUrisBackend
import sttp.model.Uri
import zio.*

import scala.util.Try

import org.knora.webapi.messages.util.rdf.JsonLDDocument
import org.knora.webapi.messages.util.rdf.JsonLDUtil
import org.knora.webapi.sharedtestdata.SharedTestDataADM.*
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.infrastructure.JwtService
import org.knora.webapi.slice.security.ScopeResolver

abstract class BaseApiClient(
  private val be: StreamBackend[Task, ZioStreams],
  private val jwtService: JwtService,
  private val scopeResolver: ScopeResolver,
) {
  protected def baseUrl: Uri

  protected lazy val backend: StreamBackend[Task, ZioStreams] = ResolveRelativeUrisBackend(be, baseUrl)

  protected lazy val asJsonLdDocument: ResponseAs[Either[String, JsonLDDocument]] = asString.map {
    case Right(value) => Try(JsonLDUtil.parseJsonLD(value)).toEither.left.map(_.getMessage)
    case Left(err)    => Left(err)
  }

  def jwtFor(user: User): UIO[String] =
    scopeResolver.resolve(user).flatMap(jwtService.createJwt(user.userIri, _)).map(_.jwtString)

  def jwtForRoot(): UIO[String] = jwtFor(rootUser)
}
