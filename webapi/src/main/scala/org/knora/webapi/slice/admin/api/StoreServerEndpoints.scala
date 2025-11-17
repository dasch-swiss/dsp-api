/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api

import sttp.tapir.ztapir.*
import zio.*

import org.knora.webapi.config.AppConfig
import org.knora.webapi.slice.admin.api.service.StoreRestService

final case class StoreServerEndpoints(
  private val appConfig: AppConfig,
  private val endpoints: StoreEndpoints,
  private val restService: StoreRestService,
) {

  val serverEndpoints: List[ZServerEndpoint[Any, Any]] =
    if (appConfig.allowReloadOverHttp)
      List(endpoints.postStoreResetTriplestoreContent.zServerLogic(restService.resetTriplestoreContent))
    else List.empty
}

object StoreServerEndpoints {
  val layer = ZLayer.derive[StoreServerEndpoints]
}
