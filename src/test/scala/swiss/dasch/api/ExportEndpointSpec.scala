/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.api

import swiss.dasch.domain.{ AssetService, AssetServiceLive, ProjectShortcode }
import swiss.dasch.test.SpecConfigurations
import swiss.dasch.test.SpecConstants.{ existingProject, nonExistentProject }
import zio.{ Scope, ZIO }
import zio.http.{ URL, * }
import zio.test.Assertion.equalTo
import zio.test.{ Spec, TestEnvironment, ZIOSpecDefault, assertCompletes, assertTrue, assertZIO }

import java.net.URI

object ExportEndpointSpec extends ZIOSpecDefault {

  private def postExport(shortcode: String | ProjectShortcode): ZIO[AssetService, Option[Response], Response] = {
    val url = URL(Root / "project" / shortcode.toString / "export")
    ExportEndpoint.app.runZIO(Request.post(Body.empty, url))
  }

  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("ExportEndpoint")(
      suite("POST on /project/{shortcode}/export should")(
        test("given the project does not exist, return 404") {
          for {
            response <- postExport(nonExistentProject)
          } yield assertTrue(response.status == Status.NotFound)
        },
        test("given the project shortcode is invalid, return 400") {
          for {
            response <- postExport("invalid-short-code")
          } yield assertTrue(response.status == Status.BadRequest)
        },
        test("given the project is valid, return 200 with correct headers") {
          for {
            response <- postExport(existingProject)
          } yield assertTrue(
            response.status == Status.Ok,
            response
              .headers
              .get("Content-Disposition")
              .contains(s"attachment; filename=export-${existingProject.toString}.zip"),
            response.headers.get("Content-Type").contains("application/zip"),
          )
        },
      )
    ).provide(AssetServiceLive.layer, SpecConfigurations.storageConfigLayer)
}
