/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.lists.api
import sttp.capabilities.zio.ZioStreams
import sttp.model.MediaType
import sttp.tapir.ztapir.*
import zio.*

import dsp.errors.NotFoundException
import org.knora.webapi.config.AppConfig
import org.knora.webapi.messages.v2.responder.KnoraResponseV2
import org.knora.webapi.slice.admin.domain.model.ListProperties.ListIri
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.common.api.KnoraResponseRenderer.FormatOptions
import org.knora.webapi.slice.lists.domain.ListsService

final case class ListsServerEndpointsV2(
  private val appConfig: AppConfig,
  private val endpoints: ListsEndpointsV2,
  private val listsService: ListsService,
) {

  private def renderResponse(resp: KnoraResponseV2, format: FormatOptions): (MediaType, String) = {
    val mediaType      = format.rdfFormat.mediaType
    val responseString = resp.format(format, appConfig)
    (mediaType, responseString)
  }

  val serverEndpoints: List[ZServerEndpoint[Any, ZioStreams]] = List(
    endpoints.getV2Lists.serverLogic((user: User) =>
      (iri: ListIri, format: FormatOptions) =>
        listsService
          .getList(iri, user)
          .mapBoth(
            {
              case Some(e) => e
              case None    => NotFoundException(s"List ${iri.value} not found.")
            },
            renderResponse(_, format),
          ),
    ),
    endpoints.getV2Node.serverLogic((user: User) =>
      (iri: ListIri, format: FormatOptions) =>
        listsService
          .getNode(iri, user)
          .mapBoth(
            {
              case Some(e) => e
              case None    => NotFoundException(s"Node ${iri.value} not found.")
            },
            renderResponse(_, format),
          ),
    ),
  )
}

object ListsServerEndpointsV2 {
  val layer = ZLayer.derive[ListsServerEndpointsV2]
}
