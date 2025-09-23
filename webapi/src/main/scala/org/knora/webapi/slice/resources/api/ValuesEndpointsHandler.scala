/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.api

import sttp.model.MediaType

import zio.*
import sttp.tapir.ztapir.*

import org.knora.webapi.slice.common.api.KnoraResponseRenderer.FormatOptions
import org.knora.webapi.slice.resources.api.model.ValueUuid
import org.knora.webapi.slice.resources.api.model.VersionDate
import org.knora.webapi.slice.resources.api.service.ValuesRestService

final class ValuesEndpointsHandler(
  endpoints: ValuesEndpoints,
  valuesRestService: ValuesRestService,
) {

  val allHandlers = Seq(
    endpoints.getValue.serverLogic(valuesRestService.getValue),
    endpoints.postValues.serverLogic(valuesRestService.createValue),
    endpoints.putValues.serverLogic(valuesRestService.updateValue),
    endpoints.deleteValues.serverLogic(valuesRestService.deleteValue),
    endpoints.postValuesErase.serverLogic(valuesRestService.eraseValue),
    endpoints.postValuesErasehistory.serverLogic(valuesRestService.eraseValueHistory),
  )
}
object ValuesEndpointsHandler {
  val layer = ZLayer.derive[ValuesEndpointsHandler]
}
