/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v2.values

import sttp.tapir.ztapir.*
import zio.*

import org.knora.webapi.responders.v2.ValuesResponderV2
import org.knora.webapi.slice.admin.domain.service.KnoraProjectService
import org.knora.webapi.slice.api.v2.ValueUuid
import org.knora.webapi.slice.common.ApiComplexV2JsonLdRequestParser
import org.knora.webapi.slice.common.api.AuthorizationRestService
import org.knora.webapi.slice.common.api.BaseEndpoints
import org.knora.webapi.slice.common.api.KnoraResponseRenderer
import org.knora.webapi.slice.common.api.KnoraResponseRenderer.FormatOptions
import org.knora.webapi.slice.resources.service.ReadResourcesService

final class ValuesServerEndpoints(endpoints: ValuesEndpoints, restService: ValuesRestService) {
  val serverEndpoints: List[ZServerEndpoint[Any, Any]] = List(
    endpoints.getValue.serverLogic(restService.getValue),
    endpoints.postValues.serverLogic(restService.createValue),
    endpoints.putValues.serverLogic(restService.updateValue),
    endpoints.deleteValues.serverLogic(restService.deleteValue),
    endpoints.postValuesErase.serverLogic(restService.eraseValue),
    endpoints.postValuesErasehistory.serverLogic(restService.eraseValueHistory),
  )
}
object ValuesServerEndpoints {
  type Dependencies =
    ApiComplexV2JsonLdRequestParser & AuthorizationRestService & BaseEndpoints & KnoraProjectService &
      KnoraResponseRenderer & ReadResourcesService & ValuesResponderV2

  type Provided = ValuesServerEndpoints

  val layer: URLayer[Dependencies, Provided] = ValuesEndpoints.layer >+>
    ValuesRestService.layer >+>
    ZLayer.derive[ValuesServerEndpoints]
}
