package org.knora.webapi.core

import org.knora.webapi.auth.JWTService
import org.knora.webapi.config.AppConfigForTestContainers
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.routing.ApiRoutes
import org.knora.webapi.slice.resourceinfo.api.LiveRestResourceInfoService
import org.knora.webapi.slice.resourceinfo.domain.IriConverter
import org.knora.webapi.slice.resourceinfo.repo.LiveResourceInfoRepo
import org.knora.webapi.store.cache.CacheServiceManager
import org.knora.webapi.store.cache.impl.CacheServiceInMemImpl
import org.knora.webapi.store.iiif.IIIFServiceManager
import org.knora.webapi.store.iiif.impl.{IIIFServiceMockImpl, IIIFServiceSipiImpl}
import org.knora.webapi.store.triplestore.TriplestoreServiceManager
import org.knora.webapi.store.triplestore.impl.TriplestoreServiceHttpConnectorImpl
import org.knora.webapi.store.triplestore.upgrade.RepositoryUpdater
import org.knora.webapi.testcontainers.{FusekiTestContainer, SipiTestContainer}
import org.knora.webapi.testservices.TestClientService
import zio.ZLayer

object LayersTest {

  /**
   * The `Environment`s that we require for the tests to run - with or without Sipi
   */
  type DefaultTestEnvironmentWithoutSipi = LayersLive.DspEnvironmentLive with FusekiTestContainer with TestClientService
  type DefaultTestEnvironmentWithSipi    = DefaultTestEnvironmentWithoutSipi with SipiTestContainer

  /**
   * All live layers with both Fuseki and Sipi testcontainers
   */
  val defaultLayersTestWithSipi =
    ZLayer.make[DefaultTestEnvironmentWithSipi](
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
      LiveRestResourceInfoService.layer,
      LiveResourceInfoRepo.layer,
      IriConverter.layer,
      StringFormatter.testLayer,
      // testcontainers
      SipiTestContainer.layer,
      FusekiTestContainer.layer,
      // Test services
      TestClientService.layer
    )

  /**
   * All live layers - with ActorSystem - but without Sipi testcontainer
   */
  val defaultLayersTestWithoutSipi =
    ZLayer.make[DefaultTestEnvironmentWithoutSipi](
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
      LiveRestResourceInfoService.layer,
      LiveResourceInfoRepo.layer,
      IriConverter.layer,
      StringFormatter.testLayer,
      // testcontainers
      FusekiTestContainer.layer,
      // Test services
      TestClientService.layer
    )

  /**
   * All live layers - with ActorSystemTest - but without Sipi testcontainer
   */
  def defaultLayersTestWithoutSipi(system: akka.actor.ActorSystem) =
    ZLayer.make[DefaultTestEnvironmentWithoutSipi](
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
      LiveRestResourceInfoService.layer,
      LiveResourceInfoRepo.layer,
      IriConverter.layer,
      StringFormatter.testLayer,
      // testcontainers
      FusekiTestContainer.layer,
      // Test services
      TestClientService.layer
    )

  /**
   * All live layers - with ActorSystemTest - but with the mocked IIIF layer
   */
  val defaultLayersTestWithMockedSipi =
    ZLayer.make[DefaultTestEnvironmentWithoutSipi](
      ActorSystem.layer,
      ApiRoutes.layer,
      AppConfigForTestContainers.fusekiOnlyTestcontainer,
      AppRouter.layer,
      CacheServiceManager.layer,
      CacheServiceInMemImpl.layer,
      HttpServer.layer,
      IIIFServiceManager.layer,
      IIIFServiceMockImpl.layer,
      JWTService.layer,
      RepositoryUpdater.layer,
      State.layer,
      TriplestoreServiceManager.layer,
      TriplestoreServiceHttpConnectorImpl.layer,
      LiveRestResourceInfoService.layer,
      LiveResourceInfoRepo.layer,
      IriConverter.layer,
      StringFormatter.testLayer,
      // testcontainers
      FusekiTestContainer.layer,
      // Test services
      TestClientService.layer
    )

  /**
   * All live layers - with ActorSystemTest - but with the mocked IIIF layer
   */
  def defaultLayersTestWithMockedSipi(system: akka.actor.ActorSystem) =
    ZLayer.make[DefaultTestEnvironmentWithoutSipi](
      ActorSystemTest.layer(system),
      ApiRoutes.layer,
      AppConfigForTestContainers.fusekiOnlyTestcontainer,
      AppRouter.layer,
      CacheServiceManager.layer,
      CacheServiceInMemImpl.layer,
      HttpServer.layer,
      IIIFServiceManager.layer,
      IIIFServiceMockImpl.layer,
      JWTService.layer,
      RepositoryUpdater.layer,
      State.layer,
      TriplestoreServiceManager.layer,
      TriplestoreServiceHttpConnectorImpl.layer,
      LiveRestResourceInfoService.layer,
      LiveResourceInfoRepo.layer,
      IriConverter.layer,
      StringFormatter.testLayer,
      // testcontainers
      FusekiTestContainer.layer,
      // Test services
      TestClientService.layer
    )
}
