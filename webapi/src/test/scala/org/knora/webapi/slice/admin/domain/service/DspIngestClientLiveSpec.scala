package org.knora.webapi.slice.admin.domain.service

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, equalTo, postRequestedFor, urlPathEqualTo}
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.options
import dsp.valueobjects.Project.Shortcode
import org.knora.webapi.IRI
import org.knora.webapi.config.DspIngestConfig
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.routing.{Jwt, JwtService}
import org.knora.webapi.slice.admin.domain.service.DspIngestClientLiveSpecLayers.{
  dspIngestConfigLayer,
  mockJwtServiceLayer,
  testPortLayer,
  wireMockServerLayer
}
import spray.json.JsValue
import zio.nio.file.Files
import zio.test.{Spec, TestEnvironment, ZIOSpecDefault, assertTrue}
import zio.{Random, Scope, Task, UIO, ULayer, ZIO, ZLayer}

object DspIngestClientLiveSpec extends ZIOSpecDefault {

  private val testShortCodeStr = "0001"
  private val testProject      = Shortcode.make(testShortCodeStr).toOption.orNull
  private val testContent      = "testContent".getBytes()
  private val expectedPath     = s"/projects/$testShortCodeStr/export"
  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("DspIngestClientLive")(test("should download a project export") {
      ZIO.scoped {
        for {
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
          path                <- DspIngestClient.exportProject(testProject)
          contentIsDownloaded <- Files.readAllBytes(path).map(_.toArray).map(_ sameElements testContent)
          // Verify the request is valid
          mockJwt <- ZIO.serviceWithZIO[JwtService](_.createJwtForDspIngest())
          _ = wiremock.verify(
                postRequestedFor(urlPathEqualTo(expectedPath))
                  .withHeader("Authorization", equalTo(s"Bearer ${mockJwt.jwtString}"))
              )
        } yield assertTrue(contentIsDownloaded)
      }
    }).provide(
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
