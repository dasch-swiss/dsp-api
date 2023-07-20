/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.core

import org.knora.webapi.config.AppConfig.AppConfigurations
import org.knora.webapi.config.AppConfigForTestContainers
import org.knora.webapi.http.middleware.AuthenticationMiddleware
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.util._
import org.knora.webapi.messages.util.search.QueryTraverser
import org.knora.webapi.messages.util.search.gravsearch.prequery.InferenceOptimizationService
import org.knora.webapi.messages.util.search.gravsearch.transformers.{ConstructTransformer, OntologyInferencer}
import org.knora.webapi.messages.util.search.gravsearch.types.GravsearchTypeInspectionRunner
import org.knora.webapi.messages.util.standoff.{StandoffTagUtilV2, StandoffTagUtilV2Live}
import org.knora.webapi.responders.IriService
import org.knora.webapi.responders.admin._
import org.knora.webapi.responders.v1._
import org.knora.webapi.responders.v2._
import org.knora.webapi.responders.v2.ontology.{
  CardinalityHandler,
  CardinalityHandlerLive,
  OntologyHelpers,
  OntologyHelpersLive
}
import org.knora.webapi.routing._
import org.knora.webapi.routing.admin.{AuthenticatorService, ProjectsRouteZ}
import org.knora.webapi.slice.admin.api.service.{ProjectADMRestService, ProjectsADMRestServiceLive}
import org.knora.webapi.slice.admin.domain.service._
import org.knora.webapi.slice.admin.repo.service.KnoraProjectRepoLive
import org.knora.webapi.slice.common.api.{RestPermissionService, RestPermissionServiceLive}
import org.knora.webapi.slice.common.repo.service.PredicateObjectMapper
import org.knora.webapi.slice.ontology.api.service.{RestCardinalityService, RestCardinalityServiceLive}
import org.knora.webapi.slice.ontology.domain.service.{CardinalityService, OntologyRepo}
import org.knora.webapi.slice.ontology.repo.service.{
  OntologyCache,
  OntologyCacheLive,
  OntologyRepoLive,
  PredicateRepositoryLive
}
import org.knora.webapi.slice.resourceinfo.api.{ResourceInfoRoute, RestResourceInfoService}
import org.knora.webapi.slice.resourceinfo.domain.{IriConverter, ResourceInfoRepo}
import org.knora.webapi.store.cache.api.CacheService
import org.knora.webapi.store.cache.impl.CacheServiceInMemImpl
import org.knora.webapi.store.cache.{CacheServiceRequestMessageHandler, CacheServiceRequestMessageHandlerLive}
import org.knora.webapi.store.iiif.api.IIIFService
import org.knora.webapi.store.iiif.impl.{IIIFServiceMockImpl, IIIFServiceSipiImpl}
import org.knora.webapi.store.iiif.{IIIFRequestMessageHandler, IIIFRequestMessageHandlerLive}
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.impl.TriplestoreServiceLive
import org.knora.webapi.store.triplestore.upgrade.RepositoryUpdater
import org.knora.webapi.store.triplestore.{TriplestoreRequestMessageHandler, TriplestoreRequestMessageHandlerLive}
import org.knora.webapi.testcontainers.{FusekiTestContainer, SipiTestContainer}
import org.knora.webapi.testservices.TestClientService
import zio._

object LayersTest {

  /**
   * The `Environment`s that we require for the tests to run - with or without Sipi
   */
  type DefaultTestEnvironmentWithoutSipi = LayersLive.DspEnvironmentLive with FusekiTestContainer with TestClientService
  type DefaultTestEnvironmentWithSipi    = DefaultTestEnvironmentWithoutSipi with SipiTestContainer

  type CommonR0 = ActorSystem with AppConfigurations with IIIFService with JwtService with StringFormatter
  type CommonR =
    ApiRoutes
      with AppRouter
      with Authenticator
      with CacheService
      with CacheServiceRequestMessageHandler
      with CardinalityHandler
      with CardinalityService
      with CkanResponderV1
      with ConstructResponseUtilV2
      with ConstructTransformer
      with DspIngestClient
      with GravsearchTypeInspectionRunner
      with GroupsResponderADM
      with HttpServer
      with IIIFRequestMessageHandler
      with InferenceOptimizationService
      with IriConverter
      with IriService
      with KnoraProjectRepoLive
      with ListsResponderADM
      with ListsResponderV1
      with ListsResponderV2
      with MessageRelay
      with OntologyCache
      with OntologyHelpers
      with OntologyRepo
      with OntologyResponderV1
      with OntologyResponderV2
      with PermissionUtilADM
      with PermissionsResponderADM
      with PredicateObjectMapper
      with ProjectADMRestService
      with ProjectADMService
      with ProjectExportService
      with ProjectExportStorageService
      with ProjectImportService
      with ProjectsResponderADM
      with ProjectsResponderV1
      with QueryTraverser
      with RepositoryUpdater
      with ResourceInfoRepo
      with ResourceUtilV2
      with ResourcesResponderV1
      with ResourcesResponderV2
      with RestCardinalityService
      with RestPermissionService
      with RestResourceInfoService
      with SearchResponderV1
      with SearchResponderV2
      with SipiResponderADM
      with OntologyInferencer
      with StandoffResponderV1
      with StandoffResponderV2
      with StandoffTagUtilV2
      with State
      with StoresResponderADM
      with TestClientService
      with TriplestoreRequestMessageHandler
      with TriplestoreService
      with UsersResponderADM
      with UsersResponderV1
      with ValueUtilV1
      with ValuesResponderV1
      with ValuesResponderV2

  private val commonLayersForAllIntegrationTests =
    ZLayer.makeSome[CommonR0, CommonR](
      ApiRoutes.layer,
      AppRouter.layer,
      AuthenticationMiddleware.layer,
      AuthenticatorLive.layer,
      AuthenticatorService.layer,
      CacheServiceInMemImpl.layer,
      CacheServiceRequestMessageHandlerLive.layer,
      CardinalityHandlerLive.layer,
      CardinalityService.layer,
      CkanResponderV1Live.layer,
      ConstructResponseUtilV2Live.layer,
      ConstructTransformer.layer,
      DspIngestClientLive.layer,
      GravsearchTypeInspectionRunner.layer,
      GroupsResponderADMLive.layer,
      HttpServer.layer,
      HttpServerZ.layer,
      IIIFRequestMessageHandlerLive.layer,
      InferenceOptimizationService.layer,
      IriConverter.layer,
      IriService.layer,
      KnoraProjectRepoLive.layer,
      ListsResponderADMLive.layer,
      ListsResponderV1Live.layer,
      ListsResponderV2Live.layer,
      MessageRelayLive.layer,
      OntologyCacheLive.layer,
      OntologyHelpersLive.layer,
      OntologyRepoLive.layer,
      OntologyResponderV1Live.layer,
      OntologyResponderV2Live.layer,
      PermissionUtilADMLive.layer,
      PermissionsResponderADMLive.layer,
      PredicateObjectMapper.layer,
      PredicateRepositoryLive.layer,
      ProjectADMServiceLive.layer,
      ProjectExportServiceLive.layer,
      ProjectExportStorageServiceLive.layer,
      ProjectImportServiceLive.layer,
      ProjectsADMRestServiceLive.layer,
      ProjectsResponderADMLive.layer,
      ProjectsResponderV1Live.layer,
      ProjectsRouteZ.layer,
      QueryTraverser.layer,
      RepositoryUpdater.layer,
      ResourceInfoRepo.layer,
      ResourceInfoRoute.layer,
      ResourceUtilV2Live.layer,
      ResourcesResponderV1Live.layer,
      ResourcesResponderV2Live.layer,
      RestCardinalityServiceLive.layer,
      RestPermissionServiceLive.layer,
      RestResourceInfoService.layer,
      SearchResponderV1Live.layer,
      SearchResponderV2Live.layer,
      SipiResponderADMLive.layer,
      OntologyInferencer.layer,
      StandoffResponderV1Live.layer,
      StandoffResponderV2Live.layer,
      StandoffTagUtilV2Live.layer,
      State.layer,
      StoresResponderADMLive.layer,
      TestClientService.layer,
      TriplestoreRequestMessageHandlerLive.layer,
      TriplestoreServiceLive.layer,
      UsersResponderADMLive.layer,
      UsersResponderV1Live.layer,
      ValueUtilV1Live.layer,
      ValuesResponderV1Live.layer,
      ValuesResponderV2Live.layer
    )

  private val fusekiAndSipiTestcontainers =
    ZLayer.make[
      FusekiTestContainer
        with SipiTestContainer
        with AppConfigurations
        with JwtService
        with IIIFService
        with StringFormatter
    ](
      AppConfigForTestContainers.testcontainers,
      FusekiTestContainer.layer,
      SipiTestContainer.layer,
      IIIFServiceSipiImpl.layer,
      JwtServiceLive.layer,
      StringFormatter.test
    )

  private val fusekiTestcontainers =
    ZLayer.make[FusekiTestContainer with AppConfigurations with JwtService with IIIFService with StringFormatter](
      AppConfigForTestContainers.fusekiOnlyTestcontainer,
      FusekiTestContainer.layer,
      IIIFServiceMockImpl.layer,
      JwtServiceLive.layer,
      StringFormatter.test
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
