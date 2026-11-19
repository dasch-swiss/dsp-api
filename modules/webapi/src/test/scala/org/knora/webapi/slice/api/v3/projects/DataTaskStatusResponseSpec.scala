/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v3.projects

import org.junit.runner.RunWith
import sttp.apispec.Schema as ASchema
import sttp.apispec.SchemaType
import sttp.tapir.*
import sttp.tapir.docs.openapi.OpenAPIDocsInterpreter
import sttp.tapir.generic.auto.*
import sttp.tapir.json.zio.jsonBody
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner

@RunWith(classOf[DspZTestJUnitRunner])
class DataTaskStatusResponseSpec extends ZIOSpecDefault {

  private val createdBySchema: Option[ASchema] =
    OpenAPIDocsInterpreter()
      .toOpenAPI(endpoint.get.out(jsonBody[DataTaskStatusResponse]), "test", "1")
      .components
      .flatMap(_.schemas.get("DataTaskStatusResponse"))
      .collect { case s: ASchema => s }
      .flatMap(_.properties.get("createdBy"))
      .collect { case s: ASchema => s }

  def spec: Spec[Any, Any] = suite("DataTaskStatusResponse OpenAPI schema")(
    test("createdBy is a plain string, with no format or pattern") {
      assertTrue(
        createdBySchema.flatMap(_.`type`).contains(List(SchemaType.String)),
        createdBySchema.flatMap(_.format).isEmpty,
        createdBySchema.flatMap(_.pattern).isEmpty,
        createdBySchema.flatMap(_.description).contains("IRI of the user or service that created the task."),
      )
    },
  )
}
