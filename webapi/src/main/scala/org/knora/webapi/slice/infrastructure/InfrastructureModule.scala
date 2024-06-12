/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.infrastructure

import zio.ZLayer

import org.knora.webapi.config.DspIngestConfig
import org.knora.webapi.config.JwtConfig

object InfrastructureModule {

  type Dependencies =
    // format: off
    DspIngestConfig &
    JwtConfig
    // format: on

  type Provided =
    // format: off
    CacheManager &
    InvalidTokenCache &
    JwtService
    // format: on

  val layer = ZLayer.makeSome[Dependencies, Provided](
    CacheManager.layer,
    InvalidTokenCache.layer,
    JwtServiceLive.layer,
  )
}
