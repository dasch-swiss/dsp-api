/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.export_.api

import sttp.model.HeaderNames
import sttp.model.MediaType
import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.zio.jsonBody
import zio.ZLayer

import org.knora.webapi.slice.api.v3.ApiV3
import org.knora.webapi.slice.api.v3.V3BaseEndpoint

final case class ExportEndpoints(
  baseEndpoints: V3BaseEndpoint,
) {
  val postExportResources = baseEndpoints.withUserEndpoint.post
    .in(ApiV3.basePath / "export" / "resources")
    .in(
      jsonBody[ExportRequest].example(
        ExportRequest(
          resourceClass = "classLink",
          selectedProperties = List(
            "http://www.knora.org/ontology/knora-base#hasStillImageFileValue",
          ),
        ),
      ),
    )
    .out(stringBody)
    .out(header[MediaType](HeaderNames.ContentType))
    .out(header[String]("Content-Disposition"))
}

object ExportEndpoints {
  val layer = ZLayer.derive[ExportEndpoints]
}
