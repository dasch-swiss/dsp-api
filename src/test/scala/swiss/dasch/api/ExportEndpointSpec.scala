/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.api

import swiss.dasch.domain.AssetServiceLive
import swiss.dasch.test.SpecConfigurations
import swiss.dasch.test.SpecConstants.{ existingProject, nonExistentProject }
import zio.Scope
import zio.http.{ URL, * }
import zio.test.Assertion.equalTo
import zio.test.{ Spec, TestEnvironment, ZIOSpecDefault, assertCompletes, assertTrue, assertZIO }

import java.net.URI

object ExportEndpointSpec extends ZIOSpecDefault {

  private def postEmptyBody(url: URL) = ExportEndpoint.app.runZIO(Request.post(Body.empty, url))

  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("ExportEndpoint")(
      suite("POST on /export/{project} should")(
        test("given the project does not exist, return 404") {
          for {
            response <- postEmptyBody(URL(Root / "export" / nonExistentProject.toString))
          } yield assertTrue(response.status == Status.NotFound)
        },
        test("given the project shortcode is invalid, return 400") {
          for {
            response <- postEmptyBody(URL(Root / "export" / "invalid-short-code"))
          } yield assertTrue(response.status == Status.BadRequest)
        },
        test("given the project is valid, return 200 with correct headers") {
          for {
            response <- postEmptyBody(URL(Root / "export" / existingProject.toString))
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
