/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.service

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.options
import spray.json.JsValue
import zio.Random
import zio.Scope
import zio.Task
import zio.UIO
import zio.ULayer
import zio.ZIO
import zio.ZLayer
import zio.nio.file.Files
import zio.test.Spec
import zio.test.TestEnvironment
import zio.test.ZIOSpecDefault
import zio.test.assertTrue

import org.knora.webapi.IRI
import org.knora.webapi.config.DspIngestConfig
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.routing.Jwt
import org.knora.webapi.routing.JwtService
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.admin.domain.service.DspIngestClientLiveSpecLayers.dspIngestConfigLayer
import org.knora.webapi.slice.admin.domain.service.DspIngestClientLiveSpecLayers.mockJwtServiceLayer
import org.knora.webapi.slice.admin.domain.service.DspIngestClientLiveSpecLayers.testPortLayer
import org.knora.webapi.slice.admin.domain.service.DspIngestClientLiveSpecLayers.wireMockServerLayer

object DspIngestClientLiveSpec extends ZIOSpecDefault {

  private val testShortCodeStr = "0001"
  private val testProject      = Shortcode.unsafeFrom(testShortCodeStr)
  private val testContent      = "testContent".getBytes()
  private val expectedPath     = s"/projects/$testShortCodeStr/export"

  private val exportProjectSuite = suite("exportProject")(test("should download a project export") {
    ZIO.scoped {
      for {
        // given
        wiremock <- ZIO.service[WireMockServer]
        _ = wiremock.stubFor(
              WireMock
                .post(urlPathEqualTo(expectedPath))
                .willReturn(
                  aResponse()
                    .withHeader("Content-Type", "application/zip")
                    .withHeader("Content-Disposition", s"export-$testShortCodeStr.zip")
                    .withBody(testContent)
                    .withStatus(200)
                )
            )
        mockJwt <- JwtService.createJwtForDspIngest()

        // when
        path <- DspIngestClient.exportProject(testProject)

        // then
        _ = wiremock.verify(
              postRequestedFor(urlPathEqualTo(expectedPath))
                .withHeader("Authorization", equalTo(s"Bearer ${mockJwt.jwtString}"))
            )
        contentIsDownloaded <- Files.readAllBytes(path).map(_.toArray).map(_ sameElements testContent)
      } yield assertTrue(contentIsDownloaded)
    }
  })

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("DspIngestClientLive")(exportProjectSuite).provide(
      DspIngestClientLive.layer,
      dspIngestConfigLayer,
      mockJwtServiceLayer,
      testPortLayer,
      wireMockServerLayer
    )
}

object DspIngestClientLiveSpecLayers {
  val mockJwtServiceLayer: ULayer[JwtService] = ZLayer.succeed(new JwtService {
    override def createJwt(user: UserADM, content: Map[String, JsValue]): UIO[Jwt] =
      throw new UnsupportedOperationException("not implemented")
    override def createJwtForDspIngest(): UIO[Jwt] = ZIO.succeed(Jwt("mock-jwt-string-value", Long.MaxValue))
    override def validateToken(token: String): Task[Boolean] =
      throw new UnsupportedOperationException("not implemented")
    override def extractUserIriFromToken(token: String): Task[Option[IRI]] =
      throw new UnsupportedOperationException("not implemented")
  })
  case class Testport(port: Int) extends AnyVal

  val testPortLayer: ULayer[Testport] = ZLayer.fromZIO(for {
    port <- Random.nextIntBetween(1000, 10_000)
  } yield Testport(port))

  val dspIngestConfigLayer: ZLayer[Testport, Nothing, DspIngestConfig] = ZLayer.fromZIO(
    ZIO
      .serviceWith[Testport](_.port)
      .map(port => DspIngestConfig(baseUrl = s"http://localhost:$port", audience = "audience"))
  )

  private def acquireWireMockServer(port: Int): Task[WireMockServer] = ZIO.attempt {
    val server = new WireMockServer(options().port(port))
    server.start()
    server
  }
  private def releaseWireMockServer(server: WireMockServer) = ZIO.attempt(server.stop()).logError.ignore

  val wireMockServerLayer: ZLayer[Testport, Throwable, WireMockServer] =
    ZLayer.scoped {
      for {
        port   <- ZIO.serviceWith[Testport](_.port)
        server <- ZIO.acquireRelease(acquireWireMockServer(port))(releaseWireMockServer)
      } yield server
    }
}
