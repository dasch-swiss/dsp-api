/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.common.api

import sttp.tapir.AnyEndpoint
import zio.ZLayer

import org.knora.webapi.slice.lists.api.ListsEndpointsV2
import org.knora.webapi.slice.resourceinfo.api.ResourceInfoEndpoints
import org.knora.webapi.slice.search.api.SearchEndpoints

final case class ApiV2Endpoints(
  private val listsEndpointsV2: ListsEndpointsV2,
  private val resourceInfoEndpoints: ResourceInfoEndpoints,
  private val searchEndpoints: SearchEndpoints,
) {

  val endpoints: Seq[AnyEndpoint] =
    listsEndpointsV2.endpoints ++
      resourceInfoEndpoints.endpoints ++
      searchEndpoints.endpoints
}

object ApiV2Endpoints {
  val layer = ZLayer.derive[ApiV2Endpoints]
}
