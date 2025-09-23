/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.core

import org.apache.pekko.actor.ActorSystem
import zio.*

import org.knora.webapi.config.AppConfig
import org.knora.webapi.config.DspIngestConfig
import org.knora.webapi.config.Features
import org.knora.webapi.config.GraphRoute
import org.knora.webapi.config.JwtConfig
import org.knora.webapi.config.KnoraApi
import org.knora.webapi.config.OpenTelemetryConfig
import org.knora.webapi.config.Sipi
import org.knora.webapi.config.Triplestore
import org.knora.webapi.core.Db.DbInitEnv
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
import org.knora.webapi.slice.common.ApiComplexV2JsonLdRequestParser
import org.knora.webapi.slice.common.CommonModule
import org.knora.webapi.slice.common.CommonModule.Provided
import org.knora.webapi.slice.common.api.*
import org.knora.webapi.slice.common.repo.service.PredicateObjectMapper
import org.knora.webapi.slice.common.service.IriConverter
import org.knora.webapi.slice.infrastructure.InfrastructureModule
import org.knora.webapi.slice.infrastructure.InfrastructureModule.Provided
import org.knora.webapi.slice.infrastructure.JwtService
import org.knora.webapi.slice.infrastructure.MetricsServer.MetricsServerEnv
import org.knora.webapi.slice.infrastructure.OpenTelemetry
import org.knora.webapi.slice.infrastructure.api.ManagementEndpoints
import org.knora.webapi.slice.infrastructure.api.ManagementRoutes
import org.knora.webapi.slice.lists.api.ListsApiModule
import org.knora.webapi.slice.ontology.OntologyModule
import org.knora.webapi.slice.ontology.OntologyModule.Provided
import org.knora.webapi.slice.ontology.api.OntologyApiModule
import org.knora.webapi.slice.ontology.domain.service.CardinalityService
import org.knora.webapi.slice.ontology.domain.service.OntologyRepo
import org.knora.webapi.slice.ontology.repo.service.OntologyCache
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
import org.knora.webapi.slice.shacl.api.ShaclEndpoints
import org.knora.webapi.store.iiif.IIIFRequestMessageHandler
import org.knora.webapi.store.iiif.IIIFRequestMessageHandlerLive
import org.knora.webapi.store.iiif.api.SipiService
import org.knora.webapi.store.iiif.impl.SipiServiceLive
import org.knora.webapi.store.triplestore.upgrade.RepositoryUpdater
import org.knora.webapi.util.Logger

object LayersLive { self =>

  /*
   * This layer composition is done really happhazardly.
   * This can certainly be improved a lot.
   * However, this brings the layers ependency graph from ~12.7k to ~2.9k nodes.
   * (By that I'm refering to the text output one gets when enabling `ZLayer.Debug.mermaid`.)
   * It is unclear how closely this relates to the actual graph construction code,
   * but I expect it will prevent the `class too big` error that we have seen in the past.
   * It seems to have little or no impact on compile time.
   */

  private type ConfigDependencies = Any
  private type ConfigProvided     = AppConfig.AppConfigurations

  private type IntermediateDependencies1 = AppConfig & Triplestore & Features & DspIngestConfig & JwtConfig
  private type IntermediateProvided1 = CommonModule.Provided & IriService & OntologyModule.Provided &
    InfrastructureModule.Provided

  private type IntermediateDependencies3 = IntermediateProvided1 & Features & DspIngestConfig & JwtConfig &
    DspIngestClient & PredicateObjectMapper & AppConfig
  private type IntermediateProvided3 = AdminModule.Provided & IntermediateProvided1

  type ApplicationEnvironment = DbInitEnv & MetricsServerEnv

  private val configLayer: ZLayer[self.ConfigDependencies, Nothing, self.ConfigProvided] =
    Logger.fromEnv() >>> AppConfig.layer

  val intermediateLayers1: ZLayer[self.IntermediateDependencies1, Nothing, self.IntermediateProvided1] =
    ZLayer.makeSome[self.IntermediateDependencies1, self.IntermediateProvided1](
      CommonModule.layer,
      IriService.layer,
      OntologyModule.layer,
      InfrastructureModule.layer,
    )

  val intermediateLayers2
    : ZLayer[JwtService & DspIngestConfig & IriConverter, Nothing, DspIngestClient & PredicateObjectMapper] =
    ZLayer.makeSome[JwtService & DspIngestConfig & IriConverter, DspIngestClient & PredicateObjectMapper](
      DspIngestClientLive.layer,
      PredicateObjectMapper.layer,
    )

  val intermediateLayers3
    : ZLayer[self.IntermediateDependencies3, Nothing, self.IntermediateProvided3 & DspIngestConfig & JwtConfig] =
    ZLayer.makeSome[self.IntermediateDependencies3, self.IntermediateProvided3 & DspIngestConfig & JwtConfig](
      AdminModule.layer,
    )

  private val intermediateLayersAll =
    configLayer >+> intermediateLayers1 >+> intermediateLayers2 >+> intermediateLayers3

  val bootstrap: ZLayer[Any, Nothing, ApplicationEnvironment] =
    intermediateLayersAll >+> LayersLive.remainingLayer

  /**
   * The `Environment` that we require to exist at startup.
   */
  type Environment =
    // format: off
    ActorSystem &
    AdminApiEndpoints &
    AdminApiModule.Provided &
    AdminModule.Provided &
    ApiComplexV2JsonLdRequestParser &
    ApiRoutes &
    ApiV2Endpoints &
    AssetPermissionsResponder &
    AuthenticationApiModule.Provided &
    AuthorizationRestService &
    CardinalityHandler &
    CommonModule.Provided &
    ConstructResponseUtilV2 &
    OntologyModule.Provided &
    DefaultObjectAccessPermissionService &
    HttpServer &
    IIIFRequestMessageHandler &
    InfrastructureModule.Provided &
    ListsApiModule.Provided &
    ListsResponder &
    MessageRelay &
    OntologyApiModule.Provided &
    OntologyInferencer &
    OntologyResponderV2 &
    PermissionUtilADM &
    PermissionsResponder &
    ProjectExportService &
    ProjectExportStorageService &
    ProjectImportService &
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
    ShaclEndpoints &
    SipiService &
    StandoffResponderV2 &
    StandoffTagUtilV2 &
    State &
    ValuesResponderV2
    // format: on

  type Config = AppConfig & Features & GraphRoute & KnoraApi & OpenTelemetryConfig & Sipi & Triplestore

  val remainingLayer =
    ZLayer.makeSome[
      Config & IntermediateProvided1 & IntermediateProvided3 & DspIngestClient & PredicateObjectMapper,
      self.Environment,
    ](
      AdminApiModule.layer,
      ApiComplexV2JsonLdRequestParser.layer,
      ApiRoutes.layer,
      ApiV2Endpoints.layer,
      AssetPermissionsResponder.layer,
      AuthenticationApiModule.layer,
      AuthorizationRestService.layer,
      BaseEndpoints.layer,
      CardinalityHandler.layer,
      ConstructResponseUtilV2.layer,
      HandlerMapper.layer,
      HttpServer.layer,
      IIIFRequestMessageHandlerLive.layer,
      KnoraResponseRenderer.layer,
      ListsApiModule.layer,
      ListsResponder.layer,
      ManagementEndpoints.layer,
      ManagementRoutes.layer,
      MessageRelayLive.layer,
      OntologyApiModule.layer,
      OntologyResponderV2.layer,
      OpenTelemetry.layer,
      PekkoActorSystem.layer,
      PermissionUtilADMLive.layer,
      PermissionsResponder.layer,
      ProjectExportServiceLive.layer,
      ProjectExportStorageServiceLive.layer,
      ProjectImportService.layer,
      RepositoryUpdater.layer,
      ResourceUtilV2.layer,
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
