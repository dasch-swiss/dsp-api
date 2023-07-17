/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.api

import swiss.dasch.config.Configuration.StorageConfig
import swiss.dasch.domain.*
import swiss.dasch.test.SpecConstants.Projects.*
import swiss.dasch.test.{ SpecConfigurations, SpecConstants, SpecPaths }
import zio.http.*
import zio.nio.file.Files
import zio.test.{ ZIOSpecDefault, assertTrue }
import zio.{ UIO, ZIO }

object ImportEndpointSpec extends ZIOSpecDefault {

  private val validContentTypeHeaders = Headers(Header.ContentType(MediaType.application.zip))
  private val bodyFromZipFile         = Body.fromFile(SpecPaths.testZip.toFile)
  private val nonEmptyChunkBody       = Body.fromFile(SpecPaths.testTextFile.toFile)

  private def postImport(
      shortcode: String | ProjectShortcode,
      body: Body,
      headers: Headers,
    ) = {
    val url = URL(Root / "projects" / shortcode.toString / "import")
    ImportEndpoint.app.runZIO(Request.post(body, url).updateHeaders(_ => headers))
  }

  val spec = suite("ImportEndpoint")(
    suite("POST on /projects/{shortcode}/import should")(
      test("given the shortcode is invalid, return 400")(for {
        response <- postImport("invalid-shortcode", bodyFromZipFile, validContentTypeHeaders)
      } yield assertTrue(response.status == Status.BadRequest)),
      test("given the Content-Type header is invalid/not-present, return 400")(
        for {
          responseNoHeader    <- postImport(existingProject, bodyFromZipFile, Headers.empty)
          responseWrongHeader <-
            postImport(emptyProject, bodyFromZipFile, Headers(Header.ContentType(MediaType.application.json)))
        } yield assertTrue(
          responseNoHeader.status == Status.BadRequest,
          responseWrongHeader.status == Status.BadRequest,
        )
      ),
      test("given the Body is empty, return 400")(for {
        response <- postImport(emptyProject, Body.empty, validContentTypeHeaders)
      } yield assertTrue(response.status == Status.BadRequest)),
      test("given the Body is a zip, return 200")(
        for {
          storageConfig <- ZIO.service[StorageConfig]
          response      <- postImport(
                             emptyProject,
                             bodyFromZipFile,
                             validContentTypeHeaders,
                           )
          importExists  <- Files.isDirectory(storageConfig.assetPath / emptyProject.toString)
                           && Files.isDirectory(storageConfig.assetPath / emptyProject.toString / "fg")
        } yield assertTrue(response.status == Status.Ok, importExists)
      ),
      test("given the Body is not a zip, will return 400") {
        for {
          storageConfig      <- ZIO.service[StorageConfig]
          response           <- postImport(emptyProject, nonEmptyChunkBody, validContentTypeHeaders)
          importDoesNotExist <- validateImportedProjectExists(storageConfig, emptyProject).map(!_)
        } yield assertTrue(response.status == Status.BadRequest, importDoesNotExist)
      },
    )
  ).provide(
    AssetInfoServiceLive.layer,
    FileChecksumServiceLive.layer,
    ImportServiceLive.layer,
    ProjectServiceLive.layer,
    SpecConfigurations.storageConfigLayer,
    StorageServiceLive.layer,
  )

  private def validateImportedProjectExists(storageConfig: StorageConfig, shortcode: String | ProjectShortcode)
      : UIO[Boolean] = {
    val expectedFiles = List("info", "jp2", "jp2.orig").map("FGiLaT4zzuV-CqwbEDFAFeS." + _)
    val projectPath   = storageConfig.assetPath / shortcode.toString
    ZIO.foreach(expectedFiles)(file => Files.exists(projectPath / file)).map(_.forall(identity))
  }
}
