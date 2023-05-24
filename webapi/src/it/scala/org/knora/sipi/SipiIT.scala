package org.knora.sipi

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.options
import zio.Scope
import zio.Task
import zio.ZIO
import zio.ZLayer
import zio.http.Client
import zio.http.Request
import zio.http.model.Status
import zio.test.Spec
import zio.test.TestAspect
import zio.test.TestEnvironment
import zio.test.ZIOSpecDefault
import zio.test.assertTrue

import org.knora.webapi.testcontainers.SipiTestContainer

object SipiIT extends ZIOSpecDefault {
  private def getSipiUrl                                = ZIO.serviceWith[SipiTestContainer](_.sipiBaseUrl)
  private def resetAndGetWireMockServer                 = ZIO.serviceWith[WireMockServer] { it => it.resetAll(); it }
  private def noInteractionWith(server: WireMockServer) = server.getAllServeEvents.isEmpty

  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("Sipi integration tests with mocked dsp-api")(
      test("health check works") {
        for {
          server      <- resetAndGetWireMockServer
          sipiBaseUrl <- getSipiUrl
          response    <- Client.request(Request.get(sipiBaseUrl.setPath("/server/test.html")))
        } yield assertTrue(response.status.isSuccess, noInteractionWith(server))
      },
      test("given an image does not exist in SIPI, the response is 404") {
        for {
          server      <- resetAndGetWireMockServer
          sipiBaseUrl <- getSipiUrl
          response <-
            Client
              .request(Request.get(sipiBaseUrl.setPath("/images/0001/doesnotexist.jp2/full/1920,1080/0/default.jpg")))
              .logError
        } yield assertTrue(response.status == Status.NotFound, noInteractionWith(server))
      },
      test("given a file does not exist in SIPI, the response is 404") {
        for {
          server      <- resetAndGetWireMockServer
          sipiBaseUrl <- getSipiUrl
          response    <- Client.request(Request.get(sipiBaseUrl.setPath("/images/0001/doesnotexist.jp2/file"))).logError
        } yield assertTrue(response.status == Status.NotFound, noInteractionWith(server))
      }
    )
      .provideSomeLayerShared[Scope with Client with WireMockServer](SipiTestContainer.layer)
      .provideSomeLayerShared[Scope with Client](HttpMockServer.layer)
      .provideSomeLayer[Scope](Client.default) @@ TestAspect.sequential

}

object HttpMockServer {
  private def acquireWireMockServer: Task[WireMockServer] = ZIO.attempt {
    val server = new WireMockServer(options().port(3333)); // No-args constructor will start on port 8080, no HTTPS
    server.start();
    server
  }
  private def releaseWireMockServer(server: WireMockServer) = ZIO.attempt(server.stop()).logError.ignore

  val layer: ZLayer[Scope, Throwable, WireMockServer] =
    ZLayer.fromZIO(ZIO.acquireRelease(acquireWireMockServer)(releaseWireMockServer))
}
