/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ingest

import org.junit.runner.RunWith
import sttp.client4.*
import sttp.client4.httpclient.zio.HttpClientZioBackend
import sttp.model.StatusCode
import sttp.model.Uri
import swiss.dasch.api.ProjectsEndpointsResponses.AssetInfoResponse
import zio.*
import zio.json.*
import zio.json.ast.Json
import zio.test.*

import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import javax.imageio.ImageIO

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.E2EZSpec
import org.knora.webapi.config.KnoraApi
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.models.filemodels.FileType
import org.knora.webapi.models.filemodels.UploadFileRequest
import org.knora.webapi.sharedtestdata.SharedTestDataADM.*
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.testservices.ResponseOps.*
import org.knora.webapi.testservices.TestApiClient

@RunWith(classOf[DspZTestJUnitRunner])
class IngestEndpointsE2ESpec extends E2EZSpec {

  override def rdfDataObjects: List[RdfDataObject] = List(anythingRdfOntology)

  private val shortcode = anythingShortcode.value

  private def url(path: String) = ZIO.serviceWith[KnoraApi](api => Uri.unsafeParse(api.externalKnoraApiBaseUrl + path))

  private def send[T](request: Request[T]) =
    ZIO.scoped(HttpClientZioBackend.scoped().flatMap(request.send(_)))

  private def withToken[T](request: Request[T], token: Option[String]) = token.fold(request)(request.auth.bearer)

  private def bearer(user: User) = ZIO.serviceWithZIO[TestApiClient](_.jwtFor(user))

  private def upload(filename: String, bytes: Array[Byte], token: Option[String]) =
    url(s"/ingest/projects/$shortcode/assets/ingest/$filename").flatMap { uri =>
      val request =
        basicRequest.post(uri).body(bytes).contentType("application/octet-stream").response(asStringAlways)
      send(withToken(request, token))
    }

  private def uploadAs(user: User, filename: String, bytes: Array[Byte]) =
    for {
      token    <- bearer(user)
      response <- upload(filename, bytes, Some(token))
      info     <-
        ZIO.fromEither(response.body.fromJson[AssetInfoResponse]).mapError(e => Exception(s"$e: ${response.body}"))
    } yield (response.code, info)

  private def get(path: String, token: Option[String]) =
    url(path).flatMap(uri => send(withToken(basicRequest.get(uri).response(asStringAlways), token)))

  private val jpeg: UIO[Array[Byte]] = ZIO.succeed {
    val out = ByteArrayOutputStream()
    ImageIO.write(BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB), "jpeg", out)
    out.toByteArray
  }

  private def sha256(bytes: Array[Byte]): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).map("%02x".format(_)).mkString

  private val restrictedPermissions = "CR knora-admin:Creator|M knora-admin:ProjectMember"

  private def createRestrictedPicture(internalFilename: String) = {
    val jsonLd = UploadFileRequest
      .make(FileType.StillImageFile(), internalFilename)
      .toJsonLd(className = Some("ThingPicture"), ontologyName = "anything")
      .replace(
        s""""knora-api:fileValueHasFilename" : "$internalFilename"""",
        s""""knora-api:fileValueHasFilename" : "$internalFilename", "knora-api:hasPermissions" : "$restrictedPermissions"""",
      )
    TestApiClient.postJsonLd(uri"/v2/resources", jsonLd, anythingAdminUser).flatMap(_.assert200)
  }

  val e2eSpec = suite("IngestEndpointsE2ESpec")(
    test("upload an image with a login token and read its asset info back") {
      for {
        image          <- jpeg
        (status, info) <- uploadAs(anythingAdminUser, "picture.jpg", image)
        assetId         = info.internalFilename.takeWhile(_ != '.')
        token          <- bearer(anythingAdminUser)
        readBack       <- get(s"/ingest/projects/$shortcode/assets/$assetId", Some(token))
      } yield assertTrue(
        status == StatusCode.Ok,
        info.originalFilename == "picture.jpg",
        info.internalFilename.endsWith(".jpx"),
        readBack.code == StatusCode.Ok,
        readBack.body.fromJson[AssetInfoResponse] == Right(info),
      )
    },
    test("refuse an upload without a token with 401, and one without write scope with ingest's body") {
      for {
        image      <- jpeg
        anonymous  <- upload("picture.jpg", image, None)
        otherToken <- bearer(incunabulaMemberUser)
        noScope    <- upload("picture.jpg", image, Some(otherToken))
      } yield assertTrue(
        anonymous.code == StatusCode.Unauthorized,
        noScope.code == StatusCode.Unauthorized,
        noScope.body == """{"reason":"No project access."}""",
      )
    },
    test("upload a file above 1 MiB with Content-Length and keep its checksum") {
      for {
        bytes          <- Random.nextBytes(3 * 1024 * 1024).map(_.toArray)
        (status, info) <- uploadAs(anythingAdminUser, "large.zip", bytes)
      } yield assertTrue(status == StatusCode.Ok, info.checksumOriginal == sha256(bytes))
    },
    test("refuse the removed ImportProjectsToDb maintenance action with 400") {
      for {
        token    <- ZIO.serviceWithZIO[TestApiClient](_.jwtForRoot())
        uri      <- url("/ingest/maintenance/ImportProjectsToDb")
        response <- send(basicRequest.post(uri).auth.bearer(token).response(asStringAlways))
      } yield assertTrue(response.code == StatusCode.BadRequest)
    },
    test("answer /ingest/health with 200 and /ingest/info with the documented fields") {
      for {
        health <- get("/ingest/health", None)
        info   <- get("/ingest/info", None)
        fields  = info.body.fromJson[Json].toOption.flatMap(_.asObject).map(_.keys.toSet)
      } yield assertTrue(
        health.code == StatusCode.Ok,
        health.body.fromJson[Json] == """{"status":"UP"}""".fromJson[Json],
        info.code == StatusCode.Ok,
        fields.contains(Set("name", "version", "scalaVersion", "sbtVersion", "buildTime", "gitCommit")),
        info.body.contains(""""name":"dsp-ingest""""),
      )
    },
    test("serve the original of a restricted asset to a permitted user and refuse it to an anonymous caller") {
      for {
        image     <- jpeg
        (_, info) <- uploadAs(anythingAdminUser, "restricted.jpg", image)
        _         <- createRestrictedPicture(info.internalFilename)
        assetId    = info.internalFilename.takeWhile(_ != '.')
        path       = s"/ingest/projects/$shortcode/assets/$assetId/original"
        token     <- bearer(anythingAdminUser)
        uri       <- url(path)
        permitted <- send(basicRequest.get(uri).response(asByteArrayAlways).auth.bearer(token))
        anonymous <- get(path, None)
      } yield assertTrue(
        permitted.code == StatusCode.Ok,
        permitted.body.sameElements(image),
        anonymous.code == StatusCode.Forbidden,
        anonymous.body == """{"reason":"permission denied"}""",
      )
    },
  )
}
