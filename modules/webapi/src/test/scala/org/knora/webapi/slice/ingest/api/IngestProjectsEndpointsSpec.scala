/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ingest.api

import org.junit.runner.RunWith
import sttp.tapir.server.ziohttp.ZioHttpInterpreter
import sttp.tapir.server.ziohttp.ZioHttpServerOptions
import zio.*
import zio.http.*
import zio.http.Header.ContentDisposition
import zio.http.Header.ContentDisposition.Attachment
import zio.http.Header.ContentType
import zio.json.*
import zio.nio.file.Files
import zio.test.*

import scala.language.implicitConversions

import org.knora.bagit.BagIt
import org.knora.bagit.domain.Compression
import org.knora.bagit.domain.PayloadEntry
import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.config.DspIngestConfig
import org.knora.webapi.config.JwtConfig
import org.knora.webapi.slice.ingest.api.IngestProjectsEndpointsResponses.AssetInfoResponse
import org.knora.webapi.slice.ingest.api.IngestProjectsEndpointsResponses.ProjectResponse
import org.knora.webapi.slice.ingest.config.IngestFeatures
import org.knora.webapi.slice.ingest.config.StorageConfig
import org.knora.webapi.slice.ingest.domain.*
import org.knora.webapi.slice.ingest.domain.AugmentedPath.Conversions.given_Conversion_AugmentedPath_Path
import org.knora.webapi.slice.ingest.infrastructure.CommandExecutorLive
import org.knora.webapi.slice.ingest.ports.OriginalAccess
import org.knora.webapi.slice.ingest.ports.OriginalAccessInMemory
import org.knora.webapi.slice.ingest.repo.*
import org.knora.webapi.slice.ingest.testutil.SipiClientMock
import org.knora.webapi.slice.ingest.testutil.SpecConfigurations
import org.knora.webapi.slice.ingest.testutil.SpecConstants.Projects.emptyProject
import org.knora.webapi.slice.ingest.testutil.SpecConstants.Projects.existingProject
import org.knora.webapi.slice.ingest.testutil.SpecConstants.Projects.nonExistentProject
import org.knora.webapi.slice.ingest.testutil.SpecJwtTokens
import org.knora.webapi.slice.ingest.testutil.SpecPaths

@RunWith(classOf[DspZTestJUnitRunner])
class IngestProjectsEndpointsSpec extends ZIOSpecDefault {

  private type Env = IngestProjectsServerEndpoints & OriginalAccessInMemory & StorageConfig & StorageService &
    JwtConfig & DspIngestConfig

  private val ingest = Path.root / "ingest" / "projects"

  private def executeRequest(request: Request) = for {
    app <- ZIO.serviceWith[IngestProjectsServerEndpoints](se =>
             ZioHttpInterpreter(ZioHttpServerOptions.default).toHttp(se.serverEndpoints),
           )
    response <- app.runZIO(request).logError
  } yield response

  private def withToken(request: Request, token: String): Request =
    request.addHeader(Header.Authorization.Bearer(token))

  private def asAdmin(request: Request) = SpecJwtTokens.adminToken().map(withToken(request, _))

  private def executeAsAdmin(request: Request) = asAdmin(request).flatMap(executeRequest)

  def testWithScope[E, Err](label: String)(assertion: => ZIO[E & Scope, Err, TestResult]): Spec[E, Err] =
    zio.test.test(label)(ZIO.scoped(assertion))

  private val projectExportSuite = {
    def postExport(shortcode: String | ProjectShortcode) =
      executeAsAdmin(Request.post(URL(ingest / shortcode.toString / "export"), Body.empty))

    suite("POST /ingest/projects/{shortcode}/export should,")(
      testWithScope("given the project does not exist, return 404") {
        postExport(nonExistentProject).map(response => assertTrue(response.status == Status.NotFound))
      },
      testWithScope("given the project shortcode is invalid, return 400") {
        postExport("invalid-short-code").map(response => assertTrue(response.status == Status.BadRequest))
      },
      testWithScope("given the project is valid, return 200 with correct headers") {
        for {
          response <- postExport(existingProject)
          headers   = response.headers
        } yield assertTrue(
          response.status == Status.Ok,
          headers
            .get("Content-Disposition")
            .contains(s"attachment; filename=export-${existingProject.toString}.bagit.zip"),
          headers.get("Content-Type").contains("application/zip"),
        )
      },
      testWithScope("given the token lacks admin scope, return 401") {
        for {
          token    <- SpecJwtTokens.tokenWithScope(s"write:project:${existingProject.value}")
          response <-
            executeRequest(withToken(Request.post(URL(ingest / existingProject.value / "export"), Body.empty), token))
          body <- response.body.asString
        } yield assertTrue(
          response.status == Status.Unauthorized,
          body == """{"reason":"Admin permissions required."}""",
        )
      },
    )
  }

  private val projectImportSuite = {
    val validContentTypeHeaders = Headers(Header.ContentType(MediaType.application.zip))
    val bodyFromZipFile         = Body.fromFile(SpecPaths.testZip.toFile)
    val nonEmptyChunkBody       = Body.fromFile(SpecPaths.testTextFile.toFile)

    def postImport(shortcode: String | ProjectShortcode, body: Body, headers: Headers) =
      executeAsAdmin(Request.post(URL(ingest / shortcode.toString / "import"), body).updateHeaders(_ => headers))

    def validateImportedProjectExists(storageConfig: StorageConfig, shortcode: String | ProjectShortcode) = {
      val expectedFiles = List("info", "jp2", "jp2.orig").map("FGiLaT4zzuV-CqwbEDFAFeS." + _)
      val projectPath   = storageConfig.assetPath / shortcode.toString
      ZIO.foreach(expectedFiles)(file => Files.exists(projectPath / file)).map(_.forall(identity))
    }

    suite("POST /ingest/projects/{shortcode}/import should")(
      testWithScope("given the shortcode is invalid, return 400")(for {
        body     <- bodyFromZipFile
        response <- postImport("invalid-shortcode", body, validContentTypeHeaders)
      } yield assertTrue(response.status == Status.BadRequest)),
      testWithScope("given the Content-Type header is invalid, return correct error")(for {
        body     <- bodyFromZipFile
        response <- postImport(emptyProject, body, Headers(Header.ContentType(MediaType.application.json)))
      } yield assertTrue(response.status == Status.UnsupportedMediaType)),
      testWithScope("given the Content-Type header is not-present, return correct error")(for {
        body     <- bodyFromZipFile
        response <- postImport(existingProject, body, Headers.empty)
      } yield assertTrue(response.status == Status.BadRequest)),
      testWithScope("given the Body is empty, return 400")(
        postImport(emptyProject, Body.empty, validContentTypeHeaders).map(r =>
          assertTrue(r.status == Status.BadRequest),
        ),
      ),
      testWithScope("given the Body is a zip, return 200")(
        for {
          storageConfig <- ZIO.service[StorageConfig]
          tmpDir        <- ZIO.acquireRelease(
                      ZIO
                        .attemptBlockingIO(java.nio.file.Files.createTempDirectory("bagit-test"))
                        .map(zio.nio.file.Path.fromJava),
                    )(p => ZIO.attemptBlockingIO(org.apache.commons.io.FileUtils.deleteDirectory(p.toFile)).ignore)
          bagitZipPath = tmpDir / "test-import.bagit.zip"
          sourceDir    = SpecPaths.testFolder / "0001" / "fg"
          _           <- BagIt.create(
                 List(PayloadEntry.Directory(prefix = "fg", sourcePath = sourceDir, compression = Compression.Deflate)),
                 bagitZipPath,
               )
          body         <- Body.fromFile(bagitZipPath.toFile)
          response     <- postImport(emptyProject, body, validContentTypeHeaders)
          importExists <- Files.isDirectory(storageConfig.assetPath / emptyProject.toString)
                            && Files.isDirectory(storageConfig.assetPath / emptyProject.toString / "fg")
        } yield assertTrue(response.status == Status.Ok, importExists),
      ),
      testWithScope("given the Body is not a zip, will return 400") {
        for {
          storageConfig      <- ZIO.service[StorageConfig]
          body               <- nonEmptyChunkBody
          response           <- postImport(emptyProject, body, validContentTypeHeaders)
          importDoesNotExist <- validateImportedProjectExists(storageConfig, emptyProject).map(!_)
        } yield assertTrue(response.status == Status.BadRequest, importDoesNotExist)
      },
    )
  }

  private def originalRequest(ref: AssetRef) =
    Request.get(URL(ingest / ref.belongsToProject.value / "assets" / ref.id.value / "original"))

  private def createTextAsset = AssetInfoFileTestHelper
    .createInfoFile("txt", "txt", Some(""""originalMimeType": "text/plain""""), Some("123".toList.map(_.toByte)))
    .map(_.assetRef)

  private val assetOriginalSuite =
    suite("/ingest/projects/<shortcode>/assets/<assetId>/original")(
      testWithScope("given the info file does not exist, it should return Not Found") {
        val req = Request.get(URL(ingest / "0666" / "assets" / "7l5QJAtPnv5-lLmBPfO7U40" / "original"))
        executeAsAdmin(req).map(response => assertTrue(response.status == Status.NotFound))
      },
      testWithScope("return the original contents and ask with the caller's token and the derivative's filename") {
        for {
          ref      <- createTextAsset
          token    <- SpecJwtTokens.validToken()
          response <- executeRequest(withToken(originalRequest(ref), token))
          body     <- response.body.asString
          calls    <- ZIO.serviceWithZIO[OriginalAccessInMemory](_.getCalls)
        } yield assertTrue(
          response.status == Status.Ok,
          body == "123",
          response.header(ContentDisposition).get == Attachment(None),
          response.header(ContentType).get.mediaType.fullType == "text/plain",
          calls == List(OriginalAccessInMemory.Call(Some(token), ref.belongsToProject, s"${ref.id}.txt")),
        )
      },
      testWithScope("treat an invalid token as anonymous") {
        for {
          ref      <- createTextAsset
          response <- executeRequest(withToken(originalRequest(ref), "invalid"))
          calls    <- ZIO.serviceWithZIO[OriginalAccessInMemory](_.getCalls)
        } yield assertTrue(response.status == Status.Ok, calls.map(_.token) == List(None))
      },
    )

  private val assetOriginalWithheldSuite =
    suite("/ingest/projects/<shortcode>/assets/<assetId>/original when withheld")(
      testWithScope("refuse the original when dsp-api withholds it") {
        for {
          ref      <- createTextAsset
          response <- executeRequest(originalRequest(ref))
          body     <- response.body.asString
        } yield assertTrue(response.status == Status.Forbidden, body == """{"reason":"permission denied"}""")
      },
    )

  private val assetOriginalFailingSuite =
    suite("/ingest/projects/<shortcode>/assets/<assetId>/original when the check fails")(
      testWithScope("answer 500 when the permission check fails") {
        for {
          ref      <- createTextAsset
          response <- executeRequest(originalRequest(ref))
          body     <- response.body.asString
        } yield assertTrue(
          response.status == Status.InternalServerError,
          body == """{"errorMessage":"error fetching permissions"}""",
        )
      },
    )

  private def assetInfoRequest(ref: AssetRef) =
    Request.get(URL(ingest / ref.belongsToProject.value / "assets" / ref.id.value))

  private def getAssetInfo(ref: AssetRef) = for {
    response <- executeAsAdmin(assetInfoRequest(ref))
    body     <- response.body.asString
  } yield (response.status, body.fromJson[AssetInfoResponse].getOrElse(throw new Exception("Invalid response")))

  private val assetInfoSuite =
    suite("/ingest/projects/<shortcode>/assets/<assetId>")(
      testWithScope("given the project folder does not exist should return Not Found") {
        val req = Request.get(URL(ingest / "0666" / "assets" / "7l5QJAtPnv5-lLmBPfO7U40"))
        executeAsAdmin(req).map(response => assertTrue(response.status == Status.NotFound))
      },
      testWithScope("given the project folder exists but the asset info file does not exist should return Not Found") {
        val req = Request.get(URL(ingest / "0666" / "assets" / "7l5QJAtPnv5-lLmBPfO7U40"))
        StorageService.getProjectFolder(ProjectShortcode.unsafeFrom("0666")).tap(StorageService.createDirectories(_)) *>
          executeAsAdmin(req).map(response => assertTrue(response.status == Status.NotFound))
      },
      testWithScope("given a basic asset info file exists it should return the info") {
        for {
          ref            <- AssetInfoFileTestHelper.createInfoFile("txt", "txt").map(_.assetRef)
          (status, info) <- getAssetInfo(ref)
        } yield assertTrue(
          status == Status.Ok,
          info == AssetInfoResponse(
            internalFilename = s"${ref.id}.txt",
            originalInternalFilename = s"${ref.id}.txt.orig",
            originalFilename = "test.txt",
            checksumOriginal = AssetInfoFileTestHelper.testChecksumOriginal.value,
            checksumDerivative = AssetInfoFileTestHelper.testChecksumDerivative.value,
          ),
        )
      },
      testWithScope("given a still image asset info file exists it should return the info") {
        for {
          ref <- AssetInfoFileTestHelper
                   .createInfoFile(
                     originalFileExt = "png",
                     derivativeFileExt = "jpx",
                     customJsonProps = Some("""
                                              |"width": 640,
                                              |"height": 480,
                                              |"internalMimeType": "image/jpx",
                                              |"originalMimeType": "image/png"
                                              |""".stripMargin),
                   )
                   .map(_.assetRef)
          (status, info) <- getAssetInfo(ref)
        } yield assertTrue(
          status == Status.Ok,
          info == AssetInfoResponse(
            internalFilename = s"${ref.id}.jpx",
            originalInternalFilename = s"${ref.id}.png.orig",
            originalFilename = "test.png",
            checksumOriginal = AssetInfoFileTestHelper.testChecksumOriginal.value,
            checksumDerivative = AssetInfoFileTestHelper.testChecksumDerivative.value,
            width = Some(640),
            height = Some(480),
            internalMimeType = Some("image/jpx"),
            originalMimeType = Some("image/png"),
          ),
        )
      },
      testWithScope("given a moving image asset info file exists it should return the info") {
        for {
          ref <- AssetInfoFileTestHelper
                   .createInfoFile(
                     originalFileExt = "mp4",
                     derivativeFileExt = "mp4",
                     customJsonProps = Some("""
                                              |"width": 640,
                                              |"height": 480,
                                              |"fps": 60,
                                              |"duration": 3.14,
                                              |"internalMimeType": "video/mp4",
                                              |"originalMimeType": "video/mp4"
                                              |""".stripMargin),
                   )
                   .map(_.assetRef)
          (status, info) <- getAssetInfo(ref)
        } yield assertTrue(
          status == Status.Ok,
          info == AssetInfoResponse(
            internalFilename = s"${ref.id}.mp4",
            originalInternalFilename = s"${ref.id}.mp4.orig",
            originalFilename = "test.mp4",
            checksumOriginal = AssetInfoFileTestHelper.testChecksumOriginal.value,
            checksumDerivative = AssetInfoFileTestHelper.testChecksumDerivative.value,
            width = Some(640),
            height = Some(480),
            duration = Some(3.14),
            fps = Some(60),
            internalMimeType = Some("video/mp4"),
            originalMimeType = Some("video/mp4"),
          ),
        )
      },
      testWithScope("given the token lacks read scope for the project, return 401 with ingest's body") {
        for {
          ref      <- AssetInfoFileTestHelper.createInfoFile("txt", "txt").map(_.assetRef)
          token    <- SpecJwtTokens.tokenWithScope("read:project:0002")
          response <- executeRequest(withToken(assetInfoRequest(ref), token))
          body     <- response.body.asString
        } yield assertTrue(response.status == Status.Unauthorized, body == """{"reason":"No project access."}""")
      },
    )

  private def ingestRequest(url: URL, body: Body) = Request.post(url, body)

  private val assetIngestSuite =
    suite("/ingest/projects/<shortcode>/assets/ingest/<filename>")(
      testWithScope("should ingest successfully") {
        val req = ingestRequest(URL(ingest / "0666" / "assets" / "ingest" / "sample.mp3"), Body.fromString("tegxd"))
        executeAsAdmin(req).map(response => assertTrue(response.status == Status.Ok))
      },
      testWithScope("should ingest successfully with a write scope for the project") {
        for {
          token <- SpecJwtTokens.tokenWithScope("write:project:0666")
          req    = ingestRequest(URL(ingest / "0666" / "assets" / "ingest" / "sample.mp3"), Body.fromString("tegxd"))
          resp  <- executeRequest(withToken(req, token))
        } yield assertTrue(resp.status == Status.Ok)
      },
      testWithScope("should handle ingest denormalized filenames") {
        // "a%CC%84.mp3" is "a" + COMBINING MACRON (NFD); URL.decode runs the server's path decoder, and
        // AssetFilename.from must store it as the precomposed NFC "ā.mp3".
        val url =
          URL.decode("/ingest/projects/0666/assets/ingest/a%CC%84.mp3").getOrElse(throw new Exception("Invalid URL"))
        for {
          response <- executeAsAdmin(ingestRequest(url, Body.fromString("tegxd")))
          body     <- response.body.asString
          info      = body.fromJson[AssetInfoResponse].getOrElse(throw new Exception("Invalid response"))
        } yield assertTrue(response.status == Status.Ok, info.originalFilename == "ā.mp3")
      },
      testWithScope("should refuse ingesting without content") {
        val req = ingestRequest(URL(ingest / "0666" / "assets" / "ingest" / "sample.mp3"), Body.empty)
        executeAsAdmin(req).map(response => assertTrue(response.status.isClientError))
      },
      testWithScope("should refuse an invalid token with 401") {
        val req = ingestRequest(URL(ingest / "0666" / "assets" / "ingest" / "sample.mp3"), Body.empty)
        executeRequest(withToken(req, "intentionallyInvalid")).map(r => assertTrue(r.status == Status.Unauthorized))
      },
      testWithScope("should refuse a request without a token with 401") {
        val req = ingestRequest(URL(ingest / "0666" / "assets" / "ingest" / "sample.mp3"), Body.fromString("tegxd"))
        executeRequest(req).map(r => assertTrue(r.status == Status.Unauthorized))
      },
      testWithScope("should refuse a token without write scope for the project with 401 and ingest's body") {
        for {
          token <- SpecJwtTokens.tokenWithScope("read:project:0666 write:project:0001")
          req    = ingestRequest(URL(ingest / "0666" / "assets" / "ingest" / "sample.mp3"), Body.fromString("tegxd"))
          resp  <- executeRequest(withToken(req, token))
          body  <- resp.body.asString
        } yield assertTrue(resp.status == Status.Unauthorized, body == """{"reason":"No project access."}""")
      },
    )

  private val projectsSuite = suite("/ingest/projects/{shortcode}")(
    testWithScope("DELETE ./erase should delete the project folder") {
      val shortcode = ProjectShortcode.unsafeFrom("1111")
      for {
        prjFolder           <- StorageService.getProjectFolder(shortcode)
        assetFolder          = prjFolder / "as" / "df"
        _                   <- Files.createDirectories(assetFolder)
        _                   <- Files.createFile(assetFolder / "asdf-test.txt")
        res                 <- executeAsAdmin(Request.delete(URL(ingest / shortcode.value / "erase")))
        prjFolderWasDeleted <- Files.exists(prjFolder).negate
      } yield assertTrue(res.status == Status.Ok, prjFolderWasDeleted)
    },
    testWithScope("GET should answer the project for a read scope") {
      for {
        token <- SpecJwtTokens.tokenWithScope("read:project:0001")
        resp  <- executeRequest(withToken(Request.get(URL(ingest / "0001")), token))
        body  <- resp.body.asString
      } yield assertTrue(resp.status == Status.Ok, body == ProjectResponse("0001").toJson)
    },
  )

  private val projectEraseDisabledSuite = suite("/ingest/projects/{shortcode}/erase when disabled")(
    testWithScope("DELETE ./erase should be forbidden and keep the project folder") {
      for {
        res    <- executeAsAdmin(Request.delete(URL(ingest / existingProject.value / "erase")))
        body   <- res.body.asString
        folder <- StorageService.getProjectFolder(existingProject)
        exists <- Files.exists(folder)
      } yield assertTrue(
        res.status == Status.Forbidden,
        body == """{"reason":"The feature to erase projects is not enabled."}""",
        exists,
      )
    },
  )

  private val projectShouldListTest =
    testWithScope("GET /ingest/projects should list non-empty project in test folders") {
      for {
        response <- executeAsAdmin(Request.get(URL(ingest)))
        body     <- response.body.asString
      } yield assertTrue(response.status == Status.Ok, body == Chunk(ProjectResponse("0001")).toJson)
    }

  private def env(originalAccess: ULayer[OriginalAccessInMemory & OriginalAccess], allowErase: Boolean) =
    ZLayer.make[Env](
      AssetInfoServiceLive.layer,
      BulkIngestService.layer,
      CommandExecutorLive.layer,
      CsvService.layer,
      FileChecksumServiceLive.layer,
      ImportServiceLive.layer,
      IngestAuthenticator.layer,
      IngestBaseEndpoints.layer,
      IngestProjectsEndpoints.layer,
      IngestProjectsRestService.layer,
      IngestProjectsServerEndpoints.layer,
      IngestService.layer,
      MimeTypeGuesser.layer,
      MovingImageService.layer,
      originalAccess,
      OtherFilesService.layer,
      ProjectService.layer,
      ReportService.layer,
      SipiClientMock.layer,
      SpecConfigurations.ingestConfigLayer,
      SpecConfigurations.jwtConfigLayer,
      SpecConfigurations.sipiConfigLayer,
      SpecConfigurations.storageConfigLayer,
      StillImageService.layer,
      StorageServiceLive.layer,
      ZLayer.succeed(IngestFeatures(allowEraseProjects = allowErase)),
    )

  val spec = suite("IngestProjectsEndpoints")(
    suite("with original access granted")(
      projectExportSuite,
      projectImportSuite,
      assetInfoSuite,
      assetIngestSuite,
      assetOriginalSuite,
      projectsSuite,
      projectShouldListTest,
    ).provideLayer(env(OriginalAccessInMemory.grantAll, allowErase = true).orDie),
    assetOriginalWithheldSuite.provideLayer(env(OriginalAccessInMemory.withholdAll, allowErase = true).orDie),
    assetOriginalFailingSuite.provideLayer(env(OriginalAccessInMemory.failAll, allowErase = true).orDie),
    projectEraseDisabledSuite.provideLayer(env(OriginalAccessInMemory.grantAll, allowErase = false).orDie),
  ) @@ TestAspect.withLiveClock

}
