/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.service

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, equalTo, get, getRequestedFor, post, postRequestedFor, urlEqualTo, urlPathEqualTo}
import com.github.tomakehurst.wiremock.client.{CountMatchingStrategy, MappingBuilder, ResponseDefinitionBuilder, WireMock}
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.options
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.knora.webapi.IRI
import org.knora.webapi.config.DspIngestConfig
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.routing.{Jwt, JwtService}
import org.knora.webapi.slice.admin.api.model.MaintenanceRequests.AssetId
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.admin.domain.service.DspIngestClientLiveSpecLayers.{dspIngestConfigLayer, jwtServiceMockLayer}
import org.knora.webapi.slice.admin.domain.service.HttpMockServer.TestPort
import spray.json.JsValue
import zio.json.{DeriveJsonEncoder, EncoderOps, JsonEncoder}
import zio.nio.file.Files
import zio.test.{Spec, TestAspect, TestEnvironment, ZIOSpecDefault, assertTrue}
import zio.{Console, Random, Scope, Task, UIO, ULayer, URIO, ZIO, ZLayer}

object DspIngestClientLiveSpec extends ZIOSpecDefault {

  private val testShortcodeStr = "0001"
  private val testShortcode    = Shortcode.unsafeFrom(testShortcodeStr)
  private val testContent      = "testContent".getBytes()

  private val exportProjectSuite = suite("exportProject")(test("should download a project export") {
    val expectedUrl = s"/projects/$testShortcodeStr/export"
    for {
      // given
      _ <- HttpMockServer.stub.postResponse(
             expectedUrl,
             aResponse()
               .withHeader("Content-Type", "application/zip")
               .withHeader("Content-Disposition", s"export-$testShortcodeStr.zip")
               .withBody(testContent)
               .withStatus(200)
           )

      // when
      path <- DspIngestClient.exportProject(testShortcode)

      // then
      mockJwt <- JwtService.createJwtForDspIngest().map(_.jwtString)
      _ <- HttpMockServer.verify.request(
             postRequestedFor(urlPathEqualTo(expectedUrl))
               .withHeader("Authorization", equalTo(s"Bearer $mockJwt"))
           )
      contentIsDownloaded <- Files.readAllBytes(path).map(_.toArray).map(_ sameElements testContent)
    } yield assertTrue(contentIsDownloaded)
  })

  private val getAssetInfoSuite = suite("getAssetInfo")(test("should return the assetInfo") {
    implicit val encoder: JsonEncoder[AssetInfoResponse] = DeriveJsonEncoder.gen[AssetInfoResponse]
    val assetId                                          = AssetId.unsafeFrom("4sAf4AmPeeg-ZjDn3Tot1Zt")
    val expectedUrl                                      = s"/projects/$testShortcodeStr/assets/$assetId"
    val expected = AssetInfoResponse(
      internalFilename = s"$assetId.txt",
      originalInternalFilename = s"$assetId.txt.orig",
      originalFilename = "test.txt",
      checksumOriginal = "bfd3192ea04d5f42d79836cf3b8fbf17007bab71",
      checksumDerivative = "17bab70071fbf8b3fc63897d24f5d40ae2913dfb",
      internalMimeType = Some("text/plain"),
      originalMimeType = Some("text/plain")
    )
    for {
      // given
      _ <- HttpMockServer.stub.getResponseJsonBody(expectedUrl, 200, expected)

      // when
      assetInfo <- DspIngestClient.getAssetInfo(testShortcode, assetId)

      // then
      mockJwt <- JwtService.createJwtForDspIngest().map(_.jwtString)
      _ <- HttpMockServer.verify.request(
             getRequestedFor(urlPathEqualTo(expectedUrl))
               .withHeader("Authorization", equalTo(s"Bearer $mockJwt"))
           )
    } yield assertTrue(assetInfo == expected)
  })

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("DspIngestClientLive")(
      exportProjectSuite,
      getAssetInfoSuite
    ).provideSome[Scope](
      DspIngestClientLive.layer,
      HttpMockServer.layer,
      TestPort.random,
      dspIngestConfigLayer,
      jwtServiceMockLayer
    ) @@ TestAspect.sequential
}

object DspIngestClientLiveSpecLayers {
  val jwtServiceMockLayer: ULayer[JwtService] = ZLayer.succeed {
    val unsupported = ZIO.die(new UnsupportedOperationException("not implemented"))
    new JwtService {
      override def createJwtForDspIngest(): UIO[Jwt]                                 = ZIO.succeed(Jwt("mock-jwt-string-value", Long.MaxValue))
      override def createJwt(user: UserADM, content: Map[String, JsValue]): UIO[Jwt] = unsupported
      override def validateToken(token: String): Task[Boolean]                       = unsupported
      override def extractUserIriFromToken(token: String): Task[Option[IRI]]         = unsupported
    }
  }

  val dspIngestConfigLayer: ZLayer[TestPort, Nothing, DspIngestConfig] = ZLayer.fromZIO(
    ZIO
      .serviceWith[TestPort](_.value)
      .map(port => DspIngestConfig(baseUrl = s"http://localhost:$port", audience = "audience"))
  )
}

object HttpMockServer {
  object verify {
    def request(
      requestPattern: RequestPatternBuilder,
      amount: CountMatchingStrategy = WireMock.exactly(1)
    ): ZIO[WireMockServer, Throwable, Unit] = ZIO.serviceWithZIO[WireMockServer](server =>
      ZIO.attempt(server.verify(amount, requestPattern)).tapError { e =>
        Console.printLine(s"\nMockDspApiServer: ${e.getMessage}")
      }
    )
  }

  object stub {
    def getResponseJsonBody[A](url: String, status: Int, body: A)(implicit
      encoder: JsonEncoder[A]
    ): URIO[WireMockServer, WireMockServer] =
      getResponse(
        url,
        aResponse().withStatus(status).withBody(body.toJson).withHeader("Content-Type", "application/json")
      )

    def getResponse(url: String, response: ResponseDefinitionBuilder): URIO[WireMockServer, WireMockServer] =
      resetAndStubServer(get(urlEqualTo(url)).willReturn(response))

    def postResponse(url: String, response: ResponseDefinitionBuilder): URIO[WireMockServer, WireMockServer] =
      resetAndStubServer(post(urlEqualTo(url)).willReturn(response))

    private def resetAndStubServer(mappingBuilder: MappingBuilder) =
      resetAndGetWireMockServer.tap(server => ZIO.succeed(server.stubFor(mappingBuilder)))
    private def resetAndGetWireMockServer: URIO[WireMockServer, WireMockServer] =
      ZIO.serviceWith[WireMockServer] { it =>
        it.resetAll();
        it
      }
  }
  case class TestPort(value: Int) extends AnyVal
  object TestPort {
    val random: ULayer[TestPort] = ZLayer.fromZIO(Random.nextIntBetween(1000, 10_000).map(TestPort.apply))
  }

  private def acquireWireMockServer: ZIO[TestPort, Throwable, WireMockServer] =
    ZIO.serviceWith[TestPort](_.value).flatMap { port =>
      ZIO.attempt {
        val server = new WireMockServer(options().port(port)); // No-args constructor will start on port 8080, no HTTPS
        server.start()
        server
      }
    }

  private def releaseWireMockServer(server: WireMockServer) = ZIO.attempt(server.stop()).logError.ignore

  val layer: ZLayer[Scope & TestPort, Throwable, WireMockServer] =
    ZLayer.fromZIO(ZIO.acquireRelease(acquireWireMockServer)(releaseWireMockServer))
}
