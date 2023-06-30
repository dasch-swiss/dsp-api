/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.api.monitoring

import swiss.dasch.api.monitoring.InfoEndpoint.InfoEndpointResponse
import swiss.dasch.version.BuildInfo
import zio.http.{ Request, Root, Status, URL }
import zio.test.*
import zio.json.EncoderOps

object InfoEndpointSpec extends ZIOSpecDefault {
  val spec = suite("InfoEndpointSpec")(
    test("InfoEndpoint should return 200") {
      val app = InfoEndpoint.app
      for {
        response     <- app.runZIO(Request.get(URL(Root / "info")))
        bodyAsString <- response.body.asString
      } yield assertTrue(
        response.status == Status.Ok,
        bodyAsString == InfoEndpointResponse(
          name = BuildInfo.name,
          version = BuildInfo.version,
          scalaVersion = BuildInfo.scalaVersion,
          sbtVersion = BuildInfo.sbtVersion,
          buildTime = BuildInfo.builtAtString,
          gitCommit = BuildInfo.gitCommit,
        ).toJson,
      )
    }
  )
}
