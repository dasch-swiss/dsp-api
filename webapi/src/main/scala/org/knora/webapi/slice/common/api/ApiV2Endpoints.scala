/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.common.api

import sttp.tapir.AnyEndpoint
import zio.ZLayer

import org.knora.webapi.slice.resourceinfo.api.ResourceInfoEndpoints
import org.knora.webapi.slice.search.api.SearchEndpoints

final case class ApiV2Endpoints(resourceInfoEndpoints: ResourceInfoEndpoints, searchEndpoints: SearchEndpoints) {

  val endpoints: Seq[AnyEndpoint] =
    resourceInfoEndpoints.endpoints ++
      searchEndpoints.endpoints
}

object ApiV2Endpoints {
  val layer = ZLayer.derive[ApiV2Endpoints]
}
