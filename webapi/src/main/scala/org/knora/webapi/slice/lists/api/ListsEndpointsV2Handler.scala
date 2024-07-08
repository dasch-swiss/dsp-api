/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.lists.api
import sttp.model.MediaType
import zio.ZLayer

import org.knora.webapi.config.AppConfig
import org.knora.webapi.messages.v2.responder.KnoraResponseV2
import org.knora.webapi.slice.admin.domain.model.ListProperties.ListIri
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.common.api.HandlerMapper
import org.knora.webapi.slice.common.api.KnoraResponseRenderer.FormatOptions
import org.knora.webapi.slice.common.api.SecuredEndpointHandler
import org.knora.webapi.slice.lists.domain.ListsService

final case class ListsEndpointsV2Handler(
  private val appConfig: AppConfig,
  private val endpoints: ListsEndpointsV2,
  private val listsService: ListsService,
  private val mapper: HandlerMapper,
) {

  private val getV2Lists = SecuredEndpointHandler(
    endpoints.getV2Lists,
    (user: User) =>
      (iri: ListIri, format: FormatOptions) => listsService.getList(iri, user).map(renderResponse(_, format)),
  )

  private def renderResponse(resp: KnoraResponseV2, format: FormatOptions): (MediaType, String) = {
    val mediaType      = format.rdfFormat.mediaType
    val responseString = resp.format(format, appConfig)
    (mediaType, responseString)
  }

  private val getV2Node = SecuredEndpointHandler(
    endpoints.getV2Node,
    (user: User) =>
      (iri: ListIri, format: FormatOptions) => listsService.getNode(iri, user).map(renderResponse(_, format)),
  )

  val allHandlers = List(getV2Lists, getV2Node).map(mapper.mapSecuredEndpointHandler(_))
}

object ListsEndpointsV2Handler {
  val layer = ZLayer.derive[ListsEndpointsV2Handler]
}
