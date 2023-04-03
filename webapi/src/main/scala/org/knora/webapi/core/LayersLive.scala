/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.core

import zio.ULayer
import zio.ZLayer

import org.knora.webapi.config.AppConfig
import org.knora.webapi.http.middleware.AuthenticationMiddleware
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.util.ConstructResponseUtilV2
import org.knora.webapi.messages.util.ConstructResponseUtilV2Live
import org.knora.webapi.messages.util.PermissionUtilADM
import org.knora.webapi.messages.util.PermissionUtilADMLive
import org.knora.webapi.messages.util.ValueUtilV1
import org.knora.webapi.messages.util.ValueUtilV1Live
import org.knora.webapi.messages.util.search.QueryTraverser
import org.knora.webapi.messages.util.search.SparqlTransformerLive
import org.knora.webapi.messages.util.search.gravsearch.types.GravsearchTypeInspectionUtil
import org.knora.webapi.messages.util.standoff.StandoffTagUtilV2
import org.knora.webapi.messages.util.standoff.StandoffTagUtilV2Live
import org.knora.webapi.responders.IriService
import org.knora.webapi.responders.admin.GroupsResponderADM
import org.knora.webapi.responders.admin.GroupsResponderADMLive
import org.knora.webapi.responders.admin.ListsResponderADM
import org.knora.webapi.responders.admin.ListsResponderADMLive
import org.knora.webapi.responders.admin.PermissionsResponderADM
import org.knora.webapi.responders.admin.PermissionsResponderADMLive
import org.knora.webapi.responders.admin.ProjectsResponderADM
import org.knora.webapi.responders.admin.ProjectsResponderADMLive
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
import org.knora.webapi.responders.v1.ResourcesResponderV1
import org.knora.webapi.responders.v1.ResourcesResponderV1Live
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
import org.knora.webapi.responders.v2.ResourcesResponderV2
import org.knora.webapi.responders.v2.ResourcesResponderV2Live
import org.knora.webapi.responders.v2.SearchResponderV2
import org.knora.webapi.responders.v2.SearchResponderV2Live
import org.knora.webapi.responders.v2.StandoffResponderV2
import org.knora.webapi.responders.v2.StandoffResponderV2Live
import org.knora.webapi.responders.v2.ValuesResponderV2
import org.knora.webapi.responders.v2.ValuesResponderV2Live
import org.knora.webapi.responders.v2.ontology.CardinalityHandler
import org.knora.webapi.responders.v2.ontology.CardinalityHandlerLive
import org.knora.webapi.responders.v2.ontology.OntologyHelpers
import org.knora.webapi.responders.v2.ontology.OntologyHelpersLive
import org.knora.webapi.routing.ApiRoutes
import org.knora.webapi.routing.Authenticator
import org.knora.webapi.routing.AuthenticatorLive
import org.knora.webapi.routing.JwtService
import org.knora.webapi.routing.JwtServiceLive
import org.knora.webapi.routing.admin.AuthenticatorService
import org.knora.webapi.routing.admin.ProjectsRouteZ
import org.knora.webapi.slice.admin.api.service.ProjectADMRestService
import org.knora.webapi.slice.admin.api.service.ProjectsADMRestServiceLive
import org.knora.webapi.slice.admin.domain.service.KnoraProjectRepo
import org.knora.webapi.slice.admin.domain.service.ProjectADMService
import org.knora.webapi.slice.admin.domain.service.ProjectADMServiceLive
import org.knora.webapi.slice.admin.repo.service.KnoraProjectRepoLive
import org.knora.webapi.slice.common.service.PredicateObjectMapper
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
      with Authenticator
      with CacheService
      with CacheServiceRequestMessageHandler
      with CardinalityHandler
      with CardinalityService
      with CkanResponderV1
      with ConstructResponseUtilV2
      with GravsearchTypeInspectionUtil
      with GroupsResponderADM
      with HttpServer
      with IIIFRequestMessageHandler
      with IIIFService
      with IriService
      with JwtService
      with KnoraProjectRepo
      with ListsResponderADM
      with ListsResponderV1
      with ListsResponderV2
      with MessageRelay
      with OntologyCache
      with OntologyHelpers
      with OntologyRepo
      with OntologyResponderV1
      with OntologyResponderV2
      with PermissionUtilADM
      with PermissionsResponderADM
      with PredicateObjectMapper
      with ProjectADMRestService
      with ProjectADMService
      with ProjectsResponderADM
      with ProjectsResponderV1
      with QueryTraverser
      with RepositoryUpdater
      with ResourceUtilV2
      with ResourceUtilV2
      with ResourcesResponderV1
      with ResourcesResponderV2
      with RestCardinalityService
      with RestResourceInfoService
      with SearchResponderV1
      with SearchResponderV2
      with SipiResponderADM
      with SparqlTransformerLive
      with StandoffResponderV1
      with StandoffResponderV2
      with StandoffTagUtilV2
      with State
      with StoresResponderADM
      with StringFormatter
      with TriplestoreRequestMessageHandler
      with TriplestoreService
      with UsersResponderADM
      with UsersResponderV1
      with ValueUtilV1
      with ValuesResponderV1
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
      CacheServiceInMemImpl.layer,
      CacheServiceRequestMessageHandlerLive.layer,
      CardinalityHandlerLive.layer,
      CardinalityService.layer,
      CkanResponderV1Live.layer,
      ConstructResponseUtilV2Live.layer,
      GravsearchTypeInspectionUtil.layer,
      GroupsResponderADMLive.layer,
      HttpServer.layer,
      HttpServerZ.layer, // this is the new ZIO HTTP server layer
      IIIFRequestMessageHandlerLive.layer,
      IIIFServiceSipiImpl.layer,
      IriConverter.layer,
      IriService.layer,
      JwtServiceLive.layer,
      KnoraProjectRepoLive.layer,
      ListsResponderADMLive.layer,
      ListsResponderV1Live.layer,
      ListsResponderV2Live.layer,
      MessageRelayLive.layer,
      OntologyCacheLive.layer,
      OntologyHelpersLive.layer,
      OntologyRepoLive.layer,
      OntologyResponderV1Live.layer,
      OntologyResponderV2Live.layer,
      PermissionUtilADMLive.layer,
      PermissionsResponderADMLive.layer,
      PredicateObjectMapper.layer,
      PredicateRepositoryLive.layer,
      ProjectADMServiceLive.layer,
      ProjectsADMRestServiceLive.layer,
      ProjectsResponderADMLive.layer,
      ProjectsResponderV1Live.layer,
      ProjectsRouteZ.layer,
      QueryTraverser.layer,
      RepositoryUpdater.layer,
      ResourceInfoRepo.layer,
      ResourceInfoRoute.layer,
      ResourceUtilV2Live.layer,
      ResourcesResponderV1Live.layer,
      ResourcesResponderV2Live.layer,
      RestCardinalityServiceLive.layer,
      RestResourceInfoService.layer,
      SearchResponderV1Live.layer,
      SearchResponderV2Live.layer,
      SipiResponderADMLive.layer,
      SparqlTransformerLive.layer,
      StandoffResponderV1Live.layer,
      StandoffResponderV2Live.layer,
      StandoffTagUtilV2Live.layer,
      State.layer,
      StoresResponderADMLive.layer,
      StringFormatter.live,
      TriplestoreRequestMessageHandlerLive.layer,
      TriplestoreServiceLive.layer,
      UsersResponderADMLive.layer,
      UsersResponderV1Live.layer,
      ValueUtilV1Live.layer,
      ValuesResponderV1Live.layer,
      ValuesResponderV2Live.layer
    )
}
