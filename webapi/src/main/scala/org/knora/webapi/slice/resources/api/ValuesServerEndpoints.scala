/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.api

import sttp.tapir.ztapir.*
import sttp.model.MediaType
import zio.ZLayer
import org.knora.webapi.slice.common.api.KnoraResponseRenderer.FormatOptions
import org.knora.webapi.slice.resources.api.model.ValueUuid
import org.knora.webapi.slice.resources.api.model.VersionDate
import org.knora.webapi.slice.resources.api.service.ValuesRestService
import sttp.capabilities.zio.ZioStreams

final class ValuesServerEndpoints(
  private val endpoints: ValuesEndpoints,
  private val valuesRestService: ValuesRestService,
) {

  val serverEndpoints: List[ZServerEndpoint[Any, ZioStreams]] = List(
    endpoints.getValue.serverLogic(valuesRestService.getValue),
    endpoints.postValues.serverLogic(valuesRestService.createValue),
    endpoints.putValues.serverLogic(valuesRestService.updateValue),
    endpoints.deleteValues.serverLogic(valuesRestService.deleteValue),
  )
}
object ValuesServerEndpoints {
  val layer = ZLayer.derive[ValuesServerEndpoints]
}
