/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
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
import org.knora.webapi.slice.security.ScopeResolver

case class TestSipiApiClient(
  private val be: StreamBackend[Task, ZioStreams],
  private val jwtService: JwtService,
  private val scopeResolver: ScopeResolver,
  private val sipiConfig: Sipi,
) extends BaseApiClient(be, jwtService, scopeResolver) {

  protected override def baseUrl: Uri = uri"${sipiConfig.internalBaseUrl}"

  def getImage(shortcode: Shortcode, filename: String, user: User): Task[Response[Either[String, String]]] =
    jwtFor(user).flatMap { jwt =>
      basicRequest.get(uri"/$shortcode/$filename/full/max/0/default.jpg").auth.bearer(jwt).send(backend)
    }

  def getFile(uri: String): Task[Response[Either[String, Array[Byte]]]] =
    basicRequest.get(internalUri(uri)).response(asByteArray).send(backend)

  def getFile(uri: String, user: User): Task[Response[Either[String, Array[Byte]]]] =
    jwtFor(user).flatMap { jwt =>
      basicRequest.get(internalUri(uri)).auth.bearer(jwt).response(asByteArray).send(backend)
    }

  private def internalUri(uri: String): Uri =
    Uri.unsafeParse(uri.replace("http://0.0.0.0:1024", sipiConfig.internalBaseUrl))
}

object TestSipiApiClient {

  def getImage(
    shortcode: Shortcode,
    filename: String,
    user: User,
  ): ZIO[TestSipiApiClient, Throwable, Response[Either[String, String]]] =
    ZIO.serviceWithZIO[TestSipiApiClient](_.getImage(shortcode, filename, user))

  def getFile(uri: String): ZIO[TestSipiApiClient, Throwable, Response[Either[String, Array[Byte]]]] =
    ZIO.serviceWithZIO[TestSipiApiClient](_.getFile(uri))

  def getFile(uri: String, user: User): ZIO[TestSipiApiClient, Throwable, Response[Either[String, Array[Byte]]]] =
    ZIO.serviceWithZIO[TestSipiApiClient](_.getFile(uri, user))

  val layer = ZLayer.derive[TestSipiApiClient]
}
