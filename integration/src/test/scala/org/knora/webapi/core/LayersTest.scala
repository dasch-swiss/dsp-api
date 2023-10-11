/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.core

import org.apache.pekko
import zio._

import org.knora.webapi.config.AppConfig.AppConfigurations
import org.knora.webapi.config.AppConfigForTestContainers
import org.knora.webapi.http.middleware.AuthenticationMiddleware
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.util._
import org.knora.webapi.messages.util.search.QueryTraverser
import org.knora.webapi.messages.util.search.gravsearch.prequery.InferenceOptimizationService
import org.knora.webapi.messages.util.search.gravsearch.transformers.ConstructTransformer
import org.knora.webapi.messages.util.search.gravsearch.transformers.OntologyInferencer
import org.knora.webapi.messages.util.search.gravsearch.types.GravsearchTypeInspectionRunner
import org.knora.webapi.messages.util.standoff.StandoffTagUtilV2
import org.knora.webapi.messages.util.standoff.StandoffTagUtilV2Live
import org.knora.webapi.responders.IriService
import org.knora.webapi.responders.admin._
import org.knora.webapi.responders.v2._
import org.knora.webapi.responders.v2.ontology.CardinalityHandler
import org.knora.webapi.responders.v2.ontology.CardinalityHandlerLive
import org.knora.webapi.responders.v2.ontology.OntologyHelpers
import org.knora.webapi.responders.v2.ontology.OntologyHelpersLive
import org.knora.webapi.routing._
import org.knora.webapi.routing.admin.AuthenticatorService
import org.knora.webapi.routing.admin.ProjectsRouteZ
import org.knora.webapi.slice.admin.api.{ProjectsEndpoints, ProjectsEndpointsHandlerF}
import org.knora.webapi.slice.admin.api.service.ProjectADMRestService
import org.knora.webapi.slice.admin.api.service.ProjectsADMRestServiceLive
import org.knora.webapi.slice.admin.domain.service._
import org.knora.webapi.slice.admin.repo.service.KnoraProjectRepoLive
import org.knora.webapi.slice.common.api.RestPermissionService
import org.knora.webapi.slice.common.api.RestPermissionServiceLive
import org.knora.webapi.slice.common.repo.service.PredicateObjectMapper
import org.knora.webapi.slice.ontology.api.service.RestCardinalityService
import org.knora.webapi.slice.ontology.api.service.RestCardinalityServiceLive
import org.knora.webapi.slice.ontology.domain.service.CardinalityService
import org.knora.webapi.slice.ontology.domain.service.OntologyRepo
import org.knora.webapi.slice.ontology.repo.service.OntologyCache
import org.knora.webapi.slice.ontology.repo.service.OntologyCacheLive
import org.knora.webapi.slice.ontology.repo.service.OntologyRepoLive
import org.knora.webapi.slice.ontology.repo.service.PredicateRepositoryLive
import org.knora.webapi.slice.resourceinfo.api.ResourceInfoRoute
import org.knora.webapi.slice.resourceinfo.api.RestResourceInfoService
import org.knora.webapi.slice.resourceinfo.domain.IriConverter
import org.knora.webapi.slice.resourceinfo.domain.ResourceInfoRepo
import org.knora.webapi.store.cache.CacheServiceRequestMessageHandler
import org.knora.webapi.store.cache.CacheServiceRequestMessageHandlerLive
import org.knora.webapi.store.cache.api.CacheService
import org.knora.webapi.store.cache.impl.CacheServiceInMemImpl
import org.knora.webapi.store.iiif.IIIFRequestMessageHandler
import org.knora.webapi.store.iiif.IIIFRequestMessageHandlerLive
import org.knora.webapi.store.iiif.api.IIIFService
import org.knora.webapi.store.iiif.impl.IIIFServiceMockImpl
import org.knora.webapi.store.iiif.impl.IIIFServiceSipiImpl
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.impl.TriplestoreServiceLive
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

  type CommonR0 = ActorSystem with AppConfigurations with IIIFService with JwtService with StringFormatter
  type CommonR =
    ApiRoutes
      with AppRouter
      with Authenticator
      with CacheService
      with CacheServiceRequestMessageHandler
      with CardinalityHandler
      with CardinalityService
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
      with ListsResponderV2
      with MessageRelay
      with OntologyCache
      with OntologyHelpers
      with OntologyRepo
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
      with QueryTraverser
      with RepositoryUpdater
      with ResourceInfoRepo
      with ResourceUtilV2
      with ResourcesResponderV2
      with RestCardinalityService
      with RestPermissionService
      with RestResourceInfoService
      with SearchResponderV2
      with SipiResponderADM
      with OntologyInferencer
      with StandoffResponderV2
      with StandoffTagUtilV2
      with State
      with StoresResponderADM
      with TestClientService
      with TriplestoreService
      with UsersResponderADM
      with ValuesResponderV2

  private val commonLayersForAllIntegrationTests =
    ZLayer.makeSome[CommonR0, CommonR](
      HandlerMapperF.layer,
      ApiRoutes.layer,
      AppRouter.layer,
      AuthenticationMiddleware.layer,
      AuthenticatorLive.layer,
      AuthenticatorService.layer,
      BaseEndpoints.layer,
      CacheServiceInMemImpl.layer,
      CacheServiceRequestMessageHandlerLive.layer,
      CardinalityHandlerLive.layer,
      CardinalityService.layer,
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
      ListsResponderV2Live.layer,
      MessageRelayLive.layer,
      OntologyCacheLive.layer,
      OntologyHelpersLive.layer,
      OntologyInferencer.layer,
      OntologyRepoLive.layer,
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
      ProjectsEndpoints.layer,
      ProjectsEndpointsHandlerF.layer,
      ProjectsResponderADMLive.layer,
      ProjectsRouteZ.layer,
      QueryTraverser.layer,
      RepositoryUpdater.layer,
      ResourceInfoRepo.layer,
      ResourceInfoRoute.layer,
      ResourceUtilV2Live.layer,
      ResourcesResponderV2Live.layer,
      RestCardinalityServiceLive.layer,
      RestPermissionServiceLive.layer,
      RestResourceInfoService.layer,
      SearchResponderV2Live.layer,
      SipiResponderADMLive.layer,
      StandoffResponderV2Live.layer,
      StandoffTagUtilV2Live.layer,
      State.layer,
      StoresResponderADMLive.layer,
      TestClientService.layer,
      TriplestoreServiceLive.layer,
      UsersResponderADMLive.layer,
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
   * @param system An optional [[pekko.actor.ActorSystem]] for use with Akka's [[pekko.testkit.TestKit]]
   * @return a [[ULayer]] with the [[DefaultTestEnvironmentWithoutSipi]]
   */
  def integrationTestsWithFusekiTestcontainers(
    system: Option[pekko.actor.ActorSystem] = None
  ): ULayer[DefaultTestEnvironmentWithoutSipi] = {
    // Due to bug in Scala 2 compiler invoking methods with by-name parameters in provide/provideSome method does not work
    // assign the layer to a temp val and use it in the ZLayer.make
    val temp = system.map(ActorSystemTest.layer).getOrElse(ActorSystem.layer)
    ZLayer.make[DefaultTestEnvironmentWithoutSipi](
      commonLayersForAllIntegrationTests,
      fusekiTestcontainers,
      temp
    )
  }

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
