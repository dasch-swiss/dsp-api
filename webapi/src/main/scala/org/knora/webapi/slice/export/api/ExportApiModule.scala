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
import org.knora.webapi.slice.export_.model.ExportService
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.slice.admin.domain.service.KnoraProjectService
import org.knora.webapi.slice.infrastructure.CsvService
import org.knora.webapi.slice.common.api.AuthorizationRestService

object ExportApiModule { self =>
  type Dependencies =
    // format: off
    Authenticator &
    AuthorizationRestService &
    CsvService &
    IriConverter &
    KnoraProjectService &
    TriplestoreService
    // format: on

  type Provided =
    // format: off
    ExportServerEndpoints
    // format: on

  val layer: URLayer[self.Dependencies, self.Provided] =
    ZLayer.makeSome[self.Dependencies, self.Provided](
      ExportEndpoints.layer,
      ExportService.layer,
      ExportRestService.layer,
      ExportServerEndpoints.layer,
      V3BaseEndpoint.layer,
    )
}
