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
import zio.json.DecoderOps
import zio.json.ast.Json
import zio.test._
import scala.util.Failure
import scala.util.Success
import scala.util.Try

import org.knora.sipi.MockDspApiServer.verify._
import org.knora.webapi.messages.admin.responder.KnoraResponseADM
import org.knora.webapi.messages.admin.responder.sipimessages._
import org.knora.webapi.testcontainers.SipiTestContainer
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder.newRequestPattern

object SipiIT extends ZIOSpecDefault {

  private val imageTestfile = "FGiLaT4zzuV-CqwbEDFAFeS.jp2"
  private val infoTestfile  = "FGiLaT4zzuV-CqwbEDFAFeS.info"
  private val origTestfile  = "FGiLaT4zzuV-CqwbEDFAFeS.jp2.orig"
  private val prefix        = "0001"
  private def copyTestFilesToSipi = ZIO.foreach(List(imageTestfile, infoTestfile, origTestfile))(
    SipiTestContainer.copyFileToImageFolderInContainer(prefix, _)
  )

  private def getWithoutAuthorization(path: String) =
    SipiTestContainer.resolveUrl(path).map(Request.get).flatMap(Client.request(_))

  // expires May 2033
  private val jwt =
    "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJpc3MiOiIwLjAuMC4wOjMzMzMiLCJzdWIiOiJodHRwOi8vcmRmaC5jaC91c2Vycy9yb290IiwiYXVkIjpbIktub3JhIiwiU2lwaSJdLCJleHAiOjIwMDAwMDAwMDAsImlhdCI6MTY4NzE2NDUzOSwianRpIjoiSG9SSFg5V1lSZHV6VnVmTXZFT1c4USJ9.4RO0MdoKAlm4_xa9PKU86BdU2cX9hUBxDnc2VQRgjwM"

  private val cookiesSuite =
    suite("Given a request is authorized using cookies")(
      test(
        "And Given the request contains multiple cookies " +
          "When getting an existing file, " +
          "then Sipi should extract the correct cookie, send it to dsp-api " +
          "and responds with Ok"
      ) {
        for {
          _ <- copyTestFilesToSipi
          _ <- MockDspApiServer.resetAndAllowWithPermissionCode(prefix, imageTestfile, 2)
          response <-
            SipiTestContainer
              .resolveUrl(s"/$prefix/$imageTestfile/file")
              .map { url =>
                Request
                  .get(url)
                  .withCookie(
                    s"KnoraAuthenticationGAXDALRQFYYDUMZTGMZQ9999aSecondCookie=anotherValueShouldBeIgnored; KnoraAuthenticationGAXDALRQFYYDUMZTGMZQ9999=$jwt"
                  )
              }
              .flatMap(Client.request(_))
          requestToDspApiContainsJwt <- MockDspApiServer.verifyAuthBearerTokenReceived(jwt)
        } yield assertTrue(response.status == Status.Ok, requestToDspApiContainsJwt)
      },
      test(
        "And Given the request contains a single cookie " +
          "When getting an existing file, " +
          "then Sipi should send it to dsp-api " +
          "and responds with Ok"
      ) {
        for {
          _ <- copyTestFilesToSipi
          _ <- MockDspApiServer.resetAndAllowWithPermissionCode(prefix, imageTestfile, 2)
          response <-
            SipiTestContainer
              .resolveUrl(s"/$prefix/$imageTestfile/file")
              .map(url => Request.get(url).withCookie(s"KnoraAuthenticationGAXDALRQFYYDUMZTGMZQ9999=$jwt"))
              .flatMap(Client.request(_))
          requestToDspApiContainsJwt <- MockDspApiServer.verifyAuthBearerTokenReceived(jwt)
        } yield assertTrue(response.status == Status.Ok, requestToDspApiContainsJwt)
      }
    )

  private val knoraJsonEndpointSuite =
    suite("Endpoint /{prefix}/{identifier}/knora.json")(
      suite("Given the user is unauthorized")(
        suite("And given a .info file exists in Sipi")(
          test(
            "And given dsp-api returns 2='full view permissions on file', " +
              "when getting the file, " +
              "then Sipi responds with Ok"
          ) {
            def expectedJson(port: Int) =
              s"""{
                 |  "@context":"http://sipi.io/api/file/3/context.json",
                 |  "id":"http://localhost:$port/0001/FGiLaT4zzuV-CqwbEDFAFeS.jp2",
                 |  "checksumOriginal":"fb252a4fb3d90ce4ebc7e123d54a4112398a7994541b11aab5e4230eac01a61c",
                 |  "checksumDerivative":"0ce405c9b183fb0d0a9998e9a49e39c93b699e0f8e2a9ac3496c349e5cea09cc",
                 |  "width":250,
                 |  "height":250,
                 |  "internalMimeType":"image/jp2",
                 |  "originalMimeType":"image/jp2",
                 |  "originalFilename":"250x250.jp2"
                 |}""".stripMargin.fromJson[Json]
            for {
              _        <- MockDspApiServer.resetAndAllowWithPermissionCode(prefix, imageTestfile, permissionCode = 2)
              _        <- copyTestFilesToSipi
              response <- getWithoutAuthorization(s"/$prefix/$imageTestfile/knora.json")
              json     <- response.body.asString.map(_.fromJson[Json])
              expected <- SipiTestContainer.port.map(expectedJson)
            } yield assertTrue(
              response.status == Status.Ok,
              json == expected
            )
          }
        )
      )
    )

  private val fileEndpointSuite =
    suite("Endpoint /{prefix}/{identifier}/file")(
      suite("Given the user is unauthorized")(
        suite("Given a file does not exist in Sipi")(
          test("When getting the file, then Sipi responds with Not Found") {
            for {
              server   <- MockDspApiServer.resetAndGetWireMockServer
              response <- getWithoutAuthorization(s"/$prefix/doesnotexist.jp2/file")
            } yield assertTrue(response.status == Status.NotFound, verifyNoInteractionWith(server))
          }
        ),
        suite("Given an image exists in Sipi")(
          test(
            "And given dsp-api returns 2='full view permissions on file', " +
              "when getting the file, " +
              "then Sipi responds with Ok"
          ) {
            val dspApiResponse       = SipiFileInfoGetResponseADM(permissionCode = 2, restrictedViewSettings = None)
            val dspApiPermissionPath = s"/admin/files/$prefix/$imageTestfile"
            for {
              server   <- MockDspApiServer.resetAndStubGetResponse(dspApiPermissionPath, 200, dspApiResponse)
              _        <- copyTestFilesToSipi
              response <- getWithoutAuthorization(s"/$prefix/$imageTestfile/file")
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
            val dspApiPermissionPath = s"/admin/files/$prefix/$imageTestfile"
            for {
              server   <- MockDspApiServer.resetAndStubGetResponse(dspApiPermissionPath, 200, dspApiResponse)
              _        <- copyTestFilesToSipi
              response <- getWithoutAuthorization(s"/$prefix/$imageTestfile/file")
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
            val dspApiPermissionPath = s"/admin/files/$prefix/$imageTestfile"
            for {
              server   <- MockDspApiServer.resetAndStubGetResponse(dspApiPermissionPath, 404)
              _        <- copyTestFilesToSipi
              response <- getWithoutAuthorization(s"/$prefix/$imageTestfile/file")
            } yield assertTrue(
              response.status == Status.NotFound,
              verifySingleGetRequest(server, dspApiPermissionPath)
            )
          }
        )
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
            response <- getWithoutAuthorization(s"/$prefix/doesnotexist.jp2/full/max/0/default.jp2")
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
          val dspApiPermissionPath = s"/admin/files/$prefix/$imageTestfile"
          for {
            server   <- MockDspApiServer.resetAndStubGetResponse(dspApiPermissionPath, 200, dspApiResponse)
            _        <- copyTestFilesToSipi
            response <- getWithoutAuthorization(s"/$prefix/$imageTestfile/full/max/0/default.jp2")
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
          val dspApiPermissionPath = s"/admin/files/$prefix/$imageTestfile"
          for {
            server   <- MockDspApiServer.resetAndStubGetResponse(dspApiPermissionPath, 200, dspApiResponse)
            _        <- copyTestFilesToSipi
            response <- getWithoutAuthorization(s"/$prefix/$imageTestfile/full/max/0/default.jp2")
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
          val dspApiPermissionPath = s"/admin/files/$prefix/$imageTestfile"
          for {
            server   <- MockDspApiServer.resetAndStubGetResponse(dspApiPermissionPath, 404)
            _        <- copyTestFilesToSipi
            response <- getWithoutAuthorization(s"/$prefix/$imageTestfile/full/max/0/default.jp2")
          } yield assertTrue(
            response.status == Status.NotFound,
            verifySingleGetRequest(server, dspApiPermissionPath)
          )
        }
      )
    )

  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("Sipi integration tests with mocked dsp-api")(
      cookiesSuite,
      knoraJsonEndpointSuite,
      fileEndpointSuite,
      iiifEndpoint,
      test("health check works") {
        for {
          server   <- MockDspApiServer.resetAndGetWireMockServer
          response <- getWithoutAuthorization("/server/test.html")
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
      verifyRequest(server, 1, getRequestedFor(urlEqualTo(path))) && server.getAllServeEvents.size() == 1
    def verifyRequest(server: WireMockServer, amount: Int, requestPattern: RequestPatternBuilder): Boolean =
      verifyRequest(server, exactly(amount), requestPattern)
    def verifyRequest(
      server: WireMockServer,
      amount: CountMatchingStrategy,
      requestPattern: RequestPatternBuilder
    ): Boolean =
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

  def resetAndAllowWithPermissionCode(
    prefix: String,
    identifier: String,
    permissionCode: Int
  ): URIO[WireMockServer, WireMockServer] = {
    val dspApiResponse       = SipiFileInfoGetResponseADM(permissionCode, restrictedViewSettings = None)
    val dspApiPermissionPath = s"/admin/files/$prefix/$identifier"
    MockDspApiServer.resetAndStubGetResponse(dspApiPermissionPath, 200, dspApiResponse)
  }

  def verifyAuthBearerTokenReceived(jwt: String): URIO[WireMockServer, Boolean] = ZIO.serviceWithZIO[WireMockServer] {
    mockServer =>
      ZIO
        .attempt(
          mockServer.verify(
            // Number of times the request should be received (in this case, only once)
            1,
            // The expected request with header and value
            newRequestPattern().withHeader("Authorization", equalTo(s"Bearer $jwt"))
          )
        )
        .logError
        .fold(err => false, succ => true)
  }

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
