package org.knora.webapi.core

import zio.ZLayer

import org.knora.webapi.auth.JWTService
import org.knora.webapi.config.AppConfigForTestContainers
import org.knora.webapi.routing.ApiRoutes
import org.knora.webapi.store.cache.CacheServiceManager
import org.knora.webapi.store.cache.impl.CacheServiceInMemImpl
import org.knora.webapi.store.iiif.IIIFServiceManager
import org.knora.webapi.store.iiif.impl.IIIFServiceMockImpl
import org.knora.webapi.store.iiif.impl.IIIFServiceSipiImpl
import org.knora.webapi.store.triplestore.TriplestoreServiceManager
import org.knora.webapi.store.triplestore.impl.TriplestoreServiceHttpConnectorImpl
import org.knora.webapi.store.triplestore.upgrade.RepositoryUpdater
import org.knora.webapi.testcontainers.FusekiTestContainer
import org.knora.webapi.testcontainers.SipiTestContainer
import org.knora.webapi.testservices.TestClientService

object LayersTest {

  type DefaultTestEnvironmentWithoutSipi = LayersLive.DspEnvironmentLive with FusekiTestContainer with TestClientService
  type DefaultTestEnvironmentWithSipi    = DefaultTestEnvironmentWithoutSipi with SipiTestContainer

  // All live layers and both Fuseki and Sipi testcontainers
  val defaultLayersTestWithSipi =
    ZLayer.make[
      DefaultTestEnvironmentWithSipi
    ](
      ActorSystem.layer,
      ApiRoutes.layer,
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
      TestClientService.layer
    )

  // All live layers but without sipi testcontainer
  val defaultLayersTestWithoutSipi =
    ZLayer.make[
      DefaultTestEnvironmentWithoutSipi
    ](
      ActorSystem.layer,
      ApiRoutes.layer,
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
      TestClientService.layer
    )
  // All live layers but without sipi testcontainer
  def defaultLayersTestWithoutSipi(system: akka.actor.ActorSystem) =
    ZLayer.make[
      DefaultTestEnvironmentWithoutSipi
    ](
      ActorSystemTest.layer(system),
      ApiRoutes.layer,
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
      TestClientService.layer
    )

  // All live layers but with the mocked IIIF layer
  val defaultLayersTestWithMockedSipi =
    ZLayer.make[
      DefaultTestEnvironmentWithoutSipi
    ](
      ActorSystem.layer,
      ApiRoutes.layer,
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
      TestClientService.layer
    )

  // All live layers but with the mocked IIIF layer
  def defaultLayersTestWithMockedSipi(system: akka.actor.ActorSystem) =
    ZLayer.make[
      DefaultTestEnvironmentWithoutSipi
    ](
      ActorSystemTest.layer(system),
      ApiRoutes.layer,
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
      TestClientService.layer
    )
}
