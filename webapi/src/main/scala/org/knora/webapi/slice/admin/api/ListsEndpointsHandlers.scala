/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api

import zio.ZLayer

import org.knora.webapi.responders.admin.ListsResponder
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.common.api.HandlerMapper
import org.knora.webapi.slice.common.api.PublicEndpointHandler

final case class ListsEndpointsHandlers(
  listsEndpoints: ListsEndpoints,
  listsResponder: ListsResponder,
  mapper: HandlerMapper
) {

  private val getListsQueryByProjectIriHandler = PublicEndpointHandler(
    listsEndpoints.getListsQueryByProjectIriOption,
    (iri: Option[ProjectIri]) => listsResponder.getLists(iri)
  )

  val allHandlers = List(
    getListsQueryByProjectIriHandler
  ).map(mapper.mapPublicEndpointHandler(_))
}

object ListsEndpointsHandlers {
  val layer = ZLayer.derive[ListsEndpointsHandlers]
}
