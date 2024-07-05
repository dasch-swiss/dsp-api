package org.knora.webapi.slice.lists.api
import zio.ZLayer

import org.knora.webapi.config.AppConfig
import org.knora.webapi.responders.v2.ListsResponderV2
import org.knora.webapi.slice.admin.domain.model.ListProperties.ListIri
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.common.api.HandlerMapper
import org.knora.webapi.slice.common.api.KnoraResponseRenderer.FormatOptions
import org.knora.webapi.slice.common.api.SecuredEndpointHandler

final case class ListsEndpointsV2Handler(
  private val appConfig: AppConfig,
  private val endpoints: ListsEndpointsV2,
  private val responder: ListsResponderV2,
  private val mapper: HandlerMapper,
) {

  private val getV2Lists = SecuredEndpointHandler(
    endpoints.getV2Lists,
    (user: User) =>
      (iri: ListIri, format: FormatOptions) => responder.getList(iri.value, user).map(_.format(format, appConfig)),
  )

  private val getV2Node = SecuredEndpointHandler(
    endpoints.getV2Node,
    (user: User) =>
      (iri: ListIri, format: FormatOptions) => responder.getNode(iri.value, user).map(_.format(format, appConfig)),
  )

  val allHandlers = List(getV2Lists, getV2Node).map(mapper.mapSecuredEndpointHandler(_))
}

object ListsEndpointsV2Handler {
  val layer = ZLayer.derive[ListsEndpointsV2Handler]
}
