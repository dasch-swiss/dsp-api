/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.admin

import sttp.tapir.ztapir.*
import zio.*

import org.knora.webapi.config.AppConfig
import org.knora.webapi.slice.api.admin.service.StoreRestService

final class StoreServerEndpoints(appConfig: AppConfig, endpoints: StoreEndpoints, restService: StoreRestService) {

  val serverEndpoints: List[ZServerEndpoint[Any, Any]] =
    if (appConfig.allowReloadOverHttp)
      List(endpoints.postStoreResetTriplestoreContent.zServerLogic(restService.resetTriplestoreContent))
    else List.empty
}

object StoreServerEndpoints {
  val layer = ZLayer.derive[StoreServerEndpoints]
}
