package org.knora.webapi.core

import zio.ZLayer

import org.knora.webapi.auth.JWTService
import org.knora.webapi.config.AppConfig
import org.knora.webapi.config.AppConfigForTestContainers
import org.knora.webapi.routing.ApiRoutes
import org.knora.webapi.store.cache.CacheServiceManager
import org.knora.webapi.store.cache.api.CacheService
import org.knora.webapi.store.cache.impl.CacheServiceInMemImpl
import org.knora.webapi.store.iiif.IIIFServiceManager
import org.knora.webapi.store.iiif.api.IIIFService
import org.knora.webapi.store.iiif.impl.IIIFServiceMockImpl
import org.knora.webapi.store.iiif.impl.IIIFServiceSipiImpl
import org.knora.webapi.store.triplestore.TriplestoreServiceManager
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.impl.TriplestoreServiceHttpConnectorImpl
import org.knora.webapi.store.triplestore.upgrade.RepositoryUpdater
import org.knora.webapi.testcontainers.FusekiTestContainer
import org.knora.webapi.testcontainers.SipiTestContainer
import org.knora.webapi.testservices.TestClientService

object LayersTest {

  /**
   * The `Environment`s that we require for the tests to run - with or without Sipi
   */
  type DefaultTestEnvironmentWithoutSipi = LayersLive.DspEnvironmentLive with FusekiTestContainer with TestClientService
  type DefaultTestEnvironmentWithSipi    = DefaultTestEnvironmentWithoutSipi with SipiTestContainer

  val common =
    ZLayer.makeSome[
      ActorSystem with IIIFService with AppConfig,
      AppRouter with ApiRoutes with State with HttpServer with IIIFServiceManager with TestClientService with RepositoryUpdater with TriplestoreServiceManager with CacheService with CacheServiceManager with TriplestoreService
    ](
      ApiRoutes.layer,
      AppRouter.layer,
      HttpServer.layer,
      IIIFServiceManager.layer,
      State.layer,
      TestClientService.layer,
      RepositoryUpdater.layer,
      TriplestoreServiceManager.layer,
      CacheServiceInMemImpl.layer,
      CacheServiceManager.layer,
      TriplestoreServiceHttpConnectorImpl.layer
    )

  /**
   * All live layers with both Fuseki and Sipi testcontainers
   */
  val defaultLayersTestWithSipi =
    ZLayer.make[DefaultTestEnvironmentWithSipi](
      ActorSystem.layer,
      AppConfigForTestContainers.testcontainers,
      common,
      IIIFServiceSipiImpl.layer, // alternative: MockSipiImpl.layer
      JWTService.layer,
      SipiTestContainer.layer,
      FusekiTestContainer.layer
    )

  /**
   * All live layers - with ActorSystem - but without Sipi testcontainer
   */
  val defaultLayersTestWithoutSipi =
    ZLayer.make[DefaultTestEnvironmentWithoutSipi](
      ActorSystem.layer,
      AppConfigForTestContainers.fusekiOnlyTestcontainer,
      common,
      IIIFServiceSipiImpl.layer, // alternative: MockSipiImpl.layer
      JWTService.layer,
      FusekiTestContainer.layer
    )

  /**
   * All live layers - with ActorSystemTest - but without Sipi testcontainer
   */
  def defaultLayersTestWithoutSipi(system: akka.actor.ActorSystem) =
    ZLayer.make[DefaultTestEnvironmentWithoutSipi](
      ActorSystemTest.layer(system),
      AppConfigForTestContainers.fusekiOnlyTestcontainer,
      common,
      IIIFServiceSipiImpl.layer, // alternative: MockSipiImpl.layer
      JWTService.layer,
      FusekiTestContainer.layer
    )

  /**
   * All live layers - with ActorSystemTest - but with the mocked IIIF layer
   */
  val defaultLayersTestWithMockedSipi =
    ZLayer.make[DefaultTestEnvironmentWithoutSipi](
      ActorSystem.layer,
      AppConfigForTestContainers.fusekiOnlyTestcontainer,
      common,
      IIIFServiceMockImpl.layer,
      JWTService.layer,
      FusekiTestContainer.layer
    )

  /**
   * All live layers - with ActorSystemTest - but with the mocked IIIF layer
   */
  def defaultLayersTestWithMockedSipi(system: akka.actor.ActorSystem) =
    ZLayer.make[DefaultTestEnvironmentWithoutSipi](
      ActorSystemTest.layer(system),
      AppConfigForTestContainers.fusekiOnlyTestcontainer,
      common,
      IIIFServiceMockImpl.layer,
      JWTService.layer,
      FusekiTestContainer.layer
    )
}
