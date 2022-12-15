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

  type CommonR0 = ActorSystem with IIIFService with AppConfig
  type CommonR = ApiRoutes
    with AppRouter
    with CacheService
    with CacheServiceManager
    with HttpServer
    with IIIFServiceManager
    with RepositoryUpdater
    with State
    with TestClientService
    with TriplestoreService
    with TriplestoreServiceManager

  private val commonLayersForAllIntegrationTests =
    ZLayer.makeSome[CommonR0, CommonR](
      ApiRoutes.layer,
      AppRouter.layer,
      CacheServiceInMemImpl.layer,
      CacheServiceManager.layer,
      HttpServer.layer,
      IIIFServiceManager.layer,
      RepositoryUpdater.layer,
      State.layer,
      TestClientService.layer,
      TriplestoreServiceHttpConnectorImpl.layer,
      TriplestoreServiceManager.layer
    )

  /**
   * All live layers with both Fuseki and Sipi testcontainers
   */
  val defaultLayersTestWithSipi =
    ZLayer.make[DefaultTestEnvironmentWithSipi](
      commonLayersForAllIntegrationTests,
      ActorSystem.layer,
      AppConfigForTestContainers.testcontainers,
      FusekiTestContainer.layer,
      IIIFServiceSipiImpl.layer,
      JWTService.layer,
      SipiTestContainer.layer
    )

  /**
   * All live layers - with ActorSystem - but without Sipi testcontainer
   */
  val defaultLayersTestWithoutSipi =
    ZLayer.make[DefaultTestEnvironmentWithoutSipi](
      commonLayersForAllIntegrationTests,
      ActorSystem.layer,
      AppConfigForTestContainers.fusekiOnlyTestcontainer,
      FusekiTestContainer.layer,
      IIIFServiceSipiImpl.layer,
      JWTService.layer
    )

  /**
   * All live layers - with ActorSystemTest - but without Sipi testcontainer
   */
  def defaultLayersTestWithoutSipi(system: akka.actor.ActorSystem) =
    ZLayer.make[DefaultTestEnvironmentWithoutSipi](
      commonLayersForAllIntegrationTests,
      ActorSystemTest.layer(system),
      AppConfigForTestContainers.fusekiOnlyTestcontainer,
      FusekiTestContainer.layer,
      IIIFServiceSipiImpl.layer,
      JWTService.layer
    )

  /**
   * All live layers - with ActorSystemTest - but with the mocked IIIF layer
   */
  val defaultLayersTestWithMockedSipi =
    ZLayer.make[DefaultTestEnvironmentWithoutSipi](
      commonLayersForAllIntegrationTests,
      ActorSystem.layer,
      AppConfigForTestContainers.fusekiOnlyTestcontainer,
      FusekiTestContainer.layer,
      IIIFServiceMockImpl.layer,
      JWTService.layer
    )

  /**
   * All live layers - with ActorSystemTest - but with the mocked IIIF layer
   */
  def defaultLayersTestWithMockedSipi(system: akka.actor.ActorSystem) =
    ZLayer.make[DefaultTestEnvironmentWithoutSipi](
      commonLayersForAllIntegrationTests,
      ActorSystemTest.layer(system),
      AppConfigForTestContainers.fusekiOnlyTestcontainer,
      FusekiTestContainer.layer,
      IIIFServiceMockImpl.layer,
      JWTService.layer
    )
}
