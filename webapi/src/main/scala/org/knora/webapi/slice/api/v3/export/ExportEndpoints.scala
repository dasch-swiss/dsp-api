/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v3.`export`

import sttp.model.HeaderNames
import sttp.model.MediaType
import sttp.model.MediaTypes
import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.zio.jsonBody
import zio.ZLayer

import java.nio.charset.StandardCharsets

import org.knora.webapi.slice.api.v3.ApiV3
import org.knora.webapi.slice.api.v3.EndpointHelper
import org.knora.webapi.slice.api.v3.V3BaseEndpoint
import org.knora.webapi.slice.api.v3.V3ErrorCode.*

final case class ExportEndpoints(baseEndpoints: V3BaseEndpoint) extends EndpointHelper {

  val postExportResources = baseEndpoints
    .withUser(
      oneOf(
        notFoundVariant(project_not_found),
        notFoundVariant(ontology_not_found),
        notFoundVariant(resourceClass_not_found),
        badRequestVariant,
      ),
    )
    .post
    .in(ApiV3.basePath / "export" / "resources")
    .description(
      "Export resources to CSV format. Publicly accessible. Requires appropriate object access permissions on the resources.",
    )
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
    .out(stringBodyAnyFormat(Codec.string.format(ExportEndpoints.Csv()), StandardCharsets.UTF_8))
    .out(header[MediaType](HeaderNames.ContentType))
    .out(header[String]("Content-Disposition"))
}

object ExportEndpoints {
  private[`export`] val layer = ZLayer.derive[ExportEndpoints]

  private case class Csv() extends CodecFormat {
    override val mediaType: MediaType = new MediaTypes {}.TextCsv
  }
}
