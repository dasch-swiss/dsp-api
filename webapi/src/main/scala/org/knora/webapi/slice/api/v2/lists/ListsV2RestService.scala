/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v2.lists

import sttp.model.MediaType
import zio.*

import dsp.errors.NotFoundException
import org.knora.webapi.config.AppConfig
import org.knora.webapi.messages.admin.responder.listsmessages.ChildNodeInfoGetResponseADM
import org.knora.webapi.messages.admin.responder.listsmessages.ListGetResponseADM
import org.knora.webapi.messages.v2.responder.listsmessages.*
import org.knora.webapi.responders.admin.ListsResponder
import org.knora.webapi.slice.admin.domain.model.ListProperties.ListIri
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.common.api.KnoraResponseRenderer
import org.knora.webapi.slice.common.api.KnoraResponseRenderer.FormatOptions
import org.knora.webapi.slice.common.api.KnoraResponseRenderer.RenderedResponse

final class ListsV2RestService(appConfig: AppConfig, listsResponder: ListsResponder, renderer: KnoraResponseRenderer) {

  /**
   * Gets a list from the triplestore.
   *
   * @param user           the user making the request.
   * @param listIri        the Iri of the list's root node.
   * @param opts           the format options
   *
   * @return the rendered response and the media type
   */
  def getList(user: User)(listIri: ListIri, opts: FormatOptions): Task[(RenderedResponse, MediaType)] =
    listsResponder
      .listGetRequestADM(listIri)
      .flatMap(r => ZIO.getOrFailWith(NotFoundException(s"List $listIri not found."))(r.asOpt[ListGetResponseADM]))
      .map(_.list)
      .map(ListGetResponseV2(_, user.lang, appConfig.fallbackLanguage))
      .flatMap(renderer.render(_, opts))

  /**
   * Gets a single list node from the triplestore.
   *
   * @param user           the user making the request.
   * @param nodeIri              the Iri of the list node.
   * @param opts           the format options
   *
   * @return the rendered response and the media type
   */
  def getNode(user: User)(nodeIri: ListIri, opts: FormatOptions): Task[(RenderedResponse, MediaType)] =
    listsResponder
      .listNodeInfoGetRequestADM(nodeIri.value)
      .flatMap(r =>
        ZIO.getOrFailWith(NotFoundException(s"Node $nodeIri not found."))(r.asOpt[ChildNodeInfoGetResponseADM]),
      )
      .map(_.nodeinfo)
      .map(NodeGetResponseV2(_, user.lang, appConfig.fallbackLanguage))
      .flatMap(renderer.render(_, opts))
}

object ListsV2RestService {
  val layer = ZLayer.derive[ListsV2RestService]
}
