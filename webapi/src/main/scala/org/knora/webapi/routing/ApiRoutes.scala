/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing

import org.knora.webapi.config.AppConfig
import org.knora.webapi.core
import org.knora.webapi.core.ActorSystem
import org.knora.webapi.core.AppRouter
import org.knora.webapi.http.directives.DSPApiDirectives
import org.knora.webapi.http.version.ServerVersion
import org.knora.webapi.messages.ResponderRequest
import org.knora.webapi.messages.admin.responder.groupsmessages.GroupsResponderRequestADM
import org.knora.webapi.messages.admin.responder.listsmessages.ListsResponderRequestADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionsResponderRequestADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectsResponderRequestADM
import org.knora.webapi.messages.admin.responder.sipimessages.SipiResponderRequestADM
import org.knora.webapi.messages.admin.responder.storesmessages.StoreResponderRequestADM
import org.knora.webapi.messages.admin.responder.usersmessages.UsersResponderRequestADM
import org.knora.webapi.messages.util.ResponderData
import org.knora.webapi.messages.v1.responder.ckanmessages.CkanResponderRequestV1
import org.knora.webapi.messages.v1.responder.listmessages.ListsResponderRequestV1
import org.knora.webapi.messages.v1.responder.ontologymessages.OntologyResponderRequestV1
import org.knora.webapi.messages.v1.responder.projectmessages.ProjectsResponderRequestV1
import org.knora.webapi.messages.v1.responder.resourcemessages.ResourcesResponderRequestV1
import org.knora.webapi.messages.v1.responder.searchmessages.SearchResponderRequestV1
import org.knora.webapi.messages.v1.responder.standoffmessages.StandoffResponderRequestV1
import org.knora.webapi.messages.v1.responder.usermessages.UsersResponderRequestV1
import org.knora.webapi.messages.v1.responder.valuemessages.ValuesResponderRequestV1
import org.knora.webapi.messages.v2.responder.listsmessages.ListsResponderRequestV2
import org.knora.webapi.messages.v2.responder.ontologymessages.OntologiesResponderRequestV2
import org.knora.webapi.messages.v2.responder.resourcemessages.ResourcesResponderRequestV2
import org.knora.webapi.messages.v2.responder.searchmessages.SearchResponderRequestV2
import org.knora.webapi.messages.v2.responder.standoffmessages.StandoffResponderRequestV2
import org.knora.webapi.messages.v2.responder.valuemessages.ValuesResponderRequestV2
import org.knora.webapi.responders.admin._
import org.knora.webapi.responders.v1._
import org.knora.webapi.responders.v2._
import org.knora.webapi.routing.AroundDirectives
import org.knora.webapi.routing.HealthRoute
import org.knora.webapi.routing.KnoraRouteData
import org.knora.webapi.routing.RejectingRoute
import org.knora.webapi.routing.SwaggerApiDocsRoute
import org.knora.webapi.routing.VersionRoute
import org.knora.webapi.routing.admin.FilesRouteADM
import org.knora.webapi.routing.admin.GroupsRouteADM
import org.knora.webapi.routing.admin.ListsRouteADM
import org.knora.webapi.routing.admin.PermissionsRouteADM
import org.knora.webapi.routing.admin.ProjectsRouteADM
import org.knora.webapi.routing.admin.StoreRouteADM
import org.knora.webapi.routing.admin.UsersRouteADM
import org.knora.webapi.routing.v1.AssetsRouteV1
import org.knora.webapi.routing.v1.AuthenticationRouteV1
import org.knora.webapi.routing.v1.CkanRouteV1
import org.knora.webapi.routing.v1.ListsRouteV1
import org.knora.webapi.routing.v1.ProjectsRouteV1
import org.knora.webapi.routing.v1.ResourceTypesRouteV1
import org.knora.webapi.routing.v1.ResourcesRouteV1
import org.knora.webapi.routing.v1.SearchRouteV1
import org.knora.webapi.routing.v1.StandoffRouteV1
import org.knora.webapi.routing.v1.UsersRouteV1
import org.knora.webapi.routing.v1.ValuesRouteV1
import org.knora.webapi.routing.v2.AuthenticationRouteV2
import org.knora.webapi.routing.v2.ListsRouteV2
import org.knora.webapi.routing.v2.OntologiesRouteV2
import org.knora.webapi.routing.v2.ResourcesRouteV2
import org.knora.webapi.routing.v2.SearchRouteV2
import org.knora.webapi.routing.v2.StandoffRouteV2
import org.knora.webapi.routing.v2.ValuesRouteV2
import org.knora.webapi.settings.KnoraDispatchers
import org.knora.webapi.settings.KnoraSettingsImpl
import org.knora.webapi.store.cache.settings.CacheServiceSettings
import org.knora.webapi.util.ActorUtil._
import zio._
import akka.http.scaladsl.server.Route
import ch.megard.akka.http.cors.scaladsl.CorsDirectives
import ch.megard.akka.http.cors.scaladsl.settings.CorsSettings
import org.knora.webapi.http.directives.DSPApiDirectives
import org.knora.webapi.http.version.ServerVersion
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route

object ApiRoutes extends AroundDirectives {

  /**
   * All routes composed together.
   */
  val apiRoutes: ZIO[ActorSystem with AppRouter with core.State, Nothing, Route] =
    for {
      sys    <- ZIO.service[ActorSystem]
      router <- ZIO.service[AppRouter]
      state  <- ZIO.service[core.State]
      routeData <- ZIO.succeed(
                     KnoraRouteData(
                       system = sys.system,
                       appActor = router.ref,
                       state = state
                     )
                   )
      routes <- makeRoutes(routeData)
    } yield routes

  /**
   * All routes composed together and CORS activated based on the
   * the configuration in application.conf (akka-http-cors).
   *
   * ALL requests go through each of the routes in ORDER.
   * The FIRST matching route is used for handling a request.
   */
  private def makeRoutes(routeData: KnoraRouteData) =
    ZIO.attempt {
      logDuration {
        ServerVersion.addServerHeader {
          DSPApiDirectives.handleErrors(routeData.system) {
            CorsDirectives.cors(CorsSettings(routeData.system)) {
              DSPApiDirectives.handleErrors(routeData.system) {
                new HealthRoute(routeData).knoraApiPath ~
                  new VersionRoute(routeData).knoraApiPath ~
                  new RejectingRoute(routeData).knoraApiPath ~
                  new ResourcesRouteV1(routeData).knoraApiPath ~
                  new ValuesRouteV1(routeData).knoraApiPath ~
                  new StandoffRouteV1(routeData).knoraApiPath ~
                  new ListsRouteV1(routeData).knoraApiPath ~
                  new ResourceTypesRouteV1(routeData).knoraApiPath ~
                  new SearchRouteV1(routeData).knoraApiPath ~
                  new AuthenticationRouteV1(routeData).knoraApiPath ~
                  new AssetsRouteV1(routeData).knoraApiPath ~
                  new CkanRouteV1(routeData).knoraApiPath ~
                  new UsersRouteV1(routeData).knoraApiPath ~
                  new ProjectsRouteV1(routeData).knoraApiPath ~
                  new OntologiesRouteV2(routeData).knoraApiPath ~
                  new SearchRouteV2(routeData).knoraApiPath ~
                  new ResourcesRouteV2(routeData).knoraApiPath ~
                  new ValuesRouteV2(routeData).knoraApiPath ~
                  new StandoffRouteV2(routeData).knoraApiPath ~
                  new ListsRouteV2(routeData).knoraApiPath ~
                  new AuthenticationRouteV2(routeData).knoraApiPath ~
                  new GroupsRouteADM(routeData).knoraApiPath ~
                  new ListsRouteADM(routeData).knoraApiPath ~
                  new PermissionsRouteADM(routeData).knoraApiPath ~
                  new ProjectsRouteADM(routeData).knoraApiPath ~
                  new StoreRouteADM(routeData).knoraApiPath ~
                  new UsersRouteADM(routeData).knoraApiPath ~
                  new FilesRouteADM(routeData).knoraApiPath ~
                  new SwaggerApiDocsRoute(routeData).knoraApiPath
              }
            }
          }
        }
      }
    }.orDie

}
