/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.lists.api
import sttp.model.MediaType
import zio.*

import org.knora.webapi.config.AppConfig
import org.knora.webapi.slice.admin.domain.model.ListProperties.ListIri
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.common.api.HandlerMapper
import org.knora.webapi.slice.common.api.KnoraResponseRenderer.FormatOptions
import org.knora.webapi.slice.common.api.SecuredEndpointHandler
import org.knora.webapi.slice.lists.api.service.ListsV2RestService

final case class ListsEndpointsV2Handler(
  private val appConfig: AppConfig,
  private val endpoints: ListsEndpointsV2,
  private val listsRestService: ListsV2RestService,
  private val mapper: HandlerMapper,
) {
  val allHandlers = List(
    SecuredEndpointHandler(endpoints.getV2Lists, listsRestService.getList),
    SecuredEndpointHandler(endpoints.getV2Node, listsRestService.getNode),
  ).map(mapper.mapSecuredEndpointHandler)
}

object ListsEndpointsV2Handler {
  val layer = ZLayer.derive[ListsEndpointsV2Handler]
}
