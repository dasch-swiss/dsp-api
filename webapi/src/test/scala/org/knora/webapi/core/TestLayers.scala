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
import org.knora.webapi.testservices.TestClientService
import org.knora.webapi.store.iiif.impl.IIIFServiceMockImpl

object TestLayers {

  type DefaultTestEnvironmentWithoutSipi = Environment with FusekiTestContainer with TestClientService
  type DefaultTestEnvironmentWithSipi    = DefaultTestEnvironmentWithoutSipi with SipiTestContainer

  // all effect layers needed to provide the `Environment`
  def defaultTestLayersWithSipi(sys: akka.actor.ActorSystem) =
    ZLayer.make[
      DefaultTestEnvironmentWithSipi
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
      FusekiTestContainer.layer,
      // Test services
      TestClientService.layer(sys)
    )

  // all effect layers needed to provide the `Environment`
  def defaultTestLayersWithoutSipi(sys: akka.actor.ActorSystem) =
    ZLayer.make[
      DefaultTestEnvironmentWithoutSipi
    ](
      ActorSystemTestImpl.layer(sys),
      AppConfigForTestContainers.fusekiOnlyTestcontainer,
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
      FusekiTestContainer.layer,
      // Test services
      TestClientService.layer(sys)
    )

  def defaultTestLayersWithMockedSipi(sys: akka.actor.ActorSystem) =
    ZLayer.make[
      DefaultTestEnvironmentWithoutSipi
    ](
      ActorSystemTestImpl.layer(sys),
      AppConfigForTestContainers.fusekiOnlyTestcontainer,
      AppRouter.layer,
      CacheServiceManager.layer,
      CacheServiceInMemImpl.layer,
      HttpServer.layer,
      IIIFServiceManager.layer,
      IIIFServiceMockImpl.layer, // alternative: IIIFServiceMockImpl.layer
      JWTService.layer,
      RepositoryUpdater.layer,
      State.layer,
      TriplestoreServiceManager.layer,
      TriplestoreServiceHttpConnectorImpl.layer,
      // testcontainers
      FusekiTestContainer.layer,
      // Test services
      TestClientService.layer(sys)
    )
}
