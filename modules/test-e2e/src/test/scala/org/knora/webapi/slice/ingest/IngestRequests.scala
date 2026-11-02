/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ingest

import sttp.client4.*
import sttp.client4.httpclient.zio.HttpClientZioBackend
import sttp.model.Method
import sttp.model.Uri
import swiss.dasch.api.ProjectsEndpointsResponses.AssetInfoResponse
import zio.*
import zio.json.*

import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

import org.knora.webapi.config.KnoraApi
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.testservices.TestApiClient

/** Raw requests to the `/ingest/` routes, which take a bearer token with ingest's scopes and stream their bodies. */
object IngestRequests {

  private def url(path: String) = ZIO.serviceWith[KnoraApi](api => Uri.unsafeParse(api.externalKnoraApiBaseUrl + path))

  /** Sends `method path` as `user` (anonymous for `None`), with `body` if given, and returns the response. */
  def send(
    method: Method,
    path: String,
    user: Option[User],
    body: Option[Array[Byte]] = None,
    contentType: String = "application/octet-stream",
  ): RIO[KnoraApi & TestApiClient, Response[String]] =
    for {
      uri      <- url(path)
      jwt      <- ZIO.foreach(user)(u => ZIO.serviceWithZIO[TestApiClient](_.jwtFor(u)))
      request   = basicRequest.method(method, uri).response(asStringAlways)
      withBody  = body.fold(request)(bytes => request.body(bytes).contentType(contentType))
      response <- ZIO.scoped(HttpClientZioBackend.scoped().flatMap(jwt.fold(withBody)(withBody.auth.bearer).send(_)))
    } yield response

  val jpeg: UIO[Array[Byte]] = ZIO.succeed {
    val out = ByteArrayOutputStream()
    ImageIO.write(BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB), "jpeg", out)
    out.toByteArray
  }

  /** Uploads a small JPEG to the project as `user` and returns the new asset's id. */
  def uploadJpeg(shortcode: String, user: User): RIO[KnoraApi & TestApiClient, String] =
    for {
      image    <- jpeg
      response <- send(Method.POST, s"/ingest/projects/$shortcode/assets/ingest/matrix.jpg", Some(user), Some(image))
      info     <-
        ZIO.fromEither(response.body.fromJson[AssetInfoResponse]).mapError(e => Exception(s"$e: ${response.body}"))
    } yield info.internalFilename.takeWhile(_ != '.')
}
