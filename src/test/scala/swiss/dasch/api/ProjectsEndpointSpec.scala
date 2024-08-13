/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.api

import sttp.tapir.server.ziohttp.ZioHttpInterpreter
import sttp.tapir.server.ziohttp.ZioHttpServerOptions
import swiss.dasch.api.ProjectsEndpointsResponses.AssetInfoResponse
import swiss.dasch.api.ProjectsEndpointsResponses.ProjectResponse
import swiss.dasch.config.Configuration.Features
import swiss.dasch.config.Configuration.StorageConfig
import swiss.dasch.domain.AugmentedPath.Conversions.given_Conversion_AugmentedPath_Path
import swiss.dasch.domain.*
import swiss.dasch.infrastructure.CommandExecutorLive
import swiss.dasch.test.SpecConfigurations
import swiss.dasch.test.SpecConstants.Projects.emptyProject
import swiss.dasch.test.SpecConstants.Projects.existingProject
import swiss.dasch.test.SpecConstants.Projects.nonExistentProject
import swiss.dasch.test.SpecPaths
import swiss.dasch.util.TestUtils
import zio.Chunk
import zio.UIO
import zio.ZIO
import zio.ZLayer
import zio.http
import zio.http.*
import zio.json.*
import zio.nio.file.Files
import zio.test.ZIOSpecDefault
import zio.test.assertTrue

import java.net.URLDecoder
import java.text.Normalizer

object ProjectsEndpointSpec extends ZIOSpecDefault {

  private def executeRequest(request: Request) = for {
    app <- ZIO.serviceWith[ProjectsEndpointsHandler](handler =>
             ZioHttpInterpreter(ZioHttpServerOptions.default).toHttp(handler.endpoints),
           )
    response <- app.runZIO(request).logError
  } yield response

  private val projectExportSuite = {
    def postExport(shortcode: String | ProjectShortcode) = {
      val request = Request
        .post(URL(Path.root / "projects" / shortcode.toString / "export"), Body.empty)
        .updateHeaders(_.addHeader("Authorization", "Bearer fakeToken"))
      executeRequest(request)
    }
    suite("POST /projects/{shortcode}/export should,")(
      test("given the project does not exist, return 404") {
        for {
          response <- postExport(nonExistentProject)
          status    = response.status
        } yield {
          assertTrue(status == Status.NotFound)
        }
      },
      test("given the project shortcode is invalid, return 400") {
        for {
          response <- postExport("invalid-short-code")
          status    = response.status
        } yield {
          assertTrue(status == Status.BadRequest)
        }
      },
      test("given the project is valid, return 200 with correct headers") {
        for {
          response <- postExport(existingProject)
          status    = response.status
          headers   = response.headers
        } yield {
          assertTrue(
            status == Status.Ok,
            headers
              .get("Content-Disposition")
              .contains(s"attachment; filename=export-${existingProject.toString}.zip"),
            headers.get("Content-Type").contains("application/zip"),
          )
        }
      },
    )
  }

  private val projectImportSuite = {
    val validContentTypeHeaders = Headers(Header.ContentType(MediaType.application.zip))
    val bodyFromZipFile         = Body.fromFile(SpecPaths.testZip.toFile)
    val nonEmptyChunkBody       = Body.fromFile(SpecPaths.testTextFile.toFile)

    def postImport(
      shortcode: String | ProjectShortcode,
      body: Body,
      headers: Headers,
    ) = {
      val url     = URL(Path.root / "projects" / shortcode.toString / "import")
      val request = Request.post(url, body).updateHeaders(_ => headers.addHeader("Authorization", "Bearer fakeToken"))
      executeRequest(request)
    }

    def validateImportedProjectExists(
      storageConfig: StorageConfig,
      shortcode: String | ProjectShortcode,
    ): UIO[Boolean] = {
      val expectedFiles = List("info", "jp2", "jp2.orig").map("FGiLaT4zzuV-CqwbEDFAFeS." + _)
      val projectPath   = storageConfig.assetPath / shortcode.toString
      ZIO.foreach(expectedFiles)(file => Files.exists(projectPath / file)).map(_.forall(identity))
    }

    suite("POST /projects/{shortcode}/import should")(
      test("given the shortcode is invalid, return 400")(for {
        body     <- bodyFromZipFile
        response <- postImport("invalid-shortcode", body, validContentTypeHeaders)
        status    = response.status
      } yield {
        assertTrue(status == Status.BadRequest)
      }),
      test("given the Content-Type header is invalid, return correct error")(
        for {
          body                <- bodyFromZipFile
          responseWrongHeader <- postImport(emptyProject, body, Headers(Header.ContentType(MediaType.application.json)))
          status               = responseWrongHeader.status
        } yield {
          assertTrue(status == Status.UnsupportedMediaType)
        },
      ),
      test("given the Content-Type header is not-present, return correct error")(
        for {
          body             <- bodyFromZipFile
          responseNoHeader <- postImport(existingProject, body, Headers.empty)
          status            = responseNoHeader.status
        } yield {
          assertTrue(status == Status.BadRequest)
        },
      ),
      test("given the Body is empty, return 400")(for {
        response <- postImport(emptyProject, Body.empty, validContentTypeHeaders)
        status    = response.status
      } yield {
        assertTrue(status == Status.BadRequest)
      }),
      test("given the Body is a zip, return 200")(
        for {
          storageConfig <- ZIO.service[StorageConfig]
          body          <- bodyFromZipFile
          response      <- postImport(emptyProject, body, validContentTypeHeaders)
          importExists <- Files.isDirectory(storageConfig.assetPath / emptyProject.toString)
                            && Files.isDirectory(storageConfig.assetPath / emptyProject.toString / "fg")
          status = response.status
        } yield {
          assertTrue(status == Status.Ok, importExists)
        },
      ),
      test("given the Body is not a zip, will return 400") {
        for {
          storageConfig      <- ZIO.service[StorageConfig]
          body               <- nonEmptyChunkBody
          response           <- postImport(emptyProject, body, validContentTypeHeaders)
          importDoesNotExist <- validateImportedProjectExists(storageConfig, emptyProject).map(!_)
          status              = response.status
        } yield {
          assertTrue(status == Status.BadRequest, importDoesNotExist)
        }
      },
    )
  }

  private val assetInfoSuite =
    suite("/projects/<shortcode>/asset/<assetId>")(
      test("given the project folder does not exist should return Not Found") {
        val req = Request
          .get(URL(Path.root / "projects" / "0666" / "assets" / "7l5QJAtPnv5-lLmBPfO7U40"))
          .addHeader("Authorization", "Bearer fakeToken")
        executeRequest(req).map(response => assertTrue(response.status == Status.NotFound))
      },
      test("given the project folder exists but the asset info file does not exist should return Not Found") {
        val req = Request
          .get(URL(Path.root / "projects" / "0666" / "assets" / "7l5QJAtPnv5-lLmBPfO7U40"))
          .addHeader("Authorization", "Bearer fakeToken")
        StorageService
          .getProjectFolder(ProjectShortcode.unsafeFrom("0666"))
          .tap(StorageService.createDirectories(_)) *>
          executeRequest(req).map(response => assertTrue(response.status == Status.NotFound))
      },
      test("given a basic asset info file exists it should return the info") {
        for {
          ref <- AssetInfoFileTestHelper.createInfoFile("txt", "txt").map(_.assetRef)
          req = Request
                  .get(URL(Path.root / "projects" / ref.belongsToProject.value / "assets" / ref.id.value))
                  .addHeader("Authorization", "Bearer fakeToken")
          // when
          response <- executeRequest(req)
          // then
          body <- response.body.asString
          info  = body.fromJson[AssetInfoResponse].getOrElse(throw new Exception("Invalid response"))
        } yield assertTrue(
          response.status == Status.Ok,
          info == AssetInfoResponse(
            internalFilename = s"${ref.id}.txt",
            originalInternalFilename = s"${ref.id}.txt.orig",
            originalFilename = "test.txt",
            checksumOriginal = AssetInfoFileTestHelper.testChecksumOriginal.value,
            checksumDerivative = AssetInfoFileTestHelper.testChecksumDerivative.value,
          ),
        )
      },
      test("given a still image asset info file exists it should return the info") {
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
          req = Request
                  .get(URL(Path.root / "projects" / ref.belongsToProject.value / "assets" / ref.id.value))
                  .addHeader("Authorization", "Bearer fakeToken")
          // when
          response <- executeRequest(req)
          // then
          body <- response.body.asString
          info  = body.fromJson[AssetInfoResponse].getOrElse(throw new Exception("Invalid response"))
        } yield assertTrue(
          response.status == Status.Ok,
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
      test("given a moving image asset info file exists it should return the info") {
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
          req = Request
                  .get(URL(Path.root / "projects" / ref.belongsToProject.value / "assets" / ref.id.value))
                  .addHeader("Authorization", "Bearer fakeToken")
          // when
          response <- executeRequest(req)
          // then
          body <- response.body.asString
          info  = body.fromJson[AssetInfoResponse].getOrElse(throw new Exception("Invalid response"))
        } yield assertTrue(
          response.status == Status.Ok,
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
    )

  private val assetIngestSuite =
    suite("/projects/<shortcode>/asset/ingest/<filename>.mp3")(
      test("should ingest successfully") {
        val req = Request
          .post(URL(Path.root / "projects" / "0666" / "assets" / "ingest" / "sample.mp3"), Body.fromString("tegxd"))
          .addHeader("Authorization", "Bearer fakeToken")
        executeRequest(req).map(response => assertTrue(response.status == Status.Ok))
      },
      test("should handle ingest denormalized filenames") {
        val encoded     = "a%CC%84.mp3"
        val decoded     = URLDecoder.decode(encoded, "UTF-8")
        val decodedNorm = Normalizer.normalize(decoded, Normalizer.Form.NFC)

        val url = URL(Path.root / "projects" / "0666" / "assets" / "ingest" / encoded)
        val req = Request
          .post(url, Body.fromString("tegxd"))
          .addHeader("Authorization", "Bearer fakeToken")

        executeRequest(req).map { response =>
          assertTrue(response.status == Status.Ok) && assertTrue(decodedNorm == "ā.mp3")
        }
      },
      test("should refuse ingesting without content") {
        val req = Request
          .post(URL(Path.root / "projects" / "0666" / "assets" / "ingest" / "sample.mp3"), Body.empty)
          .addHeader("Authorization", "Bearer fakeToken")
        executeRequest(req).map(response => assertTrue(response.status.isClientError))
      },
    )

  private val projectsSuite = suite("/admin/projects/{shortcode}")(
    test("DELETE ./erase should delete the project folder") {
      val shortcode = ProjectShortcode.unsafeFrom("1111")
      for {
        prjFolder <- StorageService.getProjectFolder(shortcode)
        // given a project folder with a file exists
        assetFolder = prjFolder / "as" / "df"
        _          <- Files.createDirectories(assetFolder)
        _          <- Files.createFile(assetFolder / "asdf-test.txt")
        // when deleting the project via the api
        res <- executeRequest(
                 Request
                   .delete(URL(Path.root / "projects" / s"${shortcode.value}" / "erase"))
                   .addHeader("Authorization", "Bearer fakeToken"),
               )
        prjFolderWasDeleted <- Files.exists(prjFolder).negate
      } yield assertTrue(res.status == Status.Ok, prjFolderWasDeleted)
    },
  )

  val projectShouldListTest =
    test("GET /projects should list non-empty project in test folders") {
      val req = Request.get(URL(Path.root / "projects")).addHeader("Authorization", "Bearer fakeToken")
      for {
        response <- executeRequest(req)
        body     <- response.body.asString
        status    = response.status
      } yield {
        assertTrue(
          status == Status.Ok,
          body == Chunk(ProjectResponse("0001")).toJson,
        )
      }
    }

  val spec = suite("ProjectsEndpoint")(
    projectExportSuite,
    projectImportSuite,
    assetInfoSuite,
    assetIngestSuite,
    projectsSuite,
    projectShouldListTest,
  ).provide(
    AssetInfoServiceLive.layer,
    AuthServiceLive.layer,
    AuthorizationHandlerLive.layer,
    BaseEndpoints.layer,
    BulkIngestService.layer,
    CsvService.layer,
    CommandExecutorLive.layer,
    FileChecksumServiceLive.layer,
    ZLayer.succeed(Features(allowEraseProjects = true)),
    StillImageService.layer,
    ImportServiceLive.layer,
    IngestService.layer,
    MimeTypeGuesser.layer,
    MovingImageService.layer,
    OtherFilesService.layer,
    ProjectService.layer,
    ProjectRepositoryLive.layer,
    ProjectsEndpoints.layer,
    ProjectsEndpointsHandler.layer,
    ReportService.layer,
    SipiClientMock.layer,
    SpecConfigurations.ingestConfigLayer,
    SpecConfigurations.jwtConfigDisableAuthLayer,
    SpecConfigurations.sipiConfigLayer,
    SpecConfigurations.storageConfigLayer,
    StorageServiceLive.layer,
    TestUtils.testDbLayerWithEmptyDb,
  )
}
