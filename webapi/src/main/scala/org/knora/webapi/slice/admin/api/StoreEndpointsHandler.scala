/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api

import zio.ZLayer

import org.knora.webapi.config.AppConfig
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.slice.admin.api.service.StoreRestService
import org.knora.webapi.slice.common.api.HandlerMapper
import org.knora.webapi.slice.common.api.PublicEndpointHandler

final case class StoreEndpointsHandler(
  endpoints: StoreEndpoints,
  appConfig: AppConfig,
  storesResponder: StoreRestService,
  mapper: HandlerMapper
) {

  private val postStoreResetTriplestoreContentHandler =
    PublicEndpointHandler[(Option[List[RdfDataObject]], Boolean), MessageResponse](
      endpoints.postStoreResetTriplestoreContent,
      { case (rdfObjs: Option[List[RdfDataObject]], prependDefaults: Boolean) =>
        storesResponder.resetTriplestoreContent(rdfObjs.getOrElse(List.empty), prependDefaults)
      }
    )

  val allHandlers = {
    val handlerIfConfigured =
      if (appConfig.allowReloadOverHttp) Seq(postStoreResetTriplestoreContentHandler) else Seq.empty
    handlerIfConfigured.map(mapper.mapPublicEndpointHandler(_))
  }
}

object StoreEndpointsHandler {
  val layer = ZLayer.derive[StoreEndpointsHandler]
}
