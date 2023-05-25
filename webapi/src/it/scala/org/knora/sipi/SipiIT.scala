/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.sipi

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.CountMatchingStrategy
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.exactly
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.options
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import zio._
import zio.http._
import zio.http.model.Status
import zio.test._
import scala.util.Failure
import scala.util.Success
import scala.util.Try

import org.knora.sipi.MockDspApiServer.verify._
import org.knora.webapi.messages.admin.responder.KnoraResponseADM
import org.knora.webapi.messages.admin.responder.sipimessages._
import org.knora.webapi.testcontainers.SipiTestContainer

object SipiIT extends ZIOSpecDefault {

  private val identifierTestFile  = "250x250.jp2"
  private val prefix              = "0001"
  private def copyTestImageToSipi = SipiTestContainer.copyImageToContainer(prefix, identifierTestFile)
  private def sendGetRequestToSipi(path: String) =
    SipiTestContainer.resolveUrl(path).map(Request.get).flatMap(Client.request(_))

  private val fileEndpointSuite =
    suite("Endpoint /{prefix}/{identifier}/file")(
      suite("Given a file does not exist in Sipi")(
        test("When getting the file, then Sipi responds with Not Found") {
          for {
            server   <- MockDspApiServer.resetAndGetWireMockServer
            response <- sendGetRequestToSipi(s"/$prefix/doesnotexist.jp2/file")
          } yield assertTrue(response.status == Status.NotFound, verifyNoInteractionWith(server))
        }
      ),
      suite("Given a file exists in Sipi")(
        test(
          "And given dsp-api returns 2='full view permissions on file', " +
            "when getting the file, " +
            "then Sipi responds with Ok"
        ) {
          val dspApiResponse       = SipiFileInfoGetResponseADM(permissionCode = 2, restrictedViewSettings = None)
          val dspApiPermissionPath = s"/admin/files/$prefix/$identifierTestFile"
          for {
            server   <- MockDspApiServer.resetAndStubGetResponse(dspApiPermissionPath, 200, dspApiResponse)
            _        <- copyTestImageToSipi
            response <- sendGetRequestToSipi(s"/$prefix/$identifierTestFile/file")
          } yield assertTrue(
            response.status == Status.Ok,
            verifySingleGetRequest(server, dspApiPermissionPath)
          )
        },
        test(
          "And given dsp-api returns 0='no view permission on file', " +
            "when getting the file, " +
            "then Sipi responds with Unauthorized"
        ) {
          val dspApiResponse       = SipiFileInfoGetResponseADM(permissionCode = 0, restrictedViewSettings = None)
          val dspApiPermissionPath = s"/admin/files/$prefix/$identifierTestFile"
          for {
            server   <- MockDspApiServer.resetAndStubGetResponse(dspApiPermissionPath, 200, dspApiResponse)
            _        <- copyTestImageToSipi
            response <- sendGetRequestToSipi(s"/$prefix/$identifierTestFile/file")
          } yield assertTrue(
            response.status == Status.Unauthorized,
            verifySingleGetRequest(server, dspApiPermissionPath)
          )
        },
        test(
          "And given dsp-api does not know this file and returns Not Found, " +
            "when getting the file, returns 2='full view permissions on file'" +
            "then Sipi responds with Not Found"
        ) {
          val dspApiPermissionPath = s"/admin/files/$prefix/$identifierTestFile"
          for {
            server   <- MockDspApiServer.resetAndStubGetResponse(dspApiPermissionPath, 404)
            _        <- copyTestImageToSipi
            response <- sendGetRequestToSipi(s"/$prefix/$identifierTestFile/file")
          } yield assertTrue(
            response.status == Status.NotFound,
            verifySingleGetRequest(server, dspApiPermissionPath)
          )
        }
      )
    )

  private val iiifEndpoint =
    suite("Endpoint {server}/{prefix}/{identifier}/{region}/{size}/{rotation}/{quality}.{format}")(
      suite("Given an image does not exist in Sipi")(
        test(
          "When getting the file, " +
            "then Sipi responds with Not Found"
        ) {
          for {
            server   <- MockDspApiServer.resetAndGetWireMockServer
            response <- sendGetRequestToSipi(s"/$prefix/doesnotexist.jp2/full/max/0/default.jp2")
          } yield assertTrue(response.status == Status.NotFound, verifyNoInteractionWith(server))
        }
      ),
      suite("Given an image exists in Sipi")(
        test(
          "And given dsp-api returns 2='full view permissions on file', " +
            "when getting the file, " +
            "Sipi responds with Ok"
        ) {
          val dspApiResponse       = SipiFileInfoGetResponseADM(permissionCode = 2, restrictedViewSettings = None)
          val dspApiPermissionPath = s"/admin/files/$prefix/$identifierTestFile"
          for {
            server   <- MockDspApiServer.resetAndStubGetResponse(dspApiPermissionPath, 200, dspApiResponse)
            _        <- copyTestImageToSipi
            response <- sendGetRequestToSipi(s"/$prefix/$identifierTestFile/full/max/0/default.jp2")
          } yield assertTrue(
            response.status == Status.Ok,
            verifySingleGetRequest(server, dspApiPermissionPath)
          )
        },
        test(
          "And given dsp-api returns 0='full view permissions on file', " +
            "when getting the file, " +
            "Sipi responds with Unauthorized"
        ) {
          val dspApiResponse       = SipiFileInfoGetResponseADM(permissionCode = 0, restrictedViewSettings = None)
          val dspApiPermissionPath = s"/admin/files/$prefix/$identifierTestFile"
          for {
            server   <- MockDspApiServer.resetAndStubGetResponse(dspApiPermissionPath, 200, dspApiResponse)
            _        <- copyTestImageToSipi
            response <- sendGetRequestToSipi(s"/$prefix/$identifierTestFile/full/max/0/default.jp2")
          } yield assertTrue(
            response.status == Status.Unauthorized,
            verifySingleGetRequest(server, dspApiPermissionPath)
          )
        },
        test(
          "And given dsp-api does not know this file and returns Not Found, " +
            "when getting the file, " +
            "Sipi responds with Not Found"
        ) {
          val dspApiPermissionPath = s"/admin/files/$prefix/$identifierTestFile"
          for {
            server   <- MockDspApiServer.resetAndStubGetResponse(dspApiPermissionPath, 404)
            _        <- copyTestImageToSipi
            response <- sendGetRequestToSipi(s"/$prefix/$identifierTestFile/full/max/0/default.jp2")
          } yield assertTrue(
            response.status == Status.NotFound,
            verifySingleGetRequest(server, dspApiPermissionPath)
          )
        }
      )
    )

  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("Sipi integration tests with mocked dsp-api")(
      fileEndpointSuite,
      iiifEndpoint,
      test("health check works") {
        for {
          server   <- MockDspApiServer.resetAndGetWireMockServer
          response <- sendGetRequestToSipi("/server/test.html")
        } yield assertTrue(response.status.isSuccess, verifyNoInteractionWith(server))
      }
    )
      .provideSomeLayerShared[Scope with Client with WireMockServer](SipiTestContainer.layer)
      .provideSomeLayerShared[Scope with Client](MockDspApiServer.layer)
      .provideSomeLayer[Scope](Client.default) @@ TestAspect.sequential
}

object MockDspApiServer {
  object verify {
    def verifySingleGetRequest(server: WireMockServer, path: String): Boolean =
      verify(server, 1, getRequestedFor(urlEqualTo(path))) && server.getAllServeEvents.size() == 1

    def verify(server: WireMockServer, amount: Int, requestPattern: RequestPatternBuilder): Boolean =
      verify(server, exactly(amount), requestPattern)

    def verify(server: WireMockServer, amount: CountMatchingStrategy, requestPattern: RequestPatternBuilder): Boolean =
      Try(server.verify(amount, requestPattern)) match {
        case Failure(e: Throwable) => println(s"\nMockDspApiServer: ${e.getMessage}"); false
        case Success(_)            => true
      }

    def verifyNoInteractionWith(server: WireMockServer): Boolean = server.getAllServeEvents.isEmpty
  }

  def resetAndGetWireMockServer: URIO[WireMockServer, WireMockServer] =
    ZIO.serviceWith[WireMockServer] { it => it.resetAll(); it }

  def resetAndStubGetResponse(url: String, status: Int): URIO[WireMockServer, WireMockServer] =
    resetAndGetWireMockServer.tap(server => ZIO.succeed(stubGetJsonResponse(server, url, status)))
  def resetAndStubGetResponse(url: String, status: Int, body: KnoraResponseADM): URIO[WireMockServer, WireMockServer] =
    resetAndGetWireMockServer.tap(server => ZIO.succeed(stubGetJsonResponse(server, url, status, Some(body))))

  private def stubGetJsonResponse(
    server: WireMockServer,
    url: String,
    status: Int,
    body: Option[KnoraResponseADM] = None
  ): Unit = {
    val json         = body.map(_.toJsValue.compactPrint).orNull
    val jsonResponse = aResponse().withStatus(status).withBody(json).withHeader("Content-Type", "application/json")
    val stubBuilder  = get(urlEqualTo(url)).willReturn(jsonResponse)
    server.stubFor(stubBuilder)
  }

  private def acquireWireMockServer: Task[WireMockServer] = ZIO.attempt {
    val server = new WireMockServer(options().port(3333)); // No-args constructor will start on port 8080, no HTTPS
    server.start()
    server
  }
  private def releaseWireMockServer(server: WireMockServer) = ZIO.attempt(server.stop()).logError.ignore

  val layer: ZLayer[Scope, Throwable, WireMockServer] =
    ZLayer.fromZIO(ZIO.acquireRelease(acquireWireMockServer)(releaseWireMockServer))
}
