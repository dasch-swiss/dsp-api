/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.core

import zio.*
import zio.ULayer
import zio.ZLayer

import org.knora.webapi.config.AppConfig
import org.knora.webapi.config.DspIngestConfig
import org.knora.webapi.config.Features
import org.knora.webapi.config.GraphRoute
import org.knora.webapi.config.JwtConfig
import org.knora.webapi.config.KnoraApi
import org.knora.webapi.config.OpenTelemetryConfig
import org.knora.webapi.config.Sipi
import org.knora.webapi.config.Triplestore
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
import org.knora.webapi.slice.admin.AdminModule
import org.knora.webapi.slice.admin.api.*
import org.knora.webapi.slice.admin.api.AdminApiModule
import org.knora.webapi.slice.admin.domain.service.*
import org.knora.webapi.slice.common.ApiComplexV2JsonLdRequestParser
import org.knora.webapi.slice.common.CommonModule
import org.knora.webapi.slice.common.api.*
import org.knora.webapi.slice.common.repo.service.PredicateObjectMapper
import org.knora.webapi.slice.infrastructure.InfrastructureModule
import org.knora.webapi.slice.infrastructure.OpenTelemetry
import org.knora.webapi.slice.infrastructure.api.ManagementEndpoints
import org.knora.webapi.slice.infrastructure.api.ManagementServerEndpoints
import org.knora.webapi.slice.lists.api.ListsApiModule
import org.knora.webapi.slice.lists.domain.ListsService
import org.knora.webapi.slice.ontology.OntologyModule
import org.knora.webapi.slice.ontology.api.OntologyApiModule
import org.knora.webapi.slice.ontology.domain.service.CardinalityService
import org.knora.webapi.slice.ontology.domain.service.OntologyRepo
import org.knora.webapi.slice.ontology.repo.service.OntologyCache
import org.knora.webapi.slice.resourceinfo.ResourceInfoLayers
import org.knora.webapi.slice.resources.ResourcesModule
import org.knora.webapi.slice.resources.api.ResourcesApiModule
import org.knora.webapi.slice.resources.api.ResourcesApiServerEndpoints
import org.knora.webapi.slice.resources.repo.service.ResourcesRepo
import org.knora.webapi.slice.resources.repo.service.ResourcesRepoLive
import org.knora.webapi.slice.search.api.SearchEndpoints
import org.knora.webapi.slice.search.api.SearchServerEndpoints
import org.knora.webapi.slice.security.SecurityModule
import org.knora.webapi.slice.security.api.AuthenticationApiModule
import org.knora.webapi.slice.shacl.ShaclModule
import org.knora.webapi.slice.shacl.api.ShaclApiModule
import org.knora.webapi.slice.shacl.api.ShaclServerEndpoints
import org.knora.webapi.store.iiif.IIIFRequestMessageHandler
import org.knora.webapi.store.iiif.IIIFRequestMessageHandlerLive
import org.knora.webapi.store.iiif.api.SipiService
import org.knora.webapi.store.iiif.impl.SipiServiceLive
import org.knora.webapi.store.triplestore.upgrade.RepositoryUpdater

object LayersLive { self =>

  /**
   * The `Environment` that we require to exist at startup.
   */
  type Environment =
    // format: off
    AdminApiEndpoints &
    AdminApiModule.Provided &
    AdminModule.Provided &
    ApiComplexV2JsonLdRequestParser &
    ApiV2Endpoints &
    AssetPermissionsResponder &
    AuthenticationApiModule.Provided &
    AuthorizationRestService &
    CardinalityHandler &
    CommonModule.Provided &
    ConstructResponseUtilV2 &
    DefaultObjectAccessPermissionService &
    DspApiServerEndpoints &
    IIIFRequestMessageHandler &
    InfrastructureModule.Provided &
    ListsApiModule.Provided &
    ListsResponder &
    ListsService &
    MessageRelay &
    OntologyApiModule.Provided &
    OntologyInferencer &
    OntologyModule.Provided &
    OntologyResponderV2 &
    PermissionUtilADM &
    PermissionsResponder &
    ProjectExportService &
    ProjectExportStorageService &
    ProjectImportService &
    RepositoryUpdater &
    ResourceUtilV2 &
    ResourcesApiServerEndpoints&
    ResourcesRepo &
    ResourcesResponderV2 &
    SearchResponderV2Module.Provided &
    SearchServerEndpoints &
    SecurityModule.Provided &
    SecurityModule.Provided &
    ShaclApiModule.Provided &
    ShaclModule.Provided &
    SipiService &
    StandoffResponderV2 &
    StandoffTagUtilV2 &
    State &
    TapirToZioHttpInterpreter &
    ValuesResponderV2
    // format: on

  type Config = AppConfig & DspIngestConfig & Features & GraphRoute & JwtConfig & KnoraApi & OpenTelemetryConfig &
    Sipi & Triplestore

  val layer: URLayer[Config, self.Environment] =
    ZLayer.makeSome[Config, self.Environment](
      // ZLayer.Debug.mermaid,
      AdminApiModule.layer,
      AdminModule.layer,
      ApiComplexV2JsonLdRequestParser.layer,
      ApiV2Endpoints.layer,
      AssetPermissionsResponder.layer,
      AuthenticationApiModule.layer,
      AuthorizationRestService.layer,
      BaseEndpoints.layer,
      CardinalityHandler.layer,
      CommonModule.layer,
      ConstructResponseUtilV2.layer,
      DspApiServerEndpoints.layer,
      DspIngestClientLive.layer,
      IIIFRequestMessageHandlerLive.layer,
      InfrastructureModule.layer,
      IriService.layer,
      KnoraResponseRenderer.layer,
      ListsApiModule.layer,
      ListsResponder.layer,
      ListsService.layer,
      ManagementEndpoints.layer,
      ManagementServerEndpoints.layer,
      MessageRelayLive.layer,
      OntologyApiModule.layer,
      OntologyModule.layer,
      OntologyResponderV2.layer,
      OpenTelemetry.layer,
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
      SearchEndpoints.layer,
      SearchResponderV2Module.layer,
      SearchServerEndpoints.layer,
      SecurityModule.layer,
      ShaclApiModule.layer,
      ShaclModule.layer,
      SipiServiceLive.layer,
      StandoffResponderV2.layer,
      StandoffTagUtilV2Live.layer,
      State.layer,
      TapirToZioHttpInterpreter.layer,
      ValuesResponderV2.layer,
    )
}
