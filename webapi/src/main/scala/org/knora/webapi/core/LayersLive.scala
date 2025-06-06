/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.core

import org.apache.pekko.actor.ActorSystem
import zio.*
import zio.ULayer
import zio.ZLayer

import org.knora.webapi.config.AppConfig
import org.knora.webapi.config.InstrumentationServerConfig
import org.knora.webapi.messages.util.*
import org.knora.webapi.messages.util.search.QueryTraverser
import org.knora.webapi.messages.util.search.gravsearch.transformers.OntologyInferencer
import org.knora.webapi.messages.util.search.gravsearch.types.GravsearchTypeInspectionRunner
import org.knora.webapi.messages.util.standoff.StandoffTagUtilV2
import org.knora.webapi.messages.util.standoff.StandoffTagUtilV2Live
import org.knora.webapi.responders.IriService
import org.knora.webapi.responders.admin.*
import org.knora.webapi.responders.admin.ListsResponder
import org.knora.webapi.responders.v2.*
import org.knora.webapi.responders.v2.ontology.CardinalityHandler
import org.knora.webapi.routing.*
import org.knora.webapi.slice.admin.AdminModule
import org.knora.webapi.slice.admin.api.*
import org.knora.webapi.slice.admin.api.AdminApiModule
import org.knora.webapi.slice.admin.api.service.GroupRestService
import org.knora.webapi.slice.admin.api.service.PermissionRestService
import org.knora.webapi.slice.admin.api.service.ProjectRestService
import org.knora.webapi.slice.admin.api.service.UserRestService
import org.knora.webapi.slice.admin.domain.service.*
import org.knora.webapi.slice.common.ApiComplexV2JsonLdRequestParser
import org.knora.webapi.slice.common.BaseModule
import org.knora.webapi.slice.common.api.*
import org.knora.webapi.slice.common.repo.service.PredicateObjectMapper
import org.knora.webapi.slice.infrastructure.InfrastructureModule
import org.knora.webapi.slice.infrastructure.OpenTelemetry
import org.knora.webapi.slice.infrastructure.api.ManagementEndpoints
import org.knora.webapi.slice.infrastructure.api.ManagementRoutes
import org.knora.webapi.slice.lists.api.ListsApiModule
import org.knora.webapi.slice.lists.domain.ListsService
import org.knora.webapi.slice.ontology.CoreModule
import org.knora.webapi.slice.ontology.api.OntologyApiModule
import org.knora.webapi.slice.ontology.domain.service.CardinalityService
import org.knora.webapi.slice.ontology.domain.service.OntologyRepo
import org.knora.webapi.slice.ontology.repo.service.OntologyCache
import org.knora.webapi.slice.resourceinfo.ResourceInfoLayers
import org.knora.webapi.slice.resources.ResourcesModule
import org.knora.webapi.slice.resources.api.ResourcesApiModule
import org.knora.webapi.slice.resources.api.ResourcesApiRoutes
import org.knora.webapi.slice.resources.repo.service.ResourcesRepo
import org.knora.webapi.slice.resources.repo.service.ResourcesRepoLive
import org.knora.webapi.slice.search.api.SearchApiRoutes
import org.knora.webapi.slice.search.api.SearchEndpoints
import org.knora.webapi.slice.security.SecurityModule
import org.knora.webapi.slice.security.api.AuthenticationApiModule
import org.knora.webapi.slice.shacl.ShaclModule
import org.knora.webapi.slice.shacl.api.ShaclApiModule
import org.knora.webapi.slice.shacl.api.ShaclApiRoutes
import org.knora.webapi.store.iiif.IIIFRequestMessageHandler
import org.knora.webapi.store.iiif.IIIFRequestMessageHandlerLive
import org.knora.webapi.store.iiif.api.SipiService
import org.knora.webapi.store.iiif.impl.SipiServiceLive
import org.knora.webapi.store.triplestore.upgrade.RepositoryUpdater

object LayersLive {

  /**
   * The `Environment` that we require to exist at startup.
   */
  type DspEnvironmentLive =
    // format: off
    ActorSystem &
    AdminApiEndpoints &
    AdminModule.Provided &
    ApiComplexV2JsonLdRequestParser &
    ApiRoutes &
    ApiV2Endpoints &
    AppConfig.AppConfigurations &
    AssetPermissionsResponder &
    AuthenticationApiModule.Provided &
    AuthorizationRestService &
    BaseModule.Provided &
    CardinalityHandler &
    ConstructResponseUtilV2 &
    CoreModule.Provided &
    DefaultObjectAccessPermissionService &
    GroupRestService &
    HttpServer &
    IIIFRequestMessageHandler &
    InfrastructureModule.Provided &
    InstrumentationServerConfig &
    ListsApiModule.Provided &
    ListsResponder &
    ListsService &
    MessageRelay &
    OntologyApiModule.Provided &
    OntologyInferencer &
    OntologyResponderV2 &
    PermissionRestService &
    PermissionUtilADM &
    PermissionsResponder &
    ProjectExportService &
    ProjectExportStorageService &
    ProjectImportService &
    ProjectRestService &
    RepositoryUpdater &
    ResourceUtilV2 &
    ResourcesApiRoutes &
    ResourcesResponderV2 &
    ResourcesRepo &
    SecurityModule.Provided &
    SearchApiRoutes &
    SearchResponderV2Module.Provided &
    SecurityModule.Provided &
    ShaclApiModule.Provided &
    ShaclModule.Provided &
    SipiService &
    StandoffResponderV2 &
    StandoffTagUtilV2 &
    State &
    UserRestService &
    ValuesResponderV2
    // format: on

  /**
   * All effect layers needed to provide the `Environment`
   */
  val dspLayersLive: ULayer[DspEnvironmentLive] =
    ZLayer.make[DspEnvironmentLive](
      AdminApiModule.layer,
      AdminModule.layer,
      ApiComplexV2JsonLdRequestParser.layer,
      ApiRoutes.layer,
      ApiV2Endpoints.layer,
      AppConfig.layer,
      AssetPermissionsResponder.layer,
      AuthenticationApiModule.layer,
      AuthorizationRestService.layer,
      BaseEndpoints.layer,
      BaseModule.layer,
      CardinalityHandler.layer,
      ConstructResponseUtilV2.layer,
      CoreModule.layer,
      DspIngestClientLive.layer,
      HandlerMapper.layer,
      HttpServer.layer,
      IIIFRequestMessageHandlerLive.layer,
      InfrastructureModule.layer,
      IriService.layer,
      KnoraResponseRenderer.layer,
      ListsApiModule.layer,
      ListsResponder.layer,
      ListsService.layer,
      ManagementEndpoints.layer,
      ManagementRoutes.layer,
      MessageRelayLive.layer,
      OntologyApiModule.layer,
      OntologyResponderV2.layer,
      OpenTelemetry.layer,
      PekkoActorSystem.layer,
      PermissionUtilADMLive.layer,
      PermissionsResponder.layer,
      PredicateObjectMapper.layer,
      ProjectExportServiceLive.layer,
      ProjectExportStorageServiceLive.layer,
      ProjectImportService.layer,
      RepositoryUpdater.layer,
      ResourceInfoLayers.live,
      ResourceUtilV2Live.layer,
      ResourcesApiModule.layer,
      ResourcesModule.layer,
      ResourcesRepoLive.layer,
      ResourcesResponderV2.layer,
      SearchApiRoutes.layer,
      SearchEndpoints.layer,
      SearchResponderV2Module.layer,
      SecurityModule.layer,
      ShaclApiModule.layer,
      ShaclModule.layer,
      SipiServiceLive.layer,
      StandoffResponderV2.layer,
      StandoffTagUtilV2Live.layer,
      State.layer,
      TapirToPekkoInterpreter.layer,
      ValuesResponderV2.layer,
      // ZLayer.Debug.mermaid,
    )
}
