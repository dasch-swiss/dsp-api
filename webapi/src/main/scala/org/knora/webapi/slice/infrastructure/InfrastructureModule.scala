/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.infrastructure

import zio.URLayer
import zio.ZLayer

import org.knora.webapi.config.DspIngestConfig
import org.knora.webapi.config.JwtConfig

object InfrastructureModule { self =>

  type Dependencies = DspIngestConfig & JwtConfig

  type Provided =
    // format: off
    CacheManager &
    CsvService &
    InvalidTokenCache &
    JwtService
    // format: on

  val layer: URLayer[self.Dependencies, self.Provided] =
    ZLayer.makeSome[self.Dependencies, self.Provided](
      CacheManager.layer,
      InvalidTokenCache.layer,
      JwtServiceLive.layer,
      CsvService.layer,
    )
}
