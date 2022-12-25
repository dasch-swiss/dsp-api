/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.core

import zio.ULayer
import zio.ZLayer

import org.knora.webapi.auth.JWTService
import org.knora.webapi.config.AppConfig
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.responders.ActorDeps
import org.knora.webapi.responders.ActorToZioBridge
import org.knora.webapi.responders.admin.ProjectsService
import org.knora.webapi.routing.ApiRoutes
import org.knora.webapi.routing.admin.ProjectsRouteZ
import org.knora.webapi.slice.resourceinfo.api.ResourceInfoRoute
import org.knora.webapi.slice.resourceinfo.api.RestResourceInfoService
import org.knora.webapi.slice.resourceinfo.domain.IriConverter
import org.knora.webapi.slice.resourceinfo.domain.ResourceInfoRepo
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
      ActorDeps.layer,
      ActorSystem.layer,
      ActorToZioBridge.live,
      ApiRoutes.layer,
      AppConfig.live,
      AppRouter.layer,
      CacheServiceInMemImpl.layer,
      CacheServiceManager.layer,
      HttpServer.layer,
      HttpServerZ.layer, // this is the new ZIO HTTP server layer
      IIIFServiceManager.layer,
      IIIFServiceSipiImpl.layer,
      IriConverter.layer,
      JWTService.layer,
      ProjectsRouteZ.layer,
      ProjectsService.layer,
      RepositoryUpdater.layer,
      ResourceInfoRepo.layer,
      ResourceInfoRoute.layer,
      RestResourceInfoService.layer,
      State.layer,
      StringFormatter.live,
      TriplestoreServiceHttpConnectorImpl.layer,
      TriplestoreServiceManager.layer
    )
}
