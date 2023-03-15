package org.knora.webapi.core

import org.knora.webapi.auth.JWTService
import org.knora.webapi.config.AppConfig
import org.knora.webapi.config.AppConfigForTestContainers
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.responders.ActorDeps
import org.knora.webapi.responders.ActorToZioBridge
import org.knora.webapi.responders.IriService
import org.knora.webapi.responders.admin.GroupsResponderADM
import org.knora.webapi.responders.admin.GroupsResponderADMLive
import org.knora.webapi.responders.admin.ProjectsResponderADM
import org.knora.webapi.responders.admin.ProjectsResponderADMLive
import org.knora.webapi.responders.v1.UsersResponderV1
import org.knora.webapi.responders.v1.UsersResponderV1Live
import org.knora.webapi.routing.ApiRoutes
import org.knora.webapi.slice.ontology.api.service.RestCardinalityServiceLive
import org.knora.webapi.slice.ontology.domain.service.CardinalityService
import org.knora.webapi.slice.ontology.repo.service.OntologyCache
import org.knora.webapi.slice.ontology.repo.service.OntologyRepoLive
import org.knora.webapi.slice.ontology.repo.service.PredicateRepositoryLive
import org.knora.webapi.slice.resourceinfo.api.RestResourceInfoService
import org.knora.webapi.slice.resourceinfo.domain.IriConverter
import org.knora.webapi.slice.resourceinfo.domain.ResourceInfoRepo
import org.knora.webapi.store.cache.api.CacheService
import org.knora.webapi.store.cache.impl.CacheServiceInMemImpl
import org.knora.webapi.store.iiif.api.IIIFService
import org.knora.webapi.store.iiif.impl.IIIFServiceMockImpl
import org.knora.webapi.store.iiif.impl.IIIFServiceSipiImpl
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.impl.TriplestoreServiceLive
import org.knora.webapi.store.triplestore.upgrade.RepositoryUpdater
import org.knora.webapi.testcontainers.FusekiTestContainer
import org.knora.webapi.testcontainers.SipiTestContainer
import org.knora.webapi.testservices.TestClientService
import zio._

import org.knora.webapi.messages.util.standoff.StandoffTagUtilV2
import org.knora.webapi.messages.util.standoff.StandoffTagUtilV2Live
import org.knora.webapi.messages.util.ConstructResponseUtilV2
import org.knora.webapi.messages.util.ConstructResponseUtilV2Live
import org.knora.webapi.messages.util.PermissionUtilADM
import org.knora.webapi.messages.util.PermissionUtilADMLive
import org.knora.webapi.messages.util.ValueUtilV1
import org.knora.webapi.messages.util.ValueUtilV1Live
import org.knora.webapi.responders.admin.ListsResponderADM
import org.knora.webapi.responders.admin.ListsResponderADMLive
import org.knora.webapi.responders.admin.UsersResponderADM
import org.knora.webapi.responders.admin.UsersResponderADMLive
import org.knora.webapi.slice.ontology.api.service.RestCardinalityService
import org.knora.webapi.responders.admin.SipiResponderADM
import org.knora.webapi.responders.admin.SipiResponderADMLive
import org.knora.webapi.responders.admin.PermissionsResponderADM
import org.knora.webapi.responders.admin.PermissionsResponderADMLive
import org.knora.webapi.responders.v1.CkanResponderV1
import org.knora.webapi.responders.v1.CkanResponderV1Live
import org.knora.webapi.responders.v2.ListsResponderV2
import org.knora.webapi.responders.v2.ListsResponderV2Live
import org.knora.webapi.responders.v1.ListsResponderV1
import org.knora.webapi.responders.v1.ListsResponderV1Live
import org.knora.webapi.responders.v1.ProjectsResponderV1
import org.knora.webapi.responders.v1.ProjectsResponderV1Live
import org.knora.webapi.responders.v1.OntologyResponderV1
import org.knora.webapi.responders.v1.OntologyResponderV1Live
import org.knora.webapi.responders.v1.SearchResponderV1
import org.knora.webapi.responders.v1.SearchResponderV1Live
import org.knora.webapi.responders.admin.StoresResponderADM
import org.knora.webapi.responders.admin.StoresResponderADMLive
import org.knora.webapi.responders.v1.StandoffResponderV1
import org.knora.webapi.responders.v1.StandoffResponderV1Live
import org.knora.webapi.responders.v1.ValuesResponderV1
import org.knora.webapi.responders.v1.ValuesResponderV1Live
import org.knora.webapi.responders.v1.ResourcesResponderV1
import org.knora.webapi.responders.v1.ResourcesResponderV1Live
import org.knora.webapi.responders.v2.ResourceUtilV2
import org.knora.webapi.responders.v2.ResourceUtilV2Live
import org.knora.webapi.responders.v2.StandoffResponderV2
import org.knora.webapi.responders.v2.StandoffResponderV2Live
import org.knora.webapi.store.cache.CacheServiceRequestMessageHandler
import org.knora.webapi.store.cache.CacheServiceRequestMessageHandlerLive
import org.knora.webapi.store.iiif.IIIFRequestMessageHandler
import org.knora.webapi.store.iiif.IIIFRequestMessageHandlerLive
import org.knora.webapi.store.triplestore.TriplestoreRequestMessageHandler
import org.knora.webapi.store.triplestore.TriplestoreRequestMessageHandlerLive
import org.knora.webapi.responders.v2.ontology.CardinalityHandler
import org.knora.webapi.responders.v2.ontology.CardinalityHandlerLive
import org.knora.webapi.responders.v2.ontology.OntologyHelpers
import org.knora.webapi.responders.v2.ontology.OntologyHelpersLive
import org.knora.webapi.responders.v2.OntologyResponderV2
import org.knora.webapi.responders.v2.OntologyResponderV2Live
import org.knora.webapi.responders.v2.ResourcesResponderV2
import org.knora.webapi.responders.v2.ResourcesResponderV2Live
import org.knora.webapi.slice.ontology.domain.service.OntologyRepo
import org.knora.webapi.slice.ontology.repo.service.OntologyCacheLive
object LayersTest {

  /**
   * The `Environment`s that we require for the tests to run - with or without Sipi
   */
  type DefaultTestEnvironmentWithoutSipi = LayersLive.DspEnvironmentLive with FusekiTestContainer with TestClientService
  type DefaultTestEnvironmentWithSipi    = DefaultTestEnvironmentWithoutSipi with SipiTestContainer

  type CommonR0 = ActorSystem with IIIFService with AppConfig
  type CommonR = ActorDeps
    with ActorToZioBridge
    with ApiRoutes
    with AppRouter
    with AppRouterRelayingMessageHandler
    with OntologyCache
    with OntologyRepo
    with CacheService
    with CacheServiceRequestMessageHandler
    with CardinalityHandler
    with CardinalityService
    with CkanResponderV1
    with ConstructResponseUtilV2
    with GroupsResponderADM
    with HttpServer
    with IIIFRequestMessageHandler
    with IriConverter
    with IriService
    with ListsResponderV2
    with ListsResponderADM
    with ListsResponderV1
    with MessageRelay
    with OntologyResponderV1
    with OntologyResponderV2
    with OntologyHelpers
    with PermissionUtilADM
    with PermissionsResponderADM
    with ProjectsResponderADM
    with ProjectsResponderV1
    with RepositoryUpdater
    with ResourceInfoRepo
    with ResourceUtilV2
    with ResourcesResponderV1
    with ResourcesResponderV2
    with RestCardinalityService
    with RestResourceInfoService
    with SearchResponderV1
    with SipiResponderADM
    with StandoffResponderV1
    with StandoffResponderV2
    with StandoffTagUtilV2
    with State
    with StoresResponderADM
    with StringFormatter
    with TestClientService
    with TriplestoreService
    with TriplestoreRequestMessageHandler
    with UsersResponderADM
    with UsersResponderV1
    with ValueUtilV1
    with ValuesResponderV1

  private val commonLayersForAllIntegrationTests =
    ZLayer.makeSome[CommonR0, CommonR](
      ActorDeps.layer,
      ActorToZioBridge.live,
      ApiRoutes.layer,
      AppRouter.layer,
      AppRouterRelayingMessageHandler.layer,
      CacheServiceInMemImpl.layer,
      CacheServiceRequestMessageHandlerLive.layer,
      CardinalityHandlerLive.layer,
      CardinalityService.layer,
      CkanResponderV1Live.layer,
      ConstructResponseUtilV2Live.layer,
      GroupsResponderADMLive.layer,
      HttpServer.layer,
      IIIFRequestMessageHandlerLive.layer,
      IriConverter.layer,
      IriService.layer,
      ListsResponderV2Live.layer,
      ListsResponderADMLive.layer,
      ListsResponderV1Live.layer,
      MessageRelayLive.layer,
      OntologyCacheLive.layer,
      OntologyHelpersLive.layer,
      OntologyRepoLive.layer,
      OntologyResponderV1Live.layer,
      OntologyResponderV2Live.layer,
      PermissionUtilADMLive.layer,
      PermissionsResponderADMLive.layer,
      PredicateRepositoryLive.layer,
      ProjectsResponderADMLive.layer,
      ProjectsResponderV1Live.layer,
      RepositoryUpdater.layer,
      ResourceInfoRepo.layer,
      ResourceUtilV2Live.layer,
      ResourcesResponderV1Live.layer,
      ResourcesResponderV2Live.layer,
      RestCardinalityServiceLive.layer,
      RestResourceInfoService.layer,
      SearchResponderV1Live.layer,
      SipiResponderADMLive.layer,
      StandoffResponderV1Live.layer,
      StandoffResponderV2Live.layer,
      StandoffTagUtilV2Live.layer,
      State.layer,
      StoresResponderADMLive.layer,
      StringFormatter.test,
      TestClientService.layer,
      TriplestoreServiceLive.layer,
      TriplestoreRequestMessageHandlerLive.layer,
      UsersResponderADMLive.layer,
      UsersResponderV1Live.layer,
      ValueUtilV1Live.layer,
      ValuesResponderV1Live.layer
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
