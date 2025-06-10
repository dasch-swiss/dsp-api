package org.knora.webapi.testservices

import sttp.capabilities.zio.ZioStreams
import sttp.client4.*
import sttp.client4.ziojson.*
import sttp.model.*
import zio.*
import zio.json.JsonDecoder

import org.knora.webapi.config.KnoraApi
import org.knora.webapi.slice.infrastructure.JwtService

final case class TestDspApiClient(
  private val config: KnoraApi,
  private val jwtService: JwtService,
  private val backend: StreamBackend[Task, ZioStreams],
) { self =>

  private val dspApiUrl: Uri = uri"${config.externalKnoraApiHostPort}"

  def get[A: JsonDecoder](wholePath: String): Task[Response[Either[ResponseException[String], A]]] =
    jwtService.createJwtForDspIngest().flatMap { jwt =>
      val url: Uri = dspApiUrl.withWholePath(wholePath)
      basicRequest
        .get(url)
        .header("Authorization", s"Bearer ${jwt.jwtString}")
        .response(asJson[A])
        .send(backend)
    }
}

object TestDspApiClient {
  val layer = ZLayer.derive[TestDspApiClient]
}
