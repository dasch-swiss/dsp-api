package org.knora.webapi.core

import zio.ULayer
import zio.ZLayer

import org.knora.webapi.auth.JWTService
import org.knora.webapi.config.AppConfig
import org.knora.webapi.config.AppConfigForTestContainers
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.routing.ApiRoutes
import org.knora.webapi.slice.ontology.api.service.RestCardinalityService
import org.knora.webapi.slice.ontology.domain.service.CardinalityService
import org.knora.webapi.slice.ontology.repo.service.OntologyCache
import org.knora.webapi.slice.ontology.repo.service.OntologyRepoLive
import org.knora.webapi.slice.ontology.repo.service.PredicateRepositoryLive
import org.knora.webapi.slice.resourceinfo.api.RestResourceInfoService
import org.knora.webapi.slice.resourceinfo.domain.IriConverter
import org.knora.webapi.slice.resourceinfo.domain.ResourceInfoRepo
import org.knora.webapi.store.cache.CacheServiceManager
import org.knora.webapi.store.cache.api.CacheService
import org.knora.webapi.store.cache.impl.CacheServiceInMemImpl
import org.knora.webapi.store.iiif.IIIFServiceManager
import org.knora.webapi.store.iiif.api.IIIFService
import org.knora.webapi.store.iiif.impl.IIIFServiceMockImpl
import org.knora.webapi.store.iiif.impl.IIIFServiceSipiImpl
import org.knora.webapi.store.triplestore.TriplestoreServiceManager
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreServiceInMemory
import org.knora.webapi.store.triplestore.upgrade.RepositoryUpdater
import org.knora.webapi.testcontainers.SipiTestContainer
import org.knora.webapi.testservices.TestClientService

object LayersTest {

  /**
   * The `Environment`s that we require for the tests to run - with or without Sipi
   */
  type DefaultTestEnvironmentWithoutSipi = LayersLive.DspEnvironmentLive with TestClientService
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
      TriplestoreServiceInMemory.layer,
      TriplestoreServiceInMemory.emptyDatasetLayer,
      TriplestoreServiceManager.layer
    )

  private val sipiTestcontainersLayer =
    ZLayer.make[SipiTestContainer with AppConfig with JWTService with IIIFService](
      AppConfigForTestContainers.testcontainers,
      SipiTestContainer.layer,
      IIIFServiceSipiImpl.layer,
      JWTService.layer
    )

  private val sipiMockLayer =
    ZLayer.make[AppConfig with JWTService with IIIFService](
      AppConfig.layer,
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
      sipiMockLayer,
      system.map(ActorSystemTest.layer).getOrElse(ActorSystem.layer)
    )

  /**
   * Provides a layer for integration tests which depend on Fuseki and Sipi as Testcontainers.
   * @return a [[ULayer]] with the [[DefaultTestEnvironmentWithSipi]]
   */
  val integrationTestsWithSipiAndFusekiTestcontainers: ULayer[DefaultTestEnvironmentWithSipi] =
    ZLayer.make[DefaultTestEnvironmentWithSipi](
      commonLayersForAllIntegrationTests,
      sipiTestcontainersLayer,
      ActorSystem.layer
    )
}
