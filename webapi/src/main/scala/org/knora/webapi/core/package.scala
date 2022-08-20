package org.knora.webapi

import zio.ZLayer
import org.knora.webapi.config.AppConfig
import org.knora.webapi.store.cache.CacheServiceManager
import org.knora.webapi.store.cache.api.CacheService
import org.knora.webapi.store.iiif.IIIFServiceManager
import org.knora.webapi.store.iiif.api.IIIFService
import org.knora.webapi.auth.JWTService
import org.knora.webapi.store.triplestore.upgrade.RepositoryUpdater
import org.knora.webapi.store.triplestore.TriplestoreServiceManager
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.cache.impl.CacheServiceInMemImpl
import org.knora.webapi.store.iiif.impl.IIIFServiceSipiImpl
import org.knora.webapi.store.triplestore.impl.TriplestoreServiceHttpConnectorImpl

package object core {

  /**
   * The `Environment` that we require to exist at startup.
   */
  type Environment = ActorSystem
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

  // all effect layers needed to provide the `Environment`
  val allLayers =
    ZLayer.make[
      ActorSystem with AppConfig with AppRouter with CacheServiceManager with CacheService with HttpServer with IIIFServiceManager with IIIFService with JWTService with RepositoryUpdater with State with TriplestoreServiceManager with TriplestoreService
    ](
      ActorSystem.layer,
      AppConfig.live,
      AppRouter.layer,
      CacheServiceManager.layer,
      CacheServiceInMemImpl.layer,
      HttpServer.layer,
      IIIFServiceManager.layer,
      IIIFServiceSipiImpl.layer,
      JWTService.layer,
      RepositoryUpdater.layer,
      State.layer,
      TriplestoreServiceManager.layer,
      TriplestoreServiceHttpConnectorImpl.layer
    )
}
