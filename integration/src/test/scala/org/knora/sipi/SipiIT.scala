/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.sipi

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.CountMatchingStrategy
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.options
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder.newRequestPattern
import zio.*
import zio.http.*
import zio.json.DecoderOps
import zio.json.ast.Json
import zio.test.*

import scala.util.Failure
import scala.util.Success
import scala.util.Try

import org.knora.sipi.MockDspApiServer.verify.*
import org.knora.webapi.config.AppConfig
import org.knora.webapi.messages.admin.responder.projectsmessages.PermissionCodeAndProjectRestrictedViewSettings
import org.knora.webapi.messages.util.KnoraSystemInstances.Users.SystemUser
import org.knora.webapi.routing.JwtService
import org.knora.webapi.routing.JwtServiceLive
import org.knora.webapi.testcontainers.SharedVolumes
import org.knora.webapi.testcontainers.SipiTestContainer

object SipiIT extends ZIOSpecDefault {

  private val imageTestfile = "FGiLaT4zzuV-CqwbEDFAFeS.jp2"
  private val prefix        = "0001"

  private def getWithoutAuthorization(path: Path) =
    SipiTestContainer
      .resolveUrl(path)
      .tap(url => Console.printLine(s"SIPI URL resolved: GET $url"))
      .map(Request.get)
      .flatMap(Client.request(_))

  private val getToken =
    ZIO
      .serviceWithZIO[JwtService](_.createJwt(SystemUser))
      .map(_.jwtString)
      .provide(JwtServiceLive.layer, AppConfig.layer)

  private val cookiesSuite =
    suite("Given a request is authorized using cookies")(
      test(
        "And Given the request contains multiple cookies " +
          "When getting an existing file, " +
          "then Sipi should extract the correct cookie, send it to dsp-api " +
          "and responds with Ok",
      ) {
        for {
          jwt <- getToken
          _   <- MockDspApiServer.resetAndAllowWithPermissionCode(prefix, imageTestfile, 2)
          response <-
            SipiTestContainer
              .resolveUrl(Root / prefix / imageTestfile / "file")
              .map { url =>
                Request
                  .get(url)
                  .addHeaders(
                    Headers(
                      Header.Cookie(
                        NonEmptyChunk(
                          Cookie.Request(
                            s"KnoraAuthenticationGAXDALRQFYYDUMZTGMZQ9999aSecondCookie",
                            "anotherValueShouldBeIgnored",
                          ),
                          Cookie.Request("KnoraAuthenticationGAXDALRQFYYDUMZTGMZQ9999", jwt),
                        ),
                      ),
                    ),
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
          "and responds with Ok",
      ) {
        for {
          jwt <- getToken
          _   <- MockDspApiServer.resetAndAllowWithPermissionCode(prefix, imageTestfile, 2)
          response <-
            SipiTestContainer
              .resolveUrl(Root / prefix / imageTestfile / "file")
              .map(url =>
                Request
                  .get(url)
                  .addHeaders(
                    Headers(
                      Header.Cookie(NonEmptyChunk(Cookie.Request("KnoraAuthenticationGAXDALRQFYYDUMZTGMZQ9999", jwt))),
                    ),
                  ),
              )
              .flatMap(Client.request(_))
          requestToDspApiContainsJwt <- MockDspApiServer.verifyAuthBearerTokenReceived(jwt)
        } yield assertTrue(response.status == Status.Ok, requestToDspApiContainsJwt)
      },
    ) @@ TestAspect.withLiveClock

  private val knoraJsonEndpointSuite =
    suite("Endpoint /{prefix}/{identifier}/knora.json")(
      suite("Given the user is unauthorized")(
        suite("And given a .info file exists in Sipi")(
          test(
            "And given dsp-api returns 2='full view permissions on file', " +
              "when getting the file, " +
              "then Sipi responds with Ok",
          ) {
            def expectedJson(port: Int, host: String) =
              s"""{
                 |  "@context":"http://sipi.io/api/file/3/context.json",
                 |  "id":"http://$host:$port/0001/FGiLaT4zzuV-CqwbEDFAFeS.jp2",
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
              response <- getWithoutAuthorization(Root / prefix / imageTestfile / "knora.json")
              json     <- response.body.asString.map(_.fromJson[Json])
              expected <- SipiTestContainer.portAndHost.map { case (port, host) => expectedJson(port, host) }
            } yield assertTrue(
              response.status == Status.Ok,
              json == expected,
            )
          },
        ),
      ),
    )

  private val fileEndpointSuite =
    suite("Endpoint /{prefix}/{identifier}/file")(
      suite("Given the user is unauthorized")(
        suite("Given a file does not exist in Sipi")(
          test("When getting the file, then Sipi responds with Not Found") {
            for {
              server   <- MockDspApiServer.resetAndGetWireMockServer
              response <- getWithoutAuthorization(Root / prefix / "doesnotexist.jp2" / "file")
            } yield assertTrue(response.status == Status.NotFound, verifyNoInteractionWith(server))
          },
        ),
        suite("Given an image exists in Sipi")(
          test(
            "And given dsp-api returns 2='full view permissions on file', " +
              "when getting the file, " +
              "then Sipi responds with Ok",
          ) {
            val dspApiResponse =
              PermissionCodeAndProjectRestrictedViewSettings(permissionCode = 2, restrictedViewSettings = None)
            val dspApiPermissionPath = s"/admin/files/$prefix/$imageTestfile"
            for {
              server   <- MockDspApiServer.resetAndStubGetResponse(dspApiPermissionPath, 200, dspApiResponse)
              response <- getWithoutAuthorization(Root / prefix / imageTestfile / "file")
            } yield assertTrue(
              response.status == Status.Ok,
              verifySingleGetRequest(server, dspApiPermissionPath),
            )
          },
          test(
            "And given dsp-api returns 0='no view permission on file', " +
              "when getting the file, " +
              "then Sipi responds with Unauthorized",
          ) {
            val dspApiResponse =
              PermissionCodeAndProjectRestrictedViewSettings(permissionCode = 0, restrictedViewSettings = None)
            val dspApiPermissionPath = s"/admin/files/$prefix/$imageTestfile"
            for {
              server   <- MockDspApiServer.resetAndStubGetResponse(dspApiPermissionPath, 200, dspApiResponse)
              response <- getWithoutAuthorization(Root / prefix / imageTestfile / "file")
            } yield assertTrue(
              response.status == Status.Unauthorized,
              verifySingleGetRequest(server, dspApiPermissionPath),
            )
          },
          test(
            "And given dsp-api does not know this file and returns Not Found, " +
              "when getting the file, returns 2='full view permissions on file'" +
              "then Sipi responds with Not Found",
          ) {
            val dspApiPermissionPath = s"/admin/files/$prefix/$imageTestfile"
            for {
              server   <- MockDspApiServer.resetAndStubGetResponse(dspApiPermissionPath, 404)
              response <- getWithoutAuthorization(Root / prefix / imageTestfile / "file")
            } yield assertTrue(
              response.status == Status.NotFound,
              verifySingleGetRequest(server, dspApiPermissionPath),
            )
          },
        ),
      ),
    )

  private val iiifEndpoint =
    suite("Endpoint {server}/{prefix}/{identifier}/{region}/{size}/{rotation}/{quality}.{format}")(
      suite("Given an image does not exist in Sipi")(
        test(
          "When getting the file, " +
            "then Sipi responds with Not Found",
        ) {
          for {
            server <- MockDspApiServer.resetAndGetWireMockServer
            response <-
              getWithoutAuthorization(Root / prefix / "doesnotexist.jp2" / "full" / "max" / "0" / "default.jp2")
          } yield assertTrue(response.status == Status.NotFound, verifyNoInteractionWith(server))
        },
      ),
      suite("Given an image exists in Sipi")(
        test(
          "And given dsp-api returns 2='full view permissions on file', " +
            "when getting the file, " +
            "Sipi responds with Ok",
        ) {
          val dspApiResponse =
            PermissionCodeAndProjectRestrictedViewSettings(permissionCode = 2, restrictedViewSettings = None)
          val dspApiPermissionPath = s"/admin/files/$prefix/$imageTestfile"
          for {
            server   <- MockDspApiServer.resetAndStubGetResponse(dspApiPermissionPath, 200, dspApiResponse)
            response <- getWithoutAuthorization(Root / prefix / imageTestfile / "full/max/0/default.jp2")
          } yield assertTrue(
            response.status == Status.Ok,
            verifySingleGetRequest(server, dspApiPermissionPath),
          )
        },
        test(
          "And given dsp-api returns 0='full view permissions on file', " +
            "when getting the file, " +
            "Sipi responds with Unauthorized",
        ) {
          val dspApiResponse =
            PermissionCodeAndProjectRestrictedViewSettings(permissionCode = 0, restrictedViewSettings = None)
          val dspApiPermissionPath = s"/admin/files/$prefix/$imageTestfile"
          for {
            server   <- MockDspApiServer.resetAndStubGetResponse(dspApiPermissionPath, 200, dspApiResponse)
            response <- getWithoutAuthorization(Root / prefix / imageTestfile / "full/max/0/default.jp2")
          } yield assertTrue(
            response.status == Status.Unauthorized,
            verifySingleGetRequest(server, dspApiPermissionPath),
          )
        },
        test(
          "And given dsp-api does not know this file and returns Not Found, " +
            "when getting the file, " +
            "Sipi responds with Not Found",
        ) {
          val dspApiPermissionPath = s"/admin/files/$prefix/$imageTestfile"
          for {
            server   <- MockDspApiServer.resetAndStubGetResponse(dspApiPermissionPath, 404)
            response <- getWithoutAuthorization(Root / prefix / imageTestfile / "full/max/0/default.jp2")
          } yield assertTrue(
            response.status == Status.NotFound,
            verifySingleGetRequest(server, dspApiPermissionPath),
          )
        },
      ),
    )

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("Sipi integration tests with mocked dsp-api")(
      cookiesSuite,
      knoraJsonEndpointSuite,
      fileEndpointSuite,
      iiifEndpoint,
      test("health check works") {
        for {
          server   <- MockDspApiServer.resetAndGetWireMockServer
          response <- getWithoutAuthorization(Root / "server" / "test.html")
        } yield assertTrue(response.status.isSuccess, verifyNoInteractionWith(server))
      },
    )
      .provideSomeLayerShared[Scope & Client & WireMockServer](
        SharedVolumes.Images.layer >+> SipiTestContainer.layer,
      )
      .provideSomeLayerShared[Scope & Client](MockDspApiServer.layer)
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
      requestPattern: RequestPatternBuilder,
    ): Boolean =
      Try(server.verify(amount, requestPattern)) match {
        case Failure(e: Throwable) => println(s"\nMockDspApiServer: ${e.getMessage}"); false
        case Success(_)            => true
      }

    def verifyNoInteractionWith(server: WireMockServer): Boolean = server.getAllServeEvents.isEmpty
  }

  def resetAndGetWireMockServer: URIO[WireMockServer, WireMockServer] =
    ZIO.serviceWith[WireMockServer] { it =>
      it.resetAll(); it
    }

  def resetAndStubGetResponse(url: String, status: Int): URIO[WireMockServer, WireMockServer] =
    resetAndGetWireMockServer.tap(server => ZIO.succeed(stubGetJsonResponse(server, url, status)))
  def resetAndStubGetResponse(
    url: String,
    status: Int,
    body: PermissionCodeAndProjectRestrictedViewSettings,
  ): URIO[WireMockServer, WireMockServer] =
    resetAndGetWireMockServer.tap(server => ZIO.succeed(stubGetJsonResponse(server, url, status, Some(body))))

  def resetAndAllowWithPermissionCode(
    prefix: String,
    identifier: String,
    permissionCode: Int,
  ): URIO[WireMockServer, WireMockServer] = {
    val dspApiResponse       = PermissionCodeAndProjectRestrictedViewSettings(permissionCode, restrictedViewSettings = None)
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
            newRequestPattern().withHeader("Authorization", equalTo(s"Bearer $jwt")),
          ),
        )
        .logError
        .fold(_ => false, _ => true)
  }

  private def stubGetJsonResponse(
    server: WireMockServer,
    url: String,
    status: Int,
    body: Option[PermissionCodeAndProjectRestrictedViewSettings] = None,
  ): Unit = {
    val json =
      body.map(it => PermissionCodeAndProjectRestrictedViewSettings.codec.encoder.encodeJson(it).toString).orNull
    val jsonResponse = aResponse().withStatus(status).withBody(json).withHeader("Content-Type", "application/json")
    val stubBuilder  = get(urlEqualTo(url)).willReturn(jsonResponse)
    server.stubFor(stubBuilder)
    ()
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
