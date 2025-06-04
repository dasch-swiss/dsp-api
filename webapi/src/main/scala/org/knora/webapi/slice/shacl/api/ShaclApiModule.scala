/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.shacl.api

import zio.*

import org.knora.webapi.slice.common.api.BaseEndpoints
import org.knora.webapi.slice.common.api.HandlerMapper
import org.knora.webapi.slice.common.api.TapirToPekkoInterpreter
import org.knora.webapi.slice.shacl.domain.ShaclValidator

object ShaclApiModule { self =>
  type Dependencies = BaseEndpoints & HandlerMapper & ShaclValidator & TapirToPekkoInterpreter
  type Provided     = ShaclApiRoutes & ShaclEndpoints
  val layer: URLayer[self.Dependencies, self.Provided] =
    ZLayer.makeSome[self.Dependencies, self.Provided](
      ShaclApiRoutes.layer,
      ShaclEndpoints.layer,
      ShaclEndpointsHandler.layer,
      ShaclApiService.layer,
    )
}
