/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing.admin

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route

import scala.concurrent.Future
import scala.concurrent.duration._

import org.knora.webapi.messages.admin.responder.storesmessages.ResetTriplestoreContentRequestADM
import org.knora.webapi.messages.admin.responder.storesmessages.StoresADMJsonProtocol
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.routing.Authenticator
import org.knora.webapi.routing.KnoraRoute
import org.knora.webapi.routing.KnoraRouteData
import org.knora.webapi.routing.RouteUtilADM

/**
 * A route used to send requests which can directly affect the data stored inside the triplestore.
 */

final case class StoreRouteADM(
  private val routeData: KnoraRouteData,
  override protected implicit val runtime: zio.Runtime[Authenticator]
) extends KnoraRoute(routeData, runtime)
    with StoresADMJsonProtocol {

  /**
   * Returns the route.
   */
  override def makeRoute: Route = Route {
    path("admin" / "store" / "ResetTriplestoreContent") {
      post {
        /* ResetTriplestoreContent */
        entity(as[Seq[RdfDataObject]]) { apiRequest =>
          parameter(Symbol("prependdefaults").as[Boolean] ? true) { prependDefaults => requestContext =>
            val msg = ResetTriplestoreContentRequestADM(
              rdfDataObjects = apiRequest,
              prependDefaults = prependDefaults
            )

            val requestMessage = Future.successful(msg)

            RouteUtilADM.runJsonRoute(
              requestMessageF = requestMessage,
              requestContext = requestContext,
              appActor = appActor,
              log = log
            )(timeout = 479999.milliseconds, executionContext = executionContext)
          }

        }
      }
    }
  }
}
