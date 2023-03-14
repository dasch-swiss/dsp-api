/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.core

import zio.ULayer
import zio.ZLayer

import org.knora.webapi.auth.JWTService
import org.knora.webapi.config.AppConfig
import org.knora.webapi.http.middleware.AuthenticationMiddleware
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.util.ConstructResponseUtilV2
import org.knora.webapi.messages.util.ConstructResponseUtilV2Live
import org.knora.webapi.messages.util.PermissionUtilADM
import org.knora.webapi.messages.util.PermissionUtilADMLive
import org.knora.webapi.messages.util.ValueUtilV1
import org.knora.webapi.messages.util.ValueUtilV1Live
import org.knora.webapi.messages.util.standoff.StandoffTagUtilV2
import org.knora.webapi.messages.util.standoff.StandoffTagUtilV2Live
import org.knora.webapi.responders.ActorDeps
import org.knora.webapi.responders.ActorToZioBridge
import org.knora.webapi.responders.IriService
import org.knora.webapi.responders.admin.GroupsResponderADM
import org.knora.webapi.responders.admin.GroupsResponderADMLive
import org.knora.webapi.responders.admin.ListsResponderADM
import org.knora.webapi.responders.admin.ListsResponderADMLive
import org.knora.webapi.responders.admin.PermissionsResponderADM
import org.knora.webapi.responders.admin.PermissionsResponderADMLive
import org.knora.webapi.responders.admin.ProjectsResponderADM
import org.knora.webapi.responders.admin.ProjectsResponderADMLive
import org.knora.webapi.responders.admin.ProjectsServiceLive
import org.knora.webapi.responders.admin.SipiResponderADM
import org.knora.webapi.responders.admin.SipiResponderADMLive
import org.knora.webapi.responders.admin.StoresResponderADM
import org.knora.webapi.responders.admin.StoresResponderADMLive
import org.knora.webapi.responders.admin.UsersResponderADM
import org.knora.webapi.responders.admin.UsersResponderADMLive
import org.knora.webapi.responders.v1.CkanResponderV1
import org.knora.webapi.responders.v1.CkanResponderV1Live
import org.knora.webapi.responders.v1.ListsResponderV1
import org.knora.webapi.responders.v1.ListsResponderV1Live
import org.knora.webapi.responders.v1.OntologyResponderV1
import org.knora.webapi.responders.v1.OntologyResponderV1Live
import org.knora.webapi.responders.v1.ProjectsResponderV1
import org.knora.webapi.responders.v1.ProjectsResponderV1Live
import org.knora.webapi.responders.v1.SearchResponderV1
import org.knora.webapi.responders.v1.SearchResponderV1Live
import org.knora.webapi.responders.v1.StandoffResponderV1
import org.knora.webapi.responders.v1.StandoffResponderV1Live
import org.knora.webapi.responders.v1.UsersResponderV1
import org.knora.webapi.responders.v1.UsersResponderV1Live
import org.knora.webapi.responders.v1.ValuesResponderV1
import org.knora.webapi.responders.v1.ValuesResponderV1Live
import org.knora.webapi.responders.v2.ListsResponderV2
import org.knora.webapi.responders.v2.ListsResponderV2Live
import org.knora.webapi.responders.v2.OntologyResponderV2
import org.knora.webapi.responders.v2.OntologyResponderV2Live
import org.knora.webapi.responders.v2.ResourceUtilV2
import org.knora.webapi.responders.v2.ResourceUtilV2Live
import org.knora.webapi.responders.v2.StandoffResponderV2
import org.knora.webapi.responders.v2.StandoffResponderV2Live
import org.knora.webapi.responders.v2.ontology.CardinalityHandler
import org.knora.webapi.responders.v2.ontology.CardinalityHandlerLive
import org.knora.webapi.responders.v2.ontology.OntologyHelpers
import org.knora.webapi.responders.v2.ontology.OntologyHelpersLive
import org.knora.webapi.routing.ApiRoutes
import org.knora.webapi.routing.admin.AuthenticatorService
import org.knora.webapi.routing.admin.ProjectsRouteZ
import org.knora.webapi.slice.ontology.api.service.RestCardinalityService
import org.knora.webapi.slice.ontology.api.service.RestCardinalityServiceLive
import org.knora.webapi.slice.ontology.domain.service.CardinalityService
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
import org.knora.webapi.store.triplestore.TriplestoreRequestMessageHandler
import org.knora.webapi.store.triplestore.TriplestoreRequestMessageHandlerLive
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
      with AppConfig
      with AppRouter
      with AppRouterRelayingMessageHandler
      with OntologyCache
      with CacheService
      with CacheServiceRequestMessageHandler
      with CardinalityHandler
      with CardinalityService
      with CkanResponderV1
      with ConstructResponseUtilV2
      with GroupsResponderADM
      with HttpServer
      with IIIFService
      with IIIFRequestMessageHandler
      with IriService
      with JWTService
      with ListsResponderV2
      with ListsResponderADM
      with ListsResponderV1
      with MessageRelay
      with OntologyResponderV1
      with OntologyResponderV2
      with OntologyHelpers
      with PermissionUtilADM
      with PermissionsResponderADM
      with ProjectsResponderADM
      with ProjectsResponderV1
      with RepositoryUpdater
      with RestCardinalityService
      with RestResourceInfoService
      with ResourceUtilV2
      with SearchResponderV1
      with SipiResponderADM
      with StandoffResponderV1
      with StandoffResponderV2
      with StandoffTagUtilV2
      with State
      with StoresResponderADM
      with TriplestoreService
      with TriplestoreRequestMessageHandler
      with UsersResponderADM
      with UsersResponderV1
      with ValuesResponderV1
      with ValueUtilV1

  /**
   * All effect layers needed to provide the `Environment`
   */
  val dspLayersLive: ULayer[DspEnvironmentLive] =
    ZLayer.make[DspEnvironmentLive](
      ActorDeps.layer,
      ActorSystem.layer,
      ActorToZioBridge.live,
      ApiRoutes.layer,
      AppConfig.layer,
      AppRouter.layer,
      AppRouterRelayingMessageHandler.layer,
      AuthenticationMiddleware.layer,
      AuthenticatorService.layer,
      CacheServiceInMemImpl.layer,
      CacheServiceRequestMessageHandlerLive.layer,
      CardinalityHandlerLive.layer,
      CardinalityService.layer,
      CkanResponderV1Live.layer,
      ConstructResponseUtilV2Live.layer,
      GroupsResponderADMLive.layer,
      HttpServer.layer,
      HttpServerZ.layer, // this is the new ZIO HTTP server layer
      IIIFRequestMessageHandlerLive.layer,
      IIIFServiceSipiImpl.layer,
      IriConverter.layer,
      IriService.layer,
      JWTService.layer,
      ListsResponderV2Live.layer,
      ListsResponderADMLive.layer,
      ListsResponderV1Live.layer,
      MessageRelayLive.layer,
      OntologyCacheLive.layer,
      OntologyHelpersLive.layer,
      OntologyRepoLive.layer,
      OntologyResponderV1Live.layer,
      OntologyResponderV2Live.layer,
      PermissionUtilADMLive.layer,
      PermissionsResponderADMLive.layer,
      PredicateRepositoryLive.layer,
      ProjectsResponderADMLive.layer,
      ProjectsResponderV1Live.layer,
      ProjectsRouteZ.layer,
      ProjectsServiceLive.layer,
      RepositoryUpdater.layer,
      ResourceInfoRepo.layer,
      ResourceInfoRoute.layer,
      RestCardinalityServiceLive.layer,
      RestResourceInfoService.layer,
      ResourceUtilV2Live.layer,
      SearchResponderV1Live.layer,
      SipiResponderADMLive.layer,
      StandoffResponderV1Live.layer,
      StandoffResponderV2Live.layer,
      StandoffTagUtilV2Live.layer,
      State.layer,
      StoresResponderADMLive.layer,
      StringFormatter.live,
      TriplestoreServiceLive.layer,
      TriplestoreRequestMessageHandlerLive.layer,
      UsersResponderADMLive.layer,
      UsersResponderV1Live.layer,
      ValuesResponderV1Live.layer,
      ValueUtilV1Live.layer
    )
}
