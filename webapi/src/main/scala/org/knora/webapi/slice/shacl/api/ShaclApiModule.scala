/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.shacl.api

import zio.*

import org.knora.webapi.slice.common.api.BaseEndpoints
import org.knora.webapi.slice.common.api.TapirToZioHttpInterpreter
import org.knora.webapi.slice.shacl.domain.ShaclValidator

object ShaclApiModule { self =>
  type Dependencies = BaseEndpoints & ShaclValidator & TapirToZioHttpInterpreter
  type Provided     = ShaclEndpoints & ShaclServerEndpoints
  val layer: URLayer[self.Dependencies, self.Provided] =
    ZLayer.makeSome[self.Dependencies, self.Provided](
      ShaclEndpoints.layer,
      ShaclServerEndpoints.layer,
      ShaclApiService.layer,
    )
}
