/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api

import zio.ZLayer
import org.knora.webapi.config.AppConfig
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.slice.admin.api.service.StoreRestService
import sttp.capabilities.zio.ZioStreams
import sttp.tapir.ztapir.*

final case class StoreServerEndpoints(
  private val endpoints: StoreEndpoints,
  private val appConfig: AppConfig,
  private val storesResponder: StoreRestService,
) {
  val serverEndpoints: List[ZServerEndpoint[Any, ZioStreams]] = List(
    endpoints.postStoreResetTriplestoreContent.zServerLogic {
      case (rdfObjs: Option[List[RdfDataObject]], prependDefaults: Boolean) =>
        storesResponder.resetTriplestoreContent(rdfObjs.getOrElse(List.empty), prependDefaults)
    },
  )
}

object StoreServerEndpoints {
  val layer = ZLayer.derive[StoreServerEndpoints]
}
