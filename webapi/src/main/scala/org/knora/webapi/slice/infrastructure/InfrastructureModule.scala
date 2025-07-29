/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.infrastructure

import zio.URLayer
import zio.ZLayer

import org.knora.webapi.config.DspIngestConfig
import org.knora.webapi.config.JwtConfig
import org.knora.webapi.infrastructure.CacheManager
import org.knora.webapi.infrastructure.CsvService
import org.knora.webapi.infrastructure.InvalidTokenCache
import org.knora.webapi.infrastructure.JwtService
import org.knora.webapi.infrastructure.JwtServiceLive

object InfrastructureModule { self =>
  type Dependencies = org.knora.webapi.config.DspIngestConfig & org.knora.webapi.config.JwtConfig
  type Provided     = CacheManager & CsvService & InvalidTokenCache & JwtService

  // Convert webapi config to infrastructure config
  private val jwtConfigLayer = ZLayer.fromFunction { (webapiJwtConfig: org.knora.webapi.config.JwtConfig) =>
    org.knora.webapi.infrastructure
      .JwtConfig(webapiJwtConfig.secret, webapiJwtConfig.expiration, webapiJwtConfig.issuer)
  }

  private val dspIngestConfigLayer =
    ZLayer.fromFunction { (webapiDspIngestConfig: org.knora.webapi.config.DspIngestConfig) =>
      org.knora.webapi.infrastructure.DspIngestConfig(webapiDspIngestConfig.baseUrl, webapiDspIngestConfig.audience)
    }

  val layer: URLayer[self.Dependencies, self.Provided] =
    ZLayer.makeSome[self.Dependencies, self.Provided](
      jwtConfigLayer,
      dspIngestConfigLayer,
      CacheManager.layer,
      InvalidTokenCache.layer,
      JwtServiceLive.layer,
      CsvService.layer,
    )
}
