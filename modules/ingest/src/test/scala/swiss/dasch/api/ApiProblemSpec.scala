/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.api

import org.junit.runner.RunWith
import swiss.dasch.api.ApiProblem.InternalServerError
import swiss.dasch.api.ApiProblem.UnprocessableContent
import zio.json.*
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner

@RunWith(classOf[DspZTestJUnitRunner])
class ApiProblemSpec extends ZIOSpecDefault {

  val spec = suite("ApiProblem JSON")(
    test("InternalServerError round-trips with snake-case fields") {
      val problem = InternalServerError("abc", "internal", "An internal error occurred.")
      val json    = problem.toJson
      assertTrue(
        json == """{"correlation_id":"abc","error_code":"internal","message":"An internal error occurred."}""",
        json.fromJson[InternalServerError] == Right(problem),
      )
    },
    test("UnprocessableContent round-trips with snake-case fields") {
      val problem = UnprocessableContent("abc", "unsupported_format", "File x.gif is not a supported file.")
      val json    = problem.toJson
      assertTrue(
        json == """{"correlation_id":"abc","error_code":"unsupported_format","message":"File x.gif is not a supported file."}""",
        json.fromJson[UnprocessableContent] == Right(problem),
      )
    },
  )
}
