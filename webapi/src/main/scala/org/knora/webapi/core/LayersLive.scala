/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.core

import zio.ULayer
import zio.ZLayer

import org.knora.webapi.auth.JWTService
import org.knora.webapi.config.AppConfig
import org.knora.webapi.routing.ApiRoutes
import org.knora.webapi.routing.ApiRoutesWithZIOHttp
import org.knora.webapi.routing.HealthRouteWithZIOHttp
import org.knora.webapi.store.cache.CacheServiceManager
import org.knora.webapi.store.cache.api.CacheService
import org.knora.webapi.store.cache.impl.CacheServiceInMemImpl
import org.knora.webapi.store.iiif.IIIFServiceManager
import org.knora.webapi.store.iiif.api.IIIFService
import org.knora.webapi.store.iiif.impl.IIIFServiceSipiImpl
import org.knora.webapi.store.triplestore.TriplestoreServiceManager
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.impl.TriplestoreServiceHttpConnectorImpl
import org.knora.webapi.store.triplestore.upgrade.RepositoryUpdater

object LayersLive {

  /**
   * The `Environment` that we require to exist at startup.
   */
  type DspEnvironmentLive =
    ActorSystem
      with ApiRoutes
      with AppConfig
      with AppRouter
      with CacheServiceManager
      with CacheService
      with HttpServer
      with IIIFServiceManager
      with IIIFService
      with JWTService
      with RepositoryUpdater
      with State
      with TriplestoreServiceManager
      with TriplestoreService

  /**
   * All effect layers needed to provide the `Environment`
   */
  val dspLayersLive: ULayer[DspEnvironmentLive] =
    ZLayer.make[DspEnvironmentLive](
      ActorSystem.layer,
      ApiRoutes.layer,
      ApiRoutesWithZIOHttp.layer,   // this is the new layer that composes all new routes
      HealthRouteWithZIOHttp.layer, // this is the new health route
      AppConfig.live,
      AppRouter.layer,
      CacheServiceManager.layer,
      CacheServiceInMemImpl.layer,
      HttpServer.layer,
      HttpServerWithZIOHttp.layer, // this is the new ZIO HTTP server layer
      IIIFServiceManager.layer,
      IIIFServiceSipiImpl.layer,
      JWTService.layer,
      RepositoryUpdater.layer,
      State.layer,
      TriplestoreServiceManager.layer,
      TriplestoreServiceHttpConnectorImpl.layer
    )
}
