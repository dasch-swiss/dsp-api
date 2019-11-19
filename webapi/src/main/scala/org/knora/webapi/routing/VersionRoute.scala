/*
 * Copyright © 2015-2019 the contributors (see Contributors.md).
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
import spray.json.{JsObject, JsString}

import scala.concurrent.duration._

import org.knora.webapi.BuildInfo


case class VersionCheckResult(name: String,
                              webapi: String,
                              scala: String,
                              sbt: String,
                              akkaHttp: String,
                              sipi: String,
                              gdbSE: String,
                              gdbFree: String)

/**
  * Provides version check logic
  */
trait VersionCheck {
    this: VersionRoute =>

    override implicit val timeout: Timeout = 1.second

    protected def versionCheck() = {
        val result = getVersion()
        createResponse(result)
    }



    protected def createResponse(result: VersionCheckResult): HttpResponse = {
        HttpResponse(
            status = StatusCodes.OK,
            entity = HttpEntity(
                ContentTypes.`application/json`,
                JsObject(
                    "name" -> JsString(result.name),
                    "webapi" -> JsString(result.webapi),
                    "scala" -> JsString(result.scala),
                    "sbt" -> JsString(result.sbt),
                    "akkaHttp" -> JsString(result.akkaHttp),
                    "sipi" -> JsString(result.sipi),
                    "gdbSE" -> JsString(result.gdbSE),
                    "gdbFree" -> JsString(result.gdbFree)
                ).compactPrint
            )
        )
    }

    private def getVersion() = {
        var sipiVersion = BuildInfo.sipiVersion
        val sipiIndex = sipiVersion.indexOf(':')
        sipiVersion = if (sipiIndex > 0) sipiVersion.substring(sipiIndex+1) else sipiVersion

        var gdbSEVersion = BuildInfo.gdbSE
        val gdbSEIndex = gdbSEVersion.indexOf(':')
        gdbSEVersion = if (gdbSEIndex > 0) gdbSEVersion.substring(gdbSEIndex+1) else gdbSEVersion

        var gdbFreeVersion = BuildInfo.gdbFree
        val gdbFreeIndex = gdbFreeVersion.indexOf(':')
        gdbFreeVersion = if (gdbFreeIndex > 0) gdbFreeVersion.substring(gdbFreeIndex+1) else gdbFreeVersion

        VersionCheckResult(
            name = "version",
            webapi = BuildInfo.version,
            scala = BuildInfo.scalaVersion,
            sbt = BuildInfo.sbtVersion,
            akkaHttp = BuildInfo.akkaHttp,
            sipi = sipiVersion,
            gdbSE = gdbSEVersion,
            gdbFree = gdbFreeVersion
        )
    }
}

/**
  * Provides the '/version' endpoint serving the components versions.
  */
class VersionRoute(routeData: KnoraRouteData) extends KnoraRoute(routeData) with VersionCheck {

    override def knoraApiPath: Route = {
        path("version") {
            get {
                requestContext =>
                    requestContext.complete(versionCheck())
            }
        }
    }
}
