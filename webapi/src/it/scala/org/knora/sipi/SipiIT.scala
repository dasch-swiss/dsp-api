/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.sipi

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.CountMatchingStrategy
import com.github.tomakehurst.wiremock.client.MappingBuilder
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.exactly
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.options
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import zio._
import zio.http._
import zio.http.model.Status
import zio.test._
import scala.util.Failure
import scala.util.Success
import scala.util.Try

import org.knora.webapi.messages.admin.responder.KnoraResponseADM
import org.knora.webapi.messages.admin.responder.sipimessages._
import org.knora.webapi.testcontainers.SipiTestContainer

object SipiIT extends ZIOSpecDefault {

  private def sendGetRequestToSipi(path: String) =
    SipiTestContainer.resolveUrl(path).map(Request.get).flatMap(Client.request(_))
  private val identifierTestFile = "250x250.jp2"
  private val prefix             = "0001"

  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("Sipi integration tests with mocked dsp-api")(
      test("health check works") {
        for {
          server   <- MockDspApiServer.resetAndGetWireMockServer
          response <- sendGetRequestToSipi("/server/test.html")
        } yield assertTrue(response.status.isSuccess, MockDspApiServer.verifyNoInteractionWith(server))
      },
      test("given an image does not exist in SIPI, the response is 404") {
        for {
          server   <- MockDspApiServer.resetAndGetWireMockServer
          response <- sendGetRequestToSipi(s"/$prefix/doesnotexist.jp2/full/1920,1080/0/default.jpg")
        } yield assertTrue(response.status == Status.NotFound, MockDspApiServer.verifyNoInteractionWith(server))
      },
      test("given a file does not exist in SIPI, the response is 404") {
        for {
          server   <- MockDspApiServer.resetAndGetWireMockServer
          response <- sendGetRequestToSipi(s"/$prefix/doesnotexist.jp2/file")
        } yield assertTrue(response.status == Status.NotFound, MockDspApiServer.verifyNoInteractionWith(server))
      },
      test(
        "given a file exists in SIPI, and given dsp-api returns 2='full view permissions on file', the response is 200"
      ) {
        val dspApiResponse = SipiFileInfoGetResponseADM(permissionCode = 2, restrictedViewSettings = None)
        val sipiServerPath = s"/admin/files/$prefix/$identifierTestFile"
        for {
          server   <- MockDspApiServer.resetAndStubGetResponse(sipiServerPath, 200, dspApiResponse)
          _        <- SipiTestContainer.copyImageToContainer(prefix, identifierTestFile)
          response <- sendGetRequestToSipi(s"/$prefix/$identifierTestFile/file")
        } yield assertTrue(
          response.status == Status.Ok,
          MockDspApiServer.verifySingleGetRequest(server, sipiServerPath)
        )
      },
      test(
        "given a file exists in SIPI, and given dsp-api returns 0='no view permission on file', the response is 401"
      ) {
        val dspApiResponse = SipiFileInfoGetResponseADM(permissionCode = 0, restrictedViewSettings = None)
        val sipiServerPath = s"/admin/files/$prefix/$identifierTestFile"
        for {
          server   <- MockDspApiServer.resetAndStubGetResponse(sipiServerPath, 200, dspApiResponse)
          _        <- SipiTestContainer.copyImageToContainer(prefix, identifierTestFile)
          response <- sendGetRequestToSipi(s"/$prefix/$identifierTestFile/file")
        } yield assertTrue(
          response.status == Status.Unauthorized,
          MockDspApiServer.verifySingleGetRequest(server, sipiServerPath)
        )
      },
      test(
        "given a file exists in SIPI, and given dsp-api does not know this file and returns a 404, the response is 404"
      ) {
        val sipiServerPath = s"/admin/files/$prefix/$identifierTestFile"
        for {
          server   <- MockDspApiServer.resetAndStubGetResponse(sipiServerPath, 404)
          _        <- SipiTestContainer.copyImageToContainer(prefix, identifierTestFile)
          response <- sendGetRequestToSipi(s"/$prefix/$identifierTestFile/file")
        } yield assertTrue(
          response.status == Status.NotFound,
          MockDspApiServer.verifySingleGetRequest(server, sipiServerPath)
        )
      }
    )
      .provideSomeLayerShared[Scope with Client with WireMockServer](SipiTestContainer.layer)
      .provideSomeLayerShared[Scope with Client](MockDspApiServer.layer)
      .provideSomeLayer[Scope](Client.default) @@ TestAspect.sequential
}

object MockDspApiServer {
  def resetAndStubGetResponse(url: String, status: Int): URIO[WireMockServer, WireMockServer] =
    resetAndGetWireMockServer.tap(server => ZIO.succeed(stubGetResponse(server, url, status)))
  def resetAndStubGetResponse(url: String, status: Int, body: KnoraResponseADM): URIO[WireMockServer, WireMockServer] =
    resetAndGetWireMockServer.tap(server => ZIO.succeed(stubGetResponse(server, url, status, Some(body))))

  private def stubGetResponse(
    server: WireMockServer,
    url: String,
    status: Int,
    body: Option[KnoraResponseADM] = None
  ): MappingBuilder = {
    val json         = body.map(_.toJsValue.compactPrint).orNull
    val jsonResponse = aResponse().withStatus(status).withBody(json).withHeader("Content-Type", "application/json")
    val stubBuilder  = get(urlEqualTo(url)).willReturn(jsonResponse)
    server.stubFor(stubBuilder)
    stubBuilder
  }

  def verifySingleGetRequest(server: WireMockServer, path: String): Boolean =
    verify(server, 1, getRequestedFor(urlEqualTo(path)))

  def verify(server: WireMockServer, amount: Int, requestPattern: RequestPatternBuilder): Boolean =
    verify(server, exactly(amount), requestPattern)

  def verify(server: WireMockServer, amount: CountMatchingStrategy, requestPattern: RequestPatternBuilder): Boolean =
    Try(server.verify(amount, requestPattern)) match {
      case Failure(e: Throwable) => println(e.getMessage); false
      case Success(_)            => true
    }

  def verifyNoInteractionWith(server: WireMockServer): Boolean = server.getAllServeEvents.isEmpty

  def resetAndGetWireMockServer: URIO[WireMockServer, WireMockServer] =
    ZIO.serviceWith[WireMockServer] { it => it.resetAll(); it }

  private def acquireWireMockServer: Task[WireMockServer] = ZIO.attempt {
    val server = new WireMockServer(options().port(3333)); // No-args constructor will start on port 8080, no HTTPS
    server.start()
    server
  }
  private def releaseWireMockServer(server: WireMockServer) = ZIO.attempt(server.stop()).logError.ignore

  val layer: ZLayer[Scope, Throwable, WireMockServer] =
    ZLayer.fromZIO(ZIO.acquireRelease(acquireWireMockServer)(releaseWireMockServer))
}
