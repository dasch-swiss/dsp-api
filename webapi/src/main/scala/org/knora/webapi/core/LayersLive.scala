/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.core

import org.apache.pekko.actor.ActorSystem
import zio.ULayer
import zio.ZLayer

import org.knora.webapi.config.AppConfig
import org.knora.webapi.config.AppConfig.AppConfigurations
import org.knora.webapi.config.InstrumentationServerConfig
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
import org.knora.webapi.responders.admin.ListsResponder
import org.knora.webapi.responders.admin._
import org.knora.webapi.responders.v2._
import org.knora.webapi.responders.v2.ontology.CardinalityHandler
import org.knora.webapi.responders.v2.ontology.CardinalityHandlerLive
import org.knora.webapi.responders.v2.ontology.OntologyHelpers
import org.knora.webapi.responders.v2.ontology.OntologyHelpersLive
import org.knora.webapi.routing._
import org.knora.webapi.slice.admin.AdminModule
import org.knora.webapi.slice.admin.api.AdminApiModule
import org.knora.webapi.slice.admin.api._
import org.knora.webapi.slice.admin.api.service.PermissionsRestService
import org.knora.webapi.slice.admin.api.service.UsersRestService
import org.knora.webapi.slice.admin.domain.service._
import org.knora.webapi.slice.common.api._
import org.knora.webapi.slice.common.repo.service.PredicateObjectMapper
import org.knora.webapi.slice.infrastructure.api.ManagementEndpoints
import org.knora.webapi.slice.infrastructure.api.ManagementRoutes
import org.knora.webapi.slice.ontology.api.service.RestCardinalityService
import org.knora.webapi.slice.ontology.api.service.RestCardinalityServiceLive
import org.knora.webapi.slice.ontology.domain.service.CardinalityService
import org.knora.webapi.slice.ontology.repo.service.OntologyCache
import org.knora.webapi.slice.ontology.repo.service.OntologyCacheLive
import org.knora.webapi.slice.ontology.repo.service.OntologyRepoLive
import org.knora.webapi.slice.ontology.repo.service.PredicateRepositoryLive
import org.knora.webapi.slice.resourceinfo.ResourceInfoLayers
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
      AssetPermissionsResponder & Authenticator & AuthorizationRestService &
      CacheServiceRequestMessageHandler & CardinalityHandler & ConstructResponseUtilV2 &
      GravsearchTypeInspectionRunner & GroupsResponderADM & HttpServer &
      IIIFRequestMessageHandler & InferenceOptimizationService & InstrumentationServerConfig & IriConverter &
      JwtService & ListsResponder & ListsResponderV2 & MessageRelay & OntologyCache & OntologyHelpers &
      OntologyInferencer & OntologyResponderV2 & PermissionsResponderADM & PermissionsRestService &
      PermissionUtilADM & ProjectExportService & ProjectExportStorageService &
      ProjectImportService & ProjectsResponderADM & QueryTraverser & RepositoryUpdater & ResourcesResponderV2 &
      ResourceUtilV2 & ResourceUtilV2 & RestCardinalityService & SearchApiRoutes &
      SearchResponderV2 & SipiService & StandoffResponderV2 & StandoffTagUtilV2 & State & StringFormatter &
      TriplestoreService & UserService & UsersResponder & UsersRestService & ValuesResponderV2

  /**
   * All effect layers needed to provide the `Environment`
   */
  val dspLayersLive: ULayer[DspEnvironmentLive] =
    ZLayer.make[DspEnvironmentLive](
      AdminApiModule.layer,
      AdminModule.layer,
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
      GravsearchTypeInspectionRunner.layer,
      GroupsResponderADM.layer,
      HandlerMapper.layer,
      HttpServer.layer,
      IIIFRequestMessageHandlerLive.layer,
      InferenceOptimizationService.layer,
      IriConverter.layer,
      IriService.layer,
      JwtServiceLive.layer,
      KnoraResponseRenderer.layer,
      KnoraUserToUserConverter.layer,
      ListsResponder.layer,
      ListsResponderV2.layer,
      ManagementEndpoints.layer,
      ManagementRoutes.layer,
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
      ProjectExportServiceLive.layer,
      ProjectExportStorageServiceLive.layer,
      ProjectImportServiceLive.layer,
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
      StringFormatter.live,
      TapirToPekkoInterpreter.layer,
      TriplestoreServiceLive.layer,
      UserService.layer,
      UsersResponder.layer,
      ValuesResponderV2Live.layer,
      org.knora.webapi.core.ActorSystem.layer,
    )
}
