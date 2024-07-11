/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.lists.domain

import zio.*

import dsp.errors.NotFoundException
import org.knora.webapi.config.AppConfig
import org.knora.webapi.messages.admin.responder.listsmessages.ChildNodeInfoGetResponseADM
import org.knora.webapi.messages.admin.responder.listsmessages.ListGetResponseADM
import org.knora.webapi.messages.v2.responder.listsmessages.*
import org.knora.webapi.responders.admin.ListsResponder
import org.knora.webapi.slice.admin.domain.model.ListProperties.ListIri
import org.knora.webapi.slice.admin.domain.model.User

final case class ListsService(private val appConfig: AppConfig, private val listsResponder: ListsResponder) {

  /**
   * Gets a list from the triplestore.
   *
   * @param listIri        the Iri of the list's root node.
   * @param requestingUser the user making the request.
   * @return a [[ListGetResponseV2]]. . A [[None]] if the list is not found.
   */
  def getList(listIri: ListIri, requestingUser: User): IO[Option[Throwable], ListGetResponseV2] =
    listsResponder
      .listGetRequestADM(listIri.value)
      .mapError {
        case e: NotFoundException => None
        case e                    => Some(e)
      }
      .flatMap {
        case ListGetResponseADM(list) => ZIO.succeed(list)
        case _                        => ZIO.fail(None)
      }
      .map(ListGetResponseV2(_, requestingUser.lang, appConfig.fallbackLanguage))

  /**
   * Gets a single list node from the triplestore.
   *
   * @param nodeIri              the Iri of the list node.
   *
   * @param requestingUser       the user making the request.
   * @return a  [[NodeGetResponseV2]]. A [[None]] if the node is not found.
   */
  def getNode(nodeIri: ListIri, requestingUser: User): IO[Option[Throwable], NodeGetResponseV2] =
    listsResponder
      .listNodeInfoGetRequestADM(nodeIri.value)
      .mapError {
        case e: NotFoundException => None
        case e                    => Some(e)
      }
      .flatMap {
        case ChildNodeInfoGetResponseADM(node) => ZIO.succeed(node)
        case _                                 => ZIO.fail(None)
      }
      .map(NodeGetResponseV2(_, requestingUser.lang, appConfig.fallbackLanguage))
}

object ListsService {
  val layer = ZLayer.derive[ListsService]
}
