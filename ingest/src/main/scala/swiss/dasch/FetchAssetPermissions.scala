/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch

import cats.implicits.*
import sttp.client4.*
import sttp.client4.httpclient.zio.HttpClientZioBackend
import sttp.client4.opentelemetry.zio.OpenTelemetryTracingZioBackend
import swiss.dasch.FetchAssetPermissions.PermissionResponse
import swiss.dasch.config.Configuration
import swiss.dasch.domain.AssetInfo
import zio.*
import zio.json.DecoderOps
import zio.json.DeriveJsonDecoder
import zio.json.JsonDecoder
import zio.telemetry.opentelemetry.tracing.Tracing

import scala.concurrent.duration.*

trait FetchAssetPermissions {
  def getPermissionCode(
    jwt: Option[String],
    assetInfo: AssetInfo,
  ): Task[Int]
}

class FetchAssetPermissionsLive(
  sttp: Backend[Task],
  apiConfig: Configuration.DspApiConfig,
) extends FetchAssetPermissions {
  def getPermissionCode(
    jwt: Option[String],
    assetInfo: AssetInfo,
  ): Task[Int] =
    (for {
      uri <-
        ZIO.succeed(
          uri"${apiConfig.url}/admin/files/${assetInfo.assetRef.belongsToProject}/${assetInfo.derivative.filename}",
        )
      response       <- basicRequest.get(uri).header("Authorization", jwt.map(jwt => s"Bearer ${jwt}")).send(sttp)
      successBody    <- ZIO.fromEither(response.body).mapError(httpError(uri.toString, response.code.code, _))
      permissionCode <-
        ZIO.fromEither(successBody.fromJson[PermissionResponse].bimap(e => new Exception(e), _.permissionCode))
    } yield permissionCode).tapError(e => ZIO.logError(s"FetchAssetPermissions failure: ${e.getMessage}"))

  def httpError(uri: String, code: Int, body: String): Throwable =
    Exception(s"FetchAssetPermissions: GET $uri returned $code and contents: $body")
}

object FetchAssetPermissions {
  final case class PermissionResponse(permissionCode: Int)

  implicit val decoder: JsonDecoder[PermissionResponse] = DeriveJsonDecoder.gen[PermissionResponse]

  val layer: URLayer[Tracing & Configuration.DspApiConfig, FetchAssetPermissions] = ZLayer
    .fromZIO(for {
      tracing               <- ZIO.service[Tracing]
      zioBackend            <- HttpClientZioBackend(options = BackendOptions.Default.connectionTimeout(5.seconds))
      backend: Backend[Task] = OpenTelemetryTracingZioBackend(zioBackend, tracing)
      apiConfig             <- ZIO.service[Configuration.DspApiConfig]
    } yield new FetchAssetPermissionsLive(backend, apiConfig))
    .orDie
}
