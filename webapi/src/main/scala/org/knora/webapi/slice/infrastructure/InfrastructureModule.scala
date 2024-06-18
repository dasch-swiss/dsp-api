/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.infrastructure

import zio.URLayer
import zio.ZLayer

import org.knora.webapi.config.DspIngestConfig
import org.knora.webapi.config.JwtConfig
import org.knora.webapi.slice.URModule

object InfrastructureModule
    extends URModule[
      // format: off
      DspIngestConfig &
      JwtConfig
      ,
      CacheManager &
      InvalidTokenCache &
      JwtService
      // format: on
    ] { self =>
  inline def layer: URLayer[self.Dependencies, self.Provided] =
    ZLayer.makeSome[self.Dependencies, self.Provided](
      CacheManager.layer,
      InvalidTokenCache.layer,
      JwtServiceLive.layer,
    )
}
