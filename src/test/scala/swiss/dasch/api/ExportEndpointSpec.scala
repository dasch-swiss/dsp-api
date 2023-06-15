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

  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("ExportEndpoint")(
      suite("POST on /export/{project} should")(
        test("given the project does not exist, return 404") {
          val url = URL.empty.withPath(!! / "export" / nonExistentProject.toString)
          for {
            response <- ExportEndpoint.app.runZIO(Request.post(Body.empty, url))
          } yield assertTrue(response.status == Status.NotFound)
        },
        test("given the project shortcode is invalid, return 400") {
          val url = URL.empty.withPath(!! / "export" / "invalid-short-code")
          for {
            response <- ExportEndpoint.app.runZIO(Request.post(Body.empty, url))
          } yield assertTrue(response.status == Status.BadRequest)
        },
        test("given the project is valid, return 200") {
          val url = URL.empty.withPath(!! / "export" / existingProject.toString)
          for {
            response <- ExportEndpoint.app.runZIO(Request.post(Body.empty, url))
          } yield assertTrue(response.status == Status.Ok)
        },
      )
    ).provide(AssetServiceLive.layer, SpecConfigurations.storageConfigLayer)
}
