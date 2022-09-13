/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing

import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives.get
import akka.http.scaladsl.server.Directives.path
import akka.http.scaladsl.server.Route
import spray.json.JsObject
import spray.json.JsString

import org.knora.webapi.http.version.BuildInfo

/**
 * Provides the '/version' endpoint serving the components versions.
 */
final case class VersionRoute() {

  protected def createResponse(): HttpResponse = {
    val sipiVersion: String   = BuildInfo.sipi.split(":").apply(1)
    val fusekiVersion: String = BuildInfo.fuseki.split(":").apply(1)

    HttpResponse(
      status = StatusCodes.OK,
      entity = HttpEntity(
        ContentTypes.`application/json`,
        JsObject(
          "name"     -> JsString("version"),
          "webapi"   -> JsString(BuildInfo.version),
          "scala"    -> JsString(BuildInfo.scalaVersion),
          "akkaHttp" -> JsString(BuildInfo.akkaHttp),
          "sipi"     -> JsString(sipiVersion),
          "fuseki"   -> JsString(fusekiVersion)
        ).prettyPrint
      )
    )
  }

  /**
   * Returns the route.
   */
  def makeRoute: Route =
    path("version") {
      get { requestContext =>
        requestContext.complete(createResponse())
      }
    }
}
