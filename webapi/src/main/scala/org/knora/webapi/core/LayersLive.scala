/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.core

import zio.*

import org.knora.webapi.config.AppConfig
import org.knora.webapi.config.AppConfig.AppConfigurations
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
import org.knora.webapi.responders.v2.*
import org.knora.webapi.responders.v2.ontology.CardinalityHandler
import org.knora.webapi.routing.*
import org.knora.webapi.slice.admin.AdminModule
import org.knora.webapi.slice.admin.api.*
import org.knora.webapi.slice.admin.domain.service.*
import org.knora.webapi.slice.api.v2.ApiV2ServerEndpoints
import org.knora.webapi.slice.common.ApiComplexV2JsonLdRequestParser
import org.knora.webapi.slice.common.CommonModule
import org.knora.webapi.slice.common.CommonModule.Provided
import org.knora.webapi.slice.common.api.*
import org.knora.webapi.slice.common.repo.service.PredicateObjectMapper
import org.knora.webapi.slice.common.service.IriConverter
import org.knora.webapi.slice.infrastructure.InfrastructureModule
import org.knora.webapi.slice.infrastructure.InfrastructureModule.Provided
import org.knora.webapi.slice.infrastructure.JwtService
import org.knora.webapi.slice.infrastructure.OpenTelemetry
import org.knora.webapi.slice.infrastructure.api.ManagementEndpoints
import org.knora.webapi.slice.infrastructure.api.ManagementRestService
import org.knora.webapi.slice.infrastructure.api.ManagementServerEndpoints
import org.knora.webapi.slice.lists.api.ListsApiModule
import org.knora.webapi.slice.ontology.OntologyModule
import org.knora.webapi.slice.ontology.OntologyModule.Provided
import org.knora.webapi.slice.ontology.api.OntologyApiModule
import org.knora.webapi.slice.ontology.domain.service.CardinalityService
import org.knora.webapi.slice.ontology.domain.service.OntologyRepo
import org.knora.webapi.slice.ontology.repo.service.OntologyCache
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
import org.knora.webapi.slice.shacl.api.ShaclEndpoints
import org.knora.webapi.store.iiif.IIIFRequestMessageHandler
import org.knora.webapi.store.iiif.IIIFRequestMessageHandlerLive
import org.knora.webapi.store.iiif.api.SipiService
import org.knora.webapi.store.iiif.impl.SipiServiceLive
import org.knora.webapi.store.triplestore.upgrade.RepositoryUpdater
import org.knora.webapi.util.Logger

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
    Endpoints &
    IIIFRequestMessageHandler &
    InfrastructureModule.Provided &
    ListsApiModule.Provided &
    ListsResponder &
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
    ResourcesApiServerEndpoints &
    ResourcesRepo &
    ResourcesResponderV2 &
    SearchResponderV2Module.Provided &
    SearchServerEndpoints &
    SecurityModule.Provided &
    SecurityModule.Provided &
    ShaclApiModule.Provided &
    ShaclEndpoints &
    ShaclModule.Provided &
    SipiService &
    StandoffResponderV2 &
    StandoffTagUtilV2 &
    State &
    ValuesResponderV2
    // format: on

  val remainingLayer: URLayer[AppConfigurations, Environment] =
    ZLayer.makeSome[
      AppConfig & DspIngestConfig & KnoraApi & Sipi & Triplestore & Features & GraphRoute & JwtConfig &
        OpenTelemetryConfig,
      self.Environment,
    ](
      // ZLayer.Debug.mermaid,
      AdminApiModule.layer,
      AdminModule.layer,
      ApiComplexV2JsonLdRequestParser.layer,
      ApiV2Endpoints.layer,
      ApiV2ServerEndpoints.layer,
      AssetPermissionsResponder.layer,
      AuthenticationApiModule.layer,
      AuthorizationRestService.layer,
      BaseEndpoints.layer,
      CardinalityHandler.layer,
      CommonModule.layer,
      ConstructResponseUtilV2.layer,
      DspIngestClient.layer,
      Endpoints.layer,
      IIIFRequestMessageHandlerLive.layer,
      InfrastructureModule.layer,
      IriService.layer,
      KnoraResponseRenderer.layer,
      ListsApiModule.layer,
      ListsResponder.layer,
      ManagementEndpoints.layer,
      ManagementRestService.layer,
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
      ResourceUtilV2.layer,
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
      ValuesResponderV2.layer,
    )

  private val loggerAndConfig: ULayer[AppConfigurations] = Logger.fromEnv() >>> AppConfig.layer

  val bootstrap: ULayer[AppConfigurations & Environment] = loggerAndConfig >+> LayersLive.remainingLayer
}
