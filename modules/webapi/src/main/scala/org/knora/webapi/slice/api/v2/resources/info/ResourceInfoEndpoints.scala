/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v2.resources.info

import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.zio.*
import zio.ZLayer

import java.time.Instant

import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.api.admin.model.Order
import org.knora.webapi.slice.api.admin.model.OrderBy
import org.knora.webapi.slice.api.v2.ApiV2
import org.knora.webapi.slice.api.v2.IriDto
import org.knora.webapi.slice.common.api.BaseEndpoints

final case class ResourceInfoEndpoints(baseEndpoints: BaseEndpoints) {
  val getResourcesInfo = baseEndpoints.publicEndpoint.get
    .in("v2" / "resources" / "info")
    .in(header[ProjectIri](ApiV2.Headers.xKnoraAcceptProject))
    .in(
      query[Option[IriDto]]("resourceClass")
        .description("Only list resources of exactly this class (no subclasses). Without it, all classes are listed."),
    )
    .in(
      query[Option[Instant]]("cutoff")
        .description(
          "Only list resources whose effective date (lastModificationDate, else creationDate) is at or after this " +
            "instant. An ISO-8601 instant with an offset, e.g. 2026-10-23T08:00:00Z; a '+' offset must be URL-encoded.",
        ),
    )
    .in(Order.queryParam)
    .in(OrderBy.queryParam.default(OrderBy.LastModificationDate))
    .out(jsonBody[ListResponseDto])
    .description(
      "List the resources of the project named in the X-Knora-Accept-Project header, with their class and dates, " +
        "deleted resources included. Optionally restricted to one resource class and to resources changed at or " +
        "after a cutoff. `asOf` is the server time read before the listing was queried; to poll for changes, pass " +
        "the previous `asOf` minus a safety window as the next cutoff. Publicly accessible.",
    )
}

object ResourceInfoEndpoints {
  private[info] val layer = ZLayer.derive[ResourceInfoEndpoints]
}
