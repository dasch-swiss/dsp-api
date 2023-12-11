/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.api

import sttp.tapir.server.ziohttp.{ZioHttpInterpreter, ZioHttpServerOptions}
import swiss.dasch.api.ProjectsEndpointsResponses.ProjectResponse
import swiss.dasch.config.Configuration.StorageConfig
import swiss.dasch.domain.*
import swiss.dasch.infrastructure.CommandExecutorLive
import swiss.dasch.test.SpecConstants.Projects.{emptyProject, existingProject, nonExistentProject}
import swiss.dasch.test.{SpecConfigurations, SpecPaths}
import zio.http.*
import zio.json.*
import zio.nio.file.Files
import zio.test.{ZIOSpecDefault, assertTrue}
import zio.{Chunk, UIO, ZIO, http}

object ProjectsEndpointSpec extends ZIOSpecDefault {

  private def executeRequest(request: Request) = for {
    app <- ZIO.serviceWith[ProjectsEndpointsHandler](handler =>
             ZioHttpInterpreter(ZioHttpServerOptions.default).toHttp(handler.endpoints)
           )
    response <- app.runZIO(request).logError
  } yield response

  private val projectExportSuite = {
    def postExport(shortcode: String | ProjectShortcode) = {
      val request = Request
        .post(URL(Root / "projects" / shortcode.toString / "export"), Body.empty)
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
            headers.get("Content-Type").contains("application/zip")
          )
        }
      }
    )
  }

  private val projectImportSuite = {
    val validContentTypeHeaders = Headers(Header.ContentType(MediaType.application.zip))
    val bodyFromZipFile         = Body.fromFile(SpecPaths.testZip.toFile)
    val nonEmptyChunkBody       = Body.fromFile(SpecPaths.testTextFile.toFile)

    def postImport(
      shortcode: String | ProjectShortcode,
      body: Body,
      headers: Headers
    ) = {
      val url     = URL(Root / "projects" / shortcode.toString / "import")
      val request = Request.post(url, body).updateHeaders(_ => headers.addHeader("Authorization", "Bearer fakeToken"))
      executeRequest(request)
    }

    def validateImportedProjectExists(
      storageConfig: StorageConfig,
      shortcode: String | ProjectShortcode
    ): UIO[Boolean] = {
      val expectedFiles = List("info", "jp2", "jp2.orig").map("FGiLaT4zzuV-CqwbEDFAFeS." + _)
      val projectPath   = storageConfig.assetPath / shortcode.toString
      ZIO.foreach(expectedFiles)(file => Files.exists(projectPath / file)).map(_.forall(identity))
    }

    suite("POST /projects/{shortcode}/import should")(
      test("given the shortcode is invalid, return 400")(for {
        response <- postImport("invalid-shortcode", bodyFromZipFile, validContentTypeHeaders)
        status    = response.status
      } yield {
        assertTrue(status == Status.BadRequest)
      }),
      test("given the Content-Type header is invalid, return correct error")(
        for {
          responseWrongHeader <-
            postImport(emptyProject, bodyFromZipFile, Headers(Header.ContentType(MediaType.application.json)))
          status = responseWrongHeader.status
        } yield {
          assertTrue(status == Status.UnsupportedMediaType)
        }
      ),
      test("given the Content-Type header is not-present, return correct error")(
        for {
          responseNoHeader <- postImport(existingProject, bodyFromZipFile, Headers.empty)
          status            = responseNoHeader.status
        } yield {
          assertTrue(status == Status.BadRequest)
        }
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
          response <- postImport(
                        emptyProject,
                        bodyFromZipFile,
                        validContentTypeHeaders
                      )
          importExists <- Files.isDirectory(storageConfig.assetPath / emptyProject.toString)
                            && Files.isDirectory(storageConfig.assetPath / emptyProject.toString / "fg")
          status = response.status
        } yield {
          assertTrue(status == Status.Ok, importExists)
        }
      ),
      test("given the Body is not a zip, will return 400") {
        for {
          storageConfig      <- ZIO.service[StorageConfig]
          response           <- postImport(emptyProject, nonEmptyChunkBody, validContentTypeHeaders)
          importDoesNotExist <- validateImportedProjectExists(storageConfig, emptyProject).map(!_)
          status              = response.status
        } yield {
          assertTrue(status == Status.BadRequest, importDoesNotExist)
        }
      }
    )
  }

  val spec = suite("ProjectsEndpoint")(
    projectExportSuite,
    projectImportSuite,
    test("GET /projects should list non-empty project in test folders") {
      val req = Request.get(URL(Root / "projects")).addHeader("Authorization", "Bearer fakeToken")
      for {
        response <- executeRequest(req)
        body     <- response.body.asString
        status    = response.status
      } yield {
        assertTrue(
          status == Status.Ok,
          body == Chunk(ProjectResponse("0001")).toJson
        )
      }
    }
  ).provide(
    AssetInfoServiceLive.layer,
    AuthServiceLive.layer,
    BaseEndpoints.layer,
    BulkIngestServiceLive.layer,
    CommandExecutorLive.layer,
    FileChecksumServiceLive.layer,
    StillImageServiceLive.layer,
    ImportServiceLive.layer,
    IngestService.layer,
    MovingImageService.layer,
    ProjectServiceLive.layer,
    ProjectsEndpoints.layer,
    ProjectsEndpointsHandler.layer,
    ReportServiceLive.layer,
    SipiClientMock.layer,
    SpecConfigurations.ingestConfigLayer,
    SpecConfigurations.jwtConfigDisableAuthLayer,
    SpecConfigurations.sipiConfigLayer,
    SpecConfigurations.storageConfigLayer,
    StorageServiceLive.layer
  )
}
