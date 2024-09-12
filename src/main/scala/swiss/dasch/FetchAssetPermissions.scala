/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch

import cats.implicits._
import sttp.capabilities.zio.ZioStreams
import sttp.client3.SttpBackend
import sttp.client3.*
import sttp.client3.httpclient.zio.HttpClientZioBackend
import swiss.dasch.config.Configuration
import swiss.dasch.domain.AssetInfo
import zio.*
import zio.json.DecoderOps
import zio.json.DeriveJsonDecoder
import zio.json.JsonDecoder

import scala.concurrent.duration._

import FetchAssetPermissions.PermissionResponse

trait FetchAssetPermissions {
  def getPermissionCode(
    jwt: String,
    assetInfo: AssetInfo,
  ): Task[Int]
}

class FetchAssetPermissionsLive(
  sttp: SttpBackend[Task, ZioStreams],
  apiConfig: Configuration.DspApiConfig,
) extends FetchAssetPermissions {
  def getPermissionCode(
    jwt: String,
    assetInfo: AssetInfo,
  ): Task[Int] =
    (for {
      uri <-
        ZIO.succeed(
          uri"${apiConfig.url}/admin/files/${assetInfo.assetRef.belongsToProject}/${assetInfo.derivative.filename}",
        )
      response    <- sttp.send(basicRequest.get(uri).header("Authorization", s"Bearer ${jwt}"))
      successBody <- ZIO.fromEither(response.body).mapError(httpError(uri.toString, response.code.code, _))
      permissionCode <-
        ZIO.fromEither(successBody.fromJson[PermissionResponse].bimap(e => new Exception(e), _.permissionCode))
    } yield permissionCode).tapError(e => ZIO.logError(s"FetchAssetPermissions failure: ${e.getMessage}"))

  def httpError(uri: String, code: Int, body: String): Throwable =
    Exception(s"FetchAssetPermissions: GET $uri returned $code and contents: $body")
}

object FetchAssetPermissions {
  final case class PermissionResponse(permissionCode: Int)

  implicit val decoder: JsonDecoder[PermissionResponse] = DeriveJsonDecoder.gen[PermissionResponse]

  val layer =
    HttpClientZioBackend.layer(SttpBackendOptions.connectionTimeout(5.seconds)).orDie >+>
      ZLayer.derive[FetchAssetPermissionsLive]
}
