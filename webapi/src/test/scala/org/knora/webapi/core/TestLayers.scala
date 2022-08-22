package org.knora.webapi.core

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
import org.knora.webapi.config.AppConfigForTestContainers
import org.knora.webapi.testcontainers.SipiTestContainer
import org.knora.webapi.testcontainers.FusekiTestContainer

package object TestLayers {

  // The `Environment` that we require to exist at startup.
  type DefaultTestEnvironment = Environment with SipiTestContainer with FusekiTestContainer

  // all effect layers needed to provide the `Environment`
  def defaultTestLayers(sys: akka.actor.ActorSystem) =
    ZLayer.make[
      DefaultTestEnvironment
    ](
      ActorSystemTestImpl.layer(sys),
      AppConfigForTestContainers.testcontainers,
      AppRouter.layer,
      CacheServiceManager.layer,
      CacheServiceInMemImpl.layer,
      HttpServer.layer,
      IIIFServiceManager.layer,
      IIIFServiceSipiImpl.layer, // alternative: MockSipiImpl.layer
      JWTService.layer,
      RepositoryUpdater.layer,
      State.layer,
      TriplestoreServiceManager.layer,
      TriplestoreServiceHttpConnectorImpl.layer,
      // testcontainers
      SipiTestContainer.layer,
      FusekiTestContainer.layer
    )
}
