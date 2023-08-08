/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.api

import swiss.dasch.api.ListProjectsEndpoint.ProjectResponse
import swiss.dasch.domain.*
import swiss.dasch.test.SpecConfigurations
import swiss.dasch.test.SpecConstants.toProjectShortcode
import zio.http.{ Request, Root, Status, URL }
import zio.json.*
import zio.test.{ ZIOSpecDefault, assertTrue }
import zio.{ Chunk, http }

object ListProjectsEndpointSpec extends ZIOSpecDefault {

  val spec = suite("ListProjectsEndpoint")(
    test("should list non-empty project in test folders") {
      for {
        response <- ListProjectsEndpoint.app.runZIO(Request.get(URL(Root / "projects")))
        body     <- response.body.asString
      } yield assertTrue(response.status == Status.Ok, body == Chunk(ProjectResponse("0001".toProjectShortcode)).toJson)
    }
  ).provide(
    AssetInfoServiceLive.layer,
    FileChecksumServiceLive.layer,
    ProjectServiceLive.layer,
    SpecConfigurations.storageConfigLayer,
    StorageServiceLive.layer,
  )
}
