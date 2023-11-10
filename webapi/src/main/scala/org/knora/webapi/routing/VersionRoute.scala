/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing

import org.apache.pekko
import spray.json.JsObject
import spray.json.JsString

import org.knora.webapi.http.version.BuildInfo

import pekko.http.scaladsl.model.*
import pekko.http.scaladsl.server.Directives.get
import pekko.http.scaladsl.server.Directives.path
import pekko.http.scaladsl.server.Route

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
          "name"      -> JsString("version"),
          "webapi"    -> JsString(BuildInfo.version),
          "scala"     -> JsString(BuildInfo.scalaVersion),
          "pekkoHttp" -> JsString(BuildInfo.pekkoHttp),
          "sipi"      -> JsString(sipiVersion),
          "fuseki"    -> JsString(fusekiVersion)
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
