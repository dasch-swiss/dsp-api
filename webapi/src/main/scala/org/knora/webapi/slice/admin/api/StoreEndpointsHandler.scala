/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
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
  private val appConfig: AppConfig,
  private val endpoints: StoreEndpoints,
  private val mapper: HandlerMapper,
  private val restService: StoreRestService,
) {

  val allHandlers = {
    val handlerIfConfigured =
      if (appConfig.allowReloadOverHttp)
        Seq(PublicEndpointHandler(endpoints.postStoreResetTriplestoreContent, restService.resetTriplestoreContent))
      else Seq.empty
    handlerIfConfigured.map(mapper.mapPublicEndpointHandler)
  }
}

object StoreEndpointsHandler {
  val layer = ZLayer.derive[StoreEndpointsHandler]
}
