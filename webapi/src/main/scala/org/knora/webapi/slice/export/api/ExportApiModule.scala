/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.export_.api

import zio.URLayer
import zio.ZLayer

import org.knora.webapi.slice.api.v3.V3BaseEndpoint
import org.knora.webapi.slice.security.Authenticator
import org.knora.webapi.slice.common.service.IriConverter

object ExportApiModule { self =>
  type Dependencies =
    // format: off
    Authenticator &
    IriConverter
    // format: on

  type Provided =
    // format: off
    ExportServerEndpoints
    // format: on

  val layer: URLayer[self.Dependencies, self.Provided] =
    ZLayer.makeSome[self.Dependencies, self.Provided](
      ExportEndpoints.layer,
      ExportRestService.layer,
      ExportServerEndpoints.layer,
      V3BaseEndpoint.layer,
    )
}
