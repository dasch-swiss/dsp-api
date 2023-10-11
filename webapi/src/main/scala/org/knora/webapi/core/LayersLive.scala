/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.core

import zio.ULayer
import zio.ZLayer
import org.knora.webapi.config.AppConfig
import org.knora.webapi.config.AppConfig.AppConfigurations
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
import org.knora.webapi.slice.common.api.{BaseEndpoints, HandlerMapperF, RestPermissionService, RestPermissionServiceLive}
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
import org.knora.webapi.store.iiif.impl.IIIFServiceSipiImpl
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.impl.TriplestoreServiceLive
import org.knora.webapi.store.triplestore.upgrade.RepositoryUpdater

object LayersLive {

  /**
   * The `Environment` that we require to exist at startup.
   */
  type DspEnvironmentLive =
    ActorSystem
      with ApiRoutes
      with AppConfigurations
      with AppRouter
      with Authenticator
      with CacheService
      with CacheServiceRequestMessageHandler
      with CardinalityHandler
      with CardinalityService
      with ConstructResponseUtilV2
      with ConstructTransformer
      with GravsearchTypeInspectionRunner
      with GroupsResponderADM
      with HttpServer
      with IIIFRequestMessageHandler
      with IIIFService
      with InferenceOptimizationService
      with IriService
      with IriConverter
      with JwtService
      with KnoraProjectRepo
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
      with ResourceUtilV2
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
      with StringFormatter
      with TriplestoreService
      with UsersResponderADM
      with ValuesResponderV2

  /**
   * All effect layers needed to provide the `Environment`
   */
  val dspLayersLive: ULayer[DspEnvironmentLive] =
    ZLayer.make[DspEnvironmentLive](
      ActorSystem.layer,
      ApiRoutes.layer,
      AppConfig.layer,
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
      HandlerMapperF.layer,
      HttpServer.layer,
      HttpServerZ.layer, // this is the new ZIO HTTP server layer
      IIIFRequestMessageHandlerLive.layer,
      IIIFServiceSipiImpl.layer,
      InferenceOptimizationService.layer,
      IriConverter.layer,
      IriService.layer,
      JwtServiceLive.layer,
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
      StringFormatter.live,
      TriplestoreServiceLive.layer,
      UsersResponderADMLive.layer,
      ValuesResponderV2Live.layer
    )
}
