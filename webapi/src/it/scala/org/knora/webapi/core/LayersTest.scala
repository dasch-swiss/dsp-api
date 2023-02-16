package org.knora.webapi.core

import org.knora.webapi.auth.JWTService
import org.knora.webapi.config.{AppConfig, AppConfigForTestContainers}
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.routing.ApiRoutes
import org.knora.webapi.slice.ontology.api.service.RestCardinalityService
import org.knora.webapi.slice.ontology.domain.service.CardinalityService
import org.knora.webapi.slice.ontology.repo.service.{OntologyCache, OntologyRepoLive, PredicateRepositoryLive}
import org.knora.webapi.slice.resourceinfo.api.RestResourceInfoService
import org.knora.webapi.slice.resourceinfo.domain.{IriConverter, ResourceInfoRepo}
import org.knora.webapi.store.cache.CacheServiceManager
import org.knora.webapi.store.cache.api.CacheService
import org.knora.webapi.store.cache.impl.CacheServiceInMemImpl
import org.knora.webapi.store.iiif.IIIFServiceManager
import org.knora.webapi.store.iiif.api.IIIFService
import org.knora.webapi.store.iiif.impl.{IIIFServiceMockImpl, IIIFServiceSipiImpl}
import org.knora.webapi.store.triplestore.TriplestoreServiceManager
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.impl.TriplestoreServiceLive
import org.knora.webapi.store.triplestore.upgrade.RepositoryUpdater
import org.knora.webapi.testcontainers.{FusekiTestContainer, SipiTestContainer}
import org.knora.webapi.testservices.TestClientService
import zio._

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
    with CardinalityService
    with HttpServer
    with IIIFServiceManager
    with IriConverter
    with MessageRelay
    with RepositoryUpdater
    with ResourceInfoRepo
    with RestCardinalityService
    with RestResourceInfoService
    with State
    with StringFormatter
    with TestClientService
    with TriplestoreService
    with TriplestoreServiceManager

  private val commonLayersForAllIntegrationTests =
    ZLayer.makeSome[CommonR0, CommonR](
      ApiRoutes.layer,
      AppRouter.layer,
      CacheServiceInMemImpl.layer,
      CacheServiceManager.layer,
      CardinalityService.layer,
      HttpServer.layer,
      IIIFServiceManager.layer,
      IriConverter.layer,
      MessageRelayLive.layer,
      OntologyCache.layer,
      OntologyRepoLive.layer,
      PredicateRepositoryLive.layer,
      RepositoryUpdater.layer,
      ResourceInfoRepo.layer,
      RestCardinalityService.layer,
      RestResourceInfoService.layer,
      State.layer,
      StringFormatter.test,
      TestClientService.layer,
      TriplestoreServiceLive.layer,
      TriplestoreServiceManager.layer
    )

  private val fusekiAndSipiTestcontainers =
    ZLayer.make[FusekiTestContainer with SipiTestContainer with AppConfig with JWTService with IIIFService](
      AppConfigForTestContainers.testcontainers,
      FusekiTestContainer.layer,
      SipiTestContainer.layer,
      IIIFServiceSipiImpl.layer,
      JWTService.layer
    )

  private val fusekiTestcontainers =
    ZLayer.make[FusekiTestContainer with AppConfig with JWTService with IIIFService](
      AppConfigForTestContainers.fusekiOnlyTestcontainer,
      FusekiTestContainer.layer,
      IIIFServiceMockImpl.layer,
      JWTService.layer
    )

  /**
   * Provides a layer for integration tests which depend on Fuseki as Testcontainers.
   * Sipi/IIIFService will be mocked with the [[IIIFServiceMockImpl]]
   * @param system An optional [[akka.actor.ActorSystem]] for use with Akka's [[akka.testkit.TestKit]]
   * @return a [[ULayer]] with the [[DefaultTestEnvironmentWithoutSipi]]
   */
  def integrationTestsWithFusekiTestcontainers(
    system: Option[akka.actor.ActorSystem] = None
  ): ULayer[DefaultTestEnvironmentWithoutSipi] =
    ZLayer.make[DefaultTestEnvironmentWithoutSipi](
      commonLayersForAllIntegrationTests,
      fusekiTestcontainers,
      system.map(ActorSystemTest.layer).getOrElse(ActorSystem.layer)
    )

  /**
   * Provides a layer for integration tests which depend on Fuseki and Sipi as Testcontainers.
   * @return a [[ULayer]] with the [[DefaultTestEnvironmentWithSipi]]
   */
  val integrationTestsWithSipiAndFusekiTestcontainers: ULayer[DefaultTestEnvironmentWithSipi] =
    ZLayer.make[DefaultTestEnvironmentWithSipi](
      commonLayersForAllIntegrationTests,
      fusekiAndSipiTestcontainers,
      ActorSystem.layer
    )
}
