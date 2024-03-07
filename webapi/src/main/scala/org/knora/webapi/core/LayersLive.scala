/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.core

import zio.ULayer
import zio.ZLayer
import org.knora.webapi.config.AppConfig
import org.knora.webapi.config.AppConfig.AppConfigurations
import org.knora.webapi.config.InstrumentationServerConfig
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.util.*
import org.knora.webapi.messages.util.search.QueryTraverser
import org.knora.webapi.messages.util.search.gravsearch.prequery.InferenceOptimizationService
import org.knora.webapi.messages.util.search.gravsearch.transformers.ConstructTransformer
import org.knora.webapi.messages.util.search.gravsearch.transformers.OntologyInferencer
import org.knora.webapi.messages.util.search.gravsearch.types.GravsearchTypeInspectionRunner
import org.knora.webapi.messages.util.standoff.StandoffTagUtilV2
import org.knora.webapi.messages.util.standoff.StandoffTagUtilV2Live
import org.knora.webapi.responders.IriService
import org.knora.webapi.responders.admin.*
import org.knora.webapi.responders.v2.*
import org.knora.webapi.responders.v2.ontology.CardinalityHandler
import org.knora.webapi.responders.v2.ontology.CardinalityHandlerLive
import org.knora.webapi.responders.v2.ontology.OntologyHelpers
import org.knora.webapi.responders.v2.ontology.OntologyHelpersLive
import org.knora.webapi.routing.*
import org.knora.webapi.slice.admin.api.*
import org.knora.webapi.slice.admin.api.service.GroupsRestServiceLive
import org.knora.webapi.slice.admin.api.service.MaintenanceRestService
import org.knora.webapi.slice.admin.api.service.PermissionsRestService
import org.knora.webapi.slice.admin.api.service.ProjectADMRestService
import org.knora.webapi.slice.admin.api.service.ProjectsADMRestServiceLive
import org.knora.webapi.slice.admin.api.service.StoreRestService
import org.knora.webapi.slice.admin.api.service.UsersRestService
import org.knora.webapi.slice.admin.domain.service.*
import org.knora.webapi.slice.admin.repo.service.KnoraGroupRepoLive
import org.knora.webapi.slice.admin.repo.service.KnoraProjectRepoLive
import org.knora.webapi.slice.admin.repo.service.KnoraUserRepoLive
import org.knora.webapi.slice.common.api.*
import org.knora.webapi.slice.common.repo.service.PredicateObjectMapper
import org.knora.webapi.slice.infrastructure.EntityCache
import org.knora.webapi.slice.ontology.api.service.RestCardinalityService
import org.knora.webapi.slice.ontology.api.service.RestCardinalityServiceLive
import org.knora.webapi.slice.ontology.domain.service.CardinalityService
import org.knora.webapi.slice.ontology.domain.service.OntologyRepo
import org.knora.webapi.slice.ontology.repo.service.OntologyCache
import org.knora.webapi.slice.ontology.repo.service.OntologyCacheLive
import org.knora.webapi.slice.ontology.repo.service.OntologyRepoLive
import org.knora.webapi.slice.ontology.repo.service.PredicateRepositoryLive
import org.knora.webapi.slice.resourceinfo.ResourceInfoLayers
import org.knora.webapi.slice.resourceinfo.api.service.RestResourceInfoService
import org.knora.webapi.slice.resourceinfo.domain.IriConverter
import org.knora.webapi.slice.search.api.SearchApiRoutes
import org.knora.webapi.slice.search.api.SearchEndpoints
import org.knora.webapi.store.cache.CacheService
import org.knora.webapi.store.cache.CacheServiceRequestMessageHandler
import org.knora.webapi.store.cache.CacheServiceRequestMessageHandlerLive
import org.knora.webapi.store.iiif.IIIFRequestMessageHandler
import org.knora.webapi.store.iiif.IIIFRequestMessageHandlerLive
import org.knora.webapi.store.iiif.api.SipiService
import org.knora.webapi.store.iiif.impl.SipiServiceLive
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.impl.TriplestoreServiceLive
import org.knora.webapi.store.triplestore.upgrade.RepositoryUpdater

object LayersLive {

  /**
   * The `Environment` that we require to exist at startup.
   */
  type DspEnvironmentLive =
    ActorSystem & AdminApiEndpoints & ApiRoutes & ApiV2Endpoints & AppConfigurations & AppRouter &
      AssetPermissionsResponder & Authenticator & AuthorizationRestService & CacheService &
      CacheServiceRequestMessageHandler & CardinalityHandler & CardinalityService & ConstructResponseUtilV2 &
      ConstructTransformer & GravsearchTypeInspectionRunner & GroupsService & GroupsResponderADM & HttpServer &
      IIIFRequestMessageHandler & InferenceOptimizationService & InstrumentationServerConfig & IriConverter &
      IriService & JwtService & KnoraProjectRepo & KnoraUserService & ListsResponder & ListsResponderV2 &
      MessageRelay & OntologyCache & OntologyHelpers & OntologyInferencer & OntologyRepo & OntologyResponderV2 &
      PasswordService & PermissionsResponderADM & PermissionsRestService & PermissionUtilADM & PredicateObjectMapper &
      ProjectADMRestService & ProjectADMService & ProjectExportService & ProjectExportStorageService &
      ProjectImportService & ProjectsResponderADM & QueryTraverser & RepositoryUpdater & ResourcesResponderV2 &
      ResourceUtilV2 & ResourceUtilV2 & RestCardinalityService & RestResourceInfoService & SearchApiRoutes &
      SearchResponderV2 & SipiService & StandoffResponderV2 & StandoffTagUtilV2 & State & StoreRestService &
      StringFormatter & TriplestoreService & UserService & UsersResponder & UsersRestService & ValuesResponderV2

  /**
   * All effect layers needed to provide the `Environment`
   */
  val dspLayersLive: ULayer[DspEnvironmentLive] =
    ZLayer.make[DspEnvironmentLive](
      ActorSystem.layer,
      AdminApiEndpoints.layer,
      AdminApiRoutes.layer,
      ApiRoutes.layer,
      ApiV2Endpoints.layer,
      AppConfig.layer,
      AppRouter.layer,
      AssetPermissionsResponder.layer,
      AuthenticatorLive.layer,
      AuthorizationRestServiceLive.layer,
      BaseEndpoints.layer,
      CacheService.layer,
      CacheServiceRequestMessageHandlerLive.layer,
      CardinalityHandlerLive.layer,
      CardinalityService.layer,
      ConstructResponseUtilV2Live.layer,
      ConstructTransformer.layer,
      DspIngestClientLive.layer,
      EntityCache.cacheManagerLayer,
      FilesEndpoints.layer,
      FilesEndpointsHandler.layer,
      GravsearchTypeInspectionRunner.layer,
      GroupsEndpoints.layer,
      GroupsEndpointsHandler.layer,
      GroupsResponderADMLive.layer,
      GroupsRestServiceLive.layer,
      GroupsService.layer,
      HandlerMapper.layer,
      HttpServer.layer,
      IIIFRequestMessageHandlerLive.layer,
      InferenceOptimizationService.layer,
      IriConverter.layer,
      IriService.layer,
      JwtServiceLive.layer,
      KnoraGroupRepoLive.layer,
      KnoraProjectRepoLive.layer,
      KnoraResponseRenderer.layer,
      KnoraUserRepoLive.layer,
      KnoraUserService.layer,
      KnoraUserToUserConverter.layer,
      ListRestService.layer,
      ListsEndpoints.layer,
      ListsEndpointsHandlers.layer,
      ListsResponder.layer,
      ListsResponderV2.layer,
      MaintenanceEndpoints.layer,
      MaintenanceEndpointsHandlers.layer,
      MaintenanceRestService.layer,
      MaintenanceServiceLive.layer,
      MessageRelayLive.layer,
      OntologyCacheLive.layer,
      OntologyHelpersLive.layer,
      OntologyInferencer.layer,
      OntologyRepoLive.layer,
      OntologyResponderV2Live.layer,
      PasswordService.layer,
      PermissionUtilADMLive.layer,
      PermissionsEndpoints.layer,
      PermissionsEndpointsHandlers.layer,
      PermissionsResponderADMLive.layer,
      PermissionsRestService.layer,
      PredicateObjectMapper.layer,
      PredicateRepositoryLive.layer,
      ProjectADMService.layer,
      ProjectExportServiceLive.layer,
      ProjectExportStorageServiceLive.layer,
      ProjectImportServiceLive.layer,
      ProjectsADMRestServiceLive.layer,
      ProjectsEndpoints.layer,
      ProjectsEndpointsHandler.layer,
      ProjectsResponderADMLive.layer,
      QueryTraverser.layer,
      RepositoryUpdater.layer,
      ResourceInfoLayers.live,
      ResourceUtilV2Live.layer,
      ResourcesResponderV2Live.layer,
      RestCardinalityServiceLive.layer,
      SearchApiRoutes.layer,
      SearchEndpoints.layer,
      SearchResponderV2Live.layer,
      SipiServiceLive.layer,
      StandoffResponderV2Live.layer,
      StandoffTagUtilV2Live.layer,
      State.layer,
      StoreEndpoints.layer,
      StoreEndpointsHandler.layer,
      StoreRestService.layer,
      StringFormatter.live,
      TapirToPekkoInterpreter.layer,
      TriplestoreServiceLive.layer,
      UserService.layer,
      UsersEndpoints.layer,
      UsersEndpointsHandler.layer,
      UsersResponder.layer,
      UsersRestService.layer,
      ValuesResponderV2Live.layer,
    )
}
