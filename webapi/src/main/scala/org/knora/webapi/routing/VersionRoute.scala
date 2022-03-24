/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing

import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives.{get, path}
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import org.knora.webapi.feature.FeatureFactoryConfig
import org.knora.webapi.http.version.BuildInfo
import spray.json.{JsObject, JsString}

import scala.concurrent.duration._

/**
 * Provides version check logic
 */
trait VersionCheck {
  this: VersionRoute =>

  override implicit val timeout: Timeout = 1.second

  protected def createResponse(): HttpResponse = {
    val sipiVersion: String = BuildInfo.sipi.split(":").apply(1)
    val fusekiVersion: String =  BuildInfo.fuseki.split(":").apply(1)

    HttpResponse(
      status = StatusCodes.OK,
      entity = HttpEntity(
        ContentTypes.`application/json`,
        JsObject(
          "name" -> JsString(BuildInfo.name),
          "webapi" -> JsString(BuildInfo.version),
          "scala" -> JsString(BuildInfo.scalaVersion),
          "akkaHttp" -> JsString(BuildInfo.akkaHttp),
          "sipi" -> JsString(sipiVersion),
          "fuseki" -> JsString(fusekiVersion)
        ).prettyPrint
      )
    )
  }
}

/**
 * Provides the '/version' endpoint serving the components versions.
 */
class VersionRoute(routeData: KnoraRouteData) extends KnoraRoute(routeData) with VersionCheck {

  /**
   * Returns the route.
   */
  override def makeRoute(featureFactoryConfig: FeatureFactoryConfig): Route =
    path("version") {
      get { requestContext =>
        requestContext.complete(createResponse())
      }
    }
}
