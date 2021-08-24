/*
 * Copyright © 2015-2021 the contributors (see Contributors.md).
 *
 * This file is part of Knora.
 *
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.routing

import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives.{get, path}
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import org.knora.webapi.feature.FeatureFactoryConfig
import org.knora.webapi.http.version.versioninfo.VersionInfo
import spray.json.{JsObject, JsString}

import scala.concurrent.duration._

case class VersionCheckResult(
  name: String,
  webapi: String,
  scala: String,
  akkaHttp: String,
  sipi: String,
  fuseki: String
)

/**
 * Provides version check logic
 */
trait VersionCheck {
  this: VersionRoute =>

  override implicit val timeout: Timeout = 1.second

  protected def versionCheck: HttpResponse =
    createResponse(getVersion)

  protected def createResponse(result: VersionCheckResult): HttpResponse =
    HttpResponse(
      status = StatusCodes.OK,
      entity = HttpEntity(
        ContentTypes.`application/json`,
        JsObject(
          "name"     -> JsString(result.name),
          "webapi"   -> JsString(result.webapi),
          "scala"    -> JsString(result.scala),
          "akkaHttp" -> JsString(result.akkaHttp),
          "sipi"     -> JsString(result.sipi),
          "fuseki"   -> JsString(result.fuseki)
        ).compactPrint
      )
    )

  private def getVersion: VersionCheckResult = {
    var sipiVersion = VersionInfo.sipiVersion
    val sipiIndex   = sipiVersion.indexOf(':')
    sipiVersion = if (sipiIndex > 0) sipiVersion.substring(sipiIndex + 1) else sipiVersion

    var fusekiVersion = VersionInfo.jenaFusekiVersion
    val fusekiIndex   = fusekiVersion.indexOf(':')
    fusekiVersion = if (fusekiIndex > 0) fusekiVersion.substring(fusekiIndex + 1) else fusekiVersion

    VersionCheckResult(
      name = "version",
      webapi = VersionInfo.webapiVersion,
      scala = VersionInfo.scalaVersion,
      akkaHttp = VersionInfo.akkaHttpVersion,
      sipi = sipiVersion,
      fuseki = fusekiVersion
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
        requestContext.complete(versionCheck)
      }
    }
}
