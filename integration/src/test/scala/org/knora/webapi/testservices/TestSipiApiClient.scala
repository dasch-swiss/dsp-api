/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.testservices

import sttp.capabilities.zio.ZioStreams
import sttp.client4.*
import sttp.model.Uri
import zio.*

import org.knora.webapi.config.Sipi
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.infrastructure.JwtService
import org.knora.webapi.slice.security.Authenticator
import org.knora.webapi.slice.security.ScopeResolver

case class TestSipiApiClient(
  private val authenticator: Authenticator,
  private val be: StreamBackend[Task, ZioStreams],
  private val jwtService: JwtService,
  private val scopeResolver: ScopeResolver,
  private val sipiConfig: Sipi,
) extends BaseApiClient(authenticator, be, jwtService, scopeResolver) {

  protected override def baseUrl: Uri = uri"${sipiConfig.internalBaseUrl}"

  def getImage(shortcode: Shortcode, filename: String, user: User): Task[Response[Either[String, String]]] =
    jwtFor(user).flatMap { jwt =>
      basicRequest.get(uri"/$shortcode/$filename/full/max/0/default.jpg").cookie(authCookieName, jwt).send(backend)
    }
}

object TestSipiApiClient {

  def getImage(
    shortcode: Shortcode,
    filename: String,
    user: User,
  ): ZIO[TestSipiApiClient, Throwable, Response[Either[String, String]]] =
    ZIO.serviceWithZIO[TestSipiApiClient](_.getImage(shortcode, filename, user))

  val layer = ZLayer.derive[TestSipiApiClient]
}
