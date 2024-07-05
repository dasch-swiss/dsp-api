/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.lists.api

import org.apache.pekko.http.scaladsl.server.Route
import zio.ZLayer

import org.knora.webapi.slice.common.api.TapirToPekkoInterpreter

final case class ListsApiV2Routes(
  private val listsEndpointsV2: ListsEndpointsV2Handler,
  private val tapirToPekko: TapirToPekkoInterpreter,
) {
  private val handlers   = listsEndpointsV2.allHandlers
  val routes: Seq[Route] = handlers.map(tapirToPekko.toRoute(_))
}
object ListsApiV2Routes {
  val layer = ZLayer.derive[ListsApiV2Routes]
}
