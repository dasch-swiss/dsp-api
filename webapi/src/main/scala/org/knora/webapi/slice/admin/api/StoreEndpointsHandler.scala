/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api

import zio.*
import sttp.tapir.ztapir.*

import org.knora.webapi.config.AppConfig
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.slice.admin.api.service.StoreRestService

final case class StoreEndpointsHandler(
  private val appConfig: AppConfig,
  private val endpoints: StoreEndpoints,
  private val restService: StoreRestService,
) {

  val allHandlers =
    if (appConfig.allowReloadOverHttp)
      Seq(endpoints.postStoreResetTriplestoreContent.zServerLogic(restService.resetTriplestoreContent))
    else Seq.empty
}

object StoreEndpointsHandler {
  val layer = ZLayer.derive[StoreEndpointsHandler]
}
