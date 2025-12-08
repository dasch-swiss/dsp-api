/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.core

import zio.*
import zio.telemetry.opentelemetry.tracing.Tracing

import org.knora.webapi.config.AppConfig
import org.knora.webapi.config.AppConfig.AppConfigurations
import org.knora.webapi.config.DspIngestConfig
import org.knora.webapi.config.Features
import org.knora.webapi.config.GraphRoute
import org.knora.webapi.config.JwtConfig
import org.knora.webapi.config.Sipi
import org.knora.webapi.config.Triplestore
import org.knora.webapi.messages.util.*
import org.knora.webapi.messages.util.search.gravsearch.transformers.OntologyInferencer
import org.knora.webapi.messages.util.standoff.StandoffTagUtilV2
import org.knora.webapi.messages.util.standoff.StandoffTagUtilV2Live
import org.knora.webapi.responders.IriService
import org.knora.webapi.responders.admin.*
import org.knora.webapi.responders.v2.*
import org.knora.webapi.responders.v2.ontology.CardinalityHandler
import org.knora.webapi.responders.v2.resources.CreateResourceV2Handler
import org.knora.webapi.slice.`export`.api.ExportApiModule
import org.knora.webapi.slice.admin.AdminModule
import org.knora.webapi.slice.admin.api.*
import org.knora.webapi.slice.admin.domain.service.*
import org.knora.webapi.slice.api.ApiModule
import org.knora.webapi.slice.common.ApiComplexV2JsonLdRequestParser
import org.knora.webapi.slice.common.CommonModule
import org.knora.webapi.slice.common.api.*
import org.knora.webapi.slice.common.repo.service.PredicateObjectMapper
import org.knora.webapi.slice.infrastructure.InfrastructureModule
import org.knora.webapi.slice.infrastructure.OpenTelemetry
import org.knora.webapi.slice.ontology.OntologyModule
import org.knora.webapi.slice.ontology.api.OntologyApiModule
import org.knora.webapi.slice.resources.ResourcesModule
import org.knora.webapi.slice.resources.api.ResourcesApiModule
import org.knora.webapi.slice.resources.repo.service.ResourcesRepo
import org.knora.webapi.slice.resources.repo.service.ResourcesRepoLive
import org.knora.webapi.slice.resources.service.ReadResourcesService
import org.knora.webapi.slice.resources.service.ReadResourcesServiceLive
import org.knora.webapi.slice.security.SecurityModule
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
    ApiModule.Dependencies &
    AdminApiModule.Provided &
    AdminModule.Provided &
    ApiComplexV2JsonLdRequestParser &
    AssetPermissionsResponder &
    AuthorizationRestService &
    CardinalityHandler &
    CommonModule.Provided &
    ConstructResponseUtilV2 &
    CreateResourceV2Handler &
    DefaultObjectAccessPermissionService &
    IIIFRequestMessageHandler &
    InfrastructureModule.Provided &
    IriService &
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
    ReadResourcesService &
    RepositoryUpdater &
    ResourceUtilV2 &
    ResourcesRepo &
    ResourcesResponderV2 &
    SearchResponderV2Module.Provided &
    SecurityModule.Provided &
    ShaclApiModule.Provided &
    ShaclEndpoints &
    ShaclModule.Provided &
    SipiService &
    StandoffResponderV2 &
    StandoffTagUtilV2 &
    State &
    Tracing &
    ValuesResponderV2 &
    io.opentelemetry.api.OpenTelemetry
    // format: on

  val remainingLayer: URLayer[AppConfigurations, Environment] =
    ZLayer.makeSome[
      AppConfig & DspIngestConfig & Sipi & Triplestore & Features & GraphRoute & JwtConfig,
      self.Environment,
    ](
      // ZLayer.Debug.mermaid,
      AdminApiModule.layer,
      AdminModule.layer,
      ApiComplexV2JsonLdRequestParser.layer,
      AssetPermissionsResponder.layer,
      AuthorizationRestService.layer,
      BaseEndpoints.layer,
      CardinalityHandler.layer,
      CreateResourceV2Handler.layer,
      CommonModule.layer,
      ConstructResponseUtilV2.layer,
      DspIngestClient.layer,
      ExportApiModule.layer,
      IIIFRequestMessageHandlerLive.layer,
      InfrastructureModule.layer,
      IriService.layer,
      KnoraResponseRenderer.layer,
      ListsResponder.layer,
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
      ReadResourcesServiceLive.layer,
      ResourceUtilV2.layer,
      ResourcesApiModule.layer,
      ResourcesModule.layer,
      ResourcesRepoLive.layer,
      ResourcesResponderV2.layer,
      SearchResponderV2Module.layer,
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
